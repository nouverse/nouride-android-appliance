//! Every key the looks name has its words in `packages/ui-kit/src/words.json`, and every placeholder in them is one
//! the screens fill: a state cannot ship without its sentence, nor a sentence with a `{…}` left in it.

use looks::{notices, tray, versions, Words};
use serde_json::Value;
use shared::{Phase, Reply, Where};

fn words() -> serde_json::Map<String, Value> {
  let text = include_str!("../../../packages/ui-kit/src/words.json");
  match serde_json::from_str(text).expect("words.json is JSON") {
    Value::Object(map) => map,
    _ => panic!("words.json is an object"),
  }
}

fn has(map: &serde_json::Map<String, Value>, key: &str) {
  assert!(map.get(key).and_then(Value::as_str).is_some_and(|s| !s.is_empty()), "no words for {key}");
}

#[test]
fn every_state_has_a_title_and_a_body() {
  let map = words();
  for w in Words::ALL {
    has(&map, &format!("{}_title", w.key()));
    has(&map, &format!("{}_body", w.key()));
  }
}

#[test]
fn every_version_card_sentence_and_label_has_words() {
  let map = words();
  for look in versions::all() {
    has(&map, look.words);
    if let Some(label) = look.label {
      has(&map, label);
    }
  }
}

#[test]
fn every_menu_item_and_notice_has_words() {
  let map = words();
  for (app, nouride) in [(false, false), (true, false), (false, true), (true, true)] {
    if let Some(k) = notices::tray(app, nouride) {
      has(&map, k);
    }
    if let Some(k) = notices::versions_row(app, nouride) {
      has(&map, k);
    }
  }
  for phase in [Phase::Stopped, Phase::Running, Phase::Failed] {
    for place in [None, Some(Where::Here), Some(Where::Remote)] {
      let look = looks::status::of(phase, true);
      let input = tray::Input { place, look: &look, ours: true, phase, app_update: true, nouride_update: true };
      for item in tray::menu(input) {
        has(&map, &item.words);
      }
    }
  }
  let _ = looks::status::remote(Some(Reply::Down));
}

#[test]
fn only_the_placeholders_the_screens_fill_are_in_the_words() {
  let filled = ["{version}", "{percent}", "{step}", "{steps}", "{app}", "{nouride}"];
  for (key, value) in words() {
    let Some(text) = value.as_str() else { continue };
    let mut rest = filled.iter().fold(text.to_owned(), |t, p| t.replace(p, ""));
    rest.retain(|c| c == '{' || c == '}');
    assert!(rest.is_empty(), "{key}: {text}");
  }
}
