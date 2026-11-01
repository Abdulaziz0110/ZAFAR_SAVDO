# -*- coding: utf-8 -*-
"""v3.12: yangi yozish turlari (mijoz to'lovi, kassa, tovar kartochkasi, yangi tovar), bitta ulanish, Firebase oqimi."""
import io
import json
import os
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import onec_write  # noqa: E402
import test_onec_data as T  # noqa: E402


class GoodsObj(T.Obj):
    """Tovar obyekti: Записать — saqlaydi, Скопировать — yangi tovar."""

    def __init__(self, c, **kw):
        super().__init__(**kw)
        self._c = c

    def Записать(self):
        self._c.goods_writes += 1
        if getattr(self, "_new", False) and not getattr(self, "_saved", False):
            self._saved = True
            self._c.n += 1
            self.Код = 5000 + self._c.n
            ref = T.Ref(uid="ng%d" % self._c.n, obj=self, Код=self.Код, Наименование=self.Наименование)
            self.Ссылка = ref
            self._c.goods[str(self.Код)] = ref
            if self.ШтрихКод:
                el = self._c.shtrix.СоздатьЭлемент()
                el.Наименование = self.ШтрихКод
                el.Товар = ref
                el.Записать()

    def Скопировать(self):
        d = {k: v for k, v in self.__dict__.items() if not k.startswith("_")}
        d["ТабШтрихкодов"] = T.Tab()
        d["ТабШтрихкодов"].Добавить().ШтрихКод = "copied"
        o = GoodsObj(self._c, **d)
        o._new = True
        o.Очистить_called = False
        return o

    def УстановитьНовыйКод(self):
        pass

    def УстановитьПометкуУдаления(self, v):
        self.deleted = v


class Tab2(T.Tab):
    def Очистить(self):
        self.rows = []


def make_c():
    c = T.FakeC()
    c.goods_writes = 0
    for code, ref in c.goods.items():
        o = GoodsObj(c, Наименование=ref.Наименование, ШтрихКод="", ТабШтрихкодов=Tab2(), ПЛУ=0, Весы=None,
                     ПродажЦена1=1000, ПродажЦена2=900, ПродажЦена3=1100, ПродажЦена4=950, ПриходЦена=500,
                     ПродажВал1=1, ПродажВал2=1, ПродажВал3=1, ПродажВал4=1, ПриходВал=0.5, Код=ref.Код, Ост=7)
        ref.obj = o
    # Скопировать ichidagi jadvallar ham tozalanadigan bo'lsin
    orig_copy = GoodsObj.Скопировать

    def copy(self):
        o = orig_copy(self)
        t = Tab2()
        t.rows = o.ТабШтрихкодов.rows
        o.ТабШтрихкодов = t
        return o
    GoodsObj.Скопировать = copy

    goods_cat = c.Справочники.Товары

    class Empty:
        def Пустая(self):
            return True

    goods_cat.НайтиПоРеквизиту = lambda name, value: next((r for r in c.goods.values() if getattr(r.obj, name, None) == value), Empty())
    goods_cat.ПолучитьСсылку = lambda uid: next((r for r in c.goods.values() if r.uid == uid), T.Ref(uid=uid, obj=None))
    ops = {"12": T.Ref(uid="op12", obj=None, Код=12, Наименование="Ijara")}
    c.Справочники.Операции = goods_cat.__class__(ops)
    c.Справочники.Весы = T.Obj(ОбщиеВесы="umumiy")
    c.Документы.Оплата_Клента = T.FakeDocMgr(c, "Оплата_Клента", "x")
    c.Документы.КассаПриход = T.FakeDocMgr(c, "КассаПриход", "x")
    c.Документы.КассаРасход = T.FakeDocMgr(c, "КассаРасход", "x")
    return c


class CountingOneC(T.FakeOneC):
    """Haqiqiy OneC kabi: close() ulanishni uzadi, connect() qayta ulaydi."""

    def __init__(self):
        super().__init__()
        self.real = make_c()
        self.c = None

    def connect(self):
        if self.c is None:
            self.connects += 1
            self.c = self.real
        return self

    def close(self):
        self.c = None


