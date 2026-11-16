//! The app's own window. It lives while it is open: closing it destroys it, and its WebKit processes with it, while
//! the app stays in the menu bar (Nouride keeps running either way). Whatever shows it makes it again when it is gone,
//! so it is looked up by its label each time, never kept.

use std::sync::Arc;

use tauri::{AppHandle, Emitter, Manager, WebviewUrl, WebviewWindow, WebviewWindowBuilder};

use crate::applog;
use crate::state::App;
use crate::windows;

pub const LABEL: &str = "main";

/// Brings the window forward as it is, or makes it at Home.
pub fn show(handle: &AppHandle) {
  reveal(handle, None);
}

/// Shows the window at a screen: the menu bar's update notice opens Versions and updates.
pub fn show_at(handle: &AppHandle, screen: &str) {
  reveal(handle, Some(screen));
}

fn reveal(handle: &AppHandle, screen: Option<&str>) {
  let window = match handle.get_webview_window(LABEL) {
    Some(w) => w,
    None => match create(handle, screen.unwrap_or("home")) {
      Ok(w) => w,
      Err(e) => {
        if let Some(app) = handle.try_state::<Arc<App>>() {
          applog::write(&app.layout.app_log(), &format!("the app's window could not be made: {e}"));
        }
        return;
      }
    },
  };
  let _ = window.unminimize();
  let _ = window.show();
  let _ = window.set_focus();
  if let Some(screen) = screen {
    let _ = handle.emit_to(LABEL, "screen", screen);
  }
}

/// Makes the window as the app's config describes it (size, title bar), at the screen asked for, in the appearance
/// chosen in Settings.
fn create(handle: &AppHandle, screen: &str) -> Result<WebviewWindow, String> {
  let mut config = handle.config().app.windows.iter().find(|w| w.label == LABEL).cloned().ok_or("no window in the config")?;
  config.url = WebviewUrl::App(looks::window::main_page(screen).into());
  let window = WebviewWindowBuilder::from_config(handle, &config).and_then(|b| b.build()).map_err(|e| e.to_string())?;
  if let Some(app) = handle.try_state::<Arc<App>>() {
    windows::apply_appearance(handle, app.appearance());
  }
  Ok(window)
}
