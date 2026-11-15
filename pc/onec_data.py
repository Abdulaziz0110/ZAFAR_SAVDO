#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
1C -> Admin (v3.2): savdolar ro'yxati va ta'minotchilar. FAQAT O'QISH (so'rovlar).

Admin ilovasi:
  * p/{inbox}/dreq = {k: "sales" | "supp", f: "yyyymmdd", t: "yyyymmdd", at} — "shu davrni yuboring" so'rovi;
  * jadval bo'yicha (har aylanishda) — oxirgi 30 kunlik savdolar va 90 kunlik ta'minotchilar, faqat o'zgarganda.
Natija in/{inbox}/{kalit} ga k = "sales" / "supp" bo'lib (gzip JSON) qo'yiladi — Admin o'zi o'qiydi.

Hujjat nomlari do'kon bazasidan (Реализация, РеализацияМобил, ПродажаРозница, Возврат_товара_от_клиента,
Поступления, ЮкБерувчигаТулов, Возврат_товара_поставщику). Boshqa bazada biror hujjat bo'lmasa — o'tkazib yuboriladi
va "err" ro'yxatida aytiladi (qolganlari ishlaydi).
"""
import datetime as dt
import hashlib
import json
import logging

log = logging.getLogger("onec_export")

# kind: (hujjat, jadval qismi, miqdor, narx so'm, summa so'm, narx $, summa $, hujjat so'm, hujjat $, izoh)
DOCS = {
    "r": ("Реализация", "Расход", "Кол", "ЦенаСум", "СуммаСум", "ЦенаВал", "СуммаВал", "ВсегоСум", "ВсегоВал", "Комментарий"),
    "m": ("РеализацияМобил", "Расход", "Кол", "ЦенаСум", "СуммаСум", "ЦенаВал", "СуммаВал", "ВсегоСум", "ВсегоВал", "Комментарий"),
    "z": ("ПродажаРозница", "Таблица", "Кол", "ЦенаСум", "СуммаСум", "ЦенаВал", "СуммаВал", "ВсегоСум", "ВсегоВал", "Комментарий"),
    "q": ("Возврат_товара_от_клиента", "Расход", "Кол", "ЦенаСум", "СуммаСум", "ЦенаВал", "СуммаВал", "ВсегоСум", "ВсегоВал", "Комментарий"),
    "p": ("Поступления", "Приход", "Кол1", "ЦенаСум1", "СуммаСум", "ЦенаВал1", "СуммаВал", "ВсегоСум", "ВсегоВал", "Комментарий"),
    "b": ("Возврат_товара_поставщику", "Таблица", "Кол", "ЦенаСум", "СуммаСум", "ЦенаВал", "СуммаВал", "ВсегоСум", "ВсегоВал", "Комментарий"),
    "y": ("ЮкБерувчигаТулов", None, None, None, None, None, None,
          "Д.НакдСум + Д.ПластикСум + Д.КликСум", "Д.НакдВал + Д.ПластикВал + Д.КликВал", "Изох"),
}
SALES_KINDS = ("r", "m", "z", "q")
SUPPLIER_KINDS = ("p", "y", "b")
SALES_DAYS = 30
SUPPLIER_DAYS = 90
MAX_DOCS = 6000


def ymd(d):
    return d.strftime("%Y%m%d")


def parse_ymd(s, default):
    try:
        s = str(s or "").strip()
        return dt.datetime.strptime(s[:8], "%Y%m%d")
    except (ValueError, TypeError):
        return default


def period_of(req, days, now=None):
    """So'rov {f, t} yoki oxirgi [days] kun -> (boshi 00:00:00, oxiri 23:59:59). 400 kundan uzun bo'lmaydi."""
    now = now or dt.datetime.now()
    end_default = now
    start_default = now - dt.timedelta(days=days - 1)
    f = parse_ymd((req or {}).get("f"), start_default)
    t = parse_ymd((req or {}).get("t"), end_default)
    if t < f:
        f, t = t, f
    if (t - f).days > 400:
        f = t - dt.timedelta(days=400)
    return f.replace(hour=0, minute=0, second=0, microsecond=0), t.replace(hour=23, minute=59, second=59, microsecond=0)


def stamp(v):
    """1C sana (datetime yoki pywintypes) -> "yyyyMMddHHmmss"."""
    if v is None:
        return ""
    try:
        return "%04d%02d%02d%02d%02d%02d" % (v.year, v.month, v.day, v.hour, v.minute, v.second)
    except AttributeError:
        digits = "".join(ch for ch in str(v) if ch.isdigit())
        return (digits + "000000")[:14] if len(digits) >= 8 else ""


def num(v):
    try:
        return round(float(v or 0), 4)
    except (TypeError, ValueError):
        try:
            return round(float(str(v).replace(",", ".").replace(chr(0xA0), "").replace(" ", "")), 4)
        except (TypeError, ValueError):
            return 0.0


def text(v):
    if v is None:
        return ""
    if isinstance(v, float) and v.is_integer():
        return str(int(v))
    return str(v).strip()


def head_query(kind):
    doc, _ts, _q, _ps, _ss, _pu, _su, s, u, note = DOCS[kind]
    se = s if s.startswith("Д.") else "Д." + s
    ue = u if u.startswith("Д.") else "Д." + u
    return (
        "ВЫБРАТЬ Д.Номер КАК Номер, Д.Дата КАК Дата, ПРЕДСТАВЛЕНИЕ(Д.Контрагент) КАК Контр, Д.Контрагент.Код КАК КонтрКод, "
        "%s КАК Сум, %s КАК Вал, Д.%s КАК Ком ИЗ Документ.%s КАК Д "
        "ГДЕ Д.Проведен И Д.Дата МЕЖДУ &Н И &К УПОРЯДОЧИТЬ ПО Д.Дата УБЫВ" % (se, ue, note, doc)
    )


HEAD_FIELDS = ["Номер", "Дата", "Контр", "КонтрКод", "Сум", "Вал", "Ком"]
LINE_FIELDS = ["Номер", "Дата", "Товар", "Код", "Кол", "ЦС", "СС", "ЦВ", "СВ"]


def lines_query(kind):
    doc, ts, q, ps, ss, pu, su = DOCS[kind][:7]
    if not ts:
        return None
    return (
        "ВЫБРАТЬ Т.Ссылка.Номер КАК Номер, Т.Ссылка.Дата КАК Дата, ПРЕДСТАВЛЕНИЕ(Т.Товары) КАК Товар, Т.Товары.Код КАК Код, "
        "Т.%s КАК Кол, Т.%s КАК ЦС, Т.%s КАК СС, Т.%s КАК ЦВ, Т.%s КАК СВ ИЗ Документ.%s.%s КАК Т "
        "ГДЕ Т.Ссылка.Проведен И Т.Ссылка.Дата МЕЖДУ &Н И &К" % (q, ps, ss, pu, su, doc, ts)
    )


def doc_key(kind, number, date_stamp):
    return "%s:%s:%s" % (kind, text(number), date_stamp)


def build_docs(kind, heads, lines):
    """So'rov qatorlaridan hujjatlar (sof mantiq, test qilinadi)."""
    by_key = {}
    for r in lines or []:
        k = doc_key(kind, r.get("Номер"), stamp(r.get("Дата")))
        name = text(r.get("Товар"))
        if not name:
            continue
        by_key.setdefault(k, []).append([name, text(r.get("Код")), num(r.get("Кол")), num(r.get("ЦС")), num(r.get("СС")), num(r.get("ЦВ")), num(r.get("СВ"))])
    out = []
    for h in heads:
        st = stamp(h.get("Дата"))
        if not st:
            continue
        k = doc_key(kind, h.get("Номер"), st)
        ls = by_key.get(k, [])
        s = num(h.get("Сум"))
        u = num(h.get("Вал"))
        if s == 0 and u == 0 and ls:
            s = round(sum(x[4] for x in ls), 2)
            u = round(sum(x[6] for x in ls), 4)
        out.append({
            "id": k, "k": kind, "no": text(h.get("Номер")), "d": st, "c": text(h.get("Контр")), "cc": text(h.get("КонтрКод")),
            "s": s, "u": u, "n": text(h.get("Ком"))[:300], "l": ls,
        })
    return out