class NewKindsTest(unittest.TestCase):
    setUp0 = T.WriteTest.setUp
    tearDown = T.WriteTest.tearDown
    put = T.WriteTest.put
    result = T.WriteTest.result

    def setUp(self):
        self.setUp0()
        self.onec = CountingOneC()
        self.w = onec_write.Writer(T.Cfg('File="%s";' % self.base), self.cloud, self.onec, self.tmp.name, now=lambda: T.D(2026, 11, 1, 10, 0))

    def test_validation(self):
        P = lambda k, p: onec_write.problem(onec_write.parse_request("w", T.req(k, p)))  # noqa: E731
        self.assertIn("mijoz", P("cp", {"ns": 5}))
        self.assertIn("summa", P("cp", {"c": "Ali", "ns": 0}))
        self.assertIsNone(P("cp", {"c": "Ali", "ks": 5}))
        self.assertIn("moddasi", P("ce", {"ns": 5}))
        self.assertIsNone(P("ci", {"op": "Ijara", "nu": 5}))
        self.assertIn("o'zgarish", P("ge", {"cc": "1001", "f": {}}))
        self.assertIn("PLU", P("ge", {"cc": "1001", "f": {"plu": 1.5}}))
        self.assertIn("narx", P("ge", {"cc": "1001", "f": {"p1": -1}}))
        self.assertIsNone(P("ge", {"cc": "1001", "f": {"p1": 1200, "cur": "s", "x": "hack"}}))
        self.assertNotIn("x", onec_write.parse_request("w", T.req("ge", {"cc": "1001", "f": {"x": 1}}))["f"])
        self.assertIn("nomi", P("ng", {"cc": "1001", "f": {"p1": 5}}))
        self.assertIn("shtrix", P("ng", {"cc": "1001", "f": {"nm": "Yangi", "bc": "1 2"}}))

    def test_client_pay_and_cash_one_connection(self):
        self.put("w1", T.req("cp", {"c": "Ali", "ns": 50000, "ku": 3, "ds": 1000, "n": "qarz"}))
        self.put("w2", T.req("ce", {"op": "Ijara", "ns": 200000, "n": "oktyabr"}))
        self.put("w3", T.req("ci", {"opc": "12", "pu": 10}))
        self.put("w4", T.req("ce", {"op": "Yo'q modda", "ns": 1}))
        done, errs = self.w.run()
        self.assertEqual((done, errs), (3, 1))
        self.assertEqual(self.onec.connects, 1)  # hammasi bitta ulanishda
        self.assertIsNone(self.onec.c)  # oxirida uzildi
        pay = list(self.onec.real.Документы.Оплата_Клента.docs.values())[0]
        self.assertTrue(pay.posted)
        self.assertEqual((pay.НакдСум, pay.КликВал, pay.ЧегирмаСум, pay.Сотрудник), (50000, 3, 1000, "Dokon"))
        out = list(self.onec.real.Документы.КассаРасход.docs.values())[0]
        self.assertEqual((out.НаличСум, out.Операция.Наименование), (200000, "Ijara"))
        self.assertIn("oktyabr", out.Коментария)
        inc = list(self.onec.real.Документы.КассаПриход.docs.values())[0]
        self.assertEqual(inc.ПеречВал, 10)
        self.assertIn("modda", self.result("w4")["e"])

    def test_uses_existing_connection(self):
        self.onec.connect()
        self.put("w1", T.req("ci", {"op": "Ijara", "ns": 5}))
        self.w.run()
        self.assertIsNotNone(self.onec.c)  # hisobot tuzilayotgan ulanish yopilmadi
        self.assertEqual(self.onec.connects, 1)

    def test_goods_edit_and_undo(self):
        g = self.onec.real.goods["1001"].obj
        self.put("w1", T.req("ge", {"cc": "1001", "f": {"p1": 1300, "p4": 1000, "pin": 600, "plu": 25, "cur": "s"}}))
        self.w.run()
        self.assertTrue(self.result("w1")["ok"])
        self.assertEqual((g.ПродажЦена1, g.ПродажЦена4, g.ПриходЦена, g.ПЛУ, g.Весы), (1300, 1000, 600, 25, "umumiy"))
        self.assertTrue(self.w.goods_changed)
        # Dollarli tovar, narx so'mda kiritilgan — kurs bilan
        self.put("w2", T.req("ge", {"cc": "77", "f": {"p3": 25200, "cur": "s"}}))
        self.w.run()
        self.assertEqual(self.onec.real.goods["77"].obj.ПродажВал3, 2.0)
        # PLU band
        self.put("w3", T.req("ge", {"cc": "77", "f": {"plu": 25}}))
        self.w.run()
        self.assertIn("band", self.result("w3")["e"])
        # Bekor qilish — eski narxlar qaytadi
        self.put("w4", T.req("undo", {"t": "w1"}))
        self.w.run()
        self.assertTrue(self.result("w4")["ok"])
        self.assertEqual((g.ПродажЦена1, g.ПродажЦена4, g.ПриходЦена, g.ПЛУ), (1000, 950, 500, 0))

    def test_new_goods(self):
        self.put("w1", T.req("ng", {"cc": "1001", "f": {"nm": "Pista  qovurilgan", "p1": 1500, "bc": "4780000000099", "plu": 31, "cur": "s"}}))
        self.w.run()
        res = self.result("w1")
        self.assertTrue(res["ok"], res)
        new = self.onec.real.goods[res["no"]].obj
        self.assertEqual(new.Наименование, "Pista qovurilgan")
        self.assertEqual((new.ПродажЦена1, new.ПродажЦена2, new.ПЛУ, new.Ост), (1500, 900, 31, 0))
        self.assertEqual(new.ТабШтрихкодов.Количество(), 0)  # namunadan ko'chgan kodlar olinmaydi
        self.assertIn("4780000000099", self.onec.real.shtrix.items)
        # Shu nom yana — rad etiladi
        self.put("w2", T.req("ng", {"cc": "1001", "f": {"nm": "Pista qovurilgan"}}))
        self.w.run()
        self.assertIn("bor", self.result("w2")["e"])
        # Shtrix kod band
        self.put("w3", T.req("ng", {"cc": "1001", "f": {"nm": "Boshqa", "bc": "4780000000099"}}))
        self.w.run()
        self.assertIn("boshqa tovarda", self.result("w3")["e"])
        # Bekor qilish — o'chirish belgisi
        self.put("w4", T.req("undo", {"t": "w1"}))
        self.w.run()
        self.assertTrue(new.deleted)


