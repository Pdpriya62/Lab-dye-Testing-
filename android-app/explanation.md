# Explanation

How LabDip Monitor works, end to end, and why it is built this way. State as of 2026-11-01. For requirements see `prd.md`; for the running log see `progress.md`.

## 1. The problem in one paragraph
A lab dip is a small dyed fabric sample compared against a target shade. The ESP32 rig (`../lab_dip_esp32/lab_dip_esp32.ino`) measures the sample's colour, computes how far it is from the target (ΔE), and watches the dye bath and the room. Before this app, results were only on a 128x64 OLED or a serial console. The app puts them on a phone: live colour, ΔE with a pass, marginal or fail verdict, bath temperature with alerts, room conditions, trends and a per-sample history. Since 2026-11-01 it can also send the rig the same commands as the Serial Monitor.

## 2. End-to-end walkthrough

```
Sensors -> ESP32 -> MQTT broker -> phone: parser -> Room DB -> ViewModel -> screen
                         ^                                                    |
                         +---- commands (cmd topic) <- Rig controls screen <--+
```

1. **Sensors** (wired to the ESP32):
   - TCS34725: colour
   - DHT11: room temperature and humidity
   - BH1750: light level
   - DS18B20: bath temperature
2. **ESP32, every 3 s** (`readAll()`, `publishReading()`):
   - reads the sensors and converts raw colour to RGB
   - converts RGB to CIE Lab (sRGB, D65) and computes ΔE (CIE76) against the target
   - builds one JSON message
3. **Only real readings leave the rig:**
   - no light from the colour sensor (missing or unplugged): nothing is published
   - a DHT11 or BH1750 read fails: that field is left out, never replaced with an old value
4. **Broker** `broker.hivemq.com`. Topics all sit under `rvu/team11/labdip/`:

   | Topic | Direction | Content |
   |---|---|---|
   | `data` | rig to app | readings (JSON) |
   | `cmd` | app to rig | text commands, same as serial |
   | `ack` | rig to app | `OK ...` or `ERR ...` replies |
   | `status` | rig to app | retained `online`; the broker publishes `offline` (MQTT last will) if the rig drops |
   | `cam` | laptop to app | camera result per capture from `camera/cam_capture.py --mqtt`: Lab, ΔE2000, levelness grid; shown in the Live screen's camera card, kept in memory only |

5. **Phone, receive** (`MqttRepository`): one HiveMQ MQTT 3.1.1 client per app process. It connects when the app is in the foreground, reconnects with 1–30 s backoff, and sorts each message by topic.
6. **Validate** (`ReadingParser`): every payload is treated as hostile. The rules:
   - size cap 2 KB
   - strict JSON decode
   - range check on every number; NaN and Infinity rejected
   - whitelist on text fields

   A bad message is dropped and counted, never crashes the app. `ReadingRepository` also caps intake at 10 messages per second.
7. **Store** (Room, `readings` table): each valid reading becomes a row. Room is the single source of truth: screens read from the database, never from MQTT. Rotation and process death don't lose anything, and history comes for free.
8. **Show** (ViewModel to Compose): each screen has one ViewModel exposing one `StateFlow<UiState>`. Composables are stateless: state in, lambdas out.
9. **Command** (Rig controls screen): the user fills a field and taps a button.
   - `RigCommands` checks the input and builds the text, for example `TARGET 52.30 10.10 -4.20`.
   - `MqttRepository.send()` publishes it to `cmd`.
   - The ESP32 runs it through the same `runCommand()` as serial and answers on `ack`.
   - The reply appears in the screen's log. With no reply in 5 s, the log says so.

## 3. Colour and ΔE
- **On the ESP32:**
  - Raw TCS34725 counts are divided by the clear channel, scaled to 0–255 and multiplied by per-channel calibration gains (`CAL_R/G/B`).
  - The result is converted sRGB to linear RGB to XYZ (D65) to CIE Lab.
  - ΔE76 = √(ΔL² + Δa² + Δb²) against the target Lab.
- **Target:** set by `CAPTURE` (current reading becomes the target) or `TARGET L a b`. Until then the firmware measures against a 50/0/0 placeholder and sends `target_set: false`; the app then says "No target set" instead of trusting the verdict.
- **Why the app does not recompute ΔE:** the ESP32 owns calibration and the target, so it is the authority. The app range-checks ΔE and Lab and uses Lab only to draw the target swatch (`ColorMath.labToRgb`).
- **Verdict on the phone** (`statusFor`):
  - pass: ΔE at or below the pass limit (default 1.0)
  - marginal: up to the marginal limit (default 2.0)
  - fail: above that

  Limits are set in Settings, so they can change per customer without reflashing.
- **Display rule:** the colour swatch always sits on a neutral N5 grey (`SwatchSurround`, R=G=B), like a viewing booth, so the violet UI never tints the shade being judged. Status is always a word plus an icon, never colour alone.

## 4. Is the rig really there?
Three separate signals, shown in the Live screen's badge:
- **Broker link** (`ConnectionState`): connecting, connected, reconnecting, broker offline.
- **Rig presence** (`rigLink()`), from the retained status and the age of the last reading:
  - **Rig offline** (red): the rig's last will says offline, or nothing arrived for 10 s and no `online` status was received.
  - **No new data** (amber): the rig says online but sends nothing, for example the colour sensor is missing.
  - **Online** (green): a reading in the last 10 s, shown as "N s ago".
- **Old readings:** when the rig is not online, Live dims the last values and says how old they are.

