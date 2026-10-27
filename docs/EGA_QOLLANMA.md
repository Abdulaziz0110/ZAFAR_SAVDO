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
