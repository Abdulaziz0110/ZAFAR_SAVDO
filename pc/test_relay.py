#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""relay.py testlari: soxta bulut va soxta SQL server bilan (Windows kerak emas)."""
import json
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import relay  # noqa: E402


class FakeCloud:
    def __init__(self):
        self.db = {}
        self.t = 1000

    def _node(self, path, make=False):
        cur = self.db
        parts = path.strip("/").split("/")
        for p in parts[:-1]:
            if p not in cur or not isinstance(cur[p], dict):
                if not make:
                    return None, parts[-1]
                cur[p] = {}
            cur = cur[p]
        return cur, parts[-1]

    def _stamp(self, v):
        if isinstance(v, dict):
            if v == {".sv": "timestamp"}:
                self.t += 1
                return self.t
            return {k: self._stamp(x) for k, x in v.items()}
        return v

    def call(self, method, path, body=None, timeout=60, **q):
        if method == "GET":
            n, k = self._node(path)
            v = None if n is None else n.get(k)
            if q.get("shallow") and isinstance(v, dict):
                return {a: True for a in v}
            return json.loads(json.dumps(v))
        if method == "PUT":
            n, k = self._node(path, True)
            n[k] = self._stamp(body)
        elif method == "PATCH":
            n, k = self._node(path, True)
            n.setdefault(k, {})
            n[k].update(self._stamp(body))
        elif method == "DELETE":
            n, k = self._node(path)
            if n is not None:
                n.pop(k, None)
        return None


class FakeSql:
    """savollar/javoblar va ma'lumot jadvallari."""

    def __init__(self):
        self.conf = {"host": "h", "database": "d"}
        self.savollar = []       # [id, user, type, text]
        self.javoblar = {}
        self.down = False
        self.reject = False
        self.agents = [[7, "Ali Valiyev", "boss", "+998901112233", "sir123"]]

    def ready(self):
        return True

    def run(self, sql, params=(), opened=None):
        if self.down:
            raise relay.NoServer("yo'q")
        if opened is not None:
            opened["ok"] = True
        if sql.startswith("INSERT INTO savollar"):
            if self.reject:
                raise Exception("String or binary data would be truncated")
            rid = len(self.savollar) + 1
            self.savollar.append([rid, params[0], params[1], params[2]])
            if params[1] == "3":
                self.javoblar[str(rid)] = "150000:12.5"
            if params[1] == "5":
                self.javoblar[str(rid)] = "<b>hisobot`x`</b>"
            return [[rid]]
        if "FROM savollar WHERE user_id = ? AND sorov_turi = ? AND sorov_izox = ?" in sql:
            return [[r[0]] for r in reversed(self.savollar) if [r[1], r[2], r[3]] == list(params)][:1]
        if "FROM savollar" in sql:
            return [[r[0], r[3]] for r in reversed(self.savollar) if [r[1], r[2]] == list(params)]
        if "FROM javoblar" in sql:
            v = self.javoblar.get(str(params[0]))
            return [[v]] if v is not None else []
        if "FROM agentlar WHERE telefon" in sql:
            return [a for a in self.agents if a[3] == params[0]]
        if "FROM agentlar" in sql:
            return [a[:4] for a in self.agents]
        if "FROM gruppa" in sql:
            return [[1, "Ichimlik"]]
        if "FROM region" in sql:
            return [[1, "Shahar"]]
        if "FROM kontragentlar" in sql:
            return [[1, 55, "Dadaxon aka", 1500000.0, 0, None]]
        if "FROM xozirgi_kurs" in sql:
            return [[12850]]
        if "FROM tovar" in sql:
            return [[1, 1001, "Fuse Tea 0.5 L", "1", 9000.0, 0.7, 12, 34.5, None, 10000, 0.8, 8000, 0.6]]
        if "GETUTCDATE" in sql:
            return [[1700000000]]
        raise AssertionError(sql)


KEY = "k" * 32


def make():
    cloud = FakeCloud()
    sql = FakeSql()
    clock = [5000.0]
    r = relay.Relay(cloud, KEY, relay.Server(sql, sleep=lambda s: None, clock=lambda: clock[0]), clock=lambda: clock[0], version="t")
    return cloud, sql, r, clock


