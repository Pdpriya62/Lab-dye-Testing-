# Progress

Update after every completed prompt or task. Newest first.

## 2026-10-29 (build)
- Built the app in this folder: `com.team11.labdip`, minSdk 26, full stack in `libs.versions.toml`, R8 + resource shrinking on release.
- Data: `ReadingParser` (size cap, strict decode, range checks, text whitelist), Room (`readings`, schema exported to `app/schemas/`), `ReadingRepository` (10 msg/s cap, drop counter), `MqttRepository` (HiveMQ, MQTT 3.1.1, auto-reconnect 1–30 s, TLS required in release), `SettingsRepository` (DataStore + Tink/Keystore-encrypted password).
- UI in the mock-up style: Onboarding (3 pages), Connect, Live (metric gauge, split swatch, reading cards, bath alert banner + one vibration), Trend (5 interactive charts), Samples (swipe delete + undo), Sample detail (charts, CSV export via share sheet), Settings (validation, test connection).
- Firmware (`lab_dip_esp32.ino`): `mqtt.setBufferSize(512)` (the 256 B default silently dropped full payloads); publishes `target_L/a/b` + `target_set`; `TARGET` checks it parsed 3 numbers.
- `tools/fake_rig.py`: publishes firmware-shaped JSON with bath excursions, sensor dropouts and ~5% malformed messages.
- Tests: 27 JVM unit tests (parser, status, CSV, colour maths, settings validation, repository with Turbine) and 3 in-memory Room DAO tests on the Samsung A26 (SM-A266B, Android 16). All pass. `lintDebug`: 0 errors.

## 2026-10-29
- Added `CLAUDE.md`: stack, architecture, UI, security, testing and workflow rules for every Claude session.
- Created `LabDipMonitorApp/` with planning docs: `prd.md`, `app-flow.md`, `frontend.md`, `architecture.md`, `database.md`, `security.md`, `readme.md`, `explanation.md` (stub), `progress.md`.
- Hardware check: ESP32 detected on `COM9` (CP210x), but it runs ESP-AT firmware, not `lab_dip_esp32.ino`. Sensors not verified yet.

## Next
- [ ] Flash `lab_dip_esp32.ino` (set Wi-Fi credentials); confirm sensors and JSON on serial at 115200.
- [x] Create the Android Studio project (Empty Activity, Compose, package `com.team11.labdip`) in this folder.
- [x] Theme tokens in `ui/theme` from `frontend.md`.
- [x] `ReadingParser` with unit tests for the rules in `security.md`.
- [x] `MqttRepository` (HiveMQ client) and a Live screen showing the latest reading.
- [x] Room, Trend and Samples screens.
- [x] Settings screen with encrypted password.
- [ ] Release build: R8, signing, Play internal track with automatic protection.
- [ ] Write `explanation.md`.
