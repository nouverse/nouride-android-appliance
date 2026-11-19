# Nouride's files as one zip

"Download Nouride's files", on the phone's More and the computer's Settings, saves the Nouride the app runs as one
`.zip` in the person's Downloads: their chats, memories, agents and files, to keep or to move. Only while Nouride runs
on this device: on another computer its files are there, not here. Cases: `fixtures/backup.json`.

## What is in it

The folder Nouride keeps everything in, `.nouride` beside the `nouride` program (`/srv/nouride/.nouride` in the
phone's Debian, `<the app's folder>/nouride/.nouride` on a computer), each file under `.nouride/` in the zip, so the zip
unpacks where the folder was. The install's own `.env` (the control token, the first password, and on some installs the
key to the secret store) is beside that folder, never in it, and never in the zip.

Then, in its place, the database: Nouride is asked for a consistent snapshot first (`nouride db backup --json`, a
`VACUUM INTO` that is safe while Nouride runs), and that file goes in as `.nouride/data/nouride.db`. The open database
is never copied: read while Nouride writes to it, the copy may not open. `snapshot(output)` reads the command's answer:
`data.path` of its JSON line when `ok` is true, or no snapshot, which stops the zip.

## What stays out

`out` in the fixture is the list, path patterns under `.nouride/`: a pattern ending in `/` is a folder and everything in
it, `*` stands for any part of one name, `**/` for any number of folders. Both apps hold the same list and are tested to.

- **Secrets in the clear**, and what opens them: the secret store (`data/secrets.enc`, the older `data/secrets.json`),
  its key (`encryption.key`), the control token (`data/.control-token`), files kept as credentials (`credentials/`),
  the agents' home with their sign-ins and keys (`home/`), the coding tools' sign-ins
  (`workspace/private/*/.cli/`), the WhatsApp session (`data/whatsapp/`), a Box's identity (`data/appliance/`), the
  coding tools' accounts of before 0.8.18 (`data/cli-accounts.json`), and the LLM gateway, its accounts and its own
  open database (`nougate/`). The secrets go in only sealed: `data/secrets.sealed`, the whole store sealed to the
  backup password a person sets in Nouride, which only that password opens. Without a backup password there is none,
  and the zip has no secret at all.
- **The open database and what comes with it**: `data/nouride.*` (the database, its `-wal` and `-shm`, its epoch, a
  database a restore set aside, the log on a Mac, the lock), and the snapshots Nouride keeps (`data/backups/`): the
  one taken for this zip is in it already.
- **What the installer or Nouride puts back**: the tools Nouride installs (`bin/`, `lib/`, `opt/`, `share/`,
  `system-packages/`, `.crates.toml`, `.crates2.json`) and, anywhere, `node_modules/`, `.venv/` and `__pycache__/`.
- **Logs and caches**: background jobs' output (`jobs/`), large tool output (`tool-output/`), the document reader's
  cache (`data/knowledge/`), the marker of a clean stop (`data/clean-shutdown.json`) and the agents' scratch folders
  (`workspace/private/*/.scratchpad/`).

A link is never followed, in or out of the folder: it could lead anywhere on the device.

## Its name, its size and room

`zipName(date)` is `nouride-backup-<YYYY-MM-DD>.zip`, the device's own date; a second one that day is numbered as any
download is (`downloads.md`). Before it starts, the app says what the zip holds and how big it may be: the files that
go in, the database at its size now. When Downloads has not that much room, with some left for the device, it says
so and does not start. The zip is written as it is read, never held whole in memory, and a zip that fails half way is
removed.

## Where the apps differ

Nowhere today.
