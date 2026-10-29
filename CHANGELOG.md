# CHANGELOG

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
