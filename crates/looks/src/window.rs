//! How long the app's windows live. Each lives while it is open: closing it destroys it, and its WebKit processes
//! with it, while the app stays in the menu bar; whatever shows it again makes it again.

/// The page the app's window is made at: Home, or the section the menu bar asked for (`index.html#/versions`). A new
/// window's screens read it on their first draw: the `screen` event that moves an open window would be sent before
/// they listen, and lost.
pub fn main_page(screen: &str) -> String {
  if screen.is_empty() || screen == "home" {
    "index.html".to_owned()
  } else {
    format!("index.html#/{screen}")
  }
}

/// Whether the app goes when it is asked to. Closing its last window asks with no exit code, and the app stays in the
/// menu bar (Nouride keeps running either way); Quit (`AppHandle::exit`) asks with one, and the app goes.
pub fn exits(code: Option<i32>) -> bool {
  code.is_some()
}

#[cfg(test)]
mod tests {
  use super::*;

  #[test]
  fn the_window_is_made_at_home_or_at_the_section_asked_for() {
    assert_eq!(main_page("home"), "index.html");
    assert_eq!(main_page(""), "index.html");
    assert_eq!(main_page("versions"), "index.html#/versions");
    assert_eq!(main_page("settings"), "index.html#/settings");
  }

  #[test]
  fn closing_the_last_window_keeps_the_app_and_quit_ends_it() {
    assert!(!exits(None));
    assert!(exits(Some(0)));
  }
}
