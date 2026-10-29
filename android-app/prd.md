# Product Requirements Document: LabDip Monitor (Android)

## 1. Problem
Lab-dip testing compares a dyed fabric sample against a target shade. The ESP32 rig (`../lab_dip_esp32/lab_dip_esp32.ino`) measures colour, computes ΔE (CIE76) and monitors dye-bath conditions, but its results are only visible on a 128x64 OLED or a serial console. Lab staff need to read results and get bath-temperature alerts on a phone.

## 2. Users
- **Lab technician**: runs the dip, watches ΔE and bath temperature live, records PASS/FAIL.
- **Supervisor / reviewer**: looks back at a sample's readings and trend.

## 3. Goals
- Show each new reading within 1 s of the ESP32 publishing it.
- Make PASS/FAIL readable at arm's length.
- Alert when the bath temperature leaves setpoint ± tolerance.
- Keep a local history per sample ID.

## 4. Non-goals (v1)
- Sending commands to the ESP32 (ID, CAPTURE, TARGET, BATH). Done over serial for now.
- Cloud backend, user accounts, multi-device sync.
- Running the Python ML model (`compare_live.py`) on the phone.
- Background alerts while the app is closed.
- iOS.

## 5. Data source
MQTT, topic `rvu/team11/labdip/data` (configurable), JSON payload every ~3 s:

| Field | Type | Unit / notes |
|---|---|---|
| `sample_id` | string | e.g. `S001` |
| `timestamp` | string | ISO-8601 local (IST); `1970-01-01T00:00:00` if NTP not synced |
| `deltaE` | float | CIE76 vs target |
| `R`, `G`, `B` | int | 0–255, calibrated |
| `L`, `a`, `b` | float | CIE Lab |
| `ambient_temp` | float | °C (DHT11) |
| `humidity` | float | %RH (DHT11) |
| `light_lux` | int | lx (BH1750) |
| `bath_temp` | float, optional | °C (DS18B20); absent when sensor disconnected |
| `bath_alert` | bool | firmware-computed |
| `setpoint_c` | float | bath setpoint °C |
| `fabric`, `dye` | string, optional | metadata |
| `dye_conc` | float, optional | % owf |

## 6. Functional requirements
| ID | Requirement | Priority |
|---|---|---|
| F1 | Connect to a configurable MQTT broker (host, port, TLS on/off, username, password, topic). | Must |
| F2 | Show the latest reading: colour swatch from RGB, ΔE, PASS/FAIL, Lab values. | Must |
| F3 | Configurable PASS/FAIL thresholds; default ΔE ≤ 1.0 pass, 1.0–2.0 marginal, > 2.0 fail. | Must |
| F4 | Show bath temperature vs setpoint; banner + vibration when `bath_alert` is true. Show "Bath sensor disconnected" when `bath_temp` is absent. | Must |
| F5 | Show ambient temperature, humidity, light level. Warn when lux is outside a configurable normal range. | Should |
| F6 | Trend screen: ΔE and bath temperature over the last N readings (default 100) for the current sample. | Must |
| F7 | Store readings locally (Room); list past samples; open a sample to view its history. | Should |
| F8 | Export a sample's readings as CSV (same columns as `sensor_readings.csv`). | Could |
| F9 | Connection status always visible: connected / reconnecting / offline, plus "last reading N s ago". | Must |
| F10 | Reject and count malformed messages; never crash on bad input. | Must |

## 7. Non-functional requirements
- Android 8.0 (API 26)+; target latest stable SDK.
- Auto-reconnect with backoff; survive screen rotation without dropping the connection.
- Light and dark theme; text contrast ≥ 4.5:1; PASS/FAIL never conveyed by colour alone.
- No credentials in source control.
- Release APK < 15 MB.

## 8. Success criteria
- Live screen runs 30 min continuously with no crash or memory growth.
- Values in the app match the serial log (`Serial.println(out)`) value-for-value.
- Bath alert appears within 1 reading of the firmware raising it.

## 9. Open questions
- Final ΔE pass threshold per customer; CIE76 now, CIEDE2000 later?
- Private broker for real trials (HiveMQ Cloud free tier assumed).
- Firmware should publish target Lab (`target_L/a/b`) so the app can draw the target swatch without manual entry.
