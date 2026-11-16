//! Whether other devices on the network may open Nouride, as the install's `.env` says it: `HOST=0.0.0.0` answers
//! them, `HOST=127.0.0.1` only this computer (the app's own window is always on the loopback). `nouride start` reads
//! its `.env` each time and `HOST` there wins over `config.toml`, so Connect's switch is this line and a restart.
//! Without the line, Nouride's own default holds: the loopback. Android's `NetworkAccess`, with its tests.

const KEY: &str = "HOST=";
pub const EVERYONE: &str = "0.0.0.0";
pub const THIS_COMPUTER: &str = "127.0.0.1";

pub fn open(env: &str) -> bool {
  let Some(line) = env.split('\n').map(str::trim).filter(|l| l.starts_with(KEY)).last() else { return false };
  let host = line[KEY.len()..].trim().trim_matches(|c| c == '"' || c == '\'');
  !host.is_empty() && host != THIS_COMPUTER && host != "localhost" && host != "::1"
}

/// The `.env` with its `HOST` set for `open`; every other line as it was.
pub fn with_access(env: &str, open: bool) -> String {
  let line = format!("{KEY}{}", if open { EVERYONE } else { THIS_COMPUTER });
  let lines: Vec<&str> = env.split('\n').collect();
  if lines.iter().any(|l| l.trim().starts_with(KEY)) {
    return lines.iter().map(|l| if l.trim().starts_with(KEY) { line.as_str() } else { l }).collect::<Vec<_>>().join("\n");
  }
  let mut out = env.to_owned();
  if !out.is_empty() && !out.ends_with('\n') {
    out.push('\n');
  }
  out.push_str(&line);
  out.push('\n');
  out
}

#[cfg(test)]
mod tests {
  use super::*;

  const INSTALLED: &str = "PORT=18254\nHOST=0.0.0.0\n";

  #[test]
  fn an_open_host_lets_other_devices_in_the_loopback_keeps_nouride_to_this_computer() {
    assert!(open(INSTALLED));
    assert!(!open("PORT=18254\nHOST=127.0.0.1\n"));
    assert!(!open("HOST=\"localhost\""));
    assert!(!open("PORT=18254\n"));
    assert!(open("HOST=192.168.1.23"));
  }

  #[test]
  fn the_switch_changes_the_host_line_and_nothing_else() {
    assert_eq!(with_access(INSTALLED, false), "PORT=18254\nHOST=127.0.0.1\n");
    assert_eq!(with_access("PORT=18254\nHOST=127.0.0.1\n", true), INSTALLED);
    assert_eq!(with_access("PORT=18254", false), "PORT=18254\nHOST=127.0.0.1\n");
    assert!(!open(&with_access(INSTALLED, false)));
    assert!(open(&with_access(&with_access(INSTALLED, false), true)));
  }

  #[test]
  fn the_last_host_line_wins_as_it_does_for_nouride() {
    assert!(!open("HOST=0.0.0.0\nHOST=127.0.0.1\n"));
  }
}