def fetch_docs(onec, kinds, d_from, d_to, party_names=None):
    """[docs], [xatolar]. [party_names] — faqat shu kontragentlar (nomi bo'yicha)."""
    docs, errors = [], []
    for kind in kinds:
        try:
            heads = onec.query(head_query(kind), HEAD_FIELDS, Н=d_from, К=d_to)
        except Exception as e:  # noqa: BLE001 — boshqa bazada hujjat yo'q bo'lishi mumkin
            errors.append("%s: %s" % (DOCS[kind][0], str(e)[:120]))
            continue
        lines = []
        lq = lines_query(kind)
        if lq:
            try:
                lines = onec.query(lq, LINE_FIELDS, Н=d_from, К=d_to)
            except Exception as e:  # noqa: BLE001
                errors.append("%s (qatorlar): %s" % (DOCS[kind][0], str(e)[:120]))
        ds = build_docs(kind, heads, lines)
        if party_names is not None:
            ds = [d for d in ds if d["c"] in party_names]
        docs.extend(ds)
    docs.sort(key=lambda d: d["d"], reverse=True)
    return docs[:MAX_DOCS], errors


def sales_payload(onec, d_from, d_to, now_ms):
    docs, errors = fetch_docs(onec, SALES_KINDS, d_from, d_to)
    return {"v": 1, "f": ymd(d_from), "t": ymd(d_to), "at": now_ms, "docs": docs, "err": errors}


