#!/usr/bin/env python3
"""forwarder.py testlari (soxta Firebase bilan, internet shart emas):  python pc/test_forwarder.py"""
import base64
import importlib.util
import json
import os
import shutil
import socket
import tempfile
import threading
import time
import unittest
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HERE = os.path.dirname(os.path.abspath(__file__))


def load_forwarder(tmp):
    spec = importlib.util.spec_from_file_location("forwarder", os.path.join(HERE, "forwarder.py"))
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    m.OUTBOX = os.path.join(tmp, "outbox")
    m.FAILED = os.path.join(tmp, "failed")
    m.LOG = os.path.join(tmp, "forwarder.log")
    m.ensure_dirs()
    return m


class Fake:
    def __init__(self, secret):
        self.secret = secret
        self.data = {}
        self.fail_code = None
        self.now = 1_700_000_000_000
        self.calls = []
        fake = self

        class H(BaseHTTPRequestHandler):
            def log_message(self, *a):
                pass

            def _do(self, method):
                u = urllib.parse.urlparse(self.path)
                q = dict(urllib.parse.parse_qsl(u.query))
                path = [p for p in u.path[:-5].strip("/").split("/") if p] if u.path.endswith(".json") else []
                n = int(self.headers.get("Content-Length") or 0)
                body = self.rfile.read(n).decode("utf-8") if n else ""
                fake.calls.append((method, "/".join(path)))
                if fake.fail_code:
                    self.send_response(fake.fail_code); self.end_headers(); self.wfile.write(b'{"error":"x"}'); return
                if q.get("auth") != fake.secret:
                    self.send_response(401); self.end_headers(); self.wfile.write(b'{"error":"Permission denied"}'); return
                if method == "PUT":
                    v = json.loads(body)
                    if isinstance(v, dict) and v.get(".sv") == "timestamp":
                        fake.now += 1; v = fake.now
                    if isinstance(v, dict) and isinstance(v.get("t"), dict):
                        fake.now += 1; v["t"] = fake.now
                    cur = fake.data
                    for p in path[:-1]:
                        cur = cur.setdefault(p, {})
                    cur[path[-1]] = v
                    self.send_response(204); self.end_headers(); return
                if method == "GET":
                    cur = fake.data
                    for p in path:
                        cur = cur.get(p) if isinstance(cur, dict) else None
                        if cur is None:
                            break
                    if q.get("shallow") == "true" and isinstance(cur, dict):
                        cur = {k: True for k in cur}
                    out = json.dumps(cur).encode()
                    self.send_response(200); self.send_header("Content-Length", str(len(out))); self.end_headers(); self.wfile.write(out); return
                self.send_response(405); self.end_headers()

            def do_PUT(self):
                self._do("PUT")

            def do_GET(self):
                self._do("GET")

        self.srv = ThreadingHTTPServer(("127.0.0.1", 0), H)
        threading.Thread(target=self.srv.serve_forever, daemon=True).start()

    @property
    def url(self):
        return "http://127.0.0.1:%d" % self.srv.server_address[1]

    def close(self):
        self.srv.shutdown()


QK = "A" * 32


class ForwarderTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.fw = load_forwarder(self.tmp)
        self.fake = Fake("SECRET")
        cfg = object.__new__(self.fw.Config)
        cfg.url = self.fake.url  # testda http (haqiqiy ishda doim https)
        cfg.secret = "SECRET"
        cfg.queue = QK
        cfg.host = "127.0.0.1"
        cfg.port = 0
        self.cfg = cfg
        self.cloud = self.fw.Cloud(cfg)

    def tearDown(self):
        self.fake.close()
        shutil.rmtree(self.tmp, ignore_errors=True)

    def test_send_in_order_and_idempotent(self):
        a = self.fw.save_job(b"\x1b@first")
        b = self.fw.save_job(b"\x1b@second")
        self.assertLess(a, b)
        self.assertEqual(2, self.fw.send_pending(self.cloud, once=True))
        q = self.fake.data["q"][QK]
        self.assertEqual(sorted(q), ["c" + a, "c" + b])
        self.assertEqual(base64.b64decode(q["c" + a]["b"]), b"\x1b@first")
        self.assertIsInstance(q["c" + a]["t"], int)
        self.assertIn("sig", self.fake.data["p"][QK])
        self.assertEqual([], self.fw.pending())
        # Javob yo'qolib, xuddi shu fayl qayta yuborilsa — kalit bir xil, navbatda baribir 2 ta
        with open(os.path.join(self.fw.OUTBOX, a + ".bin"), "wb") as f:
            f.write(b"\x1b@first")
        self.fw.send_pending(self.cloud, once=True)
        self.assertEqual(2, len(self.fake.data["q"][QK]))

    def test_auth_error_keeps_file(self):
        self.cfg.secret = "WRONG"
        self.fw.save_job(b"x")
        self.assertEqual(-30, self.fw.send_pending(self.cloud))
        self.assertEqual(1, len(self.fw.pending()))

    def test_413_moves_to_failed(self):
        self.fw.save_job(b"x")
        self.fake.fail_code = 413
        self.fw.send_pending(self.cloud, once=True)
        self.assertEqual([], self.fw.pending())
        self.assertEqual(1, len(os.listdir(self.fw.FAILED)))

    def test_offline_keeps_file(self):
        self.cfg.url = "http://127.0.0.1:1"  # hech kim tinglamaydi
        self.fw.save_job(b"x")
        self.assertEqual(-10, self.fw.send_pending(self.cloud))
        self.assertEqual(1, len(self.fw.pending()))

    def test_tcp_capture(self):
        srv = socket.socket(); srv.bind(("127.0.0.1", 0)); srv.listen(1)
        port = srv.getsockname()[1]

        def acc():
            c, a = srv.accept()
            self.fw.handle_client(c, a)
        t = threading.Thread(target=acc); t.start()
        payload = os.urandom(200_000)
        s = socket.create_connection(("127.0.0.1", port)); s.sendall(payload); s.close()
        t.join(5); srv.close()
        files = self.fw.pending()
        self.assertEqual(1, len(files))
        with open(os.path.join(self.fw.OUTBOX, files[0]), "rb") as f:
            self.assertEqual(payload, f.read())

    def test_empty_connection_ignored(self):
        srv = socket.socket(); srv.bind(("127.0.0.1", 0)); srv.listen(1)
        port = srv.getsockname()[1]

        def acc():
            c, a = srv.accept()
            self.fw.handle_client(c, a)
        t = threading.Thread(target=acc); t.start()
        socket.create_connection(("127.0.0.1", port)).close()
        t.join(5); srv.close()
        self.assertEqual([], self.fw.pending())

    def test_test_job_is_escpos(self):
        raw = self.fw.test_job()
        self.assertTrue(raw.startswith(b"\x1b@"))
        self.assertTrue(raw.endswith(b"\x1dVB\x00"))

    def test_config_validation(self):
        p = os.path.join(self.tmp, "f.ini")
        with open(p, "w") as f:
            f.write("[firebase]\nurl = x-default-rtdb.firebaseio.com/\nsecret = s\nqueue = %s\n" % QK)
        c = self.fw.Config(p)
        self.assertEqual("https://x-default-rtdb.firebaseio.com", c.url)
        with open(p, "w") as f:
            f.write("[firebase]\nurl = x\nsecret = s\nqueue = short\n")
        with self.assertRaises(SystemExit):
            self.fw.Config(p)


if __name__ == "__main__":
    unittest.main(verbosity=1)
