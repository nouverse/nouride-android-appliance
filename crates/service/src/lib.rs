//! Keeping Nouride running when the app is closed: the app's own LaunchAgent on macOS. The plist is the one Nouride
//! itself generates (`nouride service install --kind launchd`), given the app's own label so it never stands in for a
//! Nouride the person installed by hand (`tech.nouverse.nouride`). Linux joins later as a systemd user unit behind the
//! same [`Supervisor`] trait.

pub mod launchd;

use std::io;
use std::path::Path;

/// What runs a command: the system in the app, a recording in the tests.
pub trait Exec {
  /// Runs `argv` and gives its exit code and its standard output.
  fn run(&self, argv: &[String]) -> io::Result<(i32, String)>;
}

/// The job's state, as the supervisor tells it.
#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub struct Job {
  pub loaded: bool,
  pub pid: Option<u32>,
  pub last_exit: Option<i32>,
}

/// What the app asks of whatever keeps Nouride running.
pub trait Supervisor {
  /// Puts the job definition in place (from Nouride's own `generated` one) and starts it.
  fn install_and_start(&self, generated: &str) -> io::Result<()>;
  /// Starts a job already in place.
  fn start(&self) -> io::Result<()>;
  /// Stops it, and keeps it stopped until `start`.
  fn stop(&self) -> io::Result<()>;
  /// Stops and starts it: Connect's switch.
  fn restart(&self) -> io::Result<()>;
  fn job(&self) -> io::Result<Job>;
  /// Whether its definition is in place.
  fn installed(&self) -> bool;
  /// Where its definition is, for Technical Logs.
  fn definition(&self) -> &Path;
}

/// Runs commands on this system. Never through a shell: argv as it stands.
pub struct System;

impl Exec for System {
  fn run(&self, argv: &[String]) -> io::Result<(i32, String)> {
    let (program, args) = argv.split_first().ok_or_else(|| io::Error::other("an empty command"))?;
    let output = std::process::Command::new(program).args(args).output()?;
    let mut text = String::from_utf8_lossy(&output.stdout).into_owned();
    text.push_str(&String::from_utf8_lossy(&output.stderr));
    Ok((output.status.code().unwrap_or(-1), text))
  }
}
