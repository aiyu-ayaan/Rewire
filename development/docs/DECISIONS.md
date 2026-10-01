# Decisions Log

Newest first. Format: date — decision — why.

- 2026-10-01 — NavHost start destination computed once (`remember`). — Writing `onboardingDone` re-keyed the graph mid-navigation and killed the hero shared transition.
- 2026-10-01 — Card -> detail uses fade-only nav transitions. — Container transform reads clean only when the shared bounds carry the motion; slide + fade on top muddied it.
- 2026-10-01 — Hero gradient primary -> inversePrimary (not -> tertiary). — Teal->amber blended to brown on screen; tonal pair stays clean in light, dark and dynamic schemes.

- 2026-10-01 — Material3 `1.5.0-alpha26` pinned over BOM (BOM gives 1.4.0). — `MaterialExpressiveTheme`,
  `MaterialShapes`, `LoadingIndicator`, `ButtonGroup`, wavy progress only exist in 1.5 alpha. Revisit when 1.5 stable.
- 2026-10-01 — Manual `AppContainer` DI in Phase 1. — Hilt + AGP 9 built-in Kotlin compatibility unverified; tiny graph. Hilt in Phase 2.
- 2026-10-01 — No bundled/downloadable fonts. — Avoid network + APK weight; Roboto Flex is M3 default.
- 2026-10-01 — Launcher-intent `<queries>` instead of `QUERY_ALL_PACKAGES`. — Play policy; only launchable apps matter.
- 2026-10-01 — Focus timer in ViewModel for Phase 1. — UI-first phase; ForegroundService needs Phase 3 resilience design.
- 2026-10-01 — Brand scheme default, dynamic color opt-in. — Level colors (Minor/Major/Max) must stay recognizable.
