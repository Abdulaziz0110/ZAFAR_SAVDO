#!/usr/bin/env python3
"""v3.0: tugma matni sig'masa "..." bo'lib qolmasin (1-ilova maketlari).
Har bir matnli MaterialButton uchun (takror ishga tushirish xavfsiz):
  * qat'iy balandlik (48dp) -> wrap_content + minHeight (matn 2 qatorga o'tsa tugma o'sadi); yonma-yonlari match_parent
    (bir qatordagi tugmalar bir xil balandlikda qoladi);
  * maxLines=2 (bo'lmasa);
  * tanlash guruhidagi (MaterialButtonToggleGroup) tugmalarda chetki bo'sh joy 6dp;
  * yonma-yon (layout_width=0dp) tugmalarda chetki bo'sh joy 12dp.
Generatorlardan (gen_*.py) keyin shuni ishga tushiring. 2-ilova bilan umumiy (sync_ledger_kit) maketlarga tegmaydi.
"""
import os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LAYOUT = os.path.join(ROOT, 'app/src/main/res/layout')
# sync_ledger_kit.py dagi LAYOUTS ro'yxati (u faylni import qilsak — nusxalashni boshlab yuboradi, shuning uchun matndan o'qiymiz)
_kit = open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'sync_ledger_kit.py'), encoding='utf-8').read()
SHARED_LAYOUTS = set(re.findall(r"'([a-z_]+\.xml)'", re.search(r'LAYOUTS = \[(.*?)\]', _kit, re.S).group(1)))

BTN = re.compile(r'<com\.google\.android\.material\.button\.MaterialButton\b(?P<attrs>[^>]*?)(?P<end>/?>)', re.S)
GROUP_OPEN = '<com.google.android.material.button.MaterialButtonToggleGroup'
GROUP_CLOSE = '</com.google.android.material.button.MaterialButtonToggleGroup>'
PAD = re.compile(r'android:padding(Start|End|Left|Right|Horizontal)?="')


def indent_of(attrs):
    m = re.search(r'\n([ \t]+)\S', attrs)
    return m.group(1) if m else '    '


def fix_one(attrs, in_group):
    if 'android:text=' not in attrs:
        return attrs  # faqat belgili tugma
    ind = indent_of(attrs)
    add = []
    # Yonma-yon tugmalar (guruhda yoki eni 0dp) bir xil balandlikda bo'lsin: match_parent (LinearLayout eng balandiga tenglaydi)
    side = in_group or 'android:layout_width="0dp"' in attrs
    m = re.search(r'android:layout_height="(\d+)dp"', attrs)
    if m:
        attrs = attrs.replace(m.group(0), 'android:layout_height="%s"' % ('match_parent' if side else 'wrap_content'), 1)
        if 'android:minHeight=' not in attrs:
            add.append(f'android:minHeight="{m.group(1)}dp"')
    elif side and 'android:layout_height="wrap_content"' in attrs:
        attrs = attrs.replace('android:layout_height="wrap_content"', 'android:layout_height="match_parent"', 1)
    if 'android:singleLine=' not in attrs:
        if 'android:maxLines="1"' in attrs:
            attrs = attrs.replace('android:maxLines="1"', 'android:maxLines="2"', 1)
        elif 'android:maxLines=' not in attrs:
            add.append('android:maxLines="2"')
    if not PAD.search(attrs) and 'Widget.App.Segment' not in attrs:
        if in_group:
            add += ['android:paddingStart="6dp"', 'android:paddingEnd="6dp"']
        elif 'android:layout_width="0dp"' in attrs:
            add += ['android:paddingStart="12dp"', 'android:paddingEnd="12dp"']
    if not add:
        return attrs
    body = attrs.rstrip()
    tail = attrs[len(body):]
    return body + ''.join(f'\n{ind}{a}' for a in add) + tail


def process(text):
    out = []
    pos = 0
    for m in BTN.finditer(text):
        before = text[:m.start()]
        in_group = before.rfind(GROUP_OPEN) > before.rfind(GROUP_CLOSE)
        out.append(text[pos:m.start()])
        out.append('<com.google.android.material.button.MaterialButton' + fix_one(m.group('attrs'), in_group) + m.group('end'))
        pos = m.end()
    out.append(text[pos:])
    return ''.join(out)


def main():
    n = 0
    for f in sorted(os.listdir(LAYOUT)):
        if not f.endswith('.xml') or f in SHARED_LAYOUTS:
            continue
        p = os.path.join(LAYOUT, f)
        s = open(p, encoding='utf-8').read()
        t = process(s)
        if t != s:
            open(p, 'w', encoding='utf-8').write(t)
            n += 1
    print('fix_buttons:', n, 'fayl')


if __name__ == '__main__':
    main()
