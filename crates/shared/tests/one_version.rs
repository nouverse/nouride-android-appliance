//! Nouride Home has one version, in the repository's VERSION, for both apps. `scripts/bump-version` writes it into
//! every file of the desktop that states it; this fails when one of them was left behind or edited by hand.

use std::fs;
use std::path::PathBuf;

fn repo() -> PathBuf {
  PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../..")
}

fn version_file() -> String {
  fs::read_to_string(repo().join("VERSION")).unwrap().trim().to_owned()
}

#[test]
fn version_is_a_release_version() {
  assert!(shared::version::parse(&version_file()).is_some(), "VERSION is not x.y.z");
}

#[test]
fn the_crates_carry_the_version_file() {
  // Every crate, the Tauri binary included, takes `version.workspace = true`.
  assert_eq!(env!("CARGO_PKG_VERSION"), version_file(), "Cargo.toml [workspace.package]: run scripts/bump-version");
  let binary = fs::read_to_string(repo().join("apps/desktop/src-tauri/Cargo.toml")).unwrap();
  assert!(binary.contains("\nversion.workspace = true\n"), "apps/desktop/src-tauri/Cargo.toml has a version of its own");
}

#[test]
fn the_lock_carries_the_version_file() {
  let lock = fs::read_to_string(repo().join("Cargo.lock")).unwrap();
  let own: Vec<&str> = lock.split("[[package]]").skip(1).filter(|block| !block.contains("\nsource = ")).collect();
  assert!(own.len() >= 8, "the workspace's own crates are missing from Cargo.lock");
  for block in own {
    let line = block.lines().find(|l| l.starts_with("version = ")).unwrap();
    assert_eq!(line, format!("version = \"{}\"", version_file()), "Cargo.lock: {}", block.lines().nth(1).unwrap_or(""));
  }
}

#[test]
fn tauri_carries_the_version_file() {
  let conf: serde_json::Value =
    serde_json::from_str(&fs::read_to_string(repo().join("apps/desktop/src-tauri/tauri.conf.json")).unwrap()).unwrap();
  assert_eq!(conf["version"], version_file().as_str(), "tauri.conf.json: run scripts/bump-version");
}

#[test]
fn the_android_build_reads_the_version_file() {
  let gradle = fs::read_to_string(repo().join("apps/android/app/build.gradle.kts")).unwrap();
  assert!(gradle.contains("\"../../VERSION\""), "apps/android/app/build.gradle.kts no longer reads VERSION");
}
