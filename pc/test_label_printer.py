#!/usr/bin/env python3
"""label_printer.py testlari (printer va internet shart emas):  python pc/test_label_printer.py"""
import base64
import importlib.util
import io
import json
import os
import shutil
import socket
import struct
import tempfile
import threading
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
KEY = "a" * 16 + "B" * 16


def load():
    spec = importlib.util.spec_from_file_location("label_printer", os.path.join(HERE, "label_printer.py"))
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m


LP = load()


def png(w=40, h=24, black_box=True):
    from PIL import Image, ImageDraw
    img = Image.new("L", (w, h), 255)
    if black_box:
        ImageDraw.Draw(img).rectangle((0, 0, 7, h - 1), fill=0)
    b = io.BytesIO()
    img.save(b, "PNG")
    return b.getvalue()


def write_ini(tmp, extra="", mode="file"):
    p = os.path.join(tmp, "forwarder.ini")
    with open(p, "w", encoding="utf-8") as f:
        f.write("[firebase]\nurl = savdo-test.firebaseio.com/\nsecret = S3cret\n\n[labels]\nkey = %s\nprinter = Xprinter XP-365B\nmode = %s\nport = 0\nbind = 127.0.0.1\nout_dir = %s\n%s"
                % (KEY, mode, os.path.join(tmp, "out"), extra))
    return p


class FakeCloud:
    def __init__(self):
        self.data = {}
        self.calls = []

    def _node(self, path, create=False):
        d = self.data
        parts = [p for p in path.strip("/").split("/") if p]
        for p in parts[:-1]:
            if p not in d:
                if not create:
                    return None, None
                d[p] = {}
            d = d[p]
        return d, parts[-1]

    def call(self, method, path, body=None, timeout=60, **q):
        self.calls.append((method, path, q))
        d, k = self._node(path, create=method == "PUT")
        if method == "GET":
            v = None if d is None else d.get(k)
            if q.get("shallow") == "true" and isinstance(v, dict):
                return {kk: True for kk in v}
            return json.loads(json.dumps(v))
        if method == "PUT":
            d[k] = body
            return None
        if method == "DELETE":
            if d is not None:
                d.pop(k, None)
            return None
        raise AssertionError(method)


