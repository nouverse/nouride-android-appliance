# The dashboard's window: which links stay, Back, and the way back to Nouride Home

Both apps show Nouride's dashboard in a window of their own (Android's WebView, the desktop's WebKit window). What
that window opens itself, what Back does there, and what "Back to Nouride Home" does, is the same rule on both.
Cases: `fixtures/link-policy.json`.

## The mark

The window's user agent carries `NourideHome/2`. The dashboard draws its two ways out of a page only when it sees
`NourideHome/`, and reads the number after it to know which links this app catches (`commons/lib/home-app.ts` in
Nouride, from 0.8.41): to an app that says 2 or more, the Back in a phone's header is `nouridehome://back`; to one
that says 1, it is `nouridehome://home`, the only link such an app catches. The number is raised in the same change
that catches a new link, never before and never after: an app that says 2 and does not catch `nouridehome://back` has
a Back that does nothing, and one that catches it and says 1 is never sent it.

A Nouride older than 0.8.41 does not read the number: its header's Back is `nouridehome://home` for every app, so in
an app that says 2 it asks and leaves, where it used to go back a page.

## The origin

The window shows one Nouride, its `origin`: the device's own (`http://127.0.0.1:<port>`) or another machine's, the
base of [remote-address.md](remote-address.md) (https). A URL is **in app** when it has that origin's scheme, host and
port. `localhost` is the loopback under another name and counts as `127.0.0.1`; nothing else does. The same name on
another port, or over the other scheme, is another server. `about:blank` is in app.

## Links

`link(url, main_frame, origin)`:

- `nouridehome://back`, the Back the dashboard draws in a phone's header, is `back`: what the system's Back does
  there (`back`, below), a page back each time and a question only at the first page.
- `nouridehome://home`, "Back to Nouride Home" (the menu's row, the folded menu's tile, and the button on the pages
  that have no header: the sign-in, the first password, the setup), is `leave`: it leaves the dashboard for the app's
  own screen, and asks first (`leave`, below), whatever the page. The two are never the same link: paging back from
  deep inside the dashboard took as many presses as pages, and leaving with no question lost the page a person was on.
- A frame inside a page is the page's business: `here`.
- A main-frame URL in app is `here`; anything else is `outside` and goes to the browser or the app that owns it, so a
  sign-in page there has the person's own session and a stranger's page never runs with the dashboard's cookie.

## Back

`back_inside(history, current, origin)` is true only when the page before `current` is an ordinary page of that
Nouride: not `about:blank`, in app, and not a page a person passes once on the way in (`/dashboard/login`,
`/welcome`, `/setup`, `/oauth`, and anything under them; `/setupguide` is an ordinary page). And never from one of
those pages: until that step is done, the dashboard sends every other page back to it, so a page back from the
first password landed on `/dashboard/`, which put the person on `/welcome` again, and Back never left.

`back(history, current, dashboard_shown, origin)`:

- `leave` when the dashboard is not on screen (the cat that says Nouride is not answering, or it is still opening):
  nothing to lose.
- `page` when `back_inside`.
- `ask` otherwise: Back would close the dashboard, so the app asks first.

On Android this is the phone's own Back (the button, the swipe from the edge) and `nouridehome://back` alike. On the
desktop it is `nouridehome://back` alone: a window has its own close button, which closes it with no question.

## Back to Nouride Home

`leave(dashboard_shown)`:

- `ask` when the dashboard is on screen, whatever its page, the first one included: "Go back to Nouride Home?", with
  Go back and Stay. Go back leaves the dashboard for the app's own screen (on the desktop the dashboard's window
  closes and Nouride Home comes forward); Stay changes nothing.
- `leave` when it is not (the cat, or still opening): nothing to lose.

The question is the platform's own: a dialog on Android, a sheet on the dashboard's window on a Mac. It is never drawn
in the dashboard's page.

## Nouride not answering

`unreachable(main_frame, url, origin)` is true for a failed main-frame load of an in-app URL other than `about:blank`:
shown as the cat and Try again. A picture or a script that failed is the page's.

## Where the apps differ

No case differs today. The desktop's `new_window` (a page that asks for a new window) has no Android counterpart in
`LinkPolicy` (Android's `onCreateWindow` does it), so it stays in the crate's own tests.
