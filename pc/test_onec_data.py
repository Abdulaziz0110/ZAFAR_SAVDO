#!/usr/bin/env python3
"""v3.2 testlari: 1C savdolar/ta'minotchilar (onec_data.py), 1C'ga yozish (onec_write.py), daqiqalik jadval.
1C va internetsiz (soxta COM, soxta bulut):  python pc/test_onec_data.py"""
import datetime as dt
import importlib.util
import json
import os
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import onec_data  # noqa: E402
import onec_write  # noqa: E402


def load_export(tmp):
    spec = importlib.util.spec_from_file_location("onec_export", os.path.join(HERE, "onec_export.py"))
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    m.STATE = os.path.join(tmp, "state.json")
    return m


D = dt.datetime


class FakeQ:
    """onec.query(text, fields, **params) — matndagi hujjat nomiga qarab javob."""

    def __init__(self, tables, broken=()):
        self.tables = tables
        self.broken = broken
        self.calls = []

    def query(self, text, fields, **params):
        self.calls.append(text)
        for b in self.broken:
            if b in text:
                raise RuntimeError("Таблица не найдена: " + b)
        for key, rows in self.tables.items():
            if key in text:
                return [dict((f, r.get(f)) for f in fields) for r in rows]
        return []

    def connect(self):
        return self

    def close(self):
        pass


class DataTest(unittest.TestCase):
    def test_period_and_stamp(self):
        now = D(2026, 11, 1, 15, 0)
        f, t = onec_data.period_of(None, 30, now)
        self.assertEqual(f, D(2026, 10, 3, 0, 0, 0))
        self.assertEqual(t, D(2026, 11, 1, 23, 59, 59))
        f, t = onec_data.period_of({"f": "20261031", "t": "20261001"}, 30, now)
        self.assertEqual((f.day, t.day), (1, 31))
        f, t = onec_data.period_of({"f": "20200101", "t": "20261031"}, 30, now)
        self.assertEqual((t - f).days, 400)
        self.assertEqual(onec_data.stamp(D(2026, 10, 31, 14, 30, 5)), "20261031143005")
        self.assertEqual(onec_data.stamp("31.10.2026"), "31102026000000")
        self.assertEqual(onec_data.stamp(None), "")
        self.assertEqual(onec_data.text(12.0), "12")
        self.assertEqual(onec_data.num("1\u00a0500,5"), 1500.5)

    def test_sales_payload(self):
        heads = [{"Номер": "0000000123", "Дата": D(2026, 10, 31, 14, 30), "Контр": "Dadaxon Aka (TRZ)", "КонтрКод": 12.0, "Сум": 0, "Вал": 0, "Ком": ""}]
        lines = [{"Номер": "0000000123", "Дата": D(2026, 10, 31, 14, 30), "Товар": "Pista", "Код": 1001.0, "Кол": 2, "ЦС": 750000, "СС": 1500000, "ЦВ": 0, "СВ": 0},
                 {"Номер": "0000000999", "Дата": D(2026, 10, 30, 9, 0), "Товар": "Begona", "Код": 1, "Кол": 1, "ЦС": 1, "СС": 1, "ЦВ": 0, "СВ": 0}]
        q = FakeQ({"Документ.Реализация.Расход": lines, "Документ.Реализация КАК": heads}, broken=("ПродажаРозница",))
        p = onec_data.sales_payload(q, D(2026, 10, 1), D(2026, 10, 31, 23, 59, 59), 5)
        self.assertEqual(p["f"], "20261001")
        self.assertEqual(len(p["docs"]), 1)
        d = p["docs"][0]
        self.assertEqual(d["no"], "0000000123")
        self.assertEqual(d["cc"], "12")
        self.assertEqual(d["s"], 1500000)  # sarlavhada 0 — qatorlardan
        self.assertEqual(d["l"][0][:3], ["Pista", "1001", 2.0])
        self.assertTrue(any("ПродажаРозница" in e for e in p["err"]))
        # O'qish so'rovlari — faqat ВЫБРАТЬ
        self.assertTrue(all(c.startswith("ВЫБРАТЬ") for c in q.calls))
        # Xesh "at" ga bog'liq emas
        p2 = dict(p, at=999)
        self.assertEqual(onec_data.payload_sig(p), onec_data.payload_sig(p2))

    def test_suppliers(self):
        people = [{"Имя": "Ali", "Код": 3.0, "Тел": "+998"}, {"Имя": "Vali", "Код": 4.0, "Тел": ""}, {"Имя": "Ali", "Код": 3.0, "Тел": ""}]
        bal = [{"Имя": "Ali", "С": -500000, "Вл": 0}, {"Имя": "Ali", "С": -100000, "Вл": -2}, {"Имя": "Mijoz", "С": 10, "Вл": 0}]
        heads = [{"Номер": "5", "Дата": D(2026, 10, 20, 10), "Контр": "Ali", "КонтрКод": 3, "Сум": 2000000, "Вал": 0, "Ком": ""},
                 {"Номер": "6", "Дата": D(2026, 10, 21, 10), "Контр": "Mijoz", "КонтрКод": 9, "Сум": 1, "Вал": 0, "Ком": ""}]
        q = FakeQ({"РАЗЛИЧНЫЕ Д.Контрагент.Наименование": people, "Остатки": bal, "Документ.Поступления КАК Д ГДЕ Д.Проведен И Д.Дата": heads})
        p = onec_data.suppliers_payload(q, D(2026, 8, 1), D(2026, 10, 31), 1)
        self.assertEqual([x["name"] for x in p["list"]], ["Ali", "Vali"])
        ali = p["list"][0]
        self.assertEqual(ali["bs"], -600000)
        self.assertEqual(ali["bu"], -2)
        self.assertEqual([d["no"] for d in ali["docs"]], ["5"])  # mijoz hujjati kirmaydi

    def test_catalog_refs(self):
        """v5.1: yangi tovar shakli uchun 1C ma'lumotnomalari."""
        q = FakeQ({
            "Справочник.ГруппаТовара": [{"Имя": "Yong'oqlar"}, {"Имя": ""}, {"Имя": "Yong'oqlar"}],
            "Справочник.ЕдИзм": [{"Имя": "kg"}, {"Имя": "dona"}],
        }, broken=("Справочник.Бренд",))
        refs = onec_data.catalog_refs(q)
        self.assertEqual(refs["grp"], ["Yong'oqlar"])  # bo'sh va takror tushadi
        self.assertEqual(refs["unit"], ["kg", "dona"])
        self.assertNotIn("brd", refs)  # xato jadval shunchaki qo'shilmaydi

    def test_active_today(self):
        q = FakeQ({"ВзаиморасчетСКонтрагентом КАК Р": [{"Имя": "Ali"}, {"Имя": ""}]})
        self.assertEqual(onec_data.active_today(q, D(2026, 11, 1, 12)), {"Ali"})
        self.assertIsNone(onec_data.active_today(FakeQ({}, broken=("ВзаиморасчетСКонтрагентом",))))


