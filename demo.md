# Demo guide: Multi-Sensor IoT Framework for Textile Lab-Dip Testing

This guide explains how to set up, run and demo the whole system: the ESP32 rig, the serial commands, the two Python scripts and the Android app. It also explains what every reading means and how to tell a good result from a bad one.

---

## 1. How the system fits together

```
 Sensors ──> ESP32 (lab_dip_esp32.ino) ──Wi-Fi/MQTT──> broker.hivemq.com ──┬──> logger.py        (saves CSV)
   │            │                                    topic:                 ├──> compare_live.py  (PASS/FAIL vs dataset)
   │            └── OLED display (on the rig)         rvu/team11/labdip/data └──> Android app      (live dashboard)
   │
   └── Laptop USB cable: only for uploading code and typing serial commands
```

- The ESP32 reads all sensors every **3 seconds**, shows the values on the OLED, prints a JSON line on the serial port and publishes the same JSON over MQTT.
- The laptop scripts and the phone app all receive the readings from the internet (MQTT broker). They do **not** need a USB cable to the ESP32.
- The Python files never go onto the ESP32. The Arduino IDE compiles only the `.ino` file. The `.py` files run on the laptop.

---

## 2. Hardware wiring (verified 2026-11-01)

All sensors run on **3.3 V**. Unplug the USB cable every time you move a wire.

| Sensor | Sensor pin | ESP32 pin |
|---|---|---|
| **TCS34725** (RGB colour) | `VIN` | `3V3` |
| | `GND` | `GND` |
| | `SDA` | `D21` (GPIO 21) |
| | `SCL` | `D22` (GPIO 22) |
| | `3V3`, `LED`, `INT`, others | **not connected** |
| **BH1750** (light) | `VCC` / `GND` / `SDA` / `SCL` | `3V3` / `GND` / `D21` / `D22` |
| **SSD1306 OLED** (display) | `VCC` / `GND` / `SDA` / `SCL` | `3V3` / `GND` / `D21` / `D22` |
| **DHT11** (room temp + humidity) | `VCC` / `DATA` / `GND` | `3V3` / `D27` (GPIO 27) / `GND` |
| **DS18B20 probe** (bath temp) | red / yellow / black | `3V3` / `D4` (GPIO 4) / `GND` |
| | 4.7 kΩ resistor | between `D4` and `3V3` |

I2C addresses on SDA 21 / SCL 22: TCS34725 `0x29`, BH1750 `0x23`, OLED `0x3C`.

### Wiring lessons learned

- **Never connect the TCS34725's `3V3` pin.** It is the output of the board's own regulator. Feeding power into it made the sensor hot. Power goes into `VIN` only.
- **Never use the ESP32's `VIN` pin for sensors.** On the ESP32 board, `VIN` is the 5 V USB line.
- **SDA and SCL swapped** makes a sensor invisible on 21/22. The TCS34725 was found at "SDA=22 SCL=21" until the wires were swapped back.
- **If a sensor gets hot, unplug the USB at once.** Power is wired backwards or shorted.
- Repeated `rst:` / `ets Jul 29 2019` lines at boot mean the 3.3 V supply dips. Check that every `3V3` and `GND` jumper is firm. Use a good USB port or charger. A 10–100 µF capacitor between `3V3` and `GND` helps.

### Wiring test sketch

`pin_check/pin_check.ino` scans the ESP32 pins and prints PASS or WRONG PINS for each sensor. Use it after rewiring.

- Ignore "WRONG PINS" hits that appear in only one scan round (for example `SDA=32 SCL=27`). They are false readings.
- Its DS18B20 result is **not reliable**: it reported the probe missing while the real firmware read it fine. Trust `lab_dip_esp32` for the probe.
- After using it, re-flash `lab_dip_esp32`.

---

## 3. Uploading the firmware (Arduino IDE)

The sketch folder must contain only the `.ino` file with the same name as the folder. Nothing else needs to be copied next to it.

1. Install the libraries once (**Sketch > Include Library > Manage Libraries**): Adafruit TCS34725, Adafruit SSD1306, Adafruit GFX, BH1750, DHT sensor library, OneWire, DallasTemperature, PubSubClient, ArduinoJson. (All are already installed on this laptop in `Documents\Arduino\libraries`.)
2. Open `lab_dip_esp32\lab_dip_esp32.ino`.
3. Set your Wi-Fi name and password in `WIFI_SSID` and `WIFI_PASS` at the top of the file. Do not commit real passwords to git.
4. **Tools > Board > esp32 > ESP32 Dev Module.**
5. **Tools > Port > COM9** (the "Silicon Labs CP210x" port).
6. Click **Upload**.
7. Open **Tools > Serial Monitor** at **115200** baud. A JSON line should appear every 3 seconds.

