# Design System — Material 3 Expressive

Inputs: `material-3` skill (Compose-first, Expressive), `ui-ux-pro-max` design-system query
("habit tracker focus productivity wellbeing app"): teal focus + warm accent, flat/tonal,
friendly modern type, motion 7/10.

## Principles

1. **Calm control, not punishment.** Teal = focus/calm. Warm tertiary = attention. Red only for Max/errors.
2. **Tonal depth, no shadows.** Hierarchy via `surfaceContainer*` roles.
3. **Motion carries meaning.** Springs from `MotionScheme.expressive()`; shared elements for
   continuity (landing -> app, habit card -> detail). Continuous motion only on landing hero + live timer.
4. **Shape as identity.** `MaterialShapes` (Cookie, Sunny, Clover, Pill, Gem) for hero/icons,
   morph between them. Components keep standard M3 shapes.

## Color

Seed teal `#0D9488`. Full scheme in `ui/theme/Color.kt` (light + dark, all surface containers).
- primary -> focus, main CTA
- secondary -> supportive / tonal buttons
- tertiary (warm amber) -> Major level, attention
- error -> Max level, destructive
- Dynamic color optional (Profile), brand scheme default.

Per-tab accent (`AccentTheme` in `ui/theme/Theme.kt`): Guard + Profile teal, Focus indigo `#4F5BD5`,
Quit leaf `#5B8C3A`, Matrix violet `#7E57C2`. Same tonal-spot generation as the brand; tertiary and
error stay brand on every tab so warning levels never change color. With dynamic color on, Focus swaps
in the wallpaper's secondary as primary and Matrix its tertiary. Screens use `primary`/`primaryContainer`
for their own identity, never `tertiary` (that is the Major level).

Warning level mapping (always paired with icon + label, never color only):
| Level | Container | Icon |
|---|---|---|
| Minor | secondaryContainer | `Lightbulb` |
| Major | tertiaryContainer | `PanTool` |
| Max | errorContainer | `Block` |

## Typography

M3 type scale, system Roboto Flex (no bundled fonts, no network). Display uses tighter tracking +
heavier weight (expressive "emphasized"). Timer uses tabular figures (`fontFeatureSettings = "tnum"`).

## Shape

| Token | dp |
|---|---|
| extraSmall | 8 |
| small | 12 |
| medium | 20 |
| large | 28 |
| extraLarge | 36 |

Cards: `large`. Sheets/dialogs: `extraLarge`. Buttons: full (default).

## Motion tokens (`ui/theme/Motion.kt`)

- Spatial: `MaterialTheme.motionScheme.defaultSpatialSpec()` / `fastSpatialSpec()`
- Effects (color/alpha): `defaultEffectsSpec()`
- Landing loop: morph 2.4s per shape, orbit 18s/rev, glow 3s breathe
- Stagger lists 40ms/item
- Reduced motion: `Settings.Global.ANIMATOR_DURATION_SCALE == 0` -> loops stop, shapes static

## Spacing

8dp grid: 4 / 8 / 12 / 16 / 24 / 32 / 48. Screen gutter 16 (compact), 24 (>= 600dp).
Touch targets >= 48dp.

## Accessibility checklist (per screen)
- [ ] Contrast 4.5:1 text, 3:1 UI (both themes)
- [ ] Icon buttons have `contentDescription`
- [ ] Level never color-only
- [ ] Font scale 200% no clipping
- [ ] Reduced motion honored
