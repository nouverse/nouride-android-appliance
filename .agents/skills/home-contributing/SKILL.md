---
name: home-contributing
description: How to work on Nouride Home, the Android app (Kotlin, proot and a Debian rootfs on the phone) and the desktop app (Tauri v2, macOS first) in one repository with one version - the repository's layout, VERSION and scripts/bump-version, the behaviour both apps share in spec/, building and testing each app, testing on a real phone over adb or a real Mac over ssh, CI and releases, and recording a measurement in experiments/. Use when changing either app's code, its build, the shared spec, CI or a release, or testing on a device.
---

# Working on Nouride Home

One repository, two apps, one version. Each app's own rules are in `references/`: read the one for the app you
change, both when a change touches what they share.

- `references/android.md`: the Android app (`apps/android`): how it works, its constraints and why, building it with
  proot and the Debian layer, testing an update, testing on a real phone over adb.
- `references/desktop.md`: the desktop app (`apps/desktop`, `crates/`, `packages/ui-kit`): how it works, the release
  and Dev builds, the crates' tests on Linux, testing on a real Mac over ssh with the e2e hooks.

## The layout

| Path | What it is |
| --- | --- |
| `apps/android/` | The Android app, the Gradle project as it is (`app/`, `gradle/`, `gradlew`) |
| `apps/desktop/` | The desktop app in Tauri's layout: the screens (React, Tailwind) at its root, the binary in `src-tauri/` |
| `crates/` | The desktop's logic, pure and tested on Linux: `shared`, `place`, `daemon`, `service`, `updates`, `link-policy`, `looks` |
| `packages/ui-kit/` | The desktop's colours, fonts, the cat's poses, shared React pieces, its words |
| `spec/` | The behaviour both apps share, a page each, and `spec/fixtures/*.json` read by both test suites |
| `assets/` | Icons and brand, the source for both apps |
| `scripts/` | The Android toolchain, proot and rootfs builds and `assets.lock`; the release certificate; the desktop's Mac scripts and icons; `bump-version` |
| `docs/` | Design documents (`docs/rfc/`) |
| `experiments/` | What was run and measured on real phones and computers, a folder per run |
| `VERSION` | The one version of both apps |
| `Cargo.toml`, `package.json` | The Cargo workspace (`crates/*`, `apps/desktop/src-tauri`) and the bun workspaces (`apps/desktop`, `packages/*`) |

## One version

`VERSION` holds the version of both apps, `x.y.z`. Change it with `scripts/bump-version x.y.z`, never by hand: it
writes the version into the desktop's `tauri.conf.json`, the Cargo workspace (every crate and the Tauri binary take
it from there) and `Cargo.lock`. The Android build reads `VERSION` itself; its version code is
major × 1 000 000 + minor × 1 000 + patch, so a release must always be higher than the last. `scripts/bump-version
--check` names any file that disagrees, and `crates/shared/tests/one_version.rs` fails on one.

A release tag `vX.Y.Z` is VERSION, nothing else: bump VERSION on `main` first, then publish the release.

## Behaviour both apps share

Where both apps do the same thing (the address of a Nouride on another machine, which links the dashboard's window
keeps and Back, release versions), the rule is a page in `spec/` and its cases are in `spec/fixtures/`. Both suites
read them: the Android tests in `apps/android/app/src/test/.../spec/`, the Rust tests in `crates/*/tests/spec_*.rs`.
Change the page, the fixture and both apps in one change. Where the two apps answer a case differently today, the case
says so (`android` or `desktop`) and the page lists it; it is decided, not quietly made to match. See `spec/README.md`.

## Tests

Every change carries a test that fails without it: break the change, watch the test go red, put it back, and say
"sabotage: ..." in the commit body. What a test cannot reach is checked on a real phone or a real Mac, and the change
says which, and what was seen.

```sh
(cd apps/android && ./gradlew testDebugUnitTest assembleDebug)                 # after the Android setup (references/android.md)
for d in crates/*/; do (cd "$d" && cargo test -q) || exit 1; done              # the desktop's logic and the shared spec
bun install --frozen-lockfile && (cd apps/desktop && bun test && bun run build)  # the desktop's screens
scripts/bump-version --check
```

CI runs the same: `.github/workflows/android.yml` on a change to `apps/android/`, `spec/` or `scripts/`,
`.github/workflows/desktop.yml` on a change to `crates/`, `apps/desktop/`, `packages/` or `spec/`.
`.github/workflows/release.yml` builds, signs and attaches the APK to a published release `vX.Y.Z`; the desktop app
is not part of a release yet.

## Recording a measurement

A run that measures something on a real device (a soak, a battery reading, a new phone, a run on a real computer)
gets a folder in `experiments/`, named `<the date it started>-<what>`: a `README.md` with the question, how it was
run, what was measured or seen and what it means, and `evidence/` with the raw logs, output and scripts, credentials,
personal ids, personal paths and addresses removed. No screenshot of anything but the app's own windows. Add its row
to `experiments/README.md`. A folder is the record of its run: a later run gets its own.

## Commits

Conventional commits (`feat(service): ...`, `fix(install): ...`, `feat(daemon): ...`, `fix(tray): ...`), one change
each, attributed to the person you work for, with no AI co-author or attribution line. Work on a branch; a pull
request to `main`. Do not push to `main` unless asked.
