# Progress

Update after every completed prompt or task. Newest first.

## 2026-11-01 (docs and release)
- Wrote `explanation.md`: end-to-end walkthrough, colour maths, rig presence, key files, decisions, testing, limits.
- Updated `CLAUDE.md`: topics and command rules, rig presence, IPv4, new UI packages, firmware secrets, signing, fake rig topic, firmware compile check, user-only commits, both remotes.
- Committed on `android-app-v1`, merged into `main` (with the teammates' `camera/` commits from `origin/main`), pushed to both remotes.

## 2026-11-01 (checklist pass)
- Wi-Fi credentials moved out of `lab_dip_esp32.ino` into `lab_dip_esp32/secrets.h`, gitignored through a new root `.gitignore`; template `secrets.example.h`. Firmware compiles.
- Release signing: `app/build.gradle.kts` reads `keystore.properties` (gitignored, with `*.jks` and `*.keystore`); without it the release stays unsigned. Steps in `readme.md`. No key was created.
- Release APK with R8: 2.3 MB (limit 15 MB). A debug-signed copy installed and launched without crashing; the screens could not be checked because the phone locked. Debug build reinstalled afterwards.

## 2026-11-01 (rig presence)
- App now says "Rig offline" (red) when the ESP32 isn't there, separate from "Broker offline". Firmware connects with an MQTT last will (retained `offline` on `rvu/team11/labdip/status`), publishes retained `online` on connect, keep-alive 10 s, so the broker flags an unplugged rig in about 15 s. Until the reflash, the app treats no reading for 10 s as offline. Logic in `rigLink()` with `RigLinkTest`.
- Live: red "Rig offline" pill with "Showing the last reading from N min ago"; amber "No new data" only when the rig says online but sends nothing (e.g. colour sensor missing). Rig controls warns when the rig is offline.
- 35 unit tests pass, lint 0 errors, firmware compiles (76% flash). Seen on the phone with the ESP32 unplugged.

## 2026-11-01 (rig commands)
- Readings with the ESP32 unplugged came from two `tools/fake_rig.py` processes publishing on the real topic. Stopped them; `fake_rig.py` now defaults to `rvu/team11/labdip/fake`.
- Firmware: commands now work over MQTT on the same broker. It subscribes to `rvu/team11/labdip/cmd` and replies on `.../ack`. Serial and MQTT share `runCommand()` with range and whitelist checks; app commands also print on serial. It publishes only when the colour sensor returns light, and omits ambient, humidity and lux when those sensors fail. OLED shows "No colour sensor". Compiles: 76% flash. Not flashed yet.
- App: Rig controls screen (tune button on Live) for capture, target Lab, sample ID, fabric, dye, concentration and bath setpoint, with a reply log and a 5 s "no reply" warning. `RigCommands` validates input; `MqttRepository.send()` publishes, `replies` carries checked rig replies. Topic field rejects `+` and `#`.
- Live: the pill turns amber with "No new data · N min ago" when the rig stops sending for more than 10 s.
- Checked on the phone with a stand-in responder on the broker: commands arrive and replies show. Not yet tested against the reflashed ESP32.
- `testDebugUnitTest` 30 tests pass (new `RigCommandsTest`); `lintDebug` 0 errors.

## 2026-11-01 (rig online)
- Real rig verified: ESP32 on COM9 runs `lab_dip_esp32.ino`, prints JSON every 3 s with NTP time, and publishes to `broker.hivemq.com` / `rvu/team11/labdip/data`.
- Fixed "Offline" in the app: the client resolved `broker.hivemq.com` to its IPv6 address, which refused port 1883 (`Connection refused: broker.hivemq.com/[2a01:578:13::348:a03d]:1883`). `MqttRepository.connect` is now `suspend`, resolves on `Dispatchers.IO` and prefers IPv4, keeping the host name for TLS checks.
- Fixed Samples cards: the red swipe-to-delete layer showed through the translucent glass; it now draws only while swiping.
- Seen on the phone: Live, Trend and Samples fill with S001 from the rig. ΔE is 24.85 (fail) because no target is set yet (`target_set:false`).

## 2026-11-01
- Re-skinned the app to the violet glass mock-up (`original-45e9928e108b001110bff9260d116afe.webp`): new palette in `ui/theme/Color.kt` (`Night`, `Dusk`, `Violet`, `Orchid`, `Indigo`, `GlassViolet`, `NavSurface`); cards and buttons now fade from clear glass to violet.
- `Backdrop` is now animated: top-right spotlight, slow-drifting dye blobs and a faint rippling woven-thread texture. It redraws in the draw phase only, so screens do not recompose.
- Onboarding rebuilt as 5 feature slides (colour match, bath alerts, room conditions, trends, sample records): pulsing orb with a spinning dye ring, parallax swipe, animated page dots, glow colour follows the slide.
- The feature intro now plays on every app launch. First run continues to Connect; later runs go straight to Live.
- `testDebugUnitTest` (26 tests) and `lintDebug` pass: 0 errors, 25 warnings.

## 2026-10-30
- Removed Android Studio "new project" template leftovers: `com.example.myapplication` sources and tests, `backup_rules.xml`, `Theme.MyApplication` (light theme had overridden `Theme.LabDip` on `MainActivity`) and a duplicate `include(":app")`.
- Build note: default `JAVA_HOME` is Zulu JDK 11; run Gradle with Android Studio's `jbr` (JDK 17+).

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
Done so far:
- [x] Android project `com.team11.labdip`: minSdk 26, targetSdk 36, version catalog
- [x] Theme tokens, dark only (violet glass since 2026-11-01)
- [x] `ReadingParser`: size cap, strict decode, range checks, string whitelist
- [x] `MqttRepository`: HiveMQ, auto-reconnect 1–30 s, TLS required in release (F1, F9)
- [x] Room `readings` table with exported schema; `ReadingRepository` caps at 10 msg/s and counts drops (F7, F10)
- [x] `SettingsRepository`: DataStore, password encrypted with Tink/Keystore
- [x] Screens: Onboarding, Connect, Live, Trend, Samples, Sample detail, Settings (F2–F6)
- [x] CSV export through the share sheet (F8)
- [x] Release hardening: R8 and resource shrink on, cleartext off, `allowBackup="false"`, data extraction rules
- [x] Firmware: `setBufferSize(512)`, publishes `target_L/a/b` and `target_set`
- [x] `tools/fake_rig.py` test publisher
- [x] JVM unit tests and in-memory Room DAO tests; lint 0 errors
- [x] Template leftovers removed
- [x] UI re-skin to the violet mock-up, animated textile backdrop, 5-slide feature intro on every launch

To do:
- [x] Move the Wi-Fi SSID and password into gitignored `lab_dip_esp32/secrets.h`
- [x] Flash `lab_dip_esp32.ino` to the ESP32 on COM9
- [x] Confirm sensors and JSON on serial at 115200
- [ ] Reflash `lab_dip_esp32.ino` with MQTT commands, then send each command from Rig controls and check the reply
- [ ] Set a target (`TARGET` or `CAPTURE`, now possible from the app) so ΔE is measured against a real shade
- [ ] Private broker with auth before real trials (public broker lets anyone send commands)
- [ ] End-to-end test with the real rig: app values match `Serial.println(out)` value for value
- [ ] Bath alert appears within 1 reading of the firmware raising it
- [ ] Soak test: Live screen 30 min with no crash and no memory growth
- [ ] Verify the F5 lux out-of-range warning on a device
- [x] Release signing: `signingConfig` from gitignored `keystore.properties`
- [ ] Create the release key (`keytool`, see `readme.md`) and keep a backup
- [x] Release APK under 15 MB (2.3 MB)
- [ ] Check every screen on the R8 release build (TLS on), then Play internal track with automatic protection
- [ ] Decide open PRD questions: ΔE threshold per customer (CIE76 or CIEDE2000); private broker instead of `broker.hivemq.com`
- [x] Write `explanation.md` (keep it current from now on)
- [x] Commit and merge `android-app-v1`; push to `origin` and `mine`