SUPPLIER_LIST_Q = (
    "ВЫБРАТЬ РАЗЛИЧНЫЕ Д.Контрагент.Наименование КАК Имя, Д.Контрагент.Код КАК Код, Д.Контрагент.Тел КАК Тел "
    "ИЗ Документ.Поступления КАК Д ГДЕ Д.Проведен И НЕ Д.Контрагент.ПометкаУдаления"
)
# v3.12: kassa moddalari (Admin: kassa kirim / chiqim shakli uchun)
OPS_Q = "ВЫБРАТЬ О.Код КАК Код, О.Наименование КАК Имя ИЗ Справочник.Операции КАК О ГДЕ НЕ О.ПометкаУдаления УПОРЯДОЧИТЬ ПО О.Наименование"
BALANCE_Q = (
    "ВЫБРАТЬ О.Контрагент.Наименование КАК Имя, О.СуммаОстаток КАК С, О.СуммаВалОстаток КАК Вл "
    "ИЗ РегистрНакопления.ВзаиморасчетСКонтрагентом.Остатки КАК О"
)


def build_suppliers(people, balances, docs):
    """Sof mantiq: ta'minotchilar ro'yxati, qoldiq va hujjatlar."""
    bal = {}
    for b in balances or []:
        n = text(b.get("Имя"))
        s, u = bal.get(n, (0.0, 0.0))
        bal[n] = (round(s + num(b.get("С")), 2), round(u + num(b.get("Вл")), 4))
    by_party = {}
    for d in docs:
        by_party.setdefault(d["c"], []).append(d)
    out = []
    seen = set()
    for p in people:
        name = text(p.get("Имя"))
        if not name or name in seen:
            continue
        seen.add(name)
        s, u = bal.get(name, (0.0, 0.0))
        out.append({"id": text(p.get("Код")) or name, "code": text(p.get("Код")), "name": name, "tel": text(p.get("Тел")),
                    "bs": s, "bu": u, "docs": by_party.get(name, [])})
    out.sort(key=lambda x: (-(abs(x["bs"]) + abs(x["bu"]) * 12000), x["name"].lower()))
    return out


