# -*- coding: utf-8 -*-
"""
v5.4: KASSA (sensor) OYNASI — kompyuterdagi kichik veb-server.

Monoblok (yoki istalgan kompyuter/planshet) do'kon tarmog'ida brauzerda  http://<kompyuter-IP>:8077  ni ochadi.
Oyna monoblokning o'zida ishlaydi (RDP kabi sekin emas); sotuv bosilganda kompyuter uni 1C'ga
"ПродажаРозница" hujjati qilib yozadi — xuddi 1C'dagi "РеализацияСенсор1" (РасчетНажатие / ВозвратНажатие) kabi:
narx turi, qoldiq, o'rtacha tannarx, mijoz qarzi, naqd / plastik / click, chegirma, xodim.

Ish tartibi:
  * 1C bilan faqat bitta "kassa oqimi" ishlaydi (COM shu oqimda) — brauzer so'rovlari navbat orqali;
  * tovarlar, narxlar, qoldiq, shtrix kodlar, panel va mijozlar xotirada (brauzer o'zida qidiradi — tez);
  * kassir 1C'dagi kabi PAROL bilan kiradi (Справочник.Сотрудники.Пароль), parollar brauzerga yuborilmaydi;
  * bir xil so'rov ikki marta kelsa (internet uzilib qayta bosilsa) — ikkinchi hujjat yozilmaydi.

Sozlama (onec.ini, ixtiyoriy):
  [kassa]
  enabled = 1
  port = 8077
  title = Do'kon nomi (chek tepasida)
  refresh = 5        ; tovar/narx/mijozlarni necha daqiqada yangilash (qoldiq — har daqiqada)
"""
import configparser
import datetime as dt
import gzip
import json
import logging
import os
import queue
import secrets
import socket
import threading
import time
from decimal import Decimal
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import kassa_core as kc

log = logging.getLogger("kassa")

PORT = 8077
DONE_FILE = "kassa_done.jsonl"
STATIC_DIR = "kassa"
ACTIVE_SEC = 15 * 60          # brauzer shuncha vaqt so'ramasa — 1C'ni bezovta qilmaymiz
STOCK_SEC = 60
TOKEN_TTL = 16 * 3600
CALL_TIMEOUT = 120


# ---------------------------------------------------------------- sozlama

def read_text(path):
    """Bloknot UTF-8 (BOM bilan/siz) yoki ANSI (1251) da saqlagan bo'lsa ham o'qiladi. Fayl yo'q — None."""
    try:
        with open(path, "rb") as f:
            raw = f.read()
    except OSError:
        return None
    for enc in ("utf-8-sig", "cp1251"):
        try:
            return raw.decode(enc)
        except UnicodeDecodeError:
            continue
    return raw.decode("latin-1")


class KassaConf:
    def __init__(self, enabled=True, port=PORT, title="", refresh_min=5):
        self.enabled = enabled
        self.port = port
        self.title = title
        self.refresh_min = refresh_min

    @staticmethod
    def load(here):
        cp = configparser.ConfigParser(interpolation=None)
        for name in ("forwarder.ini", "onec.ini"):
            p = os.path.join(here, name)
            text = read_text(p)
            if text is None:
                continue
            try:
                cp.read_string(text, source=p)
            except configparser.Error as e:
                log.warning("kassa: %s o'qilmadi: %s", name, e)
        s = cp["kassa"] if cp.has_section("kassa") else {}
        g = lambda k, d="": (s.get(k, d) or d).strip()  # noqa: E731

        def num(k, d, lo, hi):
            try:
                return max(lo, min(hi, int(g(k, str(d)))))
            except ValueError:
                return d
        return KassaConf(
            enabled=g("enabled", "1").lower() not in ("0", "no", "false", "yoq"),
            port=num("port", PORT, 1024, 65535),
            title=g("title")[:80],
            refresh_min=num("refresh", 5, 1, 120),
        )


# ---------------------------------------------------------------- 1C so'rovlari

GOODS_FIELDS = ["Код", "Имя", "Вал", "Ц1", "Ц2", "Ц3", "Ц4", "В1", "В2", "В3", "В4", "Ед", "Гр", "ШК"]
GOODS_Q = (
    "ВЫБРАТЬ Т.Код КАК Код, Т.Наименование КАК Имя, "
    "ВЫБОР КОГДА Т.ВидВал = ЗНАЧЕНИЕ(Перечисление.ВидВал.Вал) ТОГДА 1 ИНАЧЕ 0 КОНЕЦ КАК Вал, "
    "Т.ПродажЦена1 КАК Ц1, Т.ПродажЦена2 КАК Ц2, Т.ПродажЦена3 КАК Ц3, Т.ПродажЦена4 КАК Ц4, "
    "Т.ПродажВал1 КАК В1, Т.ПродажВал2 КАК В2, Т.ПродажВал3 КАК В3, Т.ПродажВал4 КАК В4, "
    "ПРЕДСТАВЛЕНИЕ(Т.ЕдИзм) КАК Ед, ПРЕДСТАВЛЕНИЕ(Т.ГруппаТовара) КАК Гр, Т.ШтрихКод КАК ШК "
    "ИЗ Справочник.Товары КАК Т ГДЕ НЕ Т.ЭтоГруппа И НЕ Т.ПометкаУдаления"
)
# bazada ba'zi rekvizitlar bo'lmasa — oddiyrog'i
GOODS_Q_MIN = (
    "ВЫБРАТЬ Т.Код КАК Код, Т.Наименование КАК Имя, "
    "ВЫБОР КОГДА Т.ВидВал = ЗНАЧЕНИЕ(Перечисление.ВидВал.Вал) ТОГДА 1 ИНАЧЕ 0 КОНЕЦ КАК Вал, "
    "Т.ПродажЦена1 КАК Ц1, Т.ПродажЦена2 КАК Ц2, Т.ПродажЦена3 КАК Ц3, Т.ПродажЦена4 КАК Ц4, "
    "Т.ПродажВал1 КАК В1, Т.ПродажВал2 КАК В2, Т.ПродажВал3 КАК В3, Т.ПродажВал4 КАК В4 "
    "ИЗ Справочник.Товары КАК Т ГДЕ НЕ Т.ЭтоГруппа И НЕ Т.ПометкаУдаления"
)
STOCK_Q = (
    "ВЫБРАТЬ О.Товар.Код КАК Код, СУММА(О.КоличествоОстаток) КАК Кол "
    "ИЗ РегистрНакопления.Товар.Остатки(, Склад = &С) КАК О СГРУППИРОВАТЬ ПО О.Товар.Код"
)
# 1C ПолучитьШтрихКод: Справочник.Штрих (Наименование = shtrix kod) — o'chirish belgisiga qaramaydi
BARCODE_Q = "ВЫБРАТЬ Ш.Наименование КАК ШК, Ш.Товар.Код КАК Код ИЗ Справочник.Штрих КАК Ш"
# tovar kartochkasidagi qo'shimcha shtrix kodlar jadvali (bazada bor; 1C kassasi o'qimaydi — bu yerda qo'shimcha)
BARCODE_TAB_Q = ("ВЫБРАТЬ Т.ШтрихКод КАК ШК, Т.Ссылка.Код КАК Код ИЗ Справочник.Товары.ТабШтрихкодов КАК Т "
                 "ГДЕ НЕ Т.Ссылка.ПометкаУдаления")
