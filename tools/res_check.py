#!/usr/bin/env python3
"""@string/@style/@drawable/... havolalari mavjudligini tekshiradi (AAPT o'rniga)."""
import os, re, sys, glob
import xml.etree.ElementTree as ET
res = sys.argv[1]
defined = {k: set() for k in ['string','style','color','dimen','drawable','layout','menu','xml','mipmap','id','array','bool','integer','font','anim','raw','plurals']}
for f in glob.glob(res + '/values*/*.xml'):
    try: root = ET.parse(f).getroot()
    except Exception as e: print('PARSE', f, e); continue
    for el in root:
        n = el.get('name')
        if not n: continue
        t = el.tag
        if t == 'item': t = el.get('type', 'id')
        if t == 'string-array': t = 'array'
        if t in defined: defined[t].add(n)
for d in os.listdir(res):
    base = d.split('-')[0]
    if base in ('drawable','layout','menu','xml','mipmap','font','anim','raw'):
        for f in os.listdir(os.path.join(res, d)): defined[base].add(os.path.splitext(f)[0])
LIB = ('Widget.Material3', 'TextAppearance.Material3', 'ThemeOverlay.', 'Theme.Material3', 'ShapeAppearance', 'Widget.MaterialComponents', 'TextAppearance.AppCompat', 'Widget.AppCompat')
bad = 0
for f in glob.glob(res + '/**/*.xml', recursive=True):
    s = open(f, encoding='utf-8').read()
    for m in re.finditer(r'@\+?([a-z]+)/([A-Za-z0-9_.]+)', s):
        t, n = m.group(1), m.group(2)
        if s[max(0, m.start()-8):m.start()].endswith('android:'): continue
        if t == 'id' or t not in defined: continue
        if t == 'style' and (n.startswith(LIB) or n in defined['style']): continue
        if n not in defined[t] and n != 'appbar_scrolling_view_behavior': print(f'{os.path.relpath(f, res)}: missing @{t}/{n}'); bad += 1
    for m in re.finditer(r'parent="([A-Za-z0-9_.]+)"', s):
        n = m.group(1)
        if not n.startswith(LIB) and n not in defined['style']: print(f'{os.path.relpath(f, res)}: unknown parent {n}'); bad += 1
print('missing:', bad)
