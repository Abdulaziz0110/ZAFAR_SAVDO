# -*- coding: utf-8 -*-
"""Taroziga narx va PLU yuklash — 1C'dagi "ВыгрузкаТоваровНаВесы" (RONGTA) bilan aynan bir xil.

1C nima qiladi (bazadagi ishlov):
  1) "Весы" ma'lumotnomasidan tarozi(lar) IP manzillari (ТспИП1..ТспИП10, odatda "ОбщиеВесы");
  2) tovarlar: shu taroziga biriktirilgan (Товар.Весы), ПЛУ > 0, ПродажЦена1 > 0;
     "faqat yuborish" — qoldig'i borlari, "tozalab yuborish" — hammasi;
  3) C:/ProgramData/rt_scale.txt fayli (UTF-8):
       1-qator: "1" — tozalab yuborish, "0" — faqat yuborish
       2-qator: "IP1~IP2~"
       3-qator: "Nomi|PLU|shtrix kodning 3-7 belgilari|narx|29 (tarozida) yoki 28 (dona)~..."
  4) C:\\ProgramData\\ScaleDrv\\RLS1000C.exe ni ishga tushiradi — u faylni o'qib taroziga yuboradi.
Admin telefoni: p/{inbox}/scale = {m: "stock"|"clear", at}; natija: p/{inbox}/scale_res = {at, req, ok, text, n}.
Faqat 1C'dan O'QIYDI.
"""
import datetime as dt
import logging
import os
import subprocess

log = logging.getLogger("onec_export")

SCALE_FILE = r"C:\ProgramData\rt_scale.txt"
DRIVER = r"C:\ProgramData\ScaleDrv\RLS1000C.exe"
SCALE_REF = "ЗНАЧЕНИЕ(Справочник.Весы.ОбщиеВесы)"
WEIGHED = "ЗНАЧЕНИЕ(Перечисление.ТипТовара.Весовой)"


def goods_query(stock_only):
    """1C so'rovi: tovarlar (nomi, shtrix, PLU, tarozida/dona, 1-narx)."""
    if stock_only:
        return (
            "ВЫБРАТЬ О.Товар.Наименование КАК Наим, О.Товар.ШтрихКод КАК ШК, О.Товар.ПЛУ КАК ПЛУ, "
            "ВЫБОР КОГДА О.Товар.ТипТовара = %s ТОГДА 1 ИНАЧЕ 0 КОНЕЦ КАК Вес, О.Товар.ПродажЦена1 КАК Цена "
            "ИЗ РегистрНакопления.Товар.Остатки(&Дата, Товар.Весы = %s) КАК О "
            "ГДЕ О.КоличествоОстаток > 0 УПОРЯДОЧИТЬ ПО ПЛУ" % (WEIGHED, SCALE_REF)
        )
    return (
        "ВЫБРАТЬ Т.Наименование КАК Наим, Т.ШтрихКод КАК ШК, Т.ПЛУ КАК ПЛУ, "
        "ВЫБОР КОГДА Т.ТипТовара = %s ТОГДА 1 ИНАЧЕ 0 КОНЕЦ КАК Вес, Т.ПродажЦена1 КАК Цена "
        "ИЗ Справочник.Товары КАК Т ГДЕ Т.Весы = %s УПОРЯДОЧИТЬ ПО ПЛУ" % (WEIGHED, SCALE_REF)
    )


IP_FIELDS = ["И%d" % i for i in range(1, 11)]


def ips_query():
    sel = ", ".join("В.ТспИП%d КАК И%d" % (i, i) for i in range(1, 11))
    return "ВЫБРАТЬ %s ИЗ Справочник.Весы КАК В ГДЕ В.Ссылка = %s" % (sel, SCALE_REF)


def _num(v):
    try:
        return float(v or 0)
    except (TypeError, ValueError):
        return 0.0


