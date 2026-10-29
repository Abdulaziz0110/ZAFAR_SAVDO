#!/usr/bin/env python3
"""
Test uchun 1C "Mijoz kartochkasi" hisobotiga o'xshash fayllar yaratadi (ekran rasmidagi tuzilma bo'yicha):
  shared/src/test/resources/cards/card.htm  (windows-1251, rowspan/colspan)
  shared/src/test/resources/cards/card.xls  (BIFF8, birlashtirilgan kataklar)  — xlwt kerak
Haqiqiy 1C fayli kelganda u ham shu papkaga qo'shiladi va testlar u bilan ham ishlaydi.
"""
import os, sys

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "shared/src/test/resources/cards")
os.makedirs(OUT, exist_ok=True)

COLS = ["Sana", "Xarakat", "Izoxlar", "Soni", "Narxi", "Summa", "Chegirma (Mijoz)", "To'lov (Mijoz)",
        "Qaytarish (Mijoz)", "Chegirmani qaytarish (Mijoz)", "To'lov qaytarish (Mijoz)", "Qoldiq"]
# (sana, xarakat, izox, soni, {ustun: (som, usd)})
DAYS = [
    ("27.10.2026", [
        ("27.10.2026\n10:41:17", "Savdo So'm", "Qulupnay (Sublimit)", "2,5", {"Narxi": ("200 000", ""), "Summa": ("500 000", "")}),
        ("27.10.2026\n14:56:34", "Savdo So'm", "Pista Ahmadiy (AXT)", "20", {"Narxi": ("187 000", ""), "Summa": ("3 740 000", "")}),
        ("27.10.2026\n14:56:34", "Savdo So'm", "B Mayiz 134", "10", {"Narxi": ("71 000", ""), "Summa": ("710 000", "")}),
        ("27.10.2026\n15:10:00", "Qaytarish So'm", "B Mayiz 134", "2", {"Narxi": ("71 000", ""), "Qaytarish (Mijoz)": ("142 000", "")}),
        ("27.10.2026\n18:00:00", "To'lov So'm", "Naqd", "", {"To'lov (Mijoz)": ("1 000 000", "")}),
    ], {"Summa": ("4 950 000", ""), "Qaytarish (Mijoz)": ("142 000", ""), "To'lov (Mijoz)": ("1 000 000", "")}),
    ("28.10.2026", [
        ("28.10.2026 9:02:36", "Savdo So'm", "B Osh Mayiz 57", "25", {"Narxi": ("27 000", ""), "Summa": ("675 000", ""), "Chegirma (Mijoz)": ("5 000", "")}),
        ("28.10.2026\n12:18:56", "Savdo $", "Pista Akbariy (AK)", "40", {"Narxi": ("", "15,5"), "Summa": ("", "620")}),
        ("28.10.2026\n13:00:00", "To'lov $", "Click", "", {"To'lov (Mijoz)": ("", "100")}),
    ], {"Summa": ("675 000", "620")}),
]


