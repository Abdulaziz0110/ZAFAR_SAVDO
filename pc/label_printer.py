#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
IMSOFT narx yorlig'i printeri — kompyuterga USB bilan ulangan Xprinter (yorliq printeri) uchun.

1-ilova (Savdo Agent) ▸ Narx yorliqlari: yorliqlar telefonda rasm qilib chiziladi (1C "PLU Narx" va "Shtrix chek"
shakllari kabi) va shu dasturga yuboriladi:
  1) Wi-Fi/LAN: telefon -> kompyuter IP:9110 (TCP, tez, internetsiz);
  2) internet orqali: telefon -> Firebase L/{key}/{id} -> shu dastur har 3 soniyada oladi.
Bitta ish ikkala yo'ldan kelsa ham ish raqami bo'yicha bir marta chop etiladi.

Chop etish usullari ([labels] mode):
  gdi  — Windows drayveri orqali (1C kabi). Yorliq o'lchami drayver sozlamasidan olinadi (telefonga aytiladi).
         Kerak: pip install pillow pywin32
  tspl — printerga to'g'ridan-to'g'ri TSPL buyruqlari (RAW). Drayver o'lchamiga bog'liq emas. Kerak: pillow pywin32
  file — chop etmaydi, labels_out/ papkasiga PNG saqlaydi (sinov uchun).

Buyruqlar:
  python label_printer.py           — ishga tushirish (odatda label_printer.vbs orqali oynasiz)
  python label_printer.py --list    — Windows'dagi printerlar va yorliq o'lchami
  python label_printer.py --test    — sinov yorlig'ini chop etish
  python label_printer.py -v        — oynada jurnal bilan

