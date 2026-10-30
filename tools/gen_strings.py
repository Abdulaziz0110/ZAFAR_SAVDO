#!/usr/bin/env python3
"""strings.xml (lotin, asosiy) va values-b+uz+Cyrl/strings.xml (kirill, avtomatik o'giriladi) yaratadi.
Kirill matnni qo'lda tahrirlash kerak bo'lsa, shu fayldagi OVERRIDES ga yozing."""
import re, os, sys
from xml.sax.saxutils import escape

STRINGS = {
 # umumiy
 'app_name': 'Savdo Agent',
 'unit_som': "so'm", 'unit_usd': '$', 'ok': 'OK', 'cancel': 'Bekor qilish', 'save': 'Saqlash', 'close': 'Yopish', 'retry': 'Qayta urinish',
 'delete': "O'chirish", 'loading': 'Yuklanmoqda…', 'back': 'Orqaga',
 'nav_home': 'Bosh sahifa', 'nav_sale': 'Savdo', 'nav_payment': "To'lov", 'nav_history': 'Tarix', 'nav_report': 'Hisobot',
 # xatolar
 'err_not_configured': "Server sozlanmagan. Sozlamalardan server ma'lumotlarini kiriting.",
 'err_connect': "Serverga ulanib bo'lmadi. Internet va server manzilini tekshiring.",
 'err_timeout': "Server javob bermadi. Keyinroq qayta urinib ko'ring.",
 'err_query': 'Serverda xatolik yuz berdi',
 'err_unknown': 'Kutilmagan xatolik',
 'err_details': 'Tafsilot: %1$s',
 # kirish
 'login_title': 'Tizimga kirish', 'login_subtitle': 'Agent login va parolini kiriting',
 'phone_label': 'Login', 'password_label': 'Parol', 'login_button': 'Kirish', 'login_server': 'Server sozlamalari',
 'login_error_credentials': "Login yoki parol noto'g'ri", 'login_error_phone': 'Loginni kiriting',
 'login_error_password': 'Parolni kiriting', 'login_server_missing': 'Avval server sozlamalarini kiriting',
 # bosh sahifa
 'home_greeting': 'Salom, %1$s', 'home_rate': '1 $ = %1$s', 'home_rate_missing': 'Kurs yuklanmagan',
 'home_last_sync': 'Yangilangan: %1$s', 'home_never_synced': 'Hali yangilanmagan',
 'home_new_sale': 'Yangi savdo', 'home_return': 'Qaytarib olish', 'home_payment': "To'lov qabul qilish", 'home_report': 'Hisobot',
 'home_resume_title': 'Tugallanmagan savdo', 'home_resume': 'Davom etish', 'home_resume_info': '%1$d ta tovar · %2$s',
 'home_today': 'Bugun', 'home_today_orders': 'Buyurtmalar: %1$d ta · %2$s', 'home_today_payments': "To'lovlar: %1$d ta · %2$s",
 'home_no_data_title': "Ma'lumotlar yuklanmagan", 'home_no_data_text': "Tovarlar va do'konlarni serverdan yuklang.",
 'home_load': 'Yuklash', 'sync_ok': "Ma'lumotlar yangilandi", 'sync_failed': "Yangilab bo'lmadi. Avvalgi ma'lumot saqlandi.",
 'menu_sync': 'Yangilash', 'menu_settings': 'Sozlamalar',
 # savdo
 'sale_title': 'Savdo', 'return_title': 'Qaytarib olish', 'sale_select_store': "Do'kon tanlang",
 'type_cash': 'Naqd', 'type_wholesale': 'Optom',
 'currency_som': "So'm", 'currency_usd': 'Dollar', 'mode_sale': 'Sotuv', 'mode_return': 'Qaytarish',
 'search_hint': 'Tovar nomi yoki kodi', 'group_all': 'Hammasi', 'sale_empty_products': 'Tovar topilmadi',
 'sale_no_store': "Savdoni boshlash uchun do'kon tanlang", 'sale_items_count': '%1$d ta tovar', 'sale_cart': 'Savatcha',
 'block_info': 'Blok: %1$s',
 'reprice_title': 'Narxlar qayta hisoblanadi',
 'reprice_message': 'Savatchadagi narxlar yangi savdo turiga qarab qayta hisoblanadi. Davom etasizmi?',
 'clear_cart_title': 'Savatchani tozalash', 'clear_cart_message': "Savatchadagi barcha tovarlar o'chiriladi.",
 'rate_missing_warning': "Kurs yuklanmagan: narxlar noto'g'ri bo'lishi mumkin. Ma'lumotlarni yangilang.",
 'sale_debt': 'Qarz: %1$s',
 # do'kon tanlash
 'store_picker_title': "Do'kon tanlash", 'store_search_hint': "Do'kon yoki hudud nomi",
 'store_debt': 'Qarz: %1$s', 'store_empty': "Do'kon topilmadi", 'store_no_region': 'Hududsiz',
 # tovar oynasi
 'item_price': 'Narx (%1$s)', 'item_qty': 'Soni (dona)', 'item_blocks': 'Blok soni', 'item_total': 'Summa: %1$s',
 'item_save_add': "Savatchaga qo'shish", 'item_save_update': 'Saqlash', 'item_remove': 'Olib tashlash',
 'item_error_qty': 'Sonini kiriting', 'item_error_price': 'Narxni kiriting',
 # savatcha
 'cart_title': 'Savatcha', 'cart_empty': "Savatcha bo'sh", 'cart_line': '%1$s × %2$s', 'cart_checkout': 'Rasmiylashtirish',
 'cart_deleted': "Olib tashlandi", 'cart_undo': 'Qaytarish',
 # yakunlash
 'finish_title': 'Buyurtmani yakunlash', 'finish_total': 'Jami', 'finish_discount_percent': 'Chegirma (%)',
 'finish_discount_amount': 'Chegirma summasi', 'finish_payment': "To'lov", 'finish_cash_som': "Naqd (so'm)",
 'finish_cash_usd': 'Naqd ($)', 'finish_card': "Plastik (so'm)", 'finish_fill_all': "To'liq to'lash",
 'finish_printer': 'Chek printeri', 'finish_printer_n': 'Printer %1$d', 'finish_payable': "To'lanishi kerak",
 'finish_paid': "To'landi", 'finish_debt': 'Qarzga qoladi', 'finish_overpaid': "Ortiqcha to'lov",
 'finish_send': 'Buyurtmani yuborish', 'finish_sent': 'Buyurtma yuborildi',
 'finish_failed': "Yuborib bo'lmadi. Buyurtma savatchada saqlandi.",
 'finish_overpay_title': "To'lov summasi ortiqcha",
 'finish_overpay_message': "Kiritilgan to'lov buyurtma summasidan ortiq. Baribir yuborasizmi?", 'finish_send_anyway': 'Yuborish',
 # to'lov
 'payment_title': "To'lov qabul qilish", 'payment_cash': "Naqd (so'm)", 'payment_transfer': "Pul o'tkazma (so'm)",
 'payment_click': "{{Click}} (so'm)", 'payment_note': 'Izoh', 'payment_debt_title': "Do'kon qarzi",
 'payment_debt_refresh': 'Qarzni yangilash', 'payment_debt_som': "So'mda: %1$s", 'payment_debt_usd': 'Dollarda: %1$s $',
 'payment_total': "Jami to'lov: %1$s", 'payment_send': "To'lovni yuborish", 'payment_sent': "To'lov yuborildi",
 'payment_failed': "To'lovni yuborib bo'lmadi", 'payment_error_store': "Do'kon tanlang",
 'payment_error_amount': "To'lov summasini kiriting", 'payment_debt_failed': "Qarzni yangilab bo'lmadi",
 'payment_debt_unknown': "Qarz hali so'ralmagan",
 # hisobot
 'report_title': 'Hisobot', 'report_period': 'Davr', 'report_show': "Ko'rish", 'report_error_store': "Do'kon tanlang",
 'report_empty': "Hisobot bo'sh", 'report_loading': 'Hisobot yuklanmoqda…', 'report_pick_period': 'Davrni tanlang',
 'report_failed': "Hisobotni yuklab bo'lmadi",
 # tarix
 'history_title': 'Tarix', 'history_orders': 'Buyurtmalar', 'history_payments': "To'lovlar",
 'history_empty': "Hozircha bo'sh", 'history_items': '%1$d ta tovar', 'history_discount': 'Chegirma: %1$s',
 'history_paid': "To'langan: %1$s", 'history_detail_title': 'Buyurtma tafsilotlari', 'history_return': 'Qaytarish',
 'history_payment_info': "Naqd: %1$s · O'tkazma: %2$s · {{Click}}: %3$s",
 # sozlamalar
 'settings_title': 'Sozlamalar', 'settings_account': 'Hisob', 'settings_logout': 'Chiqish',
 'settings_logout_title': 'Hisobdan chiqish', 'settings_logout_message': 'Savatcha tozalanadi. Davom etasizmi?',
 'settings_server': 'Server', 'settings_server_edit': 'Serverni sozlash', 'settings_server_none': 'Sozlanmagan',
 'settings_language': 'Til', 'settings_lang_latin': 'Lotin', 'settings_lang_cyrillic': '{{Кирилл}}',
 'settings_theme': 'Mavzu', 'settings_theme_system': 'Tizim', 'settings_theme_light': "Yorug'", 'settings_theme_dark': "Qorong'i",
 'settings_backup': 'Zaxira nusxa',
 'settings_backup_hint': "Yangi imzo bilan o'rnatishdan oldin ma'lumotni saqlab qo'ying. Server paroli zaxiraga kiritilmaydi.",
 'settings_export': 'Saqlash', 'settings_import': 'Tiklash', 'settings_about': 'Ilova haqida', 'settings_version': 'Versiya %1$s',
 'backup_done': 'Zaxira nusxa saqlandi', 'backup_failed': "Zaxira nusxani saqlab bo'lmadi",
 'restore_done': "Ma'lumot tiklandi", 'restore_failed': "Tiklab bo'lmadi: fayl noto'g'ri", 'restore_title': "Ma'lumotni tiklash",
 'restore_message': "Hozirgi ma'lumot zaxira bilan almashtiriladi. Davom etasizmi?",
 # server sozlamalari
 'server_title': 'Server sozlamalari', 'server_host': 'Server manzili ({{IP:port}})', 'server_db': 'Baza nomi',
 'server_login': 'Login', 'server_password': 'Parol', 'server_test': 'Ulanishni tekshirish', 'server_save': 'Saqlash',
 'server_test_ok': 'Ulanish muvaffaqiyatli', 'server_saved': 'Saqlandi', 'server_error_empty': "To'ldiring",
 # --- 3-versiya qo'shimchalari
 'sale_cost_switch': 'Kelgan narx', 'meta_cost': 'Kelgan: %1$s', 'filter_frequent': 'Taklif', 'filter_in_cart': 'Savatchada',
 'sale_repeat_last': 'Oxirgi buyurtmani takrorlash', 'sale_repeat_none': "Bu do'konga oldingi buyurtma topilmadi",
 'sale_repeat_done': "Oxirgi buyurtma savatchaga qo'shildi: %1$d ta tovar", 'sale_clear': 'Savatchani tozalash',
 'frequent_empty': "Taklif hali yo'q: bu do'kon bilan bir necha buyurtmadan keyin shu yerda chiqadi",
 'incart_empty': "Savatchada tovar yo'q",
 'item_quick_block': '+ blok', 'item_cost': 'Kelgan narx: %1$s',
 'finish_round': 'Yaxlitlash', 'finish_store_debt': "Do'kon qarzi: %1$s", 'finish_queued': 'Buyurtma telefonda saqlandi, yuborish navbatida',
 'payment_queued': "To'lov telefonda saqlandi, yuborish navbatida",
 'home_pending_title': 'Yuborilmagan: %1$d ta', 'home_pending_retry': 'Hozir yuborish',
 'home_pending_hint': "Internet paydo bo'lganda o'zi yuboriladi (ilova yopiq bo'lsa ham). Batareya tejash kechiktirishi mumkin.",
 'history_pending': 'Kutilmoqda', 'history_share': 'Ulashish', 'share_chooser': 'Ulashish',
 'share_total': 'Jami: %1$s', 'share_discount': 'Chegirma: %1$s', 'share_paid': "To'langan: %1$s",
 'store_debtors_only': 'Faqat qarzdorlar', 'store_recent': "So'nggi do'konlar",
 'settings_keep_screen': "Ekran o'chmasin", 'settings_keep_screen_hint': "Ilova ochiq turganda ekran o'chib qolmaydi",
 'settings_general': 'Umumiy',
 'meta_usual': 'Odatda: %1$s', 'sale_suggest_bar': "Mijoz odatda oladigan tovarlar: %1$d ta", 'sale_suggest_add_all': "Hammasini qo'shish",
 'sale_other_store': "Boshqa do'kon tanlash",
 'settings_favorites': "Tez do'konlar", 'settings_favorites_hint': "Savdoga kirganda shu do'konlar tez tanlash uchun chiqib turadi (ko'pi bilan 2 ta).",
 'settings_fav_empty': "Do'kon tanlash", 'report_print': 'PDF / Chop etish',
 'server_remote_host': 'Tashqi manzil (masofadan, ixtiyoriy)',
 'server_remote_hint': "Do'kondan tashqarida ishlash uchun (masalan VPN manzili). Do'kon tarmog'ida bo'lsangiz yuqoridagi manzil ishlatiladi, ikkalasi o'zi tanlanadi.",
 'server_test_ok_via': 'Ulanish muvaffaqiyatli: %1$s',
}

