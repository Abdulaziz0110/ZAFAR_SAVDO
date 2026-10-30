# CHANGELOG

## 2.8 (versionCode 10) + Savdo Mijoz 1.3 (versionCode 4) — 2026-10-30
(2.7 dagi narx yorliqlari, tovar kodlari va quyidagilar)
Server, protokol (tur 1, 3, 4, 5), baza, applicationId va imzo o'zgarmagan.

### Kompyuter: bitta `ORNATISH.bat`
* `pc/ORNATISH.bat` + `ornatish.py`: administrator, pywin32, printer ulashish (LPD, sinov yorlig'i), forwarder.ini (Bloknot),
  1C bazasi ro'yxatdan tanlanadi + foydalanuvchi/parol (onec.ini), PLU/shtrix sinovi, avtozapusk, eski label_printer tozalanadi.
* Ilovada faqat "Ulashilgan printer" (kompyuter dasturi rejimi yashirildi). Narx yorlig'i ▸ ⋮ ▸ Kompyuterdan kodlar (1C) ▸
  **Kompyuter sozlamasi** — forwarder.ini matni shu yerdan ham olinadi.
* `onec_export.py`: Bloknot ANSI'da saqlagan ini ham o'qiladi; bir vaqtda faqat bitta nusxa ishlaydi.

### Yorliq printeri: kompyuterda dastur shart emas (Windows "ulashilgan printer")
* Yangi rejim **"Ulashilgan printer"** (standart): telefon Xprinter'ga Windows'ning o'z LPD xizmati orqali to'g'ridan-to'g'ri
  yuboradi — Python, .bat, forwarder.ini kerak emas; 1C ham avvalgidek chiqaraveradi. Yorliq TSPL buyrug'i (rasm) bo'lib boradi.
* Kompyuterda bir marta: `pc/ORNATISH.bat` — LPD yoqiladi, printer "xprinter" nomi bilan ulashiladi, 515-port.
* "Kompyuterni topish" — Wi-Fi tarmog'ida 515-port ochiq kompyuterlarni qidiradi. "Yorliq teskari chiqsa" — 180°.
* Eski yo'l ("IMSOFT dasturi", label_printer.py) ham qoldi — internet orqali chop etish kerak bo'lsa.

### Tovar kodlari: PLU va shtrix 1C'dan avtomatik
* `pc/onec_export.py` 1C'dan tarozi PLU raqamlari va shtrix kodlarni oladi (joyi metama'lumotdan o'zi topiladi; faqat o'qiydi)
  va telefonga yuboradi: har 6 soatda (o'zgargan bo'lsa) yoki 1-ilovada "Kompyuterdan kodlar (1C)" ▸ "Hozir yangilash".
  Yangi buyruq: `python onec_export.py --codes`; `--discover` oxirida "Tovar kodlari" bo'limi.
* 1-ilova: kelgan kodlar narx yorliqlari va shtrix kod bo'limiga yoziladi (qo'lda yozilgan PLU ustun), katalog yangilansa qayta bog'lanadi.
* Ro'yxatlarda har tovar ostida **"Kod 2555 · PLU 501"**: 1-ilovada savdo, skaner, yorliqlar; 2-ilovada Narxlar (PLU bo'yicha qidirish ham).

### Savdo Mijoz: yashirin menyu -> "Qo'shimcha sozlamalar"
* Faqat ilova sozlamalari va o'z ma'lumotingiz bo'yicha foydali bandlar: **ochilish sahifasi** (Bosh sahifa / Narxlar / Tarix),
  **ekran o'chmasin**, avto-qorong'i, ekran himoyasi, ko'rsatmalar, bildirishnoma sinovi, kesh tozalash, oxirgi xaridni
  takrorlash, qarz dinamikasi, top-10 tovar, CSV, kurs kalkulyatori, hozir yangilash.