Xavfsizlik: har so'rovda forwarder.ini dagi [labels] key tekshiriladi. 1C ga hech narsa yozilmaydi.
"""
import argparse
import base64
import configparser
import hmac
import io
import json
import logging
import logging.handlers
import os
import re
import socket
import struct
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request

HERE = os.path.dirname(os.path.abspath(sys.argv[0] if getattr(sys, "frozen", False) else __file__))
CONFIG = os.path.join(HERE, "forwarder.ini")
LOG = os.path.join(HERE, "label_printer.log")
DONE = os.path.join(HERE, "labels_done.json")

MAGIC = b"SLBL"
MAX_FRAME = 64 * 1024 * 1024
MAX_LABELS = 500
MAX_COPIES = 999
ID_RE = re.compile(r"^[A-Za-z0-9_-]{1,64}$")
POLL_SEC = 3
STATUS_SEC = 60
CLOUD_MAX_AGE_MS = 24 * 3600 * 1000  # bir kundan eski bulut ishlari chop etilmaydi (o'chiriladi)

log = logging.getLogger("label_printer")


def setup_logging(verbose=False, path=LOG):
    log.setLevel(logging.DEBUG if verbose else logging.INFO)
    fmt = logging.Formatter("%(asctime)s %(levelname)s %(message)s")
    if path:
        fh = logging.handlers.RotatingFileHandler(path, maxBytes=1_000_000, backupCount=3, encoding="utf-8")
        fh.setFormatter(fmt)
        log.addHandler(fh)
    if verbose or (sys.stderr is not None and sys.stderr.isatty()):
        sh = logging.StreamHandler()
        sh.setFormatter(fmt)
        log.addHandler(sh)


# ---------------------------------------------------------------- sozlama

def normalize_url(raw):
    u = (raw or "").strip().rstrip("/")
    if not u:
        return ""
    if not u.startswith("http"):
        u = "https://" + u
    u = u.replace("http://", "https://", 1)
    return "https://" + u[len("https://"):].split("/")[0]


class Config:
    def __init__(self, path=CONFIG):
        cp = configparser.ConfigParser()
        if not os.path.exists(path):
            raise SystemExit("forwarder.ini topilmadi: %s\n1-ilova > Narx yorliqlari > Yorliq printeri > 'Kompyuter sozlamasi'." % path)
        cp.read(path, encoding="utf-8-sig")
        lb = cp["labels"] if cp.has_section("labels") else {}
        self.key = lb.get("key", "").strip()
        self.printer = lb.get("printer", "").strip()
        self.mode = (lb.get("mode", "gdi").strip() or "gdi").lower()
        self.port = int(lb.get("port", "9110") or 9110)
        self.bind = lb.get("bind", "0.0.0.0").strip() or "0.0.0.0"
        self.dpi = int(lb.get("dpi", "203") or 203)
        self.width_mm = float(lb.get("width_mm", "0") or 0)
        self.height_mm = float(lb.get("height_mm", "0") or 0)
        self.gap_mm = float(lb.get("gap_mm", "2") or 2)
        self.density = int(lb.get("density", "8") or 8)
        self.speed = int(lb.get("speed", "4") or 4)
        self.invert = (lb.get("invert", "0").strip() or "0") in ("1", "yes", "true", "ha")
        self.out_dir = lb.get("out_dir", "").strip() or os.path.join(HERE, "labels_out")
        self.cloud = (lb.get("cloud", "1").strip() or "1") not in ("0", "no", "false", "yoq")
        fb = cp["firebase"] if cp.has_section("firebase") else {}
        self.url = normalize_url(fb.get("url", ""))
        self.secret = fb.get("secret", "").strip()
        if not (24 <= len(self.key) <= 64 and self.key.isalnum()):
            raise SystemExit("forwarder.ini: [labels] key noto'g'ri yoki yo'q")
        if self.mode not in ("gdi", "tspl", "file"):
            raise SystemExit("forwarder.ini: [labels] mode = gdi | tspl | file")

    @property
    def has_cloud(self):
        return self.cloud and bool(self.url and self.secret)


# ---------------------------------------------------------------- printerlar (Windows)

PREFERRED = ("xprinter", "xp-", "label", "tsc", "tspl", "barcode", "этикет")


def list_printers():
    try:
        import win32print  # noqa
    except ImportError:
        return []
    flags = win32print.PRINTER_ENUM_LOCAL | win32print.PRINTER_ENUM_CONNECTIONS
    return [p[2] for p in win32print.EnumPrinters(flags)]


def find_printer(name, names=None):
    """forwarder.ini dagi nom (qismi ham bo'ladi), bo'sh bo'lsa — Xprinter/label nomli birinchi printer."""
    names = list_printers() if names is None else names
    if name:
        for n in names:
            if n.lower() == name.lower():
                return n
        for n in names:
            if name.lower() in n.lower():
                return n
        return None
    for want in PREFERRED:
        for n in names:
            if want in n.lower():
                return n
    return None


def gdi_page(printer):
    """Drayverdagi qog'oz: (wpx, hpx, dpi). 1C ham shu o'lchamda chiqaradi."""
    import win32ui  # noqa
    dc = win32ui.CreateDC()
    dc.CreatePrinterDC(printer)
    try:
        return dc.GetDeviceCaps(8), dc.GetDeviceCaps(10), dc.GetDeviceCaps(88)  # HORZRES, VERTRES, LOGPIXELSX
    finally:
        dc.DeleteDC()


# ---------------------------------------------------------------- TSPL

def tspl_bytes(png, copies, dpi=203, width_mm=0, height_mm=0, gap_mm=2, density=8, speed=4, invert=False):
    """PNG -> TSPL: SIZE/GAP/CLS/BITMAP/PRINT. TSPL'da 0 = qora nuqta (PIL '1' rejimidagi bilan bir xil)."""
    from PIL import Image
    img = Image.open(io.BytesIO(png)).convert("L").point(lambda v: 255 if v >= 128 else 0).convert("1")
    w, h = img.size
    wb = (w + 7) // 8
    if w % 8:
        pad = Image.new("1", (wb * 8, h), 1)
        pad.paste(img, (0, 0))
        img = pad
    data = img.tobytes()
    if invert:
        data = bytes(b ^ 0xFF for b in data)
    wmm = width_mm or round(w * 25.4 / dpi, 1)
    hmm = height_mm or round(h * 25.4 / dpi, 1)
    head = ("SIZE %s mm,%s mm\r\nGAP %s mm,0 mm\r\nDIRECTION 1\r\nREFERENCE 0,0\r\nDENSITY %d\r\nSPEED %d\r\nCLS\r\n"
            % (_n(wmm), _n(hmm), _n(gap_mm), density, speed)).encode("ascii")
    bmp = ("BITMAP 0,0,%d,%d,0," % (wb, h)).encode("ascii") + data + b"\r\n"
    tail = ("PRINT 1,%d\r\n" % copies).encode("ascii")
    return head + bmp + tail


def _n(v):
    return ("%.1f" % v).rstrip("0").rstrip(".")


def raw_print(printer, data, title="IMSOFT yorliq"):
    import win32print  # noqa
    h = win32print.OpenPrinter(printer)
    try:
        win32print.StartDocPrinter(h, 1, (title, None, "RAW"))
        try:
            win32print.StartPagePrinter(h)
            win32print.WritePrinter(h, data)
            win32print.EndPagePrinter(h)
        finally:
            win32print.EndDocPrinter(h)
    finally:
        win32print.ClosePrinter(h)


def gdi_print(printer, labels, title="IMSOFT yorliq"):
    """Har nusxa — alohida sahifa (yorliq). Rasm sahifaga mos kattalashtiriladi, nisbat saqlanadi."""
    import win32ui  # noqa
    from PIL import Image, ImageWin
    dc = win32ui.CreateDC()
    dc.CreatePrinterDC(printer)
    try:
        pw, ph = dc.GetDeviceCaps(8), dc.GetDeviceCaps(10)
        dc.StartDoc(title)
        try:
            for png, copies in labels:
                img = Image.open(io.BytesIO(png)).convert("RGB")
                k = min(pw / img.width, ph / img.height)
                w, h = int(img.width * k), int(img.height * k)
                x, y = (pw - w) // 2, (ph - h) // 2
                dib = ImageWin.Dib(img)
                for _ in range(copies):
                    dc.StartPage()
                    dib.draw(dc.GetHandleOutput(), (x, y, x + w, y + h))
                    dc.EndPage()
        finally:
            dc.EndDoc()
    finally:
        dc.DeleteDC()


# ---------------------------------------------------------------- chop etuvchi

class PrintFail(Exception):
    pass


class Printer:
    """Bitta printer: ishlar ketma-ket (qulf), ish raqamlari eslab qolinadi (takror chop etilmaydi)."""

    def __init__(self, cfg, done_path=DONE, backend=None):
        self.cfg = cfg
        self.lock = threading.Lock()
        self.done_path = done_path
        self.done = self._load_done()
        self.backend = backend  # testlar uchun: fn(printer_name, labels) — labels [(png, copies)]
        self.printed_total = 0

    def _load_done(self):
        try:
            with open(self.done_path, "r", encoding="utf-8") as f:
                d = json.load(f)
            return {str(k): float(v) for k, v in d.items()} if isinstance(d, dict) else {}
        except (OSError, ValueError):
            return {}

    def _save_done(self):
        items = sorted(self.done.items(), key=lambda kv: kv[1])[-1000:]
        self.done = dict(items)
        tmp = self.done_path + ".tmp"
        try:
            with open(tmp, "w", encoding="utf-8") as f:
                json.dump(self.done, f)
            os.replace(tmp, self.done_path)
        except OSError as e:
            log.warning("labels_done.json yozilmadi: %s", e)

    def seen(self, job_id):
        base = job_id.split("_")[0]
        return job_id in self.done or base in self.done

    def printer_name(self):
        if self.cfg.mode == "file":
            return self.cfg.printer or "fayl"
        n = find_printer(self.cfg.printer)
        if not n:
            raise PrintFail("printer: topilmadi (%s)" % (self.cfg.printer or "Xprinter"))
        return n

    def info(self):
        cfg = self.cfg
        out = {"ok": True, "host": socket.gethostname(), "mode": cfg.mode, "printer": "", "wpx": 0, "hpx": 0, "dpi": cfg.dpi,
               "wmm": cfg.width_mm, "hmm": cfg.height_mm, "text": ""}
        try:
            out["printer"] = self.printer_name()
        except PrintFail as e:
            out["ok_printer"] = False
            out["text"] = str(e)
            return out
        if cfg.mode == "gdi":
            try:
                w, h, dpi = gdi_page(out["printer"])
                out.update(wpx=w, hpx=h, dpi=dpi, wmm=round(w * 25.4 / dpi, 1), hmm=round(h * 25.4 / dpi, 1))
            except Exception as e:  # drayver javob bermasa ham server ishlayversin
                out["text"] = "o'lcham olinmadi: %s" % e
        elif cfg.width_mm and cfg.height_mm:
            out.update(wpx=int(cfg.width_mm * cfg.dpi / 25.4), hpx=int(cfg.height_mm * cfg.dpi / 25.4))
        return out

    def print_job(self, job_id, labels):
        """labels: [(png bytes, copies)]. @return (chop etilgan yorliqlar soni, printer nomi, takrormi)."""
        with self.lock:
            if self.seen(job_id):
                log.info("Ish %s avval chop etilgan — takrorlanmaydi", job_id)
                return sum(c for _, c in labels), self.cfg.printer or "", True
            name = self.printer_name()
            try:
                if self.backend is not None:
                    self.backend(name, labels)
                elif self.cfg.mode == "file":
                    os.makedirs(self.cfg.out_dir, exist_ok=True)
                    for i, (png, copies) in enumerate(labels):
                        with open(os.path.join(self.cfg.out_dir, "%s_%03d_x%d.png" % (job_id, i + 1, copies)), "wb") as f:
                            f.write(png)
                elif self.cfg.mode == "tspl":
                    c = self.cfg
                    data = b"".join(tspl_bytes(p, n, c.dpi, c.width_mm, c.height_mm, c.gap_mm, c.density, c.speed, c.invert) for p, n in labels)
                    raw_print(name, data)
                else:
                    gdi_print(name, labels)
            except PrintFail:
                raise
            except Exception as e:
                raise PrintFail("printer: %s" % e)
            n = sum(c for _, c in labels)
            self.done[job_id] = time.time()
            self._save_done()
            self.printed_total += n
            log.info("Chop etildi: %s — %d ta yorliq (%s)", job_id, n, name)
            return n, name, False


def parse_labels(body):
    """So'rovdagi yorliqlar -> [(png, copies)]. Noto'g'ri bo'lsa ValueError."""
    items = body.get("labels")
    if not isinstance(items, list) or not items:
        raise ValueError("labels")
    if len(items) > MAX_LABELS:
        raise ValueError("too_many")
    out = []
    for it in items:
        if not isinstance(it, dict):
            raise ValueError("labels")
        png = base64.b64decode(str(it.get("png", "")), validate=True)
        if not png.startswith(b"\x89PNG"):
            raise ValueError("png")
        n = int(it.get("copies", 1))
        out.append((png, max(1, min(MAX_COPIES, n))))
    return out


def key_ok(cfg, body):
    return hmac.compare_digest(str(body.get("key", "")).encode("utf-8"), cfg.key.encode("utf-8"))


def handle_request(cfg, printer, body):
    """Bitta so'rov (LAN yoki bulut) -> javob."""
    if not isinstance(body, dict) or not key_ok(cfg, body):
        return {"ok": False, "error": "bad_key"}
    op = body.get("op")
    if op == "info":
        return printer.info()
    if op == "print":
        job_id = str(body.get("id", ""))
        if not ID_RE.match(job_id):
            return {"ok": False, "error": "bad_id"}
        try:
            labels = parse_labels(body)
        except (ValueError, TypeError) as e:
            return {"ok": False, "error": "bad_request:%s" % e}
        try:
            n, name, dup = printer.print_job(job_id, labels)
        except PrintFail as e:
            log.error("Ish %s chop etilmadi: %s", job_id, e)
            return {"ok": False, "error": str(e)}
        return {"ok": True, "printed": n, "printer": name, "duplicate": dup}
    return {"ok": False, "error": "bad_op"}


# ---------------------------------------------------------------- LAN (TCP + UDP qidiruv)

def read_exact(conn, n):
    buf = bytearray()
    while len(buf) < n:
        b = conn.recv(min(65536, n - len(buf)))
        if not b:
            raise ConnectionError("eof")
        buf += b
    return bytes(buf)


def send_frame(conn, obj):
    data = json.dumps(obj, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    conn.sendall(MAGIC + struct.pack(">I", len(data)) + data)


def handle_conn(cfg, printer, conn, addr):
    conn.settimeout(30)
    try:
        if read_exact(conn, 4) != MAGIC:
            return
        n = struct.unpack(">I", read_exact(conn, 4))[0]
        if n > MAX_FRAME:
            send_frame(conn, {"ok": False, "error": "too_big"})
            return
        try:
            body = json.loads(read_exact(conn, n).decode("utf-8"))
        except ValueError:
            send_frame(conn, {"ok": False, "error": "bad_json"})
            return
        conn.settimeout(300)  # chop etish uzoq bo'lishi mumkin
        resp = handle_request(cfg, printer, body)
        if body.get("op") == "print" and resp.get("ok"):
            log.info("LAN %s: %d ta yorliq", addr[0], resp.get("printed", 0))
        send_frame(conn, resp)
    except (OSError, ConnectionError, struct.error) as e:
        log.debug("Ulanish %s: %s", addr, e)
    finally:
        try:
            conn.close()
        except OSError:
            pass


def make_server(cfg):
    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    if os.name != "nt":
        srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind((cfg.bind, cfg.port))
    srv.listen(8)
    return srv


def serve(cfg, printer, srv, stop=None):
    log.info("Tinglanmoqda: %s:%d (Wi-Fi/LAN)", cfg.bind, srv.getsockname()[1])
    srv.settimeout(1.0)
    while stop is None or not stop.is_set():
        try:
            conn, addr = srv.accept()
        except socket.timeout:
            continue
        except OSError:
            break
        threading.Thread(target=handle_conn, args=(cfg, printer, conn, addr), daemon=True).start()


def discovery(cfg, printer, port, stop=None):
    """UDP "SLBL?" -> {"slbl":1, port, host, printer}. Telefon "Kompyuterni topish" da shu bilan topadi."""
    u = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    if os.name != "nt":
        u.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    try:
        u.bind((cfg.bind, port))
    except OSError as e:
        log.warning("Qidiruv porti %d band: %s (IP ni qo'lda yozish mumkin)", port, e)
        return
    u.settimeout(1.0)
    name = ""
    while stop is None or not stop.is_set():
        try:
            data, addr = u.recvfrom(512)
        except socket.timeout:
            continue
        except OSError:
            break
        if not data.startswith(b"SLBL?"):
            continue
        if not name:
            try:
                name = printer.printer_name()
            except PrintFail:
                name = ""
        resp = json.dumps({"slbl": 1, "port": cfg.port, "host": socket.gethostname(), "printer": name}).encode("utf-8")
        try:
            u.sendto(resp, addr)
        except OSError:
            pass


# ---------------------------------------------------------------- bulut (Firebase)

class HttpError(Exception):
    def __init__(self, code, body):
        super().__init__("HTTP %s: %s" % (code, body[:200]))
        self.code = code


class Cloud:
    def __init__(self, url, secret):
        self.url = url
        self.secret = secret

    def call(self, method, path, body=None, timeout=60, **q):
        q["auth"] = self.secret
        u = "%s/%s.json?%s" % (self.url, path.strip("/"), urllib.parse.urlencode(q))
        data = None if body is None else json.dumps(body, separators=(",", ":")).encode("utf-8")
        req = urllib.request.Request(u, data=data, method=method)
        req.add_header("Content-Type", "application/json; charset=utf-8")
        try:
            with urllib.request.urlopen(req, timeout=timeout) as r:
                return json.loads(r.read().decode("utf-8") or "null")
        except urllib.error.HTTPError as e:
            raise HttpError(e.code, e.read().decode("utf-8", "replace"))


def poll_cloud_once(cfg, printer, cloud, now_ms=None):
    """Bulut navbatidagi ishlarni chop etadi. @return chop etilgan ishlar soni."""
    ids = cloud.call("GET", "L/%s" % cfg.key, timeout=30, shallow="true") or {}
    if not isinstance(ids, dict):
        return 0
    done = 0
    for job_id in sorted(ids.keys()):
        if not ID_RE.match(job_id):
            cloud.call("DELETE", "L/%s/%s" % (cfg.key, job_id), timeout=30)
            continue
        body = cloud.call("GET", "L/%s/%s" % (cfg.key, job_id), timeout=60)
        if not isinstance(body, dict):
            continue
        at = body.get("at")
        now = now_ms if now_ms is not None else time.time() * 1000
        if isinstance(at, (int, float)) and now - at > CLOUD_MAX_AGE_MS:
            log.warning("Bulut ishi %s eskirgan — chop etilmadi", job_id)
            cloud.call("DELETE", "L/%s/%s" % (cfg.key, job_id), timeout=30)
            continue
        body["id"] = job_id
        resp = handle_request(cfg, printer, body)
        if resp.get("ok") or not str(resp.get("error", "")).startswith("printer"):
            # chop etildi yoki ish noto'g'ri — navbatdan olinadi; printer xatosi bo'lsa keyin qayta urinamiz
            cloud.call("DELETE", "L/%s/%s" % (cfg.key, job_id), timeout=30)
            if resp.get("ok"):
                done += 1
                log.info("Internet orqali: %s — %d ta yorliq", job_id, resp.get("printed", 0))
            else:
                log.error("Bulut ishi %s rad etildi: %s", job_id, resp.get("error"))
        else:
            raise PrintFail(resp.get("error"))
    return done


def push_status(cfg, printer, cloud):
    st = dict(printer.info())
    st["at"] = {".sv": "timestamp"}
    st.pop("ok", None)
    cloud.call("PUT", "P/%s" % cfg.key, st, timeout=30, print="silent")


def cloud_loop(cfg, printer, stop=None):
    cloud = Cloud(cfg.url, cfg.secret)
    last_status = 0.0
    wait = POLL_SEC
    while stop is None or not stop.is_set():
        try:
            if time.time() - last_status > STATUS_SEC:
                push_status(cfg, printer, cloud)
                last_status = time.time()
            poll_cloud_once(cfg, printer, cloud)
            wait = POLL_SEC
        except PrintFail as e:
            log.error("Printer xatosi, 30 soniyadan keyin qayta: %s", e)
            wait = 30
        except HttpError as e:
            wait = 60 if e.code in (401, 403, 404) else 15
            log.warning("Firebase: %s", e)
        except (urllib.error.URLError, OSError, ValueError) as e:
            log.debug("Internet yo'q? %s", e)
            wait = 15
        if stop is not None:
            stop.wait(wait)
        else:
            time.sleep(wait)


# ---------------------------------------------------------------- sinov yorlig'i

def test_png(w=464, h=320):
    from PIL import Image, ImageDraw
    img = Image.new("L", (w, h), 255)
    d = ImageDraw.Draw(img)
    d.rectangle((2, 2, w - 3, h - 3), outline=0, width=3)
    d.text((14, 14), "IMSOFT - sinov yorlig'i", fill=0)
    d.text((14, 40), time.strftime("%d.%m.%Y %H:%M:%S"), fill=0)
    d.text((14, 66), socket.gethostname(), fill=0)
    for i in range(0, w - 28, 8):
        d.rectangle((14 + i, h - 70, 14 + i + 3, h - 20), fill=0)
    b = io.BytesIO()
    img.convert("1").save(b, "PNG")
    return b.getvalue()


def local_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("10.255.255.255", 1))
        return s.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        s.close()


def main():
    ap = argparse.ArgumentParser(description="IMSOFT narx yorlig'i printeri")
    ap.add_argument("--list", action="store_true", help="printerlar ro'yxati va yorliq o'lchami")
    ap.add_argument("--test", action="store_true", help="sinov yorlig'ini chop etish")
    ap.add_argument("--config", default=CONFIG)
    ap.add_argument("-v", action="store_true")
    a = ap.parse_args()
    setup_logging(a.v)
    if a.list:
        names = list_printers()
        if not names:
            print("Printer topilmadi (pywin32 o'rnatilganmi? pip install pillow pywin32)")
        for n in names:
            try:
                w, h, dpi = gdi_page(n)
                print("%-40s %dx%d nuqta, %d dpi (%.0fx%.0f mm)" % (n, w, h, dpi, w * 25.4 / dpi, h * 25.4 / dpi))
            except Exception as e:
                print("%-40s (o'lcham olinmadi: %s)" % (n, e))
        print("Tanlanadi:", find_printer("", names) or "— (forwarder.ini [labels] printer = ... yozing)")
        return
    cfg = Config(a.config)
    printer = Printer(cfg)
    if a.test:
        info = printer.info()
        w, h = (info["wpx"], info["hpx"]) if info.get("wpx", 0) > 80 and info.get("hpx", 0) > 60 else (464, 320)
        n, name, _ = printer.print_job("test" + str(int(time.time())), [(test_png(w, h), 1)])
        print("Chop etildi: %d ta (%s)" % (n, name))
        return
    try:
        srv = make_server(cfg)
    except OSError as e:
        log.error("Port %d band (boshqa nusxa ishlayaptimi?): %s", cfg.port, e)
        raise SystemExit(2)
    info = printer.info()
    log.info("Ishga tushdi. Printer: %s (%s), kompyuter IP: %s, port %d", info.get("printer") or "?", cfg.mode, local_ip(), cfg.port)
    threading.Thread(target=discovery, args=(cfg, printer, cfg.port + 1), daemon=True).start()
    if cfg.has_cloud:
        threading.Thread(target=cloud_loop, args=(cfg, printer), daemon=True).start()
    else:
        log.info("Firebase sozlanmagan — faqat Wi-Fi/LAN orqali")
    serve(cfg, printer, srv)


if __name__ == "__main__":
    main()
