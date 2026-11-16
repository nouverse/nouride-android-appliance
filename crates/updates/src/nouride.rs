//! `GET /api/control/update` for the version and whether a newer one is out, `POST /update/download` and
//! `/update/progress` to fetch it, `POST /update/install` to put it in. Nouride checks the release's signature and
//! sha256, swaps its binary and exits, and launchd starts it again. Pure, so every answer is tested without a daemon.

use serde::Serialize;
use serde_json::Value;
use shared::NourideStep;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "snake_case")]
pub enum DownloadStatus {
  Idle,
  Downloading,
  Paused,
  Ready,
  Failed,
}

/// Nouride's own download of a new version, as its update routes report it.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub struct Download {
  pub status: DownloadStatus,
  pub received: u64,
  pub total: Option<u64>,
}

impl Download {
  /// 0 to 100; 0 while the size is not known yet.
  pub fn percent(&self) -> u8 {
    match self.total {
      Some(total) if total > 0 => (self.received.saturating_mul(100) / total).min(100) as u8,
      _ => 0,
    }
  }
}

/// What Nouride says about its own version, as much of it as the Versions screen shows.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub struct NourideStatus {
  /// The version running.
  pub current: Option<String>,
  /// The newer version it can take; `None` when it has the newest, or could not ask.
  pub ready: Option<String>,
  /// Whether this install can update itself at all.
  pub can_update: bool,
  /// The release channel did not answer: "could not check", never "up to date".
  pub check_failed: bool,
  pub download: Download,
}

/// When the new version takes over, as Nouride's answer to the install says.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "snake_case")]
pub enum Restart {
  Now,
  Idle,
  Jobs,
}

fn object(json: &str) -> Option<Value> {
  serde_json::from_str::<Value>(json).ok().filter(Value::is_object)
}

fn text(value: Option<&Value>, key: &str) -> Option<String> {
  value?.get(key)?.as_str().filter(|s| !s.trim().is_empty()).map(str::to_owned)
}

fn strip_v(version: String) -> String {
  version.strip_prefix('v').map(str::to_owned).unwrap_or(version)
}

fn download_of(value: Option<&Value>) -> Download {
  let status = match value.and_then(|v| v.get("status")).and_then(Value::as_str) {
    Some("downloading") => DownloadStatus::Downloading,
    Some("paused") => DownloadStatus::Paused,
    Some("ready") => DownloadStatus::Ready,
    Some("failed") => DownloadStatus::Failed,
    _ => DownloadStatus::Idle,
  };
  Download {
    status,
    received: value.and_then(|v| v.get("received")).and_then(Value::as_u64).unwrap_or(0),
    total: value.and_then(|v| v.get("total")).and_then(Value::as_u64),
  }
}

/// The answer to `GET /update`; `None` when it is not one.
pub fn status(json: &str) -> Option<NourideStatus> {
  let root = object(json)?;
  let install = root.get("install");
  let check = root.get("check").filter(|c| c.is_object());
  let current = text(check, "current").or_else(|| text(install, "version")).map(strip_v);
  let newer = check.and_then(|c| c.get("newer")).and_then(Value::as_bool) == Some(true);
  let plan_can = check.and_then(|c| c.get("plan")).and_then(|p| p.get("can")).and_then(Value::as_bool).unwrap_or(true);
  Some(NourideStatus {
    current,
    ready: if newer { text(check, "latest").map(strip_v) } else { None },
    can_update: root.get("supported").and_then(Value::as_bool) == Some(true) && plan_can,
    check_failed: root.get("error").is_some() && check.is_none(),
    download: download_of(root.get("download")),
  })
}

/// `download` from the answer to `/update/download` or `/update/progress`; `None` when the answer has none.
pub fn download(json: &str) -> Option<Download> {
  let root = object(json)?;
  let value = root.get("download").filter(|d| d.is_object())?;
  Some(download_of(Some(value)))
}

