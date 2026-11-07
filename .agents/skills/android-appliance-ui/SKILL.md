---
name: android-appliance-ui
description: The rules for everything a person sees in Nouride Home, the Android app that runs Nouride on the phone, whose users are not technical - the status screen, the first start, the notification, buttons, errors, battery guidance and the words on them, in English; what is hidden by default (logs, paths, ports, process states) and where it goes instead. Use when adding or changing a screen, a notification, a button, a message or an error, or reviewing one.
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

All of it lives on one screen of its own, **Support details** (`ui/SupportActivity.kt`), reached by a quiet
"Details for support ›" link at the bottom of the status screen. It is for whoever helps: the technical state, the
address with its port, the app's events and Nouride's log, the log taking the rest of the screen and opening at its
end. Copy and Share send it all, Share straight into a chat. Never a box of log inside the scrolling status screen:
two scrolls in one another catch the wrong finger.

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

The first start shows a real bar under the sentence (`server/SetupProgress.kt`): what is happening now in plain words
("Downloading and installing Nouride"), "Step 4 of 5" and the percent. It never goes back and reaches 100 only when
Nouride answers. A later start is seconds long and only says it is busy.

Under the main state, only what a person acts on:

- **Open Nouride**: the dashboard inside the app, already signed in. Not a URL to copy, and never a login page on
  the phone: the app makes the dashboard account on the first start and signs in with it (`server/FirstAdmin.kt`).
  The password is never shown; a person who wants the dashboard elsewhere sets their own in the dashboard.
- **Use from another device**: a button that opens a card with the cat, "Open Nouride on a computer or tablet", one
  sentence ("on the same Wi-Fi as this phone, type this address in its browser"), the address large in a soft pill,
  then Share address as the main button, Copy, and Close. Never shown as `http://10.x.x.x:18254/...` in the middle
  of the screen.
- **Keep running with the screen off**: shown only while the phone may stop the app, with one sentence of why ("Your
  phone may stop Nouride to save battery. Allow it to keep running.") and a button that opens the right setting.
  For brands with their own battery screens (Xiaomi, Oppo, Vivo, Samsung), a short step list for that brand, with
  the exact names that brand's settings use.
- **Stop**: a text button, not a main button. Stopping asks once: "Stop Nouride? Your agents will not answer until
  you start it again."
- **A new version of the app** (`ui/UpdateCard.kt`): the first time a release is found, a round card over the screen
  with the riding cat, "A new Nouride Home is ready", the version and that Nouride and the agents stay as they are,
  then Update now and Later. After that a small card at the top until it is installed: Update, the download's
  percent, "Almost done. When your phone asks, tap Update." Android 8 and later first ask the person to allow
  installing from the app: a card with the waving cat says so and opens that setting, and the update goes on when
  they come back. Nothing of it shows while the first start sets Nouride up. Words only: no version code, sha, URL
  or file name; a failed download says to check the Wi-Fi and try again.

## How it looks

Fun and calm: the Nouride cat, soft colours, round shapes, big touch targets.

- **The cat changes with the state** (`ui/StatusLook.kt`, the poses in `res/drawable-nodpi/mascot_*.png`, cut from
  the brand's mascot sheet): hello when off, riding its scooter while setting up or starting (it floats gently),
  thumbs up when running, leaning on the scooter when something failed, waving while turning off.
- **Soft colours** from `res/values/colors.xml`: a pale blue background, white cards, the brand blue `#0084FC` for the
  one main button, and a soft fill behind the cat for the mood (blue busy, green ready, amber needs you). No pure
  black, no saturated red.
- **Round**: cards 28dp, buttons pill-shaped, the main button 56dp tall and full width.
- **The icon** is the cat's head on the soft blue (`mipmap/ic_launcher`); the notification's small icon is a white
  paw (`drawable/ic_notification`), as Android draws small icons as a silhouette. The developer's build, "Nouride
  Home Dev", is the same cat on yellow (`app/src/debug/res`), on its icon and at the top of its screen, so the two
  apps on one phone are never mistaken for each other.
- No third-party UI library: the pieces are in `ui/Ui.kt`.

## Words

- Short sentences. What happened, then what to do. Never an error without a next step.
- The app is "Nouride Home"; what it runs is "Nouride", never the server, the daemon or the service. Say "the
  phone", not the device.
- Times as people say them: "a few minutes", "just now". Not timestamps.
- Every string is a resource in `res/values/strings.xml`. The app is English only, whatever language the phone is
  set to (the owner, 2026-11-07): no `values-<language>` folder. Plain everyday English: "Nouride is running.", not
  "The server is in the running state."
- No exclamation marks, no emoji, no jargon.

## The notification

The foreground service must show one. It says the state in the same words as the screen ("Nouride is running") and
nothing technical. Tapping it opens the status screen. It has no log line and no address. When a new version of the
app is waiting, its second line says "A new version is ready. Tap to update."

## Errors

A failure the person can fix says how in one sentence (no Wi-Fi, no space left, battery setting). A failure they
cannot fix says so plainly and offers Try again and the "Details for support" link. Never
a stack trace, never a raw message from Linux or Android.

## Checking a change

- Read every new screen and message as someone who has never used a computer for work would. If a word needs
  explaining, replace it.
- Test the state-to-words mapping as a unit test (the sentence and the button for each state), so a new state cannot
  ship without words.
- Look at it on a real phone, in the light and the dark theme, and say in the change that you did.
