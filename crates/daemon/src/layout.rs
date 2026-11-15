//! Where the app keeps Nouride and everything it made for it, all under one folder of its own
//! (`~/Library/Application Support/<bundle id>` on macOS). Nothing of it is outside that folder but the LaunchAgent.

use std::path::{Path, PathBuf};

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Layout {
  pub data: PathBuf,
}

impl Layout {
  pub fn new(data: impl Into<PathBuf>) -> Layout {
    Layout { data: data.into() }
  }

  /// Nouride's install directory, the installer's `--dir`.
  pub fn install(&self) -> PathBuf {
    self.data.join("nouride")
  }

  pub fn binary(&self) -> PathBuf {
    self.install().join("nouride")
  }

  /// Whether the app has installed Nouride here: the first question is then never asked.
  pub fn installed(&self) -> bool {
    self.binary().is_file()
  }

  /// The `HOME` the installer and `nouride service install` run with, so what they put under a home lands here:
  /// the secret store's key (`.config/nouride/nouride.env`), and nothing in the person's own home.
  pub fn home(&self) -> PathBuf {
    self.data.join("home")
  }

  pub fn env_file(&self) -> PathBuf {
    self.install().join(".env")
  }

  /// The `config.toml` Nouride reads, in its loader's order: `.nouride/config.toml`, then one beside the install; the
  /// first when neither is there yet (the installer's `gateway_config`).
  pub fn config_toml(&self) -> PathBuf {
    let inner = self.install().join(".nouride").join("config.toml");
    let beside = self.install().join("config.toml");
    if !inner.exists() && beside.exists() { beside } else { inner }
  }

  /// What `nouride` commands on this computer sign in with (`cli/client.ts` in Nouride).
  pub fn control_token(&self) -> PathBuf {
    self.install().join(".nouride").join("data").join(".control-token")
  }

  /// Nouride's mark of an update not confirmed yet (`modules/update/confirm.ts` in Nouride). While it is there, every
  /// start is counted by the plist's guard, and the fourth puts the previous version back, which then refuses the data
  /// the new one migrated: seen on a Mac, after Connect's switch was flipped twice and Nouride turned off and on within
  /// two minutes of an update. Nouride removes it once it has run healthy for five minutes.
  pub fn update_pending(&self) -> PathBuf {
    self.install().join(".nouride-pending-update")
  }

  /// Whether the app may restart Nouride now: never while an update is still proving itself.
  pub fn may_restart(&self) -> bool {
    !self.update_pending().exists()
  }

  /// Where launchd writes Nouride's output (the plist's `StandardOutPath`).
  pub fn nouride_log(&self) -> PathBuf {
    self.install().join(".nouride").join("data").join("nouride.log")
  }

  /// The plist `nouride service install --kind launchd` writes into the install folder.
  pub fn generated_plist(&self) -> PathBuf {
    self.install().join("tech.nouverse.nouride.plist")
  }

  /// The installer, as downloaded, and the folder of links it runs with on its `PATH`.
  pub fn installer_dir(&self) -> PathBuf {
    self.data.join("installer")
  }

  pub fn installer_script(&self) -> PathBuf {
    self.installer_dir().join("install.sh")
  }

  pub fn shim(&self) -> PathBuf {
    self.installer_dir().join("bin")
  }

  pub fn logs(&self) -> PathBuf {
    self.data.join("logs")
  }

  pub fn installer_log(&self) -> PathBuf {
    self.logs().join("install.log")
  }

  pub fn app_log(&self) -> PathBuf {
    self.logs().join("app.log")
  }

  pub fn prefs(&self) -> PathBuf {
    self.data.join("prefs.json")
  }

  /// The first account's password, kept by the app alone (0600), never shown.
  pub fn admin_secret(&self) -> PathBuf {
    self.data.join("admin.secret")
  }

  pub fn is_inside(&self, path: &Path) -> bool {
    path.starts_with(&self.data)
  }
}

#[cfg(test)]
mod tests {
  use super::*;

  #[test]
  fn everything_the_app_makes_is_inside_its_own_folder() {
    let layout = Layout::new("/Users/x/Library/Application Support/tech.nouverse.nouride.home.dev");
    for path in [
      layout.install(),
      layout.binary(),
      layout.home(),
      layout.env_file(),
      layout.config_toml(),
      layout.control_token(),
      layout.nouride_log(),
      layout.generated_plist(),
      layout.installer_script(),
      layout.shim(),
      layout.installer_log(),
      layout.app_log(),
      layout.prefs(),
      layout.admin_secret(),
    ] {
      assert!(layout.is_inside(&path), "{}", path.display());
    }
  }

  #[test]
  fn nouride_is_not_restarted_while_an_update_proves_itself() {
    let dir = std::env::temp_dir().join(format!("daemon-layout-pending-{}", std::process::id()));
    let _ = std::fs::remove_dir_all(&dir);
    let layout = Layout::new(&dir);
    std::fs::create_dir_all(layout.install()).unwrap();
    assert!(layout.may_restart());
    std::fs::write(layout.update_pending(), r#"{"from":"0.8.37","to":"0.8.38"}"#).unwrap();
    assert!(!layout.may_restart());
  }

  #[test]
  fn config_toml_is_looked_for_in_the_loaders_order() {
    let dir = std::env::temp_dir().join(format!("daemon-layout-{}", std::process::id()));
    let _ = std::fs::remove_dir_all(&dir);
    let layout = Layout::new(&dir);
    assert_eq!(layout.config_toml(), layout.install().join(".nouride/config.toml"));
    std::fs::create_dir_all(layout.install()).unwrap();
    std::fs::write(layout.install().join("config.toml"), "").unwrap();
    assert_eq!(layout.config_toml(), layout.install().join("config.toml"));
    std::fs::create_dir_all(layout.install().join(".nouride")).unwrap();
    std::fs::write(layout.install().join(".nouride/config.toml"), "").unwrap();
    assert_eq!(layout.config_toml(), layout.install().join(".nouride/config.toml"));
  }
}
