# REWIRE — Handoff Notes

## Current State (Phase 6c Complete)

All primary tasks for Phase 6c (Quit tracker & 27-language localization) are complete, verified on device, and lint clean.

### 1. Localization Completed
- **1,006 reflections** translated and merged across all 26 locale folders in `app/src/main/res/values-xx/quit_thoughts.xml`.
- **UI strings & plurals** translated and merged into `values-xx/strings.xml` for all 26 languages (plus English baseline in `values/`).
- Strict quality gates enforced via `scripts/quit-i18n/merge.py`:
  - Exact line count match across all 10 parts
  - Zero duplicate padding or unescaped apostrophes
  - Non-Latin script enforcement for non-Latin locales
  - Correct CLDR plural categories matching each language's `data_import_habits`
- Both `./gradlew :app:processFullDebugResources` and `./gradlew :app:lintFullDebug` pass clean without warnings or missing translations.
- Unit tests (`172 tests`) pass clean via `./gradlew testFullDebugUnitTest`.

### 2. Device & Emulator Pass Verified
- **Phones (compact portrait):** Floating navigation pill with 5 tabs; selected tab displays icon + label, unselected tabs display icons.
- **Narrow width (360dp) & Long-label languages (de, ru):** 5-tab pill fits without clipping or text overlap at 200% font scale.
- **Tablets & Foldables (>= 600dp):** Upright navigation pill on the left rail; adaptive multi-pane / master-detail layouts.
- **RTL layout (Arabic, Urdu):** Navigation pill, header, thought cards, and action buttons mirror cleanly in right-to-left orientation.
- **Dark Theme & Insets:** Edge-to-edge content respects system navigation bars without hiding behind the floating pill.

### 3. Architecture & Privacy
- Quit tracker state lives in its own dedicated DataStore (`quit`), strictly separate from Room, Matrix, analytics events, and backups.
- Neutral terminology used across code, resources, and comments ("Quit", "the habit", "the urge").

### Optional Next Steps (Discuss with Owner)
- Biometric lock or hide toggle for the Quit tab.
- Opt-in inclusion of Quit data in backup export/import (strictly disabled by default).
- Gentle milestone notifications respecting notification preferences.
