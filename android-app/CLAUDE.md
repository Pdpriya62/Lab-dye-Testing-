# LabDip Monitor: Claude instructions

Android app that subscribes to the ESP32 lab-dip rig over MQTT and shows colour, ΔE, PASS/FAIL, bath temperature and ambient readings. Firmware lives in `../`.

## Read first
`prd.md` (requirements), `architecture.md` (stack, packages), `frontend.md` (UI tokens), `security.md` (validation rules), `database.md` (schema), `progress.md` (current state). If code and docs disagree, ask before changing either.

## Stack
- Kotlin 2.x, Jetpack Compose + Material 3, Navigation Compose
- minSdk 26, targetSdk/compileSdk latest stable
- MVVM: ViewModel + `StateFlow` + coroutines; manual DI via `AppContainer` (no Hilt)
- MQTT: `com.hivemq:hivemq-mqtt-client` (never Paho Android Service)
- JSON: `kotlinx.serialization` (no Gson/Moshi)
- Room + KSP (no kapt); DataStore Preferences; Vico charts
- Gradle Kotlin DSL + `gradle/libs.versions.toml`; add every dependency through the version catalog
- Package: `com.team11.labdip`, single `app` module

## Architecture rules
- Data flow: MQTT bytes -> `ReadingParser` -> Room -> ViewModel -> Compose. UI never reads MQTT directly; Room is the single source of truth.
- Layers: `data/{mqtt,model,db,settings}`, `ui/{live,trend,samples,settings,theme}`. Composables don't touch repositories; ViewModels don't import Compose UI.
- Screens take state + lambdas (stateless); one `*ViewModel` per screen exposes a single `StateFlow<UiState>`.
- No new dependency without a one-line reason in `architecture.md`.

## UI rules (from `frontend.md`)
- Colours only from `ui/theme` tokens; chrome is pure grey (R=G=B). Dynamic colour off.
- Font: Archivo; tabular figures for every number; units next to values.
- Sentence case; no all-caps labels.
- Status always word + icon, never colour alone. Touch targets ≥ 48 dp. Content descriptions on swatch and icons.
- Every screen ships a `@Preview` for light and dark.

## Security rules (from `security.md`)
- Treat every MQTT payload as untrusted: size cap, strict decode, range checks, string whitelist. Bad message = drop + count, never crash.
- No credentials, keys or broker passwords in code, `BuildConfig`, `strings.xml` or git. Password encrypted via Android Keystore.
- Release: R8 on, cleartext off, `allowBackup="false"`, no payload/credential logging.
- Never add a trust-all `TrustManager` or disable cert checks.

## Testing
- Unit tests for `ReadingParser` (valid, missing field, out of range, NaN, oversize) and any status/threshold logic.
- Flow tests with Turbine; DAO tests with in-memory Room.
- Run `./gradlew testDebugUnitTest lint` before calling a task done; report failures as-is.

## Workflow
- After every completed task, add a dated entry at the top of `progress.md` and tick items in "Next".
- Update the matching doc when a decision changes (stack, schema, flow, security).
- Write `explanation.md` only when the project is done.
- Don't commit or push unless asked.
