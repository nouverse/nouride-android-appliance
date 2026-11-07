# RFC 0001: Nouride on Android as an app that runs Debian under proot

- **Status:** Draft, rewritten on 2026-11-07 from what the spike measured (`SPIKE_REPORT.md`). It replaces
  the draft of 2026-11-06.
- **Authors:** Nouva and Gading (first draft and decisions of 2026-11-06), Nix (spike and this rewrite).
- **Repository:** `nouverse/nouride-android`, on its own (no submodule of core yet).

## 1. Summary

Nouride has no Android build: it ships for Linux (glibc and musl) and macOS. On 2026-11-05 it ran on a Redmi 12
inside Termux, in a Debian userland under proot (`proot-distro`). This RFC proposes one APK that does the same
without Termux or a terminal: it carries proot and a Debian rootfs, unpacks Debian on the first start, runs the
official Nouride installer inside it, and keeps `nouride start` running from a foreground service. The spike on
the mission branch built that APK and ran it on the same phone; the numbers below come from that run.

## 2. Motivation

The Termux route works but asks a person to install Termux from F-Droid or GitHub, install `proot-distro` and
Debian from a shell, run the installer, write a start script, take a wake lock and find the battery settings
of their phone's brand. Each step is a place to give up. An old phone as a home server, or a phone as a small
Nouride appliance, needs an app that is installed and started.

### Goals

- One APK: install it, press Start, get the dashboard address.
- The same Nouride as on any Linux: the official installer, the latest release, not a copy in the APK.
- Keeps running with the screen off, and starts again when it stops on its own.
- Shows its state, its addresses and its log; opens the dashboard.

### Non-goals

- A native Android build of Nouride (bionic instead of glibc).
- A terminal in the app.
- 32-bit ARM. Nouride has no 32-bit ARM build; the app is arm64-v8a only.
- Google Play (see 5.1). The app is sideloaded.
- Accessibility automation of the phone, licensing and paid tiers: none of it is part of this design.

## 3. What the spike measured

On a Redmi 12 (HyperOS 2.0, Android 15, 5.5 GiB RAM), 2026-11-06/07:

| | |
| --- | --- |
| APK | 50.9 MB debug build: 49.8 MB Debian layer (stored), 2.6 MB dex, 0.3 MB proot |
| Install | `adb install` refused twice by Google Play Protect while nobody could confirm on screen; succeeded with a person at the phone |
| First start | Debian unpacked in 6 s (5,442 entries); `apt-get` and the official installer in 71 s with the screen on; `nouride ready` 2 s after `nouride start` |
| W^X | proot runs every Debian program from the app's data directory at targetSdk 28 on Android 15 |
| Service | foreground, type `specialUse`, process state 4 (foreground service), partial wake lock and Wi-Fi lock held |
| From the LAN | `/health` 200 `ok` in 0.1 s, `/dashboard/` 200, while the phone was in deep Doze on battery |
| Screen off, on battery | 6 h 12 min from `ready`, the last 5 h 30 min with the screen off, mostly in deep Doze: every `/health` from the LAN (5 min apart) answered, the service never restarted, battery 83 → 79 % over the screen-off stretch. Stopped there by Gading, short of the planned 8 h |
| Telegram | `telegram:reno` long-polling from the app stayed connected; a message reached the daemon after 5 h 30 min with the screen off (its turn failed at the provider, Nougate) |
| Storage | 321 MB rootfs after install (Nouride 119 MB of it), 326 MB of app data |
| Memory | `nouride` 64 MB RSS at the start, 52 MB after 6 h; app process 272 MB with a WebView open, 157 MB without |
| Stop | SIGTERM to `./nouride start`: clean shutdown in 56 ms, proot exits by itself |

## 4. Architecture

