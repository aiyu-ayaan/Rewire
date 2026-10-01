# App updates

How the app notices a new release and takes it. Same design as BetweenUs (`development/docs/UPDATES.md`
there), cut down to what Rewire needs: Android only, one universal APK.

There is no store, no update server and no `latest.yml`. A release is a GitHub Release with named files
attached (`.github/workflows/release.yml`); the app reads that list.

## Asset contract

```text
Rewire-<version>.apk    sideloadable, what the app downloads
Rewire-<version>.aab    for Play, never offered
```

Renaming the APK silently stops updates: the check still runs, finds the release and finds nothing in it
it can apply. `Releases.apkFor` matches `Rewire-*.apk`.

## Flow

```text
launch / daily WorkManager job
   -> AppUpdater.check()      GET api.github.com/repos/aiyu-ayaan/Rewire/releases
   -> Releases.pick()         newest on the chosen channel, newer BY VERSION (never by publish date)
   -> Available -> sheet / notification
   -> Download (app scope, SHA-256 checked against the digest GitHub records)
   -> Ready -> PackageInstaller session -> system confirmation screen (never skipped)
   -> UpdateInstallReceiver reports refusal reasons back to the Updates screen
```

| Piece | Where |
|---|---|
| Pure rules: `Version`, `UpdateChannel`, `Releases.pick/apkFor/parse`, `ReleaseNotes.parse` | `domain/update/` (unit tested) |
| Check, download, verify, install session, state | `core/update/AppUpdater` |
| Install result | `core/update/UpdateInstallReceiver` |
| Daily check while closed | `core/update/UpdateWorker` (unmetered network, battery not low, KEEP policy) |
| Sheet + Profile -> Updates screen | `feature/update/` |

## Channels

Cumulative, like BetweenUs: `stable` = finished releases; `beta` = betas + stable; `alpha` = everything.
Default is the channel the running build belongs to (an alpha install is not stranded on stable). A stable
release cut after an alpha is never an "upgrade" for someone already on a higher version.

## Settings (Room, schema v2)

`settings.updates_enabled` (default on), `update_channel` (null = follow the build), `update_snoozed_until`,
`update_last_checked`. This was the first real migration: `AutoMigration(1, 2)`, verified on-device against a
populated v1 database. "Not now" snoozes for a day; a launch check is skipped within an hour of the last one
(unauthenticated GitHub allows 60 requests an hour per address).

## Privacy and permissions

Only the public release list and the APK are fetched; nothing about the user or their usage is sent. Turning
"Check automatically" off cancels the daily job. `INTERNET` and `REQUEST_INSTALL_PACKAGES` were added for this;
the user still grants "install unknown apps" in system settings and confirms on Android's own screen.

**Trade-off:** `INTERNET` was removed earlier to avoid Play Protect flags, and `REQUEST_INSTALL_PACKAGES` is
restricted on Google Play. A Play build should compile this feature out (flavor) rather than ship it.

## What it can't do

- A debug build can't be updated from a release APK (different package name); the installer's reason is shown.
- An APK signed with a different key is refused by Android (`STATUS_FAILURE_CONFLICT`), with an explanation.
- No silent install: Android has none for non-device-owner apps, and the confirmation screen is the point.
