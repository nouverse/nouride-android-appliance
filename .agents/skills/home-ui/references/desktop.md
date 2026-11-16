# Nouride Home for desktop: what a person sees

The rules both apps share are in `../SKILL.md`; this page is the computer's.

## A computer's Nouride for people who are not technical

The person using this app wants their computer to keep Nouride running, or to open the one they have elsewhere. They do
not know what a log, a port, a process or launchd is, and they should not have to learn. If a screen needs one of those
words to make sense, the screen is wrong, not the person. The app mirrors Nouride Home for Android: the same
questions, states, sentences and cat. Where this page does not say otherwise, the Android app's page
(`android.md`, beside this one) holds, with "computer" for "phone".

## What is never shown by default

- Log lines, of the installer, of Nouride or of the app. Not a tail, not "the last error".
- File paths, bundle ids, process states, enum names, exit codes, error messages from Rust or macOS, JSON.
- The words health, daemon, launchd, LaunchAgent, plist, service, PID, localhost, `127.0.0.1`.
- A port number on its own, or an address a person has to type, where a button can open it or Copy can take it.
- Monospace text, except an address (DM Mono, in a soft pill) and Technical Logs.

All of it lives on **Technical Logs** (`apps/desktop/src/modules/logs`), set apart at the bottom of the sidebar: the technical state, the
address with its port, the app's events, the installer's log and Nouride's, the log taking the rest of the window and
opening at its end, and Copy.

## The window

A desktop window, not the phone's screen made wider: 980 × 640 when it opens, resizable down to 560 × 520
(`apps/desktop/src-tauri/tauri.conf.json`, and `tauri.dev.conf.json` for the Dev build). On macOS the title bar is an overlay with
the title hidden: the sidebar runs up under the window buttons and leaves them room, and a 32 px strip along the top
moves the window (`data-tauri-drag-region`). Linux keeps its own title bar. Closing the window keeps the app in the menu
bar; Nouride keeps running either way.

**Windows live while they are open.** Closing the app's window or the dashboard's destroys it, and its WebKit
processes with it (on a Mac, about 25 MB for the app's window and 70 to 200 MB for the dashboard's, which grows with
use; `experiments/2026-11-16-modals-memory`). The app stays in the menu bar. Whatever shows a window makes it again
when it is gone: the menu bar, a Dock click, Open Nouride, the dashboard's back. So a window is looked up by its label
each time it is needed, never kept (`windows::main::show`, `windows::dashboard::open`); the app's window is made from
its config at the screen asked for (`looks::window::main_page`, read by `startSection`), the dashboard's signed in
again as it opens. Closing the last window does not quit the app (`looks::window::exits`); Quit does.

**A modal only for a confirmation.** Nothing stands over the window but a question that confirms something the person
pressed: Settings' Turn off is the one (`commons/components/Dialog.tsx`). A form, a choice, a message or an address
opens in the page, where the person already is (`apps/desktop/tests/modals.test.ts` holds this). No native dialog either.

**The sidebar** (`commons/components/Sidebar.tsx`, the logic in `commons/lib/sections.ts`), on a slightly tinted
surface down the left: the cat on its disc and "Nouride Home" at the top (with a yellow Dev badge on the Dev build), then
**Home**, **Connect**, **Versions and updates**, **Settings**, and **Technical Logs** set apart at the bottom. They are
siblings: none opens "inside" another, so there is no Back between them. The open one sits in the soft lavender pill; a
dot on Versions and updates says an update waits there. Cmd+1 to Cmd+5 open them in that order (Ctrl on Linux).

