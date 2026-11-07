---
name: android-appliance-ui
description: The rules for everything a person sees in Nouride Home, the Android app that runs Nouride on the phone, whose users are not technical - the status screen, the first start, the notification, buttons, errors, battery guidance and the words on them, in English and Indonesian; what is hidden by default (logs, paths, ports, process states) and where it goes instead. Use when adding or changing a screen, a notification, a button, a message or an error, or reviewing one.
---

# A phone server for people who are not technical

The person using this app wants their phone to keep Nouride running. They do not know what a log, a port, a process
or Debian is, and they should not have to learn. If a screen needs one of those words to make sense, the screen is
wrong, not the person.

## What is never shown by default

- Log lines, of the installer, of Nouride or of the app. Not a tail, not "the last error".
- File paths, package names, process states (`RUNNING`, `phase`), enum names, exit codes, exception messages, JSON.
- The words health, proot, Debian, rootfs, daemon, service, PID, SIGTERM, localhost, `127.0.0.1`.
- A port number on its own, or a URL a person has to type, where a button can open it or a share sheet can send it.
- Monospace text.

All of it lives behind one **Details** switch, off by default, at the bottom of the status screen. Details is for
support: it shows the technical state, the address with its port, and the end of the log, with a button that copies
it all to share with whoever helps. Turning it on is remembered on this phone only.

## What the status screen shows

One sentence of state, in plain words, and one main button. Each state has its own sentence and its own button:

| State | What the person reads | Main button |
| --- | --- | --- |
| Stopped | "Nouride is off." | Start |
| First start: unpacking, installing | "Setting up Nouride. This takes a few minutes the first time; keep the phone on Wi-Fi and plugged in." with a progress bar | (none; Cancel as a text button) |
| Starting | "Starting Nouride..." | (none) |
| Running and answering | "Nouride is running." | Open Nouride |
| Restarting on its own | "Nouride stopped for a moment and is starting again." | (none) |
| Failed | What went wrong in one sentence, and what to do: "Nouride could not start. Check that the phone is on Wi-Fi, then try again." | Try again |

Under the main state, only what a person acts on:

- **Open Nouride**: the dashboard inside the app. Not a URL to copy.
- **Use from another device**: a button that shows the address as large text and a QR code, with "Open this on a
  computer or tablet on the same Wi-Fi". Never shown as `http://10.x.x.x:18254/...` in the middle of the screen.
- **Keep running with the screen off**: shown only while the phone may stop the app, with one sentence of why ("Your
  phone may stop Nouride to save battery. Allow it to keep running.") and a button that opens the right setting.
  For brands with their own battery screens (Xiaomi, Oppo, Vivo, Samsung), a short step list for that brand, with
  the exact names that brand's settings use.
- **Stop**: a text button, not a main button. Stopping asks once: "Stop Nouride? Your agents will not answer until
  you start it again."

## Words

- Short sentences. What happened, then what to do. Never an error without a next step.
- The app is "Nouride Home"; what it runs is "Nouride", never the server, the daemon or the service. Say "the
  phone", not the device.
- Times as people say them: "a few minutes", "just now". Not timestamps.
- Every string is a resource, in `res/values/strings.xml` (English) and `res/values-in/strings.xml` (Indonesian),
  written as a person would say it in that language, not translated word for word. Indonesian is everyday Indonesian:
  "Nouride sedang jalan.", not "Server sedang dalam status berjalan."
- No exclamation marks, no emoji, no jargon in either language.

## The notification

The foreground service must show one. It says the state in the same words as the screen ("Nouride is running") and
nothing technical. Tapping it opens the status screen. It has no log line and no address.

## Errors

A failure the person can fix says how in one sentence (no Wi-Fi, no space left, battery setting). A failure they
cannot fix says so plainly and offers Try again and "Copy details for support", which copies the Details text. Never
a stack trace, never a raw message from Linux or Android.

## Checking a change

- Read every new screen and message as someone who has never used a computer for work would. If a word needs
  explaining, replace it.
- Test the state-to-words mapping as a unit test (the sentence and the button for each state), so a new state cannot
  ship without words.
- Look at it on a real phone, in both languages, with Details off, and say in the change that you did.
