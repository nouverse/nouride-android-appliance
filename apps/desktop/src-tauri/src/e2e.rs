//! The Dev build's hooks for an end-to-end run over ssh, since WKWebView has no WebDriver: running the app's binary
//! again with `--e2e <action> [argument]` reaches the running app (single instance) and does what a click would.
//! Compiled only with the `dev` feature: a release never carries them.

use std::fs;
use std::process::Command;
use std::sync::Arc;

use serde_json::json;
use tauri::{AppHandle, Manager};

use crate::state::App;
use crate::{commands, poller, tray, windows};

fn dir(app: &App) -> std::path::PathBuf {
  app.layout.data.join("e2e")
}

pub fn handle(handle: &AppHandle, app: &Arc<App>, argv: &[String]) {
  let Some(at) = argv.iter().position(|a| a == "--e2e") else { return };
  let action = argv.get(at + 1).map(String::as_str).unwrap_or("");
  let arg = argv.get(at + 2).cloned().unwrap_or_default();
  let (h, a) = (handle.clone(), app.clone());
  let result = (|| -> Result<serde_json::Value, String> {
    match action {
      "dump" => return Ok(dump(&h, &a)),
      "place-here" => commands::here_now(&h, &a, Some(arg.clone()).filter(|v| !v.is_empty())),
      "place-remote" => return Ok(serde_json::to_value(commands::remote_now(&h, &a, &arg)).unwrap_or_default()),
      "start" => commands::start_now(&h, &a),
      "stop" => commands::turn_off_now(&h, &a),
      "retry" => commands::retry_now(&h, &a),
      "access" => commands::access_now(&h, &a, arg == "on"),
      "appearance" => {
        let appearance = serde_json::from_value(json!(arg)).map_err(|e| e.to_string())?;
        a.set_appearance(appearance);
        windows::apply_appearance(&h, appearance);
      }
      "open-dashboard" => windows::dashboard::open(&h, &a),
      "close-dashboard" => {
        if let Some(w) = h.get_webview_window(windows::dashboard::LABEL) {
          w.close().map_err(|e| e.to_string())?;
        }
      }
      "show" => windows::main::show_at(&h, if arg.is_empty() { "home" } else { &arg }),
      // As the window's close button: the window is destroyed, the app stays in the menu bar.
      "close-main" => {
        if let Some(w) = h.get_webview_window(windows::main::LABEL) {
          w.close().map_err(|e| e.to_string())?;
        }
      }
      "tray" => tray::press(&h, &a, &arg),
      // Runs a script in the dashboard's window, as a click there would (WKWebView has no WebDriver).
      "dashboard-js" => {
        let w = h.get_webview_window(windows::dashboard::LABEL).ok_or("the dashboard's window is not open")?;
        w.eval(&arg).map_err(|e| e.to_string())?;
      }
      "check-nouride-update" => {
        let _ = a.notices().remember(&updates::client::Client { origin: a.build.origin(), token_path: a.layout.control_token() }.status(true).map_err(|e| e.to_string())?, crate::applog::now_ms());
      }
      "update-nouride" => crate::versions::update_nouride(h.clone(), a.clone()),
      "backup-count" => crate::nouride_files::count(&h, &a),
      "backup-start" => crate::nouride_files::start(&h, &a),
      // The question on the dashboard's window ("Go back to Nouride Home?"): its words and buttons, and with a
      // button's words, a press on it, as a click there would.
      #[cfg(target_os = "macos")]
      "sheet" => {
        let press = Some(arg.clone()).filter(|v| !v.is_empty());
        let found = sheet::on(&h, windows::dashboard::LABEL, press.clone()).ok_or("no question on the dashboard's window")?;
        if press.is_some_and(|p| !found.buttons.contains(&p)) {
          return Err(format!("no such button: {:?}", found.buttons));
        }
        return Ok(json!({ "words": found.words, "buttons": found.buttons }));
      }
      "shot" => return shot(&h, &a, &arg),
      // The answer is written first; the app goes a moment later.
      "quit" => {
        let h = h.clone();
        std::thread::spawn(move || {
          std::thread::sleep(std::time::Duration::from_millis(500));
          h.exit(0);
        });
        return Ok(json!("quitting"));
      }
      other => return Err(format!("no such action: {other}")),
    }
    poller::tick(&h, &a, &mut 0);
    Ok(json!("done"))
  })();
  let out = dir(app);
  let _ = fs::create_dir_all(&out);
  let answer = match result {
    Ok(v) => json!({ "action": action, "ok": true, "answer": v }),
    Err(e) => json!({ "action": action, "ok": false, "error": e }),
  };
  let _ = fs::write(out.join("last.json"), serde_json::to_string_pretty(&answer).unwrap_or_default());
}

