# Closed windows give their memory back (Mac, 2026-11-16)

Gading's request: free memory when windows close. Until this change, closing the app's window or the dashboard's only
hid it, so their WebKit processes stayed for as long as the app ran. Now closing either destroys it; the app stays in
the menu bar and makes the window again when something shows it.

Run on Gading's Mac (macOS 27.0.1, arm64) with Nouride Home Dev, built there with `cd apps/desktop && bun run
build:dev` (no compiler warnings). Driven over ssh with the Dev build's e2e hooks (`scripts/e2e-mac.sh`), measured
with `footprint -p <pid>`. The guard (`scripts/mac-guard.sh`) found his own Nouride untouched after the run: plist
sha256 `24389671b3953ba6...`, PID 72300, before and after. Raw output in `evidence/`.

## Footprint

A WebKit process counts as the app's when it started after the app (the ones already running, other apps', were
listed first and left out). Nouride itself (the Dev install's `nouride start`) is about 50 MB in every row; it is in the
totals but is not the app's.

| | App | WebKit GPU | WebKit Networking | App's window (WebContent) | Dashboard's window (WebContent) | Total with Nouride |
| --- | --- | --- | --- | --- | --- | --- |
| **Before**, both windows closed (hidden), after about 2 hours of use | 41 MB | 22 MB | 8.5 MB | 41 MB | 196 MB | 360 MB |
| Before, as measured earlier the same day (from the request) | 41 MB | 21 MB | 9.5 MB | 38 MB | 181 MB | 338 MB |
| **After**, at launch: the app's window open | 33 MB | 12 MB | 6 MB | 26 MB | (none) | 126 MB |
| After, both windows open | 36 MB | 15 MB | 7 MB | 24 MB | 68 MB | 200 MB |
| After, the dashboard closed | 35 MB | 14 MB | 6.5 MB | 24 MB | (gone) | 130 MB |
| **After, both closed (menu bar only)** | 34 MB | 14 MB | 6 MB | (gone) | (gone) | **104 MB** |
| After 5 more rounds of opening and closing each window, both closed | 37 MB | 17 MB | 8 MB | (gone) | (gone) | 112 MB |

- With both windows closed, the app now holds about 54 MB (itself, WebKit's GPU and Networking processes) where it
  held about 310 MB: each window's WebContent process ends when the window closes. GPU and Networking are WebKit's,
  shared by every webview of the app, and stay while it runs.
- Before, closing changed nothing, so "before" at any moment is every process the app had made, at whatever size use
  had grown it to. The dashboard's WebContent grows with use (68 MB just opened, 181 to 196 MB after hours), so the
  before and after rows are not the same amount of use; the point is the processes that are gone.
- Opening and closing each window five more times left no WebContent process behind. The app and the GPU process grew
  by about 3 MB each over those rounds; not followed further.

## Opening the windows again

From `evidence/reopen.txt`, each with nothing open first:

| How | Seen |
| --- | --- |
| Menu bar, Open Nouride Home | the app's window made again, visible |
| Menu bar, the update notice | made again at `index.html#/versions` (the section is read from the address: the `screen` event would arrive before the new window listens) |
| `open` on the running app (what a Dock click sends: Reopen, no visible window) | made again, visible |
| Menu bar, Open Nouride | the dashboard's window made again at `/dashboard/`, no password field on the page: signed in |
| The dashboard's back (`nouridehome://home`), with the app's window closed | the dashboard's window destroyed, the app's made again |
| `open nouridehome-dev://open` | nothing: the app registers its link scheme but has no handler for one (before this change too); not changed here |

Not seen on the Mac: the screens themselves (macOS refuses `screencapture` over ssh), so that the reopened window
showed Versions and updates is known from its address and the UI's test (`startSection`), not by eye.

## What changed

- `apps/desktop/src/main.rs`: no `CloseRequested` hook any more, so a close destroys the window; `RunEvent::ExitRequested`
  without a code (the last window closed) is prevented, so the app stays in the menu bar (`looks::window::exits`).
- `windows/main.rs`: `show` and `show_at` make the window from its config when it is gone, at the screen asked for
  (`looks::window::main_page`), in the chosen appearance.
- `windows/dashboard.rs`: its back closes the window rather than hiding it; `open` already made it when it was gone and
  signs in each time.
- `e2e.rs`: `hide` is now `close-main`, the window's close button.

## Not changed, worth a word

- A window made again comes back at its default size and place (980 x 640; the dashboard's 1200 x 800): a size the
  person gave it is not remembered once it is closed. Remembering it would take a window-state store (for example
  Tauri's window-state plugin), a new dependency: not added without a yes.
- At the end of the run the new build was left running with its window open, as Gading uses it.
