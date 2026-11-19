//! What the dashboard's window may open itself: Nouride's own pages at whichever Nouride it is showing, nothing else.
//! Every other link leaves for the browser: a sign-in page there has the person's own session, and a stranger's page
//! never runs with the dashboard's cookie. Android's `LinkPolicy`, with its tests one for one.
//!
//! The origin is a parameter since Nouride can be on another computer: this computer's loopback, or the address a
//! person was given. The rules are the same for both, and a page of the other computer is as far outside as a
//! stranger's.

use url::Url;

/// The back button the dashboard draws in its header when it sees [`USER_AGENT_MARK`] (`commons/lib/home-app.ts` in
/// Nouride). The same for every build.
pub const HOME: &str = "nouridehome://home";

/// Added to the window's user agent, so the dashboard knows it is inside the app and shows that way back.
pub const USER_AGENT_MARK: &str = "NourideHome/1";

/// Run in the dashboard's window before its own scripts: a link that asks for a new tab is opened with `window.open`,
/// which reaches the window's new-window handler. WebKit on macOS drops a `target="_blank"` click before either
/// handler sees it, so the dashboard's sign-in buttons did nothing (`apps/desktop/tests/new-tabs.test.ts` runs it).
pub const NEW_TABS_SCRIPT: &str = include_str!("new-tabs.js");

/// Where a link the dashboard follows goes.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Link {
  /// The header's back: close the dashboard's window and bring Nouride Home forward.
  Back,
  Here,
  Outside,
}

/// What the keyboard's Back (Cmd+[) does in the dashboard's window.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Back {
  /// A page back inside the dashboard.
  Page,
  /// Nothing to go back to inside Nouride: on a phone this asks before closing; a window has its own close button.
  Ask,
  /// Over the cat, or while the dashboard opens: nothing to lose.
  Leave,
}

/// The header's back closes the window; Nouride's own pages open here, and a frame inside a page is the page's
/// business; anything else leaves for the browser.
pub fn link(url: &str, is_for_main_frame: bool, origin: &str) -> Link {
  if url == HOME {
    Link::Back
  } else if !is_for_main_frame || in_app(url, origin) {
    Link::Here
  } else {
    Link::Outside
  }
}

/// Where a page the dashboard asks a new window for goes. A window has no tabs, so nothing opens a second one.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum NewWindow {
  /// One of Nouride's own pages: the dashboard's window follows it. A path that only redirects (the gateway's
  /// `/oauth/start`, to Google) is caught as it redirects, by [`link`], and the page on screen stays.
  Here,
  /// Anything else, a provider's sign-in above all: the browser, where the person's own session is. Android's
  /// `onCreateWindow` does the same.
  Browser,
}

pub fn new_window(url: &str, origin: &str) -> NewWindow {
  if url != "about:blank" && in_app(url, origin) { NewWindow::Here } else { NewWindow::Browser }
}

/// Whether a failed load is Nouride itself not answering, shown as the cat and Try again: the page the window was
/// opening, at that Nouride. A picture or a script that failed is the page's.
pub fn unreachable(is_for_main_frame: bool, url: &str, origin: &str) -> bool {
  is_for_main_frame && url != "about:blank" && in_app(url, origin)
}

/// Whether Back goes back a page inside the dashboard: only onto an ordinary page of Nouride's, never onto one a
/// person passes once on the way in (the sign-in, choosing their first password, the setup wizard), never out of
/// Nouride, never past the first page. Never from one of those pages either: until that step is done the dashboard
/// sends every other page back to it, so Back from the first password went to `/dashboard/`, which put the person on
/// it again. `history` is the window's, oldest first, and `current` the page on screen.
pub fn back_inside(history: &[&str], current: usize, origin: &str) -> bool {
  let Some(previous) = current.checked_sub(1).and_then(|i| history.get(i)) else { return false };
  let on_screen = history.get(current).copied().unwrap_or("about:blank");
  !passed_once(on_screen) && *previous != "about:blank" && in_app(previous, origin) && !passed_once(previous)
}

pub fn back(history: &[&str], current: usize, dashboard_shown: bool, origin: &str) -> Back {
  if !dashboard_shown {
    Back::Leave
  } else if back_inside(history, current, origin) {
    Back::Page
  } else {
    Back::Ask
  }
}

