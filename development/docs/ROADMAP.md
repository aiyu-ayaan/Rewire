# Roadmap

Phases ship in order. Each phase ends with green build + tests, and `Todo.md` updated.
Source spec: root `CLAUDE.md` (sections referenced as §N).

| Phase | Theme | Exit criteria | Status |
|---|---|---|---|
| 1 | Foundation, Expressive UI, Notifications | All 4 tabs navigable, landing w/ shared transition, notifications complete, domain tests green | done |
| 2 | Persistence + DI | Room + Hilt, every action logged as `HabitEvent`, process death keeps data | done |
| 3 | Guard engine + Monitoring | AccessibilityService triggers Minor/Major/Max via central `RuleEngine`; boot recovery; foreground focus service | done (device test of Lite pending) |
| 4 | Usage analytics | UsageStats ingestion, WorkManager aggregation, daily summary | done |
| 5 | Matrix full | Full chart set from aggregated metrics, weekly/monthly | done |
| 6 | V2 | Smart escalation (configurable), streaks, presets, goals | done |
| 7 | V3 | External adapters, backup/sync (opt-in), AI insights (never controls rules) | planned |

Phase 6b (localisation, 2026-10-04) shipped alongside V2: 27 languages and an in-app language switch. Details in [LOCALIZATION.md](LOCALIZATION.md).

## Phase 1 scope notes (historical)

- **No fake data.** Matrix and Guard start empty and fill from what user does in session.
- **No hardcoded packages.** App picker reads launcher activities from `PackageManager`.
- **Warnings data-driven.** Default library in `res/raw/default_warnings.json`.
- **Timer in ViewModel.** Phase 1 ceiling; now `FocusController` + `FocusTimerService` (§15).
- **In-memory repos** behind interfaces; now Room-backed with no UI change.

## Still out of scope

- External data adapters, cloud backup and sync, AI insights (7, opt-in)
- Network beyond the optional GitHub update check (absent in the Play build)
