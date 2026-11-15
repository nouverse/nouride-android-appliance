//! The app's words, for the menu bar: the same file the screens read.

use std::collections::HashMap;
use std::sync::OnceLock;

static WORDS: OnceLock<HashMap<String, String>> = OnceLock::new();

pub fn t(key: &str) -> String {
  let words = WORDS.get_or_init(|| serde_json::from_str(include_str!("../../../packages/ui-kit/src/words.json")).unwrap_or_default());
  words.get(key).cloned().unwrap_or_else(|| key.to_owned())
}
