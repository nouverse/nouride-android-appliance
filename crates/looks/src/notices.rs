//! Where a waiting update is said outside its own card: the menu bar, the dot on Versions and updates, and Home's
//! versions line. Nouride Home's
//! update and Nouride's are said alike. Android's `UpdateNotices`, with the menu bar in place of the notification.

/// The menu bar's line, or `None` when nothing waits.
pub fn tray(app: bool, nouride: bool) -> Option<&'static str> {
  match (app, nouride) {
    (true, true) => Some("tray_updates"),
    (true, false) => Some("tray_update"),
    (false, true) => Some("tray_nouride_update"),
    (false, false) => None,
  }
}

/// Home's versions line when something waits, or `None` for the versions themselves.
pub fn versions_row(app: bool, nouride: bool) -> Option<&'static str> {
  match (app, nouride) {
    (true, true) => Some("versions_row_updates"),
    (true, false) => Some("versions_row_update"),
    (false, true) => Some("versions_row_nouride_update"),
    (false, false) => None,
  }
}

/// The dot on Versions and updates in the sidebar, while an update waits there. A computer has no battery setting to
/// fix, as a phone has.
pub fn versions_dot(app: bool, nouride: bool) -> bool {
  app || nouride
}

#[cfg(test)]
mod tests {
  use super::*;

  #[test]
  fn each_pair_has_its_own_words() {
    assert_eq!(tray(true, true), Some("tray_updates"));
    assert_eq!(tray(true, false), Some("tray_update"));
    assert_eq!(tray(false, true), Some("tray_nouride_update"));
    assert_eq!(tray(false, false), None);
    assert_eq!(versions_row(false, true), Some("versions_row_nouride_update"));
    assert_eq!(versions_row(false, false), None);
  }

  #[test]
  fn versions_carries_the_dot_while_either_waits() {
    assert!(versions_dot(false, true) && versions_dot(true, false));
    assert!(!versions_dot(false, false));
  }
}
