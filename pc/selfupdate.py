#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
v5.3: kompyuter dasturi O'ZI yangilanadi (telefondagi ilovalar kabi).

Avval ilova yangilanib, kompyuterdagi fayllar eski qolar edi - shunda telefondan kelgan yangi turdagi so'rovlar
"so'rov buzuq yoki eski ilova versiyasi" degan xato bilan qaytardi. Endi:
  * Admin ilovasi yangilanish manzilini bulutga yozadi (p/{inbox}/upd);
  * shu dastur soatiga bir marta <manzil>/pc.json ni tekshiradi; versiya katta bo'lsa pc.zip ni yuklab,
    sha256 ni tekshiradi, fayllarni almashtiradi va o'zini qayta ishga tushiradi.
Sozlama fayllari (*.ini, *.json, jurnal, zaxira) HECH QACHON almashtirilmaydi.
"""
import hashlib
import io
import json
import logging
import os
import sys
import time
import urllib.request
import zipfile

log = logging.getLogger("onec_export")

VERSION_FILE = "pc_version.txt"
ALLOWED = (".py", ".bat", ".vbs", ".txt", ".md", ".example", ".html", ".js", ".css")
CHECK_SEC = 3600
MAX_ZIP = 8 * 1024 * 1024


def local_version(here):
    try:
        with open(os.path.join(here, VERSION_FILE), "r", encoding="utf-8") as f:
            return int(f.read().strip() or 0)
    except (OSError, ValueError):
        return 0


def safe_name(name):
    """Faqat dastur fayllari, faqat shu papka (yoki bitta ichki papka) ichida. Sozlama fayllari - yo'q."""
    n = name.replace("\\", "/")
    if n.endswith("/") or n.startswith("/") or ".." in n.split("/") or ":" in n:
        return None
    parts = n.split("/")
    if len(parts) > 2 or any(not p or p.startswith(".") for p in parts):
        return None
    low = parts[-1].lower()
    if low == VERSION_FILE:
        return n
    if low.endswith(".ini") or low.endswith(".json") or low.endswith(".jsonl"):
        return None
    if not low.endswith(ALLOWED):
        return None
    return n


def plan(manifest, here):
    """pc.json -> (versiya, zip nomi, sha) yoki None (yangi emas / buzuq)."""
    if not isinstance(manifest, dict):
        return None
    try:
        v = int(manifest.get("v") or 0)
    except (TypeError, ValueError):
        return None
    name = str(manifest.get("zip") or "")
    sha = str(manifest.get("sha") or "").lower()
    if v <= local_version(here) or not name or "/" in name or len(sha) != 64:
        return None
    return v, name, sha


def apply_zip(raw, sha, here):
    """Tekshirib, fayllarni almashtiradi. @return yozilgan fayllar soni. Xato bo'lsa hech narsa o'zgarmaydi."""
    if len(raw) > MAX_ZIP or hashlib.sha256(raw).hexdigest() != sha:
        raise ValueError("yangilanish fayli buzuq (sha256 mos emas)")
    files = []
    with zipfile.ZipFile(io.BytesIO(raw)) as z:
        for info in z.infolist():
            if info.is_dir():
                continue
            n = safe_name(info.filename)
            if n is None:
                continue
            files.append((n, z.read(info)))
    names = [n for n, _ in files]
    if "onec_export.py" not in names or VERSION_FILE not in names:
        raise ValueError("yangilanish to'liq emas")
    # Python fayllari sintaksisi tekshiriladi - yarim yuklangan/buzuq kod o'rnatilmaydi
    for n, data in files:
        if n.endswith(".py"):
            compile(data.decode("utf-8"), n, "exec")
    staged = []
    for n, data in files:
        dst = os.path.join(here, *n.split("/"))
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        tmp = dst + ".new"
        with open(tmp, "wb") as f:
            f.write(data)
        staged.append((tmp, dst))
    # versiya fayli eng oxirida: o'rtada uzilsa keyingi safar qayta urinadi
    staged.sort(key=lambda x: os.path.basename(x[1]) == VERSION_FILE)
    for tmp, dst in staged:
        os.replace(tmp, dst)
    return len(staged)


def fetch(url, timeout=60, limit=MAX_ZIP):
    req = urllib.request.Request(url, headers={"Cache-Control": "no-cache", "User-Agent": "imsoft-pc"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read(limit + 1)


def check(cloud, inbox, here, fetcher=fetch):
    """@return yangi versiya o'rnatildimi (True bo'lsa dastur qayta ishga tushirilishi kerak)."""
    base = cloud.call("GET", "p/%s/upd" % inbox, timeout=30)
    if not isinstance(base, str) or not base.startswith("https://"):
        return False
    base = base.rstrip("/") + "/"
    manifest = json.loads(fetcher(base + "pc.json?t=%d" % int(time.time())).decode("utf-8"))
    p = plan(manifest, here)
    if p is None:
        return False
    v, name, sha = p
    n = apply_zip(fetcher(base + name + "?v=%d" % v), sha, here)
    log.info("kompyuter dasturi yangilandi: versiya %d (%d ta fayl)", v, n)
    return True


def restart():
    """Shu dasturni yangi kod bilan qayta ishga tushiradi."""
    log.info("qayta ishga tushirilmoqda…")
    try:
        logging.shutdown()
    except Exception:  # noqa: BLE001
        pass
    os.execv(sys.executable, [sys.executable] + sys.argv)


class Watch:
    """Boshqa dasturlar (chek, yorliq) uchun: versiya fayli o'zgarsa - o'zini qayta ishga tushiradi."""

    def __init__(self, here):
        self.here = here
        self.started = local_version(here)
        self.at = time.time()

    def changed(self, every=120):
        if time.time() - self.at < every:
            return False
        self.at = time.time()
        return local_version(self.here) != self.started
