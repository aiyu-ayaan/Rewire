# Build flavors: Full and Lite

One codebase, two APKs (`detection` flavor dimension in `app/build.gradle.kts`). Same features, same
applicationId, same signing key. Only how Guard notices a protected app differs.

| | Full (`full`, default) | Lite (`lite`) |
| --- | --- | --- |
| Detection | AccessibilityService (instant) | Usage access events, read once a second while the screen is on |
| Guard screen launch | Accessibility service | Display over apps: overlay shield first (`GuardShield`), then the guard screen; the shield blocks on its own if the start is refused |
| Manifest | declares `RewireAccessibilityService` | `src/lite/AndroidManifest.xml` removes it (`tools:node="remove"`) |
| `BuildConfig.ACCESSIBILITY` | `true` | `false` |
| Play Protect, browser install | blocked (enhanced fraud protection) | allowed |
| Payment / UPI apps | refuse to run while the service is on | unaffected |
| Release asset | `Rewire-<v>.apk` | `Rewire-Lite-<v>.apk` |

A third flavor, `play`, is Full detection with the self-updater compiled out (no `INTERNET` or
`REQUEST_INSTALL_PACKAGES`, no Updates UI): `BuildConfig.UPDATES = false`, overlay `src/play/AndroidManifest.xml`.
Build it with `./gradlew bundlePlayRelease`; details in [UPDATES.md](UPDATES.md). It is not part of the
GitHub release assets.

A fourth flavor, `playLite`, is **what Google Play gets**: Lite detection (no accessibility service) with the
self-updater compiled out, so the fewest permissions. `ACCESSIBILITY = false`, `UPDATES = false`, overlay
`src/playLite/AndroidManifest.xml` (Lite's and Play's removals together: keep it in step with both). Release asset
`Rewire-Play-<v>.aab`, the file to upload to the Play Console. Build it with `./gradlew bundlePlayLiteRelease`.

Both Play flavors update through Google Play's in-app update screen (`com.google.android.play:app-update`, no
permission): `src/playStore/java/.../PlayUpdates.kt` offers it once per launch and resumes an update already
started. `full` and `lite` compile the no-op `src/sideload/java/.../PlayUpdates.kt` and keep the GitHub updater.

Why: Play Protect blocks sideloaded installs (browser, file manager, messaging) of any app that
declares an accessibility service. Payment apps refuse to run while a non-Play accessibility service is
enabled. A manifest without the service is the only way past both without Play distribution.

Full also falls back to the Lite path whenever its accessibility service isn't running (e.g. the user
turns it off to pay), as long as Usage access + Display over apps are granted.

## Switching flavor (developer)

Android Studio: **View → Tool Windows → Build Variants**, set `:app` to `fullDebug` (main) or `liteDebug`.

CLI:

```bash
./gradlew installFullDebug      # main flavor
./gradlew installLiteDebug      # lite
./gradlew installPlayDebug      # play (no self-update)
./run.sh                        # Full by default
REWIRE_FLAVOR=Lite ./run.sh     # Lite
```

Both debug builds install as `com.aiyu.rewire.debug` with the debug key, so each one installs over the
other and keeps its data. A plain `installDebug` / `assembleRelease` no longer exists by that name (or
builds both): always name the flavor.

Branching on flavor in code: `if (BuildConfig.ACCESSIBILITY) …`. Keep it to UI copy and permission rows.
Detection goes through `HabitEngine.onForeground` either way, so rules stay in one place.

## Testing

```bash
./gradlew testFullDebugUnitTest testLiteDebugUnitTest
```

On a device, Lite needs Usage access + Display over apps (Profile → Permissions). With adb:

```bash
adb shell appops set com.aiyu.rewire.debug GET_USAGE_STATS allow
adb shell appops set com.aiyu.rewire.debug SYSTEM_ALERT_WINDOW allow
adb logcat -s RewireGuard   # decision=… per protected open (debug builds only, no package names)
```

Verify the manifest of a release build:

```bash
aapt2 dump xmltree --file AndroidManifest.xml app/build/outputs/apk/lite/release/*.apk | grep -c BIND_ACCESSIBILITY_SERVICE   # must be 0
```

## Release

`.github/workflows/release.yml` builds `assembleFullRelease assembleLiteRelease bundlePlayLiteRelease`, names the
assets as above, and fails if either APK is missing, Lite declares an accessibility service, Full lost it, or the
Play AAB declares an accessibility service, `INTERNET`, `REQUEST_INSTALL_PACKAGES` or the update receiver. The in-app updater (`Releases.apkFor`) only offers the installed flavor's asset. Release notes link
users to README → Install, and the app shows its build in Profile → About.

## Background behaviour (both)

Guard runs in `GuardMonitorService` (foreground, ongoing notification), started by `HabitEngine`, which
`RewireApp` creates at process start. It keeps working with Rewire closed or swiped away. It restarts after
process death (`START_STICKY`), reboot and app update (`BootReceiver`: `BOOT_COMPLETED`,
`MY_PACKAGE_REPLACED`). It stays stopped after **Force stop** until Rewire is opened, because Android
delivers nothing to a force-stopped app.
