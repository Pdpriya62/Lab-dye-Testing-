"""
Compare live sensor readings with the reference dataset and show the deviation.

For every reading from the ESP32 (RGB/Lab, bath temp, humidity, ambient temp, light) it:
  1. finds the target shade in the dataset (closest named shade, or --shade) and computes Delta-E 2000
  2. predicts the Delta-E EXPECTED under the current conditions (model trained on the dataset)
  3. reports deviation = measured - expected, PASS/FAIL, which conditions are outside the dataset's normal range,
     and the most likely cause.

  pip install pandas numpy scikit-learn paho-mqtt
  python compare_live.py --dataset sample_results.csv --mqtt                 # live from the ESP32
  python compare_live.py --dataset sample_results.csv --replay trials_real.csv   # offline test, no hardware
  options: --shade Navy   --setpoint 60   --lux-target 1200   --pass-limit 1.0   --topic ...
"""
import argparse, csv, json, os
import numpy as np, pandas as pd
from sklearn.linear_model import LinearRegression

ap = argparse.ArgumentParser()
ap.add_argument("--dataset", default="sample_results.csv")
ap.add_argument("--mqtt", action="store_true"); ap.add_argument("--replay")
ap.add_argument("--host", default="broker.hivemq.com"); ap.add_argument("--topic", default="rvu/team11/labdip/data")
ap.add_argument("--shade"); ap.add_argument("--setpoint", type=float, default=60.0)
ap.add_argument("--lux-target", type=float, default=1200.0); ap.add_argument("--pass-limit", type=float, default=1.0)
ap.add_argument("--out", default="compared_log.csv")
a = ap.parse_args()

# ------------------------------------------------ CIEDE2000
def de2000(l1, l2):
    L1, a1, b1 = l1; L2, a2, b2 = l2
    rad, deg = np.radians, np.degrees
    C1, C2 = np.hypot(a1, b1), np.hypot(a2, b2); Cb = (C1 + C2) / 2
    G = 0.5 * (1 - np.sqrt(Cb**7 / (Cb**7 + 25**7)))
    a1p, a2p = (1 + G) * a1, (1 + G) * a2
    C1p, C2p = np.hypot(a1p, b1), np.hypot(a2p, b2)
    h1p, h2p = deg(np.arctan2(b1, a1p)) % 360, deg(np.arctan2(b2, a2p)) % 360
    dLp, dCp = L2 - L1, C2p - C1p
    if C1p * C2p == 0: dh = 0
    else:
        dh = h2p - h1p
        if dh > 180: dh -= 360
        elif dh < -180: dh += 360
    dHp = 2 * np.sqrt(C1p * C2p) * np.sin(rad(dh / 2))
    Lbp, Cbp = (L1 + L2) / 2, (C1p + C2p) / 2
    if C1p * C2p == 0: hbp = h1p + h2p
    elif abs(h1p - h2p) <= 180: hbp = (h1p + h2p) / 2
    elif h1p + h2p < 360: hbp = (h1p + h2p + 360) / 2
    else: hbp = (h1p + h2p - 360) / 2
    T = (1 - 0.17*np.cos(rad(hbp-30)) + 0.24*np.cos(rad(2*hbp)) + 0.32*np.cos(rad(3*hbp+6)) - 0.20*np.cos(rad(4*hbp-63)))
    dth = 30 * np.exp(-((hbp - 275) / 25) ** 2)
    Rc = 2 * np.sqrt(Cbp**7 / (Cbp**7 + 25**7))
    Sl = 1 + 0.015 * (Lbp-50)**2 / np.sqrt(20 + (Lbp-50)**2); Sc = 1 + 0.045*Cbp; Sh = 1 + 0.015*Cbp*T
    Rt = -np.sin(rad(2*dth)) * Rc
    return float(np.sqrt((dLp/Sl)**2 + (dCp/Sc)**2 + (dHp/Sh)**2 + Rt*(dCp/Sc)*(dHp/Sh)))

