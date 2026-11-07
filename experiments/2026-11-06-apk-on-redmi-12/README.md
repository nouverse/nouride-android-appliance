# Spike report: Nouride inside an Android app

The question (brief of mission msn_fb44328c9f59460dbc23): can our own APK run the flow that ran Nouride on
Gading's Redmi 12 in Termux on 2026-11-05 (proot, Debian arm64, the official installer, `nouride start` as
the `nouride` account), installed over wireless ADB, reachable from the LAN, and still answering `/health`
after 8 hours with the screen off?

**Short answer: yes, short of the full 8 hours.** The APK installed over wireless ADB once a person could
confirm Play Protect on the screen, proot runs Debian from the app's data directory at targetSdk 28 on Android
15, the official installer put Nouride 0.8.21 in, and the daemon answered every `/health` from the LAN for
6 h 12 min on battery, the last 5 h 30 min with the screen off and the phone mostly in deep Doze. Android never
restarted the service. Gading closed the soak there, at 07:04 WIB, before the 8 hours of screen off the brief
asked for.

Everything below was measured on 2026-11-06/07 on the dev box (`agent-host`) and on the phone. Times are UTC
unless marked WIB (UTC+7). Raw logs are in `evidence/`, beside this file (the admin password, Gading's Telegram id and
Wi-Fi network names removed), with the scripts that gathered them (`soak.sh`, `final-reading.sh`, `nr.sh`).

## 1. What was built

