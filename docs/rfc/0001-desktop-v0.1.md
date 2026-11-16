# RFC 0001: Nouride Home for desktop, v0.1

- Status: accepted (Gading, 2026-11-16), being built on `feat/desktop-v0.1`
- Platforms: macOS first (Apple Silicon, as Nouride publishes a Mac build for arm64 only); Linux later, the code kept
  ready for it
- Mirrors: Nouride Home for Android 0.2.0 (`nouverse/nouride-android-appliance`, `main` at 44d8e5f)

## What it is

Nouride Home for a computer: the Android app's concept and features, on a desktop, with a menu bar (tray) icon. It
answers one question once, where Nouride should run, then keeps that Nouride running and opens its dashboard. The
people it is for are the same as on Android: they have never opened a terminal and should never need to. Every word on
screen follows the `desktop-appliance-ui` skill.

The name is "Nouride Home", the same app icon as Android's (the cat's head on lavender). Nouride Satellite (a tray,
notifications and a quick chat for a Nouride elsewhere) joins this app later, as v0.2, rather than being a second app.

## v0.1: parity with Android 0.2.0, plus the tray

### Where Nouride runs

The first screen on a computer without Nouride asks "Where should Nouride run?": **Run it on this computer** or **Use
Nouride on another computer**. A computer where the app has installed Nouride already is never asked (`place::answer`,
as Android's `Place.answer`). The answer is changed in More, "Where Nouride runs".

- **Another computer**: the address card, with Android's `RemoteHost` rules unchanged: https only, 443 dropped, a
  pasted dashboard address read as its machine, every refusal with its own sentence, and one reachability check before
  the address is kept. Home says what that computer's `/health` answered (`RemoteReply`: answers, down, or a page in
  front of it such as Cloudflare Access). Connect shows its address and a QR code. Nothing of this computer's own
  Nouride runs meanwhile; what is installed stays on disk, so switching back picks it up as it was.
- **This computer**:
  1. If a Nouride already answers `/health` on this build's port (18254 for the release, 18264 for Dev), the app uses
     it rather than install a second one: Home shows it, Open Nouride opens it (its own login page, as the app did not
     make its account), and starting, turning off and updating it stay with whoever installed it.
  2. Otherwise, Start installs Nouride with the official installer (`https://get.nouride.com/install.sh`) into the
     app's own folder and keeps it running under launchd as the app's own LaunchAgent. See "Installing" below.

### Home

One sentence of state, one main button (Start, Open Nouride, Try again), the cat changing with the state, and the first
setup's progress bar ("Step 2 of 3", the percent, never going back). The states, sentences and buttons are Android's
`StatusLook`, ported as they are (`crates/looks`). A new Nouride waiting is a small card on Home with Update, which
opens Versions and updates and starts it there.

### Open Nouride

The dashboard in a window of the app (a WebView: WKWebView on macOS), never a browser tab:

