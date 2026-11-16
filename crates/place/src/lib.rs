//! Where Nouride runs: the person's answer, the other computer's address, and what that computer said. Android's
//! `Place`, `RemoteHost` and `RemoteReply`, with their tests.

pub mod remote_host;
pub mod remote_reply;
pub mod store;

pub use remote_host::Read;
pub use store::Store;

use shared::Where;

/// The answer from what was stored and whether the app has installed Nouride on this computer already. A computer
/// with the app's Nouride on it was answered before (an install from before the question, or a reinstall of the app
/// over its own folder), so it lands on that install rather than on the question. Pure.
pub fn answer(stored: Option<&str>, installed_here: bool) -> Option<Where> {
  let known = match stored {
    Some("HERE") => Some(Where::Here),
    Some("REMOTE") => Some(Where::Remote),
    _ => None,
  };
  known.or(if installed_here { Some(Where::Here) } else { None })
}

#[cfg(test)]
mod tests {
  use super::*;

  #[test]
  fn a_computer_with_the_apps_nouride_on_it_already_is_never_asked_where_it_lives() {
    assert_eq!(answer(None, true), Some(Where::Here));
  }

  #[test]
  fn a_new_computer_is_asked() {
    assert_eq!(answer(None, false), None);
  }

  #[test]
  fn an_answer_given_wins_over_what_is_on_disk() {
    assert_eq!(answer(Some("REMOTE"), true), Some(Where::Remote));
    assert_eq!(answer(Some("HERE"), false), Some(Where::Here));
  }

  #[test]
  fn an_answer_this_version_does_not_know_is_no_answer() {
    assert_eq!(answer(Some("MOON"), false), None);
    assert_eq!(answer(Some("MOON"), true), Some(Where::Here));
  }
}