class Live(unittest.TestCase):
    def test_schedule(self):
        with tempfile.TemporaryDirectory() as tmp:
            m = load_export(tmp)
            self.assertEqual(m.parse_live({}), (0, False, 480, 1200))
            self.assertEqual(m.parse_live({"m": 5, "won": True, "ws": 540, "we": 1260}), (5, True, 540, 1260))
            self.assertEqual(m.parse_live({"m": "x"}), (0, False, 480, 1200))
            self.assertTrue(m.is_work_time(True, 480, 1200, 600))
            self.assertFalse(m.is_work_time(True, 480, 1200, 1300))
            self.assertTrue(m.is_work_time(True, 1200, 240, 100))
            self.assertEqual(m.final_minute(1200), 1199)
            self.assertEqual(m.final_minute(0), 1439)
            self.assertEqual(m.live_interval(1, 2, True, 480, 1200, D(2026, 11, 1, 10)), 60)
            self.assertEqual(m.live_interval(1, 2, True, 480, 1200, D(2026, 11, 1, 23)), 3600)
            self.assertEqual(m.live_interval(0, 2, True, 480, 1200, D(2026, 11, 1, 10)), 0)
            self.assertEqual(m.live_interval(15, 2, False, 480, 1200, D(2026, 11, 1, 23)), 900)


# ---------------------------------------------------------------- soxta 1C (yozish)

class Obj:
    """Soxta 1C obyekti: istalgan rekvizit yoziladi/o'qiladi."""

    def __init__(self, **kw):
        self.__dict__.update(kw)

    def __getattr__(self, k):
        if k.startswith("_"):
            raise AttributeError(k)
        return 0


class Ref(Obj):
    def Пустая(self):
        return False

    def УникальныйИдентификатор(self):
        return self.uid

    def ПолучитьОбъект(self):
        return self.obj


