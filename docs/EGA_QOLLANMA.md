# IMSOFT DOKON 2.3 — ega uchun qisqa qo'llanma

Kerak: telefonda **USB debugging** yoqilgan, kompyuter (yoki ikkinchi telefonda Termux: `pkg install android-tools`) va `adb`.
Xiaomi/HyperOS: Sozlamalar ▸ Dasturchi uchun ▸ **"USB orqali o'rnatish"** ham yoqilgan bo'lsin.
Paket nomi: `com.ibrohimjon.imsoft_dokon_som_val`. SQL Server'ga hech narsa qilinmaydi.

## (a) Eski ilovadan ma'lumotni xavfsiz olish va yangilash
1. **Avval nusxa (har doim, birinchi qadam).** Telefondagi ilova "debuggable" bo'lgani uchun root kerak emas:
   ```
   adb exec-out run-as com.ibrohimjon.imsoft_dokon_som_val tar -cf - databases shared_prefs > imsoft_nusxa.tar
   ```
   Fayl hajmi 0 KB bo'lmasligi kerak. Bu faylni va eski APK faylini 2 hafta saqlang.
2. **Yangilashni sinash (xavfsiz — muvaffaqiyatsiz bo'lsa telefondagi hech narsa o'zgarmaydi):**
   ```
   adb install -r IMSOFT_DOKON-2.3.apk
   ```
   * `Success` — tayyor, ma'lumot joyida. Endi (b) sinoviga o'ting.
   * `INSTALL_FAILED_UPDATE_INCOMPATIBLE` — APK boshqa kalit bilan imzolangan. Eng yaxshi yo'l: telefondagi ilovani imzolagan
     keystore'ni GitHub Secrets'ga qo'yib qayta build qiling (README ▸ "Imzo"). Kalit topilmasa — 3-qadam.
3. **Kalit topilmasa (faqat 1-qadamdagi nusxa bo'lsa!):**
   ```
   adb uninstall com.ibrohimjon.imsoft_dokon_som_val
   adb install IMSOFT_DOKON-2.3.apk
   adb shell "run-as com.ibrohimjon.imsoft_dokon_som_val tar -xf -" < imsoft_nusxa.tar
   ```
   Ilovani **3-buyruqdan keyin** oching. Kirgach: tovarlar, do'konlar, tugallanmagan savatcha joyidaligini ko'ring;
   Sozlamalar ▸ Server ma'lumotlarini tekshiring (parolni qayta kiritish kerak bo'lishi mumkin).

## (b) 1 telefon, 1 kichik real buyurtma bilan sinov
1. Kirish ▸ "Yangilash" ▸ tovar va do'konlar soni avvalgidek.
2. Yuqoridagi holat belgisi bosilsa **Diagnostika**: Internet ✅, Serverga kirish ✅, telefon soati ✅.
3. Ofisni ogohlantiring. **Bitta do'konga, bitta arzon tovar, 1 dona** buyurtma yuboring.
4. Bosh sahifa ▸ **Navbat**: kod (masalan `B-0001`) va holat **"Serverga yozildi"**.
5. Ofisdan so'rang: buyurtma ofis dasturida **bir marta** ko'rindimi, summasi va soni to'g'rimi, chek kerakli printerdan chiqdimi.
6. Internetni o'chiring ▸ yana 1 dona buyurtma ▸ "Telefonda saqlandi". Ilovani yoping, internetni yoqing, 1–2 daqiqa kuting ▸
   Navbatda "Serverga yozildi". Ofis: bu ham **bir marta** tushdimi.
7. Navbatda "Hozir yuborish" ni bir necha marta tez-tez bosing — hech narsa ikki marta tushmasligi kerak.
8. To'lov ekrani: do'kon tanlang ▸ qarz "Serverdan, HH:MM" bilan chiqadi (to'lov yubormasangiz ham bo'ladi).
9. Hisobot ▸ Serverdan (avvalgidek) va Lokal; Bosh sahifa ▸ **Kun yakuni** — 2 ta sinov buyurtmasi ko'rinadi.
10. Sozlamalar ▸ Zaxira nusxa saqlash (parolli yoki parolsiz) — fayl yaratildi.
11. Ofis sinov buyurtmalarini o'zi bekor qiladi (ilovada yuborilganni bekor qilish yo'q).
"Tekshirish kerak" chiqsa — **"Baribir qayta yuborish"ni bosishdan oldin** ofisdan so'rang: bu kod bo'yicha buyurtma bormi.
Hammasi joyida bo'lsa — qolgan telefonlarga birma-bir o'rnating.

