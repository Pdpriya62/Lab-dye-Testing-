# LabDip Monitor

Android app that shows live results from the ESP32 textile lab-dip rig: measured colour, ΔE against the target shade with PASS / marginal / FAIL, dye-bath temperature alerts, and ambient conditions. Part of the *Multi-Sensor IoT Framework for Textile Lab-Dip Testing* (Team 11).

> Status: v1 built and installed on a Samsung A26; tested against `tools/fake_rig.py`. See [progress.md](progress.md).

## How it works
```
ESP32 (TCS34725, BH1750, DHT11, DS18B20) --MQTT JSON--> broker --> this app
```
The app subscribes to the topic the firmware publishes (`rvu/team11/labdip/data` by default). No backend needed.

## Features
- Live split swatch (target vs measured) with ΔE and status
- Bath temperature vs setpoint, with alert banner
- Ambient temperature, humidity, light level
- Trend charts per sample
- Local history and CSV export

## Tech stack
Kotlin, Jetpack Compose (Material 3), HiveMQ MQTT client, kotlinx.serialization, Room, DataStore, Tink, Canvas-drawn charts. Details in [architecture.md](architecture.md).

## Getting started
1. Copy `../lab_dip_esp32/secrets.example.h` to `secrets.h` (gitignored), fill in your Wi-Fi name and password, then flash `../lab_dip_esp32/lab_dip_esp32.ino`. No rig? Run `python tools/fake_rig.py --topic rvu/team11/labdip/data` from this folder. Without `--topic` it publishes to `rvu/team11/labdip/fake`, so it never mixes with the real rig.
2. Open this `android-app/` folder in Android Studio and let Gradle sync.
3. Run on a device or emulator with API 26+.
4. In Settings, enter broker host, port and topic (defaults match the firmware), then tap **Connect**.

## Release build
1. Create a signing key once and keep it safe; losing it means you can't update the app on Play:
   `keytool -genkeypair -v -keystore labdip-release.jks -alias labdip -keyalg RSA -keysize 2048 -validity 10000`
2. Create `keystore.properties` next to `settings.gradle.kts` (gitignored):
   ```
   storeFile=labdip-release.jks
   storePassword=...
   keyAlias=labdip
   keyPassword=...
   ```
3. `./gradlew assembleRelease` (or `bundleRelease` for Play). Without `keystore.properties` the release APK is built unsigned.
4. Release builds refuse plain MQTT: turn on TLS in Settings (port 8883).

## Docs
| File | Contents |
|---|---|
| [prd.md](prd.md) | requirements |
| [app-flow.md](app-flow.md) | screens and navigation |
| [frontend.md](frontend.md) | UI design guidelines |
| [architecture.md](architecture.md) | tech stack and structure |
| [database.md](database.md) | Room schema |
| [security.md](security.md) | validation, storage, Play hardening |
| [progress.md](progress.md) | status log |
| [explanation.md](explanation.md) | walkthrough of the finished project |

## Security note
The default public broker is for demos only: anyone can read or publish on it. Use a private TLS broker for real trials (see [security.md](security.md)).
