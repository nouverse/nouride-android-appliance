//! The dashboard account the app makes for the person, so the dashboard opens signed in. Android's `FirstAdmin`: the
//! documented way, `INITIAL_ADMIN_USERNAME` and `INITIAL_ADMIN_PASSWORD` in the install's `.env` (`users/bootstrap.ts`
//! in Nouride), taken out again once Nouride is ready, with `INITIAL_ADMIN_MUST_CHANGE=1` beside them: the dashboard
//! opens on choosing the person's own username and password (`/welcome`). The app signs in with its password each
//! time it opens the dashboard; once the person has chosen their own, that fails once with 401 and the app forgets its
//! password.

use std::time::Duration;

pub const USERNAME: &str = "admin";
const PREFIX: &str = "INITIAL_ADMIN_";
pub const SESSION: &str = "nouride_session";

/// Hex, 24 characters: above Nouride's password floor, and nothing a `.env` parser could misread.
pub fn new_password() -> String {
  let mut bytes = [0u8; 12];
  getrandom::fill(&mut bytes).expect("the system's random source");
  bytes.iter().map(|b| format!("{b:02x}")).collect()
}

/// The `.env` with the account in it, in place of any `INITIAL_ADMIN_*` it had.
pub fn with_account(env: &str, password: &str) -> String {
  let mut out = without_account(env);
  if !out.is_empty() && !out.ends_with('\n') {
    out.push('\n');
  }
  out.push_str(&format!("{PREFIX}USERNAME={USERNAME}\n{PREFIX}PASSWORD={password}\n{PREFIX}MUST_CHANGE=1\n"));
  out
}

/// The `.env` without any `INITIAL_ADMIN_*` line; every other line exactly as it was.
pub fn without_account(env: &str) -> String {
  env.split('\n').filter(|line| !line.trim_start().starts_with(PREFIX)).collect::<Vec<_>>().join("\n")
}

/// Whether the app forgets its password after a sign-in that answered `code`. A 401 means the person chose their own;
/// kept, every opening of the dashboard was another failed sign-in, spending the allowance the person's own share.
pub fn forgets(code: u16) -> bool {
  code == 401
}

/// The session cookie, `nouride_session=<token>`, from a sign-in's `Set-Cookie` headers; `None` when there is none.
pub fn session_cookie<'a>(set_cookie: impl IntoIterator<Item = &'a str>) -> Option<String> {
  set_cookie
    .into_iter()
    .map(|header| header.split(';').next().unwrap_or("").trim().to_owned())
    .find(|pair| pair.starts_with(&format!("{SESSION}=")) && pair.len() > SESSION.len() + 1)
}

/// What a sign-in answered.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct SignIn {
  /// 0 when nothing answered.
  pub code: u16,
  /// `nouride_session=<token>`.
  pub cookie: Option<String>,
}

/// Signs in at `login_url` with the app's account. Blocking.
pub fn sign_in(login_url: &str, password: &str) -> SignIn {
  let agent: ureq::Agent = ureq::Agent::config_builder()
    .timeout_global(Some(Duration::from_secs(10)))
    .http_status_as_error(false)
    .build()
    .into();
  let body = serde_json::json!({ "username": USERNAME, "password": password }).to_string();
  match agent.post(login_url).header("Content-Type", "application/json").send(body) {
    Ok(response) => {
      let code = response.status().as_u16();
      let cookies: Vec<String> =
        response.headers().get_all("set-cookie").iter().filter_map(|v| v.to_str().ok()).map(str::to_owned).collect();
      SignIn { code, cookie: session_cookie(cookies.iter().map(String::as_str)) }
    }
    Err(_) => SignIn { code: 0, cookie: None },
  }
}

#[cfg(test)]
mod tests {
  use super::*;

  const INSTALLED: &str = "PORT=18264\nHOST=127.0.0.1\n";

  #[test]
  fn the_account_goes_into_the_env_to_be_replaced_on_the_first_screen_and_every_line_already_there_stays() {
    let env = with_account(INSTALLED, "abc123abc123abc123abc123");
    assert_eq!(
      env,
      "PORT=18264\nHOST=127.0.0.1\nINITIAL_ADMIN_USERNAME=admin\nINITIAL_ADMIN_PASSWORD=abc123abc123abc123abc123\nINITIAL_ADMIN_MUST_CHANGE=1\n"
    );
    assert!(with_account("PORT=18264", "p").starts_with("PORT=18264\nINITIAL_ADMIN_USERNAME=admin\n"));
  }

  #[test]
  fn a_second_start_replaces_the_account_rather_than_adding_another() {
    let twice = with_account(&with_account(INSTALLED, "first"), "second");
    assert_eq!(twice.matches("INITIAL_ADMIN_PASSWORD").count(), 1);
    assert!(twice.contains("INITIAL_ADMIN_PASSWORD=second\n"));
  }

  #[test]
  fn once_nouride_is_ready_the_password_leaves_the_file_and_nothing_else_does() {
    let after = without_account(&with_account(INSTALLED, "secret-password-value"));
    assert_eq!(after, INSTALLED);
    assert!(!after.contains("secret-password-value"));
  }

  #[test]
  fn passwords_are_long_hex_and_not_the_same_twice() {
    let one = new_password();
    assert_eq!(one.len(), 24);
    assert!(one.chars().all(|c| c.is_ascii_hexdigit() && !c.is_ascii_uppercase()), "{one}");
    assert_ne!(one, new_password());
  }

  #[test]
  fn the_password_is_forgotten_once_nouride_refuses_it_and_kept_through_anything_that_may_pass() {
    assert!(forgets(401));
    for code in [0, 200, 400, 403, 429, 500, 501, 503] {
      assert!(!forgets(code), "{code}");
    }
  }

  #[test]
  fn the_session_cookie_is_found_among_a_sign_ins_headers() {
    let cookie = session_cookie(["other=1; Path=/", "nouride_session=tok%2Ben12345; Path=/; HttpOnly; SameSite=Lax; Max-Age=2592000"]);
    assert_eq!(cookie.as_deref(), Some("nouride_session=tok%2Ben12345"));
    assert_eq!(session_cookie(["nouride_session=; Max-Age=0"]), None);
    assert_eq!(session_cookie([]), None);
  }
}
