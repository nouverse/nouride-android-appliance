//! The dashboard in a window of the app: signed in with the app's account on this computer's own Nouride, its own
//! login elsewhere; its user agent marked so the dashboard draws its back button; `link_policy` deciding what stays;
//! a file it downloads kept in Downloads as `downloads` says, with a line at the foot of the page.

use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex};

use downloads::{Source, LINE_SCRIPT};
use link_policy::{Link, NewWindow, NEW_TABS_SCRIPT, USER_AGENT_MARK};
use shared::Where;
use tauri::webview::{Cookie, DownloadEvent, NewWindowResponse};
use tauri::{AppHandle, Manager, Webview, WebviewUrl, WebviewWindow, WebviewWindowBuilder};
use tauri_plugin_opener::OpenerExt;
use url::Url;

use crate::applog;
use crate::state::App;
use crate::windows;

pub const LABEL: &str = "dashboard";

/// WebKit's own user agent on a Mac, with the app's mark: the dashboard reads `NourideHome/`.
fn user_agent() -> String {
  format!("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) {USER_AGENT_MARK}")
}

/// The app's own pages in this window (the "isn't answering" page) are allowed; they are not Nouride's.
fn own_page(url: &Url) -> bool {
  matches!(url.scheme(), "tauri") || url.host_str() == Some("tauri.localhost")
}

/// Sends a page to the person's default browser. The app's log names its host only: a sign-in's address carries its
/// state. A Dev build under an e2e run (`<data>/e2e/no-browser`) logs it and opens nothing, as the browser is on the
/// person's screen.
fn to_browser(handle: &AppHandle, app: &App, url: &Url, what: &str) {
  let host = url.host_str().unwrap_or("");
  #[cfg(feature = "dev")]
  if app.layout.data.join("e2e/no-browser").exists() {
    applog::write(&app.layout.app_log(), &format!("{what} would go to the browser: {host} (e2e, no browser)"));
    return;
  }
  match handle.opener().open_url(url.as_str(), None::<&str>) {
    Ok(()) => applog::write(&app.layout.app_log(), &format!("{what} went to the browser: {host}")),
    Err(e) => applog::write(&app.layout.app_log(), &format!("{what} could not open the browser ({host}): {e}")),
  }
}

/// Opens the dashboard, or the cat and Try again when Nouride does not answer.
pub fn open(handle: &AppHandle, app: &Arc<App>) {
  let (url, origin) = app.dashboard();
  let remote = app.where_() == Some(Where::Remote);
  let reachable = if remote {
    crate::poller::fetch(&place::remote_reply::health_url(&origin)).http_code != 0
  } else {
    crate::poller::fetch(&app.build.health_url()).nouride_answered()
  };
  let window = match handle.get_webview_window(LABEL) {
    Some(w) => w,
    None => match build(handle, app) {
      Ok(w) => w,
      Err(e) => {
        applog::write(&app.layout.app_log(), &format!("dashboard window: {e}"));
        return;
      }
    },
  };
  windows::apply_appearance(handle, app.appearance());
  if !reachable {
    let _ = window.navigate(Url::parse("tauri://localhost/index.html#/unreachable").expect("a fixed URL"));
  } else {
    if !remote && app.ours() {
      sign_in(app, &window);
    }
    if let Ok(target) = Url::parse(&url) {
      let _ = window.navigate(target);
    }
  }
  let _ = window.show();
  let _ = window.set_focus();
}

