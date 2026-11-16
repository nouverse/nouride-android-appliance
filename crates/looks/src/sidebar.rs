//! When the window's sidebar shows. Its sections are siblings, so there is no Back between them (the Android app's
//! `Tabs` had one, from Connect or More to Home).

use crate::Look;

/// The sidebar is hidden while the first setup runs: that takes minutes and its progress is the one thing to watch.
pub fn sidebar_shown(look: &Look, first_start: bool) -> bool {
  !(look.busy && first_start)
}

#[cfg(test)]
mod tests {
  use super::*;
  use crate::status;
  use shared::Phase;

  #[test]
  fn the_sidebar_hides_only_while_the_first_setup_runs() {
    let setting_up = status::of(Phase::Installing, false);
    assert!(!sidebar_shown(&setting_up, true));
    assert!(sidebar_shown(&status::of(Phase::Starting, false), false));
    assert!(sidebar_shown(&status::of(Phase::Running, true), true));
  }
}
