//! The phase Home shows for Nouride on this computer, from what the app can see: whether its installer runs or failed,
//! whether its LaunchAgent is loaded, and what `/health` answered. launchd, not the app, keeps Nouride running
//! (`KeepAlive`), so the app observes rather than drives: a Nouride that stops on its own is restarted by launchd, and
//! the app says "starting again" until it answers. Pure, with a clock passed in.

use shared::{Health, Phase};

/// How long a start may take before Home says it could not start. The first boot on a Mac is seconds long; a slow disk
/// or a large agent folder is allowed for.
pub const START_LIMIT_MS: u64 = 120_000;

/// What the app saw, once.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Seen<'a> {
  pub installing: bool,
  pub install_failed: bool,
  /// The app's LaunchAgent is loaded in launchd.
  pub loaded: bool,
  /// A turn-off was asked and is not done.
  pub stopping: bool,
  pub health: Option<&'a Health>,
  pub now_ms: u64,
}

/// The phase, and whether Nouride answers `ok` (Home's "running" against "waking up").
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Now {
  pub phase: Phase,
  pub answering: bool,
}

#[derive(Debug, Clone, Default)]
pub struct Tracker {
  /// Since when it has been loaded, or since the last time it answered, whichever is later.
  waiting_since: Option<u64>,
  /// It answered `ok` since it was loaded: no answer now is a restart, not a first start.
  answered: bool,
}

impl Tracker {
  /// Forget the last start: Try again and Start begin a new one.
  pub fn restart(&mut self) {
    *self = Tracker::default();
  }

  pub fn next(&mut self, seen: Seen) -> Now {
    let off = |phase| Now { phase, answering: false };
    if seen.install_failed {
      return off(Phase::Failed);
    }
    if seen.installing {
      return off(Phase::Installing);
    }
    if !seen.loaded {
      self.restart();
      return off(Phase::Stopped);
    }
    if seen.stopping {
      return off(Phase::Stopping);
    }
    let since = *self.waiting_since.get_or_insert(seen.now_ms);
    match seen.health {
      Some(h) if h.ok() => {
        self.answered = true;
        self.waiting_since = Some(seen.now_ms);
        Now { phase: Phase::Running, answering: true }
      }
      // Nouride's own body that is not `ok`: up, its web server not ready, or degraded.
      Some(h) if h.nouride_answered() => {
        self.waiting_since = Some(seen.now_ms);
        off(Phase::Running)
      }
      _ if seen.now_ms.saturating_sub(since) >= START_LIMIT_MS => off(Phase::Failed),
      _ if self.answered => off(Phase::Restarting),
      _ => off(Phase::Starting),
    }
  }
}

#[cfg(test)]
mod tests {
  use super::*;

  fn seen(loaded: bool, health: Option<&Health>, now_ms: u64) -> Seen<'_> {
    Seen { installing: false, install_failed: false, loaded, stopping: false, health, now_ms }
  }

  #[test]
  fn not_loaded_is_off() {
    let mut t = Tracker::default();
    assert_eq!(t.next(seen(false, None, 0)), Now { phase: Phase::Stopped, answering: false });
  }

  #[test]
  fn the_installer_running_or_failed_wins_over_everything() {
    let mut t = Tracker::default();
    let ok = Health::parse(200, r#"{"status":"ok"}"#);
    assert_eq!(t.next(Seen { installing: true, ..seen(true, Some(&ok), 0) }).phase, Phase::Installing);
    assert_eq!(t.next(Seen { install_failed: true, ..seen(true, Some(&ok), 0) }).phase, Phase::Failed);
  }

  #[test]
  fn loaded_without_an_answer_is_starting_then_running_once_it_answers_ok() {
    let mut t = Tracker::default();
    let none = Health::unreachable("refused");
    assert_eq!(t.next(seen(true, Some(&none), 0)).phase, Phase::Starting);
    let starting = Health::parse(503, r#"{"status":"starting"}"#);
    assert_eq!(t.next(seen(true, Some(&starting), 2_000)), Now { phase: Phase::Running, answering: false });
    let ok = Health::parse(200, r#"{"status":"ok"}"#);
    assert_eq!(t.next(seen(true, Some(&ok), 4_000)), Now { phase: Phase::Running, answering: true });
  }

  #[test]
  fn no_answer_after_one_is_launchd_starting_it_again() {
    let mut t = Tracker::default();
    let ok = Health::parse(200, r#"{"status":"ok"}"#);
    t.next(seen(true, Some(&ok), 0));
    assert_eq!(t.next(seen(true, None, 5_000)).phase, Phase::Restarting);
    assert_eq!(t.next(seen(true, Some(&ok), 15_000)).phase, Phase::Running);
  }

  #[test]
  fn a_start_that_never_answers_fails_after_the_limit_and_not_before() {
    let mut t = Tracker::default();
    assert_eq!(t.next(seen(true, None, 0)).phase, Phase::Starting);
    assert_eq!(t.next(seen(true, None, START_LIMIT_MS - 1)).phase, Phase::Starting);
    assert_eq!(t.next(seen(true, None, START_LIMIT_MS)).phase, Phase::Failed);
    // launchd keeps trying; when it answers, it is running again.
    let ok = Health::parse(200, r#"{"status":"ok"}"#);
    assert_eq!(t.next(seen(true, Some(&ok), START_LIMIT_MS + 10_000)).phase, Phase::Running);
  }

  #[test]
  fn a_restart_that_never_comes_back_fails_too() {
    let mut t = Tracker::default();
    let ok = Health::parse(200, r#"{"status":"ok"}"#);
    t.next(seen(true, Some(&ok), 0));
    assert_eq!(t.next(seen(true, None, START_LIMIT_MS - 1)).phase, Phase::Restarting);
    assert_eq!(t.next(seen(true, None, START_LIMIT_MS)).phase, Phase::Failed);
  }

  #[test]
  fn turning_off_says_so_until_it_is_off_and_then_a_new_start_counts_again() {
    let mut t = Tracker::default();
    t.next(seen(true, None, 0));
    assert_eq!(t.next(Seen { stopping: true, ..seen(true, None, 1_000) }).phase, Phase::Stopping);
    assert_eq!(t.next(seen(false, None, 2_000)).phase, Phase::Stopped);
    assert_eq!(t.next(seen(true, None, START_LIMIT_MS + 5_000)).phase, Phase::Starting);
  }
}
