//! The dashboard window's own history, read from WebKit as the phone reads its WebView's: what `link_policy::back`
//! decides from. A page the dashboard moves to itself (its router) is an entry here too, which the window's
//! navigation handler never sees. Only a Mac's window is read today.

use tauri::webview::PlatformWebview;

/// The window's pages, oldest first, and which one is on screen. Called where `with_webview` runs: the main thread.
#[cfg(target_os = "macos")]
pub fn read(webview: &PlatformWebview) -> (Vec<String>, usize) {
  // SAFETY: on the main thread, with the window's WKWebView alive for as long as `with_webview` runs its closure.
  unsafe {
    let list = view(webview).backForwardList();
    let mut pages: Vec<String> = list.backList().to_vec().iter().map(|item| address(item)).collect();
    let current = pages.len();
    if let Some(item) = list.currentItem() {
      pages.push(address(&item));
    }
    (pages, current)
  }
}

/// A page back, as WebKit's own Back does it.
#[cfg(target_os = "macos")]
pub fn go_back(webview: &PlatformWebview) {
  // SAFETY: as `read`.
  unsafe {
    let _ = view(webview).goBack();
  }
}

#[cfg(target_os = "macos")]
unsafe fn view(webview: &PlatformWebview) -> &objc2_web_kit::WKWebView {
  &*webview.inner().cast()
}

#[cfg(target_os = "macos")]
unsafe fn address(item: &objc2_web_kit::WKBackForwardListItem) -> String {
  item.URL().absoluteString().map(|s| s.to_string()).unwrap_or_default()
}

/// No other system's window is read yet: with no history, Back never goes back a page there, and asks.
#[cfg(not(target_os = "macos"))]
pub fn read(_webview: &PlatformWebview) -> (Vec<String>, usize) {
  (Vec::new(), 0)
}

#[cfg(not(target_os = "macos"))]
pub fn go_back(_webview: &PlatformWebview) {}