### After upload: does the laptop need to stay connected?

No. The code is stored in the ESP32's flash memory and runs every time it gets power.

- **Power:** any 5 V USB phone charger or power bank. Some power banks switch off at low current. Use a wall charger if the board goes dark.
- **Wi-Fi:** the network in `WIFI_SSID` must be in range and have internet access.
- Keep the USB cable to the laptop only when you want to upload code, watch the Serial Monitor or send commands.

---

## 4. Sending commands to the ESP32

"Sending a command" means typing text into the Arduino IDE **Serial Monitor** while the ESP32 is plugged into the laptop by USB. The ESP32 reads the text and changes its settings.

### How to send

1. Connect the ESP32 to the laptop by USB.
2. Arduino IDE: **Tools > Port > COM9**.
3. Open **Tools > Serial Monitor**.
4. Bottom right: set the baud rate to **115200**.
5. Next to it: set the line ending to **Newline**. With "No line ending", every command is ignored.
6. Type the command in the box at the top and press **Enter**.
7. The ESP32 answers with a line that starts with `OK`.

### Command list

| Type this | What it does | Reply |
|---|---|---|
| `ID S002` | Names the current sample. The app switches to the new sample. | `OK sample=S002` |
| `CAPTURE` | Saves the colour under the sensor **now** as the target. ΔE is measured against it from then on. | `OK target Lab=40.60 -3.25 22.91` |
| `TARGET 50 0 0` | Sets the target colour by typing L a b values. | `OK target set` |
| `BATH 28` | Sets the bath set point to 28 °C and switches the bath alert on. | `OK bath=28.0 +/- 2.0` |
| `FABRIC cotton` | Records the fabric name. | `OK` |
| `DYE reactive_red` | Records the dye name. | `OK` |
| `CONC 2.5` | Records the dye concentration. | `OK` |

Anything else gets `Unknown command`. Commands are not case-sensitive.

**Settings are lost on reset or power loss.** The target, `BATH` and `ID` go back to their defaults. Send them again after every restart.

### What `BATH` does exactly

You never send `bath_alert`. The ESP32 calculates it.

- `OK bath=28.0 +/- 2.0` means: set point 28.0 °C, tolerance ±2.0 °C.
- The bath is OK while the probe reads between **26.0 and 30.0 °C**. `bath_alert` stays `false`.
- Outside that range, `bath_alert` becomes `true`, the OLED bottom line shows `!! BATH TEMP ALERT !!`, and the app shows a red banner and vibrates once.
- The alert clears by itself when the temperature comes back inside the range.
- Before any `BATH` command, the alert is off, even though `setpoint_c` shows the default 60.

---

## 5. What every reading means

Example JSON line from the ESP32:

```json
{"sample_id":"S001","timestamp":"2026-11-01T18:30:13","deltaE":24.98,"R":103,"G":96,"B":57,
 "L":40.6,"a":-3.25,"b":22.91,"ambient_temp":28.5,"humidity":50.7,"light_lux":15,"bath_temp":28.3,
 "bath_alert":false,"setpoint_c":60,"target_L":50,"target_a":0,"target_b":0,"target_set":false}
```

| Field | Sensor | Meaning | What is "correct" |
|---|---|---|---|
| `sample_id` | – | Name of the sample being tested. Default `S001`. Change with `ID`. | – |
| `timestamp` | Internet time (IST) | When the reading was taken. | – |
| `R`, `G`, `B` | TCS34725 | Raw colour under the sensor. | No fixed value; depends on the fabric. |
| `L` | from RGB | Lightness: 0 = black, 100 = white. | Close to the target `L`. |
| `a` | from RGB | Green (−) to red (+). | Close to the target `a`. |
| `b` | from RGB | Blue (−) to yellow (+). | Close to the target `b`. |
| `deltaE` | calculated | Colour difference between the sample and the target (CIE76 formula). | Below about 1 = match. 2–3 and above = visible difference. |
| `target_L/a/b` | – | The target colour ΔE is measured against. | – |
| `target_set` | – | `false` = no target set yet; ΔE is against a placeholder grey (50, 0, 0) and is **meaningless**. `true` after `CAPTURE` or `TARGET`. | `true` during a real test. |
| `bath_temp` | DS18B20 | Dye-bath temperature (°C). | Within ±2 °C of the set point. |
| `setpoint_c` | – | Bath set point from the `BATH` command. Default 60. | – |
| `bath_alert` | calculated | `true` when the bath is outside set point ±2 °C (only after a `BATH` command). | `false`. |
| `ambient_temp` | DHT11 | Room temperature (°C). | Inside the dataset's normal range. |
| `humidity` | DHT11 | Room humidity (%RH). | Inside the dataset's normal range. |
| `light_lux` | BH1750 | Light-box brightness (lux). | Near the 1200 lux target. |