CLIENTS_Q = (
    "ВЫБРАТЬ К.Код КАК Код, К.Наименование КАК Имя, К.Тел КАК Тел, "
    "ВЫБОР КОГДА К.Родитель = ЗНАЧЕНИЕ(Справочник.Контрагенты.Поставщики) ТОГДА 1 ИНАЧЕ 0 КОНЕЦ КАК Пост "
    "ИЗ Справочник.Контрагенты КАК К ГДЕ НЕ К.ЭтоГруппа И НЕ К.ПометкаУдаления "
    "И К.Ссылка <> ЗНАЧЕНИЕ(Справочник.Контрагенты.Савдо)"
)
# 1C ПоучитьДолг(.., Контрагент, Сотрудники.Дукон) — do'kon qarzi
DEBTS_Q = (
    "ВЫБРАТЬ О.Контрагент.Код КАК Код, СУММА(О.СуммаОстаток) КАК С, СУММА(О.СуммаВалОстаток) КАК В "
    "ИЗ РегистрНакопления.ВзаиморасчетСКонтрагентом.Остатки(, Сотрудник = ЗНАЧЕНИЕ(Справочник.Сотрудники.Дукон)) КАК О "
    "СГРУППИРОВАТЬ ПО О.Контрагент.Код"
)
DEBT_ONE_Q = (
    "ВЫБРАТЬ ЕСТЬNULL(СУММА(О.СуммаОстаток), 0) КАК С, ЕСТЬNULL(СУММА(О.СуммаВалОстаток), 0) КАК В "
    "ИЗ РегистрНакопления.ВзаиморасчетСКонтрагентом.Остатки(, Контрагент = &К И Сотрудник = &С) КАК О"
)
STAFF_Q = (
    "ВЫБРАТЬ С.Код КАК Код, С.Наименование КАК Имя, С.Пароль КАК Пароль, С.Уволен КАК Уволен "
    "ИЗ Справочник.Сотрудники КАК С ГДЕ НЕ С.ЭтоГруппа И НЕ С.ПометкаУдаления"
)
STOCK_ONE_Q = (
    "ВЫБРАТЬ ЕСТЬNULL(СУММА(О.КоличествоОстаток), 0) КАК Кол, ЕСТЬNULL(СУММА(О.СуммаСумОстаток), 0) КАК Сум, "
    "ЕСТЬNULL(СУММА(О.СуммаВалОстаток), 0) КАК Вал ИЗ РегистрНакопления.Товар.Остатки(, Товар = &Т И Склад = &С) КАК О"
)


def text(v):
    if v is None:
        return ""
    s = str(v).strip()
    return s


def code_text(v):
    """1C kodi (son bo'lsa 15.0 emas, 15)."""
    if isinstance(v, float) and v == int(v):
        v = int(v)
    return text(v)


def fnum(v):
    try:
        return float(v or 0)
    except (TypeError, ValueError):
        return 0.0


