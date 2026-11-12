# Explanation

How the whole Multi-Sensor IoT Framework for Textile Lab-Dip Testing (Team 11) works, end to end, and why it is built this way. State as of 2026-11-13.

- How to wire, flash and demo it: [demo.md](demo.md)
- How the Android app works inside: [android-app/explanation.md](android-app/explanation.md)
- File list and quick start: [README.md](README.md)

## 1. The problem
A **lab dip** is a small fabric sample dyed in the lab before bulk dyeing. It is compared against the customer's target shade. If it is too far off, the recipe or the process is corrected and the dip is redone. Each extra round costs time and dye.

The shade a dip ends up with depends on more than the recipe:
- **Dye-bath temperature:** a bath that runs cold takes up less dye, giving a lighter, weaker shade.
- **Lighting at measurement:** extra light (a leak into the light box) makes the sample read lighter.
- **Room humidity and temperature:** affect the fabric and the reading slightly.

This project measures all of these together with the colour, so that a failed dip can be traced to a likely cause instead of guessed at.

## 2. System overview

```
              +---------------------------- ESP32 rig ----------------------------+
              |  TCS34725 colour   BH1750 light   DHT11 room   DS18B20 bath        |
              |  colour -> CIE Lab -> ΔE (CIE76) vs target, bath alert, OLED        |
              +---------------------------------+---------------------------------+
                                                | MQTT JSON every 3 s
                                                v
                                   broker.hivemq.com (public)
                    +---------------------------+---------------------------+
                    v                           v                           v
             logger.py (laptop)       compare_live.py (laptop)     LabDip Monitor app (phone)
             trials_real.csv          compared_log.csv             live view, trends, history,
                    |                                              rig controls (cmd topic)
                    v
             analyze.py  <---  sample_results.csv (synthetic, generate_labdip_data.py)
             results/ (plots, model metrics)

   Camera: ESP32-CAM --HTTP JPEG--> camera/cam_capture.py --> camera_log.csv, images/, MQTT .../cam --> app
```

The parts are loosely coupled: everything talks through MQTT topics, so each consumer (logger, comparison script, app) can run alone or together.

## 3. The rig (ESP32 firmware)
File: `lab_dip_esp32/lab_dip_esp32.ino`. Wi-Fi credentials are `WIFI_SSID` and `WIFI_PASS` in `lab_dip_esp32/secrets.h`, which is gitignored. Copy `secrets.example.h` to create it.

### Sensors
| Sensor | Measures | Connection |
|---|---|---|
| TCS34725 | fabric colour (raw R, G, B, clear) | I2C, SDA 21 / SCL 22, address `0x29` |
| BH1750 | room light level (lux); it sits on the breadboard outside the measurement box | I2C, address `0x23` |
| SSD1306 OLED | on-rig display | I2C, address `0x3C` |
| DHT11 | room temperature and humidity | GPIO 27 |
| DS18B20 | dye-bath temperature | GPIO 4, 4.7 kΩ pull-up to 3.3 V |

All sensors run on 3.3 V. Wiring lessons (for example: never power the TCS34725 through its `3V3` pin) are in [demo.md](demo.md) section 2.

### Measurement box
The fabric is measured inside a cardboard box, 21 × 16 × 8.5 cm, with the 21 × 16 cm side open and placed face down on the swatch.
- **Camera chamber (about 15 cm of the length):** the ESP32-CAM sits in the lid, lens straight down, and sees about 11 × 8 cm of fabric. Its own flash LED is the only light. A white paper bounce card under the flash sends the light to the white-lined ceiling, so the fabric is lit softly and evenly without a glare spot.
- **TCS34725 box (about 6 cm of the length):** a small opaque box, open at the bottom, with the sensor facing down a few millimetres above the fabric. The sensor's onboard LED lights the fabric.
- **Light seal:** foam or felt around the bottom rim keeps room light out.
- **Outside the box:** the ESP32, BH1750, DHT11 and OLED are on a breadboard next to the box. The BH1750 measures room light, so it shows how bright the room is, not the light inside the sealed box.

