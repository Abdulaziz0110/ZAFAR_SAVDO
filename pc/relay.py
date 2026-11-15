#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
v5.3: telefonlar ko'prigi KOMPYUTERDA. Agent/Admin telefoni <-> bulut (Firebase) <-> shu dastur <-> SQL server.

Avval bu ishni Admin telefoni bajarar edi (telefon o'chsa yoki VPN bo'lmasa - hammasi to'xtardi). Endi do'kon
kompyuteri yoniq va internetda bo'lsa:
  * katalog (tovar, narx, qoldiq, mijoz, qarz, kurs) bulutga o'zi qo'yiladi - telefonlar VPNsiz oladi;
  * telefondan kelgan savdo/to'lov SQL serverdagi "savollar" jadvaliga yoziladi (aynan avvalgi matn, dublikatsiz);
  * kirish, qarz, hisobot, server vaqti so'rovlariga javob beriladi.
Protokol va jadvallar O'ZGARMAGAN (ilovadagi RelayCodec / RelayHost bilan bir xil).

SQL serverga ulanish: Windows'ning o'zidagi ADO (pywin32) - qo'shimcha kutubxona kerak emas.
Sozlama: Admin ilovasi bulut orqali yuboradi (p/{inbox}/rl) va relay.json da saqlanadi; yoki onec.ini da:
  [sql]
  host = 127.0.0.1
  database = baza
  user = sa
  password = ...
  key = <agentlar guruhi kaliti>
"""
import base64
import datetime as dt
import gzip
import hashlib
import hmac
import json
import logging
import os
import threading
import time

log = logging.getLogger("onec_export")

PROOF_ITER = 20000
HOST_NAME = "pc"
RQ_OLD_MS = 10 * 60 * 1000
CATALOG_SEC = 5 * 60
IDLE_SEC = 20
SERVER_TIME = {".sv": "timestamp"}
OWN_DEVICE = "admin"              # Admin'ning o'z savdosi (ko'zgusini o'zi qiladi)


# ---------------------------------------------------------------- sof qism (testlanadi)

def oid(user_id, type_, payload):
    """Savdo/to'lov kaliti: agent + tur + matn (ilovadagi RelayCodec.oid bilan bir xil)."""
    d = hashlib.sha256((user_id + "\u0001" + type_ + "\u0001" + payload).encode("utf-8")).hexdigest()
    return d[:40]


def proof(password, salt, iterations=PROOF_ITER):
    k = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), ("imsoft-agent:" + salt).encode("utf-8"), iterations, 32)
    return base64.urlsafe_b64encode(k).decode("ascii").rstrip("=")


def same_proof(a, b):
    return hmac.compare_digest(str(a).encode("utf-8"), str(b).encode("utf-8"))


def pack(text):
    return base64.b64encode(gzip.compress(text.encode("utf-8"), 6, mtime=0)).decode("ascii")


def unpack(blob):
    return gzip.decompress(base64.b64decode(blob)).decode("utf-8")


def sval(v):
    """SQL qiymati -> matn (telefondagi jTDS getString kabi). None -> ''."""
    if v is None:
        return ""
    if isinstance(v, bool):
        return "1" if v else "0"
    if isinstance(v, (dt.datetime, dt.date)):
        try:
            return "%04d-%02d-%02d %02d:%02d:%02d.0" % (v.year, v.month, v.day, getattr(v, "hour", 0), getattr(v, "minute", 0), getattr(v, "second", 0))
        except Exception:  # noqa: BLE001
            return str(v)
    if isinstance(v, float):
        if v == int(v) and abs(v) < 1e15:
            return str(int(v))
        return repr(v)
    return str(v)


def payload_same(sent, stored):
    """Serverdagi matn biz yuborgan bilan bir xilmi (varchar ustunda lotin bo'lmagan harf '?' bo'ladi)."""
    a = (sent or "").rstrip()
    b = (stored or "").rstrip()
    if a == b:
        return True
    if len(a) != len(b):
        return False
    for x, y in zip(a, b):
        if x == y:
            continue
        if ord(x) > 127 and y == "?":
            continue
        return False
    return True


