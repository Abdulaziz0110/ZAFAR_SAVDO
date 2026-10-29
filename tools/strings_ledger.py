"""1C tarixi to'plami (ui/ledger) satrlari — ikki ilovada BIR XIL. gen_strings.py va gen_strings_mijoz.py qo'shib oladi."""

LEDGER = {
 'lg_unit_som': "so'm", 'lg_ok': 'OK', 'lg_cancel': 'Bekor qilish', 'lg_close': 'Yopish',
 'lg_error': 'Xato: %1$s',
 'lg_kind_sale': 'Savdo', 'lg_kind_return': 'Vozvrat', 'lg_kind_payment': "To'lov", 'lg_kind_payment_return': "To'lov qaytarildi",
 'lg_kind_discount': 'Chegirma', 'lg_kind_other': 'Boshqa',
 'lg_items_n': '%1$d xil tovar', 'lg_docs_n': '%1$d ta', 'lg_more_lines': '… yana %1$d ta qator',
 'lg_today': 'Bugun', 'lg_yesterday': 'Kecha',
 'lg_wd_1': 'dushanba', 'lg_wd_2': 'seshanba', 'lg_wd_3': 'chorshanba', 'lg_wd_4': 'payshanba', 'lg_wd_5': 'juma', 'lg_wd_6': 'shanba', 'lg_wd_7': 'yakshanba',
 'lg_search_hint': 'Qidirish: tovar, kg, narx, sana',
 'lg_search_helper': "Masalan: pista 40kg · 200000 · 27.10 · to'lov >1mln",
 'lg_help_title': 'Qanday qidiriladi',
 'lg_help_text': "Bitta qatorga istalganini aralash yozing — hammasi birga hisobga olinadi:\n\n"
                 "• Tovar: pista, bodom, орик (lotin/kirill, xato yozilsa ham)\n"
                 "• Miqdor: 40kg, 2,5 kg, 12 ta\n"
                 "• Narx yoki summa: 200000, 200 000, 200k, 3,7mln, 15$\n"
                 "• Summa chegarasi: >1mln, <500000, 1mln-5mln\n"
                 "• Sana: 27.10, 27.10.2026, 27.10-29.10, 27 oktabr, oktabr, bugun, kecha\n"
                 "• Vaqt: 16:21\n"
                 "• Turi: savdo, vozvrat, to'lov · valyuta: dollar, so'm\n"
                 "• Chiqarib tashlash: -pista\n\n"
                 "\"pista 40\" — aynan 40 kg pista bo'lgan savdolar. Aniq topilmasa eng yaqinlari \"taxminiy\" deb ko'rsatiladi.",
 'lg_f_all': 'Hammasi', 'lg_f_sales': 'Savdo', 'lg_f_returns': 'Vozvrat', 'lg_f_payments': "To'lov",
 'lg_period': 'Davr', 'lg_p_all': 'Hamma vaqt', 'lg_p_today': 'Bugun', 'lg_p_7': '7 kun', 'lg_p_month': 'Bu oy',
 'lg_p_last_month': "O'tgan oy", 'lg_p_90': '90 kun', 'lg_p_custom': 'Sanani tanlash…',
 'lg_found_n': 'Topildi: %1$d ta hujjat',
 'lg_sum_sales': 'Savdo: %1$d ta · %2$s',
 'lg_sum_returns': 'Vozvrat: %1$d ta · %2$s',
 'lg_sum_payments': "To'lov: %1$d ta · %2$s",
 'lg_sum_goods': 'Mos tovarlar: %1$d qator · %2$s miqdor · %3$s',
 'lg_approx': 'taxminiy',
 'lg_approx_note': "Aniq mos keladigan yozuv yo'q — eng yaqinlari ko'rsatilmoqda",
 'lg_no_data': "1C tarixi hali yo'q. 1C'dan \"Mijoz kartochkasi\" faylini yuklang.",
 'lg_nothing_found': 'Hech narsa topilmadi',
 'lg_clear_filters': 'Qidiruv va filtrni tozalash',
 'lg_from_1c': "1C hisobotidan · o'zgartirilmaydi",
 'lg_items_qty': 'Tovarlar · miqdor',
 'lg_total': 'Jami', 'lg_discount': 'Chegirma', 'lg_payable': 'Chegirma bilan', 'lg_balance_after': 'Qoldiq (shundan keyin)',
 'lg_client': 'Mijoz',
 'lg_receipt': 'Chek', 'lg_receipt_title': '%1$s cheki (nusxa)',
 'lg_receipt_note': "1C hisobotidagi ma'lumot asosida chiqarilgan nusxa",
 'lg_print': 'Chop etish', 'lg_pdf': 'PDF',
 'lg_share': 'Ulashish', 'lg_share_image': 'Rasm', 'lg_share_text': 'Matn', 'lg_copy_text': 'Matnni nusxalash', 'lg_copied': 'Nusxalandi',
 'lg_ph_summary': 'Jami: %1$d marta · %2$s miqdor · %3$s',
 'lg_cash': 'Naqd', 'lg_wholesale': 'Optom',
 'lg_note_hint': 'Izoh (ixtiyoriy)',
 'lg_rp_approx': '≈ katalogda: %1$s (tekshiring)',
 'lg_rp_none': "Katalogda topilmadi — qo'shilmaydi",
 'lg_rp_selected': 'Tanlandi: %1$d / %2$d',
 'lg_pdf_query': 'Qidiruv: %1$s', 'lg_pdf_list': "Ro'yxatni PDF / chop etish",
 'lg_col_time': 'Vaqt', 'lg_col_what': 'Nima', 'lg_col_qty': 'Miqdor', 'lg_col_price': 'Narxi', 'lg_col_sum': 'Summa',
}
