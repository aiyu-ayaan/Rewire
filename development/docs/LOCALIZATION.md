# Localisation

Languages (`AppLocale.tags`, must match the `values-*` folders): en, hi, es, pt-BR, id, ar, fr, ru, de, tr, ja, ko, it, vi, th, zh-CN, zh-TW, pl, bn, ta, te, mr, gu, kn, ml, pa, ur. Default/fallback: English.

## Where text lives
| What | File | Notes |
|---|---|---|
| UI strings, plurals | `res/values-xx/strings.xml` | English in `values/`. `app_name` is untranslatable. |
| Built-in warnings | `res/raw-xx/default_warnings.json` | Same ids/order as `raw/default_warnings.json` (English baseline). |
| Matrix punchlines | `punch_*` strings/plurals | Domain returns `FocusPunch`/`GuardPunch` data. |

## Switching
`AppLocale` (`core/settings`): API 33+ `LocaleManager.applicationLocales` (system recreates the activity; also visible in Settings > App languages). Below 33 the tag is kept in prefs (`app_locale`) and applied in `attachBaseContext` of `RewireApp`, `MainActivity`, `GuardActivity`, then `recreate()`. Empty tag = follow the system. UI: Profile > Language (`LanguageDialog`).

Build wiring: `androidResources.generateLocaleConfig` + `res/resources.properties` (`unqualifiedResLocale=en-US`), `localeFilters` list, `bundle.language.enableSplit = false`.

## Warnings and edits
Room stores English. `RoomWarningRepository.localize` swaps in the current language's wording only when a built-in's text still equals the English default; `delocalize` reverses it on `update`. `relocalize()` runs on `MainActivity.onCreate` and `Application.onConfigurationChanged`. Custom warnings are never touched.

## Adding a language
1. Copy `values/strings.xml` to `values-xx/`, translate (keep every `%1$s`/`%1$d`, CLDR plural quantities, `\'` escapes; `one` needs a `%d` where the language's `one` covers 0).
2. Copy `raw/default_warnings.json` to `raw-xx/`, translate title/message/motivationalMessage only.
3. Add the tag to `AppLocale.tags` and the language code to `localeFilters` in `app/build.gradle.kts`.
4. `./gradlew :app:lintLiteDebug :app:testFullDebugUnitTest`.

## Known gaps
Machine translation, no native review yet. `formatMinutes` suffixes (`m`, `h`) are English. RTL checked in code only (see Todo.md Phase 6b).
