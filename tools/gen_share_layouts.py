#!/usr/bin/env python3
"""1-ilova: "Mijozlar va 2-ilova" (fragment_share) va bitta mijoz (fragment_client) maketlari."""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from layout_kit import *  # noqa
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FILLED = '@style/Widget.App.Button'
TONAL = '@style/Widget.Material3.Button.TonalButton'
TEXT = '@style/Widget.Material3.Button.TextButton'
OUT = '@style/Widget.App.Button.Outlined'

# ---- fragment_share ----
b = ''
b += section('@string/cl_title') + card_open()
b += text('tvClientsInfo', top='8dp')
b += container('clientsList')
b += buttons([('btnAddClient', '@string/b_add', TONAL, 'ic_add'), ('btnImportFile', '@string/b_excel', FILLED, 'ic_download')])
b += CARD_CLOSE
b += section('@string/pc_title') + card_open()
b += text('tvPc', top='8dp')
b += hint('@string/pc_hint')
b += seg_group('tgPc', [('pc0', '@string/sync_off'), ('pc1', '@string/pc_1h'), ('pc2', '@string/pc_2h'), ('pc3', '@string/pc_3h')])
b += buttons([('btnPcConfig', '@string/b_pc_settings', TONAL, 'ic_server'), ('btnPcHelp', '@string/b_guide', TEXT, 'ic_info')])
b += CARD_CLOSE
b += section('@string/share_status') + card_open()
b += text('tvStatus', top='8dp')
b += buttons([('btnPublish', '@string/b_send', TONAL, 'ic_sync'), ('btnHowExport', '@string/b_how', TEXT, 'ic_info')])
b += CARD_CLOSE
b += section('@string/ord_title') + card_open()
b += text('tvOrders', top='8dp')
b += buttons([('btnOrders', '@string/ord_open', TONAL, 'ic_cart')])
b += switch_row('swOrders', '@string/ord_accept', '@string/ord_accept_hint')
b += CARD_CLOSE
b += section('@string/share_sync_title') + card_open()
b += hint('@string/share_sync_hint')
b += seg_group('tgSync', [('sync0', '@string/sync_off'), ('sync30', '@string/sync_30m'), ('sync60', '@string/sync_1h'), ('sync120', '@string/sync_2h')])
b += switch_row('swAuto', '@string/share_auto')
b += switch_row('swAnswer', '@string/share_answer', '@string/share_answer_hint')
b += CARD_CLOSE
b += section('@string/share_receipt_title') + card_open()
b += field('tilHeader', 'edtHeader', '@string/share_receipt_header', 'textCapWords', '@string/share_receipt_header_hint')
b += '                    <Space android:layout_width="match_parent" android:layout_height="12dp" />\n'
b += CARD_CLOSE
b += section('@string/share_setup') + card_open()
b += text('tvShopCode', style='@style/TextAppearance.App.ItemTitle', top='8dp')
b += hint('@string/fb_shop_code_hint')
b += field('tilUrl', 'edtUrl', '@string/share_db_url', 'textUri')
b += '''                    <com.google.android.material.textfield.TextInputLayout
                        android:id="@+id/tilSecret"
                        style="@style/Widget.Material3.TextInputLayout.OutlinedBox.Dense"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginStart="16dp"
                        android:layout_marginTop="8dp"
                        android:layout_marginEnd="16dp"
                        android:hint="@string/share_db_secret"
                        app:endIconMode="password_toggle">

                        <com.google.android.material.textfield.TextInputEditText
                            android:id="@+id/edtSecret"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:inputType="textPassword" />
                    </com.google.android.material.textfield.TextInputLayout>
'''
b += buttons([('btnSaveFirebase', '@string/b_save', TONAL, None), ('btnFbGuide', '@string/b_guide', TEXT, 'ic_info')])
b += hint('@string/share_help')
b += CARD_CLOSE
open(os.path.join(ROOT, 'app/src/main/res/layout/fragment_share.xml'), 'w').write(screen_progress('@string/share_title', b))

# ---- fragment_client ----
c = ''
c += section('@string/mb_title') + card_open()
c += text('tvShop', style='@style/TextAppearance.App.ItemTitle', top='8dp')
c += hint('@string/mb_hint')
c += container('membersList')
c += buttons([('btnAddMember', '@string/mb_add', FILLED, 'ic_person')])
c += CARD_CLOSE
c += section('@string/cl_ledger') + card_open()
c += text('tvLedger', top='8dp')
c += text('tvLog', style='?attr/textAppearanceBodySmall', color='?attr/colorOnSurfaceVariant')
c += buttons([('btnHistory', '@string/share_open_history', TONAL, 'ic_history'), ('btnImport', '@string/b_excel', TONAL, 'ic_download')])
c += buttons([('btnImportServer', '@string/share_import_server_short', TEXT, 'ic_server'), ('btnClear', '@string/b_clear', TEXT, 'ic_delete')])
c += CARD_CLOSE
c += section('@string/cl_printer_title') + card_open()
c += text('tvPrinter', top='8dp')
c += hint('@string/cl_printer_hint')
c += buttons([('btnPrinter', '@string/cl_printer_on', TONAL, 'ic_print')])
c += CARD_CLOSE
c += section('@string/cl_other') + card_open()
c += label('@string/cl_kind', top='8dp')
c += seg_group('tgKind', [('kindClient', '@string/cl_kind_client'), ('kindSupplier', '@string/cl_kind_supplier')])
c += text('tvStore', top='0dp')
c += buttons([('btnLinkStore', '@string/b_link', TEXT, 'ic_store'), ('btnLinkCode', '@string/cl_link_code', TEXT, 'ic_person')])
c += buttons([('btnNewKeys', '@string/b_newkey', TEXT, 'ic_sync'), ('btnDelete', '@string/b_delete', TEXT, 'ic_delete')])
c += CARD_CLOSE
open(os.path.join(ROOT, 'app/src/main/res/layout/fragment_client.xml'), 'w').write(screen_progress('@string/cl_title', c))
print('ok')
