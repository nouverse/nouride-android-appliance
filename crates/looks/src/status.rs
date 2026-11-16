use serde::Serialize;
use shared::{Phase, Reply};

/// The cat's pose, each meaning what it means in the dashboard: waving to greet and while turning off; riding its
/// scooter while something is on its way; a thumbs up when it went well; sad beside an unplugged cable when something
/// failed.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "snake_case")]
pub enum Mascot {
  Hello,
  Ride,
  Thumbs,
  Unplugged,
}

/// The colour of the card around the cat.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "snake_case")]
pub enum Tone {
  Calm,
  Busy,
  Good,
  Warn,
}

/// The one big button, or none while the app is busy and there is nothing to press.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "snake_case")]
pub enum Action {
  Start,
  Open,
  Retry,
  None,
}

/// Which sentence the screen says: `<key>_title` and `<key>_body` in the words.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "snake_case")]
pub enum Words {
  Off,
  SettingUp,
  Starting,
  Waking,
  Running,
  Restarting,
  Stopping,
  Failed,
  InstallFailed,
  AdoptedRunning,
  AdoptedDown,
  RemoteChecking,
  RemoteRunning,
  RemoteDown,
  RemoteUnsure,
}

impl Words {
  pub const ALL: [Words; 15] = [
    Words::Off,
    Words::SettingUp,
    Words::Starting,
    Words::Waking,
    Words::Running,
    Words::Restarting,
    Words::Stopping,
    Words::Failed,
    Words::InstallFailed,
    Words::AdoptedRunning,
    Words::AdoptedDown,
    Words::RemoteChecking,
    Words::RemoteRunning,
    Words::RemoteDown,
    Words::RemoteUnsure,
  ];

  /// The key its title and body start with.
  pub fn key(self) -> &'static str {
    match self {
      Words::Off => "off",
      Words::SettingUp => "setting_up",
      Words::Starting => "starting",
      Words::Waking => "waking",
      Words::Running => "running",
      Words::Restarting => "restarting",
      Words::Stopping => "stopping",
      Words::Failed => "failed",
      Words::InstallFailed => "install_failed",
      Words::AdoptedRunning => "adopted_running",
      Words::AdoptedDown => "adopted_down",
      Words::RemoteChecking => "remote_checking",
      Words::RemoteRunning => "remote_running",
      Words::RemoteDown => "remote_down",
      Words::RemoteUnsure => "remote_unsure",
    }
  }

  pub fn title(self) -> String {
    format!("{}_title", self.key())
  }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
pub struct Look {
  pub mascot: Mascot,
  pub tone: Tone,
  pub words: Words,
  pub action: Action,
  pub busy: bool,
}

const fn look(mascot: Mascot, tone: Tone, words: Words, action: Action, busy: bool) -> Look {
  Look { mascot, tone, words, action, busy }
}

/// Nouride on this computer, installed by the app. Running is only "ready" once Nouride answers `ok`: the process can
/// be up before its web server is.
pub fn of(phase: Phase, answering: bool) -> Look {
  use Action as A;
  use Mascot as M;
  use Tone as T;
  match phase {
    Phase::Stopped => look(M::Hello, T::Calm, Words::Off, A::Start, false),
    Phase::Installing => look(M::Ride, T::Busy, Words::SettingUp, A::None, true),
    Phase::Starting => look(M::Ride, T::Busy, Words::Starting, A::None, true),
    Phase::Running if answering => look(M::Thumbs, T::Good, Words::Running, A::Open, false),
    Phase::Running => look(M::Ride, T::Busy, Words::Waking, A::None, true),
    Phase::Restarting => look(M::Ride, T::Busy, Words::Restarting, A::None, true),
    Phase::Stopping => look(M::Hello, T::Calm, Words::Stopping, A::None, true),
    Phase::Failed => look(M::Unplugged, T::Warn, Words::Failed, A::Retry, false),
  }
}

/// The first setup did not finish (the installer failed): Try again runs it again.
pub fn setup_failed() -> Look {
  look(Mascot::Unplugged, Tone::Warn, Words::InstallFailed, Action::Retry, false)
}

