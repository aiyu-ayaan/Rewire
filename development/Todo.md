# REWIRE — Development Tracker

Single source of truth for progress. Update checkbox + commit together.
Details per phase: [`docs/ROADMAP.md`](docs/ROADMAP.md). Design rules: [`docs/DESIGN.md`](docs/DESIGN.md).
Architecture: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md). Decisions log: [`docs/DECISIONS.md`](docs/DECISIONS.md).

Legend: `[x]` done · `[ ]` todo · `[~]` partial (see note)

---

## Phase 1 — Foundation, Expressive UI, Notifications  (CURRENT)

Goal: app looks and moves like real Material 3 Expressive, all 4 sections navigable,
notifications production-ready. Data in memory + DataStore (Room = Phase 2).

### 1.1 Project tracking
- [x] `development/` tracker, roadmap, design, architecture, decisions docs

### 1.2 Design system
- [ ] Material 3 `1.5.0-alpha` (Expressive APIs) pinned over BOM
- [ ] `MaterialExpressiveTheme` + `MotionScheme.expressive()`
- [ ] Brand light/dark color schemes (teal seed, full tonal roles incl. surface containers)
- [ ] Dynamic color (Android 12+) opt-in from Profile
- [ ] Expressive type scale (emphasized weights, tabular timer digits)
- [ ] Expressive shape scale (large-increased / extra-large-increased)
- [ ] Motion tokens (spring specs, durations) in one file
- [ ] Reduced-motion respected (system animator scale = 0 -> static)

### 1.3 Landing (first launch)
- [ ] Continuous morphing hero (MaterialShapes polygon morph loop)
- [ ] Orbiting shapes + breathing glow, infinite transition
- [ ] Pager: Friction -> Awareness -> Choice story pages
- [ ] Shared element transition: hero shape -> main app (SharedTransitionLayout)
- [ ] Onboarding complete persisted (DataStore); landing shown once

### 1.4 App shell
- [ ] 4-tab nav: Guard / Focus / Matrix / Profile
- [ ] `ShortNavigationBar` on compact, `NavigationRail` on >= 600dp
- [ ] Edge-to-edge, insets handled
- [ ] Tab content crossfade/through motion
- [ ] Notification deep link opens correct tab

### 1.5 Guard (UI + in-memory)
- [ ] Today dashboard (discipline ring, focus, blocked attempts, warnings)
- [ ] Habit list with level badges, enable switch
- [ ] Habit detail with shared-bounds card -> detail transition
- [ ] Create habit sheet: name, level (connected button group), apps picker
- [ ] Installed launcher apps picker (`<queries>` LAUNCHER intent, no QUERY_ALL_PACKAGES)
- [ ] Limits UI: daily limit, launch limit, allowed window
- [ ] Warning preview: Minor / Major / Max screens (data-driven text)
- [ ] Real interception -> Phase 3

### 1.6 Focus (UI + in-memory)
- [ ] Config: focus / break / cycles, `break <= focus` enforced (domain + UI)
- [ ] Wavy circular progress timer, tabular digits
- [ ] State machine: IDLE/FOCUSING/BREAK/PAUSED/COMPLETED/CANCELLED (pure Kotlin, tested)
- [ ] Hold-to-end (escape not too easy)
- [ ] Focus bypass settings (Minor on / Major configurable / Max off)
- [ ] Timer lives in ViewModel; survives rotation, not process death -> Phase 3 ForegroundService

### 1.7 Notifications
- [ ] Channels: Focus session (ongoing), Focus alerts, Guard, Daily summary, System
- [ ] `RewireNotifier` single entry point, respects per-category prefs
- [ ] POST_NOTIFICATIONS (API 33+) with rationale card before system prompt
- [ ] Permission denied state + "Open settings" recovery
- [ ] Ongoing focus notification (chronometer countdown, public version hides detail)
- [ ] Focus start / break start / session complete notifications
- [ ] Notification preferences screen (per category toggles, DataStore)
- [ ] Test notification button
- [ ] Content intent deep links into Focus tab
- [ ] Daily summary scheduling (WorkManager) -> Phase 4
- [ ] Monitoring-disabled alert -> Phase 3

### 1.8 Matrix (basic)
- [ ] Daily metrics cards from in-memory event log
- [ ] Bar chart (focus minutes / day), progress ring, empty states
- [ ] Real data from Room -> Phase 2/5

### 1.9 Profile
- [ ] Theme (system/light/dark) + dynamic color
- [ ] Notifications section
- [ ] Permissions status (notifications live; accessibility / usage access shown as upcoming)
- [ ] Warning library: list, enable/disable, favorite, add custom (in memory)

### 1.10 Quality
- [ ] Unit tests: focus validation, focus state machine, warning picker
- [ ] `assembleDebug` + `testDebugUnitTest` green

---

## Phase 2 — Persistence + DI
- [ ] Hilt (verify AGP 9 compat) replaces `AppContainer`
- [ ] Room: habits, protected apps, rules, warnings, events, focus sessions
- [ ] Repositories swap in-memory -> Room, same interfaces
- [ ] Event logger (`HabitEvent`) writes every action
- [ ] Export / import / clear history (JSON, kotlinx.serialization)
- [ ] Migration tests

## Phase 3 — Guard engine + Monitoring
- [ ] `RuleEngine` -> `RestrictionDecision` (Minor/Major/Max, windows, limits, launches) + tests
- [ ] AccessibilityService: foreground package detect only, delegate to engine
- [ ] Warning/Block overlay activity
- [ ] Emergency unlock flow (confirm -> temp unlock -> log)
- [ ] Focus timer -> ForegroundService (`specialUse`/`shortService` review), restore from persistence
- [ ] BootReceiver restores monitoring
- [ ] Battery optimization guidance screen
- [ ] Monitoring-disabled notification
- [ ] Onboarding: permission explain + "Test protection"

## Phase 4 — Usage analytics
- [ ] UsageStatsManager adapter (usage access onboarding)
- [ ] WorkManager daily aggregation + daily summary notification
- [ ] DailyMetrics / weekly / monthly aggregator + tests

## Phase 5 — Matrix full
- [ ] Chart set: line, area, stacked bar, donut, radar, heatmap, timeline, scatter
- [ ] Weekly + monthly views, table alternative for a11y

## Phase 6 (V2) — Escalation, streaks, presets, goals
## Phase 7 (V3) — External data adapters, backup, AI insights (read-only)