### Every 3 seconds (`readAll()`, `showOLED()`, `publishReading()`)
1. **Colour:** the TCS34725 runs at `GAIN_1X` with 154 ms integration. Raw counts are divided by the clear channel, scaled to 0–255 and multiplied by per-channel calibration gains `CAL_R = 1.0`, `CAL_G = 1.585`, `CAL_B = 2.708`. These gains make white paper read neutral grey (`130 130 130`). Each reading is the per-channel median of 3 sensor reads, so one bumped or flickering read does not move ΔE.
2. **Lab:** RGB is converted sRGB to linear RGB to XYZ (D65) to CIE Lab.
3. **ΔE:** CIE76, ΔE = √(ΔL² + Δa² + Δb²), against the target Lab.
4. **Bath alert:** once `BATH` is set, the alert is on when the bath is more than `BATH_TOL` (2.0 °C) from the set point.
5. **OLED:** sample ID, ΔE, RGB, bath, room, light, and an alert or MQTT status line.
6. **Publish:** one JSON message to `rvu/team11/labdip/data`.

### Honest data rules
- No light on the colour sensor (missing or unplugged): **nothing is published**. A missing sensor reads all zeros, which would look like a black sample.
- A failed DHT11, BH1750 or DS18B20 read: that field is **left out** of the JSON, never filled with an old or fake value.
- No target set yet: ΔE is computed against a 50/0/0 placeholder and the JSON says `target_set: false`.

### JSON message
```
sample_id, timestamp, deltaE, R, G, B, L, a, b,
ambient_temp, humidity, light_lux, bath_temp,   (each left out if its read failed)
bath_alert, setpoint_c, target_L, target_a, target_b, target_set,
fabric, dye, dye_conc                            (only when set)
```

### Commands
Typed in the Serial Monitor (115200 baud) or sent by the app to `rvu/team11/labdip/cmd`. Both go through the same `runCommand()`. Replies (`OK ...` / `ERR ...`) go to serial and to `rvu/team11/labdip/ack`.

| Command | Effect |
|---|---|
| `ID S001` | set the current sample ID |
| `CAPTURE` | the current colour reading becomes the target |
| `TARGET 52.3 10.1 -4.2` | set the target Lab by hand |
| `BATH 60` | set the recipe bath temperature and enable the alert |
| `FABRIC cotton`, `DYE reactive_red`, `CONC 2.0` | metadata added to every message |

The broker is public, so every argument is validated before it changes state, and MQTT commands over 64 bytes are dropped.

### Presence
On connect the rig publishes a retained `online` to `rvu/team11/labdip/status`. Its MQTT last will is `offline`, so the broker announces when the rig drops. The app uses this for its online / offline badge.

## 4. Data
| File | Real or synthetic | Made by | Contents |
|---|---|---|---|
| `sample_results.csv` | **synthetic** | `generate_labdip_data.py` | 60 samples, one row each: recipe, target and measured Lab, ΔE76, ΔE2000, PASS/FAIL, bath statistics, conditions at measurement, main deviation driver |
| `sensor_readings.csv` | **synthetic** | `generate_labdip_data.py` | time series for the same samples, one row per 30 s (ramp, hold, cool phases) |
| `trials_real.csv` | real | `logger.py` | readings logged from the rig |
| `test_real.csv` | hand-made test set | by hand | a few readings for `compare_live.py --replay` |
| `compared_log.csv` | output | `compare_live.py` | per-reading comparison results |

### Why a synthetic dataset
Too few real dips could be dyed in the project time to train a model. The synthetic set is a stand-in with the same columns, built from published textile practice:
- pass limit CIEDE2000 ≤ 1.0
- bath set points: reactive on cotton/viscose 60 °C, acid on nylon 90 °C, disperse on polyester 98 °C
- 16 named shades (Navy, Red, Teal, ...) as targets
- Bengaluru October room conditions (about 26 °C, 62 %RH, warmer and drier in the afternoon)
- injected faults: bath dips, sustained low or high baths, light leaks
- sensor noise and resolution modelled per sensor (for example DS18B20 0.0625 °C steps)

The shade shift is a simple model: a colder bath makes the sample lighter and less saturated, extra light makes it read lighter, humidity shifts L slightly, plus random noise. It is fixed-seed (`SEED = 42`), so it is reproducible. **It must be labelled synthetic in any report.**

