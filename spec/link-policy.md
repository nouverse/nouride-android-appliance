# The dashboard's window: which links stay, and Back

Both apps show Nouride's dashboard in a window of their own (Android's WebView, the desktop's WebKit window). What
that window opens itself, and what Back does there, is the same rule on both. Cases: `fixtures/link-policy.json`.

## The origin

The window shows one Nouride, its `origin`: the device's own (`http://127.0.0.1:<port>`) or another machine's, the
base of [remote-address.md](remote-address.md) (https). A URL is **in app** when it has that origin's scheme, host and
port. `localhost` is the loopback under another name and counts as `127.0.0.1`; nothing else does. The same name on
another port, or over the other scheme, is another server. `about:blank` is in app.

## Links

`link(url, main_frame, origin)`:

- `nouridehome://home`, the back button the dashboard draws in its header when the user agent carries
  `NourideHome/1`, is `back`. What it does is each platform's: on Android the phone's Back, on the desktop the
  dashboard's window closes and Nouride Home comes forward.
- A frame inside a page is the page's business: `here`.
- A main-frame URL in app is `here`; anything else is `outside` and goes to the browser or the app that owns it, so a
  sign-in page there has the person's own session and a stranger's page never runs with the dashboard's cookie.

## Back

`back_inside(history, current, origin)` is true only when the page before `current` is an ordinary page of that
Nouride: not `about:blank`, in app, and not a page a person passes once on the way in (`/dashboard/login`,
`/welcome`, `/setup`, `/oauth`, and anything under them; `/setupguide` is an ordinary page).

`back(history, current, dashboard_shown, origin)`:

- `leave` when the dashboard is not on screen (the cat that says Nouride is not answering, or it is still opening):
  nothing to lose.
- `page` when `back_inside`.
- `ask` otherwise: Back would close the dashboard. Android asks first; on the desktop a window has its own close
  button.

## Nouride not answering

`unreachable(main_frame, url, origin)` is true for a failed main-frame load of an in-app URL other than `about:blank`:
shown as the cat and Try again. A picture or a script that failed is the page's.

## Where the apps differ

No case differs today. The desktop's `new_window` (a page that asks for a new window) has no Android counterpart in
`LinkPolicy` (Android's `onCreateWindow` does it), so it stays in the crate's own tests.
