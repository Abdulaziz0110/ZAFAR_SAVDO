# -*- coding: utf-8 -*-
import datetime as dt
import json
import os
import shutil
import sys
import tempfile
import threading
import time
import unittest
import urllib.request
from http.server import ThreadingHTTPServer

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import kassa  # noqa: E402
import kassa_core as kc  # noqa: E402


class Obj:
    def __init__(self, **kw):
        self.__dict__.update(kw)


class Ref(Obj):
    def __init__(self, name="", **kw):
        super().__init__(**kw)
        self._name = name

    def Пустая(self):  # noqa: N802
        return False

    def __str__(self):
        return self._name


class Cat:
    def __init__(self, items, **named):
        self.items = items
        self.__dict__.update(named)

    def НайтиПоКоду(self, code):  # noqa: N802
        return self.items.get(str(code))

    def НайтиПоНаименованию(self, name, exact):  # noqa: N802
        for r in self.items.values():
            if str(r) == name:
                return r
        return Obj(Пустая=lambda: True)

    def СоздатьЭлемент(self):  # noqa: N802
        cat = self
        el = Obj(Тел="")

        def write():
            el.Код = float(max(int(k) for k in cat.items) + 1)
            cat.items[str(int(el.Код))] = Ref(el.Наименование, Код=el.Код, Тел=el.Тел, Родитель=el.Родитель)
        el.УстановитьНовыйКод = lambda: None
        el.Записать = write
        return el


class Rows:
    def __init__(self, rows):
        self.rows = list(rows)
        self.cur = None

    def Следующий(self):  # noqa: N802
        if not self.rows:
            return False
        self.cur = self.rows.pop(0)
        return True

    def __getattr__(self, k):
        return self.__dict__["cur"].get(k)


class Query:
    def __init__(self, fake):
        self.fake = fake
        self.Текст = ""
        self.params = {}

    def УстановитьПараметр(self, k, v):  # noqa: N802
        self.params[k] = v

    def Выполнить(self):  # noqa: N802
        return Obj(Выбрать=lambda: Rows(self.fake.answer(self.Текст, self.params)))


class Doc(Obj):
    def __init__(self, fake):
        super().__init__()
        self.fake = fake
        self.rows = []
        self.Таблица = Obj(Добавить=self._add)
        self.Номер = "  000123 "

    def _add(self):
        r = Obj()
        self.rows.append(r)
        return r

    def Записать(self, mode):  # noqa: N802
        self.fake.written.append(self)


class FakeC:
    def __init__(self):
        self.written = []
        self.stock = {"15": (10.0, 50000.0, 0.0), "16": (1.0, 900.0, 0.0)}
        self.goods = {
            "15": Ref("Olma", Код=15.0, ЕдИзм="kg", Обьём=0, Кг=1, ДоляП=0, ПриходЦена=4000, ПриходВал=0),
            "16": Ref("Nok", Код=16.0, ЕдИзм="dona", Обьём=6, Кг=0, ДоляП=0, ПриходЦена=800, ПриходВал=0),
        }
        dukon = Ref("Do'kon", Код=1.0)
        self.staff = {"1": dukon, "7": Ref("Zafar", Код=7.0, ДоляСотОп=1, ДоляСотПр=2)}
        savdo = Ref("Savdo", Код=2.0)
        self.parties = {"2": savdo, "55": Ref("Ali", Код=55.0, ЛимитСум=100000, ЛимитВал=0)}
        self.Справочники = Obj(Товары=Cat(self.goods), Сотрудники=Cat(self.staff, Дукон=dukon),
                               Контрагенты=Cat(self.parties, Савдо=savdo, Клиенты=Ref("Klientlar")))
        self.Константы = Obj(Курс=Obj(Получить=lambda: 12700.0), Склад1=Obj(Получить=lambda: Ref("Asosiy")),
                             ПродажаБезОстатков=Obj(Получить=lambda: False), ТипВесы=Obj(Получить=lambda: "RONGTA"))
        self.Перечисления = Obj(ВидВал=Obj(Сум="Сум", Вал="Вал"), ТипЦена=Obj(Цена1="Ц1", Цена2="Ц2", Цена3="Ц3", Цена4="Ц4"),
                                ВидДокумента=Obj(Продажа="Продажа", Возврать="Возврать", ОплатаДолга="ОплатаДолга"),
                                СписокВесов=Obj(RONGTA="RONGTA"))
        self.Документы = Obj(ПродажаРозница=Obj(СоздатьДокумент=lambda: Doc(self)))
        self.РежимЗаписиДокумента = Obj(Проведение="P")

    def String(self, v):  # noqa: N802
        return str(v)

    def NewObject(self, name):  # noqa: N802
        return Query(self)

    def answer(self, text, params):
        if "Остатки(, Товар = &Т" in text:
            q, s, u = self.stock.get(str(int(params["Т"].Код)), (0, 0, 0))
            return [{"Кол": q, "Сум": s, "Вал": u}]
        if "Сотрудник = &С" in text:
            return [{"С": 25000.0, "Вл": 0.0}]
        if text == kassa.GOODS_Q:
            return [{"Код": 15.0, "Имя": "Olma", "Вал": 0, "Ц1": 6000, "Ц2": 5500, "Ц3": 0, "Ц4": 0, "В1": 0, "В2": 0,
                     "В3": 0, "В4": 0, "Ед": "kg", "Гр": "Meva", "ШК": "4780001"},
                    {"Код": 16.0, "Имя": "Nok", "Вал": 1, "Ц1": 0, "Ц2": 0, "Ц3": 0, "Ц4": 0, "В1": 0.5, "В2": 0,
                     "В3": 0, "В4": 0, "Ед": "dona", "Гр": "Meva", "ШК": ""}]
        if text == kassa.STOCK_Q:
            return [{"Код": 15.0, "Кол": 10.0}, {"Код": 16.0, "Кол": 1.0}]
        if text == kassa.BARCODE_Q:
            return [{"ШК": "2900015", "Код": 15.0}, {"ШК": "999", "Код": 404.0}, {"ШК": "777", "Код": 15.0}]
        if text == kassa.BARCODE_TAB_Q:
            return [{"ШК": "4780016", "Код": 16.0}, {"ШК": "777", "Код": 16.0}]
        if text == kassa.CLIENTS_Q:
            return [{"Код": 55.0, "Имя": "Ali", "Тел": "+998901112233", "Пост": 0}]
        if text == kassa.DEBTS_Q:
            return [{"Код": 55.0, "С": 25000.0, "Вл": 0.0}]
        if text == kassa.STAFF_Q:
            return [{"Код": 7.0, "Имя": "Zafar", "Пароль": "1234", "Уволен": False, "Админ": True},
                    {"Код": 9.0, "Имя": "Kassir", "Пароль": "5555", "Уволен": False, "Админ": False},
                    {"Код": 8.0, "Имя": "Eski", "Пароль": "9999", "Уволен": True},
                    {"Код": 1.0, "Имя": "Do'kon", "Пароль": "", "Уволен": False}]
        if "Константы КАК К" in text:
            return [{}]
        if text == kassa.DAY_DOCS_Q:
            when = dt.datetime(2026, 11, 15, 8, 5)
            return [{"Номер": "000123", "Дата": when, "КК": 55.0, "КН": "Ali", "Сот": "Zafar", "Всего": 9000.0, "Ск": 0.0,
                     "Нал": 9000.0, "Пер": 0.0, "Клик": 0.0, "Долг": 25000.0, "Вид": 0, "Савдо": 0},
                    {"Номер": "000050", "Дата": when, "КК": 55.0, "КН": "Ali", "Сот": "Kassir", "Всего": 12000.0,
                     "Ск": 1000.0, "Нал": 5000.0, "Пер": 2000.0, "Клик": 0.0, "Долг": 25000.0, "Вид": 0, "Савдо": 0}]
        if text == kassa.DAY_LINES_Q:
            return [{"Номер": "000050", "Имя": "Olma", "Код": 15.0, "Кол": 2.0, "Цена": 6000.0, "Сум": 12000.0},
                    {"Номер": "000123", "Имя": "Olma", "Код": 15.0, "Кол": 1.5, "Цена": 6000.0, "Сум": 9000.0}]
        if text == kassa.SALES_STATS_Q:
            assert params["Н2"] - params["Н1"] == dt.timedelta(days=28)
            return [{"Код": 15.0, "Н": 12.0}, {"Код": 16.0, "Н": 3.0}]
        raise AssertionError("kutilmagan so'rov: " + text[:80])


