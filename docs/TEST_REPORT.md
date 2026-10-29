# Test hisoboti — v2.5 + IMSOFT Mijoz 1.1 (2026-10-29)

## Natija (shu muhitda bajarilgan)
| Tekshiruv | Natija |
|---|---|
| Kotlin kompilyatsiyasi: `app` + `shared` (Android SDK, JDK'siz — Android unit test sharoiti) | **0 xato** |
| Kotlin kompilyatsiyasi: `mijoz` + `shared` | **0 xato** |
| JUnit, jami | **116/116 o'tdi** (v2.4 dagi 90 ta o'zgarmagan holda yashil) |
| `LedgerSearchTest` (15) — hujjatlarga yig'ish; nom (lotin/kirill/xato/klaviatura); "pista 40" bitta qatorda; narx/summa (`200 000`, `200k`, `200минг`, `21,51mln`, `15$`); son boshi — taxminiy; nomdagi raqam aniq; sana/oraliq/oy/bugun/kecha/vaqt; turi va valyuta; istisno; jami faqat mos qatorlar; turli qatorlarda — taxminiy; ajratish oralig'i; filtr; "2.5" ikki ma'noli | 15/15 |
| `CatalogMatchTest` (3) — aniq (nom/id, kirill), taxminiy, topilmadi | 3/3 |
| `ReceiptTest` (4) — chek matni/HTML (ekranlash), son formati, ESC/POS rastr baytlari va bo'laklari | 4/4 |
| `OrdersTest` (4) — kodek, id tartibi, soxta Firebase'da qoidalar: mijoz faqat yangi buyurtma qo'shadi, o'zgartira/o'chira/ortiqcha maydon yoza olmaydi, 1-ilova holat yozadi | 4/4 |
| Haqiqiy 1C fayli (repoga kiritilmagan) bilan qidiruv: 445 qator -> 126 hujjat; `pista 40` -> 7 hujjat; `орик`, `ьфнша` (mayiz), `3762` (taxminiy), `to'lov >10mln` — barchasi kutilgandek, < 25 ms | qo'lda |
| Resurs havolalari (`@string/@style/@drawable/...`) va barcha XML fayllar | xatosiz |
| `pc/test_forwarder.py` | 8/8 |
| Maketlarning taxminiy ko'rinishi (HTML render, 360dp, yorug'/qorong'i) — sig'ishi va kesilgan yozuvlar tekshirildi | qo'lda |