# --- v2.3 (APK-only kuchaytirish) ---
STRINGS.update({
 'amount_invalid': "Summa noto'g'ri yozilgan. Masalan: 1250000 yoki 1 250 000",
 'st_ready': 'Telefonda saqlandi, navbatda',
 'st_sending': 'Yuborilmoqda…',
 'st_retry': 'Internet kutilmoqda (qayta uriniladi)',
 'st_uncertain': "Yuborilgan bo'lishi mumkin — tekshirilmoqda",
 'st_sent': 'Serverga yozildi',
 'st_needs_check': 'Tekshirish kerak',
 'st_sent_manual': "Serverda bor (qo'lda belgilangan)",
 'reason_probe_unreliable': 'Serverga yetib bordimi — aniq emas. Ikki marta tushmasligi uchun o\'zi qayta yuborilmaydi. Ofisdan so\'rang yoki "Serverda bormi tekshir" ni bosing.',
 'reason_restored': "Zaxiradan tiklangan: ilgari yuborilgan bo'lishi mumkin, shuning uchun o'zi yuborilmaydi.",
 'reason_rejected': "Server bu so'rovni bir necha marta qabul qilmadi. Ofisga xabar bering.",
 'reason_probe_failed': "Serverda tekshirib bo'lmadi (ulanish yo'q). Keyinroq qayta tekshiring.",
 'status_offline': "Internet yo'q — ishlash davom etadi, ma'lumot telefonda saqlanadi",
 'status_offline_n': "Internet yo'q · yuborilmagan: %1$d ta",
 'status_server_n': "Server ko'rinmayapti · yuborilmagan: %1$d ta (bosing)",
 'status_needs_check': 'Tekshirish kerak: %1$d ta (bosing)',
 'queue_title': 'Navbat',
 'queue_empty': "Navbat bo'sh",
 'queue_hint': 'Har bir buyurtma va to\'lovning kodi va holati. "Serverga yozildi" — so\'rov server jadvaliga tushdi; uni ofis dasturi qayta ishlagani bu ilovaga ko\'rinmaydi.',
 'queue_kind_order': 'Buyurtma',
 'queue_kind_payment': "To'lov",
 'queue_sent_at': 'yozildi: %1$s',
 'queue_server_id': 'Server {{ID}}: %1$s',
 'queue_check': 'Serverda bormi tekshir',
 'queue_more': 'Boshqa…',
 'queue_found': 'Serverda topildi — qayta yuborilmaydi',
 'queue_not_found': 'Serverda topilmadi. Ofis bilan gaplashib, keyin qaror qiling.',
 'queue_force_resend': 'Baribir qayta yuborish',
 'queue_force_confirm': 'Ha, qayta yubor',
 'queue_force_warning': 'DIQQAT: agar oldingi yuborish aslida serverga yetib borgan bo\'lsa, buyurtma IKKI MARTA tushadi. Faqat ofis "bizda yo\'q" desa bosing.',
 'queue_mark_sent': 'Serverda bor deb belgilash',
 'queue_mark_sent_warning': 'Faqat ofis "bu bizda bor" deb tasdiqlagan bo\'lsa belgilang. Belgilangandan keyin bu yozuv boshqa yuborilmaydi.',
 'home_pending_needs_check': 'Tekshirish kerak: %1$d ta. "Navbat" ni oching.',
 'diag_title': 'Diagnostika',
 'diag_again': 'Qayta tekshirish',
 'diag_battery_button': "Batareya tejashni o'chirish",
 'diag_v_ok': "Hammasi joyida: server ko'rinadi.",
 'diag_v_no_internet': "Telefonda internet yo'q. {{Wi-Fi}} yoki mobil internetni yoqing.",
 'diag_v_not_configured': 'Server sozlanmagan. Sozlamalar ▸ Server.',
 'diag_v_vpn_off': "Server ko'rinmayapti. Do'kondan tashqaridasiz — VPN ({{Tailscale}}) yoqilganini tekshiring.",
 'diag_v_server_off': "Server ko'rinmayapti. Do'kondagi kompyuter o'chiq yoki tarmoqdan uzilgan bo'lishi mumkin.",
 'diag_v_login_failed': "Serverga yetib bordik, lekin kirish yoki so'rov o'tmadi. Ofisga xabar bering.",
 'diag_internet': 'Internet',
 'diag_vpn': '{{VPN}} yoqilgan',
 'diag_local_port': "Do'kon manzili (port ochiq)",
 'diag_remote_port': 'Tashqi manzil (port ochiq)',
 'diag_sql': 'Serverga kirish ({{SQL}})',
 'diag_clock': 'Telefon soati: server bilan farq %1$s daqiqa',
 'diag_queue': 'Navbat: %1$d ta yuborilmagan, %2$d ta tekshirish kerak, %3$d ta kutilmoqda',
 'diag_last_sent': 'Oxirgi muvaffaqiyatli yuborish: %1$s',
 'diag_last_error': 'Oxirgi xato: %1$s',
 'diag_battery': "Batareya tejash yoqilgan: ilova yopiq bo'lsa yuborish kechikishi mumkin.",
 'diag_db_bad': "Telefondagi baza tekshiruvdan o'tmadi. Zaxira nusxa oling va ofisga xabar bering.",
 'diag_write_error': 'Savatchani saqlashda xato: %1$s',
 'diag_bg_error': 'Fon xatosi: %1$s',
 'diag_legacy': "Eski ilovadan qolgan yuborilmagan yozuv: %1$d ta (o'zi yuborilmaydi, ofisga ayting)",
 'debt_source_server': 'Serverdan, %1$s',
 'debt_source_old': "Oxirgi ma'lum qiymat: %1$s (eskirgan bo'lishi mumkin)",
 'payment_unsent': "Hali serverga yozilmagan to'lovlar: %1$d ta · %2$s (qarzga qo'shilmagan)",
 'payment_full_debt': 'Butun qarz',
 'payment_queued_receipt': "To'lov telefonda saqlandi (%1$s), yuborish navbatida",
 'dup_payment_title': "Takroriy to'lovmi?",
 'dup_payment_message': "Shu do'konga aynan shu summa yaqinda (15 daqiqa ichida) kiritilgan. Rostdan ham yana bir to'lovmi?",
 'dup_payment_send': "Ha, yangi to'lov",
 'finish_queued_receipt': 'Buyurtma telefonda saqlandi (%1$s), yuborish navbatida',
 'check_keep': 'Shunday qoldir',
 'check_big_qty': '%1$s: %2$s ta — odatda ~%3$s ta. Xato emasmi?',
 'check_price': '%1$s: narx %2$s — oxirgi marta %3$s edi.',
 'check_missing': "Odatda oladi, savatchada yo'q: %1$s",
 'smart_pick_store': "Do'kon tanlash",
 'smart_add_usual': "Odatdagini qo'sh (%1$d)",
 'smart_finish': 'Yakunlash',
 'smart_payment': "To'lov qabul qilish (%1$s)",
 'usual_added': "%1$d ta tovar qo'shildi",
 'cart_cleared': 'Savatcha tozalandi',
 'item_changed': "O'zgartirildi",
 'sale_repeat_missing': "%1$d ta tovar katalogda yo'q — qo'shilmadi",
 'park_action': "Kutishga qo'yish",
 'park_done': "Savatcha kutishga qo'yildi",
 'parked_chip': 'Kutishda: %1$d',
 'parked_title': 'Kutishdagi savatchalar',
 'parked_empty': "Kutishdagi savatcha yo'q",
 'parked_deleted': "Savatcha o'chirildi",
 'parked_swap': 'Almashtirish',
 'parked_swap_message': "Hozirgi savatcha (%1$s) kutishga qo'yiladi va bu savatcha ochiladi.",
 'parked_missing': "%1$d ta tovar katalogda yo'q — tekshiring",
 'item_last_price': "Shu do'konga oxirgi narx: %1$s (%2$s)",
 'fb_not_needed': 'Kerak emas',
 'fb_no_stock': "Omborda yo'q",
 'fb_later': 'Keyinroq',
 'sug_explain': 'Odatda ~%1$d kunda ~%2$s · oxirgisi %3$d kun oldin',
 'sug_explain_rough': 'Taxminiy: ~%1$d kunda ~%2$s · oxirgisi %3$d kun oldin',
 'sug_once': 'Taxminiy: oldin %1$s olgan',
 'sug_popular': "Taxminiy: boshqa do'konlar ~%1$s oladi",
 'card_start': 'Boshlash',
 'card_debt': 'Qarz: %1$s',
 'card_unsent_payments': "Yuborilmagan to'lov: %1$d ta (qarzga qo'shilmagan)",
 'card_last_order': 'Oxirgi buyurtma: %1$s · %2$s',
 'card_no_history': "Bu telefonda tarix yo'q",
 'card_due': 'Vaqti kelgan: %1$s',
 'report_source_server': 'Serverdan',
 'report_source_local': 'Lokal (telefon)',
 'report_all_stores': "Barcha do'konlar",
 'report_cancel': "To'xtatish",
 'report_server_badge': 'Serverdan olingan: %1$s',
 'report_local_badge': "Lokal — faqat shu telefondagi yozuvlar. Serverdagi haqiqiy holat boshqacha bo'lishi mumkin.",
 'dayend_title': 'Kun yakuni',
 'dayend_prev': '‹ Oldingi',
 'dayend_next': 'Keyingi ›',
 'dayend_sales': 'Sotuv',
 'dayend_returns': 'Qaytarish',
 'dayend_paid_in_orders': "Buyurtmada to'langan",
 'dayend_payments': "To'lovlar",
 'dayend_unsent': "Yuborilmagan (buyurtma / to'lov)",
 'dayend_by_store': "Do'konlar bo'yicha",
 'dayend_store': "Do'kon",
 'csv_orders': 'Buyurtmalar {{CSV}}',
 'csv_payments': "To'lovlar {{CSV}}",
 'csv_saved': 'Fayl saqlandi',
 'csv_failed': "Faylni saqlab bo'lmadi",
 'backup_password_title': 'Zaxira paroli (ixtiyoriy)',
 'backup_password_hint': "Bo'sh qoldirsangiz — parolsiz. Parol hech qayerda saqlanmaydi: unutsangiz, faylni ochib bo'lmaydi.",
 'restore_password_title': 'Zaxira paroli',
 'restore_password_hint': 'Zaxira yaratilgandagi parol',
 'restore_preview': 'Zaxira sanasi: %1$s\nTovarlar: %2$d, do\'konlar: %3$d\nBuyurtmalar: %4$d, to\'lovlar: %5$d\nYuborilmagan: %6$d ta — tiklangandan keyin O\'ZI YUBORILMAYDI, "Tekshirish kerak" bo\'ladi.\n\nJoriy ma\'lumot almashadi (avval telefonga nusxasi olinadi).',
 'restore_needs_check': '%1$d ta yozuv "Tekshirish kerak": Navbatda ko\'ring',
 'backup_err_password': "Parol noto'g'ri",
 'backup_err_not_backup': 'Bu zaxira fayli emas',
 'backup_err_too_new': 'Bu zaxira ilovaning yangiroq versiyasida qilingan. Avval ilovani yangilang.',
 'backup_err_corrupt': "Zaxira fayli buzilgan. Joriy ma'lumot o'zgarmadi.",
 'backup_err_disk_full': 'Telefonda joy yetarli emas',
 'settings_reliability': 'Ishonchlilik',
 'settings_features': "Qo'shimcha imkoniyatlar",
 'settings_features_hint': "Har birini o'chirib qo'yish mumkin. O'chirilsa, ilova avvalgidek ishlaydi.",
 'f_store_card': "Do'kon tanlanganda xulosa kartasi",
 'f_smart_button': 'Aqlli asosiy tugma (pastda)',
 'f_explain': 'Takliflarda izoh ("Odatda ~7 kunda")',
 'f_checker': 'Savatcha tekshiruvchisi (faqat ogohlantirish)',
 'f_multicart': "Ko'p savatcha (kutishga qo'yish)",
 'f_dup_payment': "Takroriy to'lovdan ogohlantirish",
 'f_kpi': "Tezlik o'lchagich ({{KPI}})",
 'f_kpi_hint': 'Buyurtmaga ketgan vaqt va bosishlar soni (faqat shu telefonda)',
 'kpi_empty': "Hali o'lchov yo'q",
 'kpi_summary': "Oxirgi %1$d ta buyurtma: o'rtacha (mediana) %2$d soniya, %3$d bosish",
 'server_password_saved': "Parol saqlangan. O'zgartirish uchun yangisini yozing, aks holda bo'sh qoldiring.",
})

