//! What the other computer's Nouride said when this one asked its `/health`, in the three answers Home acts on.

use shared::{Health, Reply};

/// No answer at all (no such name, no connection, no TLS, a timeout) is down, and so is a 5xx without Nouride's own
/// body: a tunnel whose computer is off answers 502 or 530 from Cloudflare. Nouride's own body is Nouride answering,
/// whatever its status. Anything else is a page in front of it (Cloudflare Access), which the dashboard will show.
pub fn of(health: &Health) -> Reply {
  if health.http_code == 0 {
    Reply::Down
  } else if health.nouride_answered() {
    Reply::Answers
  } else if health.http_code >= 500 {
    Reply::Down
  } else {
    Reply::Unsure
  }
}

/// The other computer's `/health`, beside its dashboard.
pub fn health_url(base: &str) -> String {
  format!("{base}/health")
}

#[cfg(test)]
mod tests {
  use super::*;

  #[test]
  fn nourides_own_answer_is_nouride_answering_starting_or_not() {
    assert_eq!(of(&Health::parse(200, r#"{"status":"ok","version":"0.8.38"}"#)), Reply::Answers);
    assert_eq!(of(&Health::parse(503, r#"{"status":"starting"}"#)), Reply::Answers);
  }

  #[test]
  fn no_answer_or_a_tunnel_whose_computer_is_off_is_down() {
    assert_eq!(of(&Health::unreachable("dns error: nouride.example.com")), Reply::Down);
    assert_eq!(of(&Health::parse(530, "<html>error code: 1033</html>")), Reply::Down);
    assert_eq!(of(&Health::parse(502, "<html>Bad gateway</html>")), Reply::Down);
  }

  #[test]
  fn a_page_in_front_of_nouride_is_neither_the_dashboard_shows_it() {
    assert_eq!(of(&Health::parse(200, "<html>Sign in</html>")), Reply::Unsure);
    assert_eq!(of(&Health::parse(403, "Forbidden")), Reply::Unsure);
  }

  #[test]
  fn health_is_beside_the_dashboard() {
    assert_eq!(health_url("https://nouride.example.com"), "https://nouride.example.com/health");
  }
}