class Conn:
    def __init__(self, c):
        self.c = c
        self.closed = 0

    def close(self):
        self.closed += 1


class SnapshotTest(unittest.TestCase):
    def test_build(self):
        c = FakeC()
        snap, staff = kassa.KassaOneC("Zafar savdo").snapshot(c)
        self.assertEqual([g["c"] for g in snap["g"]], ["16", "15"])     # nom bo'yicha: Nok, Olma
        olma = [g for g in snap["g"] if g["c"] == "15"][0]
        self.assertEqual((olma["p"][1], olma["s"], olma["e"]), (5500.0, 10.0, "kg"))
        # Справочник.Штрих + kartochkadagi shtrix; mavjud bo'lmagan tovar kodi tashlanadi
        # qo'shimcha jadval + Справочник.Штрих (bir xil kodda Штрих ustun) + kartochkadagi; yo'q tovar tashlanadi
        self.assertEqual(snap["b"], {"2900015": "15", "4780001": "15", "4780016": "16", "777": "15"})
        self.assertEqual(snap["cl"], [["55", "Ali", "+998901112233", 25000.0, 0.0, 0]])
        self.assertTrue(snap["rongta"])
        self.assertEqual(snap["title"], "Zafar savdo")
        self.assertEqual([s["code"] for s in staff], ["7", "9", "8"])     # parolsiz xodim kirolmaydi
        self.assertFalse(snap["lock"])
        c.Константы.ИзмененияВСенсореСпаролем = Obj(Получить=lambda: True)
        self.assertTrue(kassa.KassaOneC().snapshot(c)[0]["lock"])

    def test_check_stock(self):
        lines = [{"code": "15", "qty": kc.qty(4), "name": "Olma"}, {"code": "15", "qty": kc.qty(7), "name": "Olma"}]
        self.assertIn("Olma (bor: 10)", kassa.check_stock(lines, lambda c: 10, kc.KIND_SALE))
        self.assertEqual(kassa.check_stock(lines, lambda c: 0, kc.KIND_RETURN), "")


def sale_body(**kw):
    b = {"id": "sale0001", "kind": "sale", "ptype": "Цена1", "token": "",
         "lines": [{"code": "15", "qty": "1.5", "price": "6000"}], "cash": "10000"}
    b.update(kw)
    return b


class ServiceTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp()
        self.c = FakeC()
        self.conn = Conn(self.c)
        self.t = [1000.0]
        self.svc = kassa.KassaService(kassa.KassaConf(title="Do'kon"), self.dir, lambda: self.conn,
                                      clock=lambda: self.t[0],
                                      onec=kassa.KassaOneC("Do'kon", now=lambda: dt.datetime(2026, 11, 15, 9, 30)))
        threading.Thread(target=self.svc.run, daemon=True).start()

    def tearDown(self):
        shutil.rmtree(self.dir, ignore_errors=True)

    def login(self):
        return self.svc.login("1234")["token"]

    def test_login(self):
        self.assertEqual(self.svc.login(" 1234 ")["name"], "Zafar")
        with self.assertRaises(kc.KassaError):
            self.svc.login("9999")          # bo'shatilgan
        with self.assertRaises(kc.KassaError):
            self.svc.login("0000")
        with self.assertRaises(PermissionError):
            self.svc.who("yo'q")

    def test_sale_written_like_1c(self):
        tok = self.login()
        res = self.svc.sale(sale_body(token=tok))
        self.assertTrue(res["ok"])
        self.assertEqual((res["no"], res["total"], res["change"], res["cash"]), ("000123", 9000, 1000, 9000))
        d = self.c.written[0]
        self.assertEqual((d.ВидДокумента, d.ВидПродаж, d.ВсегоСум, d.Нал, d.Курс), ("Продажа", "Ц1", 9000.0, 9000.0, 12700.0))
        self.assertEqual(str(d.Контрагент), "Savdo")
        self.assertEqual((str(d.Сотрудник), d.ДоляСотОп), ("Zafar", 1))
        r = d.rows[0]
        self.assertEqual((r.Кол, r.ЦенаСум, r.СуммаСум, r.Ост, r.УртачаНархСум, r.Шт, r.Блок), (1.5, 6000.0, 9000.0, 10.0, 5000.0, 1, 1.5))
        # qoldiq xotirada darhol kamaydi
        olma = [g for g in self.svc.snap["g"] if g["c"] == "15"][0]
        self.assertEqual(olma["s"], 8.5)

    def test_same_request_not_written_twice(self):
        tok = self.login()
        a = self.svc.sale(sale_body(token=tok))
        b = self.svc.sale(sale_body(token=tok))
        for x in (a, b):
            x.pop("v0"), x.pop("v1")
        self.assertEqual(a, b)
        self.assertEqual(len(self.c.written), 1)
        # dastur qayta ishga tushsa ham (jurnal faylda)
        again = kassa.DoneLog(os.path.join(self.dir, kassa.DONE_FILE))
        self.assertEqual(again.get("sale0001")["no"], "000123")

    def test_debt_with_client(self):
        tok = self.login()
        res = self.svc.sale(sale_body(token=tok, id="sale0002", client="55", cash="4000"))
        self.assertEqual(res["debt"], 5000)
        self.assertEqual((res["cname"], res["cdebt"]), ("Ali", 30000.0))
        d = self.c.written[0]
        self.assertEqual((str(d.Контрагент), d.ДтКтСум, d.ЛимитСум), ("Ali", 25000.0, 100000))
        ali = [x for x in self.svc.snap["cl"] if x[0] == "55"][0]
        self.assertEqual(ali[3], 30000.0)

    def test_errors(self):
        tok = self.login()
        with self.assertRaises(kc.KassaError):     # mijozsiz qarz
            self.svc.sale(sale_body(token=tok, id="sale0003", cash="1000"))
        with self.assertRaises(kc.KassaError):     # qoldiq yetmaydi
            self.svc.sale(sale_body(token=tok, id="sale0004", lines=[{"code": "16", "qty": 2, "price": 6350}], cash="12700"))
        with self.assertRaises(kc.KassaError):     # eski narx
            self.svc.sale(sale_body(token=tok, id="sale0005", lines=[{"code": "15", "qty": 1, "price": 5000}], cash="5000"))
        with self.assertRaises(PermissionError):
            self.svc.sale(sale_body(id="sale0006"))
        self.assertEqual(self.c.written, [])

    def test_return_and_usd_price(self):
        tok = self.login()
        res = self.svc.sale(sale_body(token=tok, id="ret00001", kind="return",
                                      lines=[{"code": "16", "qty": 1, "price": 6350}], cash="6350"))
        d = self.c.written[0]
        self.assertEqual((res["doc"], d.ВидДокумента), ("Возврать", "Возврать"))
        self.assertEqual((d.rows[0].УртачаНархСум, d.rows[0].Шт), (900.0, 6))
        nok = [g for g in self.svc.snap["g"] if g["c"] == "16"][0]
        self.assertEqual(nok["s"], 2.0)

    def test_app_fallback_when_module_fails_over_com(self):
        app_c = FakeC()
        com_c = self.c

        class OneC(kassa.KassaOneC):
            def write_sale(s, c, *a):
                if c is com_c:
                    raise Exception("Ошибка инициализации модуля: Документ.ПродажаРозница.МодульОбъекта")
                return kassa.KassaOneC.write_sale(s, c, *a)

        class App:
            def __init__(s):
                s.c = app_c
                s.quits = 0

            def connect(s):
                return s

            def quit(s):
                s.quits += 1

        self.svc.onec = OneC("Do'kon", now=lambda: dt.datetime(2026, 11, 15, 9, 30))
        self.svc.open_app = App
        tok = self.login()
        res = self.svc.sale(sale_body(token=tok, id="app00001"))
        self.assertTrue(res["ok"])
        self.assertEqual((len(com_c.written), len(app_c.written)), (0, 1))
        self.assertTrue(self.svc.app_mode)
        self.svc.sale(sale_body(token=tok, id="app00002", lines=[{"code": "15", "qty": 1, "price": 6000}], cash="6000"))
        self.assertEqual(len(app_c.written), 2)          # keyingilari darhol oyna orqali
        self.t[0] += kassa.ACTIVE_SEC + 1
        self.svc.tick()
        self.assertEqual(self.svc.app.quits, 1)          # ishlatilmasa 1C oynasi yopiladi

    def test_bridge_when_module_fails_over_com(self):
        """COM'da ПродажаРозница yaratilmaydi -> 1C ko'prigi: sotuvlar to'planib bitta 1C ochilishida yoziladi."""
        com_c = self.c

        class OneC(kassa.KassaOneC):
            def write_sale(s, c, *a):
                raise Exception("Ошибка инициализации модуля: Документ.ПродажаРозница.МодульОбъекта")

        class FakeBridge:
            def __init__(s):
                s.runs = []
                s.answer = {}
                s.left = ({}, set())
                s.fail = None

            def leftovers(s):
                return s.left

            def forget(s, rid):
                s.left[0].pop(rid, None)
                s.left[1].discard(rid)

            def heartbeat(s):
                s.beats = getattr(s, "beats", 0) + 1

            def alive(s):
                return not s.fail

            def ensure_running(s):
                if s.fail:
                    raise RuntimeError(s.fail)

            def print_errors(s):
                e, s.perr = getattr(s, "perr", []), []
                return e

            def run(s, jobs, timeout=30):
                if s.fail:
                    raise RuntimeError(s.fail)
                s.runs.append(jobs)
                return {j["id"]: s.answer.get(j["id"], ("OK", "B%d" % len(s.runs), "15.11.2026 09:31")) for j in jobs}

        br = FakeBridge()
        self.svc.onec = OneC("Do'kon", now=lambda: dt.datetime(2026, 11, 15, 9, 30))
        self.svc.make_bridge = lambda: br
        tok = self.login()
        res = self.svc.sale(sale_body(token=tok, id="br000001"))
        self.assertEqual((res["no"], res["date"]), ("B1", "15.11.2026 09:31"))
        self.assertTrue(self.svc.bridge_mode)
        job = br.runs[0][0]
        self.assertEqual((job["kind"], job["ptype"], job["doc"], job["emp"], str(job["total"]), str(job["cash"])),
                         ("sale", "Цена1", "Продажа", "7", "9000.00", "9000.00"))
        self.assertEqual((job["lines"][0]["code"], str(job["lines"][0]["sum"])), ("15", "9000.00"))
        self.assertEqual(com_c.written, [])
        # keyingi sotuvlar: kassir kutmaydi (navbatda), fonda yoziladi
        r2 = self.svc.sale(sale_body(token=tok, id="br000002", lines=[{"code": "15", "qty": 1, "price": 6000}], cash="6000"))
        self.assertTrue(r2["queued"])
        self.svc.call(lambda: None)                     # fon ishi tugashini kutish
        self.assertEqual(self.svc.done.get("br000002")["no"], "B2")
        # 1C rad etsa — admin ko'radi, qoldiq qaytadi; o'chib qolsa (noma'lum) — qayta yozilmaydi, qoldiq qaytmaydi
        br.answer = {"br000003": ("ERR", "Товар етмаяпти"), "br000004": ("UNKNOWN", "1C to'xtadi")}
        before = [g for g in self.svc.snap["g"] if g["c"] == "15"][0]["s"]
        for rid in ("br000003", "br000004"):
            self.svc.sale(sale_body(token=tok, id=rid, lines=[{"code": "15", "qty": 1, "price": 6000}], cash="6000"))
        self.svc.call(lambda: None)
        q = {x["id"]: x for x in self.svc.queue_list()}
        self.assertEqual((q["br000003"]["state"], q["br000004"]["state"]), ("error", "error"))
        self.assertIn("rad etdi", q["br000003"]["err"])
        self.assertEqual([g for g in self.svc.snap["g"] if g["c"] == "15"][0]["s"], before - 2)
        n = len(br.runs)
        self.svc.tick()
        self.assertEqual(len(br.runs), n)               # xatolilar o'zi qayta yozilmaydi
        # 1C ochilmasa — navbatda qoladi, keyin yoziladi
        br.fail = "1C ochilmadi"
        self.svc.sale(sale_body(token=tok, id="br000005", lines=[{"code": "15", "qty": 1, "price": 6000}], cash="6000"))
        self.svc.call(lambda: None)
        self.assertEqual(self.svc.ping()["err"], "1C ochilmadi")
        self.assertEqual([x["state"] for x in self.svc.queue_list() if x["id"] == "br000005"], ["wait"])
        br.fail = None
        # dastur o'chib-yonganda qolgan natija (.res) — 1C qayta ochilmasdan olinadi
        br.left = ({"br000005": ("OK", "B9", "15.11.2026 10:00")}, set())
        self.svc.retry_at = 0
        self.svc.call(lambda: self.svc._drain())
        self.assertEqual(self.svc.done.get("br000005")["no"], "B9")
        self.assertEqual(len(br.runs), n)
        # dastur qayta ishga tushsa ham ko'prik rejimi esda (COM'ga qayta urinmaydi), 1C oldindan ochib qo'yiladi
        svc2 = kassa.KassaService(kassa.KassaConf(), self.dir, lambda: self.conn, bridge=lambda: br, clock=lambda: self.t[0])
        self.assertTrue(svc2.bridge_mode)
        svc2.tick()
        self.assertGreater(br.beats, 0)
        r = self.svc.sale(sale_body(token=tok, id="br000006", lines=[{"code": "15", "qty": 1, "price": 6000}], cash="6000"))
        self.assertTrue(r["queued"])
        self.assertGreater(r["v1"], r["v0"])                                     # monoblok qoldiqni o'zida o'zgartiradi
        # chekni 1C'ning o'zi chiqaradi: sotuvda belgi, qayta chiqarish — 1C raqami bo'yicha
        self.svc.call(lambda: None)
        self.svc.sale(sale_body(token=tok, id="br000007", lines=[{"code": "15", "qty": 1, "price": 6000}], cash="6000",
                                print1c=True))
        self.svc.call(lambda: None)
        jobs = [j for run in br.runs for j in run]
        self.assertTrue([j for j in jobs if j["id"] == "br000007"][0]["print"])
        self.assertFalse([j for j in jobs if j["id"] == "br000006"][0]["print"])
        self.assertEqual(self.svc.print_1c(tok, rid="br000005"), {"ok": True})
        pj = br.runs[-1][0]
        self.assertEqual((pj["kind"], pj["note"], pj["ptype"]), ("print", "B9", "15.11.2026"))
        br.perr = ["Принтер не найден"]
        self.svc.print_1c(tok, no="000050", date="15.11.2026 08:05")
        self.assertIn("Принтер не найден", self.svc.ping()["pe"])
        with self.assertRaises(kc.KassaError):
            self.svc.print_1c(tok, no="", date="")
        self.assertEqual(self.svc.print_text(tok, "Kassa yopish", [["Naqd", "9 000"]])["ok"], True)
        tj = br.runs[-1][0]
        self.assertEqual((tj["kind"], [l["code"] for l in tj["lines"]]), ("text", ["Kassa yopish", "Naqd|9 000"]))

    def test_panel_suggestion_approve_and_reset(self):
        tok = self.login()
        self.svc.data(0)
        self.assertEqual({g["c"]: g.get("f") for g in self.svc.snap["g"]}, {"15": 12, "16": 3})     # mashhurlik qidiruv uchun
        self.assertEqual(self.svc.snap["pnls"], "1c")
        grant = self.svc.admin(tok, "1234")["grant"]
        self.svc.tick()                                                          # haftalik tavsiya o'zi
        self.assertEqual(self.svc.ping()["ps"], 1)
        r = self.svc.panel_op(tok, grant, "get")
        self.assertEqual(r["sug"]["pnl"][0], ["15", "Olma"])                     # tarozi (kg) birinchi
        self.assertEqual(r["sug"]["pnl"][1], ["16", "Nok"])                      # pc 40: 1C shtrixli (qisqa) — keyingi bo'sh joyga
        self.assertEqual(r["sug"]["new"], [0, 1])
        self.assertEqual([m["t"] for m in r["sug"]["sec"]], ["TAROZI (KG)", "1C SHTRIXLI / SHTRIXSIZ"])
        with self.assertRaises(kc.KassaError):
            self.svc.panel_op(tok, "", "approve")                                # admin ruxsatisiz emas
        self.svc.panel_op(tok, grant, "approve")
        self.assertEqual((self.svc.snap["pnls"], self.svc.snap["pnl"][1]), ("kassa", ["16", "Nok"]))
        self.assertEqual(self.svc.ping()["ps"], 0)
        self.svc.call(self.svc._refresh_full)                                    # 1C'dan yangilansa ham saqlanadi
        self.assertEqual(self.svc.snap["pnls"], "kassa")
        self.assertEqual(self.svc.panel_op(tok, grant, "reset")["src"], "1c")
        self.assertEqual(self.svc.snap["pnls"], "1c")

    def test_panel_suggest_keeps_user_buttons(self):
        """pc 40: tavsiya foydalanuvchi qo'ygan tugmalarni o'zgartirmaydi — faqat bo'sh joylarga; qidiruvdan tanlanganlar ham."""
        tok = self.login()
        self.svc.data(0)
        grant = self.svc.admin(tok, "1234")["grant"]
        own = [["", ""]] * 5 + [["16", "Nok"]]
        kassa.save_json(os.path.join(self.svc.here, kassa.PANEL_FILE), {"pnl": own, "at": 1})
        r = self.svc.panel_op(tok, grant, "make")["sug"]
        self.assertEqual(r["pnl"][5], ["16", "Nok"])                                # joyida
        self.assertEqual(r["pnl"][0], ["15", "Olma"])
        self.assertEqual(sum(1 for x in r["pnl"] if x[0] == "16"), 1)              # takrorlanmaydi
        self.assertEqual(r["new"], [0])
        own2 = [["", ""]] * 5 + [["16", "Nok"]]
        own2[0] = ["16", "Nok"]                                                    # tavsiyadan keyin qo'lda qo'yildi
        kassa.save_json(os.path.join(self.svc.here, kassa.PANEL_FILE), {"pnl": own2, "at": 2})
        self.svc.panel_op(tok, grant, "approve")
        self.assertEqual(self.svc.snap["pnl"][0], ["16", "Nok"])                   # qo'lda qo'yilgan saqlandi
        self.assertNotIn(["15", "Olma"], self.svc.snap["pnl"])
        self.assertTrue(self.svc.picked(tok, "16")["ok"])
        self.assertFalse(self.svc.picked(tok, "yo'q")["ok"])
        self.assertEqual(self.svc.hits, {"16": 1})
        self.assertEqual(kassa.load_json(os.path.join(self.svc.here, kassa.HITS_FILE), {}), {"16": 1})

    def test_client_debt_live(self):
        """pc 40: mijoz tanlanganda qarzi 1C'dan jonli; ro'yxatdagi qarz ham yangilanadi."""
        tok = self.login()
        self.svc.data(0)
        seen = []
        self.svc.onec.debt_total = lambda c, code: seen.append(code) or (125000.0, 3.0)
        r = self.svc.client_debt(tok, "55")
        self.assertEqual((r["d"], r["u"], seen), (125000.0, 3.0, ["55"]))
        row = [x for x in self.svc.snap["cl"] if x[0] == "55"][0]
        self.assertEqual((row[3], row[4]), (125000.0, 3.0))
        with self.assertRaises(kc.KassaError):
            self.svc.client_debt(tok, "")
        with self.assertRaises(PermissionError):
            self.svc.client_debt("x", "55")

    def test_no_reserved_aliases(self):
        """pc 41: 1C so'rovida «В» (IN) kalit so'z — «КАК В» xato beradi (Ожидается имя). Taxallus sifatida ishlatilmasin."""
        import re
        for name in dir(kassa):
            q = getattr(kassa, name)
            if name.endswith("_Q") and isinstance(q, str):
                self.assertIsNone(re.search(r"КАК (В|ИЛИ|И|НЕ|КАК|ИЗ|ГДЕ|ПО)[\s,)]", q + " "), name)

    def test_debt_total_query(self):
        c = FakeC()
        o = kassa.KassaOneC()
        got = []
        o.query = lambda cc, q, f, **kw: got.append((q, kw)) or [{"С": 5000.0, "Вл": 0}]
        self.assertEqual(o.debt_total(c, "55"), (5000.0, 0.0))
        self.assertIn("Контрагент = &К)", got[0][0])
        self.assertNotIn("Сотрудник", got[0][0])
        with self.assertRaises(kc.KassaError):
            o.debt_total(c, "999")

    def test_sales_list_kassa_and_1c(self):
        tok = self.login()
        self.svc.sale(sale_body(token=tok))                                        # kassada: 1C raqami 000123
        r = self.svc.sales(tok, "2026-11-15")
        self.assertEqual(r["e1c"], "")
        by = {x["no"]: x for x in r["items"]}
        self.assertEqual(sorted(by), ["000050", "000123"])                          # 000123 ikki marta emas
        self.assertEqual(by["000123"]["src"], "kassa")
        old = by["000050"]                                                         # 1C'da qilingan savdo
        self.assertEqual((old["src"], old["due"], old["debt"], old["cdebt"], old["cashier"]), ("1c", 11000.0, 4000.0, 29000.0, "Kassir"))
        self.assertEqual(old["lines"], [{"n": "Olma", "c": "15", "q": 2.0, "p": 6000.0, "s": 12000.0}])
        self.assertNotIn("ed", by["000123"])                                       # 1C'da o'zgarmagan
        self.assertEqual(by["000123"]["id"], "sale0001")
        with self.assertRaises(PermissionError):
            self.svc.sales("yo'q")
        with self.assertRaises(kc.KassaError):
            self.svc.sales(tok, "2026-13-45")

    def test_merge_1c_edited(self):
        """pc 39: kompyuterda (1C'da) o'zgartirilgan kassa savdosi — ro'yxatda 1C'dagi holati, kassa raqami saqlanadi."""
        items = [{"id": "s1", "src": "kassa", "no": "7", "total": 9000.0, "lines": [{"n": "Olma"}], "cashier": "Zafar"},
                 {"id": "s2", "src": "kassa", "no": "navbatda", "queued": True, "total": 1.0, "lines": []}]
        docs = [{"id": "1c:7", "src": "1c", "no": "7", "total": 15000.0, "lines": [{"n": "Olma"}, {"n": "Nok"}], "cashier": "Kassir",
                 "cname": "Ali"},
                {"id": "1c:8", "src": "1c", "no": "8", "total": 5.0, "lines": []}]
        out = kassa.merge_1c(items, docs)
        self.assertEqual([x["id"] for x in out], ["s1", "s2", "1c:8"])
        self.assertEqual((out[0]["total"], out[0]["src"], out[0]["ed"], out[0]["cname"], out[0]["cashier"]),
                         (15000.0, "kassa", 1, "Ali", "Zafar"))
        self.assertEqual(len(out[0]["lines"]), 2)

    def test_edit_sale_1c(self):
        """pc 39: dasturchi rejimida savdoni tahrirlash — 1C hujjati qatorlari va to'lovi almashadi, qayta o'tkaziladi."""
        c = FakeC()
        old = Doc(c)
        old.rows.append(Obj(Товары="eski"))
        old.Таблица.Очистить = lambda: old.rows.clear()
        old.Контрагент = c.parties["55"]
        old.Склад = Ref("Asosiy")
        old.ВидВал, old.ВидПродаж = "Сум", "Ц1"
        found = []

        def by_no(no, when):
            found.append((no, when))
            return Ref("doc", Пустая=lambda: False, ПолучитьОбъект=lambda: old)
        c.Документы.ПродажаРозница.НайтиПоНомеру = by_no
        sale = kc.clean_sale(sale_body(client="55", lines=[{"code": "16", "qty": "12", "price": "1000"}], cash="5000"))
        pay = kc.settle(kc.cart_total(sale["lines"]), sale["discount"], sale["cash"], sale["card"], sale["click"], True, "sale")
        when = kassa.dt.datetime(2026, 11, 15, 8, 5)
        no, d = kassa.KassaOneC().edit_sale(c, "000123", when, sale, pay)
        self.assertEqual((no, d), ("000123", "15.11.2026 08:05"))
        self.assertEqual(found, [("000123", when)])
        self.assertEqual(c.written, [old])
        self.assertEqual(len(old.rows), 1)
        r = old.rows[0]
        self.assertEqual((r.Товары, r.Кол, r.Шт, r.Блок, r.ЦенаСум, r.СуммаСум), (c.goods["16"], 12.0, 6, 2.0, 1000.0, 12000.0))
        self.assertEqual((old.ВсегоСум, old.Нал, old.СкидкаСум), (12000.0, 5000.0, 0.0))
        c.parties["55"].Наименование = "Ali"
        sale2 = dict(sale, client="")                                                    # mijozni almashtirib bo'lmaydi
        with self.assertRaises(kc.KassaError):
            kassa.KassaOneC().edit_sale(c, "000123", when, sale2, pay)

    def test_edit_sale_service(self):
        tok = self.login()
        with self.assertRaises(kc.KassaError):
            self.svc.edit_sale({"token": tok, "no": "", "date": "15.11.2026", "lines": [{"code": "15", "qty": "1", "price": "6000"}]})
        with self.assertRaises(kc.KassaError):
            self.svc.edit_sale({"token": tok, "no": "000123", "date": "15.11.2026 08:05", "lines": []})
        with self.assertRaises(PermissionError):
            self.svc.edit_sale({"token": "x", "no": "000123", "date": "15.11.2026", "lines": [{"code": "15", "qty": "1"}]})
        calls = []
        self.svc.onec.edit_sale = lambda c, no, when, sale, pay: calls.append((no, when, sale["lines"][0]["name"])) or (no, "15.11.2026 08:05")
        self.svc.data(0)
        r = self.svc.edit_sale({"token": tok, "no": "000123", "date": "15.11.2026 08:05", "kind": "sale",
                                "lines": [{"code": "15", "qty": "2", "price": "6000", "m": 1}], "cash": "12000"})
        self.assertEqual((r["no"], r["total"], r["edited"], r["lines"][0]["c"]), ("000123", 12000.0, 1, "15"))
        self.assertEqual(calls, [("000123", kassa.dt.datetime(2026, 11, 15, 8, 5), "Olma")])
        self.assertEqual(self.svc.full_at, 0.0)

    def test_auto_login(self):
        with self.assertRaises(kc.KassaError):
            self.svc.auto_login()                                                  # yoqilmagan
        tok = self.login()
        grant = self.svc.admin(tok, "1234")["grant"]
        self.svc.put_ui(tok, grant, {"auto": "7"})
        r = self.svc.auto_login()
        self.assertEqual((r["name"], r["code"]), ("Zafar", "7"))
        self.assertEqual(self.svc.who(r["token"])[1], "Zafar")
        self.svc.put_ui(tok, grant, {"auto": "8"})                                 # bo'shatilgan
        with self.assertRaises(kc.KassaError):
            self.svc.auto_login()

    def test_add_client(self):
        tok = self.login()
        self.svc.data(0)
        r = self.svc.add_client(tok, "  Vali   aka ", "+998 90 1 x 23")
        self.assertEqual(r["client"], ["56", "Vali aka", "+998 90 1  23", 0.0, 0.0, 0])
        self.assertEqual(str(self.c.parties["56"].Родитель), "Klientlar")
        self.assertIn(["56", "Vali aka", "+998 90 1  23", 0.0, 0.0, 0], self.svc.snap["cl"])
        with self.assertRaises(kc.KassaError):
            self.svc.add_client(tok, "vali AKA")                               # bor nom
        with self.assertRaises(kc.KassaError):
            self.svc.add_client(tok, "Ali")                                    # 1C'da bor
        with self.assertRaises(kc.KassaError):
            self.svc.add_client(tok, " ")

    def test_link_barcode(self):
        """pc 36: dasturchi rejimi — noma'lum shtrix kod tovarga biriktiriladi (kassada darhol, 1C'ga navbat orqali)."""
        tok = self.login()
        self.svc.data(0)
        snap = self.svc.snap
        code = snap["g"][0]["c"]
        name = snap["g"][0]["n"]
        with self.assertRaises(kc.KassaError):
            self.svc.link_barcode(tok, "4780000000017", code)                  # bulut yo'q
        calls, woke = [], []

        class Cfg:
            inbox = "INBOX1"

        class Cloud:
            cfg = Cfg()

            def call(s, method, path, body=None, timeout=0):
                calls.append((method, path, body))

        self.svc.cloud = Cloud()
        self.svc.wake = lambda: woke.append(1)
        r = self.svc.link_barcode(tok, " 4780000000017 ", code)
        self.assertEqual(r["b"], ["4780000000017", code])
        self.assertEqual(self.svc.snap["b"]["4780000000017"], code)
        self.assertEqual(len(calls), 1)
        m, path, body = calls[0]
        self.assertEqual((m, path.split("/")[:2]), ("PUT", ["w", "INBOX1"]))
        self.assertEqual(body["k"], "bc")
        self.assertEqual(json.loads(body["p"]), {"c": name, "cc": code, "bc": "4780000000017"})
        self.assertEqual(woke, [1])
        self.svc.link_barcode(tok, "4780000000017", code)                      # o'sha tovarga qayta — ruxsat
        other = snap["g"][1]["c"]
        with self.assertRaises(kc.KassaError):
            self.svc.link_barcode(tok, "4780000000017", other)                 # boshqa tovarda bor
        for bad in ("12", "ab cd", "x" * 41):
            with self.assertRaises(kc.KassaError):
                self.svc.link_barcode(tok, bad, code)
        with self.assertRaises(kc.KassaError):
            self.svc.link_barcode(tok, "4780000000024", "yo'q")
        with self.assertRaises(PermissionError):
            self.svc.link_barcode("x", "4780000000024", code)

    def test_live_state_sync(self):
        """pc 37: monoblok va kompyuter oynasi — biri savatni o'zgartirsa, ikkinchisining kutayotgan so'rovi darhol qaytadi."""
        tok = self.login()
        st0 = self.svc.get_state(tok, "1")
        r0, ur0 = st0["r"], st0["ur"]
        t0 = time.time()
        self.assertEqual(self.svc.watch_state(tok, "1", r0, ur0, timeout=0.2)["r"], r0)       # o'zgarmadi — vaqt tugadi
        self.assertGreaterEqual(time.time() - t0, 0.15)
        got = []
        th = threading.Thread(target=lambda: got.append(self.svc.watch_state(tok, "1", r0, ur0, timeout=10)))
        th.start()
        time.sleep(0.1)
        t1 = time.time()
        r = self.svc.put_state(tok, "1", {"at": 5, "wins": [{"lines": [1]}], "w": 0, "cid": "A"})
        th.join(5)
        self.assertLess(time.time() - t1, 2)                                                  # darhol
        self.assertEqual(got[0]["r"], r["r"])
        self.assertNotEqual(r["r"], r0)
        self.assertEqual((got[0]["cid"], got[0]["wins"]), ("A", [{"lines": [1]}]))
        self.assertEqual(self.svc.watch_state(tok, "2", self.svc.get_state(tok, "2")["r"], ur0, timeout=0.1)["r"],
                         self.svc.rev_base)                                                   # boshqa monoblok (k=2) — alohida
        grant = self.svc.admin(tok, "1234")["grant"]
        self.svc.put_ui(tok, grant, {"theme": "qora"})
        self.assertNotEqual(self.svc.watch_state(tok, "1", r["r"], ur0, timeout=5)["ur"], ur0)  # sozlama ham
        with self.assertRaises(PermissionError):
            self.svc.watch_state("x", "1", 0, 0, timeout=0.1)

    def test_plu_from_codes(self):
        """pc 37: tarozi PLU — onec_export codes.json'dan; tovar ma'lumotida "pl"."""
        self.svc.data(0)
        code = self.svc.snap["g"][0]["c"]
        kassa.save_json(os.path.join(self.svc.here, kassa.CODES_FILE),
                        {"v": 1, "items": [{"c": code, "n": "x", "p": "501", "b": []}, {"c": "yo'q", "p": "7"}]})
        self.svc._repanel()
        g = {x["c"]: x for x in self.svc.snap["g"]}
        self.assertEqual(g[code]["pl"], "501")
        self.assertEqual(sum(1 for x in self.svc.snap["g"] if "pl" in x), 1)

    def test_debts_fallback(self):
        """pc 39: qarz so'rovi ishlamasa yoki hammasi 0 bo'lsa — telefon hisobotidagi so'rov (nom bo'yicha, sana bilan)."""
        calls = []

        class One(kassa.KassaOneC):
            def query(s, c, q, fields, **kw):
                calls.append((q, kw))
                if len(calls) == 1:
                    raise RuntimeError("Поле не найдено Код")
                if len(calls) == 2:
                    return [{"Код": "1", "С": 0, "Вл": 0}]
                return [{"Имя": "Ali", "С": 150000.0, "Вл": 0}]
        o = One(now=lambda: kassa.dt.datetime(2026, 11, 21))
        rows = o.debts(None)
        self.assertEqual(rows, [{"Имя": "Ali", "С": 150000.0, "Вл": 0}])
        self.assertEqual(calls[2][1], {"К": kassa.dt.datetime(2026, 11, 21)})
        self.assertIn("Остатки(&К, )", calls[2][0])
        self.assertEqual((o.debt_diag["nz"], o.debt_diag["s"][0][1]), (1, "Ali"))
        self.assertEqual(len(o.debt_diag["e"]), 2)
        snap = kassa.build_snapshot([], [], [], [], [{"Код": "7", "Имя": "ALI ", "Тел": "", "Пост": 0}], rows, {}, "")
        self.assertEqual(snap["cl"][0][3], 150000.0)                   # nom bo'yicha topildi

    def test_admin_password(self):
        tok = self.svc.login("5555")["token"]
        self.assertEqual(self.svc.admin(tok, "1234")["name"], "Zafar")
        self.assertTrue(self.svc.admin(tok, "1234")["grant"])
        for bad in ("5555", "9999", "0000"):
            with self.assertRaises(kc.KassaError):
                self.svc.admin(tok, bad)
        # pc 32: parolsiz kirish standart yoqiq — kirgan kassir nomidan; o'chirilsa parol so'raladi
        self.assertEqual(self.svc.admin(tok, "")["name"], "Kassir")
        kassa.save_json(os.path.join(self.svc.here, kassa.UI_FILE), {"setFree": False})
        with self.assertRaises(kc.KassaError):
            self.svc.admin(tok, "")
        with self.assertRaises(PermissionError):
            self.svc.admin("x", "1234")

    def test_1c_down_sale_is_queued_then_written(self):
        """1C ishlamasa: kassir kutmaydi, sotuv navbatga (diskka), 1C tiklanganda o'zi bir marta yoziladi."""
        com_c = self.c
        state = {"broken": True}

        class OneC(kassa.KassaOneC):
            def write_sale(s, c, *a):
                if state["broken"]:
                    raise Exception("Ошибка инициализации модуля: Документ.ПродажаРозница.МодульОбъекта")
                return kassa.KassaOneC.write_sale(s, c, *a)

        class App:
            def connect(s):
                if state["broken"]:
                    raise SystemExit("1C ga oyna orqali ulanib bo'lmadi: Сбой при удаленном вызове процедуры")
                s.c = com_c
                return s

            def quit(s):
                pass

        self.svc.onec = OneC("Do'kon", now=lambda: dt.datetime(2026, 11, 15, 9, 30))
        self.svc.open_app = App
        tok = self.login()
        res = self.svc.sale(sale_body(token=tok, id="down0001"))
        self.assertTrue(res["ok"] and res["queued"])
        self.assertIn("Сбой при удаленном вызове", res["why"])
        self.assertIn("COM:", res["why"])
        self.assertEqual(self.svc.ping()["q"], 1)
        # qoldiq darhol kamaygan (ikkinchi kassir ortiqcha sota olmaydi)
        self.assertEqual([g for g in self.svc.snap["g"] if g["c"] == "15"][0]["s"], 8.5)
        # xuddi shu so'rov qayta kelsa — ikkinchi navbat yozuvi yo'q
        self.assertTrue(self.svc.sale(sale_body(token=tok, id="down0001"))["queued"])
        self.assertEqual(len(self.svc.pending), 1)
        # 1C tiklandi -> fon aylanishida yoziladi
        state["broken"] = False
        self.svc.retry_at = 0
        self.svc.call(lambda: self.svc._drain())
        self.assertEqual(len(com_c.written), 1)
        self.assertEqual(self.svc.ping()["q"], 0)
        self.assertEqual(self.svc.done.get("down0001")["no"], "000123")
        # endi shu so'rov — tayyor natija (ikkinchi hujjat yo'q)
        self.assertEqual(self.svc.sale(sale_body(token=tok, id="down0001"))["no"], "000123")
        self.assertEqual(len(com_c.written), 1)

    def test_power_loss_restart_keeps_queue_and_login(self):
        """Kompyuter dasturi qayta ishga tushdi (1C hali ishlamaydi): navbat, tovarlar va parollar diskdan."""
        tok = self.login()

        def boom():
            raise SystemExit("1C yo'q")
        self.svc.open_com = boom
        self.svc._drop()
        self.svc.retry_at = 0
        res = self.svc.sale(sale_body(token=tok, id="pwr00001"))
        self.assertTrue(res["queued"])
        svc2 = kassa.KassaService(kassa.KassaConf(), self.dir, boom, clock=lambda: self.t[0])
        threading.Thread(target=svc2.run, daemon=True).start()
        self.assertEqual([p["id"] for p in svc2.pending], ["pwr00001"])
        self.assertEqual(len(json.loads(svc2.data(0)[0])["g"]), 2)
        self.assertEqual(svc2.login("1234")["name"], "Zafar")
        self.assertNotIn("1234", open(os.path.join(self.dir, kassa.STAFF_FILE), encoding="utf-8").read())

    def test_business_error_from_1c_not_queued(self):
        class OneC(kassa.KassaOneC):
            def write_sale(s, c, *a):
                raise kc.KassaError("Qoldiq yetmaydi: Olma (bor: 1)")
        self.svc.onec = OneC()
        tok = self.login()
        with self.assertRaises(kc.KassaError):
            self.svc.sale(sale_body(token=tok, id="biz00001"))
        self.assertEqual(self.svc.pending, [])
        self.assertEqual([g for g in self.svc.snap["g"] if g["c"] == "15"][0]["s"], 10.0)   # qoldiq qaytdi

    def test_queue_admin_ops_and_report(self):
        tok = self.login()
        self.svc.sale(sale_body(token=tok, id="rep00001"))
        self.svc.sale(sale_body(token=tok, id="rep00002", kind="return", lines=[{"code": "16", "qty": 1, "price": 6350}], cash="6350"))
        r = self.svc.report(day="15.11.2026")
        self.assertEqual((r["n"], r["sale"], r["ret"], r["cash"]), (2, 9000.0, 6350.0, 2650.0))
        self.assertEqual(r["by"]["Zafar"]["n"], 2)
        # kassa yopish: 1C'dagi hamma savdolar (kassaniki ikki marta sanalmaydi) + 1C'da qilingan 000050
        full = self.svc.report(tok, "2026-11-15")
        self.assertEqual((full["src"], full["n"], full["sale"]), ("1c", 3, 9000.0 + 12000.0 + 9000.0 * 0))
        grant = self.svc.admin(tok, "1234")["grant"]
        self.svc.pending.append({"id": "x1", "sale": kassa.sale_to_json(kc.clean_sale(sale_body(id="x1xxxxxx"))),
                                 "res": {"_debt_delta": 0.0, "date": "", "total": 1}, "state": "error", "err": "e"})
        self.assertEqual(self.svc.queue_list()[0]["state"], "error")
        self.svc.queue_op("delete", "x1")
        self.assertEqual(self.svc.pending, [])
        with self.assertRaises(kc.KassaError):
            self.svc.granted(tok, "eski")
        self.svc.granted(tok, grant)

    def test_state_and_ui_saved_on_pc(self):
        tok = self.login()
        st = {"at": 5, "wins": [{"lines": [{"code": "15", "milli": 1000}]}]}
        self.svc.put_state(tok, "1", st)
        strip = lambda d: {k: v for k, v in d.items() if k not in ("r", "ur")}       # pc 37: jonli sinxron raqamlari
        self.assertEqual(strip(self.svc.get_state(tok, "1")), st)
        self.assertEqual(strip(self.svc.get_state(tok, "../../x")), {})
        with self.assertRaises(kc.KassaError):
            self.svc.put_state(tok, "1", {"x": "y" * (kassa.MAX_STATE + 1)})
        grant = self.svc.admin(tok, "1234")["grant"]
        self.svc.put_ui(tok, grant, {"wins": 4})
        self.assertEqual(self.svc.get_ui(), {"wins": 4})
        with self.assertRaises(kc.KassaError):
            self.svc.put_ui(tok, "x", {})
        kassir = self.svc.login("5555")["token"]
        with self.assertRaises(kc.KassaError):
            self.svc.admin(kassir, "5555")          # admin emas

    def test_idle_releases_1c(self):
        self.login()
        self.svc.call(lambda: None)
        self.t[0] += kassa.ACTIVE_SEC + 1
        self.svc.tick()
        self.assertGreaterEqual(self.conn.closed, 1)
        self.assertIsNone(self.svc.com)

    def test_data_versions(self):
        tok = self.login()
        self.svc.who(tok)
        raw, gz = self.svc.data(0)
        ver = json.loads(raw)["ver"]
        self.assertIsNone(self.svc.data(ver))


