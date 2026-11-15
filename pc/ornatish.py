#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
IMSOFT — kompyuterni BIR MARTA sozlash (ORNATISH.bat shu faylni administrator sifatida ishga tushiradi).

Nima qiladi (har safar qayta ishga tushirsa bo'ladi — bajarilganlarini o'tkazib yuboradi):
  1. Eski yorliq dasturini (label_printer.py) to'xtatadi va avtozapuskdan olib tashlaydi — endi kerak emas.
  2. Xprinter'ni telefon uchun ulashadi: Windows "LPD Print Service" + printer "xprinter" nomi bilan + 515-port.
     Telefondagi ilova printerga to'g'ridan-to'g'ri yuboradi (kompyuterda boshqa dastur ishlamaydi).
  3. forwarder.ini (telefondan olinadigan kalitlar) — Bloknot ochiladi, matnni joylab saqlaysiz.
  4. onec.ini (1C ga ulanish) — baza, foydalanuvchi va parolni shu oynada so'raydi.
  5. 1C'dan PLU va shtrix kodlarni olib ko'radi (onec_export.py --codes).
  6. onec_export.py ni avtozapuskka qo'yadi va hozir ishga tushiradi (fonda, oynasiz).
"""
import configparser
import ctypes
import getpass
import glob
import json
import os
import re
import socket
import subprocess
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
FWD = os.path.join(HERE, "forwarder.ini")
ONEC = os.path.join(HERE, "onec.ini")
EXPORT = os.path.join(HERE, "onec_export.py")
SHARE = "xprinter"
STARTUP_NAME = "IMSOFT_1C_kodlar.vbs"

# ------------------------------------------------------------------ yordamchilar


def line(ch="="):
    print(ch * 70)


def step(n, total, text):
    print()
    line()
    print(" [%d/%d] %s" % (n, total, text))
    line()


def ok(text):
    print("   OK   " + text)


def warn(text):
    print("   !!   " + text)


def ask(q, default=""):
    s = input("   ?  %s%s: " % (q, " [%s]" % default if default else "")).strip()
    return s or default


def yes(q, default=True):
    d = "H/y" if default else "h/Y"
    while True:
        s = input("   ?  %s (%s): " % (q, d)).strip().lower()
        if not s:
            return default
        if s in ("h", "ha", "yes", "д", "да"):
            return True
        if s in ("y", "yo'q", "yoq", "n", "no", "н", "нет"):
            return False


def ps(cmd, timeout=600):
    """PowerShell buyrug'i. @return (kod, matn)."""
    full = "[Console]::OutputEncoding=[Text.Encoding]::UTF8; $ErrorActionPreference='Stop'; " + cmd
    try:
        r = subprocess.run(["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", full],
                           capture_output=True, timeout=timeout)
    except Exception as e:  # noqa: BLE001
        return 1, str(e)
    out = (r.stdout or b"").decode("utf-8", "replace") + (r.stderr or b"").decode("utf-8", "replace")
    return r.returncode, out.strip()


def run(args, timeout=120):
    try:
        r = subprocess.run(args, capture_output=True, timeout=timeout)
        out = (r.stdout or b"") + (r.stderr or b"")
        for enc in ("utf-8", "cp866", "cp1251"):
            try:
                return r.returncode, out.decode(enc).strip()
            except UnicodeDecodeError:
                continue
        return r.returncode, out.decode("latin-1").strip()
    except Exception as e:  # noqa: BLE001
        return 1, str(e)


def is_admin():
    try:
        return bool(ctypes.windll.shell32.IsUserAnAdmin())
    except Exception:  # noqa: BLE001
        return False


def mask(s):
    s = s or ""
    return s[:3] + "*" * 6 + s[-2:] if len(s) > 8 else "*" * len(s)


def local_ips():
    ips = []
    try:
        for info in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET):
            ip = info[4][0]
            if not ip.startswith("127.") and not ip.startswith("169.254.") and ip not in ips:
                ips.append(ip)
    except OSError:
        pass
    return ips


def pythonw():
    w = os.path.join(os.path.dirname(sys.executable), "pythonw.exe")
    return w if os.path.exists(w) else sys.executable


# ------------------------------------------------------------------ sof mantiq (testlanadi)

