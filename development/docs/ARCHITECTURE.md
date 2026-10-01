# Architecture (Phase 1 snapshot)

```
app/src/main/java/com/rewire/app/
├── RewireApp.kt              Application; owns AppContainer, creates notification channels
├── MainActivity.kt           setContent only; reads deep-link tab extra
├── AppContainer.kt           manual DI (Hilt in Phase 2)
├── core/
│   ├── datastore/            SettingsRepository (theme, onboarding, notif prefs, focus bypass)
│   ├── notifications/        Channels, RewireNotifier, permission helpers
│   └── apps/                 InstalledAppsSource (PackageManager launcher query)
├── domain/
│   ├── habit/                Habit, ProtectedApp, RestrictionRule, WarningLevel
│   ├── warning/              Warning, WarningCategory, WarningPicker
│   ├── focus/                FocusConfig (+validation), FocusTimer state machine
│   └── analytics/            HabitEvent, DailyMetrics, MetricsCalculator
├── data/
│   ├── HabitRepository       interface + InMemory impl
│   ├── WarningRepository     interface + impl (loads res/raw/default_warnings.json)
│   └── EventLog              in-memory HabitEvent stream
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
- Repos in memory -> Room (Phase 2)
- Focus timer in ViewModel -> ForegroundService (Phase 3)
- Manual DI -> Hilt (Phase 2)
