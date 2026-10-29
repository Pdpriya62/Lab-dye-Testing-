"""
Subscribes to the ESP32's MQTT topic and appends every reading to a CSV file.
This is your REAL dataset collector for the trials (run it on any laptop on the internet).

  pip install paho-mqtt
  python logger.py --out trials_real.csv
"""
import argparse, csv, json, os, importlib

try:
  mqtt = importlib.import_module("paho.mqtt.client")
except ModuleNotFoundError as exc:
  if exc.name and exc.name.startswith("paho"):
    raise SystemExit("Missing dependency: install it with 'python -m pip install paho-mqtt'.") from exc
  raise

FIELDS = ["sample_id","timestamp","fabric","dye","dye_conc","deltaE","R","G","B","L","a","b",
          "ambient_temp","humidity","light_lux","bath_temp","setpoint_c","bath_alert"]

ap = argparse.ArgumentParser()
ap.add_argument("--host", default="broker.hivemq.com"); ap.add_argument("--topic", default="rvu/team11/labdip/data")
ap.add_argument("--out", default="trials_real.csv")
a = ap.parse_args()
new = not os.path.exists(a.out)
f = open(a.out, "a", newline=""); w = csv.DictWriter(f, fieldnames=FIELDS, extrasaction="ignore")
if new: w.writeheader()

def on_connect(c, u, fl, rc, *args): c.subscribe(a.topic); print("Listening on", a.topic)
def on_message(c, u, msg):
    try: d = json.loads(msg.payload)
    except Exception: return
    w.writerow(d); f.flush(); print(d.get("sample_id"), "dE", d.get("deltaE"), "bath", d.get("bath_temp"))

cl = mqtt.Client(); cl.on_connect = on_connect; cl.on_message = on_message
cl.connect(a.host, 1883); cl.loop_forever()
