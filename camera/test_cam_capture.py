"""Self-check for cam_capture.py on synthetic photos. Run: python camera/test_cam_capture.py (no camera needed)."""
import json, os, sys, tempfile
import numpy as np
import cv2

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import cam_capture as cc

H, W = 480, 640
PAPER, YELLOW = (235, 235, 230), (215, 205, 120)


def photo(patch=YELLOW, blotch=None, vignette=0.0, pale=False, size=0.6):
    """White paper with one patch filling `size` of the frame; optional darker blotch and lighting fall-off."""
    img = np.full((H, W, 3), PAPER, float)
    ph, pw = int(H * size), int(W * size * 0.8)
    y0, x0 = (H - ph) // 2, (W - pw) // 2
    img[y0:y0 + ph, x0:x0 + pw] = (228, 225, 200) if pale else patch
    if blotch:                                          # darker spot in the top-left cell of the patch
        img[y0 + ph // 12: y0 + ph // 4, x0 + pw // 12: x0 + pw // 4] = blotch
    if vignette:
        yy, xx = np.mgrid[0:H, 0:W]
        r = np.hypot((yy - H / 2) / (H / 2), (xx - W / 2) / (W / 2))
        img *= (1 - vignette * r ** 2)[..., None]
    rng = np.random.default_rng(0)
    img += rng.normal(0, 1.5, img.shape)                # sensor noise
    return cv2.cvtColor(np.clip(img, 0, 255).astype(np.uint8), cv2.COLOR_RGB2BGR)


def args(*extra):
    return cc.parse_args(list(extra))


def test_de2000_reference():
    # Sharma et al. (2005) test pair 1
    assert abs(cc.de2000((50, 2.6772, -79.7751), (50, 0, -82.7485)) - 2.0425) < 1e-3


def test_white_is_L100():
    L, a, b = cc.srgb_to_lab([255, 255, 255])
    assert abs(L - 100) < 0.01 and abs(a) < 0.01 and abs(b) < 0.01


def test_uniform_patch():
    res, ann = cc.measure(photo(), args())
    assert res["roi"] == "auto", res
    exp = cc.srgb_to_lab(YELLOW)
    assert cc.de2000((res["L"], res["a"], res["b"]), tuple(exp)) < 1.0, res
    assert res["levelness"] == "uniform", res
    assert ann.shape == (H, W, 3)


def test_blotch_is_found():
    res, _ = cc.measure(photo(blotch=(190, 175, 90)), args())
    assert res["levelness"] == "uneven", res
    d = [c[3] if c else -1 for c in res["cells"]]
    assert int(np.argmax(d)) == 0, d                     # top-left cell is the worst


def test_pale_patch_detected():
    res, _ = cc.measure(photo(pale=True), args())
    assert res["roi"] == "auto", res


def test_flat_field_removes_uneven_light():
    a = args()
    lit = photo(vignette=0.35)
    no_flat, _ = cc.measure(lit, a)
    paper = photo(vignette=0.35, size=0.0001)            # same light, no patch
    flat = cv2.GaussianBlur(cv2.cvtColor(paper, cv2.COLOR_BGR2RGB).astype(np.float32), (61, 61), 0).astype(float)
    with_flat, _ = cc.measure(lit, a, flat=flat)
    assert with_flat["white"] == "flat"
    assert with_flat["levelness_max"] < no_flat["levelness_max"], (with_flat["levelness_max"], no_flat["levelness_max"])
    assert with_flat["levelness"] == "uniform", with_flat


def test_flat_size_mismatch_is_clear_error():
    try:
        cc.measure(photo(), args(), flat=np.ones((10, 10, 3)))
    except RuntimeError as e:
        assert "--flat" in str(e)
    else:
        raise AssertionError("expected RuntimeError")


def test_no_patch_falls_back_to_centre():
    res, _ = cc.measure(photo(size=0.0001), args())
    assert res["roi"] == "centre", res


def test_no_auto_and_target():
    res, _ = cc.measure(photo(), args("--no-auto", "--grid", "4"), target=(80, 0, 40))
    assert res["roi"] == "centre" and res["grid"] == 4 and len(res["cells"]) == 16
    assert res["deltaE2000"] > 0


def test_payload_fits_app_limit():
    res, _ = cc.measure(photo(blotch=(190, 175, 90)), args("--grid", "4"), target=(80, 0, 40))
    payload = dict(sample_id="S-123456789012345678901", timestamp="2026-11-12T21:00:00", L=res["L"], a=res["a"],
                   b=res["b"], R=res["R"], G=res["G"], B=res["B"], grid=4, cells=res["cells"], roi="auto", white="flat",
                   levelness_max=99.99, levelness_mean=99.99, level_tol=1.0, uniform=False, target_L=80, target_a=0,
                   target_b=40, deltaE2000=99.99, sensor_L=99.99, sensor_a=-99.99, sensor_b=-99.99,
                   cam_vs_sensor_dE2000=99.99)
    assert len(json.dumps(payload, separators=(",", ":"))) < 2048


def test_rig_message_validation():
    rig = cc.Rig.__new__(cc.Rig)
    import threading
    rig.lock, rig.latest = threading.Lock(), None
    msg = type("M", (), {})
    for bad in [b"\xff\xfe", b"[1,2]", b"not json", b"{" + b"x" * 3000 + b"}"]:
        msg.payload = bad; rig._on_message(None, None, msg)
    assert rig.latest is None
    msg.payload = json.dumps({"sample_id": "S1", "L": 80, "a": -3, "b": 20, "target_set": True,
                              "target_L": 81, "target_a": -2, "target_b": 21}).encode()
    rig._on_message(None, None, msg)
    assert rig.latest["sample_id"] == "S1" and rig.latest["lab"] == [80, -3, 20] and rig.latest["target"] == [81, -2, 21]
    msg.payload = json.dumps({"sample_id": "S1;rm -rf", "L": 500, "a": 0, "b": 0, "target_set": False}).encode()
    rig._on_message(None, None, msg)
    assert rig.latest["sample_id"] is None and rig.latest["lab"] is None and rig.latest["target"] is None


def test_cam_topic():
    assert cc.cam_topic("rvu/team11/labdip/data") == "rvu/team11/labdip/cam"
    assert cc.cam_topic("data") == "cam"


def test_old_log_header_not_mixed():
    with tempfile.TemporaryDirectory() as d:
        old = os.path.join(d, "camera_log.csv")
        with open(old, "w") as f: f.write("sample_id,timestamp,cam_R\nS1,x,1\n")
        used = cc.write_log(old, {k: "" for k in cc.LOG_FIELDS})
        assert used.endswith("camera_log_v2.csv")
        assert cc.write_log(old, {k: "" for k in cc.LOG_FIELDS}) == used   # appends to the v2 file next time


def test_cli_bad_args():
    for bad in (["--roi", "2"], ["--white", "0", "1", "1"], ["--target", "120", "0", "0"], ["--sample", "a;b"],
                ["--white", "1", "1", "1", "--auto-white"], ["--topic", "a/#"]):
        try:
            cc.parse_args(bad)
        except SystemExit:
            continue
        raise AssertionError(f"accepted {bad}")


if __name__ == "__main__":
    import contextlib, io
    tests = [(n, f) for n, f in globals().items() if n.startswith("test_")]
    failed = 0
    for name, fn in tests:
        try:
            with contextlib.redirect_stderr(io.StringIO()): fn()
            print("ok  ", name)
        except Exception as e:
            failed += 1; print("FAIL", name, "-", repr(e))
    print(f"{len(tests) - failed}/{len(tests)} passed")
    sys.exit(1 if failed else 0)
