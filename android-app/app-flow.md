# App Flow

## Screens
1. **Live** (start destination)
2. **Trend**
3. **Samples** (history list), then **Sample detail**
4. **Settings** (broker + thresholds)

Bottom navigation: Live, Trend, Samples, Settings. First launch shows a 3-page onboarding (swipe or Next; Skip) before Connect.

## First launch
```
Launch
  broker settings saved?
    no  -> Onboarding -> Connect (Settings, prefilled: broker.hivemq.com, 1883, rvu/team11/labdip/data)
             tap "Connect" -> Live
    yes -> Live (auto-connect)
```

## Live screen
- Hero gauge card: five round metric buttons (ΔE, bath, ambient, humidity, light) switch what the tick gauge shows. Tapping a reading card below does the same.
- Split swatch (target | measured) on neutral grey, then reading cards and the Lab card.

## Live screen states
```
Connecting -> Waiting for first reading -> Showing reading
                                               |
Reconnecting (last reading greyed,  <----------+ connection lost
"last reading N s ago")  -> back to Showing reading on reconnect
```
- **Waiting**: "Connected. Waiting for the rig to publish on <topic>."
- **Offline**: "Can't reach <host>. Check Wi-Fi or broker settings." with an "Open settings" button.
- **Bath alert**: banner pinned above content; one vibration when the alert starts; clears when `bath_alert` goes false.
- **New sample ID arrives**: Live switches to it; Trend resets to that sample.

## Trend
- Stacked chart cards: ΔE (threshold lines, FAIL dots), bath temperature (setpoint ± tolerance band), ambient, humidity, light (normal band). Tap or drag a chart to read a point.
- Range tabs: last 20 / last 100 / all readings for this sample.

## Samples
- List rows: sample ID, fabric/dye, last ΔE, status, time.
- Tap a row: Sample detail with the final reading, both trend charts and "Export CSV" (Android share sheet).
- Swipe to delete, with an undo snackbar.

## Settings
- Broker: host, port, TLS toggle, username, password, topic. "Test connection" button.
- Target shade: optional L, a, b (same values as the `TARGET` serial command) for the target swatch.
- Thresholds: ΔE pass, ΔE marginal, lux normal range.
- "Save and reconnect" applies changes.
