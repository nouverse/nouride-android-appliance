//! The app's own window. Closing it keeps the app in the menu bar; Nouride keeps running either way.

use tauri::{AppHandle, Emitter, Manager};

pub const LABEL: &str = "main";

pub fn show(handle: &AppHandle) {
  if let Some(window) = handle.get_webview_window(LABEL) {
    let _ = window.unminimize();
    let _ = window.show();
    let _ = window.set_focus();
  }
}

/// Shows the window at a screen: the menu bar's update notice opens Versions and updates.
pub fn show_at(handle: &AppHandle, screen: &str) {
  show(handle);
  let _ = handle.emit_to(LABEL, "screen", screen);
}
