# The desktop layout, drawn on the dev box (2026-11-16)

Pictures of Nouride Home's new layout (sidebar, icon rail, Home in two columns, Connect side by side, Settings on
one page), at four window sizes, light and dark. macOS refuses `screencapture` over ssh, so they are drawn on the
Linux dev box in a browser, from the same React screens the app ships, with Tauri's API mocked.

## How they were made

```sh
cd apps/ui && bun run build:fixture     # vite.fixture.config.ts -> apps/ui/dist-fixture/
cd ../.. && node experiments/2026-11-16-desktop-layout/render.mjs
```

- `apps/ui/vite.fixture.config.ts` builds the app's screens with `@tauri-apps/api/core` and `@tauri-apps/api/event`
  aliased to `apps/ui/fixtures/`. The app's own build (`vite.config.ts`, what `tauri build` bundles) never reaches
  that folder; checked by grepping `apps/ui/dist/assets/*.js` for the fixtures' address and names (no match).
- `render.mjs` serves `dist-fixture/` on 127.0.0.1 and opens it in headless Chromium (`/usr/bin/chromium` through
  Playwright 1.62) at 2x pixels, with reduced motion, and with a Mac's user agent so the screens leave the room
  they leave on macOS for the window buttons. It reports a page that scrolls sideways: none did.
- Sizes: 1200 x 760, 980 x 640 (the window's default), 820 x 560, and 560 x 520 (its minimum).

## What the mock fakes

- `invoke("state")` answers one of the hand-made snapshots in `fixtures/snapshots.ts`, named in the address
  (`?fixture=home-here`): first run (no place yet), first setup at step 2 of 3, Nouride running on this computer
  with 0.8.40 waiting and open to other devices, Nouride on another computer (`nouride.example.com`), and Nouride off.
- `?section=` opens a section the way the menu bar does, through the `screen` event.
- `invoke("qr")` answers a code drawn ahead of time by the same `qrcode` crate and settings as the app's `qr`
  command, for the two fixture addresses. `invoke("logs")` answers made-up log lines. Every action does nothing.
- Not in the pictures: macOS's window buttons and its title bar overlay (drawn by macOS, not by the page), the
  tooltips of the icon rail (they show on hover), and the dialogs (the address card over Settings, Turn off's
  question).

## The pictures

| File | Screen | Size | Theme |
| --- | --- | --- | --- |
| 01-first-run | Where should Nouride run? | 980 x 640 | light |
| 02-setting-up | First setup, no sidebar | 980 x 640 | light |
| 03-home-here-update | Home, this computer, a new Nouride waiting | 980 x 640 | light |
| 04-home-remote | Home, another computer | 980 x 640 | light |
| 05-connect | Connect, this computer, open to other devices | 980 x 640 | light |
| 06-settings | Settings | 980 x 640 | light |
| 07-versions | Versions and updates | 980 x 640 | light |
| 08-logs | Technical Logs | 980 x 640 | light |
| 09-connect-remote | Connect, another computer | 980 x 640 | light |
| 10, 11, 12 | Home | 1200 x 760, 820 x 560, 560 x 520 (rail) | light |
| 13, 14, 15 | Connect | 1200 x 760, 820 x 560, 560 x 520 (rail) | light |
| 16 to 19 | Home, Connect, Settings, first run | 980 x 640 | dark |
| 20 | Home, Nouride off | 560 x 520 (rail) | dark |
| 21, 22 | First run, Settings | 560 x 520 | light |

Each was looked at. Fixed on the way: "Versions and updates" cut in the sidebar (wider sidebar, the dot moved
out of the text's way), Home's facts repeating the update card's sentence, the QR code overflowing its white
square, the Appearance choice stretched across the card, Connect at 820 px pushing the address below the fold,
and the other computer's address shown twice on Home.

## On the Mac (Dev build, 2026-11-16)

Built with `cd apps/desktop && bun run build:dev` on Gading's Mac (macOS 27, arm64): no compiler warnings. The
guard (`scripts/mac-guard.sh`) recorded his own Nouride's LaunchAgent and PID before and found them unchanged after
(sha256 `24389671...`, PID 72300). Only Nouride Home Dev was touched (its folder, 18264).

- The app's window opens at 980 x 640 (`evidence/windows-at-launch.json`, the e2e dump's new `size`), the
  dashboard's at 1200 x 800. Later dumps show both resized (1132 x 809 and 1218 x 938): someone at the Mac resized
  them while the run went on; the run did not.
- Not seen on the Mac: the layout itself. `screencapture` is still refused over ssh, so the pictures above are the
  layout's evidence; the title bar overlay and the drag strip were not checked by eye.

### Sign-in links open the browser

Gading's report: in the dashboard's setup (Provider step, Use Nougate, Google Gemini "through Antigravity"),
**Sign In with Google** did nothing and the app logged nothing.

Probe first (a build with a log line on every navigation and new-window request, host and path only, and the Dev
build's `<data>/e2e/no-browser` so nothing reached his browser), driving the dashboard's window with the Dev-only
`--e2e dashboard-js <script>`:

| What the page did | `on_navigation` | `on_new_window` |
| --- | --- | --- |
| click on `<a target="_blank" rel="noreferrer noopener">` | not called | not called |
| the same with `rel="opener"`, with no `rel`, or as a dispatched click | not called | not called |
| `window.open(url)`, with or without `noopener` | not called | called |
| click on a same-window link | called | not called |
| same-window link to `/llm-gateway/oauth/start` | called twice: the path, then its redirect to `accounts.google.com` | not called |

So WKWebView (Tauri 2.12.5, wry 0.57.2) drops a `target="_blank"` link click before either of the app's handlers:
nothing opened and nothing was logged, as he saw. Which part of WebKit drops it was not traced. A cancelled
redirect leaves the page on screen as it was.

The fix (commit "fix(desktop): sign-in links in the dashboard's window open the browser"): the dashboard's window
runs `link_policy::NEW_TABS_SCRIPT` before the page, which turns such a click into `window.open`;
`link_policy::new_window` keeps a Nouride page in the window and sends anything else to the browser; the redirect
out of `/llm-gateway/oauth/start` is caught by `link` as before. Both branches are logged.

Then the real button, in his dashboard's setup (Use Nougate, Google Gemini's Sign in, then **Sign In with Google**,
whose link is `/llm-gateway/oauth/start`, `target="_blank" rel="noreferrer noopener"`), clicked through
`dashboard-js` (`evidence/app.log`):

```
1794792637 a new window for Nouride's own /llm-gateway/oauth/start opens in its window
1794792637 a link outside Nouride would go to the browser: accounts.google.com (e2e, no browser)
1794792653 a new window for Nouride's own /llm-gateway/oauth/start opens in its window
1794792653 a link outside Nouride went to the browser: accounts.google.com
```

The first with the dry-run on; after it the page was still the same document (a value set in `window` before the
click was still there) with the paste box shown. The second with the dry-run removed: the one time his real browser
was opened in this run. The `PROBE` lines in `evidence/app.log` are the probe build's.

How each sign-in on that page leaves, against Android's `DashboardActivity` (`shouldOverrideUrlLoading`,
`onCreateWindow`):

