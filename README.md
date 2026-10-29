# IMSOFT DOKON — versiya 2.4 (+ 2-ilova "IMSOFT Mijoz" 1.0)

Savdo agentlari uchun ilova: do'kon tanlash, tovarni savatchaga yig'ish, buyurtma va to'lovni serverga yuborish,
do'kon qarzi va hisobotni ko'rish. Avvalgi ilovaning **butun ko'rinishi va ish tartibi qayta yozildi**;
server bilan muloqot qoidalari va lokal baza esa **o'zgarmadi** (eski ma'lumot va server bilan mosligi saqlanadi).

Kotlin · MVVM · ViewBinding · Material 3 · Coroutines/Flow · minSdk 23 · targetSdk 35

## Nima o'zgardi (foydalanuvchi uchun)

| Avval | Endi |
|---|---|
| Buyurtma: menyu → oyna → 4 ta tab → har tovarda oyna → savatcha → to'lov oynasi | Bitta ekranda: do'kon → tovar qatorida **+ / −** → pastda jami → "Savatcha" → "Rasmiylashtirish" |
| Har tovarda serverdan qoldiq so'rab kutish | **Server kutilmaydi** — tovar darhol qo'shiladi |
| Qoldiqdan ortiq sotib bo'lmasdi | **Qoldiqsiz sotish**: qoldiq ko'rsatilmaydi va tekshirilmaydi |
| Kirim narxidan arzon sotib bo'lmasdi | **Narx tekshirilmaydi** — istalgan narxda sotiladi |
| "Келиш" belgisi narxni qulflab qo'ygan | **Naqd / Optom** (2 tur). **Narx hamma turda erkin o'zgartiriladi.** Kichik **"Kelgan narx"** almashtirgichi: yoqilsa hamma tovarda kelgan (kirim) narx ham ko'rinadi, o'chirilsa asl holat (saqlanmaydi, har safar o'chiq boshlanadi) |
| Ma'lumot yangilashda eski ma'lumot avval o'chirilardi | Yangilash **bitta tranzaksiyada**: ulanish uzilsa eski ma'lumot saqlanadi |
| Tarix bo'sh edi | **Tarix**: yuborilgan buyurtma va to'lovlar (tafsilot bilan) |
| Faqat kirill | **Lotin / Кирилл** (Sozlamalar), yorug' / qorong'i mavzu |

### 2.2 da nima yangi
* **Server oldindan belgilangan**: `192.168.1.200` / `Savdo` / `sa` / parol (`ServerConfig.DEFAULT`, `domain/Models.kt`). Sozlamalarda o'zgartirish mumkin.
* **Tashqi manzil** (Sozlamalar ▸ Server): do'kondan tashqarida (VPN orqali) ishlash uchun ikkinchi manzil. Ilova o'zi tanlaydi:
  do'konda `192.168.1.200`, tashqarida tashqi manzil; oxirgi ishlaganini eslab qoladi.
* **Tez do'konlar** (Sozlamalar): ko'pi bilan 2 ta. Savdoga kirganda katta tugma bo'lib chiqadi, bosilsa darhol savdo boshlanadi.
* **Taklif algoritmi** (Savdo ▸ "Taklif" filtri): mijoz tanlanganda uning tarixidan odatda oladigan tovarlar o'zi ochiladi.
  Yaqinda olinganlar muhimroq, davriy tovar "vaqti kelganda" yuqoriga chiqadi, odatiy miqdor (mediana) bilan qo'shiladi,
  "Hammasini qo'shish" tugmasi bor. Tarix kam bo'lsa umumiy mashhur tovarlar qo'shiladi. Algoritm: `domain/Suggestions.kt`.
* **Hisobot**: zamonaviy jadval dizayni, yorug'/qorong'i mavzu, **PDF / chop etish** tugmasi.

### Masofadan ishlash (bepul): Tailscale
1. Server (SQL Server) turgan kompyuterga Tailscale o'rnating, akkauntga kiring, "Run unattended" yoqing. Kompyuter uyquga ketmasin.
2. Kompyuterning Tailscale manzilini (`100.x.y.z`) yozib oling.
3. Telefonlarga Tailscale o'rnating, bir xil akkauntga kiring, VPN yoniq tursin.
4. Ilovada Sozlamalar ▸ Server ▸ **Tashqi manzil** ga shu `100.x.y.z` ni yozing, "Ulanishni tekshirish".
5. Windows Firewall'da 1433-port Tailscale tarmog'iga ham ruxsat berilgan bo'lishi kerak.
**SQL Server'ni portini internetga to'g'ridan-to'g'ri ochmang** (port-forwarding): `sa` bilan xavfli.


## 2.4 da nima yangi (1-ilova) va 2-ilova

Loyihada endi **ikki ilova** va kompyuter uchun kichik dastur bor:

| Qism | Kim uchun | Nima qiladi |
|---|---|---|
| `app/` — **IMSOFT DOKON 2.4** (1-ilova) | Agent / ega | Savdo (avvalgidek) + 1C hisobotlarini import qilib **2-ilovaga yuborish** |
| `mijoz/` — **IMSOFT Mijoz 1.0** (2-ilova) | Mijoz (Dadaxon aka) / 2-filial | Faqat ko'rish: o'zi olgan tovarlar narxi (Naqd/Optom, so'm/$), xaridlar, to'lovlar, joriy qarz. **Savdo yo'q, kelgan narx yo'q.** 2-filial chek printeri (ixtiyoriy bo'lim) |
| `pc/forwarder.py` | 1-filial kompyuteri | 1C chop etgan chekni internet orqali 2-filial telefoniga yuboradi |
| `shared/` | ikkala ilova | Umumiy kod: qidiruv, 1C hisobot o'quvchi (.xls/.htm/.txt), bulut (Firebase), ma'lumot formati |

### Ikki ilova qanday bog'lanadi (bepul)
```
1C (kompyuter) ──"Сохранить как" .xls/.htm──► 1-ilova ──► Google Firebase (bepul) ──► 2-ilova
       └─ chek ─► forwarder.py ───────────────────────────► Firebase navbati ───────► 2-ilova ─USB─► printer
1-ilova ◄── SQL Server (narx, qarz; do'konda Wi-Fi yoki tashqarida Tailscale)
```
* **Firebase Realtime Database** (Google, bepul "Spark" tarifi, karta so'ramaydi). Kutubxonasiz, oddiy HTTPS (REST).
* **Telegram nega emas:** Telegram bot o'zi (yoki boshqa bot) yuborgan xabarni `getUpdates` orqali ololmaydi — kompyuterdagi
  bot yuborgan chekni telefondagi o'sha bot o'qiy olmaydi; bundan tashqari navbat 24 soatda o'chadi. Firebase'da navbat chek
  chiqmaguncha turadi.
* **Xavfsizlik:** Firebase "secret" faqat 1-ilovada (shifrlangan) va kompyuterdagi `forwarder.ini` da. 2-ilovaga boradigan
  **ulanish kodida secret yo'q** — u bilan faqat o'sha mijoz ma'lumotini o'qish, "yangilab ber" so'rovini yozish va o'z chek
  navbatidan o'chirish mumkin (`CloudDb.RULES`, 1-ilova o'zi o'rnatadi). Kalitlar 32 belgili tasodifiy; "Kalitlarni almashtirish"
  eski kodni o'chiradi. 2-ilovaga **kelgan (kirim) narx va SQL paroli umuman yuborilmaydi**.
* **Halol cheklov:** 2-ilovadagi qarz — 1-ilova oxirgi marta serverdan olgan qiymat (vaqti bilan ko'rsatiladi). 2-ilova "Yangilash"
  bosganda 1-ilova (yoqilgan, internetda va serverga ulana olsa) 15 daqiqagacha yangilab yuboradi; ulana olmasa bor ma'lumot yuboriladi.

### 1-ilova yangiliklari (2.4)
* **Mijoz ilovasi (2-ilova)** — Sozlamalar ▸ Qo'shimcha: Firebase sozlash (qoidalar o'zi o'rnatiladi), mijoz tanlash,
  **1C hisobotini fayldan import** (`.xls` / `.htm` / `.txt`, 1C 7.7 "Mijoz kartochkasi") yoki **serverdan** (mavjud tur 5 so'rovi,
  davr tanlab), import oldidan **ko'rib chiqish** (davr, yozuvlar, jami, katalogda topilmagan tovarlar, nechta yangi/almashadi),
  avtomatik yuborish, ulanish kodini Telegram/SMS orqali yuborish, kompyuter uchun `forwarder.ini`, 2-filial printer holati.
  Bir davrni qayta import qilsangiz **dublikat bo'lmaydi** (fayl davridagi eski yozuvlar almashtiriladi).
* **Savdoni takrorlash** — Tarix ▸ buyurtma ▸ **Takrorlash**: o'sha do'kon, savdo turi, valyuta, miqdorlar; narxlar **joriy**
  (C8 qoidalari bilan). Savatcha bo'sh bo'lmasa: almashtirish / qo'shish / joriyni kutishga qo'yish. Yuborilgan buyurtma o'zgarmaydi.
* **Professional qidiruv** (`shared/Search.kt`): bir nechta so'z istalgan tartibda; lotin/kirill va tutuq belgisiz; kod bo'yicha;
  imlo xatolari (1–2 harf, h/x, q/k, qo'sh harf); **klaviatura tili adashsa ham** ("ьфнша" → mayiz); raqamlar faqat aniq/boshidan
  (134 hech qachon 120 bo'lmaydi); topilgan qism **qalin**; "taxminiy natijalar" belgisi; mashhurlik bo'yicha saralash.
  Do'kon tanlashda ham (hudud nomi bilan).
* **Ixchamroq dizayn**: ro'yxat qatorlari va tugmalar kichraytirildi + **Ekran o'lchami** (Kichik / Ixcham — odatiy / Odatiy / Katta).
* **Qulayliklar (12 ta, har biri o'chiriladi)**: ekran o'lchami; shrift; aqlli qidiruv (imlo, klaviatura); qidiruvsiz tartib
  (server/nom/ko'p sotilgan); "+" tugmasi (darhol / miqdor so'rash); tebranish; "Kelgan narx" tugmasini yashirish; avtomatik
  yangilash (1/3/6 soat); **PIN qulf** (PBKDF2, qulflash vaqti, unutilsa agent paroli bilan); **kunlik avto-zaxira** (oxirgi 7 ta,
  tiklash); **kun yakuni eslatmasi**; yuborilmagan buyurtma ogohlantirishi.
* **Masofadan ishlash yo'riqnomasi** (Sozlamalar ▸ Masofadan ishlash) — Tailscale, bepul.

### 2-ilova (IMSOFT Mijoz) ekranlari
* **Bosh sahifa**: joriy qarz (server ma'lumoti, vaqti bilan), "Yangilash", bu oy olingan/to'langan, oxirgi xarid va to'lov.
* **Narxlar**: faqat shu mijoz olgan tovarlar; Naqd/Optom, So'm/$; qidiruv; guruhlar; oxirgi xarid; tovar bosilsa — **qachon,
  qancha, necha puldan** olingani (eng arzon/qimmat narx bilan).
* **Hisobot**: Xaridlar / To'lovlar / Hammasi; Bugun, Bu hafta, Bu oy, O'tgan oy, 90 kun, Hammasi, istalgan davr; kunlar
  bo'yicha jami; **PDF / chop etish**.
* **Printer** (Sozlamalarda yoqiladi, faqat 2-filial telefonida): doimiy servis, Firebase'dan cheklar eskisidan yangisiga, USB (OTG)
  printerga **bayt o'zgartirilmasdan**; "yangi chek bor" signali darhol (SSE) + har daqiqada tekshiruv; dublikatdan himoya
  (chop etish o'rtasida to'xtasa chek o'zi qayta chiqmaydi — "noma'lum" deb ko'rsatiladi, siz tanlaysiz); sinov cheki; jurnal;
  telefon yoqilganda o'zi ishga tushadi; holati 1-ilovada ko'rinadi.
* **Sozlamalar**: ulanish kodi, til (lotin/kirill), mavzu, ekran o'lchami, shrift.

### O'zgarmaganlar
SQL Server, `savollar`/`javoblar` protokoli (tur 1, 3, 4, 5) va fixture testlari, mavjud baza jadvallari, applicationId,
C8 biznes qoidalari. 1-ilovadagi barcha yangi ma'lumot **alohida faylda** (`files/share/`, `share_secure` sozlamalari);
`dokon_tarix.sqlite` ga faqat bitta o'qish so'rovi qo'shildi (sxema o'zgarmadi).

## 2.3 da nima yangi (faqat ilova; server, protokol va asosiy baza o'zgarmagan)

### Ishonchli yuborish — best-effort dedup, KAFOLAT EMAS
Buyurtma va to'lov avval telefonga yoziladi, keyin navbat orqali yuboriladi. Har birining kvitansiya kodi bor
(buyurtma `B-0412`, to'lov `T-0013`) va holati **Navbat** ekranida ko'rinadi:

| Holat | Ma'nosi |
|---|---|
| Telefonda saqlandi, navbatda | Hali yuborilmagan |
| Yuborilmoqda… | Hozir yuborilyapti |
| Internet kutilmoqda | Oldingi urinish serverga **yetib bormagani aniq**; o'zi qayta urinadi |
| Yuborilgan bo'lishi mumkin — tekshirilmoqda | So'rov ketgan, javob kelmagan. Qayta yuborishdan **oldin** serverdan qidiriladi |
| **Serverga yozildi** | `savollar` jadvaliga yozildi (server ID bor). Bu "ofis dasturi qayta ishladi" degani **emas** — buni ilova bila olmaydi va hech qachon ko'rsatmaydi |
| **Tekshirish kerak** | Ilova o'zi xavfsiz hal qila olmadi. **O'zi yuborilmaydi.** Agent/ofis qaror qiladi |

Qanday ishlaydi:
* WorkManager: ilova yopiq bo'lsa ham internet paydo bo'lganda yuboriladi (batareya tejash kechiktirishi mumkin).
* Har qator bir vaqtda faqat bitta ishchi tomonidan yuboriladi (bazada atomik "egallash" + vaqt cheklovi).
* `INSERT` dan **oldin** telefonga "yuborish boshlandi" va shu agentning serverdagi oxirgi so'rov raqami (watermark) yoziladi.
* Javob kelmasa yoki jarayon o'lsa — keyingi safar **avval faqat `SELECT` bilan qidiriladi** (shu agent, shu tur, aynan shu matn,
  watermark'dan keyingilar). Topilsa — qayta yuborilmaydi. Tekshiruvning o'zi bajarilmasa — **hech narsa yuborilmaydi**.
* Topilmasa: agar avval yozilgani aniq bo'lgan so'rov serverda hali turgan bo'lsa (server qatorlarni o'chirmaydi degani) —
  qayta yuboriladi; aks holda **"Tekshirish kerak"**.
* "Hozir yuborish" yuborishni yarim yo'lda to'xtatmaydi. Zaxiradan tiklangan navbat hech qachon o'zi yuborilmaydi.

**Ochiq cheklov:** bu yondashuv *best-effort* (eng yaxshi urinish). Agar serverdagi ofis dasturi `savollar` qatorlarini
o'chirsa/ko'chirsa yoki matnni o'zgartirib saqlasa, qidiruv adashishi mumkin — shuning uchun shubhali holatlar avtomatik emas,
"Tekshirish kerak" ga tushadi. To'liq kafolat faqat server tomonida (UNIQUE kalit yoki API) bo'ladi — hozir server o'zgartirilmaydi.

### Boshqa yangiliklar (har biri Sozlamalar ▸ Qo'shimcha imkoniyatlar'da o'chiriladi)
* **Doimiy holat belgisi** (yuqorida): "Internet yo'q", "Server ko'rinmayapti", "Tekshirish kerak". Bosilsa — **Diagnostika**:
  internet, VPN, do'kon/tashqi manzil porti, serverga kirish, telefon soati, navbat, batareya tejash.
* **Qarz va to'lov alohida**: "Serverdan, 14:32" yoki "Oxirgi ma'lum qiymat"; hali yuborilmagan to'lovlar alohida qator
  (qarzga qo'shilmaydi); "Butun qarz" tugmasi; 15 daqiqa ichida aynan shu summali to'lovdan ogohlantirish.
* **Pul kiritish**: `1 250 000`, `1,250,000`, `1.250.000`, `12,5` to'g'ri o'qiladi; tushunarsiz summa jimgina 0 bo'lmaydi — xato ko'rsatiladi.
* **Aqlli asosiy tugma** (pastda, joyi doim bir xil): "Do'kon tanlash" / "Odatdagini qo'sh (N)" / "Yakunlash" / "To'lov qabul qilish".
  "Odatdagini qo'sh" hech qachon jimgina qo'shmaydi — nechtasi qo'shilgani va **Bekor qilish** chiqadi.
* **Izohli taklif**: "Odatda ~7 kunda ~10 · oxirgisi 8 kun oldin"; tarix kam bo'lsa "Taxminiy". Tovar oynasida
  "Kerak emas / Omborda yo'q / Keyinroq" — algoritm inobatga oladi. "Shu do'konga oxirgi narx" ko'rinadi.
* **Savatcha tekshiruvchisi** (faqat ogohlantirish, ko'pi bilan 2 ta, hech narsani to'smaydi): odatdagidan keskin ko'p miqdor,
  oxirgi narxdan keskin farq, odatda oladigan tovar yo'q. "Shunday qoldir".
* **Bekor qilish (Undo)**: tozalash, o'chirish, miqdor/narx o'zgartirish, takrorlash, odatdagini qo'shish.
* **Do'kon xulosa kartasi**: qarz (yoshi bilan), oxirgi buyurtma, vaqti kelgan tovarlar, "To'lov qabul qilish", katta "Boshlash".
* **Ko'p savatcha**: "Kutishga qo'yish" va "Kutishda: N" (do'kon nomi katta va rangli).
* **Kun yakuni** va **Lokal hisobot** (Hisobot ▸ Lokal): faqat shu telefondagi yozuvlar, aniq belgisi bilan; **CSV** eksport (Excel ochadi).
* **Zaxira**: ixtiyoriy parol bilan shifrlash (AES-256), tiklashdan oldin ko'rib chiqish, versiya va yaxlitlik tekshiruvi,
  tiklashdan oldin joriy bazaning nusxasi (xato bo'lsa qaytariladi). Yangi baza versiyasiga o'tishdan oldin ham avtomatik nusxa.
* **KPI o'lchagich** (odatda o'chiq): buyurtmaga ketgan soniya va bosishlar, faqat telefonda.
* **Server paroli** ekranda hech qachon ko'rsatilmaydi (Login ▸ Server sozlamalarida ham). Ilovaga o'rnatilgan parol
  manba kodda oddiy matn emas. *Ochiq cheklov:* APK'ni tahlil qilgan odam uni baribir tiklay oladi — haqiqiy yechim server
  tomonida cheklangan login (hozir mumkin emas).

### Imzo (muhim)
Telefondagi ilovani **ma'lumotni saqlagan holda yangilash** uchun yangi APK aynan o'sha kalit bilan imzolangan bo'lishi kerak.
GitHub Actions o'z kaliti bilan imzolaydi — bunday APK ("CI-ONLY") eski ilova ustiga o'rnatilmaydi. O'z kalitingiz bilan:
GitHub ▸ Settings ▸ Secrets ▸ Actions'ga qo'shing: `SIGNING_KEYSTORE_B64` (keystore fayli base64), `SIGNING_STORE_PASSWORD`,
`SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD` (Android Studio debug kaliti uchun: `android` / `androiddebugkey` / `android`).
Shunda artefakt "OWN-KEY" deb nomlanadi va CI logida sertifikat SHA-256 chiqadi. Keystore repoga hech qachon qo'shilmaydi.
Yangilash, ma'lumotni saqlash va orqaga qaytish tartibi: `docs/EGA_QOLLANMA.md`.

## 2.1 da nima yangi

**Tezlik**
* Savatcha **xotirada** ishlaydi: "+" bosilganda natija zumda, bazaga yozish orqada. Avval har bosishda butun ro'yxat qayta hisoblanardi.
* Ro'yxatda faqat **o'zgargan qator** yangilanadi, narx qatorda kerak bo'lganda hisoblanadi.
* Buyurtma/to'lov **navbatga** tushadi (zumda), serverga yuborish orqada. Internet yo'q bo'lsa navbatda turadi va avtomatik qayta uriniladi.
  Takroriy yuborishdan himoya bor (v2.3 da kuchaytirilgan), lekin bu **kafolat emas** — pastdagi "Ishonchli yuborish" bo'limiga qarang. Bosh sahifada "Yuborilmagan" kartasi va "Hozir yuborish" tugmasi.

**Savdo ekrani**
* Ixcham qatorlar (ko'proq tovar sig'adi), asosiysi katta (nom, narx, son), ikkinchi darajalisi kichik. **Qoldiq ko'rsatilmaydi.**
* Sarlavha (do'kon, narx turi, valyuta) ro'yxatni aylantirganda **yig'iladi**.
* Narxni bosib darhol tahrirlash; "+" ni uzoq bosish = bitta blok (yoki 10 ta) birdan; bosganda tebranish.
* **Aqlli qidiruv**: kirill va lotin farqi yo'q ("yog" yozsa "Ёғ" ham topiladi), kod aynan mos kelsa birinchi chiqadi, so'z boshi bo'yicha saralanadi.
* Filtrlar: **Tez-tez** (shu do'konga ko'p sotilganlar), **Savatchada**, guruhlar.
* **Oxirgi buyurtmani takrorlash** (do'konga oldingi buyurtma joriy narxlarda savatchaga qo'shiladi).
* Tovar oynasida tezkor +1 / +5 / +10 / +blok.

**Qo'shimcha**
* Yakunlash: chegirma 5/10/15 % tugmalari, **Yaxlitlash** (so'mni butun mingga), do'kon qarzi ko'rinadi.
* Do'kon tanlashda so'nggi do'konlar yuqorida, "Faqat qarzdorlar" filtri.
* Bosh sahifada bugungi buyurtmalar/to'lovlar **summasi**.
* Tarixda buyurtmani **ulashish** (Telegram/WhatsApp matn), "Kutilmoqda" belgisi.
* Sozlamalarda "Ekran o'chmasin".

## Ekranlar
Pastki menyu: **Bosh sahifa · Savdo · To'lov · Tarix · Hisobot**. Sozlamalar — bosh sahifadagi tishli g'ildirak.

* **Savdo**: do'kon (qidiruv bilan), savdo turi, valyuta (so'm/$), rejim (Sotuv/Qaytarish), guruh filtri, qidiruv (nom yoki kod),
  tovar qatorini bosib miqdor/blok/narxni tahrirlash. Savatchada o'chirish — surib (qaytarish mumkin).
* **Yakunlash**: chegirma (% yoki summa), to'lov (naqd/plastik yoki $), "To'liq to'lash", printer, qarzga qoladigan summa.
* **To'lov**: do'kon tanlansa qarz avtomatik so'raladi; naqd / o'tkazma / Click; izoh; printer.
* **Hisobot**: do'kon + davr (kalendar) → server hisoboti.

## Server bilan muloqot (avvalgi ilova bilan BIR XIL)
Serverdagi `savollar` / `javoblar` jadvallari orqali, matn formatlari o'zgarmagan:

```
Buyurtma (1):  agent^do'kon^dd.MM.yyyy HH:mm:ss^valyuta^savdoTuri^naqd^dollar^plastik^chegirma^kurs^rejim^printer~tovar^soni^narx@...
To'lov (4):    agent^do'kon^naqd^0^vaqt^kurs^izox^naqd^o'tkazma^click^printer~
Qarz (3):      so'rov = do'kon id,  javob = "so'm:dollar"
Hisobot (5):   so'rov = dd.MM.yyyy#dd.MM.yyyy#do'kon id,  javob = HTML
```
Savdo turi kodlari: `0` naqd, `1` optom (eski `2` kodi endi optom hisoblanadi). Rejim: `1` sotuv, `2` qaytarish. Tovar qoldig'ini so'rash (tur 2)
endi ishlatilmaydi.

## Eski ma'lumot bilan moslik
* `applicationId` o'zgarmagan: `com.ibrohimjon.imsoft_dokon_som_val`.
* Lokal baza `assafocopy.sqlite` (versiya 1), jadval va ustun nomlari **o'zgarmagan**. Joriy savdo (do'kon, savdo turi,
  valyuta, rejim) avvalgidek `user` sozlamalari va `zakaz` jadvalida saqlanadi — tugallanmagan buyurtma yangilanishdan keyin ham qoladi.
* Server login/paroli birinchi ishga tushishda eski `sozlama` jadvalidan **shifrlangan omborga** (Android Keystore)
  ko'chiriladi, jadvaldagi parol o'chiriladi. Qayta kiritish shart emas.
* Yuborilgan buyurtma/to'lovlar jurnali alohida faylda (`dokon_tarix.sqlite`): asosiy baza sxemasi o'zgarmaydi.

## Ataylab olib tashlanganlar
* **Avto-yangilash**: avvalgi ilovada u doim "yangilanish yo'q" qaytarardi (ishlamas edi) va imzo tekshirmasdan APK o'rnatardi — xavfli.
* **PIN-qulf**: avvalgi ilovada hech qayerda ishga tushmagan. (2.4 da **ixtiyoriy, ishlaydigan** PIN qulf qo'shildi — Qulayliklar.)
* Keraksiz ruxsatlar (telefon holati, joylashuv, Bluetooth, xotira, o'rnatish, sync) olib tashlandi: faqat INTERNET va tarmoq holati. (2.4: bildirishnoma ruxsati — faqat eslatma yoqilganda so'raladi.)
* `minSdk` 21 → **23** (shifrlangan saqlash uchun). Android 5.x qurilmalarda o'rnatilmaydi.
* `allowBackup=false`: server paroli shifrlangan, avtomatik zaxiraga tushmasin. O'rniga ilova ichida **Zaxira nusxa** (Sozlamalar).

## Build va imzo
1. Android Studio (Ladybug+), JDK 17 → papkani oching → Gradle sync → `Build ▸ Build APK(s)` yoki `./gradlew assembleDebug`.
2. Kompyutersiz: GitHub'ga yuklab, `Actions ▸ Build debug APK` (`.github/workflows/build.yml`).
3. **Eski ilova ustiga yangilash** uchun APK eski bilan **bir xil kalit** bilan imzolanishi shart. Asl APK "Android Debug"
   sertifikati bilan imzolangan (SHA-256 `B6:93:7C:60:93:6B:6B:13:30:8B:B1:8A:BD:9A:F7:54:5D:61:B6:DC:99:AF:9A:03:A1:67:41:99:13:76:56:22`).
   Shu kalitli `debug.keystore` bo'lsa `keystore.properties.example` → `keystore.properties`. Bo'lmasa: eskisini o'chirishdan oldin
   eski ilovada ma'lumotni olib qo'ying (oldingi qo'llanma: `adb run-as`), yangisida **Sozlamalar ▸ Zaxira nusxa ▸ Tiklash**.
   Hozirgi o'rnatilgan (2-bosqich) ilovangiz shu kalit bilan bo'lsa, yangi versiya uning ustiga ma'lumot bilan o'rnatiladi (`versionCode` 1 → 2).

## Tuzilma
```
shared/src/main/kotlin/uz/imsoft/shared/   umumiy: Search (qidiruv), CloudDb (Firebase REST), LinkCode, Json, B64,
                                           report/ (Grid, HtmlTable, XlsReader, CardParser, Snapshot, ClientStats)
mijoz/                                     2-ilova (uz.imsoft.mijoz): data, printer (PrintService, UsbPrinter), ui
pc/                                        forwarder.py (kompyuter), test_forwarder.py
app/src/main/java/com/ilxomjon/dur_novvot_dokon/
  core/     ServiceLocator (qo'lda DI), Money, Phone
  domain/   Models, Pricing (narx qoidalari)
  data/     local (SQLite DAO), remote (jTDS, Protocol, ServerApi), repo (Catalog, Draft, Order, Report, Auth, Backup, History)
  ui/       MainActivity + home / sale / payment / report / history / settings / login / common
tools/gen_strings.py        1-ilova: lotin strings.xml dan kirill strings.xml ni avtomatik hosil qiladi
tools/gen_strings_mijoz.py  2-ilova matnlari (xuddi shunday)
tools/make_card_fixtures.py 1C hisobotiga o'xshash test fayllari (.htm, .xls)
```
Matnlarni o'zgartirish: `tools/gen_strings.py` dagi `STRINGS` (lotin) ni tahrirlab `python3 tools/gen_strings.py` ishga tushiring;
kirillni qo'lda to'g'rilash kerak bo'lsa `OVERRIDES` ga yozing.

## Testlar
* Ishga tushirish: `./gradlew testDebugUnitTest`, `python3 tools/sql_smoke_test.py`, `python3 pc/test_forwarder.py` (telefon, server va internet kerak emas). GitHub Actions har push'da avtomatik bajaradi.
* v2.4: `SharedCoreTest` (JSON, Base64, ulanish kodi, qidiruv: ko'p so'z, kirill/lotin, imlo, klaviatura, raqamlar, ajratib ko'rsatish, 5000 tovarda tezlik),
  `ReportImportTest` (1C hisobotining .htm va .xls (BIFF8, birlashtirilgan kataklar, katta SST) ko'rinishlari, So'm/$ qatorlari, kunlik jami qatorlari, qayta importda dublikat yo'q),
  `ClientStatsTest`, `CloudFlowTest` (soxta Firebase bilan: 1-ilova yuboradi → 2-ilova kod bilan o'qiydi; ruxsatlar; chek navbati idempotent), `test_forwarder.py` (8 test).
* `OutboxEngineTest` — navbat yadrosi: 300 + 150 tasodifiy nosozlik ssenariysi (ulanish uzilishi, javob yo'qolishi, jarayon o'limi, qatorlarni o'chiradigan server), bir vaqtda ikki ishchi. Shart: 0 takroriy, 0 yo'qolgan; shubhalilar "Tekshirish kerak".
* `BusinessRulesTest` (C8: arzon sotish to'smaydi, tekshiruvchi faqat ogohlantiradi; takliflar, lokal hisobot, CSV, parol manbada yo'q), `MoneyParseUserTest`, `BackupCryptoAndMatchTest`.
* `tools/sql_smoke_test.py` — `HistoryDb.kt` dagi haqiqiy SQL bilan v2 → v3 migratsiya (qatorlar saqlanadi) va atomik egallash.
* `ProtocolFixtureTest` — serverga ketadigan tur 1/3/4/5 matnlari `app/src/test/resources/fixtures/protocol.txt` bilan **bayt-bayt** solishtiriladi, 5 ta til/locale'da (uz-Latn, uz-Cyrl, ru, en, ar).
* `MoneyFixtureTest` — pul/miqdor formatlari (`fixtures/money.txt`).
* Test yiqilsa — protokol yoki pul formati o'zgargan. Ataylab bo'lmasa, o'zgarishni qaytaring. Fixture faylini faqat egasi roziligi bilan tahrirlang.
* CI'dagi debug APK vaqtinchalik kalit bilan imzolanadi — **agent telefoniga o'rnatmang**.

## Tekshirilgan / tekshirilmagan (ochiq aytilgan)
Batafsil: `docs/TEST_REPORT.md`. Qisqasi — **tekshirilgan**: protokol fixture'lari (bayt-bayt, 5 til), navbat yadrosi
(450 nosozlik ssenariysi, 1038 jarayon o'limi — 0 takroriy), pul, biznes qoidalari, zaxira shifri, SQL migratsiyasi;
butun Kotlin kodi kompilyatsiyasi (Android 35 API'ga qarshi). **Tekshirilmagan**: haqiqiy telefon, haqiqiy server,
haqiqiy Gradle/AAPT2/R8 build (GitHub Actions'da birinchi ishga tushishda ko'rinadi).

## Tavsiya (server tomoni)
Telefon SQL Server'ga **to'g'ridan-to'g'ri** ulanadi (jTDS). Bu arxitektura o'zgarmadi (server tomonda API kerak bo'lgani uchun),
lekin xavfsizlik uchun alohida REST API orqali ishlash tavsiya etiladi. Hozircha: SQL Server'da ilova uchun
faqat kerakli jadvallarga huquqi cheklangan alohida login ishlating.