def catalog_json(snap, agents, at, branches=(), last_costs=None):
    """Katalog (ilovadagi RelayCodec.catalogToJson bilan bir xil tuzilma)."""
    lc = last_costs if isinstance(last_costs, dict) else {}
    return json.dumps({
        "v": 1, "at": at,
        "g": snap["groups"], "r": snap["regions"], "s": snap["stores"], "k": snap["rates"], "p": snap["products"],
        "a": agents, "br": list(branches or []), "lc": {k: lc[k] for k in sorted(lc)},
    }, ensure_ascii=False, separators=(",", ":"))


def parse_payment(payload):
    """To'lov matni -> (do'kon, naqd, o'tkazma, click, izoh)."""
    f = payload.split("~")[0].split("^")

    def d(i):
        try:
            return float((f[i] if len(f) > i else "0").replace(",", ".") or 0)
        except ValueError:
            return 0.0
    store = f[1] if len(f) > 1 else ""
    cash = d(7) if len(f) > 7 else d(2)
    return store, cash, d(8), d(9), (f[6] if len(f) > 6 else "")


class Rejected(Exception):
    """Server so'rovni bajarmay rad etdi (qayta yuborish foydasiz)."""


class NoServer(Exception):
    """SQL server ko'rinmayapti (keyinroq qayta uriniladi)."""


# ---------------------------------------------------------------- SQL server (ADO)

class Sql:
    """SQL server: Windows ADO orqali. Har chaqiriqda ochib-yopiladi (uyqudan keyin ham ishlaydi)."""

    def __init__(self, conf):
        self.conf = dict(conf or {})

    def ready(self):
        return bool(self.conf.get("host") and self.conf.get("database"))

    def _conn_strings(self):
        c = self.conf
        base = "Data Source=%s;Initial Catalog=%s;Connect Timeout=8;" % (c.get("host"), c.get("database"))
        auth = ("User ID=%s;Password=%s;" % (c.get("user"), c.get("password", ""))) if c.get("user") else "Integrated Security=SSPI;"
        return ["Provider=%s;%s%s" % (p, base, auth) for p in ("MSOLEDBSQL", "SQLOLEDB")]

    def _open(self):
        try:
            import pythoncom
            import win32com.client
        except ImportError:
            raise NoServer("pywin32 o'rnatilmagan")
        pythoncom.CoInitialize()
        last = None
        for cs in self._conn_strings():
            try:
                conn = win32com.client.Dispatch("ADODB.Connection")
                conn.CommandTimeout = 60
                conn.Open(cs)
                return conn
            except Exception as e:  # noqa: BLE001
                last = e
        raise NoServer("SQL serverga ulanib bo'lmadi: %s" % str(last)[:200])

    def run(self, sql, params=(), opened=None):
        """So'rov -> qatorlar ro'yxati (har qator - qiymatlar ro'yxati). [opened] - 'ulanish ochildi' belgisi (dict)."""
        conn = self._open()
        if opened is not None:
            opened["ok"] = True
        try:
            import win32com.client
            cmd = win32com.client.Dispatch("ADODB.Command")
            cmd.ActiveConnection = conn
            cmd.CommandText = sql
            for v in params:
                s = "" if v is None else str(v)
                n = max(len(s), 1)
                # 202 = adVarWChar, 203 = adLongVarWChar; 1 = adParamInput
                cmd.Parameters.Append(cmd.CreateParameter("", 203 if n > 4000 else 202, 1, n, s))
            res = cmd.Execute()
            rs = res[0] if isinstance(res, tuple) else res
            out = []
            try:
                if rs is not None and rs.State == 1 and not rs.EOF:
                    cols = rs.GetRows()
                    if cols:
                        for i in range(len(cols[0])):
                            out.append([col[i] for col in cols])
            except Exception as e:  # noqa: BLE001 - natija to'plami yo'q so'rov
                log.debug("natija o'qilmadi: %s", e)
            return out
        finally:
            try:
                conn.Close()
            except Exception:  # noqa: BLE001
                pass


