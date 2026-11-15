//! The person's answer, kept in the app's own preferences.

use std::io;

use shared::prefs::Prefs;
use shared::Where;

const WHERE: &str = "where";
const ADDRESS: &str = "address";

pub struct Store {
  prefs: Prefs,
}

impl Store {
  pub fn new(prefs: Prefs) -> Store {
    Store { prefs }
  }

  /// `None` until the person has chosen, and only on a computer the app has not installed Nouride on yet.
  pub fn where_(&self, installed_here: bool) -> Option<Where> {
    crate::answer(self.prefs.get(WHERE).as_deref(), installed_here)
  }

  /// The other computer's address, or `None` while Nouride lives here.
  pub fn address(&self, installed_here: bool) -> Option<String> {
    if self.where_(installed_here) == Some(Where::Remote) { self.last_address() } else { None }
  }

  /// The last address given, kept after switching back so switching again needs no typing.
  pub fn last_address(&self) -> Option<String> {
    self.prefs.get(ADDRESS)
  }

  pub fn here(&self) -> io::Result<()> {
    self.prefs.set(&[(WHERE, Some(Where::Here.name()))])
  }

  /// Nouride lives at `base`, an address `remote_host` accepted.
  pub fn remote(&self, base: &str) -> io::Result<()> {
    self.prefs.set(&[(WHERE, Some(Where::Remote.name())), (ADDRESS, Some(base))])
  }
}

#[cfg(test)]
mod tests {
  use super::*;

  fn store(name: &str) -> Store {
    let dir = std::env::temp_dir().join(format!("place-store-{}-{name}", std::process::id()));
    let _ = std::fs::remove_dir_all(&dir);
    Store::new(Prefs::new(dir.join("prefs.json")))
  }

  #[test]
  fn switching_back_here_keeps_the_last_address_for_next_time() {
    let s = store("switch");
    assert_eq!(s.where_(false), None);
    s.remote("https://nouride.example.com").unwrap();
    assert_eq!(s.where_(false), Some(Where::Remote));
    assert_eq!(s.address(false).as_deref(), Some("https://nouride.example.com"));
    s.here().unwrap();
    assert_eq!(s.where_(false), Some(Where::Here));
    assert_eq!(s.address(false), None);
    assert_eq!(s.last_address().as_deref(), Some("https://nouride.example.com"));
  }
}