def build_snapshot(goods, stock, barcodes, panel, clients, debts, consts, title=""):
    """Sof mantiq: so'rov qatorlaridan brauzerga yuboriladigan ma'lumot."""
    st = {code_text(r.get("Код")): fnum(r.get("Кол")) for r in stock}
    g_out = []
    by_code = {}
    extra_bc = {}
    for r in goods:
        code = code_text(r.get("Код"))
        name = text(r.get("Имя"))
        if not code or not name:
            continue
        item = {
            "c": code, "n": name, "u": 1 if fnum(r.get("Вал")) else 0,
            "p": [fnum(r.get("Ц%d" % i)) for i in range(1, 5)],
            "v": [fnum(r.get("В%d" % i)) for i in range(1, 5)],
            "s": round(st.get(code, 0.0), 3),
            "e": text(r.get("Ед")), "g": text(r.get("Гр")),
        }
        by_code[code] = item
        g_out.append(item)
        bc = text(r.get("ШК"))
        if bc and bc != "0":
            extra_bc[bc] = code
    b_out = {}
    for r in barcodes:
        bc = text(r.get("ШК"))
        code = code_text(r.get("Код"))
        if bc and code in by_code:
            b_out[bc] = code              # Справочник.Штрих — 1C'dagi kabi asosiy manba
    for bc, code in extra_bc.items():
        b_out.setdefault(bc, code)        # tovar kartochkasidagi shtrix kod — qo'shimcha
    debt = {code_text(r.get("Код")): (round(fnum(r.get("С")), 2), round(fnum(r.get("В")), 2)) for r in debts}
    c_out = []
    for r in clients:
        code = code_text(r.get("Код"))
        name = text(r.get("Имя"))
        if not code or not name:
            continue
        ds, du = debt.get(code, (0.0, 0.0))
        c_out.append([code, name, text(r.get("Тел")), ds, du, 1 if fnum(r.get("Пост")) else 0])
    c_out.sort(key=lambda x: (x[5], x[1].lower()))
    g_out.sort(key=lambda x: x["n"].lower())
    return {
        "rate": consts.get("rate", 0.0), "neg": bool(consts.get("neg")), "rongta": bool(consts.get("rongta")),
        "store": consts.get("store", ""), "title": title,
        "g": g_out, "b": b_out, "pnl": panel or [], "cl": c_out,
    }


def check_stock(lines, stock_of, kind):
    """Sotuvda tovar qoldig'i yetadimi (1C ПродажаРозница o'tkazishda ham tekshiradi). @return xato matni yoki ""."""
    if kind != kc.KIND_SALE:
        return ""
    need = {}
    names = {}
    for l in lines:
        need[l["code"]] = need.get(l["code"], Decimal(0)) + l["qty"]
        names[l["code"]] = l.get("name") or l["code"]
    short = []
    for code, q in need.items():
        have = kc.qty(stock_of(code))
        if have < q:
            short.append("%s (bor: %s)" % (names[code], kc.jnum(have)))
    if short:
        return "Qoldiq yetmaydi: " + ", ".join(short)[:250]
    return ""