class Server:
    """Serverdagi jadvallar (ilovadagi ServerApi bilan bir xil so'rovlar)."""

    def __init__(self, sql, sleep=time.sleep, clock=time.time):
        self.sql = sql
        self.sleep = sleep
        self.clock = clock

    def _rows(self, q, params=()):
        return [[sval(v) for v in r] for r in self.sql.run(q, params)]

    def agent_by_phone(self, phone):
        r = self._rows("SELECT agent_id, fio, boshagan, telefon, parol FROM agentlar WHERE telefon = ?", [phone])
        return (r[0][:4], r[0][4]) if r else None

    def agents(self):
        return self._rows("SELECT agent_id, fio, boshagan, telefon FROM agentlar")

    def snapshot(self):
        return {
            "groups": self._rows("SELECT gruppa_id, gruppa_nomi FROM gruppa"),
            "regions": self._rows("SELECT region_id, region_nomi FROM region"),
            "stores": self._rows("SELECT region_id, kontr_id, kontr_nomi, qarzi_som, qarzi_val, time FROM kontragentlar"),
            "rates": [r[0] for r in self._rows("SELECT xozirgi_kurs FROM xozirgi_kurs")],
            "products": self._rows(
                "SELECT gruppa_id, tovar_id, nomi, valyuta_turi, sotish_narxi_optom_som, sotish_narxi_optom_val, blokdagi_soni, qoldigi, time, "
                "sotish_narxi_chakana_som, sotish_narxi_chakana_val, kirim_narxi_som, kirim_narxi_val FROM tovar"),
        }

    def time_ms(self):
        r = self.sql.run("SELECT DATEDIFF(SECOND, '1970-01-01', GETUTCDATE())")
        return int(float(sval(r[0][0]))) * 1000 if r else int(self.clock() * 1000)

    def insert(self, user_id, type_, payload):
        opened = {}
        try:
            r = self.sql.run("INSERT INTO savollar (user_id, sorov_turi, sorov_izox) output INSERTED.ID VALUES (?, ?, ?)",
                             [user_id, type_, payload], opened)
        except NoServer:
            raise
        except Exception as e:  # noqa: BLE001
            if not opened.get("ok"):
                raise NoServer(str(e)[:200])
            msg = str(e)
            low = msg.lower()
            if any(w in low for w in ("timeout", "timed out", "reset", "closed", "network", "communication link")):
                raise NoServer(msg[:200])      # aloqa uzildi - yozilgan bo'lishi mumkin: keyin qidirib topiladi
            raise Rejected(msg[:300])
        if not r:
            raise NoServer("so'rov ID olinmadi")
        return sval(r[0][0])

    def find(self, user_id, type_, payload):
        try:
            r = self.sql.run("SELECT TOP 1 ID FROM savollar WHERE user_id = ? AND sorov_turi = ? AND sorov_izox = ? ORDER BY ID DESC",
                             [user_id, type_, payload])
            if r:
                return sval(r[0][0])
        except NoServer:
            raise
        except Exception:  # noqa: BLE001 - ustun turi '=' ni qo'llamasa (text) - pastdagi qidiruv
            pass
        for row in self.sql.run("SELECT TOP 2000 ID, sorov_izox FROM savollar WHERE user_id = ? AND sorov_turi = ? ORDER BY ID DESC",
                                [user_id, type_]):
            if payload_same(payload, sval(row[1])):
                return sval(row[0])
        return None

    def request(self, agent_id, type_, payload, timeout):
        rid = self.insert(agent_id, type_, payload)
        end = self.clock() + timeout
        while self.clock() < end:
            v = None
            for row in self.sql.run("SELECT javob FROM javoblar WHERE sorov_id = ?", [rid]):
                v = sval(row[0])
            if v and v.strip() and v != "no":
                return v
            self.sleep(0.5)
        raise NoServer("Server javob bermadi")

    def debt(self, agent_id, store_id):
        parts = self.request(agent_id, "3", store_id, 20).split(":")
        som = (parts[0].strip() if parts else "") or "0"
        usd = (parts[1].strip() if len(parts) > 1 else "") or "0"
        return som, usd

    def report(self, agent_id, payload):
        return self.request(agent_id, "5", payload, 60).replace("\n", " ").replace("\\", "").replace("`", '"')


# ---------------------------------------------------------------- ko'prik

