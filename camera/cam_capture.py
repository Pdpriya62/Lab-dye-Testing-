"""
Fetch a fabric photo from the ESP32-CAM, measure its colour with OpenCV, check dye levelness, and log it.

  pip install opencv-python numpy requests paho-mqtt
  python camera/cam_capture.py --host 192.168.1.50 --led                    # one capture
  python camera/cam_capture.py --host 192.168.1.50 --led --mqtt --loop      # demo mode (see below)
  python camera/cam_capture.py --file photo.jpg                             # test on a saved image, no camera
  python camera/cam_capture.py --host 192.168.1.50 --led --target 85 -3 22 # Delta-E 2000 vs a target Lab
  python camera/cam_capture.py --host 192.168.1.50 --led --white 231 229 226  # white-card correction

What it does with each photo:
  1. Finds the fabric patch automatically (it differs in colour from the white paper around it). If that
     fails it falls back to the centre --roi of the image and says so.
  2. Ignores glare (clipped pixels) and the frayed edges of the patch.
  3. Median colour of the patch -> CIE Lab (D65), and Delta-E 2000 against the target when one is known.
  4. Levelness: splits the patch into a --grid x --grid of cells and gives each cell's Delta-E 2000 against
     the patch average. Max cell value <= --level-tol means "uniform", above it "uneven" (patchy dyeing).
  5. Saves the photo and an annotated copy (patch outline + cell values) in --out-dir, appends a row to --log.

--mqtt: listens to the rig's data topic, so the sample ID and target come from the rig (what the app set),
  and the sensor's Lab is compared with the camera's. Publishes the result as JSON on the sibling topic
  `.../cam` (rvu/team11/labdip/cam by default) for the LabDip Monitor app. Not retained.
--loop: press Enter for the next capture, q then Enter to quit. A failed capture does not stop the loop.
--every N (needs --mqtt): hands-free demo mode. Captures and publishes every N seconds while the rig is
  publishing; skips while the rig is paused (PAUSE) or offline, so a fabric swap is never logged under the
  old sample ID. Ctrl+C to stop.
  python camera/cam_capture.py --led --mqtt --every 10 --flat latest

White reference (pick one):
  --flat latest                the newest images/WHITE_*.jpg.
  --flat images/WHITE_....jpg  best. Put plain white paper where the patch goes, run once with --sample WHITE --led,
                               then pass that saved photo. Corrects colour cast AND uneven LED light, so levelness
                               measures the dyeing, not the lighting. Retake it if the box, LED or camera moves.
  --white R G B                one colour for the whole image: the "raw median RGB" printed for the WHITE run.
  --auto-white                 uses the paper visible around the patch in every photo (white, evenly lit border only).

Self-check (no camera needed): python camera/test_cam_capture.py
"""
import argparse, csv, datetime, json, os, re, sys, threading, time
import numpy as np

try:
    import cv2
except ImportError:
    raise SystemExit("Missing dependency: python -m pip install opencv-python")

SAFE_TEXT = re.compile(r"[A-Za-z0-9 _.-]{1,24}")
LOG_FIELDS = ["sample_id", "timestamp", "cam_R", "cam_G", "cam_B", "cam_L", "cam_a", "cam_b", "target_L", "target_a",
              "target_b", "cam_deltaE2000", "sensor_L", "sensor_a", "sensor_b", "cam_vs_sensor_dE2000",
              "levelness_max", "levelness_mean", "levelness", "grid", "roi", "white", "image_file", "annotated_file"]


