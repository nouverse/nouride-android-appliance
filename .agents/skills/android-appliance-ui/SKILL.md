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

All of it lives on one screen of its own, **Technical Logs** (`ui/SupportActivity.kt`), reached from the end
of the More tab, "Technical Logs ›". It is for whoever helps: the technical state, the
address with its port, the app's events and Nouride's log, the log taking the rest of the screen and opening at its
end. Copy and Share send it all, Share straight into a chat. Never a box of log inside the scrolling status screen:
two scrolls in one another catch the wrong finger.

## The tabs

Three tabs along a bar at the bottom (`ui/BottomBar.kt`), each opened with the same header, the cat on its disc and
the tab's name, so no tab jumps in size: **Home** (the state and its one button), **Connect** (Nouride on another
device) and **More** (what is rarely needed). The open tab's icon sits in a soft pill; a dot on More says something
there needs the person. The bar is hidden while the first start sets Nouride up: its progress is the one thing to
watch. Back from Connect or More goes Home; from Home it leaves the app (`ui/Tabs.kt`).

## Home

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
- **Keep running with the screen off**: only while the phone may stop the app, one amber line, "Nouride may stop when
  the screen is off. Fix it in More ›", which opens More, where it is fixed.
- **A new version of the app** (`ui/UpdateCard.kt`): the first time a release is found, a round card over the screen
  with the riding cat, "A new Nouride Home is ready", the version and that Nouride and the agents stay as they are,
  then Update now and Later. After that a small card at the top until it is installed: Update, the download's
  percent, "Almost done. When your phone asks, tap Update." Android 8 and later first ask the person to allow
  installing from the app: a card with the waving cat says so and opens that setting, and the update goes on when
  they come back. Nothing of it shows while the first start sets Nouride up. Words only: no version code, sha, URL
  or file name; a failed download says to check the Wi-Fi and try again.

## Connect

On top, once Nouride is installed, a switch: **Open from other devices**, "Let a tablet, a computer or another phone on
the same Wi-Fi open Nouride. Turn it off on a Wi-Fi you don't trust." On as installed. Off, Nouride answers only this
phone (the app's own dashboard keeps working) and the card under it says "Only this phone can open Nouride"; a change
says "Nouride starts again for a moment", which it does, for about a second.

Only when Nouride answers, others may open it, and the phone is on Wi-Fi: "Open Nouride on another device", one sentence, a QR code a
tablet's or a phone's camera opens (`ui/QrCode.kt`, dark on white in both themes, as a camera reads contrast), the
address large in a soft pill, Copy and Share address, and how to sign in there: "Use the username and password you
chose when you set up Nouride." Before that, the cat and "Nouride isn't running yet" with Go to Home; off Wi-Fi, "This
phone isn't on Wi-Fi". Never an address that leads nowhere, and never `http://10.x.x.x:18254/...` in a sentence.

## More

- **Keep Nouride running**: a green check and one sentence once the phone lets Nouride run with the screen off; before
  that, why ("Your phone may stop Nouride to save battery.") and Allow. On Xiaomi's phones (Xiaomi, Redmi, POCO), Start
  again after a restart: some of them keep Nouride off after a restart without Autostart, so the card says so as a
  maybe, and Open settings goes straight to Security's Autostart list, where the person turns on the app by the name
  the list shows (`PowerSettings.asksForAutostart`, `PowerSettings.autostartSettings`). Allow already sets HyperOS's
  battery saver to No restrictions (both builds showed it on the owner's phone), so the card does not ask for it.
- **Appearance**: Same as phone, Light or Dark, side by side; a choice applies at once, to every screen and to the
  dashboard (`ui/Appearance.kt`).
- **This app**: its name and version, "You have the newest version." or the version that is ready, and Check for
  updates, which asks at once; a release found goes to Home, where it is installed.
- **Turn off Nouride**: amber words, not a button, so it is never pressed by mistake. It asks once: "Turn Nouride off?
  Your assistant will not answer until you start it again."
- **Technical Logs ›**, last.

## How it looks

Fun and calm: the Nouride cat, soft colours, round shapes, big touch targets.

- **The cat changes with the state** (`ui/StatusLook.kt`, the poses in `res/drawable-nodpi/mascot_*.webp`). It is
  Nouride's soft mascot, the tuxedo cat in the lavender hoodie, and each pose means what it means in the dashboard
  (Nouride's design rules, `.agents/skills/nouride-contributing/references/design-rules.md` in the nouride repository):
  waving when off and while turning off, riding its scooter while setting up or starting (it floats gently), thumbs up
  when running, sad beside an unplugged cable when something failed or there is no Wi-Fi. Pointing at what needs the
  person (allowing updates), the wrench for a new version. A pose is copied from nouride's
  `packages/shared/assets/mascot/` (or `docs/design/mascot/` for one the dashboard does not draw yet) and trimmed to
  the cat, as the screens size it by height: `magick <pose>.webp -trim +repage -strip -quality 92 -define
  webp:alpha-quality=100 mascot_<name>.webp`.
- **Nouride's soft colours**, the dashboard's (its `main.css` and the design rules), in `res/values/colors.xml`: a
  cream page and white cards; the peach pastel `#F08A6C` for the one main button, with ink words on it, never white;
  lavender for where the person is (the open tab, the appearance chosen on More); sage mint for what is moving or done
  (the progress bar, the card when Nouride is running); pink for what needs the person (the card when it failed, the
  battery reminder and its dot). While Nouride starts, the card is plain white and the mint bar moves. Words in a colour
  take its deepened shade (`brand_text`, `place`, `good`, `warn`), which reads at AA on the card and on its tint; a
  new colour or a new pair is checked to 4.5:1 in both themes before it ships. No pure black, no saturated red. The
  night palette (`values-night`, the dashboard's violet grey) is used when the phone is dark or Dark is chosen on More.
- **Round**: cards 28dp, buttons pill-shaped, the main button 56dp tall and full width.
- **The icon** is the cat's head on pastel lavender (`mipmap/ic_launcher`, `drawable-nodpi/icon_cat.webp`: the
  dashboard's head with its sparkles cut, and a margin so a round disc never cuts its ears); the notification's small
  icon is a white paw (`drawable/ic_notification`), as Android draws small icons as a silhouette. The developer's build, "Nouride
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
cannot fix says so plainly and offers Try again and the "Technical Logs" link. Never
a stack trace, never a raw message from Linux or Android. When Nouride does not answer the dashboard, the leaning cat
and "Nouride isn't answering" with Try again and Back to Nouride Home stand in for the WebView's own error page
(`DashboardActivity`, `LinkPolicy.unreachable`).

## Checking a change

- Read every new screen and message as someone who has never used a computer for work would. If a word needs
  explaining, replace it.
- Test the state-to-words mapping as a unit test (the sentence and the button for each state), so a new state cannot
  ship without words.
- Look at it on a real phone, in the light and the dark theme, and say in the change that you did.
