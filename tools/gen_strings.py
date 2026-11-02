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
 'finish_title': 'Buyurtmani yakunlash', 'finish_total': 'Jami', 'finish_discount_percent': 'Chegirma, foiz',
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
 'settings_backup_hint': "Ma'lumotni saqlab qo'ying. Parol qo'ysangiz, sozlamalar ham (server, bulut, printerlar) saqlanadi — Admin ilovaga yoki yangi telefonga ko'chirish uchun. Parolsiz zaxirada server paroli bo'lmaydi.",
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
 'backup_password_hint': "Bo'sh qoldirsangiz — parolsiz (sozlamalarsiz). Parol hech qayerda saqlanmaydi: unutsangiz, faylni ochib bo'lmaydi.",
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
 'ex_scale_small': '×0,9', 'ex_scale_compact': '×0,92', 'ex_scale_normal': '×1', 'ex_scale_large': '×1,1',
 'ex_font': 'Shrift',
 'ex_font_small': '×0,9', 'ex_font_normal': '×1', 'ex_font_large': '×1,15',
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
 'ex_scale_xlarge': '×1,2',
 'ex_cost_switch_hint': "Savdo ekranida kelgan narxni ko'rsatish tugmasi (faqat shu telefonda)",
 'ex_backup_list_short': 'Tiklash',

})

