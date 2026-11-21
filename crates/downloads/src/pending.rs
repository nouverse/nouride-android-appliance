//! The downloads the dashboard's window has under way, and where each is written: a download that fails is taken back
//! out of Downloads, so no part of a file is left there under the file's own name, where it would look whole
//! (`spec/downloads.md`, "A download that fails").
//!
//! WebKit on a Mac says which download ended only by its address: wry (0.57) gives no path when it ends. So a download
//! is known by its URL, and two of one address at once by their order. Which of two such downloads failed cannot be
//! told, and a file that may be the other's is never removed: while two of one address overlap, a failed one is left.

use std::collections::HashMap;
use std::io::ErrorKind;
use std::path::PathBuf;

use crate::FALLBACK;

/// The downloads under way, by address.
#[derive(Debug, Default)]
pub struct Pending {
  by_url: HashMap<String, Group>,
}

/// The downloads of one address under way, oldest first.
#[derive(Debug, Default)]
struct Group {
  targets: Vec<PathBuf>,
  /// Two were under way at once since the first began: which one ends is a guess from here on.
  overlapped: bool,
}

/// What became of a download that ended.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Ended {
  /// The file's name in Downloads, for the line.
  pub name: String,
  /// What happened to what it wrote.
  pub part: Part,
}

/// What a download that ended left in Downloads.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Part {
  /// It succeeded: the file is kept.
  Kept,
  /// It failed, and what it wrote was removed.
  Removed,
  /// It failed before writing anything.
  NothingWritten,
  /// It failed while another download of its address was under way: which file is whose cannot be told, so neither
  /// was touched.
  LeftOverlapping,
  /// It failed, and the file could not be removed (the reason, for the app's log).
  NotRemoved(String),
}

impl Pending {
  /// A download of `url` begins, written to `target`.
  pub fn start(&mut self, url: &str, target: PathBuf) {
    let group = self.by_url.entry(url.to_owned()).or_default();
    if !group.targets.is_empty() {
      group.overlapped = true;
    }
    group.targets.push(target);
  }

  /// A download of `url` ended: its name, and when it failed, what it wrote removed.
  pub fn end(&mut self, url: &str, success: bool) -> Ended {
    let Some(group) = self.by_url.get_mut(url) else {
      return Ended { name: FALLBACK.to_owned(), part: if success { Part::Kept } else { Part::NothingWritten } };
    };
    let target = group.targets.remove(0);
    let overlapped = group.overlapped;
    if group.targets.is_empty() {
      self.by_url.remove(url);
    }
    let name = target.file_name().and_then(|n| n.to_str()).unwrap_or(FALLBACK).to_owned();
    let part = match (success, overlapped) {
      (true, _) => Part::Kept,
      (false, true) => Part::LeftOverlapping,
      (false, false) => match std::fs::remove_file(&target) {
        Ok(()) => Part::Removed,
        Err(e) if e.kind() == ErrorKind::NotFound => Part::NothingWritten,
        Err(e) => Part::NotRemoved(e.to_string()),
      },
    };
    Ended { name, part }
  }
}

#[cfg(test)]
mod tests {
  use super::*;
  use std::path::Path;

  struct Dir(PathBuf);

  impl Dir {
    fn new(test: &str) -> Dir {
      let dir = std::env::temp_dir().join(format!("downloads-pending-{test}-{}", std::process::id()));
      let _ = std::fs::remove_dir_all(&dir);
      std::fs::create_dir_all(&dir).unwrap();
      Dir(dir)
    }

    fn file(&self, name: &str, bytes: usize) -> PathBuf {
      let path = self.0.join(name);
      std::fs::write(&path, vec![b'x'; bytes]).unwrap();
      path
    }
  }

  impl Drop for Dir {
    fn drop(&mut self) {
      let _ = std::fs::remove_dir_all(&self.0);
    }
  }

  const EXPORT: &str = "http://127.0.0.1:18264/api/control/transfer/export/download";

  fn there(path: &Path) -> bool {
    path.exists()
  }

  #[test]
  fn a_download_that_fails_leaves_no_part_under_the_file_s_name() {
    let dir = Dir::new("fails");
    let part = dir.file("nouride-export.tar.gz", 1 << 20);
    let mut pending = Pending::default();
    pending.start(EXPORT, part.clone());
    let ended = pending.end(EXPORT, false);
    assert_eq!(ended, Ended { name: "nouride-export.tar.gz".into(), part: Part::Removed });
    assert!(!there(&part));
  }

  #[test]
  fn a_download_that_succeeds_is_kept() {
    let dir = Dir::new("succeeds");
    let file = dir.file("report.pdf", 10);
    let mut pending = Pending::default();
    pending.start("blob:http://127.0.0.1:18264/1", file.clone());
    assert_eq!(pending.end("blob:http://127.0.0.1:18264/1", true), Ended { name: "report.pdf".into(), part: Part::Kept });
    assert!(there(&file));
  }

  #[test]
  fn a_download_that_fails_before_writing_removes_nothing_else() {
    let dir = Dir::new("nothing");
    let other = dir.file("report.pdf", 10);
    let mut pending = Pending::default();
    pending.start(EXPORT, dir.0.join("report (1).pdf"));
    assert_eq!(pending.end(EXPORT, false).part, Part::NothingWritten);
    assert!(there(&other));
  }

  #[test]
  fn two_of_one_address_at_once_are_both_left_whichever_fails() {
    let dir = Dir::new("overlap");
    let first = dir.file("nouride-export.tar.gz", 10);
    let second = dir.file("nouride-export (1).tar.gz", 10);
    let mut pending = Pending::default();
    pending.start(EXPORT, first.clone());
    pending.start(EXPORT, second.clone());
    assert_eq!(pending.end(EXPORT, false).part, Part::LeftOverlapping);
    assert_eq!(pending.end(EXPORT, false).part, Part::LeftOverlapping);
    assert!(there(&first) && there(&second));
  }

  #[test]
  fn once_the_overlap_is_over_a_later_download_of_that_address_is_cleaned_up_again() {
    let dir = Dir::new("after");
    let mut pending = Pending::default();
    pending.start(EXPORT, dir.file("a.tar.gz", 1));
    pending.start(EXPORT, dir.file("a (1).tar.gz", 1));
    pending.end(EXPORT, true);
    pending.end(EXPORT, true);
    let later = dir.file("a (2).tar.gz", 1);
    pending.start(EXPORT, later.clone());
    assert_eq!(pending.end(EXPORT, false).part, Part::Removed);
    assert!(!there(&later));
  }

  #[test]
  fn a_download_of_another_address_is_not_an_overlap() {
    let dir = Dir::new("others");
    let blob = dir.file("photos.zip", 1);
    let export = dir.file("nouride-export.tar.gz", 1);
    let mut pending = Pending::default();
    pending.start("blob:http://127.0.0.1:18264/7", blob.clone());
    pending.start(EXPORT, export.clone());
    assert_eq!(pending.end(EXPORT, false).part, Part::Removed);
    assert!(!there(&export) && there(&blob));
  }

  #[test]
  fn an_end_nobody_started_names_the_file_download() {
    let mut pending = Pending::default();
    assert_eq!(pending.end(EXPORT, false), Ended { name: FALLBACK.into(), part: Part::NothingWritten });
  }
}
