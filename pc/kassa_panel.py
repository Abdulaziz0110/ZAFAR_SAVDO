# -*- coding: utf-8 -*-
"""
pc 12: KASSA PANELI TAVSIYASI — eng ko'p sotilgan tovarlar paneli (har hafta yangilanadi, admin tasdiqlaydi).

Tartib (8 sahifa x 42 tugma):
  1) TAROZI (kg) tovarlari — "oila"lar bo'yicha: hamma pistalar ketma-ket (nomi bo'yicha), keyin bodomlar, keshu ...
     Oilalar umumiy sotuvi bo'yicha (eng ko'p sotilgani birinchi); bitta oila iloji bo'lsa bitta sahifada.
  2) SHTRIX KODLI tovarlar — xuddi shunday, yangi sahifadan.
  3) BOSHQA tez ketadiganlar — yangi sahifadan.
Mashhurlik: oxirgi [DAYS] kunda nechta sotuv hujjatida bo'lgani (ПродажаРозница, qaytarishsiz).
"""
import re

SLOTS = 336
PAGE = 42
DAYS = 28
CAPS = ((PAGE * 4), (PAGE * 2))      # tarozi <= 4 sahifa, shtrix <= 2 sahifa, qolgani — boshqa

_CYR = {
    "а": "a", "б": "b", "в": "v", "г": "g", "д": "d", "е": "e", "ё": "yo", "ж": "j", "з": "z", "и": "i", "й": "y",
    "к": "k", "л": "l", "м": "m", "н": "n", "о": "o", "п": "p", "р": "r", "с": "s", "т": "t", "у": "u", "ф": "f",
    "х": "x", "ц": "s", "ч": "ch", "ш": "sh", "щ": "sh", "ъ": "", "ы": "i", "ь": "", "э": "e", "ю": "yu", "я": "ya",
    "ў": "o", "қ": "q", "ғ": "g", "ҳ": "h",
}
_APOS = "'’ʻʼ‘`´ʹ"


def fold(s):
    """Telefondagi qidiruv bilan bir xil buklash (kirill -> lotin, tutuq belgisisiz)."""
    out = []
    for ch in str(s or "").lower():
        if ch in _CYR:
            out.append(_CYR[ch])
        elif ch in _APOS:
            continue
        elif ch == "w":
            out.append("v")
        elif ch.isalnum():
            out.append(ch)
        else:
            out.append(" ")
    return re.sub(r" +", " ", "".join(out)).strip()


def family(name):
    """Oila kaliti: birinchi ma'noli so'z ("B Mayiz 134" -> mayiz, "Pista Eron" -> pista)."""
    words = [w for w in re.split(r"[ 0-9]+", fold(name)) if len(w) >= 3]
    return words[0] if words else fold(name)[:12]


def is_weighted(g):
    return fold(g.get("e", "")).replace(" ", "") in ("kg", "kilogramm", "kilogram", "kgr")


def _families(items, score):
    fams = {}
    for g in items:
        fams.setdefault(family(g["n"]), []).append(g)
    out = []
    for key, gs in fams.items():
        gs.sort(key=lambda g: fold(g["n"]))
        out.append((sum(score(g) for g in gs), key, gs))
    out.sort(key=lambda x: (-x[0], x[1]))
    return [x[2] for x in out]


def _trim(items, cap, score):
    """Eng ko'p sotilgan [cap] ta (oila to'liq bo'lmasa ham)."""
    return sorted(items, key=lambda g: (-score(g), fold(g["n"])))[:cap]


def suggest(goods, stats, barcodes, slots=SLOTS, page=PAGE):
    """
    goods: snapshot tovarlari ({c, n, e}); stats: {kod: sotuv hujjatlari soni}; barcodes: {shtrix: kod}.
    @return {"pnl": [[kod, nomi]] x slots, "sec": [{"t", "from", "to", "n"}]}
    """
    def score(g):
        return stats.get(g["c"], 0)
    sold = [g for g in goods if score(g) > 0]
    bar = set(barcodes.values())
    weighted = [g for g in sold if is_weighted(g)]
    barcoded = [g for g in sold if not is_weighted(g) and g["c"] in bar]
    other = [g for g in sold if not is_weighted(g) and g["c"] not in bar]
    sections = [("TAROZI (KG)", _trim(weighted, CAPS[0], score)), ("SHTRIX KODLI", _trim(barcoded, CAPS[1], score)),
                ("BOSHQA", _trim(other, slots, score))]
    out = [["", ""] for _ in range(slots)]
    marks = []
    pos = 0
    for title, items in sections:
        if not items:
            continue
        if pos % page:
            pos += page - pos % page          # har bo'lim yangi sahifadan
        if pos >= slots:
            break
        start = pos
        n = 0
        for fam in _families(items, score):
            if len(fam) <= page and pos % page and pos % page + len(fam) > page:
                pos += page - pos % page      # oila ikki sahifaga bo'linmasin
            for g in fam:
                if pos >= slots:
                    break
                out[pos] = [g["c"], g["n"]]
                pos += 1
                n += 1
        marks.append({"t": title, "from": start, "to": pos, "n": n})
    return {"pnl": out, "sec": marks}
