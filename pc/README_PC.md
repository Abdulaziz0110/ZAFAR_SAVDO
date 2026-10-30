# Kompyuter dasturlari

Ikkalasi ham bitta `forwarder.ini` sozlamasidan foydalanadi. Uni 1-ilova (Savdo Agent) beradi: **Mijozlar ▸ "Kompyuter sozlamasi"**.
Ichida Firebase secret bor — faqat o'z kompyuteringizda saqlang, boshqalarga bermang.

## 1. `onec_export.py` — 1C → Excel → telefon (avtomatik)

1C 8.3 bazadan har bir mijoz va yuk beruvchining kartochka hisobotini Excel qilib, 1-ilovada tanlangan oraliqda
(har 1, 2 yoki 3 soat; 0 — o'chiq) telefonga yuboradi. Telefon o'zi yuklab, 1C tarixiga qo'shadi.

* 1C'ga **hech narsa yozilmaydi**: dastur faqat hisobot tuzadi. Konfigurator va server paroli kerak emas, oddiy 1C foydalanuvchisi yetadi.
* Faqat o'zgargan hisobotlar yuboriladi (internet va telefon zaryadi tejaladi).
* Talab: Windows, 1C:Enterprise 8.3, Python 3.8+, `pip install pywin32`.

### O'rnatish

1. `forwarder.ini` ni shu papkaga saqlang. `[onec]` bo'limiga quyidagilarni yozing:
   * `connection`: baza manzili, masalan `File="D:\1C\Baza";` yoki `Srvr="server";Ref="baza";`;
   * `user`, `password`: 1C foydalanuvchisi.
2. `discover.bat` ni ishga tushiring. U `discover.txt` faylini yaratadi: bazadagi hisobotlar, ma'lumotnomalar va registrlar ro'yxati (ichida parol yo'q). Shu faylni dasturchiga yuboring: `report` va boshqa qatorlar sizning bazangizga moslab beriladi.
3. Sinab ko'rish uchun `python onec_export.py --one "Dadaxon Aka (TRZ)"` buyrug'ini bering. Fayl `export\` papkasida qoladi va telefonga ham ketadi.
4. `start_export.bat` bilan bir marta to'liq eksport qiling. Ishlasa, `onec_export.vbs` yorlig'ini `Win+R ▸ shell:startup` papkasiga qo'ying.

### Buyruqlar

| Buyruq | Nima qiladi |
|---|---|
| `python onec_export.py` | Doimiy ishlash (odatda `onec_export.vbs` orqali oynasiz) |
| `python onec_export.py --once` | Hozir bir marta eksport qilish |
| `python onec_export.py --one "Nom"` | Faqat bitta kontragentni eksport qilish (sinov uchun) |
| `python onec_export.py --discover` | 1C tuzilishini `discover.txt` ga yozish |
| `python onec_export.py --status` | Bulutdagi jadval, oxirgi holat va kutayotgan fayllar |

### `[onec]` sozlamalari

| Kalit | Ma'nosi |
|---|---|
| `report` | `Отчеты.<nom>`: kartochka hisoboti (discover.txt dan) |
| `build` | `skd` (standart, СКД hisobot) yoki `method` (oddiy hisobot, `method = ProtseduraNomi`) |
| `period_from`, `period_to` | Davr parametrlari (standart: `НачалоПериода`, `КонецПериода`) |
| `client_field` | Kontragent parametri yoki filtr maydoni (standart: `Контрагент`) |
| `catalog` | Kontragentlar ma'lumotnomasi (standart: `Контрагенты`) |
| `supplier_flag` | "Yuk beruvchi" belgisi bo'lgan rekvizit (bo'lsa telefonda alohida belgilanadi) |
| `active_register` | Faqat davr ichida harakati borlarni olish uchun registr (tezroq ishlaydi) |
| `skip` / `only` | `;` bilan ajratilgan nomlar: tashlab ketiladiganlar / faqat shular |

Jurnal: `onec_export.log`. Holat 1-ilovada ham ko'rinadi (**Mijozlar ▸ Kompyuterdan avtomatik**).

## 2. `forwarder.py` — chek uzatgichi

1C → Windows printer `filial2` (127.0.0.1:9100, Raw) → **forwarder.py** → Google Firebase → mijoz telefonidagi
**Savdo Mijoz** ilovasi → USB chek printeri.

* Faqat Python standart kutubxonasi kerak (pip shart emas), Python 3.8+.
* `forwarder.ini` dagi `queue` qatori chek printeri yoqilgan mijozniki bo'lishi kerak: 1-ilova ▸ mijoz ▸ "Chek printerini yoqish".
* O'rnatish va tekshirish tartibi: `docs/EGA_QOLLANMA.md` ▸ (h).

| Buyruq | Nima qiladi |
|---|---|
| `python forwarder.py` | Ishga tushirish (odatda `forwarder.vbs` orqali oynasiz) |
| `python forwarder.py --test` | Sinov chekini yuborish |
| `python forwarder.py --status` | Diskdagi va bulutdagi navbat, printer holati |
| `python forwarder.py --once` | Diskdagi navbatni bir marta yuborish |

Fayllar:
* `outbox/`: hali yuborilmagan cheklar;
* `failed/`: juda katta cheklar;
* `forwarder.log`: jurnal (1 MB × 4).

Chek baytlari o'zgartirilmaydi. Bulutga o'zgarmas kalit bilan yoziladi, shuning uchun javob yo'qolib qayta yuborilsa ham faqat bitta chek chiqadi.

## Testlar (1C va internetsiz)

```
python pc/test_forwarder.py
python pc/test_onec_export.py
```