class KassaOneC:
    """1C bilan ish (COM obyekti [c] beriladi). Faqat kassa oqimidan chaqiriladi."""

    def __init__(self, title="", now=None):
        self.title = title
        self.now = now or dt.datetime.now

    @staticmethod
    def query(c, q_text, fields, **params):
        q = c.NewObject("Запрос")
        q.Текст = q_text
        for k, v in params.items():
            q.УстановитьПараметр(k, v)
        res = q.Выполнить().Выбрать()
        rows = []
        while res.Следующий():
            rows.append({f: getattr(res, f) for f in fields})
        return rows

    def consts(self, c):
        out = {"rate": fnum(c.Константы.Курс.Получить())}
        store = c.Константы.Склад1.Получить()
        out["store_ref"] = store
        out["store"] = text(c.String(store))
        try:
            out["neg"] = bool(c.Константы.ПродажаБезОстатков.Получить())
        except Exception:  # noqa: BLE001
            out["neg"] = False
        try:
            out["rongta"] = text(c.String(c.Константы.ТипВесы.Получить())) == text(c.String(c.Перечисления.СписокВесов.RONGTA))
        except Exception:  # noqa: BLE001
            out["rongta"] = False
        return out

    def goods(self, c):
        try:
            return self.query(c, GOODS_Q, GOODS_FIELDS)
        except Exception as e:  # noqa: BLE001
            log.warning("kassa: to'liq tovar so'rovi ishlamadi (%s) — qisqasi", str(e)[:120])
            return self.query(c, GOODS_Q_MIN, GOODS_FIELDS[:11])

    def stock(self, c, store):
        return self.query(c, STOCK_Q, ["Код", "Кол"], С=store)

    def barcodes(self, c):
        """Avval qo'shimcha jadval, keyin Справочник.Штрих (bir xil kod bo'lsa — 1C kassasidagisi ustun)."""
        rows = []
        for q, what in ((BARCODE_TAB_Q, "ТабШтрихкодов"), (BARCODE_Q, "Справочник.Штрих")):
            try:
                rows += self.query(c, q, ["ШК", "Код"])
            except Exception as e:  # noqa: BLE001
                log.warning("kassa: %s o'qilmadi: %s", what, str(e)[:120])
        return rows

    def clients(self, c):
        for q in (CLIENTS_Q, CLIENTS_Q.replace("К.Тел КАК Тел", "\"\" КАК Тел")):
            try:
                return self.query(c, q, ["Код", "Имя", "Тел", "Пост"])
            except Exception as e:  # noqa: BLE001
                log.warning("kassa: mijozlar: %s", str(e)[:120])
        return []

    def debts(self, c):
        try:
            return self.query(c, DEBTS_Q, ["Код", "С", "В"])
        except Exception as e:  # noqa: BLE001
            log.warning("kassa: qarzlar: %s", str(e)[:120])
            return []

    def staff(self, c):
        rows = self.query(c, STAFF_Q, ["Код", "Имя", "Пароль", "Уволен"])
        out = []
        for r in rows:
            pw = text(r.get("Пароль"))
            if isinstance(r.get("Пароль"), float):
                pw = code_text(r.get("Пароль"))
            if pw:
                out.append({"code": code_text(r.get("Код")), "name": text(r.get("Имя")), "pw": pw, "fired": bool(r.get("Уволен"))})
        return out

    def panel(self, c):
        import onec_data

        class _Q:
            def query(_self, q_text, fields, **params):
                return self.query(c, q_text, fields, **params)
        return onec_data.panel(_Q())

    def snapshot(self, c):
        k = self.consts(c)
        snap = build_snapshot(self.goods(c), self.stock(c, k["store_ref"]), self.barcodes(c), self.panel(c),
                              self.clients(c), self.debts(c), k, self.title)
        return snap, self.staff(c)

    # --- yozish ---
    @staticmethod
    def find(cat, code):
        ref = None
        try:
            ref = cat.НайтиПоКоду(int(float(code)))
        except (TypeError, ValueError):
            ref = cat.НайтиПоКоду(code)
        if ref is None or ref.Пустая():
            return None
        return ref

    def stock_one(self, c, goods, store):
        rows = self.query(c, STOCK_ONE_Q, ["Кол", "Сум", "Вал"], Т=goods, С=store)
        r = rows[0] if rows else {}
        return fnum(r.get("Кол")), fnum(r.get("Сум")), fnum(r.get("Вал"))

    def debt_one(self, c, party):
        rows = self.query(c, DEBT_ONE_Q, ["С", "В"], К=party, С=c.Справочники.Сотрудники.Дукон)
        r = rows[0] if rows else {}
        return fnum(r.get("С")), fnum(r.get("В"))

    def write_sale(self, c, sale, pay, employee_code):
        """ПродажаРозница — 1C РасчетНажатие / ВозвратНажатие bilan bir xil. @return (raqam, sana matni)."""
        is_return = sale["kind"] == kc.KIND_RETURN
        k = self.consts(c)
        store = k["store_ref"]
        rate = c.Константы.Курс.Получить()
        doc = c.Документы.ПродажаРозница.СоздатьДокумент()
        when = self.now()
        doc.Дата = when
        doc.Курс = rate
        doc.Склад = store
        doc.ВидВал = c.Перечисления.ВидВал.Сум
        doc.ВидПродаж = getattr(c.Перечисления.ТипЦена, sale["ptype"])
        doc.ВсегоСум = float(pay["total"])
        doc.СкидкаСум = float(pay["discount"])
        doc.Нал = float(pay["cash"])
        doc.Пер = float(pay["card"])
        doc.Клик = float(pay["click"])
        emp = self.find(c.Справочники.Сотрудники, employee_code) if employee_code else None
        if emp is not None:
            doc.Сотрудник = emp
            if code_text(emp.Код) != code_text(c.Справочники.Сотрудники.Дукон.Код):
                doc.ДоляСотОп = emp.ДоляСотОп
                doc.ДоляСотПр = emp.ДоляСотПр
        party = self.find(c.Справочники.Контрагенты, sale["client"]) if sale["client"] else None
        if sale["client"] and party is None:
            raise kc.KassaError("1C'da mijoz topilmadi (kod %s)" % sale["client"])
        if party is not None and code_text(party.Код) != code_text(c.Справочники.Контрагенты.Савдо.Код):
            ds, du = self.debt_one(c, party)
            doc.Контрагент = party
            doc.ДтКтСум = ds
            doc.ДтКтВал = du
            doc.ЛимитСум = party.ЛимитСум
            doc.ЛимитВал = party.ЛимитВал
        else:
            doc.Контрагент = c.Справочники.Контрагенты.Савдо
        doc.ВидДокумента = getattr(c.Перечисления.ВидДокумента, pay["doc"])
        doc.Комментарий = sale["note"]
        # avval hamma tovar va qoldig'i (1C o'tkazishda qoldiq yetmasa rad etadi — oldindan aniq xabar)
        refs = {}
        for ln in sale["lines"]:
            if ln["code"] in refs:
                continue
            g = self.find(c.Справочники.Товары, ln["code"])
            if g is None:
                raise kc.KassaError("1C'da tovar topilmadi: %s" % (ln.get("name") or ln["code"]))
            refs[ln["code"]] = (g, self.stock_one(c, g, store))
        short = check_stock(sale["lines"], lambda code: refs[code][1][0], sale["kind"])
        if short:
            raise kc.KassaError(short)
        for ln in sale["lines"]:
            g, (q_have, s_have, u_have) = refs[ln["code"]]
            r = doc.Таблица.Добавить()
            r.Товары = g
            r.ЕдИзм = g.ЕдИзм
            vol = fnum(g.Обьём)
            r.Шт = vol if vol > 0 else 1
            r.ВидВал = doc.ВидВал
            r.Цены = doc.ВидПродаж
            r.Кг = g.Кг
            r.ДоляП = g.ДоляП
            if is_return:
                # 1C: ОхиргиУртачаНархиниАниклаш_Махсулот — o'rtacha tannarx (qoldiq bo'lmasa — kirim narxi)
                avg_s = s_have / q_have if q_have > 0 else fnum(g.ПриходЦена)
                avg_u = u_have / q_have if q_have > 0 else fnum(g.ПриходВал)
            else:
                r.Ост = q_have
                r.ОстСуммаСум = s_have
                r.ОстСуммаВал = u_have
                avg_s = s_have / q_have if q_have > 0 else 0.0
                avg_u = u_have / q_have if q_have > 0 else 0.0
            r.УртачаНархСум = round(avg_s, 2)
            r.УртачаНархВал = round(avg_u, 4)
            r.Кол = float(ln["qty"])
            r.Блок = float(ln["qty"]) / (vol if vol > 0 else 1)
            r.ЦенаСум = float(ln["price"])
            r.СуммаСум = float(kc.line_sum(ln["price"], ln["qty"]))
        doc.Записать(c.РежимЗаписиДокумента.Проведение)
        return text(doc.Номер), when.strftime("%d.%m.%Y %H:%M")


# ---------------------------------------------------------------- jurnal (takror yozilmasin)

