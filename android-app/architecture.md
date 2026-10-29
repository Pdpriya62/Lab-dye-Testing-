# Architecture

## System context
```
[ESP32 + sensors] --MQTT publish, JSON every 3 s--> [MQTT broker] --subscribe--> [Android app]
 TCS34725, BH1750, DHT11,                            broker.hivemq.com (demo)     LabDip Monitor
 DS18B20, SSD1306 OLED                               HiveMQ Cloud + TLS (trials)
                                                        |
                                                        +--> logger.py / compare_live.py (existing, unchanged)
```
The app is a pure subscriber. There is no backend of our own.

## Tech stack
| Layer | Choice | Why |
|---|---|---|
| Language | Kotlin 2.x | Android default |
| UI | Jetpack Compose + Material 3 | declarative; Android Studio template |
| Navigation | Navigation Compose | 4 screens |
| State | ViewModel + `StateFlow` + coroutines | lifecycle-safe live updates |
| MQTT | `com.hivemq:hivemq-mqtt-client` (MQTT 3.1.1 / 5, TLS) | maintained; Paho Android Service is unmaintained and breaks on Android 12+ |
| JSON | `kotlinx.serialization` | compile-time, no reflection |
| Local DB | Room + KSP | per-sample history, Flow queries |
| Settings | DataStore (Preferences) | broker config, thresholds, target Lab |
| Secrets | Android Keystore key (Tink / Jetpack Security) to encrypt broker password | no plaintext credentials |
| Charts | Compose `Canvas` (`ui/components/LineChart.kt`) | the mock-up's chart style needs custom drawing anyway; one less dependency |
| DI | Manual `AppContainer` | app too small for Hilt |
| Build | Gradle Kotlin DSL + version catalog; R8 on release | |
| Tests | JUnit, Turbine (Flow), Compose UI test | |
| SDK | min 26, target latest stable | |

## Package layout (single module)
```
app/src/main/java/com/team11/labdip/
  data/
    mqtt/      MqttRepository      connect, subscribe, reconnect; emits Flow<ByteArray>
    model/     Reading, ReadingParser, Status, ColorMath   model, validation, PASS/FAIL, Lab to sRGB
    ReadingRepository.kt   ingest (parse, rate limit, drop count) + DAO pass-through
    db/        AppDatabase, ReadingEntity, ReadingDao
    settings/  SettingsRepository  DataStore + encrypted password
  ui/
    live/      LiveScreen, LiveViewModel
    trend/     TrendScreen, TrendViewModel
    samples/   SamplesScreen, SampleDetailScreen, SamplesViewModel
    settings/  SettingsScreen, SettingsViewModel
    onboarding/ OnboardingScreen   3-page intro, then Connect (Settings in first-run mode)
    components/ Glass, Gauge, Swatch, LineChart   shared UI pieces
    theme/     Color.kt, Type.kt, Theme.kt   tokens from frontend.md
  AppContainer.kt, LabDipApp.kt, MainActivity.kt
```

## Data flow
```
MQTT message bytes
  -> ReadingParser.parse()   size cap, JSON decode, range checks  -> Result<Reading>
  -> ReadingDao.insert()     valid only; invalid increments a counter
  -> Room Flow               -> ViewModels -> Compose UI
```
Room is the single source of truth. The UI reads from the DB, not straight from MQTT. The Live screen shows the latest row.

## Connection lifecycle
- `AppContainer` (application scope) owns `MqttRepository`.
- Connects when the app comes to foreground (`ProcessLifecycleOwner`); disconnects in background.
- HiveMQ client automatic reconnect, backoff 1 s to 30 s.
- No foreground service in v1, so no alerts while the app is closed.

## Key decisions
- **Direct MQTT, no backend**: zero infrastructure; matches the existing firmware as-is.
- **DB as source of truth**: state survives rotation and process death; history comes free.
- **Target swatch**: firmware publishes `target_L/a/b` and `target_set` since 2026-10-29. The app draws the firmware target when `target_set` is true, else the manual target from Settings, else no target half.
- **Native only**: React/Node was considered on 2026-10-29 and dropped; the app stays Kotlin.
- **Status thresholds in the app**: the firmware sends ΔE only; PASS / marginal / FAIL is decided on the phone with configurable limits.
