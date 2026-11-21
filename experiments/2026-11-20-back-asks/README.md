# "Back to Nouride Home" asks first, and the header's Back is a link of its own

## The question

The dashboard draws two ways out of a page inside the app: Back, the chevron in a phone's header, and "Back to Nouride
Home" (the menu's row, the folded menu's tile, the button on the pages without a header). Until this change both
arrived as one link, `nouridehome://home`: Android treated it as the phone's Back, so the menu's "Back to Nouride Home"
went back a page; the desktop closed the window at once, with no question.

With the change (`spec/link-policy.md`): `nouridehome://home` asks "Go back to Nouride Home?" on any page;
`nouridehome://back`, which Nouride 0.8.41 sends from the header's chevron to an app whose user agent says
`NourideHome/2` or more, is exactly the system's Back; both apps say `NourideHome/2`. Do both apps do that on a real
phone and a real Mac, against Nouride 0.8.41?

Run on 2026-11-21, 19:18 to 19:36 +0700. (The folder carries the day the change was asked for.)

## What was tested

| | |
| --- | --- |
| Commit | `658dcca` on `fix/back-to-home-asks` (main at `878a053` plus this change) |
| Android | Nouride Home Dev (debug, `tech.nouverse.nouride.home.dev`, 0.0.0-dev), installed over the previous Home Dev build; a Redmi Note 12 (HyperOS 2.0.202, Android 15) |
| Desktop | Nouride Home Dev 0.2.1 (debug, `tech.nouverse.nouride.home.dev`), built on the Mac over the previous Home Dev build; a MacBook Air (Apple silicon), macOS 27.0.1 |
| Nouride | 0.8.41 on both devices: each Home Dev's own install, brought from 0.8.40 through its update routes (`/update/download`, `/update/progress`, `/update/install`); and a throwaway 0.8.41 on a Linux box behind a Cloudflare quick tunnel, opened from the phone as "another computer" |

The release apps were not installed or opened: the phone's `tech.nouverse.nouride.home` is still 0.2.0, last updated
2026-11-16. The owner's own Nouride on the Mac was checked with `scripts/mac-guard.sh` before and after: its
LaunchAgent's sha256 and its PID unchanged, and it still answers as 0.8.40.

## Tests

| Check | Result |
| --- | --- |
| `./gradlew testDebugUnitTest` (apps/android) | 190 tests, 0 failed (4 new) |
| `cargo test --locked` in every crate (Linux) | 198 passed, 0 failed |
| `bun test` (apps/desktop) | 36 pass, 0 fail (1 new); `bun run build` built |
| `cargo check --locked`, with and without `--features dev` (the Tauri binary, on the Mac) | No error, no warning |
| `bun run build:dev` (Mac) | Built, "Nouride Home Dev.app" |
| `scripts/bump-version --check` | No file disagrees; VERSION is 0.2.1 as before |

Each rule was broken once and its test seen red, then put back (`evidence/sabotage/`):

| Broken | Red |
| --- | --- |
| `nouridehome://home` mapped to the system's Back again | Rust: `back_to_nouride_home_is_never_the_headers_back`. Kotlin: the same test, and "link as the spec says" |
| `leave` never asks | Rust and Kotlin: "Back to Nouride Home asks on a deeper page ... and on the first page"; Kotlin also "leave as the spec says" |
| `nouridehome://back` mapped to `leave` | Rust and Kotlin: "the header's back is the system's Back, and the mark says the app catches it"; Kotlin also "link as the spec says" |
| The mark back to `NourideHome/1`, in the constant and in the fixture | Rust and Kotlin: the same test, which names 2 |
| The words for Stay removed from `words.json` | `the_question_before_the_dashboard_closes_has_its_words_and_its_two_answers` (crates/looks) |
| A second native dialog in the Tauri sources | `modals.test.ts`: "the only native dialog is the question before the dashboard's window closes" |

In the Rust runs the unit tests stop the crate's run before its spec tests, so the spec test's red is seen in the
Kotlin runs only.

## What was seen

`PASS` means what the case asks happened, seen on the device.

### Redmi Note 12

The dashboard of the phone's own Nouride is still on its first page ("Personalize Your Account"), which has no header
and no menu: finishing it would set a password on the owner's phone that nobody holds. So the deeper pages were
reached on the throwaway Nouride, opened as "another computer" and signed in there; the rules are the same for both.
Taps were real taps (`adb shell input tap`); the dialog's words were read from the screen (`uiautomator`), the page's
address and links from the WebView (its DevTools socket, a debug build has one).

