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


class RateTest(unittest.TestCase):
    setUp0 = T.WriteTest.setUp
    tearDown = T.WriteTest.tearDown
    put = T.WriteTest.put
    result = T.WriteTest.result

    def setUp(self):
        self.setUp0()
        self.onec = CountingOneC()
        c = self.onec.real
        box = {"v": 12600}
        c.Константы.Курс = T.Obj(Получить=lambda: box["v"], Установить=lambda v: box.__setitem__("v", v))
        self.box = box
        recs = []

        class Mgr(T.Obj):
            def Записать(self):
                recs.append((self.Дата, self.Сумма))
        c.РегистрыСведений = T.Obj(Курс=T.Obj(СоздатьМенеджерЗаписи=lambda: Mgr()))
        self.recs = recs
        self.w = onec_write.Writer(T.Cfg('File="%s";' % self.base), self.cloud, self.onec, self.tmp.name, now=lambda: T.D(2026, 11, 2, 9, 0))

    def test_rate_and_undo(self):
        self.assertIn("kurs", onec_write.problem(onec_write.parse_request("w", T.req("rt", {"f": {"rate": 0}}))))
        self.put("w1", T.req("rt", {"f": {"rate": 12750.5}}))
        self.w.run()
        self.assertTrue(self.result("w1")["ok"])
        self.assertEqual(self.box["v"], 12750.5)
        self.assertEqual(self.recs[0][1], 12750.5)
        self.put("w2", T.req("undo", {"t": "w1"}))
        self.w.run()
        self.assertTrue(self.result("w2")["ok"])
        self.assertEqual(self.box["v"], 12600)


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




# ---------------------------------------------------------------- v4.1

class PartyCat:
    """Soxta Справочник.Контрагенты: СоздатьЭлемент, papkalar, ПолучитьСсылку."""

    def __init__(self, c):
        self.c = c
        self.items = {"3": T.Ref(uid="p1", obj=None, Наименование="Ali", Код=3)}
        self.Клиенты = "KLIENTLAR"
        self.Поставщики = "POSTAVSHIKLAR"
        ali = PartyObj(self, Наименование="Ali", Тел="", Тел2="", Адрес="", Регион=None, Код=3)
        ali.Ссылка = self.items["3"]
        self.items["3"].obj = ali

    def НайтиПоКоду(self, code):
        return self.items.get(str(int(float(code))), Empty())

    def НайтиПоНаименованию(self, name, exact):
        for r in self.items.values():
            if r.obj is not None and r.obj.Наименование == name:
                return r
        return Empty()

    def СоздатьЭлемент(self):
        return PartyObj(self, Наименование="", Тел="", Тел2="", Адрес="", Регион=None, Код=0, _new=True)

    def ПолучитьСсылку(self, uid):
        return next((r for r in self.items.values() if r.uid == uid), T.Ref(uid=uid, obj=None))


class Empty:
    def Пустая(self):
        return True


class PartyObj(T.Obj):
    def __init__(self, cat, **kw):
        super().__init__(**kw)
        self._cat = cat

    def Записать(self):
        self._cat.c.n += 1
        if getattr(self, "_new", False) and not getattr(self, "_saved", False):
            self._saved = True
            self.Код = 100 + self._cat.c.n
            ref = T.Ref(uid="k%d" % self.Код, obj=self, Код=self.Код)
            self.Ссылка = ref
            self._cat.items[str(self.Код)] = ref
        self.writes = getattr(self, "writes", 0) + 1

    def УстановитьНовыйКод(self):
        pass

    def УстановитьПометкуУдаления(self, v):
        self.deleted = v


class RegionCat:
    def НайтиПоНаименованию(self, name, exact):
        return T.Ref(uid="r1", obj=None, Наименование=name) if name == "Toshkent" else Empty()


