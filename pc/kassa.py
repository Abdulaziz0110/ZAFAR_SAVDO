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
import re
import secrets
import socket
import threading
import time
import urllib.parse
from decimal import Decimal
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import kassa_core as kc
import kassa_panel as kp

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
# pc 33: mijozning UMUMIY qarzi (hamma xodim/agentlar bo'yicha — telefondagi hisobot bilan bir xil). Avval faqat "Дукон"
# bo'yicha olinardi — agent orqali olingan qarzlar kassada ko'rinmasdi.
DEBTS_Q = (
    "ВЫБРАТЬ О.Контрагент.Код КАК Код, СУММА(О.СуммаОстаток) КАК С, СУММА(О.СуммаВалОстаток) КАК В "
    "ИЗ РегистрНакопления.ВзаиморасчетСКонтрагентом.Остатки КАК О "
    "СГРУППИРОВАТЬ ПО О.Контрагент.Код"
)
DEBT_ONE_Q = (
    "ВЫБРАТЬ ЕСТЬNULL(СУММА(О.СуммаОстаток), 0) КАК С, ЕСТЬNULL(СУММА(О.СуммаВалОстаток), 0) КАК В "
    "ИЗ РегистрНакопления.ВзаиморасчетСКонтрагентом.Остатки(, Контрагент = &К И Сотрудник = &С) КАК О"
)
STAFF_Q = (
    "ВЫБРАТЬ С.Код КАК Код, С.Наименование КАК Имя, С.Пароль КАК Пароль, С.Уволен КАК Уволен, С.Админ КАК Админ "
    "ИЗ Справочник.Сотрудники КАК С ГДЕ НЕ С.ЭтоГруппа И НЕ С.ПометкаУдаления"
)
STAFF_Q_MIN = STAFF_Q.replace(", С.Админ КАК Админ", "")
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
        "lock": bool(consts.get("lock")), "chk": consts.get("chk") or {},
        "store": consts.get("store", ""), "title": title,
        "g": g_out, "b": b_out, "pnl": panel or [], "cl": c_out,
    }


SALES_STATS_NORET = " И Т.Ссылка.ВидДокумента <> ЗНАЧЕНИЕ(Перечисление.ВидДокумента.Возврать)"
SALES_STATS_Q = ("ВЫБРАТЬ Т.Товары.Код КАК Код, КОЛИЧЕСТВО(РАЗЛИЧНЫЕ Т.Ссылка) КАК Н "
                 "ИЗ Документ.ПродажаРозница.Таблица КАК Т "
                 "ГДЕ Т.Ссылка.Проведен И Т.Ссылка.Дата МЕЖДУ &Н1 И &Н2" + SALES_STATS_NORET +
                 " СГРУППИРОВАТЬ ПО Т.Товары.Код")


DAY_DOCS_Q = (
    "ВЫБРАТЬ Д.Номер КАК Номер, Д.Дата КАК Дата, Д.Контрагент.Код КАК КК, Д.Контрагент.Наименование КАК КН, "
    "Д.Сотрудник.Наименование КАК Сот, Д.ВсегоСум КАК Всего, Д.СкидкаСум КАК Ск, Д.Нал КАК Нал, Д.Пер КАК Пер, "
    "Д.Клик КАК Клик, Д.ДтКтСум КАК Долг, "
    "ВЫБОР КОГДА Д.ВидДокумента = ЗНАЧЕНИЕ(Перечисление.ВидДокумента.Возврать) ТОГДА 1 "
    "КОГДА Д.ВидДокумента = ЗНАЧЕНИЕ(Перечисление.ВидДокумента.ОплатаДолга) ТОГДА 2 ИНАЧЕ 0 КОНЕЦ КАК Вид, "
    "ВЫБОР КОГДА Д.Контрагент = ЗНАЧЕНИЕ(Справочник.Контрагенты.Савдо) ТОГДА 1 ИНАЧЕ 0 КОНЕЦ КАК Савдо "
    "ИЗ Документ.ПродажаРозница КАК Д ГДЕ Д.Проведен И Д.Дата МЕЖДУ &Н И &К УПОРЯДОЧИТЬ ПО Д.Дата УБЫВ")
DAY_LINES_Q = (
    "ВЫБРАТЬ Т.Ссылка.Номер КАК Номер, Т.Товары.Наименование КАК Имя, Т.Кол КАК Кол, Т.ЦенаСум КАК Цена, "
    "Т.СуммаСум КАК Сум ИЗ Документ.ПродажаРозница.Таблица КАК Т "
    "ГДЕ Т.Ссылка.Проведен И Т.Ссылка.Дата МЕЖДУ &Н И &К УПОРЯДОЧИТЬ ПО Т.Ссылка.Дата, Т.НомерСтроки")
DAY_DOCS_F = ["Номер", "Дата", "КК", "КН", "Сот", "Всего", "Ск", "Нал", "Пер", "Клик", "Долг", "Вид", "Савдо"]


def when_text(v):
    try:
        return "%02d.%02d.%04d %02d:%02d" % (v.day, v.month, v.year, v.hour, v.minute)
    except AttributeError:
        return text(v)[:16]


def day_docs(heads, lines):
    """1C ПродажаРозница (bir kun) -> chek yozuvlari (kassa javobi bilan bir xil ko'rinishda)."""
    by_no = {}
    for l in lines:
        by_no.setdefault(text(l.get("Номер")), []).append(
            {"n": text(l.get("Имя")), "q": fnum(l.get("Кол")), "p": fnum(l.get("Цена")), "s": fnum(l.get("Сум"))})
    out = []
    for h in heads:
        no = text(h.get("Номер"))
        kind = kc.KIND_RETURN if int(fnum(h.get("Вид"))) == 1 else kc.KIND_SALE
        total, disc = fnum(h.get("Всего")), fnum(h.get("Ск"))
        if kind == kc.KIND_RETURN:
            disc = 0.0
        cash, card, click = fnum(h.get("Нал")), fnum(h.get("Пер")), fnum(h.get("Клик"))
        due = round(total - disc, 2)
        debt = round(due - cash - card - click, 2)
        client = "" if int(fnum(h.get("Савдо"))) else code_text(h.get("КК"))
        before = fnum(h.get("Долг"))
        after = round(before + (debt if kind == kc.KIND_SALE else -debt), 2)
        out.append({"id": "1c:" + no, "src": "1c", "ok": True, "no": no, "date": when_text(h.get("Дата")), "kind": kind,
                    "doc": "Возврать" if kind == kc.KIND_RETURN else ("ОплатаДолга" if int(fnum(h.get("Вид"))) == 2 else "Продажа"),
                    "cashier": text(h.get("Сот")), "total": total, "discount": disc, "due": due, "cash": cash, "card": card,
                    "click": click, "change": 0, "debt": debt, "client": client, "cname": text(h.get("КН")) if client else "",
                    "cdebt": after if client else 0, "lines": by_no.get(no, [])})
    return out


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
            # 1C: o'zgartirish (kamaytirish/o'chirish) admin paroli bilan
            out["lock"] = bool(c.Константы.ИзмененияВСенсореСпаролем.Получить())
        except Exception:  # noqa: BLE001
            out["lock"] = False
        # pc 12: chek (1C "Чек1" maketi): sarlavha va telefonlar 1C konstantalaridan
        chk = {}
        for key, name in (("t", "Текст"), ("t1", "Текст1"), ("t2", "Текст2"), ("tel", "Тел"), ("tel1", "Тел1"), ("tel2", "Тел2")):
            try:
                chk[key] = text(getattr(c.Константы, name).Получить())
            except Exception:  # noqa: BLE001 - bu bazada konstanta yo'q bo'lsa — bo'sh
                chk[key] = ""
        out["chk"] = chk
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
        try:
            rows = self.query(c, STAFF_Q, ["Код", "Имя", "Пароль", "Уволен", "Админ"])
        except Exception as e:  # noqa: BLE001
            log.warning("kassa: Сотрудники.Админ o'qilmadi (%s)", str(e)[:120])
            rows = self.query(c, STAFF_Q_MIN, ["Код", "Имя", "Пароль", "Уволен"])
        out = []
        for r in rows:
            pw = text(r.get("Пароль"))
            if isinstance(r.get("Пароль"), float):
                pw = code_text(r.get("Пароль"))
            if pw:
                out.append({"code": code_text(r.get("Код")), "name": text(r.get("Имя")), "pw": pw,
                            "fired": bool(r.get("Уволен")), "admin": bool(r.get("Админ"))})
        return out

    def panel(self, c):
        import onec_data

        class _Q:
            def query(_self, q_text, fields, **params):
                return self.query(c, q_text, fields, **params)
        return onec_data.panel(_Q())

    def sales_stats(self, c, days=kp.DAYS):
        """{tovar kodi: oxirgi [days] kundagi sotuv hujjatlari soni} (qaytarishsiz)."""
        end = self.now()
        start = end - dt.timedelta(days=days)
        rows = None
        for q in (SALES_STATS_Q, SALES_STATS_Q.replace(SALES_STATS_NORET, "")):
            try:
                rows = self.query(c, q, ["Код", "Н"], Н1=start, Н2=end)
                break
            except Exception as e:  # noqa: BLE001 - eski bazada "Возврать" qiymati boshqacha bo'lishi mumkin
                log.warning("kassa: sotuv statistikasi: %s", str(e)[:200])
        return {code_text(r.get("Код")): int(fnum(r.get("Н"))) for r in rows or []}

    def new_client(self, c, name, tel):
        """1C'da yangi mijoz (Справочник.Контрагенты, "Клиенты" guruhida) — Admin ilovasidagi bilan bir xil."""
        cat = c.Справочники.Контрагенты
        dup = cat.НайтиПоНаименованию(name, True)
        if dup is not None and not dup.Пустая():
            raise kc.KassaError("Bu nomli mijoz 1C'da bor (kodi %s) — ro'yxatdan tanlang" % code_text(dup.Код))
        obj = cat.СоздатьЭлемент()
        obj.Родитель = cat.Клиенты
        try:
            obj.УстановитьНовыйКод()
        except Exception:  # noqa: BLE001 - avtomatik raqamlashda kerak emas
            pass
        obj.Наименование = name
        if tel:
            obj.Тел = tel
        obj.Записать()
        return code_text(obj.Код)

    def day_sales(self, c, day):
        start = dt.datetime(day.year, day.month, day.day)
        end = start + dt.timedelta(days=1) - dt.timedelta(seconds=1)
        heads = self.query(c, DAY_DOCS_Q, DAY_DOCS_F, Н=start, К=end)
        lines = self.query(c, DAY_LINES_Q, ["Номер", "Имя", "Кол", "Цена", "Сум"], Н=start, К=end) if heads else []
        return day_docs(heads, lines)

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
                f.flush()
                os.fsync(f.fileno())       # elektr o'chsa ham yozilgan sotuv yo'qolmaydi
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


