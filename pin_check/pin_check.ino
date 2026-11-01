/*
 * Pin check: scans every safe GPIO for each sensor and compares
 * where it was found against the wiring in README.md.
 * Open Serial Monitor at 115200. Re-runs every 15 s.
 */
#include <Wire.h>
#include <OneWire.h>
#include <DallasTemperature.h>
#include <DHT.h>

// Skips strapping pins (0, 2, 12, 15), flash pins (6-11), UART (1, 3) and input-only pins (34-39)
const uint8_t PINS[] = {4, 5, 13, 14, 16, 17, 18, 19, 21, 22, 23, 25, 26, 27, 32, 33};
const int NPINS = sizeof(PINS);

struct I2cDev { const char *name; uint8_t addr; };
const I2cDev I2C_DEVS[] = {
  {"TCS34725", 0x29}, {"BH1750", 0x23}, {"BH1750 (ADDR high)", 0x5C},
  {"SSD1306 OLED", 0x3C}, {"SSD1306 OLED (alt)", 0x3D},
};
const int EXP_SDA = 21, EXP_SCL = 22, EXP_DHT = 27, EXP_DS18B20 = 4;

bool i2cProbe(uint8_t addr) {
  Wire.beginTransmission(addr);
  return Wire.endTransmission() == 0;
}

void checkI2c() {
  Serial.print("\n== Any I2C address on SDA=21 SCL=22: ");
  Wire.begin(EXP_SDA, EXP_SCL, 100000);
  int n = 0;
  for (uint8_t a = 0x08; a < 0x78; a++) if (i2cProbe(a)) { Serial.printf("0x%02X ", a); n++; }
  Wire.end();
  Serial.println(n ? "" : "none");

  Serial.println("\n== I2C (expected SDA=21 SCL=22) ==");
  for (const I2cDev &d : I2C_DEVS) {
    bool found = false;
    for (int i = 0; i < NPINS; i++) for (int j = 0; j < NPINS; j++) {
      if (i == j) continue;
      Wire.begin(PINS[i], PINS[j], 100000);
      bool hit = i2cProbe(d.addr);
      Wire.end();
      if (!hit) continue;
      found = true;
      bool ok = PINS[i] == EXP_SDA && PINS[j] == EXP_SCL;
      Serial.printf("  %-20s 0x%02X  SDA=%d SCL=%d  %s\n", d.name, d.addr, PINS[i], PINS[j],
                    ok ? "PASS" : "WRONG PINS");
    }
    if (!found) Serial.printf("  %-20s 0x%02X  not found on any pin pair\n", d.name, d.addr);
  }
}

void checkDs18b20() {
  Serial.println("\n== DS18B20 (expected GPIO 4, 4.7k pull-up) ==");
  // Idle level tells pull-up (HIGH) from missing pull-up / short to GND (LOW); presence pulse means a chip answered
  pinMode(EXP_DS18B20, INPUT);
  delay(5);
  int idle = digitalRead(EXP_DS18B20);
  OneWire probe(EXP_DS18B20);
  Serial.printf("  GPIO %d idle=%s presence=%s\n", EXP_DS18B20, idle ? "HIGH" : "LOW",
                probe.reset() ? "yes" : "no");
  bool found = false;
  for (int i = 0; i < NPINS; i++) {
    OneWire ow(PINS[i]);
    DallasTemperature ds(&ow);
    ds.begin();
    int n = ds.getDeviceCount();
    if (n == 0) continue;
    found = true;
    ds.requestTemperatures();
    Serial.printf("  %d device(s) on GPIO %d, %.2f C  %s\n", n, PINS[i], ds.getTempCByIndex(0),
                  PINS[i] == EXP_DS18B20 ? "PASS" : "WRONG PIN");
  }
  if (!found) Serial.println("  not found on any pin (check 4.7k pull-up)");
}

void checkDht() {
  Serial.println("\n== DHT11 (expected GPIO 27) ==");
  bool found = false;
  for (int i = 0; i < NPINS; i++) {
    if (PINS[i] == EXP_SDA || PINS[i] == EXP_SCL) continue;  // I2C lines idle high, never a DHT reply
    DHT dht(PINS[i], DHT11);
    dht.begin();
    delay(50);
    float h = dht.readHumidity(true), t = dht.readTemperature();
    if (isnan(h) || isnan(t)) continue;
    found = true;
    Serial.printf("  GPIO %d: %.1f C, %.0f %%RH  %s\n", PINS[i], t, h,
                  PINS[i] == EXP_DHT ? "PASS" : "WRONG PIN");
  }
  if (!found) Serial.println("  not found on any pin");
}

void setup() {
  Serial.begin(115200);
  delay(1000);
  Serial.println("\nLab dip pin check (wiring from README.md)");
}

void loop() {
  checkI2c();
  checkDs18b20();
  checkDht();
  Serial.println("\n-- done, rescanning in 15 s --");
  delay(15000);
}