def html():
    h = ['<HTML><HEAD><META HTTP-EQUIV="Content-Type" CONTENT="text/html; CHARSET=windows-1251"><TITLE>Mijoz kartochkasi</TITLE></HEAD><BODY>',
         '<TABLE BORDER=1 CELLSPACING=0>']
    n = len(COLS)
    h.append('<TR><TD COLSPAN=%d ALIGN=RIGHT><B>01.10.2022 dan 29.10.2026&nbsp;&nbsp;:&nbsp;&nbsp;Dadaxon Aka (TRZ)</B></TD></TR>' % n)
    h.append('<TR><TD COLSPAN=%d>&nbsp;</TD></TR>' % n)
    row = ['<TR>']
    for c in COLS:
        if c in ("Sana", "Xarakat", "Izoxlar", "Soni"):
            row.append('<TD ROWSPAN=3><B>%s</B></TD>' % c)
        else:
            row.append('<TD><B>%s</B></TD>' % c.replace(" (", "<BR>("))
    row.append('</TR>')
    h.append(''.join(row))
    h.append('<TR>' + ''.join('<TD>So&#39;m</TD>' for c in COLS[4:]) + '</TR>')
    h.append('<TR>' + ''.join('<TD ALIGN=RIGHT>$</TD>' for c in COLS[4:]) + '</TR>')
    h.append('<TR><TD COLSPAN=%d>&nbsp;</TD></TR>' % n)
    for day, rows, tot in DAYS:
        for (dt, kind, izox, qty, vals) in rows:
            r1 = ['<TR>', '<TD ROWSPAN=2>%s</TD>' % dt.replace("\n", "<BR>"), '<TD ROWSPAN=2>%s</TD>' % kind,
                  '<TD ROWSPAN=2>%s</TD>' % izox, '<TD ROWSPAN=2 ALIGN=RIGHT>%s</TD>' % qty]
            r2 = ['<TR>']
            for c in COLS[4:]:
                s, u = vals.get(c, ("", ""))
                r1.append('<TD ALIGN=RIGHT>%s</TD>' % (s.replace(" ", "&nbsp;") or "&nbsp;"))
                r2.append('<TD ALIGN=RIGHT>%s</TD>' % (u or "&nbsp;"))
            h.append(''.join(r1) + '</TR>')
            h.append(''.join(r2) + '</TR>')
        # kunlik jami (oraliq) qatori — o'tkazib yuborilishi kerak
        r1 = ['<TR BGCOLOR=#FFCC99>', '<TD COLSPAN=3 ROWSPAN=2><B>%s 0:00:00</B></TD>' % day, '<TD ROWSPAN=2>&nbsp;</TD>']
        r2 = ['<TR BGCOLOR=#FFCC99>']
        for c in COLS[4:]:
            s, u = tot.get(c, ("", ""))
            r1.append('<TD>%s</TD>' % (s or "&nbsp;"))
            r2.append('<TD>%s</TD>' % (u or "&nbsp;"))
        h.append(''.join(r1) + '</TR>')
        h.append(''.join(r2) + '</TR>')
    h.append('</TABLE></BODY></HTML>')
    with open(os.path.join(OUT, "card.htm"), "wb") as f:
        f.write("\n".join(h).encode("cp1251"))


def xls():
    try:
        import xlwt
    except ImportError:
        print("xlwt yo'q — card.xls yaratilmadi"); return
    wb = xlwt.Workbook(encoding="utf-8")
    ws = wb.add_sheet("Sheet1")
    n = len(COLS)
    ws.write_merge(1, 1, 0, n - 1, "01.10.2022 dan 29.10.2026  :  Dadaxon Aka (TRZ)")
    r = 3
    for i, c in enumerate(COLS):
        if c in ("Sana", "Xarakat", "Izoxlar", "Soni"):
            ws.write_merge(r, r + 2, i, i, c)
        else:
            ws.write(r, i, c.replace(" (", "\n("))
            ws.write(r + 1, i, "So'm")
            ws.write(r + 2, i, "$")
    r += 4
    def num(t):
        t = t.replace(" ", "").replace(",", ".")
        return float(t) if t else None
    for day, rows, tot in DAYS:
        for (dt, kind, izox, qty, vals) in rows:
            ws.write_merge(r, r + 1, 0, 0, dt)
            ws.write_merge(r, r + 1, 1, 1, kind)
            ws.write_merge(r, r + 1, 2, 2, izox)
            if qty:
                ws.write_merge(r, r + 1, 3, 3, num(qty))
            for i, c in enumerate(COLS[4:], start=4):
                s, u = vals.get(c, ("", ""))
                if s: ws.write(r, i, num(s))
                if u: ws.write(r + 1, i, num(u))
            r += 2
        ws.write_merge(r, r + 1, 0, 2, day + " 0:00:00")
        for i, c in enumerate(COLS[4:], start=4):
            s, u = tot.get(c, ("", ""))
            if s: ws.write(r, i, num(s))
            if u: ws.write(r + 1, i, num(u))
        r += 2
    wb.save(os.path.join(OUT, "card.xls"))


def big_xls():
    """SST bir nechta CONTINUE yozuviga bo'linadigan katta fayl (kirill + lotin aralash)."""
    try:
        import xlwt
    except ImportError:
        return
    wb = xlwt.Workbook(encoding="utf-8")
    ws = wb.add_sheet("Sheet1")
    for i in range(3000):
        ws.write(i, 0, ("Ёнғоқ мағзи №%d — " % i) + "x" * (i % 37) if i % 2 else ("Pista Ahmadiy %d " % i) + "y" * (i % 41))
        ws.write(i, 1, i * 1.5)
    wb.save(os.path.join(OUT, "big.xls"))


