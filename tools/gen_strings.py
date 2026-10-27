#!/usr/bin/env python3
"""strings.xml (lotin, asosiy) va values-b+uz+Cyrl/strings.xml (kirill, avtomatik o'giriladi) yaratadi.
Kirill matnni qo'lda tahrirlash kerak bo'lsa, shu fayldagi OVERRIDES ga yozing."""
import re, os
from xml.sax.saxutils import escape

STRINGS = {
 # umumiy
 'app_name': '{{IMSOFT DOKON}}',
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
 'f_kpi_hint': 'Buyurtmaga ketgan vaqt va bosishlar soni. Faqat shu telefonda saqlanadi.',
 'kpi_empty': "Hali o'lchov yo'q",
 'kpi_summary': "Oxirgi %1$d ta buyurtma: o'rtacha (mediana) %2$d soniya, %3$d bosish",
 'server_password_saved': "Parol saqlangan. O'zgartirish uchun yangisini yozing, aks holda bo'sh qoldiring.",
})

# kirillni qo'lda to'g'rilash kerak bo'lgan joylar
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

def to_cyr(s):
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