# --- v2.4 (2-ilova bilan ulashish, qidiruv, takrorlash, qulayliklar) ---
STRINGS.update({
 'settings_more_title': "Qo'shimcha",
 'extras_title': 'Qulayliklar',
 'remote_help_title': 'Masofadan ishlash ({{Wi-Fi}}siz)',
 'remote_help_text': "Do'kondan tashqarida (mobil internetda) savdo qilish uchun bepul {{Tailscale}} ishlatiladi:\n\n1) Server kompyuteriga {{tailscale.com}} dan {{Tailscale}} o'rnating va Gmail bilan kiring.\n2) Agent telefonlariga Play Market'dan {{Tailscale}} o'rnatib, XUDDI SHU Gmail bilan kiring.\n3) Kompyuterdagi {{Tailscale}} oynasida ko'rsatilgan {{100.x.x.x}} manzilni ko'chiring.\n4) Sozlamalar ▸ Server ▸ \"Tashqi manzil\" ga shu manzilni yozing.\n\nIlova avval do'kon manzilini, ulanmasa tashqi manzilni o'zi sinaydi. Batafsil: {{EGA_QOLLANMA}} ▸ (d).",
 'search_approx': "Taxminiy natijalar (imlo yoki klaviatura tuzatildi)",
 # takrorlash
 'repeat_action': 'Takrorlash',
 'repeat_title': 'Savatchada tovar bor',
 'repeat_replace': "Joriy savatcha (%1$d ta) o'rniga",
 'repeat_add': "Joriy savatchaga qo'shish",
 'repeat_park': "Joriyni kutishga qo'yib, yangisini ochish",
 'repeat_nothing': "Takrorlab bo'lmadi: tovarlar katalogda yo'q",
 'repeat_done': "Savatchaga %1$d ta tovar qo'shildi (joriy narxlarda)",
 'repeat_price_changed': "%1$d ta tovar narxi o'zgargan",
 # PIN
 'pin_enter': 'PIN kodni kiriting',
 'pin_unlock': 'Ochish',
 'pin_forgot': 'PIN esimda yo\'q',
 'pin_wait': '%1$d soniyadan keyin urinib ko\'ring',
 'pin_wrong': "PIN noto'g'ri",
 'pin_forgot_msg': "Agent parolini kiriting. U serverda tekshiriladi va to'g'ri bo'lsa PIN o'chiriladi.",
 'pin_forgot_fail': "Parol noto'g'ri yoki server ko'rinmayapti",
 # bildirishnomalar
 'notif_channel': 'Eslatmalar',
 'notif_queue_title': 'Yuborilmagan buyurtmalar bor',
 'notif_queue_text': "%1$d ta yozuv serverga yetib bormagan (eng eskisi %2$d daqiqa). Internet va serverni tekshiring.",
 'notif_dayend_title': 'Kun yakuni',
 'notif_dayend_text': "Bugun: %1$d ta buyurtma (%2$s so'm), %3$d ta to'lov. Yuborilmagan: %4$d ta.",
 # qulayliklar
 'ex_scale': "Ekran o'lchami",
 'ex_scale_hint': "Tugmalar, ro'yxatlar va matnlar kattaligi (faqat shu ilovada).",
 'ex_scale_small': '90%', 'ex_scale_compact': '92%', 'ex_scale_normal': '100%', 'ex_scale_large': '110%',
 'ex_font': 'Shrift',
 'ex_font_small': '90%', 'ex_font_normal': '100%', 'ex_font_large': '115%',
 'ex_search': 'Aqlli qidiruv',
 'ex_fuzzy': "Imlo xatolarini kechirish (\"shakolad\" → Shokolad)",
 'ex_layout_fix': "Klaviatura tili adashsa ham topish (\"ьфнша\" → mayiz)",
 'ex_search_hint': "Bir nechta so'z istalgan tartibda, lotin/kirill, kod bo'yicha; topilgan qism qalin ko'rsatiladi.",
 'ex_sort': "Tovarlar tartibi (qidiruvsiz)",
 'ex_sort_server': 'Server', 'ex_sort_name': 'Nomi', 'ex_sort_popular': "Ko'p sotilgan",
 'ex_add': '"+" tugmasi',
 'ex_add_plus': "Darhol qo'shish", 'ex_add_ask': "Miqdor so'rash",
 'ex_sale_screen': 'Savdo ekrani',
 'ex_haptics': 'Bosganda tebranish',
 'ex_cost_switch': "\"Kelgan narx\" tugmasi ko'rinsin",
 'ex_autosync': 'Avtomatik yangilash',
 'ex_autosync_hint': "Ilova ochilganda ma'lumot shuncha vaqtdan eski bo'lsa o'zi yangilanadi.",
 'ex_off': "O'chiq", 'ex_h1': '1 soat', 'ex_h3': '3 soat', 'ex_h6': '6 soat',
 'ex_pin': 'PIN qulf',
 'ex_pin_on': 'Ilovani PIN bilan qulflash',
 'ex_pin_change': "PIN'ni o'zgartirish",
 'ex_pin_after': "Fonda shuncha turgandan keyin so'raladi:",
 'ex_lock_now': 'Har safar', 'ex_lock_1': '1 daq', 'ex_lock_5': '5 daq', 'ex_lock_15': '15 daq',
 'ex_pin_new': 'Yangi PIN (4–8 raqam)',
 'ex_pin_repeat': 'PIN (qayta)',
 'ex_pin_short': "PIN kamida 4 raqam bo'lsin",
 'ex_pin_mismatch': 'PIN lar bir xil emas',
 'ex_pin_set': "PIN o'rnatildi",
 'ex_pin_off': "PIN'ni o'chirish",
 'ex_pin_current': 'Hozirgi PIN',
 'ex_pin_removed': "PIN o'chirildi",
 'ex_backup': 'Avtomatik zaxira',
 'ex_auto_backup': 'Har kuni zaxira nusxa olish',
 'ex_backup_hint': "Telefon ichida saqlanadi (oxirgi 7 ta). Telefon yo'qolishidan himoya qilmaydi — buning uchun Sozlamalar ▸ Zaxira nusxa saqlash.",
 'ex_backup_now': 'Zaxira olish',
 'ex_backup_list': 'Avto-zaxiralardan tiklash',
 'ex_backup_none': "Avto-zaxira hali yo'q",
 'ex_backup_item': '%1$s · %2$d KB',
 'ex_notify': 'Eslatmalar',
 'ex_reminder': 'Kun yakuni eslatmasi',
 'ex_reminder_time': 'Eslatma vaqti',
 'ex_reminder_time_v': 'Vaqt: %1$s',
 'ex_queue_alert': "Yuborilmagan buyurtma 30 daqiqadan ko'p tursa ogohlantirish",
 'ex_notify_denied': "Bildirishnomaga ruxsat berilmadi",
 # 2-ilova bilan ulashish
 'share_title': 'Mijoz ilovasi (2-ilova)',
 'share_status': 'Holat',
 'share_publish_now': '2-ilovaga hozir yuborish',
 'share_link_code': 'Ulanish kodini yuborish',
 'share_reports': 'Hisobotlar (1C)',
 'share_import_file': 'Excel faylni yuklash (.xlsx / .xls)',
 'share_import_server': 'Serverdan olish (davr tanlab)',
 'share_how_export': "1C'dan faylni qanday olaman?",
 'share_how_export_text': "1) 1C'da mijoz kartochkasini oching (masalan Dadaxon Aka), davrni tanlab \"Ko'rsatish\" ni bosing.\n2) Файл ▸ Сохранить как.\n3) Fayl turi: Excel (*.xlsx yoki *.xls). Nom bering va saqlang. (.mxl — 1C'ning o'z formati, o'qilmaydi.)\n4) Faylni telefonga o'tkazing (Telegram \"Saved messages\", kabel yoki fleshka).\n5) Shu yerda \"Fayldan import qilish\" ni bosib faylni tanlang.\n\nBir xil davrni qayta import qilsangiz dublikat bo'lmaydi: fayl davridagi eski yozuvlar yangisi bilan almashtiriladi. Har kuni yoki har hafta oxirgi kunlarni yuborish yetarli.",
 'share_setup': 'Sozlash',
 'share_pick_client': 'Mijozni tanlang',
 'share_db_url': '{{Firebase}} manzili ({{https://...firebaseio.com}})',
 'share_db_secret': '{{Database secret}}',
 'share_save_check': 'Saqlash va tekshirish',
 'share_auto': "Yangi ma'lumotni darhol 2-ilovaga yuborsin",
 'share_answer': "2-ilova so'rasa narx va qarzni yangilash",
 'share_answer_hint': "Server ko'ringanda bajariladi (do'kon Wi-Fi yoki VPN). Excel ma'lumoti esa baribir darhol boradi.",
 'share_pc_config': 'Kompyuter uchun chek sozlamasi',
 'share_pc_config_text': "Bu matnni kompyuterdagi forwarder papkasiga \"forwarder.ini\" nomi bilan saqlang. Ichida maxfiy kalit bor — faqat o'z kompyuteringizga yuboring!",
 'share_new_keys': 'Kalitlarni almashtirish',
 'share_new_keys_msg': "Eski ulanish kodi ishlamay qoladi (masalan telefon yo'qolsa). Keyin yangi kodni 2-ilovaga, yangi sozlamani kompyuterga yuborasiz.",
 'share_new_keys_done': 'Kalitlar almashtirildi. Yangi kodni yuboring.',
 'share_clear': "Import qilingan hisobotlarni o'chirish",
 'share_clear_msg': "Shu mijoz uchun import qilingan barcha yozuvlar telefondan o'chiriladi (1C'dagi ma'lumotga tegilmaydi).",
 'share_help': "Ma'lumot bepul {{Google Firebase}} orqali boradi. 2-ilovaga faqat sotish narxlari, xaridlar, to'lovlar va qarz yuboriladi — kelgan narx va server paroli yuborilmaydi. Sozlash: {{EGA_QOLLANMA}} ▸ (e).",
 'share_change_client_title': "Mijozni almashtirish",
 'share_change_client_msg': "%1$s o'rniga %2$s tanlanadi. Eski ulanish kodi ishlamay qoladi — 2-ilovaga yangi kod yuborish kerak.",
 'share_need_client': 'Avval mijozni tanlang',
 'share_st_no_client': 'Mijoz tanlanmagan',
 'share_st_client': 'Mijoz: %1$s',
 'share_st_no_firebase': "{{Firebase}} sozlanmagan — pastdagi \"Sozlash\" bo'limi",
 'share_st_published': "2-ilovaga oxirgi yuborish: %1$s",
 'share_st_never': 'Hali yuborilmagan',
 'share_st_error': 'Oxirgi xato: %1$s',
 'share_st_dirty': "Yangi ma'lumot bor — hali yuborilmagan",
 'share_ledger_empty': "Hali hisobot import qilinmagan",
 'share_ledger_info': '%1$d ta yozuv · davrlar: %2$s',
 'share_log_line': '%1$s · %2$s · +%3$d / −%4$d',
 'share_src_server': 'serverdan',
 'share_secret_saved': 'Saqlangan (o\'zgartirish uchun yangisini yozing)',
 'share_printer_never': "2-filial printeri: 2-ilova hali ulanmagan · navbatda %1$d ta chek",
 'share_printer_ready': "2-filial printeri: ulangan · %1$s · navbatda %2$d ta chek",
 'share_printer_not_ready': "2-filial printeri: ulanmagan · oxirgi faollik %1$s · navbatda %2$d ta chek",
 'share_err_url': "{{Firebase}} manzili noto'g'ri. Masalan: {{https://imsoft-12345-default-rtdb.firebaseio.com}}",
 'share_err_secret': "{{Database secret}} ni kiriting",
 'share_firebase_ok': "{{Firebase}} tayyor: qoidalar o'rnatildi",
 'share_firebase_fail': "{{Firebase}} sozlanmadi",
 'share_err_denied': "Ruxsat yo'q: secret noto'g'ri",
 'share_err_404': "Baza topilmadi: manzilni tekshiring",
 'share_err_rules': "Qoidalar ishlamadi: baza hammaga ochiq",
 'share_err_net': "Internetga ulanib bo'lmadi",
 'share_published': "2-ilovaga yuborildi",
 'share_publish_failed': "Yuborilmadi — holatdagi xatoni ko'ring",
 'share_link_message': "Savdo Mijoz ilovasi uchun ulanish kodi (%1$s). 2-ilovada \"Kodni joylash\" ni bosing:\n%2$s",
 'share_link_explain': "Kod 2-ilova telefoniga Telegram yoki SMS orqali yuboriladi. Ichida maxfiy kalit yo'q: kod bilan faqat shu mijoz ma'lumotini o'qish va chek olish mumkin.",
 'share_send': 'Yuborish',
 'share_copy': 'Nusxalash',
 'share_copied': 'Nusxalandi',
 'share_err_mxl': "Bu 1C'ning o'z formati (.mxl). 1C'da Файл ▸ Сохранить как ▸ turini Excel (*.xlsx) qilib saqlang.",
 'share_err_xlsx': "Excel faylini o'qib bo'lmadi. Uni Excel'da ochib qayta saqlang yoki 1C'dan qayta oling.",
 'share_pv_opening': 'Boshlang\'ich qoldiq (%1$s): %2$s',
 'share_pv_closing': 'Yakuniy qoldiq (%1$s oxiriga): %2$s',
 'share_import_fail': "Faylni o'qib bo'lmadi",
 'share_pv_title': "Import: %1$s",
 'share_pv_mismatch': "⚠ Fayldagi mijoz: \"%1$s\", tanlangan: \"%2$s\". Fayl to'g'rimi?",
 'share_pv_period': 'Davr: %1$s – %2$s',
 'share_pv_counts': "Yozuvlar: %1$d (xarid %2$d, to'lov %3$d, qaytarish %4$d)",
 'share_pv_sales': 'Xaridlar jami: %1$s',
 'share_pv_payments': "To'lovlar jami: %1$s",
 'share_pv_subtotals': "Kunlik jami qatorlari o'tkazib yuborildi: %1$d",
 'share_pv_skipped': "⚠ Sanasiz qatorlar o'qilmadi: %1$d",
 'share_pv_products': 'Tovarlar katalogda topildi: %1$d / %2$d',
 'share_pv_unmatched': "Topilmaganlar (narxi ko'rsatilmaydi): %1$s",
 'share_pv_merge': "Saqlansa: yangi %1$d, almashtiriladigan %2$d, o'zgarmagan %3$d",
 'share_pv_save': 'Saqlash',
 'share_saved': 'Saqlandi',
 'share_not_card_title': "Hisobot tanilmadi",
 'share_not_card_msg': "Faylda \"Sana, Xarakat, Izoxlar, Soni, Summa\" sarlavhali jadval topilmadi. Mijoz kartochkasi hisobotini saqlaganingizni tekshiring. Quyida fayl boshi (dasturchiga yuborish mumkin):",
})