def parse_ibases(text):
    """1C bazalar ro'yxati (ibases.v8i): [(nom, ulanish qatori)]."""
    out = []
    name = None
    for raw in text.splitlines():
        s = raw.strip().lstrip("\ufeff")
        m = re.match(r"^\[(.+)\]$", s)
        if m:
            name = m.group(1).strip()
            continue
        if name and s.lower().startswith("connect="):
            conn = s[len("connect="):].strip()
            if conn:
                out.append((name, conn))
            name = None
    return out


def conn_from_input(s):
    """Foydalanuvchi yozgan papka yoki tayyor qator -> 1C ulanish qatori."""
    s = s.strip()
    if re.search(r"(?i)\b(file|srvr)\s*=", s):
        return s if s.endswith(";") else s + ";"
    return 'File="%s";' % s.strip('"')


def onec_ini_text(conn, user, password, keep=None):
    """onec.ini matni. [keep] — avvalgi fayldagi boshqa bo'limlar (masalan [codes]) va [onec] dagi qo'shimcha qatorlar."""
    cp = configparser.ConfigParser(interpolation=None)
    cp.optionxform = str
    if keep:
        try:
            cp.read_string(keep)
        except configparser.Error:
            cp = configparser.ConfigParser(interpolation=None)
            cp.optionxform = str
    if not cp.has_section("onec"):
        cp.add_section("onec")
    cp.set("onec", "connection", conn)
    cp.set("onec", "user", user)
    cp.set("onec", "password", password)
    if not cp.has_section("codes"):
        cp.add_section("codes")
        cp.set("codes", "enabled", "1")
        cp.set("codes", "hours", "6")
    lines = ["; IMSOFT: 1C ga ulanish (ORNATISH.bat yozdi). Telefondan yangi forwarder.ini kelganda bu fayl o'zgarmaydi."]
    for sec in cp.sections():
        lines.append("")
        lines.append("[%s]" % sec)
        for k, v in cp.items(sec):
            lines.append("%s = %s" % (k, v))
    return "\n".join(lines) + "\n"


def firebase_ok(text):
    """forwarder.ini to'g'rimi: [firebase] url, secret, inbox. @return (ok, sabab)."""
    cp = configparser.ConfigParser(interpolation=None)
    try:
        cp.read_string(text)
    except configparser.Error as e:
        return False, "fayl buzilgan: %s" % str(e)[:80]
    if not cp.has_section("firebase"):
        return False, "[firebase] bo'limi yo'q"
    fb = cp["firebase"]
    if not fb.get("url", "").strip():
        return False, "url yo'q"
    if not fb.get("secret", "").strip():
        return False, "secret yo'q"
    inbox = fb.get("inbox", "").strip()
    if not (24 <= len(inbox) <= 64 and inbox.isalnum()):
        return False, "inbox kaliti yo'q yoki noto'g'ri"
    return True, ""


def read_text(path):
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


def startup_vbs(py_w, script, workdir):
    return ("' IMSOFT: 1C dan PLU va shtrix kodlar -> telefon (fonda, oynasiz). ORNATISH.bat yaratdi.\r\n"
            'Set sh = CreateObject("WScript.Shell")\r\n'
            'sh.CurrentDirectory = "%s"\r\n'
            'sh.Run """%s"" ""%s""", 0, False\r\n') % (workdir, py_w, script)


def lpd_test_tspl(w_mm=58, h_mm=40):
    """Kompyuterdan sinov yorlig'i (matn, TSPL) — printer ulashish to'g'ri ishlashini tekshirish uchun."""
    t = time.strftime("%d.%m.%Y %H:%M")
    return ("SIZE %d mm,%d mm\r\nGAP 2 mm,0 mm\r\nDIRECTION 1\r\nCLS\r\n"
            'TEXT 16,16,"3",0,1,1,"IMSOFT"\r\nTEXT 16,60,"2",0,1,1,"Printer ulandi"\r\nTEXT 16,100,"2",0,1,1,"%s"\r\n'
            "PRINT 1,1\r\n" % (w_mm, h_mm, t)).encode("ascii")


