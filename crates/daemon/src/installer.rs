//! How the official installer is run. On macOS it writes outside its `--dir`: `~/Library/LaunchAgents/
//! tech.nouverse.nouride.plist` (written, unloaded and loaded with `launchctl`), `~/.local/bin/nouride` and
//! `~/.config/nouride/nouride.env`. A person may have a Nouride of their own there, so the app runs it with a `HOME` of
//! its own and a `PATH` with no `launchctl` on it: the installer then finds no launchd, writes no plist, loads and
//! unloads nothing, and everything it puts under a home lands in the app's folder. The app then makes its own
//! LaunchAgent (`service`). Pure but for `make_shim`.

use std::ffi::OsString;
use std::fs;
use std::io;
use std::path::{Path, PathBuf};

use shared::Build;

use crate::Layout;

/// Where a Mac keeps the tools the installer uses (`curl`, `shasum`, `tar`, `sed`...). `launchctl` is in `/bin`.
pub const SYSTEM_DIRS: &[&str] = &["/usr/bin", "/bin", "/usr/sbin", "/sbin"];

/// What the installer must never find.
pub const NEVER: &[&str] = &["launchctl", "systemctl", "sudo"];

/// The installer's arguments: the app's folder, the build's port, the loopback (Connect's switch opens it), and the
/// Router edition (the free one with its LLM gateway, as Nouride Home on Android installs).
pub fn args(build: &Build, layout: &Layout) -> Vec<OsString> {
  vec![
    "--variant".into(),
    "router".into(),
    "--dir".into(),
    layout.install().into_os_string(),
    "--port".into(),
    build.port.to_string().into(),
    "--host".into(),
    "127.0.0.1".into(),
  ]
}

/// The environment the installer runs in, whole: nothing of the app's own is passed on but `USER`, `LOGNAME`,
/// `LANG` and `TMPDIR`. No `SUDO_USER`, which would point the installer at another account's home.
pub fn env(layout: &Layout, inherited: &[(String, String)]) -> Vec<(String, OsString)> {
  let mut out: Vec<(String, OsString)> = vec![
    ("HOME".into(), layout.home().into_os_string()),
    ("PATH".into(), layout.shim().into_os_string()),
  ];
  for (key, value) in inherited {
    if ["USER", "LOGNAME", "LANG", "TMPDIR"].contains(&key.as_str()) {
      out.push((key.clone(), value.into()));
    }
  }
  out
}

/// Which links the shim holds: every program in `listing` (a system folder and what is in it, in `PATH` order),
/// the first of each name, none of [`NEVER`]. Pure.
pub fn shim_links(listing: &[(PathBuf, Vec<String>)]) -> Vec<(String, PathBuf)> {
  let mut out: Vec<(String, PathBuf)> = vec![];
  for (dir, names) in listing {
    for name in names {
      if NEVER.contains(&name.as_str()) || name.starts_with('.') || out.iter().any(|(n, _)| n == name) {
        continue;
      }
      out.push((name.clone(), dir.join(name)));
    }
  }
  out
}

/// Makes the shim: a folder of links to the system's programs, without [`NEVER`]. Made again each time, so a program
/// added by a macOS update is there.
#[cfg(unix)]
pub fn make_shim(shim: &Path, system_dirs: &[&str]) -> io::Result<usize> {
  if shim.exists() {
    fs::remove_dir_all(shim)?;
  }
  fs::create_dir_all(shim)?;
  let mut listing = vec![];
  for dir in system_dirs {
    let mut names: Vec<String> = match fs::read_dir(dir) {
      Ok(entries) => entries.filter_map(|e| e.ok()).map(|e| e.file_name().to_string_lossy().into_owned()).collect(),
      Err(_) => continue,
    };
    names.sort();
    listing.push((PathBuf::from(dir), names));
  }
  let links = shim_links(&listing);
  for (name, target) in &links {
    std::os::unix::fs::symlink(target, shim.join(name))?;
  }
  Ok(links.len())
}

/// The installer's step heading in an output line (`▸ Fetching the release`, in bold), or `None` for any other line.
pub fn heading(line: &str) -> Option<String> {
  let plain = strip_ansi(line);
  let rest = plain.trim().strip_prefix('▸')?;
  Some(rest.trim().to_owned())
}

/// A line without its terminal colours (`ESC [ ... m`).
pub fn strip_ansi(line: &str) -> String {
  let mut out = String::with_capacity(line.len());
  let mut chars = line.chars().peekable();
  while let Some(c) = chars.next() {
    if c == '\u{1b}' && chars.peek() == Some(&'[') {
      chars.next();
      for d in chars.by_ref() {
        if d.is_ascii_alphabetic() {
          break;
        }
      }
    } else {
      out.push(c);
    }
  }
  out
}

