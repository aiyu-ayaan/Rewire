# REWIRE — Development Tracker

Single source of truth for progress. Update checkbox + commit together.
Details per phase: [`docs/ROADMAP.md`](docs/ROADMAP.md). Design rules: [`docs/DESIGN.md`](docs/DESIGN.md).
Architecture: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md). Decisions log: [`docs/DECISIONS.md`](docs/DECISIONS.md).
Full vs Lite build, switching flavor: [`docs/FLAVORS.md`](docs/FLAVORS.md).

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
- [x] Timer in `FocusController` + `FocusTimerService` (FGS): survives swipe-away, minimise, screen off, process death (2026-10-02)

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
- [x] Daily summary scheduling (WorkManager, daily 09:00, KEEP, skips quiet days) (2026-10-03)
- [ ] Monitoring-disabled alert -> Phase 3

### 1.8 Matrix (basic)
- [x] Daily metrics cards from in-memory event log
- [x] Bar chart (focus minutes / day), progress ring, empty states
- [x] Real data from Room (events repository; weekly/monthly/daily views, Phase 5)

### 1.9 Profile
- [x] Theme (system/light/dark) + dynamic color
- [x] Notifications section
- [x] Permissions status (notifications live; accessibility / usage access shown as upcoming)
- [x] Warning library: list, enable/disable, favorite, add custom (in memory)

### 1.10 Quality
- [x] Unit tests: focus validation, focus state machine, warning picker
- [x] `assembleDebug` + `testDebugUnitTest` green

---

### 1.12 Focus fullscreen + nav bar rules (added 2026-10-01)
- [x] Fullscreen AMOLED timer route: true black, immersive bars, screen kept on
- [x] Per-digit rolling countdown (bouncy spring), blinking colon, wavy linear progress
- [x] Timer digits shared element: ring screen -> fullscreen
- [x] Burn-in drift (content shifts a few dp each minute), tap to reveal auto-hiding controls
- [x] FocusViewModel activity-scoped (one timer for tab + fullscreen)
- [x] Bottom bar / rail only on tab base screens; `HideNavigationBar()` for in-tab deeper states (focus timer, result)
- [ ] Visual check of fullscreen on device (emulator was in use)

### 1.13 Onboarding profile + grantable permissions (added 2026-10-01)
- [x] Onboarding: Landing -> Profile setup (name, avatar shape, goal, reason) -> Permissions -> app; hero shared through every step
- [x] User profile stored in DataStore; Profile tab header = user card (tap to edit, avatar shared element)
- [x] Avatar morphs between MaterialShapes with bouncy pop on change
- [x] PermissionsPanel (onboarding + Profile): Notifications, Accessibility, Usage access, Unrestricted battery; live status on resume, Allow opens the right settings
- [x] Accessibility prominent-disclosure dialog before opening settings
- [x] `RewireAccessibilityService` declared (window-change events only, no content); reports foreground package to `ForegroundAppDetector` — engine wiring is Phase 3
- [x] `PACKAGE_USAGE_STATS` declared so Rewire appears in Usage access list
- [x] Use profile `reason` inside warning screens (Phase 3 overlay)

### 1.14 Real enforcement (pulled forward from Phase 3, 2026-10-01)
- [x] `RuleEngine` (domain, 10 tests): Minor/Major warn, Max blocks on window / launch limit / daily limit, Max w/o boundary = always, focus bypass, overnight windows, next-boundary time
- [x] `HabitEngine`: accessibility foreground change -> decision -> guard screen -> events; main-thread, no polling (re-check scheduled at exact boundary)
- [x] Guard screen over Home (Home + guard in one startActivities) -> protected app can't resume over it; abandoning never re-triggers (no loops)
- [x] Guard hardening (device-verified, all 3 levels): recents/relaunch over guard re-judged, dropped guard never wedges engine, System UI overlays don't end a visit, reconnect after process death judges the open app
- [x] Continue / emergency let exactly one visit through; next open judged again
- [x] Emergency unlock requires device screen lock (BiometricPrompt: biometric or PIN/pattern/password)
- [x] Max-blocked apps' notifications hidden (NotificationListenerService, content never read), `NOTIFICATION_BLOCKED` logged
- [x] Habits, warnings, events persisted (JSON files, atomic writes) -> rules work with UI never opened / after process death
- [x] "Protection is off" banner on Guard + system notification when the service stops
- [x] Usage-based daily limit via UsageStats events (needs Usage access; skipped if not granted)
- [x] Daily-limit UI hint when Usage access missing
- [x] Foreground-service focus timer (2026-10-02)
- [x] Engine unit tests with fakes (decision -> outcome flows)

Verified on emulator: 1st Camera open allowed, 2nd blocked; emergency -> PIN -> opens once -> next open blocked;
blocked app's notification removed; kill -9 -> process + service restarted by system, still blocked;
service off -> banner + notification.

