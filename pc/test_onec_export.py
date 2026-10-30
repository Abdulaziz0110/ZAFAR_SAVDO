#!/usr/bin/env python3
"""onec_export.py testlari (1C va internetsiz: soxta COM va soxta bulut):  python pc/test_onec_export.py"""
import base64
import datetime as dt
import gzip
import importlib.util
import io
import os
import tempfile
import unittest
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))


def load(tmp):
    spec = importlib.util.spec_from_file_location("onec_export", os.path.join(HERE, "onec_export.py"))
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    m.STATE = os.path.join(tmp, "state.json")
    m.LOG = os.path.join(tmp, "x.log")
    m.OUT_DIR = os.path.join(tmp, "export")
    return m


INI = """[firebase]
url = demo-shop-default-rtdb.firebaseio.com/
secret = SECRET123
queue =
inbox = abcdefghijkmnpqrstuvwxyzABCDEF23

[onec]
connection = D:\\1C\\Baza
user = Agent
password = p"w
report = KartochkaKlienta
supplier_flag = Поставщик
skip = Test ; Eski
"""


def xlsx(text, stamp="2026-10-30T10:00:00Z"):
    b = io.BytesIO()
    with zipfile.ZipFile(b, "w") as z:
        z.writestr("xl/worksheets/sheet1.xml", "<x>%s</x>" % text)
        z.writestr("docProps/core.xml", "<t>%s</t>" % stamp)  # har safar o'zgaradi — xeshga kirmasin
    return b.getvalue()


class FakeCloud:
    def __init__(self):
        self.uploads = []
        self.statuses = []

    def upload(self, key, name, raw, client, kind):
        self.uploads.append((key, name, raw, client, kind))

    def status(self, text, ok):
        self.statuses.append((text, ok))


class FakeOneC:
    def __init__(self, people, docs, fail=()):
        self.people, self.docs, self.fail = people, docs, set(fail)
        self.connected = self.closed = 0

    def connect(self):
        self.connected += 1
        return self

    def close(self):
        self.closed += 1

    def counterparties(self, a, b):
        return [{"ref": n, "name": n, "flag": f} for n, f in self.people]

    def build_xlsx(self, ref, a, b):
        if ref in self.fail:
            raise RuntimeError("1C xato")
        return self.docs[ref]


class T(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.m = load(self.tmp)
        self.ini = os.path.join(self.tmp, "forwarder.ini")
        with open(self.ini, "w", encoding="utf-8") as f:
            f.write(INI)
        self.cfg = self.m.Config(self.ini)

    def test_config(self):
        c = self.cfg
        self.assertEqual(c.url, "https://demo-shop-default-rtdb.firebaseio.com")
        c.check_cloud()
        c.check_onec()
        self.assertEqual(c.conn_string(), 'File="D:\\1C\\Baza";Usr="Agent";Pwd="p""w";')
        self.assertEqual(c.skip, ["Test", "Eski"])
        c.connection = 'Srvr="srv";Ref="baza"'
        self.assertEqual(c.conn_string(), 'Srvr="srv";Ref="baza";Usr="Agent";Pwd="p""w";')

    def test_bad_inbox(self):
        self.cfg.inbox = "short"
        with self.assertRaises(SystemExit):
            self.cfg.check_cloud()

    def test_period(self):
        a, b = self.m.period(7, dt.datetime(2026, 10, 30, 14, 5))
        self.assertEqual(a, dt.datetime(2026, 10, 24, 0, 0))
        self.assertEqual(b, dt.datetime(2026, 10, 30, 23, 59, 59))

    def test_schedule(self):
        ps = self.m.parse_schedule
        self.assertEqual(ps({}, self.cfg), (2, 7))
        self.assertEqual(ps({"h": 0, "d": 3}, self.cfg), (0, 3))
        self.assertEqual(ps({"h": "x", "d": 99999}, self.cfg), (2, 400))

    def test_hash_ignores_timestamps(self):
        h = self.m.content_hash
        self.assertEqual(h(xlsx("A", "t1")), h(xlsx("A", "t2")))
        self.assertNotEqual(h(xlsx("A")), h(xlsx("B")))

    def test_run_once_sends_only_changed(self):
        people = [("Dadaxon Aka (TRZ)", False), ("Ali Optom", True), ("Test", False), ("Buzuq", False)]
        docs = {"Dadaxon Aka (TRZ)": xlsx("d1"), "Ali Optom": xlsx("a1"), "Test": xlsx("t")}
        cloud, one = FakeCloud(), FakeOneC(people, docs, fail=["Buzuq"])
        sent, checked, errors = self.m.run_once(self.cfg, cloud, one, 7)
        self.assertEqual((sent, checked, len(errors)), (2, 3, 1))
        self.assertEqual((one.connected, one.closed), (1, 1))
        names = {u[3]: u for u in cloud.uploads}
        self.assertEqual(names["Ali Optom"][4], "s")
        self.assertEqual(names["Dadaxon Aka (TRZ)"][4], "c")
        self.assertTrue(names["Dadaxon Aka (TRZ)"][1].startswith("Dadaxon Aka (TRZ)_"))
        # ikkinchi aylanish: o'zgarmagan — yuborilmaydi; bittasi o'zgardi — faqat u
        docs["Ali Optom"] = xlsx("a1", "boshqa vaqt")
        docs["Dadaxon Aka (TRZ)"] = xlsx("d2")
        cloud2 = FakeCloud()
        sent, _, _ = self.m.run_once(self.cfg, cloud2, FakeOneC(people, docs, fail=["Buzuq"]), 7)
        self.assertEqual(sent, 1)
        self.assertEqual(cloud2.uploads[0][3], "Dadaxon Aka (TRZ)")
        # davr o'zgarsa — qayta yuboriladi
        sent, _, _ = self.m.run_once(self.cfg, FakeCloud(), FakeOneC(people, docs, fail=["Buzuq"]), 3)
        self.assertEqual(sent, 2)

    def test_close_on_error(self):
        class Boom(FakeOneC):
            def counterparties(self, a, b):
                raise RuntimeError("ulanish uzildi")
        one = Boom([], {})
        with self.assertRaises(RuntimeError):
            self.m.run_once(self.cfg, FakeCloud(), one, 7)
        self.assertEqual(one.closed, 1)

    def test_upload_body(self):
        calls = []
        cloud = self.m.Cloud(self.cfg)
        cloud.call = lambda method, path, body=None, **kw: calls.append((method, path, body))
        raw = xlsx("x")
        cloud.upload("0001_ab", "f.xlsx", raw, "Mijoz", "c")
        method, path, body = calls[0]
        self.assertEqual((method, path), ("PUT", "in/%s/0001_ab" % self.cfg.inbox))
        self.assertEqual(gzip.decompress(base64.b64decode(body["b"])), raw)
        self.assertEqual((body["gz"], body["c"], body["k"], body["n"]), (True, "Mijoz", "c", "f.xlsx"))
        self.assertEqual(body["at"], {".sv": "timestamp"})
        cloud.status("ok", True)
        self.assertEqual(calls[1][1], "p/%s/st" % self.cfg.inbox)

    def test_keys_and_names(self):
        k = self.m.file_key("Dadaxon", 1700000000000)
        self.assertRegex(k, r"^\d{13}_[0-9a-f]{10}$")
        self.assertEqual(self.m.safe_name('A/B:"C"'), "A B C")
        self.assertEqual(self.m.status_text(1, 5, ["X: y"]), "1 ta yangilandi, 5 ta tekshirildi; 1 ta xato: X: y")


if __name__ == "__main__":
    unittest.main()