/// Made each time it is opened after a close, which destroys it with its WebKit processes (the app's session cookie
/// stays in WebKit's store, and `open` signs in again). It may stay open across a change of place, so which Nouride
/// is "here" is read at each navigation, never kept from when the window was made: kept, the other computer's
/// dashboard went to the browser.
fn build(handle: &AppHandle, app: &Arc<App>) -> tauri::Result<WebviewWindow> {
  let nav_handle = handle.clone();
  let nav_app = app.clone();
  let new_handle = handle.clone();
  let new_app = app.clone();
  let download_handle = handle.clone();
  let download_app = app.clone();
  // Each download's name, from its request to its end: on macOS the end does not say where the file went.
  let names: Arc<Mutex<HashMap<String, String>>> = Arc::default();
  WebviewWindowBuilder::new(handle, LABEL, WebviewUrl::App("index.html#/opening".into()))
    .title(crate::words::t("dashboard_title"))
    .inner_size(1200.0, 800.0)
    .min_inner_size(720.0, 520.0)
    .user_agent(&user_agent())
    // WebKit drops a click on a `target="_blank"` link before either handler below sees it: this makes it a
    // `window.open`, which `on_new_window` does see.
    .initialization_script(NEW_TABS_SCRIPT)
    .initialization_script(LINE_SCRIPT)
    .on_navigation(move |url| {
      if own_page(url) || url.as_str() == "about:blank" {
        return true;
      }
      match link_policy::link(url.as_str(), true, &nav_app.dashboard().1) {
        Link::Here => true,
        Link::Back => {
          let h = nav_handle.clone();
          std::thread::spawn(move || {
            if let Some(w) = h.get_webview_window(LABEL) {
              let _ = w.close();
            }
            windows::main::show(&h);
          });
          false
        }
        Link::Outside => {
          to_browser(&nav_handle, &nav_app, url, "a link outside Nouride");
          false
        }
      }
    })
    // A page asked for in a new window: Nouride's own opens here (a redirect out of it is caught above), anything
    // else in the browser. No second window is ever made.
    .on_new_window(move |url, _features| {
      match link_policy::new_window(url.as_str(), &new_app.dashboard().1) {
        NewWindow::Here => {
          applog::write(&new_app.layout.app_log(), &format!("a new window for Nouride's own {} opens in its window", url.path()));
          if let Some(w) = new_handle.get_webview_window(LABEL) {
            let _ = w.navigate(url);
          }
        }
        NewWindow::Browser => to_browser(&new_handle, &new_app, &url, "a new window's page"),
      }
      NewWindowResponse::Deny
    })
    // A file the dashboard downloads (System > Files, an attachment, "Download all"'s blob): Nouride's own goes to
    // Downloads under its own name, a second copy numbered; another machine's goes to the browser, so the dashboard's
    // session never goes with it.
    .on_download(move |webview, event| match event {
      DownloadEvent::Requested { url, destination } => {
        match downloads::source(url.as_str(), &download_app.dashboard().1) {
          Source::Save => {
            let target = keep_in_downloads(destination.as_path());
            let name = target.file_name().and_then(|n| n.to_str()).unwrap_or(downloads::FALLBACK).to_owned();
            *destination = target;
            line(&webview, "download_started", &name);
            names.lock().unwrap_or_else(|e| e.into_inner()).insert(url.to_string(), name);
            true
          }
          Source::Outside => {
            to_browser(&download_handle, &download_app, &url, "a download outside Nouride");
            false
          }
          Source::Refused => {
            applog::write(&download_app.layout.app_log(), &format!("a download refused: {} address", url.scheme()));
            false
          }
        }
      }
      DownloadEvent::Finished { url, success, .. } => {
        let name = names.lock().unwrap_or_else(|e| e.into_inner()).remove(url.as_str()).unwrap_or_else(|| downloads::FALLBACK.to_owned());
        line(&webview, if success { "download_saved" } else { "download_failed" }, &name);
        applog::write(&download_app.layout.app_log(), &format!("a download {}", if success { "was kept" } else { "failed" }));
        true
      }
      _ => true,
    })
    .build()
}

/// Where a download goes: the folder WebKit chose (the person's Downloads), under the name the page or the server gave
/// it, made safe, never over a file there.
fn keep_in_downloads(chosen: &Path) -> PathBuf {
  let dir = chosen.parent().map(PathBuf::from).unwrap_or_default();
  let suggested = chosen.file_name().and_then(|n| n.to_str()).unwrap_or_default();
  let original = downloads::before_webkit_numbering(suggested, |n| dir.join(n).exists());
  downloads::free_path(&dir, &downloads::safe(&original))
}

/// The line at the foot of the dashboard's page.
fn line(webview: &Webview, key: &str, name: &str) {
  let _ = webview.eval(downloads::line_call(&crate::words::t(key).replace("{name}", name)));
}

/// Signs in with the app's account and hands the session to the window. A 401 means the person chose their own
/// username and password: the app forgets its own, and their session or the login page takes over.
fn sign_in(app: &App, window: &WebviewWindow) {
  let Some(password) = daemon::setup::kept_password(&app.layout) else { return };
  let answer = daemon::first_admin::sign_in(&app.build.login_url(), &password);
  if daemon::first_admin::forgets(answer.code) {
    let _ = daemon::setup::forget_password(&app.layout);
    applog::write(&app.layout.app_log(), "dashboard sign-in: 401, the app's password is forgotten");
    return;
  }
  let Some(pair) = answer.cookie else {
    applog::write(&app.layout.app_log(), &format!("dashboard sign-in: HTTP {}, no session", answer.code));
    return;
  };
  let Some((name, value)) = pair.split_once('=') else { return };
  let cookie = Cookie::build((name.to_owned(), value.to_owned())).domain("127.0.0.1").path("/").http_only(true).build();
  match window.set_cookie(cookie) {
    Ok(()) => applog::write(&app.layout.app_log(), "dashboard sign-in: session handed to the window"),
    Err(e) => applog::write(&app.layout.app_log(), &format!("dashboard sign-in: could not set the session: {e}")),
  }
}