/// When the new version takes over, from the answer to `/update/install`: right away when it does not say.
pub fn restart(json: &str) -> Restart {
  match object(json).as_ref().and_then(|r| r.get("restart")).and_then(Value::as_str) {
    Some("idle") => Restart::Idle,
    Some("jobs") => Restart::Jobs,
    _ => Restart::Now,
  }
}

/// The step Nouride's answer puts the Versions card in. A download already under way or done comes first, whoever
/// started it (this screen, or the dashboard's Update page): the card follows it rather than offering a second one.
pub fn step(status: &NourideStatus) -> NourideStep {
  match () {
    _ if status.download.status == DownloadStatus::Downloading => NourideStep::Downloading,
    _ if status.download.status == DownloadStatus::Ready && status.ready.is_some() => NourideStep::Downloaded,
    _ if status.check_failed => NourideStep::CheckFailed,
    _ if status.ready.is_none() => NourideStep::UpToDate,
    _ if !status.can_update => NourideStep::Cannot,
    _ if status.download.status == DownloadStatus::Failed => NourideStep::DownloadFailed,
    _ => NourideStep::Ready,
  }
}

/// What the card says once the new version is in, from when Nouride said it would restart.
pub fn restarting(restart: Restart) -> NourideStep {
  match restart {
    Restart::Now => NourideStep::RestartingNow,
    Restart::Idle => NourideStep::RestartingIdle,
    Restart::Jobs => NourideStep::RestartingJobs,
  }
}

#[cfg(test)]
mod tests {
  use super::*;

  const INSTALL: &str = r#"{"version":"0.8.24","edition":"router","installDir":"/srv/nouride","supervisor":"external"}"#;
  const IDLE: &str = r#"{"status":"idle","received":0,"total":null,"tag":null,"artifact":null,"error":null}"#;

