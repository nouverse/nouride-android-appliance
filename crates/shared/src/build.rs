//! The names and numbers that differ between Nouride Home and Nouride Home Dev. Anything that names a port, an id, a
//! LaunchAgent label or a link scheme reads them here, never a literal: a literal in the Dev build reaches the
//! person's own Nouride.

/// Nouride's own default for its LLM gateway (`[nougate] port`), which a release leaves as it is.
pub const DEFAULT_GATEWAY_PORT: u16 = 18256;

/// The official installer: every computer gets the release every other install gets.
pub const INSTALL_URL: &str = "https://get.nouride.com/install.sh";

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Build {
  /// The bundle identifier, also the name of the app's folder.
  pub id: &'static str,
  /// What the person sees as the app's name.
  pub name: &'static str,
  /// Nouride's dashboard port.
  pub port: u16,
  /// The LLM gateway's port, written into Nouride's `config.toml` when it is not Nouride's default.
  pub gateway_port: u16,
  /// The app's own LaunchAgent, never the one Nouride's installer names (`tech.nouverse.nouride`).
  pub agent_label: &'static str,
  /// The app's link scheme.
  pub scheme: &'static str,
  /// Whether this build carries the e2e hooks and is never offered an update.
  pub dev: bool,
}

impl Build {
  pub const RELEASE: Build = Build {
    id: "tech.nouverse.nouride.home",
    name: "Nouride Home",
    port: 18254,
    gateway_port: DEFAULT_GATEWAY_PORT,
    agent_label: "tech.nouverse.nouride.home.nouride",
    scheme: "nouridehome",
    dev: false,
  };

  pub const DEV: Build = Build {
    id: "tech.nouverse.nouride.home.dev",
    name: "Nouride Home Dev",
    port: 18264,
    gateway_port: 18266,
    agent_label: "tech.nouverse.nouride.home.dev.nouride",
    scheme: "nouridehome-dev",
    dev: true,
  };

  /// The build this binary is: the `dev` feature makes it Nouride Home Dev.
  pub const fn current() -> Build {
    if cfg!(feature = "dev") {
      Build::DEV
    } else {
      Build::RELEASE
    }
  }

  /// This computer's Nouride, on the loopback.
  pub fn origin(&self) -> String {
    format!("http://127.0.0.1:{}", self.port)
  }

  pub fn health_url(&self) -> String {
    format!("{}/health", self.origin())
  }

  pub fn dashboard_url(&self) -> String {
    format!("{}/dashboard/", self.origin())
  }
}

#[cfg(test)]
mod tests {
  use super::*;

  #[test]
  fn the_dev_build_shares_nothing_with_the_release() {
    let (r, d) = (Build::RELEASE, Build::DEV);
    assert_ne!(r.id, d.id);
    assert_ne!(r.name, d.name);
    assert_ne!(r.port, d.port);
    assert_ne!(r.gateway_port, d.gateway_port);
    assert_ne!(r.agent_label, d.agent_label);
    assert_ne!(r.scheme, d.scheme);
    assert!(d.dev && !r.dev);
  }

  #[test]
  fn neither_build_names_the_installers_own_launch_agent() {
    for build in [Build::RELEASE, Build::DEV] {
      assert_ne!(build.agent_label, "tech.nouverse.nouride");
    }
  }

  #[test]
  fn the_dev_build_is_on_androids_dev_ports() {
    assert_eq!((Build::DEV.port, Build::DEV.gateway_port), (18264, 18266));
    assert_eq!((Build::RELEASE.port, Build::RELEASE.gateway_port), (18254, 18256));
  }

  #[test]
  fn addresses_follow_the_port() {
    assert_eq!(Build::DEV.health_url(), "http://127.0.0.1:18264/health");
    assert_eq!(Build::DEV.dashboard_url(), "http://127.0.0.1:18264/dashboard/");
  }

  #[test]
  fn the_feature_picks_the_build() {
    assert_eq!(Build::current(), if cfg!(feature = "dev") { Build::DEV } else { Build::RELEASE });
  }
}