### 1.11 Verified on emulator (API 37, 1080x2400)
- Landing morph/orbit loop, hero shared-bounds into Guard header (checked at 5x animator scale)
- Habit card -> detail container transform, create flow, Major preview pause countdown
- POST_NOTIFICATIONS prompt, ongoing focus notification w/ countdown, test notification, deep link -> Focus
- Light + dark theme, system bar icons follow app theme
- Code-level audit done 2026-10-03 (width cap on wide screens, wrap/scroll at large font, TalkBack labels/roles, lint clean). Still needs a real device: >= 600dp rail, 200% font scale, TalkBack run, landscape focus timer, NavigationRail clipping at 200% font

### Known Phase 1 limits
- Debug build drops frames on first composition of a screen; profile on release build before tuning
- UI copy partly inline in composables; extract to strings.xml with i18n pass (Phase 2)

### 1.15 Focus: background, history, notes (added 2026-10-02)
- [x] Optional "what did you achieve?" dialog when a session completes or is stopped
- [x] Focus history screen: all finished sessions, Completed / Stopped filter, focused time, blocks, note (tap to edit)
- [x] Heads-up "Focus keeps running" pop-up when leaving the app mid-session (silent channel, auto timeout)
- [x] Android 16 Live Update: countdown chip in status bar / lock screen
- [x] Focus DND mutes notifications only: calls, alarms, media allowed; Rewire focus alerts bypass it
- [x] Verified on emulator: swipe-away keeps timer, `kill -9` resumes + completes on time, import kept habit/events/profile

### 1.16 App updates, same logic as BetweenUs (added 2026-10-02)
- [x] GitHub Releases as the only source; cumulative stable/beta/alpha channels; version-based "newer"
- [x] Launch check + daily WorkManager check (unmetered), "Not now" snooze, hourly throttle
- [x] Verified download (SHA-256) + PackageInstaller session + failure reasons via `UpdateInstallReceiver`
- [x] Release notes rendered as headings / bullets; update sheet + Profile -> Updates screen
- [x] Room schema v2 (`AutoMigration`), device-verified on a populated v1 DB
- [x] Verified on emulator against the real v1.0.1-alpha.1 release (offer, notes, download, refusal message)
- Emulator pass 2026-10-03 (Lite + Full, API 37): onboarding, create habit, Max block, escalation UI, Matrix daily/weekly/monthly with real events, focus presets + quick-test session + note, export -> clear -> import round trip, Room 1->2 and 2->3 migration androidTests (2/2 pass), landscape 200% font rail, 800dp rail, `assemblePlayRelease` (no INTERNET / INSTALL permission, no update receiver)
- Found on emulator, fixed 2026-10-03: import dialog plurals, chips spacing in Matrix daily, discipline label at 200% font, New-habit button hidden over the empty state (re-checked on emulator with zero habits 2026-10-04: no FAB, "Create first habit" reachable by scroll)
- [ ] Real install end to end needs two signed releases (emulator only had a debug build)
- [x] Play build: `play` flavor, updates compiled out (UPDATES flag, no REQUEST_INSTALL_PACKAGES/INTERNET/receiver) (2026-10-03). `assemblePlayRelease` (R8) builds clean 2026-10-04

## Phase 2 — Persistence + DI  (DONE 2026-10-02 except items below)
- [x] Extract remaining inline UI copy to strings.xml (~330 strings; domain-layer text, update errors, debug button left inline on purpose) (2026-10-03)
- [x] Baseline profile generated + wired (`:baselineprofile`); emulator startup median 749 ms -> 685 ms. Frame-timing comparison needs a real device (emulator reports frame counts only)
- [x] Hilt 2.60.1 (works with AGP 9 built-in Kotlin) replaces `AppContainer`; `@HiltViewModel` per screen, `@AndroidEntryPoint` services (2026-10-02)
- [x] Room: habits, protected apps, rules, warnings, events, focus sessions, settings (schema v1 exported)
- [x] Repositories swap JSON/DataStore -> Room, same interfaces; one-time legacy import (device-verified)
- [x] Event logger (`HabitEvent`) writes every action (audited: Guard + Focus, 2026-10-03)
- [x] Export / import / clear history (versioned JSON, validated, one Room transaction; Profile > Your data) (2026-10-03). Not yet tried on a device
- [x] Migration tests 1->2 and 2->3 (androidTest, compile-checked; run on a device still pending)

