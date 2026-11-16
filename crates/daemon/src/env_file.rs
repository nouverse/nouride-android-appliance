//! Reading and rewriting the install's `.env` in place: the file keeps its mode (0600), and is written whole.

use std::fs;
use std::io;
use std::path::Path;

pub fn read(path: &Path) -> io::Result<String> {
  match fs::read_to_string(path) {
    Ok(text) => Ok(text),
    Err(e) if e.kind() == io::ErrorKind::NotFound => Ok(String::new()),
    Err(e) => Err(e),
  }
}

/// Rewrites `path` with what `change` makes of it; nothing is written when nothing changed.
pub fn rewrite(path: &Path, change: impl FnOnce(&str) -> String) -> io::Result<bool> {
  let before = read(path)?;
  let after = change(&before);
  if after == before {
    return Ok(false);
  }
  // Written through the open file, not replaced: the installer made it 0600, and a new file would take the umask's.
  fs::write(path, after)?;
  Ok(true)
}
