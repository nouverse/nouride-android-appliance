//! A small JSON file of the app's own choices (where Nouride runs, the appearance, what an update check heard),
//! written whole through a temporary file so a crash never leaves half of it.

use std::collections::BTreeMap;
use std::fs;
use std::io;
use std::path::{Path, PathBuf};

#[derive(Debug, Clone)]
pub struct Prefs {
  path: PathBuf,
}

impl Prefs {
  pub fn new(path: impl Into<PathBuf>) -> Prefs {
    Prefs { path: path.into() }
  }

  pub fn path(&self) -> &Path {
    &self.path
  }

  fn read(&self) -> BTreeMap<String, String> {
    fs::read_to_string(&self.path).ok().and_then(|text| serde_json::from_str(&text).ok()).unwrap_or_default()
  }

  pub fn get(&self, key: &str) -> Option<String> {
    self.read().remove(key)
  }

  pub fn get_u64(&self, key: &str) -> u64 {
    self.get(key).and_then(|v| v.parse().ok()).unwrap_or(0)
  }

  /// Sets each of `pairs`; a `None` removes its key.
  pub fn set(&self, pairs: &[(&str, Option<&str>)]) -> io::Result<()> {
    let mut all = self.read();
    for (key, value) in pairs {
      match value {
        Some(v) => all.insert((*key).to_owned(), (*v).to_owned()),
        None => all.remove(*key),
      };
    }
    if let Some(dir) = self.path.parent() {
      fs::create_dir_all(dir)?;
    }
    let tmp = self.path.with_extension("json.tmp");
    fs::write(&tmp, serde_json::to_string_pretty(&all).map_err(io::Error::other)?)?;
    fs::rename(tmp, &self.path)
  }
}

#[cfg(test)]
mod tests {
  use super::*;

  fn temp(name: &str) -> PathBuf {
    let dir = std::env::temp_dir().join(format!("shared-prefs-{}-{name}", std::process::id()));
    let _ = fs::remove_dir_all(&dir);
    dir.join("prefs.json")
  }

  #[test]
  fn a_value_set_is_read_back_and_none_removes_it() {
    let prefs = Prefs::new(temp("roundtrip"));
    assert_eq!(prefs.get("where"), None);
    prefs.set(&[("where", Some("REMOTE")), ("address", Some("https://n.example.com"))]).unwrap();
    assert_eq!(prefs.get("where").as_deref(), Some("REMOTE"));
    prefs.set(&[("where", None)]).unwrap();
    assert_eq!(prefs.get("where"), None);
    assert_eq!(prefs.get("address").as_deref(), Some("https://n.example.com"));
  }

  #[test]
  fn a_broken_file_reads_as_empty_rather_than_failing() {
    let path = temp("broken");
    fs::create_dir_all(path.parent().unwrap()).unwrap();
    fs::write(&path, "{not json").unwrap();
    let prefs = Prefs::new(&path);
    assert_eq!(prefs.get("where"), None);
    prefs.set(&[("where", Some("HERE"))]).unwrap();
    assert_eq!(prefs.get("where").as_deref(), Some("HERE"));
  }

  #[test]
  fn numbers_default_to_zero() {
    let prefs = Prefs::new(temp("numbers"));
    assert_eq!(prefs.get_u64("checked_at"), 0);
    prefs.set(&[("checked_at", Some("42"))]).unwrap();
    assert_eq!(prefs.get_u64("checked_at"), 42);
  }
}
