# Working on Nouride Home

Nouride Home is two apps in one repository, with one version (`VERSION`):

- **Android** (`apps/android`, `tech.nouverse.nouride.home`): runs Nouride on the phone itself, with proot and a
  Debian arm64 rootfs inside the APK, the official Nouride installer run inside Debian on the first start, and a
  foreground service that keeps `nouride start` running; or opens a Nouride on another computer. Kotlin, Gradle, no
  third-party Android libraries.
- **Desktop** (`apps/desktop`, `crates/`, `packages/ui-kit`, `tech.nouverse.nouride.home`): runs Nouride on this
  computer with the official installer and keeps it running under launchd, or opens a Nouride on another computer, and
  shows its dashboard in a window of its own, with a menu bar icon. Tauri v2 (Rust) with a React UI. macOS first;
  Linux later. It mirrors the Android app in concept, features and words.

This file is for any coding agent working here.

## The user is not technical

The person holding the phone or sitting at the computer has never opened a terminal and should never need to.
Everything the apps show is for them. **By default neither app shows** a log, a file path, a port number on its own, a
process state, an exception, JSON, a shell command, or the words health, daemon, service; on the phone also proot,
Debian, rootfs; on the computer also launchd, LaunchAgent, plist. Those live on one screen of their own, "Technical
Logs", at the end of the phone's More tab and at the bottom of the window's sidebar. Read the `home-ui` skill before
you change anything a person sees: a screen, a notification, a tray item, a button, an error.

## Start here

- `README.md`: what the apps do, how to build them and install them.
- `.agents/skills/home-ui/`: the rules for every screen and every word on it, with a page per app in `references/`.
- `.agents/skills/home-contributing/`: the layout, the one version, the shared spec, building, testing, CI and
  releases, with a page per app in `references/` (testing on a real phone, testing on a real Mac).
- `spec/`: the behaviour both apps share, and the fixtures both test suites read.
- `docs/rfc/0001-desktop-v0.1.md`: what desktop v0.1 is, and why each choice was made.
- `experiments/`: what was measured on real phones and computers. Numbers in docs come from there.

## Rules

Both apps:

1. Nouride itself is never bundled: the app runs the official installer, so the device gets the release every other
   install gets.
2. Never log, show or commit a credential: the admin or dashboard password, the control token, a bot token, a
   Telegram or other personal id, a Wi-Fi name. Evidence in `experiments/` has them removed.
3. Every change carries a test that fails without it (`./gradlew testDebugUnitTest` in `apps/android`; Rust unit tests
   in the crates and Bun tests for the desktop's screens); break the change, watch the test go red, put it back, and
   say so in the commit. What a test cannot reach is checked on a real phone or a real Mac, and said so.
4. Every text a person reads is in the app's one place for words, never a literal in code: `res/values/strings.xml`
   on Android, `packages/ui-kit/src/words.json` on desktop. Both apps are in English only, whatever the device's
   language: there is no `values-<language>` folder.
5. One version: `VERSION`, changed with `scripts/bump-version`. A release tag is `v` and VERSION.
6. Behaviour both apps implement is written in `spec/` and its cases in `spec/fixtures/`, read by both test suites.
   Change the page, the fixtures and both apps together.

Android:

7. `targetSdk` stays 28. From 29 on, Android forbids running files from the app's data directory, and proot runs every
   Debian program from there. The app is sideloaded, never on Google Play, for the same reason.
8. arm64-v8a only. Nouride has no 32-bit ARM build.
9. Every downloaded input is pinned by sha256 in `scripts/assets.lock`. A new version of proot, talloc or the Debian
   layer changes the lock in the same commit.

Desktop:

10. `crates/` never import Tauri, and are tested on Linux without a screen (`cargo test` in each). Dependencies go one
    way: `shared` <- the other crates <- `apps/desktop/src-tauri`; no crate imports a sibling except through `shared`.
11. `apps/desktop/src-tauri` has no business logic: it wires the crates to windows, the tray and commands. A rule worth
    a test goes in a crate.
12. The screens (`apps/desktop/src`) talk to Rust only through `apps/desktop/src/commons/lib/ipc.ts`.
13. The installer runs with `HOME` and `PATH` of its own so it writes nothing outside the app's folder (no
    `~/Library/LaunchAgents/tech.nouverse.nouride.plist`, no `~/.local/bin/nouride`). A person's own Nouride,
    installed by hand, is never stopped, replaced or written to.
14. Anything that names a port, a bundle id, a LaunchAgent label or a link scheme reads `shared::Build`, never a
    literal, or the Dev build reaches the person's Nouride. (On Android the same holds for `BuildConfig`.)

## Verify

```sh
# Android, after scripts/setup-toolchain.sh once
. ~/android-toolchain/env.sh
scripts/build-proot.sh && scripts/build-rootfs.sh
(cd apps/android && ./gradlew assembleDebug testDebugUnitTest)

# Desktop, on any OS
for d in crates/*/; do (cd "$d" && cargo test -q) || exit 1; done
bun install --frozen-lockfile && (cd apps/desktop && bun test && bun run build)
scripts/bump-version --check
# on a Mac:
cd apps/desktop && bun run build:dev
```

## Writing

Code, comments, commits and docs in English; the apps' words in plain English. A comment says why. Commits are
conventional (`fix(service): ...`, `feat(daemon): ...`), attributed to the person you work for, with no AI co-author or
attribution line. Do not push to `main` unless asked; work goes through a pull request.
