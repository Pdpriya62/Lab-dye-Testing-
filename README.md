# Multi-Sensor-IoT-Framework-for-Textile-Lab-Dip-Testing
An ESP32-based IoT system for textile lab-dip testing that monitors fabric colour, ambient temperature, humidity, lighting, and dye-bath temperature. QR codes link sensor readings to each fabric sample, enabling cloud-based tracking, process alerts, colour deviation analysis, and improved shade approval before bulk dyeing.

## How it works
```
ESP32 + sensors --MQTT JSON every 3 s--> broker --> logger.py / compare_live.py (laptop)
                                                \-> LabDip Monitor Android app (android-app/)
LabDip Monitor app --commands--> broker --> ESP32 (replies on the ack topic)
ESP32-CAM --HTTP JPEG--> camera/cam_capture.py (laptop, OpenCV) --MQTT JSON--> app
```

MQTT topics (default broker `broker.hivemq.com:1883`):
| Topic | Direction | Content |
|---|---|---|
| `rvu/team11/labdip/data` | rig to app/laptop | JSON reading every 3 s |
| `rvu/team11/labdip/cmd` | app to rig | text commands (same as serial) |
| `rvu/team11/labdip/ack` | rig to app | `OK ...` / `ERR ...` replies |
| `rvu/team11/labdip/status` | rig to app | retained `online`; broker sets `offline` (last will) |
| `rvu/team11/labdip/cam` | laptop to app | camera result per capture: Lab, ΔE2000, levelness grid (`cam_capture.py --mqtt`) |

