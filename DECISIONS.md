# DECISIONS — qabul qilingan qarorlar

Har qaror: sana, nima, nega, muqobil, qaytarish yo'li.

## 2026-10-26 · Phase 1, 1-qadam

**D-001. Unit test kutubxonasi: JUnit 4.13.2 (faqat `testImplementation`).**
* Foyda: Android Gradle Plugin bilan qo'shimcha sozlamasiz ishlaydi; parametrlangan (locale bo'yicha) testlar bor.
* Hajm: APK'ga kirmaydi (faqat test classpath).
* Xavfsizlik / texnik xizmat: eng keng tarqalgan, barqaror, bepul (EPL-1.0). JUnit 5 AGP'da qo'shimcha plagin talab qiladi — foydasi hozir aniq emas (C5).
* Qaytarish: `libs.versions.toml` va `app/build.gradle.kts` dagi 3 qatorni olib tashlash.

**D-002. Protokol fixture'lari = golden master (v2.2 ning hozirgi chiqishi).**
* Nega: test serverda haqiqiy server dasturi yo'q, eski ilovaning real yuborgan satrlari ham yo'q. Shuning uchun hozirgi kod chiqishi muzlatildi, maydon tartibi PDF 6-bo'lim va eski APK dekompilyatsiyasi bilan qo'lda solishtirildi, qo'shimcha ravishda tuzilma testlari (12 / 11 maydon, kodlar, ASCII raqamlar) fixture'dan mustaqil tekshiradi.
* Cheklov: agar v2.2 biror maydonni eski ilovadan farqli yozsa (masalan, eski ilova summani vergul bilan yuborgan bo'lishi mumkin — audit L-2), fixture buni "to'g'ri" deb qabul qilgan bo'ladi. Real server bilan solishtirish — test muhitdan keyin.
* Fixture faylini o'zgartirish = protokolni o'zgartirish. Faqat egasi roziligi bilan.

**D-003. Testlar 5 ta locale'da bajariladi:** uz-Latn-UZ, uz-Cyrl-UZ, ru-RU, en-US, ar-EG (arabcha raqamli xavfli holat), vaqt zonasi Asia/Tashkent.

**D-004. Money "KNOWN" xatolari testda hozirgi holicha qayd qilindi** (`parse("1,250,000") = 0.0`). Bu xatoni tasdiqlash emas: tuzatilganda fixture ataylab, alohida commit bilan yangilanadi (audit M-1). Tuzatish 1-qadamga kirmaydi.

**D-005. CI'dagi debug APK — faqat build tekshiruvi.** Imzo masalasi (C-4) 4-qadamda; hozir CI kaliti har safar yangi, APK agent telefoniga o'rnatilmaydi. Artefakt nomida `CI-ONLY` yozilgan.

**D-006. Yuborilgan `debug.keystore` eski ilova kaliti EMAS** (pastga qarang). Loyihaga ham, CI'ga ham qo'shilmadi. Qaror 4-qadamda, egasi bilan.
* Eski APK sertifikati SHA-256: `B6:93:7C:60:93:6B:6B:13:30:8B:B1:8A:BD:9A:F7:54:5D:61:B6:DC:99:AF:9A:03:A1:67:41:99:13:76:56:22`
* Yuborilgan keystore (`androiddebugkey`, 2026-04-10 da yaratilgan) SHA-256: `C9:1B:9A:4A:76:29:D4:D5:B6:03:08:A6:30:63:D0:58:84:CA:E0:84:6C:1B:2D:A6:F8:AF:89:65:BA:F7:64:0C`
* MUHIM: egasi avval aytganidek, 2-bosqich (Java'da tiklangan) ilova eski ilovani o'chirib qayta o'rnatilgan. Agar u egasining Android Studio'sida build qilingan bo'lsa, telefonda HOZIR turgan ilova aynan shu `C9:1B…` kalit bilan imzolangan bo'lishi mumkin [ASM] — unda yangilash uchun kerakli kalit shu. Telefonda tekshirish: `adb shell dumpsys package com.ibrohimjon.imsoft_dokon_som_val | grep -A3 signatures` yoki APK'ni telefondan olib `apksigner verify --print-certs`.
* Bu kalit egasining Android Studio'dagi ~10 ta boshqa ilovasini ham imzolaydi — bitta kalit sizsa, hammasi xavf ostida. 4-qadamda variantlar (shu kalit bilan davom etish yoki ma'lumotni zaxiralab alohida release kalitga o'tish) egasi bilan hal qilinadi.

## 2026-10-27 · v2.3 (egasining yangi qarori: faqat APK, bitta uzluksiz ish)

**D-007. Server va protokol o'zgarmaydi.** Yangi server jadvali, login, UNIQUE kalit, API — yo'q. Navbat yadrosi faqat mavjud
`savollar` jadvalida `INSERT` (avvalgidek) va `SELECT` (probe) ishlatadi. `javoblar` tur 1/4 uchun o'qilmaydi va
"server qayta ishladi" holati umuman yo'q (tasdiqlab bo'lmaydi).

**D-008. Probe ishonchliligi ("guvoh" qoidasi).** "Topilmadi" faqat avval yozilgani aniq bo'lgan so'rov (watermark yoki
oxirgi SENT ID) serverda hali turgan bo'lsa ishonchli hisoblanadi. Aks holda — NEEDS_CHECK (dublikatdan ko'ra qo'lda tekshiruv
afzal). Natija: server qatorlarni o'chiradigan bo'lsa, ilova dublikat qilmaydi, lekin ko'proq "Tekshirish kerak" chiqadi.

