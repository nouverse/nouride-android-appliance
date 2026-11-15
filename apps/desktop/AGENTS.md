# Working on Nouride Home for desktop

Nouride Home for a computer (`tech.nouverse.nouride.home`): it runs Nouride on this computer with the official
installer and keeps it running under launchd, or opens a Nouride that runs on another computer, and shows its
dashboard in a window of its own, with a menu bar icon. Tauri v2 (Rust) with a React UI. macOS first; Linux later. It
mirrors the Android app, `nouverse/nouride-android-appliance`, in concept, features and words. This file is for any
coding agent working here.

## The user is not technical

The person at the computer has never opened a terminal and should never need to. **By default the app never shows** a
log, a file path, a port number on its own, a process state, an exception, JSON, a shell command, or the words health,
daemon, launchd, LaunchAgent, plist, service. Those live on one screen, "Technical Logs", at the end of More. Read the
`desktop-appliance-ui` skill before you change anything a person sees: a screen, a tray item, a button, an error.

## Start here

- `README.md`: what the app does, how to build it and run it.
- `docs/rfc/0001-desktop-v0.1.md`: what v0.1 is, and why each choice was made.
- `.agents/skills/desktop-appliance-ui/`: the rules for every screen and every word on it.
- `.agents/skills/desktop-appliance-contributing/`: building, testing, the code's layout and its rules, testing on a
  real Mac.
- `experiments/`: what was run and seen on real computers.

## Rules

1. `crates/` never import Tauri, and are tested on Linux without a screen (`cargo test` in each). Dependencies go one
   way: `shared` <- the other crates <- `apps/desktop`; no crate imports a sibling except through `shared`.
2. `apps/desktop` has no business logic: it wires the crates to windows, the tray and commands. A rule worth a test
   goes in a crate.
3. `apps/ui` talks to Rust only through `apps/ui/src/commons/lib/ipc.ts`.
4. Nouride itself is never bundled: the app runs the official installer, with `HOME` and `PATH` of its own so the
   installer writes nothing outside the app's folder (no `~/Library/LaunchAgents/tech.nouverse.nouride.plist`, no
   `~/.local/bin/nouride`). A person's own Nouride, installed by hand, is never stopped, replaced or written to.
5. Anything that names a port, a bundle id, a LaunchAgent label or a link scheme reads `shared::Build`, never a
   literal, or the Dev build reaches the person's Nouride.
6. Never log, show or commit a credential: the app's dashboard password, the control token, a bot token, a personal
   id. Evidence in `experiments/` has them removed.
7. Every change carries a test that fails without it (Rust unit tests in the crates, Bun tests for the UI's logic);
   break the change, watch the test go red, put it back, and say so in the commit. What a test cannot reach is checked
   on a real Mac and said so.
8. Every text a person reads is in `packages/ui-kit/src/words.json`, never a literal in a component or in Rust. The
   app is in English only.

## Verify

```sh
for d in crates/*/; do (cd "$d" && cargo test -q) || exit 1; done
bun install --frozen-lockfile && (cd apps/ui && bun test && bun run build)
# on a Mac:
cd apps/desktop && bunx tauri build --debug --features dev --config tauri.dev.conf.json
```

## Writing

Code, comments, commits and docs in English; the app's words in plain English. A comment says why. Commits are
conventional (`feat(daemon): ...`), attributed to the person you work for, with no AI co-author or attribution line.
Do not push to `main`; work goes through a pull request.
