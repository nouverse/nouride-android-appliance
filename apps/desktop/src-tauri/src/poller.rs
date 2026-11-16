//! Keeps the state fresh: this computer's Nouride (its LaunchAgent and `/health`), the other computer's `/health`, the
//! network address, and Nouride's update notice. Each change is sent to the screens and redraws the menu bar.

use std::sync::Arc;
use std::time::Duration;

use shared::{Health, Where};
use tauri::{AppHandle, Emitter, Manager};

use crate::applog::now_ms;
use crate::state::App;
use crate::{tray, versions};

/// How often, while the window is open; and while only the menu bar is.
const SHOWN: Duration = Duration::from_secs(2);
const HIDDEN: Duration = Duration::from_secs(10);
const REMOTE_EVERY: u64 = 30_000;

pub fn start(handle: AppHandle, app: Arc<App>) {
  std::thread::spawn(move || {
    let mut remote_at = 0u64;
    loop {
      tick(&handle, &app, &mut remote_at);
      let shown = handle.get_webview_window("main").and_then(|w| w.is_visible().ok()).unwrap_or(false);
      std::thread::sleep(if shown { SHOWN } else { HIDDEN });
    }
  });
}

/// One look at everything, then the screens and the menu bar are told what changed.
pub fn tick(handle: &AppHandle, app: &App, remote_at: &mut u64) {
  let now = now_ms();
  match app.where_() {
    Some(Where::Remote) => {
      if now.saturating_sub(*remote_at) >= REMOTE_EVERY {
        *remote_at = now;
        check_remote(app);
      }
    }
    _ => {
      let job = if app.ours() { service::Supervisor::job(&app.agent).unwrap_or_default() } else { Default::default() };
      let health = if job.loaded || app.adopted() { Some(fetch(&app.build.health_url())) } else { None };
      app.observe(job, health, now);
      app.inner.lock().unwrap().lan = daemon::lan::address().map(|ip| ip.to_string());
      versions::maybe_check_notice(app, updates::notice::TWICE_A_DAY_MS);
    }
  }
  send(handle, app);
}

pub fn check_remote(app: &App) {
  let reply = app.place().last_address().map(|base| place::remote_reply::of(&fetch(&place::remote_reply::health_url(&base))));
  app.inner.lock().unwrap().remote_reply = reply;
}

pub fn fetch(url: &str) -> Health {
  daemon::health::fetch(url, Duration::from_secs(5))
}

/// Sends the snapshot when it changed, and redraws the menu bar with it.
pub fn send(handle: &AppHandle, app: &App) {
  let snapshot = app.snapshot();
  let json = serde_json::to_string(&snapshot).unwrap_or_default();
  {
    let mut inner = app.inner.lock().unwrap();
    if inner.last_sent == json {
      return;
    }
    inner.last_sent = json;
  }
  let _ = handle.emit("state", &snapshot);
  tray::redraw(handle, app, &snapshot);
}
