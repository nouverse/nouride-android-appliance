//! What Technical Logs reads: the end of a log, a cut line dropped. Android's `SupportText.tail` and `readTail`.

use std::fs::File;
use std::io::{Read, Seek, SeekFrom};
use std::path::Path;

/// The last `lines` lines of `text`, without the empty line a log's last newline leaves.
pub fn tail(text: &str, lines: usize) -> String {
  let trimmed = text.trim_end_matches('\n');
  let all: Vec<&str> = trimmed.split('\n').collect();
  all[all.len().saturating_sub(lines)..].join("\n")
}

/// The end of a log file, at most `bytes` of it; empty when there is none yet.
pub fn read_tail(path: &Path, lines: usize, bytes: u64) -> String {
  let Ok(mut file) = File::open(path) else { return String::new() };
  let len = file.metadata().map(|m| m.len()).unwrap_or(0);
  let start = len.saturating_sub(bytes);
  if file.seek(SeekFrom::Start(start)).is_err() {
    return String::new();
  }
  let mut buffer = Vec::new();
  let _ = file.read_to_end(&mut buffer);
  let text = String::from_utf8_lossy(&buffer).into_owned();
  // A cut in the middle of a line leaves half a line first: drop it.
  let text = if start > 0 { text.split_once('\n').map(|(_, rest)| rest.to_owned()).unwrap_or_default() } else { text };
  tail(&text, lines)
}

#[cfg(test)]
mod tests {
  use super::*;

  #[test]
  fn the_tail_is_the_last_lines_without_a_trailing_empty_one() {
    assert_eq!(tail("a\nb\nc\n", 2), "b\nc");
    assert_eq!(tail("a\n", 5), "a");
    assert_eq!(tail("", 5), "");
  }

  #[test]
  fn a_file_read_from_its_middle_drops_the_half_line() {
    let path = std::env::temp_dir().join(format!("daemon-support-{}", std::process::id()));
    std::fs::write(&path, "first line\nsecond line\nthird line\n").unwrap();
    assert_eq!(read_tail(&path, 10, 15), "third line");
    assert_eq!(read_tail(&path, 10, 1000), "first line\nsecond line\nthird line");
    assert_eq!(read_tail(Path::new("/no/such/log"), 10, 1000), "");
  }
}
