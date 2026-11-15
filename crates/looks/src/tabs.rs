//! Which tab shows what, and where Back goes. Android's `Tabs`, with its tests.

use serde::Serialize;

use crate::{status, Look};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "snake_case")]
pub enum Tab {
  Home,
  Connect,
  More,
}

/// What the Connect tab can offer.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "snake_case")]
pub enum Reach {
  /// Nothing to reach yet.
  Off,
  /// Nouride kept to this computer: its switch is off.
  ThisComputer,
  /// No network to reach it over.
  NoNetwork,
  /// The address and its code.
  Ready,
}

/// The bar is hidden while the first setup runs: that takes minutes and its progress is the one thing to watch.
pub fn bar_shown(look: &Look, first_start: bool) -> bool {
  !(look.busy && first_start)
}

/// Back from Connect or More goes Home; from Home there is nowhere further back.
pub fn back(from: Tab) -> Option<Tab> {
  if from == Tab::Home { None } else { Some(Tab::Home) }
}

pub fn reach(look: &Look, lan: Option<&str>, open_to_others: bool) -> Reach {
  if !status::can_share(look) {
    Reach::Off
  } else if !open_to_others {
    Reach::ThisComputer
  } else if lan.is_none() {
    Reach::NoNetwork
  } else {
    Reach::Ready
  }
}

#[cfg(test)]
mod tests {
  use super::*;
  use shared::Phase;

  #[test]
  fn the_bar_hides_only_while_the_first_setup_runs() {
    let setting_up = status::of(Phase::Installing, false);
    assert!(!bar_shown(&setting_up, true));
    assert!(bar_shown(&status::of(Phase::Starting, false), false));
    assert!(bar_shown(&status::of(Phase::Running, true), true));
  }

  #[test]
  fn back_from_connect_or_more_goes_home() {
    assert_eq!(back(Tab::Connect), Some(Tab::Home));
    assert_eq!(back(Tab::More), Some(Tab::Home));
    assert_eq!(back(Tab::Home), None);
  }

  #[test]
  fn connect_offers_the_address_only_when_there_is_one_to_reach() {
    let running = status::of(Phase::Running, true);
    assert_eq!(reach(&status::of(Phase::Stopped, false), Some("192.168.1.2"), true), Reach::Off);
    assert_eq!(reach(&running, Some("192.168.1.2"), false), Reach::ThisComputer);
    assert_eq!(reach(&running, None, true), Reach::NoNetwork);
    assert_eq!(reach(&running, Some("192.168.1.2"), true), Reach::Ready);
  }
}
