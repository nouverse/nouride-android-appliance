// Nouride Home's dashboard screen: a download the dashboard starts is handed to the app, which keeps it in the
// phone's Downloads (ui/DashboardDownloads.kt). The dashboard downloads by clicking a link with a `download` name:
// a file's address on Nouride (System -> Files, an attachment), or a `blob:` it made in the page ("Download all"'s
// .zip). A WebView keeps neither by itself, and the name the page gave is lost on the way to its download listener,
// so the click is caught here with that name. A blob lives only in this page, and the dashboard's own rules forbid
// fetching one (`connect-src 'self'`): each Blob is kept as the page makes its address, read here and handed over in
// pieces. Run on every page of the dashboard; running it twice changes nothing.
(function () {
  "use strict";
  var app = window.NourideHomeDownloads;
  if (!app || window.__nourideHomeDownloads) return;
  // Small enough that one piece, as base64, is an easy string to hand over.
  var PIECE = 512 * 1024;
  // The Blob behind each address the page made, until the page lets the address go.
  var blobs = new Map();
  var make = URL.createObjectURL;
  var revoke = URL.revokeObjectURL;
  URL.createObjectURL = function (object) {
    var url = make.call(URL, object);
    if (object instanceof Blob) blobs.set(url, object);
    return url;
  };
  URL.revokeObjectURL = function (url) {
    blobs.delete(url);
    return revoke.call(URL, url);
  };

  function anchor(node) {
    for (var n = node; n; n = n.parentNode) if (n.nodeName === "A" || n.nodeName === "AREA") return n;
    return null;
  }

  function readBlob(url, name) {
    var blob = blobs.get(url);
    if (!blob) return app.unread(name);
    var id = app.begin(url, name, blob.size);
    if (!id) return;
    var offset = 0;
    var next = function () {
      if (offset >= blob.size) return app.end(id);
      var reader = new FileReader();
      reader.onload = function () {
        var data = String(reader.result);
        app.piece(id, data.slice(data.indexOf(",") + 1));
        next();
      };
      reader.onerror = function () {
        app.fail(id);
      };
      reader.readAsDataURL(blob.slice(offset, offset + PIECE));
      offset += PIECE;
    };
    next();
  }

  window.__nourideHomeDownloads = readBlob;
  document.addEventListener(
    "click",
    function (event) {
      if (event.defaultPrevented || event.button !== 0) return;
      var a = anchor(event.target);
      if (!a || !a.href || !a.hasAttribute("download")) return;
      var name = a.getAttribute("download") || "";
      if (/^blob:/i.test(a.href)) {
        event.preventDefault();
        readBlob(a.href, name);
      } else if (/^https?:/i.test(a.href)) {
        event.preventDefault();
        app.fetch(a.href, name);
      }
    },
    true,
  );
})();
