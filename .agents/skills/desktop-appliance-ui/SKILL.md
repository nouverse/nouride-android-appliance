---
name: desktop-appliance-ui
description: The rules for everything a person sees in Nouride Home for desktop, whose users are not technical - the first question, Home, Connect, More, Versions and updates, Technical Logs, the dashboard's window, the menu bar (tray) menu, buttons, errors and the words on them, in English; what is hidden by default (logs, paths, ports, process states) and where it goes instead. Use when adding or changing a screen, a tray item, a button, a message or an error, or reviewing one.
---

# A computer's Nouride for people who are not technical

The person using this app wants their computer to keep Nouride running, or to open the one they have elsewhere. They do
not know what a log, a port, a process or launchd is, and they should not have to learn. If a screen needs one of those
words to make sense, the screen is wrong, not the person. The app mirrors Nouride Home for Android: the same
questions, states, sentences and cat. Where this skill does not say otherwise, the Android app's `android-appliance-ui`
skill holds, with "computer" for "phone".

## What is never shown by default

- Log lines, of the installer, of Nouride or of the app. Not a tail, not "the last error".
- File paths, bundle ids, process states, enum names, exit codes, error messages from Rust or macOS, JSON.
- The words health, daemon, launchd, LaunchAgent, plist, service, PID, localhost, `127.0.0.1`.
- A port number on its own, or an address a person has to type, where a button can open it or Copy can take it.
- Monospace text, except an address (DM Mono, in a soft pill) and Technical Logs.

All of it lives on **Technical Logs** (`apps/ui/src/modules/logs`), the last line of More: the technical state, the
address with its port, the app's events, the installer's log and Nouride's, the log taking the rest of the window and
opening at its end, and Copy.

## The window

One window, about a phone's width (420 × 760), with three tabs along the bottom (`modules/*`, the bar in
`commons/components/TabBar`): **Home**, **Connect**, **More**. Each opens with the same header, the cat on its disc and
the tab's name. The open tab sits in a soft lavender pill; a dot on More says something there needs the person (an
update waiting). The bar is hidden while the first setup runs: its progress is the one thing to watch (`looks::tabs`).
Closing the window keeps the app in the menu bar; Nouride keeps running either way.

## Where Nouride runs (the first screen)

On a computer where the app has not installed Nouride, before anything runs, the first screen asks the one thing the
app cannot answer for the person: "Where should Nouride run?", "Run it on this computer, or open a Nouride you already
have on another computer, at home or in the cloud." Two answers: **Run it on this computer**, which starts the first
setup at once (the press is the person asking for it), and **Use Nouride on another computer**, which opens the address
card.

The address card is a round card in the app's look: "Nouride on another computer", "Type the address you open it at.",
one field in DM Mono with `nouride.example.com` as its placeholder, then Connect and Cancel. What is wrong is said under
the field, in its own sentence, Android's words: nothing typed, an `http://` address, something that is not an address,
and an address nothing answers at, which Connect checks once ("Checking…"). While Nouride runs on this computer, the
card says first, on pink, that it turns off and its agents stop answering until the person switches back in More.

## Home

Another computer: one card, one button, from that computer's `/health` (`place::remote_reply`): "Nouride is running"
with the thumbs up, "Nouride isn't answering" with the unplugged cat and Try again, or "Nouride is on another computer",
"Open it to sign in." with the waving cat when a page stands in front of it. Under the sentence, the address without
`https://`, in DM Mono in a soft pill.

This computer: one sentence of state and one main button, from `looks::status`:

| State | What the person reads | Main button |
| --- | --- | --- |
| Off | "Nouride is off" / "Press Start, and this computer becomes the home of your AI assistant." | Start |
| First setup | "Setting up Nouride" / "This takes a few minutes the first time. Keep the computer on and online." with the bar | (none) |
| Starting | "Starting Nouride" / "Almost there." | (none) |
| Running, not answering yet | "Nouride is waking up" / "It will be ready in a moment." | (none) |
| Running and answering | "Nouride is running" / "Your assistant is ready. You can close this window: Nouride keeps running." | Open Nouride |
| Restarting on its own | "Nouride is starting again" / "It stopped for a moment. There is nothing you need to do." | (none) |
| Turning off | "Turning Nouride off" / "Just a moment." | (none) |
| Failed | "Nouride could not start" / "Check that the computer is online, then try again." | Try again |
| Already on this computer (not the app's) | "Nouride is running" / "It was already on this computer. Nouride Home opens it." | Open Nouride |

The first setup's bar (`daemon::progress`): what is happening now ("Downloading Nouride", "Installing Nouride",
"Turning Nouride on for the first time"), "Step 2 of 3" and the percent. It never goes back and reaches 100 only when
Nouride answers.