STRINGS.update({
 # v2.5: 1C tarixi (1-ilova), buyurtmalar, avto-yangilash
 'history_1c': '1C tarixi',
 'lgh_repeat_sale': 'Takrorlash (savatchaga)',
 'lgh_repeat_return': 'Vozvratni takrorlash',
 'lgh_to_cart': "Savatchaga qo'shish",
 'lgh_repeat_hint': "%1$s uchun. Narxlar hozirgi katalogdan olinadi (1C'dagi eski narx emas). Katalogda topilmagan tovar qo'shilmaydi.",
 'lgh_order_hint': "%1$s 2-ilovadan so'radi. Savatchada tekshirib, odatdagidek rasmiylashtirasiz.",
 'lgh_branch_printer': '2-filial printeri (bulut orqali)',
 'lgh_branch_sent': "Chek 2-filial printeri navbatiga qo'yildi. 2-ilova telefoni internetda bo'lsa chiqadi.",
 'ord_title': '2-ilovadan buyurtmalar',
 'ord_open': "Buyurtmalarni ko'rish",
 'ord_accept': 'Buyurtma qabul qilish',
 'ord_accept_hint': "2-ilovada mijoz o'tgan savdoni \"Qayta buyurtma\" qilsa, shu yerda ko'rinadi va bildirishnoma keladi. Buyurtma o'zi savdo bo'lmaydi — siz savatchaga olib, tekshirib rasmiylashtirasiz.",
 'ord_need_setup': "Avval pastdagi \"Sozlash\" bo'limini to'ldiring",
 'ord_off': "O'chirilgan",
 'ord_new_n': 'Yangi buyurtmalar: %1$d ta',
 'ord_none_new': "Yangi buyurtma yo'q",
 'ord_banner': '2-ilovadan yangi buyurtma: %1$d ta',
 'ord_sheet_hint': "Bosing: tovarlarni ko'rib, savatchaga oling yoki rad eting.",
 'ord_empty': "Buyurtma yo'q",
 'ord_item_title': 'Buyurtma · %1$s',
 'ord_item_lines': '%1$d xil tovar',
 'ord_note': 'Izoh: %1$s',
 'ord_st_new': 'Yangi',
 'ord_st_taken': 'Savatchaga olindi',
 'ord_st_rejected': 'Rad etildi',
 'ord_reject': 'Rad etish',
 'ord_notif_title': '%1$s: yangi buyurtma',
 'ord_notif_text': "%1$d ta yangi buyurtma (%2$d xil tovar). Izoh: %3$s",
 'share_open_history': "1C tarixi",
 'share_import_server_short': 'Serverdan',
 'share_publish_short': 'Yuborish',
 'share_link_code_short': 'Ulanish kodi',
 'share_sync_title': 'Avtomatik yangilash',
 'share_sync_hint': "Telefon o'zi (internet bo'lsa, batareya kam bo'lmasa) shu oraliqda tekshiradi: 2-ilova so'rovi, yangi buyurtma, yuborilmagan ma'lumot. Excel yuklanganda esa darhol yuboriladi.",
 'sync_off': "O'chiq", 'sync_30m': '30 daq', 'sync_1h': '1 soat', 'sync_2h': '2 soat',
 'share_receipt_title': 'Chek',
 'share_receipt_header': "Chek sarlavhasi (do'kon nomi)",
 'share_receipt_header_hint': "1C tarixidan chiqariladigan chek tepasida yoziladi. Bo'sh qoldirsangiz yozilmaydi.",

})