class CodecTest(unittest.TestCase):
    def test_oid_matches_app(self):
        # ilovadagi RelayCodec.oid: sha256(agent \\u0001 tur \\u0001 matn) dastlabki 40 belgi
        self.assertEqual(len(relay.oid("7", "1", "abc")), 40)
        self.assertNotEqual(relay.oid("7", "1", "abc"), relay.oid("7", "4", "abc"))

    def test_proof_known_vector(self):
        import base64
        import hashlib
        k = hashlib.pbkdf2_hmac("sha256", b"sir123", b"imsoft-agent:tuz", 20000, 32)
        self.assertEqual(relay.proof("sir123", "tuz"), base64.urlsafe_b64encode(k).decode().rstrip("="))
        self.assertNotIn("=", relay.proof("sir123", "tuz"))

    def test_pack_roundtrip(self):
        self.assertEqual(relay.unpack(relay.pack("salom ҳ")), "salom ҳ")

    def test_sval(self):
        self.assertEqual(relay.sval(None), "")
        self.assertEqual(relay.sval(12.0), "12")
        self.assertEqual(relay.sval(12.5), "12.5")
        self.assertEqual(relay.sval(7), "7")

    def test_payload_same_question_marks(self):
        self.assertTrue(relay.payload_same("ab ", "ab"))
        self.assertTrue(relay.payload_same("aҳb", "a?b"))
        self.assertFalse(relay.payload_same("abc", "abd"))


