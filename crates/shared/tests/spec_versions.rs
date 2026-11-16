//! spec/fixtures/versions.json, which the Android app's tests read too: release versions, for the app's own updates
//! and Nouride's.

use serde_json::Value;
use shared::version::{newer, parse};

fn fixture() -> Value {
  let path = concat!(env!("CARGO_MANIFEST_DIR"), "/../../spec/fixtures/versions.json");
  serde_json::from_str(&std::fs::read_to_string(path).unwrap()).unwrap()
}

#[test]
fn versions_parse_as_the_spec_says() {
  for case in fixture()["parse"].as_array().unwrap() {
    let version = case["version"].as_str().unwrap();
    let want: Option<[u64; 3]> = serde_json::from_value(case["expect"].clone()).unwrap();
    assert_eq!(parse(version), want, "{version:?}");
  }
}

#[test]
fn newer_as_the_spec_says() {
  for case in fixture()["newer"].as_array().unwrap() {
    let (candidate, installed) = (case["candidate"].as_str().unwrap(), case["installed"].as_str().unwrap());
    assert_eq!(newer(candidate, installed), case["expect"].as_bool().unwrap(), "{candidate} over {installed}");
  }
}
