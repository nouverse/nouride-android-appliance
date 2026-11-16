//! The two update flows behind the Versions screen: Nouride's, through its control-token routes, and the app's own,
//! through Tauri's updater. The steps and words are `updates` and `looks`; this only runs them.

use std::sync::Arc;
use std::time::Duration;

use shared::{AppStep, NourideStep, Phase};
use tauri::AppHandle;
use tauri_plugin_updater::UpdaterExt;
use updates::client::Client;
use updates::stall::{StallWatch, Verdict};
use updates::DownloadStatus;

use crate::applog::{self, now_ms};
use crate::poller;
use crate::state::App;

fn client(app: &App) -> Client {
  Client { origin: app.build.origin(), token_path: app.layout.control_token() }
}

fn running(app: &App) -> bool {
  let inner = app.inner.lock().unwrap();
  inner.phase == Phase::Running && inner.answering
}

fn set_nouride(app: &App, step: NourideStep, ready: Option<String>, percent: u8) {
  let mut inner = app.inner.lock().unwrap();
  inner.nouride_card.step = step;
  if ready.is_some() {
    inner.nouride_card.ready = ready;
  }
  inner.nouride_card.percent = percent;
}

/// Asks Nouride when the last answer is older than `max_age_ms`, as Android's `NourideNotice.check`.
pub fn maybe_check_notice(app: &App, max_age_ms: u64) {
  if !app.ours() || !running(app) {
    return;
  }
  let store = app.notices();
  if !updates::notice::due(now_ms(), store.checked_at(), max_age_ms) {
    return;
  }
  if let Ok(status) = client(app).status(false) {
    let _ = store.remember(&status, now_ms());
    applog::write(&app.layout.app_log(), &format!("nouride update check: ready {:?}", updates::notice::notice_for(&status)));
  }
}

/// Check for updates on Nouride's card: asks the release channel now.
pub fn check_nouride(handle: AppHandle, app: Arc<App>) {
  if !app.ours() || !running(&app) {
    return;
  }
  set_nouride(&app, NourideStep::Checking, None, 0);
  poller::send(&handle, &app);
  std::thread::spawn(move || {
    match client(&app).status(true) {
      Ok(status) => {
        let _ = app.notices().remember(&status, now_ms());
        let step = updates::nouride::step(&status);
        set_nouride(&app, step, status.ready.clone(), status.download.percent());
        if step == NourideStep::Downloading {
          follow_download(&handle, &app, true);
        }
      }
      Err(e) => {
        applog::write(&app.layout.app_log(), &format!("nouride update check failed: {e}"));
        set_nouride(&app, NourideStep::CheckFailed, None, 0);
      }
    }
    poller::send(&handle, &app);
  });
}

/// "Update to x" in one press: the download with its percent, then the install, then Nouride starting again.
pub fn update_nouride(handle: AppHandle, app: Arc<App>) {
  if !app.ours() {
    return;
  }
  std::thread::spawn(move || {
    let c = client(&app);
    let ready = c.status(false).ok().and_then(|s| s.ready);
    set_nouride(&app, NourideStep::Downloading, ready, 0);
    poller::send(&handle, &app);
    if let Err(e) = c.download() {
      applog::write(&app.layout.app_log(), &format!("nouride download failed to start: {e}"));
      set_nouride(&app, NourideStep::DownloadFailed, None, 0);
      poller::send(&handle, &app);
      return;
    }
    if follow_download(&handle, &app, true) {
      install(&handle, &app);
    }
  });
}

/// Install the update already downloaded.
pub fn install_nouride(handle: AppHandle, app: Arc<App>) {
  if !app.ours() {
    return;
  }
  std::thread::spawn(move || install(&handle, &app));
}

/// Follows Nouride's download once a second, restarting it when it stalls; whether it ended ready.
fn follow_download(handle: &AppHandle, app: &App, then_install: bool) -> bool {
  let c = client(app);
  let mut watch = StallWatch::new(30_000, 3);
  loop {
    std::thread::sleep(Duration::from_secs(1));
    let Ok(d) = c.progress() else { continue };
    match d.status {
      DownloadStatus::Ready => {
        set_nouride(app, if then_install { NourideStep::Installing } else { NourideStep::Downloaded }, None, 100);
        poller::send(handle, app);
        return true;
      }
      DownloadStatus::Failed => {
        set_nouride(app, NourideStep::DownloadFailed, None, 0);
        poller::send(handle, app);
        return false;
      }
      _ => {}
    }
    set_nouride(app, NourideStep::Downloading, None, d.percent());
    poller::send(handle, app);
    match watch.seen(d.received, now_ms()) {
      Verdict::Moving => {}
      Verdict::Restart => {
        applog::write(&app.layout.app_log(), "nouride download stalled: pausing and resuming it");
        let _ = c.pause();
        let _ = c.download();
      }
      Verdict::GiveUp => {
        let _ = c.pause();
        set_nouride(app, NourideStep::DownloadFailed, None, 0);
        poller::send(handle, app);
        return false;
      }
    }
  }
}

