# CHANGELOG

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
