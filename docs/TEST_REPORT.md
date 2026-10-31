# Test hisoboti — Agent/Admin 3.0 + Savdo Mijoz 1.4 (2026-10-31)

## Natija (shu muhitda bajarilgan)
| Tekshiruv | Natija |
|---|---|
| Kotlin kompilyatsiyasi: `app` + `shared` (Android SDK, JDK'siz) | **0 xato** |
| Kotlin kompilyatsiyasi: `mijoz` + `shared` | **0 xato** |
| `BarcodesTest` (6) — EAN/UPC tekshiruv raqami, kanonik kalit (UPC-A = EAN-13 = Excel soni); tarozi yorlig'i (gramm, 10 gramm, 6+4 xonali, xato tekshiruv, 0 og'irlik); 1C "Вывести список" jadvalidan ustunlarni topish (sarlavha, bo'shliqli kod, son bo'lib qolgan kod); narx ro'yxatini rad etish; kod -> nom bog'lash (ikki ma'noli nom bog'lanmaydi); resolver (tarozi kodi — tasdiq bilan) | 6/6 |
| Haqiqiy 1C shtrix kodlar fayli (`.xls`, repoga kiritilmagan): 4 212 qator, ustunlar to'g'ri topildi, 89 ms; tarozi prefikslari 28/29; UPC-A, tarozi yorlig'i (1,234 kg), tekshiruvsiz o'z kodi — hammasi topildi | qo'lda |
| Do'kondagi haqiqiy yorliqlar (rasm): `2900025002028` -> Keshu 210, 0,202 kg; `2500000024375` -> Saryog Asal 200 gr (`BarcodesTest.realLabels`) | o'tdi |
| `AppSalesTest`, `ReceiptTest` (yangi chek: "40 x 182 000 = 7 280 000", Xodim/Vaqt/sana, QAYTIB OLISH banneri, qarz qoldig'i), snapshot'da chek osti | o'tdi |
| Chek rasmi: Android chizish kodining Python nusxasi bilan ko'rinish do'kon chekiga solishtirildi (savdo va qaytarish) | qo'lda |
| `LabelsTest` (7) — EAN-13/EAN-8/Code 128 modullari (mustaqil dekoder zxing-cpp bilan 13/13 o'qildi); do'kon kodidan tur tanlash (12 xonali o'z kodi -> EAN-13 + tekshiruv raqami, vaznli -> yo'q); "PLU Narx" MXL matnidan, Excel shaklidan va PLU ustunli jadvaldan o'qish; katalogga bog'lash; PLU yo'q fayl rad etiladi; narx "60 000" | 7/7 |
| Yorliq ko'rinishi: chizish kodining Python nusxasi 58×40, 58×30, 40×30, 30×20 mm da — shtrix kodlar zxing-cpp bilan o'qildi (8/8) | qo'lda |
| Telefon klienti (Kotlin `LabelPrinterClient`, JVM'da) <-> `label_printer.py` (file rejimi): info, chop etish, takror ish (chop etilmadi), noto'g'ri kalit, kompyuter yo'q | o'tdi |
| `pc/test_label_printer.py` (18) — sozlama; printer nomini topish; chop etish va takrorlanmaslik (qayta ishga tushgandan keyin ham, bulut bo'laklari ham); kalit/id/PNG tekshiruvi; nusxa chegarasi; printer xatosi (ish bajarilmagan hisoblanadi); file rejimi; info; TSPL baytlari (o'lcham, 0=qora, teskari, to'ldirish); TCP ramka (katta ish 300 yorliq, buzuq JSON); UDP qidiruv; bulut navbati (eskirgan va begona kalitli ish o'chiriladi, LAN'dan keyin bulut — bitta), printer xatosida ish navbatda qoladi; holat yozish | 18/18 |
| `LabelsTest.codesPayload`, `snapshotCarriesPlu` — kompyuter yuborgan kodlar (PLU nollarsiz, takror shtrix, raqam ko'rinishidagi kod), katalogga bog'lash (kod, keyin nom); 2-ilovaga PLU va eski formatdagi ma'lumot | 2/2 |
| `pc/test_onec_export.py` (+7) — PLU/shtrix manbasini tanlash (UT11 kabi registr, tovar rekviziti, "Весовой" belgisi olinmaydi), so'rov matni (ВЫРАЗИТЬ/ССЫЛКА), qo'lda yozilgan manba, birlashtirish, faqat o'zgarganda yuborish, telefon so'rovi, hisobot | 16/16 |
| Python `codes.json` -> Kotlin `CodesPayload` (JVM) | o'tdi |
| `pc/test_ornatish.py` (6) — 1C bazalar ro'yxati (ibases.v8i), ulanish qatori, onec.ini (parol maxsus belgilar bilan, [codes] saqlanadi), forwarder.ini tekshiruvi, avtozapusk vbs, LPR sinov yuborish | 6/6 |
| `LprTest` (3) — TSPL bitmap (Python `tspl_bytes` bilan bir xil: 0 = qora, to'ldiruvchi bitlar oq, SIZE/GAP/DIRECTION), LPR protokoli soxta LPD serverda (navbat, nazorat fayli 'l', ma'lumot aynan), rad etilgan navbat | 3/3 |
| Yorliq ko'rinishi (yangi): chizish kodining Python nusxasi do'kon yorig'i rasmi bilan solishtirildi; PLU va shtrix yorliqlar 1C "ГодехЦенник"/"ГодехШтрих" joylashuvida; shtrix `2500000020476` zxing-cpp bilan o'qildi | qo'lda |
| `pc/test_onec_export.py` (+6) — do'kon konfiguratsiyasi ("Товары" katalogi, ПЛУ/ШтрихКод rekvizitlari), narx rekvizitlari ПродажЦена1..4 (turi bo'sh, sinonim takrori, $ va kirim chiqarib tashlanadi) | 22/22 |
| `ReceiptStyleTest` (7) — uslubni saqlash/o'qish (buzuq, eski, chegaradan tashqari), yashirish (xodim/raqam/vaqt/sana/mijoz/qarz/telefon/izoh), ikki marta qo'llash, sarlavha osti va xabar matn/HTML'da (ekranlangan), builder `infoKeys`, dither (oq/qora/kulrang), snapshot "rs"/"rl" va eski snapshot | 7/7 |
| Chek ko'rinishi: chizish kodining Python nusxasi — Standart, 1C uslubi (logo, manzil, xabar), 58 mm | qo'lda |
| Statik tekshiruv (skript): BOM/NBSP/ko'rinmas belgilar, apostrof, `%` (argumentsiz), lotin↔kirill format belgilari bir xil, barcha `R.string` kalitlari, `getString` argumentlari soni, `!!`, `runBlocking`, PendingIntent FLAG_IMMUTABLE, `removeFirst`/`removeLast` | xatosiz |
| `AppRoleTest` (2) — rol paket nomidan (agent/admin, o'xshash nom admin emas); ko'chiriladigan fayl yo'llari (`..`, mutlaq yo'l, baza va secure prefs fayllari rad etiladi) | 2/2 |
| Tugma matni o'lchash skripti: 360dp ekran, Liberation Sans (Roboto o'lchamiga yaqin), lotin va kirill, shrift ×1 va ×1,15 — sig'maydigan tugma yo'q | 0 ta |
| `RelayTest` (11) — Agent <-> soxta Firebase (qoidalar bilan) <-> Admin <-> soxta server: ulanish kodi; kirish (parol Firebase'da yo'q); katalog va 1C kodlari; savdo bir marta yoziladi (qayta yuborish, Admin o'chib qolishi); Admin oflayn -> keyin yoziladi; server yo'q -> xato holati; rad etish va soxta kalit; OutboxEngine ko'prik orqali (5 savdo, dublikatsiz, hammasi SENT); qarz/hisobot/vaqt; yorliq va chek ko'zgusi; agent boshqalarning joyini o'qiy olmaydi | 11/11 |
| JUnit (`app` + `shared`) | **169/169 o'tdi** (avvalgi 136 ta o'zgarmagan holda yashil) |
| `LoginTest` (6) — PBKDF2 JDK bilan bir xil; shifrlash/ochish; noto'g'ri parol; kalit (telefon formati farqi yo'q); do'kon kodi <-> manzil; soxta Firebase'da `a/` qoidasi (qisqa kalit o'qilmaydi, yozib bo'lmaydi) | 6/6 |
| `OrdersTest.orderCarriesSender` — `by` (kim yubordi) maydoni bulut orqali, uzunlik cheklovi | 1/1 |
| `pc/test_onec_export.py` (9, v2.6) — sozlama va ulanish qatori; davr; jadval; xesh (xlsx vaqt belgisiz); faqat o'zgarganlar yuboriladi; davr o'zgarsa qayta; xatoda ulanish uziladi; yuklash formati (gzip+base64); kalit/nom | 9/9 |
| `pc/test_forwarder.py` | 8/8 |
| Resurs havolalari (ikkala modul), satrlar (lotin + kirill avtomatik) | xatosiz |

## Tekshirib bo'lmagan narsalar (ochiq)
1. Haqiqiy telefon: kirish oynasi, o'rgatuvchi ko'rsatmalar (yoritish joyi), sirli menyu, buyurtma savatchasi.
2. Haqiqiy Firebase qoidalari v3 (`a/`) — soxta server bilan sinaldi; 1-ilova birinchi ochilishda o'zi o'rnatadi.
3. `onec_export.py` haqiqiy 1C bilan: hisobot nomi va parametrlari `--discover` natijasidan keyin sozlanadi. COM qismi
   (V83.COMConnector, `СкомпоноватьРезультат`, `ТабличныйДокумент.Записать(XLSX)`) Windows'siz sinab bo'lmaydi.
4. Eski telefon (Android 6–7) da PBKDF2 (120 000) tezligi — kirishda 1–3 soniya kutilishi mumkin.
5. PLU'ning 1C'dagi haqiqiy joyi: avtomatik topish soxta metama'lumotda sinaldi; do'kon bazasida `--codes` natijasi kerak.
6. Narx yorliqlari haqiqiy Xprinter bilan: `gdi` (pywin32 + drayver) va `tspl` rejimlari Windows'siz sinab bo'lmadi;
   yorliq o'lchami drayverdan to'g'ri kelishi va PLU fayli (hamma vaznli tovarlar bilan) hali tekshirilmagan.
7. Kamera skaneri (zxing-android-embedded) va Bluetooth/USB skaner haqiqiy telefonda; kutubxona shu muhitda yuklab bo'lmadi —
   kompilyatsiya API stub bilan tekshirildi, GitHub build haqiqiy kutubxonani oladi. Tarozi yorlig'i formati namunalar bilan tasdiqlanishi kerak.


---

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
