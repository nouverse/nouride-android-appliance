# Nouride Home

An Android app that makes a phone the home of Nouride: the phone itself runs it. Install it, press Start, and
Nouride runs on the phone, reachable from the phone itself and from other devices on the same Wi-Fi. No Termux, no
terminal.

Not a client: an app that only connects to a Nouride running elsewhere would be a different app, named Nouride.
This one is `tech.nouverse.nouride.home`.

**Who it is for:** people who are not technical. An old phone in a drawer becomes the home of their AI assistant. Everything
a person sees in the app is written for them, not for a developer: see `AGENTS.md` and the `android-appliance-ui`
skill.

## What it does

- Carries proot and a Debian arm64 system inside the APK. On the first start it unpacks Debian, then runs the official
  Nouride installer inside it (`https://get.nouride.com/install.sh`), so the phone gets the same Nouride as any Linux
  install, at the latest release.
- Keeps Nouride running from a foreground service, with the screen off, and starts it again if it stops on its own.
- Shows whether Nouride is running, the address to open, and opens the dashboard inside the app.
- Starts on boot, once the phone allows it.

Nouride listens on its default port, `18254`. The dashboard is `http://<the phone's address>:18254/dashboard/`.

Limits: arm64 phones only (Nouride has no 32-bit ARM build), sideloaded (not on Google Play: `targetSdk` is 28 on
purpose, see `AGENTS.md`).

## Building

Everything installs without root, into one directory:

```sh
scripts/setup-toolchain.sh ~/android-toolchain   # JDK 17, Android SDK, build tools, NDK
. ~/android-toolchain/env.sh
scripts/build-proot.sh                           # proot for arm64 Android, into app/src/main/jniLibs/
scripts/build-rootfs.sh                          # the Debian arm64 layer, into app/src/main/assets/rootfs.tgz
./gradlew assembleDebug testDebugUnitTest
```

The APK is `app/build/outputs/apk/debug/app-debug.apk`. Every downloaded input is pinned by sha256 in
`scripts/assets.lock`; the built proot and rootfs are not committed.

## Installing on a phone

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Over wireless ADB, Google Play Protect asks for a confirmation on the phone's screen: someone has to be at the phone
to tap it, or the install is refused.

## Layout

| Path | What it is |
| --- | --- |
| `app/src/main/kotlin/.../server/` | The foreground service, the supervisor that restarts Nouride, the health check, proot's command line |
| `app/src/main/kotlin/.../install/` | Unpacking the Debian rootfs and running the installer on the first start |
| `app/src/main/kotlin/.../ui/` | The two screens: status, and the dashboard in a WebView |
| `app/src/main/kotlin/.../power/` | The battery exemption and the phone's own battery settings |
| `app/src/main/kotlin/.../boot/` | Starting on boot |
| `app/src/test/` | Unit tests, on the JVM |
| `scripts/` | The toolchain, proot and rootfs builds, and `assets.lock` |
| `experiments/` | What was tried and measured on real phones, a folder per run |

## Working on it

Read `AGENTS.md` first. The skills in `.agents/skills/` hold the detail: `android-appliance-ui` for anything a person
sees, `android-appliance-contributing` for building, testing and the rules of the code.
