# Nouride Home for desktop

A desktop app for Nouride, two ways, as the Android app is. The computer runs Nouride itself: choose **Run it on this
computer** and the app installs Nouride with the official installer and keeps it running, reachable from this
computer and, when allowed, from other devices on the same network. Or the computer opens a Nouride that already runs
somewhere else: choose **Use Nouride on another computer** and give its HTTPS address once.

Its icon sits in the menu bar: the state in one line, opening the app or the dashboard, starting or turning off
Nouride, and a new version when one is ready. Closing the window keeps it there.

**Who it is for:** people who are not technical. Everything a person sees in the app is written for them: see
`AGENTS.md` and the `desktop-appliance-ui` skill.

Status: v0.1, macOS on Apple Silicon. Linux is planned and the code is kept ready for it. Builds are not signed with a
Developer ID yet; see `docs/rfc/0001-desktop-v0.1.md`.

## What it does

- Asks once where Nouride should run, and changes it in Settings at any time.
- On this computer: uses a Nouride that already answers on this computer's default port, or installs one with
  `https://get.nouride.com/install.sh` into the app's own folder (`~/Library/Application Support/<bundle id>/nouride`)
  and keeps it running as the app's own LaunchAgent, so Nouride runs whether or not the app is open, and starts again
  at login.
- Opens the dashboard in a window of the app, signed in with the account the app made on the first setup. Links to
  anything but Nouride open in the browser.
- Says when a new Nouride is ready (on Home, in the menu bar and with a dot on Versions and updates) and puts it in through Nouride's own update.
  The app's own updates come from this repository's Releases through Tauri's updater; none is published yet.
- Connect: lets other devices on the same network open Nouride (off as installed), with a QR code.
- Technical Logs, at the bottom of the sidebar, holds what a helper needs: the state, the app's events and Nouride's log.

What it is built on: [Tauri](https://tauri.app) 2 (the window, the WebView, the tray and the updater are Tauri's and
the system's WebKit), React and Tailwind for the screens, and launchd for keeping Nouride running. Nouride itself is
downloaded and checked by its own installer.

## Building

Needs Rust (through rustup, `rust-toolchain.toml` pins it), Bun 1.4, and on macOS the Xcode Command Line Tools.

```sh
bun install --frozen-lockfile
for d in crates/*/; do (cd "$d" && cargo test -q); done     # the logic, on any OS
(cd apps/ui && bun test && bun run build)                    # the screens
cd apps/desktop
bun run build:dev        # Nouride Home Dev: the UI, then tauri build --debug --features dev --config tauri.dev.conf.json
bun run build:release    # Nouride Home
```

The Tauri CLI runs under Bun (`bun --bun`), so Node is not needed.

The app is `target/<debug|release>/bundle/macos/Nouride Home[ Dev].app`.

## The Dev build

**Nouride Home Dev** (`tech.nouverse.nouride.home.dev`) is another app beside the release, so a developer's Mac can
keep both: the cat on yellow, its own folder, Nouride on `18264` and its LLM gateway on `18266` (the release: `18254`
and `18256`), its own LaunchAgent and link scheme. Testing it never stops or replaces the Nouride the release runs, or
one installed by hand. It is never offered an update, and it alone answers the e2e hooks (`--e2e <action>`, see the
contributing skill).

## Layout

| Path | What it is |
| --- | --- |
| `apps/desktop/` | The Tauri binary: windows, the tray, commands. Wiring only |
| `apps/ui/` | The screens (React, Tailwind), by module: first-run, home, connect, versions, settings, logs |
| `crates/shared/` | The build's names and ports, and the types the other crates share |
| `crates/place/` | Where Nouride runs, and the other computer's address (`RemoteHost`, `RemoteReply`) |
| `crates/daemon/` | Nouride on this computer: health, the installer's run and progress, `.env` and `config.toml`, the first account, the state |
| `crates/service/` | The LaunchAgent and `launchctl` |
| `crates/updates/` | Nouride's update routes, its notice, the stall watch, versions |
| `crates/link-policy/` | What stays in the dashboard's window, and Back |
| `crates/looks/` | Each state's sentence, cat and button, as keys into the words |
| `packages/ui-kit/` | Colours, fonts, the cat's poses, shared React pieces, the words |
| `experiments/` | What was run and seen on real computers, a folder per run |
