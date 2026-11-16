//! The states the app moves through, shared by the crates that decide them and the one that words them.

use serde::{Deserialize, Serialize};

/// Where Nouride lives: on this computer, or on another one this computer opens.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum Where {
  Here,
  Remote,
}

impl Where {
  pub fn name(self) -> &'static str {
    match self {
      Where::Here => "HERE",
      Where::Remote => "REMOTE",
    }
  }
}

/// What Nouride on this computer is doing, as the app sees it. Android's `ServerState.Phase`, without its unpacking:
/// a computer has no Debian to unpack.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum Phase {
  Stopped,
  Installing,
  Starting,
  Running,
  Restarting,
  Stopping,
  Failed,
}

/// What another computer's Nouride said to `/health`: it answers, it does not, or a page stands in front of it.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum Reply {
  Answers,
  Down,
  Unsure,
}

/// Where the Versions screen's Nouride Home card is.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum AppStep {
  Checking,
  UpToDate,
  Ready,
  CheckFailed,
  Downloading,
  Installing,
  DownloadFailed,
  InstallFailed,
}

/// Where its Nouride card is.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum NourideStep {
  Off,
  Checking,
  UpToDate,
  Ready,
  Cannot,
  CheckFailed,
  Downloading,
  Downloaded,
  Installing,
  RestartingNow,
  RestartingIdle,
  RestartingJobs,
  DownloadFailed,
  InstallFailed,
  /// A Nouride the app did not install: its updates are its own dashboard's.
  NotOurs,
}
