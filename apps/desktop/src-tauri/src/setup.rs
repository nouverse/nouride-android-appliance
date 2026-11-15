//! Runs `daemon::setup` on a thread of its own, with the app's LaunchAgent as its supervisor, and says how far it is.

use std::sync::Arc;

use daemon::progress::SetupProgress;
use tauri::AppHandle;

use crate::applog;
use crate::poller;
use crate::state::App;

struct Reporter<'a> {
  handle: &'a AppHandle,
  app: &'a App,
}

impl daemon::setup::Report for Reporter<'_> {
  fn progress(&self, progress: &SetupProgress) {
    self.app.inner.lock().unwrap().progress = Some(progress.clone());
    poller::send(self.handle, self.app);
  }

  fn log(&self, line: &str) {
    applog::write(&self.app.layout.installer_log(), &daemon::installer::strip_ansi(line));
  }
}

/// The first setup. Does nothing while one runs already. `version` is the e2e hook's pin; `None` everywhere else.
pub fn first(handle: AppHandle, app: Arc<App>, version: Option<String>) {
  {
    let mut inner = app.inner.lock().unwrap();
    if inner.installing {
      return;
    }
    inner.installing = true;
    inner.install_failed = false;
    inner.tracker.restart();
  }
  poller::send(&handle, &app);
  std::thread::spawn(move || {
    applog::write(&app.layout.app_log(), "first setup: start");
    let inherited: Vec<(String, String)> = std::env::vars().collect();
    let agent = |plist: &str| service::Supervisor::install_and_start(&app.agent, plist);
    let fx = daemon::setup::System { install_agent: &agent, health_url: app.build.health_url() };
    let result =
      daemon::setup::run(&app.build, &app.layout, version.as_deref(), &inherited, &fx, &Reporter { handle: &handle, app: &app });
    {
      let mut inner = app.inner.lock().unwrap();
      inner.installing = false;
      inner.progress = None;
      if let Err(e) = &result {
        inner.install_failed = !app.layout.installed();
        applog::write(&app.layout.app_log(), &format!("first setup failed: {e}"));
      } else {
        applog::write(&app.layout.app_log(), "first setup: Nouride answers");
      }
    }
    poller::tick(&handle, &app, &mut 0);
  });
}