class Query:
    """Soxta Запрос: tovar qoldig'i (Товар.Остатки)."""

    def __init__(self, stock):
        self.stock = stock
        self.params = {}

    def УстановитьПараметр(self, k, v):
        self.params[k] = v

    def Выполнить(self):
        q = self

        class Sel:
            done = False

            def Выбрать(self):
                return self

            def Следующий(self):
                if self.done:
                    return False
                self.done = True
                kol, sm, vl = q.stock.get(q.params["Т"].Код, (0, 0, 0))
                self.Кол, self.Сум, self.Вал = kol, sm, vl
                return True
        return Sel()


class WriteOffMgr(T.FakeDocMgr):
    def СоздатьДокумент(self):
        d = super().СоздатьДокумент()
        d.ОбменДанными = T.Obj(Загрузка=False)

        class Set(T.Tab):
            Записывать = False
        d.Движения = T.Obj(Товар=Set(), Прибль=Set())
        return d


def make_v41(c):
    c.Справочники.Контрагенты = PartyCat(c)
    c.Справочники.Регион = RegionCat()
    c.Документы.СписанияТоваров = WriteOffMgr(c, "СписанияТоваров", "Таблица")
    c.ВидДвиженияНакопления = T.Obj(Расход="rasxod")
    c.Перечисления.ХарактаТури = T.Obj(СписанияМахсулот="spisaniya")
    # tovar 1001 (so'm): 10 kg, 5 000 so'm; 77 (dollar): 4 kg, 12 $
    stock = {1001: (10, 5000, 0), 77: (4, 0, 12)}
    orig = c.NewObject
    c.NewObject = lambda kind, value=None: Query(stock) if kind == "Запрос" else orig(kind, value)
    return c


