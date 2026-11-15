//! Nouride Home for desktop: the window, the menu bar and the commands, wired to the crates. No business logic here.

#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod applog;
mod commands;
#[cfg(feature = "dev")]
mod e2e;
mod poller;
mod setup;
mod state;
mod tray;
mod versions;
mod windows;
mod words;

use std::sync::Arc;

use daemon::Layout;
use shared::Build;
use tauri::{Manager, RunEvent, WindowEvent};

use crate::state::App;

fn main() {
  let build = Build::current();
  let mut builder = tauri::Builder::default();
  // One app at a time: a second launch brings the first forward (and, in the Dev build, hands it an e2e action).
  builder = builder.plugin(tauri_plugin_single_instance::init(|handle, argv, _cwd| {
    #[cfg(feature = "dev")]
    if argv.iter().any(|a| a == "--e2e") {
      let app = handle.state::<Arc<App>>().inner().clone();
      let handle = handle.clone();
      std::thread::spawn(move || e2e::handle(&handle, &app, &argv));
      return;
    }
    let _ = argv;
    windows::main::show(handle);
  }));
  builder
    .plugin(tauri_plugin_opener::init())
    .plugin(tauri_plugin_clipboard_manager::init())
    .plugin(tauri_plugin_updater::Builder::new().build())
    .plugin(tauri_plugin_deep_link::init())
    .setup(move |tauri_app| {
      let handle = tauri_app.handle().clone();
      // The bundle id the config gave this app must be the build's: a release config with the Dev feature (or the
      // other way round) would put one build's Nouride in the other's folder.
      let identifier = tauri_app.config().identifier.clone();
      if identifier != build.id {
        return Err(format!("this binary is {} but its bundle is {identifier}", build.id).into());
      }
      let data = tauri_app.path().app_data_dir()?;
      let home = tauri_app.path().home_dir()?;
      // SAFETY: getuid has no preconditions and cannot fail.
      let uid = unsafe { libc::getuid() };
      let app = Arc::new(App::new(build, Layout::new(&data), uid, &home));
      applog::write(&app.layout.app_log(), &format!("{} {} started", build.name, env!("CARGO_PKG_VERSION")));
      tauri_app.manage(app.clone());
      windows::apply_appearance(&handle, app.appearance());
      tray::create(&handle, &app)?;
      poller::start(handle.clone(), app.clone());
      // A Nouride the app installed is checked for a new version when the app opens, 15 minutes apart at most.
      let (h, a) = (handle.clone(), app.clone());
      std::thread::spawn(move || {
        std::thread::sleep(std::time::Duration::from_secs(5));
        versions::maybe_check_notice(&a, updates::notice::ON_OPEN_MS);
        poller::send(&h, &a);
      });
      Ok(())
    })
    .on_window_event(|window, event| {
      // Closing the window keeps the app in the menu bar; Nouride keeps running either way.
      if let WindowEvent::CloseRequested { api, .. } = event {
        if window.label() == windows::main::LABEL || window.label() == windows::dashboard::LABEL {
          api.prevent_close();
          let _ = window.hide();
        }
      }
    })
    .invoke_handler(tauri::generate_handler![
      commands::state,
      commands::choose_here,
      commands::choose_remote,
      commands::start,
      commands::retry,
      commands::turn_off,
      commands::open_dashboard,
      commands::set_access,
      commands::set_appearance,
      commands::check_app,
      commands::update_app,
      commands::check_nouride,
      commands::update_nouride,
      commands::install_nouride,
      commands::logs,
      commands::copy,
      commands::qr,
    ])
    .build(tauri::generate_context!())
    .expect("Nouride Home could not start")
    .run(|handle, event| {
      // The Dock icon clicked with no window shown brings the app's window back.
      #[cfg(target_os = "macos")]
      if let RunEvent::Reopen { has_visible_windows: false, .. } = event {
        windows::main::show(handle);
      }
      let _ = (handle, event);
    });
}