STRINGS.update({
 # v2.5: dizayn — sozlamalar izohlari
 'settings_share_sum': "2-filial ilovasi: Excel yuklash, 1C tarixi, buyurtmalar, chek printeri",
 'settings_extras_sum': "Ekran o'lchami, savdo ekrani, qidiruv, PIN, zaxira, eslatmalar",
 'settings_remote_sum': "Do'kondan tashqarida (VPN orqali) server bilan ishlash",
 'settings_look': "Ko'rinish",
 'settings_queue_sum': "Serverga hali yetmagan buyurtma va to'lovlar",
 'settings_diag_sum': "Server, internet va navbat holatini tekshirish",
 'f_store_card_hint': "Do'kon tanlanganda qarz, oxirgi savdo va odatdagi tovarlar ko'rinadi",
 'f_smart_button_hint': "Pastdagi katta tugma keyingi qadamni aytadi (masalan \"Savatchaga o'tish\")",
 'f_explain_hint': "Taklif qilingan tovar yonida nega taklif qilingani yoziladi",
 'f_checker_hint': "G'alati miqdor yoki narxni ko'rsa ogohlantiradi, hech narsani to'xtatmaydi",
 'f_multicart_hint': "Joriy savdoni kutishga qo'yib, boshqa do'kon bilan ishlash",
 'f_dup_payment_hint': "Bir xil to'lov qisqa vaqtda ikki marta kiritilsa so'raydi",
 'home_payment_short': "To'lov",
 'home_ledger_title': '1C tarixi · %1$s',
 'home_ledger_info': 'Hisobotlar %1$s gacha · qidirish, takrorlash, chek',
 'ex_sec_screen': 'Ekran',
 'ex_scale_xlarge': '120%',
 'ex_cost_switch_hint': "Savdo ekranida kelgan narxni ko'rsatish tugmasi (faqat shu telefonda)",
 'ex_backup_list_short': 'Tiklash',

})

STRINGS.update({
 # v2.6
 'finish_no_printer': 'Printersiz (cheksiz)',
 'sale_zeroed': '%1$s savatchadan olindi',
 'back_again_to_exit': 'Chiqish uchun yana bir marta bosing',

})

