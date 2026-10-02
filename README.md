<p align="center"><img src="development/brand/rewire-symbol.svg" width="120" alt="REWIRE logo"></p>
<h1 align="center">REWIRE</h1>
<p align="center"><b>Break habits. Build control.</b></p>

<p align="center">
  <img alt="Android" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Compose" src="https://img.shields.io/badge/Jetpack%20Compose-Material%203%20Expressive-4285F4?logo=jetpackcompose&logoColor=white">
  <img alt="License" src="https://img.shields.io/badge/license-MIT-blue">
  <img alt="Privacy" src="https://img.shields.io/badge/data-on--device%20only-0f766e">
</p>

REWIRE is an Android app that reduces unwanted habits by adding **intentional friction** before distracting apps open. It is not a punishment or surveillance tool. It creates a pause, lets you choose, and measures what happens so you can learn your own patterns.

```
Friction → Awareness → Choice → Action → Measurement → Improvement
```

<p align="center"><a href="rewire-launch.mp4"><img src="docs/rewire-launch.gif" width="280" alt="REWIRE launch video"></a></p>
<p align="center"><sub>Launch video (click for the full-quality MP4)</sub></p>

## Screenshots

| Guard | Habit detail | Max block screen |
|:---:|:---:|:---:|
| <img src="docs/screenshots/guard.png" width="220"> | <img src="docs/screenshots/habit-detail.png" width="220"> | <img src="docs/screenshots/max-block.png" width="220"> |
| Today dashboard and your habits | Friction level, limits, allowed window | Hard boundary with your own reason and an emergency unlock |

| Focus | Matrix | Matrix (more) |
|:---:|:---:|:---:|
| <img src="docs/screenshots/focus.png" width="220"> | <img src="docs/screenshots/matrix.png" width="220"> | <img src="docs/screenshots/matrix-2.png" width="220"> |
| Focus / break / cycles timer | Focus and Guard stats, donut breakdown | Peak hour, by habit, heatmap |

| Warning library | Profile | Permissions |
|:---:|:---:|:---:|
| <img src="docs/screenshots/warning-library.png" width="220"> | <img src="docs/screenshots/profile.png" width="220"> | <img src="docs/screenshots/profile-2.png" width="220"> |
| Data-driven, editable warnings | Theme, dynamic color, settings | Every permission explained |

## Features

### Guard: protected apps and habit rules
- **Habits are first-class.** One habit (e.g. *Doom Scrolling*) groups many apps (Instagram, Reddit, YouTube).
- **Three friction levels**
  | Level | Behavior |
  |---|---|
  | **Minor** | A short "why are you opening this?" pause. Never blocks. Logged. |
  | **Major** | Warning with *Go back* or *Continue anyway*. Every warning and override is logged. |
  | **Max** | Hard block outside your boundary, with a deliberate emergency unlock that is logged. |
- **Boundaries per habit:** daily time limit, launch limit, allowed time window.
- **Safe by design:** rules can be disabled any time, Max never causes a device lockout, and Max bypass is off during Focus by default.

### Focus: concentration sessions
- Configurable focus, break and cycle counts. Presets: 25/5, 50/10, 90/20.
- Validated rule: `breakDuration <= focusDuration`.
- State machine: `IDLE → FOCUSING → BREAK → PAUSED → COMPLETED / CANCELLED`.
- Hold-to-end so escaping is not too easy.
- Focus can bypass selected Guard levels (Minor on, Major configurable, Max off by default).

### Matrix: analytics
- Daily, weekly and monthly views built from the event log.
- Line, area, donut, calendar heatmap, progress ring and bar charts.
- Guard breakdown (went back, continued, blocked, overrides), most guarded hour, by-habit and most-opened-app lists.
- Charts consume normalized domain metrics and never query Android APIs directly.

### Profile
- Theme (system, light, dark) and dynamic color.
- Warning library: add, edit, delete, favorite, enable/disable, randomized selection.
- Notification categories, permission status and guidance, about and acknowledgements.

### Privacy
Everything stays on the device. No analytics SDKs, no accounts, no cloud sync. Accessibility is used only for window-change events (the foreground package name), never for screen content. Notification access is used only to hide notifications from apps while they are Max-blocked, and content is never read.

## Architecture

```
                    Android System
                          │
     ┌────────────────────┼─────────────────────┐
     ▼                    ▼                     ▼
Accessibility       Notification            UsageStats
  Service             Listener                Manager
     │                    │                     │
     └──────────┬─────────┴──────────┬──────────┘
                ▼                    ▼
          HabitEngine ──────► RuleEngine (pure Kotlin)
                │                    │
                │          RestrictionDecision
                │       Allow │ Warn(level) │ Block(reason)
                ▼                    ▼
           EventLog           Warning / Block UI
                │
                ▼
      Persistent stores (JSON + DataStore)
                │
                ▼
  MetricsCalculator → Chart data → Matrix UI
```

**Layering:** `Composable → ViewModel → Repository → Data source`. The `domain` package has no Android imports, so rule, focus and analytics logic is tested on the plain JVM.

**Key rules**
- Restriction decisions live in one place, `RuleEngine`. The accessibility service only detects the foreground app and forwards it.
- Warning text is data (`res/raw/default_warnings.json`, plus user-edited warnings), not hardcoded in UI.
- Everything important becomes a `HabitEvent` (`APP_OPENED`, `WARNING_SHOWN`, `APP_BLOCKED`, `OVERRIDE_USED`, `FOCUS_*` and so on), and the events power Matrix.
- State is persisted so a killed process recovers its rules. REWIRE does not assume Android keeps it alive.

### Project structure

