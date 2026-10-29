# Progress

Update after every completed prompt or task. Newest first.

## 2026-10-29
- Pushed docs to GitHub branch `android-app` (folder `android-app/`); firmware moved to `lab_dip_esp32/` so Arduino IDE can open it.
- Added `CLAUDE.md`: stack, architecture, UI, security, testing and workflow rules for every Claude session.
- Created `LabDipMonitorApp/` with planning docs: `prd.md`, `app-flow.md`, `frontend.md`, `architecture.md`, `database.md`, `security.md`, `readme.md`, `explanation.md` (stub), `progress.md`.
- Hardware check: ESP32 detected on `COM9` (CP210x), but it runs ESP-AT firmware, not `lab_dip_esp32.ino`. Sensors not verified yet.

## Next
- [ ] Flash `lab_dip_esp32.ino` (set Wi-Fi credentials); confirm sensors and JSON on serial at 115200.
- [ ] Create the Android Studio project (Empty Activity, Compose, package `com.team11.labdip`) in this folder.
- [ ] Theme tokens in `ui/theme` from `frontend.md`.
- [ ] `ReadingParser` with unit tests for the rules in `security.md`.
- [ ] `MqttRepository` (HiveMQ client) and a Live screen showing the latest reading.
- [ ] Room, Trend and Samples screens.
- [ ] Settings screen with encrypted password.
- [ ] Release build: R8, signing, Play internal track with automatic protection.
- [ ] Write `explanation.md`.