```
APK (tech.nouverse.nouride.android, targetSdk 28, arm64-v8a)
├── lib/arm64-v8a/libproot.so, libproot-loader.so    termux/proot, built for this app
├── assets/rootfs.tgz                                 Debian trixie arm64 layer, pinned by digest
└── Kotlin, one :app module
    ├── install/  TarReader, RootfsExtractor, RootfsSetup
    ├── server/   ServerService, ProotArgs + SysData, ProotRunner, Supervisor + Backoff, HealthPoller
    ├── power/    battery-exemption dialog, the app's settings page
    ├── boot/     BootReceiver
    └── ui/       StatusActivity, DashboardActivity (WebView on 127.0.0.1)

files/debian/rootfs   Debian, unpacked on the first start
  /srv/nouride        Nouride, as the official installer lays it out (its data in /srv/nouride/.nouride)
files/debian/sysdata  fake /proc files, written at every start
files/logs            service.log, install.log, daemon.log, health.log
```

Process tree while it runs: the app → `libproot.so` → `su` → `nouride` (and whatever the agents run).

## 5. Design

### 5.1 targetSdk 28, sideloaded

From targetSdk 29 an app may not execute files from its own data directory (W^X). proot loads every Debian
program from there, so the app targets 28, as Termux does. Measured: it works on Android 15. Consequences:

- Google Play does not accept new apps or updates below its current target level. The app is sideloaded: an
  APK from our site or GitHub releases.
- Android refuses to install apps below a minimum target level, raised over time: below 23 since Android 14,
  below 24 since Android 15. If a future Android raises it above 28, this design stops installing on that
  version. Termux depends on the same thing.
- Google Play Protect checks sideloaded APKs. It refused the spike's debug APK twice when nobody could confirm
  on the screen; a person must confirm the first install. Whether a release build signed with our key, and
  Android's developer verification (its verifier, `com.google.android.verifier`, was bound during the
  install), change that, is open (8).

### 5.2 proot

termux/proot at the release Termux ships (5.1.107.102), built with NDK r27d for API 24 by
`scripts/build-proot.sh`, with talloc 2.5.0 linked in statically. It is packaged as `libproot.so` and its
loader as `libproot-loader.so` in `jniLibs/arm64-v8a`, with legacy (extracted) native-library packaging:
Android installs them into `nativeLibraryDir`, the one place an app may execute files from at any target
level. The app always passes `PROOT_LOADER` (the loader's path there) and `PROOT_TMP_DIR` (in the app's files).

The command line is `proot-distro login debian`'s (5.10, Termux): `--kill-on-exit --link2symlink --sysvipc`,
a faked kernel release (6.17.0), `-L`, `--change-id=0:0`, binds of `/dev`, `/proc`, `/sys`,
`/dev/urandom:/dev/random`, an empty directory over `/sys/fs/selinux`, a 1777 directory as `/dev/shm`, and a
fake file for each /proc entry the app cannot read (all nine of proot-distro's on Android 15:
`loadavg`, `stat`, `uptime`, `version`, `vmstat` and four sysctl files).

