# REWIRE mark

**Pause → Turn.** Two pause bars; the second turns 45°. Friction, then a deliberate choice.

| File | Use |
|---|---|
| `rewire-symbol.svg` | Primary, on light surfaces |
| `rewire-symbol-reversed.svg` | App-icon tile / dark surfaces |
| `rewire-symbol-mono.svg` | One colour (recolour freely) |
| `concepts/` | Exploration history (A/B/C) |

## Construction (256 grid)
- Stroke 34, round caps + joins (32 on the reversed tile to offset irradiation).
- Left bar `M72 200 V56`. Right bar `M136 200 V112 l48 -48` (exact 45°).
- Splash starts the right bar straight (`L136 56`, a pure pause ‖) and morphs to the turn.

## Colour
| Role | Light | Dark | Notes |
|---|---|---|---|
| Pause bar | `#006A60` primary | `#82D5C8` primary | 6.2:1 / 10.8:1 on canvas |
| Turn bar | `#B86A1B` brand amber | `#FDB876` tertiary | 3.9:1 / 10.8:1 on canvas |
| Tile | — | `#005048` | Launcher background, mint `#9EF2E4` + amber bars |

## Rules
- Clear space: one stroke width (34) on every side. Minimum size 16 px.
- Don't rotate, mirror, recolour the bars to the same hue in colour versions, add shadows, or set the bars apart further.
- Wordmark: `REWIRE` in the app's type scale (bold, wide tracking), never outlined from a different font.
- Trademark clearance not checked. Run a search before public launch.
