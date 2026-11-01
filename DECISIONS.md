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

## v2.5 / IMSOFT Mijoz 1.1 (2026-10-29)

**D-029. 1C tarixi "hujjat" bo'lib ko'rsatiladi.** 1C kartochkasida har tovar alohida qator; bir vaqtda (soniyagacha) yozilgan bir turdagi
qatorlar bitta hujjat. Haqiqiy faylda aralash turdagi bir xil vaqt yo'q (tekshirildi). Kalit: `stamp|tur|tur matni`.

**D-030. Qidiruv qoidasi: shartlar VA, sana/tur YOKI, tovar shartlari bitta qatorda.** "pista 40" deganda foydalanuvchi aynan
40 kg pista nazarda tutadi. Bo'lmasa (shartlar turli qatorlarda) natija "taxminiy" belgisi bilan beriladi — hech qachon jim
"aniq" deb ko'rsatilmaydi. "27.10" kabi ikki ma'noli so'z sana YOKI son (27,1) sifatida tekshiriladi.

**D-031. Takrorlashda narx — joriy katalogdan.** 1C dagi eski narx savatchaga qo'yilmaydi (C8 qoidalari o'zgarmaydi). Tovar nom bo'yicha
bog'lanadi: aniq — belgilangan; taxminiy — belgilanmagan, foydalanuvchi tekshiradi; topilmagan — qo'shilmaydi.

**D-032. 2-ilovada "takrorlash" = buyurtma so'rovi, savdo emas.** 2-ilova savdo qilmaydi (avvalgi qaror). So'rov 1-ilovaga boradi,
egasi ko'rib savatchaga oladi. Holatlar faqat: yuborildi / 1-ilovaga yetib bordi / savatchaga olindi / rad etildi.
"Serverda tasdiqlandi" degan holat yo'q. Firebase qoidasi: mijoz faqat YANGI yozuv qo'shadi (`!data.exists()`), maydonlar va
uzunlik cheklangan, vaqt server vaqti. Qoidalar versiyasi (`RULES_VERSION=2`) — 1-ilova secret bilan o'zi yangilaydi.

**D-033. Chek rasm sifatida (ESC/POS GS v 0).** Matn rejimida kirill va o'/g' printer kod sahifasiga bog'liq; rasm har doim to'g'ri.
80 mm = 576 nuqta, 240 qatorli bo'laklar. 1-ilova chekni 2-filial navbatiga qo'yadi — forwarder bilan bir xil format.

**D-034. Avtomatik yangilash WorkManager bilan (1–2 soat, tarmoq + batareya sharti).** Doimiy ulanish (SSE) batareyani yeydi;
2-ilova "Yangilash" baribir darhol oladi, 1-ilova import qilinganda darhol yuboradi. 2-ilova avval faqat sarlavhani o'qiydi.

