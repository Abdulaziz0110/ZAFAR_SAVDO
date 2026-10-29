#!/usr/bin/env python3
"""1-ilova "Qulayliklar" ekrani maketi (bo'limlarga ajratilgan)."""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from layout_kit import *  # noqa

TONAL = '@style/Widget.Material3.Button.TonalButton'
TEXT = '@style/Widget.Material3.Button.TextButton'
b = ''
b += section('@string/ex_sec_screen') + card_open()
b += label('@string/ex_scale', '@string/ex_scale_hint', '6dp')
b += seg_group('tgScale', [('scale90', '@string/ex_scale_small'), ('scale100', '@string/ex_scale_normal'), ('scale110', '@string/ex_scale_large'), ('scale120', '@string/ex_scale_xlarge')])
b += label('@string/ex_font', top='2dp')
b += seg_group('tgFont', [('font90', '@string/ex_font_small'), ('font100', '@string/ex_font_normal'), ('font115', '@string/ex_font_large')])
b += CARD_CLOSE
b += section('@string/ex_sale_screen') + card_open()
b += label('@string/ex_sort', top='6dp')
b += seg_group('tgSort', [('sortServer', '@string/ex_sort_server'), ('sortName', '@string/ex_sort_name'), ('sortPopular', '@string/ex_sort_popular')])
b += label('@string/ex_add', top='2dp')
b += seg_group('tgAdd', [('addPlus', '@string/ex_add_plus'), ('addAsk', '@string/ex_add_ask')])
b += switch_row('swHaptics', '@string/ex_haptics')
b += switch_row('swCostSwitch', '@string/ex_cost_switch', '@string/ex_cost_switch_hint')
b += CARD_CLOSE
b += section('@string/ex_search') + card_open()
b += switch_row('swFuzzy', '@string/ex_fuzzy')
b += switch_row('swLayout', '@string/ex_layout_fix')
b += hint('@string/ex_search_hint')
b += CARD_CLOSE
b += section('@string/ex_autosync') + card_open()
b += hint('@string/ex_autosync_hint')
b += seg_group('tgSync', [('sync0', '@string/ex_off'), ('sync1', '@string/ex_h1'), ('sync3', '@string/ex_h3'), ('sync6', '@string/ex_h6')])
b += CARD_CLOSE
b += section('@string/ex_pin') + card_open()
b += switch_row('swPin', '@string/ex_pin_on')
b += buttons([('btnPinChange', '@string/ex_pin_change', TONAL, None)])
b += label('@string/ex_pin_after', top='0dp')
b += seg_group('tgLock', [('lock0', '@string/ex_lock_now'), ('lock1', '@string/ex_lock_1'), ('lock5', '@string/ex_lock_5'), ('lock15', '@string/ex_lock_15')])
b += CARD_CLOSE
b += section('@string/ex_backup') + card_open()
b += switch_row('swAutoBackup', '@string/ex_auto_backup', '@string/ex_backup_hint')
b += buttons([('btnBackupNow', '@string/ex_backup_now', TONAL, 'ic_backup'), ('btnBackupList', '@string/ex_backup_list_short', TONAL, 'ic_restore')])
b += CARD_CLOSE
b += section('@string/ex_notify') + card_open()
b += switch_row('swReminder', '@string/ex_reminder')
b += buttons([('btnReminderTime', '@string/ex_reminder_time', TEXT, 'ic_calendar')])
b += switch_row('swQueueAlert', '@string/ex_queue_alert')
b += CARD_CLOSE
root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
open(os.path.join(root, 'app/src/main/res/layout/fragment_extras.xml'), 'w').write(screen('@string/extras_title', b))
print('ok')