/// The app's state as JSON: the snapshot, the menu bar's items, and the windows.
fn dump(handle: &AppHandle, app: &App) -> serde_json::Value {
  let window = |label: &str| {
    handle.get_webview_window(label).map(|w| {
      let scale = w.scale_factor().unwrap_or(1.0);
      let size = w.inner_size().map(|s| s.to_logical::<f64>(scale)).ok();
      json!({
        "visible": w.is_visible().unwrap_or(false),
        "size": size.map(|s| json!({ "width": s.width.round(), "height": s.height.round() })),
        "url": w.url().map(|u| u.to_string()).unwrap_or_default(),
        "title": w.title().unwrap_or_default(),
      })
    })
  };
  #[cfg(target_os = "macos")]
  let question = sheet::on(handle, windows::dashboard::LABEL, None).map(|s| json!({ "words": s.words, "buttons": s.buttons }));
  #[cfg(not(target_os = "macos"))]
  let question = None::<serde_json::Value>;
  let tray_items: Vec<_> = tray::LAST.lock().unwrap().iter().map(|(id, words, enabled)| json!({ "id": id, "words": words, "enabled": enabled })).collect();
  let state = json!({
    "snapshot": app.snapshot(),
    "tray": tray_items,
    "windows": { "main": window(windows::main::LABEL), "dashboard": window(windows::dashboard::LABEL) },
    "question": question,
    "agent": { "label": app.build.agent_label, "path": app.agent.path, "installed": service::Supervisor::installed(&app.agent) },
  });
  let _ = fs::create_dir_all(dir(app));
  let _ = fs::write(dir(app).join("state.json"), serde_json::to_string_pretty(&state).unwrap_or_default());
  state
}

/// A picture of one of the app's own windows, never the screen: the window is brought to the front, kept above the
/// others for the capture, and only its rectangle is taken (`screencapture -R`, in points).
fn shot(handle: &AppHandle, app: &App, name: &str) -> Result<serde_json::Value, String> {
  let (label, file) = match name.split_once(':') {
    Some(("dashboard", f)) => (windows::dashboard::LABEL, f),
    Some((_, f)) => (windows::main::LABEL, f),
    None => (windows::main::LABEL, name),
  };
  let window = handle.get_webview_window(label).ok_or("no such window")?;
  let scale = window.scale_factor().map_err(|e| e.to_string())?;
  let pos = window.outer_position().map_err(|e| e.to_string())?.to_logical::<f64>(scale);
  let size = window.outer_size().map_err(|e| e.to_string())?.to_logical::<f64>(scale);
  let _ = window.show();
  let _ = window.set_focus();
  let _ = window.set_always_on_top(true);
  std::thread::sleep(std::time::Duration::from_millis(700));
  let path = dir(app).join(format!("{file}.png"));
  let rect = format!("{},{},{},{}", pos.x.round(), pos.y.round(), size.width.round(), size.height.round());
  let status = Command::new("/usr/sbin/screencapture").args(["-x", "-o", "-R", &rect]).arg(&path).status();
  let _ = window.set_always_on_top(false);
  match status {
    Ok(s) if s.success() && path.exists() => Ok(json!({ "file": path, "rect": rect })),
    Ok(s) => Err(format!("screencapture exited {s}")),
    Err(e) => Err(e.to_string()),
  }
}

/// The question a window carries as a sheet, read from AppKit: WKWebView has no WebDriver and a sheet is not a page.
#[cfg(target_os = "macos")]
mod sheet {
  use std::sync::mpsc;
  use std::time::Duration;

  use objc2::rc::Retained;
  use objc2::Message;
  use objc2_app_kit::{NSButton, NSTextField, NSView, NSWindow};
  use tauri::{AppHandle, Manager, WebviewWindow};

  pub struct Sheet {
    pub words: Vec<String>,
    pub buttons: Vec<String>,
  }

  /// The sheet on the window, if one is up; with `press`, the button that says so is clicked. Never called on the
  /// main thread, which does the reading.
  pub fn on(handle: &AppHandle, label: &str, press: Option<String>) -> Option<Sheet> {
    let window = handle.get_webview_window(label)?;
    let (tx, rx) = mpsc::channel();
    handle
      .run_on_main_thread(move || {
        let _ = tx.send(read(&window, press.as_deref()));
      })
      .ok()?;
    rx.recv_timeout(Duration::from_secs(10)).ok().flatten()
  }

  fn read(window: &WebviewWindow, press: Option<&str>) -> Option<Sheet> {
    let ns = window.ns_window().ok()?;
    // SAFETY: on the main thread, and the pointer is this window's NSWindow, alive while `window` is.
    let ns: &NSWindow = unsafe { &*ns.cast() };
    let sheet = ns.attachedSheet()?;
    let mut words = Vec::new();
    let mut buttons = Vec::new();
    if let Some(content) = sheet.contentView() {
      walk(&content, &mut words, &mut buttons);
    }
    let found = Sheet { words, buttons: buttons.iter().map(|b| b.title().to_string()).collect() };
    if let Some(button) = press.and_then(|words| buttons.iter().find(|b| b.title().to_string() == words)) {
      // SAFETY: a click on a button of a sheet that is on screen, on the main thread.
      unsafe { button.performClick(None) };
    }
    Some(found)
  }

  fn walk(view: &NSView, words: &mut Vec<String>, buttons: &mut Vec<Retained<NSButton>>) {
    if let Some(button) = view.downcast_ref::<NSButton>() {
      buttons.push(button.retain());
    } else if let Some(text) = view.downcast_ref::<NSTextField>() {
      words.push(text.stringValue().to_string());
    }
    for sub in view.subviews().to_vec() {
      walk(&sub, words, buttons);
    }
  }
}
