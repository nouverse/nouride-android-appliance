//! The dashboard in a window of the app: signed in with the app's account on this computer's own Nouride, its own
//! login elsewhere; its user agent marked so the dashboard draws its back button; `link_policy` deciding what stays.

use std::sync::Arc;

use link_policy::{Link, USER_AGENT_MARK};
use shared::Where;
use tauri::webview::{Cookie, NewWindowResponse};
use tauri::{AppHandle, Manager, WebviewUrl, WebviewWindow, WebviewWindowBuilder};
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

/// Opens the dashboard, or the cat and Try again when Nouride does not answer.
pub fn open(handle: &AppHandle, app: &Arc<App>) {
  let (url, origin) = app.dashboard();
  let reachable = match app.where_() {
    Some(Where::Remote) => crate::poller::fetch(&place::remote_reply::health_url(&origin)).http_code != 0,
    _ => crate::poller::fetch(&app.build.health_url()).nouride_answered(),
  };
  let window = match handle.get_webview_window(LABEL) {
    Some(w) => w,
    None => match build(handle, app, &origin) {
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
    if app.where_() != Some(Where::Remote) && app.ours() {
      sign_in(app, &window);
    }
    if let Ok(target) = Url::parse(&url) {
      let _ = window.navigate(target);
    }
  }
  let _ = window.show();
  let _ = window.set_focus();
}

fn build(handle: &AppHandle, app: &Arc<App>, origin: &str) -> tauri::Result<WebviewWindow> {
  let nav_handle = handle.clone();
  let nav_origin = origin.to_owned();
  let new_handle = handle.clone();
  let new_origin = origin.to_owned();
  let log = app.layout.app_log();
  WebviewWindowBuilder::new(handle, LABEL, WebviewUrl::App("index.html#/opening".into()))
    .title(crate::words::t("dashboard_title"))
    .inner_size(1200.0, 800.0)
    .min_inner_size(720.0, 520.0)
    .user_agent(&user_agent())
    .on_navigation(move |url| {
      if own_page(url) || url.as_str() == "about:blank" {
        return true;
      }
      match link_policy::link(url.as_str(), true, &nav_origin) {
        Link::Here => true,
        Link::Back => {
          let h = nav_handle.clone();
          std::thread::spawn(move || {
            if let Some(w) = h.get_webview_window(LABEL) {
              let _ = w.hide();
            }
            windows::main::show(&h);
          });
          false
        }
        Link::Outside => {
          applog::write(&log, "a link outside Nouride went to the browser");
          let _ = nav_handle.opener().open_url(url.as_str(), None::<&str>);
          false
        }
      }
    })
    // A link that asks for a new window: Nouride's own page opens here, anything else in the browser.
    .on_new_window(move |url, _features| {
      if link_policy::in_app(url.as_str(), &new_origin) {
        if let Some(w) = new_handle.get_webview_window(LABEL) {
          let _ = w.navigate(url);
        }
      } else {
        let _ = new_handle.opener().open_url(url.as_str(), None::<&str>);
      }
      NewWindowResponse::Deny
    })
    .build()
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
