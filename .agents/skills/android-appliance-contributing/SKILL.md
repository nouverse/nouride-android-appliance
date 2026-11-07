---
name: android-appliance-contributing
description: How to work on Nouride Home, the Android app that runs Nouride on the phone - building the toolchain, proot and the Debian rootfs, the code's layout (service, supervisor, installer, power, boot), its constraints (targetSdk 28, arm64 only, the official installer, pinned inputs), unit tests and testing on a real phone over adb, and recording a measurement in experiments/. Use when changing the app's code, its build, or testing it on a phone.
---

# Working on Nouride Home

## How it works

1. The APK carries `libproot.so` and `libproot-loader.so` (built by `scripts/build-proot.sh`) and the Debian arm64
   layer `assets/rootfs.tgz` (fetched by `scripts/build-rootfs.sh`).
2. On the first start, `install/` unpacks the rootfs into the app's data directory, then runs the official installer
   inside Debian (`Nouride.INSTALL`: the packages the minimal image lacks, then `https://get.nouride.com/install.sh`).
3. `server/ServerService` is a foreground service of type `specialUse`. It holds a partial wake lock and a Wi-Fi lock,
   and `Supervisor` runs `nouride start` under proot (`ProotCommand`), restarting it with backoff when it exits on its
   own.
4. `HealthPoller` asks Nouride's `/health` on the phone every minute; `ServerState` holds the phase and the last answer
   for the screens and the notification.
5. `power/` asks for the battery exemption and opens the phone's own battery settings. `boot/` starts the service on
   boot.

Nouride listens on its default port, 18254 (`Nouride.PORT`).

## Constraints, and why

- **targetSdk 28.** From 29 on, Android refuses to run files from the app's data directory (W^X), and proot runs every
  Debian program from there. Termux targets 28 for the same reason. So the app is sideloaded, never on Google Play.
- **arm64-v8a only.** Nouride ships no 32-bit ARM build.
- **The official installer, never a bundled Nouride.** The phone gets the same release as every other install, and
  `nouride update` works as it does elsewhere.
- **Pinned inputs.** `scripts/assets.lock` holds the sha256 of the proot and talloc archives and the Debian layer's
  digest. Changing one is one commit with the lock and the reason.
- **No third-party Android libraries.** The APK is 48.5 MiB already, nearly all of it Debian.

## Build and test

```sh
scripts/setup-toolchain.sh ~/android-toolchain   # once, no root
. ~/android-toolchain/env.sh
scripts/build-proot.sh
scripts/build-rootfs.sh
./gradlew assembleDebug testDebugUnitTest
```

Unit tests run on the JVM (`app/src/test/`): backoff, the health answer, proot's arguments, the tar reader, process
handling. A change carries a test that fails without it: break the change, run `./gradlew testDebugUnitTest`, see it
red, put it back, and say so in the commit.

## Releases

A release is a GitHub release `vX.Y.Z` published on `main`; `.github/workflows/release.yml` runs on that (never on a
push), builds, tests, signs and attaches `nouride-home-X.Y.Z.apk` to it. The version code comes from the version (major × 1 000 000 + minor ×
1 000 + patch), so each release must be higher than the last. The workflow refuses an APK not signed with the key
in `scripts/release-cert.sha256`: a phone refuses an update signed with another key. Never commit a keystore. Try a
change to the workflow with a manual run (a dry run) before tagging.

## On a real phone

What a unit test cannot reach (the service staying alive, Doze, a brand's battery manager, the first install) is
checked on a phone, and the change says which phone, which Android, and what was seen.

- Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`. Over wireless ADB, Play Protect asks on the
  screen; someone has to tap it.
- The status screen takes extras for adb (the app id is `tech.nouverse.nouride.home`, the classes keep the package
  `tech.nouverse.nouride.android`):
  `adb shell am start -n tech.nouverse.nouride.home/tech.nouverse.nouride.android.ui.StatusActivity --ez start true`
  (also `stop`, `dashboard`).
- Never put a real password, bot token or personal id in a test, a fixture, a commit or a log you keep.

## Recording a measurement

A run that measures something (a soak, a battery reading, a new phone) gets a folder in `experiments/`, named
`<the date it started>-<what>`: a `README.md` with the question, how it was run, what was measured and what it means,
and `evidence/` with the raw logs and the scripts, credentials and personal ids removed. Add its row to
`experiments/README.md`. A folder is the record of its run: a later run gets its own.

## Commits

Conventional commits (`feat(service): ...`, `fix(install): ...`), one change each, attributed to the person you work
for, with no AI co-author or attribution line. Do not push to `main` unless asked.
