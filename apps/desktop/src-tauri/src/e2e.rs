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
      "hide" => {
        if let Some(w) = h.get_webview_window(windows::main::LABEL) {
          w.hide().map_err(|e| e.to_string())?;
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
  let tray_items: Vec<_> = tray::LAST.lock().unwrap().iter().map(|(id, words, enabled)| json!({ "id": id, "words": words, "enabled": enabled })).collect();
  let state = json!({
    "snapshot": app.snapshot(),
    "tray": tray_items,
    "windows": { "main": window(windows::main::LABEL), "dashboard": window(windows::dashboard::LABEL) },
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
