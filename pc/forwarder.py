#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
IMSOFT chek uzatgichi (forwarder) — 1-filial kompyuterida fonda ishlaydi.

1C -> Windows printer "filial2" (Standard TCP/IP port 127.0.0.1:9100, Raw)
   -> shu dastur (127.0.0.1:9100) -> outbox/*.bin (diskdagi navbat)
   -> Google Firebase (bepul) -> 2-filial telefonidagi "IMSOFT Mijoz" ilovasi -> USB chek printeri

Faqat Python standart kutubxonasi (pip shart emas). Sozlama: forwarder.ini (1-ilova ▸ Mijoz ilovasi ▸
"Kompyuter uchun chek sozlamasi" tugmasi beradi).

Buyruqlar:
  python forwarder.py            — ishga tushirish (odatda forwarder.vbs orqali oynasiz)
  python forwarder.py --test     — sinov chekini navbatga qo'yish
  python forwarder.py --status   — bulutdagi navbat va 2-filial printer holati
  python forwarder.py --once     — diskdagi navbatni bir marta yuborib chiqish

Ishonchlilik:
  * Chek avval diskka yoziladi (.tmp -> .bin), keyin yuboriladi; internet yo'q bo'lsa diskda kutadi.
  * Bulutga PUT bilan, fayl nomidan olingan O'ZGARMAS kalit bilan yoziladi — javob yo'qolib qayta yuborilsa ham
    navbatda bitta nusxa bo'ladi (dublikat chek chiqmaydi).
  * 1C/drayver yuborgan baytlar O'ZGARTIRILMAYDI.
"""
import argparse
import base64
import configparser
import json
import logging
import logging.handlers
import os
import socket
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request

HERE = os.path.dirname(os.path.abspath(sys.argv[0] if getattr(sys, "frozen", False) else __file__))
OUTBOX = os.path.join(HERE, "outbox")
FAILED = os.path.join(HERE, "failed")
CONFIG = os.path.join(HERE, "forwarder.ini")
LOG = os.path.join(HERE, "forwarder.log")

IDLE_CLOSE_SEC = 10          # spooler ulanishni yopmasa — shuncha jimlikdan keyin chek tugagan hisoblanadi
MAX_JOB = 20 * 1024 * 1024   # 20 MB dan katta chek — xato (navbatni to'smasin)

log = logging.getLogger("forwarder")


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


class Config:
    def __init__(self, path=CONFIG):
        cp = configparser.ConfigParser()
        if not os.path.exists(path):
            raise SystemExit("forwarder.ini topilmadi: %s\n1-ilova ▸ Sozlamalar ▸ Mijoz ilovasi ▸ 'Kompyuter uchun chek sozlamasi'." % path)
        cp.read(path, encoding="utf-8")
        fb = cp["firebase"] if cp.has_section("firebase") else {}
        self.url = normalize_url(fb.get("url", ""))
        self.secret = fb.get("secret", "").strip()
        self.queue = fb.get("queue", "").strip()
        loc = cp["local"] if cp.has_section("local") else {}
        self.host = loc.get("host", "127.0.0.1").strip() or "127.0.0.1"
        self.port = int(loc.get("port", "9100") or 9100)
        if not self.url or not self.secret or not self.queue:
            raise SystemExit("forwarder.ini to'liq emas (url, secret, queue kerak)")
        if not (24 <= len(self.queue) <= 64 and self.queue.isalnum()):
            raise SystemExit("forwarder.ini: queue kaliti noto'g'ri")


def normalize_url(raw):
    u = raw.strip().rstrip("/")
    if not u:
        return ""
    if not u.startswith("http"):
        u = "https://" + u
    u = u.replace("http://", "https://", 1)
    host = u[len("https://"):].split("/")[0]
    return "https://" + host


class HttpError(Exception):
    def __init__(self, code, body):
        super().__init__("HTTP %s: %s" % (code, body[:200]))
        self.code = code


class Cloud:
    """Firebase Realtime Database REST (faqat urllib)."""

    def __init__(self, cfg):
        self.cfg = cfg

    def _url(self, path, **q):
        q["auth"] = self.cfg.secret
        return "%s/%s.json?%s" % (self.cfg.url, path.strip("/"), urllib.parse.urlencode(q))

    def call(self, method, path, body=None, timeout=60, **q):
        data = None if body is None else json.dumps(body, separators=(",", ":")).encode("utf-8")
        req = urllib.request.Request(self._url(path, **q), data=data, method=method)
        req.add_header("Content-Type", "application/json; charset=utf-8")
        try:
            with urllib.request.urlopen(req, timeout=timeout) as r:
                txt = r.read().decode("utf-8") or "null"
                return json.loads(txt)
        except urllib.error.HTTPError as e:
            raise HttpError(e.code, e.read().decode("utf-8", "replace"))

    def put_job(self, key, raw, name):
        body = {"t": {".sv": "timestamp"}, "b": base64.b64encode(raw).decode("ascii"), "n": len(raw), "src": name}
        self.call("PUT", "q/%s/%s" % (self.cfg.queue, key), body, print="silent")
        # "Yangi chek bor" signali — telefon darhol uyg'onadi
        try:
            self.call("PUT", "p/%s/sig" % self.cfg.queue, {".sv": "timestamp"}, timeout=20, print="silent")
        except Exception as e:  # signal muhim emas: telefon baribir har daqiqada tekshiradi
            log.debug("signal yozilmadi: %s", e)


# ---------------------------------------------------------------- disk navbati

def ensure_dirs():
    os.makedirs(OUTBOX, exist_ok=True)
    os.makedirs(FAILED, exist_ok=True)


_seq_lock = threading.Lock()
_last_ns = [0]


def save_job(raw):
    """Chekni diskka (avval .tmp, keyin atomik nom). Nom — vaqt bo'yicha tartiblanadi va bulut kaliti ham shu."""
    with _seq_lock:
        ns = time.time_ns()
        if ns <= _last_ns[0]:
            ns = _last_ns[0] + 1
        _last_ns[0] = ns
    name = "%020d" % ns
    tmp = os.path.join(OUTBOX, name + ".tmp")
    with open(tmp, "wb") as f:
        f.write(raw)
        f.flush()
        os.fsync(f.fileno())
    os.replace(tmp, os.path.join(OUTBOX, name + ".bin"))
    return name


def pending():
    try:
        return sorted(f for f in os.listdir(OUTBOX) if f.endswith(".bin"))
    except FileNotFoundError:
        return []


# ---------------------------------------------------------------- TCP qabul qiluvchi (Windows spooler)

def handle_client(conn, addr):
    conn.settimeout(IDLE_CLOSE_SEC)
    chunks = []
    total = 0
    try:
        while True:
            try:
                b = conn.recv(65536)
            except socket.timeout:
                break
            if not b:
                break
            chunks.append(b)
            total += len(b)
            if total > MAX_JOB:
                log.error("Chek juda katta (%d bayt) — tashlandi", total)
                return
    finally:
        try:
            conn.close()
        except OSError:
            pass
    if total == 0:
        return  # SNMP/holat tekshiruvi kabi bo'sh ulanishlar
    raw = b"".join(chunks)
    name = save_job(raw)
    log.info("Chek qabul qilindi: %s (%d bayt)", name, total)
    WAKE.set()


def serve(cfg):
    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1) if os.name != "nt" else None
    try:
        srv.bind((cfg.host, cfg.port))
    except OSError as e:
        log.error("%s:%d band (boshqa nusxa ishlayaptimi?): %s", cfg.host, cfg.port, e)
        raise SystemExit(2)
    srv.listen(8)
    log.info("Tinglanmoqda: %s:%d", cfg.host, cfg.port)
    while True:
        conn, addr = srv.accept()
        threading.Thread(target=handle_client, args=(conn, addr), daemon=True).start()


# ---------------------------------------------------------------- yuboruvchi

WAKE = threading.Event()


def send_pending(cloud, once=False):
    """Diskdagi cheklarni eskisidan boshlab yuboradi. @return yuborilganlar soni."""
    sent = 0
    host = socket.gethostname()
    for fn in pending():
        path = os.path.join(OUTBOX, fn)
        try:
            with open(path, "rb") as f:
                raw = f.read()
        except OSError:
            continue
        key = "c" + fn[:-4]
        try:
            cloud.put_job(key, raw, host)
        except HttpError as e:
            if e.code == 413:
                os.replace(path, os.path.join(FAILED, fn))
                log.error("%s juda katta (413) — failed/ papkaga o'tkazildi", fn)
                continue
            if e.code in (401, 403):
                log.error("Ruxsat yo'q (%s): secret yoki qoidalar noto'g'ri. forwarder.ini ni yangilang.", e.code)
                return sent if once else -30
            if e.code == 404:
                log.error("Baza topilmadi (404): url noto'g'ri")
                return sent if once else -30
            if e.code == 429:
                return sent if once else -5
            log.warning("%s yuborilmadi: %s", fn, e)
            return sent if once else -10
        except (urllib.error.URLError, OSError, ValueError) as e:
            log.warning("Internet yo'q? %s — keyinroq (%s)", fn, e)
            return sent if once else -10
        try:
            os.remove(path)
        except OSError:
            pass
        sent += 1
        log.info("Yuborildi: %s (%d bayt)", key, len(raw))
    return sent


def sender_loop(cloud):
    while True:
        r = send_pending(cloud)
        if r < 0:
            WAKE.wait(-r)
        else:
            WAKE.wait(30)
        WAKE.clear()


# ---------------------------------------------------------------- yordamchi buyruqlar

def test_job():
    t = time.strftime("%d.%m.%Y %H:%M:%S")
    lines = ["IMSOFT forwarder", "Sinov cheki / Test", t, socket.gethostname(), "OK"]
    raw = b"\x1b@" + b"\x1ba\x01" + b"".join(l.encode("ascii", "replace") + b"\n" for l in lines) + b"\x1ba\x00\n\n\n\n" + b"\x1dVB\x00"
    return raw


def status(cfg):
    c = Cloud(cfg)
    q = c.call("GET", "q/%s" % cfg.queue, timeout=30, shallow="true") or {}
    st = c.call("GET", "p/%s/status" % cfg.queue, timeout=30) or {}
    print("Diskdagi navbat: %d ta" % len(pending()))
    print("Bulutdagi navbat: %d ta" % len(q))
    if st:
        at = st.get("at")
        when = time.strftime("%d.%m.%Y %H:%M", time.localtime(at / 1000)) if isinstance(at, (int, float)) else "?"
        print("2-filial printer: %s (%s)" % (st.get("text", "?"), when))
    else:
        print("2-filial printer: hali holat yubormagan (ilovada servis yoqilmagan)")


def main():
    ap = argparse.ArgumentParser(description="IMSOFT chek uzatgichi")
    ap.add_argument("--test", action="store_true", help="sinov chekini navbatga qo'yish")
    ap.add_argument("--status", action="store_true", help="navbat va printer holati")
    ap.add_argument("--once", action="store_true", help="diskdagi navbatni bir marta yuborish")
    ap.add_argument("--config", default=CONFIG)
    ap.add_argument("-v", action="store_true")
    a = ap.parse_args()
    setup_logging(a.v)
    ensure_dirs()
    cfg = Config(a.config)
    if a.status:
        status(cfg)
        return
    if a.test:
        name = save_job(test_job())
        print("Sinov cheki navbatga qo'yildi:", name)
        n = send_pending(Cloud(cfg), once=True)
        print("Yuborildi:", n)
        return
    if a.once:
        print("Yuborildi:", send_pending(Cloud(cfg), once=True))
        return
    log.info("Ishga tushdi. Navbat: %d ta", len(pending()))
    threading.Thread(target=sender_loop, args=(Cloud(cfg),), daemon=True).start()
    serve(cfg)


if __name__ == "__main__":
    main()
