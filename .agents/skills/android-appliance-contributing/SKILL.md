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
6. `update/` is the app's own updates, from this repository's GitHub Releases: `UpdateFeed` reads `releases/latest`
   (pure, tested), `Updates` checks it when the app opens (every 6 h at most, at once after the app changed version) and from the service twice a day,
   downloads the APK against its sha256 and hands it to `PackageInstaller`. `UpdateReceiver` hears the installer: the
   screen asking the person, then the result, and the new version starts Nouride again on success. `ui/UpdateCard`
   is the card and the big card once a release; both wait while the first start sets Nouride up. `ui/AppUpdater` is the
   download and install both it and the Versions screen use. Nouride's own update is `update/NourideUpdates`: the
   dashboard's routes (`/api/control/update`, `/update/download`, `/update/progress`, `/update/install`) on the
   loopback, signed in with the install's control token (`NourideUpdate.TOKEN_PATH`, what `nouride` commands use), so it
   needs no dashboard account. Nouride swaps its binary and exits, and `Supervisor` starts the new one.

7. `ui/` is everything a person sees: `StatusActivity` holds the three tabs along the bottom (`HomeTab`, `ConnectTab`,
   `MoreTab`, `BottomBar`, the rules in `Tabs`), `DashboardActivity` the dashboard in a WebView, `SupportActivity`
   Technical Logs. Connect's switch is `server/NetworkAccess` (the `.env`'s `HOST=0.0.0.0` or `127.0.0.1`, which
   `nouride start` reads and which wins over `config.toml`), then `ServerService.ACTION_RESTART`: the daemon stopped
   cleanly and the `Supervisor` starting it again at once (`restartNext`), not counted as a crash in its backoff.
   `QrCode` draws the Connect tab's code without a library; `QrCodeTest` checks it module by module
   against segno's output. `Appearance` is the More tab's Light or Dark: every activity starts from a context in it
   (`attachBaseContext`), as Android's per-app setting only exists from Android 12.

Nouride listens on its default port, 18254 (`Nouride.PORT`), and the Router edition's LLM gateway on 18256
(`Nouride.GATEWAY_PORT`). The debug build moves both (below).

## Constraints, and why

- **targetSdk 28.** From 29 on, Android refuses to run files from the app's data directory (W^X), and proot runs every
  Debian program from there. Termux targets 28 for the same reason. So the app is sideloaded, never on Google Play.
- **arm64-v8a only.** Nouride ships no 32-bit ARM build.
- **The official installer, never a bundled Nouride.** The phone gets the same release as every other install, and
  `nouride update` works as it does elsewhere.
- **Pinned inputs.** `scripts/assets.lock` holds the sha256 of the proot and talloc archives and the Debian layer's
  digest. Changing one is one commit with the lock and the reason.
- **No third-party Android libraries.** The APK is 48.5 MiB already, nearly all of it Debian.
- **The debug build is a second app.** `applicationIdSuffix ".dev"` in `app/build.gradle.kts`: "Nouride Home Dev",
  `tech.nouverse.nouride.home.dev`, a yellow icon (`app/src/debug/res`), Nouride on 18264, its gateway on 18266 (written
  into Nouride's `config.toml` before the wizard, as Nouride has no environment variable for it), and the link scheme
  `nouridehome-dev`. Anything that names the app's ports, its id or its link reads `BuildConfig`, never a literal, or
  the developer's copy reaches the person's Nouride.
- **HyperOS (Xiaomi) can hold broadcasts back from an app not allowed to autostart**, and which ones differs: on the
  owner's Redmi Note 12 (HyperOS 2.0.202 global) `MY_PACKAGE_REPLACED` was refused (logcat: "process is not permitted
  to auto start"), while after a restart `BOOT_COMPLETED` reached both the release and the Dev build with Autostart off,
  about 40 s after the first unlock, and Nouride answered 9 s later (2026-11-08,
  `experiments/2026-11-08-restart-on-hyperos/`). The installer's own report to the app always arrives, which is why
  `UpdateReceiver`, not `BootReceiver`, starts Nouride after an update. Autostart's switch is in Security's list
  (`PowerSettings.AUTOSTART_LIST`), not on the app's page in Settings. To test a restart: `adb reboot`, unlock once
  without opening the app, and watch `/health` from another machine (wireless debugging is off after a restart, and
  the phone's address may change); then `adb logcat -d -b all | grep -i nouride` shows `Start proc ... for broadcast
  {...BootReceiver}`.

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

Test an update before a release with two signed builds and a feed of your own: build with
`NOURIDE_HOME_VERSION=0.0.10 NOURIDE_HOME_UPDATE_FEED=http://127.0.0.1:18299/latest.json` and again as `0.0.11`,
sign both with the release key on the owner's machine (`apksigner sign --ks-pass env:...`, never a password on the
command line), serve a `latest.json` shaped like GitHub's (`tag_name`, `draft`, `prerelease`, `assets` with
`name` and `browser_download_url`) with the APK and its `.sha256` next to it, `adb reverse tcp:18299 tcp:18299`,
install the first and let it find the second. Plain HTTP is accepted from `127.0.0.1` only. A wrong sha256 must end
in "The update didn't download"; after the update, Nouride must be running again without the app being opened.

A release is a GitHub release `vX.Y.Z` published on `main`; `.github/workflows/release.yml` runs on that (never on a
push), builds, tests, signs and attaches `nouride-home-X.Y.Z.apk` to it. The version code comes from the version (major × 1 000 000 + minor ×
1 000 + patch), so each release must be higher than the last. The workflow refuses an APK not signed with the key
in `scripts/release-cert.sha256`: a phone refuses an update signed with another key. Never commit a keystore. Try a
change to the workflow with a manual run (a dry run) before tagging.

## On a real phone

What a unit test cannot reach (the service staying alive, Doze, a brand's battery manager, the first install) is
checked on a phone, and the change says which phone, which Android, and what was seen.

- Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`. Over wireless ADB, Play Protect asks on the
  screen; someone has to tap it. HyperOS also asks "Install via USB" for a package new to the phone, not for an
  update of one it has.
- The status screen takes extras for adb (the debug build's id is `tech.nouverse.nouride.home.dev`, a release's
  `tech.nouverse.nouride.home`; the classes keep the package `tech.nouverse.nouride.android`):
  `adb shell am start -n tech.nouverse.nouride.home.dev/tech.nouverse.nouride.android.ui.StatusActivity --ez start true`
  (also `stop`, `dashboard`), and `--es tab connect` (or `home`, `more`) opens a tab. `run-as` works on the debug
  build only.
- Never put a real password, bot token or personal id in a test, a fixture, a commit or a log you keep.

## Recording a measurement

A run that measures something (a soak, a battery reading, a new phone) gets a folder in `experiments/`, named
`<the date it started>-<what>`: a `README.md` with the question, how it was run, what was measured and what it means,
and `evidence/` with the raw logs and the scripts, credentials and personal ids removed. Add its row to
`experiments/README.md`. A folder is the record of its run: a later run gets its own.

## Commits

Conventional commits (`feat(service): ...`, `fix(install): ...`), one change each, attributed to the person you work
for, with no AI co-author or attribution line. Do not push to `main` unless asked.