STRINGS.update({
 # v2.6 — ko'p mijozli ulashish
 'cl_title': 'Mijozlar', 'cl_add': "Mijoz qo'shish", 'cl_add_by_name': "Nomi bilan qo'shish", 'cl_add_from_stores': "Do'konlar ro'yxatidan",
 'cl_name_hint': '1C dagi nomi (masalan: Dadaxon Aka)',
 'cl_none': "Hali mijoz yo'q. Excel faylni yuklang — mijoz fayldagi nomdan o'zi aniqlanadi — yoki qo'lda qo'shing.",
 'cl_info': '%1$d ta mijoz · %2$d ta telefon kira oladi',
 'cl_entries': '%1$d ta yozuv · %2$s gacha', 'cl_no_entries': "1C tarixi yo'q",
 'cl_members': '%1$d ta telefon', 'cl_no_members': "Telefon qo'shilmagan", 'cl_has_printer': 'chek printeri',
 'cl_import': 'Excel yuklash', 'cl_import_here': 'Shu mijozga Excel yuklash', 'cl_clear': "Tarixni o'chirish",
 'cl_ledger': '1C tarixi', 'cl_other': 'Boshqa', 'cl_kind': 'Turi',
 'cl_kind_client': 'Mijoz', 'cl_kind_supplier': 'Yuk beruvchi', 'cl_kind_supplier_short': 'yuk beruvchi',
 'cl_link_store': "Do'konga bog'lash", 'cl_link_code': 'Ulash kodi',
 'cl_store': "Ro'yxatdagi do'kon: %1$s", 'cl_store_none': "Do'konlar ro'yxatiga bog'lanmagan (savdo va serverdan olish uchun bog'lang)",
 'cl_need_store': "\"%1$s\" do'konlar ro'yxatiga bog'lanmagan. Mijoz sahifasida \"Do'konga bog'lash\" ni bosing.",
 'cl_new_keys_msg': "Shu mijozning kalitlari almashtiriladi. Telefon raqami bilan kirganlar o'zi qayta ulanadi; eski \"ulash kodi\" ishlamay qoladi.",
 'cl_delete': "Mijozni o'chirish", 'cl_delete_msg': "\"%1$s\" ro'yxatdan, telefondagi 1C tarixi va bulutdagi nusxasi o'chiriladi. Unga kirgan telefonlar ham uziladi. 1C'ga tegilmaydi.",
 'cl_printer_title': 'Chek printeri',
 'cl_printer_hint': "Mijozning kompyuterida chek printeri bo'lsa — cheklar bulut orqali o'sha yerda chiqadi.",
 'cl_printer_on': 'Chek printerini yoqish',
 'cl_printer_on_msg': "Shu mijoz uchun chek navbati ochiladi. Keyin \"Kompyuter sozlamasi\" matnini mijoz kompyuteridagi forwarder papkasiga saqlang.",
 'cl_printer_off': "Chek printeri yoqilmagan",
 'cl_no_printer': "Bu mijozda chek printeri yoqilmagan",
 'cl_pv_to': 'Mijoz: %1$s', 'cl_pv_new': "Yangi mijoz qo'shiladi: %1$s", 'cl_pv_to_current': '%1$s ga saqlash',
 'cl_src_pc': 'kompyuterdan',
 'hist_client': 'Mijoz: %1$s', 'hist_pick_client': '1C tarixi — qaysi mijoz?',
 'home_orders_title': '2-ilovadan yangi buyurtmalar',
 'ord_by': 'Kim: %1$s',
 'share_publish_all': 'Hammasini yuborish', 'share_how_export_short': 'Qanday olinadi?', 'share_pc_config_short': 'Kompyuter sozlamasi',
 'share_printer_never': "Chek printeri: kompyuter hali ulanmagan · navbatda %1$d ta chek",
 'share_printer_ready': "Chek printeri: ulangan · %1$s · navbatda %2$d ta chek",
 'share_printer_not_ready': "Chek printeri: ulanmagan · oxirgi faollik %1$s · navbatda %2$d ta chek",
 'lgh_branch_printer': '%1$s — chek printeri (bulut orqali)',
 'lgh_branch_sent': "Chek printer navbatiga qo'yildi. Kompyuter internetda bo'lsa chiqadi.",
 'settings_share_sum': "Mijozlar: Excel yuklash, 1C tarixi, telefonlar, buyurtmalar, chek printeri",
 # telefonlar (2-ilovaga kirish)
 'mb_title': '2-ilovaga kiradigan telefonlar',
 'mb_hint': "Mijozning o'zi va do'konidagi ishchilar telefon raqami bilan kiradi — hammasi faqat shu mijoz ma'lumotini ko'radi. Parol bir marta so'raladi.",
 'mb_add': "Telefon qo'shish", 'mb_name': 'Ismi (masalan: Dadaxon aka, ishchi Ali)', 'mb_phone': 'Telefon raqami', 'mb_password': 'Parol',
 'mb_password_hint': "Avtomatik yaratildi — o'zgartirsangiz bo'ladi (kamida 6 belgi)",
 'mb_bad_phone': "Telefon raqami noto'g'ri (masalan: +998 90 123 45 67)",
 'mb_short_password': 'Parol kamida %1$d belgi bo\'lsin',
 'mb_add_fail': "Qo'shib bo'lmadi",
 'mb_no_name': 'Ismsiz', 'mb_send': "Kirish ma'lumoti",
 'mb_share_text': "Savdo Mijoz ilovasiga kirish\nMijoz: %1$s\nDo'kon kodi: %2$s\nTelefon: %3$s\nParol: %4$s\n\nIlovani oching → \"Kirish\" → shu uchtasini kiriting. Faqat bir marta so'raladi.",
 'mb_remove': "Telefonni o'chirish",
 'mb_remove_msg': "%1$s endi kira olmaydi. Xavfsizlik uchun kalitlar almashtiriladi — qolgan telefonlar o'zi qayta ulanadi.",
 'mb_removed': "Telefon o'chirildi, kalitlar yangilandi",
 # kompyuterdan avtomatik eksport
 'pc_title': "Kompyuterdan avtomatik (1C → Excel)",
 'pc_hint': "Kompyuterdagi dastur 1C'dan har bir mijoz kartochkasini Excel qilib, tanlangan oraliqda shu telefonga yuboradi. Telefon o'zi yuklab oladi.",
 'pc_1h': 'Har 1 soat', 'pc_2h': 'Har 2 soat', 'pc_3h': 'Har 3 soat', 'pc_help': "Qanday o'rnatiladi?",
 'pc_st_never': "Kompyuter hali ulanmagan",
 'pc_st_ok': "Kompyuter: %1$s · %2$s",
 'pc_st_err': "Kompyuter xatosi: %1$s · %2$s",
 # Firebase / do'kon kodi
 'fb_shop_code': "Do'kon kodi: %1$s (nusxa olish uchun bosing)",
 'fb_shop_code_none': "Do'kon kodi Firebase sozlangach paydo bo'ladi",
 'fb_shop_code_hint': "Do'kon kodi — 2-ilovaga kirishda so'raladi. Har do'konning (do'stlaringizning ham) o'z {{Firebase}} loyihasi bo'ladi.",
 'fb_guide': "Qadamma-qadam yo'riqnoma",
 # yo'riqnoma (oyna)
 'gd_page': '%1$d/%2$d',
 'gd_fb1_t': 'Loyiha ochish',
 'gd_fb1': "Kompyuter yoki telefonda console.firebase.google.com saytini oching (Google akkaunt bilan). \"Loyiha qo'shish\" → nomi, masalan \"savdo-dokon\". Google Analytics shart emas. Bepul tarif yetadi.",
 'gd_fb2_t': "Ma'lumotlar bazasi",
 'gd_fb2': "Chap menyu: Build → Realtime Database → \"Create database\". Joylashuv: istalgani (masalan Belgium). Rejim: \"Locked mode\". Qoidalarni bu ilova o'zi o'rnatadi.",
 'gd_fb3_t': 'Maxfiy kalit',
 'gd_fb3': "⚙ Project settings → Service accounts → Database secrets → \"Show\" → nusxa oling. Bu kalit faqat shu telefonda (shifrlangan) va kompyuterdagi faylda turadi — 2-ilovaga hech qachon berilmaydi.",
 'gd_fb4_t': 'Shu yerga kiritish',
 'gd_fb4': "Pastdagi maydonlarga baza manzilini (yoki loyiha nomini) va kalitni kiritib \"Saqlash\" ni bosing. Shundan keyin \"Do'kon kodi\" chiqadi — mijozlarga shu kod va telefon/parol beriladi.",
 'gd_pc1_t': 'Nima kerak',
 'gd_pc1': "1C 8.3 o'rnatilgan kompyuter (chek printeri dasturi turgan kompyuter ham bo'ladi), Python 3 va pywin32 (\"pip install pywin32\"). 1C uchun oddiy foydalanuvchi va paroli — konfigurator shart emas.",
 'gd_pc2_t': 'Sozlama fayli',
 'gd_pc2': "\"Kompyuter sozlamasi\" tugmasi → matnni pc papkasiga forwarder.ini nomi bilan saqlang. [onec] bo'limiga 1C bazasi manzili (File=\"D:\\Baza\" yoki Srvr=...;Ref=...), foydalanuvchi va parolni yozing.",
 'gd_pc3_t': 'Birinchi tekshiruv',
 'gd_pc3': "Bir marta: \"discover.bat\" ni ishga tushiring. U 1C'dagi hisobotlar ro'yxatini discover.txt ga yozadi — shu faylni dasturchiga yuboring, kerakli hisobot nomi sozlab beriladi.",
 'gd_pc4_t': 'Ishga tushirish',
 'gd_pc4': "\"start_export.bat\" ni ishga tushiring (Avtozagruzkaga qo'yish mumkin). Kompyuter shu yerda tanlangan oraliqda (1/2/3 soat) har bir mijoz kartochkasini yuboradi, telefon o'zi import qiladi. Holati shu bo'limda ko'rinadi.",
 'gd_mb1_t': "Telefon qo'shish",
 'gd_mb1': "\"Telefon qo'shish\" → ism, raqam (parol o'zi yaratiladi) → Saqlash → \"Yuborish\" bilan Telegram/SMS orqali jo'nating. Bitta mijozga bir nechta telefon (masalan 3 ta ishchi) qo'shsa bo'ladi.",
 'gd_mb2_t': '2-ilovada',
 'gd_mb2': "Savdo Mijoz ilovasida \"Kirish\": do'kon kodi, telefon, parol. Bir marta kiritiladi. Telefonni o'chirsangiz u kira olmaydi, qolganlar o'zi qayta ulanadi.",
 # o'rgatuvchi ko'rsatmalar (ekranda)
 'tour_home_sale_t': 'Yangi savdo', 'tour_home_sale': "Shu yerdan savdo boshlanadi: do'kon tanlang, tovar qo'shing, yuboring.",
 'tour_home_ledger_t': '1C tarixi va buyurtmalar', 'tour_home_ledger': "Mijozning 1C tarixi va 2-ilovadan kelgan buyurtmalar shu kartada ko'rinadi.",
 'tour_home_dayend_t': 'Kun yakuni', 'tour_home_dayend': 'Kun oxirida bugungi savdo va to\'lovlar jamlanmasi.',
 'tour_sale_store_t': "Do'kon", 'tour_sale_store': "Avval do'konni tanlang — narx turi va qarz shunga qarab ko'rsatiladi.",
 'tour_sale_search_t': 'Qidirish', 'tour_sale_search': "Tovar nomi yoki kodini yozing (lotin/kirill farqi yo'q).",
 'tour_sale_plus_t': '+ va −', 'tour_sale_plus': "+ — bitta qo'shadi, uzoq bossangiz bir blok. − — bitta kamaytiradi, uzoq bossangiz nolga tushiradi (qaytarish mumkin).",
 'tour_sale_cart_t': 'Savatcha', 'tour_sale_cart': "Tanlanganlarni tekshirib, chekli yoki printersiz yuborasiz.",
 'tour_hist_tabs_t': 'Tarix', 'tour_hist_tabs': "Buyurtmalar, to'lovlar va mijozlarning 1C tarixi (qidirish, takrorlash, chek) shu yerda.",
 'tour_share_add_t': "Mijoz qo'shish", 'tour_share_add': "Kompyuterdagi istalgan mijoz yoki yuk beruvchini qo'shing.",
 'tour_share_import_t': 'Excel yuklash', 'tour_share_import': "1C'dan saqlangan kartochkani yuklang — mijoz fayldagi nomdan o'zi topiladi.",
 'tour_share_pc_t': 'Kompyuterdan avtomatik', 'tour_share_pc': "Kompyuter 1C ma'lumotini necha soatda yuborsin — shu yerda tanlanadi.",
 'tour_share_orders_t': 'Buyurtmalar', 'tour_share_orders': "Mijozlar 2-ilovadan bergan buyurtmalar. Tasdiqlasangiz — savatchaga tushadi.",
 'tour_client_member_t': 'Telefonlar', 'tour_client_member': "Mijoz va ishchilarining raqamini qo'shing — ular 2-ilovaga shu raqam bilan kiradi.",
 'tour_client_import_t': '1C tarixi', 'tour_client_import': "Shu mijozning Excel kartochkasini yuklash yoki serverdan olish.",
 'tour_client_printer_t': 'Chek printeri', 'tour_client_printer': "Mijoz kompyuterida printer bo'lsa — cheklar bulut orqali o'sha yerda chiqadi.",
 'tour_set_share_t': 'Mijozlar va 2-ilova', 'tour_set_share': "Mijozlar, telefonlar, Excel import, buyurtmalar va kompyuter sozlamasi shu yerda.",
 'tour_set_theme_t': "Ko'rinish", 'tour_set_theme': "Qorong'i/yorug' rejim. Shrift o'lchami ham sozlamalarda.",

})