def suppliers_payload(onec, d_from, d_to, now_ms):
    errors = []
    try:
        people = onec.query(SUPPLIER_LIST_Q, ["Имя", "Код", "Тел"])
    except Exception as e:  # noqa: BLE001
        try:
            people = onec.query(SUPPLIER_LIST_Q.replace(", Д.Контрагент.Тел КАК Тел", ""), ["Имя", "Код"])
        except Exception:
            return {"v": 1, "f": ymd(d_from), "t": ymd(d_to), "at": now_ms, "list": [], "err": ["Поступления: %s" % str(e)[:150]]}
    try:
        balances = onec.query(BALANCE_Q, ["Имя", "С", "Вл"])
    except Exception as e:  # noqa: BLE001
        balances = []
        errors.append("ВзаиморасчетСКонтрагентом: %s" % str(e)[:120])
    names = set(text(p.get("Имя")) for p in people)
    docs, errs = fetch_docs(onec, SUPPLIER_KINDS, d_from, d_to, names)
    errors.extend(errs)
    out = {"v": 1, "f": ymd(d_from), "t": ymd(d_to), "at": now_ms, "list": build_suppliers(people, balances, docs), "err": errors}
    out["ops"] = cash_ops(onec)
    out["refs"] = catalog_refs(onec)
    out["tel"] = party_phones(onec)
    return out


# v5.3: hamma kontragentlarning telefoni (mijozlar ro'yxatida va qarzdorlarda ko'rsatish uchun)
PHONES_Q = ("ВЫБРАТЬ К.Код КАК Код, К.Наименование КАК Имя, К.Тел КАК Тел ИЗ Справочник.Контрагенты КАК К "
            "ГДЕ НЕ К.ПометкаУдаления И НЕ К.ЭтоГруппа")


def party_phones(onec):
    """[[kod, nomi, telefon], ...] - faqat telefoni borlar. Xato bo'lsa bo'sh."""
    rows = None
    for q in (PHONES_Q, PHONES_Q.replace(" И НЕ К.ЭтоГруппа", "")):
        try:
            rows = onec.query(q, ["Код", "Имя", "Тел"])
            break
        except Exception as e:  # noqa: BLE001 - bazada bu rekvizit bo'lmasligi mumkin
            log.debug("kontragent telefonlari: %s", e)
    out = []
    for r in rows or []:
        tel = text(r.get("Тел")).strip()
        name = text(r.get("Имя")).strip()
        if not tel or not name:
            continue
        code = r.get("Код")
        if isinstance(code, float) and code == int(code):
            code = int(code)
        out.append([str(code if code is not None else "").strip(), name, tel[:40]])
        if len(out) >= 5000:
            break
    return out


# v5.1: yangi tovar shakli uchun 1C ma'lumotnomalari (guruh, brend, o'lchov birligi, tarozi, region).
# Telefon shu ro'yxatlardan tanlaydi — 1C'dagi kabi, qo'lda yozilmaydi.
REF_TABLES = (
    ("grp", "Справочник.ГруппаТовара"),
    ("brd", "Справочник.Бренд"),
    ("unit", "Справочник.ЕдИзм"),
    ("scale", "Справочник.Весы"),
    ("reg", "Справочник.Регион"),
)


def catalog_refs(onec):
    """{"grp": ["..."], "brd": [...], ...} — bo'sh yoki xato jadval shunchaki tushib qoladi."""
    out = {}
    for key, table in REF_TABLES:
        q = (
            "ВЫБРАТЬ С.Наименование КАК Имя ИЗ %s КАК С "
            "ГДЕ НЕ С.ПометкаУдаления УПОРЯДОЧИТЬ ПО С.Наименование" % table
        )
        try:
            rows = onec.query(q, ["Имя"])
        except Exception as e:  # noqa: BLE001 — bazada bu ma'lumotnoma bo'lmasligi mumkin
            log.warning("%s o'qilmadi: %s", table, e)
            continue
        names = []
        for r in rows:
            n = text(r.get("Имя")).strip()
            if n and n not in names:
                names.append(n)
            if len(names) >= 500:
                break
        if names:
            out[key] = names
    return out


def cash_ops(onec):
    """[[kod, nomi], ...] — Справочник.Операции (kassa moddalari). Xato bo'lsa bo'sh."""
    try:
        rows = onec.query(OPS_Q, ["Код", "Имя"])
    except Exception as e:  # noqa: BLE001
        log.warning("kassa moddalari o'qilmadi: %s", e)
        return []
    out = []
    for r in rows:
        name = text(r.get("Имя"))
        if not name:
            continue
        code = r.get("Код")
        if isinstance(code, float) and code == int(code):
            code = int(code)
        out.append([str(code if code is not None else "").strip(), name])
    return out