Differences from Termux's build: static talloc (Termux's binary has a RUNPATH into `/data/data/com.termux`), no
libandroid-shmem (only needed to share SysV shm with Termux's own processes), only the 64-bit loader. Two
build steps are adjusted for NDK r27d and a box without gawk (see the spike report).

proot is GPL-2.0: the APK must come with its source or a written offer, with our build script.

### 5.3 The Debian rootfs

The image `proot-distro install debian` pulls: `docker.io/library/debian`, trixie, linux/arm64/v8, a single
layer. `scripts/build-rootfs.sh` fetches it by manifest digest, checks the layer against its digest, and puts it
in the APK as `rootfs.tgz` (not `.tar.gz`: the Android Gradle plugin gunzips assets named `*.gz`). The app
unpacks it on the first start (6 s on the Redmi 12) and applies what proot-distro applies: `resolv.conf` with
8.8.8.8 and 8.8.4.4, a plain `hosts`, `aid_` users and groups for the app's own Android ids, `/tmp` 1777.
Android refuses hard links in app storage, so a hard link in the layer becomes a copy.

The digest is pinned in `scripts/assets.lock`; moving to a newer Debian is a change of that file.

### 5.4 Nouride: the official installer, not a bundled binary

The APK does not carry Nouride. On the first start the app runs, as faked root inside Debian:

```
apt-get update && apt-get install -y curl ca-certificates procps
curl -fsSL https://get.nouride.com/install.sh | sh -s -- --host 0.0.0.0 --port <port>
```

The latest release is installed, and the APK is not rebuilt for each Nouride release. The installer makes the
`nouride` account and says there is no systemd; the app then runs
`su -s /bin/sh nouride -c 'cd /srv/nouride && exec ./nouride start'`, as the Android guide does in Termux.
The first start needs the internet (about 18 MB of Debian packages and the Nouride release).

What the daemon cannot do here, the same as on Termux: each agent as its own user (`nouride-run` needs
systemd), `--allow-packages`, and Nouride Box features such as the setup hotspot.

### 5.5 The service

`ServerService` is a foreground service of type `specialUse`. `dataSync` would be wrong for a server, and for
apps targeting Android 15 it is limited to 6 hours a day; the spike targets 28, where that limit does not
apply, but the type says what the service is. It does, on a worker thread: unpack Debian if needed, write the
fake /proc files, run the installer if Nouride is not installed, then keep the daemon running through
`Supervisor`. The daemon restarts after 2 s, doubling up to 5 min, and the count starts again after a run of
10 min. The service returns `START_STICKY`, so Android brings it back if it kills the app.

Stop: SIGTERM to `./nouride start` (found under proot's pid in /proc), then SIGQUIT to proot, which kills
everything it runs (proot ignores SIGTERM), then SIGKILL.

`HealthPoller` asks `http://127.0.0.1:<port>/health` every 60 s. Only HTTP 200 with `status: ok` counts as up
(`starting`, `degraded` and `shutting_down` are 503).

### 5.6 Staying alive with the screen off

- A partial wake lock and a Wi-Fi lock while the service runs, as `termux-wake-lock` takes. Android 15 turns the
  requested `WIFI_MODE_FULL_HIGH_PERF` into a low-latency lock (type 4), documented as effective only with the
  screen on; the daemon stayed reachable on the LAN without it.
- Android's battery-exemption dialog (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) on Start. On the Redmi 12
  the exemption put the app on the Doze allowlist (standby bucket 5). HyperOS has its own battery saver per app;
  the status screen links to the app's settings page.
- Android 12 and later kill an app's child processes beyond 32 in total (phantom process killer). The phone of
  the spike already had the limit raised for Termux, so this was not tested. A busy agent can cross 32 child
  processes; the app should detect the setting and explain the adb commands the Android guide gives.

### 5.7 The screens

`StatusActivity`: state, `/health` summary, the dashboard on the phone and on the LAN (the Wi-Fi address),
Start, Stop, the battery buttons while the app is not exempt, and the end of the current log. It is where the
first login appears: the daemon prints the generated `admin` password once, in `daemon.log`.

`DashboardActivity`: a WebView on `http://127.0.0.1:<port>/dashboard/`, JavaScript and DOM storage on.
Cleartext HTTP is allowed for `127.0.0.1` only (network security config). The full app must open it only once
`/health` is ok, or show a native "starting" view and reload: in the spike it could be opened before the daemon
was up.

### 5.8 Start on boot

`BootReceiver` starts the service after a reboot if it was running before. Not tested: the spike did not reboot
the phone. Xiaomi phones also have an Autostart permission per app that can hold `BOOT_COMPLETED` back.

### 5.9 Network

The daemon binds `0.0.0.0` so other devices on the Wi-Fi can open the dashboard; that is the point of a home
server, and it is also an exposure on an untrusted network. The default port is 18254, as everywhere; the spike
used 18264 because the same phone runs Nouride in Termux on 18254. The Router edition (`--variant router`) adds
the in-process Nougate gateway on 18256; the spike installed the basic edition.

## 6. What changed from the first draft

| First draft | Now |
| --- | --- |
| targetSdk 34 | 28: from 29 the app could not execute Debian's programs (W^X). |
| `nouride-router` bundled in the rootfs in the APK | Not bundled: the official installer fetches the latest release on the first start. |
| `dataSync` foreground service | `specialUse`: a server is not a data sync, and `dataSync` is limited to 6 h a day for apps targeting Android 15. |
| proot `-r rootfs -b /dev -b /proc -b /sys -w /srv/nouride /srv/nouride/nouride start`, as root | proot-distro's full command line (link2symlink, sysvipc, fake /proc, faked kernel release, faked root) and the daemon as the `nouride` account through `su`. |
| A minimal rootfs of 35 to 45 MB | The Debian layer is 49.8 MB packed, 152 MB unpacked, 321 MB with Nouride. |
| Static proot downloaded from elsewhere | termux/proot built by our script, inputs pinned by sha256. |
| Hard-coded LAN addresses of the phone and a Mac | None in the design: the status screen reads the phone's Wi-Fi address. |
| Core described as a monorepo of several languages | Core is a Bun and TypeScript monorepo; this app shares no build with it. |
| Jetpack Compose UI and Chrome Custom Tabs | Framework views in the spike and a WebView on the loopback address; Compose stays open for the full app. |
| An accessibility bridge, licensing and paid tiers | Not part of this design. |
| An assessment of who can do the work | Replaced by the measurements in section 3. |

## 7. Risks

- **The minimum installable target level** (5.1). If Android raises it above 28, installs stop on that Android
  version. Nothing in this design avoids that; it is the same risk Termux carries.
- **Play Protect and developer verification.** A person has to confirm the first install on the phone, and a
  remote install (adb with the screen off) fails. For a sideloaded product this is the first impression.
- **Vendor battery savers.** The spike measured one phone, with the exemption granted. Other brands and other
  HyperOS versions differ.
- **The phantom process killer** on phones without the raised limit (5.6).
- **The first start depends on the network**: Debian's mirrors, get.nouride.com and GitHub releases. A failure
  leaves the app in a failed state with `install.log`; the full app needs a retry.
- **Exposure on the LAN** with `--host 0.0.0.0`.

## 8. Open questions

1. Does a release build, signed with our key, install without a confirmation on screen? What does Android's
   developer verification ask of us for a sideloaded app?
2. Does the service survive a full night (the spike stopped at 5 h 30 min with the screen off), more than one
   night, a reboot (with HyperOS Autostart), low memory, and agents at work, without the battery exemption, and
   on other phones?
3. How should Nouride update itself here? `nouride update` has no service manager to hand the restart to; the
   app's supervisor can restart the daemon, but the flow is untested.
4. Basic or Router edition by default in the app?
5. Where does the first login go: the status screen reads it from the daemon's log today.
6. Distribution and source offer for proot (GPL-2.0).

## 9. Plan for the full app

1. Release signing, the install path (download page or GitHub releases) and the Play Protect behaviour of a
   release build.
2. The first start as a guided screen: progress of unpack and install, retry on failure, the first login shown
   once.
3. The dashboard screen gated on `/health`; Stop and restart from the notification.
4. Battery: the exemption, HyperOS and other vendors' settings pages, the phantom-process check with the adb
   commands to fix it.
5. Boot and updates: `BootReceiver` with Autostart guidance; `nouride update` with the supervisor restarting the
   daemon.
6. A soak matrix: several phones, several nights, with and without the exemption, with agents running.
7. CI: the toolchain script, proot and rootfs scripts and the unit tests in GitHub Actions, the APK as an
   artifact.