class V41Test(unittest.TestCase):
    setUp0 = T.WriteTest.setUp
    tearDown = T.WriteTest.tearDown
    put = T.WriteTest.put
    result = T.WriteTest.result

    def setUp(self):
        self.setUp0()
        self.onec = CountingOneC()
        make_v41(self.onec.real)
        self.w = onec_write.Writer(T.Cfg('File="%s";' % self.base), self.cloud, self.onec, self.tmp.name, now=lambda: T.D(2026, 11, 2, 10, 0))

    def test_validation(self):
        P = lambda k, p: onec_write.problem(onec_write.parse_request("w", T.req(k, p)))  # noqa: E731
        self.assertIn("nomi", P("nk", {"f": {"tel": "998901234567"}}))
        self.assertIn("telefon", P("nk", {"f": {"nm": "Vali", "tel": "abc"}}))
        self.assertIn("guruh", P("nk", {"f": {"nm": "Vali", "grp": "x"}}))
        self.assertIsNone(P("nk", {"f": {"nm": "Vali", "tel": "+998 90 123-45-67", "grp": "s"}}))
        self.assertIn("kontragent", P("ke", {"f": {"tel": "998901234567"}}))
        self.assertIn("o'zgarish", P("ke", {"c": "Ali", "f": {"grp": "c"}}))
        self.assertIn("tovar", P("wo", {}))
        self.assertIn("miqdor", P("wo", {"l": [["Pista", "1001", 0, 0, 0]]}))
        self.assertIsNone(P("wo", {"l": [["Pista", "1001", 2, 0, 0]]}))

    def test_new_and_edit_party_and_undo(self):
        cat = self.onec.real.Справочники.Контрагенты
        self.put("w1", T.req("nk", {"f": {"nm": "Vali  aka", "tel": "998901112233", "adr": "Bozor", "reg": "Toshkent", "grp": "s"}}))
        self.w.run()
        r = self.result("w1")
        self.assertTrue(r["ok"], r)
        obj = cat.items[r["no"]].obj
        self.assertEqual((obj.Наименование, obj.Тел, obj.Адрес, obj.Родитель, obj.Регион.Наименование), ("Vali aka", "998901112233", "Bozor", "POSTAVSHIKLAR", "Toshkent"))
        # takror nom — rad
        self.put("w2", T.req("nk", {"f": {"nm": "Vali aka"}}))
        self.w.run()
        self.assertIn("bor", self.result("w2")["e"])
        # region yo'q — rad
        self.put("w3", T.req("nk", {"f": {"nm": "Yangi", "reg": "Marsda"}}))
        self.w.run()
        self.assertIn("region", self.result("w3")["e"])
        # tahrirlash va bekor qilish
        ali = cat.items["3"].obj
        self.put("w4", T.req("ke", {"c": "Ali", "cc": "3", "f": {"tel": "998907777777", "nm": "Ali aka"}}))
        self.w.run()
        self.assertTrue(self.result("w4")["ok"])
        self.assertEqual((ali.Тел, ali.Наименование), ("998907777777", "Ali aka"))
        self.put("w5", T.req("undo", {"t": "w4"}))
        self.w.run()
        self.assertEqual((ali.Тел, ali.Наименование), ("", "Ali"))
        self.put("w6", T.req("undo", {"t": "w1"}))
        self.w.run()
        self.assertTrue(obj.deleted)

    def test_write_off_som_posts_normally(self):
        self.put("w1", T.req("wo", {"l": [["Pista", "1001", 2, 0, 0]], "n": "buzilgan"}))
        self.w.run()
        r = self.result("w1")
        self.assertTrue(r["ok"], r)
        doc = list(self.onec.real.Документы.СписанияТоваров.docs.values())[0]
        row = doc.Таблица.rows[0]
        self.assertTrue(doc.posted)
        self.assertEqual((row.Кол, row.Ост, row.УртачаНархСум, row.СуммаСум, doc.ВсегоСум), (2, 10, 500, 1000, 1000))
        self.assertIn("buzilgan", doc.Комментарий)
        self.assertFalse(doc.ОбменДанными.Загрузка)

    def test_write_off_usd_manual_movements_and_shortage(self):
        self.put("w1", T.req("wo", {"l": [["Mayiz", "77", 4, 0, 0], ["Pista", "1001", 12, 0, 0]]}))
        self.w.run()
        r = self.result("w1")
        self.assertTrue(r["ok"], r)
        self.assertIn("qoldiqdan", r["e"])  # Pista: 12 > 10 — ogohlantirish
        doc = list(self.onec.real.Документы.СписанияТоваров.docs.values())[0]
        self.assertTrue(doc.ОбменДанными.Загрузка)
        self.assertTrue(doc.Проведен)
        usd_row = doc.Таблица.rows[0]
        self.assertEqual((usd_row.СуммаВал, usd_row.УртачаНархВал), (12, 3))  # butun qoldiq — qoldiq summasi
        mv = doc.Движения
        self.assertTrue(mv.Товар.Записывать and mv.Прибль.Записывать)
        self.assertEqual([(m.ВидДвижения, m.Количество, m.СуммаВал, m.СуммаСум) for m in mv.Товар.rows], [("rasxod", 4, 12, 0), ("rasxod", 12, 0, 6000)])
        self.assertEqual([(m.ХаракатТури, m.ПриходСумма, m.ПриходВал) for m in mv.Прибль.rows], [("spisaniya", 0, 12), ("spisaniya", 6000, 0)])
        self.assertEqual((doc.ВсегоСум, doc.ВсегоВал), (6000, 12))

    def test_com_setup_hint(self):
        self.assertIn("Модуль внешнего соединения", onec_write.explain_1c_error("{Справочник.Товары.МодульОбъекта(125)}: Переменная не определена (мМобилАпп)"))
        self.assertEqual(onec_write.explain_1c_error("boshqa xato"), "boshqa xato")


