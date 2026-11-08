# Experiments

What was tried and measured outside the app's code: spikes, soaks, benchmarks, how a phone behaves. One folder each,
named `<the date it started>-<what>`:

- `README.md`: the question, how it was run, what was measured, and what it means.
- `evidence/`: the raw logs and the scripts that gathered them, with passwords, personal ids and network names removed.

A folder is a record of its run. When a later run changes a number, it gets a folder of its own; the old one is not
edited.

| Folder | What it answered |
| --- | --- |
| [2026-11-06-apk-on-redmi-12](./2026-11-06-apk-on-redmi-12/) | Whether one APK can run Nouride in Debian under proot on a Redmi 12, installed over wireless ADB, reachable from the LAN, with the screen off: yes, 6 h 12 min on battery, 5 h 30 min of it screen off, every `/health` answered |
| [2026-11-08-restart-on-hyperos](./2026-11-08-restart-on-hyperos/) | Whether Nouride comes back after a Redmi Note 12 restarts, and whether HyperOS's Autostart is needed for it: yes, 40 s after the first unlock; and not on that phone, where the Dev build with Autostart off came back too |