class Relay:
    def __init__(self, cloud, key, server, codes_file=None, clock=time.time, version=""):
        self.cloud = cloud
        self.key = key
        self.server = server
        self.codes_file = codes_file
        self.clock = clock
        self.version = version
        self.catalog_at = 0.0
        self.catalog_hash = ""
        self.codes_sent = 0
        self.cleaned_at = 0.0
        self.error = ""

    def path(self, sub):
        return "g/%s/%s" % (self.key, sub)

    def _map(self, sub):
        v = self.cloud.call("GET", self.path(sub), timeout=60)
        return v if isinstance(v, dict) else {}

    def now_ms(self):
        return int(self.clock() * 1000)

    # --- so'rovlar ---
    def answer(self, op, p):
        try:
            o = json.loads(p or "{}")
        except ValueError:
            o = {}
        if not isinstance(o, dict):
            o = {}
        s = lambda k: str(o.get(k) or "")  # noqa: E731
        if op == "login":
            got = self.server.agent_by_phone(s("ph"))
            if not got:
                return {"a": None}
            agent, password = got
            if not same_proof(proof(password, s("s")), s("pr")):
                return {"a": None}
            return {"a": agent}
        if op == "debt":
            som, usd = self.server.debt(s("a"), s("st"))
            return {"s": som, "u": usd}
        if op == "report":
            return self.server.report(s("a"), s("p"))
        if op == "time":
            return self.server.time_ms()
        raise KeyError(op)

    def requests(self):
        done = 0
        for rid, o in sorted(self._map("rq").items()):
            if not isinstance(o, dict):
                continue
            at = o.get("at") if isinstance(o.get("at"), (int, float)) else 0
            if 0 < at < self.now_ms() - RQ_OLD_MS:
                self._quiet("DELETE", "rq/%s" % rid)
                continue
            op = str(o.get("op") or "")
            if op in ("bc", "bcs"):
                # Shtrix kodni 1C'ga qo'yish - Admin navbati: saqlab qo'yiladi, Admin ilovasi ochilganda oladi
                self.cloud.call("PUT", self.path("bq/%s" % rid), {"at": SERVER_TIME, "op": op, "p": o.get("p") or ""}, print="silent")
                n = 1
                if op == "bcs":
                    try:
                        n = len(json.loads(o.get("p") or "{}").get("l") or [])
                    except (ValueError, AttributeError):
                        n = 0
                ans = {"ok": True, "r": True if op == "bc" else n}
            else:
                try:
                    ans = {"ok": True, "r": self.answer(op, str(o.get("p") or ""))}
                except KeyError:
                    ans = {"ok": False, "e": "unknown_op"}
                except Rejected as e:
                    ans = {"ok": False, "e": "rejected"}
                    log.info("rad etildi: %s", e)
                except Exception as e:  # noqa: BLE001
                    ans = {"ok": False, "e": str(e)[:300]}
            ans["at"] = SERVER_TIME
            try:
                self.cloud.call("PUT", self.path("rs/%s" % rid), ans, print="silent")
                self.cloud.call("DELETE", self.path("rq/%s" % rid))
                done += 1
            except Exception as e:  # noqa: BLE001
                log.warning("javob yozilmadi: %s", e)
        return done

    def _quiet(self, method, sub, body=None):
        try:
            self.cloud.call(method, self.path(sub), body, print="silent") if body is not None else self.cloud.call(method, self.path(sub))
        except Exception as e:  # noqa: BLE001
            log.debug("%s %s: %s", method, sub, e)

    # --- savdo va to'lovlar ---
    def blocked(self):
        ax = getattr(self, "_ax", None) or {}
        b = ax.get("bl")
        return set(b) if isinstance(b, list) else set()

    def write(self, oid_, o):
        """@return True - serverga yozildi. Server ko'rinmasa NoServer."""
        u = str(o.get("u") or "")
        t = str(o.get("t") or "")
        p = str(o.get("p") or "")
        if self.cloud.call("GET", self.path("obr/%s" % oid_)) is not None:
            self.cloud.call("DELETE", self.path("ob/%s" % oid_))
            return False
        dev = str(o.get("d") or "")
        bad = None
        if dev and dev in self.blocked():
            bad = "blocked"
        elif not u or t not in ("1", "4") or oid(u, t, p) != oid_:
            bad = "bad_item"
        if bad:
            self.cloud.call("PUT", self.path("obr/%s" % oid_), {"at": SERVER_TIME, "st": "rej", "m": bad}, print="silent")
            self.cloud.call("DELETE", self.path("ob/%s" % oid_))
            return False
        try:
            sid = self.server.find(u, t, p) or self.server.insert(u, t, p)
        except Rejected as e:
            self.cloud.call("PUT", self.path("obr/%s" % oid_), {"at": SERVER_TIME, "st": "rej", "m": str(e)[:300]}, print="silent")
            self.cloud.call("DELETE", self.path("ob/%s" % oid_))
            return False
        self.cloud.call("PUT", self.path("obr/%s" % oid_), {"at": SERVER_TIME, "st": "ok", "id": sid}, print="silent")
        # Admin ilovasi uchun iz: statistika, mijoz ilovasi tarixi va filial cheki (Admin ochilganda bajaradi).
        # Admin'ning o'z savdosi - ko'zgusini o'zi qilgan.
        if dev != OWN_DEVICE:
            self._quiet("PUT", "mr/%s" % oid_, {"at": SERVER_TIME, "u": u, "t": t, "p": p, "m": o.get("m")})
        self.cloud.call("DELETE", self.path("ob/%s" % oid_))
        return True

    def writes(self):
        done = 0
        items = self._map("ob")

        def at(kv):
            v = kv[1].get("at") if isinstance(kv[1], dict) else None
            return v if isinstance(v, (int, float)) else float("inf")
        for oid_, o in sorted(items.items(), key=at):
            if not isinstance(o, dict):
                continue
            try:
                if self.write(oid_, o):
                    done += 1
            except NoServer as e:
                self.error = str(e)[:200]
                break       # server ko'rinmayapti - qolganlari keyingi safar (tartib saqlanadi)
        return done

    # --- katalog ---
    def publish_catalog(self):
        snap = self.server.snapshot()
        agents = self.server.agents()
        try:
            ax = self.cloud.call("GET", self.path("ax"), timeout=30)
            self._ax = ax if isinstance(ax, dict) else {}
        except Exception:  # noqa: BLE001
            self._ax = getattr(self, "_ax", None) or {}
        br = self._ax.get("br") if isinstance(self._ax.get("br"), list) else []
        lc = self._ax.get("lc") if isinstance(self._ax.get("lc"), dict) else {}
        at = self.now_ms()
        sig = hashlib.sha256(catalog_json(snap, agents, 0, br, lc).encode("utf-8")).hexdigest()
        if sig == self.catalog_hash:
            self.cloud.call("PATCH", self.path("cat"), {"at": at}, print="silent")
        else:
            self.cloud.call("PATCH", self.path("cat"), {"at": at, "b": pack(catalog_json(snap, agents, at, br, lc))}, timeout=120, print="silent")
        self.catalog_hash = sig
        self.catalog_at = self.clock()
        return len(snap["products"])

    def publish_codes(self):
        f = self.codes_file
        if not f or not os.path.isfile(f):
            return False
        at = int(os.path.getmtime(f) * 1000)
        if at <= self.codes_sent:
            return False
        with open(f, "rb") as fh:
            raw = fh.read()
        blob = base64.b64encode(gzip.compress(raw, 6, mtime=0)).decode("ascii")
        self.cloud.call("PATCH", self.path("codes"), {"at": at, "b": blob}, timeout=120, print="silent")
        self.codes_sent = at
        return True

    def cleanup(self):
        now = self.now_ms()
        for sub, keep in (("obr", 30 * 86400000), ("lbr", 86400000), ("rs", 86400000), ("mr", 30 * 86400000), ("bq", 30 * 86400000)):
            for k, v in self._map(sub).items():
                at = v.get("at") if isinstance(v, dict) and isinstance(v.get("at"), (int, float)) else 0
                if 0 < at < now - keep:
                    self._quiet("DELETE", "%s/%s" % (sub, k))

    def pump(self, force_catalog=False):
        """Hamma kutayotgan ish. @return bajarilganlar soni."""
        self.error = ""
        done = 0
        try:
            done += self.requests()
        except Exception as e:  # noqa: BLE001
            self.error = str(e)[:200]
        try:
            done += self.writes()
        except Exception as e:  # noqa: BLE001
            self.error = str(e)[:200]
        try:
            asked = self.cloud.call("GET", self.path("creq"), timeout=30)
            asked = asked if isinstance(asked, (int, float)) else 0
            if force_catalog or asked > self.catalog_at * 1000 or self.clock() - self.catalog_at > CATALOG_SEC:
                self.publish_catalog()
        except Exception as e:  # noqa: BLE001
            self.error = str(e)[:200]
            self.catalog_at = self.clock() - CATALOG_SEC + 60     # bir daqiqadan keyin qayta urinadi
        try:
            self.publish_codes()
        except Exception as e:  # noqa: BLE001
            log.debug("kodlar: %s", e)
        if self.clock() - self.cleaned_at > 86400:
            try:
                self.cleanup()
                self.cleaned_at = self.clock()
            except Exception as e:  # noqa: BLE001
                log.debug("tozalash: %s", e)
        try:
            left = self.cloud.call("GET", self.path("ob"), shallow="true", timeout=30)
            q = len(left) if isinstance(left, dict) else 0
            self.cloud.call("PUT", self.path("hb"), {"at": SERVER_TIME, "q": q, "e": self.error, "v": self.version, "h": HOST_NAME}, print="silent")
        except Exception as e:  # noqa: BLE001
            log.debug("holat: %s", e)
        return done


