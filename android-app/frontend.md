# Frontend Design Guidelines

## Subject and primary job
The app is a pocket version of a **colour-matching viewing booth**. Its one job: tell a dyer at a glance whether this dip matches the target shade, and whether the bath is in range.

## Design idea
Dye labs judge shades in a booth with **neutral grey walls** (ISO 3664, Munsell N-greys) so nothing tints the eye. The UI follows the same rule: all chrome is strictly achromatic. **The only chroma on screen comes from the measured fabric colour and from status.** The swatch is the hero; everything else stays quiet.

Second borrowed object: the **lab-dip card**, where target and sample are mounted side by side. The Live screen shows a split swatch, target half and measured half, with ΔE set across the seam.

## Colour tokens
All chrome tokens are pure greys (R = G = B).

| Token | Light | Dark | Use |
|---|---|---|---|
| `booth` | `#BDBDBD` (≈ N7.5) | `#3B3B3B` (≈ N2.5) | screen background |
| `panel` | `#D4D4D4` | `#4A4A4A` | grouped readings |
| `ink` | `#000000` | `#FFFFFF` | primary text |
| `inkSoft` | `#2E2E2E` | `#D0D0D0` | secondary text (≥ 4.5:1 on `booth`) |
| `rule` | `#9E9E9E` | `#5C5C5C` | hairline dividers |
| `pass` | `#1F6B3A` | `#7CCB95` | PASS text/icon |
| `marginal` | `#7A5A00` | `#E3C15A` | marginal text/icon |
| `fail` | `#9C1C1C` | `#F08A8A` | FAIL, bath alert |

- Status colours appear only as text/icon colour and in the alert banner, never as background washes.
- Every status has a word and an icon (check / tilde / cross), so it reads without colour.
- Turn off Material You dynamic colour: a wallpaper tint would break the neutral-grey rule.

## Typography
One family: **Archivo** (Google Fonts, variable width + weight). Condensed widths suit dense instrument readouts; normal width reads well as body text.

| Role | Style | Size / line |
|---|---|---|
| ΔE readout | width 75, weight 700, tabular figures | 64 / 64 sp |
| Status word | width 100, weight 700 | 22 / 28 sp |
| Reading value | width 87, weight 600, tabular figures | 20 / 24 sp |
| Reading label | weight 400 | 13 / 18 sp |
| Body | weight 400 | 15 / 22 sp |

- Sentence case everywhere. No all-caps labels.
- Units sit next to values: "61.8 °C", "412 lx".
- Tabular figures on every number, so values don't jitter as they update.

## Live screen layout
```
+--------------------------------+
| S001  cotton, reactive red   [gear]   <- top bar; connection dot + "2 s ago"
+--------------------------------+
| +------------+---------------+ |
| |  target    |   measured    | |  split swatch, ~45% of screen height
| |            |               | |
| |         ΔE 1.42            | |  ΔE on the seam; ink picked by swatch luminance
| +------------+---------------+ |
| ~ Marginal   (pass at 1.0 or less)
+--------------------------------+
| Bath       61.8 °C   set 60 ±2 |  row turns `fail` colour + banner on alert
| Lab        52.3  10.1  -4.2    |
| Ambient    27.4 °C   58 %      |
| Light      412 lx              |
+--------------------------------+
```
- Labels left-aligned; values right-aligned in one column.
- Readings are rows inside one `panel`, split by `rule` hairlines, not separate cards.
- Corner radius by role: swatch 4 dp (it is a fabric card), panel 12 dp, buttons full pill.
- No elevation on content. Only the alert banner and dialogs are elevated.
- If no target is set, the swatch is full-width measured colour and ΔE sits at its bottom edge.

## Charts (Vico)
- Data lines in `ink`; threshold and setpoint lines in `inkSoft`, dashed; tolerance band in `rule` at 30% opacity.
- Readings that FAIL get a `fail` dot. No gradient fills under lines.
- Axis titles include units.

## Motion
- One deliberate motion: when a new reading arrives, the measured half of the swatch crossfades to the new colour (300 ms). The ΔE number updates without animation.
- Alert banner slides down from the top (200 ms).
- Respect the system "Remove animations" setting.

## Copy
- Errors state what happened and what to do: "Can't reach broker.hivemq.com. Check Wi-Fi or broker settings."
- Empty states invite action: "No samples yet. Readings appear here once the rig publishes."
- One verb per action across a flow: "Connect" button, "Connected" status.

## Accessibility
- Touch targets ≥ 48 dp.
- TalkBack: swatch announces "Measured colour red 182, green 40, blue 52. Delta E 1.42, marginal."
- Supports font scale up to 200%; Live scrolls rather than clipping.