## Tekshirib bo'lmagan narsalar (ochiq)
1. Haqiqiy telefon: yangi ranglar, 100% o'lcham, ro'yxatlar, bottom sheet'lar, chek rasmi ko'rinishi.
2. Haqiqiy Firebase qoidalari v2 (`ord`, `now`) — soxta server bilan sinaldi; telefonda birinchi ochilishda o'rnatiladi.
3. Chekning Xprinter XP-Q80A'da rastr bo'lib chiqishi (GS v 0, 240 qatorli bo'laklar) — printer bilan sinash kerak.
4. WorkManager'ning Xiaomi/HyperOS'da 1–2 soatlik ishlashi (batareya cheklovi "Cheklovsiz" bo'lishi kerak).
5. Haqiqiy Gradle build (AAPT2: yangi uslub atributlari, FileProvider) — GitHub Actions'da.

---

# Test hisoboti — v2.4 + IMSOFT Mijoz 1.0 (2026-10-29)

## Natija (shu muhitda bajarilgan)
| Tekshiruv | Natija |
|---|---|
| Kotlin kompilyatsiyasi: `app` (1-ilova) + `shared` | **0 xato** |
| Kotlin kompilyatsiyasi: `mijoz` (2-ilova) + `shared` | **0 xato** |
| JUnit, jami (1-ilova modulida, `shared` testlari bilan) | **88/88 o'tdi** (v2.3 dagi 56 ta o'zgarmagan holda yashil) |
| `ProtocolFixtureTest`, `MoneyFixtureTest` — fixture fayllari o'zgarmagan | yashil |
| `SharedCoreTest` (17) — JSON, Base64 (JDK bilan solishtirish), ulanish kodi, URL, qidiruv: ko'p so'z, kirill/lotin, tutuq belgisi, kod, imlo, h/x q/k, klaviatura tartibi, raqamlar, ajratib ko'rsatish oralig'i, qisman natija, mashhurlik, 5000 tovarda < 150 ms | 17/17 |
| `ReportImportTest` (9) — ekran rasmidagi tuzilmadagi 1C hisoboti: `.htm` (windows-1251, rowspan/colspan), `.xls` (BIFF8, birlashtirilgan kataklar), 3000 satrli `.xls` (SST bir nechta CONTINUE'ga bo'lingan), TSV; So'm/$ qatorlari, kunlik jami qatorlari tashlanadi, mijoz nomi va davr sarlavhadan; qayta importda dublikat yo'q; davr bo'yicha almashtirish | 9/9 |
| `ClientStatsTest` (4) — jami, kunlar, tovar tarixi, davrlar (oy/hafta/o'tgan oy, yil o'tishi) | 4/4 |
| `CloudFlowTest` (2) — soxta Firebase (REST + qoidalar takrori): 1-ilova yuboradi → 2-ilova kod bilan o'qiydi; mijoz faqat `req` yoza oladi, ildiz/boshqa yozish taqiqlangan; chek navbati tartibi va idempotent PUT; o'chirish; holat | 2/2 |
| `pc/test_forwarder.py` (8) — tartib, idempotent qayta yuborish, 401 (fayl qoladi), 413 (failed/), internet yo'q (fayl qoladi), 200 KB TCP qabul, bo'sh ulanish, `forwarder.ini` tekshiruvi | 8/8 |
| `tools/sql_smoke_test.py` | OK |

## Tekshirib bo'lmagan narsalar (ochiq)
1. **Haqiqiy 1C fayli.** O'quvchi ekran rasmidagi tuzilmaga o'xshatib yasalgan fayllarda sinaldi. Haqiqiy 1C 7.7 "Сохранить как"
   fayli (ustun nomlari, birlashtirish, sana ko'rinishi) farq qilishi mumkin — namunaviy fayl so'raldi; import oldidan ko'rib
   chiqish oynasi bunday holatni ko'rsatadi.
2. **Server hisoboti (tur 5) HTML'i** "Mijoz kartochkasi" bilan bir xilmi — noma'lum (shuning uchun fayldan import asosiy yo'l).
3. **Haqiqiy Firebase.** REST chaqiriqlari Firebase hujjatiga mos yozilgan va soxta server bilan sinaldi; haqiqiy qoidalar
   dvigateli, SSE oqimi (`text/event-stream`) va Uzbekistondan ulanish — telefonda tekshiriladi.
4. **Haqiqiy Gradle build** (ikki modul, AAPT2, manifest) — GitHub Actions'da.
5. **2-ilova USB printer kodi** ChekPrinter tavsifi asosida qayta yozildi (o'sha sinf 7 + bulk OUT yo'li u yerda sinalgan);
   shu ilovada telefon + Xprinter bilan hali sinalmagan. Xiaomi'da servisning ekran o'chiq paytda uzoq ishlashi, avto-ishga tushish.
6. **Ekran o'lchami** (Activity zichligini almashtirish) HyperOS'da ko'rinishi; PIN qulf, eslatmalar, avto-zaxira — telefonda.
7. **Tailscale** orqali SQL Server'ga ulanish (firewall qoidasi bilan).

---

# (oldingi) Test hisoboti — v2.3 (2026-10-27)

## Natija (shu muhitda bajarilgan)
| Tekshiruv | Natija |
|---|---|
| Kotlin kompilyatsiyasi: barcha `app/src/main` (Android 35 API, AndroidX, WorkManager stub'lari, generatsiya qilingan R/ViewBinding) | **0 xato**, 4 ta eskirgan API ogohlantirishi |
| JUnit, jami | **56/56 o'tdi** |
| `ProtocolFixtureTest` — tur 1/3/4/5 bayt-bayt, 5 locale × 4 test (fixture fayllari o'zgarmagan) | 20/20 |
| `MoneyFixtureTest` — eski `Money` xatti-harakati (o'zgarmagan) | 10/10 |
| `MoneyParseUserTest` — minglik ajratgich, o'nlik vergul, rad etish, 5 locale | 6/6 |
| `OutboxEngineTest` — 300 tasodifiy nosozlik ssenariysi (35 % xato, 15 % jarayon o'limi), 150 ta "qatorlarni o'chiradigan server", 40 qatorga 3 parallel ishchi, 7 aniq ssenariy | 10/10 · **1038 o'lim, 0 takroriy, 0 yo'qolgan**; 186 ta qator NEEDS_CHECK ga tushdi (kutilgan: bu ssenariylarda guvoh yo'q) |
| `BusinessRulesTest` — C8 (kirimdan arzon narx to'smaydi, kurs 0), tekshiruvchi faqat ≤2 ogohlantirish, "Shunday qoldir", oxirgi narx, taklif izohi va fikri, lokal hisobot, CSV qochirish, parol manbada yo'qligi | 6/6 |
| `BackupCryptoAndMatchTest` — zaxira shifri, noto'g'ri parol, payload solishtirish (varchar `?`, char probellar) | 4/4 |
| `tools/sql_smoke_test.py` — `HistoryDb.kt` dagi haqiqiy SQL bilan v2 → v3 migratsiya (50 + 3 qator saqlandi, eski navbat → UNCERTAIN), atomik egallash, lease, `draft_cart` | **OK** |
| Mutatsiya: probe o'chirilsa navbat testlari yiqiladimi | Ha, 5 test yiqildi (testlar ishlaydi) |
| Statik resurs tekshiruvi: barcha `@string/@drawable/@id` havolalari, XML yaxlitligi, lotin/kirill kalitlari va `%1$s` argumentlari mos | OK |

## Tekshirib bo'lmagan narsalar (ochiq)
1. **Haqiqiy Gradle build** (AAPT2 resurs bog'lash, manifest birlashtirish, R8, aniq kutubxona versiyalari). Bu muhitda Google
   Maven va Gradle serverlari yopiq. GitHub Actions'dagi birinchi ishga tushish shuni tekshiradi; xato chiqsa logni yuboring.
2. **Haqiqiy telefon** (Xiaomi HyperOS 3): UI ko'rinishi, bosishlar, WorkManager fonda ishlashi, batareya tejash, Android Keystore.
3. **Haqiqiy server**: `savollar` ustun turlari (unicode/uzunlik), server dasturi qatorlarni o'chiradimi, `SELECT MAX(ID)` va
   probe so'rovlari tezligi, `GETDATE()` ruxsati. Server tekshirilmaydi va o'zgartirilmaydi (egasining qarori).
4. **Tailscale orqali** ishlash.
5. **Eski ilova ma'lumotini ko'chirish** (`run-as`) — buyruqlar qo'llanmada, telefonda bajarilmagan.
6. Android SQLite'da migratsiya (Python sqlite3 da sinaldi, Android'da emas).
7. 50 000 tovarli katalogda tezlik va xotira.

Bu ro'yxatdagi narsalar uchun: `docs/EGA_QOLLANMA.md` (b) bo'limidagi 1 telefon, 1 kichik buyurtma sinovi.