# ---------------------------------------------------------------- sozlama va ishga tushirish

def load_conf(here, cfg_ini=None, cloud=None, inbox=""):
    """Sozlama: onec.ini [sql] > bulutdan (Admin yuborgan) > relay.json (oxirgi ma'lum)."""
    path = os.path.join(here, "relay.json")
    conf = {}
    try:
        with open(path, "r", encoding="utf-8") as f:
            conf = json.load(f) or {}
    except (OSError, ValueError):
        conf = {}
    if cloud is not None and inbox:
        try:
            v = cloud.call("GET", "p/%s/rl" % inbox, timeout=30)
            if isinstance(v, dict) and v.get("key"):
                fresh = {k: str(v.get(k) or "") for k in ("key", "host", "database", "user", "password")}
                if fresh != {k: conf.get(k, "") for k in fresh}:
                    conf.update(fresh)
                    try:
                        with open(path, "w", encoding="utf-8") as f:
                            json.dump(conf, f, ensure_ascii=False)
                    except OSError:
                        pass
        except Exception as e:  # noqa: BLE001
            log.debug("ko'prik sozlamasi o'qilmadi: %s", e)
    if isinstance(cfg_ini, dict):
        for k in ("key", "host", "database", "user", "password"):
            if cfg_ini.get(k):
                conf[k] = cfg_ini[k]
    # Telefonda manzil "192.168.1.5:1433" ko'rinishida bo'lishi mumkin - ADO uchun "192.168.1.5,1433"
    h = str(conf.get("host") or "")
    if ":" in h and "," not in h and "\\" not in h:
        conf["host"] = h.replace(":", ",", 1)
    return conf


