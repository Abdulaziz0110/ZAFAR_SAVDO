#!/usr/bin/env python3
"""1-ilova: "Shtrix kodlar" sozlamasi (fragment_barcodes) maketi."""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from layout_kit import *  # noqa
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FILLED = '@style/Widget.App.Button'
TONAL = '@style/Widget.Material3.Button.TonalButton'
TEXT = '@style/Widget.Material3.Button.TextButton'

b = ''
b += section('@string/bc_list') + card_open()
b += text('tvStatus', style='@style/TextAppearance.App.ItemTitle', top='8dp')
b += text('tvStatusSub', style='?attr/textAppearanceBodySmall', color='?attr/colorOnSurfaceVariant')
b += buttons([('btnImport', '@string/b_excel', FILLED, 'ic_download'), ('btnHow', '@string/b_how', TEXT, 'ic_info')])
b += buttons([('btnLearned', '@string/bc_learned_btn', TEXT, 'ic_list'), ('btnClear', '@string/b_clear', TEXT, 'ic_delete')])
b += CARD_CLOSE
b += section('@string/bc_scale') + card_open()
b += switch_row('swScale', '@string/bc_scale_on', '@string/bc_scale_hint')
b += field('tilPrefixes', 'edtPrefixes', '@string/bc_prefixes', 'number|textNoSuggestions', '@string/bc_prefixes_helper')
b += label('@string/bc_code_len')
b += seg_group('tgCodeLen', [('code4', '@string/bc_n4'), ('code5', '@string/bc_n5'), ('code6', '@string/bc_n6')])
b += label('@string/bc_value_len')
b += seg_group('tgValueLen', [('val4', '@string/bc_n4'), ('val5', '@string/bc_n5'), ('val6', '@string/bc_n6')])
b += label('@string/bc_unit')
b += seg_group('tgUnit', [('unitG', '@string/bc_unit_g'), ('unit10', '@string/bc_unit_10g')])
b += switch_row('swCheck', '@string/bc_check', '@string/bc_check_hint')
b += switch_row('swTrust', '@string/bc_trust', '@string/bc_trust_hint')
b += CARD_CLOSE
b += section('@string/bc_test') + card_open()
b += field('tilTest', 'edtTest', '@string/bc_test_hint', 'number|textNoSuggestions')
b += text('tvTest', top='4dp')
b += buttons([('btnTestScan', '@string/bc_scan_camera', TONAL, 'ic_scan')])
b += CARD_CLOSE
b += section('@string/bc_scanner') + card_open()
b += switch_row('swBeep', '@string/bc_beep')
b += switch_row('swVibrate', '@string/bc_vibrate')
b += hint('@string/bc_hw_hint')
b += CARD_CLOSE
open(os.path.join(ROOT, 'app/src/main/res/layout/fragment_barcodes.xml'), 'w').write(screen_progress('@string/bc_title', b))
print('ok')
