# Behaviour both apps share

Nouride Home is two apps, Android (Kotlin, `apps/android`) and desktop (Rust and React, `apps/desktop`, `crates/`),
that do some of the same things. Where they do, the behaviour is written down here once, a short page each, and its
cases are in `fixtures/` as JSON that both test suites read: the Android unit tests (`apps/android/app/src/test/kotlin/tech/nouverse/nouride/android/spec/`, given
this folder by Gradle as `nouride.spec`) and the crates' Rust tests (`crates/*/tests/spec_*.rs`). A case changed
here is checked against both apps on the next test run.

| Page | Fixture | Android | Desktop |
| --- | --- | --- | --- |
| [remote-address.md](remote-address.md) | `fixtures/remote-address.json` | `server/RemoteHost.kt` | `crates/place/src/remote_host.rs` |
| [link-policy.md](link-policy.md) | `fixtures/link-policy.json` | `ui/LinkPolicy.kt` | `crates/link-policy` |
| [update-feed.md](update-feed.md) | `fixtures/versions.json` | `update/UpdateFeed.kt` | `crates/shared/src/version.rs` |
| [downloads.md](downloads.md) | `fixtures/downloads.json` | `download/Downloads.kt` | `crates/downloads` |
| [backup.md](backup.md) | `fixtures/backup.json` | `backup/Backup.kt` | `crates/backup` |

## Reading a case

A case's `expect` is the answer of both apps. When the two apps answer differently today, the case also carries
`android` or `desktop`, that app's own answer, and each test suite takes its own one first. Such a case is listed
under "Where the apps differ" on its page, and stays there until it is decided which answer is right: a difference
is recorded, never fixed by changing a fixture to match one app.

## Changing shared behaviour

Change the page, the fixture and both apps in the same change. A case that only one app can run (a platform's own
rule) stays in that app's own tests, not here.
