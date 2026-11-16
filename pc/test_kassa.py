# -*- coding: utf-8 -*-
import datetime as dt
import json
import os
import shutil
import sys
import tempfile
import threading
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
                               Контрагенты=Cat(self.parties, Савдо=savdo))
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
            return [{"С": 25000.0, "В": 0.0}]
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
            return [{"Код": 55.0, "С": 25000.0, "В": 0.0}]
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
            return [{"Номер": "000050", "Имя": "Olma", "Кол": 2.0, "Цена": 6000.0, "Сум": 12000.0}]
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
        self.assertEqual(r["sug"]["pnl"][0], ["15", "Olma"])                     # tarozi (kg) birinchi sahifada
        self.assertEqual(r["sug"]["pnl"][42], ["16", "Nok"])                     # shtrix kodli — keyingi sahifada
        self.assertEqual([m["t"] for m in r["sug"]["sec"]], ["TAROZI (KG)", "SHTRIX KODLI"])
        with self.assertRaises(kc.KassaError):
            self.svc.panel_op(tok, "", "approve")                                # admin ruxsatisiz emas
        self.svc.panel_op(tok, grant, "approve")
        self.assertEqual((self.svc.snap["pnls"], self.svc.snap["pnl"][42]), ("kassa", ["16", "Nok"]))
        self.assertEqual(self.svc.ping()["ps"], 0)
        self.svc.call(self.svc._refresh_full)                                    # 1C'dan yangilansa ham saqlanadi
        self.assertEqual(self.svc.snap["pnls"], "kassa")
        self.assertEqual(self.svc.panel_op(tok, grant, "reset")["src"], "1c")
        self.assertEqual(self.svc.snap["pnls"], "1c")

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
        self.assertEqual(old["lines"], [{"n": "Olma", "q": 2.0, "p": 6000.0, "s": 12000.0}])
        with self.assertRaises(PermissionError):
            self.svc.sales("yo'q")
        with self.assertRaises(kc.KassaError):
            self.svc.sales(tok, "2026-13-45")

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

    def test_admin_password(self):
        tok = self.svc.login("5555")["token"]
        self.assertEqual(self.svc.admin(tok, "1234")["name"], "Zafar")
        self.assertTrue(self.svc.admin(tok, "1234")["grant"])
        for bad in ("5555", "9999", "0000", ""):
            with self.assertRaises(kc.KassaError):
                self.svc.admin(tok, bad)
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
        self.assertEqual(self.svc.get_state(tok, "1"), st)
        self.assertEqual(self.svc.get_state(tok, "../../x"), {})
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


if __name__ == "__main__":
    unittest.main()
