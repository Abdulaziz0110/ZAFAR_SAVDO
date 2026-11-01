# Holat — Savdo Agent / Savdo Admin 3.9 (versionCode 21) + Savdo Mijoz 1.13 (versionCode 14), 2026-11-01

Belgilar:
- **VERIFIED** — shu muhitda kompilyatsiya qilindi va testdan o'tdi (yoki mustaqil vosita bilan tekshirildi).
- **IMPLEMENTED-UNVERIFIED** — kod yozilgan va kompilyatsiya bo'ladi, lekin haqiqiy qurilma/printer/1C'da sinalmagan.
- **NOT-IMPLEMENTED** — qilinmagan.


## 1-bosqich (v3.1 / Mijoz 1.5)
| Narsa | Holat |
|---|---|
| 1-2-3-4 narx: narx hisoblash, snapshot (9-element), eski ma'lumot bilan moslik | VERIFIED (`snapshotCarriesFourPrices`, kompilyatsiya ✓) |
| Savdo / Shtrix-savdo / Mijoz Narxlar ekranida 4 tugma | IMPLEMENTED-UNVERIFIED (kompilyatsiya ✓, 360dp o'lchov ✓) |
| 1/2-narx 1C narxidan (prices_1c.json) | IMPLEMENTED-UNVERIFIED (kompyuterdan narx turlari kelishi kerak) |
| 2-ilova mijozlari uchun qulflangan 4-narx | IMPLEMENTED-UNVERIFIED |
| Kamera doimiy avtofokus | IMPLEMENTED-UNVERIFIED (telefonda sinash kerak) |
| Yorliq: siljitish, density, kalibrovka, supersampling, qalin shrift | TSPL baytlari kompilyatsiya ✓ / haqiqiy XP-365B'da IMPLEMENTED-UNVERIFIED |
| Yangi savdo oqimi ("Boshlash"siz), qidiruvda panel yashirish | IMPLEMENTED-UNVERIFIED |
| Ko'prik standart yoqiq, 1 daqiqalik yangilash | IMPLEMENTED-UNVERIFIED |
| Mijoz qarzi ochilganda yangilanishi | IMPLEMENTED-UNVERIFIED |

## v3.9 / Mijoz 1.13 — Mijozda savdo, internetsiz
| Narsa | Holat |
|---|---|
| Mijoz "Savdo" ekrani (4 narx, qidiruv, −/+, savatcha, saqlash, chek) | IMPLEMENTED-UNVERIFIED (kompilyatsiya ✓) |
| Savatcha telefonda saqlanadi | IMPLEMENTED-UNVERIFIED |
| Bosh sahifa: Yangi savdo, 4 plitka, bugungi savdo, Sozlamalar tugmasi | IMPLEMENTED-UNVERIFIED |
| Pastki menyu 5 tadan oshmaydi (printer yoqilganda Sozlamalar o'rnida) | IMPLEMENTED-UNVERIFIED |
| "Internet yo'q" ogohlantirishi | IMPLEMENTED-UNVERIFIED |
| Agent tarixi — eng yangidan, bosqichma-bosqich | IMPLEMENTED-UNVERIFIED |

## v3.8 / Mijoz 1.12 — server javobi (solishtirma dalolatnoma)
| Narsa | Holat |
|---|---|
| Haqiqiy server javobi (bo'sh davr): mijoz nomi, davr, boshlang'ich va oxirgi qoldiq | VERIFIED (`AktParserTest.realServerAnswerWithoutOperations` — foydalanuvchi yuborgan fayl) |
| Hujjat qatorlari (savdo/to'lov/qaytarish) | VERIFIED sun'iy qatorlar bilan (`AktParserTest.operationsBecomeDocuments`) / haqiqiy hujjatli javobda IMPLEMENTED-UNVERIFIED |
| Tovarsiz hujjatlar ekrani, chek, PDF | IMPLEMENTED-UNVERIFIED (kompilyatsiya ✓) |

## v3.7 — "1C tarixi": kompyuterdagi barcha savdolar (Admin va Agent)
| Narsa | Holat |
|---|---|
| Kompyuter savdolari yig'iladi (1 yil), davr ichidagisi yangilanadi | VERIFIED (`OneCArchiveTest.newPeriodReplacesOnlyInside`, `olderThanYearDropped`) |
| 1C hujjati (savdo/chakana/vozvrat) -> 1C tarixi hujjati | VERIFIED (`OneCArchiveTest.docBecomesLedgerDocument`) |
| Kompyuterdan 1 yilgacha orqaga 30 kunlik so'rovlar | IMPLEMENTED-UNVERIFIED (kompilyatsiya ✓; kompyuter ishlab turishi kerak) |
| Server kartochkasi bilan takrorlanmaslik, holat qatori, bo'sh holat matni | IMPLEMENTED-UNVERIFIED (kompilyatsiya ✓) |
| Agent: Admin tarixini bulutdan olish (10 kunlik bo'laklar, xesh bo'yicha) | VERIFIED bo'lak/kodlash (`HistoryPackTest`, 3 test) / bulut va ekran IMPLEMENTED-UNVERIFIED |
| Firebase qoidalari v6 (g/{kalit}/hist) | IMPLEMENTED-UNVERIFIED (Admin ochilganda o'zi o'rnatadi) |

## v3.6 / Mijoz 1.11 — 1C tarixi barcha mijozlar, qarz ekrani
| Narsa | Holat |
|---|---|
| Bir soniyadagi ikki mijoz hujjati aralashmaydi, mijoz nomi bilan qidiruv | VERIFIED (`LedgerOwnerTest`, 3 test) |
| Admin "1C tarixi": barcha mijozlar, 31 kun → 1 yil (92 kunlik bo'laklar), savdodan keyin, soatlik/kunlik yangilash | IMPLEMENTED-UNVERIFIED (kompilyatsiya ✓; server javobi formati telefonda sinalmagan) |
| Holat qatori, "Hozir yangilash", "Javobni ulashish" | IMPLEMENTED-UNVERIFIED (kompilyatsiya ✓) |
| 2-ilova mijozi kitobi shu ma'lumotdan (1 yil), do'konga avtomatik bog'lash | IMPLEMENTED-UNVERIFIED (kompilyatsiya ✓) |
| 2-ilova qarz ekrani: yangilashlar navbat bilan, eski ma'lumot ekranda qolmaydi | IMPLEMENTED-UNVERIFIED (kompilyatsiya ✓) |
| Agent ilovasi "1C tarixi" — avvalgidek (tanlangan mijoz) | IMPLEMENTED-UNVERIFIED |

## v3.5 / Mijoz 1.10 — Narxlar
| Narsa | Holat |
|---|---|
| 1 yillik kartochka bir marta, olgan tovarlar eslab qolinadi, 1-ilova savdolari tovarlari | IMPLEMENTED-UNVERIFIED (kompilyatsiya ✓) |
| 2-ilova: bo'sh tovarlar ro'yxati kelsa oldingisi saqlanadi | IMPLEMENTED-UNVERIFIED (kompilyatsiya ✓) |

## v3.4 / Mijoz 1.9 — qarz va tarix
| Narsa | Holat |
|---|---|
| 1-ilova yozuvi 1C'da paydo bo'lgach yashirish (tur, ±20 daq, summa), ikki marta ko'rinmaslik | VERIFIED (`AppSalesTest.noDuplicateWhenOneCAlreadyHasIt`) |
| 2-ilova qarzi: asos + keyingi savdo/to'lovlar | VERIFIED (`AppSalesTest.pendingDebtAfterServerTime`) / ekran IMPLEMENTED-UNVERIFIED |
| Qarz faqat serverdan alohida so'ralgani (katalog ro'yxati emas) | IMPLEMENTED-UNVERIFIED (kompilyatsiya ✓) |
| 1C tarixi serverdan har 10 daqiqada / so'rovda / savdodan keyin | IMPLEMENTED-UNVERIFIED (server hisoboti formati — avvalgi "Serverdan olish" bilan bir xil) |
| Admin so'rovlarga 25 soniyada javob, 2-ilova 3 daqiqagacha o'zi kutadi | IMPLEMENTED-UNVERIFIED |

## v3.3 / Mijoz 1.7 — to'lov va qarz
| Narsa | Holat |
|---|---|
| To'lov 2-ilova tarixida (1C qamragunicha), "Bu oy" ga kiradi, savdodagi to'lov alohida | VERIFIED (`AppSalesTest.paymentsAppearInHistory`) |
| Admin to'lovi va agent to'lovi (Admin orqali) -> ko'zgu, qarzni qayta o'qish (0/1/3/10 daq) | IMPLEMENTED-UNVERIFIED (kompilyatsiya ✓) |
| Agent 2-ilovaga yubormaydi, kompyuter fayllarini olmaydi | IMPLEMENTED-UNVERIFIED (kompilyatsiya ✓) |
| Mijoz 1.8: "Yangilash"da kalit almashganini tekshirib yangi kalitga o'tish | IMPLEMENTED-UNVERIFIED (kompilyatsiya ✓) |

## 2–4-bosqichlar (v3.2 / Mijoz 1.6)
| Narsa | Holat |
|---|---|
| Ish vaqti mantiqi (oraliq, tungi smena, oxirgi yuklash vaqti) | VERIFIED (`V32Test.workHours`) |
| Avtomatik yangilash (ilova ochiq: N daqiqa; yopiq: WorkManager 15 daq; budilnik — tugashidan 1 daqiqa oldin) | IMPLEMENTED-UNVERIFIED (kompilyatsiya ✓, telefonda sinash kerak) |
| Kunlik zaxira va tiklash (Agent/Admin: bazalar; Mijoz: 1C tarixi + Kassa) | IMPLEMENTED-UNVERIFIED |
| Vidjetlar (Tez savdo; Mijoz: qarz, Narxlar, Kassa) | IMPLEMENTED-UNVERIFIED |
| Excel import tugmalari olib tashlandi (Admin/Agent) | IMPLEMENTED-UNVERIFIED (kompilyatsiya ✓) |
| Agent/Mijoz soddalashtirish ("Ko'proq sozlamalar") | IMPLEMENTED-UNVERIFIED |
| Admin "Boshqaruv" bo'limlari | IMPLEMENTED-UNVERIFIED |
| 1C savdolar va ta'minotchilar: kompyuter so'rovlari va JSON (faqat ВЫБРАТЬ) | VERIFIED (`test_onec_data.py`, soxta 1C) / haqiqiy 1C'da IMPLEMENTED-UNVERIFIED |
| Telefonda o'qish (`OneCCodec`), qidiruv, jami | VERIFIED (`V32Test.salesCodec/suppliersCodec`) |
| 1C'ga kiritish: so'rov kodeki va tekshiruv | VERIFIED (`V32Test.writeCodec`) |
| Kompyuterda kiritish: kirim, to'lov, shtrix, bekor qilish, jurnal (qayta kiritilmaydi), zaxirasiz yozmaslik, xatolar | VERIFIED (`test_onec_data.py`, soxta COM) / haqiqiy 1C'da IMPLEMENTED-UNVERIFIED |
| Ta'minotchiga qaytarish, mijoz qaytargan tovar (1C hujjati to'ldirilishi) | IMPLEMENTED-UNVERIFIED (1C formasi kodidan olingan maydonlar; haqiqiy 1C'da birinchi hujjatni tekshiring) |
| Daqiqalik jadval va ish vaqti (kompyuter) | VERIFIED (`test_onec_data.py.Live`) |
| Mijoz to'lov / tuzatish so'rovlari, Firebase qoidalari v5 (faqat yangi yozuv, holatni faqat Admin) | VERIFIED (`V32Test.clientRequestsAndRules`, soxta RTDB) / haqiqiy Firebase'da IMPLEMENTED-UNVERIFIED |
| Admin: to'lovni tasdiqlash -> serverga to'lov -> server ID kelgach "Tasdiqlandi" | IMPLEMENTED-UNVERIFIED |
| Admin: chek tuzatishini qo'llash (2-ilova chekida) | IMPLEMENTED-UNVERIFIED |
| Mijoz Kassa: qarz, hisobot, chek | VERIFIED (`V32Test.posLogic`) / ekran IMPLEMENTED-UNVERIFIED |
| Chek qo'shimchalari (brend o'zgarmaydi) | IMPLEMENTED-UNVERIFIED |
| 2-ilova mijozi savdosida chek joyini tanlash | IMPLEMENTED-UNVERIFIED |
| Agent shtrix kodi Admin orqali 1C'ga | IMPLEMENTED-UNVERIFIED (kompilyatsiya ✓) |
| Qarzdorlar ro'yxati (tavsiya) | IMPLEMENTED-UNVERIFIED |

## Qilinmagan (tavsiya, keyinroq)
| Narsa | Holat |
|---|---|
| Ombor qoldig'i (1C Товар registri) va "kam qolgan tovarlar" ogohlantirishi | NOT-IMPLEMENTED |
| Kassa savdolarini do'kon egasiga yuborish (hozir faqat telefonda) | NOT-IMPLEMENTED |
| 1C'dagi savdo hujjatini telefondan o'zgartirish (hozir — faqat 2-ilova chekida tuzatish) | NOT-IMPLEMENTED |

## Agent / Admin (v3.0)
| Narsa | Holat |
|---|---|
| Ikki flavor: agent (asl applicationId), admin (`.admin`, nom va belgi alohida) | IMPLEMENTED-UNVERIFIED (Gradle build GitHub Actions'da) |
| Rolni aniqlash (`AppRole`, paket nomidan) | VERIFIED (`AppRoleTest`) |
| Agent: chek maydonlari 2.8 dagidek; Admin: "Chek ko'rinishi" | IMPLEMENTED-UNVERIFIED (kompilyatsiya ✓) |
| Parolli zaxirada sozlamalar (server paroli, bulut, yorliq, fayllar) va tiklash | IMPLEMENTED-UNVERIFIED (kompilyatsiya ✓); fayl yo'li himoyasi VERIFIED (`AppRoleTest.transferPaths`) |
| Admin kirish ekranida "Agent ilovadan ko'chirish" | IMPLEMENTED-UNVERIFIED |
| Tugmalarda "..." (matn sig'masligi) | VERIFIED (360dp, shrift ×1 va ×1,15 — o'lchash skripti) / telefonda IMPLEMENTED-UNVERIFIED |

## Agentlar "Admin orqali" (v3.0)
| Narsa | Holat |
|---|---|
| Ulanish kodi, kirish isboti (parolsiz), katalog joylash/o'qish, qarz/hisobot/vaqt so'rovlari | VERIFIED (`RelayTest`, soxta Firebase qoidalari bilan) |
| Savdo/to'lov: bitta yozuv (qayta yuborish, Admin yozib natijani joylamay o'chganda ham), rad etish, soxta kalit | VERIFIED (`RelayTest`) |
| Navbat yadrosi (OutboxEngine) ko'prik orqali: Admin oflayn -> kutadi, keyin hammasi SENT, dublikat yo'q | VERIFIED (`RelayTest.outboxEngineThroughRelay`) |
| Firebase qoidalari v4 (agent faqat o'z joyida) | VERIFIED (soxta RTDB'da qo'lda takrorlangan) / haqiqiy Firebase'da IMPLEMENTED-UNVERIFIED |
| Yorliq va 2-filial cheki Admin orqali | VERIFIED (`RelayTest.labelsAndBranchReceipt`) / haqiqiy printerda IMPLEMENTED-UNVERIFIED |
| Admin fon xizmati (SSE + 60 s), 15 daqiqalik zaxira, qayta yoqilganda tiklash | IMPLEMENTED-UNVERIFIED (telefonda sinash kerak) |
| Agent sozlamasi "Admin orqali", Admin "Agentlar ko'prigi" ekrani | IMPLEMENTED-UNVERIFIED (kompilyatsiya ✓) |

## Chek ko'rinishi (v2.9, endi faqat Admin)
| Narsa | Holat |
|---|---|
| `ReceiptStyle`: saqlash/o'qish, chegaralar, yashirish, sarlavha osti, xabar, andozalar | VERIFIED (`ReceiptStyleTest`, 7) |
| Logo: rasm tanlash, kichraytirish, nuqtali qora-oq (Floyd–Steinberg) | VERIFIED (dither testi) / telefonda IMPLEMENTED-UNVERIFIED |
| Chek rasmi (Standart / 1C uslubi / 58 mm, logo bilan) | VERIFIED (chizish kodining Python nusxasi) / haqiqiy printerda IMPLEMENTED-UNVERIFIED |
| Ko'rinish 2-ilovaga (snapshot "rs"/"rl"), eski ma'lumot bilan moslik | VERIFIED (`snapshotCarriesStyleAndLogo`) |
| "Chek ko'rinishi" ekrani, Sinov cheki | IMPLEMENTED-UNVERIFIED (kompilyatsiya ✓) |

## Narx yorliqlari (1-ilova)
| Narsa | Holat |
|---|---|
| Tovar tanlash, nusxa soni, Naqd/Optom yoki qo'lda narx, filtrlar, skaner (kamera va ulanadigan) | IMPLEMENTED-UNVERIFIED (kompilyatsiya ✓, telefonda sinalmagan) |
| "PLU narx" va "Shtrixli narx" yorliq rasmi — 1C "ГодехЦенник"/"ГодехШтрих" joylashuvi, Liberation Sans Bold | VERIFIED (Python nusxasi, shtrix zxing-cpp bilan o'qildi) / haqiqiy printerda IMPLEMENTED-UNVERIFIED |
| Yorliq narxi = 1C "Цена 1 (Сум)" (ПродажЦена1), avtomatik tanlanadi | VERIFIED (onec_export testlari) / telefonda IMPLEMENTED-UNVERIFIED |
| EAN-13 / EAN-8 / Code 128 kodlash | VERIFIED (`LabelsTest`, mustaqil dekoder) |
| Telefon ↔ `label_printer.py` protokoli (Wi-Fi TCP, takror chop etmaslik, kalit) | VERIFIED (Kotlin klient JVM'da ↔ Python server, file rejimi) |
| Internet orqali navbat (Firebase `L/`), holat (`P/`) | VERIFIED (soxta bulut bilan, `test_label_printer.py`) |
| Haqiqiy Xprinter'da chop etish (`gdi` va `tspl`) | IMPLEMENTED-UNVERIFIED (Windows va printer kerak) |
| Ulashilgan printer (Windows LPD, TSPL) — kompyuter dasturisiz | VERIFIED (soxta LPD, TSPL baytlari) / haqiqiy XP-365B'da IMPLEMENTED-UNVERIFIED |
| `ORNATISH.bat` + `ornatish.py` (hammasi bitta joyda: LPD, ulashish, 515, ini, 1C, avtozapusk) | sof qismlar VERIFIED (6/6); Windows'da IMPLEMENTED-UNVERIFIED |
| UDP orqali kompyuterni topish | VERIFIED (localhost), haqiqiy Wi-Fi'da IMPLEMENTED-UNVERIFIED |

## Tovar kodlari: PLU va shtrix 1C'dan
| Narsa | Holat |
|---|---|
| `onec_export.py --codes`: manbani avto-topish, so'rov, birlashtirish, faqat o'zgarganda yuborish, telefon so'rovi | VERIFIED (soxta metama'lumot/COM bilan, 22/22) |
| Do'kon 1C bazasida PLU va shtrix (Товары.ПЛУ, Товары.ШтрихКод) | VERIFIED (do'kon kompyuterida: 2469 tovar keldi) |
| Do'kon 1C bazasida narx turlari (ПродажЦена1..4) | IMPLEMENTED-UNVERIFIED — yangi `onec_export.py` bilan `--codes` qayta ishga tushirilishi kerak |
| 1-ilova: qabul qilish, katalogga bog'lash, "Kod · PLU" ro'yxatlarda | VERIFIED (format testi, Python→Kotlin) / ekran IMPLEMENTED-UNVERIFIED |
| 2-ilova: Narxlar'da "Kod · PLU", PLU bo'yicha qidirish, eski format bilan moslik | VERIFIED (`snapshotCarriesPlu`), ekran IMPLEMENTED-UNVERIFIED |

## Savdo Mijoz: "Qo'shimcha sozlamalar"
| Narsa | Holat |
|---|---|
| Kalit/ulanish/texnik bandlar olib tashlandi; ochilish sahifasi, "Ekran o'chmasin" | IMPLEMENTED-UNVERIFIED (kompilyatsiya ✓) |

## Qilinmagan
| Narsa | Holat |
|---|---|
| Kompyutersiz (Bluetooth/OTG) yorliq | NOT-IMPLEMENTED (XP-365B faqat USB; kompyuter yoniq turishi kerak) |
| Telefondan 1C ga yozish (tasdiqlash navbati, zaxira, jurnal, bekor qilish) | NOT-IMPLEMENTED (keyingi bosqich) |
| 1C'dan qo'shimcha ma'lumotlar (ombor qoldig'i, kontragentlar, kassa...) | NOT-IMPLEMENTED (keyingi bosqich, faqat Admin) |

## Testlar
- JUnit (`app` + `shared`): 169/169; `mijoz` kompilyatsiyasi: 0 xato.
- `pc/test_onec_export.py` 22/22, `pc/test_ornatish.py` 6/6, `pc/test_label_printer.py` 19/19, `pc/test_forwarder.py` 8/8.
- Satrlar (lotin + kirill), resurs havolalari, format argumentlari: xatosiz.
- Haqiqiy Gradle build (AAPT2, lint) — GitHub Actions'da (`android-build.yml`).
