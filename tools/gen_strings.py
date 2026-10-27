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
 'finish_round': 'Yaxlitlash', 'finish_store_debt': "Do'kon qarzi: %1$s", 'finish_queued': 'Buyurtma qabul qilindi, serverga yuborilmoqda',
 'payment_queued': 'To\'lov qabul qilindi, serverga yuborilmoqda',
 'home_pending_title': 'Yuborilmagan: %1$d ta', 'home_pending_retry': 'Hozir yuborish',
 'home_pending_hint': "Internet paydo bo'lganda avtomatik yuboriladi",
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
    s = s.replace("'", "\\'").replace('"', '\\"')
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
