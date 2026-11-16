# Experiments

What was tried and measured outside the apps' code, on real phones and computers: spikes, soaks, benchmarks, how a
phone or a Mac behaves. One folder each, named `<the date it started>-<what>`:

- `README.md`: the question, how it was run, what was measured, and what it means.
- `evidence/`: the raw logs and the scripts that gathered them, with credentials, personal ids, personal paths,
  addresses and network names removed.

A folder is a record of its run. When a later run changes a number, it gets a folder of its own; the old one is not
edited.

| Folder | What it answered |
| --- | --- |
| [2026-11-06-apk-on-redmi-12](./2026-11-06-apk-on-redmi-12/) | Whether one APK can run Nouride in Debian under proot on a Redmi 12, installed over wireless ADB, reachable from the LAN, with the screen off: yes, 6 h 12 min on battery, 5 h 30 min of it screen off, every `/health` answered |
| [2026-11-08-restart-on-hyperos](./2026-11-08-restart-on-hyperos/) | Whether Nouride comes back after a Redmi Note 12 restarts, and whether HyperOS's Autostart is needed for it: yes, 40 s after the first unlock; and not on that phone, where the Dev build with Autostart off came back too |
| [2026-11-16-macos-e2e](./2026-11-16-macos-e2e/) | Nouride Home Dev end to end on a Mac: first question, install, dashboard signed in, Nouride update, Connect, another computer and back, the menu bar; the owner's Nouride untouched |
| [2026-11-16-desktop-layout](./2026-11-16-desktop-layout/) | The desktop layout (sidebar, icon rail, Settings on one page) at four window sizes, light and dark, drawn from the app's own screens; and the Mac's record of sign-in links opening the browser |
| [2026-11-16-modals-memory](./2026-11-16-modals-memory/) | Whether closing the desktop app's windows gives their memory back: yes, about 104 MB with both closed against about 338 MB before |
| [2026-11-16-downloads-and-backup](./2026-11-16-downloads-and-backup/) | Whether a file the dashboard downloads, and a zip of Nouride's files, reach Downloads on a Redmi Note 12 and a Mac: yes, both downloads and blobs, a second copy numbered; the zip without the secrets in the clear or the open database, and its snapshot passes `nouride db verify`; macOS asks once for the Downloads folder |