def real_like_xlsx():
    """Haqiqiy 1C (Excel'da saqlangan) "Mijoz kartochkasi" tuzilmasi (2026-10-29 namunasi bo'yicha, ma'lumot o'ylab topilgan):
    20 ustun (yetkazib beruvchi, qarz olish/berish, Qarzi (Dt)/Xaqqi (Kt)), kun boshiga/oxiriga qoldiq qatorlari,
    raqamlar satr sifatida ("340 000"), har yozuv 2 qator (So'm/$), kunlik jami qatorlari, umumiy aylanma."""
    try:
        import openpyxl
    except ImportError:
        print("openpyxl yo'q — card_real_like.xlsx yaratilmadi"); return
    wb = openpyxl.Workbook(); ws = wb.active; ws.title = "Лист1"
    def put(cell, v): ws[cell] = v
    put("B2", "01.10.2026 dan 03.10.2026    :  Test Mijoz (TRZ)"); ws.merge_cells("B2:AC2")
    hdr = {"B": "Sana", "C": "Xarakat", "D": "Izoxlar", "F": "Soni", "H": "Narxi", "J": "Summa", "K": "Chegirma (Mijoz)",
           "L": "To'lov (Mijoz)", "N": "Qaytarish (Mijoz)", "O": "Chegirmani qaytarish (Mijoz)", "P": "To'lovnini qaytarish (Mijoz)",
           "R": "Maxsulot kirimi", "S": "Chegirma (Yuk beruvchi)", "T": "To'lov (Yuk beruvchi)", "V": "Qaytarish (Yuk beruvchi)",
           "W": "Chegirmani qaytarish (Yuk beruvchi)", "Y": "Qarz olish", "Z": "Qarzga berish", "AB": "Qarzi (Dt)", "AC": "Xaqqi (Kt)"}
    for col, t in hdr.items():
        put(col + "6", t)
        if col in ("B", "C", "D", "F"):
            ws.merge_cells("%s6:%s8" % (col, col))
        else:
            put(col + "7", "So'm"); put(col + "8", "$       ")
    put("I7", "  "); put("I8", "  ")
    put("B9", "01.10.2026 - sanadagi kun boshiga qarzdorlik qoldiq summasi "); ws.merge_cells("B9:Z10"); put("AB9", "1 000 000")
    rows = [
        ("01.10.2026 10:08:52", "Savdo So'm", "Pista Turk (Cankaya 5)", 5, "340 000", {"J": "1 700 000", "AB": "2 700 000"}),
        ("01.10.2026 14:51:47", "To'lov So'm", "Мижоз накд сумда тулов килди", None, None, {"L": "155 000", "AB": "2 545 000"}),
        ("SUB", "01.10.2026 0:00:00", 5, {"J": "1 700 000", "L": "155 000", "AB": "2 545 000"}),
        ("02.10.2026 10:52:50", "Qaytarib olish So'm", "Quvvat Sulton 1 kg", 4, "80 000", {"N": "320 000", "AB": "2 225 000"}),
        ("02.10.2026 11:00:00", "Savdo So'm", "Bodom Yongoq M", 2.5, "117 000", {"J": "292 500", "AB": "2 517 500"}),
        ("SUB", "02.10.2026 0:00:00", 6.5, {"J": "292 500", "N": "320 000", "AB": "2 517 500"}),
    ]
    r = 12
    for row in rows:
        if row[0] == "SUB":
            put("B%d" % r, row[1]); ws.merge_cells("B%d:D%d" % (r, r + 1)); put("F%d" % r, row[2])
            for c, v in row[3].items(): put("%s%d" % (c, r), v)
            r += 3
            continue
        dt, kind, izox, qty, price, vals = row
        put("B%d" % r, dt); ws.merge_cells("B%d:B%d" % (r, r + 1))
        put("C%d" % r, kind); ws.merge_cells("C%d:C%d" % (r, r + 1))
        put("D%d" % r, izox); ws.merge_cells("D%d:E%d" % (r, r + 1))
        if qty is not None: put("F%d" % r, qty)
        if price: put("H%d" % r, price)
        put("I%d" % r, "  "); put("I%d" % (r + 1), "  ")
        for c, v in vals.items(): put("%s%d" % (c, r), v)
        r += 2
    put("B%d" % (r + 2), "01.10.2026 - 02.10.2026  - саналардаги умумий айланма: "); put("J%d" % (r + 2), "1 992 500")
    put("B%d" % (r + 5), "02.10.2026 - sanada kun oxiriga qarzdorlik qoldiq summasi "); put("AB%d" % (r + 5), "2 517 500")
    wb.save(os.path.join(OUT, "card_real_like.xlsx"))


html()
xls()
big_xls()
real_like_xlsx()
print("OK:", sorted(os.listdir(OUT)))