**Responsive**, from the window's width and the content's (`@container` on each page, Tailwind's container variants):

| Window | Sidebar | Content |
| --- | --- | --- |
| about 960 px and up | labels, 228 px | Home in two columns, Connect's code beside the switch and the address |
| 720 to about 960 px | labels | one column: Home's facts under the state card; Connect's switch over the code and the address |
| under 720 px | an icon rail, 64 px: the cat on top, icons with their name as a tooltip and for a screen reader, the dot still on Versions | one column, one card under another |

There is no bottom tab bar at any width: a narrow window is still a desktop window. Nothing may be cut, overlap or
scroll sideways at any width from the minimum up. Each page keeps a readable width on a wide window (960 px, Settings
720 px); Technical Logs takes the whole content area. A page opens with its name in Fredoka, nothing else in its header
but Technical Logs' Copy.

No sidebar before the first question is answered, nor while the first setup runs: its progress is the one thing to
watch, alone in the middle of the window (`looks::sidebar`).

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
card says first, on pink, that it turns off and its agents stop answering until the person switches back in Settings.

The screen is one centred column: the waving cat, the question and its sentence, then the two answers side by side as
two big choice cards (an icon, the answer, one line under it: "Nouride Home installs it and keeps it running.", "Type the
address of a Nouride you already have."), one under the other in a narrow window. The address card takes the answers'
place in the same column; in Settings it opens as a section of the place's card, under the choice (below).

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

Under the state card, its one main button. Beside it when there is room (under it otherwise), what a person may want
to read, never a port, a path or a process state:

- **A new Nouride is ready**, when one waits (a small card, "Nouride 0.8.40 is ready. Your agents, chats and settings
  stay as they are.", Update, which opens Versions and updates and starts the update there). Never while Nouride runs
  on another computer.
- One card of facts: **Where Nouride runs** ("This computer", or the other computer's address in its pill, the only
  place Home shows it); **Versions** ("Nouride Home 0.1.0", and "Nouride 0.8.39" while it runs here; a new Nouride
  Home's line when one waits, opening Versions and updates); **Open from other devices** (on, off, or as the other
  computer or the Nouride already here was set up) with Go to Connect.

## Connect

This computer, once Nouride is installed by the app: a switch, **Open from other devices**, "Let a tablet, a phone or
another computer on the same network open Nouride. Turn it off on a network you don't trust." Off as installed. A
change says "Nouride starts again for a moment". Off: "Only this computer can open Nouride". On and answering: "Open
Nouride on another device", a QR code (dark on white in both themes), the address large in a soft pill, Copy, and how to
sign in there. Not answering: the cat, "Nouride isn't running yet" and Go to Home. No network: "This computer isn't on
a network".

Another computer: that computer's address and its code, Copy, "Sign in with that Nouride's username and password.", no
switch.

With room, the code stands on the left and the switch over the address beside it; in a medium window the switch spans
the top with the code and the address side by side under it; in a narrow one: the switch, the code, the address.

## Versions and updates

Android's two cards (`looks::versions`), side by side when there is room: what it is, its version, where its update is,
one button. The sidebar's dot sits on it while an update waits.

## Settings

One page of grouped sections, as a computer's settings window has them (a group's name above its card), not a list of
rows that open other screens:

- **Where Nouride runs**: one sentence, and This computer or Another computer as a choice; on another computer its
  address and Change address. Another computer (or Change address) opens the address as a section of the same card,
  right under the choice, never over the window: "Nouride on another computer", the pink warning while Nouride runs
  here, the field, Connect and Cancel. The choice shows Another computer while it is open; nothing changes until
  Connect works. Cancel, or pressing This computer, closes it and leaves the place as it was
  (`modules/settings/placeChoice.ts`).
- **Appearance**: "Nouride Home and Nouride's window look this way.", Same as computer, Light, Dark; applies at once
  to the app and to the dashboard's window.
- **Nouride on this computer** (installed by the app, while it may be turned off): one sentence and **Turn off
  Nouride** in amber words, not a button. It asks once, the app's one modal: "Turn Nouride off? Your assistant will not
  answer until you start it again."

## The menu bar

The icon is the cat's head as a template image (macOS draws it in the menu bar's colour). The menu, top to bottom
(`apps/desktop/src-tauri/src/tray.rs`, words from `looks::tray`):

1. The state, greyed: Home's title ("Nouride is running").
2. The update notice when one waits: "A new Nouride is ready", "A new version of Nouride Home is ready", or "Updates are
   ready", which opens Versions and updates.
3. Open Nouride Home.
4. Open Nouride, while it may work.
5. Start Nouride or Turn off Nouride (this computer, installed by the app). Turn off opens Settings, where it asks
   first.
6. Quit Nouride Home ("Nouride keeps running" is what Quit means: it never stops Nouride).

## The dashboard's window

Opened by Open Nouride: the dashboard, signed in, at 1200 × 800, titled "Nouride". Links outside Nouride open in the
default browser, the dashboard's sign-in buttons above all (Claude, ChatGPT, GitHub Copilot, Google Gemini): the
person's own session is there, and the dashboard's cookie never goes to a stranger's page. A link that asks for a new
tab (`target="_blank"`) goes the same way: WebKit drops such a click, so the window runs `link_policy::NEW_TABS_SCRIPT`
to turn it into `window.open`, and `link_policy::new_window` decides; a Nouride path that redirects out (the gateway's
`/oauth/start`, to Google) leaves for the browser as it redirects, and the page on screen stays. When Nouride does not answer, the leaning cat and "Nouride isn't answering", "It may still be starting, or it
is turned off. Check Home, then try again." with Try again stand in for the WebView's own error page. The dashboard's
own back button closes the window (destroying it, as its close button does) and brings Nouride Home forward, made
again if it was closed.

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

- The app is "Nouride Home"; what it runs is "Nouride", never the server, the daemon or the service. Say "the
  computer".
- Every string is in `packages/ui-kit/src/words.json`. English only. No exclamation marks, no emoji, no jargon.

## Checking a change

- Read every new screen and message as someone who has never used a computer for work would.
- The state-to-words mappings are tested (`crates/looks`, and the UI's test that every key has its words).
- Look at it at the window's sizes (1200 × 760, 980 × 640, 820 × 560, 560 × 520), light and dark: on a real Mac, or,
  as macOS refuses screen captures over ssh, in the fixture build (`cd apps/desktop && bun run build:fixture`, then
  `experiments/2026-11-16-desktop-layout/render.mjs`). Say in the change which you did.
