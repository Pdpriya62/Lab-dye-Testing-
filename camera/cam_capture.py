"""
Fetch a fabric photo from the ESP32-CAM, measure its colour, and log it.
Separate from the main pipeline: results go to camera_log.csv (same sample_id as your sensor data).

  pip install requests pillow numpy
  python cam_capture.py --sample S001                          # fetch from http://labdip-cam.local/capture
  python cam_capture.py --sample S001 --host 192.168.1.50      # use the IP if .local does not resolve
  python cam_capture.py --sample S001 --target 25 2 -22        # also compute Delta-E 2000 vs a target Lab
  python cam_capture.py --sample S001 --white 231 229 226      # correct with a white-card reading (see below)
  python cam_capture.py --sample S001 --file photo.jpg         # test on an existing image, no camera

White-card correction: photograph a white/grey card once, run with --file or --host, note the printed "mean RGB",
then pass it as --white R G B in later runs. This removes the camera's colour cast.
"""
import argparse, csv, io, os, datetime
import numpy as np
from PIL import Image

ap = argparse.ArgumentParser()
ap.add_argument("--sample", default="S001"); ap.add_argument("--host", default="labdip-cam.local")
ap.add_argument("--file"); ap.add_argument("--led", action="store_true")
ap.add_argument("--roi", type=float, default=0.3, help="central fraction of the image to measure (0.3 = middle 30%%)")
ap.add_argument("--target", nargs=3, type=float, metavar=("L", "A", "B")); ap.add_argument("--white", nargs=3, type=float)
ap.add_argument("--out-dir", default="images"); ap.add_argument("--log", default="camera_log.csv")
a = ap.parse_args()

# ---- get the image
if a.file:
    img = Image.open(a.file).convert("RGB")
else:
    import requests
    r = requests.get(f"http://{a.host}/capture" + ("?led=1" if a.led else ""), timeout=15); r.raise_for_status()
    img = Image.open(io.BytesIO(r.content)).convert("RGB")
os.makedirs(a.out_dir, exist_ok=True)
stamp = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
path = os.path.join(a.out_dir, f"{a.sample}_{stamp}.jpg"); img.save(path, quality=95)

# ---- measure colour in the central region (median resists glare/specks)
arr = np.asarray(img, dtype=float); h, w, _ = arr.shape
dh, dw = int(h * a.roi / 2), int(w * a.roi / 2)
roi = arr[h // 2 - dh: h // 2 + dh, w // 2 - dw: w // 2 + dw].reshape(-1, 3)
rgb = np.median(roi, axis=0)
raw_rgb = rgb.copy()
if a.white: rgb = np.clip(rgb * (255.0 / np.array(a.white)), 0, 255)

def srgb_to_lab(rgb):
    c = rgb / 255.0; lin = np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)
    X = (0.4124564*lin[0] + 0.3575761*lin[1] + 0.1804375*lin[2]) / 0.95047
    Y = (0.2126729*lin[0] + 0.7151522*lin[1] + 0.0721750*lin[2])
    Z = (0.0193339*lin[0] + 0.1191920*lin[1] + 0.9503041*lin[2]) / 1.08883
    f = lambda t: np.cbrt(t) if t > 0.008856 else 7.787 * t + 16 / 116
    return 116 * f(Y) - 16, 500 * (f(X) - f(Y)), 200 * (f(Y) - f(Z))

def de2000(l1, l2):
    L1, a1, b1 = l1; L2, a2, b2 = l2; rad, deg = np.radians, np.degrees
    C1, C2 = np.hypot(a1, b1), np.hypot(a2, b2); Cb = (C1 + C2) / 2
    G = 0.5 * (1 - np.sqrt(Cb**7 / (Cb**7 + 25**7))); a1p, a2p = (1 + G) * a1, (1 + G) * a2
    C1p, C2p = np.hypot(a1p, b1), np.hypot(a2p, b2)
    h1p, h2p = deg(np.arctan2(b1, a1p)) % 360, deg(np.arctan2(b2, a2p)) % 360
    dLp, dCp = L2 - L1, C2p - C1p
    if C1p * C2p == 0: dh_ = 0
    else:
        dh_ = h2p - h1p
        if dh_ > 180: dh_ -= 360
        elif dh_ < -180: dh_ += 360
    dHp = 2 * np.sqrt(C1p * C2p) * np.sin(rad(dh_ / 2)); Lbp, Cbp = (L1 + L2) / 2, (C1p + C2p) / 2
    if C1p * C2p == 0: hbp = h1p + h2p
    elif abs(h1p - h2p) <= 180: hbp = (h1p + h2p) / 2
    elif h1p + h2p < 360: hbp = (h1p + h2p + 360) / 2
    else: hbp = (h1p + h2p - 360) / 2
    T = 1 - 0.17*np.cos(rad(hbp-30)) + 0.24*np.cos(rad(2*hbp)) + 0.32*np.cos(rad(3*hbp+6)) - 0.20*np.cos(rad(4*hbp-63))
    dth = 30 * np.exp(-((hbp - 275) / 25) ** 2); Rc = 2 * np.sqrt(Cbp**7 / (Cbp**7 + 25**7))
    Sl = 1 + 0.015*(Lbp-50)**2 / np.sqrt(20 + (Lbp-50)**2); Sc = 1 + 0.045*Cbp; Sh = 1 + 0.015*Cbp*T
    Rt = -np.sin(rad(2*dth)) * Rc
    return float(np.sqrt((dLp/Sl)**2 + (dCp/Sc)**2 + (dHp/Sh)**2 + Rt*(dCp/Sc)*(dHp/Sh)))

L, A, B = srgb_to_lab(rgb)
dE = round(de2000((L, A, B), tuple(a.target)), 2) if a.target else ""
row = dict(sample_id=a.sample, timestamp=datetime.datetime.now().isoformat(timespec="seconds"),
           cam_R=int(rgb[0]), cam_G=int(rgb[1]), cam_B=int(rgb[2]), cam_L=round(L, 2), cam_a=round(A, 2), cam_b=round(B, 2),
           cam_deltaE2000=dE, image_file=path)
new = not os.path.exists(a.log)
with open(a.log, "a", newline="") as f:
    wr = csv.DictWriter(f, fieldnames=list(row)); 
    if new: wr.writeheader()
    wr.writerow(row)
print(f"saved {path}\nmean RGB (raw, before --white): {raw_rgb.astype(int).tolist()}")
print(f"RGB {rgb.astype(int).tolist()}  Lab {L:.1f} {A:.1f} {B:.1f}" + (f"  dE2000 vs target = {dE}" if a.target else ""))
