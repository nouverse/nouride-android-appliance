//! spec/fixtures/downloads.json, which the Android app's tests read too: which downloads the dashboard's window keeps,
//! the name each is saved under, and the number a second copy gets.

use downloads::Source;
use serde_json::Value;

fn fixture() -> Value {
  let path = concat!(env!("CARGO_MANIFEST_DIR"), "/../../spec/fixtures/downloads.json");
  serde_json::from_str(&std::fs::read_to_string(path).unwrap()).unwrap()
}

fn expected(case: &Value) -> &str {
  case.get("desktop").unwrap_or(&case["expect"]).as_str().unwrap()
}

fn cases<'a>(spec: &'a Value, group: &str) -> &'a Vec<Value> {
  let cases = spec[group].as_array().unwrap();
  assert!(!cases.is_empty(), "{group}");
  cases
}

#[test]
fn which_downloads_are_kept_as_the_spec_says() {
  let spec = fixture();
  for case in cases(&spec, "source") {
    let got = match downloads::source(case["url"].as_str().unwrap(), case["origin"].as_str().unwrap()) {
      Source::Save => "save",
      Source::Outside => "outside",
      Source::Refused => "refused",
    };
    assert_eq!(got, expected(case), "{case}");
  }
}

#[test]
fn every_name_as_the_spec_says() {
  let spec = fixture();
  for case in cases(&spec, "name") {
    let got = downloads::name(case["given"].as_str(), case["disposition"].as_str(), case["url"].as_str().unwrap());
    assert_eq!(got, expected(case), "{case}");
  }
}

#[test]
fn a_second_copy_is_numbered_as_the_spec_says() {
  let spec = fixture();
  for case in cases(&spec, "numbered") {
    let n = u32::try_from(case["n"].as_u64().unwrap()).unwrap();
    assert_eq!(downloads::numbered(case["name"].as_str().unwrap(), n), expected(case), "{case}");
  }
  for case in cases(&spec, "free") {
    let taken: Vec<&str> = case["taken"].as_array().unwrap().iter().map(|t| t.as_str().unwrap()).collect();
    assert_eq!(downloads::free(case["name"].as_str().unwrap(), |n| taken.contains(&n)), expected(case), "{case}");
  }
}
