# Working on Nouride Home

Nouride Home (`tech.nouverse.nouride.home`), the Android app that runs Nouride on the phone itself: proot and a
Debian arm64 rootfs inside the APK, the official Nouride installer run inside Debian on the first start, and a
foreground service that keeps `nouride start` running. Kotlin, Gradle, no third-party Android libraries. This file is for any coding agent working here.

## The user is not technical

The person holding the phone has never opened a terminal and should never need to. Everything the app shows is for
them. **By default the app never shows** a log, a file path, a port number on its own, a process state, an exception,
JSON, a shell command, or the words health, proot, Debian, daemon, rootfs. Those live behind one "Details" switch,
off by default, for support. Read the `android-appliance-ui` skill before you change anything a person sees: a
screen, a notification, a button, an error.

## Start here

- `README.md`: what the app does, how to build it and install it.
- `.agents/skills/android-appliance-ui/`: the rules for every screen and every word on it.
- `.agents/skills/android-appliance-contributing/`: building, testing, the code's layout and its rules, testing on a
  real phone.
- `experiments/`: what was measured on real phones. Numbers in docs come from there.

## Rules

1. `targetSdk` stays 28. From 29 on, Android forbids running files from the app's data directory, and proot runs every
   Debian program from there. The app is sideloaded, never on Google Play, for the same reason.
2. arm64-v8a only. Nouride has no 32-bit ARM build.
3. Nouride itself is never bundled: the app runs the official installer, so the phone gets the release every other
   install gets.
4. Every downloaded input is pinned by sha256 in `scripts/assets.lock`. A new version of proot, talloc or the Debian
   layer changes the lock in the same commit.
5. Never log, show or commit a credential: the admin password, a bot token, a Telegram id, a Wi-Fi name. Evidence in
   `experiments/` has them removed.
6. Every change carries a test that fails without it (`./gradlew testDebugUnitTest`); break the change, watch the
   test go red, put it back. What a unit test cannot reach is checked on a real phone and said so.
7. Every text a person reads is a string resource in `res/values/strings.xml`, never a literal in Kotlin. The app
   is in English only, whatever the phone's language: there is no `values-<language>` folder.

## Verify

```sh
. ~/android-toolchain/env.sh      # after scripts/setup-toolchain.sh once
./gradlew assembleDebug testDebugUnitTest
```

## Writing

Code, comments, commits and docs in English; the app's words in English. A comment says why. Commits
are conventional (`fix(service): ...`), attributed to the person you work for, with no AI co-author or attribution
line. Do not push to `main` unless asked.