class RelayTest(unittest.TestCase):
    def test_login_ok_and_wrong_password(self):
        cloud, sql, r, _ = make()
        good = {"ph": "+998901112233", "s": "tuz", "pr": relay.proof("sir123", "tuz")}
        bad = dict(good, pr=relay.proof("xato", "tuz"))
        cloud.call("PUT", r.path("rq/a1"), {"at": 4999000, "op": "login", "p": json.dumps(good)})
        cloud.call("PUT", r.path("rq/a2"), {"at": 4999000, "op": "login", "p": json.dumps(bad)})
        r.pump()
        self.assertEqual(cloud.db["g"][KEY]["rs"]["a1"]["r"]["a"], ["7", "Ali Valiyev", "boss", "+998901112233"])
        self.assertIsNone(cloud.db["g"][KEY]["rs"]["a2"]["r"]["a"])
        self.assertNotIn("rq", [k for k, v in cloud.db["g"][KEY].items() if v])

    def test_password_never_leaves(self):
        cloud, sql, r, _ = make()
        cloud.call("PUT", r.path("rq/a1"), {"at": 4999000, "op": "login", "p": json.dumps({"ph": "+998901112233", "s": "t", "pr": "x"})})
        r.pump()
        self.assertNotIn("sir123", json.dumps(cloud.db))

    def test_debt_report_time(self):
        cloud, sql, r, _ = make()
        cloud.call("PUT", r.path("rq/d"), {"at": 4999000, "op": "debt", "p": json.dumps({"a": "7", "st": "55"})})
        cloud.call("PUT", r.path("rq/h"), {"at": 4999000, "op": "report", "p": json.dumps({"a": "7", "p": "01.11.2026#12.11.2026#55"})})
        cloud.call("PUT", r.path("rq/t"), {"at": 4999000, "op": "time", "p": "{}"})
        r.pump()
        rs = cloud.db["g"][KEY]["rs"]
        self.assertEqual(rs["d"]["r"], {"s": "150000", "u": "12.5"})
        self.assertEqual(rs["h"]["r"], '<b>hisobot"x"</b>')
        self.assertEqual(rs["t"]["r"], 1700000000000)

    def test_old_request_dropped_and_unknown_op(self):
        cloud, sql, r, _ = make()
        cloud.call("PUT", r.path("rq/old"), {"at": 1, "op": "time", "p": "{}"})
        cloud.call("PUT", r.path("rq/x"), {"at": 4999000, "op": "zzz", "p": "{}"})
        r.pump()
        rs = cloud.db["g"][KEY]["rs"]
        self.assertNotIn("old", rs)
        self.assertEqual(rs["x"]["e"], "unknown_op")

    def test_sale_written_once(self):
        cloud, sql, r, _ = make()
        p = "7^55^12.11.2026 10:00:00^1^1^0^0^0^0^12850^1^~1001^2^9000@"
        o = relay.oid("7", "1", p)
        cloud.call("PUT", r.path("ob/" + o), {"at": 10, "u": "7", "t": "1", "p": p, "m": "{}"})
        r.pump()
        self.assertEqual(len(sql.savollar), 1)
        self.assertEqual(cloud.db["g"][KEY]["obr"][o]["st"], "ok")
        self.assertEqual(cloud.db["g"][KEY]["mr"][o]["p"], p)
        # qayta yuborilsa - yangi yozuv yo'q
        cloud.call("PUT", r.path("ob/" + o), {"at": 11, "u": "7", "t": "1", "p": p})
        r.pump()
        self.assertEqual(len(sql.savollar), 1)
        self.assertEqual(cloud.db["g"][KEY].get("ob"), {})

    def test_result_lost_then_found_on_server(self):
        """Yozildi, lekin natija bulutga yetmadi: qayta urinishda serverdan topiladi (dublikat yo'q)."""
        cloud, sql, r, _ = make()
        p = "7^55^x~"
        o = relay.oid("7", "4", p)
        sql.savollar.append([1, "7", "4", p])
        cloud.call("PUT", r.path("ob/" + o), {"at": 10, "u": "7", "t": "4", "p": p})
        r.pump()
        self.assertEqual(len(sql.savollar), 1)
        self.assertEqual(cloud.db["g"][KEY]["obr"][o]["id"], "1")

    def test_server_down_keeps_queue_in_order(self):
        cloud, sql, r, _ = make()
        sql.down = True
        for i, at in ((1, 20), (2, 10)):
            p = "7^55^%d~" % i
            cloud.call("PUT", r.path("ob/" + relay.oid("7", "4", p)), {"at": at, "u": "7", "t": "4", "p": p})
        r.pump()
        self.assertEqual(len(cloud.db["g"][KEY]["ob"]), 2)
        self.assertTrue(cloud.db["g"][KEY]["hb"]["e"])
        sql.down = False
        r.pump()
        self.assertEqual([x[3] for x in sql.savollar], ["7^55^2~", "7^55^1~"])

    def test_bad_item_and_blocked_and_rejected(self):
        cloud, sql, r, _ = make()
        cloud.call("PUT", r.path("ob/" + "0" * 40), {"at": 1, "u": "7", "t": "1", "p": "x"})
        r.pump()
        self.assertEqual(cloud.db["g"][KEY]["obr"]["0" * 40]["m"], "bad_item")
        r._ax = {"bl": ["dev9"]}
        p = "7^55^b~"
        o = relay.oid("7", "4", p)
        cloud.call("PUT", r.path("ob/" + o), {"at": 2, "u": "7", "t": "4", "p": p, "d": "dev9"})
        r.writes()
        self.assertEqual(cloud.db["g"][KEY]["obr"][o]["m"], "blocked")
        sql.reject = True
        p2 = "7^55^c~"
        o2 = relay.oid("7", "4", p2)
        cloud.call("PUT", r.path("ob/" + o2), {"at": 3, "u": "7", "t": "4", "p": p2})
        r.writes()
        self.assertEqual(cloud.db["g"][KEY]["obr"][o2]["st"], "rej")
        self.assertEqual(len(sql.savollar), 0)

    def test_catalog_published_and_unchanged_only_time(self):
        cloud, sql, r, clock = make()
        cloud.call("PUT", r.path("ax"), {"br": ["55"], "lc": {"1001": [7900, 0.6]}})
        r.pump()
        cat = cloud.db["g"][KEY]["cat"]
        j = json.loads(relay.unpack(cat["b"]))
        self.assertEqual(j["p"][0][:4], ["1", "1001", "Fuse Tea 0.5 L", "1"])
        self.assertEqual(j["s"][0], ["1", "55", "Dadaxon aka", "1500000", "0", ""])
        self.assertEqual(j["k"], ["12850"])
        self.assertEqual(j["a"], [["7", "Ali Valiyev", "boss", "+998901112233"]])
        self.assertEqual(j["br"], ["55"])
        self.assertEqual(j["lc"], {"1001": [7900, 0.6]})
        self.assertNotIn("sir123", json.dumps(j))
        first = cat["b"]
        clock[0] += relay.CATALOG_SEC + 1
        r.pump()
        self.assertEqual(cloud.db["g"][KEY]["cat"]["b"], first)
        self.assertGreater(cloud.db["g"][KEY]["cat"]["at"], j["at"])

    def test_catalog_on_agent_request(self):
        cloud, sql, r, clock = make()
        r.pump()
        at1 = cloud.db["g"][KEY]["cat"]["at"]
        clock[0] += 10
        cloud.call("PUT", r.path("creq"), int(clock[0] * 1000))
        r.pump()
        self.assertGreater(cloud.db["g"][KEY]["cat"]["at"], at1)

    def test_barcode_requests_kept_for_admin(self):
        cloud, sql, r, _ = make()
        cloud.call("PUT", r.path("rq/b"), {"at": 4999000, "op": "bc", "p": json.dumps({"i": "1001", "n": "x", "c": "478"})})
        cloud.call("PUT", r.path("rq/c"), {"at": 4999000, "op": "bcs", "p": json.dumps({"l": [["1", "2"], ["3", "4"]]})})
        r.pump()
        self.assertEqual(cloud.db["g"][KEY]["rs"]["b"]["r"], True)
        self.assertEqual(cloud.db["g"][KEY]["rs"]["c"]["r"], 2)
        self.assertEqual(set(cloud.db["g"][KEY]["bq"]), {"b", "c"})

    def test_heartbeat_names_host(self):
        cloud, sql, r, _ = make()
        r.pump()
        hb = cloud.db["g"][KEY]["hb"]
        self.assertEqual(hb["h"], "pc")
        self.assertEqual(hb["v"], "t")

    def test_payment_parse(self):
        self.assertEqual(relay.parse_payment("7^55^100^0^t^12850^izoh^100^50^25^p~"), ("55", 100.0, 50.0, 25.0, "izoh"))

    def test_host_port_for_ado(self):
        import tempfile
        d = tempfile.mkdtemp()
        c = relay.load_conf(d, {"key": "k", "host": "192.168.1.5:1433", "database": "b", "user": "", "password": ""})
        self.assertEqual(c["host"], "192.168.1.5,1433")