/// A Nouride that was on this computer before the app, which the app opens and leaves to whoever installed it.
pub fn adopted(answers: bool) -> Look {
  if answers {
    look(Mascot::Thumbs, Tone::Good, Words::AdoptedRunning, Action::Open, false)
  } else {
    look(Mascot::Unplugged, Tone::Warn, Words::AdoptedDown, Action::Retry, false)
  }
}

/// Nouride on another computer, by what its `/health` said (`None` while this computer is still asking). Open is
/// offered whenever it may work, and only a computer that does not answer at all asks for Try again.
pub fn remote(reply: Option<Reply>) -> Look {
  use Action as A;
  use Mascot as M;
  use Tone as T;
  match reply {
    None => look(M::Hello, T::Calm, Words::RemoteChecking, A::Open, false),
    Some(Reply::Answers) => look(M::Thumbs, T::Good, Words::RemoteRunning, A::Open, false),
    Some(Reply::Down) => look(M::Unplugged, T::Warn, Words::RemoteDown, A::Retry, false),
    Some(Reply::Unsure) => look(M::Hello, T::Calm, Words::RemoteUnsure, A::Open, false),
  }
}

/// Whether Turn off is offered: whenever Nouride is on or on its way, never when it is off already.
pub fn can_turn_off(phase: Phase) -> bool {
  !matches!(phase, Phase::Stopped | Phase::Stopping | Phase::Failed | Phase::Installing)
}

/// Whether Open, and Connect's address, are offered: only when there is something to reach.
pub fn can_share(look: &Look) -> bool {
  look.action == Action::Open
}

#[cfg(test)]
mod tests {
  use super::*;

  const PHASES: [Phase; 7] =
    [Phase::Stopped, Phase::Installing, Phase::Starting, Phase::Running, Phase::Restarting, Phase::Stopping, Phase::Failed];

  #[test]
  fn every_phase_has_a_sentence_and_at_most_one_button() {
    for phase in PHASES {
      for answering in [true, false] {
        let l = of(phase, answering);
        // Busy states have nothing to press.
        if l.busy {
          assert_eq!(l.action, Action::None, "{phase:?}");
        }
      }
    }
  }

  #[test]
  fn running_is_only_ready_once_nouride_answers() {
    assert_eq!(of(Phase::Running, true).action, Action::Open);
    assert_eq!(of(Phase::Running, true).mascot, Mascot::Thumbs);
    let waking = of(Phase::Running, false);
    assert_eq!((waking.words, waking.action, waking.busy), (Words::Waking, Action::None, true));
  }

  #[test]
  fn off_offers_start_and_a_failure_offers_try_again_with_the_unplugged_cat() {
    assert_eq!(of(Phase::Stopped, false).action, Action::Start);
    let failed = of(Phase::Failed, false);
    assert_eq!((failed.mascot, failed.tone, failed.action), (Mascot::Unplugged, Tone::Warn, Action::Retry));
    assert_eq!(setup_failed().action, Action::Retry);
  }

  #[test]
  fn setting_up_and_starting_ride_the_scooter() {
    for phase in [Phase::Installing, Phase::Starting, Phase::Restarting] {
      assert_eq!(of(phase, false).mascot, Mascot::Ride);
    }
  }

  #[test]
  fn another_computer_offers_open_whenever_it_may_work_and_try_again_only_when_nothing_answers() {
    assert_eq!(remote(None).action, Action::Open);
    assert_eq!(remote(Some(Reply::Answers)).words, Words::RemoteRunning);
    assert_eq!(remote(Some(Reply::Unsure)).action, Action::Open);
    let down = remote(Some(Reply::Down));
    assert_eq!((down.action, down.mascot), (Action::Retry, Mascot::Unplugged));
  }

  #[test]
  fn turn_off_only_while_nouride_is_on_or_on_its_way() {
    assert!(can_turn_off(Phase::Running) && can_turn_off(Phase::Starting) && can_turn_off(Phase::Restarting));
    assert!(!can_turn_off(Phase::Stopped) && !can_turn_off(Phase::Stopping) && !can_turn_off(Phase::Failed));
    assert!(!can_turn_off(Phase::Installing));
  }

  #[test]
  fn a_nouride_already_here_is_opened_and_one_that_stopped_answering_says_so() {
    assert_eq!(adopted(true).action, Action::Open);
    assert_eq!(adopted(false).words, Words::AdoptedDown);
    assert!(can_share(&adopted(true)) && !can_share(&adopted(false)));
  }
}
