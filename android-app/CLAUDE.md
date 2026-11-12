# LabDip Monitor: Claude instructions

Android app that subscribes to the ESP32 lab-dip rig over MQTT and shows colour, ΔE, PASS/FAIL, bath temperature and ambient readings, and sends the rig the same commands as the Serial Monitor. Firmware lives in `../lab_dip_esp32/`. This folder is the Gradle project (`app/`) and holds these docs.

## Read first
`prd.md` (requirements), `architecture.md` (stack, packages), `frontend.md` (UI tokens), `security.md` (validation rules), `database.md` (schema), `progress.md` (current state), `explanation.md` (how it all works, end to end). If code and docs disagree, ask before changing either.

## Stack
- Kotlin 2.x, Jetpack Compose + Material 3, Navigation Compose
- minSdk 26, targetSdk/compileSdk latest stable
- MVVM: ViewModel + `StateFlow` + coroutines; manual DI via `AppContainer` (no Hilt)
- MQTT: `com.hivemq:hivemq-mqtt-client` (never Paho Android Service)
- JSON: `kotlinx.serialization` (no Gson/Moshi)
- Room + KSP (no kapt); DataStore Preferences; Tink; charts drawn on Compose `Canvas` (no chart library)
- Gradle Kotlin DSL + `gradle/libs.versions.toml`; add every dependency through the version catalog
- Package: `com.team11.labdip`, single `app` module
- Build with JDK 17+ (Android Studio's bundled JBR works); AGP 8.13 rejects JDK 11

## Architecture rules
- Data flow: MQTT bytes -> `ReadingParser` -> Room -> ViewModel -> Compose. UI never reads MQTT directly; Room is the single source of truth.
- Topics sit next to the data topic (`rvu/team11/labdip/`): `data` (readings), `cmd` (app to rig), `ack` (rig replies), `status` (retained `online`, last-will `offline`). Derive them with the helpers in `RigCommands.kt`; never hard-code them in the app.
- Commands: `RigCommands` validates and builds them, `MqttRepository.send()` publishes; they are not stored in Room. Keep app limits identical to `runCommand()` in the firmware; change both together.
- Rig presence comes from `rigLink()` (status topic + reading age), separate from the broker `ConnectionState`. Never show a green state for old data.
- `MqttRepository` connects to the broker's IPv4 address (its IPv6 refused 1883 on the lab Wi-Fi); keep the host name for TLS.
- Layers: `data/{mqtt,model,db,settings}`, `ui/{onboarding,live,trend,samples,controls,settings,components,theme}`. Composables don't touch repositories; ViewModels don't import Compose UI.
- Screens take state + lambdas (stateless); one `*ViewModel` per screen exposes a single `StateFlow<UiState>`.
- No new dependency without a one-line reason in `architecture.md`.

## UI rules (from `frontend.md`)
- Colours only from `ui/theme` tokens; dark violet glass style from the reference mock-up (`frontend.md`, 2026-11-01) with the animated `Backdrop`; the swatch always sits on the neutral `SwatchSurround` grey. Dynamic colour off.
- Animations read their values in the draw phase (`drawBehind`, `graphicsLayer`) so screens don't recompose every frame.
- The 5-slide feature intro plays on every launch; first run then goes to Connect, later runs to Live.
- Fonts: Archivo (UI, numbers) and Instrument Serif italic (screen titles); tabular figures for every number; units next to values.
- Sentence case; no all-caps labels.
- Status always word + icon, never colour alone. Touch targets ≥ 48 dp. Content descriptions on swatch and icons.
- Every screen ships a `@Preview` (dark only; the app has no light theme).

## Security rules (from `security.md`)
- Treat every MQTT payload as untrusted: size cap, strict decode, range checks, string whitelist. Bad message = drop + count, never crash.
- No credentials, keys or broker passwords in code, `BuildConfig`, `strings.xml` or git. Password encrypted via Android Keystore.
- Release: R8 on, cleartext off, `allowBackup="false"`, no payload/credential logging.
- Never add a trust-all `TrustManager` or disable cert checks.
- Rig replies and status are untrusted too: replies max 128 B printable ASCII; status only the exact words `online` / `offline`.
- Firmware: Wi-Fi credentials are `WIFI_SSID` / `WIFI_PASS` in the gitignored `../lab_dip_esp32/secrets.h` (template `secrets.example.h`). Every command argument is range-checked or whitelisted; publish only real sensor values (no colour reading = no message; failed sensors = field left out).
- Release signing only through gitignored `keystore.properties`; never commit `*.jks` / `*.keystore`.
- `tools/fake_rig.py` defaults to `rvu/team11/labdip/fake`; never point it at the real topic while the rig is running.

## Testing
- Unit tests for `ReadingParser` (valid, missing field, out of range, NaN, oversize), `RigCommands`, `rigLink()` and any status/threshold logic.
- Firmware changes must compile: `arduino-cli compile --fqbn esp32:esp32:esp32 ../lab_dip_esp32` (arduino-cli ships inside Arduino IDE).
- Flow tests with Turbine; DAO tests with in-memory Room.
- Run `./gradlew testDebugUnitTest lint` before calling a task done; report failures as-is.

## Workflow
- After every completed task, add a dated entry at the top of `progress.md` and tick items in "Next".
- Update the matching doc when a decision changes (stack, schema, flow, security).
- Keep `explanation.md` current: update it when behaviour, flow or a key decision changes.
- Don't commit or push unless asked. Commits are authored by the user only: no `Co-Authored-By` or other Claude trailers.
- Remotes: `origin` (team repo, nischitamn-alt) and `mine` (Pdpriya62). Push to both when asked to publish.