Under the main state, only what a person acts on: **Open Nouride**, and **A new Nouride is ready** (a small card, "Nouride
0.8.40 is ready. Your agents, chats and settings stay as they are.", Update, which opens Versions and updates and starts
the update there). Never while Nouride runs on another computer.

## Connect

This computer, once Nouride is installed by the app: a switch, **Open from other devices**, "Let a tablet, a phone or
another computer on the same network open Nouride. Turn it off on a network you don't trust." Off as installed. A
change says "Nouride starts again for a moment". Off: "Only this computer can open Nouride". On and answering: "Open
Nouride on another device", a QR code (dark on white in both themes), the address large in a soft pill, Copy, and how to
sign in there. Not answering: the cat, "Nouride isn't running yet" and Go to Home. No network: "This computer isn't on
a network".

Another computer: that computer's address and its code, Copy, "Sign in with that Nouride's username and password.", no
switch.

## More

- **Where Nouride runs**: This computer or Another computer, side by side as Appearance's choices are. Under it one
  sentence, and on another computer its address and Change address.
- **Appearance**: Same as computer, Light, Dark; applies at once to the app and to the dashboard's window.
- **Versions and updates ›**: the two versions in one line ("Nouride Home 0.1.0, Nouride 0.8.39"), or what is ready.
  It opens a screen of two cards, Android's (`looks::versions`): what it is, its version, where its update is, one
  button.
- **Turn off Nouride**: amber words, not a button. It asks once: "Turn Nouride off? Your assistant will not answer
  until you start it again."
- **Technical Logs ›**, last.

## The menu bar

The icon is the cat's head as a template image (macOS draws it in the menu bar's colour). The menu, top to bottom
(`apps/desktop/src/tray.rs`, words from `looks::tray`):

1. The state, greyed: Home's title ("Nouride is running").
2. The update notice when one waits: "A new Nouride is ready", "A new version of Nouride Home is ready", or "Updates are
   ready", which opens Versions and updates.
3. Open Nouride Home.
4. Open Nouride, while it may work.
5. Start Nouride or Turn off Nouride (this computer, installed by the app). Turn off asks first, in the window.
6. Quit Nouride Home ("Nouride keeps running" is what Quit means: it never stops Nouride).

## The dashboard's window

Opened by Open Nouride: the dashboard, signed in, at 1200 × 800, titled "Nouride". Links outside Nouride open in the
browser. When Nouride does not answer, the leaning cat and "Nouride isn't answering", "It may still be starting, or it
is turned off. Check Home, then try again." with Try again stand in for the WebView's own error page. The dashboard's
own back button closes the window and brings Nouride Home forward.

## How it looks

Nouride's soft look, the dashboard's tokens (`packages/ui-kit/src/theme.css`, the two daisyUI themes copied from the
dashboard's `main.css`): a cream page and white cards; peach for the one main button, ink words on it; lavender for
where the person is; sage mint for what is moving or done; pink for what needs the person. Fredoka 500 for headings,
Manrope for the rest, DM Mono for an address. Cards at 22px radius with a hairline and a soft shadow; buttons are pills,
the main one full width with a faint peach glow. The night palette when the computer is dark or Dark is chosen.

The cat changes with the state (`looks::Mascot`, the poses in `packages/ui-kit/src/mascot/`, copied from the Android
app): waving when off, riding while setting up or starting, thumbs up when running, the unplugged cable when something
failed, the wrench for a new version.

The icon is the Android app's: the cat's head on pastel lavender. The Dev build is the same cat on yellow, on its icon
and at the top of its window.

## Words

- Short sentences. What happened, then what to do. Never an error without a next step.
- The app is "Nouride Home"; what it runs is "Nouride", never the server, the daemon or the service. Say "the
  computer".
- Every string is in `packages/ui-kit/src/words.json`. English only. No exclamation marks, no emoji, no jargon.

## Checking a change

- Read every new screen and message as someone who has never used a computer for work would.
- The state-to-words mappings are tested (`crates/looks`, and the UI's test that every key has its words).
- Look at it on a real Mac, in the light and the dark appearance, and say in the change that you did.
