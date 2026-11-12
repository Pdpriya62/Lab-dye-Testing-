/*
 * ESP32-CAM (AI-Thinker, OV2640) - STANDALONE fabric photo station for the Lab-Dip project.
 * Separate from the main sensor ESP32: it only needs Wi-Fi. The laptop fetches photos over HTTP.
 *
 * Arduino IDE: Board = "AI Thinker ESP32-CAM", Partition = default. No extra libraries needed
 * (esp_camera, WiFi, WebServer, ESPmDNS come with the ESP32 board package).
 *
 * Endpoints (same Wi-Fi as your laptop):
 *   http://labdip-cam.local/          status page
 *   http://labdip-cam.local/capture   returns a JPEG       (add ?led=1 to use the flash LED)
 *   (if .local does not resolve on your laptop, use the IP printed in the Serial Monitor)
 */
#include "esp_camera.h"
#include <WiFi.h>
#include <WebServer.h>
#include <ESPmDNS.h>

// ---------- USER SETTINGS ----------
const char* WIFI_SSID = "YOUR_WIFI";
const char* WIFI_PASS = "YOUR_PASSWORD";
const char* HOSTNAME  = "labdip-cam";
#define LOCK_COLOUR_SETTINGS 1   // 1 = fixed white balance + exposure (needed for repeatable colour); 0 = auto
#define MANUAL_EXPOSURE 150      // 0..1200, raise if images are dark, lower if bright (tune with a white card)
// -----------------------------------

// AI-Thinker ESP32-CAM pin map
#define PWDN_GPIO_NUM 32
#define RESET_GPIO_NUM -1
#define XCLK_GPIO_NUM 0
#define SIOD_GPIO_NUM 26
#define SIOC_GPIO_NUM 27
#define Y9_GPIO_NUM 35
#define Y8_GPIO_NUM 34
#define Y7_GPIO_NUM 39
#define Y6_GPIO_NUM 36
#define Y5_GPIO_NUM 21
#define Y4_GPIO_NUM 19
#define Y3_GPIO_NUM 18
#define Y2_GPIO_NUM 5
#define VSYNC_GPIO_NUM 25
#define HREF_GPIO_NUM 23
#define PCLK_GPIO_NUM 22
#define FLASH_LED_PIN 4

WebServer server(80);

bool initCamera() {
  camera_config_t c;
  c.ledc_channel = LEDC_CHANNEL_0; c.ledc_timer = LEDC_TIMER_0;
  c.pin_d0 = Y2_GPIO_NUM; c.pin_d1 = Y3_GPIO_NUM; c.pin_d2 = Y4_GPIO_NUM; c.pin_d3 = Y5_GPIO_NUM;
  c.pin_d4 = Y6_GPIO_NUM; c.pin_d5 = Y7_GPIO_NUM; c.pin_d6 = Y8_GPIO_NUM; c.pin_d7 = Y9_GPIO_NUM;
  c.pin_xclk = XCLK_GPIO_NUM; c.pin_pclk = PCLK_GPIO_NUM; c.pin_vsync = VSYNC_GPIO_NUM; c.pin_href = HREF_GPIO_NUM;
#if defined(ESP_ARDUINO_VERSION_MAJOR) && ESP_ARDUINO_VERSION_MAJOR >= 3
  c.pin_sccb_sda = SIOD_GPIO_NUM; c.pin_sccb_scl = SIOC_GPIO_NUM;
#else
  c.pin_sscb_sda = SIOD_GPIO_NUM; c.pin_sscb_scl = SIOC_GPIO_NUM;
#endif
  c.pin_pwdn = PWDN_GPIO_NUM; c.pin_reset = RESET_GPIO_NUM;
  c.xclk_freq_hz = 20000000;
  c.pixel_format = PIXFORMAT_JPEG;
  c.frame_size = FRAMESIZE_VGA;          // 640x480 is plenty for colour measurement
  c.jpeg_quality = 10;                   // lower number = better quality
  c.fb_count = 1;
  c.fb_location = psramFound() ? CAMERA_FB_IN_PSRAM : CAMERA_FB_IN_DRAM;
  c.grab_mode = CAMERA_GRAB_LATEST;
  if (esp_camera_init(&c) != ESP_OK) return false;

  sensor_t* s = esp_camera_sensor_get();
#if LOCK_COLOUR_SETTINGS
  s->set_whitebal(s, 0);        // auto white balance off
  s->set_awb_gain(s, 0);
  s->set_wb_mode(s, 0);         // 0 auto-preset; fixed while AWB is off. Try 1 (sunny) / 2 (cloudy) / 3 (office)
  s->set_exposure_ctrl(s, 0);   // auto exposure off
  s->set_aec2(s, 0);
  s->set_aec_value(s, MANUAL_EXPOSURE);
  s->set_gain_ctrl(s, 0);       // auto gain off
  s->set_agc_gain(s, 0);
  s->set_brightness(s, 0); s->set_contrast(s, 0); s->set_saturation(s, 0);
#endif
  return true;
}

void handleCapture() {
  bool led = server.hasArg("led") && server.arg("led") == "1";
  if (led) { pinMode(FLASH_LED_PIN, OUTPUT); digitalWrite(FLASH_LED_PIN, HIGH); delay(200); }
  camera_fb_t* fb = esp_camera_fb_get();  if (fb) esp_camera_fb_return(fb);   // discard stale frame
  fb = esp_camera_fb_get();
  if (led) digitalWrite(FLASH_LED_PIN, LOW);
  if (!fb) { server.send(500, "text/plain", "capture failed"); return; }
  server.setContentLength(fb->len);
  server.send(200, "image/jpeg", "");
  WiFiClient client = server.client();
  client.write(fb->buf, fb->len);
  esp_camera_fb_return(fb);
}

void handleRoot() {
  server.send(200, "text/plain", String("Lab-dip camera ready\nIP: ") + WiFi.localIP().toString() + "\nGET /capture  (optional ?led=1)\n");
}

void setup() {
  Serial.begin(115200);
  pinMode(FLASH_LED_PIN, OUTPUT); digitalWrite(FLASH_LED_PIN, LOW);
  if (!initCamera()) { Serial.println("Camera init FAILED - check ribbon cable and power"); while (true) delay(1000); }
  WiFi.mode(WIFI_STA); WiFi.begin(WIFI_SSID, WIFI_PASS);
  while (WiFi.status() != WL_CONNECTED) { delay(400); Serial.print("."); }
  Serial.println("\nConnected. IP: " + WiFi.localIP().toString());
  if (MDNS.begin(HOSTNAME)) Serial.printf("Also reachable at http://%s.local/capture\n", HOSTNAME);
  server.on("/", handleRoot); server.on("/capture", handleCapture); server.begin();
}

void loop() {
  if (WiFi.status() != WL_CONNECTED) { WiFi.reconnect(); delay(2000); }   // simple auto-reconnect
  server.handleClient();
}
