//! What Connect can offer. Android's `Tabs.reach`, with its test.

use serde::Serialize;

use crate::{status, Look};

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
  fn connect_offers_the_address_only_when_there_is_one_to_reach() {
    let running = status::of(Phase::Running, true);
    assert_eq!(reach(&status::of(Phase::Stopped, false), Some("192.168.1.2"), true), Reach::Off);
    assert_eq!(reach(&running, Some("192.168.1.2"), false), Reach::ThisComputer);
    assert_eq!(reach(&running, None, true), Reach::NoNetwork);
    assert_eq!(reach(&running, Some("192.168.1.2"), true), Reach::Ready);
  }
}
