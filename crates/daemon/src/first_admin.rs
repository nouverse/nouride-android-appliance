//! The dashboard account the app makes for the person on a fresh install. Android's `FirstAdmin`: the documented way,
//! `INITIAL_ADMIN_USERNAME` and `INITIAL_ADMIN_PASSWORD` in the install's `.env` (`users/bootstrap.ts` in Nouride),
//! taken out again once Nouride is ready, with `INITIAL_ADMIN_MUST_CHANGE=1` beside them: the dashboard opens on
//! choosing the person's own username and password (`/welcome`). The app never signs in with that password: the
//! dashboard is signed in with a device token bound to this first admin (`device_sign_in`), and the setup forgets the
//! password once the account exists.

pub const USERNAME: &str = "admin";
const PREFIX: &str = "INITIAL_ADMIN_";

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
}