  #[test]
  fn a_newer_release_is_the_version_on_offer() {
    let s = status(&format!(
      r#"{{"supported":true,"install":{INSTALL},"download":{IDLE},
        "check":{{"current":"0.8.24","latest":"v0.8.25","newer":true,"publishedAt":null,"notesUrl":null,
        "plan":{{"can":true,"from":"0.8.24","to":"0.8.25"}}}}}}"#
    ))
    .unwrap();
    assert_eq!(s.current.as_deref(), Some("0.8.24"));
    assert_eq!(s.ready.as_deref(), Some("0.8.25"));
    assert!(s.can_update);
    assert!(!s.check_failed);
    assert_eq!(s.download.status, DownloadStatus::Idle);
  }

  #[test]
  fn the_newest_release_offers_nothing_even_when_the_check_names_it_as_latest() {
    let s = status(&format!(
      r#"{{"supported":true,"install":{INSTALL},"download":{IDLE},
        "check":{{"current":"0.8.25","latest":"0.8.25","newer":false,"publishedAt":null,"notesUrl":null}}}}"#
    ))
    .unwrap();
    assert_eq!(s.current.as_deref(), Some("0.8.25"));
    assert_eq!(s.ready, None);
    assert!(!s.check_failed);
  }

  #[test]
  fn a_release_channel_that_did_not_answer_is_a_failed_check_never_up_to_date() {
    let s = status(&format!(
      r#"{{"supported":true,"install":{INSTALL},"download":{IDLE},"error":"fetch failed","plain":"Nouride could not check"}}"#
    ))
    .unwrap();
    assert!(s.check_failed);
    assert_eq!(s.ready, None);
    assert_eq!(s.current.as_deref(), Some("0.8.24"));
  }

  #[test]
  fn a_build_that_cannot_update_itself_or_a_plan_that_cannot_is_said_to_be_one() {
    let unsupported = status(&format!(r#"{{"supported":false,"install":{INSTALL},"download":{IDLE}}}"#)).unwrap();
    assert!(!unsupported.can_update);
    let cannot = status(&format!(
      r#"{{"supported":true,"install":{INSTALL},"download":{IDLE},
        "check":{{"current":"0.8.24","latest":"0.8.25","newer":true,"plan":{{"can":false,"from":"0.8.24","to":"0.8.25","reason":"x"}}}}}}"#
    ))
    .unwrap();
    assert!(!cannot.can_update);
    assert_eq!(cannot.ready.as_deref(), Some("0.8.25"));
  }

  #[test]
  fn a_downloads_percent_comes_from_its_bytes_and_is_0_while_its_size_is_unknown() {
    let going = download(r#"{"ok":true,"download":{"status":"downloading","received":28563088,"total":57126176}}"#).unwrap();
    assert_eq!(going.status, DownloadStatus::Downloading);
    assert_eq!(going.percent(), 50);
    let unknown = download(r#"{"download":{"status":"downloading","received":1000,"total":null}}"#).unwrap();
    assert_eq!(unknown.total, None);
    assert_eq!(unknown.percent(), 0);
    assert_eq!(download(r#"{"download":{"status":"ready","received":5,"total":5}}"#).unwrap().status, DownloadStatus::Ready);
    assert_eq!(download(r#"{"download":{"status":"failed","received":0,"total":null}}"#).unwrap().status, DownloadStatus::Failed);
    assert_eq!(download(r#"{"ok":true}"#), None);
    assert_eq!(download("not json"), None);
  }

  #[test]
  fn the_installs_answer_says_when_nouride_restarts() {
    assert_eq!(restart(r#"{"ok":true,"to":"0.8.25","restart":"now"}"#), Restart::Now);
    assert_eq!(restart(r#"{"ok":true,"to":"0.8.25","restart":"idle"}"#), Restart::Idle);
    assert_eq!(restart(r#"{"ok":true,"to":"0.8.25","restart":"jobs"}"#), Restart::Jobs);
    assert_eq!(restart(r#"{"ok":true,"to":"0.8.25"}"#), Restart::Now);
  }

  #[test]
  fn an_answer_that_is_not_one_is_none_not_up_to_date() {
    assert_eq!(status("<html>"), None);
  }

  fn with(ready: Option<&str>, can: bool, failed: bool, download: DownloadStatus) -> NourideStatus {
    NourideStatus {
      current: Some("0.8.24".into()),
      ready: ready.map(str::to_owned),
      can_update: can,
      check_failed: failed,
      download: Download { status: download, received: 0, total: None },
    }
  }

  #[test]
  fn a_newer_nouride_is_offered_and_a_fetched_one_is_ready_to_install() {
    assert_eq!(step(&with(Some("0.8.25"), true, false, DownloadStatus::Idle)), NourideStep::Ready);
    assert_eq!(step(&with(Some("0.8.25"), true, false, DownloadStatus::Ready)), NourideStep::Downloaded);
  }

  #[test]
  fn a_download_under_way_is_followed_whoever_started_it() {
    assert_eq!(step(&with(Some("0.8.25"), true, false, DownloadStatus::Downloading)), NourideStep::Downloading);
  }

  #[test]
  fn a_check_that_failed_the_newest_version_and_an_update_it_cannot_take_each_say_so() {
    assert_eq!(step(&with(None, true, true, DownloadStatus::Idle)), NourideStep::CheckFailed);
    assert_eq!(step(&with(None, true, false, DownloadStatus::Idle)), NourideStep::UpToDate);
    assert_eq!(step(&with(Some("0.8.25"), false, false, DownloadStatus::Idle)), NourideStep::Cannot);
    assert_eq!(step(&with(Some("0.8.25"), true, false, DownloadStatus::Failed)), NourideStep::DownloadFailed);
    assert_eq!(step(&with(Some("0.8.25"), true, false, DownloadStatus::Paused)), NourideStep::Ready);
  }

  #[test]
  fn the_words_after_the_install_follow_when_nouride_said_it_restarts() {
    assert_eq!(restarting(Restart::Now), NourideStep::RestartingNow);
    assert_eq!(restarting(Restart::Idle), NourideStep::RestartingIdle);
    assert_eq!(restarting(Restart::Jobs), NourideStep::RestartingJobs);
  }
}
