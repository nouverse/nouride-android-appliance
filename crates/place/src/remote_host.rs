//! The address of a Nouride that runs on another computer, which this one opens instead of running its own: a
//! computer at home reached through a Cloudflare tunnel, or any machine with an HTTPS address in front of it.
//!
//! A person is handed that address once and types it, so what is taken is generous: with or without `https://`, with
//! a port, or the dashboard's own address pasted out of a chat. `https` is assumed when nothing says otherwise. `http`
//! is refused: a dashboard reached over the network is a login, and a password on the wire. Pure, so every shape a
//! person may type is tested; the cases are Android's `RemoteHostTest`, one for one.

use url::Url;

const DASHBOARD_PATH: &str = "/dashboard";
/// Long enough for a tunnel's own name, short enough that nothing else is in there.
const MAX: usize = 200;
/// What a strict URI parser (Android's `java.net.URI`) refuses and a browser's quietly mends.
const NEVER_IN_AN_ADDRESS: &[char] = &['\\', '<', '>', '"', '{', '}', '|', '^', '`'];

/// What a person typed, read: an address to open, or why it cannot be one, each with its own sentence on screen.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Read {
  Address(String),
  /// Nothing typed.
  Empty,
  /// `http://`: the one refusal a person fixes by typing `https://`, so it is said on its own.
  PlainHttp,
  /// A space, another scheme, a query or a fragment, a path that is not the dashboard's.
  NotAnAddress,
}

/// The base URL of the Nouride at `typed`, or why there is none.
pub fn read(typed: &str) -> Read {
  let text = typed.trim();
  if text.is_empty() {
    return Read::Empty;
  }
  if text.chars().count() > MAX || text.chars().any(|c| c.is_whitespace() || NEVER_IN_AN_ADDRESS.contains(&c)) {
    return Read::NotAnAddress;
  }
  let full = if text.contains("://") { text.to_owned() } else { format!("https://{text}") };
  let scheme = full.split("://").next().unwrap_or_default().to_ascii_lowercase();
  if scheme == "http" {
    return Read::PlainHttp;
  }
  if scheme != "https" {
    return Read::NotAnAddress;
  }
  let Ok(url) = Url::parse(&full) else { return Read::NotAnAddress };
  let Some(host) = url.host_str().filter(|h| !h.is_empty()) else { return Read::NotAnAddress };
  let path = url.path().strip_suffix('/').unwrap_or(url.path());
  if !path.is_empty() && path != DASHBOARD_PATH {
    return Read::NotAnAddress;
  }
  if url.query().is_some() || url.fragment().is_some() {
    return Read::NotAnAddress;
  }
  // `url` refuses a port above 65535 itself, and takes 0, which no machine listens on.
  if url.port() == Some(0) {
    return Read::NotAnAddress;
  }
  // 443 is https's own and `url` drops it: kept, the origin would not match the links the dashboard writes.
  let port = url.port().map(|p| format!(":{p}")).unwrap_or_default();
  Read::Address(format!("https://{}{port}", host.to_ascii_lowercase()))
}

/// The base URL of the Nouride at `typed`, or `None` when it cannot be one.
pub fn base(typed: &str) -> Option<String> {
  match read(typed) {
    Read::Address(base) => Some(base),
    _ => None,
  }
}

/// How an address is shown to a person: the name they typed, without the scheme every one of them has.
pub fn shown(base: &str) -> &str {
  base.strip_prefix("https://").unwrap_or(base)
}

/// The dashboard at that computer: the same path as this computer's own.
pub fn dashboard(base: &str) -> String {
  format!("{base}{DASHBOARD_PATH}/")
}

#[cfg(test)]
mod tests {
  use super::*;

  #[test]
  fn an_address_typed_without_a_scheme_is_the_tunnels_over_https() {
    assert_eq!(base("nouride.example.com").as_deref(), Some("https://nouride.example.com"));
    assert_eq!(base("  nouride.example.com/  ").as_deref(), Some("https://nouride.example.com"));
    assert_eq!(base("https://nouride.example.com").as_deref(), Some("https://nouride.example.com"));
    assert_eq!(base("HTTPS://nouride.example.com/").as_deref(), Some("https://nouride.example.com"));
  }

  #[test]
  fn a_port_is_kept_and_the_dashboards_own_address_becomes_the_machine_it_is_on() {
    assert_eq!(base("https://nouride.example.com:8443").as_deref(), Some("https://nouride.example.com:8443"));
    assert_eq!(base("nouride.example.com:8443").as_deref(), Some("https://nouride.example.com:8443"));
    assert_eq!(base("https://nouride.example.com/dashboard/").as_deref(), Some("https://nouride.example.com"));
    assert_eq!(base("https://nouride.example.com/dashboard").as_deref(), Some("https://nouride.example.com"));
    assert_eq!(base("box.tail1234.ts.net").as_deref(), Some("https://box.tail1234.ts.net"));
  }

  #[test]
  fn plain_http_is_refused_and_so_is_anything_that_is_not_an_address() {
    assert_eq!(base("http://nouride.example.com"), None);
    assert_eq!(base("http://192.168.50.20:18254"), None);
    assert_eq!(base("ftp://nouride.example.com"), None);
    assert_eq!(base(""), None);
    assert_eq!(base("   "), None);
    assert_eq!(base("nouride example.com"), None);
    assert_eq!(base("https://nouride.example.com/agents"), None);
    assert_eq!(base("https://nouride.example.com?token=1"), None);
    assert_eq!(base("https://nouride.example.com#/agents"), None);
    assert_eq!(base("https://"), None);
    assert_eq!(base(&format!("https://{}.example.com", "a".repeat(250))), None);
  }

  #[test]
  fn https_own_port_is_dropped_so_the_origin_matches_the_links_the_dashboard_writes() {
    assert_eq!(base("https://nouride.example.com:443").as_deref(), Some("https://nouride.example.com"));
    assert_eq!(base("nouride.example.com:443/dashboard/").as_deref(), Some("https://nouride.example.com"));
    assert_eq!(base("https://Nouride.Example.com").as_deref(), Some("https://nouride.example.com"));
  }

  #[test]
  fn each_refusal_says_why_so_the_screen_can_say_what_to_do() {
    assert_eq!(read("  "), Read::Empty);
    assert_eq!(read("http://nouride.example.com"), Read::PlainHttp);
    assert_eq!(read("HTTP://nouride.example.com"), Read::PlainHttp);
    assert_eq!(read("nouride example.com"), Read::NotAnAddress);
    assert_eq!(read("ftp://nouride.example.com"), Read::NotAnAddress);
    assert_eq!(read("nouride.example.com"), Read::Address("https://nouride.example.com".into()));
  }

  #[test]
  fn what_a_browser_would_mend_is_not_an_address() {
    assert_eq!(read("https://nouride.example.com\\dashboard"), Read::NotAnAddress);
    assert_eq!(read("nouride.example.com:99999"), Read::NotAnAddress);
  }

  #[test]
  fn an_address_is_shown_without_its_scheme() {
    assert_eq!(shown("https://nouride.example.com"), "nouride.example.com");
    assert_eq!(shown("https://nouride.example.com:8443"), "nouride.example.com:8443");
  }

  #[test]
  fn the_dashboard_is_the_same_path_as_this_computers_own() {
    assert_eq!(dashboard(&base("nouride.example.com").unwrap()), "https://nouride.example.com/dashboard/");
  }
}