def parse_args(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--sample", help="sample ID (default: the rig's with --mqtt, else S001)")
    ap.add_argument("--host", default="labdip-cam.local", help="ESP32-CAM address; use the IP if .local fails")
    ap.add_argument("--file", help="measure this image instead of fetching from the camera")
    ap.add_argument("--led", action="store_true", help="use the ESP32-CAM flash LED")
    ap.add_argument("--roi", type=float, default=0.3, help="fallback: central fraction of the image (0.3 = middle 30%%)")
    ap.add_argument("--no-auto", action="store_true", help="skip patch detection, always use the centre --roi")
    ap.add_argument("--seg-thresh", type=float, default=6.0, help="colour distance from the paper that counts as fabric")
    ap.add_argument("--grid", type=int, default=3, choices=[2, 3, 4], help="levelness grid size")
    ap.add_argument("--level-tol", type=float, default=1.0, help="max cell Delta-E 2000 still called uniform")
    ap.add_argument("--target", nargs=3, type=float, metavar=("L", "A", "B"))
    ap.add_argument("--white", nargs=3, type=float, metavar=("R", "G", "B"), help="white-card raw RGB")
    ap.add_argument("--auto-white", action="store_true", help="use the paper border as the white reference")
    ap.add_argument("--flat", help="photo of plain white paper in the box (same camera settings): corrects colour "
                                   "cast AND uneven lighting. Best for levelness.")
    ap.add_argument("--out-dir", default="images")
    ap.add_argument("--log", default="camera_log.csv")
    ap.add_argument("--mqtt", action="store_true", help="read sample/target from the rig, publish results")
    ap.add_argument("--broker", default="broker.hivemq.com")
    ap.add_argument("--port", type=int, default=1883)
    ap.add_argument("--topic", default="rvu/team11/labdip/data", help="rig data topic; results go to .../cam")
    ap.add_argument("--loop", action="store_true", help="capture again on every Enter, q to quit")
    ap.add_argument("--every", type=float, metavar="SECONDS",
                    help="with --mqtt: capture automatically every N s while the rig is publishing (not when paused)")
    a = ap.parse_args(argv)
    if a.every is not None and (not a.mqtt or a.every < 3): ap.error("--every needs --mqtt and at least 3 seconds")
    if a.every is not None and a.loop: ap.error("use --every or --loop, not both")
    if not 0.05 <= a.roi <= 1.0: ap.error("--roi must be between 0.05 and 1")
    if a.white and any(v <= 0 or v > 255 for v in a.white): ap.error("--white values must be 1-255")
    if sum(map(bool, (a.white, a.auto_white, a.flat))) > 1: ap.error("use only one of --white, --auto-white, --flat")
    if a.target and not (0 <= a.target[0] <= 100 and all(-128 <= v <= 128 for v in a.target[1:])):
        ap.error("--target: L 0-100, a and b -128 to 128")
    if a.sample is not None and not SAFE_TEXT.fullmatch(a.sample):
        ap.error("--sample: 1-24 letters, digits, space, _ . -")
    if "+" in a.topic or "#" in a.topic: ap.error("--topic must not contain + or #")
    return a


# ---------------------------------------------------------------- colour maths (same as compare_live.py)
def srgb_to_lab(rgb):
    """sRGB 0-255, shape (..., 3) -> CIE Lab D65, same shape."""
    c = np.asarray(rgb, dtype=float) / 255.0
    lin = np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)
    X = (0.4124564 * lin[..., 0] + 0.3575761 * lin[..., 1] + 0.1804375 * lin[..., 2]) / 0.95047
    Y = (0.2126729 * lin[..., 0] + 0.7151522 * lin[..., 1] + 0.0721750 * lin[..., 2])
    Z = (0.0193339 * lin[..., 0] + 0.1191920 * lin[..., 1] + 0.9503041 * lin[..., 2]) / 1.08883
    f = lambda t: np.where(t > 0.008856, np.cbrt(t), 7.787 * t + 16 / 116)
    fx, fy, fz = f(X), f(Y), f(Z)
    return np.stack([116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)], axis=-1)


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


# ---------------------------------------------------------------- image in / out
def fetch(host, led):
    import requests
    try:
        r = requests.get(f"http://{host}/capture" + ("?led=1" if led else ""), timeout=15)
    except (requests.ConnectionError, requests.Timeout):
        raise RuntimeError(f"camera not reachable at {host}: check its IP in the Serial Monitor and that the laptop "
                           "is on the same Wi-Fi") from None
    r.raise_for_status()
    img = cv2.imdecode(np.frombuffer(r.content, np.uint8), cv2.IMREAD_COLOR)
    if img is None: raise RuntimeError("camera reply is not a JPEG image")
    return img


