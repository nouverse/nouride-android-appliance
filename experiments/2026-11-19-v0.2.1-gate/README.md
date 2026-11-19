# Nouride Home 0.2.1 before its release, on a Redmi Note 12 and a Mac

## The question

`main` at `2edf4a8` (VERSION 0.2.1) carries downloads from the dashboard and "Download Nouride's files" (PR #11), the
dashboard always signed in on its own device (PR #12) and a modal only for a confirmation (PR #7). Built from main as
merged, do both apps do all of that on a real phone and a real Mac, and did the merge break what was there before?

## What was tested

| | |
| --- | --- |
| Commit | `2edf4a8`, then this branch with two fixes (`484862b`, `79cfff1`): the bug cases were run again on that build |
| Android | Nouride Home Dev (debug, `tech.nouverse.nouride.home.dev`, 0.0.0-dev), installed over the previous Home Dev build; a Redmi Note 12 (HyperOS 2.0.202, Android 15) |
| Desktop | Nouride Home Dev 0.2.1 (debug, `tech.nouverse.nouride.home.dev`), built on the Mac over the previous Home Dev build; a MacBook Air (Apple silicon), macOS 27.0.1 |
| Nouride | 0.8.40 on both devices (Home Dev's own installs); a throwaway 0.8.40 on a Linux box behind a Cloudflare quick tunnel for remote mode |

The release apps were not installed or opened. The release APK was built unsigned only to read its version, and the
release Mac app only to read its `Info.plist`. The owner's own Nouride on the Mac was checked with `scripts/mac-guard.sh`
before and after (below).

## Builds and tests, on main as merged

| Check | Result |
| --- | --- |
| `./gradlew testDebugUnitTest` (apps/android) | 179 tests, 0 failed |
| `assembleDebug`, `assembleRelease` | Built. Release: `versionName='0.2.1' versionCode='2001'`, label "Nouride Home" (`aapt dump badging`) |
| `cargo test --locked` in every crate (Linux) | 26 test binaries, 192 tests passed, 0 failed |
| `bun test` (apps/desktop, on the Mac) | 35 pass, 0 fail |
| `bun run build:dev`, `bun run build:release` (Mac) | Built. Both `Info.plist`s: `CFBundleShortVersionString` 0.2.1 (`.home.dev` and `.home`) |
| `scripts/bump-version --check` | No file disagrees |

After the two fixes: Android 186 tests (7 new), 0 failed; every crate again, 193 passed (1 new), 0 failed. The PR's
Android and Desktop CI passed.

## What was seen

`PASS` means what the case asks happened, seen on the device. The Android dashboard's account is still on its first-run
page ("Personalize Your Account"). Finishing it would set a password on the owner's phone that nobody holds, so the
phone's Sign out and Revoke were done through the page and the route, as in `2026-11-19-device-sign-in`. The Mac's
account is past that page, so its real buttons were used.

### Redmi Note 12 (2026-11-19, 18:43 to 19:10 +0700)

| # | Case | Result | Seen |
| --- | --- | --- | --- |
| 1 | Install over the previous build | PASS | `adb install -r`, data kept (same install id `461c687569e7`, same device token). HyperOS refused `MY_PACKAGE_REPLACED` ("not permitted to auto start"), as `references/android.md` says, so Nouride stayed off until Start; up 3 s after it, 0.8.40. Open Nouride: one device `login`, `/dashboard/welcome` signed in |
| 2 | Device sign-in | PASS | Cookie deleted then reload, session ended from outside (old cookie then 401), Sign out in the page: one device `login` each, signed in again. Revoke through the route: `device_token_revoked`, then exactly one `device_login_failed`, a new token `dev_3133…` and its `login`. Force-stop and reopen: one `login` with the same token (`evidence/android-audit.txt`) |
| 3 | Dashboard downloads | PASS | `config.toml` then `config (1).toml`, both with the install's sha256; a 300 000-byte blob named `gate-blob.tar.gz` twice: `gate-blob.tar.gz`, `gate-blob (1).tar.gz` (the `.tar.gz` rule), byte for byte |
| 4 | Download Nouride's files | PASS | More → "Nouride's files" ("Keys and passwords stay out, as Nouride has no backup password"), then `nouride-backup-2026-11-19.zip`, 39 948 bytes, 5 entries; checked against `spec/fixtures/backup.json`: nothing excluded inside, `data/.control-token`, the live database and its lock, `data/backups/` and `nougate/` left out; the snapshot opens, `integrity_check` ok; "nouride-backup-2026-11-19.zip is in Downloads." under the button (`evidence/android-unzip-l.txt`, `android-backup-check.txt`) |
| 5 | Modals only for a confirmation | PASS | Turn off Nouride asks; Cancel left it running (uptime 318 s, then 321 s). The address of another computer is its own screen ("Nouride on another computer"). The update offer could not be seen: both Versions cards said "You have the newest version" (a Dev build is never offered the app's update, and 0.8.40 is the newest Nouride) |
| 6 | Remote mode | PASS after fix | `nouride.example.com:99999` refused ("That doesn't look like an address"). The tunnel's address: Home said "It runs on another computer", the phone's own Nouride stopped, the dashboard opened on that Nouride's own sign-in page; a password sign-in there, and its device list stayed empty. Back to This phone: Nouride started, the dashboard signed in with the device token. Two bugs found on the way (below) |
| 8 | Bottom bar, More dot, notice cards | PASS, cards not seen | The three tabs, More's dot while "Keep Nouride running" waits (battery not exempt), gone in remote mode. Neither notice card could show, for the reason in case 5 |

### MacBook Air (2026-11-19, 19:10 to 19:26 +0700)

| # | Case | Result | Seen |
| --- | --- | --- | --- |
| 1 | Install over the previous build | PASS | Built over the previous Home Dev (`target/` kept), the old app quit with `--e2e quit`, the new one opened: 0.2.1, place here, Nouride 0.8.40 running under its own label (it was not stopped). `open-dashboard`: one device `login` with the same token, Overview signed in |
| 2 | Device sign-in | PASS | Logout in the page then reload, the sidebar's Sign out and its dialog, quit and reopen: one `login` each, same token. Users → "Devices that stay signed in" → Revoke, confirmed: `device_token_revoked`; then, on a reload, exactly one `device_login_failed`, a new token `dev_54dd…` and its `login`, the window still on `/dashboard/users` (`evidence/mac-audit.txt`) |
| 3 | Dashboard downloads | Not run live | Needs the macOS Downloads permission, and nobody was at the Mac. Two tries (19:16, 19:23): no file in Downloads after 30 s, and the app stopped answering its hooks, as `references/desktop.md` says happens while macOS asks. Each time the app was quit and opened again, without the question. A rebuilt ad-hoc Dev app is asked again. Passed on this Mac on 2026-11-17 (`2026-11-16-downloads-and-backup`) |
| 4 | Download Nouride's files | Not run live | Same reason: the zip is written to Downloads. Passed on this Mac on 2026-11-17 (`2026-11-16-downloads-and-backup`) |
| 5 | Modals only for a confirmation | Not seen live | The app's own window pictures (`--e2e shot`) failed, the display asleep and then awake: the new ad-hoc build is likely without Screen Recording. No picture of the whole screen was taken. `bun test` covers it: "the only modal is Settings' Turn off confirmation", "nothing else covers the window", "the address for another computer is a card on the first screen and a section in Settings" |
| 6 | Remote mode | PASS | `place-remote nouride.example.com:99999`: `where_not_an_address`, nothing changed. The tunnel's address: the app's Nouride stopped; Open Nouride loaded that Nouride's sign-in page, a password sign-in there, its device list empty. `place-here`: running again, the dashboard signed in with the device token |
| 7 | Windows, tray, browser | PASS | Opening the dashboard added one WebContent process (93 MB RSS); `close-dashboard` ended it (8 → 7 WebKit processes). `close-main` ended one more; the app alone then 111 MB. The tray's Open Nouride Home and Open Nouride made each window again. With `e2e/no-browser`, a `target="_blank"` link and a `window.open` to outside pages were logged as "would go to the browser". Then, the marker removed, one `target="_blank"` link to nouride.com went to the browser for real ("went to the browser"): the only time the browser was opened |

## Bugs found, and fixed on this branch

1. **Android: the dashboard of the Nouride just left came back** (`79cfff1`). The dashboard's screen is `singleTask`
   and chose its Nouride only when it was made. The app opened from the launcher, Open Nouride, then a tap on the
   notification: that put a second status screen above the dashboard. More → Another computer → Connect → Open Nouride then brought back the
   same screen on `http://127.0.0.1:18264/dashboard/welcome`, whose Nouride had just been turned off
   ("Reconnecting to Nouride"). Fixed with `DashboardAim` (pure, tested): opened again after Nouride moved, the
   screen loads the other one from its first page and forgets the old history. Same path on the fixed build: the
   remote dashboard, its history only the remote's two pages.
2. **Android: Back never left the first-run page** (`484862b`). On `/welcome`, Back went a page back to
   `/dashboard/`, which the dashboard sends to `/welcome` again: the history grew from three entries to five and the
   screen never changed. Seen first on the phone's own dashboard (its Nouride off at that moment), then against the
   remote Nouride, running. Every person whose account is still on that page, a fresh install's first open included,
   was stuck in it. `back_inside` is now false on any page a person passes once, in the shared spec
   (`spec/link-policy.md`, four new fixture cases), the Rust crate and Android. The desktop does not call `back()`. On the fixed build Back on `/welcome` asked
   "Go back to Nouride Home?", and Go back closed the dashboard.

## Seen, not changed

- After `adb install -r` Nouride stays off until Start (or the next restart): HyperOS holds `MY_PACKAGE_REPLACED`
  back. An update through the app's own installer goes through `UpdateReceiver` instead, which was not run here (it
  needs two signed builds).
- Force-stopping the app and opening it again started Nouride without Start (the app asked for `ServerService`
  itself 300 ms after its process started). The earlier record saw the same. The code path behind it was not found.
- Home Dev's notification says "Tap to open Nouride Home", the release app's words, so the two are told apart only by
  expanding them.
- The phone's backup card said "About 1,7 MB", then "About 819 kB" after the first zip: Android formats the size in
  the phone's locale (Indonesian here), and the estimate dropped once the database's write-ahead log was folded in.
- The desktop's dashboard window stays on the old Nouride after the place changes, until Open Nouride, which loads
  the right one.
- The scripts in `2026-11-19-device-sign-in/evidence/scripts/` do not parse: the apostrophe in
  `${NOTE12:?the phone's …}` opens a quote, in sh and bash alike. The copies here have it removed.

## Not run, and why

- The app's own update (offer on Home, download, install, Nouride started again by `UpdateReceiver`): it needs two
  signed builds and a feed of their own, and a Dev build is never offered the update. The update and notice cards are
  covered by unit tests (`UpdateLooks`, `UpdateNotices`, `VersionLooks`).
- Nouride's update notice: 0.8.40 is the newest release, so nothing was there to offer.
- A fresh install from nothing on either device: both were installs over the previous build, as the case asks.
- On the Mac: downloads, the zip of Nouride's files and the window pictures (cases 3 to 5), for the reasons in the
  table. On the Mac a dashboard revoke was noticed after a reload, not on coming back to the window: the window was
  hidden (display asleep, `visibilityState` hidden) and the dashboard does not ask `/auth/me` again while hidden. The
  earlier record saw the return path on this Mac.

## The owner's own Nouride on the Mac

`scripts/mac-guard.sh` before and after: `unchanged: plist=24389671b395…46f0 pid=85900` (started 2026-11-18 02:12, not
restarted). Home Dev was left running on both devices, on its own Nouride; nothing was added to the Mac's Downloads.
