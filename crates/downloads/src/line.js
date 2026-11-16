// Nouride Home's dashboard window: a short line at the foot of the page, for a download the app kept in Downloads
// or could not (`downloads::line_call`). A browser shows its own list of downloads; this window has none, so this is
// how a person learns where the file went. Run before the page's own scripts; it changes nothing else on the page.
(function () {
  "use strict";
  if (window.__nourideHomeLine) return;
  var shown = null;
  var timer = 0;
  window.__nourideHomeLine = function (text) {
    if (!document.body) return;
    if (!shown) {
      shown = document.createElement("div");
      shown.setAttribute("role", "status");
      shown.style.cssText =
        "position:fixed;left:50%;bottom:24px;transform:translateX(-50%);z-index:2147483647;" +
        "max-width:min(560px,calc(100% - 32px));padding:10px 18px;border-radius:999px;" +
        "background:#2f2a3d;color:#fbf7f0;font:500 14px/1.4 -apple-system,system-ui,sans-serif;" +
        "box-shadow:0 6px 24px rgba(47,42,61,.18);pointer-events:none";
    }
    shown.textContent = text;
    document.body.appendChild(shown);
    clearTimeout(timer);
    timer = setTimeout(function () {
      shown.remove();
    }, 6000);
  };
})();
