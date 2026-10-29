/*
 * Multi-Sensor IoT Framework for Textile Lab-Dip Testing  (Team 11)
 * ESP32 firmware: reads sensors, computes Delta-E on-device, shows on OLED, publishes JSON via MQTT.
 *
 * Libraries (Arduino Library Manager):
 *   Adafruit TCS34725, DHT sensor library (Adafruit), Adafruit Unified Sensor, BH1750 (by Christopher Laws),
 *   OneWire, DallasTemperature, Adafruit SSD1306, Adafruit GFX, PubSubClient, ArduinoJson
 *
 * Wiring: I2C SDA=GPIO21, SCL=GPIO22 (TCS34725, BH1750, OLED)
 *         DHT11 data=GPIO27
 *         DS18B20 data=GPIO4 with 4.7k pull-up to 3.3V
 *
 * Serial commands (115200 baud, newline terminated):
 *   ID S001            set current Sample ID
 *   CAPTURE            store the CURRENT colour reading as the target shade
 *   TARGET 52.3 10.1 -4.2   set target Lab values manually
 *   BATH 60            set recipe bath temperature (C); alert if outside +/- BATH_TOL
 *   FABRIC cotton / DYE reactive_red / CONC 2.0   optional metadata added to every JSON
 */
#include <Wire.h>
#include <WiFi.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>
#include <Adafruit_TCS34725.h>
#include <DHT.h>
#include <BH1750.h>
#include <OneWire.h>
#include <DallasTemperature.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include <math.h>
#include <time.h>

// ---------- USER SETTINGS ----------
const char* WIFI_SSID = "YOUR_WIFI";
const char* WIFI_PASS = "YOUR_PASSWORD";
const char* MQTT_HOST = "broker.hivemq.com";   // public test broker; use your own for real trials
const int   MQTT_PORT = 1883;
const char* MQTT_TOPIC = "rvu/team11/labdip/data";   // make this unique to avoid other people's traffic
const unsigned long SAMPLE_INTERVAL_MS = 3000;       // 2-5 s as per methodology
const float BATH_TOL = 2.0;                          // +/- C alert window
// Per-channel calibration gains (fill in after Priya's calibration against reference swatches)
float CAL_R = 1.0, CAL_G = 1.0, CAL_B = 1.0;
// -----------------------------------

#define ONE_WIRE_PIN 4
#define OLED_W 128
#define OLED_H 64

Adafruit_TCS34725 tcs(TCS34725_INTEGRATIONTIME_154MS, TCS34725_GAIN_4X);
#define DHT_PIN 27
#define DHT_TYPE DHT11
DHT dht(DHT_PIN, DHT_TYPE);
BH1750 lightMeter;
OneWire oneWire(ONE_WIRE_PIN);
DallasTemperature ds18(&oneWire);
Adafruit_SSD1306 oled(OLED_W, OLED_H, &Wire, -1);
WiFiClient wifiClient;
PubSubClient mqtt(wifiClient);

String sampleId = "S001", fabric = "", dye = "";
float conc = 0;
float targetL = 50, targetA = 0, targetB = 0;
float bathSet = 60.0;
bool haveBath = false;
unsigned long lastSample = 0;

struct Reading { float r, g, b, L, a, bb, dE, ambT, hum, press, lux, bath; bool alert; };
Reading cur;

// ---------- Colour maths: sRGB -> XYZ(D65) -> CIE Lab, then CIE76 Delta-E ----------
float lin(float c) { c /= 255.0; return (c <= 0.04045) ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4); }
float fLab(float t) { return (t > 0.008856) ? cbrt(t) : (7.787 * t + 16.0 / 116.0); }
void rgb2lab(float R, float G, float B, float &L, float &a, float &b) {
  float r = lin(R), g = lin(G), bl = lin(B);
  float X = (0.4124564 * r + 0.3575761 * g + 0.1804375 * bl) / 0.95047;
  float Y = (0.2126729 * r + 0.7151522 * g + 0.0721750 * bl) / 1.00000;
  float Z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * bl) / 1.08883;
  float fx = fLab(X), fy = fLab(Y), fz = fLab(Z);
  L = 116.0 * fy - 16.0; a = 500.0 * (fx - fy); b = 200.0 * (fy - fz);
}
float deltaE76(float L1, float a1, float b1, float L2, float a2, float b2) {
  return sqrt((L1 - L2) * (L1 - L2) + (a1 - a2) * (a1 - a2) + (b1 - b2) * (b1 - b2));
}

// ---------- Sensors ----------
void readAll() {
  uint16_t r, g, b, c;
  tcs.getRawData(&r, &g, &b, &c);
  float R = 0, G = 0, B = 0;
  if (c > 0) {  // normalise by clear channel -> 0..255
    R = constrain((float)r / c * 255.0 * CAL_R, 0, 255);
    G = constrain((float)g / c * 255.0 * CAL_G, 0, 255);
    B = constrain((float)b / c * 255.0 * CAL_B, 0, 255);
  }
  cur.r = R; cur.g = G; cur.b = B;
  rgb2lab(R, G, B, cur.L, cur.a, cur.bb);
  cur.dE = deltaE76(cur.L, cur.a, cur.bb, targetL, targetA, targetB);
  float t_amb = dht.readTemperature(), h_amb = dht.readHumidity();
  if (!isnan(t_amb)) cur.ambT = t_amb;   // keep last good value if a read fails
  if (!isnan(h_amb)) cur.hum = h_amb;
  cur.lux = lightMeter.readLightLevel();
  ds18.requestTemperatures();
  float t = ds18.getTempCByIndex(0);
  cur.bath = (t == DEVICE_DISCONNECTED_C) ? NAN : t;
  cur.alert = haveBath && !isnan(cur.bath) && fabs(cur.bath - bathSet) > BATH_TOL;
}