class DoneLog:
    def __init__(self, path, keep=3000):
        self.path = path
        self.keep = keep
        self.items = {}
        self.order = []
        self.appended = 0
        try:
            with open(path, "r", encoding="utf-8") as f:
                for line in f:
                    try:
                        o = json.loads(line)
                    except ValueError:
                        continue
                    if isinstance(o, dict) and o.get("id"):
                        self._put(o)
        except OSError:
            pass

    def _put(self, o):
        if o["id"] not in self.items:
            self.order.append(o["id"])
        self.items[o["id"]] = o
        while len(self.order) > self.keep:
            self.items.pop(self.order.pop(0), None)

    def get(self, rid):
        return self.items.get(rid)

    def add(self, o):
        self._put(o)
        try:
            with open(self.path, "a", encoding="utf-8") as f:
                f.write(json.dumps(o, ensure_ascii=False) + "\n")
        except OSError as e:
            log.warning("kassa jurnali: %s", e)
        self.appended += 1
        if self.appended >= self.keep:       # fayl cheksiz o'smasin: oxirgi [keep] tasi qoladi
            self.appended = 0
            self._compact()

    def _compact(self):
        try:
            tmp = self.path + ".tmp"
            with open(tmp, "w", encoding="utf-8") as f:
                for rid in self.order:
                    f.write(json.dumps(self.items[rid], ensure_ascii=False) + "\n")
            os.replace(tmp, self.path)
        except OSError:
            pass


# ---------------------------------------------------------------- xizmat (bitta 1C oqimi)