* Olib tashlandi: "Ulanish pasporti" (do'kon kodi, kalitlar), fon ishlari, qurilma pasporti, qidiruv laboratoriyasi,
  rulet/hikmat/bayram. 1-ilovaning sirli menyusi o'zgarmagan.

### Narx yorliqlari (Xprinter) — 1-ilova
* Bosh sahifa ▸ **Narx yorlig'i** (Sozlamalar ▸ Narx yorliqlari): tovarlarni qidirib yoki skanerlab ro'yxatga yig'ish,
  har biriga nechta yorliq; Naqd/Optom narx yoki qo'lda narx; filtrlar "Tanlangan", "PLU bor", "Shtrix bor".
* Ikki tugma: **PLU narx** (nom, "KOD: 501", katta narx — tarozi tovarlari) va **Shtrixli narx** (nom, narx, EAN-13/EAN-8/Code 128).
  Chop etishdan oldin namunalar; PLU/shtrix kodi yo'qlar sababi bilan ko'rsatiladi va shu yerning o'zida tuzatiladi.
* PLU raqamlari 1C "PLU Narx" faylidan (Excel yoki .mxl) yuklanadi yoki qo'lda yoziladi.
* Kamera (to'xtamasdan) yoki ulanadigan Bluetooth/USB skaner bilan tovar qo'shish; topilmagan kod ro'yxatdan bog'lanadi.
* Printer kompyuterda (USB): yangi `pc/label_printer.py` — Wi-Fi orqali darhol, bo'lmasa internet (Firebase) navbati;
  telefon kompyuterni o'zi topadi; o'lcham Windows drayveridan olinadi; bitta ish ikki marta chiqmaydi. "Sinov yorlig'i".

## 2.6 (versionCode 8) + Savdo Mijoz 1.2 (versionCode 3) — 2026-10-30
Server, `savollar`/`javoblar` protokoli (tur 1, 3, 4, 5) va fixture testlari, mavjud baza jadvallari, applicationId va
C8 biznes qoidalari **o'zgarmagan**. Yangi lokal ma'lumot alohida (eski v2.5 sozlamasi bir marta ko'chiriladi).

### Mayda tuzatishlar (ikkala ilova)
* Hisobot ▸ chop etish/PDF bosilganda ilova yopilib qolardi ("Can print only from an activity") — tuzatildi (chek va 1C tarixi
  PDF'i ham). Chop etishda hisobot doim oq qog'ozda.
* Hisobot qorong'i rejimda: oq fon ustida och kulrang (ko'rinmas) matnlar va kesilgan ustunlar — bitta mavzuga keltirildi,
  sarlavha/jami qatorlari ajratilgan, summalar bo'linmaydi, keng jadval yon tomonga suriladi.
* Bosh sahifa: "Yangi savdo" ostida bir xil katta **"Shtrix kod bilan savdo"** tugmasi.
* "Orqaga" ikki marta bosilganda chiqadi (2 soniya ichida); 2-ilovada avval bosh sahifaga qaytadi.
* Standart: qorong'i rejim va shrift 90% (o'zingiz o'zgartirgan sozlama saqlanadi).
* "−" ni uzoq bosish — tovar nolga tushadi (pastda "Qaytarish" bor). "+" ni uzoq bosish — blok (avvalgidek).
* "Printer 3" -> **"Printersiz"** (chek chiqmaydigan savdo).
* Yangi nom va ikonka: **Savdo Agent** (1-ilova), **Savdo Mijoz** (2-ilova).

### Universal: istalgan do'kon, barcha mijozlar
* 1-ilova ▸ **Mijozlar**: kompyuterdagi barcha mijoz va yuk beruvchilar. Har biriga — 1C tarixi, telefonlar, chek printeri.
  Excel yuklansa mijoz fayldagi nomdan o'zi topiladi (yangi bo'lsa qo'shiladi). Tarix ▸ 1C tarixi — mijozni tanlash.
* Har do'konning o'z Firebase loyihasi va **do'kon kodi**. Qadamma-qadam yo'riqnoma ilova ichida.
* 2-ilovaga **kirish oynasi**: do'kon kodi + telefon + parol, bir marta. Bitta mijozga bir nechta telefon (masalan 3 ta ishchi) —
  hammasi faqat o'sha mijoz ma'lumotini ko'radi. Telefonni o'chirsangiz u kira olmaydi, qolganlar o'zi qayta ulanadi.

### 1-ilovadagi savdo -> mijozning 2-ilovasi
* Ro'yxatdagi mijozga (do'konga bog'langan) qilingan har savdo/qaytarish uning 2-ilovasida **darhol** ko'rinadi (Tarix,
  "1-ilovadan" belgisi bilan — 1C hisoboti shu vaqtni qamragach o'zi 1C'dagi asl yozuv bilan almashadi).
* 2-ilova ▸ Sozlamalar ▸ **"1-ilova savdolari cheklari"** yoqilsa, har yangi savdo cheki uning printer navbatiga tushadi;
  printer ulanishi bilan navbatdagi hamma cheklar ketma-ket chiqadi. Printer bo'limida navbatdagi cheklar ro'yxati.
  Internet bo'lmasa chek 1-ilovada kutadi va keyin yuboriladi; bitta chek ikki marta chiqmaydi.
* **Chek ko'rinishi do'kon chekidek**: katta do'kon nomi, Xodim / Chek № / Vaqt va sana, Mijoz, oldingi qarz,
  "QAYTIB OLISH !!!", ramkali qatorlar ("1 ) Nom" / "40 x 117 000 = 4 680 000"), katta "Summa", to'lov, qarz qoldig'i (≈),
  telefonlar. 1-ilova ▸ Mijozlar ▸ Chek: do'kon nomi, Xodim, chek osti (telefonlar), "Namuna chekni ko'rish".
  1C tarixidagi cheklar ham shu ko'rinishda.

### Buyurtma (2-ilova -> 1-ilova)
* 2-ilova: Bosh sahifa ▸ **Buyurtma berish** / Narxlar ▸ tovar yonidagi **+** ▸ miqdor ▸ pastdagi **Yuborish** (izoh bilan).
  Sirli menyuda "Oxirgi xaridni takrorlash".
* 1-ilova: bildirishnoma, Bosh sahifa kartasi, "Buyurtmalar" ro'yxati (qaysi mijoz, kim yubordi). Tasdiqlash -> savatcha.

### Kompyuterdan avtomatik (1C -> Excel -> telefon)
* `pc/onec_export.py` (+ `discover.bat`, `start_export.bat`, `onec_export.vbs`): har 1/2/3 soatda (1-ilovada tanlanadi)
  har bir kontragent kartochkasini Excel qilib yuboradi; telefon o'zi import qiladi. Faqat o'zgarganlari yuboriladi.
  1C'ga hech narsa yozilmaydi. Holat 1-ilovada ko'rinadi.

### Shtrix kod bilan savdo (1-ilova)
* Savdo ekrani ▸ qidiruv yonidagi **skaner** tugmasi -> **skaner savdosi** ekrani: tepada ixcham kamera oynasi (doim yoniq,
  to'xtamasdan ketma-ket o'qiydi, chiroq, pauza, hisoblagich), ostida natija qatori, qidiruv va tovarlar ro'yxati (qo'lda ham
  +/− bilan qo'shiladi, "Savatchada" filtri), pastda jami va "Savatcha". Narx odatdagi qoida bilan (savdo turi, valyuta, kurs).
* Kod topilmasa kamera TO'XTAMAYDI: natija qatori "ro'yxatdan tovarni bosing" deydi — bosilgan tovar shu kodga bog'lanadi
  (eslab qolinadi) va qo'shiladi; bir nechta topilmagan kod navbatda turadi.
* Ulanadigan **Bluetooth/USB skaner** (klaviatura kabi): Savdo ekrani ochiq bo'lsa kodni o'qiting — tovar savatchaga tushadi.
* **Tarozi yorlig'i**: prefiks + tovar kodi + og'irlik — og'irlik miqdor bo'lib tushadi. 1C'dagi vazn tovarlarining 7 xonali
  kodlari (masalan `2900027`) yorliq boshiga mos keladi — tovar so'ramasdan topiladi; prefikslar (28, 29) importda o'zi sozlanadi.
  1C'da tekshiruv raqamisiz saqlangan o'z kodlarimiz (`250000000031`) EAN-13 bo'lib chop etilsa ham topiladi. Format sozlanadi
  (prefikslar, kod/og'irlik uzunligi, gramm/10 gramm, tekshiruv raqami).
* Sozlamalar ▸ **Shtrix kodlar**: 1C'dan "Вывести список" bilan saqlangan Excel'ni yuklash (ustunlar o'zi topiladi, tovarga
  avval kod, keyin nom bo'yicha bog'lanadi), sinash maydoni va kamera bilan sinash.
* **Kod yozish rejimi** (Shtrix kodlar ▸ "Kod yozish" yoki Savdo ▸ skaner tugmasini uzoq bosish): do'kon bo'ylab yurib
  bazada yo'q kodlarni o'qitasiz va ro'yxatdan tovarni bosasiz — kod telefonga yoziladi (bir tovarga bir nechta kod ham).
  "Excel'ga" — yozilganlar CSV (kod, tovar, shtrix kod) bo'lib ulashiladi, 1C'ga kiritish uchun. Zaxiraga ham kiradi.
* Noma'lum kod — "qaysi tovar?" deb so'raladi va eslab qolinadi (tarozi kodi bo'lsa — shu kodli hamma yorliqlar).

### O'rgatish va sirli menyu
* Ekranlar birinchi ochilganda tugmalar yoritilib, qisqa izoh chiqadi (har ekran bir marta; sirli menyudan qayta yoqiladi).
* Versiya yozuvini uzoq bosing — **Sirli xona**: qidiruv laboratoriyasi, top-10 tovar, CSV eksport, kurs kalkulyatori,
  tovar ruleti, kun hikmati, avto-qorong'i, ekran himoyasi, ko'rsatmalar, bildirishnoma sinovi, fon ishlari, qurilma pasporti,
  kesh tozalash, bayram 🎉; 1-ilovada + statistika, kunlik maqsad, savdo soatlari, turbo sinxron, bulut pasporti, sinov cheki;
  2-ilovada + statistika, qarz dinamikasi, oxirgi xaridni takrorlash, tezkor yangilash, ulanish pasporti.

## 2.5 (versionCode 7) + IMSOFT Mijoz 1.1 (versionCode 2) — 2026-10-29
Server, `savollar`/`javoblar` protokoli (tur 1, 3, 4, 5) va fixture testlari, mavjud baza jadvallari, applicationId va
C8 biznes qoidalari **o'zgarmagan**. Yangi lokal ma'lumot alohida; yangi funksiyalar sozlamada o'chiriladi.

### 1C tarixi — ikkala ilovada bir xil (`ui/ledger/`, `tools/sync_ledger_kit.py`)
* Import qilingan savdo, vozvrat va to'lovlar **hujjat** sifatida ro'yxatda (bir vaqtda yozilgan qatorlar — bitta savdo;
  haqiqiy fayl: 445 qator = 126 hujjat), kunlar bo'yicha, "Bugun/Kecha", hafta kuni bilan.
* Qidiruv (`shared/report/LedgerSearch.kt`): tovar nomi (lotin/kirill, xato, klaviatura tili), miqdor (`40kg`, `2,5 кг`),
  narx/summa (`200000`, `200 000`, `200k`, `3,7mln`, `15$`), chegaralar (`>1mln`, `1mln-5mln`), sana (`27.10`, `27.10-29.10`,
  `27 oktabr`, `oktabr`, `bugun`, `kecha`), vaqt (`16:21`), turi (`savdo`, `vozvrat`, `to'lov`), valyuta, istisno (`-pista`).
  Tovar shartlari bitta qatorda bajarilishi kerak ("pista 40" — aynan 40 kg pista); bo'lmasa "taxminiy" deb ko'rsatiladi.
  Filtrlar (Hammasi/Savdo/Vozvrat/To'lov, davr) va jami (topilgan hujjatlar, mos tovarlar miqdori va summasi).
* Hujjat oynasi: qatorlar (qidirilgani ajratilgan), jami, chegirma, "qoldiq (shundan keyin)", tovar tarixi (bosilsa).
* **Chek**: rasm ko'rinishi, chop etish, rasm (Telegram), matn, nusxa, PDF. Chek rasm bo'lib chop etiladi (ESC/POS rastr) —
  kirill/lotin harflari printer kod sahifasiga bog'liq emas. Sarlavha (do'kon nomi) 1-ilova sozlamasidan.
* 1-ilova: **Takrorlash** — tovarlar katalogga bog'lanadi (aniq/taxminiy/topilmadi), narx JORIY katalogdan, Naqd/Optom tanlanadi,
  savatcha bo'sh bo'lmasa almashtirish/qo'shish/kutishga qo'yish so'raladi. Chek -> **2-filial printeriga** (bulut navbati).
* 2-ilova: **Qayta buyurtma** — 1-ilovaga so'rov (savdo emas), izoh bilan; "Buyurtmalarim" (Yuborildi / 1-ilovaga yetib bordi /
  Savatchaga olindi / Rad etildi). Chek -> USB printer.
* 1-ilova: "2-ilovadan buyurtmalar" (bildirishnoma, ro'yxat, savatchaga olish yoki rad etish). Firebase qoidalari v2 —
  1-ilova o'zi yangilaydi; mijoz faqat yangi buyurtma qo'sha oladi (o'zgartira/o'chira olmaydi).

### Tezlik va avtomatik yangilash
* Excel yuklanganda 1-ilova darhol yuboradi (internet bo'lmasa — paydo bo'lishi bilan). 2-ilova "Yangilash" 1-ilovadagi eng so'nggi
  ma'lumotni darhol oladi; "15 daqiqa" xabari olib tashlandi.
* Fonda avtomatik yangilash: 1-ilova 30 daq / 1 soat / 2 soat / o'chiq; 2-ilova har soat / har 2 soat / o'chiq (+ yangi ma'lumot
  haqida xabar). Faqat internet bor va batareya kam bo'lmaganda; o'zgarmagan bo'lsa katta fayl yuklanmaydi.
* 1-ilova ochilganda 2-ilova so'rovi va buyurtmalari darhol tekshiriladi.

### Dizayn (ikkala ilova bir xil: `tools/gen_theme.py`)
* To'liq Material 3 palitrasi (yorug' va qorong'i), "surface container" ranglari ham brend rangidan — kartalar, pastki menyu,
  dialoglar endi standart binafsha tus olmaydi. Asosiy konteyner rangi yumshatildi.
* Ekran o'lchami standarti **100%** (oldin 92% — ro'yxatlar mayda edi; eski standart bir marta avtomatik o'zgartiriladi).
  Tanlov: 90/100/110/120%.
* Tovar va narx qatorlari kattaroq (64dp, nom 16sp, narx 16–17sp, +/− 44–48dp), ajratuvchi chiziq, savatchadagi tovar foni.
* Bosh sahifa: bitta katta "Yangi savdo" tugmasi + 3 ixcham plitka, "1C tarixi" kartasi.
* Sozlamalar: bo'limlar va qatorlar, har bir funksiya ostida qisqa izoh; "Qulayliklar" va 2-ilova sozlamalari kartalarga ajratildi.
* Kichik matnlar 12sp -> 13sp; bir xil karta burchagi va fon; tugma/segment balandliklari bir xil.

### Testlar
* JVM: 90 -> **116** (1C tarixi qidiruvi 15, katalog bog'lash 3, chek/ESC/POS 4, buyurtma va qoidalar 4).

## 2.4 (versionCode 6) + IMSOFT Mijoz 1.0 — 2026-10-29
Server, `savollar`/`javoblar` protokoli (tur 1, 3, 4, 5) va fixture testlari, mavjud baza jadvallari, applicationId va
C8 biznes qoidalari **o'zgarmagan**. Yangi lokal ma'lumot alohida faylda; yangi funksiyalar Sozlamalarda o'chiriladi.

### Yangi: 2-ilova (`mijoz/`, `com.ibrohimjon.imsoft_mijoz`, minSdk 26)
* Bosh sahifa (qarz — server qiymati va vaqti, bu oy, oxirgi xarid/to'lov), Narxlar (faqat mijoz olgan tovarlar, Naqd/Optom,
  so'm/$, tovar tarixi), Hisobot (xarid/to'lov/hammasi, davrlar, kunlar bo'yicha, PDF), Sozlamalar (ulanish kodi, til, mavzu, o'lcham).
* 2-filial chek printeri: foreground servis (`connectedDevice`), Firebase navbati, SSE signal + 60 s zaxira tekshiruv, USB bulk
  (sinf 7 ustuvor), "inflight/printed" dublikat himoyasi, noma'lum holatni foydalanuvchi hal qiladi, sinov cheki, jurnal,
  BOOT_COMPLETED, batareya cheklovi tugmasi, holat 1-ilovaga yoziladi.

### 1-ilova
* Mijoz ilovasi (Sozlamalar ▸ Qo'shimcha): Firebase (REST, secret shifrlangan, qoidalar avtomatik), mijoz tanlash, 1C hisoboti
  importi (.xls BIFF5/8 + birlashtirilgan kataklar, .htm cp1251/UTF-8 + rowspan/colspan, .txt/.csv), serverdan import (tur 5,
  o'zgarishsiz), ko'rib chiqish oynasi, davr bo'yicha almashtirish (dublikatsiz), ulanish kodi, `forwarder.ini`, `ShareWorker`
  (15 daqiqada: 2-ilova so'roviga javob, o'zgarishni yuborish), 2-filial printer holati.
* Tarix ▸ **Takrorlash** (joriy narxlarda; almashtirish/qo'shish/kutishga qo'yish).
* Professional qidiruv (`shared/Search.kt`) savdo va do'kon tanlashda, ajratib ko'rsatish, "taxminiy" belgisi, mashhurlik.
* Ixcham qatorlar; ekran o'lchami va shrift (Activity konfiguratsiyasi orqali).
* Qulayliklar (12): o'lcham, shrift, imlo, klaviatura, tartib, "+" rejimi, tebranish, "Kelgan narx" tugmasi, avto-yangilash, PIN qulf,
  kunlik avto-zaxira (+tiklash), kun yakuni eslatmasi, navbat ogohlantirishi.
* Masofadan ishlash yo'riqnomasi (Tailscale) ilova ichida.
* Kirill matnlarda brend nomlari (VPN, Firebase, Telegram…) lotinda qoladi.

### Kompyuter
* `pc/forwarder.py` Telegram o'rniga Firebase: idempotent PUT (fayl nomidan kalit — qayta yuborishda dublikat yo'q), "yangi chek"
  signali, 401/403/404/413/429 alohida, `--test`, `--status`, `--once`; `forwarder.vbs`; testlar `pc/test_forwarder.py`.

### Haqiqiy 1C fayli (2026-10-29)
* `.xlsx` o'quvchi (ZIP + SAX, birlashtirilgan kataklar). Haqiqiy "Mijoz kartochkasi": 445 yozuv (406 savdo, 35 to'lov, 4 qaytarish),
  jami hisobot oxiridagi aylanma bilan bir xil. 20 ustun: yetkazib beruvchi va qarz olish/berish ustunlari mijoz ustunlariga
  aralashmaydi; `Qarzi (Dt)`/`Xaqqi (Kt)` va "kun boshiga/oxiriga qoldiq" qatorlari o'qiladi → 2-ilovada rasmiy qoldiq.

### Testlar
* JVM: 56 → **90** (qidiruv, JSON/Base64/kod, 1C .htm/.xls/.txt o'quvchi, statistika, soxta Firebase bilan oqim). Python: forwarder 8 test.
* CI ikkala APK'ni va forwarder papkasini artefakt qiladi.


## 2.3 (versionCode 5) — 2026-10-27 · faqat APK
Server, `savollar`/`javoblar` protokoli (tur 1, 3, 4, 5), `assafocopy.sqlite` mavjud jadvallari, SharedPreferences kalitlari,
applicationId va C8 biznes qoidalari **o'zgarmagan**. Protokol fixture testlari o'zgarmagan va yashil.

### Ishonchlilik
* Navbat yadrosi qayta yozildi (`domain/outbox`): holat mashinasi (READY, SENDING, RETRY_WAIT, UNCERTAIN, SENT, NEEDS_CHECK,
  SENT_MANUAL), lokal UUID, atomik egallash + lease, INSERT'dan oldin watermark va "boshlandi" belgisi, faqat SELECT bilan probe,
  probe ishonchliligi tekshiruvi, probe bajarilmasa yubormaslik, `NonCancellable` INSERT.
* Tuzatildi (audit C-2): "Hozir yuborish" yuborishni kesib, dedup'siz qayta INSERT qilishi; INSERT'dan keyin jarayon o'lsa
  dedup'siz qayta yuborish; zaxiradan tiklangan navbatning o'zi yuborilishi.
* Tuzatildi (H-2): navbat ishchisidagi kutilmagan xato ilovani yiqitmaydi; ilova bo'ylab CoroutineExceptionHandler.
* Tuzatildi (H-4): bitta rad etilgan qator navbatni to'smaydi (5 rad etishdan keyin "Tekshirish kerak").
* WorkManager (`work-runtime-ktx` 2.9.1): internet paydo bo'lganda + har 15 daqiqada, ilova yopiq bo'lsa ham.
* Kvitansiya kodlari (`B-0412`, `T-0013`), **Navbat** ekrani, qo'lda amallar (serverda tekshirish, tasdiq bilan qayta yuborish,
  "serverda bor" deb belgilash).
* Buyurtma navbatga tushgach savatcha sinxron tozalanadi (jarayon o'lsa qayta chiqmaydi); `onStop` da savatcha yozuvlari tugatiladi;
  savatcha yozish xatosi diagnostikada ko'rinadi.

### Ma'lumot xavfsizligi
* `dokon_tarix.sqlite` v2 → v3 (faqat qo'shimcha ustun/jadval: `debt_cache`, `suggestion_feedback`, `kpi_session`, `migration_log`),
  o'tishdan oldin fayl nusxasi; `onDowngrade` ma'lumotni saqlaydi; ishga tushganda `PRAGMA quick_check`.
* `assafocopy.sqlite`: faqat yangi `draft_cart` jadvali (kutishdagi savatchalar). Versiya 1 qoladi.
* Zaxira: ikkala baza checkpoint, `manifest.json`, ixtiyoriy parol (AES-256-GCM, PBKDF2), tiklashdan oldin ko'rib chiqish,
  versiya/yaxlitlik tekshiruvi, tiklashdan oldin joriy baza nusxasi va xatoda avtomatik qaytarish, disk to'lishi aniq xato bilan.
  Eski (2.2) zaxira fayllari ham ochiladi. Yuborilgan SENT qatorlar 60 kundan keyin tozalanadi.

### Pul
* `Money.parseUser`: minglik ajratgichlar (`1,250,000`, `1.250.000`, `1 250 000`) va o'nlik vergul; tushunarsiz matn — xato
  (audit M-1). Eski `Money.parse` va uning fixture'lari o'zgarmagan.

### Savdo tezligi va qulaylik (har biri o'chiriladi)
* Aqlli asosiy tugma, do'kon xulosa kartasi, izohli taklif + "Yo'q ekan" fikri, "shu do'konga oxirgi narx", savatcha tekshiruvchisi,
  Undo, ko'p savatcha, takrorlashda yo'q tovarlar haqida xabar, kattaroq "Yuborish" tugmasi, KPI o'lchagich.

### Qarz va to'lov
* Qarz manbasi va vaqti ("Serverdan, 14:32" / "Oxirgi ma'lum qiymat"), yuborilmagan to'lovlar alohida, "Butun qarz",
  takroriy to'lov ogohlantirishi (15 daqiqa).

### Hisobot
* Hisobot ▸ Serverdan / Lokal; server hisobotini to'xtatish tugmasi; Kun yakuni; CSV eksport (buyurtmalar, to'lovlar).

### Xavfsizlik (APK ichida mumkin bo'lgani)
* Server paroli ekranga chiqmaydi (Login ▸ Server sozlamalaridan ham ko'rib bo'lmaydi); ilovaga o'rnatilgan parol manba kodda
  oddiy matn emas (test bilan tekshiriladi). Lokal hisobot WebView'i tarmoqqa chiqmaydi.

### Matnlar
* "Buyurtma qabul qilindi" → "Buyurtma telefonda saqlandi, yuborish navbatida" (server tasdig'i bilan adashmaslik uchun).
* 145 ta yangi matn, lotin va kirill.

### Build / CI
* CI: SQL smoke test, unit testlar, ixtiyoriy o'z kaliti bilan imzolash (GitHub Secrets), APK sertifikatini logga chiqarish.

## [Phase 1, 1-qadam] — 2026-10-26
* JUnit 4.13.2, protokol (tur 1/3/4/5) va Money fixture testlari, CI yangilandi. Ilova kodi o'zgarmagan.
