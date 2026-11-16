//! Asking a Nouride's `/health`. Blocking: off the main thread.

use std::time::Duration;

use shared::Health;

pub fn fetch(url: &str, timeout: Duration) -> Health {
  let agent: ureq::Agent =
    ureq::Agent::config_builder().timeout_global(Some(timeout)).http_status_as_error(false).build().into();
  match agent.get(url).call() {
    Ok(response) => {
      let code = response.status().as_u16();
      let body = response.into_body().with_config().limit(64 * 1024).read_to_string().unwrap_or_default();
      Health::parse(code, &body)
    }
    Err(e) => Health::unreachable(e.to_string()),
  }
}

#[cfg(test)]
mod tests {
  use super::*;
  use std::io::{Read, Write};
  use std::net::TcpListener;

  fn serve_once(status: &str, body: &str) -> String {
    let listener = TcpListener::bind("127.0.0.1:0").unwrap();
    let port = listener.local_addr().unwrap().port();
    let response = format!("HTTP/1.1 {status}\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{body}", body.len());
    std::thread::spawn(move || {
      let (mut stream, _) = listener.accept().unwrap();
      let mut buf = [0u8; 1024];
      let _ = stream.read(&mut buf);
      stream.write_all(response.as_bytes()).unwrap();
    });
    format!("http://127.0.0.1:{port}/health")
  }

  #[test]
  fn a_503_while_starting_is_read_not_taken_for_no_answer() {
    let health = fetch(&serve_once("503 Service Unavailable", r#"{"status":"starting"}"#), Duration::from_secs(5));
    assert_eq!(health.http_code, 503);
    assert!(health.nouride_answered() && !health.ok());
  }

  #[test]
  fn nothing_listening_is_no_answer() {
    let port = TcpListener::bind("127.0.0.1:0").unwrap().local_addr().unwrap().port();
    let health = fetch(&format!("http://127.0.0.1:{port}/health"), Duration::from_secs(2));
    assert_eq!(health.http_code, 0);
  }
}