class HttpTest(unittest.TestCase):
    def test_routes(self):
        d = tempfile.mkdtemp()
        try:
            os.makedirs(os.path.join(d, "kassa"))
            with open(os.path.join(d, "kassa", "index.html"), "w", encoding="utf-8") as f:
                f.write("<html>kassa</html>")
            c = FakeC()
            svc = kassa.KassaService(kassa.KassaConf(), d, lambda: Conn(c))
            threading.Thread(target=svc.run, daemon=True).start()
            httpd = ThreadingHTTPServer(("127.0.0.1", 0), kassa.make_handler(svc, os.path.join(d, "kassa")))
            threading.Thread(target=httpd.serve_forever, daemon=True).start()
            base = "http://127.0.0.1:%d" % httpd.server_address[1]

            def post(path, body):
                req = urllib.request.Request(base + path, json.dumps(body).encode(), {"Content-Type": "application/json"})
                with urllib.request.urlopen(req, timeout=10) as r:
                    return json.loads(r.read())

            with urllib.request.urlopen(base + "/", timeout=10) as r:
                self.assertIn(b"kassa", r.read())
            with urllib.request.urlopen(base + "/monoblok.bat", timeout=10) as r:
                self.assertIn(("URL=" + base + "/").encode(), r.read())
            for bad in ("/../kassa.py", "/kassa.py", "/x.exe"):
                with self.assertRaises(urllib.error.HTTPError):
                    urllib.request.urlopen(base + bad, timeout=10)
            with self.assertRaises(urllib.error.HTTPError) as e:
                urllib.request.urlopen(base + "/api/data?t=x", timeout=10)
            self.assertEqual(e.exception.code, 401)
            self.assertFalse(post("/api/login", {"pw": "0"})["ok"])
            tok = post("/api/login", {"pw": "1234"})["token"]
            with urllib.request.urlopen(base + "/api/data?t=%s&v=0" % tok, timeout=10) as r:
                data = json.loads(r.read())
            self.assertEqual(len(data["g"]), 2)
            res = post("/api/sale", sale_body(token=tok, id="http0001"))
            self.assertTrue(res["ok"])
            self.assertFalse(post("/api/sale", sale_body(token=tok, id="http0002", cash="1"))["ok"])
            httpd.shutdown()
        finally:
            shutil.rmtree(d, ignore_errors=True)


