"""
Fake ESP32 lab-dip rig: publishes the same JSON as lab_dip_esp32.ino publishReading() so the app
can be tested without hardware. Includes bath excursions (bath_alert) and some malformed messages.

  python -m pip install paho-mqtt
  python tools/fake_rig.py                       # public broker, default topic, 3 s interval
  python tools/fake_rig.py --bad-rate 0.2 --interval 1
"""
import argparse, json, math, random, time
from datetime import datetime

import paho.mqtt.client as mqtt

ap = argparse.ArgumentParser()
ap.add_argument("--host", default="broker.hivemq.com")
ap.add_argument("--port", type=int, default=1883)
ap.add_argument("--topic", default="rvu/team11/labdip/data")
ap.add_argument("--interval", type=float, default=3.0, help="seconds between readings (firmware: 3)")
ap.add_argument("--per-sample", type=int, default=40, help="readings before switching to the next sample ID")
ap.add_argument("--bad-rate", type=float, default=0.05, help="fraction of messages that are malformed")
ap.add_argument("--no-target", action="store_true", help="publish target_set=false, like a rig without TARGET/CAPTURE")
a = ap.parse_args()

# (fabric, dye, target Lab) per sample; measured colour drifts toward the target over the dip
RECIPES = [
    ("cotton", "reactive_red", (41.0, 58.0, 30.0), 60.0),
    ("viscose", "reactive_green", (52.0, -45.0, 28.0), 60.0),
    ("polyester", "disperse_blue", (35.0, 12.0, -48.0), 120.0),
]


def lab_to_rgb(L, A, B):
    # Inverse of the firmware's rgb2lab (sRGB, D65)
    fy = (L + 16) / 116; fx = fy + A / 500; fz = fy - B / 200
    inv = lambda t: t ** 3 if t ** 3 > 0.008856 else (t - 16 / 116) / 7.787
    x, y, z = inv(fx) * 0.95047, inv(fy), inv(fz) * 1.08883
    r = 3.2404542 * x - 1.5371385 * y - 0.4985314 * z
    g = -0.9692660 * x + 1.8760108 * y + 0.0415560 * z
    b = 0.0556434 * x - 0.2040259 * y + 1.0572252 * z
    gam = lambda c: 12.92 * c if c <= 0.0031308 else 1.055 * c ** (1 / 2.4) - 0.055
    return [max(0, min(255, round(gam(c) * 255))) for c in (r, g, b)]


def reading(i):
    k = (i // a.per_sample) % len(RECIPES)
    step = i % a.per_sample
    fabric, dye, (tL, ta, tb), setpoint = RECIPES[k]
    # Start ~4 dE away and settle near the target, with sensor noise
    off = 4.0 * math.exp(-step / 12) + random.uniform(-0.25, 0.25)
    L, A, B = tL + off * 0.6, ta + off * 0.5, tb - off * 0.6
    R, G, Bl = lab_to_rgb(L, A, B)
    dE = math.sqrt((L - tL) ** 2 + (A - ta) ** 2 + (B - tb) ** 2)
    # Bath ramps to setpoint, with an excursion in the middle of each sample
    bath = setpoint - 15 * math.exp(-step / 2.5) + random.uniform(-0.6, 0.6)
    if a.per_sample * 0.45 < step < a.per_sample * 0.55:
        bath += 4.5
    d = {
        "sample_id": f"S{i // a.per_sample + 1:03d}",
        "timestamp": datetime.now().strftime("%Y-%m-%dT%H:%M:%S"),
        "deltaE": round(dE, 2), "R": R, "G": G, "B": Bl,
        "L": round(L, 2), "a": round(A, 2), "b": round(B, 2),
        "ambient_temp": round(27 + random.uniform(-0.5, 0.5), 1), "humidity": round(55 + random.uniform(-3, 3), 1),
        "light_lux": round(1180 + random.uniform(-60, 60)),
        "bath_alert": abs(bath - setpoint) > 2.0, "setpoint_c": setpoint,
        "target_L": tL, "target_a": ta, "target_b": tb, "target_set": not a.no_target,
        "fabric": fabric, "dye": dye, "dye_conc": 2.0,
    }
    if step % 17 != 16:  # now and then the DS18B20 "disconnects" and the firmware omits bath_temp
        d["bath_temp"] = round(bath, 1)
    return d


def bad_message():
    return random.choice([
        b"not json at all",
        json.dumps({"sample_id": "S999", "deltaE": 1.0}).encode(),  # missing required fields
        json.dumps({**reading(0), "R": 999}).encode(),  # out of range
        b'{"sample_id":"S001","deltaE":NaN}',
        json.dumps({**reading(0), "fabric": "x" * 3000}).encode(),  # oversize
    ])


client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id=f"labdip-fake-{random.randrange(1 << 24):06x}")
client.connect(a.host, a.port)
client.loop_start()
print(f"Publishing to {a.host}:{a.port} {a.topic} every {a.interval}s. Ctrl+C to stop.")
i = 0
try:
    while True:
        if random.random() < a.bad_rate:
            client.publish(a.topic, bad_message())
            print("  sent malformed message")
        else:
            d = reading(i)
            client.publish(a.topic, json.dumps(d))
            print(f"{d['sample_id']} dE {d['deltaE']:.2f} bath {d.get('bath_temp', '--')} alert {d['bath_alert']}")
            i += 1
        time.sleep(a.interval)
except KeyboardInterrupt:
    pass
finally:
    client.loop_stop()
    client.disconnect()