# ---------------------------------------------------------------- v4.1: hisobotlar (1C registrlaridan, faqat o'qish)
# 1C'dagi "Фойда", "Касса", "Харажатлар", "Остаток по товаров" hisobotlari bilan bir xil registrlar.

PROFIT_Q = (
    "ВЫБРАТЬ П.Товар.Наименование КАК Товар, П.Товар.Код КАК Код, СУММА(П.КолОборот) КАК Кол, "
    "СУММА(П.ПриходСуммаОборот) КАК ПС, СУММА(П.РасходСуммаОборот) КАК РС, СУММА(П.ПрибльСуммаОборот) КАК ФС, "
    "СУММА(П.ПриходВалОборот) КАК ПВ, СУММА(П.РасходВалОборот) КАК РВ, СУММА(П.ПрибльВалОборот) КАК ФВ "
    "ИЗ РегистрНакопления.Прибль.Обороты(&Н, &К, , ) КАК П СГРУППИРОВАТЬ ПО П.Товар.Наименование, П.Товар.Код"
)
PROFIT_F = ["Товар", "Код", "Кол", "ПС", "РС", "ФС", "ПВ", "РВ", "ФВ"]
DISCOUNT_Q = (
    "ВЫБРАТЬ ЕСТЬNULL(СУММА(П.ПрибльСумСкидкаОборот), 0) КАК С, ЕСТЬNULL(СУММА(П.ПрибльВалСкидкаОборот), 0) КАК Вл "
    "ИЗ РегистрНакопления.Прибль.Обороты(&Н, &К, , ) КАК П"
)
EXPENSES_Q = (
    "ВЫБРАТЬ Х.Номи.Наименование КАК Имя, СУММА(Х.СумОборот) КАК С, СУММА(Х.ВалОборот) КАК Вл "
    "ИЗ РегистрНакопления.Харажатлар.Обороты(&Н, &К, , ) КАК Х СГРУППИРОВАТЬ ПО Х.Номи.Наименование"
)
CASH_BAL_Q = (
    "ВЫБРАТЬ ЕСТЬNULL(СУММА(К.НаличОстаток), 0) КАК Н, ЕСТЬNULL(СУММА(К.ПеречОстаток), 0) КАК П, "
    "ЕСТЬNULL(СУММА(К.КликОстаток), 0) КАК К, ЕСТЬNULL(СУММА(К.НаличВалОстаток), 0) КАК НВ, "
    "ЕСТЬNULL(СУММА(К.ПеречВалОстаток), 0) КАК ПВ ИЗ РегистрНакопления.Касса.Остатки(&К, ) КАК К"
)
CASH_TURN_Q = (
    "ВЫБРАТЬ К.Асос.Наименование КАК Имя, СУММА(К.НаличПриход) КАК НП, СУММА(К.НаличРасход) КАК НР, "
    "СУММА(К.ПеречПриход) КАК ПП, СУММА(К.ПеречРасход) КАК ПР, СУММА(К.КликПриход) КАК КП, СУММА(К.КликРасход) КАК КР, "
    "СУММА(К.НаличВалПриход) КАК ВП, СУММА(К.НаличВалРасход) КАК ВР "
    "ИЗ РегистрНакопления.Касса.Обороты(&Н, &К, , ) КАК К СГРУППИРОВАТЬ ПО К.Асос.Наименование"
)
CASH_TURN_F = ["Имя", "НП", "НР", "ПП", "ПР", "КП", "КР", "ВП", "ВР"]
STOCK_Q = (
    "ВЫБРАТЬ О.Товар.Наименование КАК Товар, О.Товар.Код КАК Код, СУММА(О.КоличествоОстаток) КАК Кол, "
    "ВЫБОР КОГДА О.Товар.ВидВал = ЗНАЧЕНИЕ(Перечисление.ВидВал.Вал) ТОГДА 1 ИНАЧЕ 0 КОНЕЦ КАК Вал, "
    "О.Товар.ПриходЦена КАК ЦС, О.Товар.ПриходВал КАК ЦВ "
    "ИЗ РегистрНакопления.Товар.Остатки(&К, ) КАК О "
    "СГРУППИРОВАТЬ ПО О.Товар.Наименование, О.Товар.Код, О.Товар.ВидВал, О.Товар.ПриходЦена, О.Товар.ПриходВал"
)
STOCK_F = ["Товар", "Код", "Кол", "Вал", "ЦС", "ЦВ"]
DEBT_Q = (
    "ВЫБРАТЬ Вз.Контрагент.Наименование КАК Имя, ВЫБОР КОГДА Вз.Контрагент.Родитель = ЗНАЧЕНИЕ(Справочник.Контрагенты.Поставщики) "
    "ТОГДА 1 ИНАЧЕ 0 КОНЕЦ КАК Пост, СУММА(Вз.СуммаОстаток) КАК С, СУММА(Вз.СуммаВалОстаток) КАК Вл "
    "ИЗ РегистрНакопления.ВзаиморасчетСКонтрагентом.Остатки(&К, ) КАК Вз "
    "СГРУППИРОВАТЬ ПО Вз.Контрагент.Наименование, Вз.Контрагент.Родитель"
)
REPORT_ROWS = 300