class KassaService:
    def __init__(self, conf, here, open_com, open_app=None, use_app=lambda: False, onec=None, clock=time.time):
        """
        open_com() -> COM ulanish obyekti ([.c] va [.close()]); open_app() -> 1C oynasi (yozishda kerak bo'lsa).
        use_app() — sozlamada "oyna orqali" majburiymi ([onec] mode = app). Aks holda avval COM, baza moduli COM'da
        ishlamasa (yozuv boshlanmasdan chiqadigan xato) — shu kundan oyna orqali (kassachi kutib qolmasligi uchun
        oyna kassa ishlatilayotgan paytda yopilmaydi).
        """
        self.conf = conf
        self.here = here
        self.open_com = open_com
        self.open_app = open_app
        self.use_app = use_app
        self.onec = onec or KassaOneC(conf.title)
        self.clock = clock
        self.jobs = queue.Queue()
        self.lock = threading.Lock()
        self.snap = None
        self.snap_json = b""
        self.snap_gz = b""
        self.ver = 0
        self.staff = []
        self.tokens = {}
        self.full_at = 0.0
        self.stock_at = 0.0
        self.seen_at = 0.0
        self.com = None
        self.app = None
        self.done = DoneLog(os.path.join(here, DONE_FILE))
        self.last_error = ""
        self.app_mode = False

    # --- brauzer tomoni (HTTP oqimlari) ---
    def touch(self):
        self.seen_at = self.clock()

    def call(self, fn, timeout=CALL_TIMEOUT):
        box = {}
        ev = threading.Event()
        self.jobs.put((fn, box, ev))
        if not ev.wait(timeout):
            raise kc.KassaError("1C javob bermadi (band). Birozdan keyin qayta urinib ko'ring")
        if "e" in box:
            raise box["e"]
        return box.get("r")

    def data(self, ver):
        """@return (yangi bo'lsa) json baytlari, gzip baytlari; o'zgarmagan bo'lsa None."""
        self.touch()
        if self.snap is None:
            self.call(self._refresh_full)
        with self.lock:
            if ver and ver == self.ver:
                return None
            return self.snap_json, self.snap_gz

    def login(self, pw):
        self.touch()
        pw = str(pw or "").strip()
        if not pw:
            raise kc.KassaError("Parolni kiriting")
        if not self.staff:
            self.call(self._refresh_full)
        if not self.staff:
            raise kc.KassaError("1C'da hech bir xodimga parol qo'yilmagan (Сотрудники → Пароль)")
        for s in self.staff:
            if s["pw"] == pw:
                if s["fired"]:
                    raise kc.KassaError("Siz bo'shatilgansiz")
                tok = secrets.token_urlsafe(24)
                now = self.clock()
                with self.lock:
                    self.tokens = {t: v for t, v in self.tokens.items() if now - v[2] < TOKEN_TTL}
                    self.tokens[tok] = (s["code"], s["name"], now)
                return {"ok": True, "token": tok, "name": s["name"]}
        raise kc.KassaError("Bunday parolli xodim yo'q")

    def who(self, token):
        with self.lock:
            v = self.tokens.get(token or "")
        if v is None or self.clock() - v[2] >= TOKEN_TTL:
            raise PermissionError("login")
        return v

    def sale(self, body):
        self.touch()
        sale = kc.clean_sale(body)
        emp_code, emp_name, _ = self.who(sale["token"])
        prev = self.done.get(sale["id"])
        if prev is not None:
            return prev
        res = self.call(lambda: self._write(sale, emp_code, emp_name))
        return res

    # --- 1C oqimi ---
    def _com(self):
        if self.com is None:
            self.com = self.open_com()
        return self.com.c

    def _drop(self):
        for o in (self.com,):
            if o is not None:
                try:
                    o.close()
                except Exception:  # noqa: BLE001
                    pass
        self.com = None

    def _publish(self, snap):
        snap = dict(snap)
        self.ver = int(self.clock() * 1000)
        snap["ver"] = self.ver
        raw = json.dumps(snap, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        with self.lock:
            self.snap = snap
            self.snap_json = raw
            self.snap_gz = gzip.compress(raw, 6)

    def _refresh_full(self):
        c = self._com()
        snap, staff = self.onec.snapshot(c)
        self.staff = staff
        self._publish(snap)
        self.full_at = self.stock_at = self.clock()
        log.info("kassa: ma'lumot yangilandi (%d tovar, %d mijoz)", len(snap["g"]), len(snap["cl"]))

    def _refresh_stock(self):
        c = self._com()
        k = self.onec.consts(c)
        st = {code_text(r.get("Код")): fnum(r.get("Кол")) for r in self.onec.stock(c, k["store_ref"])}
        snap = dict(self.snap or {})
        changed = abs(fnum(snap.get("rate")) - k["rate"]) > 1e-9 or bool(snap.get("neg")) != k["neg"]
        goods = []
        for g in snap.get("g", []):
            s = round(st.get(g["c"], 0.0), 3)
            if s != g["s"]:
                g = dict(g, s=s)
                changed = True
            goods.append(g)
        self.stock_at = self.clock()
        if changed:
            snap.update(g=goods, rate=k["rate"], neg=k["neg"])
            self._publish(snap)

    def _apply_stock(self, sale, debt_delta):
        """Yozilgandan keyin qoldiq va mijoz qarzi xotirada darhol o'zgaradi (keyingi yangilanishgacha kutilmaydi)."""
        snap = dict(self.snap or {})
        sign = -1 if sale["kind"] == kc.KIND_SALE else 1
        delta = {}
        for l in sale["lines"]:
            delta[l["code"]] = delta.get(l["code"], 0.0) + sign * float(l["qty"])
        snap["g"] = [dict(g, s=round(g["s"] + delta[g["c"]], 3)) if g["c"] in delta else g for g in snap.get("g", [])]
        if sale["client"] and debt_delta:
            snap["cl"] = [[x[0], x[1], x[2], round(x[3] + debt_delta, 2), x[4], x[5]] if x[0] == sale["client"] else x
                          for x in snap.get("cl", [])]
        self._publish(snap)

    def _write(self, sale, emp_code, emp_name):
        prev = self.done.get(sale["id"])
        if prev is not None:
            return prev
        if self.snap is None:
            self._refresh_full()
        goods = {g["c"]: g for g in self.snap["g"]}
        rate = self.snap.get("rate", 0.0)
        changed = kc.check_prices(sale["lines"], goods, sale["ptype"], rate)
        if changed:
            self._refresh_full()
            goods = {g["c"]: g for g in self.snap["g"]}
            if kc.check_prices(sale["lines"], goods, sale["ptype"], self.snap.get("rate", 0.0)):
                raise kc.KassaError("Narx o'zgargan: %s. Kassa yangilandi — savatni qayta tekshiring" % ", ".join(changed)[:200])
        for l in sale["lines"]:
            l["name"] = goods[l["code"]]["n"]
        total = kc.cart_total(sale["lines"])
        pay = kc.settle(total, sale["discount"], sale["cash"], sale["card"], sale["click"], bool(sale["client"]), sale["kind"])
        number, when = self._write_doc(sale, pay, emp_code)
        # qarz: sotuvda to'lanmagan qismi qo'shiladi (ortiqcha to'lov/qarz to'lovi — kamayadi), qaytarishda — kamayadi
        debt_delta = float(pay["debt"]) * (1 if sale["kind"] == kc.KIND_SALE else -1)
        old = [x for x in self.snap.get("cl", []) if x[0] == sale["client"]] if sale["client"] else []
        res = {
            "id": sale["id"], "ok": True, "no": number, "date": when, "kind": sale["kind"], "doc": pay["doc"],
            "cashier": emp_name, "total": kc.jnum(pay["total"]), "discount": kc.jnum(pay["discount"]),
            "due": kc.jnum(pay["due"]), "cash": kc.jnum(pay["cash"]), "card": kc.jnum(pay["card"]),
            "click": kc.jnum(pay["click"]), "change": kc.jnum(pay["change"]), "debt": kc.jnum(pay["debt"]),
            "client": sale["client"], "cname": old[0][1] if old else "",
            "cdebt": round(old[0][3] + debt_delta, 2) if old else 0,
            "lines": [{"n": l["name"], "q": kc.jnum(l["qty"]), "p": kc.jnum(l["price"]),
                       "s": kc.jnum(kc.line_sum(l["price"], l["qty"]))} for l in sale["lines"]],
        }
        self.done.add(res)
        self._apply_stock(sale, debt_delta)
        return res

    def _write_doc(self, sale, pay, emp_code):
        import onec_write
        if self.open_app is not None and (self.app_mode or self.use_app()):
            return self._write_app(sale, pay, emp_code)
        try:
            return self.onec.write_sale(self._com(), sale, pay, emp_code)
        except kc.KassaError:
            raise
        except Exception as e:  # noqa: BLE001
            if self.open_app is not None and onec_write.needs_app(str(e)):
                log.info("kassa: COM'da modul ishlamadi — 1C oynasi orqali")
                self.app_mode = True
                return self._write_app(sale, pay, emp_code)
            raise kc.KassaError("1C: %s" % onec_write.explain_1c_error(str(e))[:250])

    def _write_app(self, sale, pay, emp_code):
        import onec_write
        if self.app is None:
            self.app = self.open_app()
        self.app.connect()
        try:
            return self.onec.write_sale(self.app.c, sale, pay, emp_code)
        except kc.KassaError:
            raise
        except Exception as e:  # noqa: BLE001
            raise kc.KassaError("1C: %s" % onec_write.explain_1c_error(str(e))[:250])

    def tick(self):
        """Navbat bo'sh paytda: brauzer ochiq bo'lsa — ma'lumotni yangilab turish."""
        now = self.clock()
        if now - self.seen_at > ACTIVE_SEC:
            # hech kim ishlatmayapti — 1C ulanishlari yopiladi (litsenziya bo'shaydi)
            if self.com is not None:
                self._drop()
            if self.app is not None:
                try:
                    self.app.quit()
                except Exception:  # noqa: BLE001
                    pass
            return
        if self.snap is None or now - self.full_at >= self.conf.refresh_min * 60:
            self._refresh_full()
        elif now - self.stock_at >= STOCK_SEC:
            self._refresh_stock()

    def run(self):
        try:
            import pythoncom
            pythoncom.CoInitialize()
        except ImportError:
            pass
        while True:
            try:
                fn, box, ev = self.jobs.get(timeout=5)
            except queue.Empty:
                try:
                    self.tick()
                except (Exception, SystemExit) as e:  # noqa: BLE001 - 1C'ga ulanib bo'lmasa ham oqim to'xtamaydi
                    self.last_error = str(e)[:300]
                    log.warning("kassa: yangilash: %s", self.last_error)
                    self._drop()
                continue
            try:
                box["r"] = fn()
            except kc.KassaError as e:
                box["e"] = e
            except SystemExit as e:
                box["e"] = kc.KassaError(str(e)[:250])
                self._drop()
            except Exception as e:  # noqa: BLE001
                log.exception("kassa: 1C xatosi")
                self._drop()
                box["e"] = kc.KassaError("1C: %s" % str(e)[:250])
            finally:
                ev.set()


# ---------------------------------------------------------------- monoblok o'rnatish fayli

def monoblok_bat(url):
    """
    Monoblokda bir marta ochiladigan .bat: Chrome (yo'q bo'lsa Edge) ni kassa manzili bilan TO'LIQ EKRANDA ochadi,
    chekni so'ramasdan standart printerga chiqaradi va Windows yoqilganda o'zi ochiladigan qiladi.
    Alohida profil (--user-data-dir) — oddiy Chrome ochiq bo'lsa ham kiosk rejimi ishlaydi. Faqat ASCII, CRLF.
    """
    if not url.startswith("http://") or any(ch in url for ch in '"%^&|<>\r\n '):
        raise ValueError("url")
    lines = [
        "@echo off",
        "rem IMSOFT kassa oynasi: monoblokda to'liq ekranda ochish va avtozapusk",
        "set \"URL=%s\"" % url,
        "set \"PF86=%ProgramFiles(x86)%\"",
        "set \"B=\"",
        "if exist \"%ProgramFiles%\\Google\\Chrome\\Application\\chrome.exe\" set \"B=%ProgramFiles%\\Google\\Chrome\\Application\\chrome.exe\"",
        "if not defined B if exist \"%PF86%\\Google\\Chrome\\Application\\chrome.exe\" set \"B=%PF86%\\Google\\Chrome\\Application\\chrome.exe\"",
        "if not defined B if exist \"%LocalAppData%\\Google\\Chrome\\Application\\chrome.exe\" set \"B=%LocalAppData%\\Google\\Chrome\\Application\\chrome.exe\"",
        "if not defined B if exist \"%PF86%\\Microsoft\\Edge\\Application\\msedge.exe\" set \"B=%PF86%\\Microsoft\\Edge\\Application\\msedge.exe\"",
        "if not defined B if exist \"%ProgramFiles%\\Microsoft\\Edge\\Application\\msedge.exe\" set \"B=%ProgramFiles%\\Microsoft\\Edge\\Application\\msedge.exe\"",
        "if not defined B goto nobrowser",
        "set \"S=%APPDATA%\\Microsoft\\Windows\\Start Menu\\Programs\\Startup\\IMSOFT_kassa.bat\"",
        "> \"%S%\" echo @start \"\" \"%B%\" --kiosk --kiosk-printing --edge-kiosk-type=fullscreen --no-first-run "
        "--user-data-dir=\"%LocalAppData%\\IMSOFT_kassa\" %URL%",
        "start \"\" \"%B%\" --kiosk --kiosk-printing --edge-kiosk-type=fullscreen --no-first-run "
        "--user-data-dir=\"%LocalAppData%\\IMSOFT_kassa\" %URL%",
        "echo Tayyor: kassa ochildi va har safar Windows yoqilganda o'zi ochiladi. Chiqish: Alt+F4.",
        "timeout /t 5 >nul",
        "exit /b 0",
        ":nobrowser",
        "echo Chrome yoki Edge topilmadi. Google Chrome ni o'rnating va bu faylni qayta oching.",
        "pause",
        "exit /b 1",
    ]
    return ("\r\n".join(lines) + "\r\n").encode("ascii")


# ---------------------------------------------------------------- HTTP

CT = {".html": "text/html; charset=utf-8", ".js": "application/javascript; charset=utf-8",
      ".css": "text/css; charset=utf-8", ".png": "image/png", ".svg": "image/svg+xml", ".ico": "image/x-icon",
      ".json": "application/json; charset=utf-8", ".webmanifest": "application/manifest+json"}


def make_handler(service, static_dir):
    class H(BaseHTTPRequestHandler):
        server_version = "IMSOFT-Kassa"

        def log_message(self, fmt, *args):  # noqa: N802 - jim
            log.debug("kassa http: " + fmt, *args)

        def _send(self, code, body, ctype="application/json; charset=utf-8", gz=False):
            self.send_response(code)
            self.send_header("Content-Type", ctype)
            self.send_header("Cache-Control", "no-store")
            if gz:
                self.send_header("Content-Encoding", "gzip")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def _json(self, code, obj):
            self._send(code, json.dumps(obj, ensure_ascii=False).encode("utf-8"))

        def _body(self):
            n = int(self.headers.get("Content-Length") or 0)
            if n <= 0 or n > 2_000_000:
                return {}
            try:
                return json.loads(self.rfile.read(n).decode("utf-8"))
            except ValueError:
                return {}

        def do_GET(self):  # noqa: N802
            path = self.path.split("?", 1)[0]
            query = self.path.split("?", 1)[1] if "?" in self.path else ""
            if path == "/api/data":
                params = dict(p.split("=", 1) for p in query.split("&") if "=" in p)
                try:
                    service.who(params.get("t", ""))
                    ver = int(params.get("v") or 0)
                    got = service.data(ver)
                except PermissionError:
                    return self._json(401, {"ok": False, "e": "login"})
                except kc.KassaError as e:
                    return self._json(503, {"ok": False, "e": str(e)})
                except ValueError:
                    return self._json(400, {"ok": False, "e": "v"})
                if got is None:
                    return self._json(200, {"same": True})
                raw, gz = got
                if "gzip" in (self.headers.get("Accept-Encoding") or ""):
                    return self._send(200, gz, gz=True)
                return self._send(200, raw)
            if path == "/api/ping":
                return self._json(200, {"ok": True, "err": service.last_error})
            if path == "/monoblok.bat":
                host = (self.headers.get("Host") or "").strip()
                try:
                    if not host or "/" in host:
                        raise ValueError("host")
                    raw = monoblok_bat("http://%s/" % host)
                except ValueError:
                    return self._send(400, b"bad host", "text/plain")
                self.send_response(200)
                self.send_header("Content-Type", "application/octet-stream")
                self.send_header("Content-Disposition", "attachment; filename=IMSOFT_kassa.bat")
                self.send_header("Content-Length", str(len(raw)))
                self.end_headers()
                self.wfile.write(raw)
                return None
            name = "index.html" if path in ("/", "") else path.lstrip("/")
            if "/" in name or "\\" in name or name.startswith(".") or name.endswith(".py"):
                return self._send(404, b"not found", "text/plain")
            p = os.path.join(static_dir, name)
            ext = os.path.splitext(name)[1].lower()
            if ext not in CT or not os.path.isfile(p):
                return self._send(404, b"not found", "text/plain")
            with open(p, "rb") as f:
                self._send(200, f.read(), CT[ext])

        def do_POST(self):  # noqa: N802
            path = self.path.split("?", 1)[0]
            body = self._body()
            try:
                if path == "/api/login":
                    return self._json(200, service.login(body.get("pw")))
                if path == "/api/sale":
                    return self._json(200, service.sale(body))
                if path == "/api/refresh":
                    service.who(body.get("token"))
                    service.call(service._refresh_full)
                    return self._json(200, {"ok": True})
            except PermissionError:
                return self._json(401, {"ok": False, "e": "login"})
            except kc.KassaError as e:
                return self._json(200, {"ok": False, "e": str(e)})
            return self._send(404, b"not found", "text/plain")

    return H


def lan_ip():
    """Do'kon tarmog'idagi IP (internetga hech narsa yuborilmaydi — faqat marshrut tanlanadi)."""
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("10.255.255.255", 1))
        return s.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        s.close()