class LabelTest(unittest.TestCase):
    PR = [("Microsoft Print to PDF", ""), ("XP-365B", "xprinter"), ("Canon", "canon")]

    def test_pick_printer(self):
        self.assertEqual(relay.pick_label_printer(self.PR, "xprinter"), "XP-365B")
        self.assertEqual(relay.pick_label_printer(self.PR, "canon"), "Canon")
        self.assertEqual(relay.pick_label_printer(self.PR, ""), "XP-365B")
        self.assertEqual(relay.pick_label_printer([("Label TSC", "")], "yoq"), "Label TSC")
        self.assertIsNone(relay.pick_label_printer([("Canon", "c")], "xprinter"))

    def _relay(self, printers):
        cloud, sql, r, clock = make()
        sent = []
        r.labels = relay.LabelSink("xprinter", enum=lambda: printers, send=lambda n, d: sent.append((n, d)), clock=lambda: clock[0])
        return cloud, r, sent

    def test_agent_label_printed_once_on_pc(self):
        import base64
        cloud, r, sent = self._relay(self.PR)
        cloud.call("PUT", "g/%s/lb/j1" % KEY, {"at": 1, "n": 2, "b": base64.b64encode(b"TSPL").decode()})
        r.pump()
        self.assertEqual(sent, [("XP-365B", b"TSPL")])
        g = cloud.db["g"][KEY]
        self.assertNotIn("j1", g.get("lb", {}))
        self.assertTrue(g["lbr"]["j1"]["ok"])
        self.assertEqual(g["lbr"]["j1"]["q"], "XP-365B")
        self.assertEqual(g["hb"]["lb"], 1)
        r.pump()
        self.assertEqual(len(sent), 1)

    def test_printer_error_reported(self):
        import base64
        cloud, sql, r, clock = make()
        r.labels = relay.LabelSink("xprinter", enum=lambda: self.PR, send=lambda n, d: (_ for _ in ()).throw(OSError("offline")), clock=lambda: clock[0])
        cloud.call("PUT", "g/%s/lb/j2" % KEY, {"at": 1, "n": 1, "b": base64.b64encode(b"X").decode()})
        r.pump()
        res = cloud.db["g"][KEY]["lbr"]["j2"]
        self.assertFalse(res["ok"])
        self.assertIn("offline", res["e"])

    def test_no_printer_leaves_job_for_admin(self):
        import base64
        cloud, r, sent = self._relay([("Canon", "canon")])
        cloud.call("PUT", "g/%s/lb/j3" % KEY, {"at": 1, "n": 1, "b": base64.b64encode(b"X").decode()})
        r.pump()
        self.assertIn("j3", cloud.db["g"][KEY]["lb"])
        self.assertEqual(cloud.db["g"][KEY]["hb"]["lb"], 0)
        self.assertEqual(sent, [])


if __name__ == "__main__":
    unittest.main()
