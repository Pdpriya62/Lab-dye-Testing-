# Classroom demo plan

One turn of about 5 minutes. Goal: show every sensor working, both colour sensors measuring a fabric swatch inside the measurement box, and the readings flowing to the app and the laptop.

Everything sits on one cardboard sheet. The measurement box is on one side. A glass of water with a few drops of ink stands in for the dye bath. Full wiring, firmware and troubleshooting notes are in [demo.md](demo.md); how the system works is in [explanation.md](explanation.md); the box and the calibration are described in [README.md](README.md).

## 1. The plan in short

- **Before your turn:** start the hotspot, power the rig and the camera, take the camera's white-paper photo, set the bath set point from the ink glass, and start `logger.py`.
- **During your turn, about 4 minutes:**
  - Capture the reference swatch as the target, then swap in a second swatch to show ΔE rising.
  - Run the camera on the swatch and show its Lab value and the annotated photo.
  - Hold the bath probe in your hand until the bath alert triggers.
  - Show the logged rows and the app's trend screen.
- **If the network fails:** the OLED still shows every reading, and commands work over USB in the Serial Monitor.

Use the phone hotspot, not classroom Wi-Fi. Classroom Wi-Fi often blocks MQTT or needs a login page, which the ESP32 cannot handle. The hotspot name and password must match `WIFI_SSID` and `WIFI_PASS` in `lab_dip_esp32/secrets.h`, and the camera must be flashed with the same network.

**Arduino IDE:** you do not need it during the demo. Both boards are already flashed and start on their own when powered. Open it only as a backup: the Serial Monitor (COM9 for the ESP32, 115200 baud) if the network fails, or COM10 to read the camera's IP if it changes.

### If you change the hotspot

**Easiest:** give the new hotspot the same name and password as the one in `lab_dip_esp32/secrets.h` (phone settings: Hotspot > Hotspot name / Password, band 2.4 GHz). Both boards then connect on their own and nothing else changes.

If the name or password is different, reflash both boards the day before the demo, with both plugged into the laptop:

1. **Main ESP32 (COM9):** put the new `WIFI_SSID` and `WIFI_PASS` in `lab_dip_esp32/secrets.h`, then upload `lab_dip_esp32/lab_dip_esp32.ino` (board "ESP32 Dev Module").
2. **ESP32-CAM (COM10):** make a local copy of `camera/esp32cam_capture.ino`, put the new name and password in the copy, and upload it (board "AI Thinker ESP32-CAM"). Do not commit that copy.
3. Connect the laptop and the phone to the new hotspot.
4. **Find the camera's new IP.** It almost always changes. Read it in the Serial Monitor on COM10 (`Connected. IP: ...`), or try `labdip-cam.local`. Use the new IP in every `--host` command below.
5. Take a new white photo with the new `--host` (section 4, step 4).
6. Check: the OLED shows `MQTT ok`, and `http://<new IP>/` says `Lab-dip camera ready`.

The app, the broker and the MQTT topics do not change.

## 2. What to bring

**Rig**
- [ ] Cardboard sheet, about A3 or 60 × 40 cm, with tape.
- [ ] Measurement box (21 × 16 × 8.5 cm) with the ESP32-CAM, the bounce card under its flash, the white lining, the TCS34725 box and a foam or felt rim.
- [ ] Breadboard with the ESP32, BH1750, DHT11, OLED and the DS18B20 connection.
- [ ] Two USB cables: ESP32 and ESP32-CAM. A power bank as a spare.
- [ ] Spare jumper wires.

**Bath probe**
- [ ] DS18B20 **waterproof** probe: the steel-tipped type with a sealed cable. Only the steel tip goes in the water.
- [ ] Its three wires: red to `3V3`, black to `GND`, yellow (or white) data to `GPIO 4`.
- [ ] A 4.7 kΩ resistor between the data wire and `3V3`. Without it the probe reads nothing.
- [ ] A clear glass of water with a few drops of ink, standing on a tray or plate.
- [ ] Tape to hold the probe cable along the cardboard edge, so the tip stays in the glass and nobody pulls it out.
- [ ] Tissue for spills.

**Swatches and paper**
- [ ] Two or more swatches of one colour in different shades, at least 18 × 10 cm each, ironed flat.
- [ ] Plain white paper, the same kind used for calibration.

**Laptop and phone**
- [ ] Laptop with the repo and Python packages: `pip install opencv-python numpy requests paho-mqtt`.
- [ ] Phone with the LabDip Monitor app, hotspot `408` on (2.4 GHz).
- [ ] Laptop, phone and power bank charged.

## 3. Layout on the cardboard

```
 +-----------------------------------------------------------+
 |                                                           |
 |  +-----------------+    +-------------------+    ( ink  ) |
 |  |  MEASUREMENT    |    |   BREADBOARD      |    ( glass) |
 |  |  BOX 21 x 16 cm |====|  ESP32  OLED      |~~~~ DS18B20 |
 |  |  open side down |TCS |  BH1750 DHT11     |     probe   |
 |  |  camera on top  |wire|                   |    on tray  |
 |  +-----------------+    +-------------------+             |
 |   swatch slides under    OLED faces audience   farthest    |
 |                                                from the    |
 |   USB: camera            USB: ESP32            electronics |
 +-----------------------------------------------------------+
                 audience / examiner side
```

- **Box on the left edge.** Mark its position on the cardboard with tape. If it moves, the camera's white photo is no longer valid. Leave a slot under the front edge, or lift the box, to slide swatches in and out.
- **Breadboard in the middle**, OLED facing the audience. Tape the TCS wires flat along the cardboard: a pulled TCS wire reads `255 255 255` or "missing".
- **Ink glass on the right edge**, on its tray, as far from the electronics and the laptop as possible.
- **BH1750 uncovered.** It measures room light.

