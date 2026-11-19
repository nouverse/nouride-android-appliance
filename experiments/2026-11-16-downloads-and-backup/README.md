# Downloads from the dashboard, and Nouride's files as one zip (0.2.1)

## The question

Inside both apps, does a file the dashboard downloads end up in the person's Downloads folder, under its own name,
with a second copy numbered? This covers a file from System → Files and a "Download all" zip, which the page builds as
a `blob:`. And does "Download Nouride's files" make a zip with the person's data and a consistent database, and without
the secrets in the clear, the open database, or what the installer puts back (`spec/backup.md`)?

## Android: Redmi Note 12, HyperOS 2.0, Android 15 (2026-11-17, 00:00 to 00:16 +0700)

Nouride Home Dev, a debug build of `release/0.2.1-downloads`, running Nouride 0.8.38 on the phone, driven over wireless
ADB. This Dev install's dashboard was on its first-run "Personalize your account" page, and finishing it would have
changed the app's own account on the owner's phone. So the page was driven over WebView devtools (`evidence/cdp.ts`),
with links built the way the dashboard builds them: `<a href download=name>`, added to the body, then clicked. That is
`use-file-actions` `save()` for Files and `download-all` `saveAs()` for "Download all".

What was seen (`evidence/android.txt`):

- **A file from Files.** `GET /api/control/system/files/download?path=config.toml&check=1` answered 200, then the link
  was clicked. `config.toml` (41 bytes) appeared in `/sdcard/Download`, with the same sha256 as the install's file.
  Clicking again gave `config (1).toml`. Android asked for no permission: the file went in through
  `MediaStore.Downloads`.
- **A blob zip.** A 1 600 222-byte zip made in the page went over in four 512 KiB pieces and was saved as `slide.zip`,
  byte for byte. The first try failed because the dashboard's CSP is `connect-src 'self'`, which refuses
  `fetch(blob:)`. The script now keeps each Blob as the page makes its URL.
- **Download Nouride's files** on More. The card said the zip would be "About 1,5 MB" and that keys and passwords stay
  out, as this install has no backup password. Pressing it made `nouride-backup-2026-11-17.zip`, 34 200 bytes
  (`evidence/android-unzip-l.txt`): `config.toml`, `data/last-run-version`, `memory/.nouride-vault.json`,
  `people/admin.toml` and the snapshot as `.nouride/data/nouride.db`. Left out, though they were in the folder:
  - `data/.control-token`
  - the live database with its `-wal`, `-shm` and `.epoch`
  - `data/nouride.lock`
  - `data/backups/`
  - `home/.ssh`
  - `nougate/data`
  - `bin/`
  - `credentials/`

  `nouride db verify` on the zipped database: `ok: true`, exit 0. It ran on the dev box with a copy of Nouride 0.8.39;
  the phone runs 0.8.38.
- **A bigger zip.** With a 150 MiB random test file added under `workspace/shared`, the card said "About 159 MB". The
  bar moved (`evidence/android-more-saving.png`, 37%), and about 8 s later the zip was saved as
  `nouride-backup-2026-11-17 (1).zip`, 157 368 856 bytes (`evidence/android-more-saved.png`). `unzip -t` found no
  error, and each entry kept its own time (`evidence/android-unzip-l-big.txt`).

The test file, the zips and the downloaded files were removed afterwards. Each zip leaves one more snapshot in
Nouride's `data/backups/db`, which Nouride trims to `backup_keep` itself.

Not seen on the phone:
- a sealed secret store in the zip
- the "not enough room" line
- the card hidden for another computer (unit-tested: `BackupLooks.shown`)
- Android 9 or older, where the app asks for the storage permission first

## Mac: the owner's Mac, macOS 27.0.1 (2026-11-17, 06:10 to 06:20 +0700)

Nouride Home Dev 0.2.1, built from the branch in a folder of its own, with Nouride 0.8.38 that the Dev app had
installed. Everything was driven with the Dev build's e2e hooks (`scripts/e2e-mac.sh`). The owner's own Nouride was
checked with `scripts/mac-guard.sh` before and after, and was unchanged. Details in `evidence/mac.txt`.

- **The first download asks macOS.** It waited in WebKit's sandbox call for `~/Downloads` until the owner answered
  macOS's question on screen: may Nouride Home Dev use the Downloads folder. After Allow, the downloads that were
  waiting finished. A release build will ask the same question once, on its first download.
- **A file from Files.** `config.toml` landed with the same sha256 as the install's file. Downloading it again gave
  `config (1).toml` up to `config (4).toml`. The line at the foot of the dashboard said "config (4).toml is in
  Downloads."
- **A blob zip.** A 270 222-byte zip made in the page landed as `slide.zip`, byte for byte.
- **Download Nouride's files.** The bar showed 27%, then `nouride-backup-2026-11-17.zip` was saved, 15.4 MB with 53
  entries (`evidence/mac-zip-summary.txt`). None of the excluded paths in the folder are in it. The database is the
  snapshot. `nouride db verify` on it, with the Dev install's own Nouride, passed. `unzip -t` found no errors.

Everything added to `~/Downloads`, the build folder and temporary files were removed afterwards. No picture of the
window was taken: `screencapture` exits 1 over ssh while the display sleeps.

## What it means

Both kinds of download the dashboard makes reach Downloads on both devices. The phone needs no permission on Android
10 and later. On the Mac, macOS asks once whether the app may use the Downloads folder.
The backup's exclusion list holds on a real install. The zipped database is a consistent snapshot that Nouride's own
check accepts.