## (c) Muammo chiqsa — eski APK'ga qaytish
1. Navbat ▸ yuborilmaganlar **0** bo'lsin (bo'lmasa kodlarini yozib oling va ofisga ayting). Sozlamalar ▸ Zaxira nusxa saqlang.
2. Kalit bir xil bo'lsa: `adb install -r -d eski.apk` (ma'lumot qoladi).
3. Kalit boshqa bo'lsa: `adb uninstall com.ibrohimjon.imsoft_dokon_som_val`, `adb install eski.apk`, keyin (a)-3 dagi
   `tar -xf` buyrug'i bilan **yangilashdan oldingi** `imsoft_nusxa.tar` ni qaytaring va ilovani oching.
4. Faqat **eski APK** (`IMSOFT_DOKON_2610` yoki telefondagi avvalgi ilova) ga qayting — 2.2 sinov build'lariga emas.
5. Nima bo'lganini (ekran rasmi, Diagnostika matni) saqlab, dasturchiga yuboring.

---

# 2.4 — yangi qismlar (har biri qisqa qadamlar)

## (d) 1-ilovani 2.4 ga yangilash
2.3 sizning kalitingiz bilan o'rnatilgan, 2.4 ham o'sha kalit bilan chiqadi — **oddiy yangilash**, ma'lumot joyida qoladi.
1. GitHub ▸ Actions ▸ oxirgi yashil build ▸ **IMSOFT_DOKON-debug-apk-OWN-KEY** ni yuklab oling.
2. Telefonda APK'ni oching ▸ "Yangilash". (Yoki kompyuterdan: `adb install -r app-debug.apk`.)