**ΔE in plain words:** ΔE 1 is about the smallest colour difference a human eye can see. Below 1 the dyed sample matches the standard. Above 2–3, people can see the difference.

### The example reading explained (no fabric, no dye)

- `L 40.6, a −3.25, b +22.9`: a medium-dark yellowish colour. With no fabric, this is the desk surface under warm room light.
- `deltaE 24.98`: meaningless, because `target_set` is `false`. It is the difference from the placeholder grey 50/0/0. Check: √(9.4² + 3.25² + 22.9²) ≈ 24.98.
- `light_lux 15`: very dim, like a dark room. The light-box target is 1200 lux.
- `bath_temp 28.3`: the probe is in air, so it reads room temperature.
- `setpoint_c 60` with `bath_alert false`: no `BATH` command was sent yet, so the alert is not armed.
- `ambient_temp 28.5`, `humidity 50.7`: normal room conditions.
- Two readings 3 s apart differ by only 0.1 in ΔE: the sensors are stable.

---

## 6. The Python scripts (laptop)

They run on the laptop and receive readings from the MQTT broker. The laptop needs internet; it does not need the USB cable.

| File | What it does |
|---|---|
| `logger.py` | Saves every ESP32 reading as a row in `trials_real.csv`. This is the **real** dataset collector. It does not judge anything. |
| `compare_live.py` | Compares each live reading with the reference dataset and prints PASS/FAIL, expected ΔE, deviation and likely cause. Saves to `compared_log.csv`. |
| `analyze.py` | Analyses a CSV for the research paper (which conditions explain ΔE). |
| `generate_labdip_data.py` | Creates the **synthetic** reference dataset `sample_results.csv`. |
| `android-app\tools\fake_rig.py` | Pretends to be the ESP32, for testing the app without hardware. |

### Install once

```powershell
python -m pip install paho-mqtt pandas numpy scikit-learn
```

### Run the two live scripts

Open **two** PowerShell windows. In each one:

```powershell
cd C:\Users\PRIYADARSHINI\Desktop\IOT\Multi-Sensor-IoT-Framework-for-Textile-Lab-Dip-Testing
```

Window 1 (save readings):

```powershell
python logger.py --out trials_real.csv
```

It prints `Listening on rvu/team11/labdip/data`, then appends every reading to `trials_real.csv`.

Window 2 (live comparison):

```powershell
python compare_live.py --dataset sample_results.csv --mqtt
```

Useful options for `compare_live.py`:

- `--shade Navy` compares against a fixed shade instead of the closest one.
- `--setpoint 60` sets the default bath set point (used when the reading has none).
- `--lux-target 1200` sets the light target.
- `--pass-limit 1.0` sets the ΔE PASS limit.
- Offline test without hardware: `python compare_live.py --dataset sample_results.csv --replay trials_real.csv`

Press `Ctrl+C` to stop either script.

### How to read a `compare_live.py` line

Illustrative output (numbers are made up to show the format):

```
S001  shade Navy  dE2000 0.84 [PASS]  expected 0.90  deviation -0.06
   outside normal range: lighting vs target = -98.8 (normal -5.0 to 5.0)
   likely cause: bath below set point
```

- **shade**: the closest shade in the dataset (or the one given with `--shade`).
- **dE2000**: colour difference from that shade, using the CIEDE2000 formula (the industry standard).
- **[PASS] / [FAIL]**: PASS if dE2000 ≤ 1.0 (or `--pass-limit`).
- **expected**: the ΔE the model predicts for the current bath, light, humidity and room temperature.
- **deviation**: measured minus expected. Near 0 = the conditions explain the colour error. Large and positive = something else is wrong (dye recipe, fabric).
- **outside normal range**: which condition is unusual compared with the dataset's PASS samples.
- **likely cause** (FAIL only): the condition that adds most to the predicted ΔE. "random variation" if none stands out.

With no fabric, no hot bath and no light box, expect **FAIL** with "bath below set point" and "lighting vs target" flagged. That is correct behaviour.

### Important differences

- The ESP32 / OLED / app `deltaE` uses **CIE76** against **your captured target**.
- `compare_live.py`'s `dE2000` uses **CIEDE2000** against the **closest dataset shade**.
- They are different numbers. Do not compare them directly.
- `sample_results.csv` is **synthetic**: generated by simulation, not measured. Say so in the demo and in any report.

---

## 7. The Android app (LabDip Monitor)

The app listens to the same MQTT topic and shows everything live. The ESP32 needs no changes.

### Install

1. Plug in the phone with USB debugging on.
2. Open the `android-app` folder in Android Studio.
3. Select the phone and press **Run ▶**. Use the **debug** build: it allows plain MQTT on port 1883. The release build requires TLS.

### First launch

