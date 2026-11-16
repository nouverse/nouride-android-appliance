//! spec/fixtures/backup.json, which the Android app's tests read too: what under `.nouride` stays out of the zip, the
//! zip's name and where its database goes, and the snapshot `nouride db backup --json` names.

use serde_json::Value;

fn fixture() -> Value {
  let path = concat!(env!("CARGO_MANIFEST_DIR"), "/../../spec/fixtures/backup.json");
  serde_json::from_str(&std::fs::read_to_string(path).unwrap()).unwrap()
}

fn expected(case: &Value) -> &Value {
  case.get("desktop").unwrap_or(&case["expect"])
}

#[test]
fn the_app_holds_the_spec_s_list_of_what_stays_out() {
  let spec = fixture();
  let listed: Vec<&str> = spec["out"].as_array().unwrap().iter().map(|r| r["pattern"].as_str().unwrap()).collect();
  assert_eq!(backup::OUT, listed.as_slice());
}

#[test]
fn every_path_is_in_or_out_as_the_spec_says() {
  let spec = fixture();
  let cases = spec["kept"].as_array().unwrap();
  assert!(cases.len() >= 60);
  for case in cases {
    let path = case["path"].as_str().unwrap();
    let got = if backup::kept(path) { "in" } else { "out" };
    assert_eq!(got, expected(case), "{path}");
  }
}

#[test]
fn the_name_the_entries_and_the_snapshot_as_the_spec_says() {
  let spec = fixture();
  assert_eq!(backup::FOLDER_ENTRY, spec["entries"]["folder"]);
  assert_eq!(backup::DATABASE_ENTRY, spec["entries"]["database"]);
  for case in spec["zip_name"].as_array().unwrap() {
    assert_eq!(backup::zip_name(case["date"].as_str().unwrap()), expected(case).as_str().unwrap());
  }
  for case in spec["snapshot"].as_array().unwrap() {
    let got = backup::snapshot(case["output"].as_str().unwrap());
    assert_eq!(got.as_deref(), expected(case).as_str(), "{}", case["output"]);
  }
}