**D-009. Eski (v2) navbat qatorlari migratsiyada UNCERTAIN.** Ular natijasi noma'lum holda qolgan bo'lishi mumkin (audit C-2),
shuning uchun avval qidiriladi; guvoh bo'lmasa NEEDS_CHECK.

**D-010. WorkManager 2.9.1** (`work-runtime-ktx`). Foyda: ilova yopiq bo'lsa ham yuborish; hajm ~1 MB; Google rasmiy, bepul
(Apache-2.0); o'zi Room'ni ichki ishlatadi — ilova kodi Room'ga o'tmaydi (C9 saqlanadi). Ilova ichidagi darhol yuborish ham qoladi;
ikkalasi bitta engine + bazadagi egallash orqali ishlaydi.

**D-011. Money ichki turi Double qoladi.** P10 bo'yicha Long/BigDecimal'ga o'tkazish katta va xavfli; egasi faqat "1,250,000 → 0"
xatosini so'radi. Yangi `Money.parseUser` (foydalanuvchi kiritishi uchun), eski `Money.parse` va fixture'lari tegilmagan
(bazadagi qiymatlarni o'qish uchun ishlatiladi).

**D-012. Bitta vergul = o'nlik** (`1,250` = 1.25), chunki eski ilova ham shunday o'qigan. Bir nechta vergul/nuqta — minglik.

**D-013. Yangi funksiyalar standart holatda YOQIQ**, KPI o'lchagich esa O'CHIQ. Hammasi Sozlamalarda o'chiriladi; o'chirilsa
ilova 2.2 kabi ishlaydi. Mavjud elementlarning joyi o'zgartirilmadi (agent odatlari): yangi tugmalar qo'shimcha qilib qo'yildi.
Yakunlash oynasi bo'limlari yig'ilmadi (joylashuv saqlansin) — faqat "Yuborish" kattalashtirildi va ogohlantirish qatori qo'shildi.

**D-014. Server paroli.** Egasi server login/parolini o'zgartira olmaydi, ilova esa ulanishi kerak. Shuning uchun parol ilovada
qoladi, lekin: ekranga hech qachon chiqmaydi, manbada oddiy matn emas (XOR bilan yashirilgan, test tekshiradi). Bu kuchli himoya
emas — README'da ochiq yozilgan.

**D-015. Takroriy to'lov oynasi 15 daqiqa** (shu do'kon, aynan shu naqd/o'tkazma/click). Faqat tasdiq so'raydi, to'smaydi.

**D-016. Savatcha tekshiruvchisi chegaralari:** miqdor ≥ max(3 × mediana, mediana + 10) (kamida 2 ta tarix); narx oxirgi narxdan
> 30 % farq (shu valyutada). Qaytarish rejimida va tarix bo'lmasa jim. Ko'pi bilan 2 ta.

**D-017. Taklif fikri:** "Kerak emas" — 7 kun, "Keyinroq" — 2 kun, "Omborda yo'q" — 1 kun taklif qilinmaydi.

**D-018. Lokal tekshiruv vositalari (repoga kirmaydi).** Bu muhitda Google Maven yopiq bo'lgani uchun kod Android 35 `android.jar`,
eski APK'dagi AndroidX (enjarify + metadata tiklash), WorkManager/lifecycle/security uchun qo'lda yozilgan imzo-stub'lar va
generatsiya qilingan R/ViewBinding'ga qarshi kompilyatsiya qilindi. Bu haqiqiy Gradle build o'rnini BOSMAYDI (AAPT2, manifest
merge, R8, kutubxona versiyalari farqi tekshirilmagan) — birinchi CI ishga tushishi yakuniy tekshiruv.

**D-019. Lokal hisobot WebView'i tarmoqqa chiqmaydi**; server hisobotida avvalgi xatti-harakat saqlandi (rasmlar bo'lishi mumkin).