## 5. Laptop scripts
| Script | Input | What it does | Output |
|---|---|---|---|
| `logger.py` | MQTT `data` topic | appends every reading to a CSV | `trials_real.csv` |
| `generate_labdip_data.py` | none | builds the synthetic dataset | `sample_results.csv`, `sensor_readings.csv` |
| `analyze.py` | `sample_results.csv` or `trials_real.csv` | statistics and models (section 6) | `results/` |
| `compare_live.py` | dataset + live MQTT or `--replay` CSV | judges each reading against the dataset (section 7) | `compared_log.csv` |
| `camera/cam_capture.py` | ESP32-CAM or an image file | measures photo colour (section 8) | `camera_log.csv`, `images/` |

## 6. Analysis (`analyze.py`)
Question: **which conditions explain colour deviation, and can ΔE be predicted?**

1. **One row per sample.** It auto-detects the input: `sample_results.csv` is already one row per sample; `trials_real.csv` readings are averaged per `sample_id`.
2. **Features relative to the recipe**, not a fixed 60 °C:
   - `temp_dev`: bath mean minus set point (signed)
   - `temp_under`: shortfall only, since a cold bath is what lightens the shade
   - `bath_std`: bath stability
   - `lux_dev_pct`: % deviation from the 1200 lux target
   - humidity, room temperature, dye concentration, hold time, fabric (one-hot)
   - `dye_class` is left out: it is fully determined by fabric, so it would be collinear.
3. **Spearman correlation** with ΔE: `results/correlation.png`.
4. **Scatter plots** of ΔE against bath deviation, humidity and lighting, with the pass line: `results/scatter.png`.
5. **OLS regression + ANOVA:** which factors are statistically significant.
6. **Prediction, 5-fold cross-validation:** Linear Regression and Random Forest. Current results on the synthetic set (`results/model_metrics.csv`):

   | Model | R² | RMSE | MAE |
   |---|---|---|---|
   | Linear Regression | 0.727 | 0.235 | 0.177 |
   | Random Forest | 0.529 | 0.308 | 0.241 |

   Linear wins because the synthetic shift is close to linear and 60 samples is small for a forest. Real data may differ.
7. **PASS/FAIL classifier** (Random Forest), compared against the "always the majority class" baseline.
8. **Permutation importance:** which condition drives ΔE most: `results/feature_importance.png`.

## 7. Live comparison (`compare_live.py`)
For each live (`--mqtt`) or replayed (`--replay file.csv`) reading:
1. **Target shade:** the closest named shade in the dataset, or the one given with `--shade`.
2. **ΔE2000** between the reading and that shade.
3. **Expected ΔE:** a linear model trained on the dataset predicts the ΔE the current conditions should give (bath shortfall, lighting, humidity, room temperature).
4. **Deviation** = measured minus expected. Large positive deviation means something other than the logged conditions went wrong.
5. **Flags:** any condition outside the 5–95 % range seen in PASS samples, for example `bath below set point = 2.8 (normal 0.0 to 2.3)`.
6. **Likely cause:** the condition contributing most to the expected ΔE, or "random variation" when none stands out.

## 8. Camera station (optional)
A separate ESP32-CAM (`camera/esp32cam_capture.ino`) serves a JPEG at `http://labdip-cam.local/capture`. White balance and exposure are locked (`LOCK_COLOUR_SETTINGS`, `MANUAL_EXPOSURE 150`) so the same fabric gives the same colour every time. At exposure 300 the flash clipped the green channel on white paper, so it was halved.

The locked white balance gives every raw photo a strong green cast: white paper comes out about `140 209 132`. That is expected. The `--flat` white reference in step 3 below removes it. Without `--flat`, the yellow test swatch read `a -39.7` (green). With `--flat` it read `L 78.4 a -9.7 b 48.3`, a yellow.