def lpr_send(host, queue, data, port=515, timeout=10):
    """RFC 1179 — telefondagi ilova bilan bir xil yo'l. 'l' — xom (o'zgartirilmaydi)."""
    s = socket.create_connection((host, port), timeout=timeout)
    try:
        def ack(step):
            b = s.recv(1)
            if b != b"\x00":
                raise RuntimeError("LPD rad etdi (%s)" % step)
        h = "pc"
        num = "%03d" % (int(time.time()) % 1000)
        s.sendall(b"\x02" + queue.encode("ascii") + b"\n")
        ack("navbat")
        ctrl = ("H%s\nPimsoft\nJtest\nldfA%s%s\nUdfA%s%s\n" % (h, num, h, num, h)).encode("ascii")
        s.sendall(b"\x02" + str(len(ctrl)).encode() + b" cfA" + num.encode() + h.encode() + b"\n")
        ack("nazorat")
        s.sendall(ctrl + b"\x00")
        ack("nazorat fayli")
        s.sendall(b"\x03" + str(len(data)).encode() + b" dfA" + num.encode() + h.encode() + b"\n")
        ack("ma'lumot")
        s.sendall(data + b"\x00")
        ack("ma'lumot oxiri")
    finally:
        s.close()


# ------------------------------------------------------------------ qadamlar

TOTAL = 6


def step_cleanup():
    step(1, TOTAL, "Eski yorliq dasturini olib tashlash (endi kerak emas)")
    code, out = ps("Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -match 'label_printer\\.py' } | "
                   "ForEach-Object { Stop-Process -Id $_.ProcessId -Force; $_.ProcessId }")
    ok("label_printer.py to'xtatildi" if out.strip() else "label_printer.py ishlamayapti")
    startup = os.path.join(os.environ.get("APPDATA", ""), r"Microsoft\Windows\Start Menu\Programs\Startup")
    removed = 0
    for f in glob.glob(os.path.join(startup, "*")):
        if re.search(r"(?i)label_printer|start_labels", os.path.basename(f)):
            try:
                os.remove(f)
                removed += 1
            except OSError:
                pass
    ok("avtozapuskdan olib tashlandi: %d ta" % removed)
    for r in ("IMSOFT yorliq TCP 9110", "IMSOFT yorliq UDP 9111"):
        run(["netsh", "advfirewall", "firewall", "delete", "rule", "name=%s" % r])
    ok("eski 9110/9111 portlar yopildi")