## v2.4 / IMSOFT Mijoz 1.0

**D-020. Bepul bulut — Firebase Realtime Database (REST), Telegram emas.** Telegram bot o'zi yoki boshqa bot yuborgan xabarni
`getUpdates` orqali olmaydi (kompyuterdagi bot → telefondagi bot sxemasi ishlamaydi) va navbat 24 soatda o'chadi. Firebase: bepul,
karta so'ramaydi, kutubxonasiz HTTPS, navbat chek chiqmaguncha turadi, ikkala ilova va kompyuter uchun bitta joy.

**D-021. Ruxsatlar modeli.** Secret faqat egasida (1-ilova shifrlangan, `forwarder.ini`). 2-ilova secret'siz: qoidalar faqat
`s/{32 belgili kalit}` o'qish + `req` yozish, `q/{kalit}` o'qish + o'chirish, `p/{kalit}` o'qish/yozish. Ildizni hech kim
ro'yxatlay olmaydi. Mijoz almashtirilsa o'qish kaliti yangilanadi.

**D-022. Umumiy kod — alohida Gradle modulisiz** (`shared/src/main/kotlin` ikkala modulga `srcDir`): yangi plagin/bog'liqlik yo'q,
CI xavfi kam; testlar 1-ilova testlari bilan birga ishlaydi.

**D-023. 1C hisobot o'quvchi ustunlarni sarlavha MATNI bo'yicha topadi** (tartib/son farq qilsa ham ishlaydi). Import davri
(sarlavhadagi "dan … gacha" yoki yozuvlar oralig'i) ichidagi eski yozuvlar almashtiriladi — qayta yuborishda dublikat yo'q.
Haqiqiy fayl kelgach sinov fixture'i qo'shiladi.

**D-024. Qarz faqat server qiymati** (1-ilova katalogi/tur 3), vaqti bilan. Hisobot yozuvlaridan qarz "hisoblanmaydi" —
boshlang'ich qoldiq noma'lum, noto'g'ri raqam ko'rsatish xavfi bor.

**D-025. Chek dublikatidan himoya:** kompyuter — fayl nomidan o'zgarmas kalit bilan PUT; telefon — "inflight" diskka yozilib keyin
printerga; jarayon o'rtada o'lsa chek o'zi qayta chiqmaydi, foydalanuvchi "Qayta chop etish" yoki "Chiqqan" ni tanlaydi.

**D-026. 2-ilova printer servisi turi `connectedDevice`** (USB printer). `dataSync` Android 15'da kuniga 6 soat bilan cheklangan.

**D-027. Takrorlash — joriy narxlarda** (C8: narx har doim katalog qoidasi bilan); yuborilgan buyurtma o'zgarmaydi.

**D-028. Ekran o'lchami** — Activity konfiguratsiyasidagi zichlik (dp) ko'paytuvchisi: barcha ekranlar bir xil kichrayadi,
alohida layout nusxalari kerak emas. Odatiy — "Ixcham" (0.92). Qatorlar va tugmalar ham biroz kichraytirildi.
