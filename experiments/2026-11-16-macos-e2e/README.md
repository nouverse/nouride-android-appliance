# Nouride Home Dev, end to end on a Mac (2026-11-16)

## The question

Does Nouride Home for desktop v0.1, as Nouride Home Dev, do on a real Mac what Android 0.2.0 does on a phone, plus
the menu bar? And does it do it without touching a Nouride the owner installed by hand on the same Mac?

## The machine

The owner's Mac: Apple Silicon, macOS 27.0.1. It runs his own native Nouride (`~/nouride`, LaunchAgent
`tech.nouverse.nouride`, port 18254) and two benchmark containers (18354, 18454). The build was
`target/debug/bundle/macos/Nouride Home Dev.app` (`bun run build:dev`, unsigned debug build), from this branch at
d5e35fe, then at 79c77a6 with the fixes below. Rust came from a user-level rustup; Bun was the Mac's own.

## How it was run

Everything ran over `ssh mac`, through the Dev build's e2e hooks (`scripts/e2e-mac.sh`): `--e2e <action>` reaches the
running app (single instance), and `dump` writes its state. WKWebView has no WebDriver, so the dashboard's window is
read from its URL, not its pixels. `scripts/mac-guard.sh` recorded the owner's plist checksum and PID before the run
and compared them after each step.

Evidence (paths and the LAN address replaced, nothing else changed) is in `evidence/`: the state at each step
(`nrh-0*.json`), the installer's log, the app's log, and the LaunchAgent's `ProgramArguments`.

## What was seen

| Step | Seen |
| --- | --- |
| First start, no folder of the app's yet | `place: null`: the first question, "Where should Nouride run?" (`nrh-01`) |
| Run it on this computer (pinned to v0.8.37 for the update test) | The installer, run with the app's own `HOME` and a `PATH` without `launchctl`, printed "No service manager" and loaded nothing. The bar went download, install, start. Nouride 0.8.37 answered on 127.0.0.1:18264, its gateway on 127.0.0.1:18266 (`[nougate] port = 18266`), under `tech.nouverse.nouride.home.dev.nouride`. `.env` had `HOST=127.0.0.1`, `PORT=18264` and no `INITIAL_ADMIN_*` left. The key file was in the app's own `home/.config/nouride`. `~/.local/bin/nouride` still pointed at the owner's Nouride, and there was no `~/.config/nouride`. (`nrh-02`) |
| Menu bar | "Nouride is running" (greyed), "A new Nouride is ready", Open Nouride Home, Open Nouride, Turn off Nouride, Quit Nouride Home |
| Open Nouride | The dashboard's window at `http://127.0.0.1:18264/dashboard/welcome`: signed in with the app's account, on the page where the person chooses their own username and password, as on Android |
| Update notice and update | The notice came from Nouride's update route on open: "0.8.38". The app's update downloaded and installed it; Nouride said `restart: idle`, launchd started 0.8.38, and the notice and More's dot went away (`nrh-03`) |
| Connect's switch | On: `HOST=0.0.0.0`, Nouride listening on `*:18264`, Connect offering `http://<LAN address>:18264/dashboard/`. Off: back on 127.0.0.1 only |
| Turn off, then Start from the menu bar | Off: the LaunchAgent unloaded, nothing on 18264, and the menu offered Start. Start: see "found" 2 below |
| Address card | `http://…` was refused as plain http, `nouride example.com` as not an address, and a name nothing answers at as unreachable, each with its own words |
| Another computer | A throwaway Nouride on the dev box behind a Cloudflare quick tunnel, pasted as its dashboard address. Taken as its machine. The Dev Nouride here turned off. Home: "remote_running". Connect: its address. The menu bar had no Start or Turn off (`nrh-04`). Open Nouride: its own login page, in the window |
| Back to this computer | The Dev Nouride started again (0.8.38) and Open Nouride was signed in again at `/dashboard/welcome` (`nrh-05`) |
| Appearance | Dark, then Same as computer, set on the windows |
| The owner's Nouride | `plist=24389671…46f0 pid=72300` before the run and after every step: unchanged |

## Found, and fixed on this branch

1. **Nouride's launchd plist cannot run from a folder with a space in its path.** `nouride service install --kind
   launchd` splits its start command on spaces (`apps/engine/src/service/unit.ts`, `execStart.split(" ")`, 0.8.37).
   Under `~/Library/Application Support/…` the path became two arguments, and launchd exited 126 in a loop.
   `service::launchd::with_program` now writes the binary's path as one argument and keeps Nouride's update guard
   (d5e35fe). Nouride itself should be fixed too.
2. **Restarting Nouride soon after an update rolls it back into an install that cannot start.** Until Nouride confirms
   an update (five minutes up on a quiet install), the plist's guard counts starts and restores the previous version
   on the fourth. The previous version then refuses the data the new one migrated ("A newer Nouride has already
   upgraded it"). Here, Connect on, Connect off, and Turn off then Start, within two minutes of the update, did exactly
   that. The Dev install was put back by hand, as Nouride's message says (the 0.8.38 binary moved back from
   `nouride.failed`). The app now makes no restart while `.nouride-pending-update` is there: Connect's switch waits
   with "Nouride is finishing an update. Try this again in a few minutes.", and Try again leaves restarts to launchd
   (79c77a6). The rollback that leaves an install unable to start is Nouride's to look at.
3. **The dashboard's window kept the origin it was made with.** After switching to another computer, that computer's
   dashboard counted as "outside" and went to the browser once (a login page of the throwaway Nouride opened in the
   Mac's browser). The window now reads which Nouride is current at each navigation, and the second try opened it in
   the window.

The e2e helper also learned to refuse when no app is running. Run with none, the binary started as the app itself,
and an old build kept answering after a rebuild: the first setup of the second try ran on the build before fix 1.

## Not proven here

- **Pictures of the windows.** macOS refused `screencapture` from the ssh session and from the app ("could not create
  image from rect"). It needs Screen Recording permission, which only the owner can give.
- **The dashboard's own back button** (`nouridehome://home` closing the window). It is a click inside the dashboard,
  which the hooks cannot make. `link_policy` is unit-tested for it.
- **The app's own update.** No feed is published and no signing key is set, so Check for updates says it could not
  check. The Dev build is never offered one anyway.
- **The release build** (`Nouride Home`, 18254): not run on this Mac. Its port is the owner's Nouride, which the app
  would find answering and use rather than install over (the "already on this computer" path); that path is
  unit-tested only.
- **Starting at login, and the app staying in the menu bar after its window is closed:** not checked.
