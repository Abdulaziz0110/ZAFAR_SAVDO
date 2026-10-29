#!/usr/bin/env python3
"""1C tarixi to'plamini (ui/ledger + maketlar) 1-ilovadan 2-ilovaga ko'chiradi — ikkalasi bir xil ko'rinsin va ishlasin.
Manba: app/ (1-ilova). 2-ilovadagi nusxani qo'lda o'zgartirmang — shu skriptni ishga tushiring.
"""
import os, shutil

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC_PKG, DST_PKG = 'com.ilxomjon.dur_novvot_dokon', 'uz.imsoft.mijoz'
SRC_JAVA = os.path.join(ROOT, 'app/src/main/java/com/ilxomjon/dur_novvot_dokon/ui/ledger')
DST_JAVA = os.path.join(ROOT, 'mijoz/src/main/java/uz/imsoft/mijoz/ui/ledger')
LAYOUTS = ['fragment_ledger.xml', 'item_ledger_day.xml', 'item_ledger_doc.xml', 'item_ledger_line.xml', 'sheet_ledger_doc.xml',
           'sheet_ledger_repeat.xml', 'item_repeat_line.xml', 'dialog_receipt_preview.xml']
DRAWABLES = ['bg_circle.xml', 'bg_line_match.xml']
XML = ['file_paths.xml']

os.makedirs(DST_JAVA, exist_ok=True)
for f in os.listdir(DST_JAVA):
    if f.endswith('.kt'):
        os.remove(os.path.join(DST_JAVA, f))
n = 0
for f in sorted(os.listdir(SRC_JAVA)):
    if not f.endswith('.kt'):
        continue
    s = open(os.path.join(SRC_JAVA, f), encoding='utf-8').read().replace(SRC_PKG, DST_PKG)
    s = s.replace('// DIQQAT: ui/ledger/ papkasi', '// NUSXA (tools/sync_ledger_kit.py) — o\'zgartirmang. ui/ledger/ papkasi')
    open(os.path.join(DST_JAVA, f), 'w', encoding='utf-8').write(s)
    n += 1
for sub, names in (('layout', LAYOUTS), ('drawable', DRAWABLES), ('xml', XML)):
    for name in names:
        shutil.copyfile(os.path.join(ROOT, 'app/src/main/res', sub, name), os.path.join(ROOT, 'mijoz/src/main/res', sub, name))
        n += 1
print(n, 'files synced')
