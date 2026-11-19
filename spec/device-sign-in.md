# The dashboard signed in on its own device

When the app runs Nouride on the device itself (local mode: the Nouride the app installed), the dashboard in its
window is always signed in. It never stays on the dashboard's sign-in page, whatever ended the session: its week ran
out, it was ended from another browser, the cookie or the window's data was cleared, the device's sign-in was revoked
on the Users page, the person pressed Sign out, Nouride restarted or was updated. A Nouride on another computer
(remote mode, `remote-address.md`) is out of this: its sign-in page is the way in, as in any browser. Cases:
`fixtures/device-sign-in.json`.

It rests on Nouride's device token (Nouride 0.8.40 and later): a credential bound to the first admin that never
expires, made only with the install's control token (`data/.control-token`, which only something on the same machine
can read), and traded for an ordinary session. The app keeps one in its own private storage (Android: its private
preferences; desktop: `device.token` in its data folder, 0600), never logged, shown or sent anywhere but this device's
Nouride.

## The calls

- `mint`: `POST /api/control/auth/devices`, header `x-nouride-token: <control token>`, body `{"name": "<label>"}`.
  201 answers `{device, value}`; `value` (`ndev_…`) is the token, shown once.
- `trade`: `POST /api/control/auth/device`, body `{"token": "<device token>"}`. 200 answers a `Set-Cookie:
  nouride_session=…`, which the app hands to the window. A failed trade spends the address's sign-in allowance, which
  password sign-ins share (30 an address in 15 minutes): the app never trades a refused token again.

## Its label

What the Users page lists it as, under "Devices that stay signed in": `Nouride Home (<name>)`, where the name is the
phone's name on Android (About phone, `Settings.Global.DEVICE_NAME`; the model, `Build.MODEL`, on a phone with none:
that is `23021RAA2Y` on a Redmi Note 12) and the computer's name on desktop (macOS: the Computer Name in Sharing;
elsewhere the host name).
Nouride takes 1 to 60 letters, digits, spaces and `. _ ' ( ) -`. The name keeps the ASCII letters and digits, spaces
and those marks; a typographic apostrophe (`’` or `‘`) becomes `'`; anything else becomes a space; runs of spaces
become one, and spaces at either end go. The name is cut so the whole label is at most 60 characters, spaces at the cut's end
going too. An empty name gives `Nouride Home`.

ASCII only is a choice: Nouride takes any letter, but the two platforms' ideas of "a letter" differ at the edges, and a
label Nouride refused would leave the app with no token at all.

## A round

Each time the window opens the dashboard, and each time the sign-in page is reached in it (below), the app signs in
in one round, which ends signed in (the session handed to the window) or given up (the window shows what it shows,
the dashboard's own sign-in page included). It starts with `trade` when a token is kept, `mint` when none is.

What each answer leads to, `next(call, code, minted)`, where `minted` says this round has already made a token:

| Call | Answer | Then |
| --- | --- | --- |
| trade | 200 with a `nouride_session` cookie | signed in |
| trade | 200 without one | give up |
| trade | 401 (the token is unknown or revoked) | `mint`, once a round; if this round minted already, give up |
| trade | 429 | wait `retry-after` seconds (60 when it is missing or unreadable), then `trade` |
| trade | 404 (Nouride before 0.8.40) | give up |
| trade | anything else, no answer included | give up |
| mint | 201 with a `value` | keep the token, then `trade` |
| mint | 201 without one | give up |
| mint | 409 (no admin yet: a fresh install still starting) | wait 2 seconds, then `mint` |
| mint | 429 | as for `trade`, then `mint` |
| mint | 404 (Nouride before 0.8.40) | give up |
| mint | anything else (403, 501, no control token, no answer) | give up |

A round waits at most 120 seconds in all: a wait that would pass that gives up instead. A Nouride before 0.8.40 has no
device token: the app's Nouride update notice is the way to one, and until then the dashboard's own sign-in page shows
as it did before (the person's own password).

## The sign-in page

`sign_in_page(url, origin)`, `origin` always this device's Nouride (`http://127.0.0.1:<port>`), so a sign-in page on
another computer is never one: the dashboard's sign-in page of the local Nouride, `<origin>/dashboard/login`, with or
without a slash after it and with any query or fragment. The dashboard reaches it inside the page (its router, after
an API answered 401 or after Sign out), not as a new page load, so each app watches the page's address where the page
itself moves: Android's `doUpdateVisitedHistory`; on desktop, a script in the window turns an arrival on that page
into a page load, which `on_navigation` sees and cancels; the script hides the page first, so the sign-in form never
shows while the app signs in again.

`after_sign_in(url, origin)`: where the window goes once signed in again. The page names it in `next`
(`/dashboard/login?next=%2Fagents`, a path inside the dashboard): `<origin>/dashboard` and that path, when it starts
with one `/`, holds no backslash or control character and is not the sign-in page itself, as the dashboard's own
`safeNext` checks. Otherwise `<origin>/dashboard/`.

`again(last, now)`: a round from the sign-in page starts only when `last` is 10 seconds ago or more (milliseconds since
the epoch; none yet counts as long ago). `last` is when the last round from the sign-in page started, or the last round
on opening the window that gave up after an answer from Nouride: `gave_up_counts(code)`, the last answer's code, 0
when nothing answered. One that nothing answered (Nouride still starting) does not count: the round after the
window's Try again can sign in. A session that fails the moment it is made (a clock far off, a Nouride that refuses
it) would otherwise reload the dashboard for ever, and a round that gave up on opening would be tried again at once:
in both, the sign-in page stays. A round on opening that signed in leaves `last` as it was, so a Sign out a moment
after opening is signed in again too.

## The first account

The app still writes `INITIAL_ADMIN_USERNAME`, `INITIAL_ADMIN_PASSWORD` and `INITIAL_ADMIN_MUST_CHANGE=1` into the
install's `.env` before the first start, so the first admin exists on a fresh install and the dashboard opens on
`/welcome` (choosing the person's own username and password), with the device's session. The app no longer signs in
with that password: it forgets it once Nouride is ready, when the lines leave `.env`.

## Where the apps differ

Nowhere today.
