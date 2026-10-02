# Decisions Log

Newest first. Format: date — decision — why.

- 2026-10-02 — Block screen uses `FLAG_SECURE`. — Android lists the on-screen task in Recents despite `excludeFromRecents` and captures it before any callback (pause, top-resumed and focus loss were all tried on API 37: too late). A secure window is blank there. Cost: no screenshots of that one screen.
- 2026-10-02 — `RewireApp` injects `HabitEngine` eagerly. — The engine starts `GuardMonitorService`. Only the accessibility service created it before, so Lite never monitored.
- 2026-10-02 — Focus timer screens follow the sensor (`FULL_SENSOR`) and get landscape layouts. — A desk timer is often landscape. The fixed 300dp ring squashed in landscape and the controls fell off screen. Orientation is restored on leaving full screen.

- 2026-10-02 — Two flavors: `full` (Accessibility) and `lite` (no accessibility service in the manifest, Usage access + overlay detection). — Play Protect's enhanced fraud protection blocks sideloaded installs that declare an accessibility service, and UPI/payment apps refuse to run while one is enabled. Without Play Console, a manifest without the service is the only way past both. Usage events have no push API, so detection reads one second of events per tick, only while the screen is on and Accessibility isn't reporting. Full falls back to the same path when Accessibility is off.

- 2026-10-02 — Self-update from GitHub Releases, BetweenUs-style, with `HttpURLConnection` (no OkHttp) and kotlinx.serialization for parsing. — Mirrors the proven design; stdlib HTTP keeps the dependency list unchanged and the parser is JVM-testable. Re-adds `INTERNET` (+ `REQUEST_INSTALL_PACKAGES`) knowingly; opt-out switch, no usage data sent, see docs/UPDATES.md.
- 2026-10-02 — Update prefs live in the Room settings row (schema v2, AutoMigration), not SharedPreferences. — Same rule as the rest of the app: one database, one backup/export story.

- 2026-10-02 — Hilt replaces manual `AppContainer`; one `AppModule` of @Provides (no @Binds-per-repo modules). — AGP 9 compat verified (2.60.1); repos need non-injectable args (clock, defaults), so providers are simpler than @Inject constructors. Preference screens share one `SettingsViewModel`.

- 2026-10-02 — Everything in Room, incl. settings (single typed row); DataStore kept only to import old prefs once. — One source of truth, migrations, history queries. Repos keep a write-through cache so the a11y service reads rules synchronously.
- 2026-10-02 — Focus timer = app-scoped `FocusController` + `FocusTimerService` (specialUse FGS, sticky). — Activity-scoped ViewModel died with the task; Room row lets a killed process resume the session.
- 2026-10-02 — Partial wake lock until the running phase ends, not exact alarms. — Exact alarms need a user-granted permission on Android 14+; a timed wake lock is reliable and scoped to an active session.
- 2026-10-02 — Focus DND allows calls, alarms, media; Rewire focus channels bypass it; chimes on alarm stream. — "Mute notifications, not calls"; the old policy also muted alarm clocks.

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
