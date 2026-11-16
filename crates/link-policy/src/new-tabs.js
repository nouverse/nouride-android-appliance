// Nouride Home's dashboard window: a link that asks for a new tab is opened with `window.open` instead.
// WebKit on macOS hands such a click to neither of the app's handlers: it is dropped, and a sign-in button
// ("Sign In with Google", `<a target="_blank">`) did nothing. `window.open` reaches the app's new-window handler,
// which sends the page to the browser or keeps it here (`link_policy::new_window`). Run in the main frame, before the
// page's own scripts; it changes nothing else on the page.
(function () {
  "use strict";
  var KEEP = { "": true, _self: true, _parent: true, _top: true };
  function anchor(node) {
    for (var n = node; n; n = n.parentNode) if (n.nodeName === "A" || n.nodeName === "AREA") return n;
    return null;
  }
  document.addEventListener(
    "click",
    function (event) {
      if (event.defaultPrevented || event.button !== 0) return;
      var a = anchor(event.target);
      if (!a || !a.href || a.hasAttribute("download")) return;
      var target = (a.getAttribute("target") || "").toLowerCase();
      if (KEEP[target]) return;
      if (!/^https?:/i.test(a.href)) return;
      event.preventDefault();
      window.open(a.href, "_blank", "noopener");
    },
    true,
  );
})();