# ---------------------------------------------------------------- fayllar (elektr o'chsa ham buzilmaydi)

def save_json(path, obj):
    """Atomar yozish: avval .tmp, diskka majburan (fsync), keyin almashtirish — yarim yozilgan fayl qolmaydi."""
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, separators=(",", ":"))
        f.flush()
        os.fsync(f.fileno())
    os.replace(tmp, path)


def load_json(path, default):
    try:
        with open(path, "r", encoding="utf-8") as f:
            return json.load(f)
    except (OSError, ValueError):
        return default


def sale_to_json(sale):
    """Tozalangan sotuv (Decimal'lar bilan) -> faylga yoziladigan ko'rinish."""
    out = dict(sale)
    out["lines"] = [dict(l, qty=str(l["qty"]), price=str(l["price"])) for l in sale["lines"]]
    for k in ("discount", "cash", "card", "click"):
        out[k] = str(sale[k])
    out.pop("token", None)
    return out


def sale_from_json(o):
    s = dict(o)
    s["lines"] = [dict(l, qty=kc.qty(l["qty"]), price=kc.money(l["price"])) for l in o.get("lines", [])]
    for k in ("discount", "cash", "card", "click"):
        s[k] = kc.money(o.get(k))
    s.setdefault("note", "")
    s.setdefault("client", "")
    return s


def pw_hash(pw):
    import hashlib
    return hashlib.sha256(("imsoft-kassa:" + str(pw)).encode("utf-8")).hexdigest()


def today_items(items, day):
    """Kunlik hisobot: [day] = "dd.mm.YYYY"."""
    out = {"n": 0, "sale": 0.0, "ret": 0.0, "cash": 0.0, "card": 0.0, "click": 0.0, "debt": 0.0, "disc": 0.0,
           "debt_paid": 0.0, "by": {}}
    for r in items:
        if not str(r.get("date", "")).startswith(day):
            continue
        out["n"] += 1
        sign = -1 if r.get("kind") == kc.KIND_RETURN else 1
        if r.get("kind") == kc.KIND_RETURN:
            out["ret"] += fnum(r.get("total"))
        else:
            out["sale"] += fnum(r.get("total"))
            out["disc"] += fnum(r.get("discount"))
        for k in ("cash", "card", "click"):
            out[k] += sign * fnum(r.get(k))
        d = fnum(r.get("debt"))
        if r.get("kind") == kc.KIND_SALE and d > 0:
            out["debt"] += d
        elif r.get("kind") == kc.KIND_SALE and d < 0:
            out["debt_paid"] += -d
        who = r.get("cashier") or "-"
        b = out["by"].setdefault(who, {"n": 0, "sum": 0.0})
        b["n"] += 1
        b["sum"] += sign * fnum(r.get("due") or r.get("total"))
    for k in ("sale", "ret", "cash", "card", "click", "debt", "disc", "debt_paid"):
        out[k] = round(out[k], 2)
    return out


# ---------------------------------------------------------------- xizmat (bitta 1C oqimi)

QUEUE_FILE = "kassa_queue.json"
SNAP_FILE = "kassa_snap.json.gz"
STAFF_FILE = "kassa_staff.json"
UI_FILE = "kassa_ui.json"
MODE_FILE = "kassa_mode.json"              # {"bridge": true} — 1C ko'prigi ishlatiladi (dastur qayta yonganda ham)
BRIDGE_WAIT = 8                            # birinchi sotuvda ko'prik natijasini kutish
PANEL_FILE = "kassa_panel.json"            # admin tasdiqlagan panel (bo'lmasa — 1C'dagi panel)
SUGGEST_FILE = "kassa_panel_suggest.json"  # haftalik tavsiya
STATS_SEC = 3600                           # sotuv statistikasi soatda bir o'qiladi
SUGGEST_SEC = 7 * 24 * 3600                # tavsiya haftada bir
SALE_WAIT = 45            # sotuv 1C'ga shuncha soniyada yozilmasa — navbatda qoladi, kassir kutmaydi
RETRY_SEC = 60            # 1C ishlamasa navbatni qayta urinish oralig'i
GRANT_SEC = 15 * 60       # admin ruxsati (sozlamalar) amal qilish vaqti
MAX_STATE = 600_000


class Retry(kc.KassaError):
    """1C'ga ulanib bo'lmadi / band — sotuv navbatda qoladi va keyin o'zi yoziladi."""


class UseBridge(Exception):
    """COM ulanishida baza moduli ishlamaydi — sotuvlar 1C ko'prigi (oddiy 1C + kassa_bridge.epf) orqali yoziladi."""


BRIDGE_REJECT = r"(?i)етмаяпти|не удалось провести|недостаточно|topilmadi|не найден"
BRIDGE_TRANSIENT = r"(?i)блокиров|lock|занят|монопольн"


