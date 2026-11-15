---
name: desktop-appliance-contributing
description: How to work on Nouride Home for desktop (Tauri v2, macOS first) - the workspace's layout and its one-way dependencies, the crates' pure logic and their tests on Linux, the UI's build and Bun tests, building the app and the Dev build, how the official installer is run without touching a Nouride installed by hand, the LaunchAgent, the e2e hooks, and testing on a real Mac over ssh. Use when changing the app's code, its build, or testing it on a Mac.
---

# Working on Nouride Home for desktop

## How it works

1. `apps/desktop` (Tauri v2) opens the app's window on `apps/ui` and puts the cat in the menu bar. It holds no
   business logic: every rule is in `crates/`, which `apps/desktop/src/commands/` calls and `apps/ui` reaches only
   through `apps/ui/src/commons/lib/ipc.ts` (Tauri's `invoke` and one `state` event).
2. The app keeps one state (`apps/desktop/src/state.rs`): where Nouride runs (`place`), the local phase
   (`daemon::phase`), the remote reply, the setup's progress, the update notices. A poller asks `/health` every few
   seconds while the window is open and every minute when it is hidden; each change is sent to the UI as `state` and
   redraws the tray menu.
3. **Installing** (`daemon::installer`): the official installer is downloaded into the app's folder and run with
   `HOME=<data>/home`, a `PATH` of links to the system's tools without `launchctl` (`daemon::installer::shim`), and
   `--dir <data>/nouride --port <build port> --host 127.0.0.1 --variant router`. On macOS the installer otherwise writes
   `~/Library/LaunchAgents/tech.nouverse.nouride.plist`, `~/.local/bin/nouride` and `~/.config/nouride/nouride.env`
   and loads launchd: a person's own Nouride, installed by hand, would be replaced. `installer::args` and `env` are
   tested; never run the installer any other way.
4. After it: the gateway port into `config.toml` for a non-default port (`daemon::config`), the first account into
   `.env` (`daemon::first_admin`), then `nouride service install --kind launchd --dir <dir>` with the same `HOME`
   makes Nouride's own plist in the install folder, and `service::agent` gives it the app's label and writes it to
   `~/Library/LaunchAgents/<label>.plist`. `service::launchctl` builds the `bootstrap`, `bootout`, `kickstart -k` and
   `print` command lines and reads `print`'s answer.
5. **Opening the dashboard**: `daemon::first_admin` signs in over HTTP, the session cookie is set on the dashboard's
   window (`set_cookie`), and the window's `on_navigation` asks `link_policy::link` what each load is.
6. **Nouride's update**: `updates::nouride` reads the control-token routes, `updates::notice` keeps what was heard,
   `updates::stall` restarts a stuck download. The app's own: `tauri-plugin-updater` against this repository's
   Releases.

## Builds

`shared::Build` holds every name and number that differs between the release and the Dev build: the bundle id, the
app's name, Nouride's port and its gateway's, the LaunchAgent label, the link scheme. The cargo feature `dev` picks
`Build::DEV`; `tauri.dev.conf.json` (merged over `tauri.conf.json` with `--config`) gives the Dev app its id, name and
yellow icon. A literal port or id anywhere else is a bug: the Dev build would reach the person's Nouride.

```sh
cd apps/desktop
bunx tauri build --debug --features dev --config tauri.dev.conf.json   # Nouride Home Dev
bunx tauri build                                                        # Nouride Home
```

Builds are unsigned (ad-hoc): no Apple Developer account yet.

## Tests

- Crates: `cargo test` in each, on Linux, no screen: `for d in crates/*/; do (cd "$d" && cargo test -q) || exit 1; done`.
  The rules ported from Android keep Android's test cases, one for one, so a difference is visible.
- UI: `cd apps/ui && bun test` (the words, the IPC types' mapping, formatting) and `bun run build`.
- A change carries a test that fails without it: break it, see it red, put it back, and say "sabotage: ..." in the
  commit body.

## Testing on a Mac (over ssh)

Gading's Mac is reached with `ssh mac`. He runs his own Nouride there (`~/nouride`, LaunchAgent
`tech.nouverse.nouride`, port 18254) and benchmark containers on 18354 and 18454: never stop, update, sign in to or
write to any of them. Test only Nouride Home Dev, which installs into
`~/Library/Application Support/tech.nouverse.nouride.home.dev/` and runs on 18264/18266 under the label
`tech.nouverse.nouride.home.dev.nouride`.

- Work in `~/nix-work/nouride-appliance-desktop` (rsync the tree there, without `target/` and `node_modules/`).
  rustup is user-level (`. ~/.cargo/env`); Bun is `~/.bun/bin/bun`; `zsh -lc` does not put either on PATH.
- Before and after every run: `scripts/mac-guard.sh` records the sha256 of
  `~/Library/LaunchAgents/tech.nouverse.nouride.plist` and the PID of `launchctl list tech.nouverse.nouride`, and
  fails when either changed.
- The e2e hooks (Dev build only, `apps/desktop/src/e2e.rs`): run the app's binary again with `--e2e <action>` and the
  running app does it (single instance): `dump` (the state as JSON into `<data>/e2e/state.json`), `place-here`,
  `place-remote <address>`, `start`, `stop`, `open-dashboard`, `close-dashboard`, `tab <home|connect|more|versions|logs>`,
  `tray <item id>`, `check-nouride-update`, `shot <name>` (a capture of the app's own window only, never the screen).
- Quit what you open (`--e2e quit`), and remove only what you made: the Dev app's folder, its LaunchAgent
  (`launchctl bootout gui/$(id -u)/tech.nouverse.nouride.home.dev.nouride`, then the plist).

## Recording a run

A run on a real computer gets a folder in `experiments/`, `<date>-<what>`: a `README.md` with what was run and what was
seen, and `evidence/` with raw output, credentials and personal ids removed. No screenshot of anything but the app's
own windows.

## Commits

Conventional commits (`feat(daemon): ...`, `fix(tray): ...`), one change each, attributed to the person you work for,
with no AI co-author or attribution line. Work on a branch; a pull request to `main`.
