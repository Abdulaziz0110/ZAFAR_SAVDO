#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
pc 43: do'kon vizitkasi — Xprinter (yorliq printeri, TSPL) uchun. Har qator yorliq eniga sig'adigan ENG KATTA shriftda,
hammasi bo'yiga sig'maguncha birga kichraytiriladi. Pillow bo'lsa — TrueType (qalin) rasm; bo'lmasa — printerning
o'z shriftlari (TSPL TEXT).
"""
import io
import os

DEFAULT_LINES = [
    "ZAFAR QURUQ MEVALARI",
    "+998 93 647 46 46",
    "+998 91 123 45 76",
    "+998 90 778 85 10",
    "Telegram: @zafarquruqmevalari",
    "Instagram: @zafar_quruq_mevalari",
]
MAX_LINES = 8
MAX_LEN = 60
FONTS = (r"C:\Windows\Fonts\arialbd.ttf", r"C:\Windows\Fonts\calibrib.ttf", r"C:\Windows\Fonts\segoeuib.ttf",
         "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf")
MARGIN = 10         # nuqta (203 dpi: ~1,25 mm)
GAP = 4             # qatorlar orasi, nuqta


def clean_lines(lines):
    out = []
    for x in lines or []:
        t = " ".join(str(x or "").split())[:MAX_LEN]
        if t:
            out.append(t)
    return out[:MAX_LINES] or list(DEFAULT_LINES)


def dots(mm, dpi=203):
    return int(round(float(mm) * dpi / 25.4))


def _font_path():
    for p in FONTS:
        if os.path.exists(p):
            return p
    return None


def fit_sizes(lines, width, height, measure, lo=8, hi=400):
    """Har qatorga eniga sig'adigan eng katta shrift o'lchami, keyin hammasi bo'yiga sig'guncha birga kichraytiriladi.
    measure(text, size) -> (w, h). @return [size]"""
    sizes = []
    for t in lines:
        a, b = lo, hi
        while a < b:                                    # eng katta o'lcham: eni <= width
            m = (a + b + 1) // 2
            if measure(t, m)[0] <= width:
                a = m
            else:
                b = m - 1
        sizes.append(a)

    def total(ss):
        return sum(measure(t, s)[1] for t, s in zip(lines, ss)) + GAP * (len(lines) - 1)
    k = 1.0
    while total(sizes) > height and k > 0.05:
        k *= 0.95
        sizes = [max(lo, int(s * 0.95)) for s in sizes]
        if all(s == lo for s in sizes):
            break
    return sizes


def render_png(lines, w, h):
    """Qora-oq PNG (w x h nuqta). Pillow yoki TrueType shrift yo'q bo'lsa — None."""
    try:
        from PIL import Image, ImageDraw, ImageFont
    except ImportError:
        return None
    path = _font_path()
    if path is None:
        return None
    cache = {}

    def font(s):
        if s not in cache:
            cache[s] = ImageFont.truetype(path, s)
        return cache[s]

    def measure(t, s):
        x0, y0, x1, y1 = font(s).getbbox(t)
        return x1 - x0, y1 - y0
    lines = clean_lines(lines)
    sizes = fit_sizes(lines, w - 2 * MARGIN, h - 2 * MARGIN, measure)
    img = Image.new("L", (w, h), 255)
    d = ImageDraw.Draw(img)
    hs = [measure(t, s)[1] for t, s in zip(lines, sizes)]
    free = (h - 2 * MARGIN) - sum(hs)
    gap = max(GAP, free // max(1, len(lines) - 1)) if len(lines) > 1 else 0
    gap = min(gap, GAP * 6)
    y = MARGIN + max(0, ((h - 2 * MARGIN) - sum(hs) - gap * (len(lines) - 1)) // 2)
    for t, s, lh in zip(lines, sizes, hs):
        f = font(s)
        x0, y0, x1, y1 = f.getbbox(t)
        x = (w - (x1 - x0)) // 2 - x0
        d.text((x, y - y0), t, font=f, fill=0)
        y += lh + gap
    out = io.BytesIO()
    img.point(lambda v: 255 if v >= 128 else 0).convert("1").save(out, "PNG")
    return out.getvalue()


# TSPL o'z shriftlari (203 dpi): nomi -> (eni, bo'yi) nuqta; ko'paytiruvchi 1..10
TSPL_FONTS = (("1", 8, 12), ("2", 12, 20), ("3", 16, 24), ("4", 24, 32), ("5", 32, 48))


def tspl_text(lines, w, h, wmm, hmm, gap_mm=2, copies=1, density=10):
    """Pillow'siz: har qatorga sig'adigan eng katta TSPL shrifti (shrift x ko'paytiruvchi), o'rtada."""
    lines = clean_lines(lines)
    pick = []
    for t in lines:
        best = ("1", 8, 12, 1)
        for name, cw, ch in TSPL_FONTS:
            for m in range(1, 11):
                if len(t) * cw * m <= w - 2 * MARGIN and ch * m > best[2] * best[3]:
                    best = (name, cw, ch, m)
        pick.append(best)
    while sum(p[2] * p[3] for p in pick) + GAP * (len(pick) - 1) > h - 2 * MARGIN:
        i = max(range(len(pick)), key=lambda j: pick[j][2] * pick[j][3])
        name, cw, ch, m = pick[i]
        if m > 1:
            pick[i] = (name, cw, ch, m - 1)
        else:
            smaller = [f for f in TSPL_FONTS if f[2] < ch]
            if not smaller:
                break
            pick[i] = (smaller[-1][0], smaller[-1][1], smaller[-1][2], 1)
    used = sum(p[2] * p[3] for p in pick) + GAP * (len(pick) - 1)
    y = MARGIN + max(0, (h - 2 * MARGIN - used) // 2)
    body = []
    for t, (name, cw, ch, m) in zip(lines, pick):
        x = max(0, (w - len(t) * cw * m) // 2)
        body.append('TEXT %d,%d,"%s",0,%d,%d,"%s"' % (x, y, name, m, m, t.replace('"', "'")))
        y += ch * m + GAP
    head = ("SIZE %s mm,%s mm\r\nGAP %s mm,0 mm\r\nDIRECTION 1\r\nREFERENCE 0,0\r\nDENSITY %d\r\nSPEED 4\r\nCLS\r\n"
            % (_n(wmm), _n(hmm), _n(gap_mm), density))
    return (head + "\r\n".join(body) + "\r\nPRINT 1,%d\r\n" % max(1, min(99, int(copies)))).encode("ascii", "replace")


def build(lines, wmm=58.0, hmm=40.0, gap_mm=2.0, copies=1, dpi=203):
    """Vizitka -> TSPL baytlari (printerga RAW)."""
    w, h = dots(wmm, dpi), dots(hmm, dpi)
    png = render_png(lines, w, h)
    if png is None:
        return tspl_text(lines, w, h, wmm, hmm, gap_mm, copies)
    import label_printer
    return label_printer.tspl_bytes(png, max(1, min(99, int(copies))), dpi=dpi, width_mm=wmm, height_mm=hmm, gap_mm=gap_mm,
                                    density=10)


def _n(v):
    return ("%.1f" % float(v)).rstrip("0").rstrip(".")
