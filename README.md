# Multi-Sensor-IoT-Framework-for-Textile-Lab-Dip-Testing
An ESP32-based IoT system for textile lab-dip testing that monitors fabric colour, ambient temperature, humidity, lighting, and dye-bath temperature. QR codes link sensor readings to each fabric sample, enabling cloud-based tracking, process alerts, colour deviation analysis, and improved shade approval before bulk dyeing.

## How it works
```
ESP32 + sensors --MQTT JSON every 3 s--> broker --> logger.py / compare_live.py (laptop)
                                                \-> LabDip Monitor Android app (android-app/)
```

## Hardware
| Sensor | Measures | ESP32 pin |
|---|---|---|
| TCS34725 | fabric colour (RGB, converted to CIE Lab) | I2C SDA 21, SCL 22 |
| BH1750 | light-box illuminance (lux) | I2C SDA 21, SCL 22 |
| SSD1306 OLED | on-device display | I2C SDA 21, SCL 22 (0x3C) |
| DHT11 | ambient temperature, humidity | GPIO 27 |
| DS18B20 | dye-bath temperature | GPIO 4, 4.7k pull-up to 3.3 V |

## Repository layout
| Path | Purpose |
|---|---|
| `lab_dip_esp32/lab_dip_esp32.ino` | ESP32 firmware: reads sensors, computes ΔE (CIE76), OLED display, MQTT publish, serial commands |
| `logger.py` | subscribes to MQTT and saves real readings to `trials_real.csv` |
| `generate_labdip_data.py` | generates the **synthetic** reference dataset (`sample_results.csv`, `sensor_readings.csv`) |
| `analyze.py` | correlation, OLS + ANOVA, Linear Regression and Random Forest (5-fold CV), PASS/FAIL classifier, permutation importance; outputs to `results/` |
| `compare_live.py` | compares live or replayed readings with the dataset (CIEDE2000), predicts expected ΔE, flags out-of-range conditions |
| `results/` | plots and model metrics from `analyze.py` |
| `android-app/` | LabDip Monitor Android app: planning docs (PRD, architecture, UI, security). See [android-app/readme.md](android-app/readme.md) |

## Quick start
1. **Firmware**: open `lab_dip_esp32/lab_dip_esp32.ino` in Arduino IDE, install the libraries listed at the top of the file, set `WIFI_SSID` / `WIFI_PASS`, select "ESP32 Dev Module" and upload. Serial monitor at 115200 baud.
2. **Python**:
   ```
   pip install pandas numpy scipy statsmodels scikit-learn matplotlib paho-mqtt
   python logger.py --out trials_real.csv
   python analyze.py sample_results.csv
   python compare_live.py --dataset sample_results.csv --mqtt
   ```
3. **Android app**: see [android-app/readme.md](android-app/readme.md).

## Notes
- `sample_results.csv` and `sensor_readings.csv` are **synthetic** (simulated, not measured). Label them as synthetic in any report.
- The firmware computes ΔE with CIE76; the synthetic dataset and `compare_live.py` use CIEDE2000. Values are not directly comparable.
- The default broker `broker.hivemq.com` is public: anyone can read or publish on the topic. Use a private TLS broker for real trials.
- Never commit real Wi-Fi or broker credentials.