| Path | What it is |
| --- | --- |
| `scripts/setup-toolchain.sh` | Installs Temurin JDK 17, the Android command-line tools, platform 36, build-tools 35 and NDK r27d into one directory, without root. |
| `scripts/build-proot.sh` | Builds termux/proot 5.1.107.102 (the release Termux ships) for arm64 Android with the NDK, talloc 2.5.0 linked statically, into `app/src/main/jniLibs/arm64-v8a/libproot.so` and `libproot-loader.so`. |
| `scripts/build-rootfs.sh` | Fetches the Debian trixie arm64 layer (the image proot-distro 5.10 installs for `debian`) from Docker Hub by digest into `app/src/main/assets/rootfs.tgz`. |
| `scripts/assets.lock` | Versions, URLs and sha256 of every input above. The scripts stop on a mismatch. The outputs are not committed. |
| `app/` | One `:app` module, Kotlin, package `tech.nouverse.nouride.android`, targetSdk 28, minSdk 24, arm64-v8a only, framework views (no AndroidX). |
| `app/.../install/` | `TarReader`, `RootfsExtractor` (hard links become copies), `RootfsSetup` (proot-distro's fixups: resolv.conf, hosts, `aid_` ids, `/tmp` 1777), `AndroidFileOps`. |
| `app/.../server/` | `ProotArgs` (proot-distro's argv for this app's paths), `SysData` (fake /proc files), `ProotRunner`, `Supervisor` + `Backoff`, `HealthPoller` + `Health`, `Proc`, `ServerState`, `ServerService`. |
| `app/.../power/`, `boot/`, `ui/` | Battery-exemption dialog, `BootReceiver`, `StatusActivity` (state, addresses, Start/Stop, log tail), `DashboardActivity` (WebView on `http://127.0.0.1:<port>/dashboard/`). |

What the app does when Start is pressed (or `adb shell am start -n tech.nouverse.nouride.android/.ui.StatusActivity --ez start true`):

1. Starts `ServerService` in the foreground, type `specialUse`, and takes a partial wake lock and a Wi-Fi lock
   (what `termux-wake-lock` takes). Shows Android's battery-exemption dialog if the app is not exempt.
2. First start only: unpacks `rootfs.tgz` into `files/debian/rootfs`, applies the fixups, writes the fake /proc
   files, then runs as faked root inside Debian:
   `apt-get update && apt-get install -y curl ca-certificates procps`, then
   `curl -fsSL https://get.nouride.com/install.sh | sh -s -- --host 0.0.0.0 --port 18264` (latest release,
   nothing pinned).
3. Runs `su -s /bin/sh nouride -c 'cd /srv/nouride && exec ./nouride start'` under proot and keeps it running:
   when it exits on its own it starts it again after 2 s, doubling up to 5 min, the count reset by a run of
   10 min or more. `START_STICKY` brings the service back if Android kills the app.
4. Asks `http://127.0.0.1:18264/health` every 60 s and writes each answer to `files/logs/health.log`.
5. Stop sends SIGTERM to `./nouride start` (proot itself ignores SIGTERM), then SIGQUIT to proot (which kills
   everything it runs), then SIGKILL.

The proot command line is the one `proot-distro login debian` (5.10) builds on Termux, with this app's paths:

```
libproot.so --kill-on-exit --link2symlink --sysvipc
  '--kernel-release=\Linux\localhost\6.17.0-PRoot-Distro\#1 SMP PREEMPT_DYNAMIC Fri, 10 Oct 2025 00:00:00 +0000\aarch64\localdomain\-1\'
  -L --change-id=0:0 --rootfs=<files>/debian/rootfs --cwd=/root
  --bind=/dev --bind=/proc --bind=/sys --bind=/dev/urandom:/dev/random
  --bind=<sysdata>/sys_empty:/sys/fs/selinux
  --bind=<sysdata>/<name>:<path>   (for each of the 9 /proc files the app cannot read)
  --bind=<files>/debian/shm:/dev/shm
  /bin/su -s /bin/sh nouride -c 'cd /srv/nouride && exec ./nouride start'
env: PROOT_LOADER=<nativeLibraryDir>/libproot-loader.so PROOT_TMP_DIR=<files>/proot-tmp
     PROOT_L2S_DIR=<rootfs>/.l2s HOME=/root PATH=<Debian's> LANG=C.UTF-8 TMPDIR=/tmp
```

Small decisions taken in the spike, within the brief:

- **A Wi-Fi lock next to the wake lock**, as Termux's wake lock does. Android 15 reports it as type 4
  (`WIFI_MODE_FULL_LOW_LATENCY`): `WIFI_MODE_FULL_HIGH_PERF` is deprecated since API 34.
- **The asset is named `rootfs.tgz`**, not `.tar.gz`: the Android Gradle plugin gunzips assets named `*.gz`
  (the first APK carried a 146 MB `rootfs.tar`). As `.tgz` the APK carries the registry layer byte for byte,
  stored uncompressed.
- **`apt-get`, not `apt`**, in the install script: `apt` warns that its CLI is not stable for scripts.
- **proot differs from Termux's build** in three ways: talloc is static (Termux's binary has a RUNPATH into
  `/data/data/com.termux`), no libandroid-shmem (it only shares SysV shm with Termux's own processes), and only
  the 64-bit loader is shipped. The compiled-in loader directory is never used: the app always sets
  `PROOT_LOADER`.
- **Port 18264**, so the spike does not collide with the Termux install of Nouride on 18254.
- **Three adb hooks**: `StatusActivity` takes the boolean extras `start`, `stop` and `dashboard`, because the
  phone refuses input injection and `DashboardActivity` is not exported.

## 2. How it was built

On the dev box, not in GitHub Actions: `scripts/setup-toolchain.sh ~/work/android-toolchain` (no root), then

```
. ~/work/android-toolchain/env.sh
scripts/build-proot.sh          # 28 s
scripts/build-rootfs.sh
./gradlew assembleDebug testDebugUnitTest
```

Toolchain: Temurin 17.0.21+9, Android command-line tools 23.0 (16111833; `sdkmanager` now prints that it is
deprecated in favour of `android sdk`, but works), platform android-36, build-tools 35.0.0, NDK r27d
(27.3.13750724), Gradle 8.14.6 (wrapper), Android Gradle plugin 8.13.2, Kotlin 2.3.21. A cold
`assembleDebug` took 1 min 40 s, an incremental one 4 to 7 s; the unit tests 7 s.

| Asset | Source (sha256 in `scripts/assets.lock`) | Output sha256 | Size |
| --- | --- | --- | --- |
| `libproot.so` | termux/proot v5.1.107.102 archive, talloc 2.5.0, NDK r27d, API 24 | `be3079e062aae8222a47df49d29801d7479a7e3871af5be8957da2e0ebf98b44` | 287,304 B, PIE, needs only `libc.so` and `libdl.so` |
| `libproot-loader.so` | same build | `a192f4a0c2999542c414111ad88c4603ea74e0a0cab5d40d8c65e839ee514934` | 5,336 B, static |
| `rootfs.tgz` | `docker.io/library/debian:trixie`, linux/arm64/v8, built 2026-11-04 | `23b69c48ce5c2a6fb0681d959fa700427ad2a7cf7e6be5d25249170a1853d7ad` (= the layer digest) | 49,778,604 B |

**APK size: 50,867,228 bytes (48.5 MiB)**, debug-signed, sha256
`bd3c0078b9000309babf7829d6ef735813242b7809641912ee4d4e762baea10b`. Of that, 49.8 MB is the rootfs (stored),
2.6 MB dex (Kotlin's standard library, unshrunk in a debug build) and 0.3 MB proot.

## 3. Tests

`./gradlew testDebugUnitTest`: 18 tests, all pass.

| Test | What it holds |
| --- | --- |
| `ProotArgsTest` (3) | The exact argv above, fake /proc binds only for unreadable files, `/dev/fd` and stdio binds only where the host lacks them, the proot environment. |
| `BackoffTest` (3) | 2 s doubling to 5 min, reset after a 10-minute run, the cap holds after 100 failures. |
| `HealthTest` (4) | `ok` only for HTTP 200 with `status: ok`; `starting` and `degraded` (503) are not; a non-JSON body is reported, not thrown. |
| `ProcTest` (2) | ppid from `/proc/<pid>/stat` with a name holding spaces and parentheses; descendants in order. |
| `RootfsExtractorTest` (6) | Files, modes, symlinks, hard links as copies; GNU long names and pax paths; `..` and entries under a symlink refused; **the real Debian layer unpacked** (over 5,000 entries, `apt-get` executable, `perl5.40.1` a copy of `perl`) with the fixups applied. |

Sabotage, each run once and reverted: `--link2symlink` dropped from the argv (ProotArgsTest red); the backoff
reset only after more than 10 min (BackoffTest red); health `ok` from the HTTP code alone (HealthTest red);
the symlinked-parent check removed (RootfsExtractorTest red); the pax `path` record ignored
(RootfsExtractorTest red).

## 4. Phone evidence

The phone: Redmi 12 (`23021RAA2Y`, `topaz`), HyperOS `OS2.0.202.0.VMGIDXM`, Android 15 (SDK 35), kernel
5.15.167, 5.5 GiB RAM, arm64-v8a. Wireless ADB at `10.18.1.99:42571`. Already set on the phone before the
spike (by the Termux setup): `max_phantom_processes=2147483647`. When the spike started, Termux was not
running (ports 8022 and 18254 closed), so the Termux install of Nouride (agent reno) was down; the spike did
not touch it until Gading asked for reno to move into the app and for Termux to go (4.7).

### 4.1 Before the APK: the same proot from `adb shell`

To find installer or daemon problems before the one install Play Protect lets through, the built proot ran the
whole flow from `adb shell` (the `shell` user, not the app) in a scratch folder in `/data/local/tmp`, on port
18274. The folder was deleted afterwards.

- Unpacked with toybox tar: `tar: can't link 'usr/bin/perl5.40.1' -> 'usr/bin/perl': Permission denied`.
  Android refuses hard links; the app's extractor copies them.
- `apt-get update` and the three packages: 15:33:07 to 15:37:43 (4 min 36 s, screen off). The installer:
  44 s, exit 0, `no systemd here, so the daemon will be installed but not supervised`, `created user nouride`,
  Nouride 0.8.21.
- `nouride ready` at 15:38:42.762Z, 3.6 s after start. From the dev box over the LAN: `/health` HTTP 200
  `ok` in 0.08 s, `/dashboard/` HTTP 200.
- The host sees `libproot.so` → `su` (`/bin/su -s /bin/sh nouride -c ...`) → `nouride` (cmdline
  `./nouride start`). SIGTERM to the daemon: `shutting down`, then `shutdown complete` 56 ms later; proot
  exited by itself within 0.3 s.

### 4.2 The install over wireless ADB

```
2026-11-06T15:22:30Z  adb install -r app-debug.apk
  adb: failed to install app-debug.apk: Failure [INSTALL_FAILED_VERIFICATION_FAILURE: Install not allowed for file:///data/app/vmdl1642058399.tmp]
  real 2m5.359s
```

logcat at that moment: `Finsky: VerifyApps: Returning package verification result id=109, result=REJECT`,
then `PackageManager: Install not allowed`. Google Play Protect checks every `adb install`
(`package_verifier_user_consent=1`, `upload_apk_enable=1`) and waits for an answer on screen; the screen was
off. `com.google.android.verifier` (`BackportVerificationService`) was bound during the install as well. A
watcher on the dev box then installed the APK each time the screen came on:

```
== screen on at 2026-11-06T17:38:13Z, install try 1
Failure [INSTALL_FAILED_VERIFICATION_FAILURE: Install not allowed for file:///data/app/vmdl286623148.tmp]
== adb install exit 1 after 78s
== screen on at 2026-11-06T17:53:01Z, install try 2
Performing Streamed Install
Success
== adb install exit 0 after 36s at 2026-11-06T17:53:37Z
Starting: Intent { cmp=tech.nouverse.nouride.android/.ui.StatusActivity (has extras) }
```

### 4.3 First start in the app

The app's own log (`files/logs/service.log`, WIB):

```
2026-11-07T00:53:40+0700 server started
2026-11-07T00:53:46+0700 rootfs unpacked: 5442 entries in 6s
2026-11-07T00:53:46+0700 installer started
2026-11-07T00:54:57+0700 installer exited with 0
2026-11-07T00:54:57+0700 daemon started
2026-11-07T00:54:59+0700 daemon ready
```

The daemon's line (`files/logs/daemon.log`):

```
{"timestamp":"2026-11-06T17:54:58.072Z","level":"info","component":"web","message":"http listening","data":{"url":"http://0.0.0.0:18264","dashboard":"http://localhost:18264/dashboard/"}}
{"timestamp":"2026-11-06T17:54:59.199Z","level":"info","component":"daemon","message":"nouride ready","data":{"agents":[],"gateways":["web:dashboard","web:mission"],"providers":[],"uptime_ms":1168}}
```

From Debian's start to a ready daemon took 79 s with the screen on (the same apt step took 4 min 36 s from
`adb shell` with the screen off). The daemon logged the same warning as on Termux:
`agents' commands run as the daemon's own user` (no `nouride-run` without systemd).

**proot executes from the app's data directory at targetSdk 28 on Android 15 (W^X does not apply).** Every
Debian program runs from `files/debian/rootfs` through the loader in `nativeLibraryDir`.

All nine fake /proc files were bound: the app (SELinux domain `untrusted_app_27`) can read none of
`/proc/{loadavg,stat,uptime,version,vmstat}`, `/proc/sys/kernel/{cap_last_cap,overflowuid,overflowgid}` and
`/proc/sys/fs/inotify/max_user_watches`. The `shell` user can read the first five.

### 4.4 Android's view (2026-11-07 01:03:39 WIB)

```
ServerService  isForeground=true foregroundId=1 types=0x40000000          (specialUse)
ProcessRecord  26111:tech.nouverse.nouride.android/u0a306  curProcState=4  oom adj 200
standby bucket 5 (exempted)
deviceidle whitelist: user,tech.nouverse.nouride.android,10306            (battery exemption granted on the phone)
deep=IDLE
PARTIAL_WAKE_LOCK 'nouride:server' ... LONG (uid=10306 pid=26111)
WifiLock{nouride:server type=4 uid=1000 workSource=WorkSource{10306 tech.nouverse.nouride.android}}
battery: AC powered: false, USB powered: false, level: 83, temperature: 31.0 C
u0_a306 26111  1351 278748 tech.nouverse.nouride.android
u0_a306 30949 26111   3960 libproot.so
u0_a306 30952 30949   3088 su
u0_a306 30954 30952  65556 nouride
```

The app process' 272 MB RSS includes a WebView: `DashboardActivity` had been opened on the phone at about
00:54:28 WIB, before the daemon was ready.

Storage after the first start: rootfs 321 MB (of which `/srv/nouride` 119 MB), app data 326 MB, plus the
50.9 MB APK.

### 4.5 The dashboard from the LAN (T0)

```
== 2026-11-06T18:04:15Z from agent-host (LAN)
GET http://10.18.1.99:18264/health
{"status":"ok","edition":"basic","appliance":false,"uptime":558,"version":"0.8.21","eventProtocol":"1.8","agents":0,"install":"461c687569e7","gateways":{"web:dashboard":"connected"},"memory":{"rssBytes":67055616,"heapUsedBytes":8719051},...}
HTTP 200 in 0.107090s
GET /dashboard/ HTTP 200, 2940 bytes, text/html;charset=utf-8, 0.018548s
<title>Nouride Dashboard</title>
```

### 4.6 The soak: 6 hours on battery, 5 h 30 min of it with the screen off

From the dev box, `soak.sh` asked `http://10.18.1.99:18264/health` every 5 minutes and read the phone's screen
state, Doze state and battery over adb (`evidence/soak.log`). In the app, `HealthPoller` asked
`127.0.0.1` every 60 s (`evidence/app/health.log`).

- **Window:** daemon ready at 2026-11-06T17:54:59Z, last reading at 2026-11-07T00:06:39Z: 6 h 12 min. Gading
  closed the test then (07:04 WIB), before the 8 hours of screen off that would have ended at 02:40Z.
- **Screen:** on at the 17:53:38Z sample (the install) and at the 18:30:16Z sample (01:30 WIB); off at all 67
  samples from 18:35:16Z to 00:05:45Z, 5 h 30 min without a break.
- **From the LAN:** 74 of 75 samples HTTP 200 `ok`, in 0.019 to 0.251 s (median 0.118 s). The one miss is the
  first sample, 17:53:38Z, before the daemon had started. All 67 screen-off samples answered.
- **Doze:** deep `IDLE` at 63 of the 67 screen-off samples (2 `IDLE_MAINTENANCE`, 1 `IDLE_PENDING`, 1
  `INACTIVE`). The app is on the Doze allowlist and holds its wake lock, so its network stays up in deep idle.
- **In the app:** `health.log` has a line every 60 s from 00:55:40 to 07:05:56 WIB with no gap over 90 s, 371
  lines `200 ok`. Its only misses are 00:53:40 and 00:54:40 WIB, before `ready`.
- **Battery:** on battery throughout (no AC, no USB). 84 % at 17:53Z, 83 % at the last screen-on sample
  (18:30Z), 79 % at 00:06Z: 4 points over the screen-off stretch, under 1 point an hour. Temperature 31.0 °C
  at 18:03Z, 27.0 °C at 00:06Z.
- **Android restarted nothing:** at 07:06:40 WIB `ServerService` was 6 h 13 min old with its `restartTime`
  equal to its `createTime`, the app process still had pid 26111 (as at 01:03:39 WIB), the wake lock had been
  held for 6 h 13 min, standby bucket 5, `curProcState=4`.
- **One daemon restart, on purpose:** at 18:42:03Z the daemon got SIGTERM over adb so it would load reno's
  Telegram gateway (4.7). `Supervisor` logged `daemon exited with 0, restarting in 2s`, and `nouride ready`
  came again at 18:42:08Z; the app's 60 s poll fell outside the gap. The `/health` uptime at the last reading,
  19,474 s, counts from that restart.
- **Memory at the end:** `nouride` 52 MB RSS (64 MB at T0), the app process 157 MB with no WebView open.

The last reading (`evidence/lan-T1.txt`):

```
== 2026-11-07T00:06:39Z from agent-host (LAN)
GET http://10.18.1.99:18264/health
{"status":"ok","edition":"basic","appliance":false,"uptime":19474,"version":"0.8.21","eventProtocol":"1.8","agents":1,"install":"461c687569e7","gateways":{"telegram:reno":"connected","web:dashboard":"connected"},"memory":{"rssBytes":51675136,"heapUsedBytes":12725429},...}
HTTP 200 in 0.042371s
GET /dashboard/ HTTP 200, 2940 bytes, text/html;charset=utf-8, 0.106684s
<title>Nouride Dashboard</title>
```

### 4.7 Gading's agent in the app

During the soak, at Gading's request, his agent reno moved from Termux into the app, and Termux was removed:

- Through `files/nr.sh` (`adb shell run-as tech.nouverse.nouride.android files/nr.sh <command>`, the app's
  proot command line): the provider is Nougate on the LAN with model `antigravity:gemini-3.8-flash`, the
  gateway `telegram:reno` (@RenoNourideBot) polls, and access is `allowlist` with Gading's account allowed.
  Then the restart above.
- `adb uninstall com.termux`: `Success`. The Termux install of Nouride 0.8.18 went with it. The leftovers in
  `/data/local/tmp` were removed.
- `telegram:reno` connected at 18:42:08Z and was still connected at the last reading.
- A Telegram message reached the daemon at 00:03:49Z, after about 5 h 30 min with the screen off. Its turn
  failed at the provider: Nougate answered `403 Forbidden: Access denied: unknown_error` for `antigravity`.
  Not the app: at 00:07Z, from the dev box, the same model through the same Nougate answered "This version of
  Antigravity is no longer supported", and `copilot:gemini-3.8-flash` answered normally.

## 5. What failed and how it was got around

| What | Got around by |
| --- | --- |
| `yes \| sdkmanager --licenses` ended the setup script: `yes` got SIGPIPE under `set -o pipefail`. | Dropped: this `sdkmanager` says the licenses step is no longer needed. |
| samba.org stalled for 3 minutes, then reset the connection. | `curl --retry 5 --retry-all-errors` in the scripts; the next try took 16 s. |
| NDK r27d's clang refuses `ashmem_memfd.c`: `strcmp` and `memset` used without `<string.h>`. | `build-proot.sh` adds the include to that one file. Forcing it into every file broke `loader.c` (its own static `basename`). |
| `loader/loader-info.awk` needs gawk's `strtonum`; the box has mawk. | `build-proot.sh` writes `loader-info.c` itself from the two symbols, read with `llvm-readelf`. |
| Static talloc: `undefined symbol: rep_memset_explicit`. | The archive takes libreplace's `replace.c` object next to `talloc.c`. |
| Android refuses hard links in app storage (`link(2)`: Permission denied). | The extractor copies the file; the Debian layer has one hard link (`perl5.40.1`). |
| The Android Gradle plugin gunzips `*.gz` assets: the APK carried a 146 MB `rootfs.tar`. | The asset is `rootfs.tgz`, stored as is. |
| Play Protect refused `adb install` twice while nobody could answer on screen. | A watcher installed each time the screen came on; the third attempt succeeded. |
| The dev box's `nouride.service` restarted at 2026-11-06T18:00:43Z and systemd killed every process in its cgroup, the soak logger and the adb server among them. | The logger was restarted at 18:05:14Z; the gap (17:58:38Z to 18:05:14Z) is covered by the app's own `health.log` on the phone. |

## 6. Still unknown

- **A full night.** The soak stopped at 5 h 30 min with the screen off. Eight hours and more, several nights,
  and what HyperOS does after that are not measured.
- **An agent turn in the app.** The daemon received a Telegram message after 5 h 30 min with the screen off,
  but the only turn of the night failed at the provider (4.7). No agent turn, and none of an agent's commands
  under proot, has completed in the app yet.
- **A reboot.** `BootReceiver` is built and never ran: the phone was not rebooted. Xiaomi's Autostart
  permission may hold `BOOT_COMPLETED` back.
- **Without the battery exemption.** Gading granted it on the first start; the app without it was not
  measured.
- **The phantom process killer.** This phone had `max_phantom_processes` raised for Termux before the spike. On
  a phone without that, Android 12 and later may kill proot and the daemon. Not tested.
- **On a charger.** Measured on battery only. A server phone mostly sits on a charger, where HyperOS's battery
  protection was not observed.
- **Other phones.** One Redmi 12 on HyperOS 2.0 and Android 15.
- **A release build.** Only the debug-signed APK was installed. Whether a build signed with our key installs
  without a person confirming on the screen is open.
- **Updating Nouride.** `nouride update` in the app was not tried.
- **Low memory.** Not tested, with or without agents at work.

## 7. Recommendation

**Go** for the full app. The two results that would have stopped it did not happen: proot runs Debian from
the app's data directory at targetSdk 28 on Android 15, and HyperOS kept the `specialUse` foreground service,
its wake lock and the LAN port up for 6 h 12 min on battery, the last 5 h 30 min with the screen off, without a
single restart. The RFC (`internals/pro/docs/rfc/RFC_ANDROID_APPLIANCE.md` in the main repository) is rewritten from these measurements.

Before the full app is called reliable, in this order:

1. One completed agent turn in the app, with a tool call, once Nougate's `antigravity` route answers again or
   on another model.
2. A full night or more with the screen off, on a charger as a server phone would sit, and one reboot to run
   `BootReceiver`.
3. A phone without the raised phantom-process limit, and the app without the battery exemption.
4. A release-signed APK installed the way a user would, to see what Play Protect and developer verification
   ask.