fn install(handle: &AppHandle, app: &App) {
  set_nouride(app, NourideStep::Installing, None, 100);
  poller::send(handle, app);
  match client(app).install() {
    Ok(restart) => {
      applog::write(&app.layout.app_log(), &format!("nouride update installed, restart {restart:?}"));
      set_nouride(app, updates::nouride::restarting(restart), None, 100);
    }
    Err(e) => {
      applog::write(&app.layout.app_log(), &format!("nouride update install failed: {e}"));
      set_nouride(app, NourideStep::InstallFailed, None, 0);
    }
  }
  poller::send(handle, app);
  // The card follows Nouride back, then says the new version.
  let target = app.inner.lock().unwrap().nouride_card.ready.clone();
  for _ in 0..180 {
    std::thread::sleep(Duration::from_secs(2));
    let version = app.inner.lock().unwrap().health.as_ref().and_then(|h| h.version.clone());
    if let (Some(v), Some(t)) = (version, target.as_ref()) {
      if !shared::version::newer(t, v.trim_start_matches('v')) && running(app) {
        set_nouride(app, NourideStep::UpToDate, None, 0);
        let _ = app.notices().remember(
          &updates::NourideStatus {
            current: Some(v),
            ready: None,
            can_update: true,
            check_failed: false,
            download: updates::Download { status: DownloadStatus::Idle, received: 0, total: None },
          },
          now_ms(),
        );
        poller::send(handle, app);
        return;
      }
    }
  }
}

fn set_app(app: &App, step: AppStep, ready: Option<String>, percent: u8) {
  let mut inner = app.inner.lock().unwrap();
  inner.app_card = crate::state::AppCard { step, ready: ready.or(inner.app_card.ready.clone()), percent };
  if step == AppStep::Ready {
    inner.app_update = inner.app_card.ready.clone();
  }
}

/// The app's own update, from this repository's Releases. The Dev build is never offered one.
pub fn check_app(handle: AppHandle, app: Arc<App>) {
  if app.build.dev {
    return;
  }
  set_app(&app, AppStep::Checking, None, 0);
  poller::send(&handle, &app);
  tauri::async_runtime::spawn(async move {
    let result = match handle.updater() {
      Ok(updater) => updater.check().await.map_err(|e| e.to_string()),
      Err(e) => Err(e.to_string()),
    };
    match result {
      Ok(Some(update)) => set_app(&app, AppStep::Ready, Some(update.version.clone()), 0),
      Ok(None) => set_app(&app, AppStep::UpToDate, None, 0),
      Err(e) => {
        applog::write(&app.layout.app_log(), &format!("app update check failed: {e}"));
        set_app(&app, AppStep::CheckFailed, None, 0);
      }
    }
    poller::send(&handle, &app);
  });
}

pub fn update_app(handle: AppHandle, app: Arc<App>) {
  if app.build.dev {
    return;
  }
  tauri::async_runtime::spawn(async move {
    let Ok(Some(update)) = (match handle.updater() {
      Ok(u) => u.check().await,
      Err(e) => Err(e),
    }) else {
      set_app(&app, AppStep::DownloadFailed, None, 0);
      poller::send(&handle, &app);
      return;
    };
    set_app(&app, AppStep::Downloading, Some(update.version.clone()), 0);
    poller::send(&handle, &app);
    let mut received = 0u64;
    let (h, a) = (handle.clone(), app.clone());
    let done = update
      .download_and_install(
        move |chunk, total| {
          received += chunk as u64;
          let percent = total.filter(|t| *t > 0).map(|t| (received * 100 / t).min(100) as u8).unwrap_or(0);
          set_app(&a, AppStep::Downloading, None, percent);
          poller::send(&h, &a);
        },
        || {},
      )
      .await;
    match done {
      Ok(()) => {
        set_app(&app, AppStep::Installing, None, 100);
        poller::send(&handle, &app);
        handle.restart();
      }
      Err(e) => {
        applog::write(&app.layout.app_log(), &format!("app update failed: {e}"));
        set_app(&app, AppStep::InstallFailed, None, 0);
        poller::send(&handle, &app);
      }
    }
  });
}
