#!/usr/bin/env python3
"""v3.10 testlari: taroziga narx/PLU (onec_scale.py) — 1C'dagi RONGTA yuklash bilan bir xil fayl.
1C va internetsiz (soxta COM, soxta bulut):  python pc/test_onec_scale.py"""
import os
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import onec_scale  # noqa: E402


class FakeOneC:
    def __init__(self, ips, goods):
        self.ips, self.goods, self.queries = ips, goods, []

    def connect(self):
        pass

    def close(self):
        pass

    def query(self, text, fields, **params):
        self.queries.append((text, params))
        if "Справочник.Весы" in text and "Товар" not in text:
            return [self.ips]
        return self.goods


class FakeCloud:
    class Cfg:
        inbox = "k" * 24

    def __init__(self, req):
        self.cfg, self.req, self.put = self.Cfg(), req, {}

    def call(self, method, path, body=None, timeout=60, **q):
        if method == "GET":
            return self.req
        self.put[path] = body
        return None


GOODS = [
    {"Наим": "Qulupnay | Sublimit", "ШК": "2200123000000", "ПЛУ": 12, "Вес": 1, "Цена": 200000},
    {"Наим": "Kompot 1987", "ШК": "2800777000000", "ПЛУ": 5.0, "Вес": 0, "Цена": 14000.5},
    {"Наим": "Narxsiz", "ШК": "1", "ПЛУ": 3, "Вес": 1, "Цена": 0},
    {"Наим": "PLUsiz", "ШК": "1", "ПЛУ": 0, "Вес": 1, "Цена": 1000},
]


class ScaleTest(unittest.TestCase):
    def test_line_like_1c(self):
        self.assertEqual(onec_scale.line("Pista", 7.0, "2200123000000", 250000, True), "Pista|7|00123|250000|29")
        self.assertEqual(onec_scale.line("Kompot", 5, "2800777000000", 14000.5, False), "Kompot|5|00777|14000,5|28")

    def test_file_text(self):
        t = onec_scale.build_text(True, ["192.168.1.50", "192.168.1.51"], ["A|1|00001|100|29"])
        self.assertEqual(t, "1\r\n192.168.1.50~192.168.1.51~\r\nA|1|00001|100|29~\r\n")
        self.assertTrue(onec_scale.build_text(False, ["x"], []).startswith("0\r\n"))

    def test_run_writes_file_and_filters(self):
        tmp = tempfile.mkdtemp()
        path = os.path.join(tmp, "rt_scale.txt")
        o = FakeOneC({"И1": "192.168.1.50", "И2": "", "И3": " 192.168.1.52 "}, GOODS)
        n, ips = onec_scale.run(o, clear=False, path=path, launch=False)
        self.assertEqual(n, 2)
        self.assertEqual(ips, ["192.168.1.50", "192.168.1.52"])
        raw = open(path, "rb").read()
        self.assertTrue(raw.startswith(b"\xef\xbb\xbf0\r\n"))
        body = raw.decode("utf-8-sig").split("\r\n")
        self.assertEqual(body[2], "Qulupnay Sublimit|12|00123|200000|29~Kompot 1987|5|00777|14000,5|28~")
        # "faqat yuborish" — qoldiq bo'yicha so'rov
        self.assertIn("Остатки", o.queries[1][0])
        o2 = FakeOneC({"И1": "1.1.1.1"}, GOODS)
        onec_scale.run(o2, clear=True, path=path, launch=False)
        self.assertIn("Справочник.Товары", o2.queries[1][0])

    def test_no_ip_is_error(self):
        with self.assertRaises(RuntimeError):
            onec_scale.run(FakeOneC({}, GOODS), clear=True, path=os.path.join(tempfile.mkdtemp(), "x.txt"), launch=False)

    def test_tick_once_per_request(self):
        st = {}
        saved = []
        c = FakeCloud({"m": "clear", "at": 100})
        orig = onec_scale.run
        onec_scale.run = lambda onec, clear, **k: (3, ["1.2.3.4"])
        try:
            text = onec_scale.tick(c, None, st, saved.append)
            self.assertIn("3 ta", text)
            res = c.put["p/%s/scale_res" % c.cfg.inbox]
            self.assertTrue(res["ok"])
            self.assertEqual(res["req"], 100)
            self.assertIsNone(onec_scale.tick(c, None, st, saved.append))
        finally:
            onec_scale.run = orig


if __name__ == "__main__":
    unittest.main(verbosity=1)