// ---------- Display ----------
void showOLED() {
  oled.clearDisplay(); oled.setTextSize(1); oled.setTextColor(SSD1306_WHITE);
  oled.setCursor(0, 0);  oled.printf("ID:%s dE:%.1f", sampleId.c_str(), cur.dE);
  oled.setCursor(0, 11); oled.printf("RGB %d %d %d", (int)cur.r, (int)cur.g, (int)cur.b);
  oled.setCursor(0, 22); oled.printf("Bath: %.1f C", cur.bath);
  oled.setCursor(0, 33); oled.printf("Amb: %.1fC %.0f%%", cur.ambT, cur.hum);
  oled.setCursor(0, 44); oled.printf("Light: %.0f lx", cur.lux);
  oled.setCursor(0, 55); oled.print(cur.alert ? "!! BATH TEMP ALERT !!" : (mqtt.connected() ? "MQTT ok" : "MQTT offline"));
  oled.display();
}

// ---------- Network ----------
void connectWiFi() {
  if (WiFi.status() == WL_CONNECTED) return;
  WiFi.mode(WIFI_STA); WiFi.begin(WIFI_SSID, WIFI_PASS);
  unsigned long t0 = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - t0 < 10000) delay(250);
  if (WiFi.status() == WL_CONNECTED) configTime(19800, 0, "pool.ntp.org");  // IST = UTC+5:30
}
void connectMQTT() {
  if (mqtt.connected() || WiFi.status() != WL_CONNECTED) return;
  String cid = "labdip-" + String((uint32_t)ESP.getEfuseMac(), HEX);
  mqtt.connect(cid.c_str());
}
String isoTime() {
  struct tm t;
  if (!getLocalTime(&t, 50)) return "1970-01-01T00:00:00";
  char buf[25]; strftime(buf, sizeof(buf), "%Y-%m-%dT%H:%M:%S", &t); return String(buf);
}
void publishReading() {
  StaticJsonDocument<512> d;
  d["sample_id"] = sampleId; d["timestamp"] = isoTime();
  d["deltaE"] = round(cur.dE * 100) / 100.0;
  d["R"] = (int)cur.r; d["G"] = (int)cur.g; d["B"] = (int)cur.b;
  d["L"] = round(cur.L * 100) / 100.0; d["a"] = round(cur.a * 100) / 100.0; d["b"] = round(cur.bb * 100) / 100.0;
  d["ambient_temp"] = round(cur.ambT * 10) / 10.0; d["humidity"] = round(cur.hum * 10) / 10.0;
  d["light_lux"] = round(cur.lux);
  if (!isnan(cur.bath)) d["bath_temp"] = round(cur.bath * 10) / 10.0;
  d["bath_alert"] = cur.alert;
  d["setpoint_c"] = bathSet;
  if (fabric.length()) d["fabric"] = fabric;
  if (dye.length()) d["dye"] = dye;
  if (conc > 0) d["dye_conc"] = conc;
  char out[512]; size_t n = serializeJson(d, out, sizeof(out));
  mqtt.publish(MQTT_TOPIC, (const uint8_t*)out, n, false);
  Serial.println(out);  // also print to serial so you can log without Wi-Fi
}

// ---------- Serial commands ----------
void handleSerial() {
  if (!Serial.available()) return;
  String line = Serial.readStringUntil('\n'); line.trim();
  if (!line.length()) return;
  String cmd = line.substring(0, line.indexOf(' ') < 0 ? line.length() : line.indexOf(' ')); cmd.toUpperCase();
  String arg = line.indexOf(' ') < 0 ? "" : line.substring(line.indexOf(' ') + 1); arg.trim();
  if (cmd == "ID")       { sampleId = arg; Serial.println("OK sample=" + sampleId); }
  else if (cmd == "CAPTURE") { targetL = cur.L; targetA = cur.a; targetB = cur.bb; Serial.printf("OK target Lab=%.2f %.2f %.2f\n", targetL, targetA, targetB); }
  else if (cmd == "TARGET")  { sscanf(arg.c_str(), "%f %f %f", &targetL, &targetA, &targetB); Serial.println("OK target set"); }
  else if (cmd == "BATH")    { bathSet = arg.toFloat(); haveBath = true; Serial.printf("OK bath=%.1f +/- %.1f\n", bathSet, BATH_TOL); }
  else if (cmd == "FABRIC")  { fabric = arg; Serial.println("OK"); }
  else if (cmd == "DYE")     { dye = arg; Serial.println("OK"); }
  else if (cmd == "CONC")    { conc = arg.toFloat(); Serial.println("OK"); }
  else Serial.println("Unknown command");
}

void setup() {
  Serial.begin(115200);
  Wire.begin(21, 22);
  if (!tcs.begin())  Serial.println("TCS34725 not found");
  dht.begin();
  if (!lightMeter.begin()) Serial.println("BH1750 not found");
  ds18.begin();
  if (!oled.begin(SSD1306_SWITCHCAPVCC, 0x3C)) Serial.println("OLED not found");
  oled.clearDisplay(); oled.setCursor(0, 0); oled.setTextColor(SSD1306_WHITE); oled.println("Connecting..."); oled.display();
  connectWiFi();
  mqtt.setServer(MQTT_HOST, MQTT_PORT);
  Serial.println("Ready. Commands: ID, CAPTURE, TARGET, BATH, FABRIC, DYE, CONC");
}

void loop() {
  handleSerial();
  if (WiFi.status() != WL_CONNECTED) connectWiFi();   // auto-reconnect
  connectMQTT();
  mqtt.loop();
  if (millis() - lastSample >= SAMPLE_INTERVAL_MS) {
    lastSample = millis();
    readAll(); showOLED(); publishReading();
  }
}