class KassaService:
    def __init__(self, conf, here, open_com, open_app=None, use_app=lambda: False, onec=None, clock=time.time,
                 version="", wake=None, bridge=None):
        """
        open_com() -> COM ulanish obyekti ([.c] va [.close()]); open_app() -> 1C oynasi (yozishda kerak bo'lsa).
        use_app() — sozlamada "oyna orqali" majburiymi ([onec] mode = app). Aks holda avval COM, baza moduli COM'da
        ishlamasa — 1C oynasi orqali (kassa ishlatilayotgan paytda oyna yopilmaydi).
        bridge() -> onec_bridge.Bridge: berilgan bo'lsa, COM'da ishlamaydigan yozuv 1C oynasi o'rniga "ko'prik"
        orqali (1C oddiy ochiladi, sotuvlar to'plab bitta ochilishda yoziladi; kassir kutmaydi).

        Ishonchlilik: har sotuv AVVAL diskdagi navbatga yoziladi (kassa_queue.json, fsync), keyin 1C'ga. 1C ishlamasa
        yoki band bo'lsa — kassir kutmaydi, sotuv navbatda qoladi va 1C tiklanganda o'zi yoziladi (bir marta).
        Monoblokdagi savatlar ham shu kompyuterga saqlanadi (elektr o'chsa monoblok o'chadi, kompyuter qoladi).
        """
        self.conf = conf
        self.here = here
        self.open_com = open_com
        self.open_app = open_app
        self.use_app = use_app
        self.make_bridge = bridge
        self.bridge = None
        self.br_lock = threading.Lock()
        mode = load_json(os.path.join(here, MODE_FILE), {})
        self.bridge_mode = bool(bridge is not None and isinstance(mode, dict) and mode.get("bridge"))
        self.warm_at = 0.0
        self.print_err = ""
        self.updating = ""          # "Dastur yangilanmoqda…" — monoblokda yozuv chiqadi
        self.onec = onec or KassaOneC(conf.title)
        self.clock = clock
        self.version = version
        self.wake = wake
        self.jobs = queue.Queue()
        self.lock = threading.Lock()
        self.qlock = threading.Lock()
        self.snap = None
        self.snap_json = b""
        self.snap_gz = b""
        self.ver = 0
        self.staff = load_json(os.path.join(here, STAFF_FILE), [])
        self.tokens = {}
        self.grants = {}
        self.full_at = 0.0
        self.stock_at = 0.0
        self.seen_at = 0.0
        self.com = None
        self.app = None
        self.done = DoneLog(os.path.join(here, DONE_FILE))
        self.last_error = ""
        self.app_mode = False
        self.busy = False
        self.retry_at = 0.0
        self.update_asked = threading.Event()
        self.stats = load_json(os.path.join(here, "kassa_stats.json"), {})
        if not isinstance(self.stats, dict):
            self.stats = {}
        self.stats_at = None
        self.suggest_try_at = -STATS_SEC
        self.pnl1c = []
        self.pending = load_json(os.path.join(here, QUEUE_FILE), [])
        if not isinstance(self.pending, list):
            self.pending = []
        self._load_snap()

    # --- saqlangan ma'lumot (1C ishlamasa ham kassa ochiladi) ---
    def _load_snap(self):
        try:
            with open(os.path.join(self.here, SNAP_FILE), "rb") as f:
                gz = f.read()
            snap = json.loads(gzip.decompress(gz).decode("utf-8"))
        except (OSError, ValueError, EOFError):
            return
        if isinstance(snap, dict) and isinstance(snap.get("g"), list):
            self.snap = snap
            self.snap_gz = gz
            self.snap_json = gzip.decompress(gz)
            self.ver = int(snap.get("ver") or 0)

    def _save_snap(self, gz):
        try:
            tmp = os.path.join(self.here, SNAP_FILE + ".tmp")
            with open(tmp, "wb") as f:
                f.write(gz)
            os.replace(tmp, os.path.join(self.here, SNAP_FILE))
        except OSError as e:
            log.warning("kassa: ma'lumot nusxasi saqlanmadi: %s", e)

    def _save_queue(self):
        save_json(os.path.join(self.here, QUEUE_FILE), self.pending)

    # --- brauzer tomoni (HTTP oqimlari) ---
    def touch(self):
        self.seen_at = self.clock()

    def call(self, fn, timeout=CALL_TIMEOUT):
        box = {}
        ev = threading.Event()
        self.jobs.put((fn, box, ev))
        if not ev.wait(timeout):
            raise Retry("1C javob bermadi (band)")
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
        return self._packed()

    def _packed(self):
        """Ma'lumot JSON/gzip holida — faqat so'ralganda tayyorlanadi (sotuv paytida emas: kassir kutmasin)."""
        with self.lock:
            if self.snap_json is None and self.snap is not None:
                raw = json.dumps(self.snap, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
                self.snap_json, self.snap_gz = raw, gzip.compress(raw, 6)
            return self.snap_json, self.snap_gz

    def _token(self, s):
        tok = secrets.token_urlsafe(24)
        now = self.clock()
        with self.lock:
            self.tokens = {t: v for t, v in self.tokens.items() if now - v[2] < TOKEN_TTL}
            self.tokens[tok] = (s["code"], s["name"], now)
        return {"ok": True, "token": tok, "name": s["name"], "code": s["code"]}

    def auto_login(self):
        """pc 15: sozlamada "parol so'ralmasin" yoqilgan bo'lsa — tanlangan kassir nomidan darhol kiriladi."""
        self.touch()
        code = str(self.get_ui().get("auto") or "")
        if not code:
            raise kc.KassaError("Parolsiz kirish yoqilmagan")
        if not self.staff:
            self.call(self._refresh_full)
        for s in self.staff:
            if s["code"] == code and not s["fired"]:
                return self._token(s)
        raise kc.KassaError("Parolsiz kirish uchun tanlangan xodim topilmadi")

    def login(self, pw):
        self.touch()
        pw = str(pw or "").strip()
        if not pw:
            raise kc.KassaError("Parolni kiriting")
        if not self.staff:
            self.call(self._refresh_full)
        if not self.staff:
            raise kc.KassaError("1C'da hech bir xodimga parol qo'yilmagan (Сотрудники → Пароль)")
        h = pw_hash(pw)
        for s in self.staff:
            if s["h"] == h:
                if s["fired"]:
                    raise kc.KassaError("Siz bo'shatilgansiz")
                return self._token(s)
        raise kc.KassaError("Bunday parolli xodim yo'q")

    def admin(self, token, pw):
        """1C ИзмененияВСенсореСпаролем / sozlamalar: admin (Сотрудники.Админ) paroli. Bazada admin belgilanmagan
        bo'lsa — istalgan ishlayotgan xodim paroli. @return ruxsat kaliti (15 daqiqa)."""
        who = self.who(token)
        pw = str(pw or "").strip()
        if not pw:
            # pc 32: sozlamada «Sozlamalarga parolsiz kirish» yoqiq (standart) — kirgan kassir nomidan ruxsat
            if self.settings_free():
                return {"ok": True, "name": who[1], "grant": self._new_grant()}
            raise kc.KassaError("Parolni kiriting")
        h = pw_hash(pw)
        any_admin = any(s.get("admin") for s in self.staff)
        for s in self.staff:
            if s["h"] == h and not s["fired"]:
                if s.get("admin") or not any_admin:
                    return {"ok": True, "name": s["name"], "grant": self._new_grant()}
                raise kc.KassaError("Bu xodim admin emas")
        raise kc.KassaError("Parol noto'g'ri")

    def _new_grant(self):
        g = secrets.token_urlsafe(16)
        now = self.clock()
        with self.lock:
            self.grants = {k: v for k, v in self.grants.items() if now - v < GRANT_SEC}
            self.grants[g] = now
        return g

    def settings_free(self):
        """pc 32: sozlamalarga parolsiz kirish (kassa_ui.json "setFree"; yozilmagan bo'lsa — yoqiq)."""
        ui = load_json(os.path.join(self.here, UI_FILE), {})
        return not (isinstance(ui, dict) and ui.get("setFree") is False)

    def granted(self, token, grant):
        self.who(token)
        with self.lock:
            at = self.grants.get(grant or "")
        if at is None or self.clock() - at >= GRANT_SEC:
            raise kc.KassaError("Admin ruxsati tugadi — sozlamalarga qayta kiring")

    def who(self, token):
        with self.lock:
            v = self.tokens.get(token or "")
        if v is None or self.clock() - v[2] >= TOKEN_TTL:
            raise PermissionError("login")
        return v

    def ping(self):
        now = self.clock()
        with self.qlock:
            wait = [p for p in self.pending if p.get("state") != "error"]
            errors = sum(1 for p in self.pending if p.get("state") == "error")
            oldest = max([now - p.get("at", now) for p in wait] or [0])
        return {"ok": True, "v": self.version, "q": len(wait), "qa": int(oldest), "qe": errors,
                "err": self.last_error[:300], "data": self.ver, "ps": 1 if self._suggest_new() else 0, "pe": self.print_err,
                "up": self.updating}

    # --- sotuv: avval navbat (disk), keyin 1C ---
    def sale(self, body):
        """pc 12: javobda v0/v1 — ma'lumot versiyasi sotuvdan oldin va keyin. Monoblok qoldiqni o'zida o'zgartiradi va
        butun tovarlar ro'yxatini qayta yuklamaydi (sekin monoblokda ham sotuvdan keyin kutish yo'q)."""
        t0 = time.time()
        with self.lock:
            v0 = self.ver
        r = dict(self._sale(body))
        with self.lock:
            v1 = self.ver
        ms = int((time.time() - t0) * 1000)
        if ms > 400:
            log.info("kassa: sotuv javobi sekin: %d ms", ms)
        r.update(v0=v0, v1=v1)
        return r

    def _sale(self, body):
        self.touch()
        sale = kc.clean_sale(body)
        emp_code, emp_name, _ = self.who(sale["token"])
        prev = self.done.get(sale["id"])
        if prev is not None:
            return prev
        with self.qlock:
            item = next((p for p in self.pending if p["id"] == sale["id"]), None)
        if item is None:
            res = self._prepare(sale, emp_name)
            item = {"id": sale["id"], "sale": sale_to_json(sale), "emp": emp_code, "res": res, "at": self.clock(),
                    "tries": 0, "state": "wait", "err": "", "print": bool(isinstance(body, dict) and body.get("print1c"))}
            with self.qlock:
                self.pending.append(item)
                self._save_queue()
            self._apply_stock(sale, res["_debt_delta"])
        elif item.get("state") == "error":
            raise kc.KassaError(item.get("err") or "Navbatdagi sotuv xatoli")
        if self.bridge_mode:
            # kassir kutmaydi: chek darhol, 1C (ochiq turgan) 1-2 soniyada fonda yozadi
            self.jobs.put((self._drain_quiet, {}, threading.Event()))
            return self._queued(sale["id"], "1C'ga fonda yoziladi")
        try:
            return self.call(lambda: self._drain(sale["id"]), timeout=SALE_WAIT)
        except Retry as e:
            log.info("kassa: sotuv navbatda qoldi (%s)", e)
            return self._queued(sale["id"], str(e))

    def _queued(self, rid, why):
        with self.qlock:
            item = next((p for p in self.pending if p["id"] == rid), None)
        if item is None:
            prev = self.done.get(rid)
            if prev is not None:
                return prev
            raise kc.KassaError("Sotuv topilmadi")
        r = dict(item["res"])
        r.pop("_debt_delta", None)
        r.update(ok=True, queued=True, no="navbatda", why=why[:200])
        return r

    def _prepare(self, sale, emp_name):
        """1C'siz tekshiruv (narx, to'lov, qoldiq) — xato bo'lsa kassirga darhol aytiladi, navbatga tushmaydi."""
        if self.snap is None:
            raise Retry("Tovarlar ma'lumoti hali yuklanmagan")
        goods = {g["c"]: g for g in self.snap["g"]}
        changed = kc.check_prices(sale["lines"], goods, sale["ptype"], self.snap.get("rate", 0.0))
        if changed:
            raise kc.KassaError("Narx o'zgargan: %s. Kassa yangilandi — savatni qayta tekshiring" % ", ".join(changed)[:200])
        for l in sale["lines"]:
            l["name"] = goods[l["code"]]["n"]
        if not self.snap.get("neg"):
            # snapdagi qoldiq navbatdagi (hali yozilmagan) sotuvlarni allaqachon hisobga olgan
            short = check_stock(sale["lines"], lambda code: goods[code]["s"], sale["kind"])
            if short:
                raise kc.KassaError(short)
        total = kc.cart_total(sale["lines"])
        pay = kc.settle(total, sale["discount"], sale["cash"], sale["card"], sale["click"], bool(sale["client"]), sale["kind"])
        debt_delta = float(pay["debt"]) * (1 if sale["kind"] == kc.KIND_SALE else -1)
        old = [x for x in self.snap.get("cl", []) if x[0] == sale["client"]] if sale["client"] else []
        now = dt.datetime.fromtimestamp(self.clock())
        return {
            "id": sale["id"], "ok": True, "no": "", "date": now.strftime("%d.%m.%Y %H:%M"), "kind": sale["kind"],
            "doc": pay["doc"], "cashier": emp_name, "total": kc.jnum(pay["total"]), "discount": kc.jnum(pay["discount"]),
            "due": kc.jnum(pay["due"]), "cash": kc.jnum(pay["cash"]), "card": kc.jnum(pay["card"]),
            "click": kc.jnum(pay["click"]), "change": kc.jnum(pay["change"]), "debt": kc.jnum(pay["debt"]),
            "client": sale["client"], "cname": old[0][1] if old else "",
            "cdebt": round(old[0][3] + debt_delta, 2) if old else 0,
            "lines": [{"n": l["name"], "q": kc.jnum(l["qty"]), "p": kc.jnum(l["price"]),
                       "s": kc.jnum(kc.line_sum(l["price"], l["qty"]))} for l in sale["lines"]],
            "_debt_delta": debt_delta,
        }

    def _drain_quiet(self):
        try:
            self._drain()
        except kc.KassaError as e:
            log.info("kassa: navbat: %s", e)

    def _drain(self, target=None):
        """Navbatni tartib bilan 1C'ga yozadi (faqat 1C oqimida). [target] yozilsa — natijasi, aks holda Retry."""
        now = self.clock()
        if self.retry_at and now < self.retry_at and target is not None:
            raise Retry(self.last_error or "1C hozircha ishlamayapti")
        while True:
            if self.bridge_mode:
                return self._drain_bridge(target)
            with self.qlock:
                item = next((p for p in self.pending if p.get("state") != "error"), None)
            if item is None:
                break
            self.busy = True
            try:
                res = self._write_item(item)
            except UseBridge:
                continue
            except Retry as e:
                self.retry_at = self.clock() + RETRY_SEC
                self.last_error = str(e)[:300]
                with self.qlock:
                    item["tries"] = item.get("tries", 0) + 1
                    item["err"] = str(e)[:300]
                    self._save_queue()
                raise
            except kc.KassaError as e:
                # 1C rad etdi (masalan qoldiq yetmaydi): yangi sotuv bo'lsa kassirga aytiladi va navbatdan olinadi,
                # oldingi (fonda) sotuv bo'lsa — "xato" bo'lib qoladi, admin ko'rib chiqadi
                with self.qlock:
                    if item["id"] == target:
                        self.pending.remove(item)
                    else:
                        item.update(state="error", err=str(e)[:300], tries=item.get("tries", 0) + 1)
                    self._save_queue()
                if item["id"] == target:
                    self._apply_stock(sale_from_json(item["sale"]), -item["res"]["_debt_delta"], undo=True)
                    raise
                continue
            finally:
                self.busy = False
            self.retry_at = 0.0
            self.last_error = ""
            if item["id"] == target:
                return res
        if target is not None:
            prev = self.done.get(target)
            if prev is not None:
                return prev
            raise Retry("navbatda")
        return None

    def _done(self, item, number, when):
        res = dict(item["res"])
        res.pop("_debt_delta", None)
        res.update(no=number, date=when, ok=True)
        res["ts"] = self.clock()
        self.done.add(res)
        with self.qlock:
            if item in self.pending:
                self.pending.remove(item)
            self._save_queue()
        return res

    def _job(self, item):
        sale = sale_from_json(item["sale"])
        pay = kc.settle(kc.cart_total(sale["lines"]), sale["discount"], sale["cash"], sale["card"], sale["click"],
                        bool(sale["client"]), sale["kind"])
        return {"id": item["id"], "kind": sale["kind"], "ptype": sale["ptype"], "emp": item.get("emp", ""),
                "client": sale["client"], "total": pay["total"], "discount": pay["discount"], "cash": pay["cash"],
                "card": pay["card"], "click": pay["click"], "doc": pay["doc"], "note": sale["note"],
                "lines": [{"code": l["code"], "qty": l["qty"], "price": l["price"],
                           "sum": kc.line_sum(l["price"], l["qty"])} for l in sale["lines"]],
                "print": bool(item.get("print"))}

    def _bridge_fail(self, item, msg, target):
        """1C hujjatni yozmadi: yangi sotuv — kassirga xato; fondagisi — "xato" (admin ko'radi)."""
        with self.qlock:
            if item["id"] == target:
                self.pending.remove(item)
            else:
                item.update(state="error", err=msg[:300], tries=item.get("tries", 0) + 1)
            self._save_queue()
        if item["id"] == target:
            self._apply_stock(sale_from_json(item["sale"]), -item["res"]["_debt_delta"], undo=True)

    def _br(self):
        with self.br_lock:
            if self.bridge is None:
                self.bridge = self.make_bridge()
            return self.bridge

    def bridge_rpc(self):
        """pc 30: telefondan 1C'ga yozish uchun shu ko'prik 1C'i (alohida 1C ochilmaydi). Ko'prik yopiq bo'lsa —
        kassa aylanishi uni ochadi, shu yerda tayyor bo'lishi kutiladi. @raise onec_bridge.RpcError"""
        import onec_bridge
        self._set_bridge_mode()
        return onec_bridge.Rpc(self._br()).wait_ready()

    def _print_errs(self, br):
        try:
            errs = br.print_errors()
        except (AttributeError, OSError):
            errs = []
        if errs:
            self.print_err = "%s %s" % (time.strftime("%H:%M"), errs[-1])
            log.warning("kassa: 1C chekni chiqarmadi: %s", errs[-1])

    def _set_bridge_mode(self):
        if not self.bridge_mode:
            self.bridge_mode = True
            try:
                save_json(os.path.join(self.here, MODE_FILE), {"bridge": True})
            except OSError:
                pass

    def _drain_bridge(self, target=None):
        """Navbatdagi sotuvlar ochiq turgan 1C orqali yoziladi (onec_bridge, 1-2 soniya)."""
        br = self._br()
        # oldingi ishga tushirishdan (kompyuter o'chib qolgan bo'lsa) qolgan natijalar — avval ular
        res, wip = br.leftovers()
        with self.qlock:
            items = [p for p in self.pending if p.get("state") != "error"]
        out = {}
        todo = []
        for it in items:
            if it["id"] in res:
                out[it["id"]] = res[it["id"]]
                br.forget(it["id"])
            elif it["id"] in wip:
                out[it["id"]] = ("UNKNOWN", "1C hujjatni yozayotganda to'xtagan — 1C'da tekshiring (yozilgan bo'lishi mumkin)")
                br.forget(it["id"])
            else:
                todo.append(it)
        err = None
        if todo:
            self.busy = True
            try:
                out.update(br.run([self._job(it) for it in todo], timeout=BRIDGE_WAIT if target else 20))
                self._print_errs(br)
            except (RuntimeError, OSError) as e:
                err = str(e)[:300]
            finally:
                self.busy = False
        target_res = None
        target_err = None
        for it in items:
            r = out.get(it["id"])
            if r is None:
                if err:                       # 1C ochilmadi; aks holda — 1C hali yozmoqda, keyingi aylanishda olinadi
                    with self.qlock:
                        it["tries"] = it.get("tries", 0) + 1
                        it["err"] = err[:300]
                continue
            if r[0] == "OK":
                done = self._done(it, r[1], r[2])
                if it["id"] == target:
                    target_res = done
            elif r[0] == "ERR" and re.search(BRIDGE_TRANSIENT, r[1]) and not re.search(BRIDGE_REJECT, r[1]):
                with self.qlock:
                    it["tries"] = it.get("tries", 0) + 1
                    it["err"] = r[1][:300]
                err = err or r[1]
            elif r[0] == "UNKNOWN":
                # yozilgan bo'lishi mumkin — qayta yozilmaydi, qoldiq qaytarilmaydi; admin 1C'da tekshiradi
                with self.qlock:
                    it.update(state="error", err=r[1][:300], tries=it.get("tries", 0) + 1)
            else:
                msg = "1C rad etdi: %s" % r[1]
                self._bridge_fail(it, msg, target)
                if it["id"] == target:
                    target_err = msg
        with self.qlock:
            self._save_queue()
        if err:
            self.retry_at = self.clock() + RETRY_SEC
            self.last_error = err
        else:
            self.retry_at = 0.0
            self.last_error = ""
        if target is None:
            return None
        if target_res is not None:
            return target_res
        if target_err is None and target in out and out[target][0] == "UNKNOWN":
            return self._queued(target, out[target][1])
        if target_err is not None:
            raise kc.KassaError(target_err)
        prev = self.done.get(target)
        if prev is not None:
            return prev
        raise Retry(err or "navbatda")

    def _write_item(self, item):
        sale = sale_from_json(item["sale"])
        pay = kc.settle(kc.cart_total(sale["lines"]), sale["discount"], sale["cash"], sale["card"], sale["click"],
                        bool(sale["client"]), sale["kind"])
        number, when = self._write_doc(sale, pay, item.get("emp", ""))
        return self._done(item, number, when)

    def queue_list(self):
        with self.qlock:
            return [{"id": p["id"], "state": p.get("state", "wait"), "err": p.get("err", ""), "tries": p.get("tries", 0),
                     "date": p["res"].get("date", ""), "total": p["res"].get("total", 0), "kind": p["res"].get("kind"),
                     "cashier": p["res"].get("cashier", ""), "lines": len(p["res"].get("lines", []))} for p in self.pending]

    def queue_op(self, op, rid):
        with self.qlock:
            item = next((p for p in self.pending if p["id"] == rid), None)
            if item is None:
                raise kc.KassaError("Navbatda bunday sotuv yo'q")
            if op == "retry":
                item.update(state="wait", err="")
            elif op == "delete":
                self.pending.remove(item)
            else:
                raise kc.KassaError("Amal noto'g'ri")
            self._save_queue()
        if op == "delete":
            self._apply_stock(sale_from_json(item["sale"]), -item["res"]["_debt_delta"], undo=True)
        self.retry_at = 0.0
        return {"ok": True}

    def sales(self, token, day=None, with_1c=True):
        """pc 13: kun savdolari (kassa + 1C'dagi boshqa savdolar) — istalganining chekini qayta chiqarish uchun."""
        self.who(token)
        try:
            d = dt.datetime.strptime(day, "%Y-%m-%d") if day else dt.datetime.fromtimestamp(self.clock())
        except ValueError:
            raise kc.KassaError("Sana noto'g'ri")
        key = d.strftime("%d.%m.%Y")
        items = [dict(r, src="kassa") for r in self.done.items.values() if str(r.get("date", "")).startswith(key)]
        with self.qlock:
            for p in self.pending:
                if str(p["res"].get("date", "")).startswith(key):
                    r = dict(p["res"])
                    r.pop("_debt_delta", None)
                    r.update(src="kassa", queued=True, no="navbatda" if p.get("state") != "error" else "xato")
                    items.append(r)
        err = ""
        if with_1c:
            try:
                have = {text(r.get("no")) for r in items}
                items += [r for r in self.call(lambda: self.onec.day_sales(self._com(), d), timeout=40)
                          if r["no"] not in have]
            except (kc.KassaError, Exception) as e:  # noqa: BLE001 - 1C'siz ham kassa savdolari ko'rinadi
                err = str(e)[:200]
        items.sort(key=lambda r: (r.get("date", "")[6:10], r.get("date", "")[3:5], r.get("date", "")[:2],
                                  r.get("date", "")[11:]), reverse=True)
        return {"ok": True, "day": d.strftime("%Y-%m-%d"), "items": items[:500] if with_1c else items, "e1c": err,
                "all": items}

    def print_1c(self, token, rid="", no="", date=""):
        """pc 15: chekni 1C'ning O'ZI chiqaradi (bazadagi ПечатьНажатие_1 — kompyuterdagi chek printeriga)."""
        self.who(token)
        if rid:
            r = self.done.get(rid)
            if r is None:
                with self.qlock:
                    pend = any(p["id"] == rid for p in self.pending)
                raise kc.KassaError("Sotuv hali 1C'ga yozilmagan — bir oz kuting" if pend else "Sotuv topilmadi")
            no, date = r.get("no", ""), r.get("date", "")
        no, date = str(no or "").strip(), str(date or "").strip()[:10]
        if not no or not re.match(r"^\d\d\.\d\d\.\d{4}$", date):
            raise kc.KassaError("Chek uchun hujjat raqami yo'q")
        if self.make_bridge is None:
            raise kc.KassaError("1C ko'prigi sozlanmagan")
        import onec_bridge
        job = onec_bridge.print_job("pr" + secrets.token_hex(6), no, date)

        def go():
            out = self._br().run([job], timeout=15)
            self._print_errs(self._br())
            r = out.get(job["id"])
            if r is None:
                return {"ok": True, "wait": True}
            if r[0] != "OK":
                raise kc.KassaError("1C: %s" % r[1][:200])
            return {"ok": True}
        try:
            return self.call(go, timeout=40)
        except (RuntimeError, OSError) as e:
            raise kc.KassaError(str(e)[:200])

    def report(self, token=None, day=None):
        """pc 20: KASSA YOPISH (kun hisoboti) — 1C'dagi HAMMA savdolar (kassa + 1C sensor oynasi) bo'yicha; 1C o'qilmasa —
        kassaning o'z yozuvlari. day: "YYYY-MM-DD" (bo'sh — bugun)."""
        if token is None:                                   # faqat kassa yozuvlari (day: "dd.mm.YYYY")
            key = day or dt.datetime.fromtimestamp(self.clock()).strftime("%d.%m.%Y")
            out = today_items(list(self.done.items.values()), key)
            out["day"] = key
            out.update(self.ping())
            return out
        r = self.sales(token, day)
        d = dt.datetime.strptime(r["day"], "%Y-%m-%d")
        key = d.strftime("%d.%m.%Y")
        out = today_items(r["all"], key)
        out.update(self.ping())
        out.update(day=key, iso=r["day"], e1c=r["e1c"], src="1c" if not r["e1c"] else "kassa")
        return out

    def add_client(self, token, name, tel=""):
        """pc 27: kassada yangi mijoz qo'shish — 1C'ga darhol yoziladi va ro'yxatga qo'shiladi."""
        self.who(token)
        name = " ".join(str(name or "").split())[:150]
        tel = re.sub(r"[^0-9+ ]", "", str(tel or ""))[:30].strip()
        if len(name) < 2:
            raise kc.KassaError("Mijoz ismini yozing")
        if self.snap is not None:
            low = name.lower()
            for row in self.snap.get("cl", []):
                if str(row[1]).strip().lower() == low:
                    raise kc.KassaError("Bu nomli mijoz bor (kodi %s) — ro'yxatdan tanlang" % row[0])

        def go():
            code = self.onec.new_client(self._com(), name, tel)
            with self.lock:
                snap = dict(self.snap or {})
            row = [code, name, tel, 0.0, 0.0, 0]
            snap["cl"] = list(snap.get("cl", [])) + [row]
            self._publish(snap, save=True)
            return {"ok": True, "client": row}
        return self.call(go, timeout=40)

    def print_text(self, token, title, rows):
        """Hisobotni kompyuterdagi chek printeriga (1C orqali) chiqarish."""
        self.who(token)
        if self.make_bridge is None:
            raise kc.KassaError("1C ko'prigi sozlanmagan")
        import onec_bridge
        lines = [str(title or "")[:60]] + ["%s|%s" % (str(a)[:40], str(b)[:30]) for a, b in (rows or [])[:80]]
        job = onec_bridge.text_job("tx" + secrets.token_hex(6), lines)

        def go():
            out = self._br().run([job], timeout=15)
            self._print_errs(self._br())
            r = out.get(job["id"])
            if r is not None and r[0] != "OK":
                raise kc.KassaError("1C: %s" % r[1][:200])
            return {"ok": True, "wait": r is None}
        return self.call(go, timeout=40)

    # --- monoblok holati (savatlar) — elektr o'chsa ham shu yerda qoladi ---
    def state_path(self, k):
        k = "".join(ch for ch in str(k or "1") if ch.isalnum())[:16] or "1"
        return os.path.join(self.here, "kassa_state_%s.json" % k)

    def get_state(self, token, k):
        self.who(token)
        return load_json(self.state_path(k), {})

    def put_state(self, token, k, state):
        self.who(token)
        raw = json.dumps(state, ensure_ascii=False)
        if len(raw) > MAX_STATE or not isinstance(state, dict):
            raise kc.KassaError("Holat juda katta")
        save_json(self.state_path(k), state)
        return {"ok": True}

    # --- interfeys sozlamalari (hamma oyna va monobloklar uchun bitta) ---
    def get_ui(self):
        return load_json(os.path.join(self.here, UI_FILE), {})

    def put_ui(self, token, grant, ui):
        self.granted(token, grant)
        if not isinstance(ui, dict) or len(json.dumps(ui)) > 200_000:
            raise kc.KassaError("Sozlama noto'g'ri")
        save_json(os.path.join(self.here, UI_FILE), ui)
        return {"ok": True}

    def ask_update(self, token, grant):
        self.granted(token, grant)
        self.update_asked.set()
        if self.wake is not None:
            self.wake()
        return {"ok": True}

    def wait_idle(self, sec):
        """Dastur yangilanib qayta ishga tushishidan oldin: 1C'ga yozilayotgan sotuv tugasin."""
        end = time.time() + sec
        while self.busy and time.time() < end:
            time.sleep(0.5)

    # --- 1C oqimi ---
    def _com(self):
        if self.com is None:
            try:
                self.com = self.open_com()
            except SystemExit as e:
                raise Retry(str(e)[:250])
        return self.com.c

    def _drop(self):
        for o in (self.com,):
            if o is not None:
                try:
                    o.close()
                except Exception:  # noqa: BLE001
                    pass
        self.com = None

    def _publish(self, snap, save=False):
        snap = dict(snap)
        self.ver = max(self.ver + 1, int(self.clock() * 1000))      # doim o'sadi (bir millisekundda ikki marta ham)
        snap["ver"] = self.ver
        with self.lock:
            self.snap = snap
            self.snap_json = self.snap_gz = None
        if save:
            self._save_snap(self._packed()[1])

    def _pending_delta(self):
        """Navbatdagi (1C'ga hali yozilmagan) sotuvlar qoldiqqa ta'siri — 1C'dan yangilaganda ham hisobga olinadi."""
        d = {}
        with self.qlock:
            items = list(self.pending)
        for p in items:
            sign = -1 if p["sale"].get("kind") == kc.KIND_SALE else 1
            for l in p["sale"].get("lines", []):
                d[l["code"]] = d.get(l["code"], 0.0) + sign * fnum(l["qty"])
        return d

    def _with_pending(self, goods):
        d = self._pending_delta()
        if not d:
            return goods
        return [dict(g, s=round(g["s"] + d[g["c"]], 3)) if g["c"] in d else g for g in goods]

    def _refresh_full(self):
        c = self._com()
        snap, staff = self.onec.snapshot(c)
        self.staff = [{"code": s["code"], "name": s["name"], "h": pw_hash(s["pw"]), "fired": s["fired"],
                       "admin": s.get("admin", False)} for s in staff]
        try:
            save_json(os.path.join(self.here, STAFF_FILE), self.staff)
        except OSError:
            pass
        snap["g"] = self._with_pending(snap["g"])
        if self.stats_at is None or self.clock() - self.stats_at >= STATS_SEC:
            self.stats_at = self.clock()
            try:
                stats = self.onec.sales_stats(c)
                if stats:
                    self.stats = stats
                    save_json(os.path.join(self.here, "kassa_stats.json"), stats)
            except Exception as e:  # noqa: BLE001 - statistikasiz ham kassa ishlaydi
                log.warning("kassa: statistika: %s", str(e)[:200])
        self.pnl1c = snap.get("pnl") or []
        self._decorate(snap)
        self._publish(snap, save=True)
        self.full_at = self.stock_at = self.clock()
        log.info("kassa: ma'lumot yangilandi (%d tovar, %d mijoz)", len(snap["g"]), len(snap["cl"]))

    def _decorate(self, snap):
        """Mashhurlik (qidiruvda yuqorida) va tasdiqlangan panel."""
        st = self.stats
        snap["g"] = [dict(g, f=st[g["c"]]) if st.get(g["c"]) else g for g in snap.get("g", [])]
        own = load_json(os.path.join(self.here, PANEL_FILE), {})
        if isinstance(own, dict) and isinstance(own.get("pnl"), list) and own["pnl"]:
            snap["pnl"] = own["pnl"]
            snap["pnls"] = "kassa"
        else:
            snap["pnls"] = "1c"
        return snap

    # --- panel tavsiyasi ---
    def _suggest_new(self):
        sug = load_json(os.path.join(self.here, SUGGEST_FILE), {})
        if not isinstance(sug, dict) or not sug.get("pnl") or sug.get("seen"):
            return False
        own = load_json(os.path.join(self.here, PANEL_FILE), {})
        return not (isinstance(own, dict) and own.get("at", 0) >= sug.get("at", 0))

    def _make_suggest(self):
        if self.snap is None:
            raise Retry("Tovarlar ma'lumoti hali yuklanmagan")
        if not self.stats:
            self.stats_at = self.clock()
            self.stats = self.onec.sales_stats(self._com())
            save_json(os.path.join(self.here, "kassa_stats.json"), self.stats)
        if not self.stats:
            raise kc.KassaError("Oxirgi %d kunda sotuv topilmadi — tavsiya qilib bo'lmaydi" % kp.DAYS)
        res = kp.suggest(self.snap.get("g", []), self.stats, self.snap.get("b", {}))
        res.update(at=self.clock(), days=kp.DAYS, seen=False)
        save_json(os.path.join(self.here, SUGGEST_FILE), res)
        log.info("kassa: panel tavsiyasi tayyor (%s)", ", ".join("%s %d" % (m["t"], m["n"]) for m in res["sec"]))
        return res

    def panel_op(self, token, grant, op):
        self.granted(token, grant)
        sug_path = os.path.join(self.here, SUGGEST_FILE)
        own_path = os.path.join(self.here, PANEL_FILE)
        if op == "make":
            self.call(self._make_suggest)
        elif op == "approve":
            sug = load_json(sug_path, {})
            if not isinstance(sug, dict) or not sug.get("pnl"):
                raise kc.KassaError("Tavsiya yo'q")
            save_json(own_path, {"pnl": sug["pnl"], "at": self.clock(), "sec": sug.get("sec", [])})
            sug["seen"] = True
            save_json(sug_path, sug)
            self._repanel()
        elif op == "reset":
            try:
                os.remove(own_path)
            except FileNotFoundError:
                pass
            self._repanel()
        elif op == "dismiss":
            sug = load_json(sug_path, {})
            if isinstance(sug, dict) and sug:
                sug["seen"] = True
                save_json(sug_path, sug)
        elif op != "get":
            raise kc.KassaError("Amal noto'g'ri")
        sug = load_json(sug_path, {})
        own = load_json(own_path, {})
        return {"ok": True, "sug": sug if isinstance(sug, dict) and sug.get("pnl") else None,
                "src": "kassa" if isinstance(own, dict) and own.get("pnl") else "1c",
                "own_sec": own.get("sec", []) if isinstance(own, dict) else []}

    def _repanel(self):
        snap = dict(self.snap or {})
        if not snap:
            return
        snap["pnl"] = self.pnl1c or snap.get("pnl", [])
        self._decorate(snap)
        self._publish(snap, save=True)

    def _refresh_stock(self):
        c = self._com()
        k = self.onec.consts(c)
        st = {code_text(r.get("Код")): fnum(r.get("Кол")) for r in self.onec.stock(c, k["store_ref"])}
        snap = dict(self.snap or {})
        d = self._pending_delta()
        changed = abs(fnum(snap.get("rate")) - k["rate"]) > 1e-9 or bool(snap.get("neg")) != k["neg"]
        goods = []
        for g in snap.get("g", []):
            s = round(st.get(g["c"], 0.0) + d.get(g["c"], 0.0), 3)
            if s != g["s"]:
                g = dict(g, s=s)
                changed = True
            goods.append(g)
        self.stock_at = self.clock()
        if changed:
            snap.update(g=goods, rate=k["rate"], neg=k["neg"])
            self._publish(snap)

    def _apply_stock(self, sale, debt_delta, undo=False):
        """Sotuv qabul qilinishi bilan qoldiq va mijoz qarzi xotirada o'zgaradi (1C'ga yozilishini kutmaydi)."""
        snap = dict(self.snap or {})
        sign = -1 if sale["kind"] == kc.KIND_SALE else 1
        if undo:
            sign = -sign
        delta = {}
        for l in sale["lines"]:
            delta[l["code"]] = delta.get(l["code"], 0.0) + sign * float(l["qty"])
        snap["g"] = [dict(g, s=round(g["s"] + delta[g["c"]], 3)) if g["c"] in delta else g for g in snap.get("g", [])]
        if sale["client"] and debt_delta:
            snap["cl"] = [[x[0], x[1], x[2], round(x[3] + debt_delta, 2), x[4], x[5]] if x[0] == sale["client"] else x
                          for x in snap.get("cl", [])]
        self._publish(snap)

    def _write_doc(self, sale, pay, emp_code):
        import onec_write
        if self.make_bridge is not None and self.use_app():
            self._set_bridge_mode()
            raise UseBridge()
        if self.open_app is not None and (self.app_mode or self.use_app()):
            return self._write_app(sale, pay, emp_code)
        try:
            return self.onec.write_sale(self._com(), sale, pay, emp_code)
        except kc.KassaError:
            raise
        except Exception as e:  # noqa: BLE001
            if self.make_bridge is not None and onec_write.needs_app(str(e)):
                log.warning("kassa: COM ulanishida 1C moduli ishlamadi (%s) — 1C ko'prigi orqali", str(e)[:300])
                self._set_bridge_mode()
                raise UseBridge()
            if self.open_app is not None and onec_write.needs_app(str(e)):
                log.warning("kassa: COM ulanishida 1C moduli ishlamadi (%s) — 1C oynasi orqali", str(e)[:300])
                self.app_mode = True
                try:
                    return self._write_app(sale, pay, emp_code)
                except Retry as e2:
                    self.app_mode = False      # keyingi safar yana COM'dan boshlanadi (masalan tuzatilgan bo'lsa)
                    raise Retry("%s | COM: %s" % (str(e2)[:220], str(e)[:120]))
            log.warning("kassa: 1C yozuv xatosi: %s", str(e)[:300])
            self._drop()
            raise Retry("1C: %s" % onec_write.explain_1c_error(str(e))[:250])

    def _write_app(self, sale, pay, emp_code):
        import onec_write
        if self.app is None:
            self.app = self.open_app()
        try:
            self.app.connect()
        except SystemExit as e:
            log.warning("kassa: 1C oynasi ochilmadi: %s", e)
            raise Retry(str(e)[:300])
        try:
            return self.onec.write_sale(self.app.c, sale, pay, emp_code)
        except kc.KassaError:
            raise
        except Exception as e:  # noqa: BLE001
            msg = onec_write.explain_1c_error(str(e))
            # 1C o'zi rad etgan (masalan "... етмаяпти" - qoldiq yetmaydi) - qayta urinish foydasiz
            if re.search(r"(?i)етмаяпти|не удалось провести|недостаточно", str(e)):
                raise kc.KassaError("1C rad etdi: %s" % msg[:250])
            try:
                self.app.quit()
            except Exception:  # noqa: BLE001
                pass
            raise Retry("1C: %s" % msg[:250])

    def tick(self):
        """Navbat bo'sh paytda: navbatdagi sotuvlarni yozish va (brauzer ochiq bo'lsa) ma'lumotni yangilab turish."""
        now = self.clock()
        if self.bridge_mode:
            # 1C ko'prigi doim ochiq: dastur ishlayotganini bildiradi, 1C yopilgan bo'lsa — oldindan ochib qo'yadi
            br = self._br()
            br.heartbeat()
            try:
                br.hide()                     # 1C oynasi kompyuterda oldinga chiqib xalaqit bermasin
            except (AttributeError, OSError):
                pass
            if not br.alive() and now >= self.warm_at:
                self.warm_at = now + RETRY_SEC
                try:
                    br.ensure_running()
                except (RuntimeError, OSError) as e:
                    self.last_error = str(e)[:300]
                    log.warning("kassa: 1C ko'prigi: %s", self.last_error)
        if self.pending and (not self.retry_at or now >= self.retry_at):
            try:
                self._drain()
            except kc.KassaError:
                pass
        if now - self.seen_at > ACTIVE_SEC:
            # hech kim ishlatmayapti — 1C ulanishlari yopiladi (litsenziya bo'shaydi)
            if self.com is not None:
                self._drop()
            if self.app is not None and not self.pending:
                try:
                    self.app.quit()
                except Exception:  # noqa: BLE001
                    pass
            return
        if self.snap is None or now - self.full_at >= self.conf.refresh_min * 60:
            self._refresh_full()
        elif now - self.stock_at >= STOCK_SEC:
            self._refresh_stock()
        sug = load_json(os.path.join(self.here, SUGGEST_FILE), {})
        due = not isinstance(sug, dict) or not sug.get("pnl") or now - sug.get("at", 0) >= SUGGEST_SEC
        if self.stats and self.snap is not None and due and now - self.suggest_try_at >= STATS_SEC:
            self.suggest_try_at = now
            try:
                self._make_suggest()
            except kc.KassaError as e:
                log.info("kassa: panel tavsiyasi: %s", e)

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
                box["e"] = Retry(str(e)[:250])
                self._drop()
            except Exception as e:  # noqa: BLE001
                log.exception("kassa: 1C xatosi")
                self._drop()
                box["e"] = Retry("1C: %s" % str(e)[:250])
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
        # pc 12: monoblok kompyuterdan oldin yonsa — kassa sahifasi kompyuter javob berguncha kutadi (15 daqiqagacha),
        # aks holda brauzer "sayt ochilmadi" bo'lib qolardi
        "> \"%S%\" echo @echo off",
        ">> \"%S%\" echo set n=0",
        ">> \"%S%\" echo :w",
        ">> \"%S%\" echo if not exist \"%%SystemRoot%%\\System32\\curl.exe\" goto go",
        ">> \"%S%\" echo curl -s -o nul -m 3 \"%URL%api/ping\" ^&^& goto go",
        ">> \"%S%\" echo set /a n+=1",
        ">> \"%S%\" echo if %%n%% LSS 300 (timeout /t 3 /nobreak ^>nul ^& goto w)",
        ">> \"%S%\" echo :go",
        ">> \"%S%\" echo start \"\" \"%B%\" --kiosk --kiosk-printing --edge-kiosk-type=fullscreen --no-first-run "
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
                return self._json(200, service.ping())
            if path == "/api/sales":
                params = dict(p.split("=", 1) for p in query.split("&") if "=" in p)
                try:
                    return self._json(200, service.sales(urllib.parse.unquote(params.get("t", "")), params.get("d") or None))
                except PermissionError:
                    return self._json(401, {"ok": False, "e": "login"})
                except kc.KassaError as e:
                    return self._json(200, {"ok": False, "e": str(e)})
            if path in ("/api/state", "/api/report", "/api/queue", "/api/ui"):
                params = dict(p.split("=", 1) for p in query.split("&") if "=" in p)
                tok = urllib.parse.unquote(params.get("t", ""))
                try:
                    if path == "/api/ui":
                        return self._json(200, service.get_ui())
                    service.who(tok)
                    if path == "/api/state":
                        return self._json(200, service.get_state(tok, params.get("k", "1")))
                    if path == "/api/report":
                        try:
                            return self._json(200, service.report(tok, params.get("d") or None))
                        except kc.KassaError as e:
                            return self._json(200, {"ok": False, "e": str(e)})
                    return self._json(200, {"ok": True, "items": service.queue_list()})
                except PermissionError:
                    return self._json(401, {"ok": False, "e": "login"})
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
                    if body.get("auto"):
                        return self._json(200, service.auto_login())
                    return self._json(200, service.login(body.get("pw")))
                if path == "/api/client":
                    return self._json(200, service.add_client(body.get("token"), body.get("name"), body.get("tel")))
                if path == "/api/print_text":
                    rows = body.get("rows") if isinstance(body.get("rows"), list) else []
                    return self._json(200, service.print_text(body.get("token"), body.get("title"),
                                                              [r for r in rows if isinstance(r, list) and len(r) == 2]))
                if path == "/api/print":
                    return self._json(200, service.print_1c(body.get("token"), body.get("id") or "", body.get("no") or "",
                                                            body.get("date") or ""))
                if path == "/api/sale":
                    return self._json(200, service.sale(body))
                if path == "/api/admin":
                    return self._json(200, service.admin(body.get("token"), body.get("pw")))
                if path == "/api/refresh":
                    service.who(body.get("token"))
                    service.call(service._refresh_full)
                    return self._json(200, {"ok": True})
                if path == "/api/state":
                    return self._json(200, service.put_state(body.get("token"), body.get("k"), body.get("state")))
                if path == "/api/ui":
                    return self._json(200, service.put_ui(body.get("token"), body.get("grant"), body.get("ui")))
                if path == "/api/panel":
                    return self._json(200, service.panel_op(body.get("token"), body.get("grant"), body.get("op")))
                if path == "/api/update":
                    return self._json(200, service.ask_update(body.get("token"), body.get("grant")))
                if path == "/api/queue":
                    service.granted(body.get("token"), body.get("grant"))
                    return self._json(200, service.queue_op(body.get("op"), body.get("id")))
            except PermissionError:
                return self._json(401, {"ok": False, "e": "login"})
            except kc.KassaError as e:
                return self._json(200, {"ok": False, "e": str(e), "retry": isinstance(e, Retry)})
            except Exception as e:  # noqa: BLE001 - kassa sahifasi hech qachon "qotib" qolmasin
                log.exception("kassa http")
                return self._json(200, {"ok": False, "e": "Kompyuterdagi xato: %s" % str(e)[:200], "retry": True})
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


def start(cfg, here, cloud=None, onec_cls=None, app_cls=None, wake=None):
    """onec_export.py chaqiradi. @return xizmat yoki None (o'chiq). Port band bo'lsa — bo'shaguncha fonda kutadi."""
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

    try:
        import selfupdate
        version = str(selfupdate.local_version(here))
    except Exception:  # noqa: BLE001
        version = ""
    def bridge():
        import onec_bridge
        return onec_bridge.Bridge(cfg.conn_string(), here, onec_bridge.find_exe())

    svc = KassaService(conf, here, open_com, (lambda: app_cls(cfg)) if app_cls else None, use_app,
                       version=version, wake=wake, bridge=bridge)
    url = "http://%s:%d" % (lan_ip(), conf.port)
    svc.url = url
    threading.Thread(target=svc.run, daemon=True, name="kassa-1c").start()

    def serve():
        # pc 18: dastur yangilanib qayta ishga tushganda eski nusxa portni hali bo'shatmagan bo'lishi mumkin —
        # bir marta urinib to'xtamaymiz (aks holda monoblokda "ERR_CONNECTION_REFUSED"), port bo'shaguncha qayta urinamiz
        delay, warned = 1.0, False
        while True:
            try:
                httpd = ThreadingHTTPServer(("0.0.0.0", conf.port), make_handler(svc, os.path.join(here, STATIC_DIR)))
                break
            except OSError as e:
                if not warned:
                    log.warning("kassa oynasi: %d-port hozircha band (%s) — qayta urinilmoqda", conf.port, e)
                    warned = True
                time.sleep(delay)
                delay = min(delay * 2, 15.0)
        httpd.daemon_threads = True
        ensure_firewall(conf.port)
        log.info("kassa oynasi: %s", url)
        if cloud is not None:
            try:
                cloud.call("PUT", "p/%s/kassa" % cloud.cfg.inbox, {"url": url, "at": {".sv": "timestamp"}}, timeout=30,
                           print="silent")
            except Exception as e:  # noqa: BLE001
                log.warning("kassa manzilini bulutga yozib bo'lmadi: %s", e)
        httpd.serve_forever()
    threading.Thread(target=serve, daemon=True, name="kassa-http").start()
    return svc