def load_file(path):
    # imdecode(fromfile) instead of imread: imread fails on Windows paths with non-ASCII characters
    img = cv2.imdecode(np.fromfile(path, np.uint8), cv2.IMREAD_COLOR) if os.path.exists(path) else None
    if img is None: raise RuntimeError(f"cannot read image {path}")
    return img


def save_jpg(path, bgr):
    ok, buf = cv2.imencode(".jpg", bgr, [cv2.IMWRITE_JPEG_QUALITY, 95])
    if not ok: raise RuntimeError(f"cannot encode {path}")
    buf.tofile(path)


# ---------------------------------------------------------------- analysis
def border_ring(h, w, frac=0.06):
    m = np.zeros((h, w), bool); t = max(2, int(min(h, w) * frac))
    m[:t, :] = m[-t:, :] = m[:, :t] = m[:, -t:] = True
    return m


def centre_mask(h, w, roi):
    m = np.zeros((h, w), bool); dh, dw = max(1, int(h * roi / 2)), max(1, int(w * roi / 2))
    m[h // 2 - dh: h // 2 + dh, w // 2 - dw: w // 2 + dw] = True
    return m


def find_fabric(rgb, thresh):
    """Boolean mask of the fabric patch, or None. rgb is float 0-255."""
    h, w, _ = rgb.shape
    small = cv2.GaussianBlur(rgb.astype(np.float32) / 255.0, (5, 5), 0)
    lab = cv2.cvtColor(small, cv2.COLOR_RGB2Lab)          # float input -> real Lab, L 0-100
    bg = np.median(lab[border_ring(h, w)], axis=0)
    d = np.sqrt((0.5 * (lab[..., 0] - bg[0])) ** 2 + (lab[..., 1] - bg[1]) ** 2 + (lab[..., 2] - bg[2]) ** 2)
    # Otsu on the distance map adapts to dark or textured backgrounds; --seg-thresh is the floor
    top = max(float(np.percentile(d, 99.5)), 1e-6)
    otsu, _ = cv2.threshold(np.clip(d / top * 255, 0, 255).astype(np.uint8), 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
    mask = (d > max(thresh, otsu / 255 * top)).astype(np.uint8) * 255
    # Wide opening cuts loose threads that would join the patch to its neighbours; holes are filled below
    k = max(5, (min(h, w) // 25) | 1)
    mask = cv2.morphologyEx(mask, cv2.MORPH_OPEN, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (k, k)))
    contours = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)[-2]
    if not contours: return None
    c = max(contours, key=cv2.contourArea)
    area = cv2.contourArea(c) / (h * w)
    if not 0.03 <= area <= 0.90: return None
    filled = np.zeros((h, w), np.uint8)
    cv2.drawContours(filled, [c], -1, 255, cv2.FILLED)
    # Drop the outer ~8% of the patch: frayed threads and paper show there
    e = max(3, int(np.sqrt(area * h * w) * 0.08) | 1)
    filled = cv2.erode(filled, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (e, e)))
    m = filled > 0
    return m if m.sum() >= 0.01 * h * w else None


def load_flat(path):
    """White-paper photo -> smooth per-pixel white reference (float RGB, >= 1)."""
    img = cv2.cvtColor(load_file(path), cv2.COLOR_BGR2RGB).astype(np.float32)
    k = max(3, (min(img.shape[:2]) // 8) | 1)
    return np.maximum(cv2.GaussianBlur(img, (k, k), 0).astype(float), 1.0)


def measure(bgr, a, target=None, flat=None):
    """Analyse one BGR photo. Returns a result dict and the annotated BGR image."""
    rgb_raw = cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB).astype(float)
    h, w, _ = rgb_raw.shape
    clipped = (rgb_raw >= 250).any(axis=2)                # glare / saturated pixels

    white, white_mode = None, "none"
    if flat is not None:
        if flat.shape != rgb_raw.shape: raise RuntimeError(f"--flat image is {flat.shape[1]}x{flat.shape[0]}, photo is {w}x{h}")
        white, white_mode = flat, "flat"                  # per-pixel white: fixes colour cast and uneven light
    elif a.white:
        white, white_mode = np.array(a.white, float), "manual"
    elif a.auto_white:
        ring = border_ring(h, w) & ~clipped
        if ring.sum() > 100:
            white, white_mode = np.median(rgb_raw[ring], axis=0), "auto"
            white = np.maximum(white, 1.0)
    rgb = np.clip(rgb_raw * (255.0 / white), 0, 255) if white is not None else rgb_raw

    mask = None if a.no_auto else find_fabric(rgb_raw, a.seg_thresh)
    roi_mode = "auto" if mask is not None else "centre"
    if mask is None: mask = centre_mask(h, w, a.roi)
    use = mask & ~clipped
    glare_note = ""
    if use.sum() < 200:                                   # almost all glare: measure anyway, but say so
        use, glare_note = mask, "mostly glare"
    raw_med = np.median(rgb_raw[use], axis=0)
    med = np.median(rgb[use], axis=0)
    lab = srgb_to_lab(med)

    # Levelness: grid over the patch's bounding box, each cell's median vs the patch median
    ys, xs = np.nonzero(mask)
    y0, y1, x0, x1 = ys.min(), ys.max() + 1, xs.min(), xs.max() + 1
    n = a.grid
    cells, vals = [], []
    for i in range(n):
        for j in range(n):
            cy0, cy1 = y0 + (y1 - y0) * i // n, y0 + (y1 - y0) * (i + 1) // n
            cx0, cx1 = x0 + (x1 - x0) * j // n, x0 + (x1 - x0) * (j + 1) // n
            cm = use[cy0:cy1, cx0:cx1]
            if cm.sum() < max(30, 0.25 * cm.size):       # corner cells mostly off the patch: skip, not a fake value
                cells.append(None); continue
            # Mean, not median: a blotch covering part of a cell must still move it (glare is already masked out)
            cl = srgb_to_lab(np.mean(rgb[cy0:cy1, cx0:cx1][cm], axis=0))
            d = de2000(tuple(lab), tuple(cl))
            cells.append([round(float(cl[0]), 2), round(float(cl[1]), 2), round(float(cl[2]), 2), round(d, 2)])
            vals.append(d)
    lev_max = round(max(vals), 2) if vals else None
    lev_mean = round(float(np.mean(vals)), 2) if vals else None

    res = dict(L=round(float(lab[0]), 2), a=round(float(lab[1]), 2), b=round(float(lab[2]), 2),
               R=int(round(med[0])), G=int(round(med[1])), B=int(round(med[2])),
               raw_rgb=[int(round(v)) for v in raw_med], grid=n, cells=cells,
               levelness_max=lev_max, levelness_mean=lev_mean,
               levelness=None if lev_max is None else ("uniform" if lev_max <= a.level_tol else "uneven"),
               roi=roi_mode, white=white_mode, note=glare_note)
    if target is not None:
        res["target"] = [round(float(v), 2) for v in target]
        res["deltaE2000"] = round(de2000(tuple(lab), tuple(target)), 2)

    # Annotated copy: patch outline, grid, per-cell Delta-E (green <= tol, amber <= 2x tol, red above)
    ann = bgr.copy()
    outline = cv2.findContours(mask.astype(np.uint8) * 255, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)[-2]
    cv2.drawContours(ann, outline, -1, (255, 255, 255), 2)
    fs = max(0.4, min(h, w) / 900)
    for idx, cell in enumerate(cells):
        i, j = divmod(idx, n)
        cy0, cy1 = y0 + (y1 - y0) * i // n, y0 + (y1 - y0) * (i + 1) // n
        cx0, cx1 = x0 + (x1 - x0) * j // n, x0 + (x1 - x0) * (j + 1) // n
        if cell is None:
            col, txt = (160, 160, 160), "-"
        else:
            d = cell[3]
            col = (80, 200, 80) if d <= a.level_tol else (0, 190, 255) if d <= 2 * a.level_tol else (60, 60, 230)
            txt = f"{d:.2f}"
        cv2.rectangle(ann, (int(cx0), int(cy0)), (int(cx1) - 1, int(cy1) - 1), col, 2)
        (tw, th), _ = cv2.getTextSize(txt, cv2.FONT_HERSHEY_SIMPLEX, fs, 2)
        org = (int((cx0 + cx1 - tw) / 2), int((cy0 + cy1 + th) / 2))
        cv2.putText(ann, txt, org, cv2.FONT_HERSHEY_SIMPLEX, fs, (0, 0, 0), 4, cv2.LINE_AA)
        cv2.putText(ann, txt, org, cv2.FONT_HERSHEY_SIMPLEX, fs, col, 2, cv2.LINE_AA)
    head = f"Lab {res['L']:.1f} {res['a']:.1f} {res['b']:.1f}"
    if "deltaE2000" in res: head += f"  dE00 {res['deltaE2000']:.2f}"
    if lev_max is not None: head += f"  level max {lev_max:.2f} {res['levelness']}"
    hs = min(fs, 0.9 * w / max(1, cv2.getTextSize(head, cv2.FONT_HERSHEY_SIMPLEX, 1, 1)[0][0]))  # fit the width
    (_, th), _ = cv2.getTextSize(head, cv2.FONT_HERSHEY_SIMPLEX, hs, 1)
    cv2.rectangle(ann, (0, 0), (w, th + 14), (0, 0, 0), cv2.FILLED)
    cv2.putText(ann, head, (8, th + 7), cv2.FONT_HERSHEY_SIMPLEX, hs, (255, 255, 255), 1, cv2.LINE_AA)
    return res, ann


# ---------------------------------------------------------------- MQTT (optional)
def cam_topic(data_topic):
    head = data_topic.rsplit("/", 1)[0] if "/" in data_topic else ""
    return f"{head}/cam" if head else "cam"


def _num(d, k, lo, hi):
    v = d.get(k)
    return float(v) if isinstance(v, (int, float)) and not isinstance(v, bool) and lo <= v <= hi else None


class Rig:
    """Keeps the latest rig reading from the data topic and publishes camera results. Payloads are untrusted."""

    def __init__(self, a):
        import paho.mqtt.client as mqtt
        self.topic, self.out_topic = a.topic, cam_topic(a.topic)
        self.latest, self.lock, self.ready = None, threading.Lock(), threading.Event()
        self.c = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id=f"labdip-cam-{os.getpid()}")
        self.c.on_connect = lambda c, u, f, rc, p=None: (c.subscribe(self.topic), self.ready.set())
        self.c.on_message = self._on_message
        self.c.connect(a.broker, a.port, keepalive=30)
        self.c.loop_start()
        if not self.ready.wait(10): raise RuntimeError(f"cannot reach MQTT broker {a.broker}:{a.port}")

    def _on_message(self, c, u, msg):
        if len(msg.payload) > 2048: return
        try: d = json.loads(msg.payload.decode("utf-8"))
        except (UnicodeDecodeError, ValueError): return
        if not isinstance(d, dict): return
        sid = d.get("sample_id")
        lab = [_num(d, "L", 0, 100), _num(d, "a", -128, 128), _num(d, "b", -128, 128)]
        tgt = [_num(d, "target_L", 0, 100), _num(d, "target_a", -128, 128), _num(d, "target_b", -128, 128)]
        r = dict(sample_id=sid if isinstance(sid, str) and SAFE_TEXT.fullmatch(sid) else None,
                 lab=lab if None not in lab else None,
                 target=tgt if d.get("target_set") is True and None not in tgt else None, at=time.time())
        with self.lock: self.latest = r

    def reading(self, wait=5.0):
        """Latest rig reading no older than 15 s, waiting up to `wait` s for one (the rig sends every 3 s)."""
        end = time.time() + wait
        while True:
            with self.lock: r = self.latest
            if r and time.time() - r["at"] <= 15: return r
            if time.time() >= end: return None
            time.sleep(0.2)

    def publish(self, payload):
        info = self.c.publish(self.out_topic, json.dumps(payload, separators=(",", ":")), qos=1, retain=False)
        info.wait_for_publish(5)
        return info.is_published()

    def close(self):
        self.c.loop_stop(); self.c.disconnect()


# ---------------------------------------------------------------- one capture
def write_log(path, row):
    if os.path.exists(path):
        with open(path, newline="") as f: header = next(csv.reader(f), None)
        if header != LOG_FIELDS:                      # older camera_log.csv layout: never mix columns
            root, ext = os.path.splitext(path); path = f"{root}_v2{ext or '.csv'}"
    new = not os.path.exists(path)
    with open(path, "a", newline="") as f:
        wr = csv.DictWriter(f, fieldnames=LOG_FIELDS)
        if new: wr.writeheader()
        wr.writerow(row)
    return path


def capture_once(a, rig=None, flat=None):
    rig_r = rig.reading() if rig else None
    if rig and rig_r is None: print("no reading from the rig in the last 15 s; using CLI values only")
    sample = a.sample or (rig_r and rig_r["sample_id"]) or "S001"
    target = a.target or (rig_r and rig_r["target"]) or None
    sensor = rig_r["lab"] if rig_r else None

    bgr = load_file(a.file) if a.file else fetch(a.host, a.led)
    res, ann = measure(bgr, a, target, flat)
    os.makedirs(a.out_dir, exist_ok=True)
    stamp = datetime.datetime.now()
    base = os.path.join(a.out_dir, f"{sample.replace(' ', '_')}_{stamp:%Y%m%d_%H%M%S}")
    save_jpg(base + ".jpg", bgr); save_jpg(base + "_annotated.jpg", ann)

    cam_vs_sensor = round(de2000((res["L"], res["a"], res["b"]), tuple(sensor)), 2) if sensor else None
    ts = stamp.isoformat(timespec="seconds")
    log = write_log(a.log, dict(
        sample_id=sample, timestamp=ts, cam_R=res["R"], cam_G=res["G"], cam_B=res["B"],
        cam_L=res["L"], cam_a=res["a"], cam_b=res["b"],
        target_L=target[0] if target else "", target_a=target[1] if target else "", target_b=target[2] if target else "",
        cam_deltaE2000=res.get("deltaE2000", ""),
        sensor_L=sensor[0] if sensor else "", sensor_a=sensor[1] if sensor else "", sensor_b=sensor[2] if sensor else "",
        cam_vs_sensor_dE2000=cam_vs_sensor if cam_vs_sensor is not None else "",
        levelness_max=res["levelness_max"] if res["levelness_max"] is not None else "",
        levelness_mean=res["levelness_mean"] if res["levelness_mean"] is not None else "",
        levelness=res["levelness"] or "", grid=res["grid"], roi=res["roi"], white=res["white"],
        image_file=base + ".jpg", annotated_file=base + "_annotated.jpg"))

    # ASCII only: the Windows console code page cannot print Greek Delta
    print(f"saved {base}.jpg and {base}_annotated.jpg, logged to {log}")
    print(f"raw median RGB (use as --white when the patch is white paper): {res['raw_rgb']}")
    print(f"[{sample}] camera Lab {res['L']:.2f} {res['a']:.2f} {res['b']:.2f}  RGB {res['R']} {res['G']} {res['B']}"
          f"  patch={res['roi']} white={res['white']}" + (f"  ({res['note']})" if res["note"] else ""))
    if "deltaE2000" in res: print(f"  dE2000 vs target {res['target']} = {res['deltaE2000']:.2f}")
    if sensor: print(f"  sensor Lab {sensor[0]:.2f} {sensor[1]:.2f} {sensor[2]:.2f}  camera vs sensor dE2000 = {cam_vs_sensor:.2f}")
    if res["levelness_max"] is not None:
        print(f"  levelness {res['grid']}x{res['grid']}: max {res['levelness_max']:.2f}, mean {res['levelness_mean']:.2f}"
              f" -> {res['levelness']} (tolerance {a.level_tol})")
    if res["roi"] == "centre" and not a.no_auto:
        print("  patch not found automatically; measured the centre region. Check the annotated image.")

    if rig:
        payload = dict(sample_id=sample, timestamp=ts, L=res["L"], a=res["a"], b=res["b"],
                       R=res["R"], G=res["G"], B=res["B"], grid=res["grid"], cells=res["cells"],
                       roi=res["roi"], white=res["white"])
        if res["levelness_max"] is not None:
            payload.update(levelness_max=res["levelness_max"], levelness_mean=res["levelness_mean"],
                           level_tol=a.level_tol, uniform=res["levelness"] == "uniform")
        if target: payload.update(target_L=target[0], target_a=target[1], target_b=target[2], deltaE2000=res["deltaE2000"])
        if sensor: payload.update(sensor_L=sensor[0], sensor_a=sensor[1], sensor_b=sensor[2], cam_vs_sensor_dE2000=cam_vs_sensor)
        ok = rig.publish(payload)
        print(f"  published to {rig.out_topic}" if ok else f"  MQTT publish to {rig.out_topic} not confirmed")
    return res


def latest_white(out_dir):
    """Newest WHITE_*.jpg photo in out_dir (annotated copies excluded), or None."""
    import glob
    files = [f for f in glob.glob(os.path.join(out_dir, "WHITE_*.jpg")) if not f.endswith("_annotated.jpg")]
    # by the WHITE_YYYYMMDD_HHMMSS name, not file time: a git checkout or copy resets file times
    return max(files, key=os.path.basename) if files else None


def auto_loop(a, rig, flat):
    """Capture every a.every s while the rig is publishing; a paused or offline rig sends nothing, so skip."""
    waiting = False
    rig.reading(wait=5)                                  # first rig message usually lands within 3 s of connecting
    while True:
        with rig.lock: r = rig.latest
        if r and time.time() - r["at"] <= 7:          # rig sends every 3 s; older means PAUSE or offline
            waiting = False
            try:
                capture_once(a, rig, flat)
            except Exception as e:                       # camera off, Wi-Fi drop, bad image: report, keep going
                print(f"capture failed: {e}")
        elif not waiting:
            print("rig paused or offline: not capturing (RESUME on the rig to continue)")
            waiting = True
        time.sleep(a.every)


def main(argv=None):
    a = parse_args(argv)
    if a.flat == "latest":
        a.flat = latest_white(a.out_dir)
        if not a.flat: raise SystemExit(f"--flat latest: no WHITE_*.jpg in {a.out_dir}; take one with --sample WHITE --led")
        print(f"white reference: {a.flat}")
    try:
        flat = load_flat(a.flat) if a.flat else None
    except RuntimeError as e:
        raise SystemExit(f"--flat: {e}")
    rig = None
    if a.mqtt:
        try:
            rig = Rig(a)
            print(f"MQTT: listening on {a.topic}, publishing results on {rig.out_topic}")
        except ImportError:
            raise SystemExit("Missing dependency: python -m pip install paho-mqtt")
        except (OSError, RuntimeError) as e:
            raise SystemExit(f"MQTT: {e}")
    try:
        if a.every:
            print(f"auto mode: capturing every {a.every:g} s while the rig publishes. Ctrl+C to stop.")
            auto_loop(a, rig, flat)
        while True:
            try:
                capture_once(a, rig, flat)
            except Exception as e:                       # camera off, Wi-Fi drop, bad image: report, keep looping
                if not a.loop: raise SystemExit(f"capture failed: {e}")
                print(f"capture failed: {e}")
            if not a.loop: break
            try:
                if input("\nEnter = next capture, q = quit: ").strip().lower() == "q": break
            except EOFError:
                break
    except KeyboardInterrupt:
        print("\nstopped")
    finally:
        if rig: rig.close()


if __name__ == "__main__":
    main()
