//! The app's own events, for Technical Logs: one line each, with the time. Never a password or a token.

use std::fs::{self, OpenOptions};
use std::io::Write;
use std::path::Path;
use std::time::{SystemTime, UNIX_EPOCH};

pub fn now_ms() -> u64 {
  SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_millis() as u64).unwrap_or(0)
}

pub fn write(path: &Path, line: &str) {
  if let Some(dir) = path.parent() {
    let _ = fs::create_dir_all(dir);
  }
  if let Ok(mut file) = OpenOptions::new().create(true).append(true).open(path) {
    let _ = writeln!(file, "{} {line}", now_ms() / 1000);
  }
}
