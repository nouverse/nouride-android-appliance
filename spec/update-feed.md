# Release versions and the update feed

Both apps compare release versions: for their own update, and for Nouride's. Cases: `fixtures/versions.json`.

## Versions

A release version is `x.y.z`, three numbers of digits only; a leading `v` is read (`v0.8.39` is `0.8.39`). Anything
else is no release: `0.8`, `nightly`, `0.1.1-beta`, `0.1.+1`, and `0.0.0-dev`, the version of a developer's build.

`newer(candidate, installed)` is true only when both are releases and the candidate is later, number by number
(`0.10.0` is later than `0.9.0`). A developer's build is therefore never offered an update.

## The app's own update feed

Android reads the newest release of this repository, `https://api.github.com/repos/nouverse/nouride-home/releases/latest`
(drafts and pre-releases left out): a tag `vX.Y.Z` with `nouride-home-X.Y.Z.apk` and `nouride-home-X.Y.Z.apk.sha256`
attached, over https only. The feed's JSON is Android's alone today: the desktop's own update is Tauri's updater, and
the release does not publish a desktop build yet. Its cases stay in `UpdateFeedTest`.

## Where the apps differ

No case differs today.
