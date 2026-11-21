# A file downloaded from the dashboard

Both apps show Nouride's dashboard inside a window of their own, and the dashboard's Download buttons (a file in
System → Files, a chat's attachment, a reply's "Download all") hand the window a file to keep. A browser would keep it
in its own list of downloads; the apps put it in the person's Downloads folder themselves, under its own name, and say
so in a line. Cases: `fixtures/downloads.json`.

## Which downloads are kept

`source(url, origin)`, where `origin` is the Nouride the window shows (this device's, or the address of another
computer, `remote-address.md`):

- `save`: an `http` or `https` address on that origin, and a `blob:` the dashboard made on it ("Download all" builds
  its `.zip` in the page). Only these are fetched with the dashboard's session.
- `outside`: an `http` or `https` address anywhere else. It goes to the browser, as every other link outside Nouride
  does (`link-policy.md`): the dashboard's session is never sent to another machine.
- `refused`: anything else, a `blob:` made by another origin included, and `data:`, `file:` or `javascript:`.

Origins are compared as a browser does: scheme, host (any case) and port, the scheme's own port written or not.

## Its name

`name(given, disposition, url)`, the first of these that is there:

1. `given`: the name the page gave the link (`<a download="report.pdf">`), when it is not empty.
2. `disposition`: the server's `Content-Disposition`, its `filename*` (`UTF-8''` and percent-encoded) before its
   `filename` (quoted or not, `\"` inside quotes read as `"`).
3. The last part of the URL's path, percent-decoded, for an `http` or `https` URL. A `blob:`'s is a random id: never.
4. `download`.

Then it is made safe to be a file of its own in Downloads, whatever the server sent: `/` and `\` become `_`, so a
name is never a path; control characters go; `:`, `*`, `?`, `"`, `<`, `>`, `|` become `_` (a phone's shared storage
and other systems refuse them); dots and spaces at either end go, so it is never hidden; a name longer than 200
characters is cut before its extension. A name left empty is `download`.

## A second copy

A file is never written over. When the name is taken, the copy gets the first free number before its extension, as
browsers write it: `report.pdf`, then `report (1).pdf`, `report (2).pdf`. The extension is what follows the last dot,
`.tar.gz` (and `.tar.bz2`, `.tar.xz`, `.tar.zst`) counted as one; a name with no dot gets the number at its end.

## A download that fails

What it wrote is removed, and the line says it did not download: no part of a file is left in Downloads under the
file's own name, where it would look whole. A download cancelled half way is one that failed.

## Where the apps differ

- Two downloads of one address at once (the same file pressed twice) on the desktop: WebKit says which ended only by
  its address, so which of the two failed cannot be told, and a failed one is left in Downloads rather than remove a
  file that may be the other's. Android writes each download itself and always knows which failed.