1. Swipe through or skip the onboarding.
2. The Connect screen is prefilled with `broker.hivemq.com`, `1883`, `rvu/team11/labdip/data`. Tap **Connect**.
3. The Live screen fills in within about 3 s.

The phone needs internet (mobile data or any Wi-Fi). It does not need to be on the ESP32's network.

### What each screen shows

| Screen | What it shows |
|---|---|
| **Live** | Gauge with five buttons (ΔE, bath, ambient, humidity, light). Split colour swatch: target left, measured right. Reading cards and Lab values. PASS/FAIL status. Red bath-alert banner plus one vibration. |
| **Trend** | Charts over time: ΔE with threshold lines and FAIL dots, bath with set-point band, ambient, humidity, light. Tap or drag to read a point. Last 20 / 100 / all. |
| **Samples** | One row per sample ID. Tap for details and **Export CSV**. Swipe to delete (with undo). |
| **Settings** | Broker, topic, target L/a/b, ΔE pass and marginal limits, lux range. "Test connection" and "Save and reconnect". |

The app does **not** show "expected", "deviation" or "likely cause". Those exist only in `compare_live.py`.

---

## 8. Demo script (step by step)

### Before the demo

1. Wire and power the rig. The OLED shows readings and `MQTT ok` on the bottom line.
2. Laptop: start `logger.py` and `compare_live.py` (section 6).
3. Phone: open the app; the Live screen shows readings.
4. Laptop: Serial Monitor open on COM9 at 115200, line ending **Newline**.
5. Have ready: one reference colour (fabric, paper or cloth), one different colour, and a steady bright light.
6. Do one full rehearsal of steps 4–7 below.

### During the demo

1. **Show the rig and the OLED.** Each line is one sensor: ID and ΔE, RGB, bath, ambient, light, MQTT status. This proves all five sensors work and data reaches the internet.
2. **Show the app's Live screen.** Tap through the five gauge buttons: each is a separate sensor.
3. **Start a sample:** type `ID S002`. The app switches to the new sample.
4. **Capture the reference colour:** put the reference under the colour sensor and type `CAPTURE`. ΔE drops to about 0. The two swatch halves in the app match. Status: PASS.
5. **Show a mismatch:** swap in the different colour. ΔE rises. Status: FAIL.
6. **Show the bath alert:** type `BATH 28` (or a value near the probe's reading). Hold the probe tip tightly in your hand for 30–60 s. Once it passes 30 °C: `bath_alert` becomes `true`, the OLED shows `!! BATH TEMP ALERT !!`, and the app shows the red banner and vibrates. Let go; the alert clears as it cools. If your hand cannot push it past 30 °C, use `BATH 26` (upper limit 28 °C).
7. **Show the Trend screen:** the ΔE jump and the bath spike on the charts.
8. **Show the laptop:**
   - `logger.py` window: each reading being saved; open `trials_real.csv`.
   - `compare_live.py` window: PASS/FAIL, expected ΔE, deviation, out-of-range conditions and likely cause.
9. **Show the Samples screen:** open S002 and tap **Export CSV**.

### Points to state clearly

- `sample_results.csv` is synthetic, generated by simulation.
- Before `CAPTURE`, the ESP32's ΔE is meaningless (placeholder grey target).
- Low lux and "bath below set point" flags are expected without a light box and a heated bath.
- The app/OLED ΔE (CIE76, captured target) and `compare_live.py`'s dE2000 (CIEDE2000, dataset shade) are different measurements.

---

## 9. Troubleshooting

| Problem | Cause and fix |
|---|---|
| Serial Monitor shows nothing | Wrong port or baud. Use COM9, 115200. Press the board's EN button. |
| Commands do nothing | Line ending is not **Newline**. |
| OLED shows `MQTT offline` | No Wi-Fi or no internet. Check `WIFI_SSID` / `WIFI_PASS` and the hotspot. |
| A sensor reads nothing / missing from JSON | Loose wire or wrong pin. Run `pin_check` (section 2). |
| `bath_temp` missing from JSON | DS18B20 not answering. Check red/yellow/black wiring and the 4.7 kΩ resistor. |
| Sensor gets hot | Power reversed or into the wrong pin. **Unplug USB immediately.** |
| Many `rst:` lines at boot | 3.3 V supply dipping. Firm up `3V3`/`GND` jumpers, better USB supply, add a capacitor. |
| Scripts print nothing | ESP32 not publishing (check OLED `MQTT ok`) or laptop offline. |
| App stuck on "Waiting for first reading" | Same as above; also check the topic is `rvu/team11/labdip/data`. |
| `Missing dependency: ... paho-mqtt` | `python -m pip install paho-mqtt` |
| Build error `PubSubClient.h: No such file or directory` | Install PubSubClient and ArduinoJson in the Library Manager. |