```
app/src/main/java/com/aiyu/rewire/
├── RewireApp.kt, MainActivity.kt, AppContainer.kt   Application, entry point, manual DI
├── core/
│   ├── apps/            Installed launcher apps (PackageManager)
│   ├── datastore/       Settings (theme, onboarding, notification and focus prefs)
│   ├── guard/           HabitEngine, UsageTracker
│   ├── notifications/   Channels, notifier, permission helpers
│   └── permissions/     Accessibility / usage / battery status checks
├── data/                JsonStore (atomic file persistence), repositories
├── domain/              Pure Kotlin
│   ├── habit/           Habit, ProtectedApp, RestrictionRule, WarningLevel
│   ├── restriction/     RuleEngine, RestrictionDecision
│   ├── warning/         Warning, categories, picker
│   ├── focus/           FocusConfig validation, FocusTimer state machine
│   └── analytics/       HabitEvent, DailyMetrics, MetricsCalculator
├── feature/             landing, onboarding, guard, focus, matrix, profile
├── service/
│   ├── accessibility/   RewireAccessibilityService
│   └── notifications/   RewireNotificationListener
└── ui/                  theme (color, type, shape, motion), shared components, nav host
```

More detail: [`development/docs/ARCHITECTURE.md`](development/docs/ARCHITECTURE.md), [`DESIGN.md`](development/docs/DESIGN.md), [`DECISIONS.md`](development/docs/DECISIONS.md), [`ROADMAP.md`](development/docs/ROADMAP.md). Progress is tracked in [`development/Todo.md`](development/Todo.md).

## Tech stack

| Area | Choice |
|---|---|
| Language | Kotlin 2.4 |
| UI | Jetpack Compose, Material 3 Expressive (`1.5.0-alpha`), dynamic color |
| Navigation | Navigation Compose, type-safe routes, shared element transitions |
| Async | Coroutines + Flow |
| Persistence | `JsonStore` (atomic JSON files) and DataStore Preferences |
| Serialization | kotlinx.serialization |
| Monitoring | AccessibilityService, UsageStatsManager, NotificationListenerService |
| Other | Biometric, Core SplashScreen, Graphics Shapes (morphing shapes) |
| Testing | JUnit (JVM unit tests for domain logic) |

SDK: `minSdk 26`, `targetSdk 35`. Versions are in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## Permissions

Each permission is explained in-app before it is requested.

| Permission | Why |
|---|---|
| Accessibility service | Detect which protected app just opened. Reads no screen content. |
| Usage access | Count time in guarded apps for limits and Matrix. Stays on device. |
| Notifications | Focus timer, break alerts and important Guard status. |
| Notification access | Hide notifications from apps while they are Max-blocked. |
| Battery optimization (guidance only) | Reduce the chance the system stops protection. The app never changes this silently. |

> Android controls process lifetime. REWIRE is built for resilience (persisted state, system-bound services), not for a promise that it can never be killed.

## Install

Every [release](https://github.com/aiyu-ayaan/Rewire/releases) ships two APKs with the same features:

| APK | Detects protected apps through | Pick it when |
| --- | --- | --- |
| `Rewire-Lite-<version>.apk` | Usage access + Display over apps (within ~1 s) | You download from a browser. Play Protect lets it install, and banking/UPI apps keep working. |
| `Rewire-<version>.apk` (Full) | Accessibility (instant) | You install with [Obtainium](https://github.com/ImranR98/Obtainium) or `adb install`. |

Why two: Play Protect's fraud protection blocks browser and file-manager installs of any app that declares an accessibility service. Payment apps also refuse to run while one from outside Play is switched on. On Full, turn Rewire off in Accessibility before paying. Guard keeps working through Usage access (grant it and Display over apps), then turn it back on.

Both are signed with the same key. Install the other APK over the top to switch builds, and your data is kept. Each one updates itself within its own flavor.
**Profile → About Rewire** shows which one you have (*Full build* / *Lite build*).

Both keep guarding with Rewire closed or swiped away from recents. The Guard service runs on its own with an ongoing notification and restarts after the system kills it or the phone reboots. Two things stop it until you open Rewire again: **Force stop** in App info, and an aggressive battery saver. Set Rewire's battery usage to **Unrestricted**.

## Build and run

Requirements: Android Studio (current stable) or JDK 17+ with the Android SDK, and a device or emulator on API 26+.

```bash
# Build, install and launch the debug build on a connected device/emulator
./run.sh            # add --logs to stream logcat

# Or manually (Full; installLiteDebug for Lite, REWIRE_FLAVOR=Lite ./run.sh)
./gradlew installFullDebug

# Unit tests
./gradlew test
```

The debug build installs as `com.aiyu.rewire.debug`. After install, open **Profile → Permissions** to enable Accessibility (Full only), Usage access and the other grants, then create a habit in **Guard**.

## Testing

Domain logic is tested without Android: rule engine (Minor, Major, Max, daily and launch limits, allowed window, Focus bypass), focus state machine and `break <= focus` validation, warning picking and metrics aggregation. See `app/src/test/`.

## Roadmap

| Phase | Scope |
|---|---|
| 1 | Expressive UI, 4-tab shell, notifications (done) |
| 2 | Room persistence, Hilt |
| 3 | Foreground-service focus timer, boot recovery, full monitoring |
| 4–5 | Usage analytics, complete Matrix |
| 6 (V2) | Smart escalation, streaks, presets, goals |
| 7 (V3) | Opt-in external data adapters, backup, read-only AI insights |

Rules always stay deterministic. Any future AI layer only explains recorded data and never controls restrictions.

## Contributing

Small, conventional commits (`feat:`, `fix:`, `refactor:`, `test:`). Keep business logic out of Activities, services and receivers, keep rule logic in `RuleEngine`, and add tests for rule-engine changes. See [`CLAUDE.md`](CLAUDE.md) for the full project rules.

## License

Released under the [MIT License](LICENSE).
