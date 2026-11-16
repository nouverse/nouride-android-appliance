//! A newer Nouride this computer's Nouride can put in, as last heard from its update route: what Home's card, the
//! tray, More's dot and its Versions line say. Asked when the app opens (15 minutes apart at most) and twice a day
//! while it runs, with Nouride's own few minutes of cache. Kept until an answer says otherwise, as no answer is not
//! "up to date"; dropped by itself once the Nouride running is that version or newer. Android 0.2.0's `NourideNotice`.

use std::io;

use shared::prefs::Prefs;
use shared::version;

use crate::NourideStatus;

/// When the app opens, at most this often.
pub const ON_OPEN_MS: u64 = 15 * 60 * 1000;
/// While it runs.
pub const TWICE_A_DAY_MS: u64 = 12 * 60 * 60 * 1000;

const READY: &str = "nouride_update_ready";
const CHECKED_AT: &str = "nouride_update_checked_at";

/// The version worth a notice in `status`: a newer one this install can put in by itself. Pure.
pub fn notice_for(status: &NourideStatus) -> Option<String> {
  status.ready.clone().filter(|_| status.can_update)
}

/// `ready` while the Nouride running (`running`, `None` when not known) is older than it. Pure.
pub fn still_waiting(ready: Option<&str>, running: Option<&str>) -> Option<String> {
  let ready = ready?;
  match running {
    None => Some(ready.to_owned()),
    Some(running) if version::newer(ready, running.strip_prefix('v').unwrap_or(running)) => Some(ready.to_owned()),
    Some(_) => None,
  }
}

/// Whether to ask Nouride again: the last answer is older than `max_age_ms`. Pure.
pub fn due(now_ms: u64, checked_at_ms: u64, max_age_ms: u64) -> bool {
  now_ms.saturating_sub(checked_at_ms) >= max_age_ms
}

pub struct NoticeStore {
  prefs: Prefs,
}

impl NoticeStore {
  pub fn new(prefs: Prefs) -> NoticeStore {
    NoticeStore { prefs }
  }

  /// The version waiting, held against the version running now.
  pub fn waiting(&self, running: Option<&str>) -> Option<String> {
    still_waiting(self.prefs.get(READY).as_deref(), running)
  }

  pub fn checked_at(&self) -> u64 {
    self.prefs.get_u64(CHECKED_AT)
  }

  /// Keeps what `status` says, unless it could not ask: then the last answer stands.
  pub fn remember(&self, status: &NourideStatus, now_ms: u64) -> io::Result<()> {
    if status.check_failed {
      return Ok(());
    }
    let now = now_ms.to_string();
    self.prefs.set(&[(READY, notice_for(status).as_deref()), (CHECKED_AT, Some(&now))])
  }
}

#[cfg(test)]
mod tests {
  use super::*;
  use crate::{Download, DownloadStatus};

  fn status(ready: Option<&str>, can_update: bool, check_failed: bool) -> NourideStatus {
    NourideStatus {
      current: Some("0.8.38".into()),
      ready: ready.map(str::to_owned),
      can_update,
      check_failed,
      download: Download { status: DownloadStatus::Idle, received: 0, total: None },
    }
  }

  #[test]
  fn a_newer_nouride_this_install_can_put_in_is_worth_a_notice() {
    assert_eq!(notice_for(&status(Some("0.8.39"), true, false)).as_deref(), Some("0.8.39"));
  }

  #[test]
  fn none_when_it_is_up_to_date_or_cannot_update_itself() {
    assert_eq!(notice_for(&status(None, true, false)), None);
    assert_eq!(notice_for(&status(Some("0.8.39"), false, false)), None);
  }

  #[test]
  fn the_notice_goes_once_the_nouride_running_has_that_version_or_a_newer_one() {
    assert_eq!(still_waiting(Some("0.8.39"), Some("0.8.38")).as_deref(), Some("0.8.39"));
    assert_eq!(still_waiting(Some("0.8.39"), Some("0.8.39")), None);
    assert_eq!(still_waiting(Some("0.8.39"), Some("0.8.40")), None);
    assert_eq!(still_waiting(Some("0.8.39"), Some("v0.8.38")).as_deref(), Some("0.8.39"));
    assert_eq!(still_waiting(Some("0.8.39"), None).as_deref(), Some("0.8.39"));
    assert_eq!(still_waiting(None, Some("0.8.38")), None);
  }

  #[test]
  fn asked_again_only_once_the_last_answer_is_old() {
    assert!(!due(10 * 60 * 1000, 0, ON_OPEN_MS));
    assert!(due(15 * 60 * 1000, 0, ON_OPEN_MS));
  }

  #[test]
  fn a_failed_check_keeps_the_last_answer() {
    let dir = std::env::temp_dir().join(format!("updates-notice-{}", std::process::id()));
    let _ = std::fs::remove_dir_all(&dir);
    let store = NoticeStore::new(Prefs::new(dir.join("prefs.json")));
    store.remember(&status(Some("0.8.39"), true, false), 1_000).unwrap();
    store.remember(&status(None, true, true), 2_000).unwrap();
    assert_eq!(store.waiting(Some("0.8.38")).as_deref(), Some("0.8.39"));
    assert_eq!(store.checked_at(), 1_000);
    store.remember(&status(None, true, false), 3_000).unwrap();
    assert_eq!(store.waiting(Some("0.8.38")), None);
  }
}
