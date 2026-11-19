//! The dashboard signed in on this computer's own Nouride with a device token (Nouride 0.8.40): one kept in the app's
//! data folder (0600), made with the install's control token, traded for a session each time the window opens the
//! dashboard and each time the dashboard reaches its sign-in page. The rules both apps share are `spec/device-sign-in.md`
//! and its cases `spec/fixtures/device-sign-in.json`; the Android app implements the same in `signin/DeviceSignIn.kt`.
//! The token and the control token are never logged or shown, and sent only to this computer's Nouride.

use std::fs;
use std::io::{self, Write};
use std::path::PathBuf;
use std::time::Duration;

use url::Url;

/// The session cookie's name in Nouride (`users/auth.ts`).
pub const SESSION: &str = "nouride_session";
/// What every label starts with.
pub const LABEL_PREFIX: &str = "Nouride Home";
/// The longest label Nouride takes (`routes/devices.ts`).
pub const LABEL_MAX: usize = 60;
/// The most a round waits, all its waits together.
pub const ROUND_WAIT_MS: u64 = 120_000;
/// Between two mints while Nouride has no admin yet.
pub const MINT_WAIT_MS: u64 = 2_000;
/// A 429 that does not say how long.
pub const RETRY_AFTER_MS: u64 = 60_000;
/// The least time between two rounds started from the sign-in page.
pub const AGAIN_MS: u64 = 10_000;
/// The dashboard's sign-in page, under the origin.
pub const SIGN_IN_PATH: &str = "/dashboard/login";

