# The address of a Nouride on another machine

Both apps can open a Nouride that runs somewhere else instead of running their own: a computer at home reached through
a Cloudflare tunnel, or any machine with an HTTPS address in front of it. A person is handed that address once and
types it. Cases: `fixtures/remote-address.json`.

## What is taken

- With or without `https://`; a bare name means `https`, as a tunnel is https. The scheme is read in any case.
- Spaces around it and one trailing `/` are dropped.
- A port is kept, except `443`, https's own: dropped, so the origin matches the links the dashboard writes.
- The dashboard's own address (`/dashboard` or `/dashboard/`) pasted from a chat becomes the machine it is on.
- The host is lower-cased. The result, the base, is `https://<host>[:<port>]`.

## What is refused, and how it is said

Each refusal has its own sentence on screen, so `read` says why:

- `empty`: nothing typed, or only spaces.
- `plain_http`: `http://`. A dashboard reached over the network is a login, and a password on the wire. Said on its
  own because a person fixes it by typing `https://`.
- `not_an_address`: a space inside, another scheme, a path other than the dashboard's, a query, a fragment, no host,
  a port outside 1 to 65535, more than 200 characters, or what a strict URI parser refuses.

## Shown and opened

`shown(base)` is the base without `https://`, as a person typed it. `dashboard(base)` is `<base>/dashboard/`, the same
path as the device's own Nouride.

## Where the apps differ

Nowhere today.