def firewall_args(port):
    name = "IMSOFT kassa %d" % port
    return (["netsh", "advfirewall", "firewall", "delete", "rule", "name=%s" % name],
            ["netsh", "advfirewall", "firewall", "add", "rule", "name=%s" % name, "dir=in", "action=allow",
             "protocol=TCP", "localport=%d" % port, "profile=any"])


def ensure_firewall(port):
    """Windows brandmauerida portni ochadi (dastur administrator huquqi bilan ishlaydi). Bo'lmasa — jurnalga maslahat."""
    if os.name != "nt":
        return False
    import subprocess
    flags = getattr(subprocess, "CREATE_NO_WINDOW", 0)
    ok = True
    for args in firewall_args(port):
        try:
            r = subprocess.run(args, capture_output=True, timeout=30, creationflags=flags)
            ok = r.returncode == 0
        except (OSError, subprocess.SubprocessError):
            ok = False
    if not ok:
        log.warning("kassa: brandmauerda %d-port ochilmadi - allow_firewall.bat ni administrator sifatida ishga tushiring", port)
    return ok


def start(cfg, here, cloud=None, onec_cls=None, app_cls=None):
    """onec_export.py chaqiradi. @return xizmat yoki None (o'chiq / port band)."""
    conf = KassaConf.load(here)
    if not conf.enabled:
        log.info("kassa oynasi o'chiq ([kassa] enabled = 0)")
        return None

    def open_com():
        o = onec_cls(cfg)
        o.connect()
        return o

    def use_app():
        return str(getattr(cfg, "onec_mode", "auto") or "auto").lower() == "app"

    svc = KassaService(conf, here, open_com, (lambda: app_cls(cfg)) if app_cls else None, use_app)
    try:
        httpd = ThreadingHTTPServer(("0.0.0.0", conf.port), make_handler(svc, os.path.join(here, STATIC_DIR)))
    except OSError as e:
        log.warning("kassa oynasi: %d-port band yoki ruxsat yo'q: %s", conf.port, e)
        return None
    httpd.daemon_threads = True
    ensure_firewall(conf.port)
    threading.Thread(target=svc.run, daemon=True, name="kassa-1c").start()
    threading.Thread(target=httpd.serve_forever, daemon=True, name="kassa-http").start()
    url = "http://%s:%d" % (lan_ip(), conf.port)
    log.info("kassa oynasi: %s", url)
    if cloud is not None:
        try:
            cloud.call("PUT", "p/%s/kassa" % cloud.cfg.inbox, {"url": url, "at": {".sv": "timestamp"}}, timeout=30, print="silent")
        except Exception as e:  # noqa: BLE001
            log.warning("kassa manzilini bulutga yozib bo'lmadi: %s", e)
    svc.url = url
    return svc