def build_reports(profit, discount, expenses, cash_bal, cash_turn, stock, debts, limit=REPORT_ROWS):
    """Sof mantiq: so'rov qatorlaridan hisobotlar (test qilinadi). Har bo'lim — jami + eng kattalari."""
    out = {}
    if profit is not None:
        rows = []
        tot = [0.0] * 6
        for r in profit:
            vals = [num(r.get(k)) for k in ("ПС", "РС", "ФС", "ПВ", "РВ", "ФВ")]
            if not any(vals) and not num(r.get("Кол")):
                continue
            tot = [a + b for a, b in zip(tot, vals)]
            rows.append([text(r.get("Товар")), text(r.get("Код")), num(r.get("Кол"))] + vals)
        rows.sort(key=lambda x: -(abs(x[5]) + abs(x[8]) * 12000))
        d = discount or {}
        out["profit"] = {"tot": [round(x, 4) for x in tot], "rows": rows[:limit], "n": len(rows),
                         "disc": [num(d.get("С")), num(d.get("Вл"))]}
    if expenses is not None:
        ex = [[text(r.get("Имя")) or "-", num(r.get("С")), num(r.get("Вл"))] for r in expenses]
        ex = [x for x in ex if x[1] or x[2]]
        ex.sort(key=lambda x: -(abs(x[1]) + abs(x[2]) * 12000))
        out["exp"] = ex[:limit]
    if cash_bal is not None or cash_turn is not None:
        b = (cash_bal or [{}])[0] if cash_bal else {}
        turn = []
        for r in cash_turn or []:
            v = [num(r.get(k)) for k in CASH_TURN_F[1:]]
            if any(v):
                turn.append([text(r.get("Имя")) or "-"] + v)
        turn.sort(key=lambda x: -(sum(abs(y) for y in x[1:7]) + (abs(x[7]) + abs(x[8])) * 12000))
        out["cash"] = {"bal": [num(b.get(k)) for k in ("Н", "П", "К", "НВ", "ПВ")], "turn": turn[:limit]}
    if stock is not None:
        rows = []
        vs = vu = 0.0
        for r in stock:
            q = num(r.get("Кол"))
            if q == 0:
                continue
            usd = bool(num(r.get("Вал")))
            cost = num(r.get("ЦВ")) if usd else num(r.get("ЦС"))
            value = round(q * cost, 4 if usd else 2)
            if usd:
                vu += value
            else:
                vs += value
            rows.append([text(r.get("Товар")), text(r.get("Код")), q, 1 if usd else 0, cost, value])
        rows.sort(key=lambda x: -(abs(x[5]) * (12000 if x[3] else 1)))
        out["stock"] = {"rows": rows[:limit * 4], "n": len(rows), "vs": round(vs, 2), "vu": round(vu, 4),
                        "neg": sum(1 for x in rows if x[2] < 0)}
    if debts is not None:
        pos = [0.0, 0.0]
        neg = [0.0, 0.0]
        top, owe = [], []
        for r in debts:
            sm, us = num(r.get("С")), num(r.get("Вл"))
            if not sm and not us:
                continue
            name = text(r.get("Имя"))
            sup = bool(num(r.get("Пост")))
            if sm > 0:
                pos[0] += sm
            else:
                neg[0] += -sm
            if us > 0:
                pos[1] += us
            else:
                neg[1] += -us
            if sm > 0 or us > 0:
                top.append([name, max(sm, 0), max(us, 0), 1 if sup else 0])
            if sm < 0 or us < 0:
                owe.append([name, max(-sm, 0), max(-us, 0), 1 if sup else 0])
        key = lambda x: -(x[1] + x[2] * 12000)  # noqa: E731
        top.sort(key=key)
        owe.sort(key=key)
        out["debt"] = {"pos": [round(x, 2) for x in pos], "neg": [round(x, 2) for x in neg], "top": top[:50], "owe": owe[:50]}
    return out


