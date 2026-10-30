#!/usr/bin/env python3
"""onec_export.py testlari (1C va internetsiz: soxta COM va soxta bulut):  python pc/test_onec_export.py"""
import base64
import datetime as dt
import gzip
import importlib.util
import io
import json
import os
import time
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

    def test_onec_ini_kept_separately(self):
        with open(self.ini, "w", encoding="utf-8") as f:
            f.write(INI.split("[onec]")[0])  # telefondan kelgan yangi forwarder.ini — [onec] yo'q
        with open(os.path.join(self.tmp, "onec.ini"), "w", encoding="utf-8") as f:
            f.write("[onec]\nconnection = D:\\Baza\nreport = Kartochka\n\n[codes]\nplu = attr:ПЛУ\n")
        c = self.m.Config(self.ini)
        self.assertEqual((c.report, c.plu_src), ("Kartochka", "attr:ПЛУ"))
        self.assertTrue(c.inbox)

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


def F(name, *types, syn=""):
    return {"name": name, "syn": syn, "types": list(types)}


UT11 = {
    "goods_name": "Номенклатура", "goods_found": True,
    "goods": [F("Артикул", "Строка"), F("Весовой", "Булево", syn="Весовой товар"), F("ЕдиницаИзмерения", "Справочник.УпаковкиЕдиницыИзмерения")],
    "regs": [
        {"name": "ЦеныНоменклатуры", "syn": "Цены", "fields": [F("Номенклатура", "Справочник.Номенклатура"), F("Цена", "Число")]},
        {"name": "ТоварыНаПодключаемомОборудовании", "syn": "Товары на подключаемом оборудовании",
         "fields": [F("ПодключаемоеОборудование", "Справочник.ПодключаемоеОборудование"), F("Номенклатура", "Справочник.Номенклатура"),
                    F("Код", "Число"), F("ДатаИзменения", "Дата")]},
        {"name": "Штрихкоды", "syn": "Штрихкоды", "fields": [F("Штрихкод", "Строка"),
                    F("Владелец", "Справочник.Номенклатура", "Справочник.ХарактеристикиНоменклатуры"), F("ЕдиницаИзмерения", "Справочник.Ед")]},
    ],
}


