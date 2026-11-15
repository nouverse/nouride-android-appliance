//! Nouride's update routes on this computer's loopback, signed in with the install's control token: what `nouride`
//! commands on the computer sign in with (`cli/client.ts` in Nouride), so it needs no dashboard account. Blocking: off
//! the main thread. The token is read for each call and sent only to this computer's Nouride; never logged or shown.

use std::fs;
use std::io;
use std::path::PathBuf;
use std::time::Duration;

use crate::nouride::{self, Download, NourideStatus, Restart};

pub struct Client {
  /// This computer's Nouride, `http://127.0.0.1:<port>`.
  pub origin: String,
  /// `<install>/.nouride/data/.control-token`.
  pub token_path: PathBuf,
}

fn other(message: impl Into<String>) -> io::Error {
  io::Error::other(message.into())
}

impl Client {
  /// Whether Nouride is updated, and to what. `force` asks the release channel now, past Nouride's cache.
  pub fn status(&self, force: bool) -> io::Result<NourideStatus> {
    let body = self.call("GET", if force { "/update?force=1" } else { "/update" })?;
    nouride::status(&body).ok_or_else(|| other("Nouride's update answer could not be read"))
  }

  /// Starts the download, or picks it up where it stopped. Returns at once; `progress` follows it.
  pub fn download(&self) -> io::Result<Download> {
    nouride::download(&self.call("POST", "/update/download")?).ok_or_else(|| other("no download in Nouride's answer"))
  }

  pub fn progress(&self) -> io::Result<Download> {
    nouride::download(&self.call("GET", "/update/progress")?).ok_or_else(|| other("no download in Nouride's answer"))
  }

  /// Stops the download, keeping what it has: `download` then goes on from there, on a new connection.
  pub fn pause(&self) -> io::Result<()> {
    self.call("POST", "/update/pause").map(|_| ())
  }

  /// Puts the downloaded version in. Nouride restarts on its own, when [`Restart`] says.
  pub fn install(&self) -> io::Result<Restart> {
    Ok(nouride::restart(&self.call("POST", "/update/install")?))
  }

  fn call(&self, method: &str, path: &str) -> io::Result<String> {
    let token = fs::read_to_string(&self.token_path)?.trim().to_owned();
    if token.is_empty() {
      return Err(other("Nouride's control token is empty"));
    }
    let agent: ureq::Agent = ureq::Agent::config_builder()
      .timeout_connect(Some(Duration::from_secs(5)))
      // The check asks GitHub, and the install swaps the binary before it answers: either can take a while.
      .timeout_global(Some(Duration::from_secs(120)))
      .http_status_as_error(false)
      .build()
      .into();
    let url = format!("{}/api/control{path}", self.origin);
    let response = if method == "POST" {
      agent
        .post(&url)
        .header("x-nouride-token", &token)
        .header("Accept", "application/json")
        .header("Content-Type", "application/json")
        .send("{}")
    } else {
      agent.get(&url).header("x-nouride-token", &token).header("Accept", "application/json").call()
    }
    .map_err(|e| other(format!("{method} {path}: {e}")))?;
    let code = response.status().as_u16();
    let body = response.into_body().read_to_string().unwrap_or_default();
    if !(200..300).contains(&code) {
      return Err(other(format!("HTTP {code} from Nouride's {method} {path}")));
    }
    Ok(body)
  }
}