#[cfg(test)]
mod tests {
  use super::*;

  fn layout() -> Layout {
    Layout::new("/Users/x/Library/Application Support/tech.nouverse.nouride.home.dev")
  }

  #[test]
  fn the_installer_installs_into_the_apps_folder_on_the_builds_port_kept_to_this_computer() {
    let args: Vec<String> = args(&Build::DEV, &layout()).into_iter().map(|a| a.into_string().unwrap()).collect();
    assert_eq!(
      args,
      [
        "--variant",
        "router",
        "--dir",
        "/Users/x/Library/Application Support/tech.nouverse.nouride.home.dev/nouride",
        "--port",
        "18264",
        "--host",
        "127.0.0.1"
      ]
    );
  }

  #[test]
  fn the_installers_home_and_path_are_the_apps_own() {
    let inherited = vec![
      ("HOME".to_owned(), "/Users/x".to_owned()),
      ("PATH".to_owned(), "/usr/bin:/bin".to_owned()),
      ("SUDO_USER".to_owned(), "someone".to_owned()),
      ("USER".to_owned(), "x".to_owned()),
      ("TMPDIR".to_owned(), "/var/folders/t".to_owned()),
    ];
    let env = env(&layout(), &inherited);
    let get = |k: &str| env.iter().find(|(key, _)| key == k).map(|(_, v)| v.to_string_lossy().into_owned());
    assert_eq!(get("HOME").unwrap(), "/Users/x/Library/Application Support/tech.nouverse.nouride.home.dev/home");
    assert_eq!(get("PATH").unwrap(), "/Users/x/Library/Application Support/tech.nouverse.nouride.home.dev/installer/bin");
    assert_eq!(get("SUDO_USER"), None);
    assert_eq!(get("USER").as_deref(), Some("x"));
    assert_eq!(get("TMPDIR").as_deref(), Some("/var/folders/t"));
    assert_eq!(env.iter().filter(|(k, _)| k == "HOME").count(), 1);
  }

  #[test]
  fn the_shim_has_no_launchctl_and_the_first_of_each_name() {
    let listing = vec![
      (PathBuf::from("/usr/bin"), vec!["curl".into(), "shasum".into(), "sudo".into()]),
      (PathBuf::from("/bin"), vec!["sh".into(), "launchctl".into(), "curl".into()]),
      (PathBuf::from("/sbin"), vec!["md5".into(), ".hidden".into()]),
    ];
    let links = shim_links(&listing);
    let names: Vec<&str> = links.iter().map(|(n, _)| n.as_str()).collect();
    assert_eq!(names, ["curl", "shasum", "sh", "md5"]);
    assert_eq!(links[0].1, PathBuf::from("/usr/bin/curl"));
  }

  #[cfg(unix)]
  #[test]
  fn a_shim_made_from_real_folders_finds_no_launchctl() {
    let root = std::env::temp_dir().join(format!("daemon-shim-{}", std::process::id()));
    let _ = fs::remove_dir_all(&root);
    let (bin, other) = (root.join("bin"), root.join("other"));
    fs::create_dir_all(&bin).unwrap();
    fs::create_dir_all(&other).unwrap();
    for name in ["launchctl", "sh"] {
      fs::write(bin.join(name), "").unwrap();
    }
    fs::write(other.join("curl"), "").unwrap();
    let shim = root.join("shim");
    let made = make_shim(&shim, &[bin.to_str().unwrap(), other.to_str().unwrap(), "/no/such/dir"]).unwrap();
    assert_eq!(made, 2);
    assert!(!shim.join("launchctl").exists());
    assert!(shim.join("sh").exists() && shim.join("curl").exists());
    // Made again, it starts from nothing.
    fs::write(bin.join("launchctl2"), "").unwrap();
    assert_eq!(make_shim(&shim, &[bin.to_str().unwrap()]).unwrap(), 2);
  }

  #[test]
  fn the_installers_step_headings_are_read_through_its_colours() {
    assert_eq!(heading("\n\u{1b}[1m▸ Fetching the release\u{1b}[0m").as_deref(), Some("Fetching the release"));
    assert_eq!(heading("  installed /x/nouride"), None);
    assert_eq!(strip_ansi("\u{1b}[33m!\u{1b}[0m warn"), "! warn");
  }
}
