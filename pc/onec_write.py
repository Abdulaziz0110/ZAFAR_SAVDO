#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Admin -> 1C yozish ("Tasdiqlab kiritish", v3.2). Faqat Admin ilovasida egasi TASDIQLAGAN so'rovlar keladi:

  w/{inbox}/{id} = {at, k, by, p}   k: in — kirim (Поступления), sp — ta'minotchiga to'lov (ЮкБерувчигаТулов),
                                       rs — ta'minotchiga qaytarish, rc — mijoz qaytargan tovar, bc — shtrix kod,
                                       v3.12: cp — mijozdan to'lov (Оплата_Клента), ci / ce — kassa kirim / chiqim
                                       (КассаПриход / КассаРасход), ge — tovar kartochkasi (narx 1-4, kirim narxi, PLU, nomi),
                                       ng — yangi tovar (namuna tovardan nusxa, Товары),
                                       v4.1: nk / ke — yangi kontragent / tahrirlash (Контрагенты),
                                       wo — tovarni hisobdan chiqarish (СписанияТоваров, tannarx 1C qoldig'idan),
                                       undo — oldingi yozuvni bekor qilish (o'chirish belgisi; ge — eski qiymatlar tiklanadi)
  natija -> wr/{inbox}/{id} = {at, ok, st, no, ref, e}

Xavfsizlik:
  * kun birinchi yozuvidan oldin baza fayli nusxalanadi (fayl baza bo'lsa): backup/1Cv8_YYYYMMDD.1CD, oxirgi 7 tasi;
    nusxa olinmasa yozilmaydi ([write] require_backup = 0 bo'lsa — baribir yoziladi);
  * har yozuv write_journal.jsonl ga (id, hujjat turi, raqami, UUID) — bir so'rov ikki marta kiritilmaydi;
  * bekor qilish — hujjatga o'chirish belgisi (1C'da "Пометка удаления"ni olib tiklasa bo'ladi), hech narsa o'chirilmaydi;
  * [write] enabled = 0 — yozish butunlay o'chiq (so'rovlar kutib turadi).
"""
import datetime as dt
import glob
import json
import logging
import os
import re
import shutil

log = logging.getLogger("onec_export")

KINDS = {"in", "sp", "rs", "rc", "bc", "cp", "ci", "ce", "ge", "ng", "rt", "nk", "ke", "wo", "undo", "de", "bk"}
DOC_OF = {"in": "Поступления", "sp": "ЮкБерувчигаТулов", "rs": "Возврат_товара_поставщику", "rc": "Возврат_товара_от_клиента",
          "cp": "Оплата_Клента", "ci": "КассаПриход", "ce": "КассаРасход"}
# ge / ng: telefon yuboradigan maydonlar (f) — boshqasi qabul qilinmaydi
GOODS_FIELDS = {"p1", "p2", "p3", "p4", "pin", "plu", "nm", "cur", "bc", "rate",
                # v5.1: yangi tovar kartochkasi (1C'dagi maydonlar)
                "grp", "brd", "unit", "vol", "pack", "nac", "typ", "scl", "abc"}

# v5.1: telefondagi kalit -> 1C ma'lumotnomasi (yangi tovar kartochkasi)
CARD_REFS = {"grp": "ГруппаТовара", "brd": "Бренд", "unit": "ЕдИзм", "scl": "Весы"}
# son maydonlari
CARD_NUMS = {"vol": "Обьём", "pack": "Шт", "nac": "Нац"}
# v4.1: nk / ke — kontragent maydonlari (grp: "c" mijozlar, "s" ta'minotchilar)
PARTY_FIELDS = {"nm", "tel", "tel2", "adr", "reg", "grp", "inn", "lim"}
PARTY_ATTR = {"nm": "Наименование", "tel": "Тел", "tel2": "Тел2", "adr": "Адрес", "inn": "ИНН"}
# v5.1: son maydonlari (kredit limiti)
PARTY_NUMS = {"lim": "ЛимитСум"}
# v5.2 (A5): tarixdagi savdoni o'zgartirish — 1C hujjati nomi va tovarlar jadvali
EDITABLE_DOCS = {
    "r": ("Реализация", "Расход"),
    "m": ("РеализацияМобил", "Расход"),
    "z": ("ПродажаРозница", "Таблица"),
    "q": ("Возврат_товара_от_клиента", "Расход"),
    "b": ("Возврат_товара_поставщику", "Таблица"),
}
# v5.2 (A11): ko'p tovarga birdan amal
BULK_FIELDS = {"bop", "bfind", "bto", "btxt", "bwhere", "bkey", "bval", "bgrp", "bmatch", "bcodes", "bqty"}
BULK_OPS = {"repl", "add", "field", "stock"}
BULK_KEYS = {"grp", "brd", "unit", "vol", "pack", "nac", "scl", "p1", "p2", "p3", "p4", "pin"}
BULK_MAX = 5000
PHONE_RE = re.compile(r"[+0-9 ()-]{5,20}")
PRICE_KEYS = ("p1", "p2", "p3", "p4", "pin")
KEEP_BACKUPS = 7


class WriteError(Exception):
    pass


# ---------------------------------------------------------------- sof mantiq

def parse_request(rid, item):
    """Bulutdagi so'rov -> dict yoki None (buzuq)."""
    if not isinstance(item, dict):
        return None
    k = item.get("k")
    if k not in KINDS:
        return None
    try:
        p = json.loads(item.get("p") or "{}")
    except (TypeError, ValueError):
        return None
    if not isinstance(p, dict):
        return None
    lines = []
    for x in p.get("l") or []:
        if not isinstance(x, list) or not x:
            continue
        name = str(x[0] or "").strip()
        def f(i):
            try:
                return float(x[i]) if len(x) > i and x[i] is not None else 0.0
            except (TypeError, ValueError):
                return 0.0
        lines.append({"name": name, "code": str(x[1] if len(x) > 1 and x[1] is not None else "").strip(), "q": f(2), "ps": f(3), "pu": f(4)})
    def g(key):
        try:
            return float(p.get(key) or 0)
        except (TypeError, ValueError):
            return 0.0
    f = p.get("f") if isinstance(p.get("f"), dict) else {}
    return {
        "id": rid, "k": k, "by": str(item.get("by") or ""), "party": str(p.get("c") or "").strip(), "party_code": str(p.get("cc") or "").strip(),
        "note": str(p.get("n") or "")[:300], "lines": lines, "ns": g("ns"), "nu": g("nu"), "ps": g("ps"), "pu": g("pu"),
        "ks": g("ks"), "ku": g("ku"), "ds": g("ds"), "du": g("du"),
        "bc": str(p.get("bc") or "").strip(), "target": str(p.get("t") or "").strip(),
        "op": str(p.get("op") or "").strip(), "op_code": str(p.get("opc") or "").strip(),
        # v5.2 (A5): tayyor 1C hujjatini tahrirlash — turi, raqami va sanasi (yyyyMMddHHmmss)
        "dk": str(p.get("dk") or "").strip(), "no": str(p.get("no") or "").strip(), "ds_stamp": str(p.get("dt") or "").strip(),
        "f": {str(a): b for a, b in f.items() if str(a) in GOODS_FIELDS or str(a) in PARTY_FIELDS or str(a) in BULK_FIELDS},
    }


def _fnum(v):
    try:
        x = float(v)
    except (TypeError, ValueError):
        return None
    return x if x == x else None  # NaN emas


def bulk_needles(find):
    """Bir nechta topiladigan matn "|" bilan beriladi. Telefondagi BulkText.needles bilan bir xil."""
    return [x for x in str(find or "").split("|") if x]


def bulk_tidy(s):
    """Ketma-ket bo'shliqlar bittaga, chetlari kesiladi (telefondagi BulkText.tidy bilan bir xil)."""
    out = []
    space = False
    for ch in s:
        if ch in (" ", "\u00a0"):
            space = True
            continue
        if space and out:
            out.append(" ")
        space = False
        out.append(ch)
    return "".join(out).strip()


def bulk_new_name(f, name):
    """Yangi nom (telefondagi BulkPlan.newName bilan AYNAN bir xil qoida)."""
    op = str(f.get("bop") or "")
    if op == "repl":
        s = name
        for n in bulk_needles(f.get("bfind")):
            s = s.replace(n, str(f.get("bto") or ""))
        return bulk_tidy(s)
    if op == "add":
        text = str(f.get("btxt") or "")
        if not text:
            return name
        where = str(f.get("bwhere") or "")
        if where == "start":
            s = text + name
        elif where == "end":
            s = name + text
        else:
            i = int(where) if where.isdigit() else len(name)
            i = max(0, min(i, len(name)))
            s = name[:i] + text + name[i:]
        return bulk_tidy(s)
    return name


def bulk_covers(f, code, name, group):
    codes = [x.strip() for x in str(f.get("bcodes") or "").split(",") if x.strip()]
    if codes and str(code) not in codes:
        return False
    grp = str(f.get("bgrp") or "").strip()
    if grp and str(group).strip().lower() != grp.lower():
        return False
    match = str(f.get("bmatch") or "").strip()
    if match and match.lower() not in str(name).lower():
        return False
    return True


def problem(req):
    """Tekshiruv (telefondagi bilan bir xil). None — to'g'ri."""
    k = req["k"]
    if k in ("in", "rs", "rc"):
        if not req["party"]:
            return "kontragent ko'rsatilmagan"
        if not req["lines"]:
            return "tovar yo'q"
        for ln in req["lines"]:
            if not ln["name"] and not ln["code"]:
                return "tovar nomi bo'sh"
            if ln["q"] <= 0 or ln["ps"] < 0 or ln["pu"] < 0:
                return "miqdor yoki narx noto'g'ri: %s" % ln["name"]
    elif k == "sp":
        if not req["party"]:
            return "ta'minotchi ko'rsatilmagan"
        if min(req["ns"], req["nu"], req["ps"], req["pu"]) < 0 or req["ns"] + req["nu"] + req["ps"] + req["pu"] <= 0:
            return "summa noto'g'ri"
    elif k == "bc":
        if not re.fullmatch(r"[0-9A-Za-z]{4,40}", req["bc"]):
            return "shtrix kod noto'g'ri"
        if not req["party"] and not req["party_code"]:
            return "tovar ko'rsatilmagan"
    elif k == "cp":
        if not req["party"] and not req["party_code"]:
            return "mijoz ko'rsatilmagan"
        vals = [req[x] for x in ("ns", "nu", "ps", "pu", "ks", "ku", "ds", "du")]
        if min(vals) < 0 or sum(vals) <= 0:
            return "summa noto'g'ri"
    elif k in ("ci", "ce"):
        if not req["op"] and not req["op_code"]:
            return "kassa moddasi ko'rsatilmagan"
        vals = [req[x] for x in ("ns", "nu", "ps", "pu", "ks")]
        if min(vals) < 0 or sum(vals) <= 0:
            return "summa noto'g'ri"
    elif k == "rt":
        r = _fnum(req["f"].get("rate"))
        if r is None or not 0 < r < 1000000:
            return "kurs noto'g'ri"
    elif k in ("ge", "ng"):
        f = req["f"]
        if not req["party"] and not req["party_code"]:
            return "tovar ko'rsatilmagan" if k == "ge" else "namuna tovar ko'rsatilmagan"
        if k == "ge" and not any(x in f for x in ("p1", "p2", "p3", "p4", "pin", "plu", "nm")):
            return "o'zgarish yo'q"
        for x in PRICE_KEYS:
            if x in f and (_fnum(f[x]) is None or _fnum(f[x]) < 0):
                return "narx noto'g'ri"
        if "plu" in f and (_fnum(f["plu"]) is None or not 0 <= _fnum(f["plu"]) <= 99999 or _fnum(f["plu"]) != int(_fnum(f["plu"]))):
            return "PLU noto'g'ri (0-99999)"
        if "nm" in f and not 2 <= len(str(f["nm"]).strip()) <= 150:
            return "tovar nomi noto'g'ri"
        if k == "ng" and not str(f.get("nm") or "").strip():
            return "yangi tovar nomi yo'q"
        if str(f.get("bc") or "").strip() and not re.fullmatch(r"[0-9A-Za-z]{4,40}", str(f["bc"]).strip()):
            return "shtrix kod noto'g'ri"
    elif k in ("nk", "ke"):
        f = req["f"]
        nm = " ".join(str(f.get("nm") or "").split())
        if k == "ke" and not req["party"] and not req["party_code"]:
            return "kontragent ko'rsatilmagan"
        if k == "nk" and not nm:
            return "kontragent nomi yo'q"
        if "nm" in f and not 2 <= len(nm) <= 150:
            return "kontragent nomi noto'g'ri"
        for x in ("tel", "tel2"):
            v = str(f.get(x) or "").strip()
            if v and not PHONE_RE.fullmatch(v):
                return "telefon noto'g'ri"
        if len(str(f.get("adr") or "")) > 250:
            return "manzil juda uzun"
        if "grp" in f and f["grp"] not in ("c", "s"):
            return "guruh noto'g'ri"
        if k == "ke" and not any(x in f for x in ("nm", "tel", "tel2", "adr", "reg")):
            return "o'zgarish yo'q"
    elif k == "wo":
        if not req["lines"]:
            return "tovar yo'q"
        for ln in req["lines"]:
            if not ln["name"] and not ln["code"]:
                return "tovar nomi bo'sh"
            if ln["q"] <= 0:
                return "miqdor noto'g'ri: %s" % ln["name"]
    elif k == "undo":
        if not req["target"]:
            return "bekor qilinadigan yozuv ko'rsatilmagan"
    elif k == "bk":
        f = req["f"]
        op = str(f.get("bop") or "")
        if op not in BULK_OPS:
            return "amal turi noto'g'ri"
        if op == "repl" and not bulk_needles(f.get("bfind")):
            return "nima almashtirilishi ko'rsatilmagan"
        if op == "add":
            if not str(f.get("btxt") or ""):
                return "qo'shiladigan matn yo'q"
            w = str(f.get("bwhere") or "")
            if w not in ("start", "end") and not w.isdigit():
                return "qayerga qo'shilishi noto'g'ri"
        if op == "field":
            if str(f.get("bkey") or "") not in BULK_KEYS:
                return "maydon noto'g'ri"
            if not str(f.get("bval") or "").strip():
                return "qiymat bo'sh"
        if op == "stock":
            try:
                if _fnum(f.get("bqty")) < 0:
                    return "qoldiq manfiy bo'lmaydi"
            except Exception:  # noqa: BLE001 — son emas
                return "qoldiq soni noto'g'ri"
    elif k == "de":
        # v5.2 (A5): hujjatni tahrirlash
        if req["dk"] not in EDITABLE_DOCS:
            return "bu turdagi hujjatni o'zgartirib bo'lmaydi"
        if not req["no"]:
            return "hujjat raqami yo'q"
        if len(req["ds_stamp"]) < 8:
            return "hujjat sanasi yo'q"
        if not req["lines"]:
            return "tovar yo'q"
        for ln in req["lines"]:
            if not ln["name"] and not ln["code"]:
                return "tovar nomi bo'sh"
            if ln["q"] <= 0 or ln["ps"] < 0 or ln["pu"] < 0:
                return "miqdor yoki narx noto'g'ri: %s" % ln["name"]
    return None


# 1C konfiguratsiyasidagi Товары / Контрагенты modullari faqat 1C oynasida bor global o'zgaruvchi va funksiyalarga
# murojaat qiladi (мМобилАпп, Вигрузит_док1 ...). Kompyuter dasturi (COM) ulanganda ular yo'q — 1C bir martalik sozlashni so'raydi.
COM_SETUP_HINT = ("1C'da bir martalik sozlash kerak: Konfigurator > Konfiguratsiya > \"Модуль внешнего соединения\" "
                  "(OQING.txt, 4.1 bo'limi)")


def explain_1c_error(text):
    """1C xato matni -> telefonda ko'rinadigan tushuntirish."""
    t = str(text or "")
    if re.search(r"(?i)(переменная не определена|процедура или функция с указанным именем не определена)", t):
        return "%s — %s" % (t[:160], COM_SETUP_HINT)
    return t


def row_values(line, goods, rate=0.0):
    """Tovar qatori qiymatlari. goods = {"cur": "Сум"|"Вал", "unit_pieces": Обьём}. Tovar valyutasidagi narx olinadi;
    telefonda boshqa valyutada yozilgan bo'lsa — 1C kursi bilan aylantiriladi."""
    usd = goods.get("cur") == "Вал"
    q = line["q"]
    price = line["pu"] if usd else line["ps"]
    try:
        rate = float(rate or 0)
    except (TypeError, ValueError):
        rate = 0.0
    if price == 0 and rate > 0:
        price = round(line["ps"] / rate, 4) if usd else round(line["pu"] * rate, 2)
    return {"usd": usd, "q": q, "price": price, "sum": round(q * price, 4 if usd else 2), "pieces": goods.get("unit_pieces") or 0}


def backup_source(conn):
    """Fayl baza papkasidagi 1Cv8.1CD yo'li yoki None (server baza)."""
    m = re.search(r'(?i)\bfile\s*=\s*"?([^";]+)"?', conn or "")
    if not m:
        return None
    return os.path.join(m.group(1).strip(), "1Cv8.1CD")


class Journal:
    """write_journal.jsonl — har natija bir qator. Oxirgisi hisoblanadi."""

    def __init__(self, path):
        self.path = path
        self.items = {}
        try:
            with open(path, encoding="utf-8") as f:
                for line in f:
                    try:
                        e = json.loads(line)
                        if isinstance(e, dict) and e.get("id"):
                            self.items[e["id"]] = e
                    except ValueError:
                        continue
        except OSError:
            pass

    def get(self, rid):
        return self.items.get(rid)

    def add(self, entry):
        self.items[entry["id"]] = entry
        with open(self.path, "a", encoding="utf-8") as f:
            f.write(json.dumps(entry, ensure_ascii=False) + "\n")


# ---------------------------------------------------------------- 1C

class Writer:
    def __init__(self, cfg, cloud, onec, here, now=None):
        self.cfg = cfg
        self.cloud = cloud
        self.onec = onec
        self.here = here
        self.now = now or dt.datetime.now
        self.journal = Journal(os.path.join(here, "write_journal.jsonl"))
        self.backup_dir = os.path.join(here, "backup")
        # v3.12: tovar (shtrix, narx, PLU, yangi tovar) o'zgardi — telefonlarga kodlar/narxlar qayta yuborilsin
        self.goods_changed = False

    # --- bulut ---
    def pending(self):
        v = self.cloud.call("GET", "w/%s" % self.cfg.inbox, timeout=60)
        return v if isinstance(v, dict) else {}

    def answer(self, rid, res):
        body = {"at": {".sv": "timestamp"}, "ok": bool(res.get("ok")), "st": res.get("st", ""), "no": res.get("no", ""),
                "ref": res.get("ref", ""), "e": (res.get("e") or "")[:300]}
        self.cloud.call("PUT", "wr/%s/%s" % (self.cfg.inbox, rid), body, timeout=60, print="silent")
        self.cloud.call("DELETE", "w/%s/%s" % (self.cfg.inbox, rid), timeout=60)

    # --- zaxira ---
    def daily_backup(self):
        """@return (ok, matn). Server baza — zaxira 1C serverida (ok)."""
        src = backup_source(self.cfg.connection)
        if not src:
            return True, "server baza"
        day = self.now().strftime("%Y%m%d")
        os.makedirs(self.backup_dir, exist_ok=True)
        dst = os.path.join(self.backup_dir, "1Cv8_%s.1CD" % day)
        if os.path.exists(dst) and os.path.getsize(dst) > 0:
            return True, dst
        try:
            shutil.copy2(src, dst + ".tmp")
            os.replace(dst + ".tmp", dst)
        except OSError as e:
            try:
                os.remove(dst + ".tmp")
            except OSError:
                pass
            return False, "zaxira olinmadi: %s" % e
        old = sorted(glob.glob(os.path.join(self.backup_dir, "1Cv8_*.1CD")))
        for f in old[:-KEEP_BACKUPS]:
            try:
                os.remove(f)
            except OSError:
                pass
        return True, dst

    # --- asosiy ---
    def run(self):
        """Navbatdagi hammasini bajaradi. @return (bajarildi, xatolar).
        v3.12: hammasi BITTA 1C ulanishida (har so'rovga qayta ulanish 2-5 soniya edi). 1C allaqachon ulangan bo'lsa
        (hisobot tuzilayotgan payt) — o'sha ulanish ishlatiladi va yopilmaydi."""
        items = self.pending()
        if not items:
            return 0, 0
        self._opened = False
        try:
            return self._run(items)
        finally:
            if self._opened:
                self.onec.close()
            self._opened = False

    def conn(self):
        if getattr(self.onec, "c", None) is None:
            self.onec.connect()
            self._opened = True
        return self.onec.c

    def _run(self, items):
        done = errs = 0
        for rid in sorted(items):
            prev = self.journal.get(rid)
            if prev is not None:
                # Oldin bajarilgan (natija yozilmay qolgan) — qayta kiritilmaydi, natija qayta yuboriladi
                self.answer(rid, prev)
                continue
            req = parse_request(rid, items[rid])
            if req is None:
                res = {"id": rid, "ok": False, "st": "err", "e": "so'rov buzuq yoki eski ilova versiyasi"}
            else:
                why = problem(req)
                if why:
                    res = {"id": rid, "ok": False, "st": "err", "e": why}
                else:
                    res = self.apply(req)
            res["at"] = self.now().isoformat(timespec="seconds")
            res.setdefault("k", items[rid].get("k") if isinstance(items[rid], dict) else "")
            self.journal.add(res)
            self.answer(rid, res)
            if res.get("ok"):
                done += 1
            else:
                errs += 1
        return done, errs

    def apply(self, req):
        rid = req["id"]
        if getattr(self.cfg, "write_on", True) is False:
            return {"id": rid, "ok": False, "st": "err", "e": "kompyuterda 1C'ga yozish o'chiq ([write] enabled = 0)"}
        if req["k"] != "undo":
            ok, msg = self.daily_backup()
            if not ok and getattr(self.cfg, "require_backup", True):
                return {"id": rid, "ok": False, "st": "err", "e": msg}
        try:
            c = self.conn()
            k = req["k"]
            if k == "undo":
                return self.undo(c, req)
            if k == "bc":
                return self.barcode(c, req)
            if k == "cp":
                return self.client_pay(c, req)
            if k in ("ci", "ce"):
                return self.cash(c, req)
            if k == "ge":
                return self.goods_edit(c, req)
            if k == "ng":
                return self.new_goods(c, req)
            if k == "rt":
                return self.set_rate(c, req)
            if k == "nk":
                return self.new_party(c, req)
            if k == "ke":
                return self.edit_party(c, req)
            if k == "wo":
                return self.write_off(c, req)
            if k == "de":
                return self.doc_edit(c, req)
            if k == "bk":
                return self.bulk(c, req)
            return self.document(c, req)
        except WriteError as e:
            return {"id": rid, "ok": False, "st": "err", "e": str(e)}
        except SystemExit as e:  # 1C'ga ulanib bo'lmadi
            return {"id": rid, "ok": False, "st": "err", "e": str(e)[:250]}
        except Exception as e:  # noqa: BLE001 — 1C xatosi matni telefonga
            log.exception("1C'ga yozishda xato")
            return {"id": rid, "ok": False, "st": "err", "e": "1C: %s" % explain_1c_error(str(e))[:290]}

    # --- yordamchilar ---
    @staticmethod
    def find_party(c, name, code):
        cat = c.Справочники.Контрагенты
        ref = None
        if code:
            try:
                ref = cat.НайтиПоКоду(int(float(code)))
            except (TypeError, ValueError):
                ref = cat.НайтиПоКоду(code)
            if ref is not None and ref.Пустая():
                ref = None
        if ref is None and name:
            ref = cat.НайтиПоНаименованию(name, True)
            if ref is not None and ref.Пустая():
                ref = None
        if ref is None:
            raise WriteError("1C'da kontragent topilmadi: %s" % (name or code))
        return ref

    @staticmethod
    def find_goods(c, name, code):
        cat = c.Справочники.Товары
        ref = None
        if code:
            try:
                ref = cat.НайтиПоКоду(int(float(code)))
            except (TypeError, ValueError):
                ref = cat.НайтиПоКоду(code)
            if ref is not None and ref.Пустая():
                ref = None
        if ref is None and name:
            ref = cat.НайтиПоНаименованию(name, True)
            if ref is not None and ref.Пустая():
                ref = None
        if ref is None:
            raise WriteError("1C'da tovar topilmadi: %s" % (name or code))
        return ref

    @staticmethod
    def goods_info(c, ref):
        cur = "Вал" if str(c.String(ref.ВидВал)) == str(c.String(c.Перечисления.ВидВал.Вал)) else "Сум"
        try:
            pieces = float(ref.Обьём or 0)
        except (TypeError, ValueError):
            pieces = 0
        return {"cur": cur, "unit_pieces": pieces}

    @staticmethod
    def uuid_of(c, ref):
        return str(c.String(ref.УникальныйИдентификатор()))

    def document(self, c, req):
        k = req["k"]
        doc_name = DOC_OF[k]
        mgr = getattr(c.Документы, doc_name)
        party = self.find_party(c, req["party"], req["party_code"])
        obj = mgr.СоздатьДокумент()
        obj.Дата = self.now()
        obj.Контрагент = party
        rate = c.Константы.Курс.Получить()
        obj.Курс = rate
        if k != "sp":
            obj.Склад = c.Константы.Склад1.Получить()
        try:
            obj.Сотрудник = c.Справочники.Сотрудники.Дукон
        except Exception:  # noqa: BLE001 — bu turda Сотрудник yo'q (ЮкБерувчигаТулов)
            pass
        note = ("Telefondan (Admin): " + req["note"]).strip()[:250]
        if k == "sp":
            obj.НакдСум = req["ns"]
            obj.НакдВал = req["nu"]
            obj.ПластикСум = req["ps"]
            obj.ПластикВал = req["pu"]
            obj.Изох = note
        else:
            obj.Комментарий = note
            total_s = total_u = 0.0
            if k == "in":
                ts = obj.Приход
            elif k == "rs":
                ts = obj.Таблица
            else:
                ts = obj.Расход
            for ln in req["lines"]:
                g = self.find_goods(c, ln["name"], ln["code"])
                v = row_values(ln, self.goods_info(c, g), rate)
                r = ts.Добавить()
                r.Товары = g
                r.ЕдИзм = g.ЕдИзм
                if v["pieces"]:
                    r.Шт = v["pieces"]
                if k == "in":
                    r.Код = g.Код
                    r.Кол1 = v["q"]
                    r.ДопКол = v["q"]
                    r.Кол2 = v["q"]
                    if v["usd"]:
                        r.ЦенаВал1 = v["price"]
                        r.СуммаВал = v["sum"]
                        r.ОбщаяСуммаВал = v["sum"]
                    else:
                        r.ЦенаСум1 = v["price"]
                        r.СуммаСум = v["sum"]
                        r.ОбщаяСуммаСум = v["sum"]
                else:
                    r.Кол = v["q"]
                    if v["usd"]:
                        r.ЦенаВал = v["price"]
                        r.СуммаВал = v["sum"]
                    else:
                        r.ЦенаСум = v["price"]
                        r.СуммаСум = v["sum"]
                    if k == "rs":
                        r.Код = g.Код
                    if k == "rc":
                        r.ВидВал = c.Перечисления.ВидВал.Вал if v["usd"] else c.Перечисления.ВидВал.Сум
                        r.УртачаНархСум = g.ПриходЦена
                        r.УртачаНархВал = g.ПриходВал
                if v["usd"]:
                    total_u += v["sum"]
                else:
                    total_s += v["sum"]
            obj.ВсегоСум = round(total_s, 2)
            obj.ВсегоВал = round(total_u, 4)
            if k == "in":
                obj.ОбщаяСуммаСум = round(total_s, 2)
                obj.ОбщаяСуммаВал = round(total_u, 4)
                obj.Подбор = True
            if k == "rc":
                obj.ВидВал = c.Перечисления.ВидВал.Вал if total_u > 0 and total_s == 0 else c.Перечисления.ВидВал.Сум
        obj.Записать(c.РежимЗаписиДокумента.Проведение)
        ref = obj.Ссылка
        return {"id": req["id"], "ok": True, "st": "done", "no": str(obj.Номер).strip(), "ref": self.uuid_of(c, ref), "doc": doc_name}

    def bulk(self, c, req):
        """
        v5.2 (A11): ko'p tovarga birdan amal. Telefon o'chsa ham shu yerda oxirigacha bajariladi.
        Eski qiymatlar natijaga yoziladi — "Qaytarish" shular bo'yicha tiklaydi.
        """
        f = req["f"]
        op = str(f.get("bop") or "")
        if op == "stock":
            return self.bulk_stock(c, req)
        q = c.NewObject("Запрос")
        q.Текст = ("ВЫБРАТЬ Т.Ссылка КАК Ссылка, Т.Код КАК Код, Т.Наименование КАК Нм, ПРЕДСТАВЛЕНИЕ(Т.ГруппаТовара) КАК Грп "
                   "ИЗ Справочник.Товары КАК Т ГДЕ НЕ Т.ПометкаУдаления")
        sel = q.Выполнить().Выбрать()
        changed = 0
        seen = 0
        old_all = []
        while sel.Следующий():
            code = str(sel.Код)
            name = str(sel.Нм or "")
            group = str(sel.Грп or "")
            if not bulk_covers(f, code, name, group):
                continue
            seen += 1
            go = sel.Ссылка.ПолучитьОбъект()
            if go is None:
                continue
            if op in ("repl", "add"):
                new = bulk_new_name(f, name)
                if not new or new == name:
                    continue
                old_all.append({"c": code, "f": {"Наименование": name}})
                go.Наименование = new[:150]
            else:
                key = str(f.get("bkey"))
                val = str(f.get("bval") or "").strip()
                info = self.goods_info(c, sel.Ссылка)
                old = self._apply_fields(c, go, {key: val}, info["cur"] == "Вал", code)
                keep = {k: self._plain(v) for k, v in old.items() if v is not None}
                old_all.append({"c": code, "f": keep})
            go.Записать()
            changed += 1
            if changed >= BULK_MAX:
                break
        self.goods_changed = True
        return {
            "id": req["id"], "ok": True, "st": "done" if changed else "skip",
            "no": str(changed), "ref": "bulk", "doc": "ТоварларКуп", "seen": seen, "old_all": old_all,
            "e": "" if changed else "o'zgaradigan tovar topilmadi",
        }

    def bulk_stock(self, c, req):
        """
        v5.2 (A11): qamrovdagi tovarlar qoldig'ini belgilangan songa TUSHIRISH — bitta "СписанияТоваров"
        hujjati bilan (1C'dagi hisobdan chiqarish). Qoldig'i allaqachon shu songa teng yoki kam bo'lganlar
        tegilmaydi; oshirish uchun kirim hujjati kerak, u bu yerda qilinmaydi va ro'yxati qaytariladi.
        """
        f = req["f"]
        target = _fnum(f.get("bqty"))
        store = c.Константы.Склад1.Получить()
        q = c.NewObject("Запрос")
        q.Текст = ("ВЫБРАТЬ Т.Ссылка КАК Ссылка, Т.Код КАК Код, Т.Наименование КАК Нм, ПРЕДСТАВЛЕНИЕ(Т.ГруппаТовара) КАК Грп "
                   "ИЗ Справочник.Товары КАК Т ГДЕ НЕ Т.ПометкаУдаления")
        sel = q.Выполнить().Выбрать()
        pairs = []
        need_up = []
        seen = 0
        while sel.Следующий():
            code = str(sel.Код)
            name = str(sel.Нм or "")
            if not bulk_covers(f, code, name, str(sel.Грп or "")):
                continue
            seen += 1
            have = self.stock(c, sel.Ссылка, store)[0]
            if have > target + 1e-9:
                pairs.append((sel.Ссылка, round(have - target, 3)))
            elif have < target - 1e-9:
                need_up.append(name)
            if len(pairs) >= BULK_MAX:
                break
        if not pairs:
            return {"id": req["id"], "ok": True, "st": "skip", "no": "0", "ref": "bulk", "doc": "ТоварларКуп",
                    "seen": seen, "e": "qoldig'i kamaytiriladigan tovar topilmadi"}
        note = "Qoldiq %s ga tushirildi (telefondan)" % ("%g" % target)
        res = self.write_off_doc(c, pairs, note, req["id"])
        res["seen"] = seen
        if need_up:
            res["e"] = "qoldig'i kam — kirim hujjati kerak: %s" % ", ".join(need_up[:10])[:200]
        return res

    def doc_edit(self, c, req):
        """
        v5.2 (A5): tayyor 1C hujjatidagi tovarlarni almashtirish. Hujjat raqami va sanasi bo'yicha topiladi,
        eski qatorlar jurnalga yoziladi (qaytarish uchun), yangi qatorlar yoziladi va hujjat qayta o'tkaziladi.
        Izohga "Admin o'zgartirdi" qo'shiladi — 1C'da kim o'zgartirgani ko'rinib tursin.
        """
        doc_name, table = EDITABLE_DOCS[req["dk"]]
        mgr = getattr(c.Документы, doc_name)
        day = self.stamp_date(req["ds_stamp"])
        ref = mgr.НайтиПоНомеру(req["no"], day)
        if ref is None or ref.Пустая():
            ref = mgr.НайтиПоНомеру(req["no"].lstrip("0"), day)
        if ref is None or ref.Пустая():
            raise WriteError("1C'da hujjat topilmadi: %s %s" % (doc_name, req["no"]))
        obj = ref.ПолучитьОбъект()
        if obj is None:
            raise WriteError("1C'da hujjatni ochib bo'lmadi: %s" % req["no"])
        ts = getattr(obj, table)
        old = []
        for i in range(ts.Количество()):
            r = ts.Получить(i)
            old.append({
                "code": str(getattr(r.Товары, "Код", "")), "q": float(r.Кол or 0),
                "ps": float(getattr(r, "ЦенаСум", 0) or 0), "pu": float(getattr(r, "ЦенаВал", 0) or 0),
            })
        rate = c.Константы.Курс.Получить()
        ts.Очистить()
        total_s = total_u = 0.0
        for ln in req["lines"]:
            g = self.find_goods(c, ln["name"], ln["code"])
            v = row_values(ln, self.goods_info(c, g), rate)
            r = ts.Добавить()
            r.Товары = g
            r.ЕдИзм = g.ЕдИзм
            if v["pieces"]:
                r.Шт = v["pieces"]
            r.Кол = v["q"]
            if v["usd"]:
                r.ЦенаВал = v["price"]
                r.СуммаВал = v["sum"]
                total_u += v["sum"]
            else:
                r.ЦенаСум = v["price"]
                r.СуммаСум = v["sum"]
                total_s += v["sum"]
            if req["dk"] == "b":
                r.Код = g.Код
            if req["dk"] == "q":
                r.ВидВал = c.Перечисления.ВидВал.Вал if v["usd"] else c.Перечисления.ВидВал.Сум
                r.УртачаНархСум = g.ПриходЦена
                r.УртачаНархВал = g.ПриходВал
        obj.ВсегоСум = round(total_s, 2)
        obj.ВсегоВал = round(total_u, 4)
        mark = "Admin o'zgartirdi %s" % self.now().strftime("%d.%m.%Y %H:%M")
        if req["note"]:
            mark += ": " + req["note"]
        prev_note = str(getattr(obj, "Комментарий", "") or "")
        obj.Комментарий = (prev_note + " | " + mark).strip(" |")[:250]
        obj.Записать(c.РежимЗаписиДокумента.Проведение)
        self.goods_changed = True
        return {
            "id": req["id"], "ok": True, "st": "done", "no": str(obj.Номер).strip(),
            "ref": self.uuid_of(c, obj.Ссылка), "doc": doc_name, "tab": table, "note0": prev_note,
            "old_lines": old,
        }

    @staticmethod
    def stamp_date(stamp):
        """yyyyMMddHHmmss -> datetime (kun boshi bo'lsa ham 1C НайтиПоНомеру uchun yetarli)."""
        t = "".join(ch for ch in str(stamp) if ch.isdigit())
        t = (t + "00000000000000")[:14]
        return dt.datetime(int(t[0:4]), int(t[4:6]), int(t[6:8]), int(t[8:10]), int(t[10:12]), int(t[12:14]))

    def barcode(self, c, req):
        g = self.find_goods(c, req["party"], req["party_code"])
        code = req["bc"]
        cat = c.Справочники.Штрих
        old = cat.НайтиПоНаименованию(code, True)
        if old is not None and not old.Пустая():
            if str(old.Товар.Код) == str(g.Код):
                return {"id": req["id"], "ok": True, "st": "skip", "no": code, "ref": self.uuid_of(c, old), "doc": "Штрих", "e": "bu kod allaqachon shu tovarda"}
            raise WriteError("bu shtrix kod 1C'da boshqa tovarda: %s" % old.Товар.Наименование)
        el = cat.СоздатьЭлемент()
        el.Наименование = code
        el.Товар = g
        el.ДатаПоступ = self.now()
        el.Записать()
        self.goods_changed = True
        # Tovarning o'z ro'yxati (ТабШтрихкодов) va asosiy kod bo'sh bo'lsa
        go = g.ПолучитьОбъект()
        try:
            r = go.ТабШтрихкодов.Добавить()
            r.ШтрихКод = code
            r.ДатаПоступ = self.now()
            r.Записано = True
        except Exception:  # noqa: BLE001 — bu bazada jadval yo'q
            pass
        try:
            if not str(go.ШтрихКод or "").strip():
                go.ШтрихКод = code
        except Exception:  # noqa: BLE001
            pass
        go.Записать()
        return {"id": req["id"], "ok": True, "st": "done", "no": code, "ref": self.uuid_of(c, el.Ссылка), "doc": "Штрих", "goods": str(g.Код)}

    # --- v3.12: mijozdan to'lov, kassa ---
    def client_pay(self, c, req):
        party = self.find_party(c, req["party"], req["party_code"])
        obj = c.Документы.Оплата_Клента.СоздатьДокумент()
        obj.Дата = self.now()
        obj.Контрагент = party
        obj.Курс = c.Константы.Курс.Получить()
        try:
            obj.Сотрудник = c.Справочники.Сотрудники.Дукон
        except Exception:  # noqa: BLE001
            pass
        obj.НакдСум = req["ns"]
        obj.НакдВал = req["nu"]
        obj.ПластикСум = req["ps"]
        obj.ПластикВал = req["pu"]
        obj.КликСум = req["ks"]
        obj.КликВал = req["ku"]
        obj.ЧегирмаСум = req["ds"]
        obj.ЧегирмаВал = req["du"]
        obj.Изох = ("Telefondan (Admin): " + req["note"]).strip()[:250]
        obj.Записать(c.РежимЗаписиДокумента.Проведение)
        return {"id": req["id"], "ok": True, "st": "done", "no": str(obj.Номер).strip(), "ref": self.uuid_of(c, obj.Ссылка), "doc": "Оплата_Клента"}

    @staticmethod
    def find_op(c, name, code):
        cat = c.Справочники.Операции
        ref = None
        if code:
            try:
                ref = cat.НайтиПоКоду(int(float(code)))
            except (TypeError, ValueError):
                ref = cat.НайтиПоКоду(code)
            if ref is not None and ref.Пустая():
                ref = None
        if ref is None and name:
            ref = cat.НайтиПоНаименованию(name, True)
            if ref is not None and ref.Пустая():
                ref = None
        if ref is None:
            raise WriteError("1C'da kassa moddasi topilmadi: %s" % (name or code))
        return ref

    def cash(self, c, req):
        doc = DOC_OF[req["k"]]
        obj = getattr(c.Документы, doc).СоздатьДокумент()
        obj.Дата = self.now()
        obj.Операция = self.find_op(c, req["op"], req["op_code"])
        obj.НаличСум = req["ns"]
        obj.ПеречСум = req["ps"]
        obj.КликСум = req["ks"]
        obj.НаличВал = req["nu"]
        obj.ПеречВал = req["pu"]
        obj.Коментария = ("Telefondan (Admin): " + req["note"]).strip()[:250]
        obj.Записать(c.РежимЗаписиДокумента.Проведение)
        return {"id": req["id"], "ok": True, "st": "done", "no": str(obj.Номер).strip(), "ref": self.uuid_of(c, obj.Ссылка), "doc": doc}

    # --- v3.12: tovar kartochkasi va yangi tovar ---
    @staticmethod
    def _price_value(f, key, usd, rate):
        """Telefondagi narx (f[cur]: "s" so'm / "u" $) -> tovar valyutasidagi narx."""
        v = _fnum(f.get(key))
        if v is None:
            return None
        cur = str(f.get("cur") or "").lower()
        try:
            rate = float(rate or 0)
        except (TypeError, ValueError):
            rate = 0.0
        if usd and cur == "s":
            return round(v / rate, 4) if rate > 0 else None
        if not usd and cur == "u":
            return round(v * rate, 2) if rate > 0 else None
        return round(v, 4 if usd else 2)

    @staticmethod
    def price_attr(key, usd):
        if key == "pin":
            return "ПриходВал" if usd else "ПриходЦена"
        return ("ПродажВал%s" if usd else "ПродажЦена%s") % key[1]

    def _check_plu(self, c, plu, own_code):
        if plu <= 0:
            return
        try:
            other = c.Справочники.Товары.НайтиПоРеквизиту("ПЛУ", plu)
        except Exception:  # noqa: BLE001
            return
        if other is not None and not other.Пустая() and str(other.Код) != str(own_code):
            raise WriteError("PLU %d band: %s" % (plu, other.Наименование))

    def _check_barcode_free(self, c, code):
        old = c.Справочники.Штрих.НайтиПоНаименованию(code, True)
        if old is not None and not old.Пустая():
            raise WriteError("bu shtrix kod 1C'da boshqa tovarda: %s" % old.Товар.Наименование)

    def _apply_fields(self, c, go, f, usd, own_code):
        """Tovar obyektiga narx/PLU/nom yozadi. @return eski qiymatlar (bekor qilish uchun)."""
        rate = c.Константы.Курс.Получить()
        old = {}
        for key in PRICE_KEYS:
            if key not in f:
                continue
            v = self._price_value(f, key, usd, rate)
            if v is None:
                raise WriteError("narxni aylantirib bo'lmadi (1C'da kurs yo'q)")
            attr = self.price_attr(key, usd)
            old[attr] = getattr(go, attr)
            setattr(go, attr, v)
        if "plu" in f:
            plu = int(_fnum(f["plu"]))
            self._check_plu(c, plu, own_code)
            old["ПЛУ"] = go.ПЛУ
            go.ПЛУ = plu
            if plu > 0:
                try:
                    if go.Весы is None or go.Весы.Пустая():
                        old["Весы"] = go.Весы
                        go.Весы = c.Справочники.Весы.ОбщиеВесы
                except Exception:  # noqa: BLE001 — bazada Весы yo'q yoki bo'sh qiymat solishtirilmaydi
                    pass
        if "nm" in f:
            nm = " ".join(str(f["nm"]).split())[:150]
            old["Наименование"] = go.Наименование
            go.Наименование = nm
        # v5.1: kartochka maydonlari — guruh, brend, o'lchov birligi, tarozi
        for key, cat in CARD_REFS.items():
            v = " ".join(str(f.get(key) or "").split())
            if not v:
                continue
            ref = getattr(c.Справочники, cat).НайтиПоНаименованию(v, True)
            if ref is None or ref.Пустая():
                raise WriteError("1C'da topilmadi (%s): %s" % (cat, v))
            attr = "ГруппаТовара" if key == "grp" else ("Бренд" if key == "brd" else ("ЕдИзм" if key == "unit" else "Весы"))
            old[attr] = None  # COM havola jurnalga yozilmaydi
            setattr(go, attr, ref)
        for key, attr in CARD_NUMS.items():
            if key not in f or str(f[key]).strip() == "":
                continue
            old[attr] = getattr(go, attr)
            setattr(go, attr, _fnum(f[key]))
        if f.get("typ"):
            kind = "Весовой" if str(f["typ"]) == "w" else "Товар"
            try:
                old["ТипТовара"] = None
                go.ТипТовара = getattr(c.Перечисления.ТипТовара, kind)
            except Exception as e:  # noqa: BLE001 — bu bazada bunday tur bo'lmasligi mumkin
                raise WriteError("1C'da tovar turi yo'q: %s" % kind) from e
        return old

    @staticmethod
    def _plain(v):
        """Jurnalga yoziladigan qiymat (COM obyekt emas)."""
        if isinstance(v, (int, float, str)) or v is None:
            return v
        return None

    def goods_edit(self, c, req):
        g = self.find_goods(c, req["party"], req["party_code"])
        info = self.goods_info(c, g)
        go = g.ПолучитьОбъект()
        old = self._apply_fields(c, go, req["f"], info["cur"] == "Вал", g.Код)
        go.Записать()
        self.goods_changed = True
        keep = {k: self._plain(v) for k, v in old.items() if k != "Весы"}
        return {"id": req["id"], "ok": True, "st": "done", "no": str(g.Код), "ref": self.uuid_of(c, g), "doc": "Товары", "old": keep}

    @staticmethod
    def _ean13(body12):
        """12 raqamga nazorat raqamini qo'shadi (EAN-13)."""
        total = 0
        for i, ch in enumerate(body12):
            total += int(ch) * (1 if i % 2 == 0 else 3)
        return body12 + str((10 - total % 10) % 10)

    def _auto_barcode(self, c, code):
        """v5.1: ichki shtrix kod — "2" bilan boshlanadi (do'kon ichidagi diapazon), bo'sh raqam tanlanadi."""
        digits = "".join(ch for ch in str(code) if ch.isdigit())[-9:] or "0"
        for n in range(100):
            bc = self._ean13("2" + digits.rjust(9, "0") + ("%02d" % n))
            old = c.Справочники.Штрих.НайтиПоНаименованию(bc, True)
            if old is None or old.Пустая():
                return bc
        raise WriteError("avtomatik shtrix kod yaratib bo'lmadi")

    def new_goods(self, c, req):
        f = req["f"]
        # v5.1: namuna tovar ixtiyoriy — berilmasa bo'sh kartochka ochiladi (1C'dagidek)
        tpl = None
        if str(req.get("party") or "").strip() or str(req.get("party_code") or "").strip():
            tpl = self.find_goods(c, req["party"], req["party_code"])
        nm = " ".join(str(f.get("nm") or "").split())[:150]
        dup = c.Справочники.Товары.НайтиПоНаименованию(nm, True)
        if dup is not None and not dup.Пустая():
            raise WriteError("bu nomli tovar 1C'da bor (kodi %s)" % dup.Код)
        bc = str(f.get("bc") or "").strip()
        if bc:
            self._check_barcode_free(c, bc)
        info = self.goods_info(c, tpl) if tpl is not None else {"cur": "Сум"}
        if tpl is not None:
            obj = tpl.ПолучитьОбъект().Скопировать()
        else:
            obj = c.Справочники.Товары.СоздатьЭлемент()
        try:
            obj.УстановитьНовыйКод()
        except Exception:  # noqa: BLE001 — avtomatik raqamlashda kerak emas
            pass
        for tab in ("ТабШтрихкодов", "ТабКалкуляция", "ТабКалкуляция2", "ТабСтоимостьРабот"):
            try:
                getattr(obj, tab).Очистить()
            except Exception:  # noqa: BLE001
                pass
        obj.ШтрихКод = ""
        obj.ПЛУ = 0
        try:
            obj.Ост = 0
        except Exception:  # noqa: BLE001
            pass
        fields = dict(f)
        fields["nm"] = nm
        fields.pop("bc", None)
        self._apply_fields(c, obj, fields, info["cur"] == "Вал", None)
        # v5.1: "avtomatik shtrix" tanlansa — 1C tomonida bo'sh kod yaratiladi
        if not bc and str(f.get("abc") or "") == "1":
            bc = self._auto_barcode(c, obj.Код)
        if bc:
            # Товары.ПриЗаписи o'zi Справочник.Штрих'ga yozadi (1C'da qo'lda kiritgandagidek)
            obj.ШтрихКод = bc
        obj.Записать()
        self.goods_changed = True
        return {"id": req["id"], "ok": True, "st": "done", "no": str(obj.Код), "ref": self.uuid_of(c, obj.Ссылка), "doc": "Товары", "new": True, "bc": bc}

    # --- v4.1: kontragentlar ---
    @staticmethod
    def find_region(c, name):
        name = " ".join(str(name or "").split())
        if not name:
            return None
        ref = c.Справочники.Регион.НайтиПоНаименованию(name, True)
        if ref is None or ref.Пустая():
            raise WriteError("1C'da region topilmadi: %s" % name)
        return ref

    def _apply_party(self, c, obj, f):
        old = {}
        for key, attr in PARTY_ATTR.items():
            if key not in f:
                continue
            v = " ".join(str(f[key] or "").split()) if key == "nm" else str(f[key] or "").strip()
            old[attr] = self._plain(getattr(obj, attr))
            setattr(obj, attr, v[:150] if key == "nm" else v[:250])
        for key, attr in PARTY_NUMS.items():
            if key not in f or str(f[key]).strip() == "":
                continue
            old[attr] = getattr(obj, attr)
            setattr(obj, attr, _fnum(f[key]))
        if str(f.get("reg") or "").strip():
            old["Регион"] = None  # COM havola jurnalga yozilmaydi (bekor qilishda region o'zgarmaydi)
            obj.Регион = self.find_region(c, f["reg"])
        return old

    def new_party(self, c, req):
        f = req["f"]
        nm = " ".join(str(f.get("nm") or "").split())[:150]
        cat = c.Справочники.Контрагенты
        dup = cat.НайтиПоНаименованию(nm, True)
        if dup is not None and not dup.Пустая():
            raise WriteError("bu nomli kontragent 1C'da bor (kodi %s)" % dup.Код)
        obj = cat.СоздатьЭлемент()
        obj.Родитель = cat.Поставщики if f.get("grp") == "s" else cat.Клиенты
        try:
            obj.УстановитьНовыйКод()
        except Exception:  # noqa: BLE001 — avtomatik raqamlashda kerak emas
            pass
        fields = dict(f)
        fields["nm"] = nm
        self._apply_party(c, obj, fields)
        obj.Записать()
        return {"id": req["id"], "ok": True, "st": "done", "no": str(obj.Код), "ref": self.uuid_of(c, obj.Ссылка), "doc": "Контрагенты", "new": True}

    def edit_party(self, c, req):
        ref = self.find_party(c, req["party"], req["party_code"])
        obj = ref.ПолучитьОбъект()
        f = dict(req["f"])
        if "nm" in f:
            nm = " ".join(str(f["nm"] or "").split())
            dup = c.Справочники.Контрагенты.НайтиПоНаименованию(nm, True)
            if dup is not None and not dup.Пустая() and str(dup.Код) != str(ref.Код):
                raise WriteError("bu nomli kontragent 1C'da bor (kodi %s)" % dup.Код)
        old = self._apply_party(c, obj, f)
        obj.Записать()
        keep = {k: v for k, v in old.items() if v is not None}
        return {"id": req["id"], "ok": True, "st": "done", "no": str(ref.Код), "ref": self.uuid_of(c, ref), "doc": "Контрагенты", "old": keep}

    # --- v4.1: hisobdan chiqarish ---
    def stock(self, c, goods, store):
        """Tovar qoldig'i (miqdor, summa so'm, summa $) — РегистрНакопления.Товар."""
        q = c.NewObject("Запрос")
        q.Текст = ("ВЫБРАТЬ ЕСТЬNULL(СУММА(О.КоличествоОстаток), 0) КАК Кол, ЕСТЬNULL(СУММА(О.СуммаСумОстаток), 0) КАК Сум, "
                   "ЕСТЬNULL(СУММА(О.СуммаВалОстаток), 0) КАК Вал ИЗ РегистрНакопления.Товар.Остатки(, Товар = &Т И Склад = &С) КАК О")
        q.УстановитьПараметр("Т", goods)
        q.УстановитьПараметр("С", store)
        r = q.Выполнить().Выбрать()
        if r.Следующий():
            return float(r.Кол or 0), float(r.Сум or 0), float(r.Вал or 0)
        return 0.0, 0.0, 0.0

    def write_off(self, c, req):
        """Telefondan kelgan ro'yxat bo'yicha hisobdan chiqarish."""
        pairs = [(self.find_goods(c, ln["name"], ln["code"]), ln["q"]) for ln in req["lines"]]
        return self.write_off_doc(c, pairs, req["note"], req["id"])

    def write_off_doc(self, c, pairs, note, req_id):
        """СписанияТоваров: tannarx — o'rtacha (qoldiq summasi / qoldiq miqdori), qoldiq bo'lmasa — kirim narxi.
        1C hujjat modulida dollarli tovar uchun xato bor (Стр.ОстКол) — bunday hujjatda harakatlar shu yerda yoziladi
        (modul bilan aynan bir xil: Товар — расход, Прибль — СписанияМахсулот).
        [pairs] — (tovar havolasi, miqdor) juftliklari."""
        store = c.Константы.Склад1.Получить()
        rate = c.Константы.Курс.Получить()
        obj = c.Документы.СписанияТоваров.СоздатьДокумент()
        obj.Дата = self.now()
        obj.Склад = store
        obj.Курс = rate
        obj.Подбор = True
        obj.Комментарий = ("Telefondan (Admin): " + str(note or "")).strip()[:250]
        total_s = total_u = 0.0
        any_usd = False
        short = []
        rows = []
        for g, qty in pairs:
            usd = self.goods_info(c, g)["cur"] == "Вал"
            bq, bs, bu = self.stock(c, g, store)
            avg_s = bs / bq if bq > 0 else float(g.ПриходЦена or 0)
            avg_u = bu / bq if bq > 0 else float(g.ПриходВал or 0)
            r = obj.Таблица.Добавить()
            r.Товары = g
            r.ЕдИзм = g.ЕдИзм
            r.Код = g.Код
            r.Кол = qty
            r.Ост = bq
            r.ОстСуммаСум = bs
            r.ОстСуммаВал = bu
            r.УртачаНархСум = round(avg_s, 2)
            r.УртачаНархВал = round(avg_u, 4)
            if usd:
                any_usd = True
                r.СуммаВал = round(bu if qty == bq else qty * avg_u, 4)
                total_u += r.СуммаВал
            else:
                r.СуммаСум = round(bs if qty == bq else qty * avg_s, 2)
                total_s += r.СуммаСум
            if qty > bq:
                short.append(str(g.Наименование))
            rows.append((g, qty, r, usd))
        obj.ВсегоСум = round(total_s, 2)
        obj.ВсегоВал = round(total_u, 4)
        manual = False
        if not any_usd:
            obj.Записать(c.РежимЗаписиДокумента.Проведение)
        else:
            manual = True
            obj.ОбменДанными.Загрузка = True
            obj.Проведен = True
            mv = obj.Движения
            mv.Товар.Записывать = True
            mv.Прибль.Записывать = True
            for g, qty, r, usd in rows:
                m = mv.Товар.Добавить()
                m.ВидДвижения = c.ВидДвиженияНакопления.Расход
                m.Период = obj.Дата
                m.Склад = store
                m.Товар = g
                m.Количество = qty
                if usd:
                    m.СуммаВал = r.СуммаВал
                else:
                    m.СуммаСум = r.СуммаСум
                p = mv.Прибль.Добавить()
                p.Период = obj.Дата
                p.ХаракатТури = c.Перечисления.ХарактаТури.СписанияМахсулот
                p.Товар = g
                p.Кол = qty
                p.ПриходСумма = r.СуммаСум
                p.ПриходВал = r.СуммаВал
            obj.Записать()
        res = {"id": req_id, "ok": True, "st": "done", "no": str(obj.Номер).strip(), "ref": self.uuid_of(c, obj.Ссылка),
               "doc": "СписанияТоваров", "manual": manual}
        if short:
            res["e"] = "qoldiqdan ko'p chiqarildi: %s" % ", ".join(short)[:200]
        return res

    # --- v4.0: dollar kursi ---
    def set_rate(self, c, req):
        rate = round(_fnum(req["f"].get("rate")), 2)
        old = c.Константы.Курс.Получить()
        c.Константы.Курс.Установить(rate)
        try:
            m = c.РегистрыСведений.Курс.СоздатьМенеджерЗаписи()
            m.Дата = self.now()
            m.Сумма = rate
            m.Записать()
        except Exception:  # noqa: BLE001 — tarix registri bo'lmasa ham kurs o'rnatildi
            log.warning("Курс registriga yozilmadi")
        return {"id": req["id"], "ok": True, "st": "done", "no": str(rate), "ref": "rate", "doc": "Курс", "old": {"rate": self._plain(old)}}

    def undo(self, c, req):
        t = self.journal.get(req["target"])
        if t is None or not t.get("ok") or not t.get("ref"):
            raise WriteError("bekor qilinadigan yozuv jurnalda topilmadi")
        if t.get("st") == "skip":
            return {"id": req["id"], "ok": True, "st": "done", "no": t.get("no", ""), "ref": t["ref"], "e": "o'zgartirilmagan edi"}
        doc = t.get("doc", "")
        if doc == "Курс":
            old = (t.get("old") or {}).get("rate")
            if old:
                c.Константы.Курс.Установить(old)
            self.journal.add(dict(t, st="undone", undone_by=req["id"]))
            return {"id": req["id"], "ok": True, "st": "done", "no": str(old or ""), "ref": "rate"}
        if doc == "ТоварларКуп":
            # v5.2 (A11): ko'p tovarga qilingan amalni qaytarish — har tovarga eski qiymatlari yoziladi
            back = 0
            for row in t.get("old_all") or []:
                g = c.Справочники.Товары.НайтиПоКоду(int(row["c"]) if str(row["c"]).isdigit() else row["c"])
                if g is None or g.Пустая():
                    continue
                go = g.ПолучитьОбъект()
                if go is None:
                    continue
                for attr, v in (row.get("f") or {}).items():
                    if v is not None:
                        setattr(go, attr, v)
                go.Записать()
                back += 1
            self.goods_changed = True
            self.journal.add(dict(t, st="undone", undone_by=req["id"]))
            return {"id": req["id"], "ok": True, "st": "done", "no": str(back), "ref": "bulk"}
        uid = c.NewObject("УникальныйИдентификатор", t["ref"])
        if doc == "Товары":
            obj = c.Справочники.Товары.ПолучитьСсылку(uid).ПолучитьОбъект()
            if obj is None:
                raise WriteError("1C'da tovar topilmadi")
            if t.get("new"):
                obj.УстановитьПометкуУдаления(True)
                if t.get("bc"):
                    sh = c.Справочники.Штрих.НайтиПоНаименованию(t["bc"], True)
                    if sh is not None and not sh.Пустая():
                        sh.ПолучитьОбъект().УстановитьПометкуУдаления(True)
            else:
                for attr, v in (t.get("old") or {}).items():
                    if v is not None:
                        setattr(obj, attr, v)
                obj.Записать()
            self.goods_changed = True
        elif doc == "Контрагенты":
            obj = c.Справочники.Контрагенты.ПолучитьСсылку(uid).ПолучитьОбъект()
            if obj is None:
                raise WriteError("1C'da kontragent topilmadi")
            if t.get("new"):
                obj.УстановитьПометкуУдаления(True)
            else:
                for attr, v in (t.get("old") or {}).items():
                    if v is not None:
                        setattr(obj, attr, v)
                obj.Записать()
        elif doc == "Штрих":
            ref = c.Справочники.Штрих.ПолучитьСсылку(uid)
            obj = ref.ПолучитьОбъект()
            if obj is None:
                raise WriteError("1C'da yozuv topilmadi")
            code = str(obj.Наименование).strip()
            go = obj.Товар.ПолучитьОбъект()
            obj.УстановитьПометкуУдаления(True)
            try:
                tab = go.ТабШтрихкодов
                for i in range(tab.Количество() - 1, -1, -1):
                    if str(tab.Получить(i).ШтрихКод).strip() == code:
                        tab.Удалить(i)
                if str(go.ШтрихКод or "").strip() == code:
                    go.ШтрихКод = ""
                go.Записать()
            except Exception:  # noqa: BLE001
                pass
        else:
            mgr = getattr(c.Документы, doc)
            ref = mgr.ПолучитьСсылку(uid)
            obj = ref.ПолучитьОбъект()
            if obj is None:
                raise WriteError("1C'da hujjat topilmadi")
            obj.УстановитьПометкуУдаления(True)
        self.journal.add(dict(t, st="undone", undone_by=req["id"]))
        try:
            self.cloud.call("PATCH", "wr/%s/%s" % (self.cfg.inbox, req["target"]), {"st": "undone", "at": {".sv": "timestamp"}}, timeout=60, print="silent")
        except Exception:  # noqa: BLE001
            pass
        return {"id": req["id"], "ok": True, "st": "done", "no": t.get("no", ""), "ref": t["ref"]}