class Tab:
    def __init__(self):
        self.rows = []

    def Добавить(self):
        r = Obj()
        self.rows.append(r)
        return r

    def Количество(self):
        return len(self.rows)

    def Получить(self, i):
        return self.rows[i]

    def Удалить(self, i):
        del self.rows[i]


class FakeDocMgr:
    def __init__(self, c, name, tab):
        self.c, self.name, self.tab = c, name, tab
        self.docs = {}

    def СоздатьДокумент(self):
        c, mgr = self.c, self

        class Doc(Obj):
            def Записать(self, mode=None):
                c.n += 1
                self.Номер = "%010d" % c.n
                self.posted = mode == "post"
                uid = "uuid-%d" % c.n
                self.Ссылка = Ref(uid=uid, obj=self)
                mgr.docs[uid] = self

            def УстановитьПометкуУдаления(self, v):
                self.deleted = v
        d = Doc()
        setattr(d, self.tab, Tab())
        return d

    def ПолучитьСсылку(self, uid):
        return Ref(uid=uid, obj=self.docs.get(uid))


class FakeC:
    def __init__(self):
        self.n = 0
        goods_obj = Obj(ШтрихКод="", ТабШтрихкодов=Tab(), Записать=lambda: None)
        self.goods = {
            "1001": Ref(uid="g1", obj=goods_obj, Код=1001, Наименование="Pista", ВидВал="Сум", ЕдИзм="кг", Обьём=0, ПриходЦена=500, ПриходВал=0),
            "77": Ref(uid="g2", obj=Obj(), Код=77, Наименование="Mayiz", ВидВал="Вал", ЕдИзм="кг", Обьём=10, ПриходЦена=0, ПриходВал=3),
        }
        self.Перечисления = Obj(ВидВал=Obj(Вал="Вал", Сум="Сум"))
        self.РежимЗаписиДокумента = Obj(Проведение="post")
        self.Константы = Obj(Курс=Obj(Получить=lambda: 12600), Склад1=Obj(Получить=lambda: "Asosiy"))
        party = Ref(uid="p1", obj=None, Наименование="Ali")
        c = self

        class Cat:
            def __init__(self, items):
                self.items = items

            def НайтиПоКоду(self, code):
                return self.items.get(str(int(code)) if str(code).replace(".", "").isdigit() else str(code), EmptyRef())

            def НайтиПоНаименованию(self, name, exact):
                for r in self.items.values():
                    if getattr(r, "Наименование", None) == name:
                        return r
                return EmptyRef()

        class EmptyRef:
            def Пустая(self):
                return True

        class Shtrix:
            def __init__(self):
                self.items = {}

            def НайтиПоНаименованию(self, name, exact):
                return self.items.get(name, EmptyRef())

            def СоздатьЭлемент(self):
                cat = self

                class El(Obj):
                    def Записать(self):
                        c.n += 1
                        self.Ссылка = Ref(uid="s%d" % c.n, obj=self)
                        cat.items[self.Наименование] = Ref(uid="s%d" % c.n, obj=self, Товар=self.Товар)

                    def УстановитьПометкуУдаления(self, v):
                        self.deleted = v
                return El()

            def ПолучитьСсылку(self, uid):
                for r in self.items.values():
                    if r.uid == uid:
                        return r
                return Ref(uid=uid, obj=None)

        self.shtrix = Shtrix()
        self.Справочники = Obj(Контрагенты=Cat({"3": party}), Товары=Cat(self.goods), Сотрудники=Obj(Дукон="Dokon"), Штрих=self.shtrix)
        self.Документы = Obj(Поступления=FakeDocMgr(self, "Поступления", "Приход"), ЮкБерувчигаТулов=FakeDocMgr(self, "ЮкБерувчигаТулов", "x"),
                             Возврат_товара_поставщику=FakeDocMgr(self, "Возврат_товара_поставщику", "Таблица"),
                             Возврат_товара_от_клиента=FakeDocMgr(self, "Возврат_товара_от_клиента", "Расход"))

    def String(self, v):
        return getattr(v, "uid", v)

    def NewObject(self, kind, value):
        return value


class FakeOneC:
    def __init__(self):
        self.c = FakeC()
        self.connects = 0

    def connect(self):
        self.connects += 1
        return self

    def close(self):
        pass


