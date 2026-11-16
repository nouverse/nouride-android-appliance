pub mod dashboard;
pub mod main;

use tauri::{AppHandle, Manager, Theme};

use crate::state::Appearance;

/// Applies the appearance chosen on More to every window: the screens and the dashboard follow it through
/// `prefers-color-scheme`.
pub fn apply_appearance(handle: &AppHandle, appearance: Appearance) {
  let theme = match appearance {
    Appearance::System => None,
    Appearance::Light => Some(Theme::Light),
    Appearance::Dark => Some(Theme::Dark),
  };
  for window in handle.webview_windows().values() {
    let _ = window.set_theme(theme);
  }
}
