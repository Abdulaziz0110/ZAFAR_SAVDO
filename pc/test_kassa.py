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
            return [{"Код": 7.0, "Имя": "Zafar", "Пароль": "1234", "Уволен": False},
                    {"Код": 8.0, "Имя": "Eski", "Пароль": "9999", "Уволен": True},
                    {"Код": 1.0, "Имя": "Do'kon", "Пароль": "", "Уволен": False}]
        if "Константы КАК К" in text:
            return [{}]
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
        self.assertEqual([s["code"] for s in staff], ["7", "8"])     # parolsiz xodim kirolmaydi

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