| Sign-in | Its link | Here | Android |
| --- | --- | --- | --- |
| Google Gemini (Antigravity) | `/llm-gateway/oauth/start`, redirects to Google | window follows it, redirect goes to the browser (seen above) | unseen WebView follows it, redirect goes to the browser |
| ChatGPT (Codex) | the provider's own address | browser | browser |
| Claude | the provider's own address | browser | browser |
| GitHub Copilot, Kiro | the device page | browser | browser |

Only Gemini's was clicked on the Mac; the others are covered by `link_policy`'s test with their addresses' shapes.
What stays different from Android:

- Android also catches the sign-in's return itself (`signin/LoopbackSignIn`, `CallbackCatcher`: it listens on the
  phone's ports 3000 and 1455 for one sign-in, so nothing is pasted). Not ported: on a computer the browser lands on
  `localhost:3000/oauth-callback` (Gemini) or `localhost:1455/auth/callback` (ChatGPT), and the person pastes that
  address, as the dashboard's step 2 says. Porting it means the app listening on fixed ports of the person's
  computer (3000 is a common development port; it was free on this Mac when checked), a decision for later.
- Nougate's Claude callback page tells its opener through `window.opener.postMessage`; only Nougate's own frontend
  listens for that, not Nouride's dashboard. Opened in the browser it has no opener, says the account was added,
  and moves to Nougate's frontend after 1.5 s. The dashboard asks again for its accounts when its window is focused
  again (react-query's refetch on focus). Android's sign-ins leave for the browser the same way, without an opener.