STRINGS.update({
 # v2.6 — sirli menyu (1-ilova)
 'sx_stats': 'Statistika', 'sx_stats_sub': "Katalog, tarix, mijozlar — hammasi raqamlarda",
 'sx_st_catalog': "Katalog: %1$d tovar · %2$d guruh · %3$d do'kon",
 'sx_st_history': "Tarix: %1$d savdo · %2$d to'lov · yuborilmagan: %3$d",
 'sx_st_clients': 'Mijozlar: %1$d (yuk beruvchi: %2$d) · telefonlar: %3$d',
 'sx_st_ledger': '1C tarixi (%1$s): %2$d yozuv', 'sx_st_orders': 'Yangi buyurtmalar: %1$d',
 'sx_goal': 'Kunlik maqsad', 'sx_goal_sub': "Bugungi savdo uchun maqsad qo'ying — erishsangiz bayram!",
 'sx_goal_set': "Maqsad: %1$s so'm", 'sx_goal_hint': "Maqsad (so'm, 0 — o'chiq)",
 'sx_goal_today': "Bugun sotildi: %1$s so'm", 'sx_goal_done': 'Maqsadga erishildi! 🎉',
 'sx_hours': 'Savdo soatlari', 'sx_hours_sub': 'Qaysi soatlarda eng ko\'p savdo bo\'ladi',
 'sx_hours_empty': "Savdo tarixi hali yo'q", 'sx_hours_best': "Eng qizg'in vaqt: %1$02d:00–%2$02d:00",
 'sx_turbo': 'Turbo sinxron', 'sx_turbo_sub': "Bulutga hamma mijozlarni hozir yuborish va buyurtmalarni olish",
 'sx_turbo_run': 'Yuborilmoqda…', 'sx_turbo_ok': 'Tayyor: %1$d ms', 'sx_turbo_fail': "Yuborilmadi (%1$d ms) — internetni tekshiring",
 'sx_cloud': 'Bulut pasporti', 'sx_cloud_sub': "Firebase holati (kalitlar yashirilgan)",
 'sx_cl_shop': "Do'kon kodi: %1$s", 'sx_cl_secret': 'Secret: %1$s', 'sx_cl_inbox': 'Kompyuter qutisi: %1$s',
 'sx_cl_rules': 'Qoidalar versiyasi: %1$d (kerak: %2$d)', 'sx_cl_err': 'Oxirgi xato: %1$s',
 'sx_test_receipt': 'Sinov cheki', 'sx_test_receipt_sub': '%1$s printeriga sinov cheki yuborish',

})

STRINGS.update({
 # v2.6 — qisqa tugma yozuvlari (ikkita yonma-yon tugma 360dp ga sig'sin)
 'b_add': "Qo'shish", 'b_excel': 'Excel', 'b_pc_settings': 'Sozlama', 'b_guide': "Yo'riqnoma", 'b_send': 'Yuborish',
 'b_how': "1C'dan olish", 'b_save': 'Saqlash', 'b_clear': 'Tozalash', 'b_link': "Bog'lash", 'b_newkey': 'Yangi kalit', 'b_delete': "O'chirish",
 'pc_1h': '1 soat', 'pc_2h': '2 soat', 'pc_3h': '3 soat',
 'share_title': 'Mijozlar va 2-ilova',

})