def reports_payload(onec, d_from, d_to, now_ms):
    """Hisobotlar: foyda (tovar bo'yicha), chegirma, xarajatlar, kassa qoldig'i va aylanmasi, ombor qoldig'i, qarzlar."""
    errors = []

    def q(name, text_, fields, **params):
        try:
            return onec.query(text_, fields, **params)
        except Exception as e:  # noqa: BLE001 — bitta hisobot buzuq bo'lsa ham qolganlari
            errors.append("%s: %s" % (name, str(e)[:120]))
            return None

    profit = q("Прибль", PROFIT_Q, PROFIT_F, Н=d_from, К=d_to)
    disc = q("Прибль (скидка)", DISCOUNT_Q, ["С", "Вл"], Н=d_from, К=d_to)
    expenses = q("Харажатлар", EXPENSES_Q, ["Имя", "С", "Вл"], Н=d_from, К=d_to)
    cash_bal = q("Касса", CASH_BAL_Q, ["Н", "П", "К", "НВ", "ПВ"], К=d_to)
    cash_turn = q("Касса (aylanma)", CASH_TURN_Q, CASH_TURN_F, Н=d_from, К=d_to)
    stock = q("Товар (qoldiq)", STOCK_Q, STOCK_F, К=d_to)
    debts = q("ВзаиморасчетСКонтрагентом", DEBT_Q, ["Имя", "Пост", "С", "Вл"], К=d_to)
    out = build_reports(profit, (disc or [{}])[0] if disc else None, expenses, cash_bal, cash_turn, stock, debts)
    out.update({"v": 1, "f": ymd(d_from), "t": ymd(d_to), "at": now_ms, "err": errors})
    return out


TODAY_Q = (
    "ВЫБРАТЬ РАЗЛИЧНЫЕ Р.Контрагент.Наименование КАК Имя ИЗ РегистрНакопления.ВзаиморасчетСКонтрагентом КАК Р "
    "ГДЕ Р.Период >= &Н"
)


def active_today(onec, now=None):
    """Bugun harakati bo'lgan kontragentlar nomi (tezkor yangilash uchun). Xato bo'lsa None — hammasi."""
    now = now or dt.datetime.now()
    start = now.replace(hour=0, minute=0, second=0, microsecond=0)
    try:
        return set(text(r.get("Имя")) for r in onec.query(TODAY_Q, ["Имя"], Н=start) if text(r.get("Имя")))
    except Exception as e:  # noqa: BLE001
        log.warning("bugungi harakatlar so'rovi ishlamadi: %s", e)
        return None


def payload_sig(p):
    """"at" siz xesh — ma'lumot o'zgarmagan bo'lsa qayta yuborilmaydi."""
    q = dict(p)
    q.pop("at", None)
    return hashlib.sha256(json.dumps(q, ensure_ascii=False, sort_keys=True).encode("utf-8")).hexdigest()