**Keep water and ink away from the electronics.** Only the sealed steel tip of the probe goes in the glass, never the bare sensor or its wire joints. Ink stains: keep it away from the swatches and the white paper.

## 4. Before your turn (about 15 minutes)

1. Turn on the hotspot. Connect the laptop and the phone to it.
2. Plug in both USB cables. The OLED shows `MQTT ok`.
3. Check the camera: open `http://10.83.144.145/` in a browser. It says `Lab-dip camera ready`. If not, try `http://labdip-cam.local/`, or read the new IP in the Serial Monitor on COM10.
4. **Camera white photo.** Put white paper under the box, covering the whole opening. Close the box and keep your hands off it, then run:
   ```
   python camera/cam_capture.py --host 10.83.144.145 --led --sample WHITE
   ```
   Note the file name it prints, for example `images\WHITE_20261113_002053.jpg`. A good white photo has no RGB value at 255 and no "(mostly glare)". If it shows glare, the bounce card under the flash has slipped.
5. **TCS check.** With the box still on the white paper, the TCS reads about `130 130 130` on the OLED or in the app. If it reads `255 255 255` or nothing, push its four wires back in.
6. **Bath set point.** Put the probe tip in the ink glass. Read the bath temperature on the OLED and send it from the app (Rig controls) as the bath set point, rounded, for example `27`. The bath alert is off.
7. Double-click **`start_demo.bat`** in the project folder. It opens two windows and runs both parts of the laptop side; leave them open for the whole demo and close them afterwards:
   - **LabDip logger:** `python logger.py --out demo_trials.csv`, saves every rig reading.
   - **LabDip camera:** `python camera/cam_capture.py --led --mqtt --every 10 --flat latest`, the camera in auto mode.
8. The camera window photographs the swatch every 10 s and sends the result to the app's camera card. It uses the newest white photo and finds the camera at `labdip-cam.local`, so no IP or file name is needed. While the rig is paused it takes no photos.
9. Open the app (version 2 or later) on the Live screen. The badge shows Online and the camera card appears within about 10 s.

## 5. During your turn

1. **Introduce the rig (30 s):** the measurement box with the camera and the colour sensor, the bath probe in the ink, the room sensors on the breadboard, and data going live to the app over MQTT.
2. **Target:** slide the reference swatch under the box. In the app, tap the sliders icon (Rig controls), then **Capture current colour as target**. ΔE drops to about 0.
3. **Test swatch:** in Rig controls, tap **Pause**. Swap in a different shade, set the Sample ID to `S2` and tap **Set**, then tap **Resume**. ΔE rises, and PASS / FAIL shows on the OLED and in the app. S1 keeps its own result, because nothing is logged while paused.
4. **Camera:** the camera card on the Live screen updates by itself every 10 s with the camera's Lab value, ΔE and levelness grid. To show the photo, open the newest `images\S2_..._annotated.jpg`. The grid is only meaningful when the swatch fills the whole camera view.
5. **Bath alert:** lift the probe out of the ink and hold the steel tip in your fist. Once it is more than 2 °C above the set point, the OLED shows `!! BATH TEMP ALERT !!` and the app shows its alert. Put it back and the alert clears.
6. **Room sensors:** breathe on the DHT11 for 2–3 s to raise the humidity. Shine the phone torch on the BH1750, then cover it.
7. **Data:** show `demo_trials.csv` growing in the terminal and the Trend chart in the app.

## 6. If something fails

| Problem | What to do |
|---|---|
| OLED shows `MQTT offline` | Check the hotspot is on and has mobile data. Restart the rig. |
| Network cannot be fixed in time | The rig still works alone: OLED readings, and commands over USB in the Serial Monitor (COM9, 115200 baud). |
| TCS reads `255 255 255`, or the OLED shows no colour reading | A TCS wire is loose. Re-seat the four wires with the USB unplugged. |
| Camera does not respond | It may have a new IP. Read it in the Serial Monitor on COM10, or use `labdip-cam.local`. |
| Camera colour looks green | `--flat` is missing or points to an old white photo. Take a new white photo (section 4, step 4). |
| White photo shows "(mostly glare)" or RGB 255 | The bounce card under the flash has slipped. Fix it and take the white photo again. |
| Levelness says "uneven" with very large cells | Part of the grid is on paper or a shadow. Centre a bigger swatch, or show only the Lab value. |
| Bath reads nothing | The probe's data wire or the 4.7 kΩ resistor is loose. |
| Probe in hand does not trigger the alert | Your hand may not warm it 2 °C quickly. Send a bath set point 3 °C below the current reading: the alert triggers at once. |
| Humidity does not move | The DHT11 updates slowly. Breathe on it again and wait 10 s, or move on. |
| App shows no data | Show the OLED and the laptop terminal instead. |
| Rig completely dead | Play a recorded video of the rig working, then explain the system from [explanation.md](explanation.md). |

## 7. What to say

- The ink glass stands in for the dye bath. In a real lab dip, the probe watches the bath temperature during dyeing, because a cold bath gives a weaker shade.
- Both colour sensors were calibrated against white paper: the camera with a white photo (`--flat`), the TCS with per-channel gains.
- The TCS and the camera agree on hue (98° and 101° on the yellow test swatch), but their lightness and chroma scales differ. Each sensor's ΔE is only measured against a target that the same sensor captured.
- The rig's ΔE is CIE76 against the captured swatch. The camera's ΔE is CIEDE2000. The two numbers are not comparable.
- The BH1750 measures room light outside the sealed box.