/// `Nouride Home (<name>)`, which Nouride's Users page lists; only what Nouride takes in a label, at most 60.
pub fn label(name: &str) -> String {
  let kept: String = name
    .chars()
    .map(|c| match c {
      '\u{2019}' | '\u{2018}' => '\'',
      c if c.is_ascii_alphanumeric() || " ._'()-".contains(c) => c,
      _ => ' ',
    })
    .collect();
  let words = kept.split(' ').filter(|w| !w.is_empty()).collect::<Vec<_>>().join(" ");
  if words.is_empty() {
    return LABEL_PREFIX.to_owned();
  }
  let room = LABEL_MAX - LABEL_PREFIX.len() - " ()".len();
  let cut: String = words.chars().take(room).collect();
  format!("{LABEL_PREFIX} ({})", cut.trim_end())
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Call {
  /// `POST /api/control/auth/device`: the token for a session.
  Trade,
  /// `POST /api/control/auth/devices` with the control token: a new token.
  Mint,
}

/// What a call answered. `code` 0 when nothing answered.
#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct Answer {
  pub code: u16,
  /// The `retry-after` header, as sent.
  pub retry_after: Option<String>,
  /// A trade's `nouride_session=<token>`.
  pub cookie: Option<String>,
  /// A mint's token, `ndev_…`.
  pub value: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Step {
  SignedIn,
  Trade,
  Mint,
  Wait { ms: u64, then: Call },
  GiveUp,
}

/// What an answer leads to; `minted` when this round has made a token already (the spec's table).
pub fn next(call: Call, answer: &Answer, minted: bool) -> Step {
  match (call, answer.code) {
    (Call::Trade, 200) if answer.cookie.is_some() => Step::SignedIn,
    // Refused: a new token, once. Never this one again, nor a new one twice: a failed trade spends the sign-in
    // allowance the person's own password shares.
    (Call::Trade, 401) if !minted => Step::Mint,
    (Call::Mint, 201) if answer.value.is_some() => Step::Trade,
    // No admin yet: a fresh install's first start is still making it.
    (Call::Mint, 409) => Step::Wait { ms: MINT_WAIT_MS, then: Call::Mint },
    (call, 429) => Step::Wait { ms: retry_after_ms(answer.retry_after.as_deref()), then: call },
    _ => Step::GiveUp,
  }
}

fn retry_after_ms(header: Option<&str>) -> u64 {
  header.and_then(|s| s.trim().parse::<u64>().ok()).map_or(RETRY_AFTER_MS, |s| s.saturating_mul(1000))
}

/// What a round does to the world, so a test can stand in for it.
pub trait Effects {
  fn trade(&mut self, token: &str) -> Answer;
  fn mint(&mut self) -> Answer;
  /// Keeps a new token in place of the old.
  fn keep(&mut self, token: &str);
  fn wait(&mut self, ms: u64);
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Outcome {
  /// `nouride_session=<token>`, for the window.
  SignedIn(String),
  /// The last answer's code: the window shows what it shows.
  GaveUp(u16),
}

/// One round: a trade of the kept token, or a mint first when none is kept, until signed in or given up.
pub fn round(kept: Option<String>, fx: &mut dyn Effects) -> Outcome {
  let mut token = kept;
  let mut minted = false;
  let mut waited = 0u64;
  let mut call = if token.is_some() { Call::Trade } else { Call::Mint };
  loop {
    let answer = match call {
      Call::Trade => fx.trade(token.as_deref().unwrap_or_default()),
      Call::Mint => fx.mint(),
    };
    call = match next(call, &answer, minted) {
      Step::SignedIn => return Outcome::SignedIn(answer.cookie.unwrap_or_default()),
      Step::GiveUp => return Outcome::GaveUp(answer.code),
      Step::Mint => Call::Mint,
      Step::Trade => {
        let value = answer.value.unwrap_or_default();
        fx.keep(&value);
        token = Some(value);
        minted = true;
        Call::Trade
      }
      Step::Wait { ms, then } => {
        if waited + ms > ROUND_WAIT_MS {
          return Outcome::GaveUp(answer.code);
        }
        fx.wait(ms);
        waited += ms;
        then
      }
    };
  }
}

/// Whether `url` is the local dashboard's sign-in page.
pub fn sign_in_page(url: &str, origin: &str) -> bool {
  let (Ok(url), Ok(origin)) = (Url::parse(url), Url::parse(origin)) else { return false };
  url.origin() == origin.origin() && matches!(url.path(), SIGN_IN_PATH | "/dashboard/login/")
}

/// Where the window goes once signed in again from the sign-in page `url`: the page its `next` names, checked as the
/// dashboard's own `safeNext` checks it, or the dashboard's first page.
pub fn after_sign_in(url: &str, origin: &str) -> String {
  let origin = origin.trim_end_matches('/');
  let next = Url::parse(url).ok().and_then(|u| u.query_pairs().find(|(k, _)| k == "next").map(|(_, v)| v.into_owned()));
  match next.filter(|n| safe_next(n)) {
    Some(next) => format!("{origin}/dashboard{next}"),
    None => format!("{origin}/dashboard/"),
  }
}

fn safe_next(next: &str) -> bool {
  if !next.starts_with('/') || next.starts_with("//") || next.chars().any(|c| c == '\\' || c.is_control()) {
    return false;
  }
  let path = next.split(['?', '#']).next().unwrap_or_default().to_lowercase();
  path != "/login" && !path.starts_with("/login/")
}

/// Whether a round on opening that gave up after `code` holds off the next from the sign-in page ([`again`]): only when
/// Nouride answered. Nothing answering means it is still starting, and the round after Try again should sign in.
pub fn gave_up_counts(code: u16) -> bool {
  code != 0
}

/// Whether a round may start from the sign-in page now, `last` being when the last round from it, or the last that gave
/// up, started (ms since the epoch).
pub fn again(last: Option<u64>, now: u64) -> bool {
  last.is_none_or(|last| now.saturating_sub(last) >= AGAIN_MS)
}

/// A script for the dashboard's window on this computer's Nouride. The dashboard moves to its sign-in page inside the
/// page (its router, after an API answered 401 or after Sign out), which the window's navigation handler never sees:
/// this turns that arrival into a page load, which it does. A load of the sign-in page itself is left alone, so a
/// round given up shows the page once, not for ever.
pub fn watch_script(origin: &str) -> String {
  let origin = serde_json::to_string(origin.trim_end_matches('/')).expect("a string");
  format!(
    r#"(() => {{
  if (location.origin !== {origin}) return;
  const onSignIn = () => location.pathname.replace(/\/$/, "") === "{SIGN_IN_PATH}";
  let shown = onSignIn();
  const check = () => {{
    const now = onSignIn();
    if (now && !shown) location.replace(location.href);
    shown = now;
  }};
  for (const name of ["pushState", "replaceState"]) {{
    const original = history[name];
    history[name] = function (...args) {{
      const result = original.apply(this, args);
      check();
      return result;
    }};
  }}
  addEventListener("popstate", check);
}})();"#
  )
}

/// The session cookie, `nouride_session=<token>`, from a trade's `Set-Cookie` headers; `None` when there is none.
pub fn session_cookie<'a>(set_cookie: impl IntoIterator<Item = &'a str>) -> Option<String> {
  set_cookie
    .into_iter()
    .map(|header| header.split(';').next().unwrap_or("").trim().to_owned())
    .find(|pair| pair.starts_with(&format!("{SESSION}=")) && pair.len() > SESSION.len() + 1)
}

/// This computer's Nouride and the app's token file: the real [`Effects`].
pub struct Local {
  /// `http://127.0.0.1:<port>`.
  pub origin: String,
  /// `<install>/.nouride/data/.control-token`.
  pub control_token: PathBuf,
  /// The app's own `device.token` (0600).
  pub token_file: PathBuf,
  pub label: String,
}

impl Local {
  /// The kept token, or `None`.
  pub fn kept(&self) -> Option<String> {
    fs::read_to_string(&self.token_file).ok().map(|s| s.trim().to_owned()).filter(|s| !s.is_empty())
  }