class DoneLogTest(unittest.TestCase):
    def test_keeps_last_and_compacts(self):
        d = tempfile.mkdtemp()
        try:
            path = os.path.join(d, "j.jsonl")
            log = kassa.DoneLog(path, keep=3)
            for i in range(7):
                log.add({"id": "r%d" % i, "no": str(i)})
            self.assertIsNone(log.get("r0"))
            self.assertEqual(log.get("r6")["no"], "6")
            again = kassa.DoneLog(path, keep=3)
            self.assertEqual(sorted(again.items), ["r4", "r5", "r6"])
            with open(path, encoding="utf-8") as f:
                self.assertLessEqual(len(f.readlines()), 6)
        finally:
            shutil.rmtree(d, ignore_errors=True)


class BrokenOneCTest(unittest.TestCase):
    def test_connect_failure_does_not_kill_thread(self):
        d = tempfile.mkdtemp()
        try:
            def boom():
                raise SystemExit("1C ga ulanib bo'lmadi")
            svc = kassa.KassaService(kassa.KassaConf(), d, boom)
            threading.Thread(target=svc.run, daemon=True).start()
            svc.touch()
            svc.tick = lambda: boom()
            with self.assertRaises(kc.KassaError):
                svc.login("1234")
            self.assertEqual(svc.call(lambda: 5), 5)        # oqim tirik
        finally:
            shutil.rmtree(d, ignore_errors=True)


