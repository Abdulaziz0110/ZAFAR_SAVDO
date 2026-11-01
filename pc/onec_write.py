#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Admin -> 1C yozish ("Tasdiqlab kiritish", v3.2). Faqat Admin ilovasida egasi TASDIQLAGAN so'rovlar keladi:

  w/{inbox}/{id} = {at, k, by, p}   k: in — kirim (Поступления), sp — ta'minotchiga to'lov (ЮкБерувчигаТулов),
                                       rs — ta'minotchiga qaytarish, rc — mijoz qaytargan tovar, bc — shtrix kod,
                                       undo — oldingi yozuvni bekor qilish (o'chirish belgisi)
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

KINDS = {"in", "sp", "rs", "rc", "bc", "undo"}
DOC_OF = {"in": "Поступления", "sp": "ЮкБерувчигаТулов", "rs": "Возврат_товара_поставщику", "rc": "Возврат_товара_от_клиента"}
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
    return {
        "id": rid, "k": k, "by": str(item.get("by") or ""), "party": str(p.get("c") or "").strip(), "party_code": str(p.get("cc") or "").strip(),
        "note": str(p.get("n") or "")[:300], "lines": lines, "ns": g("ns"), "nu": g("nu"), "ps": g("ps"), "pu": g("pu"),
        "bc": str(p.get("bc") or "").strip(), "target": str(p.get("t") or "").strip(),
    }


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
    elif k == "undo":
        if not req["target"]:
            return "bekor qilinadigan yozuv ko'rsatilmagan"
    return None


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
        """Navbatdagi hammasini bajaradi. @return (bajarildi, xatolar)."""
        items = self.pending()
        if not items:
            return 0, 0
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
        self.onec.connect()
        try:
            c = self.onec.c
            if req["k"] == "undo":
                return self.undo(c, req)
            if req["k"] == "bc":
                return self.barcode(c, req)
            return self.document(c, req)
        except WriteError as e:
            return {"id": rid, "ok": False, "st": "err", "e": str(e)}
        except Exception as e:  # noqa: BLE001 — 1C xatosi matni telefonga
            log.exception("1C'ga yozishda xato")
            return {"id": rid, "ok": False, "st": "err", "e": "1C: %s" % str(e)[:250]}
        finally:
            self.onec.close()

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

    def undo(self, c, req):
        t = self.journal.get(req["target"])
        if t is None or not t.get("ok") or not t.get("ref"):
            raise WriteError("bekor qilinadigan yozuv jurnalda topilmadi")
        if t.get("st") == "skip":
            return {"id": req["id"], "ok": True, "st": "done", "no": t.get("no", ""), "ref": t["ref"], "e": "o'zgartirilmagan edi"}
        uid = c.NewObject("УникальныйИдентификатор", t["ref"])
        doc = t.get("doc", "")
        if doc == "Штрих":
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