class ReportsTest(unittest.TestCase):
    def test_build_reports(self):
        import onec_data
        profit = [
            {"Товар": "Pista", "Код": 1001, "Кол": 10, "ПС": 5000, "РС": 8000, "ФС": 3000, "ПВ": 0, "РВ": 0, "ФВ": 0},
            {"Товар": "Mayiz", "Код": 77, "Кол": 2, "ПС": 0, "РС": 0, "ФС": 0, "ПВ": 6, "РВ": 10, "ФВ": 4},
            {"Товар": "Bo'sh", "Код": 1, "Кол": 0, "ПС": 0, "РС": 0, "ФС": 0, "ПВ": 0, "РВ": 0, "ФВ": 0},
        ]
        out = onec_data.build_reports(
            profit, {"С": 500, "Вл": 1}, [{"Имя": "Ijara", "С": 2000000, "Вл": 0}, {"Имя": "Svet", "С": 0, "Вл": 0}],
            [{"Н": 100, "П": 20, "К": 5, "НВ": 3, "ПВ": 0}], [{"Имя": "Savdo", "НП": 50, "НР": 0, "ПП": 0, "ПР": 0, "КП": 0, "КР": 0, "ВП": 0, "ВР": 0}],
            [{"Товар": "Pista", "Код": 1001, "Кол": 3, "Вал": 0, "ЦС": 500, "ЦВ": 0}, {"Товар": "Mayiz", "Код": 77, "Кол": -1, "Вал": 1, "ЦС": 0, "ЦВ": 3},
             {"Товар": "Nol", "Код": 2, "Кол": 0, "Вал": 0, "ЦС": 1, "ЦВ": 0}],
            [{"Имя": "Ali", "Пост": 0, "С": 700, "Вл": 0}, {"Имя": "Zavod", "Пост": 1, "С": -900, "Вл": -2}],
        )
        self.assertEqual(out["profit"]["tot"], [5000, 8000, 3000, 6, 10, 4])
        self.assertEqual(out["profit"]["n"], 2)
        self.assertEqual(out["profit"]["rows"][0][0], "Mayiz")  # 4$ ~ 48 000 > 3 000
        self.assertEqual(out["profit"]["disc"], [500, 1])
        self.assertEqual(out["exp"], [["Ijara", 2000000, 0]])
        self.assertEqual(out["cash"]["bal"], [100, 20, 5, 3, 0])
        self.assertEqual(len(out["cash"]["turn"]), 1)
        self.assertEqual((out["stock"]["n"], out["stock"]["vs"], out["stock"]["vu"], out["stock"]["neg"]), (2, 1500, -3, 1))
        self.assertEqual(out["debt"]["pos"], [700, 0])
        self.assertEqual(out["debt"]["neg"], [900, 2])
        self.assertEqual(out["debt"]["owe"][0][0], "Zavod")

    def test_payload_survives_broken_query(self):
        import onec_data

        class Q:
            def query(self, text, fields, **params):
                if "Харажатлар" in text:
                    raise RuntimeError("registr yo'q")
                return []
        p = onec_data.reports_payload(Q(), T.D(2026, 11, 1), T.D(2026, 11, 2, 23, 59, 59), 1)
        self.assertEqual(len(p["err"]), 1)
        self.assertIn("profit", p)
        self.assertNotIn("exp", p)
        self.assertEqual((p["f"], p["t"]), ("20261101", "20261102"))




class QueryAliasTest(unittest.TestCase):
    """1C so'rov tilida "В" (IN) va "И" (AND) — kalit so'z: nom (alias) sifatida ishlatib bo'lmaydi ("Ожидается имя")."""

    def test_no_reserved_aliases(self):
        import re
        import onec_data
        import onec_scale
        texts = [v for m in (onec_data, onec_scale) for v in vars(m).values() if isinstance(v, str) and "ВЫБРАТЬ" in v]
        texts += [onec_scale.ips_query(), onec_scale.goods_query(True), onec_scale.goods_query(False)]
        texts += [onec_data.head_query(k) for k in onec_data.DOCS] + [q for q in (onec_data.lines_query(k) for k in onec_data.DOCS) if q]
        src = open(os.path.join(HERE, "onec_write.py"), encoding="utf-8").read()
        texts += re.findall(r'"(ВЫБРАТЬ[^"]*)"', src)
        bad = re.compile(r"(?:КАК|ИЗ [^ ]+) (В|И|ИЛИ|НЕ|ПО|ИЗ|ДЛЯ|ВСЕ|ТИП|ЕСТЬ)(?=[\s,)]|$)")
        for t in texts:
            self.assertIsNone(bad.search(t), t[:120])
        self.assertGreater(len(texts), 15)


if __name__ == "__main__":
    unittest.main()
