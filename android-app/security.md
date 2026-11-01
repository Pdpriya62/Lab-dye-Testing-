# Security

## Threat summary
| Threat | Where | Mitigation |
|---|---|---|
| Anyone reads or injects readings | public `broker.hivemq.com` topic | demo only; real trials use a private broker with TLS, auth and ACLs |
| Malformed or hostile payload crashes the app or pollutes history | MQTT input | parser validation (section 1) |
| Anyone sends commands to the rig (changes target, sample ID, bath setpoint) | public `.../cmd` topic | accepted for the demo; firmware range-checks and whitelists every command (section 5); real trials need a private broker with auth and ACLs |
| Fake readings mixed into history | any publisher on the data topic | `tools/fake_rig.py` defaults to `rvu/team11/labdip/fake`; Live flags "No new data" when the rig goes quiet |
| Broker password leaks | device storage, source control | Keystore-backed encryption; never in the repo |
| Wi-Fi credentials committed to GitHub | `lab_dip_esp32/lab_dip_esp32.ino:35-36` | move to an untracked `secrets.h` |
| Tampered or repackaged APK | Play Store | R8 + Play automatic protection (pairip) |

## 1. Input sanitization (every MQTT payload is untrusted)
All checks live in `ReadingParser`. An invalid message is dropped and counted; it never throws into the UI.

- **Size cap**: reject payloads over 2 KB before decoding (firmware max is 512 B).
- **Strict decode**: `Json { ignoreUnknownKeys = true; isLenient = false }`. A missing required field means reject.
- **Range checks**, reject when outside:
  - `R`, `G`, `B`: 0–255. `L`: 0–100. `a`, `b`: −128 to 128. `deltaE`: 0–300.
  - `ambient_temp`: −20 to 80 °C. `humidity`: 0–100 %. `light_lux`: 0–100 000.
  - `bath_temp`: −10 to 130 °C. `setpoint_c`: 0–130 °C. `dye_conc`: 0–100.
  - NaN or Infinity: reject.
- **Strings** (`sample_id`, `fabric`, `dye`): trim, max 32 chars, allow only `[A-Za-z0-9 _.-]`; otherwise store `"?"`. Render as plain Compose `Text`, never in a WebView.
- **Timestamp**: parse ISO-8601; on failure keep the raw text but order by `received_at`.
- **CSV export**: prefix any cell starting with `=`, `+`, `-` or `@` with `'` to block formula injection in Excel.
- **Rate limit**: process at most 10 messages/s and drop the rest, so a flooding publisher cannot bloat the DB.
- **Rig replies** (`.../ack`, `RigCommands.cleanReply`): max 128 bytes, printable ASCII only, otherwise dropped. Shown as plain text.
- **Outgoing commands** (`RigCommands`): built only from validated fields, same limits as the firmware (text `[A-Za-z0-9 _.-]{1,24}`, L 0–100, a/b −128 to 128, bath 0–130 °C, conc 0–100 %). The topic field rejects `+` and `#`.

## 2. Backend API validation
v1 has no backend, so there is no API to validate. The MQTT broker is the only server:
- Use TLS (port 8883) and verify the server certificate with the default trust store. Never install a trust-all `TrustManager`.
- Per-device credentials. Broker ACL: the ESP32 may only **publish** to its topic; the app user may only **subscribe**.
- Use a non-guessable topic name.

If a backend is added later, validate every request server-side with the rules in section 1. App-side checks are for UX only.

## 3. On-device checks
- **Credentials**: broker password encrypted with an Android Keystore key (Tink / Jetpack Security). Host and username in DataStore. Nothing in `BuildConfig` or `strings.xml`.
- **Network**: `network_security_config.xml` sets `cleartextTrafficPermitted="false"`, but that covers HTTP stacks only, not raw MQTT sockets. `MqttRepository` therefore refuses non-TLS brokers itself when `BuildConfig.DEBUG` is false. Plain port 1883 works only in debug builds.
- **Parser note**: kotlinx.serialization accepts quoted numbers (`"R":"182"`) even with `isLenient = false`. Range checks still apply, so this is harmless.
- **Backups**: `android:allowBackup="false"` plus `data_extraction_rules.xml` excluding everything from cloud backup and device transfer (Android 12+ ignores `allowBackup` for device transfer).
- **Logging**: no payloads or credentials in release logs (debug-only logging tree).
- **Components**: only `MainActivity` exported. CSV sharing goes through a `FileProvider` with `grantUriPermissions`; no world-readable files.
- **Permissions**: `INTERNET` and `VIBRATE` only.
- **Root / debugger detection**: not needed at this risk level.

## 4. Play Store hardening (pairip)
- **R8**: `isMinifyEnabled = true` and `isShrinkResources = true` for release.
- **Play App Signing**: enroll. Keep the upload key and `keystore.properties` out of git.
- **Automatic protection (pairip)**: Play Console, *App integrity*, *Automatic protection*. Google adds integrity checks (the `pairip` library) that stop sideloaded or repackaged copies from running. It applies only to Play-distributed builds, so test on an internal testing track; local debug builds won't include it.
- **Play Integrity API**: add only if a backend is introduced, and verify its tokens server-side.

## 5. Firmware notes (ESP32, outside the app)
- Move `WIFI_SSID`, `WIFI_PASS` and broker credentials into `secrets.h`, listed in `.gitignore`.
- Use `WiFiClientSecure` with the broker's CA certificate for TLS.
- Serial and MQTT commands share `runCommand()`. MQTT input: 64-byte cap, printable ASCII only, topic must equal `MQTT_CMD_TOPIC`. Every command checks its argument: text whitelist `[A-Za-z0-9 _.-]`, 1–24 chars; `TARGET` needs exactly 3 numbers in range; `BATH` 0–130; `CONC` 0–100; `CAPTURE` needs a real colour reading. Bad input gets `ERR ...` and changes nothing.
- Status topic (`.../status`): the app accepts only the exact words `online` and `offline`; anything else is ignored. A spoofed `online` cannot fake readings, and the app still marks the rig quiet when no fresh data arrives.
- The firmware publishes only when the TCS34725 returns light, and leaves out ambient, humidity and lux when their sensors fail, so no stale or zero values reach the app.
