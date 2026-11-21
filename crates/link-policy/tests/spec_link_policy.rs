//! spec/fixtures/link-policy.json, which the Android app's tests read too: which links the dashboard's window keeps,
//! what Back does there, and what "Back to Nouride Home" does. A case's `desktop` answer, when there is one, is where this app differs from Android.

use link_policy::{back, back_inside, in_app, leave, link, unreachable, Back, Link, BACK, HOME, USER_AGENT_MARK};
use serde_json::Value;

fn fixture() -> Value {
  let path = concat!(env!("CARGO_MANIFEST_DIR"), "/../../spec/fixtures/link-policy.json");
  serde_json::from_str(&std::fs::read_to_string(path).unwrap()).unwrap()
}

fn cases(name: &str) -> Vec<Value> {
  let all = fixture()[name].as_array().unwrap().clone();
  assert!(!all.is_empty(), "{name}");
  all
}

fn expected(case: &Value) -> &Value {
  case.get("desktop").unwrap_or(&case["expect"])
}

fn s<'a>(case: &'a Value, key: &str) -> &'a str {
  case[key].as_str().unwrap()
}

fn named(back: Back) -> &'static str {
  match back {
    Back::Page => "page",
    Back::Ask => "ask",
    Back::Leave => "leave",
  }
}

fn history(case: &Value) -> Vec<&str> {
  case["history"].as_array().unwrap().iter().map(|h| h.as_str().unwrap()).collect()
}

#[test]
fn the_constants_are_the_specs() {
  let spec = fixture();
  assert_eq!(HOME, spec["home"]);
  assert_eq!(BACK, spec["page_back"]);
  assert_eq!(USER_AGENT_MARK, spec["user_agent_mark"]);
}

#[test]
fn in_app_as_the_spec_says() {
  for case in cases("in_app") {
    assert_eq!(in_app(s(&case, "url"), s(&case, "origin")), expected(&case).as_bool().unwrap(), "{case}");
  }
}

#[test]
fn link_as_the_spec_says() {
  for case in cases("link") {
    let got = match link(s(&case, "url"), case["main_frame"].as_bool().unwrap(), s(&case, "origin")) {
      Link::Back => "back",
      Link::Leave => "leave",
      Link::Here => "here",
      Link::Outside => "outside",
    };
    assert_eq!(got, expected(&case).as_str().unwrap(), "{case}");
  }
}

#[test]
fn back_inside_as_the_spec_says() {
  for case in cases("back_inside") {
    let current = case["current"].as_u64().unwrap() as usize;
    assert_eq!(back_inside(&history(&case), current, s(&case, "origin")), expected(&case).as_bool().unwrap(), "{case}");
  }
}

#[test]
fn back_as_the_spec_says() {
  for case in cases("back") {
    let current = case["current"].as_u64().unwrap() as usize;
    let got = back(&history(&case), current, case["dashboard_shown"].as_bool().unwrap(), s(&case, "origin"));
    assert_eq!(named(got), expected(&case).as_str().unwrap(), "{case}");
  }
}

#[test]
fn leave_as_the_spec_says() {
  for case in cases("leave") {
    let got = leave(case["dashboard_shown"].as_bool().unwrap());
    assert_eq!(named(got), expected(&case).as_str().unwrap(), "{case}");
  }
}

#[test]
fn unreachable_as_the_spec_says() {
  for case in cases("unreachable") {
    let got = unreachable(case["main_frame"].as_bool().unwrap(), s(&case, "url"), s(&case, "origin"));
    assert_eq!(got, expected(&case).as_bool().unwrap(), "{case}");
  }
}