STRINGS.update({
 # v2.6
 'finish_no_printer': 'Cheksiz',
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

STRINGS.update({
 # v2.6 — kod yozish rejimi
 'link_title': 'Kod yozish (bazada yo\'q kodlar)', 'link_live': '● Kod yozish',
 'link_hint': "Tovarning shtrix kodini o'qiting, keyin pastdagi ro'yxatdan shu tovarni bosing — kod telefonga yoziladi",
 'link_scan_first': "Avval tovarning shtrix kodini kameraga ko'rsating",
 'link_known': 'Bu kod allaqachon bor: %1$s', 'link_change': "O'zgartirish",
 'link_done': '✓ Yozildi: %1$s', 'link_session': 'Bu safar yozildi: %1$d ta', 'link_total': 'Telefonda: %1$d ta kod',
 'link_export': "Excel'ga", 'b_export': "Excel'ga",

})

STRINGS.update({
 'home_scan_sale': 'Shtrix kod bilan savdo',

})

STRINGS.update({
 # v2.6 — savdo 2-ilovaga ham
 'mir_receipt_sale': 'Savdo cheki', 'mir_receipt_return': 'Qaytarish cheki',
 'mir_receipt_note': '%1$s · Savdo Agent',
 'cl_printer_hint': "Chek printeri mijozning kompyuterida yoki uning 2-ilova telefoniga ulangan bo'lishi mumkin — cheklar bulut orqali o'sha yerda chiqadi. 2-ilova sozlamasida «1-ilova savdolari cheklari» yoqilsa, shu mijozga qilingan har yangi savdo cheki ham o'zi boradi.",

})

STRINGS.update({
 # v2.6 — chek ko'rinishi (do'kon chekidek)
 'rc_staff_field': 'Xodim (chekda)', 'rc_staff_hint': 'Masalan: Dokon. Bo\'sh bo\'lsa chiqmaydi',
 'rc_footer_field': 'Chek osti (telefonlar)', 'rc_footer_hint': "Har qator alohida, masalan: Tel: +998 93 647 46 46",
 'rc_preview': "Namuna chekni ko'rish",
 'rc_paid_som': "Naqd (So'm)", 'rc_paid_usd': 'Naqd ($)',
 'rc_note_estimate': "Qarz qoldig'i telefon hisobi (≈), rasmiysi — 1C'da",

})

STRINGS.update({
 # v2.6: narx yorliqlari (Xprinter)
 'home_labels': "Narx yorlig'i",
 'lb_title': 'Narx yorliqlari', 'lb_settings_sum': 'Xprinter: PLU va shtrixli narx yorliqlari',
 'lb_price_cash': 'Naqd narx', 'lb_price_wholesale': 'Optom narx',
 'lb_filter_all': 'Hammasi', 'lb_filter_selected': 'Tanlangan', 'lb_filter_selected_n': 'Tanlangan: %1$d',
 'lb_filter_plu': 'PLU bor', 'lb_filter_barcode': 'Shtrix bor',
 'lb_print_plu': 'PLU narx', 'lb_print_barcode': 'Shtrixli narx',
 'lb_printer_title': 'Yorliq printeri', 'lb_import_plu': "PLU ro'yxatini yuklash",
 'lb_clear': "Ro'yxatni tozalash", 'lb_clear_msg': "Tanlangan tovarlar ro'yxatdan olib tashlanadi. PLU raqamlari saqlanib qoladi.",
 'lb_preview': "Ko'rinishi", 'lb_find_pc': 'Kompyuterni topish', 'lb_check': 'Tekshirish',
 'lb_host': 'Kompyuter IP manzili', 'lb_host_hint': "Masalan 192.168.1.10 (bo'sh — faqat internet orqali)", 'lb_host_bad': "IP manzil noto'g'ri",
 'lb_port': 'Port', 'lb_cloud': 'Internet orqali ham yuborish',
 'lb_cloud_hint': "Telefon do'kon Wi-Fi tarmog'ida bo'lmasa, yorliqlar navbatga qo'yiladi va kompyuter internetdan olib chiqaradi.",
 'lb_cloud_off': "Internet orqali yuborish uchun avval Sozlamalar ▸ \"Mijozlar va 2-ilova\" da Firebase ni sozlang.",
 'lb_size': "Yorliq o'lchami (mm)", 'lb_size_5840': '58×40', 'lb_size_5830': '58×30', 'lb_size_4030': '40×30', 'lb_size_3020': '30×20',
 'lb_dpi_203': '203 dpi', 'lb_dpi_300': '300 dpi',
 'lb_test': "Sinov yorlig'i", 'lb_test_name': "Sinov yorlig'i — Xprinter",
 'lb_setup_hint': "Kompyuterda (bir marta): pip install pillow pywin32, keyin \"Kompyuter sozlamasi\" ni forwarder.ini nomi bilan {{pc}} papkasiga saqlang va start_labels.bat ni ishga tushiring. Telefon va kompyuter bitta Wi-Fi tarmog'ida bo'lsa yorliq darhol chiqadi.",
 'lb_ini_text': "Bu matnni kompyuterdagi {{pc}} papkasiga forwarder.ini nomi bilan saqlang (bor bo'lsa — almashtiring). Unda maxfiy kalit bor — faqat o'zingizning kompyuteringizga yuboring.",
 'lb_count': '%1$d ta yorliq · %2$d ta tovar', 'lb_count_none': 'Tovar tanlang: + bosing yoki skanerlang',
 'lb_empty_selected': 'Hali tovar tanlanmagan',
 'lb_note_plu': 'PLU %1$s', 'lb_note_barcode': 'shtrix ✓', 'lb_note_manual_price': "narx qo'lda",
 'lb_printer_lan': '%1$s · %2$s (Wi-Fi)', 'lb_printer_offline': 'Kompyuter javob bermadi: %1$s',
 'lb_printer_cloud_fallback': 'internet orqali yuboriladi', 'lb_printer_cloud': '%1$s · internet orqali',
 'lb_printer_none': 'Printer sozlanmagan — bosing',
 'lb_copies': 'Nechta yorliq', 'lb_price_manual': "Narx (qo'lda, ixtiyoriy)", 'lb_price_auto': "Bo'sh qolsa: %1$s",
 'lb_plu': 'PLU raqami (tarozi)', 'lb_plu_hint': "Tarozidagi tovar raqami; bo'sh — o'chiriladi", 'lb_remove': "Ro'yxatdan olish",
 'lb_plu_pv': "Faylda %1$d ta PLU topildi, %2$d tasi tovarlarga bog'landi.",
 'lb_plu_pv_missing': "Bog'lanmaganlar (nomi katalogdagidan farq qiladi): %1$d ta",
 'lb_plu_saved': 'PLU raqamlari saqlandi: %1$d ta',
 'lb_plu_not_found': "Faylda PLU raqamlari topilmadi. 1C'dagi \"PLU Narx\" shaklini (hamma tarozi tovarlari bilan) Excel yoki MXL qilib saqlang, yoki PLU ustuni bor jadvalni tanlang.",
 'lb_print_plu_title': "PLU narx yorlig'i", 'lb_print_barcode_title': "Shtrixli narx yorlig'i",
 'lb_summary': '%1$d ta yorliq (%2$d ta tovar) · %3$d×%4$d mm',
 'lb_nothing': "Chiqadigan yorliq yo'q", 'lb_print_n': 'Chop etish (%1$d ta)',
 'lb_route_lan_cloud': "Printer: %1$s · %2$s (Wi-Fi, bo'lmasa internet)", 'lb_route_lan': 'Printer: %1$s · %2$s (Wi-Fi)',
 'lb_route_cloud': 'Printer: %1$s · internet orqali', 'lb_route_none': 'Printer sozlanmagan — bosing',
 'lb_skip_plu': "PLU raqami yo'q — chiqmaydi: %1$d ta", 'lb_skip_barcode': "Shtrix kodi yo'q — chiqmaydi: %1$d ta",
 'lb_skip_price': "Narxi yo'q — chiqmaydi: %1$d ta", 'lb_skip_catalog': "Katalogda yo'q: %1$d ta",
 'lb_and_more': 'va yana %1$d ta', 'lb_fix_plu': 'PLU raqamlarini kiritish', 'lb_fix_barcode': 'Shtrix kod yozish (skaner)',
 'lb_n_of': '%1$d / %2$d', 'lb_skip': "O'tkazish", 'lb_copies_n': '× %1$d',
 'lb_printed': '%1$d ta yorliq chiqarildi (%2$s)', 'lb_queued': "%1$d ta yorliq navbatga qo'yildi — kompyuter chiqaradi",
 'lb_print_fail': "Chop etib bo'lmadi",
 'lb_err_net': "Kompyuterga ulanib bo'lmadi: %1$s\n\nKompyuter yoniqmi, start_labels.bat ishlayaptimi va telefon o'sha Wi-Fi tarmog'idami — tekshiring.",
 'lb_err_no_route': "Printer sozlanmagan: kompyuter IP manzilini kiriting (\"Kompyuterni topish\") yoki internet orqali yuborishni yoqing.",
 'lb_err_key': "Kompyuterdagi kalit mos emas. \"Kompyuter sozlamasi\" ni qayta yuborib, forwarder.ini ni almashtiring.",
 'lb_err_printer': "Printer xatosi: %1$s\n\nPrinter yoniqmi, qog'oz bormi — tekshiring.",
 'lb_st_ok': 'Ulangan: %1$s', 'lb_st_offline': 'Kompyuter javob bermadi (%1$s)', 'lb_st_cloud': 'Internet orqali: %1$s',
 'lb_st_cloud_unknown': "Internet orqali (kompyuter hali ko'rinmadi)", 'lb_st_none': 'Printer sozlanmagan',
 'lb_st_pc': 'Kompyuter: %1$s', 'lb_st_paper': "Qog'oz: %1$d×%2$d mm, %3$d dpi", 'lb_st_seen': 'Oxirgi aloqa: %1$s', 'lb_st_err': 'Xato: %1$s',
 'lb_find_none': "Wi-Fi tarmog'ida kompyuter topilmadi. Kompyuterda start_labels.bat ishlayotganini va telefon o'sha Wi-Fi tarmog'ida ekanini tekshiring. IP manzilni qo'lda ham yozish mumkin.",
 'lb_found_item': '%1$s (%2$s) · %3$s',
 'lb_size_from_pc': 'Kompyuterdagi printer sozlamasidan: %1$d×%2$d nuqta',
 'lb_size_manual': "%1$d×%2$d nuqta (kompyuter o'lcham aytmasa shu ishlatiladi)",
 'lb_scan_live': 'Yorliq uchun skanerlang', 'lb_scan_hint': "Tovar kodini skanerlang — yorliqlar ro'yxatiga qo'shiladi",
 'lb_hw_unknown': "Kod topilmadi: %1$s — skaner tugmasi orqali tovarga bog'lang",
 'lb_scan_done': "Ro'yxatga", 'lb_scan_count': '%1$d ta yorliq', 'lb_scan_products': '%1$d ta tovar', 'lb_scan_added': "Ro'yxatda: %1$d ta",

})

STRINGS.update({
 # v2.7: tovar kodlari (1C -> kompyuter -> telefon)
 'meta_code': 'Kod %1$s', 'meta_code_plu': 'Kod %1$s · PLU %2$s',
 'lb_codes': "Kompyuterdan kodlar (1C)",
 'lb_codes_last': "Oxirgi: %1$s\nPLU: %2$d ta tovar · shtrix kod: %3$d ta",
 'lb_codes_src': "1C'dagi joyi: %1$s",
 'lb_codes_never': "Kompyuterdan hali kod kelmagan.",
 'lb_codes_how': "Kompyuterdagi onec_export.py 1C'dan tarozi PLU raqamlari va shtrix kodlarni o'zi oladi (har 6 soatda va siz so'raganda) — har bir tovarni qo'lda yozish shart emas. Qo'lda yozilgan PLU ustun turadi.",
 'lb_codes_now': 'Hozir yangilash',
 'lb_codes_ini_text': "Bu matnni o'zingizga (Telegram) yuboring. Kompyuterda ORNATISH.bat Bloknot ochganda shu matnni joylab saqlaysiz. Ichida maxfiy kalit bor — boshqalarga bermang.",
 'lb_codes_wait': "So'rov yuborildi. Kompyuter 1-3 daqiqada yuboradi — kuting…",
 'lb_codes_done': "Yangilandi: PLU %1$d ta, shtrix kod %2$d ta",
 'lb_codes_timeout': "Kompyuterdan javob kelmadi. Kompyuter yoniqmi va onec_export.py ishlayaptimi — tekshiring.",
 'lb_codes_fail': "So'rov yuborilmadi: %1$s",
 'lb_codes_no_cloud': "Avval Firebase sozlang (Sozlamalar ▸ Mijozlar va 2-ilova) va kompyuterga sozlamani yuboring.",

})

STRINGS.update({
 # v2.7: ulashilgan printer (Windows LPD) — kompyuterda dastur shart emas
 'lb_mode_lpd': 'Ulashilgan printer', 'lb_mode_app': 'IMSOFT dasturi',
 'lb_queue': 'Printer ulashish nomi', 'lb_queue_hint': "Windows'da printerga berilgan umumiy nom (bo'shliqsiz), masalan xprinter",
 'lb_rotate': "Yorliq teskari chiqsa — 180° burish",
 'lb_host_hint_lpd': "Printer ulangan kompyuter IP manzili, masalan 192.168.1.200",
 'lb_setup_hint_lpd': "Kompyuterda bir marta ORNATISH.bat ni ishga tushiring — u printerni xprinter nomi bilan ulashadi. Boshqa dastur kerak emas, 1C ham avvalgidek chiqaraveradi. Kompyuter yoniq tursa bo'ldi.",
 'lb_printer_lpd': '%1$s · %2$s (ulashilgan)',
 'lb_found_lpd': '%1$s — ulashilgan printer xizmati bor',
 'lb_find_none_lpd': "Wi-Fi tarmog'ida ulashilgan printer xizmati (LPD) bor kompyuter topilmadi. Kompyuterda \"LPD Print Service\" yoqilganini va telefon o'sha Wi-Fi tarmog'ida ekanini tekshiring. IP manzilni qo'lda ham yozish mumkin.",
 'lb_st_lpd_ok': 'Tayyor: %1$s · %2$s',
 'lb_st_lpd_unknown': 'Tekshirilmagan: %1$s · %2$s',
 'lb_st_lpd_err': "Xato: %1$s — kompyuterda \"LPD Print Service\" yoqilganmi va 515-port ochiqmi?",
 'lb_err_no_host_lpd': "Printer ulangan kompyuter IP manzilini kiriting (\"Kompyuterni topish\" yoki qo'lda).",
 'lb_err_lpd_queue': "Kompyuterda \"%1$s\" nomli ulashilgan printer yo'q (%2$s). Xprinter ▸ Свойства принтера ▸ Доступ ▸ \"Общий доступ\" — nomini ilovadagidek yozing.",
 'lb_err_lpd': "Kompyuter ishni qabul qilmadi: %1$s",
 'lb_err_lpd_net': "Kompyuterga (%1$s) ulanib bo'lmadi: %2$s\n\nKompyuter yoniqmi, \"LPD Print Service\" yoqilganmi, telefon o'sha Wi-Fi tarmog'idami — tekshiring.",

})

STRINGS.update({
 # v2.8: 1C narx turlari (yorliq uchun)
 'lb_price_1c': '1C narxi',
 'lb_codes_prices': "1C narx turlari: %1$s",
 'lb_codes_pc': "Kompyuter (%1$s): %2$s",
 'lb_price_1c_title': "Yorliqda qaysi 1C narxi chiqsin",
 'lb_price_1c_none': "1C narx turlari hali kelmagan. Kompyuterdan kodlar (1C) ▸ Hozir yangilash — kompyuter 1C'dagi hamma narx turlarini yuboradi, keyin shu yerda kerakligini tanlaysiz.",

})

STRINGS.update({
 # v2.9: chek ko'rinishi
 'rs_title': "Chek ko'rinishi", 'rs_preview': 'Namuna chek', 'rs_open': "Chek ko'rinishi",
 'rs_open_hint': "Do'kon nomi, manzil, logo, shrift, chekda nimalar chiqishi va printer sozlamasi — namunani ko'rib turib.",
 'rs_sec_preset': 'Andoza', 'rs_preset_std': 'Standart', 'rs_preset_1c': '1C uslubi', 'rs_preset_compact': 'Ixcham 58 mm',
 'rs_preset_hint': "1C uslubi — do'kon 1C chekidagidek: hamma matn Arial qalin. Keyin istalgan bandni o'zgartirishingiz mumkin.",
 'rs_sec_texts': 'Matnlar', 'rs_sub': 'Sarlavha osti (manzil)', 'rs_sub_hint': "Do'kon nomi ostida, har qator alohida (4 tagacha)",
 'rs_thanks': 'Oxirgi xabar', 'rs_thanks_hint': 'Masalan: Xaridingiz uchun rahmat!',
 'rs_sec_logo': 'Logo', 'rs_logo': 'Logo', 'rs_logo_pick': 'Rasm tanlash', 'rs_logo_remove': 'Olib tashlash',
 'rs_logo_show': 'Logoni chiqarish', 'rs_logo_width': 'Logo eni: %1$d%%', 'rs_logo_bad': "Rasmni o'qib bo'lmadi yoki u juda katta",
 'rs_sec_font': "Qog'oz va shrift", 'rs_paper_80': '80 mm', 'rs_paper_58': '58 mm', 'rs_font_system': 'Tizim shrifti', 'rs_font_arial': 'Arial (1C)',
 'rs_bold': 'Hamma matn qalin', 'rs_header_serif': "Do'kon nomi serifli shriftda",
 'rs_text_size': "Matn o'lchami: %1$d%%", 'rs_header_size': "Do'kon nomi o'lchami: %1$d%%",
 'rs_sec_show': 'Chekda nimalar chiqsin', 'rs_show_staff': 'Xodim', 'rs_show_number': 'Chek raqami', 'rs_show_time': 'Vaqt', 'rs_show_date': 'Sana',
 'rs_show_client': 'Mijoz', 'rs_show_before': 'Oldingi qarz', 'rs_show_lineno': 'Qator raqami (1, 2, 3…)', 'rs_show_frame': 'Qatorlar ramkada',
 'rs_show_after': "To'lov va qarz qoldig'i", 'rs_show_footer': 'Chek osti (telefonlar)', 'rs_show_notes': 'Kichik izohlar',
 'rs_sec_printer': 'Printer', 'rs_dark': 'Qoralik: %1$d / 13', 'rs_feed': "Oxirida bo'sh joy: %1$d mm",
 'rs_test_print': 'Sinov cheki', 'rs_share_sample': 'Namunani ulashish',
 'rs_print_hint': "Sinov cheki tanlangan mijozning chek printeriga boradi. 2-ilovalar ham shu ko'rinishda chop etadi (keyingi yuborishdan keyin).",
 'rs_no_printer': "Chek printeri sozlanmagan: tanlangan mijozda printer navbati yo'q",
 'rs_reset': 'Standartga qaytarish', 'rs_reset_msg': "Ko'rinish standart holatga qaytadi (matnlar saqlanadi).",

})

STRINGS.update({
 # v3.0: Agent / Admin, sozlamalarni ko'chirish
 'restore_has_settings': "Ichida sozlamalar ham bor (server, bulut, mijozlar, printerlar) — ular ham tiklanadi.",
 'restore_restart_title': 'Sozlamalar tiklandi', 'restore_restart_msg': 'Yangi sozlamalar bilan ishlash uchun ilova qayta ishga tushadi.',
 'restore_restart': 'Qayta ishga tushirish',
 'login_transfer': "Agent ilovadan ko'chirish", 'login_transfer_pick': 'Faylni tanlash',
 'login_transfer_msg': "Agent ilovada: Sozlamalar ▸ Zaxira nusxa ▸ Saqlash — parol qo'yib saqlang. Keyin shu faylni tanlang: ma'lumot va sozlamalar (server, bulut, mijozlar, printerlar) Admin'ga o'tadi.",
 'login_transfer_no_settings': "Bu zaxirada sozlamalar yo'q. Agent ilovada zaxirani parol bilan saqlang.",

})

STRINGS.update({
 # v3.0: agentlar "Admin orqali"
 'srv_mode_title': 'Ulanish', 'srv_mode_server': "Server (Wi-Fi / Tailscale)", 'srv_mode_admin': 'Admin orqali (internet)',
 'srv_mode_admin_hint': "Server, Tailscale va do'kon Wi-Fi'si kerak emas — faqat internet. Savdo va to'lovlarni serverga Admin telefoni yozadi; Admin internetda bo'lmasa savdolar navbatda kutadi.",
 'srv_relay_code': 'Admin ulanish kodi', 'srv_relay_code_hint': "Admin ilova ▸ Mijozlar va 2-ilova ▸ Agentlar ko'prigi ▸ kodni yuborish",
 'srv_relay_code_bad': "Kod noto'g'ri. Admin yuborgan kodni to'liq nusxalab qo'ying (IMSAG1: bilan boshlanadi).",
 'srv_relay_ok': "Admin bilan aloqa bor. Admin oxirgi marta: %1$s",
 'err_admin_offline': "Admin javob bermadi. Admin telefonida internet va \"Agentlar ko'prigi\" yoqilganini tekshiring.",
 'err_admin_never': "Admin hali ulanmagan: Admin ilovada \"Agentlar ko'prigi\"ni yoqing.",
 'err_admin_no_catalog': "Admin hali katalogni joylamagan. Admin telefonida ko'prik yoqilganini tekshiring.",
 'err_admin_wait': "Admin'ga yetdi, server tasdig'i kutilmoqda (Admin internetga ulanganda yoziladi).",
 'lb_printer_admin': 'Printer: Admin orqali',
 'rl_title': "Agentlar ko'prigi", 'rl_open': "Agentlar ko'prigi", 'rl_channel': "Agentlar ko'prigi",
 'rl_share_hint': "Agentlar faqat internet bilan ishlaydi: katalog, qarz, hisobot va savdolarni serverga shu telefon yetkazadi.",
 'rl_on_short': "Yoqilgan — agentlar Admin orqali ishlaydi", 'rl_off_short': "O'chiq",
 'rl_sec_state': 'Holat', 'rl_switch': "Ko'prik yoqilgan",
 'rl_switch_hint': "Fonda ishlaydi (bildirishnoma ko'rinadi). Telefon internetda va serverga ulana oladigan bo'lsin (do'kon Wi-Fi'si yoki Tailscale).",
 'rl_pump': 'Hozir tekshirish', 'rl_publish': 'Katalogni joylash',
 'rl_sec_code': 'Agent ulanish kodi',
 'rl_code_hint': "Agent ilovada: Sozlamalar ▸ Server ▸ \"Admin orqali\" ▸ shu kodni qo'ying. Kodni faqat o'z agentlaringizga bering.",
 'rl_code_share': 'Kodni yuborish', 'rl_code_copy': 'Nusxa olish', 'rl_code_copied': 'Kod nusxalandi',
 'rl_sec_how': 'Qanday ishlaydi',
 'rl_how': "• Agent katalog, do'konlar, qarz va kursni shu telefon joylagan nusxadan oladi (20 daqiqada yoki agent so'raganda yangilanadi).\n• Savdo va to'lovlar shu telefon orqali serverga yoziladi: har birining o'z kaliti bor, takror yozilmaydi; agentda \"yuborildi\" faqat server tasdig'idan keyin.\n• Agent savdosining cheki 2-filial printeriga ham shu yerdan ketadi.\n• Yorliqlar shu telefonning yorliq printeri sozlamasi bilan chiqadi.\n• Telefon o'chsa yoki internet bo'lmasa — agentlar ishlayveradi, savdolar navbatda kutadi.",
 'rl_need_cloud': "Avval shu ekrandagi Firebase sozlamasini to'ldiring.",
 'rl_rules_failed': "Firebase qoidalarini yangilab bo'lmadi. Internet va Firebase sozlamasini tekshiring.",
 'rl_state_off': "O'chiq. Agentlar \"Admin orqali\" ishlay olmaydi.", 'rl_state_starting': 'Ishga tushmoqda…',
 'rl_state': "Oxirgi ish: %1$s\nServerga yozildi: %2$d · so'rovlar: %3$d · yorliqlar: %4$d\nKatalog joylangan: %5$s",
 'rl_state_error': 'Xato: %1$s',
 'rl_notif_starting': "Agentlar ko'prigi ishga tushmoqda", 'rl_notif_ok': 'Oxirgi: %1$s · serverga yozildi: %2$d',
 'rl_notif_error': "Oxirgi: %1$s · serverga ulanib bo'lmadi",

})

STRINGS.update({
 # v3.1: 4 narx, yorliqni moslash
 'price_l1': '1', 'price_l2': '2', 'price_l3': '3', 'price_l4': '4',
 'price_l1_desc': '1-narx', 'price_l2_desc': '2-narx', 'price_l3_desc': '3-narx (naqd)', 'price_l4_desc': '4-narx (optom)',
 'lb_fit_title': "Qog'ozga moslash",
 'lb_fit_hint': "\"Kalibrlash yorlig'i\"ni chiqaring: ramka qog'oz chetiga to'g'ri tushishi kerak. Ramka chapga surilgan bo'lsa siljitishni + tomonga suring. Ramka qog'ozdan kichik yoki katta bo'lsa — \"Boshqa o'lcham\"da haqiqiy o'lchamni yozing.",
 'lb_shift_x': "Chap ↔ o'ng siljitish: %1$s mm", 'lb_shift_y': "Tepa ↕ past siljitish: %1$s mm", 'lb_density': 'Qoralik: %1$d',
 'lb_calib': "Kalibrlash yorlig'i", 'lb_calib_sent': "Kalibrlash yorlig'i yuborildi", 'lb_custom_size': "Boshqa o'lcham",
 'lb_size_w': 'Eni, mm', 'lb_size_h': "Bo'yi, mm",

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
# Format belgilari to'liq himoyalanadi: %1$s, %1$02d, %4$.2f, %% (v2.9: kengligi/aniqligi bor belgilar ham)
PROTECT = re.compile(r'(%(?:\d+\$)?[-#+0,(]*\d*(?:\.\d+)?[a-zA-Z]|%%|\{\{.*?\}\})')

# Lotinda qolishi kerak bo'lgan nomlar (v2.4): avtomatik {{...}} ichiga olinadi.
BRANDS = ['Gmail', 'Wi-Fi', 'Excel', 'HTML', 'Telegram', 'SMS', 'Firebase', 'Google', 'Tailscale', 'Play Market', 'PIN', 'KB',
          'xlsx', 'xls', 'htm', 'mxl', 'VPN', 'IMSOFT', 'Click', 'USB', 'ESC/POS', 'forwarder.ini', 'forwarder.py', 'Python',
          'OTG', 'Xiaomi', 'HyperOS', 'Redmi', 'DOKON', 'Windows', 'Xprinter', 'PLU', 'IP', 'dpi', 'MXL',
          'start_labels.bat', 'label_printer.py', 'pip install pillow pywin32', 'onec_export.py', 'LPD Print Service', 'LPD', 'xprinter', 'Свойства принтера', 'Доступ', 'Общий доступ', 'ORNATISH.bat', 'Telegram', 'Arial']

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

STRINGS.update({
 # v3.2 — Admin boshqaruv
 'ad_title': 'Boshqaruv',
 'ad_hint': "Admin'ning qo'shimcha imkoniyatlari bo'limlarga ajratilgan: 1C ma'lumotlari va 1C'ga kiritish, mijozlar, agentlar, tovarlar, ilova.",
 'ad_card_sub': "1C savdolar, ta'minotchilar, 1C'ga kiritish, mijoz so'rovlari, kompyuter",
 'ad_sec_1c': '1C (kompyuter orqali)',
 'ad_sales': "1C savdolar ro'yxati", 'ad_sales_sub': 'Oxirgi 30 kun, istalgan davr, qidiruv',
 'ad_sales_st': '%1$d ta hujjat · %2$s',
 'ad_supp': "Ta'minotchilar", 'ad_supp_sub': "Qarzimiz, kirimlar, to'lovlar, qaytarishlar",
 'ad_supp_st': "%1$d ta ta'minotchi",
 'ad_writes': "1C'ga kiritish navbati", 'ad_writes_sub': 'Tasdiqlangan yozuvlar, natijasi, bekor qilish',
 'ad_writes_st': 'Kutmoqda: %1$d · xato: %2$d',
 'ad_writes_wait': "1C'ga kiritish kutmoqda: %1$d",
 'ad_new_receipt': "Tovar kirimi (1C'ga)", 'ad_new_receipt_sub': "Ta'minotchidan kelgan tovar",
 'ad_client_return': "Mijoz qaytargan tovar (1C'ga)", 'ad_client_return_sub': "Qaytgan tovar 1C'ga kiritiladi",
 'ad_pc': 'Kompyuter (1C)', 'ad_pc_sub': 'Holat, yangilash tezligi, ish vaqti, kompyuter sozlamasi',
 'ad_sec_clients': 'Mijozlar',
 'ad_requests': "Mijoz so'rovlari", 'ad_requests_sub': "2-ilovadan to'lov va chek tuzatish",
 'ad_requests_new': "Yangi so'rov: %1$d", 'ad_requests_none': "Yangi so'rov yo'q",
 'ad_debtors': 'Qarzdorlar', 'ad_debtors_sub': 'Qarzi kattalari birinchi',
 'ad_share': 'Mijozlar va 2-ilova', 'ad_share_sub': 'Ulanish, xodimlar, printer, yuborish',
 'ad_receipt_style': "Chek ko'rinishi", 'ad_receipt_style_sub': 'Logo, shrift, qatorlar',
 'ad_sec_agents': 'Agentlar', 'ad_relay': "Agentlar ko'prigi",
 'ad_sec_goods': 'Tovarlar',
 'ad_labels': 'Narx yorliqlari', 'ad_labels_sub': '{{Xprinter}}, PLU va shtrix',
 'ad_barcodes': 'Shtrix kodlar', 'ad_barcodes_sub': "Tarozi formati, telefonda bog'langan kodlar",
 'ad_sec_app': 'Ilova', 'ad_settings': 'Sozlamalar', 'ad_settings_sub': 'Server, til, zaxira',
 'ad_none_yet': 'Hali kelmagan — kompyuter yoqilganini tekshiring',
 'ad_error': 'Xato: %1$s',
 'ad_product_pick': 'Tovar tanlang', 'ad_product_search': 'Tovar nomi yoki kodi', 'ad_product_code': 'Kod: %1$s',
 'ad_qty': 'Miqdori', 'ad_price_som': "Narxi (so'm)", 'ad_price_usd': 'Narxi ($)',
 # Avtomatik yangilash
 'au_title': 'Avtomatik yangilash va zaxira', 'au_sub': 'Ish vaqti, tezlik, kunlik zaxira',
 'au_hint': "Ish vaqtida ma'lumot (narx, qarz, mijozlar) o'zi yangilanadi. Ish tugashidan 1 daqiqa oldin hammasi to'liq yuklab olinadi — keyin internetsiz ham ko'rinadi.",
 'au_auto': 'Avtomatik yangilash', 'au_every': 'Ish vaqtida har',
 'au_work_on': 'Ish vaqtida tez (tashqarida — 15 daqiqada)',
 'au_start': 'Boshlanishi', 'au_end': 'Tugashi',
 'au_final_hint': "Ilova yopiq bo'lsa ham telefon 15 daqiqada bir yangilaydi. Oxirgi yuklash aynan vaqtida bo'lishi uchun \"Aniq vaqt ruxsati\"ni bering.",
 'au_exact': 'Aniq vaqt ruxsati',
 'au_backup': 'Kunlik zaxira', 'au_backup_on': "Har kuni avtomatik (eskisi almashadi)",
 'au_backup_now': 'Hozir zaxiralash', 'au_restore': 'Zaxiradan tiklash', 'au_backup_done': 'Zaxira saqlandi',
 'au_backup_last': 'Oxirgi zaxira: %1$s', 'au_backup_never': 'Hali zaxira olinmagan', 'au_backup_where': 'Fayl: %1$s',
 'au_restore_text': "%1$s dagi zaxira tiklanadi: hozirgi tarix va navbat almashadi. Davom etasizmi?",
 'au_restored': 'Tiklandi: %1$d ta yozuv',
 # Kompyuter
 'pc_refresh_status': 'Holatni yangilash',
 'pc_live': 'Tezkor yangilash',
 'pc_live_hint': "Kompyuter shu oraliqda bugungi o'zgarishlar, 1C savdolar, ta'minotchilar va tovar kodlarini yuboradi (faqat o'zgarganda).",
 'pc_off': "O'chiq", 'pc_min': '%1$d daq',
 'pc_full': "To'liq hisobot", 'pc_full_hint': "Hamma mijozlar kartochkasi shu oraliqda to'liq yangilanadi.",
 'pc_hours': '%1$d soat', 'pc_days': '%1$d kun',
 'pc_work': 'Ish vaqti',
 'pc_work_on': "%1$s – %2$s · oxirgi to'liq yuklash %3$s",
 'pc_work_off': "Ish vaqti o'chiq — doim bir xil tezlikda",
 'pc_work_change': "Ish vaqtini o'zgartirish",
 'pc_setup': 'Kompyuter sozlamasi',
 'pc_setup_hint': "Kompyuterga yangi o'rnatishda: shu matnni yuboring, ORNATISH.bat Bloknot ochganda joylab saqlang.",
 'pc_codes_now': '1C kodlarini hozir yangilash',
 'pc_saved': "Saqlandi — kompyuter 30 soniyada o'qiydi",
 'pc_write_hint': "1C'ga kiritish: kompyuter har kuni birinchi yozuvdan oldin baza nusxasini oladi (backup papkasi, 7 kun) va hamma yozuvlarni jurnalga yozadi.",
 # 1C savdolar
 'oc_sales_title': '1C savdolar', 'oc_search': 'Mijoz, tovar yoki hujjat raqami',
 'oc_f_all': 'Hammasi', 'oc_f_sales': 'Savdo', 'oc_f_retail': 'Chakana', 'oc_f_returns': 'Qaytish',
 'oc_last30': 'Oxirgi 30 kun', 'oc_range': 'Davr tanlash',
 'oc_empty': "Hujjat yo'q",
 'oc_none': "Ma'lumot hali kelmagan. Kompyuterda {{onec_export}} ishlayotganini tekshiring.",
 'oc_requested': "So'rov yuborildi — kompyuter 1 daqiqa ichida yuboradi",
 'oc_row_sub': '%1$s №%2$s · %3$s · %4$d qator',
 'oc_period': 'Davr: %1$s – %2$s · olingan %3$s',
 'oc_errors': 'Ogohlantirish: %1$s',
 'oc_total': '%1$d ta hujjat · jami %2$s',
 'oc_no_cloud': "{{Firebase}} sozlanmagan yoki kompyuter ulanmagan",
 'oc_request_sent': "So'rov yuborildi",
 'oc_doc_head': '%1$s №%2$s · %3$s',
 'oc_line': '%1$s × %2$s = %3$s',
 'oc_doc_total': 'Jami: %1$s',
 'oc_k_sale': 'Savdo', 'oc_k_mobile': 'Mobil savdo', 'oc_k_retail': 'Chakana', 'oc_k_return': 'Qaytarish',
 'oc_k_receipt': 'Kirim', 'oc_k_pay': "To'lov", 'oc_k_sret': "Ta'minotchiga qaytarildi", 'oc_k_other': 'Hujjat',
 # Ta'minotchilar
 'sp_title': "Ta'minotchilar", 'sp_search': 'Nomi yoki telefon', 'sp_new_receipt': 'Yangi kirim', 'sp_refresh': 'Yangilash',
 'sp_empty': "Ta'minotchilar hali kelmagan",
 'sp_docs': '%1$d ta hujjat', 'sp_settled': "Hisob-kitob yo'q", 'sp_we_owe': 'qarzimiz', 'sp_they_owe': 'ular qarzdor',
 'sp_info': '%1$d ta · jami qarzimiz %2$s · %3$s',
 'sp_balance_we': 'Qarzimiz: %1$s', 'sp_balance_they': 'Ular qarzdor: %1$s',
 'sp_receipt': 'Kirim', 'sp_pay': "To'lov", 'sp_return': 'Qaytarish',
 'sp_no_docs': "Hujjat yo'q (oxirgi 90 kun)",
 'sp_doc_sub': '№%1$s · %2$s · %3$d qator',
 # 1C'ga kiritish shakli
 'wf_receipt': 'Tovar kirimi', 'wf_pay': "Ta'minotchiga to'lov", 'wf_sreturn': "Ta'minotchiga qaytarish", 'wf_creturn': 'Mijoz qaytargan tovar',
 'wf_hint': "Hammasini tekshiring: tasdiqlagandan keyin kompyuter 1C'ga kiritadi va hujjat raqamini qaytaradi.",
 'wf_client': 'Mijoz', 'wf_supplier': "Ta'minotchi", 'wf_pick': 'Tanlash', 'wf_not_chosen': 'Tanlanmagan',
 'wf_goods': 'Tovarlar', 'wf_add': "Tovar qo'shish", 'wf_no_goods': "Tovar qo'shilmagan", 'wf_amounts': 'Summa',
 'wf_cash_som': "Naqd (so'm)", 'wf_cash_usd': 'Naqd ($)', 'wf_card_som': "Plastik (so'm)", 'wf_card_usd': 'Plastik ($)',
 'wf_note': 'Izoh', 'wf_total': 'Jami: %1$s', 'wf_confirm': 'Tasdiqlash',
 'wf_safety': "1C'ga kiritishdan oldin kompyuter baza nusxasini oladi. Xato bo'lsa — navbatda \"Bekor qilish\".",
 'wf_supplier_name': "Ta'minotchi nomi (1C'dagidek)",
 'wf_supplier_manual': "Ta'minotchilar ro'yxati hali kelmagan. Nomni 1C'dagidek aniq yozing.",
 'wf_edit': "O'zgartirish", 'wf_remove': 'Olib tashlash',
 'wf_err_party': 'Kontragentni tanlang', 'wf_err_lines': "Tovar qo'shing", 'wf_err_amount': 'Summani yozing',
 'wf_err_line': "Miqdor yoki narx noto'g'ri",
 'wf_sum_lines': '%1$d xil tovar',
 'wf_confirm_title': "1C'ga kiritilsinmi?", 'wf_confirm_text': "Kompyuter 1C'ga kiritadi va hujjatni o'tkazadi.",
 'wf_confirm_yes': 'Ha, kiritilsin',
 'wf_sent': "Navbatga qo'yildi", 'wf_sent_later': "Internet yo'q — navbatda turibdi, keyin yuboriladi",
 # Navbat
 'wr_title': "1C'ga kiritish",
 'wr_hint': "Kompyuter har 30 soniyada tekshiradi. \"Kiritildi\" — faqat 1C hujjat raqami kelgandan keyin.",
 'wr_empty': "Hali yozuv yo'q",
 'wr_barcode': 'Shtrix kod', 'wr_undo': 'Bekor qilish',
 'wr_st_unsent': 'Yuborilmadi (internet)', 'wr_st_wait': 'Kompyuterni kutmoqda', 'wr_st_undone': 'Bekor qilingan',
 'wr_st_skip': "1C'da bor edi", 'wr_st_done': "1C'ga kiritildi №%1$s", 'wr_st_err': 'Xato: %1$s',
 'wr_undo_btn': 'Bekor qilish', 'wr_undo_title': 'Bekor qilinsinmi?',
 'wr_undo_text': "1C'da hujjatga o'chirish belgisi qo'yiladi (o'tkazilmagan bo'ladi). Kerak bo'lsa 1C'da belgini olib tiklash mumkin.",
 'wr_resend': 'Qayta yuborish',
 # Mijoz so'rovlari
 'cr_hint': "2-ilova (Mijoz) yuborgan to'lov va chek tuzatish so'rovlari. To'lov tasdiqlansa serverga yoziladi; mijozda chek server tasdiqlagandan keyin chiqadi.",
 'cr_pays': "To'lovlar", 'cr_edits': 'Chek tuzatishlari', 'cr_none': "Yo'q",
 'cr_st_ok': 'Tasdiqlandi (server: %1$s)', 'cr_st_no': 'Rad etildi', 'cr_st_acc': 'Qabul qilindi — serverga yozilmoqda', 'cr_st_new': 'Yangi',
 'cr_m_card': 'plastik', 'cr_m_click': '{{Click}}', 'cr_m_cash': 'naqd',
 'cr_edit_lines': '%1$d qator',
 'cr_pay_title': "To'lov so'rovi",
 'cr_pay_text': '%1$s\n%2$s (%3$s)\nIzoh: %4$s\n\n%5$s',
 'cr_accept': 'Tasdiqlash', 'cr_reject': 'Rad etish', 'cr_accepted': 'Qabul qilindi — serverga yuborilmoqda',
 'cr_reason': "Sababi (mijozga ko'rinadi)",
 'cr_edit_title': 'Chekni tuzatish', 'cr_edit_doc': 'Chek: %1$s', 'cr_edit_new': "Mijoz so'ragan qatorlar:",
 'cr_edit_done': "Tasdiqlandi — mijoz chekida o'zgaradi",
 'cr_err_no_store': "Bu mijoz do'konga bog'lanmagan", 'cr_err_no_client': 'Mijoz topilmadi',
 'cr_pay_note': '2-ilova:', 'cr_notif_title': "Mijoz so'rovi", 'cr_notif_text': "Yangi so'rovlar: %1$d",
 # Qarzdorlar
 'dbt_search': "Do'kon yoki hudud", 'dbt_empty': "Qarzdor yo'q",
 'dbt_info': '%1$d ta qarzdor · jami %2$s · %3$s holatiga',
 'dbt_sync': 'Qarzlarni yangilash', 'dbt_payment': "To'lov qabul qilish",
 # Yakunlash: chek qayerda
 'fs_where': 'Chek qayerda chiqsin', 'fs_here': "Do'konda", 'fs_branch': '2-filialda',
 # Sozlamalar
 'st_more': "Ko'proq sozlamalar", 'st_less': 'Kamroq',
 # Vidjet
 'wg_title': 'Tez savdo', 'wg_desc': "Sevimli do'konlar bilan tez savdo, skaner, to'lov",
 'wg_fav_empty': "Sozlamalarda tez do'kon tanlang", 'wg_scan': 'Skaner', 'wg_pay': "To'lov",
})

# v3.6 Admin: 1C tarixi — barcha mijozlar (serverdan, 1 yil)
STRINGS.update({
 'sh_title': '1C tarixi: qayerdan va qachon',
 'sh_status': 'Server: %1$s · %2$d/%3$d mijoz yuklandi · 1 yillik: %4$d',
 'sh_status_none': "Server: hali yuklanmadi (%1$d mijoz). Admin ochiq va Tailscale yoqiq bo'lsin",
 'sh_status_running': 'Yuklanmoqda: %1$s',
 'sh_status_err': 'Xato (%1$s): %2$s',
 'sh_details_how': "Qanday ishlaydi:\n"
                   "• Kompyuter 1C'dagi BARCHA savdolarni (savdo, mobil, chakana, vozvrat) yuboradi: har necha daqiqada oxirgi 30 kun, keyin 1 yilgacha orqaga 30 kunlik bo'laklar bilan.\\n"
                   "• Barcha mijozlarning 1C kartochkasi (savdo, vozvrat, to'lov) serverdan olinadi — Admin telefoni Tailscale orqali serverni ko'rsa.\n"
                   "• Avval har mijozning oxirgi 31 kuni, keyin 1 yilgacha orqaga.\n"
                   "• Savdo yoki to'lov qilinsa — o'sha mijoz 2.5 va 6 daqiqadan keyin qayta olinadi (1C qabul qilgach).\n"
                   "• Faol mijozlar har soatda, qolganlari kuniga bir yangilanadi.\n"
                   "• Kompyuter 1C'dan \"Mijoz kartochkasi\" yuborsa — u ham shu yerga qo'shiladi.\n"
                   "• 2-ilova (Mijoz) o'z kartochkasini shu ma'lumotdan oladi.\n"
                   "Mijoz nomi bilan qidirish mumkin: masalan \"dadaxon\".",
 'sh_details_parse': "Server javobi %1$d marta jadval sifatida o'qilmadi. \"Javobni ulashish\" bilan faylni yuboring.",
 'sh_details_errors': 'Oxirgi xatolar:',
 'sh_err_agent': 'Agent topilmadi (katalogni yangilang)',
 'sh_err_parse': "Server javobi o'qilmadi: %1$s",
 'sh_refresh': 'Hozir yangilash',
 'sh_refresh_started': "Yangilash boshlandi — bir necha daqiqada to'ladi",
 'sh_share_fail': 'Javobni ulashish',
 # v3.7: kompyuterdagi barcha savdolar
 'ah_loading': 'Yuklanmoqda…',
 'ah_server_wait': 'Server: kutilmoqda',
 'ah_retail': 'Chakana',
 'ah_pc': 'Kompyuter (1C): %1$s – %2$s · %3$d ta savdo/vozvrat · %4$s olingan',
 'ah_pc_filling': "1 yilga to'ldirilmoqda",
 'ah_pc_none': "Kompyuter (1C): savdolar hali kelmagan. Do'kon kompyuterida dastur ishlab turishi kerak",
 'ah_pc_last': 'Kompyuter oxirgi xabari (%1$s): %2$s',
 'ah_pc_never': "Kompyuterdan hech qanday xabar kelmagan (Boshqaruv ▸ Kompyuter (1C) sozlamasini tekshiring)",
 'ah_empty': "1C tarixi hali bo'sh. Yuqoridagi holat qatorini bosing: qayerdan, qachon keladi va nima yetishmayapti",
 'ah_agent_status': "Admin'dan: %1$s yangilangan · %2$d yozuv (do'kondagi barcha 1C savdolari, 1 yil). Bosing — hozir yangilash",
 'ah_agent_no_link': "Agent Admin'ga ulanmagan: Sozlamalar ▸ \"Admin orqali\" — Admin bergan kodni kiriting",
 'ah_agent_wait': "Admin hali yubormagan: Admin ilovasi yangi versiyada, ochiq va Tailscale yoqiq bo'lishi kerak",
 'ah_agent_empty': "1C tarixi hali kelmagan. Yuqoridagi holat qatorida sababi yozilgan",
 'ah_agent_syncing': 'Yangilanmoqda…',
 # v3.10: tarozi
 'sc_title': 'Taroziga yuborish', 'sc_sub': "Narx va PLU — 1C'dagi \"Taroziga yuklash\" kabi",
 'sc_hint': "Do'kon kompyuteri 1C'dan taroziga biriktirilgan tovarlarni (PLU va 1-narxi bor) oladi va tarozi dasturi orqali yuboradi — 1C'dagi tugma bilan aynan bir xil. Kompyuter yoqiq va dastur ishlab turishi kerak.",
 'sc_stock': 'Narxlarni yuborish', 'sc_stock_hint': "Faqat qoldig'i bor tovarlar. Tarozidagi boshqa tovarlar o'chirilmaydi.",
 'sc_clear': 'Tozalab, hammasini yuborish', 'sc_clear_hint': "Tarozi tozalanadi va taroziga biriktirilgan hamma tovar qaytadan yuklanadi.",
 'sc_clear_confirm': "Tarozidagi hamma tovar o'chiriladi va 1C'dagi ro'yxat qaytadan yuklanadi. Davom etasizmi?",
 'sc_result': 'Natija', 'sc_none': "Hali yuborilmagan", 'sc_waiting': "Kompyuterga yuborildi, javob kutilmoqda (30 soniyagacha)…",
 'sc_sent': 'Kompyuterga yuborildi', 'sc_ok': '%1$s: %2$s', 'sc_fail': 'Xato (%1$s): %2$s',
 'sc_timeout': "Kompyuter 3 daqiqada javob bermadi. Boshqaruv ▸ Kompyuter (1C) holatini tekshiring.",
})

STRINGS.update({
 # v3.12: Shtrix kod -> 1C
 'bb_title': "Shtrix kod → 1C", 'bb_sub': "Ko'p kodni skanerlab, bitta tugma bilan 1C'ga",
 'bb_hint_admin': "1) \"Skanerlash\" — kodni o'qiting, pastdagi ro'yxatdan tovarini bosing. Istagancha kod va tovar.\n2) \"1C'ga yozish\" — hammasi kompyuter orqali 1C'dagi o'sha tovarning shtrix kodlari ro'yxatiga yoziladi (bir necha soniyada).",
 'bb_hint_agent': "1) \"Skanerlash\" — kodni o'qiting, pastdagi ro'yxatdan tovarini bosing. Istagancha kod va tovar.\n2) \"1C'ga yozish\" — ro'yxat Admin'ga ketadi, Admin telefoni kompyuter orqali 1C'ga yozadi.",
 'bb_scan': 'Skanerlash (kamera)', 'bb_manual': "Kodni yozing yoki skaner qurilmasi bilan o'qiting",
 'bb_manual_hint': "Enter (yoki +) bosilsa — tovar tanlash oynasi ochiladi",
 'bb_list': "Ro'yxat: %1$d ta kod", 'bb_empty': "Ro'yxat bo'sh. \"Skanerlash\"ni bosing.",
 'bb_item_sub': 'Shtrix kod: %1$s', 'bb_send': "1C'ga yozish (%1$d ta)", 'bb_clear': "Ro'yxatni tozalash",
 'bb_clear_q': "Ro'yxatdagi hamma kod o'chiriladi (1C'ga yozilmaydi). Davom etasizmi?",
 'bb_change_product': 'Tovarni almashtirish', 'bb_pick_other': 'Boshqa tovar',
 'bb_bad_code': "Shtrix kod noto'g'ri (4-40 ta harf yoki raqam)",
 'bb_added': "Ro'yxatga qo'shildi: %1$s", 'bb_already': "Ro'yxatda bor: %1$s",
 'bb_live': "● Shtrix kod → 1C", 'bb_scan_hint': "Kodni o'qiting, keyin pastdagi ro'yxatdan shu tovarni bosing",
 'bb_to_list': "Ro'yxat", 'bb_count': "Ro'yxatda: %1$d ta", 'bb_bar_hint': "Tugatgach — \"Ro'yxat\" ▸ \"1C'ga yozish\"",
 'bb_confirm_title': "1C'ga yozish", 'bb_confirm': "%1$d ta shtrix kod 1C'dagi tovarlarga yoziladi. Davom etasizmi?", 'bb_confirm_yes': 'Yozish',
 'bb_no_route': "Bu rejimda 1C'ga yozib bo'lmaydi: agent \"Admin orqali\" ulangan bo'lishi (yoki Admin'da bulut sozlangan bo'lishi) kerak",
 'bb_sent_admin': "%1$d ta kod kompyuterga yuborildi — natija shu yerda ko'rinadi",
 'bb_sent_agent': "%1$d ta kod Admin'ga yuborildi — Admin kompyuter orqali 1C'ga yozadi",
 'bb_admin_offline': "Admin javob bermadi: Admin telefoni yoqiq va internetda bo'lishi kerak. Ro'yxat saqlanib qoldi.",
 'bb_admin_old': "Admin ilovasi eski: Admin'ni yangilang. Ro'yxat saqlanib qoldi.",
 'bb_results_sub': "Har kodning natijasi (yozildi / xato) va bekor qilish",
 # v3.12: Boshqaruv - 1C'ga yozish
 'ad_sec_write': "1C'ga yozish",
 'of_client_pay': "Mijozdan to'lov", 'of_client_pay_sub': "1C'ga to'lov hujjati (naqd, plastik, Click, chegirma)",
 'of_client_pay_hint': "Mijoz to'lagan pul 1C'dagi \"Оплата клиента\" hujjati bo'lib kiradi va uning qarzidan ayriladi.",
 'of_cash_in': 'Kassa kirim', 'of_cash_in_sub': "Kassaga boshqa kirim (1C: Касса приход)",
 'of_cash_in_hint': "Kassaga tushgan boshqa pul (savdo va mijoz to'lovidan tashqari). Kassa moddasini tanlang.",
 'of_cash_out': 'Kassa chiqim (xarajat)', 'of_cash_out_sub': "Xarajat: ijara, svet, ish haqi… (1C: Касса расход)",
 'of_cash_out_hint': "Kassadan chiqqan pul (xarajat). 1C'dagi xarajatlar va foyda hisobotiga ham tushadi.",
 'of_goods_edit': 'Narx, PLU, nom', 'of_goods_edit_sub': "Tovar kartochkasi: 1-4 narx, kirim narxi, PLU, nomi",
 'of_goods_edit_hint': "Tovarni tanlang — hozirgi narxlari yoziladi. Faqat o'zgartirganingiz 1C'ga yoziladi; xato bo'lsa \"Bekor qilish\" eski qiymatni qaytaradi.",
 'of_new_goods': 'Yangi tovar', 'of_new_goods_sub': "Namuna tovardan nusxa: nomi, narxi, shtrix kodi",
 'of_new_goods_hint': "Avval o'xshash (namuna) tovarni tanlang — guruhi, o'lchov birligi va valyutasi shundan olinadi. Keyin yangi nom, narx va (bo'lsa) shtrix kodni yozing.",
 'of_supp_pay_sub': "Ta'minotchiga to'langan pul", 'of_supp_return_sub': "Ta'minotchiga qaytarilgan tovar",
 'of_op': 'Kassa moddasi', 'of_op_name': 'Modda nomi (1C\'dagidek aniq)',
 'of_ops_none': "Kassa moddalari ro'yxati kompyuterdan hali kelmagan (kompyuterdagi dastur yangilanishi kerak). Nomini 1C'dagidek aniq yozing.",
 'of_sample': 'Namuna tovar', 'of_product': 'Tovar',
 'of_product_info': 'Kodi: %1$s · narxlar %2$sda', 'of_cur_som': "so'm", 'of_cur_usd': 'dollar',
 'of_new_name_sec': 'Yangi tovar', 'of_new_name': 'Yangi tovar nomi', 'of_barcode': 'Shtrix kod (ixtiyoriy)',
 'of_prices_sec': 'Narxlar (%1$sda)',
 'of_p1': '1-narx', 'of_p2': '2-narx', 'of_p3': '3-narx (Naqd)', 'of_p4': '4-narx (Optom)', 'of_cost': 'Kirim narxi',
 'of_other_sec': 'Boshqa', 'of_plu': 'PLU (tarozi kodi)', 'of_name': 'Tovar nomi',
 'of_only_changed': "Faqat o'zgartirilgan maydonlar yuboriladi.",
 'of_click_som': "Click (so'm)", 'of_click_usd': 'Click ($)', 'of_discount_som': "Chegirma (so'm)",
 'of_err_product': 'Tovarni tanlang', 'of_err_op': 'Kassa moddasini tanlang', 'of_err_empty': "Hech narsa o'zgartirilmadi",
 'of_err_price': "Narx noto'g'ri", 'of_err_plu': "PLU noto'g'ri (0-99999)", 'of_err_name': "Tovar nomi kamida 2 harf bo'lsin",
})

STRINGS.update({
 # v4.0: kalitsiz ishlash (qurilma kutish ekrani)
 'dw_title': "Telefon ro'yxatga olindi",
 'dw_text': "Admin ilovasida ▸ Boshqaruv ▸ Qurilmalar'da shu telefonni tanlab, kimligini belgilaydi. Shundan keyin ilova o'zi ochiladi — hech narsa yozish shart emas.",
 'dw_phone': '%1$s\nTelefon kodi: %2$s',
 'dw_wait': 'Admin belgilashini kutyapmiz…', 'dw_offline': "Internet yo'q — ulanish kutilmoqda…",
 'dw_blocked': 'Bu telefon bloklangan', 'dw_blocked_sub': "Admin bu telefonni to'xtatgan. Savol bo'lsa do'kon egasiga murojaat qiling.",
 'dw_other': 'Ulanish kodi bilan kirish',
 # v4.0: Admin ▸ Qurilmalar
 'dv_title': 'Qurilmalar', 'dv_sub': "Agent va Mijoz telefonlari: kimligi, bloklash, xatolar",
 'dv_hint': "Agent va Mijoz ilovasi ochilishi bilan telefon shu yerda paydo bo'ladi. Bosing va kimligini belgilang — telefonda kod yoki parol kerak emas. Bloklangan telefonda ma'lumot ko'rinmaydi.",
 'dv_shop_code': "Do'kon kodi (GitHub uchun)", 'dv_shop_code_sub': "Bir marta: GitHub'ga SHOP_CODE nomi bilan qo'yiladi",
 'dv_shop_code_how': "Shu kodni nusxalab GitHub'ga qo'ying (bir marta):\nrepo ▸ Settings ▸ Secrets and variables ▸ Actions ▸ New repository secret\nName: SHOP_CODE\nSecret: kod\n\nShundan keyingi build'dagi Agent va Mijoz ilovalari kalitsiz ishlaydi va o'zi yangilanadi.\n\n%1$s",
 'dv_iphone': 'iPhone uchun havola', 'dv_iphone_sub': "iPhone agentga yuboring — bosishi bilan ulanadi",
 'dv_iphone_text': "Savdo Agent (iPhone): havolani Safari'da oching, keyin Ulashish ▸ \"Bosh ekranga qo'shish\".\n%1$s",
 'dv_new': 'Yangi — belgilang', 'dv_agents': 'Agentlar', 'dv_clients': 'Mijoz telefonlari', 'dv_blocked': 'Bloklangan', 'dv_admins': 'Admin',
 'dv_empty': "Hali hech qaysi telefon yo'q. Agent yoki Mijoz ilovasini (yangi versiya) oching — shu yerda paydo bo'ladi.",
 'dv_code': 'kodi %1$s', 'dv_seen': 'oxirgi: %1$s', 'dv_crash': '⚠ xato bor', 'dv_updated': 'Yangilangan: %1$s',
 'dv_offline': "Internet yo'q — oxirgi ma'lum ro'yxat",
 'dv_as_agent': 'Agent sifatida belgilash', 'dv_as_client': 'Mijozga biriktirish', 'dv_block': 'Bloklash', 'dv_unblock': 'Blokdan chiqarish',
 'dv_block_q': "Bu telefonda ma'lumot ko'rsatilmaydi va undan savdo qabul qilinmaydi. Bloklaysizmi?",
 'dv_show_crash': "Xatoni ko'rish", 'dv_remove': "Ro'yxatdan o'chirish", 'dv_done': 'Saqlandi — telefon bir daqiqada o\'zgaradi',
 'dv_agents_fail': "Agentlar ro'yxatini serverdan olib bo'lmadi (Tailscale yoqilganmi?)",
 'dv_no_clients': "Mijozlar yo'q: avval Boshqaruv ▸ Mijozlar bilan ulashishda mijoz qo'shing",
 'dv_label': 'Ism (masalan: Dadaxon aka — Sardor)', 'dv_label_hint': "Bu ism savdo va so'rovlarda \"kim\" bo'lib ko'rinadi",
 'dv_crash_title': 'Xato (%1$s)', 'dv_copy': 'Nusxalash', 'dv_copied': 'Nusxalandi',
})

STRINGS.update({
 # v4.0: o'zi yangilanish
 'up_title': 'Yangi versiya: %1$s', 'up_body': "Ilovaning yangi versiyasi tayyor. O'rnatilsa ma'lumotlar saqlanib qoladi.",
 'up_install': "O'rnatish", 'up_later': 'Keyinroq', 'up_loading': 'Yuklanmoqda…', 'up_error': "Yuklab bo'lmadi: %1$s",
 'up_permission': "Telefon ruxsat so'radi: ochilgan oynada \"Ruxsat berish\"ni yoqing, orqaga qayting va yana \"O'rnatish\"ni bosing.",
 'up_none': "Eng yangi versiya o'rnatilgan", 'up_off': "Bu versiyada o'zi yangilanish yoqilmagan (GitHub'da SHOP_CODE qo'yilmagan)",
 'up_check': 'Yangilanishni tekshirish',
})

STRINGS.update({
 # v4.0: yagona ro'yxat
 'history_all': 'Hammasi', 'pending_1c': "⏳ 1C'ga ketmoqda",
 'of_suggest': '4-narx taklifi: %1$s + 2 000 = %2$s', 'of_suggest_sub': "Oxirgi kelgan narx bo'yicha. Bosing — 4-narxga yoziladi (o'zi qo'yilmaydi)",
 'pr_shop': "Printer 1 (do'kon)", 'pr_branch': 'Printer 2 (filial)', 'pr_both': 'Ikkalasi',
})

STRINGS.update({
 # v4.0: statistika
 'stt_title': 'Statistika', 'stt_hint': "1C bazasidagi savdo va to'lovlar bo'yicha (kompyuterdan kelgan; telefonda qilingan, hali 1C'ga yetmagan savdo qo'shilmaydi). Ustunni bosing — o'sha kun ko'rsatkichlari chiqadi.",
 'stt_day': 'Kun', 'stt_week': 'Hafta', 'stt_month': 'Oy', 'stt_year': 'Yil',
 'stt_sales': 'Savdo', 'stt_payments': "To'lov", 'stt_returns': 'Qaytarish', 'stt_profit': 'Foyda',
 'stt_selected': '%1$s: %2$s', 'stt_total': '%1$s bo\'yicha, grafikdagi jami: %2$s',
 'stt_top': "Eng ko'p sotilgan tovarlar", 'stt_prices': "Narxi o'zgargan tovarlar", 'stt_empty': "Bu davrda savdo yo'q", 'stt_change': '%1$s → %2$s',
 # v4.0.1: ko'proq statistika
 'stt_kpi_for': 'Tanlangan: %1$s', 'stt_k_sales': 'Savdo', 'stt_k_profit': 'Foyda', 'stt_k_docs': 'Cheklar soni',
 'stt_k_avg': "O'rtacha chek", 'stt_k_clients': 'Mijozlar', 'stt_k_pay': "To'lovlar",
 'stt_share_clients': 'Mijozlar ulushi', 'stt_share_goods': 'Tovarlar ulushi', 'stt_other': 'Boshqalar',
 'stt_fast': 'Tez sotilayotgan tovarlar', 'stt_fast_sub': '%1$d kunda sotildi · kuniga ~%2$s',
 'stt_weekdays': "Hafta kunlari bo'yicha", 'stt_best_day': "Eng ko'p savdo: %1$s",
 'stt_wd_1': 'Du', 'stt_wd_2': 'Se', 'stt_wd_3': 'Ch', 'stt_wd_4': 'Pa', 'stt_wd_5': 'Ju', 'stt_wd_6': 'Sh', 'stt_wd_7': 'Ya',
 'stt_hours': "Kun soatlari bo'yicha", 'stt_best_hour': 'Eng gavjum vaqt: %1$s:00–%2$s:00',
 'stt_idle': "To'xtab qolgan tovarlar", 'stt_idle_sub': '%1$d kundan beri sotilmayapti · oldin %2$d kunda sotilgan',
 'stt_no_goods': "Savdolarning tovar qatorlari hali kelmagan — kompyuter dasturi ishlab turganini tekshiring (1C savdolari tovarlari bilan keladi).",
 # v4.0: Boss
 'boss_title': 'Boss', 'boss_card_sub': "Bugungi savdo, foyda, qarzlar, agentlar, tizim holati",
 'boss_line': 'Bugun: %1$s · foyda %2$s · tushum %3$s',
 'boss_today': 'Bugun', 'boss_docs_vs': '%1$d ta hujjat · kecha %2$s', 'boss_profit_sub': "Sotilgan narx − oxirgi kelgan narx",
 'boss_income': 'Tushum (to\'lovlar)', 'boss_income_sub': "Mijozlar to'lagan pul",
 'boss_debts': 'Qarzlar', 'boss_debt_total': 'Jami qarz · %1$d ta mijoz', 'boss_debt_sub': "Serverdan oxirgi yangilangan. Bosing — ro'yxat",
 'boss_agents': 'Agentlar (bugun)', 'boss_agents_none': "Bugun agentlardan savdo kelmadi",
 'boss_agent_id': 'Agent #%1$s', 'boss_agent_sub': "%1$d ta yozuv · to'lov %2$s",
 'boss_system': 'Tizim holati', 'boss_pc': 'Kompyuter (1C)', 'boss_server': 'Server (SQL)', 'boss_cloud': 'Bulut (Firebase)',
 'boss_cloud_sub': "Agent va Mijoz telefonlari bilan aloqa", 'boss_last': 'oxirgi: %1$s', 'boss_never': "hali aloqa bo'lmagan",
 'rt_title': 'Dollar kursi', 'rt_sub': "1C'dagi kursni telefondan o'zgartirish",
 'rt_hint': "Yangi kurs 1C'ga yoziladi: bundan keyingi hujjatlar shu kurs bilan. Xato bo'lsa \"Bekor qilish\" eskisini qaytaradi.",
 'rt_now': 'Hozirgi kurs: %1$s', 'rt_new': "Yangi kurs (1 $ = so'm)", 'rt_bad': "Kurs noto'g'ri", 'rt_confirm': 'Kurs: %1$s → %2$s',
 'boss_paid_ago': "oxirgi to'lov %1$d kun oldin", 'boss_paid_never': "to'lov ko'rinmadi (1 yil)",
 'ev_title': 'Bugungi hisobot', 'ev_text': "Savdo: %1$s (%2$d ta)\nFoyda: %3$s\nTushum: %4$s\nUmumiy qarz: %5$s",
 'boss_ok': '✓ ishlayapti', 'boss_bad': '⚠ tekshiring', 'boss_devices_sub': '%1$d tasi onlayn (jami %2$d)', 'boss_crashes': '%1$d tasida xato',
})

STRINGS.update({
 # v4.0: sozlamalar (qaytadan)
 'sg_admin': 'Admin', 'sg_agent': 'Agent', 'sg_version': 'versiya %1$s',
 'sg_font': 'Yozuv o\'lchami', 'sg_font_s': 'Kichik', 'sg_font_m': "O'rta", 'sg_font_l': 'Katta',
 'sg_work': 'Ish', 'sg_queue': 'Yuborilmagan navbat', 'sg_stats_sub': 'Kun, hafta, oy, yil grafiklari',
 'sg_scanner': 'Skaner va tarozi kodlari', 'sg_scanner_sub': "Shtrix kod sozlamalari, sinash, tovush",
 'sg_system': 'Tizim (faqat Admin)', 'sg_server_sub': "SQL server manzili (Tailscale)",
 'sg_help': 'Yordam', 'sg_update_sub': "Yangi versiya bormi — tekshirish va o'rnatish", 'sg_diag': 'Diagnostika',
 'sg_more': 'Boshqa sozlamalar', 'sg_more_sub': "Sevimli do'konlar, zaxira, qo'shimcha imkoniyatlar (eski to'liq ekran)",
})

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
