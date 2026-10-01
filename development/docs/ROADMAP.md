# Roadmap

Phases ship in order. Each phase ends with green build + tests, and `Todo.md` updated.
Source spec: root `CLAUDE.md` (sections referenced as §N).

| Phase | Theme | Exit criteria |
|---|---|---|
| 1 | Foundation, Expressive UI, Notifications | All 4 tabs navigable, landing w/ shared transition, notifications complete, domain tests green |
| 2 | Persistence + DI | Room + Hilt, every action logged as `HabitEvent`, process death keeps data |
| 3 | Guard engine + Monitoring | AccessibilityService triggers Minor/Major/Max via central `RuleEngine`; boot recovery; foreground focus service |
| 4 | Usage analytics | UsageStats ingestion, WorkManager aggregation, daily summary |
| 5 | Matrix full | Full chart set from aggregated metrics, weekly/monthly |
| 6 | V2 | Smart escalation (configurable), streaks, presets, goals |
| 7 | V3 | External adapters, backup/sync (opt-in), AI insights (never controls rules) |

## Phase 1 scope notes

- **No fake data.** Matrix and Guard start empty and fill from what user does in session.
- **No hardcoded packages.** App picker reads launcher activities from `PackageManager`.
- **Warnings data-driven.** Default library in `res/raw/default_warnings.json`.
- **Timer in ViewModel.** Accepted Phase 1 ceiling; moves to ForegroundService in Phase 3 (§15).
- **In-memory repos** behind interfaces so Phase 2 swaps to Room without UI change.

## Out of scope until noted phase

- Interception / blocking other apps (3)
- Usage access permission (4)
- Network of any kind (7, opt-in)
