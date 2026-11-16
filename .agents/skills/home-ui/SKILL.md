---
name: home-ui
description: The rules for everything a person sees in Nouride Home, on Android and on desktop, whose users are not technical - the first question (where Nouride runs), Home, Connect, More or Settings, Versions and updates, Technical Logs, the dashboard's window, the phone's notification and the computer's menu bar, buttons, errors and the words on them, in English; what is hidden by default (logs, paths, ports, process states) and where it goes instead; the cat, the colours and the type. Use when adding or changing a screen, a notification, a tray item, a button, a message or an error in either app, or reviewing one.
---

# Nouride Home for people who are not technical

Both apps are for the same person: someone who wants their phone or their computer to keep Nouride running, or to
open the Nouride they have elsewhere, and who does not know what a log, a port or a process is and should not have to
learn. If a screen needs one of those words to make sense, the screen is wrong, not the person.

The two apps ask the same questions, in the same states, with the same sentences and the same cat. Each one's screens
are in `references/`: read the page of the app you change, and the other one's when you change something both show.

- `references/android.md`: the phone. Three tabs (Home, Connect, More), the address card, the notification, battery
  guidance, Xiaomi's Autostart.
- `references/desktop.md`: the computer. A window with a sidebar (Home, Connect, Versions and updates, Settings,
  Technical Logs), its sizes, the menu bar, the dashboard's window.

## What both apps never show by default

Log lines, file paths, package or bundle ids, process states, exit codes, raw error messages, JSON, a port number on its
own, or the words health, daemon or service. Everything technical lives on one screen of its own, **Technical Logs**,
for whoever helps, set apart at the end (More's last line on the phone, the bottom of the sidebar on the computer).
Each page lists the words its own platform adds (proot and Debian on the phone, launchd and LaunchAgent on the
computer).

## Shared by both

- **The first question** is where Nouride should run: on this device, or another computer at an HTTPS address. What an
  address may be is `spec/remote-address.md`, and what is wrong with one is said under the field in its own sentence,
  in the same words on both.
- **The dashboard** opens inside the app, signed in; links outside Nouride go to the browser (`spec/link-policy.md`).
- **The look** is Nouride's soft look, the dashboard's: cream page, white cards, peach for the one main button,
  lavender for where the person is, sage mint for what moves or is done, pink for what needs the person; Fredoka,
  Manrope and DM Mono; the cat whose pose follows the state. The Dev build is the same cat on yellow.
- **The app is "Nouride Home"; what it runs is "Nouride"**, never the server, the daemon or the service.

## Words

- Short sentences. What happened, then what to do. Never an error without a next step.
- English only, whatever language the device is set to. No exclamation marks, no emoji, no jargon.
- Every string lives in the app's one place for words (`res/values/strings.xml` on Android,
  `packages/ui-kit/src/words.json` on desktop), never a literal in code.

## Checking a change

Read every new screen and message as someone who has never used a computer for work would. The state-to-words
mappings are tested in both apps, so a new state cannot ship without words. Look at it in the light and the dark
theme, on a real phone, or a real Mac or the desktop's fixture build, and say in the change which you did.
