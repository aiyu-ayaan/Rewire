# Architecture (Phase 1 snapshot)

```
app/src/main/java/com/aiyu/rewire/
├── RewireApp.kt              Application; owns AppContainer, creates notification channels
├── MainActivity.kt           setContent only; reads deep-link tab extra
├── di/AppModule.kt          Hilt singleton graph (Room, repos, settings flow, engines)
├── core/
│   ├── settings/             Settings model + SettingsRepository (Room single-row table)
│   ├── update/               AppUpdater, UpdateWorker, UpdateInstallReceiver (GitHub self-update)
│   ├── focus/                FocusController: app-scoped timer, persists every transition
│   ├── notifications/        Channels, RewireNotifier, permission helpers
│   └── apps/                 InstalledAppsSource (PackageManager launcher query)
├── domain/
│   ├── habit/                Habit, ProtectedApp, RestrictionRule, WarningLevel
│   ├── warning/              Warning, WarningCategory, WarningPicker
│   ├── update/               Version, UpdateChannel, Releases, ReleaseNotes (pure)
│   ├── focus/                FocusConfig (+validation), FocusTimer state machine
│   └── analytics/            HabitEvent, DailyMetrics, MetricsCalculator
├── data/
│   ├── Repositories.kt       Habit / Warning / Event / FocusSession repos: Room + write-through cache
│   └── local/                RewireDatabase, entities, DAOs, mappers, LegacyImport (JSON + DataStore -> Room, once)
├── service/
│   ├── accessibility/  monitoring/  boot/
│   └── focus/                FocusTimerService: FGS + wake lock for the running phase
├── feature/
│   ├── landing/  guard/  focus/  matrix/  profile/
└── ui/
    ├── theme/                Color, Type, Shape, Motion, Theme
    ├── components/           shared composables (LevelBadge, MorphingShape, StatCard…)
    └── RewireNavHost.kt      landing -> main, tabs, shared transition scope
```

Flow: `Composable -> ViewModel -> Repository/UseCase -> source`.
Domain has zero Android imports (enforced by keeping it in plain Kotlin; tests run on JVM).

## Phase 1 ceilings (marked `ponytail:` in code)
- Repos cache whole tables in memory (sync reads for the a11y hot path) -> page from Room if it grows
- Focus wake lock per running phase -> exact AlarmManager alarms if battery vitals complain

## Database (Room, `rewire.db`, schema exported to `app/schemas/`)
| Table | Key | Notes |
|---|---|---|
| `habits` | id | `created_at` orders the list |
| `protected_apps` | (habit_id, package_name) | FK habits CASCADE, index package_name |
| `restriction_rules` | id, unique habit_id | FK habits CASCADE, 1:1 |
| `warnings` | id | built-ins insert-ignored on start, user edits kept |
| `habit_events` | id | no FK (history outlives habits); index timestamp, (habit_id, type, timestamp); metadata JSON |
| `focus_sessions` | id | live timer state while active, history + note once finished |
| `settings` | id = 0 | single typed row |
Schema changes = new version + `Migration` + exported JSON. Never destructive fallback.

## Protected-app detection (two paths, one engine)

```text
RewireAccessibilityService (Full only) ──┐
                                        ├──> HabitEngine.onForeground ──> RuleEngine ──> GuardActivity
GuardMonitorService usage watch ────────┘    (when accessibility isn't running; Usage access + overlay)
```

The usage watch reads one second of `UsageEvents` per tick, only with the screen on, consuming each resume
once (cursor = its timestamp). See [FLAVORS.md](FLAVORS.md).
