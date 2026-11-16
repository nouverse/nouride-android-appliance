//! One answer of Nouride's `/health`, or why there was none. Android's `Health`, with its tests.

use serde::Serialize;

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub struct Health {
  /// 0 when nothing answered at all.
  pub http_code: u16,
  pub status: Option<String>,
  pub version: Option<String>,
  pub uptime_seconds: Option<u64>,
  pub issues: Vec<String>,
  pub error: Option<String>,
}

impl Health {
  /// Nouride answers 200 with `ok` only; `starting`, `degraded` and `shutting_down` are 503.
  pub fn ok(&self) -> bool {
    self.http_code == 200 && self.status.as_deref() == Some("ok")
  }

  /// Nouride's own body, whatever its status: something with a `status` answered.
  pub fn nouride_answered(&self) -> bool {
    self.status.is_some()
  }

  pub fn unreachable(error: impl Into<String>) -> Health {
    Health { http_code: 0, status: None, version: None, uptime_seconds: None, issues: vec![], error: Some(error.into()) }
  }

  pub fn parse(http_code: u16, body: &str) -> Health {
    let json: serde_json::Value = match serde_json::from_str(body) {
      Ok(value @ serde_json::Value::Object(_)) => value,
      _ => {
        let head: String = body.chars().take(80).collect();
        return Health { http_code, status: None, version: None, uptime_seconds: None, issues: vec![], error: Some(format!("not JSON: {head}")) };
      }
    };
    let text = |key: &str| json.get(key).and_then(|v| v.as_str()).filter(|s| !s.is_empty()).map(str::to_owned);
    Health {
      http_code,
      status: text("status"),
      version: text("version"),
      uptime_seconds: json.get("uptime").and_then(|v| v.as_u64().or_else(|| v.as_f64().map(|f| f as u64))),
      issues: json
        .get("issues")
        .and_then(|v| v.as_array())
        .map(|a| a.iter().map(|i| i.as_str().map(str::to_owned).unwrap_or_else(|| i.to_string())).collect())
        .unwrap_or_default(),
      error: None,
    }
  }

  /// For Technical Logs only.
  pub fn summary(&self) -> String {
    if let Some(error) = &self.error {
      if self.http_code == 0 {
        return format!("unreachable: {error}");
      }
    }
    match &self.status {
      None => format!("HTTP {}", self.http_code),
      Some(status) => {
        let mut out = status.clone();
        if let Some(v) = &self.version {
          out.push_str(&format!(", version {v}"));
        }
        if let Some(u) = self.uptime_seconds {
          out.push_str(&format!(", up {u}s"));
        }
        if !self.issues.is_empty() {
          out.push_str(&format!(", issues: {}", self.issues.join("; ")));
        }
        out
      }
    }
  }
}

#[cfg(test)]
mod tests {
  use super::*;

  #[test]
  fn ok_is_a_200_with_status_ok() {
    let health = Health::parse(200, r#"{"status":"ok","edition":"basic","uptime":42,"version":"0.8.18","agents":1}"#);
    assert!(health.ok());
    assert_eq!(health.version.as_deref(), Some("0.8.18"));
    assert_eq!(health.uptime_seconds, Some(42));
    assert_eq!(health.summary(), "ok, version 0.8.18, up 42s");
  }

  #[test]
  fn starting_and_degraded_are_503_and_not_ok() {
    let starting = Health::parse(503, r#"{"status":"starting","uptime":1,"version":"0.8.18"}"#);
    assert!(!starting.ok());
    assert_eq!(starting.status.as_deref(), Some("starting"));
    let degraded = Health::parse(503, r#"{"status":"degraded","uptime":9,"version":"0.8.18","issues":["telegram: dead","x"]}"#);
    assert!(!degraded.ok());
    assert_eq!(degraded.issues, vec!["telegram: dead", "x"]);
  }

  #[test]
  fn a_200_that_does_not_say_ok_is_not_ok() {
    assert!(!Health::parse(200, r#"{"status":"shutting_down"}"#).ok());
    assert!(!Health::parse(200, "<html>proxy</html>").ok());
  }

  #[test]
  fn a_body_that_is_not_json_is_reported_not_thrown() {
    let health = Health::parse(502, "Bad Gateway");
    assert_eq!(health.status, None);
    assert_eq!(health.error.as_deref(), Some("not JSON: Bad Gateway"));
    assert_eq!(health.summary(), "HTTP 502");
  }

  #[test]
  fn nothing_answering_says_why_for_the_logs() {
    let health = Health::unreachable("connection refused");
    assert!(!health.ok() && !health.nouride_answered());
    assert_eq!(health.summary(), "unreachable: connection refused");
  }
}