class MonoblokTest(unittest.TestCase):
    def test_bat(self):
        raw = kassa.monoblok_bat("http://192.168.1.10:8077/")
        raw.decode("ascii")
        self.assertIn(b"\r\n", raw)
        self.assertNotIn(b"\n", raw.replace(b"\r\n", b""))
        self.assertIn(b'set "URL=http://192.168.1.10:8077/"', raw)
        self.assertIn(b"--kiosk --kiosk-printing", raw)
        self.assertIn(b"Startup", raw)
        for bad in ("ftp://x", "http://a&b", "http://a b", 'http://a"'):
            with self.assertRaises(ValueError):
                kassa.monoblok_bat(bad)

    def test_firewall_args(self):
        d, a = kassa.firewall_args(8077)
        self.assertIn("name=IMSOFT kassa 8077", d)
        self.assertIn("localport=8077", a)


class ConfTest(unittest.TestCase):
    def test_load(self):
        d = tempfile.mkdtemp()
        try:
            self.assertEqual(kassa.KassaConf.load(d).port, kassa.PORT)
            with open(os.path.join(d, "onec.ini"), "w", encoding="utf-8") as f:
                f.write("[kassa]\nenabled = 0\nport = 9000\ntitle = Zafar\nrefresh = 999\n")
            k = kassa.KassaConf.load(d)
            self.assertEqual((k.enabled, k.port, k.title, k.refresh_min), (False, 9000, "Zafar", 120))
            # Bloknot ANSI (1251) da saqlagan ini — kassa baribir ishga tushadi (5.4 dagi xato)
            with open(os.path.join(d, "onec.ini"), "wb") as f:
                f.write("[onec]\nbase = D:\\1C — Do'kon\n[kassa]\ntitle = Дукон\n".encode("cp1251"))
            with open(os.path.join(d, "forwarder.ini"), "wb") as f:
                f.write(b"\xef\xbb\xbf[x]\na = 1\n")
            k = kassa.KassaConf.load(d)
            self.assertEqual((k.enabled, k.title), (True, "Дукон"))
        finally:
            shutil.rmtree(d, ignore_errors=True)


class DebtMatchTest(unittest.TestCase):
    def test_debt_by_code_then_by_name(self):
        clients = [{"Код": "000055", "Имя": "Ali", "Тел": "", "Пост": 0}, {"Код": 77.0, "Имя": "Vali aka", "Тел": "", "Пост": 0},
                   {"Код": 88.0, "Имя": "Yo'q", "Тел": "", "Пост": 0}]
        debts = [{"Код": "000055", "Имя": "Ali", "С": 1000.0, "Вл": 0.0},
                 {"Код": " 77 ", "Имя": "VALI AKA", "С": 2500.5, "Вл": 1.0},     # kod boshqacha ko'rinishda — nom bo'yicha
                 {"Код": "000055", "Имя": "Ali", "С": 500.0, "Вл": 0.0}]          # bir nechta qator — qo'shiladi
        snap = kassa.build_snapshot([], [], [], [], clients, debts, {})
        d = {c[1]: c[3] for c in snap["cl"]}
        self.assertEqual(d["Ali"], 1500.0)
        self.assertEqual(d["Vali aka"], 2500.5)
        self.assertEqual(d["Yo'q"], 0.0)
        self.assertEqual((snap["dn"], snap["dm"]), (3, 2))


if __name__ == "__main__":
    unittest.main()