- Signed in with the account the app made on the first setup (Android's `FirstAdmin`: `INITIAL_ADMIN_*` in the
  install's `.env` with `MUST_CHANGE=1`, taken out once the account exists). The app signs in over HTTP and hands the
  session cookie to the window (`set_cookie`); a 401 means the person chose their own password, and the app forgets
  its own. Another computer, or a Nouride the app did not install, opens on its own login page.
- The window's user agent carries `NourideHome/1`, so the dashboard draws its back button; that button's link
  (`nouridehome://home`) closes the window and brings Nouride Home forward.
- `LinkPolicy`, ported as it is: Nouride's own pages at the Nouride shown stay in the window, every other link goes to
  the browser, and a main-frame load that fails at Nouride's origin shows the cat and Try again instead of the
  WebView's own error page.
- Back: on a computer the window's close button and the dashboard's own back button are explicit, so there is no
  "Go back to Nouride Home?" question as on a phone's swipe. The `back` rules are ported and tested for the keyboard's
  Back (Cmd+[), which walks the dashboard's history but never onto a sign-in, welcome, setup or OAuth page.

### Updates

- **The app's own**: the Tauri updater, wired to this repository's GitHub Releases
  (`releases/latest/download/latest.json`). Nothing is published in v0.1, and no feed exists yet: Check for updates
  says it could not check. The updater's signing key is the owner's to make (`tauri signer generate`); its public half
  goes into `tauri.conf.json` when he does. The Dev build is never offered an update, as on Android.
- **Nouride's**: its control-token routes (`/api/control/update`, `/update/download`, `/update/progress`,
  `/update/install`) on the loopback, with the install's `.nouride/data/.control-token`, as Android 0.2.0 does
  (`NourideUpdate`, `NourideNotice`, `StallWatch`). The notice is said on Home, in the tray and on More (its dot and its
  Versions line, `UpdateNotices`), checked when the app opens (15 minutes apart at most) and twice a day while it runs.
  Nouride swaps its binary and exits; launchd starts the new one (`KeepAlive`).

### More

(Since the desktop layout, 2026-11-16, there is no More: its rows are the sidebar's sections and the Settings page, as
the `desktop-appliance-ui` skill describes. The rest of this section is v0.1 as first written.)

Where Nouride runs; Appearance (Same as computer, Light, Dark, applied to the app's windows and to the dashboard's);
Versions and updates (Android's two cards and `VersionLooks`); Turn off Nouride (amber words, asks once); Technical
Logs (the technical state, the app's events, the installer's log and Nouride's, Copy).

Android's "Keep Nouride running" (the battery exemption, Xiaomi's Autostart) has no counterpart: launchd keeps the
LaunchAgent running and starts it at login.

### Connect

The switch "Open from other devices" sets `HOST=0.0.0.0` or `HOST=127.0.0.1` in the install's `.env` and restarts
Nouride (Android's `NetworkAccess`). **It is off as installed**, unlike Android's: a laptop moves between networks, and
the installer's own default on a computer is the loopback. On, Connect shows the computer's address on its network as a
QR code with Copy. On another computer, it shows that computer's address and code, with no switch.

### The tray (menu bar on macOS)

One menu: the state in one line (the same sentence as Home's title), Open Nouride Home, Open Nouride (while it may
work), Start or Turn off Nouride (this computer only), the update notice when one waits ("A new Nouride is ready" and
the like, `UpdateNotices`, opening Versions and updates), and Quit Nouride Home. Closing the window keeps the app in
the tray; Nouride keeps running either way, since launchd, not the app, keeps it. Quitting the app leaves Nouride
running as well.

## Installing on this computer

The official installer is run, never a bundled Nouride: the computer gets the release every other install gets, and
`nouride update` works as it does elsewhere. On macOS the installer writes outside its `--dir`:
`~/Library/LaunchAgents/tech.nouverse.nouride.plist` (written, unloaded and loaded), `~/.local/bin/nouride` (a symlink),
and `~/.config/nouride/nouride.env` (the secret store's key, reused when there). A person may already have a Nouride
of their own there, and the Dev build must never touch the owner's. So the app runs it:

- with `HOME` set to a folder of the app's own (`<app data>/home`): everything the installer puts under the home goes
  there, the key file included;
- with a `PATH` in which there is no `launchctl` (a folder of links to the system's tools, `launchctl` left out): the
  installer finds no launchd, so it writes no plist, loads nothing and unloads nothing;
- with `--dir <app data>/nouride --port <build's port> --host 127.0.0.1 --variant router` (the free build with its LLM
  gateway, as on Android).

Then the app writes Nouride's gateway port into `config.toml` when it is not the default (the Dev build), the first
account into `.env`, and its own LaunchAgent: the plist Nouride itself generates (`nouride service install --kind
launchd`, which writes it into the install folder), with the app's own label. That is loaded with `launchctl bootstrap
gui/<uid>`, turned off with `bootout`, and restarted (Connect's switch) with `kickstart -k`. Made with the same `HOME`,
the plist names the key file the installer made there (`NOURIDE_ENCRYPTION_KEY_FILE`), never the key itself, and
never the person's own `~/.config/nouride`.

The first setup's progress has three steps, moved by the installer's own step lines (`▸ Fetching the release`, then
the binary and what follows it) and by the app: downloading Nouride, installing it, and turning it on for the first
time, which ends when Nouride answers. The installer waits up to 30 seconds for a Nouride it did not start; that wait
is inside "Installing Nouride".

## The Dev build

"Nouride Home Dev" sits beside the release on one computer, as on Android: bundle id
`tech.nouverse.nouride.home.dev`, its own folder, Nouride on 18264 and its gateway on 18266, its own LaunchAgent label
(`tech.nouverse.nouride.home.dev.nouride`; the release's is `tech.nouverse.nouride.home.nouride`), its own link scheme
(`nouridehome-dev`), and the cat on yellow. It is the cargo feature `dev`, with `tauri.dev.conf.json` over the base
config. Everything that names a port, an id, a label or a scheme reads `shared::Build`, never a literal.

Only the Dev build carries the e2e hooks: running its binary again with `--e2e <action>` reaches the running app
(single instance) and does what a click would (start, open the dashboard, pick a place, press a tray item), and
`--e2e dump` writes the app's state as JSON. WKWebView has no WebDriver; these stand in for adb's extras on Android.

## Code layout

```
apps/desktop/   the Tauri binary, wiring only (commands, windows, tray, boot)
apps/ui/        React + Tailwind screens; talks to Rust only through commons/lib/ipc.ts
crates/         pure logic, no Tauri, tested on Linux: shared, place, daemon, service, updates, link-policy, looks
packages/ui-kit tokens, fonts, mascot poses, shared React pieces, the app's words
```

Dependencies go one way: `shared` <- the other crates <- `apps/desktop`. No crate imports a sibling except through
`shared`. `crates/looks` (Android's `StatusLook`, `VersionLooks`, `UpdateNotices`, `Tabs`) is a seventh crate beside
the six agreed: it combines the states of `daemon` and `place`, so those states live in `shared` and the mapping in a
crate of its own, used by the UI (through the state the app sends it) and the tray alike. The words themselves are one
file, `packages/ui-kit/src/words.json`, read by the UI and, for the tray, by `apps/desktop`.

## Signing

v0.1 builds are unsigned or ad-hoc signed: there is no Apple Developer account yet. macOS then shows "cannot be opened
because the developer cannot be verified" on a downloaded copy, and a person must allow it in System Settings, Privacy
and Security. Signing with a Developer ID and notarising is a later release's, as is the updater's own signing key.

## Not in v0.1

- Linux packaging (the crates and the service layer leave room: a systemd user unit in place of the LaunchAgent).
- Nouride Satellite (v0.2): notifications, a quick chat from the tray.
- Published releases and an update feed.
- Intel Macs: Nouride has no public build for them.