def step_printer():
    step(2, TOTAL, "Xprinter'ni telefon uchun ulashish (Windows LPD)")
    code, out = ps("Get-Printer | Select-Object Name,Shared,ShareName,PortName | ConvertTo-Json -Compress")
    try:
        data = json.loads(out) if out else []
        printers = data if isinstance(data, list) else [data]
    except ValueError:
        printers = []
        warn("printerlar ro'yxati olinmadi: %s" % out[:200])
    if not printers:
        warn("Windows'da printer topilmadi. Xprinter drayveri o'rnatilganini tekshiring.")
        return None
    pick = None
    for p in printers:
        if re.search(r"(?i)xprinter|xp-?\d", p.get("Name", "")):
            pick = p
            break
    if pick is None:
        print("   Printerlar:")
        for i, p in enumerate(printers, 1):
            print("     %d) %s" % (i, p.get("Name")))
        n = ask("Qaysi biri Xprinter (raqam)", "1")
        try:
            pick = printers[int(n) - 1]
        except (ValueError, IndexError):
            warn("noto'g'ri tanlov — printer qadami o'tkazib yuborildi")
            return None
    name = pick.get("Name")
    ok("printer: %s" % name)

    # LPD xizmati
    code, out = ps("Get-Service LPDSVC -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Status")
    if not out.strip():
        print("   LPD Print Service yoqilmoqda (1-3 daqiqa)...")
        c1, o1 = ps("Install-WindowsFeature Print-LPD-Service | Out-Null; 'ok'")
        if c1 != 0:
            c2, o2 = ps("Enable-WindowsOptionalFeature -Online -FeatureName Printing-Foundation-Features,"
                        "Printing-Foundation-LPDPrintService -All -NoRestart | Out-Null; 'ok'")
            if c2 != 0:
                warn("LPD yoqilmadi: %s" % (o2 or o1)[:300])
                warn("Qo'lda: Server Manager > Add Roles > Print and Document Services > LPD Service")
                return None
        ok("LPD Print Service o'rnatildi")
    run(["sc", "config", "LPDSVC", "start=", "auto"])
    run(["net", "start", "LPDSVC"])
    code, out = ps("(Get-Service LPDSVC).Status")
    if "Running" in out:
        ok("LPD xizmati ishlayapti (avtomatik ishga tushadi)")
    else:
        warn("LPD xizmati ishga tushmadi (%s). Kompyuterni qayta yoqib, ORNATISH.bat ni yana oching." % out[:100])

    # Ulashish
    share = (pick.get("ShareName") or "").strip() if pick.get("Shared") else ""
    if share and share.lower() != SHARE:
        warn("printer allaqachon '%s' nomi bilan ulashilgan — ilovaga shu nomni yozing" % share)
    else:
        c, o = ps("Set-Printer -Name '%s' -Shared $true -ShareName '%s'; 'ok'" % (name.replace("'", "''"), SHARE))
        if c == 0:
            share = SHARE
            ok("printer ulashildi, nomi: %s" % SHARE)
        else:
            warn("ulashib bo'lmadi: %s" % o[:200])
            warn("Qo'lda: Printer > Свойства принтера > Доступ > Общий доступ, имя: xprinter")
            share = SHARE

    # Brandmauer
    run(["netsh", "advfirewall", "firewall", "delete", "rule", "name=IMSOFT LPD 515"])
    c, o = run(["netsh", "advfirewall", "firewall", "add", "rule", "name=IMSOFT LPD 515", "dir=in", "action=allow",
                "protocol=TCP", "localport=515", "profile=any"])
    ok("brandmauer: 515-port ochildi") if c == 0 else warn("brandmauer qoidasi qo'shilmadi: %s" % o[:200])

    # Sinov
    if yes("Kompyuterning o'zidan sinov yorlig'i chiqarilsinmi", True):
        try:
            lpr_send("127.0.0.1", share, lpd_test_tspl())
            ok("sinov yuborildi — printerdan 'IMSOFT / Printer ulandi' yorlig'i chiqishi kerak")
        except Exception as e:  # noqa: BLE001
            warn("sinov yuborilmadi: %s" % e)
    return share


def step_forwarder():
    step(3, TOTAL, "forwarder.ini — telefon bilan bog'lovchi kalitlar")
    while True:
        text = read_text(FWD)
        if text is not None:
            good, why = firebase_ok(text)
            if good:
                cp = configparser.ConfigParser(interpolation=None)
                cp.read_string(text)
                ok("forwarder.ini to'g'ri (url: %s, secret: %s)" % (cp["firebase"].get("url", "").strip(), mask(cp["firebase"].get("secret", "").strip())))
                if not yes("Yangisi bilan almashtirasizmi", False):
                    return True
            else:
                warn("forwarder.ini to'liq emas: %s" % why)
        print("""
   Telefonda: Savdo Agent > Narx yorlig'i > yuqoridagi ⋮ > "Kompyuterdan kodlar (1C)" > "Kompyuter sozlamasi"
   (yoki Sozlamalar > Mijozlar va 2-ilova > Kompyuter sozlamasi) > Yuborish > o'zingizga Telegram.
   Kompyuterda o'sha matnni nusxalang. Hozir Bloknot ochiladi: hammasini o'chirib, matnni joylang,
   Ctrl+S bilan saqlang va Bloknotni yoping.""")
        input("   Tayyor bo'lsangiz Enter bosing...")
        if not os.path.exists(FWD):
            with open(FWD, "w", encoding="utf-8") as f:
                f.write("; Shu yerga telefondagi 'Kompyuter sozlamasi' matnini joylang va saqlang (Ctrl+S)\n")
        subprocess.run(["notepad.exe", FWD])
        text = read_text(FWD) or ""
        good, why = firebase_ok(text)
        if good:
            ok("forwarder.ini saqlandi")
            return True
        warn("hali to'g'ri emas: %s" % why)
        if not yes("Qayta urinasizmi", True):
            return False