class Codes(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.m = load(self.tmp)
        self.m.CODES_OUT = os.path.join(self.tmp, "codes.json")
        ini = os.path.join(self.tmp, "forwarder.ini")
        with open(ini, "w", encoding="utf-8") as f:
            f.write(INI)
        self.cfg = self.m.Config(ini)

    def test_pick_register(self):
        self.assertEqual(self.m.pick_plu(UT11), ("reg", "ТоварыНаПодключаемомОборудовании", "Номенклатура", "Код", True))
        self.assertEqual(self.m.pick_barcode(UT11), ("reg", "Штрихкоды", "Владелец", "Штрихкод", False))

    def test_pick_attr_and_ignore_flag(self):
        s = {"goods_name": "Номенклатура", "goods": [F("Весовой", "Булево"), F("НомерPLU", "Число"), F("Штрихкод", "Строка")], "regs": []}
        self.assertEqual(self.m.pick_plu(s), ("attr", "НомерPLU"))
        self.assertEqual(self.m.pick_barcode(s), ("attr", "Штрихкод"))
        s2 = {"goods_name": "Номенклатура", "goods": [F("КодВесов", "Строка", syn="Код на весах")], "regs": []}
        self.assertEqual(self.m.pick_plu(s2), ("attr", "КодВесов"))
        s3 = {"goods_name": "Номенклатура", "goods": [F("Весовой", "Булево"), F("ВесНетто", "Число")], "regs": []}
        self.assertIsNone(self.m.pick_plu(s3))

    def test_query_text(self):
        q = self.m.reg_query(("reg", "Штрихкоды", "Владелец", "Штрихкод", False), "Номенклатура")
        self.assertIn("ВЫРАЗИТЬ(Р.Владелец КАК Справочник.Номенклатура).Код КАК Код", q)
        self.assertIn("ГДЕ Р.Владелец ССЫЛКА Справочник.Номенклатура", q)
        q2 = self.m.reg_query(("reg", "ТоварыНаВесах", "Номенклатура", "Код", True), "Номенклатура")
        self.assertEqual(q2, "ВЫБРАТЬ Р.Номенклатура.Код КАК Код, Р.Номенклатура.Наименование КАК Наименование, Р.Код КАК Знач ИЗ РегистрСведений.ТоварыНаВесах КАК Р")

    def test_parse_src_and_norm(self):
        self.assertEqual(self.m.parse_src("attr: ПЛУ"), ("attr", "ПЛУ"))
        self.assertEqual(self.m.parse_src("Весы:Товар:PLU"), ("reg", "Весы", "Товар", "PLU", False))
        self.assertIsNone(self.m.parse_src(""))
        with self.assertRaises(SystemExit):
            self.m.parse_src("nimadir")
        self.assertEqual(self.m.norm_plu(501.0), "501")
        self.assertEqual(self.m.norm_plu("00151"), "151")
        self.assertEqual(self.m.norm_plu(0), "")
        self.assertEqual(self.m.norm_code(2555.0), "2555")

    def test_goods_codes_merge(self):
        m = self.m
        rows = {
            "Код КАК Знач": [{"Код": 2555.0, "Наименование": "Orik Bargak Balonchik", "Знач": 501.0},
                             {"Код": "00241", "Наименование": "Orik Subxon 116", "Знач": 151}],
            "Штрихкод КАК Знач": [{"Код": 2555.0, "Наименование": "Orik Bargak Balonchik", "Знач": "2901740"},
                                  {"Код": 2555.0, "Наименование": "Orik Bargak Balonchik", "Знач": "2901740"},
                                  {"Код": "4307", "Наименование": "Mirinda 0.3 L", "Знач": "012000800214"}],
        }

        class O(m.OneC):
            def query(self, text, fields, **p):
                for k, v in rows.items():
                    if k in text:
                        return v
                raise AssertionError(text)
        items, src = O(self.cfg, conn=object()).goods_codes(UT11)
        by = {i["c"]: i for i in items}
        self.assertEqual(by["2555"], {"c": "2555", "n": "Orik Bargak Balonchik", "p": "501", "b": ["2901740"]})
        self.assertEqual(by["00241"]["p"], "151")
        self.assertEqual(by["4307"]["b"], ["012000800214"])
        self.assertIn("ТоварыНаПодключаемомОборудовании", src["plu"])

    def test_run_codes_and_request(self):
        m = self.m

        class One:
            def __init__(self, items):
                self.items = items
            def connect(self):
                return self
            def close(self):
                pass
            def goods_codes(self):
                return self.items, {"plu": "rekvizit PLU", "bc": ""}
        cloud = FakeCloud()
        items = [{"c": "1", "n": "A", "p": "5", "b": []}]
        self.assertEqual(m.run_codes(self.cfg, cloud, One(items))[0], True)
        self.assertEqual(cloud.uploads[0][4], "codes")
        body = json.loads(cloud.uploads[0][2])
        self.assertEqual(body["items"], items)
        self.assertEqual(m.run_codes(self.cfg, cloud, One(items))[0], False, "o'zgarmagan — yuborilmaydi")
        self.assertEqual(m.run_codes(self.cfg, cloud, One(items), force=True)[0], True)
        self.assertEqual(m.codes_status(False, 1, {"plu": "x", "bc": ""}), "Tovar kodlari: 1 ta (o'zgarmagan); shtrix manbasi topilmadi")
        # telefon so'rovi: vaqti kelmagan bo'lsa ham bajariladi, bir marta
        cloud.codes_request = lambda: 5000
        n0 = len(cloud.uploads)
        self.assertTrue(m.codes_tick(self.cfg, cloud, One(items), last_codes=time.time()))
        self.assertEqual(len(cloud.uploads), n0 + 1)
        self.assertFalse(m.codes_tick(self.cfg, cloud, One(items), last_codes=time.time()))
        cloud.codes_request = lambda: 0
        self.assertTrue(m.codes_tick(self.cfg, cloud, One(items), last_codes=time.time() - 7 * 3600))

    def test_report(self):
        t = self.m.codes_report(UT11, self.cfg)
        self.assertIn("PLU manbasi: РегистрСведений.ТоварыНаПодключаемомОборудовании", t)
        self.assertIn("Shtrix kod manbasi: РегистрСведений.Штрихкоды", t)


if __name__ == "__main__":
    unittest.main()
