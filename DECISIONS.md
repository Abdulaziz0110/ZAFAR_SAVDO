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
