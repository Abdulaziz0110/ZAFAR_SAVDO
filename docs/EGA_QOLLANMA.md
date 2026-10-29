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