const ONCE: &[&str] = &["/login", "/welcome", "/setup", "/oauth"];

/// A dashboard page a person goes through once (`redirectFor` in the dashboard's `session.ts` sends them there).
pub fn passed_once(url: &str) -> bool {
  let Ok(parsed) = Url::parse(url) else { return true };
  let path = parsed.path();
  let page = path.strip_prefix("/dashboard").unwrap_or(path).trim_end_matches('/');
  ONCE.iter().any(|once| page == *once || page.starts_with(&format!("{once}/")))
}

/// Whether `url` is a page of the Nouride at `origin`: that computer's own name, port and scheme. This computer's own
/// Nouride answers over http and one reached through a tunnel over https, and a page served the other way on the same
/// name and port is not that dashboard. `localhost` is the loopback under another name, and nothing else is.
pub fn in_app(url: &str, origin: &str) -> bool {
  if url == "about:blank" {
    return true;
  }
  let (Ok(page), Ok(here)) = (Url::parse(url), Url::parse(origin)) else { return false };
  if !page.scheme().eq_ignore_ascii_case(here.scheme()) {
    return false;
  }
  let (Some(host), Some(ours)) = (page.host_str(), here.host_str()) else { return false };
  if host != ours && !(ours == "127.0.0.1" && host == "localhost") {
    return false;
  }
  page.port_or_known_default() == here.port_or_known_default()
}

#[cfg(test)]
mod tests {
  use super::*;

  const ORIGIN: &str = "http://127.0.0.1:18264";
  const DASHBOARD: &str = "http://127.0.0.1:18264/dashboard/";

  fn page(path: &str) -> String {
    format!("{ORIGIN}/dashboard/{path}")
  }

  #[test]
  fn back_goes_to_the_dashboard_page_before_as_a_browser_does() {
    let (a, b) = (page("agents"), page("agents/nouva"));
    let history = [DASHBOARD, &a, &b];
    assert!(back_inside(&history, 2, ORIGIN));
    assert!(back_inside(&history, 1, ORIGIN));
  }

  #[test]
  fn never_a_page_back_from_the_first_password_which_the_page_before_sends_the_person_back_to() {
    let welcome = page("welcome");
    assert!(!back_inside(&[DASHBOARD, &welcome], 1, ORIGIN));
    assert_eq!(back(&[DASHBOARD, &welcome], 1, true, ORIGIN), Back::Ask);
    let (agents, login) = (page("agents"), page("login?next=%2Fagents"));
    assert!(!back_inside(&[&agents, &login], 1, ORIGIN));
  }

  #[test]
  fn from_the_first_page_back_leaves_for_the_app() {
    assert!(!back_inside(&[DASHBOARD], 0, ORIGIN));
    assert!(!back_inside(&["about:blank", DASHBOARD], 1, ORIGIN));
  }

  #[test]
  fn never_back_onto_the_sign_in_the_first_password_or_the_setup_wizard() {
    for once in ["login", "login?next=%2Fagents", "welcome", "setup", "oauth/mcp/callback?code=x"] {
      let p = page(once);
      assert!(!back_inside(&[&p, DASHBOARD], 1, ORIGIN), "{once}");
    }
    let guide = page("setupguide");
    assert!(back_inside(&[&guide, DASHBOARD], 1, ORIGIN));
  }

  #[test]
  fn never_out_of_nouride() {
    assert!(!back_inside(&["https://accounts.google.com/o/oauth2", DASHBOARD], 1, ORIGIN));
  }

  #[test]
  fn back_that_would_close_the_dashboard_asks_first_and_one_that_goes_back_a_page_does_not() {
    let agents = page("agents");
    assert_eq!(back(&[DASHBOARD, &agents], 1, true, ORIGIN), Back::Page);
    assert_eq!(back(&[DASHBOARD], 0, true, ORIGIN), Back::Ask);
    let setup = page("setup");
    assert_eq!(back(&[&setup, DASHBOARD], 1, true, ORIGIN), Back::Ask);
  }

