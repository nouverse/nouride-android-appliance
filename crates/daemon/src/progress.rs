//! How far the first setup is, as a bar a person can watch move. Three steps, each owning a slice of the bar: the
//! installer checking the computer and downloading the release, the installer putting it in, and the app turning
//! Nouride on, which ends when it answers. A step whose length is unknown moves a little closer to the end of its slice
//! with every line it prints and never reaches it; only the next step's start fills a slice, so the bar never stands
//! still for long and never goes back. Android's `SetupProgress`, with the installer's own headings as its markers.

use serde::Serialize;

use crate::installer;

#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Serialize)]
#[serde(rename_all = "snake_case")]
pub enum Step {
  Download,
  Install,
  Start,
}

impl Step {
  pub const ALL: [Step; 3] = [Step::Download, Step::Install, Step::Start];

  /// 1-based, for "Step 2 of 3".
  pub fn number(self) -> usize {
    Step::ALL.iter().position(|s| *s == self).unwrap_or(0) + 1
  }

  fn slice(self) -> (f64, f64) {
    match self {
      Step::Download => (0.0, 45.0),
      Step::Install => (45.0, 85.0),
      Step::Start => (85.0, 99.0),
    }
  }
}

/// What the screen shows: a step and a percent, 0 to 99 (100 is the running screen itself).
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
pub struct Progress {
  pub step: Step,
  pub percent: u8,
}

/// The installer's headings up to and including its download; every later one is the install.
const DOWNLOAD_HEADINGS: &[&str] = &["Checking this machine", "Fetching the release"];

const NUDGE: f64 = 0.04;

#[derive(Debug, Clone)]
pub struct SetupProgress {
  step: Step,
  exact: f64,
}

impl Default for SetupProgress {
  fn default() -> Self {
    SetupProgress { step: Step::Download, exact: 0.0 }
  }
}

impl SetupProgress {
  pub fn snapshot(&self) -> Progress {
    Progress { step: self.step, percent: self.exact.clamp(0.0, 99.0) as u8 }
  }

  /// A line the installer printed: a heading may start the next step, any other line nudges the bar.
  pub fn line(&mut self, text: &str) {
    if let Some(heading) = installer::heading(text) {
      let step = if DOWNLOAD_HEADINGS.contains(&heading.as_str()) { Step::Download } else { Step::Install };
      self.enter(step);
      return;
    }
    if text.trim().is_empty() {
      return;
    }
    let end = self.step.slice().1;
    self.advance(self.exact + (end - self.exact) * NUDGE);
  }

  /// The installer is done; the app now turns Nouride on.
  pub fn starting(&mut self) {
    self.enter(Step::Start);
  }

  fn enter(&mut self, next: Step) {
    if next < self.step {
      return;
    }
    self.step = next;
    self.advance(next.slice().0);
  }

  fn advance(&mut self, to: f64) {
    if to > self.exact {
      self.exact = to;
    }
  }
}

#[cfg(test)]
mod tests {
  use super::*;

  #[test]
  fn a_heading_starts_its_step_and_the_bar_jumps_to_its_slice() {
    let mut p = SetupProgress::default();
    assert_eq!(p.snapshot(), Progress { step: Step::Download, percent: 0 });
    p.line("\u{1b}[1m▸ Fetching the release\u{1b}[0m");
    assert_eq!(p.snapshot(), Progress { step: Step::Download, percent: 0 });
    p.line("\u{1b}[1m▸ Installing the binary\u{1b}[0m");
    assert_eq!(p.snapshot(), Progress { step: Step::Install, percent: 45 });
    p.starting();
    assert_eq!(p.snapshot(), Progress { step: Step::Start, percent: 85 });
  }

  #[test]
  fn lines_nudge_the_bar_toward_the_end_of_the_step_never_past_it() {
    let mut p = SetupProgress::default();
    p.line("▸ Service account and directory");
    p.line("  install directory /x/nouride");
    let one = p.snapshot().percent;
    assert!(one > 45, "a line moves the bar, got {one}");
    for _ in 0..10_000 {
      p.line("  something");
    }
    assert_eq!(p.snapshot(), Progress { step: Step::Install, percent: 84 });
    p.line("");
    assert_eq!(p.snapshot().percent, 84);
  }

  #[test]
  fn the_bar_never_goes_back_not_even_for_an_earlier_heading() {
    let mut p = SetupProgress::default();
    p.line("▸ Environment file");
    p.line("▸ Fetching the release");
    assert_eq!(p.snapshot(), Progress { step: Step::Install, percent: 45 });
  }

  #[test]
  fn it_never_says_100_before_nouride_answers() {
    let mut p = SetupProgress::default();
    p.starting();
    for _ in 0..10_000 {
      p.line("booting");
    }
    assert_eq!(p.snapshot().percent, 98);
  }

  #[test]
  fn steps_count_from_one() {
    assert_eq!((Step::Download.number(), Step::Install.number(), Step::Start.number()), (1, 2, 3));
  }
}
