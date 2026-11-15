//! Where a waiting update is said outside its own card: the menu bar, More's dot and its Versions line. Nouride Home's
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

/// More's Versions line when something waits there, or `None` for the versions themselves.
pub fn versions_row(app: bool, nouride: bool) -> Option<&'static str> {
  match (app, nouride) {
    (true, true) => Some("versions_row_updates"),
    (true, false) => Some("versions_row_update"),
    (false, true) => Some("versions_row_nouride_update"),
    (false, false) => None,
  }
}

/// The dot on More: an update waiting in Versions. A computer has no battery setting to fix, as a phone has.
pub fn more_dot(app: bool, nouride: bool) -> bool {
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
  fn more_carries_the_dot_while_either_waits() {
    assert!(more_dot(false, true) && more_dot(true, false));
    assert!(!more_dot(false, false));
  }
}
