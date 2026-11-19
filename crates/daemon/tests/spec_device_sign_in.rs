//! spec/fixtures/device-sign-in.json, which the Android app's tests read too: the device token's label, what each
//! answer leads to, whole rounds, the sign-in page, where the window goes after it and how often a round starts there.

use daemon::device_sign_in::{self, Answer, Call, Effects, Outcome, Step};
use serde_json::{json, Value};

fn fixture() -> Value {
  let path = concat!(env!("CARGO_MANIFEST_DIR"), "/../../spec/fixtures/device-sign-in.json");
  serde_json::from_str(&std::fs::read_to_string(path).unwrap()).unwrap()
}

fn expected(case: &Value) -> &Value {
  case.get("desktop").unwrap_or(&case["expect"])
}

fn cases<'a>(spec: &'a Value, group: &str) -> &'a Vec<Value> {
  let cases = spec[group].as_array().unwrap();
  assert!(!cases.is_empty(), "{group}");
  cases
}

fn call_name(call: Call) -> &'static str {
  match call {
    Call::Trade => "trade",
    Call::Mint => "mint",
  }
}

fn step_json(step: &Step) -> Value {
  match step {
    Step::SignedIn => json!({ "do": "signed_in" }),
    Step::Trade => json!({ "do": "trade" }),
    Step::Mint => json!({ "do": "mint" }),
    Step::Wait { ms, then } => json!({ "do": "wait", "ms": ms, "then": call_name(*then) }),
    Step::GiveUp => json!({ "do": "give_up" }),
  }
}

#[test]
fn the_label_as_the_spec_says_and_always_one_nouride_takes() {
  let spec = fixture();
  for case in cases(&spec, "label") {
    let got = device_sign_in::label(case["name"].as_str().unwrap());
    assert_eq!(got, expected(case).as_str().unwrap(), "{case}");
    assert!(!got.is_empty() && got.chars().count() <= 60, "{got}");
    assert!(got.chars().all(|c| c.is_ascii_alphanumeric() || " ._'()-".contains(c)), "{got}");
  }
}

#[test]
fn every_answer_leads_where_the_spec_says() {
  let spec = fixture();
  for case in cases(&spec, "answers") {
    let call = if case["call"] == "trade" { Call::Trade } else { Call::Mint };
    let answer = Answer {
      code: u16::try_from(case["code"].as_u64().unwrap()).unwrap(),
      retry_after: case["retry_after"].as_str().map(str::to_owned),
      cookie: case["cookie"].as_bool().unwrap_or(true).then(|| "nouride_session=s".to_owned()),
      value: case["value"].as_bool().unwrap_or(true).then(|| "ndev_x".to_owned()),
    };
    let minted = case["minted"].as_bool().unwrap_or(false);
    assert_eq!(step_json(&device_sign_in::next(call, &answer, minted)), *expected(case), "{case}");
  }
}

/// Nouride as a list of answers, in order; what the round asked of it, as the fixture writes it.
struct Scripted {
  answers: Vec<Value>,
  calls: Vec<String>,
  kept: Option<String>,
}

impl Scripted {
  fn answer(&mut self) -> Answer {
    assert!(!self.answers.is_empty(), "the round asked more than the case answers: {:?}", self.calls);
    let a = self.answers.remove(0);
    let code = u16::try_from(a["code"].as_u64().unwrap()).unwrap();
    Answer {
      code,
      retry_after: a["retry_after"].as_str().map(str::to_owned),
      cookie: (code == 200).then(|| "nouride_session=s".to_owned()),
      value: a["value"].as_str().map(str::to_owned),
    }
  }
}

impl Effects for Scripted {
  fn trade(&mut self, token: &str) -> Answer {
    self.calls.push(format!("trade {token}"));
    self.answer()
  }

  fn mint(&mut self) -> Answer {
    self.calls.push("mint".into());
    self.answer()
  }

  fn keep(&mut self, token: &str) {
    self.kept = Some(token.to_owned());
  }

  fn wait(&mut self, ms: u64) {
    self.calls.push(format!("wait {ms}"));
  }
}

#[test]
fn whole_rounds_ask_nouride_exactly_what_the_spec_says() {
  let spec = fixture();
  for case in cases(&spec, "rounds") {
    let kept = case["kept"].as_str().map(str::to_owned);
    let mut nouride = Scripted { answers: case["answers"].as_array().unwrap().clone(), calls: vec![], kept: kept.clone() };
    let outcome = device_sign_in::round(kept, &mut nouride);
    let want = expected(case);
    let outcome = match outcome {
      Outcome::SignedIn(cookie) => {
        assert_eq!(cookie, "nouride_session=s");
        "signed_in"
      }
      Outcome::GaveUp(_) => "give_up",
    };
    assert_eq!(json!(nouride.calls), want["calls"], "{}", case["about"]);
    assert_eq!(outcome, want["outcome"], "{}", case["about"]);
    assert_eq!(json!(nouride.kept), want["kept"], "{}", case["about"]);
    assert!(nouride.answers.is_empty(), "{}: answers left over", case["about"]);
  }
}

#[test]
fn the_sign_in_page_where_the_window_goes_after_it_and_how_often_as_the_spec_says() {
  let spec = fixture();
  for case in cases(&spec, "sign_in_page") {
    let got = device_sign_in::sign_in_page(case["url"].as_str().unwrap(), case["origin"].as_str().unwrap());
    assert_eq!(json!(got), *expected(case), "{case}");
  }
  for case in cases(&spec, "after_sign_in") {
    let got = device_sign_in::after_sign_in(case["url"].as_str().unwrap(), case["origin"].as_str().unwrap());
    assert_eq!(json!(got), *expected(case), "{case}");
  }
  for case in cases(&spec, "gave_up_counts") {
    let got = device_sign_in::gave_up_counts(u16::try_from(case["code"].as_u64().unwrap()).unwrap());
    assert_eq!(json!(got), *expected(case), "{case}");
  }
  for case in cases(&spec, "again") {
    let got = device_sign_in::again(case["last"].as_u64(), case["now"].as_u64().unwrap());
    assert_eq!(json!(got), *expected(case), "{case}");
  }
}
