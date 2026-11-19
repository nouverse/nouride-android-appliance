# The dashboard always signed in on its own device (0.2.1, Nouride 0.8.40)

## The question

In local mode, does the dashboard inside Nouride Home stay signed in through Nouride 0.8.40's device token
(`spec/device-sign-in.md`), whatever ended the session? The cases: the first open, the session's cookie gone, the
session ended by another client, the token revoked (exactly one failed trade in the audit, then a new token), Sign
out, the app killed and reopened, Nouride restarted. And does a device-token session still get the first-run
`/welcome` page?

## How it was run

Two Nouride Home Dev installs, each first updated from Nouride 0.8.39 to 0.8.40 through the same control-token routes
the app's Versions screen calls (`/update/download`, `/update/progress`, `/update/install`). Neither had a device token
before. The release apps and the owner's own Nouride on the Mac were not touched; the Mac's guard
(`scripts/mac-guard.sh`) read the same plist hash and PID before and after.

- **Android**: a Redmi Note 12 (HyperOS 2.0, Android 15), Home Dev built from this branch (`ca5df34`), installed over
  wireless ADB. The dashboard's WebView was read over its DevTools socket (debug builds have one):
  `evidence/scripts/wv.mjs` with `probe.js` reads `location.href` and asks `/api/control/auth/me`, `wv-cdp.mjs` sends
  DevTools commands (cookies), `phone-ctl.sh` asks the phone's Nouride with the install's control token (read with
  `run-as`, never printed): the audit log and the device list.
- **Desktop**: a MacBook Air (Apple silicon), Home Dev built on it from this branch, driven with the e2e hooks over
  ssh (`open-dashboard`, `dashboard-js`, `dump`): `evidence/scripts/mac-probe.sh` has the page write where it is and
  what `/auth/me` answered into its own address, which `dump` reads back. `nr-ctl.sh` reads the audit.

The audit trails of both runs are in `evidence/android-audit.txt` and `evidence/mac-audit.txt` (the Mac's account and
name redacted).

## What was seen: Android (2026-11-19, 18:07 to 18:13 +0700)

This Dev install's account had never left the first-run page ("Personalize Your Account", `mustChangePassword`).
Finishing it would have set a password on the owner's phone that nobody holds (Nouride has no command to reset one), so
it was left there; that page has no Sign out button and no Users page. Where a case needed those, the page did what the
dashboard does, through DevTools, as said below.

| Case | Done | Seen |
| --- | --- | --- |
| First open | Home → Open Nouride | `device_token_created` with label `Nouride Home (Redmi Note 12)` (the phone's name, not `Build.MODEL` `23021RAA2Y`), one `login` with that device; the page on `/dashboard/welcome` signed in as `admin`, `mustChangePassword: true` |
| The cookie gone | `Network.deleteCookies` for `nouride_session`, then a reload | Cookie list empty, then one more device `login`, the page back on `/dashboard/welcome` (through `/login?next=%2Fwelcome`) |
| Session ended elsewhere | `POST /auth/logout` sent from this box with the WebView's cookie (old cookie then 401); Home, then back to the dashboard | One more device `login`; `/auth/me` 200 again |
| Revoked | `DELETE /api/control/auth/devices/<id>` (what the Users page's Revoke calls); Home, back after 40 s | Exactly one `device_login_failed`, then `device_token_created` (a new id, same label) and its `login`; `/auth/me` 200 |
| Sign out | In the page: `POST /api/control/auth/logout`, then the router moved to `/dashboard/login` (what the Sign out button does) | One more device `login`, the page back signed in |
| Killed and reopened | `am force-stop` (which also stops Nouride), launcher icon, Open Nouride | Nouride restarted by the app (uptime 11 s); one `login` with the same token, no new one |

The app's preferences then held only `device_token` and `run`: the first admin's password is gone since Nouride's
next ready, and the install's `.env` has no `INITIAL_ADMIN_*` line.

Timing to know: after a session ends behind its back, the dashboard notices it only when it asks `/auth/me` again (on
coming back to the window, once that answer is older than 30 seconds, or on a reload). The first revoke was followed by
a return 15 seconds after the previous check, and nothing happened until the next return 40 seconds later. That is the
dashboard's own timing, the same in a browser; the app signs in again the moment the dashboard reaches its sign-in page.

## What was seen: desktop (2026-11-19, 18:13 to 18:19 +0700)

This install's account is the owner's own, past the first-run page, so the real buttons were used.

| Case | Done | Seen |
| --- | --- | --- |
| First open | `open-dashboard` | `device_token_created` with label `Nouride Home (<the Mac's name>)`, its typographic apostrophe written as `'`; one device `login`; Overview signed in; `device.token` is `-rw-------` |
| Session ended, cookie cleared | In the page: `POST /auth/logout` (its answer clears the HttpOnly cookie), then a reload | One more device `login`, back on `/dashboard/` |
| Sign out | The sidebar's Sign out, then Sign out in its dialog | One more device `login`, back on `/dashboard/` |
| Quit and reopened | `--e2e quit`, then the app opened again, `open-dashboard` | One `login` with the same token |
| Revoked | Users → "Devices that stay signed in" → Revoke, confirmed; the window's `visibilitychange` (as on coming back to it) | `device_token_revoked`, then exactly one `device_login_failed`, `device_token_created` (a new id) and its `login`; the window still on `/dashboard/users`, `/auth/me` 200 |
| Nouride restarted | `launchctl kickstart -k` on Home Dev's own label, then a reload | Still signed in: the session outlives a restart, no new trade needed |

**A bug found here, and fixed.** The first desktop build sent the window to the app's own opening page while it
signed in again, then to the dashboard. After the Sign out, the first load won: the window stayed on the opening
page, while Tauri's `dump` still reported the dashboard's address. The window's own `location` showed it. The fix
(`353a8ae`) never leaves the page. The window's script hides the page as it reaches the sign-in page, and the one load
after the round is the page itself. The two cases above that use the page (session ended, Sign out) were run again on
the fixed build, with the window's location read from the page; the table shows those runs.

## What it means

On both apps, in local mode, every way a session ends led back to a signed-in dashboard with no sign-in page shown.
A revoked token costs exactly one failed trade before a new one is made, so the address's sign-in allowance is not
spent in a loop. A device-token session for an account still on its first-run page lands on `/welcome`.

Not covered here:

- A fresh install from nothing (the `INITIAL_ADMIN_*` path, then the first trade answering 409 until the admin exists)
  is covered by the unit tests' rounds, not run live.
- A Nouride before 0.8.40 (the round gives up on 404 and the dashboard's sign-in page shows) is covered by unit tests,
  not run live.
- On Android, the dashboard's Sign out button and the Users page's Revoke button were not pressed, for the reason above.
  The same calls were made in the page and through the route.
- Remote mode was not run again. Its code path does not sign in (`local` is false), and `sign_in_page` is only ever
  given this device's origin.