STRINGS.update({
 # v2.6 — shtrix kod bilan savdo
 'scan_title': 'Shtrix kod skaneri', 'scan_torch': 'Chiroq', 'scan_done': 'Tayyor', 'scan_undo': 'Qaytarish', 'scan_undone': 'Oxirgisi olib tashlandi',
 'scan_no_permission': "Kameraga ruxsat berilmadi. Telefon sozlamalari ▸ Ilovalar ▸ Savdo Agent ▸ Ruxsatlar ▸ Kamera.\n\nUlanadigan skaner (Bluetooth/USB) kamerasiz ham ishlaydi: Savdo ekranida kodni o'qiting.",
 'scan_cart': 'Savatchada: %1$d xil · %2$s',
 'scan_added_one': '+1', 'scan_added_weight': '+%1$s kg', 'scan_in_cart': 'savatchada %1$s', 'scan_weight': "og'irlik %1$s kg",
 'scan_unknown_title': 'Shtrix kod topilmadi',
 'scan_unknown': "%1$s\n\nBu kod ro'yxatda yo'q. Qaysi tovar ekanini tanlang — keyingi safar ilova o'zi taniydi.",
 'scan_unknown_scale': "%1$s\n\nTarozi yorlig'i, tovar kodi: %2$s. Qaysi tovar ekanini tanlang — shu kodli hamma yorliqlar keyin o'zi taniladi.",
 'scan_scale_confirm_title': "Tarozi yorlig'i",
 'scan_scale_confirm': "Yorliqdagi tovar kodi %1$s — bu «%2$s» mi? Tasdiqlasangiz, keyingi safar so'ralmaydi.",
 'scan_yes': "Ha, shu", 'scan_pick_other': 'Boshqa tovar', 'scan_pick': 'Tovarni tanlash', 'scan_pick_title': 'Qaysi tovar?',
 'bc_title': 'Shtrix kodlar', 'bc_settings_sum': "Skanerlab savdo: 1C ro'yxati, tarozi yorlig'i, sinash",
 'bc_list': "1C'dagi shtrix kodlar", 'bc_learned_btn': 'Eslangan',
 'bc_none': "Ro'yxat hali yuklanmagan", 'bc_status': '%1$d ta shtrix kod tovarlarga bog\'langan',
 'bc_status_file': '%1$s · %2$s', 'bc_status_unmatched': "Katalogda topilmagan: %1$d ta", 'bc_status_learned': "Skanerda o'rganilgan: %1$d ta",
 'bc_how_title': "1C'dan shtrix kodlarni olish",
 'bc_how_text': "1) 1C'da shtrix kodlar ro'yxatini oching (tovar kodi, nomi va shtrix kodi ko'rinadigan oyna). Filtr bo'lsa — olib tashlang.\n2) Tepada Действия ▸ Вывести список... (yoki ro'yxat ichida sichqonchaning o'ng tugmasi).\n3) Ustunlarda kod, nom va shtrix kod belgilangan bo'lsin ▸ OK.\n4) Ochilgan jadvalda Файл ▸ Сохранить как ▸ «Лист Excel (*.xlsx)».\n5) Faylni telefonga o'tkazing (Telegram) va shu yerda «Excel» tugmasini bosing.\n\nIlova ustunlarni o'zi topadi va tovarlarga avval kod, keyin nom bo'yicha bog'laydi. Yangi tovar qo'shilganda faylni qayta yuklang (eskisi almashtiriladi, skanerda o'rganilganlar saqlanadi).",
 'bc_not_list_title': "Bu shtrix kodlar ro'yxati emas",
 'bc_not_list': "Faylda shtrix kod ustuni topilmadi (8–14 xonali raqamlar). «1C'dan olish» yo'riqnomasidagidek Действия ▸ Вывести список orqali saqlang.",
 'bc_pv_title': 'Import: %1$s', 'bc_pv_rows': 'Fayldagi shtrix kodlar: %1$d ta',
 'bc_pv_linked': "Tovarlarga bog'landi: %1$d ta (kod bo'yicha %2$d, nom bo'yicha %3$d)",
 'bc_pv_unmatched': 'Katalogda topilmadi: %1$d ta, masalan:',
 'bc_pv_no_code': "Faylda tovar kodi ustuni topilmadi — faqat nom bo'yicha bog'landi.",
 'bc_pv_code_mismatch': "Diqqat: kodlar katalogdagi kodlarga mos kelmadi, faqat nom bo'yicha bog'landi. Ehtimol ro'yxatda boshqa kod chiqarilgan.",
 'bc_pv_replace': "Saqlansa, avvalgi import qilingan ro'yxat almashtiriladi.",
 'bc_saved': '%1$d ta shtrix kod saqlandi',
 'bc_clear_title': 'Tozalash', 'bc_clear_imported': "Import qilingan ro'yxatni o'chirish", 'bc_clear_learned': "Skanerda o'rganilganlarni o'chirish",
 'bc_learned_title': "O'rganilgan kodlar", 'bc_learned_none': "Hali o'rganilgan kod yo'q", 'bc_scale_code': 'tarozi kodi %1$s',
 'bc_forget_q': "Unutilsinmi?\n%1$s",
 'bc_scale': "Tarozi yorlig'i", 'bc_scale_on': "Tarozi shtrix kodlarini tanish",
 'bc_scale_hint': "Yorliqda tovar kodi va og'irlik bo'ladi: og'irlik savatchaga miqdor bo'lib tushadi.",
 'bc_prefixes': 'Boshlanishi (prefiks)', 'bc_prefixes_helper': "Vergul bilan: masalan 20,21,22 yoki 2",
 'bc_code_len': "Tovar kodi uzunligi", 'bc_value_len': "Og'irlik uzunligi",
 'bc_n4': '4 ta', 'bc_n5': '5 ta', 'bc_n6': '6 ta',
 'bc_unit': "Og'irlik birligi", 'bc_unit_g': 'gramm', 'bc_unit_10g': '10 gramm',
 'bc_check': "Oxirgi tekshiruv raqami", 'bc_check_hint': "Noto'g'ri o'qilgan kod qabul qilinmaydi. Tarozi tekshiruv raqamini qo'ymasa — o'chiring.",
 'bc_trust': "Tovar kodi = 1C kodi (so'ramasdan)", 'bc_trust_hint': "O'chiq bo'lsa, har yangi tovar kodi uchun bir marta tasdiq so'raladi.",
 'bc_test': 'Sinash', 'bc_test_hint': 'Shtrix kod raqamlarini yozing', 'bc_scan_camera': 'Kamera bilan sinash',
 'bc_t_check_ok': 'Tekshiruv raqami to\'g\'ri (%1$d xonali)', 'bc_t_check_bad': "Tekshiruv raqami mos emas (%1$d xonali) — kod xato o'qilgan bo'lishi mumkin",
 'bc_t_scale': "Tarozi yorlig'i: tovar kodi %1$s, og'irlik %2$s kg",
 'bc_t_found': '✅ Tovar: %1$s', 'bc_t_missing': "Kod ma'lum, lekin tovar katalogda yo'q (ma'lumotlarni yangilang)",
 'bc_t_suggest': "❓ Taklif: %1$s (birinchi skanerda tasdiq so'raladi)", 'bc_t_unknown': "❌ Topilmadi — skanerlanganda tovarni tanlash so'raladi",
 'bc_scanner': 'Skaner', 'bc_beep': 'Tovush', 'bc_vibrate': 'Tebranish',
 'bc_hw_hint': "Bluetooth/USB skaner ham ishlaydi: Savdo ekrani ochiq bo'lsa kodni o'qiting — tovar savatchaga tushadi (qidiruv maydoni tanlangan bo'lsa ham).",
 'tour_sale_scan_t': 'Shtrix kod', 'tour_sale_scan': "Kamera bilan ketma-ket skanerlang — har bir tovar savatchaga qo'shiladi. Tarozi yorlig'idagi og'irlik ham.",

})

STRINGS.update({
 # v2.6 — skaner savdosi (professional)
 'scan_hint': "Kamerani shtrix kodga qarating — to'xtamasdan ketma-ket o'qiladi",
 'scan_live': '● Skanerlanmoqda', 'scan_paused': '❚❚ Pauza', 'scan_pause': 'Pauza',
 'scan_in_cart_chip': 'Savatchada', 'scan_in_cart_chip_n': 'Savatchada %1$d',
 'scan_pending_title': 'Topilmadi: %1$s', 'scan_pending_pick': "Ro'yxatdan tovarni bosing — eslab qolinadi",
 'scan_pending_suggest': "Shu tovarmi: «%1$s»? Yoki ro'yxatdan boshqasini bosing",
 'scan_skip': "O'tkazish",
 'bc_test_scan_hint': "Kodni o'qiting — ilova uni qanday tushunishi chiqadi (savatchaga qo'shilmaydi)",
 'bc_pv_scale': "Tarozi tovarlari: %1$d ta (prefiks %2$s) — tarozi sozlamasi shunga moslanadi",

})

# kirillni qo'lda to'g'rilash kerak bo'lgan joylar
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from strings_ledger import LEDGER  # noqa: E402
for _k in LEDGER:
    assert _k not in STRINGS, _k
STRINGS.update(LEDGER)

OVERRIDES = {}

MAP = {'a':'а','b':'б','d':'д','e':'е','f':'ф','g':'г','h':'ҳ','i':'и','j':'ж','k':'к','l':'л','m':'м','n':'н','o':'о',
       'p':'п','q':'қ','r':'р','s':'с','t':'т','u':'у','v':'в','x':'х','y':'й','z':'з','c':'к','w':'в'}
APOS = "'’ʻʼ‘`"

def cap(span_orig, out):
    if not span_orig[0].isupper(): return out
    if len(span_orig) > 1 and span_orig[1].islower(): return out[0].upper() + out[1:]
    return out.upper()

def conv_token(tok):
    for a in APOS[1:]: tok = tok.replace(a, "'")
    t = tok.lower(); out = []; i = 0; n = len(t)
    while i < n:
        c = t[i]; nx = t[i+1] if i+1 < n else ''; nn = t[i+2] if i+2 < n else ''
        if c == 's' and nx == 'h': out.append(cap(tok[i:i+2], 'ш')); i += 2; continue
        if c == 'c' and nx == 'h': out.append(cap(tok[i:i+2], 'ч')); i += 2; continue
        if c == 'o' and nx == "'": out.append(cap(tok[i:i+2], 'ў')); i += 2; continue
        if c == 'g' and nx == "'": out.append(cap(tok[i:i+2], 'ғ')); i += 2; continue
        if c == 'n' and nx == 'g' and nn != "'": out.append(cap(tok[i:i+2], 'нг')); i += 2; continue
        if c == 'y' and nx == 'o' and nn != "'": out.append(cap(tok[i:i+2], 'ё')); i += 2; continue
        if c == 'y' and nx == 'u': out.append(cap(tok[i:i+2], 'ю')); i += 2; continue
        if c == 'y' and nx == 'a': out.append(cap(tok[i:i+2], 'я')); i += 2; continue
        if c == "'": out.append('ъ'); i += 1; continue
        if c == 'e': m = 'э' if i == 0 else 'е'
        else: m = MAP.get(c, c)
        out.append(cap(tok[i], m)); i += 1
    return ''.join(out)

TOKEN = re.compile(r"[A-Za-z'’ʻʼ‘`]+")
PROTECT = re.compile(r'(%\d*\$?[sdf]|%%|\{\{.*?\}\})')

# Lotinda qolishi kerak bo'lgan nomlar (v2.4): avtomatik {{...}} ichiga olinadi.
BRANDS = ['Gmail', 'Wi-Fi', 'Excel', 'HTML', 'Telegram', 'SMS', 'Firebase', 'Google', 'Tailscale', 'Play Market', 'PIN', 'KB',
          'xlsx', 'xls', 'htm', 'mxl', 'VPN', 'IMSOFT', 'Click', 'USB', 'ESC/POS', 'forwarder.ini', 'forwarder.py', 'Python',
          'OTG', 'Xiaomi', 'HyperOS', 'Redmi', 'DOKON', 'Windows']

def protect_brands(s):
    for b in sorted(BRANDS, key=len, reverse=True):
        s = re.sub(r'(?<![{\w])' + re.escape(b) + r'(?![\w}])', '{{' + b + '}}', s)
    return s

def to_cyr(s):
    s = protect_brands(s)
    res = []
    for part in PROTECT.split(s):
        if part.startswith('{{'): res.append(part[2:-2])
        elif PROTECT.fullmatch(part) : res.append(part)
        else: res.append(TOKEN.sub(lambda m: conv_token(m.group(0)), part))
    return ''.join(res)

def lat_text(s): return re.sub(r'\{\{(.*?)\}\}', r'\1', s)

def esc(s):
    s = escape(s)
    s = s.replace("'", "\\'").replace('"', '\\"').replace('\n', '\\n')
    if s.startswith(('@', '?')): s = '\\' + s
    return s

def write(path, items):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n')
        for k, v in items:
            fmt = ' formatted="false"' if False else ''
            f.write(f'    <string name="{k}">{esc(v)}</string>\n')
        f.write('</resources>\n')

if __name__ == '__main__':
    base = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'app', 'src', 'main', 'res')
    write(os.path.join(base, 'values', 'strings.xml'), [(k, lat_text(v)) for k, v in STRINGS.items()])
    write(os.path.join(base, 'values-b+uz+Cyrl', 'strings.xml'), [(k, OVERRIDES.get(k, to_cyr(v))) for k, v in STRINGS.items()])
    print(len(STRINGS), 'strings')