  fn post(&self, path: &str, body: serde_json::Value, control_token: Option<&str>) -> Answer {
    let agent: ureq::Agent = ureq::Agent::config_builder()
      .timeout_global(Some(Duration::from_secs(10)))
      .http_status_as_error(false)
      .build()
      .into();
    let mut request = agent
      .post(format!("{}/api/control{path}", self.origin.trim_end_matches('/')))
      .header("Content-Type", "application/json")
      .header("Accept", "application/json");
    if let Some(token) = control_token {
      request = request.header("x-nouride-token", token);
    }
    let Ok(response) = request.send(body.to_string()) else { return Answer::default() };
    let code = response.status().as_u16();
    let header = |name: &str| response.headers().get(name).and_then(|v| v.to_str().ok()).map(str::to_owned);
    let retry_after = header("retry-after");
    let cookies: Vec<String> =
      response.headers().get_all("set-cookie").iter().filter_map(|v| v.to_str().ok()).map(str::to_owned).collect();
    let cookie = session_cookie(cookies.iter().map(String::as_str));
    let text = response.into_body().read_to_string().unwrap_or_default();
    let value = serde_json::from_str::<serde_json::Value>(&text)
      .ok()
      .and_then(|v| v.get("value").and_then(|v| v.as_str()).map(str::to_owned))
      .filter(|v| !v.is_empty());
    Answer { code, retry_after, cookie, value }
  }
}

impl Effects for Local {
  fn trade(&mut self, token: &str) -> Answer {
    self.post("/auth/device", serde_json::json!({ "token": token }), None)
  }

  fn mint(&mut self) -> Answer {
    let control = fs::read_to_string(&self.control_token).map(|s| s.trim().to_owned()).unwrap_or_default();
    if control.is_empty() {
      return Answer::default();
    }
    self.post("/auth/devices", serde_json::json!({ "name": self.label }), Some(&control))
  }

  fn keep(&mut self, token: &str) {
    let _ = write_private(&self.token_file, token);
  }

  fn wait(&mut self, ms: u64) {
    std::thread::sleep(Duration::from_millis(ms));
  }
}

/// What this computer is called, for the label: on macOS the Computer Name in Sharing ("Mira's MacBook Air"), elsewhere
/// the host name. Empty when neither can be read, which [`label`] turns into plain "Nouride Home".
pub fn computer_name() -> String {
  #[cfg(target_os = "macos")]
  let named = std::process::Command::new("/usr/sbin/scutil").args(["--get", "ComputerName"]).output();
  #[cfg(not(target_os = "macos"))]
  let named = std::process::Command::new("hostname").output();
  named.ok().filter(|o| o.status.success()).map(|o| String::from_utf8_lossy(&o.stdout).trim().to_owned()).unwrap_or_default()
}

/// Writes `text` to a file only this user can read, in place of what it held.
fn write_private(path: &PathBuf, text: &str) -> io::Result<()> {
  let mut options = fs::OpenOptions::new();
  options.write(true).create(true).truncate(true);
  #[cfg(unix)]
  std::os::unix::fs::OpenOptionsExt::mode(&mut options, 0o600);
  options.open(path)?.write_all(text.as_bytes())
}

#[cfg(test)]
mod tests {
  use super::*;

  #[test]
  fn the_session_cookie_is_found_among_a_trades_headers() {
    let cookie = session_cookie(["other=1; Path=/", "nouride_session=tok%2Ben12345; Path=/; HttpOnly; SameSite=Lax; Max-Age=604800"]);
    assert_eq!(cookie.as_deref(), Some("nouride_session=tok%2Ben12345"));
    assert_eq!(session_cookie(["nouride_session=; Max-Age=0"]), None);
    assert_eq!(session_cookie([]), None);
  }

  #[test]
  fn the_watch_script_watches_only_this_nourides_sign_in_page() {
    let script = watch_script("http://127.0.0.1:18264/");
    assert!(script.contains(r#"location.origin !== "http://127.0.0.1:18264""#), "{script}");
    assert!(script.contains(r#"=== "/dashboard/login""#), "{script}");
    assert!(script.contains("location.replace(location.href)"));
  }

  #[test]
  fn the_token_is_kept_for_this_user_alone_and_replaced_whole() {
    let dir = std::env::temp_dir().join(format!("device-sign-in-{}", std::process::id()));
    fs::create_dir_all(&dir).unwrap();
    let mut local = Local {
      origin: "http://127.0.0.1:9".into(),
      control_token: dir.join("missing"),
      token_file: dir.join("device.token"),
      label: label("test"),
    };
    assert_eq!(local.kept(), None);
    local.keep("ndev_a_long_first_token");
    local.keep("ndev_b");
    assert_eq!(local.kept().as_deref(), Some("ndev_b"));
    #[cfg(unix)]
    {
      use std::os::unix::fs::PermissionsExt;
      assert_eq!(fs::metadata(&local.token_file).unwrap().permissions().mode() & 0o777, 0o600);
    }
    // No control token: nothing is asked of Nouride, and the round gives up.
    assert_eq!(local.mint(), Answer::default());
    fs::remove_dir_all(&dir).unwrap();
  }
}