class WakerTest(unittest.TestCase):
    def test_feed(self):
        import importlib.util
        spec = importlib.util.spec_from_file_location("onec_export", os.path.join(HERE, "onec_export.py"))
        m = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(m)

        class Cfg:
            url = "https://x.firebaseio.com"
            secret = "s"
        w = m.Waker(Cfg(), ["w/abc"])
        self.assertIn("/w/abc.json?auth=s", w.url("w/abc"))
        lines = [b"event: keep-alive\n", b"data: null\n", b"event: put\n", b'data: {"path":"/w1","data":null}\n']
        self.assertTrue(w.feed(lines))
        self.assertFalse(w.event.is_set())  # o'chirish — uyg'otmaydi
        w.feed(io.BytesIO(b'event: put\ndata: {"path":"/w2","data":{"k":"bc"}}\n\n'))
        self.assertTrue(w.wait(0.01))
        self.assertFalse(w.event.is_set())
        self.assertFalse(w.feed([b"event: auth_revoked\n", b"data: credential expired\n"]))


class OpsTest(unittest.TestCase):
    def test_cash_ops(self):
        import onec_data

        class Q:
            def query(self, text, fields, **kw):
                assert "Операции" in text
                return [{"Код": 12.0, "Имя": "Ijara"}, {"Код": 3, "Имя": ""}]
        self.assertEqual(onec_data.cash_ops(Q()), [["12", "Ijara"]])

        class Bad:
            def query(self, *a, **k):
                raise RuntimeError("yo'q")
        self.assertEqual(onec_data.cash_ops(Bad()), [])


if __name__ == "__main__":
    unittest.main(verbosity=1)