def start(cfg, cloud, here, waker_factory, ini=None, version=""):
    """Alohida oqimda ishga tushiradi. Sozlama bo'lmasa - kutib turadi (Admin yuborishi bilan boshlanadi)."""
    def run():
        relay = None
        waker = None
        conf_at = 0.0
        while True:
            try:
                if relay is None or time.time() - conf_at > 300:
                    conf = load_conf(here, ini, cloud, cfg.inbox)
                    conf_at = time.time()
                    sql = Sql(conf)
                    if conf.get("key") and sql.ready():
                        if relay is None or relay.key != conf["key"] or relay.server.sql.conf != sql.conf:
                            relay = Relay(cloud, conf["key"], Server(sql), os.path.join(here, "codes.json"), version=version)
                            waker = waker_factory(["g/%s/sig" % conf["key"]])
                            log.info("telefonlar ko'prigi ishga tushdi (SQL: %s / %s)", conf.get("host"), conf.get("database"))
                if relay is None:
                    time.sleep(60)
                    continue
                n = relay.pump()
                if n:
                    log.info("ko'prik: %d ta ish bajarildi%s", n, (" (xato: %s)" % relay.error) if relay.error else "")
                elif relay.error:
                    log.warning("ko'prik: %s", relay.error)
                waker.wait(IDLE_SEC)
            except Exception:  # noqa: BLE001
                log.exception("ko'prik xatosi")
                time.sleep(30)
    t = threading.Thread(target=run, daemon=True, name="relay")
    t.start()
    return t