def fmt_price(v):
    """1C Формат(Цена, "ЧГ=0"): guruhlashsiz; kasr bo'lsa vergul bilan."""
    x = round(_num(v), 2)
    if x == int(x):
        return str(int(x))
    return ("%.2f" % x).rstrip("0").rstrip(".").replace(".", ",")


def clean(s):
    return " ".join(str(s or "").replace("|", " ").replace("~", " ").split())


def line(name, plu, barcode, price, weighed):
    """Bitta tovar: "Nomi|PLU|shtrix[3..7]|narx|29/28"."""
    return "%s|%s|%s|%s|%s" % (clean(name), int(_num(plu)), str(barcode or "").strip()[2:7], fmt_price(price), "29" if weighed else "28")


def collect(rows):
    """1C qatorlari -> tovarlar (1C bilan bir xil shart: nomi bor, narx > 0, PLU > 0)."""
    out = []
    for r in rows:
        name = clean(r.get("Наим"))
        if not name or _num(r.get("Цена")) <= 0 or _num(r.get("ПЛУ")) <= 0:
            continue
        out.append(line(name, r.get("ПЛУ"), r.get("ШК"), r.get("Цена"), _num(r.get("Вес")) == 1))
    return out


def ips_of(row):
    return [str(row.get(f) or "").strip() for f in IP_FIELDS if str(row.get(f) or "").strip()]


def build_text(clear, ips, items):
    """rt_scale.txt matni (CRLF, 1C TextDocument kabi)."""
    lines = ["1" if clear else "0", "".join(ip + "~" for ip in ips), "".join(i + "~" for i in items)]
    return "\r\n".join(lines) + "\r\n"


def run(onec, clear, now=None, path=SCALE_FILE, driver=DRIVER, launch=True):
    """1C'dan o'qib faylni yozadi va haydovchini ishga tushiradi. @return (tovarlar soni, IP'lar)."""
    now = now or dt.datetime.now()
    onec.connect()
    try:
        ip_rows = onec.query(ips_query(), IP_FIELDS)
        ips = ips_of(ip_rows[0]) if ip_rows else []
        rows = onec.query(goods_query(not clear), ["Наим", "ШК", "ПЛУ", "Вес", "Цена"],
                          Дата=now.replace(hour=23, minute=59, second=59, microsecond=0))
    finally:
        onec.close()
    items = collect(rows)
    if not ips:
        raise RuntimeError("Tarozi IP manzili topilmadi (1C: Весы ▸ ОбщиеВесы ▸ ТспИП1)")
    if not items:
        raise RuntimeError("Yuboriladigan tovar yo'q (PLU va 1-narxi bor, taroziga biriktirilgan tovar topilmadi)")
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    with open(path, "w", encoding="utf-8-sig", newline="") as f:
        f.write(build_text(clear, ips, items))
    if launch:
        if not os.path.isfile(driver):
            raise RuntimeError("Tarozi dasturi topilmadi: %s" % driver)
        subprocess.Popen([driver], cwd=os.path.dirname(driver))
    return len(items), ips


def tick(cloud, onec, state, save_state):
    """Admin so'rovi bo'lsa — bajaradi va natijani yozadi. @return holat matni yoki None."""
    req = cloud.call("GET", "p/%s/scale" % cloud.cfg.inbox, timeout=30)
    if not isinstance(req, dict):
        return None
    at = req.get("at") or 0
    if not at or at <= state.get("__scale__", 0):
        return None
    clear = req.get("m") == "clear"
    try:
        n, ips = run(onec, clear)
        ok, text = True, "Taroziga yuborildi: %d ta tovar -> %s" % (n, ", ".join(ips))
    except Exception as e:  # noqa: BLE001
        log.exception("tarozi")
        ok, n, text = False, 0, "Tarozi: %s" % str(e)[:200]
    state["__scale__"] = at
    save_state(state)
    cloud.call("PUT", "p/%s/scale_res" % cloud.cfg.inbox,
               {"at": {".sv": "timestamp"}, "req": at, "ok": ok, "text": text[:300], "n": n}, timeout=30, print="silent")
    return text