## (e) Firebase (bepul bulut) — bir marta, ~5 daqiqa
1. **console.firebase.google.com** ▸ **Create a project** (yoki "Add project") ▸ nom: `imsoft` ▸ Analytics: o'chiring ▸ Create.
2. Chap menyuda **Realtime Database** ("Build" yoki "Databases" bo'limida) ▸ **Create Database** ▸ joy: **Belgium (europe-west1)** ▸ **locked mode** ▸ Enable.
3. Sahifa tepasidagi manzilni nusxalang: `https://imsoft-…-default-rtdb.europe-west1.firebasedatabase.app`.
4. ⚙ (Project settings) ▸ **Service accounts** ▸ **Database secrets** ▸ **Show** ▸ nusxalang (bo'sh bo'lsa **Add secret**).
5. 1-ilova ▸ Sozlamalar ▸ **Mijoz ilovasi (2-ilova)** ▸ manzil va secret'ni joylang ▸ **Saqlash va tekshirish**.
6. **Mijozni tanlang** ▸ Dadaxon Aka (TRZ).
Tarif bepul ("Spark"), karta kerak emas. Qoidalarni ilova o'zi o'rnatadi. Secret'ni hech kimga bermang.

## (f) 1C hisobotini 1-ilovaga olish (har kuni yoki har hafta)
1. 1C ▸ Mijoz kartochkasi ▸ mijoz va davrni tanlang ▸ **Ko'rsatish**.
2. **Файл ▸ Сохранить как** ▸ turi **Excel (*.xlsx)** ▸ saqlang. (`.mxl` — 1C'ning o'z formati, o'qilmaydi.)
3. Faylni telefonga o'tkazing (Telegram "Saved messages" eng oson).
4. 1-ilova ▸ Mijoz ilovasi ▸ **Excel faylni yuklash** ▸ faylni tanlang ▸ ko'rib chiqing ▸ **Saqlash**.
   Avto-yuborish yoqilgan bo'lsa 2-ilovaga o'zi ketadi.
* Bir davrni qayta yuborsangiz dublikat bo'lmaydi. Oxirgi 7 kunni yuborish yetarli.
* "Hisobot tanilmadi" chiqsa — "Yuborish" tugmasi bilan matnni dasturchiga yuboring.
* **Serverdan olish** tugmasi 1-ilovaning odatdagi "Hisobot ▸ Serverdan" so'rovini ishlatadi; agar u ham shu jadvalni bersa — fayl kerak emas.

## (g) 2-ilova (IMSOFT Mijoz) ni o'rnatish
1. GitHub ▸ Actions ▸ **IMSOFT_MIJOZ-debug-apk-OWN-KEY** ▸ yuklab oling ▸ mijoz (yoki 2-filial) telefoniga o'rnating (Android 8+).
2. 1-ilova ▸ Mijoz ilovasi ▸ **Ulanish kodini yuborish** ▸ Telegram orqali o'sha telefonga yuboring.
3. 2-ilovada xabarni nusxalang ▸ **Kodni joylash** ▸ OK. Bosh sahifada qarz va ma'lumot chiqadi.
* "Yangilash" bosilsa 1-ilovadagi **eng so'nggi** ma'lumot darhol keladi (1-ilovada Excel yuklanganda u darhol yuboriladi).
  Narx va qarzni serverdan yangilash ham so'raladi — 1-ilova buni server ko'ringanda (do'kon tarmog'ida) bajaradi.

## (h) 2-filial chek printeri
**Telefonda (2-filial):**
1. 2-ilova ▸ Sozlamalar ▸ **"Printer" bo'limini ko'rsatish** ▸ yoqing.
2. Printer bo'limi ▸ **Servisni yoqish** ▸ bildirishnomaga ruxsat bering.
3. Printerni USB (OTG) bilan ulang ▸ chiqqan oynada **"Doim"** belgilab OK.
4. **Batareya cheklovini o'chirish** ▸ ruxsat. Xiaomi: Sozlamalar ▸ Ilovalar ▸ IMSOFT Mijoz ▸ Avto-ishga tushirish **yoqilgan**, Batareya ▸ **Cheklovsiz**.
5. **Sinov cheki** ▸ chek chiqishi kerak.
**Kompyuterda (1-filial):**
1. `pc` papkasini (forwarder.py, forwarder.vbs) `C:\imsoft-chek\` ga ko'chiring. Eski Telegram'li forwarder ishlayotgan bo'lsa — Vazifalar dispetcheridan `pythonw` ni to'xtating.
2. 1-ilova ▸ Mijoz ilovasi ▸ **Kompyuter uchun chek sozlamasi** ▸ Telegram orqali kompyuterga yuboring ▸ matnni Bloknotga qo'yib
   `C:\imsoft-chek\forwarder.ini` nomi bilan saqlang ("Тип файла: Все файлы", kodirovka UTF-8).
3. Sinov: `cmd` ▸ `cd C:\imsoft-chek` ▸ `python forwarder.py --test` ▸ telefondagi printerdan chek chiqadi.
4. `forwarder.vbs` yorlig'ini **Win+R ▸ `shell:startup`** papkasiga qo'ying ▸ ikki marta bosing (endi kompyuter yoqilganda o'zi ishlaydi).
5. Windows'dagi `filial2` printeri (Standard TCP/IP, `127.0.0.1`, Raw, 9100) avvalgidek qoladi — 1C cheklarni shunga chop etadi.
* Holat: `python forwarder.py --status` yoki 1-ilova ▸ Mijoz ilovasi (yuqorida "2-filial printeri").
* Internet yo'q bo'lsa cheklar kompyuterda (`outbox`) yoki bulutda kutadi va keyin tartib bilan chiqadi.

## (i) Masofadan savdo (Wi-Fi'siz) — Tailscale, bepul
**Server kompyuterida (SQL Server turgan):**
1. **tailscale.com/download** ▸ Windows ▸ o'rnating ▸ Gmail bilan kiring. Tailscale belgisi ▸ **Run unattended** (yoki "Unattended mode") yoqing.
2. Belgidagi manzilni (`100.x.y.z`) yozib oling. Kompyuter uyquga ketmasin (Elektr ta'minoti ▸ "Hech qachon").
3. PowerShell'ni **administrator** sifatida oching va bitta buyruq:
   `New-NetFirewallRule -DisplayName "SQL Tailscale" -Direction Inbound -Protocol TCP -LocalPort 1433 -RemoteAddress 100.64.0.0/10 -Action Allow`
   (faqat Tailscale tarmog'iga ochiladi; SQL Server'ning o'zi o'zgarmaydi).
**Har bir agent telefonida:**
4. Play Market ▸ **Tailscale** ▸ o'sha Gmail bilan kiring ▸ yoqing.
5. 1-ilova ▸ Sozlamalar ▸ Server ▸ **Tashqi manzil** = `100.x.y.z` ▸ "Ulanishni tekshirish".
Ilova do'konda `192.168.1.200` ni, tashqarida tashqi manzilni o'zi tanlaydi. **SQL portini routerda ochmang** (port-forwarding) — `sa` bilan xavfli.
**Kompyuterdan masofadan 1C:** ikkinchi kompyuterga ham Tailscale o'rnating va server kompyuteriga **Удалённый рабочий стол**
(`mstsc` ▸ `100.x.y.z`; Windows Pro kerak) yoki bepul **RustDesk** orqali ulaning — 1C'da odatdagidek ishlaysiz.

---

# 2.5 — yangi qismlar

## (j) 1-ilovani 2.5 ga, 2-ilovani 1.1 ga yangilash
Ikkalasi ham oddiy yangilash (ma'lumot joyida qoladi): GitHub ▸ Actions ▸ oxirgi yashil build ▸ ikkala APK ▸ telefonlarda "Yangilash".
Birinchi ochilishda ekran o'lchami 100% bo'ladi (oldingi "Ixcham" o'rniga). Kichikroq kerak bo'lsa: Sozlamalar ▸ Qulayliklar ▸ Ekran ▸ 90%.

## (k) 1C tarixi (ikkala ilovada)
* 1-ilova: **Tarix ▸ 1C tarixi** yorlig'i (yoki Bosh sahifa ▸ "1C tarixi" kartasi). 2-ilova: pastdagi **Tarix**.
* Qidiruv qatoriga istalganini yozing: `pista`, `pista 40kg`, `200000`, `27.10`, `27.10-29.10`, `oktabr`, `to'lov >10mln`, `-pista`.
  (i) tugmasi — to'liq yo'riqnoma.
* Hujjatni bosing: tovarlar, jami, qoldiq. Tovarni bosing — shu tovar qachon, qancha, necha puldan olingani.
* **Chek** — rasmni ko'rasiz: Chop etish / Rasm (Telegram) / PDF.
  1-ilovada chop etish chekni **2-filial printeriga** yuboradi (2-ilova telefoni internetda bo'lsa chiqadi).
  Chek tepasidagi do'kon nomi: 1-ilova ▸ Mijoz ilovasi ▸ **Chek** ▸ sarlavha.
* 1-ilova: **Takrorlash (savatchaga)** — narx hozirgi katalogdan; "≈" belgili tovarni tekshirib belgilang.

## (l) 2-ilovadan qayta buyurtma
1. 2-ilova ▸ Tarix ▸ savdoni bosing ▸ **Qayta buyurtma** ▸ miqdorlarni tekshiring ▸ izoh (ixtiyoriy) ▸ **1-ilovaga yuborish**.
2. 1-ilovaga bildirishnoma keladi (Mijoz ilovasi ▸ "2-ilovadan buyurtmalar" yoki Tarix ▸ 1C tarixi tepasida).
3. Buyurtmani bosing ▸ **Savatchaga qo'shish** ▸ odatdagidek rasmiylashtiring (yoki **Rad etish**).
4. 2-ilovada: Tarix ▸ 🛒 **Buyurtmalarim** — holat ko'rinadi.
* O'chirish: 1-ilova ▸ Mijoz ilovasi ▸ "Buyurtma qabul qilish".
* Birinchi marta: 1-ilova yangi qoidalarni Firebase'ga o'zi o'rnatadi (internet bo'lsa, ilova ochilganda) — siz hech narsa qilmaysiz.

## (m) Avtomatik yangilash (batareyani tejab)
* 1-ilova ▸ Mijoz ilovasi ▸ **Avtomatik yangilash**: 30 daq / 1 soat (tavsiya) / 2 soat / O'chiq.
* 2-ilova ▸ Sozlamalar ▸ **Fonda avtomatik yangilash**: Har soat (tavsiya) / Har 2 soat / O'chiq; "Yangi ma'lumot kelganda xabar berish".
* Telefon faqat internet bor va batareya kam bo'lmaganda tekshiradi. Xiaomi'da ishonchli ishlashi uchun: Sozlamalar ▸ Ilovalar ▸
  ilova ▸ Batareya ▸ **Cheklovsiz**.


# 2.6 — yangi qismlar

## (n) Yangilash: 1-ilova 2.6, 2-ilova 1.2
Oddiy yangilash: GitHub ▸ Actions ▸ oxirgi yashil build ▸ ikkala APK ▸ telefonlarda "Yangilash". Ma'lumotlar joyida qoladi.

1-ilovada:
* avvalgi mijoz (Dadaxon Aka) **Mijozlar** ro'yxatiga o'zi ko'chadi;
* 2-ilova eski "ulash kodi" bilan ishlashda davom etadi.

Telefon raqami bilan kirish uchun (p) bandiga qarang.

## (o) Mijozlar (1-ilova ▸ Sozlamalar ▸ Mijozlar va 2-ilova)
* **Excel yuklash**: mijoz fayl ichidagi nomdan topiladi, ro'yxatda bo'lmasa qo'shiladi. Oyna pastida "…ga saqlash" ham bor.
* **Mijoz qo'shish**: do'konlar ro'yxatidan yoki nomi bilan. Yuk beruvchilar uchun mijoz sahifasida ▸ Turi ▸ **Yuk beruvchi**.
* Mijozni bosing. Mijoz sahifasida:
  * telefonlar;
  * 1C tarixi (Excel yoki serverdan);
  * chek printeri;
  * do'konga bog'lash: savatchaga olish va serverdan olish uchun kerak;
  * kalitlarni almashtirish va o'chirish.
* 1-ilova ▸ Tarix ▸ 1C tarixi: tepadagi **Mijoz: …** tugmasi bilan boshqa mijozni tanlaysiz.

## (p) 2-ilovaga telefon raqami bilan kirish
1. 1-ilova ▸ Mijozlar ▸ mijoz ▸ **Telefon qo'shish**: ism va raqam kiriting, parol o'zi yaratiladi.
2. **Saqlash** ▸ **Yuborish**: Telegram/SMS orqali do'kon kodi, telefon va parol ketadi.
3. 2-ilova ▸ **Kirish**: shu uchtasini kiriting. Faqat bir marta so'raladi.
* Bitta mijozga bir nechta telefon qo'shish mumkin (masalan, do'kondagi 3 ta ishchi). Hammasi faqat shu mijoz ma'lumotini ko'radi.
  Buyurtmada kim yuborgani yoziladi.
* Ishchi ketsa: uning telefonini o'chiring. U boshqa kira olmaydi, qolganlar o'zi qayta ulanadi.
* Parolni unutishsa: telefonni o'chirib, qaytadan qo'shing (yangi parol chiqadi).

## (q) Buyurtma (2-ilova)
1. 2-ilova ▸ Bosh sahifa ▸ **Buyurtma berish** (yoki Narxlar): tovar yonidagi **+** ▸ miqdor ▸ **Savatchaga**.
2. Pastdagi **Yuborish**: miqdorlarni tekshiring, izoh yozing ▸ **1-ilovaga yuborish**.
3. 1-ilovaga bildirishnoma keladi. Bosh sahifa yoki Mijozlar ▸ **Buyurtmalar** ▸ buyurtma ▸ **Savatchaga qo'shish**.
   Narx joriy katalogdan olinadi. Keyin odatdagidek rasmiylashtiring.

Buyurtma o'zi savdo emas: siz tasdiqlamaguningizcha hech narsa serverga ketmaydi.

## (r) Kompyuterdan avtomatik (1C → Excel → telefon)
1. 1-ilova ▸ Mijozlar ▸ **Kompyuterdan avtomatik**: har 1 / 2 / 3 soat (yoki o'chiq). Shu yerda **Kompyuter sozlamasi** matnini
   oling va kompyuterdagi `pc` papkasiga `forwarder.ini` nomi bilan saqlang.
2. Kompyuterda bir marta o'rnating: Python 3 va `pip install pywin32`.
3. `onec.ini` faylini yarating (forwarder.ini yonida) va `[onec]` bo'limiga quyidagilarni yozing (alohida faylda — telefondan yangi forwarder.ini kelganda o'chmaydi):
   * `connection`, masalan `File="D:\1C\Baza";`;
   * `user`, `password`: oddiy 1C foydalanuvchisi; konfigurator paroli kerak emas.
4. `discover.bat` ni ishga tushiring. U `discover.txt` faylini yaratadi — **shu faylni dasturchiga yuboring**: hisobot nomi
   sozlab beriladi (bu baza tuzilishiga bog'liq, bir marta qilinadi).
5. `start_export.bat` ni ishga tushiring. Ishlasa, `onec_export.vbs` yorlig'ini `Win+R ▸ shell:startup` papkasiga qo'ying.

Holat 1-ilovada (shu bo'limda) ko'rinadi. Telefon kelgan fayllarni o'zi import qiladi. Batafsil: `pc/README_PC.md`.

## (s0) Shtrix kod bilan savdo
1. **Ro'yxatni yuklash** (bir marta, keyin yangi tovar qo'shilganda qayta):
   1. 1C'da shtrix kodlar ro'yxatini oching ▸ **Действия ▸ Вывести список...** ▸ OK.
   2. **Файл ▸ Сохранить как ▸ Лист Excel (*.xlsx)**.
   3. Faylni telefonga o'tkazing.
   4. Ilovada: Sozlamalar ▸ **Shtrix kodlar** ▸ **Excel**.
2. **Savdo**: do'konni tanlang ▸ qidiruv yonidagi **skaner** tugmasi ▸ tovarlarni to'xtamasdan ketma-ket ko'rsating.
   Pastdagi ro'yxatdan qo'lda ham qo'shish mumkin. Oxirida **Savatcha** ni bosing.
   * Bluetooth/USB skaner: Savdo ekrani ochiq bo'lsa shunchaki o'qiting.
   * Kod topilmasa kamera to'xtamaydi: qizil qatorda "Topilmadi" chiqadi. Ro'yxatdan (qidirib) to'g'ri tovarni bosing.
     Kod shu tovarga bog'lanadi va keyingi safar o'zi taniladi.
3. **Bazada yo'q kodlar** (masalan, 1C'da bitta kodi bor sokning 10 xili): Shtrix kodlar ▸ **Kod yozish** (yoki Savdo ▸ skaner
   tugmasini uzoq bosing). Kodni o'qiting ▸ pastdagi ro'yxatdan tovarni bosing ▸ keyingisiga o'ting. Kod telefonga yoziladi.
   Xohlasangiz **Excel'ga** — ro'yxatni 1C'ga kiritish uchun yuboradi.
4. **Tarozi**: yorliqdagi og'irlik miqdor bo'lib tushadi. Format to'g'riligini tekshirish:
   * Shtrix kodlar ▸ **Sinash** maydoniga yorliq raqamlarini yozing;
   * kerak bo'lsa prefiks, uzunliklar va birlikni o'zgartiring.

## (s1) Savdo mijozning 2-ilovasida va uning printerida
* 1-ilova ▸ Mijozlar ▸ mijoz **do'konga bog'langan** bo'lsa, unga qilingan savdo 2-ilovasida darhol ko'rinadi.
* Chek ham chiqishi uchun:
  1. 1-ilova ▸ mijoz ▸ **Chek printerini yoqish**.
  2. 2-ilova ▸ Sozlamalar ▸ **1-ilova savdolari cheklari** ni yoqing.
  3. 2-ilova ▸ Printer ▸ servisni yoqing.

  Printer ulanishi bilan navbatdagi hamma cheklar chiqadi.
* Chek ko'rinishi: 1-ilova ▸ Mijozlar ▸ **Chek** bo'limida do'kon nomi, Xodim va telefonlarni yozing. **Namuna chekni ko'rish**
  bilan tekshiring.

## (s2) Narx yorliqlari (Xprinter) — PLU va shtrixli narx

**Bir marta sozlash (kompyuter):**
1. `IMSOFT_kompyuter.zip` ni `D:\imsoft\` ga oching va **ORNATISH.bat** ni ikki marta bosing (administrator so'raydi).
2. Oynadagi savollarga javob bering: printer ulashiladi, Bloknot ochilganda telefondagi "Kompyuter sozlamasi" matnini
   joylab saqlaysiz (Narx yorlig'i ▸ ⋮ ▸ Kompyuterdan kodlar (1C) ▸ Kompyuter sozlamasi), 1C bazasi, foydalanuvchi va parol.
3. Telefonda: **Narx yorlig'i** ▸ printer belgisi ▸ **Kompyuterni topish** (yoki IP: `192.168.1.200`), nomi `xprinter` ▸
   yorliq o'lchami ▸ **Sinov yorlig'i**. Teskari chiqsa — "180° burish".


**PLU va shtrix kodlar — avtomatik (1C'dan):** kompyuterdagi `onec_export.py` ularni 1C'dan o'zi oladi va telefonga yuboradi
(har 6 soatda va so'ralganda). Birinchi marta: kompyuterda `python onec_export.py --codes` — oynada "PLU manbasi" va
"shtrix" topilganini ko'ring. Topilmasa `--discover` qilib `discover.txt` ni yuboring. Telefonda: Narx yorliqlari ▸ ⋮ ▸
**Kompyuterdan kodlar (1C)** ▸ **Hozir yangilash**. Shundan keyin har tovar ostida "Kod 2555 · PLU 501" ko'rinadi
(1-ilovada savdo, skaner va yorliq ro'yxatlarida; 2-ilovada Narxlar ro'yxatida).

Zaxira yo'llar: 1C'dagi "PLU Narx" shaklini hamma vaznli tovarlar bilan bir marta Excel/MXL qilib yuklash (⋮ ▸ PLU ro'yxatini
yuklash) yoki tovarni bosib PLU ni qo'lda yozish (qo'lda yozilgani doim ustun).

**Har kuni:**
1. Narx yorlig'i ▸ tovarlarni **+** bilan tanlang (qidiruv, filtr "PLU bor"/"Shtrix bor") yoki skaner tugmasi bilan skanerlang.
   Nechta yorliq kerak bo'lsa — + ni shuncha bosing yoki tovarni bosib son yozing.
2. Yuqorida narx turi: **Naqd** yoki **Optom** (tovarni bosib narxni qo'lda ham yozish mumkin).
3. Pastda **PLU narx** (tarozi yorlig'i: nom, KOD, narx) yoki **Shtrixli narx** (nom, narx, shtrix kod) ▸ ko'rinishini tekshiring ▸
   **Chop etish**. PLU yoki shtrix kodi yo'q tovarlar ro'yxatda alohida ko'rsatiladi va chiqmaydi.
4. Telefon do'kon Wi-Fi'sida bo'lmasa (internet yoqilgan bo'lsa) yorliqlar navbatga qo'yiladi — kompyuter internetdan olib chiqaradi.

## (s3) Chek ko'rinishi (1-ilova 2.9, 2-ilova 1.4)
1. 1-ilova ▸ Sozlamalar ▸ Mijozlar va 2-ilova ▸ **Chek ko'rinishi**. Tepada namuna — har o'zgarish darhol ko'rinadi.
2. Andoza: **1C uslubi** (do'kondagi 1C chekiga o'xshash) yoki **Standart**; 58 mm printer bo'lsa **Ixcham 58 mm**.
3. **Logo** ▸ Rasm tanlash (oq fonli logo yaxshi chiqadi) ▸ enini suring.
4. Matnlar: do'kon nomi, manzil (sarlavha osti), xodim, telefonlar, oxirgi xabar ("Xaridingiz uchun rahmat!").
5. "Chekda nimalar chiqsin" — keraksizini o'chiring. Chek oqarib chiqsa **Qoralik**ni oshiring.
6. **Sinov cheki** — tanlangan mijozning printeriga boradi. 2-ilovalar yangi ko'rinishni keyingi yuborishdan keyin oladi
   (Mijozlar ▸ Hozir yuborish).

## (s4) Savdo Admin (3.0) — o'z telefoningizga
1. Ishchilar telefoniga **SavdoAgent** APK — eski ilova ustidan yangilanadi, hamma narsa joyida qoladi.
2. O'z telefoningizda avval Agent'ni ham yangilang. Agent ▸ Sozlamalar ▸ **Zaxira nusxa ▸ Saqlash** ▸ **parol qo'ying** ▸ faylni saqlang.
3. **SavdoAdmin** APK ni o'rnating (yangi ilova, to'q ko'k belgi). Kirish ekranida **"Agent ilovadan ko'chirish"** ▸ faylni tanlang ▸
   parol ▸ Tiklash ▸ ilova qayta ochiladi. Server, mijozlar, printerlar, 1C kodlari — hammasi o'tgan bo'ladi.
4. Kompyuterda hech narsa o'zgartirmang (forwarder.ini kalitlari bir xil).
5. Shundan keyin o'z telefoningizda 1C hisobotlari, mijozlar va chek ko'rinishi bilan **Admin**'da ishlang; Agent'ni o'chirib
   tashlashingiz mumkin (ikkalasi bir vaqtda kompyuterdan kelgan ma'lumotni o'qimasin).

## (s5) Agentlar faqat internet bilan ("Admin orqali")
**Admin (o'z telefoningiz, Tailscale yoki do'kon Wi-Fi'si bilan):**
1. Mijozlar va 2-ilova ▸ **Agentlar ko'prigi** ▸ "Ko'prik yoqilgan" ▸ bildirishnomaga ruxsat bering.
2. **Kodni yuborish** ▸ Telegram orqali agentlarga (kodni faqat o'z agentlaringizga bering).
3. Telefonda batareya cheklovini o'chiring (Sozlamalar ▸ Ilovalar ▸ Savdo Admin ▸ Batareya ▸ Cheklovsiz).

**Agent (sherik telefoni, faqat internet):**
1. Sozlamalar ▸ Server ▸ **Admin orqali (internet)** ▸ kodni qo'ying ▸ **Tekshirish** ("Admin bilan aloqa bor") ▸ Saqlash.
2. Odatdagidek login va parol bilan kiring.

* Admin telefoni internetda bo'lmasa: agentlar sotaveradi, savdolar "kutilmoqda"; Admin ulanganda hammasi serverga yoziladi.
* Agentda "yuborildi" — faqat server tasdig'idan keyin. Navbatda "Admin'ga yetdi, server tasdig'i kutilmoqda" ko'rinishi mumkin.

# 3.2 — yangi qismlar (Admin 3.2, Agent 3.2, Mijoz 1.6)

## (t1) Yangilash
1. GitHub Actions'da build: `SavdoAgent`, `SavdoAdmin`, `SavdoMijoz` APK'lari. Ustidan o'rnating (ma'lumot qoladi).
2. Kompyuterga yangi `IMSOFT_kompyuter.zip` dagi 5 ta faylni qo'ying va `ORNATISH.bat` ni qayta oching.

## (t2) Admin ▸ Boshqaruv
* **Kompyuter (1C)**: "Tezkor yangilash" — 1 yoki 5 daqiqa. Ish vaqtini "Avtomatik yangilash" sahifasida qo'ying.
* **1C savdolar** — oxirgi 30 kun o'zi keladi; boshqa davr: "Davr tanlash" (1 daqiqada keladi).
* **Ta'minotchilar** — qarzimiz va hujjatlar. Ta'minotchini bosing: Kirim / To'lov / Qaytarish.
* **1C'ga kiritish**: tovarlarni qo'shing -> "Tasdiqlash" -> xulosani tekshirib "Ha, kiritilsin". Navbatda "1C'ga kiritildi №..."
  chiqqach, 1C'da hujjatni bir marta ochib tekshiring (birinchi kunlari). Xato bo'lsa — yozuvni bosib "Bekor qilish".
* **Mijoz so'rovlari** — 2-filial yuborgan to'lov va chek tuzatishlari: "Tasdiqlash" yoki "Rad etish".

## (t3) Avtomatik yangilash va zaxira (hammada)
Sozlamalar ▸ "Avtomatik yangilash va zaxira": ish vaqti, tezlik, "Aniq vaqt ruxsati" (Android 12+), kunlik zaxira, tiklash.

## (t4) Vidjet
Bosh ekranni uzoq bosing ▸ Vidjetlar ▸ Savdo ▸ "Tez savdo". Tugmalardagi do'konlar — Sozlamalar ▸ Tez do'konlar
(masalan Naqd va Dadaxon aka).

## (t5) 2-filial (Mijoz 1.6)
* **Kassa** — o'z mijozlaringizga savdo, qarz, to'lov, hisobot (faqat shu telefonda).
* **To'lov kiritish** — do'kon egasi tasdiqlagach chek chiqadi.
* 1C tarixida savdo chekini oching ▸ **"Chekni tuzatishni so'rash"**.
* Sozlamalar ▸ **Chek qo'shimchalari** — manzil, telefon, rahmat matni.

## (s) Ko'rsatmalar va sirli menyu
* Har ekran birinchi ochilganda kerakli tugmalar yoritilib, izoh chiqadi. "O'tkazib yuborish" — shu ekran uchun yopiladi.
* **Sirli xona**: Sozlamalar ▸ eng pastdagi versiya yozuvini **uzoq bosing**. U yerda:
  * o'rgatuvchi ko'rsatmalarni qayta yoqish yoki butunlay o'chirish;
  * avto-qorong'i rejim, ekran himoyasi;
  * kunlik maqsad, CSV eksport, sinov cheki va boshqalar.
* 2-ilovada (Savdo Mijoz) xuddi shu joyda **Qo'shimcha sozlamalar**: ochilish sahifasi (Bosh sahifa / Narxlar / Tarix),
  "Ekran o'chmasin", avto-qorong'i, ekran himoyasi, ko'rsatmalar, oxirgi xaridni takrorlash, qarz dinamikasi, top-10 tovar,
  CSV, kurs kalkulyatori, hozir yangilash. Kalitlar, do'kon kodi va ulanish ma'lumotlari u yerda ko'rsatilmaydi.
