//! Settings' "Nouride's files": one zip of the Nouride this app installed (`spec/backup.md`), and what the section says
//! at each step. Android's `BackupLooks`, with its tests.

use serde::Serialize;
use shared::Where;

/// Where the zip is.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default, Serialize)]
#[serde(rename_all = "snake_case")]
pub enum Step {
  #[default]
  Idle,
  /// Nouride takes its database's snapshot.
  Snapshot,
  /// The zip is written.
  Saving,
  Saved,
  /// Not enough room in Downloads.
  NoRoom,
  /// Nouride took no snapshot of its database, so there is no zip.
  NoSnapshot,
  Failed,
}

impl Step {
  pub const ALL: [Step; 7] = [Step::Idle, Step::Snapshot, Step::Saving, Step::Saved, Step::NoRoom, Step::NoSnapshot, Step::Failed];

  pub fn busy(self) -> bool {
    matches!(self, Step::Snapshot | Step::Saving)
  }
}

/// What the app knows of the zip: its size once counted, whether the secrets go in sealed, and how far it is.
#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct Backup {
  pub size: Option<u64>,
  pub sealed: bool,
  pub step: Step,
  pub done: u64,
  pub total: u64,
  /// The name the last zip got in Downloads.
  pub saved: Option<String>,
}

/// The section as the screen draws it. `size` and `result` may hold `{size}`, `{percent}` and `{name}`.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct BackupLook {
  pub shown: bool,
  pub secrets: &'static str,
  pub size_words: &'static str,
  pub size: Option<u64>,
  pub button: bool,
  pub bar: bool,
  pub percent: Option<u8>,
  pub result: Option<&'static str>,
  pub name: Option<String>,
}

/// Only for the Nouride this app installed on this computer: on another computer its files are there, and a Nouride
/// installed by hand keeps its folder somewhere this app does not look.
pub fn shown(place: Option<Where>, ours: bool) -> bool {
  place == Some(Where::Here) && ours
}

pub fn secrets(sealed: bool) -> &'static str {
  if sealed {
    "backup_sealed"
  } else {
    "backup_unsealed"
  }
}

/// The line under the button: where it is, or what happened last.
pub fn result(step: Step) -> Option<&'static str> {
  match step {
    Step::Idle => None,
    Step::Snapshot => Some("backup_snapshot"),
    Step::Saving => Some("backup_saving"),
    Step::Saved => Some("backup_saved"),
    Step::NoRoom => Some("backup_no_room"),
    Step::NoSnapshot => Some("backup_no_snapshot"),
    Step::Failed => Some("backup_failed"),
  }
}

/// How far the zip is, while it is written.
pub fn percent(backup: &Backup) -> Option<u8> {
  (backup.step == Step::Saving).then(|| if backup.total == 0 { 0 } else { (backup.done.min(backup.total) * 100 / backup.total) as u8 })
}

pub fn look(backup: &Backup, place: Option<Where>, ours: bool) -> BackupLook {
  BackupLook {
    shown: shown(place, ours),
    secrets: secrets(backup.sealed),
    size_words: if backup.size.is_some() { "backup_size" } else { "backup_counting" },
    size: backup.size,
    // Pressed only once it is counted, and never again while a zip is made.
    button: backup.size.is_some() && !backup.step.busy(),
    bar: backup.step.busy(),
    percent: percent(backup),
    result: result(backup.step),
    name: backup.saved.clone(),
  }
}

#[cfg(test)]
mod tests {
  use super::*;

  fn at(step: Step) -> Backup {
    Backup { size: Some(10), step, ..Backup::default() }
  }

  #[test]
  fn only_the_nouride_this_app_installed_here() {
    assert!(shown(Some(Where::Here), true));
    assert!(!shown(Some(Where::Remote), true));
    assert!(!shown(Some(Where::Here), false));
    assert!(!shown(None, true));
  }

  #[test]
  fn every_step_but_the_first_says_where_it_is_or_what_happened() {
    for step in Step::ALL {
      assert_eq!(result(step).is_none(), step == Step::Idle, "{step:?}");
    }
  }

  #[test]
  fn nothing_to_press_until_it_is_counted_nor_while_a_zip_is_made() {
    assert!(!look(&Backup::default(), Some(Where::Here), true).button);
    for step in Step::ALL {
      assert_eq!(look(&at(step), Some(Where::Here), true).button, !step.busy(), "{step:?}");
    }
  }

  #[test]
  fn the_bar_moves_only_while_it_works_with_a_number_only_while_the_zip_is_written() {
    assert_eq!(Step::ALL.iter().filter(|s| s.busy()).count(), 2);
    assert_eq!(percent(&at(Step::Snapshot)), None);
    assert_eq!(percent(&Backup { done: 25, total: 100, ..at(Step::Saving) }), Some(25));
    assert_eq!(percent(&Backup { done: 120, total: 100, ..at(Step::Saving) }), Some(100));
    assert_eq!(percent(&Backup { done: 5, total: 0, ..at(Step::Saving) }), Some(0));
  }

  #[test]
  fn the_secrets_are_said_as_they_are() {
    assert_eq!(secrets(true), "backup_sealed");
    assert_eq!(secrets(false), "backup_unsealed");
    assert_eq!(look(&Backup::default(), None, true).size_words, "backup_counting");
    assert_eq!(look(&at(Step::Idle), None, true).size_words, "backup_size");
  }
}
