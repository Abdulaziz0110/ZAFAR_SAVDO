# IMSOFT DOKON — versiya 2.2

Savdo agentlari uchun ilova: do'kon tanlash, tovarni savatchaga yig'ish, buyurtma va to'lovni serverga yuborish,
do'kon qarzi va hisobotni ko'rish. Avvalgi ilovaning **butun ko'rinishi va ish tartibi qayta yozildi**;
server bilan muloqot qoidalari va lokal baza esa **o'zgarmadi** (eski ma'lumot va server bilan mosligi saqlanadi).

Kotlin · MVVM · ViewBinding · Material 3 · Coroutines/Flow · minSdk 23 · targetSdk 35

## Nima o'zgardi (foydalanuvchi uchun)

| Avval | Endi |
|---|---|
| Buyurtma: menyu → oyna → 4 ta tab → har tovarda oyna → savatcha → to'lov oynasi | Bitta ekranda: do'kon → tovar qatorida **+ / −** → pastda jami → "Savatcha" → "Rasmiylashtirish" |
| Har tovarda serverdan qoldiq so'rab kutish | **Server kutilmaydi** — tovar darhol qo'shiladi |
| Qoldiqdan ortiq sotib bo'lmasdi | **Qoldiqsiz sotish**: qoldiq faqat ma'lumot (manfiy bo'lsa qizil) |
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

## 2.1 da nima yangi

**Tezlik**
* Savatcha **xotirada** ishlaydi: "+" bosilganda natija zumda, bazaga yozish orqada. Avval har bosishda butun ro'yxat qayta hisoblanardi.
* Ro'yxatda faqat **o'zgargan qator** yangilanadi, narx qatorda kerak bo'lganda hisoblanadi.
* Buyurtma/to'lov **navbatga** tushadi (zumda), serverga yuborish orqada. Internet yo'q bo'lsa navbatda turadi va avtomatik qayta uriniladi.
  Qayta urinishda server tekshiriladi, shuning uchun buyurtma **ikki marta tushmaydi**. Bosh sahifada "Yuborilmagan" kartasi va "Hozir yuborish" tugmasi.

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
* **PIN-qulf**: avvalgi ilovada hech qayerda ishga tushmagan.
* Keraksiz ruxsatlar (telefon holati, joylashuv, Bluetooth, xotira, o'rnatish, sync) olib tashlandi: faqat INTERNET va tarmoq holati.
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
app/src/main/java/com/ilxomjon/dur_novvot_dokon/
  core/     ServiceLocator (qo'lda DI), Money, Phone
  domain/   Models, Pricing (narx qoidalari)
  data/     local (SQLite DAO), remote (jTDS, Protocol, ServerApi), repo (Catalog, Draft, Order, Report, Auth, Backup, History)
  ui/       MainActivity + home / sale / payment / report / history / settings / login / common
tools/gen_strings.py   lotin strings.xml dan kirill strings.xml ni avtomatik hosil qiladi
```
Matnlarni o'zgartirish: `tools/gen_strings.py` dagi `STRINGS` (lotin) ni tahrirlab `python3 tools/gen_strings.py` ishga tushiring;
kirillni qo'lda to'g'rilash kerak bo'lsa `OVERRIDES` ga yozing.

## Testlar (Phase 1, 1-qadam)
* Ishga tushirish: `./gradlew testDebugUnitTest` (telefon va server kerak emas). GitHub Actions har push'da avtomatik bajaradi.
* `ProtocolFixtureTest` — serverga ketadigan tur 1/3/4/5 matnlari `app/src/test/resources/fixtures/protocol.txt` bilan **bayt-bayt** solishtiriladi, 5 ta til/locale'da (uz-Latn, uz-Cyrl, ru, en, ar).
* `MoneyFixtureTest` — pul/miqdor formatlari (`fixtures/money.txt`).
* Test yiqilsa — protokol yoki pul formati o'zgargan. Ataylab bo'lmasa, o'zgarishni qaytaring. Fixture faylini faqat egasi roziligi bilan tahrirlang.
* CI'dagi debug APK vaqtinchalik kalit bilan imzolanadi — **agent telefoniga o'rnatmang**.

## Tekshirilgan / tekshirilmagan (ochiq aytilgan)
**Tekshirilgan** (bu muhitda): barcha Kotlin kodi Kotlin 2.0.21 kompilyatori bilan (asl APK'dagi AndroidX/Material baytkodi va
Android 35 `android.jar` ga qarshi) **0 xato**; barcha XML yaxlit, resurs/matn havolalari to'liq; 42 ta mantiqiy tekshiruv
(buyurtma/to'lov matn formatlari, narx hisoblash, pul/telefon formatlash) — asl ilova formatlariga mos.

**Tekshirilmagan**: Gradle build (bu muhitda Maven yo'q edi), AAPT2 resurs bog'lash, R8 (`release` da `minify` yoqilgan,
qoidalar `proguard-rules.pro`da), telefonda haqiqiy ishlash va haqiqiy server bilan buyurtma/to'lov yuborish.
Birinchi build'da kichik xato chiqsa, xato matnini yuboring.

## Tavsiya (server tomoni)
Telefon SQL Server'ga **to'g'ridan-to'g'ri** ulanadi (jTDS). Bu arxitektura o'zgarmadi (server tomonda API kerak bo'lgani uchun),
lekin xavfsizlik uchun alohida REST API orqali ishlash tavsiya etiladi. Hozircha: SQL Server'da ilova uchun
faqat kerakli jadvallarga huquqi cheklangan alohida login ishlating.
