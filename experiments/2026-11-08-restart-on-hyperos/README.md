# Nouride after the phone restarts, on HyperOS

**Question:** does Nouride Home start Nouride again by itself after the phone restarts, on a Xiaomi phone, and does it
need HyperOS's Autostart switch for that?

**Answer, on one phone:** yes, about 40 s after the first unlock; and no, not on this phone. The release build, with
Autostart on, and the Dev build, with Autostart off, both started Nouride from `BootReceiver`, with nobody opening
either app.

## The phone and the builds

- Redmi Note 12 (`23021RAA2Y`), HyperOS `OS2.0.202.0.VMGIDXM` (global), Android 15, 2026-11-08.
- Nouride Home 0.1.4 (release signing, installed over 0.1.3 by its own updater), Nouride 0.8.22, Router edition, on
  18254. Autostart on (`appops get tech.nouverse.nouride.home 10008`: `allow`), turned on in Security's Autostart list.
- Nouride Home Dev (debug build of the same source plus the Autostart button change), Nouride on 18264. Autostart off
  (`10008`: `ignore`).
- Both: the battery exemption given through the app's Allow (`dumpsys deviceidle whitelist` lists both), and HyperOS's
  battery saver for each app on No restrictions. Both had Nouride running before the restart (`PREF_RUN`).

## How it was run

1. `adb reboot` at 05:49:59Z, from the dev box over wireless ADB.
2. The owner unlocked the phone once (pattern) when the lock screen came up, and opened neither app.
3. From the dev box, `evidence/watch_back.py` scanned the LAN for `:18254` every few seconds and read `/health` (the
   phone's address can change after a restart; it did not this time, 10.18.1.98).
4. Once wireless debugging was turned on again, `adb logcat -d -b all` from the boot; the lines about the unlock and
   the two apps are in `evidence/logcat-restart.txt`.

## What was measured

| When (phone time, UTC+7) | What |
| --- | --- |
| 12:51:16.0 | `LOCKED_BOOT_COMPLETED` sent: the boot is done, the phone still locked |
| 12:52:03.9 | The owner unlocks (`miui_keyguard_password: pattern unlock`) |
| 12:52:44.2 | `Start proc ... tech.nouverse.nouride.home ... for broadcast {...BootReceiver}` |
| 12:52:44.3 | The same for `tech.nouverse.nouride.home.dev`, Autostart off |
| 12:52:44.4 | `Background started FGS: Allowed ... ServerService ... code:SYSTEM_ALLOW_LISTED` for the release (12:52:44.8 for Dev) |
| 12:52:53 | `/health` answers on 18254: `status ok`, `uptime 7`, 0.8.22; 18264 answers too |

So: 40 s from the unlock to the broadcast, 9 s from the broadcast to a Nouride that answers, 2 min 54 s from
`adb reboot` to `/health`.

In the same boot, HyperOS refused `MEDIA_MOUNTED` to `com.miui.player` ("process is not permitted to auto start"), and
listed its own restricted apps (`isAutoStartRestrict`, mode 1 for Telegram, TikTok Lite and others); neither Nouride
Home build was in that list. On 2026-11-07 the same phone had refused `MY_PACKAGE_REPLACED` to Nouride Home (Autostart
off then).

## What it means

- `BootReceiver` works on HyperOS; Nouride comes back after a restart once the phone is unlocked. Before the unlock,
  nothing runs: the app is not direct-boot aware, and its data is in credential-encrypted storage.
- On this phone and HyperOS version, Autostart is not what lets `BOOT_COMPLETED` through. What did let it through for
  the Dev build is not proven: the battery exemption is the likely difference from `com.miui.player`, which was
  refused. Other HyperOS and MIUI versions are reported (dontkillmyapp.com's Xiaomi page among others) to hold `BOOT_COMPLETED`
  back without Autostart, so the More
  tab keeps the step, worded as one some Xiaomi phones need.
- The app's page in Settings has no Autostart switch on HyperOS 2: the switch is in Security's list
  (`miui.intent.action.OP_AUTO_START`). Open settings went to the app's page until 0.1.5; it now opens the list, checked
  from the Dev build's button on this phone (`AutoStartManagementActivity` came to the front).

## Not tried

- A restart with the battery exemption taken away, to tell whether it is what lets the boot broadcast through.
- Other Xiaomi phones and HyperOS versions, the China ROM, and phones of other brands.
- A restart with the phone left locked for a long time (Nouride stays off until the unlock, by design).