class FakeCloud:
    def __init__(self):
        self.data = {}
        self.calls = []

    def call(self, method, path, body=None, timeout=60, **q):
        self.calls.append((method, path))
        if method == "GET":
            node = self.data
            for p in path.split("/"):
                node = node.get(p) if isinstance(node, dict) else None
            return node
        parts = path.split("/")
        node = self.data
        for p in parts[:-1]:
            node = node.setdefault(p, {})
        if method == "PUT":
            node[parts[-1]] = body
        elif method == "PATCH":
            node.setdefault(parts[-1], {}).update(body)
        elif method == "DELETE":
            node.pop(parts[-1], None)
        return None


class Cfg:
    inbox = "abcdefghijkmnpqrstuvwxyzABCDEF23"
    write_on = True
    require_backup = True

    def __init__(self, conn):
        self.connection = conn


def req(kind, p):
    return {"at": 1, "k": kind, "by": "Admin", "p": json.dumps(p, ensure_ascii=False)}


class WriteTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.base = os.path.join(self.tmp.name, "baza")
        os.makedirs(self.base)
        with open(os.path.join(self.base, "1Cv8.1CD"), "wb") as f:
            f.write(b"1CD" * 100)
        self.cloud = FakeCloud()
        self.onec = FakeOneC()
        self.w = onec_write.Writer(Cfg('File="%s";' % self.base), self.cloud, self.onec, self.tmp.name, now=lambda: D(2026, 11, 1, 10, 0))
        self.inbox = Cfg.inbox

    def tearDown(self):
        self.tmp.cleanup()

    def put(self, rid, item):
        self.cloud.data.setdefault("w", {}).setdefault(self.inbox, {})[rid] = item

    def result(self, rid):
        return self.cloud.data.get("wr", {}).get(self.inbox, {}).get(rid)

    def test_pure(self):
        r = onec_write.parse_request("w1", req("in", {"c": "Ali", "cc": "3", "l": [["Pista", "1001", 2, 500, 0]]}))
        self.assertIsNone(onec_write.problem(r))
        self.assertEqual(r["lines"][0]["q"], 2.0)
        self.assertIsNone(onec_write.parse_request("w1", {"k": "drop", "p": "{}"}))
        self.assertIsNone(onec_write.parse_request("w1", {"k": "in", "p": "buzuq"}))
        self.assertIn("miqdor", onec_write.problem(onec_write.parse_request("w", req("in", {"c": "A", "l": [["X", "", 0, 1, 0]]}))))
        self.assertIn("summa", onec_write.problem(onec_write.parse_request("w", req("sp", {"c": "A", "ns": 0}))))
        self.assertIn("shtrix", onec_write.problem(onec_write.parse_request("w", req("bc", {"c": "A", "bc": "12 3"}))))
        v = onec_write.row_values({"q": 2.0, "ps": 500.0, "pu": 0.04}, {"cur": "Вал", "unit_pieces": 10})
        self.assertTrue(v["usd"])
        self.assertEqual(v["sum"], 0.08)
        # Telefonda so'mda yozilgan, 1C'da dollarli tovar — kurs bilan
        v2 = onec_write.row_values({"q": 2.0, "ps": 25200.0, "pu": 0.0}, {"cur": "Вал"}, 12600)
        self.assertEqual((v2["price"], v2["sum"]), (2.0, 4.0))
        v3 = onec_write.row_values({"q": 1.0, "ps": 0.0, "pu": 2.0}, {"cur": "Сум"}, 12600)
        self.assertEqual(v3["price"], 25200.0)
        self.assertEqual(onec_write.backup_source('File="D:\\1C\\Baza";Usr="a";'), os.path.join("D:\\1C\\Baza", "1Cv8.1CD"))
        self.assertIsNone(onec_write.backup_source('Srvr="srv";Ref="base";'))

    def test_receipt_once_and_undo(self):
        self.put("w1", req("in", {"c": "Ali", "cc": "3", "n": "kirim", "l": [["Pista", "1001", 2, 500, 0], ["Mayiz", "77", 10, 0, 3]]}))
        done, errs = self.w.run()
        self.assertEqual((done, errs), (1, 0))
        res = self.result("w1")
        self.assertTrue(res["ok"])
        self.assertEqual(res["st"], "done")
        self.assertNotIn("w1", self.cloud.data["w"][self.inbox])
        doc = self.onec.c.Документы.Поступления.docs[res["ref"]]
        self.assertTrue(doc.posted)
        self.assertEqual(doc.ВсегоСум, 1000)
        self.assertEqual(doc.ВсегоВал, 30)
        self.assertEqual(len(doc.Приход.rows), 2)
        self.assertEqual(doc.Приход.rows[0].ЦенаСум1, 500)
        self.assertEqual(doc.Приход.rows[1].ЦенаВал1, 3)
        self.assertEqual(doc.Склад, "Asosiy")
        self.assertTrue(doc.Комментарий.startswith("Telefondan"))
        # Zaxira olindi
        self.assertTrue(os.path.exists(os.path.join(self.tmp.name, "backup", "1Cv8_20261101.1CD")))
        # Shu so'rov yana kelsa (natija yetib bormagan) — qayta kiritilmaydi
        self.put("w1", req("in", {"c": "Ali", "cc": "3", "l": [["Pista", "1001", 2, 500, 0]]}))
        self.w.run()
        self.assertEqual(len(self.onec.c.Документы.Поступления.docs), 1)
        # Jurnal qayta o'qilganda ham
        w2 = onec_write.Writer(Cfg('File="%s";' % self.base), self.cloud, self.onec, self.tmp.name)
        self.put("w1", req("in", {"c": "Ali", "cc": "3", "l": [["Pista", "1001", 2, 500, 0]]}))
        w2.run()
        self.assertEqual(len(self.onec.c.Документы.Поступления.docs), 1)
        # Bekor qilish
        self.put("w2", req("undo", {"t": "w1"}))
        self.w.run()
        self.assertTrue(self.result("w2")["ok"])
        self.assertTrue(doc.deleted)
        self.assertEqual(self.result("w1")["st"], "undone")

    def test_errors(self):
        self.put("w1", req("in", {"c": "Noma'lum", "l": [["Pista", "1001", 1, 1, 0]]}))
        self.put("w2", req("in", {"c": "Ali", "cc": "3", "l": [["Yo'q tovar", "", 1, 1, 0]]}))
        self.put("w3", {"k": "in", "p": "{"})
        done, errs = self.w.run()
        self.assertEqual((done, errs), (0, 3))
        self.assertIn("kontragent", self.result("w1")["e"])
        self.assertIn("tovar", self.result("w2")["e"])
        self.assertIn("buzuq", self.result("w3")["e"])
        self.assertEqual(len(self.onec.c.Документы.Поступления.docs), 0)

    def test_backup_required(self):
        w = onec_write.Writer(Cfg('File="%s";' % os.path.join(self.tmp.name, "yoq")), self.cloud, self.onec, self.tmp.name)
        self.put("w1", req("sp", {"c": "Ali", "cc": "3", "ns": 100000}))
        w.run()
        self.assertIn("zaxira", self.result("w1")["e"])
        self.assertEqual(len(self.onec.c.Документы.ЮкБерувчигаТулов.docs), 0)

    def test_payment_and_barcode(self):
        self.put("w1", req("sp", {"c": "Ali", "cc": "3", "ns": 100000, "pu": 5, "n": "to'lov"}))
        self.put("w2", req("bc", {"c": "Pista", "cc": "1001", "bc": "4780000000017"}))
        done, errs = self.w.run()
        self.assertEqual((done, errs), (2, 0))
        pay = list(self.onec.c.Документы.ЮкБерувчигаТулов.docs.values())[0]
        self.assertEqual((pay.НакдСум, pay.ПластикВал), (100000, 5))
        g = self.onec.c.goods["1001"].obj
        self.assertEqual(g.ШтрихКод, "4780000000017")
        self.assertEqual(g.ТабШтрихкодов.rows[0].ШтрихКод, "4780000000017")
        # Shu kod yana — o'tkazib yuboriladi (dublikat yo'q)
        self.put("w3", req("bc", {"c": "Pista", "cc": "1001", "bc": "4780000000017"}))
        self.w.run()
        self.assertEqual(self.result("w3")["st"], "skip")
        # Bekor qilish — tovar ro'yxatidan ham olinadi
        self.put("w4", req("undo", {"t": "w2"}))
        self.w.run()
        self.assertTrue(self.result("w4")["ok"])
        self.assertEqual(g.ТабШтрихкодов.Количество(), 0)
        self.assertEqual(g.ШтрихКод, "")

    def test_disabled(self):
        cfg = Cfg('File="%s";' % self.base)
        cfg.write_on = False
        w = onec_write.Writer(cfg, self.cloud, self.onec, self.tmp.name)
        self.put("w1", req("sp", {"c": "Ali", "ns": 1}))
        w.run()
        self.assertIn("o'chiq", self.result("w1")["e"])


if __name__ == "__main__":
    unittest.main(verbosity=1)