  #[test]
  fn over_the_cat_or_while_it_opens_back_leaves_without_asking() {
    let agents = page("agents");
    assert_eq!(back(&[DASHBOARD, &agents], 1, false, ORIGIN), Back::Leave);
    assert_eq!(back(&[DASHBOARD], 0, false, ORIGIN), Back::Leave);
  }

  #[test]
  fn the_headers_back_is_caught_never_loaded() {
    assert_eq!(link(HOME, true, ORIGIN), Link::Back);
  }

  #[test]
  fn the_other_machines_pages_open_here_and_this_computers_own_are_outside() {
    let remote = "https://nouride.example.com";
    assert_eq!(link(&format!("{remote}/dashboard/agents"), true, remote), Link::Here);
    assert_eq!(link(DASHBOARD, true, remote), Link::Outside);
    assert!(in_app(&format!("{remote}/dashboard/"), remote));
    assert!(!in_app("https://elsewhere.example.com/dashboard/", remote));
    assert!(!in_app("https://nouride.example.com:8443/dashboard/", remote));
    let (first, agents) = (format!("{remote}/dashboard/"), format!("{remote}/dashboard/agents"));
    assert!(back_inside(&[&first, &agents], 1, remote));
    assert!(!back_inside(&[&first], 0, remote));
    assert_eq!(back(&[&first], 0, true, remote), Back::Ask);
  }

  #[test]
  fn nourides_pages_open_here_and_anything_else_outside() {
    assert_eq!(link(&page("agents"), true, ORIGIN), Link::Here);
    assert_eq!(link("https://accounts.google.com/o/oauth2", true, ORIGIN), Link::Outside);
    assert_eq!(link("https://www.youtube.com/embed/x", false, ORIGIN), Link::Here);
  }

  #[test]
  fn a_sign_in_asking_for_a_new_window_goes_to_the_browser() {
    // Each sign-in on the dashboard's Provider step, as the gateway answers it: ChatGPT's and Claude's go straight to
    // the provider, GitHub Copilot's device page too, Google Gemini's through the gateway's own redirect.
    for url in [
      "https://auth.openai.com/oauth/authorize?client_id=x&redirect_uri=http%3A%2F%2Flocalhost%3A1455%2Fauth%2Fcallback",
      "https://claude.ai/oauth/authorize?code=true&client_id=x",
      "https://github.com/login/device",
      "https://accounts.google.com/o/oauth2/v2/auth?client_id=x",
    ] {
      assert_eq!(new_window(url, ORIGIN), NewWindow::Browser, "{url}");
    }
    assert_eq!(new_window("about:blank", ORIGIN), NewWindow::Browser);
  }

  #[test]
  fn nourides_own_page_in_a_new_window_stays_and_its_redirect_to_google_leaves() {
    let start = format!("{ORIGIN}/llm-gateway/oauth/start");
    assert_eq!(new_window(&start, ORIGIN), NewWindow::Here);
    assert_eq!(link(&start, true, ORIGIN), Link::Here);
    assert_eq!(link("https://accounts.google.com/o/oauth2/v2/auth?client_id=x", true, ORIGIN), Link::Outside);
  }

  #[test]
  fn the_new_tabs_script_opens_with_window_open_and_leaves_ordinary_links_alone() {
    assert!(NEW_TABS_SCRIPT.contains("window.open(a.href, \"_blank\", \"noopener\")"));
    assert!(NEW_TABS_SCRIPT.contains("_self: true"));
  }

  #[test]
  fn localhost_is_the_loopback_and_the_other_builds_port_is_another_nouride() {
    assert!(in_app("http://localhost:18264/dashboard/", ORIGIN));
    assert!(!in_app("http://127.0.0.1:18254/dashboard/", ORIGIN));
    assert!(!in_app("https://127.0.0.1:18264/dashboard/", ORIGIN));
  }

  #[test]
  fn only_a_main_frame_failing_at_nourides_origin_is_nouride_not_answering() {
    assert!(unreachable(true, DASHBOARD, ORIGIN));
    assert!(!unreachable(false, DASHBOARD, ORIGIN));
    assert!(!unreachable(true, "about:blank", ORIGIN));
    assert!(!unreachable(true, "https://fonts.example.com/x.woff2", ORIGIN));
  }
}