# ------------------------------------------------ learn from the reference dataset
ds = pd.read_csv(a.dataset)
shades = ds.groupby("shade_name")[["target_L", "target_a", "target_b"]].first()   # shade library
F = ["temp_under", "lux_dev_pct", "humidity", "ambient_temp"]
tr = pd.DataFrame({
    "temp_under": (ds["setpoint_c"] - ds["hold_mean_temp_c"]).clip(lower=0),
    "lux_dev_pct": (ds["light_lux_at_measure"] - a.lux_target) / a.lux_target * 100,
    "humidity": ds["humidity_at_measure_pct"], "ambient_temp": ds["ambient_temp_at_measure_c"]})
mu, sd = tr.mean(), tr.std().replace(0, 1)
model = LinearRegression().fit(((tr - mu) / sd).values, ds["delta_E_2000"].values)
good = tr[ds["delta_E_2000"] <= a.pass_limit]                                      # conditions seen in PASS samples
lo, hi = good.quantile(0.05), good.quantile(0.95)
print(f"Reference dataset: {len(ds)} samples, {len(shades)} shades. Listening...\n" if a.mqtt else f"Reference dataset: {len(ds)} samples, {len(shades)} shades.\n")

LABEL = {"temp_under": "bath below set point", "lux_dev_pct": "lighting vs target",
         "humidity": "humidity", "ambient_temp": "room temperature"}
FIELDS = ["sample_id", "timestamp", "shade", "deltaE2000", "expected_dE", "deviation", "result", "likely_cause", "flags"]
new = not os.path.exists(a.out)
fout = open(a.out, "a", newline=""); w = csv.DictWriter(fout, fieldnames=FIELDS)
if new: w.writeheader()

def evaluate(d):
    try: lab = (float(d["L"]), float(d["a"]), float(d["b"]))
    except (KeyError, TypeError, ValueError): return
    sp = float(d.get("setpoint_c") or a.setpoint)
    shade = a.shade or min(shades.index, key=lambda n: de2000(lab, tuple(shades.loc[n])))   # closest library shade
    dE = de2000(lab, tuple(shades.loc[shade]))
    x = pd.Series({
        "temp_under": max(sp - float(d["bath_temp"]), 0) if d.get("bath_temp") not in (None, "") else 0.0,
        "lux_dev_pct": (float(d["light_lux"]) - a.lux_target) / a.lux_target * 100,
        "humidity": float(d["humidity"]), "ambient_temp": float(d["ambient_temp"])})
    z = ((x - mu) / sd)
    expected = float(model.predict(z.values.reshape(1, -1))[0])
    contrib = pd.Series(model.coef_ * z.values, index=F)
    cause = contrib.idxmax() if contrib.max() > 0.15 else None
    flags = [f"{LABEL[f]} = {x[f]:.1f} (normal {lo[f]:.1f} to {hi[f]:.1f})" for f in F if x[f] < lo[f] or x[f] > hi[f]]
    res = "PASS" if dE <= a.pass_limit else "FAIL"
    row = dict(sample_id=d.get("sample_id", ""), timestamp=d.get("timestamp", ""), shade=shade, deltaE2000=round(dE, 2),
               expected_dE=round(expected, 2), deviation=round(dE - expected, 2), result=res,
               likely_cause=LABEL[cause] if cause else "random variation", flags="; ".join(flags))
    w.writerow(row); fout.flush()
    print(f"{row['sample_id']}  shade {shade}  dE2000 {dE:.2f} [{res}]  expected {expected:.2f}  deviation {dE-expected:+.2f}")
    if flags: print("   outside normal range:", "; ".join(flags))
    if res == "FAIL": print("   likely cause:", row["likely_cause"])
    return row

if a.replay:
    for _, r in pd.read_csv(a.replay).iterrows(): evaluate(r.to_dict())
elif a.mqtt:
    import paho.mqtt.client as mqtt
    def on_msg(c, u, m):
        try: evaluate(json.loads(m.payload))
        except Exception as e: print("skipped:", e)
    cl = mqtt.Client(); cl.on_connect = lambda c, *x: c.subscribe(a.topic); cl.on_message = on_msg
    cl.connect(a.host, 1883); cl.loop_forever()
else:
    ap.error("choose --mqtt (live) or --replay FILE.csv")
