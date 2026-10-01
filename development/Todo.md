# REWIRE — Development Tracker

Single source of truth for progress. Update checkbox + commit together.
Details per phase: [`docs/ROADMAP.md`](docs/ROADMAP.md). Design rules: [`docs/DESIGN.md`](docs/DESIGN.md).
Architecture: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md). Decisions log: [`docs/DECISIONS.md`](docs/DECISIONS.md).

Legend: `[x]` done · `[ ]` todo · `[~]` partial (see note)

---

## Phase 1 — Foundation, Expressive UI, Notifications  (DONE 2026-10-01)

Goal: app looks and moves like real Material 3 Expressive, all 4 sections navigable,
notifications production-ready. Data in memory + DataStore (Room = Phase 2).

### 1.1 Project tracking
- [x] `development/` tracker, roadmap, design, architecture, decisions docs

### 1.2 Design system
- [x] Material 3 `1.5.0-alpha` (Expressive APIs) pinned over BOM
- [x] `MaterialExpressiveTheme` + `MotionScheme.expressive()`
- [x] Brand light/dark color schemes (teal seed, full tonal roles incl. surface containers)
- [x] Dynamic color (Android 12+) opt-in from Profile
- [x] Expressive type scale (emphasized weights, tabular timer digits)
- [x] Expressive shape scale (large-increased / extra-large-increased)
- [x] Motion tokens (spring specs, durations) in one file
- [x] Reduced-motion respected (system animator scale = 0 -> static)

### 1.3 Landing (first launch)
- [x] Continuous morphing hero (MaterialShapes polygon morph loop)
- [x] Orbiting shapes + breathing glow, infinite transition
- [x] Pager: Friction -> Awareness -> Choice story pages
- [x] Shared element transition: hero shape -> main app (SharedTransitionLayout)
- [x] Onboarding complete persisted (DataStore); landing shown once

### 1.4 App shell
- [x] 4-tab nav: Guard / Focus / Matrix / Profile
- [x] `ShortNavigationBar` on compact, `NavigationRail` on >= 600dp
- [x] Edge-to-edge, insets handled
- [x] Tab content crossfade/through motion
- [x] Notification deep link opens correct tab

### 1.5 Guard (UI + in-memory)
- [x] Today dashboard (discipline ring, focus, blocked attempts, warnings)
- [x] Habit list with level badges, enable switch
- [x] Habit detail with shared-bounds card -> detail transition
- [x] Create habit sheet: name, level (connected button group), apps picker
- [x] Installed launcher apps picker (`<queries>` LAUNCHER intent, no QUERY_ALL_PACKAGES)
- [x] Limits UI: daily limit, launch limit, allowed window
- [x] Warning preview: Minor / Major / Max screens (data-driven text)
- [ ] Real interception -> Phase 3

### 1.6 Focus (UI + in-memory)
- [x] Config: focus / break / cycles, `break <= focus` enforced (domain + UI)
- [x] Wavy circular progress timer, tabular digits
- [x] State machine: IDLE/FOCUSING/BREAK/PAUSED/COMPLETED/CANCELLED (pure Kotlin, tested)
- [x] Hold-to-end (escape not too easy)
- [x] Focus bypass settings (Minor on / Major configurable / Max off)
- [~] Timer lives in ViewModel; survives rotation, not process death -> Phase 3 ForegroundService

### 1.7 Notifications
- [x] Channels: Focus session (ongoing), Focus alerts, Guard, Daily summary, System
- [x] `RewireNotifier` single entry point, respects per-category prefs
- [x] POST_NOTIFICATIONS (API 33+) with rationale card before system prompt
- [x] Permission denied state + "Open settings" recovery
- [x] Ongoing focus notification (chronometer countdown, public version hides detail)
- [x] Focus start / break start / session complete notifications
- [x] Notification preferences screen (per category toggles, DataStore)
- [x] Test notification button
- [x] Content intent deep links into Focus tab
- [ ] Daily summary scheduling (WorkManager) -> Phase 4
- [ ] Monitoring-disabled alert -> Phase 3

### 1.8 Matrix (basic)
- [x] Daily metrics cards from in-memory event log
- [x] Bar chart (focus minutes / day), progress ring, empty states
- [ ] Real data from Room -> Phase 2/5

### 1.9 Profile
- [x] Theme (system/light/dark) + dynamic color
- [x] Notifications section
- [x] Permissions status (notifications live; accessibility / usage access shown as upcoming)
- [x] Warning library: list, enable/disable, favorite, add custom (in memory)

### 1.10 Quality
- [x] Unit tests: focus validation, focus state machine, warning picker
- [x] `assembleDebug` + `testDebugUnitTest` green

---

### 1.11 Verified on emulator (API 37, 1080x2400)
- Landing morph/orbit loop, hero shared-bounds into Guard header (checked at 5x animator scale)
- Habit card -> detail container transform, create flow, Major preview pause countdown
- POST_NOTIFICATIONS prompt, ongoing focus notification w/ countdown, test notification, deep link -> Focus
- Light + dark theme, system bar icons follow app theme
- Not yet checked: >= 600dp NavigationRail layout, 200% font scale, TalkBack pass

### Known Phase 1 limits
- In-memory habits/warnings/events vanish on process death (Room in Phase 2)
- Debug build drops frames on first composition of a screen; profile on release build before tuning
- UI copy partly inline in composables; extract to strings.xml with i18n pass (Phase 2)

## Phase 2 — Persistence + DI  (NEXT)
- [ ] Extract remaining inline UI copy to strings.xml
- [ ] Baseline profile + release-build perf check of transitions
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