Rig commands (Serial Monitor at 115200 baud, or the app's Controls screen): `ID S001`, `CAPTURE`, `TARGET 52.3 10.1 -4.2`, `BATH 60`, `FABRIC cotton`, `DYE reactive_red`, `CONC 2.0`. Details in [demo.md](demo.md).

## Hardware
| Sensor | Measures | ESP32 pin |
|---|---|---|
| TCS34725 | fabric colour (RGB, converted to CIE Lab) | I2C SDA 21, SCL 22 |
| BH1750 | room (ambient) illuminance (lux), on the breadboard outside the box | I2C SDA 21, SCL 22 |
| SSD1306 OLED | on-device display | I2C SDA 21, SCL 22 (0x3C) |
| DHT11 | ambient temperature, humidity | GPIO 27 |
| DS18B20 | dye-bath temperature | GPIO 4, 4.7k pull-up to 3.3 V |

### Measurement box
A cardboard box, 21 × 16 × 8.5 cm. The 21 × 16 cm side is open and is placed face down on the fabric.
```
 side view (21 cm length)                          top view (21 x 16 cm opening)
 +--------------------------+--------+  -          +--------------------------+--------+
 |  white paper lining       | TCS    |  |          |   camera sees ~11 x 8 cm | TCS    |
 |  [ESP32-CAM] lens down    | box    |  8.5 cm     |        [CAM]             | box    |
 |  [bounce card] under flash| ~5x5cm |  |          |                          | ~5x5cm |
 +==========================+========+  -          +--------------------------+--------+
 ~~~~~~~~~~~~~ fabric swatch ~~~~~~~~~~
 |<--------- ~15 cm -------->|<~6 cm->|
```
- **Camera chamber:** the ESP32-CAM sits in the lid, lens straight down. Its own flash LED is the light source. A small white paper strip under the flash bounces the light off the white-lined ceiling, so the fabric gets soft light without a hot spot. No extra LEDs are used.
- **TCS34725 box:** a small opaque box, open at the bottom, at one end of the big box. The sensor faces down a few millimetres above the fabric and uses its own onboard LED.
- **Light seal:** a foam or felt strip around the bottom rim keeps room light out.
- **Outside the box:** the ESP32, BH1750, DHT11 and OLED stay on the breadboard. The BH1750 therefore measures room light, not the light inside the box.
- **Swatch size:** at least 18 × 10 cm, so the fabric covers both the camera view and the TCS box.

### Calibration (2026-11-12)
Both colour sensors are calibrated against plain white paper.
- **TCS34725:** gain `TCS34725_GAIN_1X`, 154 ms integration. White paper read `R130 G82 B48` with no correction, so `CAL_R = 1.0`, `CAL_G = 1.585`, `CAL_B = 2.708` make it read neutral grey (`130 130 130`, a ≈ 0, b ≈ 0). To redo it: put the TCS box on white paper, set all three gains to 1.0, flash, read R, G and B, then set `CAL_G = R/G` and `CAL_B = R/B`.
- **ESP32-CAM:** white balance and exposure are locked, with `MANUAL_EXPOSURE 150`. At 300 the flash clipped the green channel on white paper. The colour cast is removed in software: take a white-paper photo (`--sample WHITE`), then measure with `--flat` pointing at that photo. Take a new white photo whenever the camera, the box or the exposure changes.

## Repository layout
| Path | Purpose |
|---|---|
| `lab_dip_esp32/lab_dip_esp32.ino` | ESP32 firmware: reads sensors, computes ΔE (CIE76), OLED display, MQTT publish, serial and MQTT commands, online/offline status |
| `pin_check/pin_check.ino` | wiring test: scans ESP32 pins and reports where each sensor was found |
| `camera/esp32cam_capture.ino` | optional standalone ESP32-CAM photo station (`http://labdip-cam.local/capture`) |
| `camera/cam_capture.py` | fetches a camera photo, finds the fabric patch (OpenCV), measures its colour (CIEDE2000 vs target), checks levelness on a grid, saves an annotated photo, logs to `camera_log.csv`, publishes on `.../cam` with `--mqtt` |
| `camera/test_cam_capture.py` | self-check for `cam_capture.py` on synthetic photos (no camera needed) |
| `logger.py` | subscribes to MQTT and saves real readings to `trials_real.csv` |
| `trials_real.csv` | real readings logged from the rig |
| `test_real.csv` | small hand-made test set for `compare_live.py` replay |
| `compared_log.csv` | output of `compare_live.py`: PASS/FAIL, expected ΔE, deviation, likely cause |
| `generate_labdip_data.py` | generates the **synthetic** reference dataset (`sample_results.csv`, `sensor_readings.csv`) |
| `analyze.py` | correlation, OLS + ANOVA, Linear Regression and Random Forest (5-fold CV), PASS/FAIL classifier, permutation importance; outputs to `results/` |
| `compare_live.py` | compares live or replayed readings with the dataset (CIEDE2000), predicts expected ΔE, flags out-of-range conditions |
| `results/` | plots and model metrics from `analyze.py` |
| `android-app/` | LabDip Monitor Android app (Kotlin, Jetpack Compose): live readings, trends, history, rig controls. See [android-app/readme.md](android-app/readme.md) |
| `explanation.md` | how the whole system works end to end: rig, data, analysis, live comparison, camera, app, limits |
| `classroom_demo.md` | 5-minute classroom demo: cardboard layout, measurement box, ink bath, step-by-step run, backup plans |
| `demo.md` | demo guide: verified wiring, firmware upload, commands, reading meanings, demo script, troubleshooting |

## Quick start
1. **Firmware**: open `lab_dip_esp32/lab_dip_esp32.ino` in Arduino IDE, install the libraries listed at the top of the file, copy `lab_dip_esp32/secrets.example.h` to `lab_dip_esp32/secrets.h` and set `WIFI_SSID` / `WIFI_PASS` there (`secrets.h` is gitignored), select "ESP32 Dev Module" and upload. Serial monitor at 115200 baud.
2. **Python**:
   ```
   pip install pandas numpy scipy statsmodels scikit-learn matplotlib paho-mqtt
   python logger.py --out trials_real.csv
   python analyze.py sample_results.csv
   python compare_live.py --dataset sample_results.csv --mqtt
   ```
3. **Android app**: see [android-app/readme.md](android-app/readme.md).
4. **Camera (optional)**: flash `camera/esp32cam_capture.ino` (board "AI Thinker ESP32-CAM", set Wi-Fi in a local copy of the file and never commit it), then `pip install opencv-python numpy requests paho-mqtt`. White reference once: `python camera/cam_capture.py --host <IP> --led --sample WHITE` with white paper in the box. Demo: `python camera/cam_capture.py --host <IP> --led --flat images/WHITE_<stamp>.jpg --mqtt --loop`.
5. **Wiring problems**: flash `pin_check/pin_check.ino` and read the Serial Monitor at 115200 baud.

Full walkthrough: [demo.md](demo.md). How it all works: [explanation.md](explanation.md).

## Notes
- `sample_results.csv` and `sensor_readings.csv` are **synthetic** (simulated, not measured). Label them as synthetic in any report.
- The TCS34725 divides each channel by its clear channel, so it measures hue and saturation but not true lightness: white paper reads L ≈ 55, not 100, and its chroma is much lower than the camera's. Compare each sensor only against a target it measured itself. On the yellow test swatch both agreed on hue (TCS 98°, camera 101°).
- Readings in `trials_real.csv` taken before 2026-11-12 used the old gains (`GAIN_4X`, `CAL` = 1.0). Do not mix them with newer readings.
- The firmware computes ΔE with CIE76; the synthetic dataset and `compare_live.py` use CIEDE2000. Values are not directly comparable.
- The default broker `broker.hivemq.com` is public: anyone can read or publish on the topics, including sending rig commands. The firmware validates every command argument, but use a private TLS broker for real trials.
- Never commit real Wi-Fi or broker credentials.
