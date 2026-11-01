# Kompyuter dasturlari

## Eng oddiy yo'l: `ORNATISH.bat` (hammasini o'zi qiladi)
Kompyuterga 5 ta fayl kerak: `ORNATISH.bat`, `ornatish.py`, `onec_export.py`, `onec_data.py`, `onec_write.py` (masalan `D:\imsoft\` ga).
`ORNATISH.bat` ni ikki marta bosing — administrator so'raydi va ketma-ket:
1. eski `label_printer.py` ni to'xtatib, avtozapuskdan olib tashlaydi;
2. Xprinter'ni telefon uchun ulashadi (Windows LPD, nomi `xprinter`, 515-port) va sinov yorlig'i chiqaradi;
3. `forwarder.ini` uchun Bloknot ochadi — telefondagi "Kompyuter sozlamasi" matnini joylab saqlaysiz;
4. 1C bazasini ro'yxatdan tanlatadi, foydalanuvchi va parolni so'raydi (`onec.ini`);
5. 1C'dan PLU va shtrix kodlarni olib ko'radi (topilmasa `discover.txt` yaratadi);
6. `onec_export.py` ni avtozapuskka qo'yadi va fonda ishga tushiradi.
Qayta ishga tushirsa bo'ladi — bajarilganlarini so'rab o'tadi. Kompyuterda server/localhost dasturi qolmaydi:
printerga Windows'ning o'zi, 1C kodlariga `onec_export.py` (faqat internetga chiqadi) xizmat qiladi.

Quyidagilar — batafsil va qo'lda sozlash uchun.

Hammasi bitta papkada turadi (masalan `C:\imsoft\`). Kerak: Python 3.8+ va bir marta `pip install pywin32 pillow`.

| Dastur | Nima uchun | Birinchi marta (oynada) | Doimiy (yorlig'i `Win+R` ▸ `shell:startup` ga) |
|---|---|---|---|
| `onec_export.py` | 1C → telefon: mijoz kartochkalari + **PLU va shtrix kodlar** | `start_export.bat`, `codes.bat`, `discover.bat` | `onec_export.vbs` |
| `label_printer.py` | Telefon → Xprinter (narx yorliqlari) | `start_labels.bat` (+ kerak bo'lsa `allow_firewall.bat` administrator sifatida) | `label_printer.vbs` |
| `forwarder.py` | 1C cheki → 2-filial printeri (faqat shu kerak bo'lsa) | `python forwarder.py --test` | `forwarder.vbs` |

Sozlama fayllari:
* `forwarder.ini` — 1-ilova beradi (Mijozlar ▸ "Kompyuter sozlamasi" yoki Narx yorliqlari ▸ Yorliq printeri ▸ "Kompyuter sozlamasi").
  Ichida Firebase secret bor — faqat o'z kompyuteringizda saqlang. Yangisi kelsa — shunchaki almashtiring.
* `onec.ini` — **o'zingiz yozasiz** (namuna: `onec.ini.example`): `[onec]` (1C ga ulanish, hisobot) va kerak bo'lsa `[codes]`. Alohida faylda turgani uchun
  `forwarder.ini` almashtirilganda o'chib ketmaydi (eski usul — `[onec]` ni `forwarder.ini` ichida saqlash — ham ishlaydi).

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

### Tovar kodlari (PLU va shtrix) — v2.7
`onec_export.py` 1C'dan **tarozi PLU raqamlari** va **shtrix kodlarni** ham oladi (faqat o'qiydi) va telefonga yuboradi:
har 6 soatda (`[codes] hours`), o'zgargan bo'lsa, yoki 1-ilova ▸ Narx yorliqlari ▸ ⋮ ▸ "Kompyuterdan kodlar (1C)" ▸
**Hozir yangilash** bosilganda (kompyuter 2 daqiqada bir tekshiradi). `[onec] report` bo'sh bo'lsa ham ishlaydi.

* Joyi 1C tuzilishidan o'zi topiladi: PLU — tovarning "PLU/Код весов" rekviziti yoki "весы / оборудование / PLU" registri;
  shtrix — "Штрихкод" maydonli registr. Qaysi biri olinganini `python onec_export.py --codes` ko'rsatadi (`codes.json` ham yoziladi).
* Topilmasa: `python onec_export.py --discover` ▸ `discover.txt` ning oxiridagi "Tovar kodlari" bo'limini yuboring
  yoki `[codes] plu = ...` ni qo'lda yozing (`forwarder.ini.example` da namunalar).

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

## 3. Narx yorlig'i printeri (Xprinter, USB)

**Asosiy yo'l — dasturisiz:** `ORNATISH.bat` ni ishga tushiring (Windows LPD xizmati,
printer "xprinter" nomi bilan ulashiladi, 515-port). Telefonda "Ulashilgan printer" rejimi. Quyidagi `label_printer.py` —
faqat internet orqali chop etish kerak bo'lsa.

### `label_printer.py` (ixtiyoriy)

1-ilova ▸ **Narx yorliqlari** ▸ "PLU narx" / "Shtrixli narx" → telefon yorliqni rasm qilib chizadi →
Wi-Fi orqali shu kompyuterga (TCP 9110) yoki internet orqali (Firebase `L/{kalit}`) → **label_printer.py** → USB Xprinter.

* Kerak: Python 3.8+, `pip install pillow pywin32`.
* `forwarder.ini` dagi `[labels]` bo'limi: 1-ilova ▸ Narx yorliqlari ▸ Yorliq printeri ▸ **Kompyuter sozlamasi** (fayl bor bo'lsa almashtiring).
* `mode = gdi` (standart): Windows drayveri orqali, 1C kabi. Yorliq o'lchami drayverdagi qog'oz o'lchamidan olinadi va telefonga aytiladi
  (Printerlar ▸ Xprinter ▸ Свойства ▸ Настройка ▸ Бумага: 58×40 mm kabi). `mode = tspl` — drayversiz, to'g'ridan-to'g'ri TSPL.
* Telefon kompyuterni o'zi topadi (UDP 9111). Topmasa: `allow_firewall.bat` ni administrator sifatida bir marta ishga tushiring
  yoki IP manzilni qo'lda yozing (`start_labels.bat` oynasida ko'rinadi).
* Bitta ish ikki yo'ldan (Wi-Fi va internet) kelsa ham bir marta chiqadi (`labels_done.json`).
* **Ulanmasa tekshirish:** telefon brauzerida `http://<kompyuter IP>:9110` ni oching. "IMSOFT yorliq printeri ishlayapti" chiqsa —
  tarmoq va dastur joyida (ilovaga shu IP ni yozing). Ochilmasa — `allow_firewall.bat` (administrator) yoki routerdagi "AP isolation".

| Buyruq | Nima qiladi |
|---|---|
| `start_labels.bat` | Printerlar ro'yxati + oynada ishga tushirish (birinchi marta shuni oching) |
| `python label_printer.py --list` | Windows printerlari, yorliq o'lchami, qaysi biri tanlanadi |
| `python label_printer.py --test` | Sinov yorlig'i |
| `label_printer.vbs` | Oynasiz ishga tushirish (yorlig'ini `shell:startup` ga qo'ying) |

## Testlar (1C va internetsiz)

```
python pc/test_forwarder.py
python pc/test_onec_export.py
python pc/test_label_printer.py
```


## v3.2 — 1C savdolar, ta'minotchilar, 1C'ga kiritish
* `onec_data.py` — 1C savdolar ro'yxati va ta'minotchilar (faqat o'qish), Admin jadvali yoki so'rovi bo'yicha.
* `onec_write.py` — Admin'da tasdiqlangan yozuvlarni 1C'ga kiritadi: kirim, ta'minotchiga to'lov, qaytarishlar, shtrix kodlar.
  Har kuni birinchi yozuvdan oldin `backup\1Cv8_YYYYMMDD.1CD` (oxirgi 7 ta), jurnal `write_journal.jsonl`.
  O'chirish: `onec.ini` ga `[write]` / `enabled = 0`. Zaxira olinmasa ham yozish: `require_backup = 0` (tavsiya etilmaydi).
* Sozlama (`onec.ini`, ixtiyoriy): `[data]` `sales_days = 30`, `supp_days = 90`, `enabled = 1`.
* Test: `python test_onec_data.py`.