`camera/cam_capture.py` fetches a photo and analyses it with OpenCV:
1. **Patch detection:** the fabric differs in colour from the paper around it (Lab distance to the image border, Otsu threshold, opening to cut loose threads, largest contour). The outer 8 % of the patch is dropped (frayed edges). If no patch is found it falls back to the central `--roi` and says so.
2. **Glare:** clipped pixels (any channel ≥ 250) are ignored.
3. **White reference:** `--flat` divides by a photo of plain white paper taken in the same box, pixel by pixel. That removes the camera's colour cast and the uneven LED light. `--white R G B` corrects colour only.
4. **Colour:** median of the patch, converted to CIE Lab (D65). ΔE2000 against the target (`--target`, or the rig's target with `--mqtt`).
5. **Levelness:** the patch is split into a 3×3 grid (`--grid 2–4`). Each cell's mean colour is compared with the patch colour (ΔE2000). Max cell ≤ `--level-tol` (1.0) is "uniform", above it "uneven". Uneven dyeing is a real lab-dip reject reason that a single-spot sensor cannot see.
6. **Output:** the photo plus an annotated copy (outline, grid, cell values) in `images/`, a row in `camera_log.csv`. With `--mqtt` it reads the sample ID, target and sensor Lab from the rig's data topic, and publishes the result on `rvu/team11/labdip/cam` for the app. `--loop` captures again on each Enter.

The camera-vs-sensor ΔE is an agreement check, not a calibrated comparison: the TCS34725 RGB is normalised by its clear channel, the camera's by the white reference. On the yellow test swatch the two agreed on hue (TCS 98°, camera 101°). They did not agree on lightness or chroma: the TCS read `L 54.9 a -1.6 b 11.0`, the camera `L 87.3 a -10.2 b 52.4`.

The levelness grid only means something when the fabric fills the whole camera view. Grid cells that fall on the white paper around a small swatch, or on a shadow, give false "uneven" values.

## 9. Android app (LabDip Monitor)
Kotlin and Jetpack Compose, in `android-app/`. It subscribes to the same topics as the laptop scripts. No backend is needed.
- live split swatch (target vs measured) on neutral grey, ΔE with pass / marginal / fail
- bath temperature vs set point with alerts, room conditions
- trend charts and a per-sample history in a local Room database, CSV export
- rig controls: sends the commands from section 3 and shows the rig's reply
- rig presence badge: online, no new data, or offline

Every incoming message is validated (size cap, strict JSON, range checks) before it is stored. Full detail: [android-app/explanation.md](android-app/explanation.md).

## 10. Two ΔE numbers: do not mix them
| Where | Formula | Against |
|---|---|---|
| ESP32, OLED, app, `trials_real.csv` | CIE76 | the target set by `CAPTURE` or `TARGET` |
| `compare_live.py`, `sample_results.csv`, `cam_capture.py` | CIEDE2000 | the closest dataset shade, or `--target` |

CIE76 is cheap enough for the ESP32. CIEDE2000 matches how people see colour difference and is the textile industry standard, so the analysis uses it. The two numbers are different scales and must not be compared directly.

## 11. Security
- `broker.hivemq.com` is public: anyone can read the readings and send commands to the rig. The firmware validates every command, but use a private broker with TLS for real trials.
- Wi-Fi credentials are in the gitignored `lab_dip_esp32/secrets.h`. The camera firmware `camera/esp32cam_capture.ino` still holds placeholder Wi-Fi values: set the real ones only in a local copy and do not commit them.
- The app's release build refuses plain MQTT and requires TLS.

## 12. Limits and not done
- **Synthetic training data:** the models in `results/` describe the simulation, not real dyeing. Retrain with `python analyze.py trials_real.csv` once enough real dips are logged.
- **Calibration:** both colour sensors are calibrated against white paper only (2026-11-12), not against certified reference tiles or a spectrophotometer. The TCS34725 divides by its clear channel, so it measures hue and saturation but not true lightness: white paper reads L ≈ 55, not 100. Strong blues may clip, because the blue channel is multiplied by 2.7. Readings in `trials_real.csv` taken before 2026-11-12 used the old settings (`GAIN_4X`, gains 1.0) and must not be mixed with newer ones.
- **BH1750 placement:** it measures room light, outside the sealed box. `compare_live.py` still compares lux with a 1200 lux light-box target, so its lighting flag does not apply to this build.
- **TCS34725 is a low-cost sensor**, not a spectrophotometer. Results show trends and likely causes, not certified shade approval.
- **DHT11** has coarse resolution (±2 °C, ±5 %RH). The synthetic set models a DHT22 by default.
- **QR codes:** the project description mentions QR codes per sample. They are not implemented; samples are identified by the `ID` command.
- **Cloud tracking:** data goes over a public MQTT broker to the app and laptop. There is no cloud database.