def step_onec():
    step(4, TOTAL, "onec.ini — 1C ga ulanish")
    old = read_text(ONEC)
    if old:
        cp = configparser.ConfigParser(interpolation=None)
        try:
            cp.read_string(old)
            cur = cp["onec"].get("connection", "") if cp.has_section("onec") else ""
        except configparser.Error:
            cur = ""
        if cur.strip():
            ok("hozirgi baza: %s" % cur)
            if not yes("O'zgartirasizmi", False):
                return True
    bases = []
    for f in glob.glob(os.path.join(os.environ.get("APPDATA", ""), "1C", "1CEStart", "ibases.v8i")):
        bases += parse_ibases(read_text(f) or "")
    conn = ""
    if bases:
        print("   1C bazalari (1C ishga tushganda ko'rinadigan ro'yxat):")
        for i, (n, c) in enumerate(bases, 1):
            print("     %d) %s   — %s" % (i, n, c))
        v = ask("Qaysi baza (raqam) yoki papka yo'lini yozing", "1")
        if v.isdigit() and 1 <= int(v) <= len(bases):
            conn = bases[int(v) - 1][1]
        else:
            conn = conn_from_input(v)
    else:
        print("   1C bazalar ro'yxati topilmadi. 1C ishga tushganda bazani tanlab > Изменить — 'Каталог' papkasini yozing.")
        conn = conn_from_input(ask("Baza papkasi (masalan D:\\1C\\Baza)"))
    user = ask("1C foydalanuvchi nomi (1C'ga kiradigan ism)")
    password = getpass.getpass("   ?  1C paroli (yozilganda ko'rinmaydi, bo'sh bo'lsa Enter): ")
    with open(ONEC, "w", encoding="utf-8") as f:
        f.write(onec_ini_text(conn, user, password, keep=old))
    ok("onec.ini saqlandi: %s, foydalanuvchi: %s" % (conn, user or "(bo'sh)"))
    return True


def com_ready():
    c, o = run([sys.executable, "-c", "import win32com.client as w; w.Dispatch('V83.COMConnector'); print('ok')"], timeout=60)
    return c == 0 and "ok" in o, o


def step_codes():
    step(5, TOTAL, "1C'dan PLU va shtrix kodlarni olish (sinov)")
    bits = 64 if sys.maxsize > 2 ** 32 else 32
    pf64 = os.path.isdir(r"C:\Program Files\1cv8")
    pf32 = os.path.isdir(r"C:\Program Files (x86)\1cv8")
    ok("Python %s-bit; 1C: %s" % (bits, ", ".join(x for x, y in (("64-bit", pf64), ("32-bit", pf32)) if y) or "topilmadi"))
    ready, msg = com_ready()
    if not ready:
        base = r"C:\Program Files\1cv8" if bits == 64 else r"C:\Program Files (x86)\1cv8"
        dlls = sorted(glob.glob(os.path.join(base, "*", "bin", "comcntr.dll")))
        if dlls:
            print("   1C COM komponenti ro'yxatdan o'tkazilmoqda: %s" % dlls[-1])
            run(["regsvr32", "/s", dlls[-1]])
            ready, msg = com_ready()
    if not ready:
        if bits == 64 and pf32 and not pf64:
            warn("1C 32-bit, Python 64-bit — mos emas. python.org'dan Python 3.12 (32-bit) o'rnating va ORNATISH.bat ni qayta oching.")
        else:
            warn("1C COM ishlamadi: %s" % msg[-300:])
        return False
    ok("1C COM tayyor")
    print("   1C ga ulanilmoqda va kodlar olinmoqda (bir necha daqiqa bo'lishi mumkin)...")
    c, out = run([sys.executable, EXPORT, "--codes"], timeout=1800)
    print("\n".join("   | " + x for x in out.splitlines()[-15:]))
    if c != 0:
        warn("xato bilan tugadi")
        return False
    if "topilmadi" in out.lower():
        print("   PLU yoki shtrix kod joyi avtomatik topilmadi — 1C tuzilishi yozib olinmoqda...")
        run([sys.executable, EXPORT, "--discover"], timeout=1800)
        warn("D:\\imsoft\\discover.txt faylini dasturchiga yuboring (ichida parol yo'q).")
    return True