class Base(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.cfg = LP.Config(write_ini(self.tmp))
        self.printed = []
        self.printer = LP.Printer(self.cfg, done_path=os.path.join(self.tmp, "done.json"),
                                  backend=lambda name, labels: self.printed.append((name, [(len(p), n) for p, n in labels])))
        LP.find_printer = lambda name, names=None: "Xprinter XP-365B"

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def job(self, job_id="j1", n=2, copies=3, key=KEY):
        return {"v": 1, "op": "print", "key": key, "id": job_id,
                "labels": [{"png": base64.b64encode(png()).decode(), "copies": copies, "name": "T%d" % i} for i in range(n)]}


class ConfigTest(Base):
    def test_config(self):
        self.assertEqual(self.cfg.url, "https://savdo-test.firebaseio.com")
        self.assertTrue(self.cfg.has_cloud)
        self.assertEqual(self.cfg.mode, "file")

    def test_bad_key_rejected(self):
        p = os.path.join(self.tmp, "bad.ini")
        with open(p, "w") as f:
            f.write("[labels]\nkey = short\n")
        with self.assertRaises(SystemExit):
            LP.Config(p)

    def test_find_printer(self):
        f = load().find_printer
        names = ["Microsoft Print to PDF", "Xprinter XP-365B", "EPSON L3150"]
        self.assertEqual(f("", names), "Xprinter XP-365B")
        self.assertEqual(f("epson", names), "EPSON L3150")
        self.assertIsNone(f("zebra", names))
        self.assertIsNone(f("", ["Microsoft Print to PDF"]))


class RequestTest(Base):
    def test_print_and_dedup(self):
        r = LP.handle_request(self.cfg, self.printer, self.job())
        self.assertEqual(r, {"ok": True, "printed": 6, "printer": "Xprinter XP-365B", "duplicate": False})
        self.assertEqual(len(self.printed), 1)
        r2 = LP.handle_request(self.cfg, self.printer, self.job())
        self.assertTrue(r2["ok"] and r2["duplicate"])
        self.assertEqual(len(self.printed), 1, "takror chop etilmasligi kerak")
        # bulutdagi bo'lak (j1_0) ham takrorlanmaydi
        LP.handle_request(self.cfg, self.printer, self.job("j1_0"))
        self.assertEqual(len(self.printed), 1)
        # qayta ishga tushgandan keyin ham eslaydi
        p2 = LP.Printer(self.cfg, done_path=os.path.join(self.tmp, "done.json"), backend=lambda n, l: self.printed.append(n))
        self.assertTrue(p2.seen("j1"))

    def test_bad_key_and_input(self):
        self.assertEqual(LP.handle_request(self.cfg, self.printer, self.job(key="x" * 32))["error"], "bad_key")
        self.assertEqual(LP.handle_request(self.cfg, self.printer, self.job(job_id="../x"))["error"], "bad_id")
        j = self.job()
        j["labels"][0]["png"] = base64.b64encode(b"not a png").decode()
        self.assertTrue(LP.handle_request(self.cfg, self.printer, j)["error"].startswith("bad_request"))
        j = self.job(n=0)
        self.assertTrue(LP.handle_request(self.cfg, self.printer, j)["error"].startswith("bad_request"))
        self.assertEqual(LP.handle_request(self.cfg, self.printer, {"key": KEY, "op": "zzz"})["error"], "bad_op")
        self.assertEqual(self.printed, [])

    def test_copies_clamped(self):
        LP.handle_request(self.cfg, self.printer, self.job(n=1, copies=5000))
        self.assertEqual(self.printed[0][1][0][1], 999)

    def test_printer_error(self):
        def boom(name, labels):
            raise RuntimeError("qog'oz tugadi")
        self.printer.backend = boom
        r = LP.handle_request(self.cfg, self.printer, self.job())
        self.assertFalse(r["ok"])
        self.assertTrue(r["error"].startswith("printer"))
        self.assertFalse(self.printer.seen("j1"), "xato bo'lsa ish bajarilgan hisoblanmaydi")

    def test_file_mode(self):
        p = LP.Printer(self.cfg, done_path=os.path.join(self.tmp, "d2.json"))
        r = LP.handle_request(self.cfg, p, self.job("f1", n=2, copies=1))
        self.assertTrue(r["ok"])
        files = sorted(os.listdir(os.path.join(self.tmp, "out")))
        self.assertEqual(files, ["f1_001_x1.png", "f1_002_x1.png"])

    def test_info(self):
        i = LP.handle_request(self.cfg, self.printer, {"key": KEY, "op": "info"})
        self.assertTrue(i["ok"])
        self.assertEqual(i["printer"], "Xprinter XP-365B")
        self.assertEqual(i["mode"], "file")


class TsplTest(unittest.TestCase):
    def test_bytes(self):
        data = LP.tspl_bytes(png(40, 24), 3, dpi=203, gap_mm=2)
        head, rest = data.split(b"BITMAP ", 1)
        self.assertIn(b"SIZE 5 mm,3 mm\r\n", head)
        self.assertIn(b"GAP 2 mm,0 mm\r\n", head)
        self.assertTrue(head.endswith(b"CLS\r\n"))
        self.assertTrue(rest.startswith(b"0,0,5,24,0,"))  # 40 nuqta = 5 bayt, 24 qator
        bmp = rest[len(b"0,0,5,24,0,"):][:5 * 24]
        self.assertEqual(bmp[0], 0x00)   # chapdagi 8 nuqta qora -> 0 bitlar (TSPL'da 0 = chop)
        self.assertEqual(bmp[1], 0xFF)   # oq -> 1
        self.assertTrue(data.endswith(b"\r\nPRINT 1,3\r\n"))
        inv = LP.tspl_bytes(png(40, 24), 1, invert=True)
        self.assertEqual(inv.split(b"BITMAP 0,0,5,24,0,")[1][0], 0xFF)

    def test_padding(self):
        data = LP.tspl_bytes(png(36, 10), 1)
        self.assertIn(b"BITMAP 0,0,5,10,0,", data)
        bits = data.split(b"BITMAP 0,0,5,10,0,")[1]
        self.assertEqual(bits[4] & 0x0F, 0x0F, "qo'shilgan to'ldiruvchi nuqtalar oq")


class LanTest(Base):
    def setUp(self):
        super().setUp()
        self.srv = LP.make_server(self.cfg)
        self.stop = threading.Event()
        self.t = threading.Thread(target=LP.serve, args=(self.cfg, self.printer, self.srv, self.stop), daemon=True)
        self.t.start()

    def tearDown(self):
        self.stop.set()
        self.srv.close()
        super().tearDown()

    def rpc(self, body, raw=None):
        s = socket.create_connection(("127.0.0.1", self.srv.getsockname()[1]), timeout=10)
        try:
            data = raw if raw is not None else json.dumps(body).encode()
            s.sendall(b"SLBL" + struct.pack(">I", len(data)) + data)
            self.assertEqual(LP.read_exact(s, 4), b"SLBL")
            n = struct.unpack(">I", LP.read_exact(s, 4))[0]
            return json.loads(LP.read_exact(s, n))
        finally:
            s.close()

    def test_framing(self):
        r = self.rpc(self.job("lan1", n=3, copies=2))
        self.assertEqual(r["printed"], 6)
        self.assertEqual(self.rpc({"key": KEY, "op": "info"})["printer"], "Xprinter XP-365B")
        self.assertEqual(self.rpc(None, raw=b"{not json")["error"], "bad_json")
        self.assertEqual(self.rpc({"key": "nope", "op": "info"})["error"], "bad_key")

    def test_big_job(self):
        j = self.job("big", n=1, copies=1)
        j["labels"] = j["labels"] * 300
        self.assertEqual(self.rpc(j)["printed"], 300)

    def test_discovery(self):
        u = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        u.bind(("127.0.0.1", 0))
        port = u.getsockname()[1]
        u.close()
        t = threading.Thread(target=LP.discovery, args=(self.cfg, self.printer, port, self.stop), daemon=True)
        t.start()
        c = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        c.settimeout(3)
        got = None
        for _ in range(10):
            c.sendto(b"SLBL?", ("127.0.0.1", port))
            try:
                got = json.loads(c.recvfrom(2048)[0])
                break
            except socket.timeout:
                continue
        c.close()
        self.assertIsNotNone(got)
        self.assertEqual(got["slbl"], 1)
        self.assertEqual(got["printer"], "Xprinter XP-365B")


class CloudTest(Base):
    def test_poll(self):
        fc = FakeCloud()
        j = self.job("c1", n=2, copies=1)
        j["at"] = 1_000
        fc.call("PUT", "L/%s/c1" % KEY, j)
        old = self.job("c0", n=1)
        old["at"] = 1_000 - LP.CLOUD_MAX_AGE_MS - 5
        fc.call("PUT", "L/%s/c0" % KEY, old)
        wrong = self.job("c2", key="z" * 32)
        fc.call("PUT", "L/%s/c2" % KEY, wrong)
        n = LP.poll_cloud_once(self.cfg, self.printer, fc, now_ms=2_000)
        self.assertEqual(n, 1)
        self.assertEqual(fc.data["L"][KEY], {}, "hammasi navbatdan olinadi")
        self.assertEqual(len(self.printed), 1)

    def test_lan_then_cloud_no_double(self):
        fc = FakeCloud()
        LP.handle_request(self.cfg, self.printer, self.job("d1"))
        fc.call("PUT", "L/%s/d1" % KEY, self.job("d1"))
        LP.poll_cloud_once(self.cfg, self.printer, fc, now_ms=0)
        self.assertEqual(len(self.printed), 1)
        self.assertEqual(fc.data["L"][KEY], {})

    def test_printer_error_keeps_job(self):
        fc = FakeCloud()
        fc.call("PUT", "L/%s/e1" % KEY, self.job("e1"))
        self.printer.backend = lambda n, l: (_ for _ in ()).throw(RuntimeError("offline"))
        with self.assertRaises(LP.PrintFail):
            LP.poll_cloud_once(self.cfg, self.printer, fc, now_ms=0)
        self.assertIn("e1", fc.data["L"][KEY], "printer xatosida ish navbatda qoladi")

    def test_status(self):
        fc = FakeCloud()
        LP.push_status(self.cfg, self.printer, fc)
        st = fc.data["P"][KEY]
        self.assertEqual(st["printer"], "Xprinter XP-365B")
        self.assertEqual(st["at"], {".sv": "timestamp"})
        self.assertNotIn("key", st)


if __name__ == "__main__":
    unittest.main(verbosity=2)