This exists because, on 2026-11-01, readings appeared with the ESP32 unplugged. They came from `tools/fake_rig.py` publishing on the real topic. That tool now defaults to `rvu/team11/labdip/fake`.

## 5. Key files

### Firmware (`../lab_dip_esp32/`)
| File | What it does |
|---|---|
| `lab_dip_esp32.ino` | Sensors, colour maths, OLED, MQTT publish, command handling (serial and MQTT), last will |

### App (`app/src/main/java/com/team11/labdip/`)
| File | What it does |
|---|---|
| `AppContainer.kt` | Manual DI: one DB, one MQTT client, one settings store for the process |
| `MainActivity.kt` | Navigation: intro every launch, then Connect (first run) or Live; bottom tabs |
| `data/mqtt/MqttRepository.kt` | Connect (IPv4 first), subscribe, route by topic, `send()`, `replies`, `rigOnline` |
| `data/mqtt/RigCommands.kt` | Builds and validates commands; cmd, ack and status topic names; checks replies |
| `data/model/ReadingParser.kt` | Untrusted payload to `Reading`, or a counted drop |
| `data/model/RigLink.kt` | Online, quiet or offline decision |
| `data/model/Status.kt`, `ColorMath.kt` | Pass, marginal or fail; Lab to sRGB for the target swatch |
| `data/ReadingRepository.kt` | Parse, rate limit, insert; history queries; delete with undo |
| `data/db/` | Room database, `ReadingEntity`, `ReadingDao`, CSV export |
| `data/settings/SettingsRepository.kt` | DataStore settings; broker password encrypted with Tink and Android Keystore |
| `ui/onboarding/` | 5 animated feature slides |
| `ui/live/` | Gauge, split swatch, reading cards, bath alert banner with one vibration, rig badge |
| `ui/trend/`, `ui/samples/` | Charts drawn on Canvas; sample list (swipe to delete), detail, CSV share |
| `ui/controls/` | Rig controls: capture, target, sample ID, fabric, dye, concentration, bath setpoint, reply log |
| `ui/settings/` | Broker, limits, manual target; test connection |
| `ui/theme/`, `ui/components/` | Violet glass tokens, animated textile backdrop, shared cards, fields, gauge, charts |

### Tools
| File | What it does |
|---|---|
| `tools/fake_rig.py` | Publishes firmware-shaped JSON, including bad messages, for testing without hardware. Default topic `.../fake`; pass `--topic rvu/team11/labdip/data` to feed the app. |

## 6. Design decisions and trade-offs
- **Direct MQTT, no backend:** zero infrastructure and it matches the firmware. Cost: no cloud history, no multi-phone sync, no alerts while the app is closed.
- **Room as the single source of truth:** the UI never touches MQTT, so screens survive rotation, and Trend and Samples are plain queries.
- **Public broker:** fine for a class demo, free and no setup. Cost: anyone who knows the topics can read readings or send commands. Mitigations:
  - both sides validate everything
  - the firmware range-checks and whitelists every command and replies `ERR` without changing state
  - the app accepts only the exact words `online` and `offline` on the status topic

  Real trials need a private broker with login, TLS and ACLs.
- **IPv4 first:** the broker's IPv6 address refused port 1883 on the lab Wi-Fi, so the app resolves the host itself and connects to the IPv4 address. The host name is kept so TLS still checks the certificate.
- **Commands on the same broker:** the ESP32, the app and the Serial Monitor all use one broker and one command handler, so behaviour is identical whichever way a command arrives. App commands are echoed on serial.
- **No libraries for charts or DI:** charts are drawn on Compose `Canvas` and DI is a small `AppContainer`. Fewer dependencies, smaller APK (release is 2.3 MB with R8).
- **Dark violet glass UI:** based on a reference mock-up, with an animated backdrop (spotlight, drifting dye blobs, rippling woven threads). The animation runs in the draw phase only, so screens are not recomposed every frame.
- **Release safety:**
  - R8 and resource shrinking on
  - cleartext traffic off; release builds refuse plain MQTT
  - backups off
  - no payloads or credentials in logs
  - signing keys read from a gitignored `keystore.properties`

## 7. Testing
- **JVM unit tests** (35):
  - parser: valid, missing field, out of range, NaN, oversize
  - status thresholds, CSV escaping, colour maths
  - settings validation
  - repository flows with Turbine
  - command building and reply checks
  - rig presence
- **Room DAO tests** (in-memory) run on the phone.
- **Checks:** run `./gradlew testDebugUnitTest lint` with JDK 17+ before calling a task done.
- **Manual on a Samsung A26:**
  - live data from the real rig
  - Trend and Samples
  - offline badge with the ESP32 unplugged
  - command round trip against a stand-in responder

## 8. Known limitations and next steps
- The reflashed firmware (MQTT commands, last will, real-reading guards) compiles but has not been run on the ESP32 yet. Next: flash it, send each command from Rig controls, compare app values with the serial log, check the bath alert, run a 30 min soak test.
- **Public broker:** move to a private broker with auth before real trials.
- **ΔE formula:** CIE76 only. CIEDE2000 and per-customer limits are open questions.
- **Background:** no alerts while the app is closed; it would need a foreground service.
- **Release:** create a signing key, check every screen on the release build with TLS on, then the Play internal track.
- **Calibration:** `CAL_R/G/B` were set against white paper on 2026-11-12 (`1.0`, `1.585`, `2.708`, TCS gain `1X`). White paper reads neutral grey, L ≈ 55, because the firmware divides by the clear channel. Lab values from before that date used gains of 1.0.