def step_autostart():
    step(6, TOTAL, "Avtozapusk: 1C kodlari har 6 soatda o'zi va telefondan so'ralganda yuboriladi")
    startup = os.path.join(os.environ.get("APPDATA", ""), r"Microsoft\Windows\Start Menu\Programs\Startup")
    os.makedirs(startup, exist_ok=True)
    for f in glob.glob(os.path.join(startup, "*")):
        if re.search(r"(?i)onec_export", os.path.basename(f)):
            try:
                os.remove(f)
            except OSError:
                pass
    path = os.path.join(startup, STARTUP_NAME)
    # v5.3: UTF-16 (BOM bilan) — yo'lda kirill harflari bo'lsa ham (C:\Users\Администратор\...) to'g'ri o'qiladi.
    # Avval ASCII edi: kirill harflari "?" bo'lib, kompyuter yonganda "Не удается найти указанный файл" chiqardi.
    with open(path, "w", encoding="utf-16", newline="") as f:
        f.write(startup_vbs(pythonw(), EXPORT, HERE))
    ok("avtozapuskka qo'yildi: %s" % path)
    # Ehtiyot: har soatda tekshiruvchi vazifa — dastur to'xtab qolgan bo'lsa qayta ishga tushadi
    # (ishlab turgan bo'lsa yangi nusxa o'zi chiqib ketadi — bir vaqtda faqat bitta ishlaydi).
    c, o = run(["schtasks", "/create", "/tn", "IMSOFT 1C kodlar", "/tr", '"%s" "%s"' % (pythonw(), EXPORT),
                "/sc", "hourly", "/rl", "highest", "/f"])
    ok("Rejalashtiruvchi: har soatda tekshiriladi") if c == 0 else warn("rejalashtiruvchiga qo'shilmadi: %s" % o[:200])
    ps("Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -match 'onec_export\\.py' } | "
       "ForEach-Object { Stop-Process -Id $_.ProcessId -Force }")
    time.sleep(1)
    subprocess.Popen([pythonw(), EXPORT], cwd=HERE, creationflags=getattr(subprocess, "DETACHED_PROCESS", 0))
    ok("onec_export.py hozir fonda ishga tushirildi")


def main():
    print()
    line("#")
    print("  IMSOFT — kompyuterni sozlash. Savollarga javob bering (Enter — qavs ichidagi javob).")
    line("#")
    if os.name != "nt":
        print("Bu dastur faqat Windows uchun.")
        return 1
    if not is_admin():
        warn("administrator emas — ORNATISH.bat orqali oching (o'zi administrator so'raydi).")
    share = None
    for fn in (step_cleanup, step_printer):
        try:
            r = fn()
            if fn is step_printer:
                share = r
        except KeyboardInterrupt:
            raise
        except Exception as e:  # noqa: BLE001
            warn("%s: %s" % (fn.__name__, e))
    fwd = False
    try:
        fwd = step_forwarder()
    except Exception as e:  # noqa: BLE001
        warn("forwarder.ini: %s" % e)
    onec = False
    try:
        onec = step_onec()
    except Exception as e:  # noqa: BLE001
        warn("onec.ini: %s" % e)
    codes = False
    if fwd and onec:
        try:
            codes = step_codes()
        except Exception as e:  # noqa: BLE001
            warn("kodlar: %s" % e)
        try:
            step_autostart()
        except Exception as e:  # noqa: BLE001
            warn("avtozapusk: %s" % e)
    print()
    line("#")
    print("  NATIJA")
    line("#")
    ips = local_ips()
    print("  Telefonda (Savdo Agent > Narx yorlig'i > printer belgisi):")
    print("    IP: %s   printer nomi: %s" % (" yoki ".join(ips) or "?", share or SHARE))
    print("    > Tekshirish > Sinov yorlig'i")
    print("  PLU/shtrix: Narx yorlig'i > ⋮ > Kompyuterdan kodlar (1C) > Hozir yangilash")
    print("  Holat: printer %s, kalitlar %s, 1C %s, kodlar %s" % (
        "OK" if share else "XATO", "OK" if fwd else "XATO", "OK" if onec else "XATO", "OK" if codes else "tekshirilmadi/XATO"))
    print("  Biror narsa XATO bo'lsa — shu oynaning rasmini dasturchiga yuboring. Qayta: ORNATISH.bat")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        print("\nTo'xtatildi.")
        sys.exit(1)
