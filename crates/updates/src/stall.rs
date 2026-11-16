//! Whether Nouride's download of an update is still moving, from what it reports once a second. Nouride up to 0.8.26
//! waits on a quiet connection for ever; a pause and a resume opens a new connection and goes on from the bytes
//! already on disk, so a download quiet for `stall_ms` is restarted that way, at most `max_restarts` times, before it
//! is said to have failed. Android's `StallWatch`, with its tests.

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Verdict {
  Moving,
  Restart,
  GiveUp,
}

#[derive(Debug, Clone)]
pub struct StallWatch {
  stall_ms: u64,
  max_restarts: u32,
  last: Option<u64>,
  since: u64,
  restarts: u32,
}

impl StallWatch {
  pub fn new(stall_ms: u64, max_restarts: u32) -> StallWatch {
    StallWatch { stall_ms, max_restarts, last: None, since: 0, restarts: 0 }
  }

  /// `received`: the bytes Nouride has, now; `now_ms`: a clock that only goes forward.
  pub fn seen(&mut self, received: u64, now_ms: u64) -> Verdict {
    if self.last != Some(received) {
      self.last = Some(received);
      self.since = now_ms;
      return Verdict::Moving;
    }
    if now_ms.saturating_sub(self.since) < self.stall_ms {
      return Verdict::Moving;
    }
    if self.restarts >= self.max_restarts {
      return Verdict::GiveUp;
    }
    self.restarts += 1;
    self.since = now_ms;
    Verdict::Restart
  }
}

#[cfg(test)]
mod tests {
  use super::*;

  #[test]
  fn a_download_that_moves_is_left_alone_however_slow() {
    let mut watch = StallWatch::new(30_000, 2);
    for second in 0..=120u64 {
      assert_eq!(watch.seen(second * 10, second * 1_000), Verdict::Moving);
    }
  }

  #[test]
  fn one_that_stops_is_restarted_once_it_has_been_quiet_for_the_window_not_before() {
    let mut watch = StallWatch::new(30_000, 2);
    assert_eq!(watch.seen(0, 0), Verdict::Moving);
    assert_eq!(watch.seen(0, 29_999), Verdict::Moving);
    assert_eq!(watch.seen(0, 30_000), Verdict::Restart);
    assert_eq!(watch.seen(0, 31_000), Verdict::Moving);
    assert_eq!(watch.seen(0, 59_999), Verdict::Moving);
  }

  #[test]
  fn bytes_after_a_restart_start_the_window_again() {
    let mut watch = StallWatch::new(30_000, 1);
    watch.seen(0, 0);
    assert_eq!(watch.seen(0, 30_000), Verdict::Restart);
    assert_eq!(watch.seen(5_000, 31_000), Verdict::Moving);
    assert_eq!(watch.seen(5_000, 60_999), Verdict::Moving);
  }

  #[test]
  fn it_gives_up_after_its_restarts_rather_than_waiting_for_ever() {
    let mut watch = StallWatch::new(30_000, 2);
    watch.seen(100, 0);
    assert_eq!(watch.seen(100, 30_000), Verdict::Restart);
    assert_eq!(watch.seen(100, 60_000), Verdict::Restart);
    assert_eq!(watch.seen(100, 90_000), Verdict::GiveUp);
  }
}
