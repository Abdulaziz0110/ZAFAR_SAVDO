#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
1C → Excel → telefon (avtomatik). 1C 8.3 o'rnatilgan kompyuterda fonda ishlaydi.

Har N soatda (N = 1-ilovada tanlanadi: 1/2/3 soat, 0 — o'chiq) 1C bazaga COM orqali ODDIY foydalanuvchi bilan ulanadi,
har bir mijoz / yuk beruvchi uchun "kartochka" hisobotini tuzadi, Excel (.xlsx) qilib saqlaydi va Firebase'ga
`in/{inbox}/{id}` ga qo'yadi. 1-ilova (telefon) o'zi yuklab, 1C tarixiga import qiladi (xuddi qo'lda Excel yuklagandek).

Hisobot tuzishda 1C'ga HECH NARSA YOZILMAYDI. Yozish faqat Admin'da tasdiqlangan so'rovlar uchun (onec_write.py). Konfigurator va server paroli kerak emas.

Talab: Windows, 1C:Enterprise 8.3 (COM komponenti "V83.COMConnector" ro'yxatdan o'tgan), Python 3.8+, pywin32:
    pip install pywin32

Buyruqlar:
  python onec_export.py --discover   — 1C'dagi hisobotlar/ma'lumotnomalar ro'yxati -> discover.txt (dasturchiga yuboring)
  python onec_export.py --once       — hozir bir marta eksport qilish
  python onec_export.py --one "Dadaxon Aka (TRZ)"  — faqat bitta mijoz (sinov; fayl export/ papkada qoladi)
  python onec_export.py --status     — bulutdagi jadval va oxirgi holat
  python onec_export.py --codes      — tovar kodlari (PLU va shtrix kodlar) ni hozir yuborish (natija: codes.json)
  python onec_export.py              — doimiy ishlash (odatda onec_export.vbs orqali oynasiz)

Sozlama: forwarder.ini ([firebase] url/secret/inbox — 1-ilova beradi; [onec] — o'zingiz to'ldirasiz).

v3.2: Admin'da tanlangan daqiqalik jadval (1, 5, 15, 30, 60 daqiqa) va ish vaqti — har aylanishda bugun harakati
bo'lgan mijozlar kartochkasi, 1C savdolar ro'yxati va ta'minotchilar (onec_data.py) yuboriladi; ish tugashidan 1 daqiqa
oldin hammasi to'liq. Admin tasdiqlagan yozuvlar 1C'ga kiritiladi (onec_write.py: zaxira, jurnal, bekor qilish).

Tovar kodlari (v2.7): 1C'dagi tarozi PLU raqamlari va shtrix kodlar har [codes] hours soatda (standart 6) yoki
1-ilovada "Kompyuterdan yangilash" bosilganda `in/{inbox}/` ga "codes" bo'lib yuboriladi. Qayerda saqlanishi
1C metama'lumotidan o'zi topiladi (PLU — tovar rekviziti yoki "весы/оборудование/PLU" registri; shtrix — "Штрихкод"
maydonli registr). Topilmasa: --discover natijasini yuboring yoki [codes] plu/barcode ni qo'lda yozing.
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
import threading
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
CODES_OUT = os.path.join(HERE, "codes.json")
PC_VERSION = "5.4"                # kompyuter dasturi versiyasi (pc_version.txt bilan birga yangilanadi)
TICK_SEC = 30                     # jadval, telefon so'rovlari va 1C'ga yozish navbati shuncha vaqtda bir tekshiriladi

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

def read_text(path):
    """Bloknot UTF-8 (BOM bilan/siz) yoki ANSI (1251) da saqlagan bo'lsa ham o'qiladi. Fayl yo'q bo'lsa — None."""
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


def single_instance(name="onec_export", wait=45, sleep=time.sleep):
    """Bir vaqtda faqat bitta nusxa ishlasin (avtozapusk + qo'lda ishga tushirilsa). @return qulf fayli yoki None.
    pc 18: yangilanishdan keyin qayta ishga tushganda eski nusxa hali yopilmagan bo'lishi mumkin — [wait] soniya kutiladi
    (aks holda yangi nusxa "boshqasi ishlayapti" deb chiqib ketar va dastur umuman ishlamay qolardi)."""
    path = os.path.join(tempfile.gettempdir(), "imsoft_%s.lock" % name)
    end = time.time() + wait
    while True:
        f = None
        try:
            f = open(path, "a+")
            if os.name == "nt":
                import msvcrt
                f.seek(0)
                msvcrt.locking(f.fileno(), msvcrt.LK_NBLCK, 1)
            else:
                import fcntl
                fcntl.flock(f.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
            return f
        except OSError:
            if f is not None:
                f.close()
            if time.time() >= end:
                return None
            sleep(1)


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
        # onec.ini (ixtiyoriy) — [onec] va [codes] shu yerda tursa, telefondan yangi forwarder.ini kelganda o'chib ketmaydi
        for f in (path, os.path.join(os.path.dirname(os.path.abspath(path)), "onec.ini")):
            text = read_text(f)
            if text is not None:
                cp.read_string(text, source=f)
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
        cd = cp["codes"] if cp.has_section("codes") else {}
        gc = lambda k, d="": (cd.get(k, d) or d).strip()  # noqa: E731
        self.codes_on = gc("enabled", "1") not in ("0", "no", "false", "yoq")
        self.goods = gc("goods", "Номенклатура")
        # Qo'lda: "attr:<Rekvizit>" yoki "<RegistrSvedeniy>:<tovar maydoni>:<qiymat maydoni>"
        self.plu_src = gc("plu")
        self.barcode_src = gc("barcode")
        # Narxlar: "attr:Цена1,Цена2" yoki "<RegistrSvedeniy>:<tovar>:<narx turi>:<narx>"
        self.price_src = gc("price")
        self.codes_hours = max(1, int(gc("hours", "6") or 6))
        # v3.2: 1C'ga yozish ("Tasdiqlab kiritish") va 1C ma'lumotlari (savdolar, ta'minotchilar)
        wr = cp["write"] if cp.has_section("write") else {}
        gw = lambda k, d="": (wr.get(k, d) or d).strip()  # noqa: E731
        self.write_on = gw("enabled", "1") not in ("0", "no", "false", "yoq")
        self.require_backup = gw("require_backup", "1") not in ("0", "no", "false", "yoq")
        da = cp["data"] if cp.has_section("data") else {}
        gd = lambda k, d="": (da.get(k, d) or d).strip()  # noqa: E731
        self.data_on = gd("enabled", "1") not in ("0", "no", "false", "yoq")
        # v5.3: telefonlar ko'prigi (relay.py) - SQL server. Odatda bo'sh: Admin ilovasi bulut orqali yuboradi
        sq = cp["sql"] if cp.has_section("sql") else {}
        self.sql = {k: (sq.get(k, "") or "").strip() for k in ("key", "host", "database", "user", "password")}
        self.relay_on = (sq.get("enabled", "1") or "1").strip() not in ("0", "no", "false", "yoq")
        # v5.3: 1C'ga ulanish usuli: auto (avval COM, tovar moduli xato bersa - 1C oynasi orqali), com, app
        self.onec_mode = g("mode", "auto").lower()
        self.sales_days = max(1, min(400, int(gd("sales_days", "30") or 30)))
        self.supp_days = max(1, min(400, int(gd("supp_days", "90") or 90)))

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

    def codes_request(self):
        """1-ilova "Kompyuterdan yangilash" bosgan vaqt (ms) yoki 0."""
        v = self.call("GET", "p/%s/creq" % self.cfg.inbox, timeout=30)
        return v if isinstance(v, (int, float)) else 0

    def update_request(self):
        """v5.4: Admin "Kompyuter dasturini hozir yangilash" bosgan vaqt (ms) yoki 0."""
        v = self.call("GET", "p/%s/ureq" % self.cfg.inbox, timeout=30)
        return v if isinstance(v, (int, float)) else 0

    def data_request(self):
        """Admin "shu davrni yuboring" so'rovi: {k, f, t, at} yoki {}."""
        v = self.call("GET", "p/%s/dreq" % self.cfg.inbox, timeout=30)
        return v if isinstance(v, dict) else {}

    def status(self, text, ok):
        self.call("PUT", "p/%s/st" % self.cfg.inbox, {"at": {".sv": "timestamp"}, "text": text[:300], "ok": bool(ok)}, timeout=30, print="silent")

    def upload(self, key, name, raw, client, kind):
        body = {
            "at": {".sv": "timestamp"}, "n": name, "gz": True, "c": client[:120], "k": kind,
            "b": base64.b64encode(gzip.compress(raw, 9)).decode("ascii"),
        }
        self.call("PUT", "in/%s/%s" % (self.cfg.inbox, key), body, timeout=120, print="silent")


class Waker:
    """v3.12: Firebase oqimi (SSE). Admin 1C'ga yozish / tarozi / ma'lumot so'rovini qo'yishi bilan kompyuter DARHOL
    uyg'onadi (oldin 30 soniyagacha kutardi). Oqim uzilsa — o'zi qayta ulanadi; ishlamasa ham eski 30 soniyalik tekshiruv qoladi."""

    def __init__(self, cfg, paths, opener=None):
        self.cfg = cfg
        self.paths = list(paths)
        self.event = threading.Event()
        self.opener = opener or urllib.request.urlopen
        self.connected = {}

    def start(self):
        for p in self.paths:
            threading.Thread(target=self._loop, args=(p,), daemon=True, name="wake-" + p.split("/")[0]).start()
        return self

    def url(self, path):
        return "%s/%s.json?%s" % (self.cfg.url, path.strip("/"), urllib.parse.urlencode({"auth": self.cfg.secret}))

    def feed(self, lines):
        """SSE qatorlari: yangi so'rov (null bo'lmagan put/patch) kelsa — uyg'otadi. @return False — oqim yopilsin."""
        ev = None
        for raw in lines:
            line = (raw.decode("utf-8", "replace") if isinstance(raw, bytes) else raw).strip()
            if line.startswith("event:"):
                ev = line[6:].strip()
            elif line.startswith("data:"):
                if ev in ("cancel", "auth_revoked"):
                    return False
                if ev in ("put", "patch"):
                    try:
                        d = json.loads(line[5:].strip())
                    except ValueError:
                        d = None
                    if isinstance(d, dict) and d.get("data") is not None:
                        self.event.set()
        return True

    def _loop(self, path):
        delay = 5
        while True:
            try:
                req = urllib.request.Request(self.url(path), headers={"Accept": "text/event-stream"})
                with self.opener(req, timeout=90) as r:
                    self.connected[path] = True
                    delay = 5
                    self.feed(r)
            except Exception as e:  # noqa: BLE001 — internet uzilishi: kutib qayta ulanadi
                log.debug("oqim (%s): %s", path, e)
            self.connected[path] = False
            time.sleep(delay)
            delay = min(delay * 2, 300)

    def wait(self, sec):
        """[sec] soniya yoki so'rov kelguncha kutadi. @return so'rov kelib uyg'otdimi."""
        hit = self.event.wait(sec)
        self.event.clear()
        return hit


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

    # --- tovar kodlari (PLU, shtrix) ---
    def _type_names(self, field):
        """Maydon turlari: ["Справочник.Номенклатура", "Число", ...]."""
        out = []
        try:
            for t in self.items(field.Тип.Типы()):
                m = self.c.Метаданные.НайтиПоТипу(t)
                if m is not None:
                    out.append(str(m.ПолноеИмя()))
                else:
                    out.append(str(self.c.Строка(t)))
        except Exception:
            pass
        return out

    def meta_summary(self):
        """1C tuzilishidan faqat kerakli qism (sof ma'lumot — tanlash mantig'i shu bilan testlanadi)."""
        md = self.c.Метаданные
        goods = []
        cat = md.Справочники.Найти(self.cfg.goods)
        if cat is None:
            # Tovarlar ma'lumotnomasi boshqacha nomlangan bo'lishi mumkin (masalan "Товары")
            for alt in GOODS_CATALOGS:
                cat = md.Справочники.Найти(alt)
                if cat is not None:
                    log.info("tovarlar ma'lumotnomasi: Справочник.%s", alt)
                    self.cfg.goods = alt
                    break
        tabs = []
        if cat is not None:
            for a in self.items(cat.Реквизиты):
                goods.append({"name": a.Имя, "syn": str(a.Синоним), "types": self._type_names(a)})
            # v5.3: tovarning jadval qismlari (qo'shimcha shtrix kodlar ro'yxati shu yerda bo'lishi mumkin)
            try:
                for t in self.items(cat.ТабличныеЧасти):
                    tabs.append({"name": t.Имя, "fields": [{"name": f.Имя} for f in self.items(t.Реквизиты)]})
            except Exception:  # noqa: BLE001
                pass
        regs = []
        for r in self.items(md.РегистрыСведений):
            fields = []
            for col in ("Измерения", "Ресурсы", "Реквизиты"):
                try:
                    for f in self.items(getattr(r, col)):
                        fields.append({"name": f.Имя, "syn": str(f.Синоним), "types": self._type_names(f)})
                except Exception:
                    pass
            regs.append({"name": r.Имя, "syn": str(r.Синоним), "fields": fields})
        # Shtrix kodlar alohida ma'lumotnomada bo'lishi mumkin (masalan Справочник.Штрих, egasi — tovar)
        cats = []
        for c in self.items(md.Справочники):
            if not re.search(r"(?i)штрих|barcode|shtrix", "%s %s" % (c.Имя, c.Синоним)):
                continue
            owners = []
            try:
                owners = [str(o.ПолноеИмя()) for o in self.items(c.Владельцы)]
            except Exception:
                pass
            fields = []
            try:
                for a in self.items(c.Реквизиты):
                    fields.append({"name": a.Имя, "syn": str(a.Синоним), "types": self._type_names(a)})
            except Exception:
                pass
            cats.append({"name": c.Имя, "syn": str(c.Синоним), "owners": owners, "fields": fields})
        return {"goods_name": self.cfg.goods, "goods_found": cat is not None, "goods": goods, "regs": regs, "cats": cats, "tabs": tabs}

    def goods_codes(self, summary=None):
        """[{c, n, p, b}] — har tovar: 1C kodi, nomi, PLU, shtrix kodlar. Faqat o'qiydi."""
        summary = summary or self.meta_summary()
        plu = parse_src(self.cfg.plu_src) or pick_plu(summary)
        bc = parse_src(self.cfg.barcode_src) or pick_barcode(summary)
        items = {}

        def item(code, name):
            code = norm_code(code)
            name = str(name or "").strip()
            k = code or ("n:" + name.lower())
            it = items.get(k)
            if it is None:
                it = items[k] = {"c": code, "n": name, "p": "", "b": [], "pr": {}}
            return it

        goods = self.cfg.goods
        if plu and plu[0] == "attr":
            for r in self._goods_rows("Т.%s КАК Знач" % plu[1]):
                v = norm_plu(r["Знач"])
                if v:
                    item(r["Код"], r["Наименование"])["p"] = v
        elif plu:
            best = None
            fields = [plu[3]] if self.cfg.plu_src else plu_candidates(summary, plu)
            for f in fields:
                src_f = plu[:3] + (f,) + plu[4:]
                try:
                    rows = [(r["Код"], r["Наименование"], norm_plu(r["Знач"])) for r in self.query(reg_query(src_f, goods, summary), ["Код", "Наименование", "Знач"])]
                except Exception as e:  # noqa: BLE001
                    log.warning("PLU so'rovi (%s) ishlamadi: %s", f, e)
                    continue
                rows = [x for x in rows if x[2]]
                log.info("PLU maydoni %s: %d ta qiymat", f, len(rows))
                if best is None or len(rows) > len(best[1]):
                    best = (src_f, rows)
            if best:
                plu = best[0]
                for code, name, v in best[1]:
                    item(code, name)["p"] = v
        def bc_rows(src):
            if src[0] == "cat":
                return self.query(cat_query(src), ["Код", "Наименование", "Знач"])
            if src[0] == "attr":
                return self._goods_rows("Т.%s КАК Знач" % src[1])
            if src[0] == "tab":
                return self.query(tab_query(src, goods), ["Код", "Наименование", "Знач"])
            return self.query(reg_query(src, goods, summary), ["Код", "Наименование", "Знач"])

        def add_bc(rows):
            n = 0
            for r in rows:
                v = str(r["Знач"] or "").strip()
                if v:
                    it = item(r["Код"], r["Наименование"])
                    if v not in it["b"]:
                        it["b"].append(v)
                        n += 1
            return n

        if bc:
            add_bc(bc_rows(bc))
        # v5.3: shtrix kod BIR NECHTA joyda bo'ladi: tovarning asosiy kodi (rekvizit), qo'shimcha kodlar ro'yxati
        # (jadval qismi) va alohida ma'lumotnoma (Штрих). Avval faqat bittasi o'qilar edi - qo'shimcha kodlar
        # telefonga bormas, skaner "topilmadi" der edi. Endi hammasi birlashtiriladi.
        bc_all = [src_text(bc)] if bc else []
        if not self.cfg.barcode_src:
            for extra in barcode_sources(summary):
                if extra == bc:
                    continue
                try:
                    if add_bc(bc_rows(extra)):
                        bc_all.append(src_text(extra))
                except Exception as e:  # noqa: BLE001 - bu manba bu bazada ishlamadi: qolganlari bilan davom
                    log.debug("qo'shimcha shtrix manbasi (%s): %s", extra, e)
        # Narxlar (barcha narx turlari) — yorliqda qaysi biri chiqishini telefonda tanlaysiz
        pr = parse_price_src(self.cfg.price_src) or pick_price(summary)
        types = []
        if pr and pr[0] == "pattr":
            for attr, title in zip(pr[1], pr[2]):
                if title not in types:
                    types.append(title)
                for r in self._goods_rows("Т.%s КАК Знач" % attr):
                    v = norm_price(r["Знач"])
                    if v > 0:
                        item(r["Код"], r["Наименование"])["pr"][title] = v
        elif pr:
            rows = None
            for q in price_queries(pr, goods):
                try:
                    rows = self.query(q, ["Код", "Наименование", "Тип", "Знач"])
                    break
                except Exception as e:  # noqa: BLE001
                    log.debug("narx so'rovi ishlamadi: %s", e)
            for r in rows or []:
                v = norm_price(r["Знач"])
                t = str(r["Тип"] or "").strip() or "?"
                if v > 0:
                    if t not in types:
                        types.append(t)
                    item(r["Код"], r["Наименование"])["pr"][t] = v
        src = {"plu": src_text(plu), "bc": " + ".join(x for x in bc_all if x) or src_text(bc), "price": price_src_text(pr), "types": types}
        out = []
        for it in items.values():
            if it["p"] or it["b"] or it["pr"]:
                if not it["pr"]:
                    del it["pr"]
                out.append(it)
        return out, src

    def _goods_rows(self, extra):
        base = "ВЫБРАТЬ Т.Код КАК Код, Т.Наименование КАК Наименование, %s ИЗ Справочник.%s КАК Т ГДЕ НЕ Т.ПометкаУдаления" % (extra, self.cfg.goods)
        try:
            return self.query(base + " И НЕ Т.ЭтоГруппа", ["Код", "Наименование", "Знач"])
        except Exception:
            return self.query(base, ["Код", "Наименование", "Знач"])

    # --- namuna (tashxis) ---
    def _dump(self, kind, name, n, out):
        """Ma'lumotnoma yoki ma'lumot registridan birinchi [n] ta yozuv (hamma maydonlar, ko'rinishi bilan)."""
        md = self.c.Метаданные
        coll = md.Справочники if kind == "cat" else md.РегистрыСведений
        obj = coll.Найти(name)
        if obj is None:
            out.append("  (%s yo'q)" % name)
            return
        fields = []
        if kind == "cat":
            fields = ["Код", "Наименование"]
            try:
                if obj.Владельцы.Количество() > 0:
                    fields.append("Владелец")
            except Exception:
                pass
            cols = ("Реквизиты",)
        else:
            cols = ("Измерения", "Ресурсы", "Реквизиты")
        types = {}
        for col in cols:
            try:
                for f in self.items(getattr(obj, col)):
                    fields.append(f.Имя)
                    types[f.Имя] = "/".join(self._type_names(f)[:2])
            except Exception:
                pass
        table = ("Справочник." if kind == "cat" else "РегистрСведений.") + name
        try:
            cnt = self.query("ВЫБРАТЬ КОЛИЧЕСТВО(*) КАК Н ИЗ %s КАК Т" % table, ["Н"])
            out.append("  yozuvlar soni: %s" % (cnt[0]["Н"] if cnt else "?"))
        except Exception as e:  # noqa: BLE001
            out.append("  (sanab bo'lmadi: %s)" % str(e)[:120])
        out.append("  maydonlar: " + ", ".join("%s[%s]" % (f, types.get(f, "")) for f in fields))
        names = ["П%d" % i for i in range(len(fields))]
        sel = ", ".join("ПРЕДСТАВЛЕНИЕ(Т.%s) КАК %s" % (f, nm) for f, nm in zip(fields, names))
        try:
            rows = self.query("ВЫБРАТЬ ПЕРВЫЕ %d %s ИЗ %s КАК Т" % (n, sel, table), names)
        except Exception as e:  # noqa: BLE001
            out.append("  (o'qib bo'lmadi: %s)" % str(e)[:200])
            return
        for r in rows:
            out.append("  | " + " | ".join("%s=%s" % (f, str(r[nm])[:40]) for f, nm in zip(fields, names)))

    def sample(self):
        """PLU, shtrix va narx qayerdaligini aniqlash uchun namunalar (parol va kalitlar yo'q)."""
        summary = self.meta_summary()
        out = ["IMSOFT namuna (onec_export.py --sample)", ""]
        out.append("== Tovarlar: Справочник.%s ==" % self.cfg.goods)
        self._dump("cat", self.cfg.goods, 5, out)
        for c in summary.get("cats", []):
            out.append("")
            out.append("== Shtrix: Справочник.%s (egasi: %s) ==" % (c["name"], ", ".join(c.get("owners", [])) or "-"))
            self._dump("cat", c["name"], 8, out)
        plu = pick_plu(summary)
        if plu and plu[0] == "reg":
            out.append("")
            out.append("== PLU: РегистрСведений.%s ==" % plu[1])
            self._dump("reg", plu[1], 12, out)
        for extra in ("Весы",):
            out.append("")
            out.append("== Справочник.%s ==" % extra)
            self._dump("cat", extra, 5, out)
        out.append("")
        out.append("== Barcha ma'lumot registrlari (РегистрыСведений) ==")
        for r in summary.get("regs", []):
            out.append("  %s — %s: %s" % (r["name"], r.get("syn", ""), ", ".join(
                "%s[%s]" % (f["name"], "/".join(f.get("types", [])[:2])) for f in r["fields"])))
        out.append("")
        out.append(codes_report(summary, self.cfg))
        return "\n".join(str(x) for x in out)

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
        w("")
        try:
            w(codes_report(self.meta_summary(), self.cfg))
        except Exception as e:
            w("== Tovar kodlari ==\n(o'qilmadi: %s)" % str(e)[:200])
        return "\n".join(str(x) for x in out)



UPDATE_SEC = 3600           # yangilanish soatda bir marta (tezroq kerak bo'lsa — telefondan "hozir yangilash")
AUTOSTART_TASK = "IMSOFT server"


def autostart_args(pyw, script):
    """Windows'ga kirilishi bilan (kutmasdan) dastur administrator huquqida ishga tushadi."""
    return ["schtasks", "/create", "/tn", AUTOSTART_TASK, "/tr", '"%s" "%s"' % (pyw, script), "/sc", "onlogon",
            "/rl", "highest", "/f"]


def pythonw_path(exe):
    d, name = os.path.split(exe)
    if name.lower() == "python.exe" and os.path.isfile(os.path.join(d, "pythonw.exe")):
        return os.path.join(d, "pythonw.exe")
    return exe


def ensure_autostart():
    """pc 12: kompyuter yonib Windows'ga kirilishi bilan dastur o'zi ishga tushadi (bir marta sozlanadi)."""
    if os.name != "nt":
        return False
    import subprocess
    flags = getattr(subprocess, "CREATE_NO_WINDOW", 0)
    try:
        import ctypes
        if not ctypes.windll.shell32.IsUserAnAdmin():
            return False
        r = subprocess.run(["schtasks", "/query", "/tn", AUTOSTART_TASK], capture_output=True, timeout=30, creationflags=flags)
        if r.returncode == 0:
            return True
        r = subprocess.run(autostart_args(pythonw_path(sys.executable), os.path.abspath(__file__)), capture_output=True,
                           timeout=30, creationflags=flags)
        log.info("avtozapusk (Windows'ga kirilganda): %s", "qo'yildi" if r.returncode == 0 else "XATO")
        return r.returncode == 0
    except (OSError, subprocess.SubprocessError, AttributeError) as e:
        log.warning("avtozapusk: %s", e)
        return False


def app_hint():
    """Kompyuterdagi HAQIQIY 1cv8.exe yo'li bilan maslahat (Program Files yoki Program Files (x86))."""
    exe = ""
    try:
        import onec_bridge
        exe = onec_bridge.find_exe()
    except Exception:  # noqa: BLE001
        exe = ""
    return "Kompyuterda administrator sifatida bir marta bajaring: \"%s\" /regserver (yoki 1C_COM_tuzatish.bat)" % (
        exe or "<1C papkasi>\\bin\\1cv8.exe")
_REGISTERED = []


RPC_DEAD = (-2147023170, -2147023174, -2147417848, -2147023169)   # Сбой при удаленном вызове / RPC недоступен / ...


def err_code(err):
    try:
        return err.args[0] if err.args and isinstance(err.args[0], int) else getattr(err, "hresult", None)
    except Exception:  # noqa: BLE001
        return None


def parse_procs(csv_text):
    """wmic ... /format:csv natijasi -> [(pid, buyruq qatori)] faqat 1cv8.exe uchun."""
    out = []
    for line in str(csv_text or "").splitlines():
        parts = [x.strip() for x in line.split(",")]
        if len(parts) < 3 or not parts[-1].isdigit():
            continue
        out.append((int(parts[-1]), ",".join(parts[1:-1])))
    return out


def is_automation(cmdline):
    """Dastur COM orqali ochgan (ko'rinmas) 1C: buyruq qatorida -Embedding / /Automation bor. Kassir ochgani — yo'q."""
    return bool(re.search(r"(?i)(^|\s)[-/](embedding|automation)\b", str(cmdline or "")))


def stale_automation_procs():
    """Ilgari ochilib, osilib qolgan ko'rinmas 1C (COM-server) jarayonlari: [(pid, buyruq)]."""
    if os.name != "nt":
        return []
    import subprocess
    flags = getattr(subprocess, "CREATE_NO_WINDOW", 0)
    try:
        r = subprocess.run(["wmic", "process", "where", "name='1cv8.exe'", "get", "CommandLine,ProcessId", "/format:csv"],
                           capture_output=True, timeout=30, creationflags=flags)
        text = r.stdout.decode("utf-16", "replace") if r.stdout[:2] in (b"\xff\xfe", b"\xfe\xff") else \
            r.stdout.decode("cp866", "replace")
    except (OSError, subprocess.SubprocessError):
        return []
    return [(pid, cmd) for pid, cmd in parse_procs(text) if is_automation(cmd)]


def kill_stale_automation():
    """Osilib qolgan ko'rinmas 1C'larni yopadi (kassirning 1C oynasiga tegmaydi). @return nechta yopildi."""
    procs = stale_automation_procs()
    if not procs:
        return 0
    import subprocess
    for pid, cmd in procs:
        log.info("osilib qolgan ko'rinmas 1C yopilmoqda: PID %d (%s)", pid, cmd[:120])
        try:
            subprocess.run(["taskkill", "/PID", str(pid), "/F"], capture_output=True, timeout=30,
                           creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        except (OSError, subprocess.SubprocessError):
            pass
    time.sleep(2)
    return len(procs)


def needs_register(err):
    """V83.Application ro'yxatdan o'tmaganligi/buzilganligi xatosimi (RPC_S_UNKNOWN_IF, REGDB_E_CLASSNOTREG, ...)."""
    code = err_code(err)
    if code in (-2147023179, -2147221164, -2147221005, -2147024894):
        return True
    return bool(re.search(r"(?i)неизвестный интерфейс|interface is unknown|не зарегистрирован|not registered|"
                          r"недопустимая строка с указанием класса|invalid class string", str(err)))


def exe_from_command(cmd):
    """Reestrdagi LocalServer32 / InprocServer32 qiymatidan fayl yo'li: '"C:\\a b\\1cv8.exe" /automation' -> 'C:\\a b\\1cv8.exe'."""
    cmd = str(cmd or "").strip()
    if cmd.startswith('"'):
        end = cmd.find('"', 1)
        return cmd[1:end] if end > 0 else cmd.strip('"')
    m = re.match(r"(?i)^(.+?\.(exe|dll))(\s|$)", cmd)
    return m.group(1) if m else cmd


def app_server_candidates(comcntr_path, current_server):
    """Qaysi 1cv8.exe ni ro'yxatdan o'tkazish: avval ishlayotgan COMConnector (comcntr.dll) yonidagisi, keyin hozirgisi."""
    out = []
    if comcntr_path:
        import ntpath   # reestrdagi yo'llar doim Windows ko'rinishida
        out.append(ntpath.join(ntpath.dirname(exe_from_command(comcntr_path)), "1cv8.exe"))
    if current_server:
        out.append(exe_from_command(current_server))
    seen = []
    for p in out:
        if p and p.lower().endswith("1cv8.exe") and p not in seen:
            seen.append(p)
    return seen


def _clsid_server(progid, kind):
    import winreg
    with winreg.OpenKey(winreg.HKEY_CLASSES_ROOT, progid + "\\CLSID") as k:
        clsid = winreg.QueryValue(k, None)
    for view in (0, getattr(winreg, "KEY_WOW64_32KEY", 0), getattr(winreg, "KEY_WOW64_64KEY", 0)):
        try:
            with winreg.OpenKey(winreg.HKEY_CLASSES_ROOT, "CLSID\\%s\\%s" % (clsid, kind), 0, winreg.KEY_READ | view) as k:
                return winreg.QueryValue(k, None)
        except OSError:
            continue
    return ""


def register_app_server():
    """1cv8.exe /regserver (bir marta). @return urinildimi."""
    if _REGISTERED or os.name != "nt":
        return False
    _REGISTERED.append(1)
    comcntr = current = ""
    try:
        comcntr = _clsid_server("V83.COMConnector", "InprocServer32")
    except OSError:
        pass
    try:
        current = _clsid_server("V83.Application", "LocalServer32")
    except OSError:
        pass
    import subprocess
    for exe in app_server_candidates(comcntr, current):
        if not os.path.isfile(exe):
            continue
        log.info("1C oynasi COM-server sifatida ro'yxatdan o'tkazilmoqda: %s /regserver", exe)
        try:
            subprocess.run([exe, "/regserver"], timeout=180, creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
            return True
        except (OSError, subprocess.SubprocessError) as e:
            log.warning("1cv8.exe /regserver: %s", e)
    log.warning("1cv8.exe topilmadi (COMConnector: %s, V83.Application: %s)", comcntr or "-", current or "-")
    return False


class OneCApp(OneC):
    """v5.3: 1C'ga "oyna orqali" ulanish (V83.Application) - Konfiguratorda hech narsa o'zgartirmasdan.

    Do'kon bazasidagi tovar/kontragent modullari faqat 1C oynasida bor o'zgaruvchilarga tayanadi, shuning uchun
    oddiy COM ulanishida "Ошибка инициализации модуля" beradi. Bu yerda 1C ko'rinmas oyna bo'lib ochiladi -
    modullar xuddi kassir oynasidagidek ishlaydi. Ulanish bir necha daqiqa ochiq turadi (har safar 10-20 soniya
    kutmaslik uchun), keyin o'zi yopiladi."""

    KEEP_SEC = 180

    def __init__(self, cfg, conn=None):
        super().__init__(cfg, conn)
        self.used_at = 0.0

    def connect(self):
        self.used_at = time.time()
        if self.c is not None:
            return self
        try:
            import pythoncom  # noqa: F401  (pywin32)
            import win32com.client
        except ImportError:
            raise SystemExit("pywin32 o'rnatilmagan: pip install pywin32")
        import win32com.client.dynamic
        pythoncom.CoInitialize()
        errs = []
        app = ok = None
        killed = False
        for attempt in range(3):
            try:
                # sof "kech bog'lanish" (IDispatch) - makepy keshidagi eski tiplar xalaqit bermaydi
                app = win32com.client.dynamic.Dispatch("V83.Application")
                log.info("1C ga oyna orqali ulanilmoqda…")
                ok = app.Connect(self.cfg.conn_string())
                break
            except Exception as e:  # noqa: BLE001
                errs.append(str(e)[:200])
                app = None
                # v5.4: "Неизвестный интерфейс" / "класс не зарегистрирован" - 1cv8.exe COM-server sifatida
                # ro'yxatdan o'tmagan yoki boshqa versiyaga bog'langan. Ishlayotgan COMConnector bilan bir xil
                # versiyadagi 1cv8.exe /regserver qilinadi (dastur administrator huquqida) va bir marta qayta urinadi.
                if attempt < 2 and needs_register(e) and register_app_server():
                    continue
                # v5.4: oldingi urinishdan osilib qolgan ko'rinmas 1C (COM-server) yangisini "ushlab" oladi va
                # darhol "Сбой при удаленном вызове процедуры" beradi - shularni yopib qayta urinamiz
                if not killed and err_code(e) in RPC_DEAD:
                    killed = True
                    if kill_stale_automation():
                        continue
                raise SystemExit("1C ga oyna orqali ulanib bo'lmadi: %s. %s" % ("; ".join(errs), app_hint()))
        if ok is False:
            raise SystemExit("1C ga ulanib bo'lmadi (foydalanuvchi yoki parol)")
        try:
            app.Visible = False
        except Exception:  # noqa: BLE001
            pass
        # Baza kodi tekshirildi: 1C oynasi ochilganda dastur moduli мМобилАпп ni O'ZI o'rnatadi
        # (Константы.МобилАпп qiymatidan) - xuddi kassir oynasidagidek. Shuning uchun bu yerda tegilmaydi.
        self.c = app
        return self

    def close(self):
        """Darhol yopilmaydi: oxirgi ishdan KEEP_SEC o'tgach [idle] yopadi."""
        self.used_at = time.time()

    def idle(self):
        if self.c is not None and time.time() - self.used_at > self.KEEP_SEC:
            self.quit()

    def quit(self):
        app, self.c = self.c, None
        if app is None:
            return
        for call in (lambda: app.ЗавершитьРаботуСистемы(False), lambda: app.Exit(False)):
            try:
                call()
                break
            except Exception:  # noqa: BLE001
                continue
        del app
        try:
            import gc
            gc.collect()
        except Exception:  # noqa: BLE001
            pass


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


def run_once(cfg, cloud, onec, days, state=None, now=None, only=None, between=None):
    """Bir aylanish. @return (yuborildi, tekshirildi, xatolar).
    [between] — har mijozdan keyin chaqiriladi (v3.12: shu orada kelgan 1C'ga yozish so'rovlari kutib qolmasin)."""
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
            if between is not None:
                try:
                    between()
                except Exception as e:  # noqa: BLE001
                    log.warning("navbatdagi yozuvlar: %s", e)
    finally:
        onec.close()
    return sent, checked, errors


# ---------------------------------------------------------------- tovar kodlari: manbani tanlash (sof mantiq)

PLU_REG = re.compile(r"(?i)plu|плу|весы|весов|оборудован|offline|tarozi|scale")
PLU_FIELD = re.compile(r"(?i)^(plu|плу|код|кодplu|кодплу|номерplu|номерплу|номер|кодтовара|кодтовараplu)$")
BC_FIELD = re.compile(r"(?i)^(штрихкод|штрих_код|штрихкодтовара|barcode|shtrixkod|ean)$")
GOODS_FIELD = re.compile(r"(?i)^(номенклатура|товар|владелец|tovar|номенклатурa)$")


def _goods_field(fields, goods):
    """Tovarga ishora qiluvchi maydon: (nomi, faqat tovarmi). Avval turi bo'yicha, keyin nomi bo'yicha."""
    full = "Справочник." + goods
    for f in fields:
        if full in f.get("types", []):
            return f["name"], len(f["types"]) == 1
    for f in fields:
        if GOODS_FIELD.match(f["name"]):
            return f["name"], len(f.get("types", [])) <= 1
    return None


def pick_plu(summary):
    """PLU qayerda: ("attr", rekvizit) yoki ("reg", registr, tovar maydoni, qiymat maydoni, faqat_tovar)."""
    for a in summary.get("goods", []):
        text = a["name"] + " " + a.get("syn", "")
        types = a.get("types", [])
        if "Булево" in types or any(t.startswith("Справочник.") for t in types):
            continue  # "Весовой" (ha/yo'q) belgisi yoki boshqa ma'lumotnomaga havola — PLU emas
        if re.search(r"(?i)plu|плу", text) or (re.search(r"(?i)вес|tarozi|scale", text) and re.search(r"(?i)код|номер|kod|code", text)):
            return ("attr", a["name"])
    best = None
    for r in summary.get("regs", []):
        title = r["name"] + " " + r.get("syn", "")
        if not PLU_REG.search(title):
            continue
        g = _goods_field(r["fields"], summary.get("goods_name", "Номенклатура"))
        if g is None:
            continue
        val = None
        for f in r["fields"]:
            if f["name"] == g[0]:
                continue
            if PLU_FIELD.match(f["name"]) or re.search(r"(?i)plu|плу", f["name"] + " " + f.get("syn", "")):
                if val is None or "Число" in f.get("types", []):
                    val = f["name"]
        if val is None:
            continue
        score = (3 if re.search(r"(?i)plu|плу", title) else 0) + (2 if re.search(r"(?i)весы|весов|tarozi|scale", title) else 0) + \
            (1 if re.search(r"(?i)оборудован|offline", title) else 0)
        if best is None or score > best[0]:
            best = (score, ("reg", r["name"], g[0], val, g[1]))
    return best[1] if best else None


GOODS_CATALOGS = ("Номенклатура", "Товары", "Товар", "Продукция", "Mahsulotlar")


def plu_candidates(summary, plu):
    """PLU registrida PLU bo'lishi mumkin bo'lgan barcha maydonlar (masalan "КодПлу" va "плу") — qaysida qiymat ko'p bo'lsa, o'sha."""
    reg = next((r for r in summary.get("regs", []) if r["name"] == plu[1]), None)
    if reg is None:
        return [plu[3]]
    out = [f["name"] for f in reg["fields"] if f["name"] != plu[2] and re.search(r"(?i)plu|плу", f["name"] + " " + f.get("syn", ""))
           and not any(t.startswith(("Справочник.", "Перечисление.")) for t in f.get("types", []))]
    if plu[3] not in out:
        out.insert(0, plu[3])
    return out


def pick_barcode_cat(summary):
    """Shtrix kodlar ma'lumotnomasi: ("cat", nom, tovar havolasi, qiymat maydoni)."""
    goods = summary.get("goods_name", "Номенклатура")
    full = "Справочник." + goods
    for c in summary.get("cats", []):
        gf = "Владелец" if full in c.get("owners", []) else next(
            (f["name"] for f in c.get("fields", []) if full in f.get("types", []) or GOODS_FIELD.match(f["name"])), None)
        if gf is None:
            continue
        vf = next((f["name"] for f in c.get("fields", []) if BC_FIELD.match(f["name"]) or re.match(r"(?i)^штрих", f["name"])), "Наименование")
        return ("cat", c["name"], gf, vf)
    return None


def barcode_sources(summary):
    """Shtrix kod bo'lishi mumkin bo'lgan HAMMA joy: tovar rekviziti, tovarning jadval qismlari, alohida ma'lumotnoma."""
    out = []
    for a in summary.get("goods", []):
        if BC_FIELD.match(a["name"]):
            out.append(("attr", a["name"]))
    for t in summary.get("tabs", []):
        f = next((x["name"] for x in t.get("fields", []) if BC_FIELD.match(x["name"]) or re.match(r"(?i)^штрих", x["name"])), None)
        if f:
            out.append(("tab", t["name"], f))
    c = pick_barcode_cat(summary)
    if c:
        out.append(c)
    return out


def tab_query(src, goods):
    _, tab, f = src
    return ("ВЫБРАТЬ Т.Ссылка.Код КАК Код, Т.Ссылка.Наименование КАК Наименование, Т.%s КАК Знач ИЗ Справочник.%s.%s КАК Т "
            "ГДЕ НЕ Т.Ссылка.ПометкаУдаления" % (f, goods, tab))


def cat_query(src):
    _, cat, gf, vf = src
    return ("ВЫБРАТЬ Ш.%s.Код КАК Код, Ш.%s.Наименование КАК Наименование, Ш.%s КАК Знач ИЗ Справочник.%s КАК Ш ГДЕ НЕ Ш.ПометкаУдаления"
            % (gf, gf, vf, cat))


def pick_barcode(summary):
    best = None
    for r in summary.get("regs", []):
        val = next((f["name"] for f in r["fields"] if BC_FIELD.match(f["name"])), None)
        if val is None:
            continue
        g = _goods_field([f for f in r["fields"] if f["name"] != val], summary.get("goods_name", "Номенклатура"))
        if g is None:
            continue
        score = (2 if re.search(r"(?i)штрих|barcode", r["name"]) else 0) + (1 if g[1] else 0)
        if best is None or score > best[0]:
            best = (score, ("reg", r["name"], g[0], val, g[1]))
    if best:
        return best[1]
    for a in summary.get("goods", []):
        if BC_FIELD.match(a["name"]):
            return ("attr", a["name"])
    return pick_barcode_cat(summary)


PRICE_TYPE_FIELD = re.compile(r"(?i)^(типцен|типцены|видцены|видцен|типценыноменклатуры|narxturi|pricetype)$")
PRICE_FIELD = re.compile(r"(?i)^(цена|price|narx|narxi|сумма)$")


def pick_price(summary):
    """Narxlar qayerda: ("preg", registr, tovar maydoni, narx turi maydoni, narx maydoni, faqat_tovar)
    yoki ("pattr", [rekvizitlar], [sarlavhalar]) — tovarning Цена1..Цена4 kabi rekvizitlari."""
    goods = summary.get("goods_name", "Номенклатура")
    best = None
    for r in summary.get("regs", []):
        title = r["name"] + " " + r.get("syn", "")
        g = _goods_field(r["fields"], goods)
        if g is None:
            continue
        tf = None
        for f in r["fields"]:
            if f["name"] == g[0]:
                continue
            if PRICE_TYPE_FIELD.match(f["name"]) or any(re.search(r"(?i)ТипыЦен|ВидыЦен|ТипЦен", t) for t in f.get("types", [])):
                tf = f["name"]
                break
        pf = next((f["name"] for f in r["fields"] if PRICE_FIELD.match(f["name"]) and f["name"] != g[0]), None)
        if tf is None or pf is None:
            continue
        score = (3 if re.search(r"(?i)цен|price|narx", title) else 0) + (1 if g[1] else 0) + \
            (2 if re.search(r"(?i)номенклатур|товар", r["name"]) else 0)
        if best is None or score > best[0]:
            best = (score, ("preg", r["name"], g[0], tf, pf, g[1]))
    if best:
        return best[1]
    attrs = [a for a in summary.get("goods", [])
             if (not a.get("types") or any(t in ("Число", "Number") for t in a.get("types", [])))
             and re.search(r"(?i)цен|price|narx", a["name"] + " " + a.get("syn", ""))
             and not re.search(r"(?i)закуп|приход|кирим|kirim|себест|вход|ндс|вал|\$|usd", a["name"] + " " + a.get("syn", ""))]
    if attrs:
        return ("pattr", [a["name"] for a in attrs], price_titles(attrs))
    return None


def price_titles(attrs):
    """Narx turlarining nomlari: "ПродажЦена1" -> "Цена 1 (Сум)". Sinonim takrorlansa (Цена 3 ikki marta) — raqam nomdan olinadi."""
    out = []
    for a in attrs:
        syn = (a.get("syn") or "").strip()
        m = re.search(r"(\d+)$", a["name"])
        if m and re.search(r"(?i)цен|narx|price", a["name"]):
            cur = re.search(r"\(([^)]*)\)", syn)
            t = "Цена %s" % m.group(1) + (" (%s)" % cur.group(1) if cur else "")
        else:
            t = syn or a["name"]
        while t in out:
            t += "*"
        out.append(t)
    return out


def parse_price_src(s):
    s = (s or "").strip()
    if not s:
        return None
    if s.lower().startswith("attr:"):
        names = [x.strip() for x in s[5:].split(",") if x.strip()]
        return ("pattr", names, names)
    parts = [x.strip() for x in s.split(":")]
    if len(parts) == 4 and all(parts):
        return ("preg", parts[0], parts[1], parts[2], parts[3], False)
    raise SystemExit("forwarder.ini [codes] price: noto'g'ri yozuv '%s' (attr:Цена1,Цена2 yoki Registr:Tovar:NarxTuri:Narx)" % s)


def price_queries(src, goods):
    """Avval oxirgi narxlar (СрезПоследних — davriy registr), bo'lmasa oddiy jadval."""
    _, reg, gf, tf, pf, only_goods = src
    ref = "Р.%s" % gf if only_goods else "ВЫРАЗИТЬ(Р.%s КАК Справочник.%s)" % (gf, goods)
    where = "" if only_goods else " ГДЕ Р.%s ССЫЛКА Справочник.%s" % (gf, goods)
    sel = "ВЫБРАТЬ %s.Код КАК Код, %s.Наименование КАК Наименование, ПРЕДСТАВЛЕНИЕ(Р.%s) КАК Тип, Р.%s КАК Знач ИЗ РегистрСведений.%s%s КАК Р%s"
    return [sel % (ref, ref, tf, pf, reg, ".СрезПоследних", where), sel % (ref, ref, tf, pf, reg, "", where)]


def price_src_text(src):
    if not src:
        return ""
    if src[0] == "pattr":
        return "rekvizitlar " + ", ".join(src[1])
    return "РегистрСведений.%s (%s, %s -> %s)" % (src[1], src[2], src[3], src[4])


def norm_price(v):
    try:
        f = float(v if v is not None else 0)
    except (TypeError, ValueError):
        try:
            f = float(str(v).replace("\u00a0", "").replace(" ", "").replace(",", "."))
        except ValueError:
            return 0.0
    return round(f, 2)


def parse_src(s):
    """forwarder.ini [codes]: "attr:ПЛУ" yoki "ТоварыНаВесах:Номенклатура:Код"."""
    s = (s or "").strip()
    if not s:
        return None
    if s.lower().startswith("attr:"):
        return ("attr", s[5:].strip())
    parts = [x.strip() for x in s.split(":")]
    if len(parts) == 3 and all(parts):
        return ("reg", parts[0], parts[1], parts[2], False)
    raise SystemExit("forwarder.ini [codes]: noto'g'ri yozuv '%s' (attr:Rekvizit yoki Registr:TovarMaydoni:QiymatMaydoni)" % s)


def reg_query(src, goods, summary=None):
    _, reg, gf, vf, only_goods = src
    ref = "Р.%s" % gf if only_goods else "ВЫРАЗИТЬ(Р.%s КАК Справочник.%s)" % (gf, goods)
    where = "" if only_goods else " ГДЕ Р.%s ССЫЛКА Справочник.%s" % (gf, goods)
    return ("ВЫБРАТЬ %s.Код КАК Код, %s.Наименование КАК Наименование, Р.%s КАК Знач ИЗ РегистрСведений.%s КАК Р%s"
            % (ref, ref, vf, reg, where))


def src_text(src):
    if not src:
        return ""
    if src[0] == "attr":
        return "rekvizit " + src[1]
    if src[0] == "cat":
        return "Справочник.%s (%s -> %s)" % (src[1], src[2], src[3])
    if src[0] == "tab":
        return "jadval qismi %s.%s" % (src[1], src[2])
    return "РегистрСведений.%s (%s -> %s)" % (src[1], src[2], src[3])


def norm_code(v):
    if isinstance(v, float) and v == int(v):
        v = int(v)
    return str(v if v is not None else "").strip()


def norm_plu(v):
    """PLU: faqat raqam, oldidagi nollarsiz (0 — yo'q)."""
    if isinstance(v, float) and v == int(v):
        v = int(v)
    d = re.sub(r"\D", "", str(v if v is not None else ""))
    d = d.lstrip("0")
    return d


def codes_report(summary, cfg):
    plu = parse_src(cfg.plu_src) or pick_plu(summary)
    bc = parse_src(cfg.barcode_src) or pick_barcode(summary)
    out = ["== Tovar kodlari (PLU, shtrix) =="]
    out.append("Tovarlar ma'lumotnomasi: Справочник.%s%s" % (summary.get("goods_name"), "" if summary.get("goods_found") else " — TOPILMADI ([codes] goods = ...)"))
    out.append("  rekvizitlar: " + ", ".join("%s (%s)" % (a["name"], a.get("syn", "")) for a in summary.get("goods", [])))
    out.append("PLU manbasi: %s" % (src_text(plu) or "TOPILMADI"))
    out.append("Shtrix kod manbasi: %s" % (src_text(bc) or "TOPILMADI"))
    pr = parse_price_src(cfg.price_src) or pick_price(summary)
    out.append("Narx manbasi: %s" % (price_src_text(pr) or "TOPILMADI"))
    for c in summary.get("cats", []):
        out.append("Shtrix ma'lumotnomasi: Справочник.%s (egasi: %s): %s" % (c["name"], ", ".join(c.get("owners", [])) or "-",
                   ", ".join("%s[%s]" % (f["name"], "/".join(f.get("types", [])[:2])) for f in c.get("fields", []))))
    out.append("Nomzod registrlar:")
    for r in summary.get("regs", []):
        names = " ".join(f["name"] for f in r["fields"])
        if re.search(r"(?i)plu|плу|вес|оборуд|штрих|код|номенклат|товар|цен", r["name"] + " " + r.get("syn", "") + " " + names):
            out.append("  %s — %s: %s" % (r["name"], r.get("syn", ""), ", ".join(
                "%s[%s]" % (f["name"], "/".join(f.get("types", [])[:3])) for f in r["fields"])))
    return "\n".join(out)


def codes_payload(items, src):
    items = sorted(items, key=lambda x: (x["c"], x["n"]))
    return {"v": 1, "src": src, "items": items}


def run_codes(cfg, cloud, onec, state=None, force=False):
    """Tovar kodlarini yuboradi (o'zgargan bo'lsa yoki force). @return (yuborildimi, soni, src)."""
    state = load_state() if state is None else state
    onec.connect()
    try:
        items, src = onec.goods_codes()
    finally:
        onec.close()
    payload = codes_payload(items, src)
    raw = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    try:
        with open(CODES_OUT, "wb") as f:
            f.write(raw)
    except OSError:
        pass
    sig = hashlib.sha256(raw).hexdigest()
    if not items:
        return False, 0, src
    if not force and state.get("__codes__") == sig:
        return False, len(items), src
    cloud.upload(file_key("codes", int(time.time() * 1000)), "codes.json", raw, "", "codes")
    state["__codes__"] = sig
    save_state(state)
    log.info("tovar kodlari yuborildi: %d ta (PLU: %s; shtrix: %s)", len(items), src.get("plu") or "-", src.get("bc") or "-")
    return True, len(items), src


def codes_status(sent, n, src):
    t = "Tovar kodlari: %d ta" % n + (" yuborildi" if sent else " (o'zgarmagan)")
    if src.get("types"):
        t += "; narx turlari: " + ", ".join(src["types"][:8])
    if not src.get("plu"):
        t += "; PLU manbasi topilmadi"
    if not src.get("bc"):
        t += "; shtrix manbasi topilmadi"
    return t


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


def parse_live(sch):
    """v3.2 jadval: (daqiqa, ish vaqti yoqiqmi, boshi, oxiri) — daqiqa 0 bo'lsa eski soatlik rejim."""
    sch = sch or {}
    def gi(k, d):
        try:
            return int(sch.get(k, d) or d) if sch.get(k, d) is not None else d
        except (TypeError, ValueError):
            return d
    m = max(0, min(gi("m", 0), 24 * 60))
    won = bool(sch.get("won", False))
    ws = max(0, min(gi("ws", 8 * 60), 1439))
    we = max(0, min(gi("we", 20 * 60), 1439))
    return m, won, ws, we


def is_work_time(won, ws, we, minute):
    if not won or ws == we:
        return True
    if ws < we:
        return ws <= minute < we
    return minute >= ws or minute < we


def final_minute(we):
    return (we + 1439) % 1440


def live_interval(minutes, hours, won, ws, we, now=None):
    """Tezkor yangilash oralig'i (soniya) yoki 0 (o'chiq). Ish vaqtidan tashqarida — soatiga bir."""
    if minutes <= 0:
        return 0
    now = now or dt.datetime.now()
    sec = max(60, minutes * 60)
    if not is_work_time(won, ws, we, now.hour * 60 + now.minute):
        sec = max(sec, 3600)
    return sec


def upload_json(cloud, kind, payload, state, force=False):
    """JSON'ni faqat o'zgargan bo'lsa yuboradi. @return yuborildimi."""
    import onec_data
    sig = onec_data.payload_sig(payload)
    key = "__%s__" % kind
    if not force and state.get(key) == sig:
        return False
    raw = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    cloud.upload(file_key(kind, int(time.time() * 1000)), kind + ".json", raw, "", kind)
    state[key] = sig
    save_state(state)
    return True


def data_tick(cfg, cloud, onec, req=None, now=None):
    """Savdolar va ta'minotchilar (1C'dan o'qish). [req] — Admin so'rovi ({k, f, t}) yoki None (jadval). @return matn."""
    import onec_data
    now = now or dt.datetime.now()
    st = load_state()
    out = []
    # v4.1: "rep" (hisobotlar) — faqat Admin so'raganda
    kinds = [req.get("k")] if req and req.get("k") in ("sales", "supp", "rep") else ["sales", "supp"]
    onec.connect()
    try:
        ms = int(time.time() * 1000)
        if "sales" in kinds:
            f, t = onec_data.period_of(req if req and req.get("k") == "sales" else None, cfg.sales_days, now)
            p = onec_data.sales_payload(onec, f, t, ms)
            sent = upload_json(cloud, "sales", p, st, force=bool(req))
            out.append("1C savdolar: %d ta hujjat%s" % (len(p["docs"]), "" if sent else " (o'zgarmagan)"))
        if "supp" in kinds:
            f, t = onec_data.period_of(req if req and req.get("k") == "supp" else None, cfg.supp_days, now)
            p = onec_data.suppliers_payload(onec, f, t, ms)
            sent = upload_json(cloud, "supp", p, st, force=bool(req))
            out.append("Ta'minotchilar: %d ta%s" % (len(p["list"]), "" if sent else " (o'zgarmagan)"))
        if "rep" in kinds:
            f, t = onec_data.period_of(req, 1, now)
            p = onec_data.reports_payload(onec, f, t, ms)
            upload_json(cloud, "rep", p, st, force=True)
            out.append("Hisobotlar: %s%s" % (onec_data.ymd(f), (" (xato: %d)" % len(p["err"])) if p["err"] else ""))
    finally:
        onec.close()
    return "; ".join(out)


def loop(cfg):
    try:
        import onec_data
        import onec_write
    except ImportError as e:
        raise SystemExit("onec_data.py va onec_write.py onec_export.py yonida bo'lishi kerak (%s)" % e)
    cloud = Cloud(cfg)
    onec = OneC(cfg)
    writer = onec_write.Writer(cfg, cloud, onec, HERE, app_onec=OneCApp(cfg))
    inbox = cfg.inbox
    try:
        fix_startup()
    except Exception as e:  # noqa: BLE001
        log.warning("avtozapusk faylini tuzatib bo'lmadi: %s", e)
    waker = Waker(cfg, ["w/%s" % inbox, "p/%s/scale" % inbox, "p/%s/dreq" % inbox, "p/%s/creq" % inbox,
                        "p/%s/ureq" % inbox]).start()
    # v5.3: telefonlar ko'prigi - alohida oqimda (1C band bo'lsa ham telefonlar kutib qolmaydi)
    if getattr(cfg, "relay_on", True):
        try:
            import relay
            relay.start(cfg, cloud, HERE, lambda paths: Waker(cfg, paths).start(), getattr(cfg, "sql", None), PC_VERSION)
        except Exception as e:  # noqa: BLE001
            log.warning("telefonlar ko'prigi ishga tushmadi: %s", e)
    # v5.4: kassa (sensor) oynasi - do'kon tarmog'idagi monoblok brauzerda ochadi (o'z oqimi va 1C ulanishi bilan)
    kassa_svc = None
    try:
        import kassa
        kassa_svc = kassa.start(cfg, HERE, cloud, OneC, OneCApp, wake=waker.event.set)
    except Exception as e:  # noqa: BLE001
        log.warning("kassa oynasi ishga tushmadi: %s", e)

    def do_writes():
        """1C'ga yozish navbati (Admin tasdiqlagan). Tovar o'zgargan bo'lsa — kodlar/narxlar darhol telefonlarga."""
        try:
            done, errs = writer.run()
            if done or errs:
                safe_status(cloud, "1C'ga yozildi: %d ta%s" % (done, ", xato: %d" % errs if errs else ""), errs == 0)
        except Exception as e:  # noqa: BLE001
            log.warning("1C'ga yozish navbati: %s", e)
        writer.idle()
        # (hisobot tuzilayotgan payt — ulanish band: kodlar keyingi aylanishda)
        if writer.goods_changed and cfg.codes_on and getattr(onec, "c", None) is None:
            writer.goods_changed = False
            try:
                run_codes(cfg, cloud, onec, load_state())
            except Exception as e:  # noqa: BLE001
                log.warning("kodlarni yangilash: %s", e)

    def urgent():
        if waker.event.is_set():
            waker.event.clear()
            do_writes()

    last_run = 0.0
    last_codes = 0.0
    last_live = 0.0
    last_final_day = ""
    last_upd = 0.0
    while True:
        # v5.4: "hozir yangilash" - Admin ilovasidan yoki kassa sozlamasidan (kompyuterni qayta yoqish shart emas)
        asked = update_asked(cloud, kassa_svc)
        if asked:
            last_upd = 0.0
        # v5.3: dastur o'zi yangilanadi (soatiga bir marta tekshiradi; yozish navbati bo'sh paytda)
        if time.time() - last_upd >= UPDATE_SEC:
            last_upd = time.time()
            try:
                import selfupdate
                if selfupdate.check(cloud, inbox, HERE):
                    safe_status(cloud, "Kompyuter dasturi yangilandi — qayta ishga tushmoqda", True)
                    if kassa_svc is not None:
                        kassa_svc.updating = "Dastur yangilanmoqda — 1 daqiqa kuting, kassa o'zi qayta ochiladi"
                        kassa_svc.wait_idle(90)     # kassa sotuvi yozilayotgan bo'lsa tugashini kutadi
                    writer.quit()
                    selfupdate.restart()
                elif asked:
                    safe_status(cloud, "Kompyuter dasturi eng yangi versiyada (%s)" % PC_VERSION, True)
            except Exception as e:  # noqa: BLE001
                log.warning("yangilanishni tekshirib bo'lmadi: %s", e)
                last_upd = time.time() - UPDATE_SEC + 300    # internet yo'q edi - 5 daqiqadan keyin qayta
        # v3.12: avval yozish navbati — Admin kutib qolmasin
        do_writes()
        try:
            sch = cloud.schedule()
        except Exception as e:
            log.warning("jadvalni o'qib bo'lmadi: %s", e)
            sch = {}
        hours, days = parse_schedule(sch, cfg)
        minutes, won, ws, we = parse_live(sch)
        now = dt.datetime.now()
        # Ish tugashidan 1 daqiqa oldin — hammasi to'liq (telefonlar oxirgi ma'lumotni olib qoladi)
        final_now = won and now.hour * 60 + now.minute == final_minute(we) and last_final_day != now.strftime("%Y%m%d")
        if final_now:
            last_final_day = now.strftime("%Y%m%d")
        full_due = (hours > 0 or minutes > 0) and cfg.report and time.time() - last_run >= max(hours, 1) * 3600 - 60
        if full_due or (final_now and cfg.report):
            last_run = time.time()
            try:
                sent, checked, errors = run_once(cfg, cloud, onec, days, between=urgent)
                msg = status_text(sent, checked, errors)
                log.info(msg)
                safe_status(cloud, msg, not errors or sent > 0)
            except SystemExit as e:
                log.error("%s", e)
                safe_status(cloud, str(e), False)
            except Exception:
                log.exception("eksport xatosi")
                safe_status(cloud, "Xato: eksport", False)
        # Tezkor (daqiqalik): bugun harakati borlarning kartochkasi, savdolar, ta'minotchilar, kodlar
        live = live_interval(minutes, hours, won, ws, we, now)
        if (live and time.time() - last_live >= live - 5) or final_now:
            last_live = time.time()
            try:
                if cfg.report and not full_due:
                    onec.connect()
                    try:
                        names = onec_data.active_today(onec, now)
                    finally:
                        onec.close()
                    if names and cfg.only:
                        names = names & set(cfg.only)
                    if names:
                        sent, checked, errors = run_once(cfg, cloud, onec, days, only=sorted(names), between=urgent)
                        if sent:
                            log.info("tezkor: %s", status_text(sent, checked, errors))
                if cfg.data_on:
                    msg = data_tick(cfg, cloud, onec, now=now)
                    log.info(msg)
                if cfg.codes_on:
                    st = load_state()
                    if time.time() - st.get("__codes_live__", 0) >= max(live, 300) or final_now:
                        run_codes(cfg, cloud, onec, st)
                        st = load_state()
                        st["__codes_live__"] = time.time()
                        save_state(st)
            except SystemExit as e:
                log.error("%s", e)
                safe_status(cloud, str(e), False)
            except Exception:
                log.exception("tezkor yangilash xatosi")
        # Admin so'rovi: "shu davrdagi savdolar / ta'minotchilar"
        try:
            req = cloud.data_request()
        except Exception:
            req = {}
        if req and cfg.data_on:
            st = load_state()
            at = req.get("at") or 0
            if at and at > st.get("__dreq__", 0):
                try:
                    msg = data_tick(cfg, cloud, onec, req=req)
                    safe_status(cloud, msg, True)
                except Exception as e:  # noqa: BLE001
                    log.exception("so'rov bajarilmadi")
                    safe_status(cloud, "1C ma'lumoti: %s" % str(e)[:200], False)
                st = load_state()
                st["__dreq__"] = at
                save_state(st)
        # v3.10: taroziga narx va PLU (Admin so'rovi; 1C'dagi "ВыгрузкаТоваровНаВесы" bilan bir xil)
        try:
            import onec_scale
            msg = onec_scale.tick(cloud, onec, load_state(), save_state)
            if msg:
                safe_status(cloud, msg, msg.startswith("Taroziga"))
        except Exception as e:  # noqa: BLE001
            log.warning("tarozi so'rovi: %s", e)
        # (1C'ga yozish navbati — aylanish boshida va hisobot orasida; so'rov kelsa darhol uyg'onadi)
        if cfg.codes_on:
            codes_tick(cfg, cloud, onec, last_codes)
            st = load_state()
            last_codes = st.get("__codes_at__", last_codes)
        # So'rov kelsa darhol uyg'onadi (Firebase oqimi), aks holda TICK_SEC
        if waker.wait(TICK_SEC):
            log.info("so'rov keldi — darhol bajarilmoqda")


def update_asked(cloud, kassa_svc=None):
    """Yangilash so'ralganmi: bulutdagi p/{inbox}/ureq (Admin ilovasi) yoki kassa sozlamasi. Bir so'rov bir marta."""
    asked = False
    try:
        req = cloud.update_request()
    except Exception:  # noqa: BLE001
        req = 0
    if req:
        st = load_state()
        if req > st.get("__ureq__", 0):
            st["__ureq__"] = req
            save_state(st)
            asked = True
    if kassa_svc is not None and kassa_svc.update_asked.is_set():
        kassa_svc.update_asked.clear()
        asked = True
    if asked:
        log.info("yangilash so'raldi — hozir tekshiriladi")
    return asked


def codes_tick(cfg, cloud, onec, last_codes, now=None):
    """Tovar kodlari: vaqti kelgan bo'lsa yoki telefon so'ragan bo'lsa. @return bajarildimi."""
    now = now or time.time()
    try:
        req = cloud.codes_request()
    except Exception:
        req = 0
    st = load_state()
    asked = req and req > st.get("__creq__", 0)
    if not asked and now - last_codes < cfg.codes_hours * 3600:
        return False
    try:
        sent, n, src = run_codes(cfg, cloud, onec, st, force=bool(asked))
        msg = codes_status(sent, n, src)
        log.info(msg)
        safe_status(cloud, msg, n > 0)
    except SystemExit as e:
        log.error("%s", e)
        safe_status(cloud, "Tovar kodlari: %s" % e, False)
    except Exception as e:
        log.exception("tovar kodlari xatosi")
        safe_status(cloud, "Tovar kodlari xatosi: %s" % str(e)[:200], False)
    st = load_state()
    st["__codes_at__"] = now
    if asked:
        st["__creq__"] = req
    save_state(st)
    return True


def safe_status(cloud, text, ok):
    try:
        cloud.status(text, ok)
    except Exception as e:
        log.warning("holat yozilmadi: %s", e)


STARTUP_NAME = "IMSOFT_1C_kodlar.vbs"


def startup_text(py_w, script, workdir):
    return ("' IMSOFT: 1C <-> telefonlar (fonda, oynasiz). Dastur o'zi yozadi.\r\n"
            'Set sh = CreateObject("WScript.Shell")\r\n'
            'sh.CurrentDirectory = "%s"\r\n'
            'sh.Run """%s"" ""%s""", 0, False\r\n') % (workdir, py_w, script)


def fix_startup(startup_dir=None, py_w=None, script=None, workdir=None):
    """v5.3: avtozapusk faylini o'zi tuzatadi. Eski fayl ASCII'da yozilgan edi — yo'ldagi kirill harflari
    ("Администратор") "?" bo'lib qolib, kompyuter yonganda "Не удается найти указанный файл" chiqardi.
    Endi UTF-16'da, hozir ishlayotgan Python va shu papkaga qarab qayta yoziladi. @return yozildimi."""
    if startup_dir is None:
        if os.name != "nt":
            return False
        startup_dir = os.path.join(os.environ.get("APPDATA", ""), r"Microsoft\Windows\Start Menu\Programs\Startup")
    if not os.path.isdir(startup_dir):
        return False
    if py_w is None:
        w = os.path.join(os.path.dirname(sys.executable), "pythonw.exe")
        py_w = w if os.path.exists(w) else sys.executable
    script = script or os.path.abspath(__file__)
    workdir = workdir or HERE
    want = startup_text(py_w, script, workdir)
    path = os.path.join(startup_dir, STARTUP_NAME)
    try:
        with open(path, "rb") as f:
            raw = f.read()
        cur = raw.decode("utf-16") if raw[:2] in (b"\xff\xfe", b"\xfe\xff") else None
        if cur == want:
            return False
    except OSError:
        pass
    with open(path, "w", encoding="utf-16", newline="") as f:
        f.write(want)
    log.info("avtozapusk fayli tuzatildi: %s", path)
    return True


def app_test(cfg, out_path=None):
    """v5.4 (pc 11): kassa sotuvi 1C'ga "ko'prik" orqali yoziladi (1C oddiy ochiladi + kassa_bridge.epf).
    Shu sinov qayerda to'xtashini ko'rsatadi (oynada va app_test.txt faylida - rasmga olib dasturchiga yuboring)."""
    out_path = out_path or os.path.join(HERE, "app_test.txt")
    lines = []

    def say(msg):
        lines.append(msg)
        print(msg)
        try:
            with open(out_path, "w", encoding="utf-8") as f:
                f.write("\n".join(lines) + "\n")
        except OSError:
            pass
    import ctypes
    try:
        admin = bool(ctypes.windll.shell32.IsUserAnAdmin())
    except Exception:  # noqa: BLE001
        admin = None
    say("1C kassa ko'prigi sinovi  %s  (Python %s, %d-bit, administrator: %s)" % (
        dt.datetime.now().strftime("%d.%m.%Y %H:%M"), sys.version.split()[0], 64 if sys.maxsize > 2 ** 32 else 32, admin))
    for prog, kind in (("V83.COMConnector", "InprocServer32"), ("V83.Application", "LocalServer32")):
        try:
            say("   %s: %s" % (prog, _clsid_server(prog, kind)))
        except OSError as e:
            say("   %s: ro'yxatda yo'q (%s)" % (prog, e))
    import onec_bridge
    exe = onec_bridge.find_exe()
    say("1) 1C dasturi: %s" % (exe or "TOPILMADI"))
    if not exe:
        return False
    changed, errs = onec_bridge.allow_external(exe)
    say("2) Tashqi ishlov ogohlantirishi (conf.cfg): %s" % (
        "o'chirildi" if changed else ("XATO: " + "; ".join(errs)) if errs else "allaqachon o'chiq"))
    say("3) 1C oddiy usulda ochilmoqda (kassa ko'prigi bilan, 10-60 soniya)... Sinovdan keyin bu 1C o'zi yopiladi. "
        "Agar 1C biror savol so'rasa — rasmga oling.")
    br = onec_bridge.Bridge(cfg.conn_string(), HERE, exe, jobs_dir="kassa_jobs_test")   # kassa navbatiga tegmaydi
    t0 = time.time()
    try:
        res = br.run([onec_bridge.probe_job("probe_%d" % int(t0))], timeout=120)
    except (RuntimeError, OSError) as e:
        say("   XATO (%.0f s): %s" % (time.time() - t0, e))
        return False
    finally:
        br.stop()             # sinov 1C'i yopiladi (kassaning o'z 1C'i alohida, ochiq turadi)
    r = list(res.values())[0] if res else ("ERR", "javob yo'q")
    ok = r[0] == "OK" and r[1] == "PROBE"
    say("   %s (%.0f s): %s" % ("OK" if ok else "XATO", time.time() - t0, " | ".join(r[1:]) if not ok else r[2]))
    say("4) Natija: %s" % ("HAMMASI ISHLAYDI - kassa sotuvlari 1C'ga yoziladi" if ok else "XATO BOR"))
    return ok


def crash_info():
    """1cv8.exe yiqilgan bo'lsa: Windows jurnalidagi oxirgi xatolar va 1C dump fayllari (dasturchiga)."""
    out = []
    try:
        import subprocess
        r = subprocess.run(["wevtutil", "qe", "Application", "/c:6", "/rd:true", "/f:text",
                            "/q:*[System[(Level=1 or Level=2)]]"], capture_output=True, timeout=30,
                           creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        text = r.stdout.decode("cp866", "replace")
        keep = [ln.strip() for ln in text.splitlines()
                if re.search(r"(?i)1cv8|date:|source:|faulting|exception code|описание|имя сбойного|код исключения", ln)]
        out.append("Windows jurnali (oxirgi xatolar):")
        out.extend(keep[:30] or ["  (1C xatosi yo'q)"])
    except Exception as e:  # noqa: BLE001
        out.append("Windows jurnali o'qilmadi: %s" % e)
    for base in (os.environ.get("APPDATA", ""), os.environ.get("LOCALAPPDATA", "")):
        d = os.path.join(base, "1C", "1cv8", "dumps")
        try:
            files = sorted((os.path.join(d, f) for f in os.listdir(d)), key=os.path.getmtime, reverse=True)[:3]
        except OSError:
            continue
        for f in files:
            out.append("1C dump: %s (%s)" % (f, dt.datetime.fromtimestamp(os.path.getmtime(f)).strftime("%d.%m %H:%M")))
    return out


def main():
    ap = argparse.ArgumentParser(description="1C → Excel → telefon (avtomatik)")
    ap.add_argument("--discover", action="store_true", help="1C tuzilishini discover.txt ga yozish")
    ap.add_argument("--once", action="store_true", help="hozir bir marta eksport")
    ap.add_argument("--one", metavar="NOM", help="faqat bitta kontragent (fayl export/ papkada ham qoladi)")
    ap.add_argument("--status", action="store_true", help="bulutdagi jadval va holat")
    ap.add_argument("--codes", action="store_true", help="tovar kodlari (PLU, shtrix) ni hozir yuborish")
    ap.add_argument("--sample", action="store_true", help="PLU/shtrix/narx namunalari -> sample.txt (dasturchiga)")
    ap.add_argument("--days", type=int, help="necha kunlik (jadvaldagini almashtiradi)")
    ap.add_argument("--app-test", action="store_true", help="1C oynasi (V83.Application) orqali ulanish sinovi")
    ap.add_argument("--config", default=CONFIG)
    ap.add_argument("-v", action="store_true")
    a = ap.parse_args()
    setup_logging(a.v)
    cfg = Config(a.config)
    if a.sample:
        cfg.check_onec(need_report=False)
        o = OneC(cfg).connect()
        try:
            txt = o.sample()
        finally:
            o.close()
        path = os.path.join(HERE, "sample.txt")
        with open(path, "w", encoding="utf-8") as f:
            f.write(txt)
        print("Tayyor:", path, "— shu faylni dasturchiga yuboring (ichida parol yo'q).")
        return
    if a.app_test:
        cfg.check_onec(need_report=False)
        app_test(cfg)
        return
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
    if a.codes:
        cfg.check_onec(need_report=False)
        sent, n, src = run_codes(cfg, cloud, OneC(cfg), force=True)
        msg = codes_status(sent, n, src)
        safe_status(cloud, msg, n > 0)
        print(msg)
        print("PLU manbasi:", src.get("plu") or "topilmadi", "| shtrix:", src.get("bc") or "topilmadi")
        print("Narx manbasi:", src.get("price") or "topilmadi", "| turlari:", ", ".join(src.get("types") or []) or "-")
        print("Fayl:", CODES_OUT)
        return
    if not a.once and not a.one and not cfg.report:
        cfg.check_onec(need_report=False)
        lock = single_instance()
        if lock is None:
            log.info("Boshqa nusxa allaqachon ishlayapti — chiqildi")
            return
        log.info("Ishga tushdi (faqat tovar kodlari — [onec] report bo'sh)")
        ensure_autostart()
        loop(cfg)
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
    lock = single_instance()
    if lock is None:
        log.info("Boshqa nusxa allaqachon ishlayapti — chiqildi")
        return
    log.info("Ishga tushdi")
    ensure_autostart()
    loop(cfg)


if __name__ == "__main__":
    main()
