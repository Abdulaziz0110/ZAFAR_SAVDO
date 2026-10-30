#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
1C → Excel → telefon (avtomatik). 1C 8.3 o'rnatilgan kompyuterda fonda ishlaydi.

Har N soatda (N = 1-ilovada tanlanadi: 1/2/3 soat, 0 — o'chiq) 1C bazaga COM orqali ODDIY foydalanuvchi bilan ulanadi,
har bir mijoz / yuk beruvchi uchun "kartochka" hisobotini tuzadi, Excel (.xlsx) qilib saqlaydi va Firebase'ga
`in/{inbox}/{id}` ga qo'yadi. 1-ilova (telefon) o'zi yuklab, 1C tarixiga import qiladi (xuddi qo'lda Excel yuklagandek).

1C'ga HECH NARSA YOZILMAYDI: faqat hisobot tuziladi. Konfigurator va server paroli kerak emas.

Talab: Windows, 1C:Enterprise 8.3 (COM komponenti "V83.COMConnector" ro'yxatdan o'tgan), Python 3.8+, pywin32:
    pip install pywin32

Buyruqlar:
  python onec_export.py --discover   — 1C'dagi hisobotlar/ma'lumotnomalar ro'yxati -> discover.txt (dasturchiga yuboring)
  python onec_export.py --once       — hozir bir marta eksport qilish
  python onec_export.py --one "Dadaxon Aka (TRZ)"  — faqat bitta mijoz (sinov; fayl export/ papkada qoladi)
  python onec_export.py --status     — bulutdagi jadval va oxirgi holat
  python onec_export.py              — doimiy ishlash (odatda onec_export.vbs orqali oynasiz)

Sozlama: forwarder.ini ([firebase] url/secret/inbox — 1-ilova beradi; [onec] — o'zingiz to'ldirasiz).
"""
import argparse
import base64
import configparser
import datetime as dt
import gzip
import hashlib
import json
import logging
import logging.handlers
import os
import re
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request

HERE = os.path.dirname(os.path.abspath(sys.argv[0] if getattr(sys, "frozen", False) else __file__))
CONFIG = os.path.join(HERE, "forwarder.ini")
LOG = os.path.join(HERE, "onec_export.log")
STATE = os.path.join(HERE, "export_state.json")
OUT_DIR = os.path.join(HERE, "export")
DISCOVER = os.path.join(HERE, "discover.txt")

MAX_FILE = 8 * 1024 * 1024        # bitta Excel shundan katta bo'lsa yuborilmaydi (jurnalga yoziladi)
POLL_SEC = 10 * 60                # jadval o'chiq bo'lsa — shuncha vaqtda bir tekshiriladi
log = logging.getLogger("onec_export")


def setup_logging(verbose=False):
    log.setLevel(logging.DEBUG if verbose else logging.INFO)
    fmt = logging.Formatter("%(asctime)s %(levelname)s %(message)s")
    fh = logging.handlers.RotatingFileHandler(LOG, maxBytes=1_000_000, backupCount=3, encoding="utf-8")
    fh.setFormatter(fmt)
    log.addHandler(fh)
    if sys.stderr is not None and sys.stderr.isatty():
        sh = logging.StreamHandler()
        sh.setFormatter(fmt)
        log.addHandler(sh)


# ---------------------------------------------------------------- sozlama

def normalize_url(raw):
    u = raw.strip().rstrip("/")
    if not u:
        return ""
    if not u.startswith("http"):
        u = "https://" + u
    u = u.replace("http://", "https://", 1)
    return "https://" + u[len("https://"):].split("/")[0]


def is_key(s):
    return 24 <= len(s) <= 64 and s.isascii() and s.isalnum()


class Config:
    def __init__(self, path=CONFIG):
        if not os.path.exists(path):
            raise SystemExit("forwarder.ini topilmadi: %s\n1-ilova ▸ Mijozlar ▸ 'Kompyuter sozlamasi' matnini shu nom bilan saqlang." % path)
        cp = configparser.ConfigParser(interpolation=None)
        cp.read(path, encoding="utf-8-sig")
        fb = cp["firebase"] if cp.has_section("firebase") else {}
        self.url = normalize_url(fb.get("url", ""))
        self.secret = fb.get("secret", "").strip()
        self.inbox = fb.get("inbox", "").strip()
        oc = cp["onec"] if cp.has_section("onec") else {}
        g = lambda k, d="": (oc.get(k, d) or d).strip()  # noqa: E731
        self.connection = g("connection")
        self.user = g("user")
        self.password = oc.get("password", "") if oc else ""
        # Hisobot: Отчеты.<report>. build = skd (СкомпоноватьРезультат) yoki method (<method>(ТабличныйДокумент))
        self.report = g("report")
        self.build = g("build", "skd").lower()
        self.method = g("method")
        self.param_from = g("period_from", "НачалоПериода")
        self.param_to = g("period_to", "КонецПериода")
        self.client_field = g("client_field", "Контрагент")
        # Kimlar: Справочник.<catalog>; supplier_flag — "yuk beruvchi" belgisi (rekvizit nomi, ixtiyoriy)
        self.catalog = g("catalog", "Контрагенты")
        self.supplier_flag = g("supplier_flag")
        # Faqat davr ichida harakati borlar (tezroq): РегистрНакопления.<active_register>, o'lchov <client_field>
        self.active_register = g("active_register")
        self.only = [x.strip() for x in g("only").split(";") if x.strip()]
        self.skip = [x.strip() for x in g("skip").split(";") if x.strip()]
        self.default_hours = int(g("hours", "2") or 2)
        self.default_days = int(g("days", "7") or 7)

    def check_cloud(self):
        if not self.url or not self.secret:
            raise SystemExit("forwarder.ini: [firebase] url va secret kerak (1-ilova beradi)")
        if not is_key(self.inbox):
            raise SystemExit("forwarder.ini: [firebase] inbox kaliti yo'q yoki noto'g'ri — 1-ilovadan sozlamani qayta oling")

    def check_onec(self, need_report=True):
        if not self.connection:
            raise SystemExit("forwarder.ini: [onec] connection bo'sh. Masalan: connection = File=\"D:\\1C\\Baza\";")
        if need_report and not self.report:
            raise SystemExit("forwarder.ini: [onec] report bo'sh. Avval: python onec_export.py --discover")

    def conn_string(self):
        s = self.connection.strip()
        if not re.search(r"(?i)\b(file|srvr)\s*=", s):
            s = 'File="%s";' % s.strip('"')     # faqat papka yozilgan bo'lsa
        if not s.endswith(";"):
            s += ";"
        if self.user and not re.search(r"(?i)\busr\s*=", s):
            s += 'Usr="%s";' % self.user.replace('"', '""')
        if self.password and not re.search(r"(?i)\bpwd\s*=", s):
            s += 'Pwd="%s";' % self.password.replace('"', '""')
        return s


# ---------------------------------------------------------------- bulut

class HttpError(Exception):
    def __init__(self, code, body):
        super().__init__("HTTP %s: %s" % (code, body[:200]))
        self.code = code


class Cloud:
    def __init__(self, cfg):
        self.cfg = cfg

    def call(self, method, path, body=None, timeout=60, **q):
        q["auth"] = self.cfg.secret
        url = "%s/%s.json?%s" % (self.cfg.url, path.strip("/"), urllib.parse.urlencode(q))
        data = None if body is None else json.dumps(body, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
        req = urllib.request.Request(url, data=data, method=method)
        req.add_header("Content-Type", "application/json; charset=utf-8")
        try:
            with urllib.request.urlopen(req, timeout=timeout) as r:
                return json.loads(r.read().decode("utf-8") or "null")
        except urllib.error.HTTPError as e:
            raise HttpError(e.code, e.read().decode("utf-8", "replace"))

    def schedule(self):
        """1-ilova yozgan jadval: {h: soat (0 — o'chiq), d: necha kunlik}."""
        v = self.call("GET", "p/%s/exp" % self.cfg.inbox, timeout=30) or {}
        return v if isinstance(v, dict) else {}

    def status(self, text, ok):
        self.call("PUT", "p/%s/st" % self.cfg.inbox, {"at": {".sv": "timestamp"}, "text": text[:300], "ok": bool(ok)}, timeout=30, print="silent")

    def upload(self, key, name, raw, client, kind):
        body = {
            "at": {".sv": "timestamp"}, "n": name, "gz": True, "c": client[:120], "k": kind,
            "b": base64.b64encode(gzip.compress(raw, 9)).decode("ascii"),
        }
        self.call("PUT", "in/%s/%s" % (self.cfg.inbox, key), body, timeout=120, print="silent")


# ---------------------------------------------------------------- 1C (COM)

class OneC:
    """Faqat O'QISH: ma'lumotnoma ro'yxati va hisobot tuzish. Hech narsa yozilmaydi."""

    def __init__(self, cfg, conn=None):
        self.cfg = cfg
        self.c = conn

    def connect(self):
        if self.c is not None:
            return self
        try:
            import pythoncom  # noqa: F401  (pywin32)
            import win32com.client
        except ImportError:
            raise SystemExit("pywin32 o'rnatilmagan: pip install pywin32")
        pythoncom.CoInitialize()
        try:
            connector = win32com.client.Dispatch("V83.COMConnector")
        except Exception as e:
            raise SystemExit("1C COM komponenti topilmadi (V83.COMConnector). 1C 8.3 o'rnatilganini tekshiring yoki "
                             "administrator sifatida: regsvr32 \"C:\\Program Files\\1cv8\\<versiya>\\bin\\comcntr.dll\"\n%s" % e)
        log.info("1C ga ulanilmoqda…")
        self.c = connector.Connect(self.cfg.conn_string())
        return self

    def close(self):
        # Litsenziya band bo'lib turmasin — har aylanishdan keyin uziladi.
        self.c = None
        try:
            import gc
            gc.collect()
        except Exception:
            pass

    # --- yordamchi ---
    @staticmethod
    def items(col):
        n = col.Количество()
        return [col.Получить(i) for i in range(n)]

    def query(self, text, fields, **params):
        """So'rov natijasi: har qator — {maydon: qiymat} (Ссылка COM obyekt bo'lib qoladi)."""
        q = self.c.NewObject("Запрос")
        q.Текст = text
        for k, v in params.items():
            q.УстановитьПараметр(k, v)
        res = q.Выполнить().Выбрать()
        rows = []
        while res.Следующий():
            rows.append({f: getattr(res, f) for f in fields})
        return rows

    # --- kimlar ---
    def counterparties(self, d_from, d_to):
        """[{ref, name, flag}] — ma'lumotnomadagi barcha (o'chirilmagan) kontragentlar."""
        cat = self.cfg.catalog
        sel = "Т.Ссылка КАК Ссылка, Т.Наименование КАК Наименование"
        fields = ["Ссылка", "Наименование"]
        if self.cfg.supplier_flag:
            sel += ", Т.%s КАК Флаг" % self.cfg.supplier_flag
            fields.append("Флаг")
        base = "ВЫБРАТЬ %s ИЗ Справочник.%s КАК Т ГДЕ НЕ Т.ПометкаУдаления" % (sel, cat)
        try:
            raw = self.query(base + " И НЕ Т.ЭтоГруппа", fields)
        except Exception:
            raw = self.query(base, fields)  # ierarxiyasiz ma'lumotnoma
        rows = [{"ref": r["Ссылка"], "name": str(r["Наименование"]).strip(), "flag": bool(r.get("Флаг") or False)} for r in raw]
        if self.cfg.active_register:
            try:
                act = self.query(
                    "ВЫБРАТЬ РАЗЛИЧНЫЕ Р.%s.Наименование КАК Наименование ИЗ РегистрНакопления.%s КАК Р "
                    "ГДЕ Р.Период МЕЖДУ &A И &B" % (self.cfg.client_field, self.cfg.active_register),
                    ["Наименование"], A=d_from, B=d_to)
                names = {str(r["Наименование"]).strip() for r in act}
                rows = [r for r in rows if r["name"] in names]
            except Exception as e:
                log.warning("active_register so'rovi ishlamadi (%s) — hamma kontragentlar tekshiriladi", e)
        return rows

    # --- hisobot ---
    def _set_param(self, settings, name, value):
        p = settings.ПараметрыДанных.НайтиЗначениеПараметра(self.c.NewObject("ПараметрКомпоновкиДанных", name))
        if p is None:
            return False
        p.Значение = value
        p.Использование = True
        return True

    def _set_filter(self, settings, field, value):
        """Отбор: <field> = value (bor element bo'lsa — o'sha, yo'q bo'lsa qo'shiladi)."""
        flt = settings.Отбор
        eq = self.c.ВидСравненияКомпоновкиДанных.Равно
        for el in self.items(flt.Элементы):
            try:
                if str(el.ЛевоеЗначение) == field:
                    el.ВидСравнения = eq
                    el.ПравоеЗначение = value
                    el.Использование = True
                    return
            except Exception:
                continue
        el = flt.Элементы.Добавить(self.c.Тип("ЭлементОтбораКомпоновкиДанных"))  # global Тип() — COM ulanishida ham bor
        el.ЛевоеЗначение = self.c.NewObject("ПолеКомпоновкиДанных", field)
        el.ВидСравнения = eq
        el.ПравоеЗначение = value
        el.Использование = True

    def build_xlsx(self, ref, d_from, d_to):
        """Bitta kontragent uchun hisobot -> .xlsx baytlari."""
        rep = getattr(self.c.Отчеты, self.cfg.report).Создать()
        doc = self.c.NewObject("ТабличныйДокумент")
        if self.cfg.build == "method":
            # Oddiy (SKD siz) hisobot: rekvizitlar + eksport protsedura
            setattr(rep, self.cfg.param_from, d_from)
            setattr(rep, self.cfg.param_to, d_to)
            setattr(rep, self.cfg.client_field, ref)
            getattr(rep, self.cfg.method or "СформироватьОтчет")(doc)
        else:
            sk = rep.КомпоновщикНастроек
            st = sk.Настройки
            try:
                empty = st.Структура.Количество() == 0
            except Exception:
                empty = True
            if empty:
                md = self.c.Метаданные.Отчеты.Найти(self.cfg.report)
                schema = rep.ПолучитьМакет(md.ОсновнаяСхемаКомпоновкиДанных.Имя)
                sk.Инициализировать(self.c.NewObject("ИсточникДоступныхНастроекКомпоновкиДанных", schema))
                sk.ЗагрузитьНастройки(schema.НастройкиПоУмолчанию)
                st = sk.Настройки
            ok_from = self._set_param(st, self.cfg.param_from, d_from)
            ok_to = self._set_param(st, self.cfg.param_to, d_to)
            if not (ok_from and ok_to):
                # Ba'zi hisobotlarda bitta "Период" (СтандартныйПериод) parametri bo'ladi
                per = self.c.NewObject("СтандартныйПериод", d_from, d_to)
                if not self._set_param(st, "Период", per):
                    log.debug("davr parametri topilmadi (%s/%s)", self.cfg.param_from, self.cfg.param_to)
            if not self._set_param(st, self.cfg.client_field, ref):
                self._set_filter(st, self.cfg.client_field, ref)
            rep.СкомпоноватьРезультат(doc)
        fd, path = tempfile.mkstemp(suffix=".xlsx")
        os.close(fd)
        try:
            doc.Записать(path, self.c.ТипФайлаТабличногоДокумента.XLSX)
            with open(path, "rb") as f:
                return f.read()
        finally:
            try:
                os.remove(path)
            except OSError:
                pass

    # --- discover ---
    def discover(self):
        c = self.c
        md = c.Метаданные
        out = []
        w = out.append
        w("1C konfiguratsiya: %s (%s) versiya %s" % (md.Имя, md.Синоним, md.Версия))
        w("")
        w("== Hisobotlar (Отчеты) ==")
        for r in self.items(md.Отчеты):
            schema = ""
            try:
                if r.ОсновнаяСхемаКомпоновкиДанных is not None:
                    schema = " [SKD: %s]" % r.ОсновнаяСхемаКомпоновкиДанных.Имя
            except Exception:
                pass
            w("%s — %s%s" % (r.Имя, r.Синоним, schema))
            if re.search(r"(?i)карт|kart|сверк|mijoz|клиент|контраг|qarz|долг|взаимо", "%s %s" % (r.Имя, r.Синоним)):
                try:
                    for a in self.items(r.Реквизиты):
                        w("    rekvizit: %s — %s" % (a.Имя, a.Синоним))
                except Exception:
                    pass
                try:
                    rep = getattr(c.Отчеты, r.Имя).Создать()
                    sch = rep.ПолучитьМакет(r.ОсновнаяСхемаКомпоновкиДанных.Имя)
                    for p in self.items(sch.Параметры):
                        w("    parametr: %s — %s" % (p.Имя, p.Заголовок))
                    for ds in self.items(sch.НаборыДанных):
                        names = []
                        for fl in self.items(ds.Поля):
                            try:
                                names.append(str(fl.Поле))
                            except Exception:
                                pass
                        w("    maydonlar (%s): %s" % (ds.Имя, ", ".join(names[:60])))
                except Exception as e:
                    w("    (SKD o'qilmadi: %s)" % str(e)[:120])
        w("")
        w("== Ma'lumotnomalar (Справочники) ==")
        for s in self.items(md.Справочники):
            line = "%s — %s" % (s.Имя, s.Синоним)
            if re.search(r"(?i)контраг|клиент|постав|mijoz|kontr|partn|партн", "%s %s" % (s.Имя, s.Синоним)):
                try:
                    attrs = ", ".join("%s" % a.Имя for a in self.items(s.Реквизиты))
                    line += "\n    rekvizitlar: " + attrs
                    cnt = self.query("ВЫБРАТЬ КОЛИЧЕСТВО(*) КАК Н ИЗ Справочник.%s КАК Т" % s.Имя, ["Н"])
                    if cnt:
                        line += "\n    yozuvlar soni: %s" % cnt[0]["Н"]
                except Exception:
                    pass
            w(line)
        w("")
        w("== Jamg'arish registrlari (РегистрыНакопления) ==")
        for g in self.items(md.РегистрыНакопления):
            try:
                dims = ", ".join(x.Имя for x in self.items(g.Измерения))
                res = ", ".join(x.Имя for x in self.items(g.Ресурсы))
            except Exception:
                dims = res = "?"
            w("%s — %s\n    o'lchovlar: %s\n    resurslar: %s" % (g.Имя, g.Синоним, dims, res))
        w("")
        w("== Hujjatlar (Документы) ==")
        for d in self.items(md.Документы):
            w("%s — %s" % (d.Имя, d.Синоним))
        return "\n".join(str(x) for x in out)


# ---------------------------------------------------------------- eksport

def period(days, now=None):
    now = now or dt.datetime.now()
    days = max(1, min(int(days or 7), 400))
    start = (now - dt.timedelta(days=days - 1)).replace(hour=0, minute=0, second=0, microsecond=0)
    end = now.replace(hour=23, minute=59, second=59, microsecond=0)
    return start, end


def safe_name(s):
    s = re.sub(r'[\\/:*?"<>|\r\n\t]+', " ", s).strip()
    return (s or "mijoz")[:80]


def file_key(name, now_ms):
    """Vaqt bo'yicha tartiblanadigan noyob kalit: vaqt + nom xeshi (Firebase kalitida ruxsat etilgan belgilar)."""
    h = hashlib.sha1(name.encode("utf-8")).hexdigest()[:10]
    return "%013d_%s" % (now_ms, h)


def load_state():
    try:
        with open(STATE, encoding="utf-8") as f:
            v = json.load(f)
            return v if isinstance(v, dict) else {}
    except Exception:
        return {}


def save_state(st):
    tmp = STATE + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(st, f, ensure_ascii=False, indent=0)
    os.replace(tmp, STATE)


def content_hash(raw):
    """xlsx ichidagi vaqt belgilari har safar o'zgaradi — hisobot matni (sheet xml) bo'yicha xesh."""
    import io
    import zipfile
    try:
        z = zipfile.ZipFile(io.BytesIO(raw))
        h = hashlib.sha256()
        for n in sorted(z.namelist()):
            if n.startswith("xl/") and n.endswith(".xml"):
                h.update(n.encode())
                h.update(z.read(n))
        return h.hexdigest()
    except Exception:
        return hashlib.sha256(raw).hexdigest()


def run_once(cfg, cloud, onec, days, state=None, now=None, only=None):
    """Bir aylanish. @return (yuborildi, tekshirildi, xatolar)."""
    state = load_state() if state is None else state
    d_from, d_to = period(days, now)
    sent = checked = 0
    errors = []
    onec.connect()
    try:
        people = onec.counterparties(d_from, d_to)
        names_only = set(only or cfg.only)
        for p in people:
            name = p["name"]
            if not name or name in cfg.skip or (names_only and name not in names_only):
                continue
            checked += 1
            try:
                raw = onec.build_xlsx(p["ref"], d_from, d_to)
            except Exception as e:
                errors.append("%s: %s" % (name, str(e)[:120]))
                log.warning("hisobot tuzilmadi: %s — %s", name, e)
                continue
            if not raw or len(raw) > MAX_FILE:
                errors.append("%s: fayl hajmi %d" % (name, len(raw or b"")))
                continue
            sig = "%s|%s|%s" % (d_from.date(), days, content_hash(raw))
            if state.get(name) == sig:
                continue  # o'zgarmagan — yuborilmaydi (internet va telefon zaryadi tejaladi)
            kind = "s" if p.get("flag") else "c"
            fname = "%s_%s.xlsx" % (safe_name(name), d_to.strftime("%Y%m%d"))
            cloud.upload(file_key(name, int(time.time() * 1000)), fname, raw, name, kind)
            state[name] = sig
            save_state(state)
            sent += 1
            log.info("yuborildi: %s (%d KB)", name, len(raw) // 1024)
    finally:
        onec.close()
    return sent, checked, errors


def status_text(sent, checked, errors):
    t = "%d ta yangilandi, %d ta tekshirildi" % (sent, checked)
    if errors:
        t += "; %d ta xato: %s" % (len(errors), errors[0])
    return t


def parse_schedule(sch, cfg):
    """1-ilova jadvali -> (soat, kun). Jadval yo'q bo'lsa — forwarder.ini dagi standart."""
    if not sch:
        return cfg.default_hours, cfg.default_days
    try:
        h = int(sch.get("h", cfg.default_hours) or 0)
    except (TypeError, ValueError):
        h = cfg.default_hours
    try:
        d = int(sch.get("d", cfg.default_days) or cfg.default_days)
    except (TypeError, ValueError):
        d = cfg.default_days
    return max(0, min(h, 24)), max(1, min(d, 400))


def loop(cfg):
    cloud = Cloud(cfg)
    onec = OneC(cfg)
    last_run = 0.0
    while True:
        try:
            sch = cloud.schedule()
        except Exception as e:
            log.warning("jadvalni o'qib bo'lmadi: %s", e)
            sch = {}
        hours, days = parse_schedule(sch, cfg)
        if hours <= 0:
            time.sleep(POLL_SEC)
            continue
        if time.time() - last_run >= hours * 3600 - 60:
            last_run = time.time()
            try:
                sent, checked, errors = run_once(cfg, cloud, onec, days)
                msg = status_text(sent, checked, errors)
                log.info(msg)
                safe_status(cloud, msg, not errors or sent > 0)
            except SystemExit as e:
                log.error("%s", e)
                safe_status(cloud, str(e), False)
            except Exception as e:
                log.exception("eksport xatosi")
                safe_status(cloud, "Xato: %s" % str(e)[:200], False)
        time.sleep(min(POLL_SEC, max(60, hours * 3600 - (time.time() - last_run))))


def safe_status(cloud, text, ok):
    try:
        cloud.status(text, ok)
    except Exception as e:
        log.warning("holat yozilmadi: %s", e)


def main():
    ap = argparse.ArgumentParser(description="1C → Excel → telefon (avtomatik)")
    ap.add_argument("--discover", action="store_true", help="1C tuzilishini discover.txt ga yozish")
    ap.add_argument("--once", action="store_true", help="hozir bir marta eksport")
    ap.add_argument("--one", metavar="NOM", help="faqat bitta kontragent (fayl export/ papkada ham qoladi)")
    ap.add_argument("--status", action="store_true", help="bulutdagi jadval va holat")
    ap.add_argument("--days", type=int, help="necha kunlik (jadvaldagini almashtiradi)")
    ap.add_argument("--config", default=CONFIG)
    ap.add_argument("-v", action="store_true")
    a = ap.parse_args()
    setup_logging(a.v)
    cfg = Config(a.config)
    if a.discover:
        cfg.check_onec(need_report=False)
        o = OneC(cfg).connect()
        try:
            txt = o.discover()
        finally:
            o.close()
        with open(DISCOVER, "w", encoding="utf-8") as f:
            f.write(txt)
        print("Tayyor:", DISCOVER, "— shu faylni dasturchiga yuboring (ichida parol yo'q).")
        return
    cfg.check_cloud()
    cloud = Cloud(cfg)
    if a.status:
        print("Jadval:", cloud.schedule() or "(1-ilova hali yozmagan — standart %d soat)" % cfg.default_hours)
        print("Holat:", cloud.call("GET", "p/%s/st" % cfg.inbox, timeout=30))
        print("Kutayotgan fayllar:", len(cloud.call("GET", "in/%s" % cfg.inbox, timeout=30, shallow="true") or {}))
        return
    cfg.check_onec()
    days = a.days or parse_schedule(cloud.schedule(), cfg)[1]
    if a.one:
        o = OneC(cfg).connect()
        try:
            people = [p for p in o.counterparties(*period(days)) if p["name"] == a.one]
            if not people:
                raise SystemExit("Topilmadi: %s" % a.one)
            raw = o.build_xlsx(people[0]["ref"], *period(days))
        finally:
            o.close()
        os.makedirs(OUT_DIR, exist_ok=True)
        path = os.path.join(OUT_DIR, safe_name(a.one) + ".xlsx")
        with open(path, "wb") as f:
            f.write(raw)
        cloud.upload(file_key(a.one, int(time.time() * 1000)), os.path.basename(path), raw, a.one, "s" if people[0].get("flag") else "c")
        print("Saqlandi va yuborildi:", path)
        return
    if a.once:
        sent, checked, errors = run_once(cfg, cloud, OneC(cfg), days)
        msg = status_text(sent, checked, errors)
        safe_status(cloud, msg, not errors or sent > 0)
        print(msg)
        for e in errors[:20]:
            print("  xato:", e)
        return
    log.info("Ishga tushdi")
    loop(cfg)


if __name__ == "__main__":
    main()