| # | Case | Result | Seen |
| --- | --- | --- | --- |
| 1 | The mark | PASS | `navigator.userAgent` in the WebView carries `NourideHome/2` |
| 2 | The page's "Nouride Home" button (`nouridehome://home`) on the first-password page, the phone's own Nouride 0.8.41 | PASS | "Go back to Nouride Home?", "Nouride keeps running and your agents keep answering. Tap Open Nouride on Home to come back.", STAY and GO BACK. Stay: still `/dashboard/welcome`, no dialog. Again, Go back: Home (`evidence/android-01-gate-home-asks.txt`) |
| 3 | The phone's Back on that page | PASS | The same question, as before this change |
| 4 | The header's chevron is the new link | PASS | On the throwaway 0.8.41 the page has `<a href="nouridehome://back" aria-label="Back">` and `<a href="nouridehome://home">` "Back to Nouride Home" |
| 5 | The chevron three pages in (`/dashboard/work`, after Agents and Chats) | PASS | `/dashboard/chat`, no question |
| 6 | The phone's Back there | PASS | `/dashboard/agents`, no question |
| 7 | The chevron at the first page | PASS | With the sign-in page before it in the history: the question. Stay: still `/dashboard/` |
| 8 | "Back to Nouride Home" in the More sheet, on `/dashboard/agents`, a deeper page | PASS | The question, in the same words. Stay: still `/dashboard/agents`, the history as it was (`evidence/android-02-remote-0841.txt`) |
| 9 | The same, then Go back | PASS | Home: "Nouride is running", "It runs on another computer. This phone opens it." |

One thing seen on the way, not from this change: after a password sign-in the dashboard's history holds its first
page twice (`/dashboard/`, `/dashboard/login`, `/dashboard/`, `/dashboard/`), so the first Back there goes "a page
back" onto the same page and the second one asks.

Afterwards Home Dev was switched back to "This phone": its own Nouride runs again, 0.8.41.

### Mac

Nobody was at the Mac, so the app was driven over ssh with the Dev build's hooks: `dashboard-js` to click a link in
the dashboard's page, `dump` for the windows, and the new `sheet`, which reads the sheet on the dashboard's window
from AppKit (its texts and its buttons) and clicks the button named. The sheet is the system's own
(`beginSheetModalForWindow`), not a page.

| # | Case | Result | Seen |
| --- | --- | --- | --- |
| 1 | The mark | PASS | The page reads `NourideHome/2` in its user agent; at 1200 points wide it has the sidebar's "Back to Nouride Home" (`nouridehome://home`, drawn) and the header's Back (`nouridehome://back`, in the page but not drawn at this width) |
| 2 | The sidebar's "Back to Nouride Home" on the first page | PASS | A sheet on the dashboard's window: "Go back to Nouride Home?", "Nouride keeps running and your agents keep answering. Press Open Nouride on Home to come back.", buttons Stay and Go back. Stay: the window still open on `/dashboard/`, no sheet (`evidence/mac-01-first-page.txt`) |
| 3 | The header's Back three pages in (`/dashboard/work`, after Agents and Chats), twice | PASS | `/dashboard/chat`, then `/dashboard/agents`, no sheet: a page back each time, from WebKit's own history of the window |
| 4 | "Back to Nouride Home" there, on a deeper page | PASS | The sheet. Stay: still `/dashboard/agents` |
| 5 | The header's Back at the first page | PASS | Back once more: `/dashboard/`. Again: the sheet. Stay: still `/dashboard/` |
| 6 | "Back to Nouride Home", then Go back | PASS | The dashboard's window closed, the app's window shown (`evidence/mac-02-deeper.txt`) |
| 7 | Over the cat (Home Dev's own Nouride turned off), with the app's window closed | PASS | "Back to Nouride Home" on the cat's page closed the dashboard's window with no sheet, and the app's window was made again (`evidence/mac-03-cat.txt`) |

The app's log has one line per question and one per answer ("asked first", then "stay" or "go back"): four questions
in this run, three Stay and one Go back, none over the cat.

Afterwards Home Dev's Nouride was started again (0.8.41) and the new build left running in the menu bar, its windows
closed as they were before the run.

## What it means

Both apps do what `spec/link-policy.md` says against Nouride 0.8.41: "Back to Nouride Home" asks first on any page,
Stay keeps the page, Go back leaves; the header's Back is the system's Back; over the cat nothing is asked.

## Not verified

- On the Mac nobody looked at the sheet: its words, its buttons and that it sits on the dashboard's window were read
  from AppKit, and its buttons were clicked through the hook, not with a mouse. No picture of it (a new ad-hoc build
  has no Screen Recording permission). Return and Esc on the sheet were not tried.
- On the Mac the header's Back was clicked by script while not drawn: the window opens 1200 points wide and the hooks
  cannot resize it. The click takes the same path as one on the drawn chevron in a narrow window.
- The dashboard on a Nouride older than 0.8.41 with this build: its header's chevron is still `nouridehome://home`, so
  it asks and leaves instead of going back a page. That follows from the code on both sides; it was not run.
- The Android case "over the cat it leaves without asking" for `nouridehome://home`: the cat's own "Back to Nouride
  Home" on Android is a button of the app, not that link, so the rule is held by the unit tests only.
- The release builds were not installed.

## Evidence

- `evidence/android-*.txt`, `evidence/mac-*.txt`: what each device showed, in order. The tunnel's name, the owner's
  home folder and his Nouride's LaunchAgent hash and PID are replaced.
- `evidence/sabotage/`: each broken rule's red tests.
- `evidence/scripts/`: what drove the run (the phone's control calls and WebView reads, the Mac's three hook runs, the
  throwaway Nouride). No password is in them: the throwaway's was made at its start, kept in a temporary file and
  removed with it.