**D-035. Bitta dizayn tizimi ikkala ilova uchun (`tools/gen_theme.py`).** Ranglar Material 3 tonal palitrasidan (brend #006A60),
surface container rollari ham aniqlangan (aks holda Material standart binafsha tusni qo'yadi). Ekran standarti 100%.
1C tarixi UI kodi 1-ilovada yoziladi va `tools/sync_ledger_kit.py` bilan 2-ilovaga ko'chiriladi (qo'lda tahrirlanmaydi).


## v2.6 / Savdo Mijoz 1.2 (2026-10-30)
**D-036. Universal: har do'konning o'z Firebase loyihasi.** Ilova ichida hech qanday do'konga xos manzil yo'q. 1-ilovaga
Firebase manzili va secret kiritiladi; "do'kon kodi" = Firebase loyiha nomi (`x` -> `https://x-default-rtdb.firebaseio.com`,
mintaqa bo'lsa `x@europe-west1`). Do'stlar o'z bazasi bilan shu ilovalarni ishlatadi, ma'lumotlar aralashmaydi.

**D-037. Ko'p mijozli 1-ilova.** Har bir mijoz yoki yuk beruvchining alohida o'qish kaliti, alohida 1C tarixi fayli
(`share/ledger_{id}.json`), ixtiyoriy chek printeri navbati va telefonlari bor. v2.5 dagi yagona mijoz bir marta avtomatik
ro'yxatga ko'chiriladi (fayl o'sha id bilan qoladi). Excel import qilinganda mijoz fayldagi nomdan aniqlanadi (yangi bo'lsa qo'shiladi).

**D-038. 2-ilovaga kirish: do'kon kodi + telefon + parol (Firebase Auth'siz).** `a/{sha256(baza|telefon)[:40]}` yozuvida mijoz
kaliti AES-GCM bilan shifrlangan, kalit = PBKDF2-HMAC-SHA256(parol, tuz, 120 000). Parol bulutga chiqmaydi. Google akkaunt
orqali kirish qo'shilmadi: har do'konda Firebase Auth va Google sozlamasini talab qiladi (do'stlarga o'rnatish qiyinlashadi).
Parol 1 marta so'raladi (2-ilovada Keystore bilan shifrlangan holda saqlanadi). Telefon o'chirilsa — mijoz kaliti almashadi,
qolgan telefonlar jimgina qayta kiradi, o'chirilgan telefon kira olmaydi. Kalit almashganda kutilayotgan buyurtmalar yangi kalit
ostiga ko'chiriladi. Avtomatik parol 8 belgi (4 harf + 4 raqam). Eski "ulash kodi" ham ishlaydi.

**D-039. Buyurtma savatchasi (2-ilova) — savdo emas.** "Narxlar"da "+" bilan tanlanadi, 1-ilovaga buyurtma bo'lib boradi
(`by` — kim yubordi). 1-ilova egasi tasdiqlasa savatchasiga tushadi (narx JORIY katalogdan) va odatdagidek rasmiylashtiriladi.
Faqat 1-ilova "Buyurtma qabul qilish" yoqiq bo'lsa ko'rinadi.

**D-040. Kompyuterdan avtomatik 1C eksporti (COM, faqat o'qish).** `pc/onec_export.py` oddiy 1C foydalanuvchisi bilan
`V83.COMConnector` orqali ulanadi, kartochka hisobotini (SKD yoki eksport protsedura) har kontragent uchun tuzib, `.xlsx`
qilib `in/{inbox}` ga qo'yadi. Faqat o'zgarganlari yuboriladi (xlsx ichidagi vaqt belgilari xeshga kirmaydi). Jadval
(1/2/3 soat, 0 — o'chiq) 1-ilovadan bulut orqali boshqariladi (`p/{inbox}/exp`), holat `p/{inbox}/st` ga yoziladi.
Hisobot nomi va parametrlari bazaga bog'liq — `--discover` natijasiga qarab `forwarder.ini [onec]` da sozlanadi.
1C'ga hech narsa yozilmaydi; har aylanishdan keyin ulanish uziladi (litsenziya band bo'lib turmasin).

**D-041. O'rgatuvchi ko'rsatmalar ekranning o'zida (coach marks).** Ekran birinchi ochilganda kerakli tugma yoritiladi va qisqa
izoh chiqadi; har ekran bir marta. Yoritilgan joyga tegish asl tugmani bosmaydi (tasodifiy amal bo'lmasin). Dvigatel umumiy
(`ui/ledger/CoachMarks.kt`), qadamlar har ilovada. Sozlash (Firebase, kompyuter, telefonlar) uchun qadamma-qadam oynalar.

**D-042. Sirli menyu (versiya yozuvini uzoq bosish).** 1-ilovada 20, 2-ilovada 19 band. Hech biri serverga (1C/SQL) yozmaydi,
secret va parollar faqat yashirilgan holda (`abc••••••yz (32)`) ko'rinadi. Umumiy qism `ui/ledger/Secret.kt` da.

**D-044. Shtrix kod bilan savdo: lokal ro'yxat + o'rganish, ZXing.** 1C'dagi shtrix kodlar bazadan to'g'ridan-to'g'ri
o'qilmaydi (serverga tegilmaydi): ega "Вывести список" dan Excel saqlab yuklaydi; ustunlar avtomatik topiladi, kod -> nom
tartibida katalogga bog'lanadi (bir xil nomli ikki tovar nom bo'yicha bog'lanmaydi). Kalit — oldidagi nollarsiz raqam
(UPC-A/EAN-13/Excel soni bir xil). Noma'lum kod bir marta tanlanadi va eslab qolinadi (import almashganda ham saqlanadi).
Haqiqiy 1C ro'yxati (4 214 qator) tahlili: vazn tovarlari 7 xonali "prefiks+kod" (28/29) — yorliqning boshi bo'yicha
topiladi; 12 xonali o'z kodlari tekshiruv raqamisiz — EAN-13 o'qilganda oxirgi raqamsiz ham qidiriladi.
Skaner ekrani kamerani to'xtatmaydi (bir kod 1,2 s ichida qayta sanalmaydi), topilmagan kod ro'yxatdan bosib bog'lanadi.
Tarozi yorlig'i formati sozlanadi; yorliqdagi kod 1C kodiga mos kelsa ham BIRINCHI marta tasdiq so'raladi (noto'g'ri tovar
jimgina tushmasin), "ishonish" sozlamasi bilan o'chiriladi. Kamera kutubxonasi — zxing-android-embedded 4.3.0 + core 3.3.0
(minSdk 23 bilan mos, ML yo'q, internet shart emas). Skaner faqat savatchaga qo'shadi — savdo odatdagidek rasmiylashtiriladi.

**D-045. Savdo ko'zgusi va avtomatik chek.** 1-ilova savdosi navbatga qo'yilishi bilan mijozning 2-ilovasiga (snapshot)
"1-ilovadan" belgili yozuv bo'lib boradi — bu server tasdig'i emas, 1C hisoboti shu vaqtni qamragach olib tashlanadi.
Chek faqat mijoz 2-ilovada o'zi yoqqanda (`p/{navbat}/auto`) yuboriladi; navbat kaliti savdo id sidan — qayta yuborilsa
ham bitta chek. Chekdagi qarz qoldig'i 1-ilova hisobi ("≈" bilan va izoh bilan), rasmiysi 1C'da. Chek ko'rinishi do'konning
qog'oz chekidan olindi; rasm bo'lib chop etiladi (kirill/lotin printerga bog'liq emas).

**D-043. Standart: qorong'i rejim, shrift 90%; "orqaga" — ikki marta bosilganda chiqish.** "Printer 3" -> "Printersiz"
(protokoldagi qiymat "3" o'zgarmagan). "−" ni uzoq bosish — tovarni nolga tushiradi (qaytarish mumkin).
1-ilova nomi "Savdo Agent", 2-ilova "Savdo Mijoz", yangi ikonkalar; applicationId o'zgarmagan.

**D-046. Narx yorliqlari: telefonda chizish, kompyuter orqali chop etish.** Xprinter kompyuterga USB bilan ulangan
(tarmoq porti yo'q), shuning uchun telefon to'g'ridan-to'g'ri printerga emas, kompyuterdagi `label_printer.py` ga yuboradi:
avval Wi-Fi/LAN (oddiy TCP "SLBL"+uzunlik+JSON — HTTP emas, Android cleartext cheklovi tegmaydi; UDP qidiruv), bo'lmasa
Firebase `L/{yorliq kaliti}` navbati. Kalit 1-ilovada yaratiladi va faqat `forwarder.ini` ga beriladi (2-ilovaga emas).
Yorliq telefonda 1C "PLU Narx" / "Shtrix chek" shakllari kabi rasm qilib chiziladi (qora-oq, shtrix modullari butun
nuqtada) — kompyuterda shrift/kirill muammosi yo'q. O'lcham: `gdi` rejimida Windows drayveridagi qog'oz (1C ham shundan
foydalanadi) telefonga aytiladi; bo'lmasa sozlamadagi mm × dpi. Ish raqami bo'yicha takror chop etilmaydi (LAN javobi
yo'qolib bulutga ham ketsa ham). PLU raqami — tarozidagi alohida ma'lumot (1C kodi ham, yorliq kodi ham emas):
"PLU Narx" faylidan (Excel/MXL) yoki qo'lda; lokal saqlanadi. Shtrix: 13 xonali — EAN-13, 12 xonali o'z kodlari — oxiriga
tekshiruv raqami (do'kondagi yorliqlardagidek), 8 — EAN-8, boshqasi — Code 128; vaznli (7 xonali "2...") — PLU yorlig'i.
Narx — katalogdagi Naqd/Optom (tanlanadi) yoki qo'lda. 1C/SQL'ga hech narsa yozilmaydi.

**D-047. 2-ilovadagi yashirin menyu — faqat foydalanuvchi sozlamalari.** Mijoz telefoni boshqa odam qo'liga tushishi
mumkin, shuning uchun 2-ilovada kalit, do'kon kodi, navbat va texnik ma'lumot ko'rsatadigan bandlar yo'q (`userOnly`).
Umumiy kod (ui/ledger/Secret.kt) bir xil qoladi, 1-ilova menyusi o'zgarmagan.

**D-048. PLU va shtrix kodlar 1C'dan kompyuter orqali.** Tahlil: PLU tarozidagi alohida raqam — 1C kodi (2555) ham,
vazn shtrix kodidagi tovar kodi (2901740 -> 01740) ham emas, demak 1C'da alohida saqlanadi ("PLU Narx" shakli shuni chiqaradi).
SQL/1C ga tegmasdan olishning yagona yo'li — mavjud `onec_export.py` (COM, oddiy foydalanuvchi, faqat so'rov). Joyi
metama'lumotdan topiladi (tovar rekviziti yoki "весы/оборудование/PLU" registri; Булево "Весовой" va boshqa havolalar
hisobga olinmaydi), qo'lda `[codes]` bilan almashtiriladi. Firebase `in/{inbox}` orqali (k = "codes"), faqat o'zgarganda.
2-ilovaga PLU `PriceItem` ning 8-elementi (additiv; eski 2-ilova o'qimaydi). Kelgan narx 2-ilovaga baribir yuborilmaydi.

**D-049. Yorliq: Windows LPD orqali to'g'ridan-to'g'ri (kompyuter dasturisiz).** Ega kompyuterda alohida dastur ishlatishni
istamadi. XP-365B faqat USB (Bluetooth/Wi-Fi yo'q), shuning uchun telefon printerga faqat kompyuter orqali yetadi; eng
yengil yo'l — Windows'ning o'z "LPD Print Service" xizmati (RFC 1179). Ilova yorliqni TSPL BITMAP qilib, 'l' (xom) turida
yuboradi — drayver o'zgartirmaydi, printer TSPL'ni o'zi tushunadi; SIZE/GAP ilova sozlamasidan. LPR klienti va TSPL
kodlovchi shared'da (JVM testlari: soxta LPD, Python versiyasi bilan bir xil bitmap). label_printer.py — ikkinchi rejim.

## D-050 — Yorliq ko'rinishi 1C shablonidan, narx = ПродажЦена1
Do'kon 1C konfiguratsiyasida yorliqlar umumiy shablonlar "ГодехЦенник" (PLU) va "ГодехШтрих" (shtrix) orqali chiqadi,
narx — `Товар.ПродажЦена1`. Ilova yorlig'i shu joylashuvni ulushlarda takrorlaydi (har qanday yorliq o'lchamiga mos),
shrift Arial o'lchamli Liberation Sans Bold (OFL, ilova ichida). Narx standart bo'yicha "Цена 1"; foydalanuvchi boshqasini tanlashi mumkin.

## D-051 — Chek ko'rinishi sozlamasi 1-ilovada, 2-ilovaga snapshot orqali
Uslub kichik JSON (`ReceiptStyle.encode`), logo PNG (kulrang, ≤576 px, ≤60 KB) — snapshot'ga qo'shimcha "rs"/"rl" maydonlari.
Eski ilovalar ularni e'tiborsiz qoldiradi; yangi ilova bo'sh/buzuq qiymatda standart ko'rinishni oladi. 1C'dagi logo (konfiguratsiya
rasmi) ilovaga qo'shilmadi — foydalanuvchi o'z logosini galereyadan tanlaydi. Shrift: Liberation Sans (Arial o'lchamli, OFL) ikkala ilovada.

## D-052 — Agent va Admin: bitta kod, flavor'lar
Ikki xil kod bazasi yuritilmaydi: `app` modulida `agent` va `admin` flavor'lari. Agent asl applicationId va imzoni saqlaydi
(ishchilar telefonida oddiy yangilanadi), Admin `.admin` qo'shimchasi bilan alohida o'rnatiladi. Rol paket nomidan aniqlanadi
(`AppRole`) — BuildConfig yoki qo'shimcha sozlama shart emas. Admin'ga xos imkoniyatlar `AppRole.isAdmin` bilan ko'rsatiladi.
Agent 2.8 dagi imkoniyatlar bilan qoladi; tuzatishlar (yorliq ko'rinishi, xatolar, tugmalar) ikkalasiga ham.

## D-053 — Sozlamalarni ko'chirish faqat parolli zaxirada
Admin yangi ilova bo'lgani uchun sozlamalar (server paroli, Firebase kaliti, mijozlar, yorliq) Agent'dan ko'chiriladi. Ular faqat
parol bilan shifrlangan zaxiraga (AES-256-GCM, PBKDF2) yoziladi; parolsiz zaxira avvalgidek ularsiz. Tiklashda faqat ma'lum fayllar
(`share/`, logo, 1C kodlari/narxlari) qabul qilinadi, `..` va mutlaq yo'llar rad etiladi; keyin ilova qayta ishga tushadi.
Ikkala ilova bitta telefonda bir xil kirish qutisini (inbox) o'qimasligi uchun: ko'chirgandan keyin o'z telefoningizda Agent'da
"Mijozlar va 2-ilova" ishlatilmaydi (Admin'da ishlaydi).

## D-054 — Agentlar "Admin orqali": Firebase ko'prigi, server ishini Admin bajaradi
Foydalanuvchi talabi: ma'lumot kompyuterdan Admin'ga, Admin'dan agent va mijozlarga; agentlarga faqat internet. Agent'da
`Backend` interfeysi ikki yo'lga ega: `ServerApi` (to'g'ridan-to'g'ri) va `RelayClient` (Firebase g/{kalit}/...). Server bilan
ishlashni Admin'dagi `RelayHost` mavjud `ServerApi` orqali bajaradi — protokol, jadvallar va server o'zgarmaydi; kompyuterga
yangi dastur ham kerak emas. Savdo kaliti = sha256(agent, tur, matn): Firebase'da bitta yozuv, Admin yozishdan oldin serverda
qidiradi (natijani joylashdan oldin o'chsa ham dublikat yo'q); agentda SENT faqat server ID'si bilan. Kirish: parol yuborilmaydi,
PBKDF2 isbot (tasodifiy tuz) Admin serverdagi parol bilan solishtiradi. Admin fon xizmati specialUse turida (Android 15 dagi
dataSync 6 soat cheklovi tufayli), zaxira — 15 daqiqalik WorkManager. Admin oflayn — agentlar ishlaydi, savdolar kutadi.

## D-055 — 4 ta narx (v3.1)
- 3 = Naqd (server kodi "0"), 4 = Optom ("1"). 1 va 2 — 1C sotish narxlari (ПродажЦена1/2, kompyuterdan `prices_1c.json`);
  1C'da bo'lmasa 4-narx olinadi. Serverga 1/2-narx Optom kodi bilan, lekin har qator narxi aniq yoziladi — protokol o'zgarmaydi.
- Mijoz'ga 1/2-narx snapshot'da `p` qatorining 9-elementi bo'lib boradi (qo'shimcha); eski Mijoz uni e'tiborsiz qoldiradi.
- 2-ilova ro'yxatidagi mijozlar (Dadaxon aka) uchun savdoda doim 4-narx (tugmalar qulflanadi).

## D-056 — Yorliqni qog'ozga moslash (v3.1)
- 1C yorliqni Windows drayveri orqali АвтоМасштаб bilan chiqaradi; biz TSPL'ni to'g'ridan yuboramiz. Printerdagi qog'oz
  joylashuvi farqini dasturda tuzatish uchun: siljitish (mm, nuqtaga aylantiriladi; manfiy — chap/yuqoridan kesiladi),
  DENSITY, kalibrovka yorlig'i (ramka va markaz chiziqlari). Rasm 3x katta chizilib kichraytiriladi (silliq qirralar).

## D-057 — 1C'ga yozish: Admin tasdig'i + kompyuter (v3.2)
- Telefon 1C'ga to'g'ridan yozmaydi. Admin egasi xulosani ko'rib tasdiqlaydi -> `w/{inbox}/{id}`; kompyuter (`onec_write.py`)
  kun birinchi yozuvidan oldin fayl bazani nusxalaydi (7 kun), hujjatni yaratib o'tkazadi, jurnalga (id, UUID, raqam) yozadi,
  natijani `wr/{inbox}/{id}` ga qaytaradi. "Kiritildi" faqat hujjat raqami kelganda.
- Bir so'rov id'si jurnalda bo'lsa qayta kiritilmaydi (natija qayta yuboriladi). Bekor qilish — o'chirish belgisi (o'chirish emas).
- Hujjat maydonlari do'kon 1C konfiguratsiyasi kodidan (Поступления, ЮкБерувчигаТулов, Возврат_*): ombor = Константы.Склад1,
  kurs = Константы.Курс, xodim = Сотрудники.Дукон; tovar narxi 1C'dagi tovar valyutasida (telefonda boshqasida yozilsa — kurs bilan).

## D-058 — 1C savdolar va ta'minotchilar (v3.2)
- Kompyuter jadval bo'yicha (o'zgarganda) oxirgi 30 kun savdolarni va 90 kun ta'minotchilarni, Admin so'rasa istalgan davrni
  (400 kungacha) JSON qilib yuboradi. Faqat ВЫБРАТЬ so'rovlari. Bazada biror hujjat turi bo'lmasa — qolganlari ishlaydi.
- Ta'minotchilar — Поступления hujjati bor kontragentlar; qoldiq ВзаиморасчетСКонтрагентом registridan (manfiy — qarzimiz).

## D-059 — Ish vaqti va avtomatik yangilash (v3.2)
- Har ilovada ish vaqti (standart 08:00–20:00). Ish vaqtida ochiq ilova N daqiqada (Agent/Admin 5, Mijoz 2), tashqarida 15
  daqiqada; yopiq ilova — WorkManager 15 daqiqa (Android cheklovi). Tugashidan 1 daqiqa oldin budilnik — to'liq yuklab olish.
  Aniq budilnik ruxsati bo'lmasa — taxminiy.
- Admin ish vaqti va tezkor oraliqni kompyuterga `p/{inbox}/exp` (m, won, ws, we) orqali yuboradi. Kompyuter har 30 soniyada
  o'qiydi; ish vaqtidan tashqarida soatiga bir.

## D-060 — 2-ilova so'rovlari (v3.2)
- `s/{readKey}/pay` va `s/{readKey}/ed`: 2-ilova faqat yangi yozuv qo'shadi (qoidalar v5), holatni Admin secret bilan yozadi.
- To'lov: Admin tasdiqlasa — odatdagi to'lov navbati (server, tur 4); 2-ilovada "Tasdiqlandi" va chek faqat server ID'si kelgach.
- Chek tuzatish: tasdiqlansa Admin snapshot'ida shu hujjat qatorlari almashadi (2-ilova chekida). 1C hujjati o'zgarmaydi.

## D-061 — Mijoz Kassa (v3.2)
- 2-filialning o'z mijozlariga savdosi faqat telefondagi `kassa.db` da (serverga, Admin'ga bormaydi). Summalar so'mda.
  Chekda do'kon nomi (brend) — 1-ilovadan; manzil/telefon/rahmat — 2-filial sozlamasi.
