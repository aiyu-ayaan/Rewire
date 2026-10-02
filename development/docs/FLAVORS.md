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
| Release asset | `Rewire-<v>.apk` (+ `.aab` for Play) | `Rewire-Lite-<v>.apk` |

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

`.github/workflows/release.yml` builds `assembleFullRelease assembleLiteRelease bundleFullRelease`, names the
assets as in the table, and fails if either APK is missing, Lite declares an accessibility service, or Full
lost it. The in-app updater (`Releases.apkFor`) only offers the installed flavor's asset. Release notes link
users to README → Install, and the app shows its build in Profile → About.

## Background behaviour (both)

Guard runs in `GuardMonitorService` (foreground, ongoing notification), started by `HabitEngine`, which
`RewireApp` creates at process start. It keeps working with Rewire closed or swiped away. It restarts after
process death (`START_STICKY`), reboot and app update (`BootReceiver`: `BOOT_COMPLETED`,
`MY_PACKAGE_REPLACED`). It stays stopped after **Force stop** until Rewire is opened, because Android
delivers nothing to a force-stopped app.
