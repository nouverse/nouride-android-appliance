//! spec/fixtures/remote-address.json, which the Android app's tests read too: the address a person types for a
//! Nouride on another machine. A case's `desktop` answer, when there is one, is where this app differs from Android.

use place::remote_host::{self, Read};
use serde_json::Value;

fn fixture() -> Value {
  let path = concat!(env!("CARGO_MANIFEST_DIR"), "/../../spec/fixtures/remote-address.json");
  serde_json::from_str(&std::fs::read_to_string(path).unwrap()).unwrap()
}

fn expected(case: &Value) -> &Value {
  case.get("desktop").unwrap_or(&case["expect"])
}

fn read_as_json(read: Read) -> Value {
  match read {
    Read::Address(base) => serde_json::json!({ "read": "address", "base": base }),
    Read::Empty => serde_json::json!({ "read": "empty" }),
    Read::PlainHttp => serde_json::json!({ "read": "plain_http" }),
    Read::NotAnAddress => serde_json::json!({ "read": "not_an_address" }),
  }
}

#[test]
fn every_typed_address_reads_as_the_spec_says() {
  let cases = fixture()["read"].as_array().unwrap().clone();
  assert!(cases.len() >= 20);
  for case in &cases {
    let typed = case["typed"].as_str().unwrap();
    assert_eq!(&read_as_json(remote_host::read(typed)), expected(case), "typed {typed:?}");
  }
}

#[test]
fn shown_and_dashboard_as_the_spec_says() {
  let spec = fixture();
  for case in spec["shown"].as_array().unwrap() {
    assert_eq!(remote_host::shown(case["base"].as_str().unwrap()), expected(case).as_str().unwrap());
  }
  for case in spec["dashboard"].as_array().unwrap() {
    assert_eq!(remote_host::dashboard(case["base"].as_str().unwrap()), expected(case).as_str().unwrap());
  }
}