## Phase 3 — Guard engine + Monitoring
- [x] `RuleEngine` -> `RestrictionDecision` (Minor/Major/Max, windows, limits, launches) + tests
- [x] AccessibilityService: foreground package detect only, delegate to engine
- [x] Warning/Block overlay activity
- [x] Emergency unlock flow (confirm -> temp unlock -> log)
- [x] Guard monitoring ForegroundService (`specialUse`) with ongoing notification
- [x] BootReceiver restores monitoring
- [x] Battery optimization guidance screen
- [x] Android 13+ restricted settings guidance + deep link
- [x] Monitoring-disabled notification
- [x] Onboarding: permission explain + "Test protection"
- [x] Lite flavor (no accessibility service) + Usage-access fallback detection; CI ships both APKs (2026-10-02)
- [x] Engine created at app start (Lite had no monitoring); Guard restarts after app update (2026-10-02)
- [x] Block screen content hidden in Recents (FLAG_SECURE) (2026-10-02)
- [x] About shows Full/Lite build + install guide link; CI fails unless both APKs ship correctly (2026-10-02)
- [x] Focus timer + full-screen timer: landscape layouts, sensor rotation (2026-10-02)
- [x] Lite: Max not blocking on a real device fixed: overlay shield before the guard start, every usage resume judged (2026-10-02)
- [x] Lite: re-judge open app after unlock, restart monitoring when Rewire opens, warn on refused start / missing grants, 15 s usage lookback (2026-10-02)
- [ ] Device-test Lite release on a real phone (OEM skin) after the shield fix: Max daily limit, window, launch limit
- [x] Full flavor tested on emulator (API 37) 2026-10-03: Accessibility service bound, Camera intercepted, Minor tier via escalation, Max block, emergency unlock offered

## Phase 4 — Usage analytics
- [x] UsageStatsManager adapter synced with Digital Wellbeing data
- [x] WorkManager daily aggregation + daily summary notification (2026-10-03)
- [x] DailyMetrics / weekly / monthly aggregator + tests (`PeriodMetrics`) (2026-10-03). Past-day screen time stored via `ScreenTimeStore` (2026-10-04)

## Phase 5 — Matrix full
- [x] Chart set: line, area, stacked bar, bar, donut, radar, heatmap, timeline, scatter (2026-10-03)
- [x] Weekly + monthly views, table alternative for a11y (2026-10-03)

## Phase 6 (V2) — Escalation, streaks, presets, goals  (2026-10-03)
- [x] Smart escalation: configurable Minor/Major/Max minute tiers, off by default, in `RuleEngine`; Room schema v3 (AutoMigration 2->3)
- [x] Escalation: "repeated usage" mode: thresholds count today's opens, no Usage access needed; Room schema v5 (AutoMigration 4->5, migration test) (2026-10-04)
- [x] Goals (daily focus target, max overrides) + streaks, Profile editor, chips on Guard/Matrix daily, in backup
- [x] Focus presets (built-in + user presets, DataStore, in backup)
- [x] Built-in preset names in strings.xml, resolved by preset id in the UI (2026-10-04)
- [x] Daily screen time snapshots (`ScreenTimeStore`, 62 days, DataStore) synced on Matrix open + daily worker; cleared with history, included in backup export/import (2026-10-04)
- [x] Removed unused `ActivityHeatmap` in Charts.kt (2026-10-04)
## Phase 6b — Localisation  (2026-10-04)
- [x] Last hardcoded UI strings moved to `strings.xml`; Matrix punchlines are data from the domain (`FocusPunch`/`GuardPunch`), wording in resources
- [x] 26 translations in `values-xx/`: hi, es, pt-BR, id, ar, fr, ru, de, tr, ja, ko, it, vi, th, zh-CN, zh-TW, pl, bn (plural quantities per CLDR; lint clean)
- [x] Batch 3 (Indian languages): ta, te, mr, gu, kn, ml, pa, ur incl. Urdu RTL (2026-10-04)
- [x] Per-app language: Profile > Language (`AppLocale`); API 33+ via `LocaleManager` (shows in system App languages), below via prefs + `attachBaseContext`; `generateLocaleConfig`, `localeFilters`, Play `bundle.language.enableSplit = false`
- [x] Built-in warnings localised per language (`res/raw-xx/default_warnings.json`); Room keeps the English baseline, an unedited built-in is shown in the current language, an edited one stays as written (`RoomWarningRepository`, tested)
- [x] Notification channel names/descriptions refreshed on language change (`MainActivity` recreate + `onConfigurationChanged`)
- [x] Hindi, Arabic, Bengali punchlines re-polished; `Send` icon mirrors in RTL (charts are Canvas, stay LTR on purpose)
- [ ] Native-speaker review of all translations before release (esp. punch_* lines and warnings); machine translation only so far
- [ ] Device pass in Arabic (RTL), a CJK language and a long-text language (de/ru) for clipping; test language switch on API < 33 and >= 33
- [ ] `formatMinutes` unit suffixes (`5m`, `1h`) still English; `Locale`-aware date/number formatting not audited
- [ ] Release notes pulled from GitHub stay English (not app strings)
- [x] README (languages, feature list, permissions, tech stack, roadmap, current mermaid diagrams), ARCHITECTURE.md and ROADMAP.md refreshed to the real code (2026-10-04)

## Phase 7 (V3) — External data adapters, backup, AI insights (read-only)
