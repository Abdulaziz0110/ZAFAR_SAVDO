# Holat — Savdo (bitta ilova: Admin/xodim) 5.3.6 (versionCode 56) + kompyuter dasturi 5.4 (pc 44) + Savdo Mijoz 5.4 (versionCode 32) + iPhone: agent va mijoz (veb), 2026-11-20

Belgilar:
- **VERIFIED** — shu muhitda kompilyatsiya qilindi va testdan o'tdi (yoki mustaqil vosita bilan tekshirildi).
- **IMPLEMENTED-UNVERIFIED** — kod yozilgan va kompilyatsiya bo'ladi, lekin haqiqiy qurilma/printer/1C'da sinalmagan.
- **NOT-IMPLEMENTED** — qilinmagan.


## Kompyuter 5.4 — kassa (sensor) oynasi monoblokda, RDP'siz

| Ish | Holat |
|---|---|
| Kompyuterda kassa sayti (http://IP:8077): bitta 1C oqimi, navbat, brauzerga parolsiz ma'lumot | VERIFIED (test_kassa.py, soxta 1C bilan; HTTP marshrutlari) |
| Kirish 1C xodim paroli bilan (Сотрудники.Пароль, bo'shatilgan — yo'q) | VERIFIED (test) / 1C'da IMPLEMENTED-UNVERIFIED |
| Shtrix kod tahlili 1C ПолеТекПриИзменении kabi (29 tarozi, 28 dona, 21, 25, "0") — Python va JS bir xil | VERIFIED (300 tasodifiy holat solishtirildi, node + python testlari) |
| Narx turi 1–4, $ tovar narxi × kurs, qoldiqdan ortiq sotmaslik (ПродажаБезОстатков), savatni qayta narxlash | VERIFIED (test) |
| To'lov: naqd/plastik/click, chegirma, qaytim, mijozsiz qarz taqiqlangan, qarz to'lovi, qaytarish | VERIFIED (kassa_core + logic.js testlari) |
| ПродажаРозница yozish (РасчетНажатие / ВозвратНажатие maydonlari: Ост, ОстСумма, УртачаНарх, ДтКт, Лимит, Сотрудник, ДоляСот) | VERIFIED (soxta 1C) / haqiqiy 1C'da IMPLEMENTED-UNVERIFIED |
| Qaytarishda tannarx: 1C'ning yopiq (shifrlangan) ОхиргиУртачаНархиниАниклаш o'rniga qoldiqdagi o'rtacha yoki kirim narxi | IMPLEMENTED-UNVERIFIED |
| COM'da baza moduli ishlamasa — 1C oynasi orqali yozish (kassa ishlatilayotganda yopilmaydi) | VERIFIED (test) / 1C'da IMPLEMENTED-UNVERIFIED |
| Bir sotuv ikki marta yozilmaydi (so'rov raqami, kassa_done.jsonl) | VERIFIED (test) |
| Panel 8×42 (тов1..тов336), qidiruv (nom/kod/narx), raqam paneli (miqdor ×, kod), tanlangan qatorni +/−/o'chirish | VERIFIED (Playwright skrinshot, soxta 1C) |
| Chek 80 mm, so'ramasdan chop etish (--kiosk-printing) | IMPLEMENTED-UNVERIFIED (brauzerda ko'rindi, printerda sinalmagan) |
| Monoblok o'rnatish fayli (Chrome/Edge kiosk, avtozapusk) | IMPLEMENTED-UNVERIFIED (Windows'da sinalmagan) |
| Brandmauer 8077-port (dastur o'zi; bo'lmasa allow_firewall.bat) | IMPLEMENTED-UNVERIFIED |
| Admin ilovasi: Kompyuter ekranida kassa manzili va monoblok ko'rsatmasi | VERIFIED (kompilyatsiya) |
| pc 6: onec.ini Bloknotda ANSI (1251) saqlangan bo'lsa kassa ishga tushmasdi — tuzatildi; internet yo'q bo'lsa yangilanish 5 daqiqada qayta tekshiriladi | VERIFIED (test) |
| pc 7: kassa ekrani 1C sensor oynasi asosida qayta: raqam maydoni + panel, ×, Kod, Narx bo'yicha, Miqdor, Chegirma summa/%, 3 ta parallel oyna, Panel alohida sahifa, ekran klaviaturasi (lotin/kirill), Blok, Chiqish | VERIFIED (Playwright, soxta 1C) |
| Admin paroli bilan kamaytirish/o'chirish (Константы.ИзмененияВСенсореСпаролем, Сотрудники.Админ) | VERIFIED (test) / 1C'da IMPLEMENTED-UNVERIFIED |
| "Chiqish" — kiosk oynasini yopish (window.close); yopilmasa barmoq bilan yo'l ko'rsatiladi | IMPLEMENTED-UNVERIFIED (monoblokda sinalmagan) |
| 1C oynasi (V83.Application) "Неизвестный интерфейс": 1cv8.exe /regserver avtomatik + 1C_COM_tuzatish.bat; xatoda COM va oyna sabablari birga ko'rsatiladi | IMPLEMENTED-UNVERIFIED (Windows'da sinalmagan) |
| pc 8: sotuv avval diskdagi navbatga (fsync), keyin 1C; 1C ishlamasa navbatda qoladi va o'zi bir marta yoziladi; biznes xatosi (qoldiq) navbatga tushmaydi | VERIFIED (test_kassa: 1C ishlamay qolishi, qayta ishga tushish, takror so'rov) |
| Savatlar kompyuterga sinxron (elektr o'chsa monoblokdan tiklanadi); kompyuter javob bermasa — monoblok navbati | VERIFIED (Playwright: yangi brauzer xotirasi bilan tiklandi) |
| So'rovlarga vaqt chegarasi, "yozilmoqda" 100 s dan ortiq qolmaydi, global xato tiklash | VERIFIED (kod, Playwright) |
| Kassa sozlamalari (admin): oynalar 1–4, chek/ovoz/qoldiq, tugmalarni yashirish, interfeys muharriri (o'lcham, rang, matn), navbat, kunlik hisobot | VERIFIED (Playwright, soxta 1C) |
| Kompyuter dasturini qayta yoqmasdan yangilash (ilova tugmasi + kassa sozlamasi; kassa sahifasi o'zi yangilanadi) | VERIFIED (test) / Windows'da IMPLEMENTED-UNVERIFIED |
| 1C_oyna_tekshiruv.bat — V83.Application sinovi (app_test.txt) | IMPLEMENTED-UNVERIFIED |
| pc 10: osilib qolgan ko'rinmas 1C (COM-server, -Embedding) jarayonlarini topib yopish va qayta urinish; kassirning 1C oynasiga tegilmaydi | VERIFIED (test) / Windows'da IMPLEMENTED-UNVERIFIED |
| pc 11: 1C KO'PRIGI — COM'da ПродажаРозница yaratilmasa (Ошибка инициализации модуля) kassa sotuvlari 1C'ni ODDIY usulda ochib (`1cv8.exe ENTERPRISE /Execute kassa_bridge.epf /C kassa_jobs`) yoziladi: .epf fayli dasturning o'zida yasaladi (1C konteyner formati, xUnitFor1C namunasi bilan tuzilishi bir xil), modul 1C sensor oynasining РасчетНажатие/ВозвратНажатие kodi (МахсулотКолдигиниАниклаш, ПоучитьДолг, ОхиргиУртачаНархиниАниклаш_Махсулот, Константы.Склад1) | VERIFIED (konteyner/topshiriq/natija testlari) / 1C'da IMPLEMENTED-UNVERIFIED |
| pc 11: navbatdagi hamma sotuv bitta 1C ochilishida; kassir kutmaydi (chek darhol, 1C'ga fonda); 1C rad etsa — "xato" (admin ko'radi, qoldiq qaytadi); 1C yozayotganda o'chsa (.wip bor, .res yo'q) — qayta yozilmaydi (ikki marta yozilmasin), admin 1C'da tekshiradi; kompyuter o'chib yonsa qolgan natijalar olinadi | VERIFIED (test) |
| pc 11: tashqi ishlov ogohlantirishi (8.3.9+ "Предупреждение безопасности") conf.cfg'da o'chiriladi (DisableUnsafeActionProtection); maslahatda 1cv8.exe ning HAQIQIY yo'li (Program Files (x86)); 1C_oyna_tekshiruv.bat endi ko'prikni sinaydi (hech narsa yozmaydi) | VERIFIED (test) / Windows'da IMPLEMENTED-UNVERIFIED |
| pc 12: kassa qidiruvi telefondagi algoritm bilan bir xil (shared/Search.kt -> logic.js): imlo xatolari (1-2 harf, harflar almashuvi), lotin/kirill, tutuq belgisisiz, h~x q~k, klaviatura tartibi adashsa, so'zlar istalgan tartibda, raqamlar aniq; ko'p sotiladigan yuqorida; mijoz qidiruvi ham shunday | VERIFIED (node testlar + brauzer sinovi) |
| pc 12: panel tavsiyasi — oxirgi 28 kun ПродажаРозница bo'yicha: tarozi (kg) tovarlari oilalar bilan (pistalar, bodomlar, keshu... ketma-ket, nomi bo'yicha), keyin shtrix kodlilar, keyin boshqalar; har hafta o'zi, admin ko'rib tasdiqlaydi (yoki 1C'dagi panelga qaytadi); panel KATTA harfda | VERIFIED (test + brauzer) / 1C so'rovi IMPLEMENTED-UNVERIFIED |
| pc 12: narx turi xaridorga bilinmaydi — yuqoridagi tugma yo'q, raqam maydoni burchagida kichik raqam; 1 soniya bosib turilsa "Rejim 1-4"; narx tekshirishda faqat joriy narx (sozlamada ochiq ko'rsatish mumkin) | VERIFIED (brauzer sinovi) |
| pc 12: skaner HAMMA holatda: panel, qidiruv, to'lov, chek, ma'lumot, sozlama oynasi ochiq bo'lsa ham yopilib tovar savatga tushadi; tez kiritilgan belgilar qidiruv/parol maydonidan olib tashlanadi; qo'lda sekin yozish skaner hisoblanmaydi | VERIFIED (brauzer sinovi) |
| pc 12: avtomatik ishga tushish — dastur Windows'ga kirilishi bilan administrator huquqida (vazifa "IMSOFT server"); AVTO_KIRISH.bat — Windows parol so'ramasdan kiradi, kompyuter uxlamaydi; monoblok kassa sahifasi kompyuter javob berguncha kutadi; 1C kerak bo'lganda o'zi ochiladi (ko'prik) | VERIFIED (test) / Windows'da IMPLEMENTED-UNVERIFIED |
| pc 13: 1C ko'prigi OCHIQ turadi — bir marta ochiladi (kichraytirilgan), har ~1 soniyada papkani tekshiradi, sotuv 1-2 soniyada yoziladi; dastur qayta yonsa ochiq 1C'dan foydalanadi, dastur to'xtasa 5 daqiqada 1C o'zi yopiladi; ko'prik rejimi esda qoladi (COM'ga qayta urinmaydi), 1C oldindan ochib qo'yiladi; .wip li topshiriq qayta yozilmaydi | VERIFIED (test) / 1C'da IMPLEMENTED-UNVERIFIED |
| pc 13: sotuvdan keyin monoblok butun tovar ro'yxatini qayta yuklamaydi — qoldiq va qarz o'zida o'zgaradi (javobda v0/v1); chek chop etish ekranni ushlab turmaydi | VERIFIED (test + brauzer: sotuv 75 ms, qayta yuklash 0) |
| pc 13: chek 1C "Чек1" maketi tartibida: sarlavha (Константы.Текст), Vaqt/Sana, Mijoz (kod, nomi), Agent, oldingi qarz, "N || tovar" va "miqdor х narx = summa" jadvali, Summa/Chegirma Sum-Val, To'lov Naqd/Plastik/Val, keyingi qarz, Tel (Тел, Тел1, Тел2) | VERIFIED (brauzer) / logotip NOT-IMPLEMENTED |
| pc 14: pastdagi tugmalar ikonkali; yashirilganlari o'rniga qolganlari kengayib joyni to'liq egallaydi (6 ustunli to'r, oxirgi qator ham to'ladi; 4 tadan kam bo'lsa 2 ustun) | VERIFIED (brauzer) |
| pc 14: "Savdolar" — kun bo'yicha (oldingi/keyingi kun) kassa savdolari va 1C'dagi boshqa ПродажаРозница hujjatlari; istalganining chekini qayta chiqarish | VERIFIED (test + brauzer) / 1C so'rovi IMPLEMENTED-UNVERIFIED |
| pc 14: savatda tovar nomi pastga tushib (kesilib) qolmasligi — qator balandligi bir xil | VERIFIED (brauzer) |
| pc 15: savatda miqdor birliksiz va nuqta bilan: donali "1", "2"; kiloli "0.122" (chekda 1C kabi qoladi) | VERIFIED (brauzer) |
| pc 16: 1C ko'prigidagi kompilyatsiya xatosi tuzatildi ("Новый Файл(Путь).Существует()" — 8.3.10 da "Неопознанный оператор"); test bunday yozuvni taqiqlaydi | VERIFIED (test) / 1C'da IMPLEMENTED-UNVERIFIED |
| pc 16: chekni 1C'ning O'ZI chiqaradi — bazadagi ПечатьНажатие_1 / ПечатьНажатиеВоз_1 (sensor oynasidagi bilan aynan bir xil, kompyuterdagi chek printeriga); sotuvda, "Oxirgi chek" va "Savdolar"dan qayta chiqarish; chiqmasa kassada xabar; sozlamada brauzer chekiga qaytish mumkin | VERIFIED (test) / 1C'da IMPLEMENTED-UNVERIFIED |
| pc 16: to'lovdan keyin dialog yo'q — darhol bo'sh oyna (qaytim qisqa xabarda); Tozalash va sotuvdan keyin narx turi 1 ga qaytadi; "Blok" tugmasini yashirish; "Kirishda parol so'ralmasin" (tanlangan kassir nomidan avtomatik kirish) | VERIFIED (test + brauzer) |
| pc 17: sotuv javobi tezroq — tovarlar ma'lumoti (JSON/gzip) sotuv paytida emas, faqat so'ralganda tayyorlanadi; sekin javob jurnalga yoziladi; kutish paytida 0→100% to'ladigan chiziq (tez javobda ko'rinmaydi) | VERIFIED (test + brauzer) |
| pc 18: yangilanishdan keyin dastur ishlamay qolishi tuzatildi — yangi nusxa eski nusxa yopilishini 45 s kutadi (avval darhol "boshqasi ishlayapti" deb chiqib ketardi); kassa porti band bo'lsa bo'shaguncha qayta urinadi (avval bir marta urinib to'xtardi -> ERR_CONNECTION_REFUSED) | VERIFIED (test) / Windows'da IMPLEMENTED-UNVERIFIED |
| pc 19: ixcham joylashuv — ro'yxatga 12-14 tovar sig'adi (tugmalar o'lchami o'zgarmagan); oq/qora fon (Sozlamalar); pastdagi tugmalar ikonka + yirik nom; katta oyna tugmalari | VERIFIED (brauzer) |
| pc 19: qoldiq hech qayerda ko'rinmaydi, sozlamada yoqilsa ro'yxatda "Qoldiq" ustuni; qidiruvda oxirgi 12 ta tanlangan tovar; to'lovda banknot tugmalari (50/100/200 ming); sotuvdan keyin QAYTIM (yoki qarz) savat ustida katta yozuvda 6 s; qo'shilgan qator yonib turadi; "tik" ovozi (xato — past ovoz) | VERIFIED (brauzer) |
| pc 19: sariq "1C'ga yozilmagan" faqat sotuv 1 daqiqadan ko'p kutsa; xatolar — Sozlamalar tugmasida qizil nuqta; telefondan yangilanganda kassada "Dastur yangilanmoqda" yozuvi, keyin o'zi qayta ochiladi; Sozlamalarda versiya | VERIFIED (test + brauzer) |
| pc 20: "Kassa yopish" — kun hisoboti 1C'dagi HAMMA savdolar bo'yicha (kassa + 1C sensor oynasi, takrorlanmasdan): savdo, chegirma, qaytarish, naqd/plastik/Click, qarzga, qarz to'lovi, kassirlar; kunlar bo'yicha; kompyuterdagi chek printeriga 1C orqali chop etish | VERIFIED (test + brauzer) / 1C chop etish IMPLEMENTED-UNVERIFIED |
| pc 20: "Miqdor" — tez tanlash: tarozi tovari 0.1…3 kg, donali 1…24; savat ostida 7 ta ixcham tugma (▲ ▼ − + Miqdor O'chirish Tozalash); muvaffaqiyatli skanerda ovoz yo'q (skanerning o'zi chiqaradi) | VERIFIED (brauzer) |
| pc 21: 1C ko'prigi "Ошибка совместного доступа к файлу 1c.alive" tuzatildi — dastur belgi faylini ochmaydi (versiya fayl nomida), 1C fayllarni vaqtinchalik nom bilan yozib almashtiradi (3 marta urinadi), modul xatosi hech qachon oyna chiqarmaydi (1C yopiladi, dastur qayta ochadi); yangi 1C ochishdan oldin osilib qolgan ko'prik 1C'lari yopiladi (bittadan ortiq ochilmaydi); ko'prik 1C oynasi yashiriladi (oldinga chiqmaydi) | VERIFIED (test) / Windows'da IMPLEMENTED-UNVERIFIED |
| pc 21: narxni o'zgartirish — raqam panelida yangi narx + "Narx" tugmasi (parolsiz); qo'lda yozilgan narx belgilanadi, narx turi almashganda o'zgarmaydi, kompyuter uni rad etmaydi; bo'sh bosilsa asl narxga qaytadi | VERIFIED (test + brauzer) |
| Android lint: HashMap.putIfAbsent (API 24) minSdk 23 da — oddiy tekshiruv bilan almashtirildi (AdminHistory, SaleViewModel); CI'da yangilanish fayllari artifact o'rniga cache orqali (artifact kvotasi to'lsa ham yangilanish chiqadi) | VERIFIED (324 test; lint CI'da) |
| Haqiqiy 1C'ga yozish: COM'da ПродажаРозница moduli ishlamaydi (8.3.10), V83.Application "Сбой при удаленном вызове" | Hal qilindi (pc 11-16: 1C ko'prigi — oddiy 1C ochiq turadi, kassa_bridge.epf) — foydalanuvchi sotuvlar va chek ishlayotganini tasdiqladi |
| Kassada yangi mijoz qo'shish, narxni qo'lda o'zgartirish, tovar rasmi, tannarx/foyda ustunlari, chekda logotip | NOT-IMPLEMENTED (kunni yopish — pc 20 "Kassa yopish") |

## 5.3 — 1-qism: ishlamayotganlar, VPNsiz almashuv, bitta ilova
| Narsa | Holat |
|---|---|
| **Telefonlar ko'prigi kompyuterda** (pc/relay.py): katalog bulutga o'zi qo'yiladi, savdo/to'lov SQL serverga kompyuterda yoziladi, kirish/qarz/hisobot so'rovlariga javob — Admin telefoni va VPN kerak emas | VERIFIED (15 Python test, soxta SQL/bulut) / haqiqiy SQL serverda IMPLEMENTED-UNVERIFIED |
| Yo'lni ilova o'zi tanlaydi (AutoBackend): do'kon Wi-Fi'sida to'g'ridan serverga, bo'lmasa bulut orqali; server ko'rinmasa 2–16 daqiqa bulutda qoladi; dublikat yo'q | VERIFIED (9 test) |
| Kompyuter ko'prigi tirik bo'lsa Admin chetda turadi, o'chiq bo'lsa (4 daqiqa jim) Admin o'zi bajaradi; kompyuter yozgan savdoning ko'zgusi (statistika, 2-ilova tarixi, filial cheki) Admin ochilganda bajariladi | VERIFIED (5 test) |
| **Bitta ilova**: Agent ilovasi olib tashlandi; rol kirishdan — egasining raqami (+998907788510) Admin, boshqalar xodim; avvalgi Admin ustiga yangilansa Admin qoladi | VERIFIED (test + kompilyatsiya) / telefonda IMPLEMENTED-UNVERIFIED |
| **1C Konfiguratorsiz**: tovar moduli COM'da "Ошибка инициализации модуля" bersa — 1C oynasi (V83.Application) orqali qayta yoziladi va eslab qolinadi | VERIFIED (4 test, soxta ulanish) / haqiqiy 1C'da IMPLEMENTED-UNVERIFIED |
| **Kompyuter dasturi o'zi yangilanadi** (pc.zip + sha256, sozlama fayllariga tegmaydi, buzuq kod o'rnatilmaydi) | VERIFIED (7 test) / kompyuterda IMPLEMENTED-UNVERIFIED. Birinchi marta qo'lda qo'yiladi |
| Shtrix kod: asosiy kod + qo'shimcha kodlar ro'yxati + "Штрих" ma'lumotnomasi — hammasi telefonga boradi (avval faqat bittasi) | VERIFIED (2 test) / haqiqiy bazada IMPLEMENTED-UNVERIFIED |
| 1C navbati: qayta yuborish o'sha qatorni yangilaydi (urinish soni, birinchi vaqt), eski takrorlar o'zi birlashadi; saralash (hammasi/kutmoqda/xato/kiritildi); tozalash; hamma xatoni birdan qayta yuborish; bosh sahifada karta | VERIFIED (6 test + kompilyatsiya) |
| Savdo sozlash oynasi: do'kon tanlangach oyna qayta ochiladi, tanlangan narx turi yo'qolmaydi | VERIFIED (kompilyatsiya) / ekranda IMPLEMENTED-UNVERIFIED |
| Qurilmalar: bitta telefon — bitta qator (qayta o'rnatishdan qolgan eski yozuvlar yashiriladi, bir bosishda o'chiriladi), oxirgi ko'ringani tepada | VERIFIED (5 test) |
| Mijoz: printer ulanishi bilan bo'lim ochiladi, ruxsat so'raladi, servis yonadi va navbat chiqadi; holat servis o'chiq bo'lsa ham to'g'ri; sinov cheki servisiz ham; boshqa sinfdagi printerlar ham topiladi | VERIFIED (kompilyatsiya) / printerda IMPLEMENTED-UNVERIFIED |
| Veb-agent: "Do'kon kodi" (IMSHOP1) ham qabul qilinadi, kod atrofidagi matn e'tiborsiz | VERIFIED (test) |
| Agent yorliq chop etishi (lb navbati) — pc 28 dan kompyuter o'zi chop etadi (Admin telefoni shart emas) | VERIFIED (test) / printerda IMPLEMENTED-UNVERIFIED |

## 5.3 — 2-qism: keraksizlar olib tashlandi, bir xil ishlar bitta joyda
| Narsa | Holat |
|---|---|
| **Boshqaruv/Sozlamalar** — katta plitkalar (2 ustun, planshetda 3): katta ikonka, yirik nom, izoh; bo'lim har doim plitka, ish ekrani har doim ish ekrani | VERIFIED (kompilyatsiya) / ekranda IMPLEMENTED-UNVERIFIED |
| **Mahsulotlar markazi**: qidiruv (kod/nom/narx), qatorda tez amallar (tahrirlash, ma'lumot, rasm), bosilsa hamma amal; yangi tovar, ko'p tovarga birdan, hisobdan chiqarish, fayl — shu yerda | VERIFIED (kompilyatsiya) / ekranda IMPLEMENTED-UNVERIFIED |
| **Mahsulot rasmi** (faqat telefonda): kamera yoki galereya, ro'yxatda va savdo oynasida ko'rinadi | VERIFIED (3 test + kompilyatsiya) / telefonda IMPLEMENTED-UNVERIFIED |
| **Mijozlar va Yuk beruvchilar markazi**: nom + telefon + qarz, qatorda tez amallar (to'lov, qaytarish, kirim, tahrirlash), bosilsa tarixi | VERIFIED (kompilyatsiya) / ekranda IMPLEMENTED-UNVERIFIED |
| Kontragent telefonlari 1C'dan (kompyuter yuboradi) | VERIFIED (Python testlar o'tadi) / haqiqiy 1C'da IMPLEMENTED-UNVERIFIED |
| **"Qarzni to'liq yopish"**: bir marta tasdiq, to'lov darhol navbatga, ro'yxatga qaytadi, qarz darhol nol ko'rinadi. Dollar qarzi yopilmaydi (ogohlantiradi) | VERIFIED (kompilyatsiya) / telefonda IMPLEMENTED-UNVERIFIED |
| **Aqlli qidiruv** (savdo va mahsulotlar): raqam — kod, keyin narx; harf — nom; harf+raqam — nom va narx; narx tanlangan narx turi bo'yicha | VERIFIED (9 test) |
| **Savatcha ikki qadam**: Mahsulotlar / To'lov; tepada "hammasini tozalash"; uzoq bosish — faqat shu mahsulot | VERIFIED (kompilyatsiya) / ekranda IMPLEMENTED-UNVERIFIED |
| **Yangi tovar**: tepada guruh, nom, hajm, narxlar, turi (shtrix kodli / kodni yaratish / tarozili); qolgani "Qo'shimcha maydonlar" ichida | VERIFIED (kompilyatsiya) / ekranda IMPLEMENTED-UNVERIFIED |
| Yangi tovarda "Qoldiq me'yori" (НормаОст, odatda 1) | VERIFIED (kompilyatsiya; rekvizit nomi baza kodidan) |
| Yangi tovarda "Fayl" (РасмНоми — kompyuterdagi rasm fayli nomi) | NOT-IMPLEMENTED (rasm telefonda alohida saqlanadi) |
| Savdoda mijoz tanlash oynasida "Yangi mijoz qo'shish" (1C'ga qo'shiladi, tayyor bo'lgach o'zi tanlanadi) | VERIFIED (kompilyatsiya) / haqiqiy 1C'da IMPLEMENTED-UNVERIFIED |
| Dollar kursi: har kuni Markaziy bankdan, qo'lda yozilgani ham almashtiriladi; sozlamasi olib tashlandi | VERIFIED (kompilyatsiya) |
| Olib tashlandi: Agent ilovasi, chek ko'rinishi (bitta standart chek), dollar kursi, kassa kirim/chiqim, sozlamalardagi "mijozdan to'lov"/"mijoz qaytargan tovar", narx belgilash (tahrirlashga qo'shildi), agentlar ko'prigi va server sozlamasi (server — faqat sirli menyuda) | VERIFIED (kompilyatsiya) |
| Sirli menyu (versiyani uzoq bosish): server sozlamasi va hamma parollar ochiq ko'rinadi | VERIFIED (kompilyatsiya) |
| 1C navbati: yuborilmaganda aniq sabab va maslahat; kassa paneli qatorlari "N-tugma: tovar" ko'rinishida | VERIFIED (kompilyatsiya) |
| Bio matni savdoda yirikroq (ikkala ilovada) | VERIFIED (kompilyatsiya) |
| **Kassa paneli telefondan** (1C: Константы.тов1…тов336, 8 sahifa x 42): tugmaga tovar qo'yish/bo'shatish, "ko'p sotilganlar bilan to'ldirish", bitta so'rov bilan saqlash, bekor qilsa eski holat qaytadi | VERIFIED (6 Kotlin + 5 Python test) / haqiqiy 1C'da IMPLEMENTED-UNVERIFIED |
| 1C baza kodi (BaseOld.zip) o'qildi: savdo oynasi (РеализацияСенсор1) ПродажаРозница hujjatini qanday to'ldirishi, Товары rekvizitlari, dastur moduli (мМобилАпп) tasdiqlandi | VERIFIED (manba o'qildi) |
| Xodim (agent) ko'rinishini alohida soddalashtirish, statistika, Mijoz ilovasi dizayni, veb-agentni ilova bilan bir xil qilish | IMPLEMENTED — pastdagi «5.3.6 + iPhone» bo'limiga qarang |
| Sensor savdo oynasi (monoblok) | IMPLEMENTED — kompyuter dasturi pc 8-20 (pastdagi "Kompyuter 5.4 — kassa" bo'limi) |

## 5.2 — 3-qism: tekshiruvda topilgan kamchiliklar yopildi
| Narsa | Holat |
|---|---|
| A3: 2-ilovaning O'Z kassa cheki ham 1C "Chek1" maketida (avval qo'lda yasalgan ko'rinishda edi) — endi uchala ilovada AYNAN bir xil | VERIFIED (kompilyatsiya) / printerda IMPLEMENTED-UNVERIFIED |
| A3: 2-ilova chek sozlamasidan "sarlavha osti" va "rahmat" qo'shimchalari olib tashlandi; 1C maketidagi "Agent" maydoni qo'shildi | VERIFIED |
| A7: savdo oynasidagi ma'lumot matnini uzoq bosib Xprinterdan chiqarish (Admin) | VERIFIED (kompilyatsiya) / printerda IMPLEMENTED-UNVERIFIED |
| A10b: avtomatik shtrix kod yoqilganda qanday kod yasalishi ko'rsatiladi (kompyuterdagi qoida bilan aynan bir xil) | VERIFIED (3 test) |

## 5.2 — 2-qism: savdo tahriri, 1C shakllari, mahsulot ma'lumoti, ommaviy amallar
| Narsa | Holat |
|---|---|
| **A5**: tarixdagi savdoni o'zgartirish — Admin 1C hujjatidagi tovar/miqdor/narxni almashtiradi, 1C izohiga "Admin o'zgartirdi" qo'shiladi, eski qatorlar kompyuter jurnalida. CHEKLOVSIZ (istalgan eski hujjat) | VERIFIED (5 Kotlin + 4 Python test, kompilyatsiya) / 1C'da IMPLEMENTED-UNVERIFIED |
| **A10a**: yuk beruvchi tanlash — qidiruvli ro'yxat (nom, kod, telefon), qarz va oxirgi hujjat sanasi, oxirgi ishlanganlar tepada | VERIFIED (kompilyatsiya) |
| **A10b**: to'lovda qarz, to'lovdan keyingi qoldiq (yozayotganda darhol), "Qarzni to'liq yopish", "Tarixi" (oxirgi 40 hujjat, qidiruv bilan); kirimda hujjatdan keyingi qarz | VERIFIED (kompilyatsiya) |
| **A10b**: yangi tovarda keyingi bo'sh PLU taklif qilinadi, eng katta kod ko'rsatiladi | VERIFIED (kompilyatsiya) |
| **A7**: mahsulot ma'lumoti (bio) — matn ALOHIDA yozuv, tovarlar unga ulanadi | VERIFIED (15 test) |
| **A7b**: IKKITA maydon — asosiy matn CHEGARASIZ (savdo paytida ko'rinadi), yorliq matni 278 belgi (faqat chop etish uchun, doim saqlanadi) | VERIFIED (testlar + kompilyatsiya) |
| A7b: "Asosiydan olish" tugmasi — yorliq matnini asosiydan to'ldirish (278 ga kesib) | VERIFIED (kompilyatsiya) |
| A7b: eski yozuvlar (yorliq matni yo'q) ochiladi, chop etish tugmasi bo'sh matnda ogohlantiradi | VERIFIED (test) |
| A7: Sozlamalar > Mahsulotlar — qidiruv, "ma'lumoti bor/yo'q" filtri, soni; internetsiz ishlaydi | VERIFIED (kompilyatsiya) |
| A7: ko'chirish (kod almashsa), bir nechta mahsulotga ulash, uzish, boshqasidan olish | VERIFIED (testlar) |
| A7: savdo oynasida INFO — bosilsa klaviatura yopilib matn tepada ochiladi; "avtomatik ochish" sozlamasi (3 ilovada) | VERIFIED (kompilyatsiya) / ekranda IMPLEMENTED-UNVERIFIED |
| A7: Xprinterdan chiqarish — 1C dagidek: ODDIY (qalin emas) Arial 10, NOM chop etilmaydi, faqat matn | IMPLEMENTED-UNVERIFIED — printerda sinash kerak |
| A7: kompyuterda (Excel) yozish — CSV chiqarish/yuklash (kod;nom;malumot;chop_matni) | VERIFIED (3 test) |
| A7: ma'lumot 2-ilovaga snapshot bilan, Agentga bulut orqali boradi | VERIFIED (kompilyatsiya) / ulanishda IMPLEMENTED-UNVERIFIED |
| **A11**: ko'p tovarga birdan — nomdan belgi olib tashlash/almashtirish, matn qo'shish (bosh/oxir/indeks), maydon o'zgartirish | VERIFIED (8 Kotlin + 5 Python test) |
| A11: qamrov (guruh, nomida, kodlar, hammasi), KO'RIB CHIQISH (nechta + 20 misol), tasdiq, bajarish | VERIFIED (testlar) |
| A11: kompyuter COM orqali bajaradi — telefon o'chsa ham davom etadi | VERIFIED (kompilyatsiya) / 1C'da IMPLEMENTED-UNVERIFIED |
| A11: **QAYTARISH** — eski qiymatlar saqlanadi, Yozuvlar ro'yxatidan bitta tugma | VERIFIED (kod) / 1C'da IMPLEMENTED-UNVERIFIED |
| A11: **qoldiqni belgilangan songa tushirish** — 1C'dagi "СписанияТоваров" (hisobdan chiqarish) hujjati bilan, bitta hujjatda. Qoldig'i kam bo'lganlar tegilmaydi, ro'yxati natijada chiqadi | VERIFIED (2 Kotlin + 2 Python test) / 1C'da IMPLEMENTED-UNVERIFIED |
| **A12**: 2-ilovada "server" -> "do'kon kompyuteri"; "kontragent" -> "mijoz / yuk beruvchi"; Agentda 1C server sozlamasi yashirildi | VERIFIED |
| A12: qolgan soddalashtirish (kam ekran, qisqa savdo) | IMPLEMENTED-UNVERIFIED — qaysi joy qiyinligini aytsangiz aniq qilamiz |

## 5.2 — 1-qism: mayda detallar, sozlamalar va holat
| Narsa | Holat |
|---|---|
| A1: narx yorlig'i chop etilgach tanlangan ro'yxat o'zi tozalanadi | VERIFIED (kompilyatsiya) |
| A2: tarixdagi "yangilanish" uzun qatori olib tashlandi — tepada ikonka, bosilsa hamma ma'lumot | VERIFIED (kompilyatsiya) |
| A3: tarixdagi savdo/to'lovga "Chek" tugmasi — 1C "Chek1" maketi, uchala ilovada bir xil | VERIFIED (kompilyatsiya) |
| A3: chekdan logo va logo sozlamalari olib tashlandi (1C chekida yo'q) | VERIFIED |
| A4: ro'yxat sarlavhasi "Savdo — Dadaxon aka" / "Savdo — Naqd", nom savdo so'zi bilan bir xil o'lchamda | VERIFIED (kompilyatsiya) |
| A6: tarixda mijoz bo'yicha qidiruv (qidiruvli ro'yxat), jami ham shunga qarab hisoblanadi | VERIFIED (kompilyatsiya) |
| A8: bosh sahifa pastida holat xulosasi — yashil belgilar, xato bo'lsa aynan nima (internet, Tailscale, kompyuterdagi server, 1C bazasi, navbat) | VERIFIED (kompilyatsiya) |
| A9: tepadagi uch nuqta olib tashlandi — Boss, Statistika, Shtrix kod, Yangilash ikonkalari | VERIFIED (kompilyatsiya) |
| A9: Sozlamalar faqat bo'limlardan iborat, 5 guruhga ajratilgan; til/mavzu/o'lcham alohida bo'lim; Boshqaruv eng pastda | VERIFIED (kompilyatsiya) |
| A10-0: Boshqaruvdagi Boss/Statistika/Sozlamalar takrorlari olib tashlandi | VERIFIED |
| A12 (qisman): 2-ilovada "server" so'zi "do'kon kompyuteri" ga almashtirildi; Agentda 1C server sozlamasi yashirildi | VERIFIED |
| A5, A7, A10a/b, A11 | ISHLANMOQDA — keyingi qismda |

## 5.1.1a — qurilmada topilgan 3 ta xato
| Narsa | Holat |
|---|---|
| "Chek qayerda chiqsin" oynasida tugmalar ko'rinmasligi | VERIFIED (sabab: AlertDialog'da `setMessage` va `setItems` BIRGA ishlamaydi — xabar bo'lsa ro'yxat yashirinadi; xabar sarlavhaga ko'chirildi) |
| "Savatchaga" ishlamasligi | VERIFIED (sabab: Mijozdagi bilan bir xil — `Fragment.ledgerHost()` buyurtmalar varaqasidan null qaytarardi; endi `DokonLedgerHost` ga tushadi) |
| Bildirishnoma kelmasligi | VERIFIED (3 ta sabab yopildi: Android 13+ POST_NOTIFICATIONS ruxsati ilova ochilganda bir marta so'raladi; `refreshOrders` endi sukut bo'yicha xabar beradi; `rulesVersion >= 2` sharti olib tashlandi) |
| Buyurtmalar varaqasida "bildirishnoma ruxsati yo'q" / "fon xizmati o'chiq" ogohlantirishi (bosilsa ruxsat so'raydi) | VERIFIED (kompilyatsiya) / qurilmada IMPLEMENTED-UNVERIFIED |

## 5.1.1 — Mijoz kamchiliklari va buyurtmani tasdiqlash
| Narsa | Holat |
|---|---|
| Mijoz: "Buyurtma berish" asosiy tugmalar qatorida (2-tugma); buyurtma yopiq bo'lsa shu tugma "Narxlar" bo'ladi | VERIFIED (kompilyatsiya) / ekranda IMPLEMENTED-UNVERIFIED |
| Mijoz: narx turlari "1-narx" emas "1", "2", "3", "4" | VERIFIED (strings.xml, lotin va kirill) |
| **Mijoz: "Yakunlash/Yuborish" bosilganda hech narsa bo'lmasligi — XATOLIK tuzatildi** | VERIFIED (sabab: `Fragment.ledgerHost()` "Narxlar" ekranida null qaytarar edi va buyurtma oynasi darhol yopilib ketardi; endi `MijozLedgerHost` ga tushadi) |
| Admin: yangi buyurtma darhol bilinadi (Firebase oqimi — `s/{kalit}/ord`, oxirgi 1 yozuv) | VERIFIED (kompilyatsiya) / internet bilan IMPLEMENTED-UNVERIFIED |
| Admin: buyurtma bildirishnomasi alohida, ovozli kanalda (IMPORTANCE_HIGH), har buyurtmaga bittasi | VERIFIED (kompilyatsiya) / qurilmada IMPLEMENTED-UNVERIFIED |
| Admin: bildirishnomadagi "Tasdiqlash" — savdo darhol rasmiylashadi, chek **Printer 1** dan chiqadi | VERIFIED (`OrderConfirmTest.directSalePayloadIsDebtSaleOnChosenPrinter`) / 1C va printerda IMPLEMENTED-UNVERIFIED |
| Admin: bildirishnomadagi "Rad etish" | VERIFIED (kompilyatsiya) |
| Admin: ilova ichida tasdiqlaganda chek printerini o'zi tanlaydi (Printer 1 / Printer 2 / Cheksiz) | VERIFIED (kompilyatsiya) / printerda IMPLEMENTED-UNVERIFIED |
| Tasdiqlash savatchaga TEGMAYDI (`OrderRepository.sendDirect`): optom narx, so'm, qarzga, chegirmasiz | VERIFIED (`OrderConfirmTest`, 4 ta test) |
| "Savatchaga" yo'li saqlandi (tovar/miqdorni o'zgartirish kerak bo'lsa) | VERIFIED (kompilyatsiya) |
| Bildirishnoma bosilsa buyurtmalar ro'yxati ochiladi | VERIFIED (kompilyatsiya) |
| Bildirishnomadan tasdiqlash ilova yopiq bo'lsa ham ishlashi (WorkManager) | IMPLEMENTED-UNVERIFIED — qurilmada sinash kerak |

## 5.1 — 1C'ga yozish 1C'dagidek
| Narsa | Holat |
|---|---|
| Yangi tovar: kod 1C tomonidan avtomatik (shaklda yozib turiladi) | VERIFIED (kod: `УстановитьНовыйКод`) |
| Guruh, brend, o'lchov birligi, tarozi — 1C ma'lumotnomalaridan tanlanadi; kompyuter ro'yxatni yuboradi | VERIFIED (`catalog_refs` + `test_catalog_refs`, `Suppliers.refs`) / 1C'da IMPLEMENTED-UNVERIFIED |
| Hajmi (Обьём), upakovkada dona (Шт), ustama % (Нац) | VERIFIED (`NewGoodsTest.numbersChecked`, PC `CARD_NUMS`) |
| Tovar turi Oddiy / Tarozi (Перечисление.ТипТовара); tarozi tanlansa PLU va tarozi majburiy | VERIFIED (`NewGoodsTest.weighedNeedsPlu`) |
| Shtrix kodni 1C o'zi yaratishi (ichki EAN-13, "2" bilan, band bo'lmagani) | VERIFIED (`AutoBarcode.test_check_digit`) / 1C'da IMPLEMENTED-UNVERIFIED |
| Namuna tovar ixtiyoriy — bo'lmasa bo'sh kartochka, guruh va o'lchov birligi majburiy | VERIFIED (`NewGoodsTest.groupAndUnitRequiredWithoutSample`) |
| Yuk beruvchiga to'lovda bizning qarzimiz | VERIFIED (kod) / ma'lumot bilan IMPLEMENTED-UNVERIFIED |
| Kontragent kartochkasida INN va kredit limiti | VERIFIED (kompilyatsiya + PC `PARTY_ATTR`/`PARTY_NUMS`) |
| Kontragentning bank rekvizitlari (РС, Банк, МФО, ОКОНХ), "Доставшик" | NOT-IMPLEMENTED (telefondan kam kiritiladi; kerak bo'lsa qo'shiladi) |
| Tovar kartochkasidagi Кг, НормаОст, Доля %, Клавиатура, rasm | NOT-IMPLEMENTED |
| Yangi tovar 1C'da haqiqatda yaratilishi (COM) | IMPLEMENTED-UNVERIFIED — kompyuterda sinash kerak |

## 5.0.5 — ko'rinish tuzatishlari, dollarsiz, yuk beruvchi
| Narsa | Holat |
|---|---|
| Plitkalar bir qatorda bir xil balandlikda (Ui.tiles) — uchchala ilovada | VERIFIED (kompilyatsiya) / ekranda IMPLEMENTED-UNVERIFIED |
| Bosh sahifadagi katta bo'shliq: "Ochiq savdolar" kartasi tugmalar qatori ICHIGA qo'yilayotgani sabab edi — tuzatildi | VERIFIED |
| 4 ta tugma teng balandlikda (match_parent) — Agent/Admin va Mijoz | VERIFIED |
| Menyu va tanlov varaqasi aylantiriladi (uzun ro'yxat kesilmaydi) | VERIFIED |
| Narx yorlig'i: PLU bo'lmasa "KOD: 0" bilan chiqadi | VERIFIED (kod) / printerda IMPLEMENTED-UNVERIFIED |
| Savdoda valyuta tanlash, savatchadagi dollar to'lovi va 1C shakllaridagi dollar summalari olib tashlandi | VERIFIED (kompilyatsiya) |
| "Ta'minotchi" -> "Yuk beruvchi" (hamma matnlar) | VERIFIED (0 ta eski so'z qoldi) |
| Yuk beruvchiga to'lovda bizning qarzimiz ko'rsatiladi | VERIFIED (kod) / 1C ma'lumoti bilan IMPLEMENTED-UNVERIFIED |
| Tarixdagi eski dollar summalari ko'rsatilishi (kiritish emas) | IMPLEMENTED-UNVERIFIED (ataylab qoldirildi — 1C'da eski dollar qarzlar bor) |
| **1C'ga yoziladigan shakllar 1C bilan to'liq bir xil** (yangi tovar: guruh, brend, o'lchov birligi, hajmi, shtrix/tarozi turi, avtomatik shtrix; kirim; qolgan hujjatlar) | IMPLEMENTED (5.1 dan: OneCFormFragment — guruh/brend/birlik/tarozi 1C ro'yxatlari bilan, avtomatik shtrix; `onec_write.py` CARD_REFS) / haqiqiy 1C'da IMPLEMENTED-UNVERIFIED |

## 5.0.4 — vidjet
| Narsa | Holat |
|---|---|
| Vidjet uchchala ilovada (Agent, Admin, Mijoz) | VERIFIED (kompilyatsiya, manifest va resurslar) / telefonda IMPLEMENTED-UNVERIFIED |
| 6 ta ikonkali tugma (2 qator x 3 ta), 4x2 katak | VERIFIED (maket) |
| Har tugmaning amali ilovadan tanlanadi (Sozlamalar ▸ Vidjet), tanlov saqlanadi | VERIFIED (kompilyatsiya) |
| Tez do'kon tugmasi: bosilishi bilan shu do'kon bilan savdo (Agent/Admin) | IMPLEMENTED-UNVERIFIED |
| Ko'rinish: mavzuning yuza rangi, chegara, 22dp burchak; ranglar kodda beriladi (vidjetda mavzu atributlari ishlamaydi) | VERIFIED |
| Mijoz vidjeti tepasida ism va qarz | IMPLEMENTED-UNVERIFIED |
| Vidjet o'lchamini o'zgartirganda tugmalar soni o'zgarishi (responsive) | NOT-IMPLEMENTED (6 ta tugma doim) |

## 5.0.3 — bosh sahifa, menyu, kurs va statistika
| Narsa | Holat |
|---|---|
| Shtrix kod ikonkasi yana tepada | VERIFIED |
| Bosh sahifada 4 ta katta ikonkali tugma (2 qator, yumaloq burchak, eng tepada); 3- va 4-si uzoq bosib tanlanadi va saqlanadi | VERIFIED (kompilyatsiya) / telefonda IMPLEMENTED-UNVERIFIED |
| Boss va Boshqaruv faqat menyuda; uch nuqta — ikonkali ro'yxat (Android menyusi ikonka ko'rsatmaydi) | VERIFIED |
| Pastdagi 5-bo'lim ikkala rolda ham Sozlamalar (Boshqaruv takrorlanmasin) | VERIFIED |
| Boshqaruv va Sozlamalar plitkali: 2 ustun, keng ekranda 3; uzoq bosilsa izoh | VERIFIED |
| Dollar kursi faqat mahalliy: internetdagi bozor kursi kuniga bir marta olinadi, hamma hisob shundan; 1C kursiga qaralmaydi | VERIFIED (kompilyatsiya, `RateQuoteTest`) / internetda IMPLEMENTED-UNVERIFIED |
| Qo'lda yozilgan kurs internetdan almashtirilmaydi (o'chirib-yoqiladigan sozlama) | VERIFIED |
| Statistika mijoz filtri: Hammasi / Faqat shu / Shusiz — hamma statistika ekranida bir xil | VERIFIED (`StatsClientsTest`) |
| Mijozlar statistikasi: savdo, foyda va ulushi, chek soni, o'rtacha chek, ulush (halqa), oxirgi savdo; mijoz bosilsa to'liq statistikasi | VERIFIED (`Stats.clients` testlari) / ekran IMPLEMENTED-UNVERIFIED |
| Boss: "Oy boshidan" va "Mijozlar" bo'limlari | IMPLEMENTED-UNVERIFIED |
| Statistika faqat savdo yozuvlari va oxirgi kirim narxlaridan | VERIFIED (`costByName`, `Stats.profitOf`) |
| Mijoz ilovasida: o'z xaridlari bo'yicha soatlar va "oldin olinardi, endi yo'q"; bosh sahifa tugmalari va ikonkali menyu | VERIFIED (kompilyatsiya) / telefonda IMPLEMENTED-UNVERIFIED |
| Mijoz ilovasida mijozlar kesimi (u bitta mijoz — o'zi) | NOT-IMPLEMENTED (kerak emas) |

## 5.0.2 — yorliq va chek 1C bilan bir xil
| Narsa | Holat |
|---|---|
| PLU yorlig'i: nom Arial 16 qalin, narx Arial 26 qalin, kod Arial 20 qalin (1C "PLU Narx" shaklidagi aynan o'lchamlar, pt -> printer nuqtasi) | VERIFIED (kod + o'lchamlar shakldan olindi) / qog'ozda IMPLEMENTED-UNVERIFIED |
| Harflarni sun'iy qalinlashtirish olib tashlandi (harflar bo'rtib chiqardi) | VERIFIED |
| Shtrixli yorliq: nom Arial 16 qalin, narx Arial 20 qalin (1C "Shtrix chek" shaklidan) | IMPLEMENTED-UNVERIFIED |
| Chek 1C "Чек1" maketining aynan o'zi: Vaqt/Sana, Mijoz (raqam), Mijoz, Agent, Qarz qoldig'i (So'm/Val), ramkali tovar qatorlari, Summa, Chegirma, To'lov (Naqd/Plastik/Val), Qarz qoldig'i, Tel | VERIFIED (`ChekTest`, `ReceiptTest`) / printerda IMPLEMENTED-UNVERIFIED |
| Chekdagi qo'shimchalar olib tashlandi: do'kon nomi sarlavhasi, "Xodim/Chek №", "QAYTIB OLISH" banneri, rahmat yozuvi, kichik izohlar | VERIFIED (test: chekda yo'q) |
| Naqd va plastik chekda alohida (savdoda kiritilganidek) | VERIFIED (`ChekTest.saleChek`) |
| Chek ko'rinishi ekranida ishlamay qolgan tugmalar olib tashlandi (logo, Agent, telefon, qog'oz eni, qoralik, bo'sh joy qoldi) | VERIFIED (kompilyatsiya, 28 ta keraksiz matn o'chirildi) |
| Chek yozuvlari lotin (maketning "UZB" tili). 1C kirillda chop etsa — `Chek.Labels.CYRILLIC` bitta joyda almashtiriladi | IMPLEMENTED-UNVERIFIED |
| 1C tarixidan chop etilgan chekda naqd/plastik taqsimoti 0 (1C hisobotida bu ma'lumot yo'q) | NOT-IMPLEMENTED (ataylab) |

## 5.0.1 — savdo ro'yxati tuzatildi
| Narsa | Holat |
|---|---|
| Savdo ro'yxatida har doim hamma tovar (`SaleViewModel` do'kon tanlanganda `ListFilter.Suggested`ga o'tmaydi) | VERIFIED (kompilyatsiya + 228 test) |
| Taklif ma'lumotini kerak bo'lganda ko'rish: tepadagi "Mijoz odatda oladigan tovarlar: N ta" bosilsa izohlar ro'yxati va "Hammasini qo'shish" | IMPLEMENTED-UNVERIFIED (telefonda ko'rish kerak) |
| Taklif miqdori ("+" bosilganda odatiy miqdor) va miqdor oynasidagi tarix o'zgarmadi | VERIFIED |

## 5.0 — 24 ta yaxshilash (Admin / Agent / Mijoz)
Raqamlar — sizning ro'yxatingiz tartibida.

| # | Narsa | Holat |
|---|---|---|
| 1 | Bir vaqtda bir nechta savdo oynasi: Bosh sahifada "Ochiq savdolar" ro'yxati, yangi savdo boshlanganda joriysi avtomatik kutishga o'tadi, bosilsa qaytadi | VERIFIED (kompilyatsiya + savatcha testlari) / telefonda IMPLEMENTED-UNVERIFIED |
| 2 | Dollar kursi internetdan: bank kursi (bozorga yaqin) va Markaziy bank kursi, sana bilan; bosilsa maydonga tushadi, 1C'ga faqat tasdiqlasangiz yoziladi | VERIFIED (`RateQuoteTest` — ikki xil sayt formati) / internetda IMPLEMENTED-UNVERIFIED |
| 3 | Pastdagi 5 bo'lim: Bosh sahifa, Savdo, To'lov, Tarix, 5-si — Adminda "Boshqaruv", Agentda "Sozlamalar" | VERIFIED (menyu + rol testlari) |
| 4 | "Boss" rejimi uch nuqta menyusida (Adminda) | IMPLEMENTED-UNVERIFIED |
| 5 | Takrorlangan funksiyalar olib tashlandi — har ish bitta joyda (Savdodagi filtr/park/takror, tugash dialogi, Tarix menyusi, Sozlamalardagi dubllar) | VERIFIED (o'chirilgan fayllar: FinishSheet, ParkedSheet, CartAdapter) |
| 6 | Savdo ekrani ixcham: eng yuqorida qidiruv + skaner + sozlash, do'kon/narx turi/tannarx dialogda, pastdagi panel kichik | IMPLEMENTED-UNVERIFIED (telefonda ko'rish kerak) |
| 7 | Tovar qatori: 1-qator — kod va nom, 2-qator — narx, PLU va blok; "taxminiy oldin olingan" matni yo'q | VERIFIED (kompilyatsiya) |
| 8 | "Savdo" bosilsa dialog ochiladi; dialog yopilsa kursor qidiruvda, klaviatura ochiq | IMPLEMENTED-UNVERIFIED |
| 9 | Tovar dialogi kichik, maydonlar qisqa, pastdagi uch tugma yo'q, qizil "Kerak emas" tugmasi bor (taklifdan bir hafta chiqmaydi) | VERIFIED (kompilyatsiya + FeedbackReason) |
| 10 | Pauza belgisi yo'q; asosiy tugma uzoq bosilsa "hammasini tozalash" so'raladi | IMPLEMENTED-UNVERIFIED |
| 11 | Tugash dialogi yo'q — to'lov savatcha ro'yxatining oxirida, tovar qatorlaridan ajralib turadi | IMPLEMENTED-UNVERIFIED |
| 12 | Hamma joyda minglik ajratiladi ("12 000") va maydonlar qisqa — Agent, Admin va Mijozda | VERIFIED (`NumTextTest`, `NumTextRoundTripTest`; Mijozda ham `NumText.parse`) |
| 13 | Salomlashish / telefon / chiqish / ilova haqida — alohida "Profil" ekranida (versiya faqat shu yerda) | VERIFIED (kompilyatsiya) |
| 14 | Printer tanlovi: Printer 1 / Printer 1+2 / Printer 2 / Cheksiz | IMPLEMENTED-UNVERIFIED (printerda sinash kerak) |
| 15 | Dadaxon aka uchun 4-narx — faqat taklif, o'zgartirib bo'ladi | VERIFIED (narx maydoni ochiq) |
| 16 | Miqdor dialogining pastida shu mijozning shu tovar bo'yicha tarixi (qachon, qancha, qanchadan) | VERIFIED (`LedgerOrdersTest`) / 1C ma'lumoti bilan IMPLEMENTED-UNVERIFIED |
| 17 | Tavsiya/tahlil endi mijozning HAMMA xaridini hisobga oladi (1C tarixi + telefon savdolari, 6 soat / 0,5 % ichida takror hisoblanmaydi) | VERIFIED (`suggestionsLearnFromShopSales`) |
| 18 | Tarix ekrani: printer belgisi va "Tarix" sarlavhasi yo'q; yuqorida uch bo'lim, keyin qidiruv, filtrlar, sanoq, ro'yxat | IMPLEMENTED-UNVERIFIED |
| 19 | Har daqiqada yangilash yo'q: faqat ish vaqtida, kompyuterdagi ma'lumot o'zgarsa yoki qo'lda; bitta tugma kun → hafta → oy → yil → hammasi bosqichida oladi, avval telefon bazasiga yozadi, ko'rsatish bazadan | VERIFIED (`LedgerDb`, bosqich testlari) / uzoq ishlashi IMPLEMENTED-UNVERIFIED |
| 20 | Sozlamalar: ism/telefon yo'q, versiya pastda (Profilda), ko'rinish ixcham, 2 ustunli plitkalar, uzoq bosilsa to'liq izoh | VERIFIED (kompilyatsiya) |
| 21 | Admin — faqat egasiga, to'liq huquq; Agent — faqat savdo, to'lov, tarix, shtrix kod (admin ishlari yo'q) | VERIFIED (`AppRoleTest` + gating) |
| 22 | Uch ilova — uch qiyofa: Admin qorong'i "terminal" (monospace raqam, o'tkir burchak, yashil-amber urg'u, yangi belgi `>_`), Agent yorug' va do'stona (yumaloq), Mijoz indigo va juda yumaloq | VERIFIED (uch xil mavzu/rang/shrift/belgi, res tekshiruvi 0 xato) / ko'rinishi telefonda IMPLEMENTED-UNVERIFIED |
| 23 | Adminda Boshqaruv/Boss/Sozlamalar chalkashligi: Boshqaruv — qidiruvli bitta ro'yxat (1C ko'rish / 1C'ga yozish / Odamlar / Tovarlar / Ilova), har sozlama bitta joyda | VERIFIED (kompilyatsiya) |
| 24 | Mijoz interfeysi butunlay boshqacha va shu yaxshilashlar unda ham: indigo mavzu, kod+nom qatori, minglik ajratish, 1C bazasidan statistika va tarix | VERIFIED (kompilyatsiya, Mijoz testlari) / telefonda IMPLEMENTED-UNVERIFIED |

### 5.0 — qo'shimcha
| Narsa | Holat |
|---|---|
| Admin mavzusi har doim qorong'i — Sozlamalarda yorug'/qorong'i tanlovi Adminda ko'rsatilmaydi (ishlamaydigan tugma qolmasin) | VERIFIED |
| Agent palitrasi `app/src/main/res`, Admin palitrasi `app/src/admin/res` (flavor main'dan ustun) — `tools/gen_theme.py` yaratadi | VERIFIED (`tools/res_check_all.sh`: agent/admin/mijoz — 0 xato) |
| Kurs manbalari: `nbu.uz/uz/exchange-rates/json/` (bank) va `cbu.uz/uz/arkhiv-kursov-valyut/json/USD/` (rasmiy); o'qish "bag'rikeng" — sayt maydon nomini o'zgartirsa ham USD topiladi, topilmasa oxirgi saqlangani ko'rsatiladi | VERIFIED (parse testlari) / saytlar javobi IMPLEMENTED-UNVERIFIED |
| Minglik ajratgich bilan yozilgan sonni o'qish — bo'shliq, NBSP, "narrow" bo'shliq va apostrof tushadi | VERIFIED (`NumTextTest.parseIgnoresGroupingAndNbsp`) |
| Mijozda yangilash allaqachon ish vaqtiga bog'langan (ish vaqtida — `liveMinutes`, tashqarisida 15 daqiqa) | IMPLEMENTED-UNVERIFIED |

### 5.0 — qilinmagani
| Narsa | Holat |
|---|---|
| Dollar kursini internetdan olib 1C'ga O'ZI yozish | NOT-IMPLEMENTED (ataylab: kurs ko'rsatiladi, 1C'ga yozishni Admin tasdiqlaydi) |
| Mijozda savdo tarixini alohida SQLite bazaga ko'chirish (Agent/Admindagi `LedgerDb` kabi) | NOT-IMPLEMENTED (Mijozda ma'lumot bitta snapshot fayl, bosqichli sahifalash kerak emas) |
| Uch ilova uchun uchta mutlaqo boshqa maket (ekran tuzilishi) — hozircha rang/shrift/burchak/belgi farqlanadi, ekran tuzilishi o'xshash | NOT-IMPLEMENTED |


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

## 4.1 — Admin'da 1C'ning qolgan imkoniyatlari
| Narsa | Holat |
|---|---|
| Tarozi va ta'minotchi qoldig'i so'rovi ("Ожидается имя") — 1C kalit so'zi "В" alias sifatida ishlatilgan edi | IMPLEMENTED-UNVERIFIED (kompyuterda sinash kerak) |
| Ommaviy narx belgilash: guruh / oxirgi kirim / qo'lda tanlash, 1–4-narx, kelgan+summa, kelgan+foiz, hozirgidan foiz, aniq narx, yaxlitlash, ko'rib chiqish | VERIFIED (PriceRule testlari) / ekran IMPLEMENTED-UNVERIFIED |
| Yangi mijoz / ta'minotchi va kontragentni tahrirlash (nom, telefon, manzil, region; Клиенты/Поставщики) | VERIFIED (V41Test, PC V41Test) / 1C'da IMPLEMENTED-UNVERIFIED |
| Tovarni hisobdan chiqarish (СписанияТоваров): tannarx 1C qoldig'idan; dollarli tovarda 1C modulining xatosi chetlab o'tiladi (harakatlar dastur yozadi) | VERIFIED (PC testlari) / 1C'da IMPLEMENTED-UNVERIFIED |
| Hisobotlar: yalpi va sof foyda, tovar bo'yicha foyda, chegirma, xarajatlar, kassa qoldig'i va aylanmasi, ombor qoldig'i, qarzlar | VERIFIED (build_reports, OneCReportsCodec) / 1C'da IMPLEMENTED-UNVERIFIED |
| Yozuvlarni bekor qilish — kontragent va hisobdan chiqarish uchun ham | IMPLEMENTED-UNVERIFIED |
| 1C'da bir martalik sozlash (Модуль внешнего соединения) — xato matnida yo'riqnoma ko'rsatiladi | IMPLEMENTED-UNVERIFIED (OQING.txt, 4.1) |
| Ommaviy narx o'zgartirish — 4.0'da NOT-IMPLEMENTED edi, endi bor | VERIFIED |

## 4.0.2 — Admin'dagi savdo agentlarda darhol
| Narsa | Holat |
|---|---|
| Admin telefonida qilingan, hali 1C'ga yetmagan savdo/to'lov agentlarga "1C'ga ketmoqda" bilan boradi, 1C'dan kelgach almashadi | VERIFIED (PendingMerge testi) / telefonda IMPLEMENTED-UNVERIFIED |
| Admin tarixni 20 soniyada, agent 30 soniyada yangilaydi; agentda "Yangilash" tarixni ham darhol oladi | IMPLEMENTED-UNVERIFIED |

## 4.0.1 — Mijozda olingan tovarlar, statistika bazadan va kengroq
| Narsa | Holat |
|---|---|
| Mijoz tarixida savdoning tovarlari va Narxlar ro'yxati: kartochkadagi tovarsiz savdo o'rniga 1C hujjati (kompyuterdan, tovarlari bilan) | VERIFIED (V401GoodsTest) / telefonda IMPLEMENTED-UNVERIFIED |
| Yangi 1C savdolari kelganda Mijoz ma'lumoti qayta yuboriladi | IMPLEMENTED-UNVERIFIED |
| Statistika, Boss va kechki hisobot — faqat 1C bazasidagi yozuvlardan (telefondagi, 1C'ga yetmaganlar qo'shilmaydi) | IMPLEMENTED-UNVERIFIED |
| Ko'rsatkichlar (savdo, foyda, cheklar, o'rtacha chek, mijozlar, to'lov), mijozlar/tovarlar ulushi (halqa grafik), tez sotilayotgan (o'sish %), hafta kunlari, soatlar, to'xtab qolgan tovarlar | VERIFIED (mantiq testlari) / ko'rinishi IMPLEMENTED-UNVERIFIED |
| Mijoz build skripti (java.util.Base64 importi) | VERIFIED (GitHub build xatosi tuzatildi) |

## 4.0 — kalitsiz ishlash, o'zi yangilanish, yagona ro'yxat, Boss, statistika
| Narsa | Holat |
|---|---|
| Do'kon kodi (IMSHOP1) / Mijoz kodi (IMSDEV1, agentlar kalitisiz), yangilanish papkasi xeshi — Kotlin, Gradle va Python bir xil | VERIFIED (V40DeviceTest) |
| Qurilmalar: ro'yxatdan o'tish, Admin biriktiradi/bloklaydi, bloklangandan savdo qabul qilinmaydi, qoidalar v7 | IMPLEMENTED-UNVERIFIED (kompilyatsiya ✓; haqiqiy Firebase'da sinash kerak) |
| Eski usulda kirgan agent 4.0 ga o'tganda chiqarib yuborilmaydi (biriktirilmaguncha) | IMPLEMENTED-UNVERIFIED |
| Xato hisoboti (yiqilsa — keyingi ochilishda Admin ▸ Qurilmalar) | IMPLEMENTED-UNVERIFIED |
| O'zi yangilanish: build → Cloudflare yashirin papka → "O'rnatish" oynasi | IMPLEMENTED-UNVERIFIED (SHOP_CODE secret kerak) |
| Yagona savdo ro'yxati: telefondagi savdo "1C'ga ketmoqda" → 1C'dan kelgach almashadi | VERIFIED (PendingMergeTest) / ekranda IMPLEMENTED-UNVERIFIED |
| Savdodan keyin kompyuterdan bugungi savdolar so'raladi (2 va 6 daqiqa) | IMPLEMENTED-UNVERIFIED |
| Chek: Printer 1 (do'kon) / Printer 2 (filial) / Ikkalasi / Cheksiz; agent tanlovi Admin'ga boradi | IMPLEMENTED-UNVERIFIED |
| Mijoz: kalitsiz, filial printeri o'zi yoqiladi, sodda savdo (nasiyasiz), statistika va olgan tovarlar | IMPLEMENTED-UNVERIFIED |
| Statistika (kun/hafta/oy/yil, foyda, top tovarlar, narx o'zgarishi) | VERIFIED (StatsTest) / grafik telefonda IMPLEMENTED-UNVERIFIED |
| Boss ekrani, kechki hisobot, qarz yoshi, agentlar kunlik | IMPLEMENTED-UNVERIFIED |
| Kelgan narx = oxirgi kirim narxi (agentlarga ham), 4-narx taklifi (+2 000, o'zi qo'yilmaydi) | IMPLEMENTED-UNVERIFIED |
| Dollar kursi telefondan (Константы.Курс + Курс registri), bekor qilish | VERIFIED (soxta 1C testi) / haqiqiy 1C'da IMPLEMENTED-UNVERIFIED |
| Tugma yozuvlari (4 narx ko'rinmasligi, "Do…") — chekka 4dp, matn o'zi kichrayadi, shrift 1.15 dan oshmaydi | IMPLEMENTED-UNVERIFIED (telefonda ko'rish kerak) |
| Sozlamalar qaytadan (bo'limli) | IMPLEMENTED-UNVERIFIED |
| Build'da emulyatorda ochib tekshirish (smoke) | IMPLEMENTED-UNVERIFIED (birinchi build'da ko'rinadi) |
| iPhone havolasi (#c=...) bilan ulanish | VERIFIED (sintaksis, E2E) |
| Ommaviy narx o'zgartirish (guruhga %), tovarni hisobdan chiqarish | NOT-IMPLEMENTED (keyingi versiya; hisobdan chiqarishda 1C'ning o'z xatosi bor) |

## Mijoz 1.15
| Narsa | Holat |
|---|---|
| Ilova ochilishida yiqilish (pastki menyu 6 band) tuzatildi — 5 band, 5-o'rin almashadi | VERIFIED (sabab aniqlandi, kompilyatsiya ✓, tekshiruv qoidasi) / telefonda ochilishi IMPLEMENTED-UNVERIFIED |

## v3.12 — Shtrix kod → 1C, tez yozish, Boshqaruv'da hamma 1C yozuvlari, ikonkalar
| Narsa | Holat |
|---|---|
| "Shtrix kod → 1C": ko'p kod skanerlash/yozish, tovar tanlash, bitta tugma (Admin va Agent) | IMPLEMENTED-UNVERIFIED (kompilyatsiya ✓; telefonda sinash kerak) |
| Agent → Admin ro'yxat birdaniga (relay "bcs") | IMPLEMENTED-UNVERIFIED |
| Kompyuter: Firebase oqimi (SSE) — so'rov kelishi bilan darhol uyg'onadi | VERIFIED (oqim tahlili testi) / haqiqiy Firebase'da IMPLEMENTED-UNVERIFIED |
| Kompyuter: navbat bitta 1C ulanishida, hisobot tuzilayotganda ham orada bajariladi | VERIFIED (test_onec_write2: 1 ulanish, mavjud ulanish yopilmaydi) |
| Admin: natija 2.5 soniyada tekshiriladi (bitta so'rov bilan) | IMPLEMENTED-UNVERIFIED |
| Mijozdan to'lov (Оплата_Клента), kassa kirim/chiqim (КассаПриход/КассаРасход) | VERIFIED (soxta 1C testi, 1C proveden kodi bo'yicha) / haqiqiy 1C'da IMPLEMENTED-UNVERIFIED |
| Tovar kartochkasi: 1-4 narx, kirim narxi, PLU (band PLU tekshiriladi), nomi; bekor qilish eski qiymatni qaytaradi | VERIFIED (soxta 1C) / haqiqiy 1C'da IMPLEMENTED-UNVERIFIED |
| Yangi tovar (namuna tovardan nusxa, nom/shtrix dublikati tekshiriladi) | VERIFIED (soxta 1C) / haqiqiy 1C'da IMPLEMENTED-UNVERIFIED |
| Kassa moddalari ro'yxati kompyuterdan (Справочник.Операции) | VERIFIED (test) |
| Tovar o'zgargach kodlar/narxlar telefonlarga darhol | IMPLEMENTED-UNVERIFIED |
| Ikonkalar: Boshqaruv, ro'yxatlar, tugmalar (Admin, Agent, Mijoz) — Material Icons (Apache 2.0) | VERIFIED (kompilyatsiya) / ko'rinishi telefonda tekshiriladi |
| Tovarni hisobdan chiqarish (СписанияТоваров) | NOT-IMPLEMENTED — 1C'dagi o'tkazish kodida xato bor (Стр.ОстКол yo'q rekvizit), dollarli tovarda 1C o'zi xato beradi |

## v3.11
| Narsa | Holat |
|---|---|
| VIBRATE ruxsati qo'shildi (skanerda tebranish oldin jim ishlamas edi; lint xatosi) | VERIFIED (manifest, lint xatosi sababi) / tebranish telefonda IMPLEMENTED-UNVERIFIED |
| iPhone agent Cloudflare Pages'ga (zafar-savdo-agent.pages.dev) | VERIFIED (build #46 web bosqichi yashil) |
| Workflow: Node 24 versiyalari (checkout v5, setup-java v5, setup-android v4, upload v6, download v7) | IMPLEMENTED-UNVERIFIED (keyingi build) |

## v3.10 — iPhone agent (veb) va tarozi
| Narsa | Holat |
|---|---|
| Veb-agent server matnlari (tur 1, 4, 5) Android fixture'lari bilan bir xil | VERIFIED (`web/agent/test/core.test.js`, 7 test) |
| Kirish isboti va oid Kotlin bilan bir xil | VERIFIED (`WebAgentParityTest` + core.test.js) |
| Veb-agent: ulanish, kirish, katalog, 1-narx, savdo, qarz, to'lov, 1C tarixi, hisobot, internetsiz navbat | VERIFIED soxta Firebase/Admin bilan Chromium'da (`web/agent/test/e2e.js`) / haqiqiy iPhone Safari'da IMPLEMENTED-UNVERIFIED |
| GitHub Pages'ga joylash | IMPLEMENTED-UNVERIFIED (repo sozlamasiga bog'liq) |
| Admin: 1/2-narx cat/q | IMPLEMENTED-UNVERIFIED (kompilyatsiya ✓) |
| Tarozi: fayl formati 1C bilan bir xil, so'rov/natija | VERIFIED (`pc/test_onec_scale.py`, 5 test) / haqiqiy RONGTA tarozida IMPLEMENTED-UNVERIFIED |
| Admin "Taroziga yuborish" ekrani | IMPLEMENTED-UNVERIFIED (kompilyatsiya ✓) |

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


## 5.3.3 / pc 26 — shu seansdagi ishlar

| Ish | Holat |
|---|---|
| Kassa: narx turi har bir oynada alohida (1..4-oyna bir-biriga ta'sir qilmaydi), eski saqlangan savatlar ko'chadi | VERIFIED (jsdom sinovi: xato eski kodda takrorlandi, yangida yo'q; test_kassa* o'tdi) |
| Kassa: tugmalar tartibi (sudrab/▲▼), o'lcham, aniq kenglik va balandlik, nom/ikonka (Ikonka+nom / Nom / Ikonka, 31 ikonka), yashirish | VERIFIED (jsdom sinovi, 26 tekshiruv) / ko'rinishi monoblokda IMPLEMENTED-UNVERIFIED |
| Kassa: 10 ta mavzu (qorong'ilarida to'q fon, och yozuv va ikonka) va 20 ta rangni sozlash; yozuv/ikonka kontrasti hisoblab tekshirilgan | VERIFIED (kontrast hisobi, jsdom) / ko'rinishi monoblokda IMPLEMENTED-UNVERIFIED |
| Kassa: o'ng panel 470 dan 394 px ga (taxminan 2 sm), sozlanadi; uzun yorliq shrifti o'zi kichrayadi | IMPLEMENTED-UNVERIFIED (monoblokda ko'rinishi sinalmagan) |
| Admin ilova: Sozlamalar > Yorliqlar > Matnli etiketka (58x40, Arial 14, tarix, qayta chiqarish, tahrirlash, o'chirish) | IMPLEMENTED-UNVERIFIED (Android kompilyatsiyasi GitHub Actions'da; printerda sinalmagan) |
| Matnli etiketka mantiqi (TextLabels: matnni tozalash, nusxa soni, qisqa ko'rinish) | VERIFIED (kotlinc bilan ishga tushirildi) |
| Matnli etiketka: ekran matnlari ikkala tilda (lotin/kirill), format argumentlari mos | VERIFIED (skript) |
| Printerga ulanish (LAN/LPD/bulut, Tailscale) | O'zgartirilmagan: mavjud print() yo'lidan foydalaniladi |
| Telefondan 1C'ga yozish xatosi ("Вызванный объект был отключен от клиентов", COM uzilishi) | NOT-IMPLEMENTED (tekshirilmoqda) |

## pc 27 (2026-11-20)
| Funksiya | Holat |
|---|---|
| Kassada yangi mijoz: to'lovda "Mijozni tanlash" -> ismni yozish -> "+ Yangi mijoz" -> telefon (ixtiyoriy) -> 1C'ga yoziladi (Контрагенты, "Клиенты" guruhi) va darhol tanlanadi; bor nom qayta qo'shilmaydi | VERIFIED (test + brauzer) / 1C'da IMPLEMENTED-UNVERIFIED |
| Android lint: View.tooltipText (API 26) -> TooltipCompat | VERIFIED (kompilyatsiya; lint CI'da) |


## pc 28 + ilova 5.3.4 (versionCode 54) (2026-11-20)
| Funksiya | Holat |
|---|---|
| Agent yorlig'i (TSPL) kompyuterdagi Xprinter'ga to'g'ridan-to'g'ri: kompyuter ko'prigi `lb` navbatini oladi (avval o'chiradi, keyin chop etadi — ikki marta chiqmaydi), natijani `lbr` ga yozadi | VERIFIED (test_relay) / printerda IMPLEMENTED-UNVERIFIED |
| Printer tanlash: Admin ilovasidagi ulashish nomi (`lq`, standart "xprinter") -> printer nomi -> Xprinter/label nomli birinchi printer | VERIFIED (test) |
| Kompyuter holatida `lb:1` — Admin ilovasi (5.3.4) yorliqni o'zi chiqarmaydi; printer topilmasa yoki eski kompyuter dasturi bo'lsa Admin avvalgidek chiqaradi | VERIFIED (V53Test) |
| Eski Admin ilovasi (5.3.3) bilan bir vaqtda ishlasa juda kam holda bitta yorliq ikki marta chiqishi mumkin — ilovani 5.3.4 ga yangilang | IMPLEMENTED-UNVERIFIED |

## pc 29 (2026-11-20) — sensor kassa sozlamalari
| Funksiya | Holat |
|---|---|
| O'ng tugmalar: aniq kenglik (1/3, 1/2, 2/3, To'liq) va «Yangi qatordan»; bo'sh joy kengligi qo'lda berilmagan tugmalarga bo'linadi | VERIFIED (brauzer, Playwright) |
| Bir tugma bilan: «Panel va Qidiruv — TO'LOV tepasida yonma-yon (teng ikkiga)» | VERIFIED (brauzer) |
| Har bir tugma (o'ng va savat ostidagi): yozuv o'lchami (px, ikonka ham mos kattalashadi) va qalinligi (Asl / Qalin / Juda qalin) | VERIFIED (brauzer) |
| Hamma tugmalar yozuvi (70–160%), qalinligi; tugma va panel burchagi (0–40 px) | VERIFIED (brauzer) |
| Sozlamalar tugmasidagi qizil nuqta: panel tavsiyasi sozlamalar ochilganda «ko'rildi» bo'ladi; 1C'ga yozilmagan sotuv bo'lsa sozlamalar tepasida qizil karta (sababi va «Navbatni ko'rish») | VERIFIED (brauzer) |
| Narx turi 2 rejim: «Tugma bilan» (avvalgidek) yoki «Barmoqlar bilan» — ro'yxat ustida N barmoq bilan 3 sm pastga surish = Narx N (surish ko'rsatkichi bilan) | VERIFIED (Chromium'da soxta ko'p barmoqli teginish) / monoblok ekranida IMPLEMENTED-UNVERIFIED |
| Android lint: ExifInterface(InputStream) va deleteSharedPreferences faqat API 24+ da (Android 6 da xavfsiz o'rinbosar) | VERIFIED (kompilyatsiya; lint CI'da) |

## pc 30 (2026-11-20) — telefondan 1C'ga yozish kassa ko'prigi orqali
| Funksiya | Holat |
|---|---|
| Sabab: bu kompyuterda yashirin 1C (V83.Application) ochilgach darhol yopiladi («Сбой при удаленном вызове процедуры», «Вызванный объект был отключен от клиентов») — telefon yozuvlari (shtrix kod, kassa paneli, ko'p tovarga birdan, kirim, to'lov ...) shu sababli ishlamagan | VERIFIED (xato matnlari va kod tahlili) |
| Endi «oyna orqali» yozuvlar ochiq turgan kassa ko'prigi 1C'ida bajariladi: har amal *.rpq (JSON) -> 1C -> *.rps; obyektlar raqam bilan saqlanadi; alohida 1C oynasi ochilmaydi (1C seanslari ko'paymaydi) | VERIFIED (test_onec_bridge: soxta 1C bilan shtrix kod yozuvi to'liq) / haqiqiy 1C'da IMPLEMENTED-UNVERIFIED |
| Ko'prik versiyasi 6: yangilangach eski ko'prik 1C o'zi yopilib, yangisi ochiladi; RPC paytida ham kassa sotuvlari kutib qolmaydi (so'rovlar orasida bajariladi) | IMPLEMENTED-UNVERIFIED |

## pc 31 (2026-11-20) — sensor kassa to'lovi
| Funksiya | Holat |
|---|---|
| TO'LOV o'rniga ikki katta tugma: CHEKLI va CHEKSIZ. Bir bosish — darhol naqd savdo (chek bilan / chek siz), uzoq bosish (0,6 s) — to'lov oynasi; CHEKLI'dan kirilsa «Chek chiqarish» belgilangan, CHEKSIZ'dan — belgisiz | VERIFIED (brauzer, Playwright) |
| Klaviatura/skaner Enter'i tugmani «bosmaydi» (tasodifiy savdo bo'lmaydi); savdo ketayotganda ikkinchi bosish e'tiborsiz | VERIFIED (kod; brauzer) |
| Nasiyada: mijozning hozirgi qarzi va savdodan keyingi jami qarzi to'lov oynasida | VERIFIED (brauzer) |
| Ro'yxatdagi tovarlar yirikroq (21 px); sozlamada «Ro'yxat yozuvi» va «Ro'yxat qatori balandligi» | VERIFIED (brauzer) |

## pc 32 (2026-11-20) — kassa sozlamalari tartibi va mavzular
| Funksiya | Holat |
|---|---|
| Sozlamalarga parolsiz kirish (standart yoqiq; «Asosiy» bo'limida o'chirilsa — admin paroli) | VERIFIED (test_kassa, brauzer) |
| Narx turi sichqoncha bilan: ro'yxatni bosib pastga tortish, har 3 sm — keyingi narx turi, qo'yib yuborilganda o'zgaradi (barmoqlar rejimi yoqiq bo'lsa) | VERIFIED (brauzer) |
| To'lov tugmalari: «TO'LOV» yozuvi + belgi (chek / chek siz); qorong'i mavzuda ikkinchisi ham ajralib turadi | VERIFIED (brauzer) |
| Sozlamalar 5 bo'limga: Asosiy, Ko'rinish, Tugmalar, 1C va dastur, Dasturchi; har tugma sozlamasi yig'iq — «Sozlash» bosilganda ochiladi | VERIFIED (brauzer) |
| Savat ostidagi tugmalar har biri o'z rangida (mavzuga moslanadi; muharrirda alohida o'zgartirish mumkin) | VERIFIED (brauzer) |
| 10 mavzu qayta chizildi (5 yorug': Oq, Dengiz, Issiq krem, Lavanda, Anor; 5 qorong'i: Grafit, Tungi ko'k, Zumrad, Qahva, Kontrast), tanlashda kichik namuna | VERIFIED (brauzer) |
| Ranglarda alohida: ro'yxat (savat) foni, o'ng tugmalar foni va yozuvi | VERIFIED (brauzer) |
| Dasturchi rejimi (yoqilsa — texnik ma'lumot; qo'shimchalar keyin) | VERIFIED (brauzer) |

## pc 33 (2026-11-20)
| Funksiya | Holat |
|---|---|
| «Qarz to'lash» tugmasi olib tashlandi: savat bo'sh bo'lsa TO'LOV (bir yoki uzoq bosish) qarz to'lash oynasini ochadi (mijoz tanlash bilan) | VERIFIED (brauzer) |
| Mijoz qarzi: kassada endi UMUMIY qarz (hamma xodim/agentlar bo'yicha, telefondagi hisobot bilan bir xil); avval faqat «Дукон» bo'yicha edi | VERIFIED (test) / haqiqiy 1C'da IMPLEMENTED-UNVERIFIED |
| Oyna tugmalarida narx turi belgisi N1..N4 (har biri o'z rangida); bitta oyna bo'lsa yuqorida «Narx N» | VERIFIED (brauzer) |
| «Kassadan chiqish» tugmasi olib tashlandi (kassani yopish — Sozlamalar → 1C va dastur) | VERIFIED (brauzer) |
| Dasturchi rejimida: mijozlar soni, qarzdorlar va haqdorlar soni | VERIFIED (brauzer) |

## pc 34 (2026-11-20)
| Funksiya | Holat |
|---|---|
| Telefon yozuvlari ko'prik orqali haqiqiy 1C'da ishladi (shtrix kodlar, kassa paneli — «Kiritildi 4») | VERIFIED (foydalanuvchi telefonida) |
| «Ko'p tovarga birdan» 1561-amalda «Ошибка совместного доступа к файлу ...rpq»: ko'prik v7 — so'rov fayli band bo'lsa javob bermay 30 soniyagacha qayta o'qiydi (amal bajarilmagan, ikki marta bajarilmaydi) | IMPLEMENTED-UNVERIFIED |
| Kassa ekrani ikki barmoq / Ctrl+g'ildirak / Ctrl +/- bilan kattalashmaydi | VERIFIED (CSS va hodisalar, brauzer) |
| Tugmalar uchun 91 ta ikonka (strelkalarning 14 turi va boshqalar), keng tanlash oynasi | VERIFIED (brauzer) |

## pc 35 + ilova 5.3.5 (versionCode 55) (2026-11-20)
| Funksiya | Holat |
|---|---|
| Admin telefoni Tailscale'siz Xprinter'ga: to'g'ridan-to'g'ri ulanmasa (3 soniya) — internet orqali kompyuter ko'prigiga (lb navbati), kompyuter o'zi chop etadi; tushunarli xato matni | VERIFIED (kompilyatsiya, 330 test) / telefonda IMPLEMENTED-UNVERIFIED |
| Kassada mijoz qarzi: kod bo'yicha, topilmasa nom bo'yicha moslash; bir nechta qator qo'shiladi; dasturchi rejimida «1C'dagi qarz qatorlari / mos keldi» | VERIFIED (test_kassa) / haqiqiy 1C'da IMPLEMENTED-UNVERIFIED |
| gen_strings.py strings.xml bilan sinxron (matnli yorliq matnlari generatorga ko'chirildi) | VERIFIED (generator natijasi avvalgisi bilan bir xil) |

## 5.3.9 (versionCode 59) + pc 44 (2026-11-21)
| Funksiya | Holat |
|---|---|
| Rasm orqali kirim — BEPUL yo'l: Google Gemini kaliti (aistudio.google.com, "AIza…"), standart model gemini-2.5-flash, topilmasa zaxira gemini-3.1-flash-lite; Anthropic kaliti ("sk-ant-…") bo'lsa — Claude. Kalit turiga qarab o'zi tanlaydi | VERIFIED (unit test: so'rov/javob shakli) / haqiqiy kalit bilan IMPLEMENTED-UNVERIFIED |
| Kassa: savdolar ro'yxati ixchamroq (shrift va tugmalar kichikroq) — bir ekranda ko'proq savdo | VERIFIED (kod) |

## 5.3.8 (versionCode 58) — rasm orqali kirim (2026-11-21)
| Funksiya | Holat |
|---|---|
| Kirim shaklida «Rasmdan to'ldirish»: kamera/galereya → rasmni sun'iy intellekt (Anthropic Claude, Admin kiritgan API kalit) o'qiydi: yuk beruvchi, sana, valyuta, jami, har qator (nom, miqdor, birlik, narx, summa, rasmdagi o'rni), burilgan rasm, qo'lyozma, kirill/lotin, chizib tashlangan qatorlar | IMPLEMENTED-UNVERIFIED (javobni o'qish — unit test; haqiqiy rasm va kalit bilan sinalmagan) |
| Har qator tovarga moslanadi: avval o'rganilganlar (aynan yoki o'xshash yozuv, shu yuk beruvchiniki ustun), keyin katalog nomlari (kirill=lotin, q~k, x~h, 1 harf xato); belgisi: o'rganilgan / o'xshash % / taxmin % | VERIFIED (unit test) |
| Ro'yxatda nom, miqdor, narxni o'zgartirish; har qatorni bosganda rasmning aynan shu qatori kesib ko'rsatiladi (butun rasm, burish); qo'lda qator qo'shish, uzoq bosib o'chirish; bir nechta rasm (uzun ro'yxat) | IMPLEMENTED-UNVERIFIED (qurilmada sinalmagan) |
| Ogohlantirishlar: qator summasi rasmdagidan farq qiladi, rasmdagi jami bilan farq, oxirgi kirim narxidan 25% dan ko'p farq | VERIFIED (unit test: narx sakrashi) / ekran IMPLEMENTED-UNVERIFIED |
| O'zini o'zi o'rgatish: «Kirimga o'tkazish»da yozuv→tovar, yuk beruvchi sarlavhasi→1C kontragenti, blok→dona ko'paytiruvchisi (masalan ×12), oxirgi narx eslab qolinadi; keyingi safar o'zi tanlaydi | VERIFIED (unit test) |
| Kamera ilovani yopib yuborsa ham o'qilgan ro'yxat yo'qolmaydi (24 soat) | IMPLEMENTED-UNVERIFIED |

## pc 43 (2026-11-21)
| Funksiya | Holat |
|---|---|
| Savatda nom kengroq: soni/narxi/summasi ustunlari torroq va shrifti sal kichik; soni doim 3 xonali ("1.000", "0.345") | VERIFIED (brauzer) |
| Ekran klaviaturasida katta/kichik harf: «⇧» — bir marta / doim / o'chiq; matn yozishda birinchi harf katta | VERIFIED (brauzer: "Zafar") |
| Vizitka (Xprinter): QR oynasida va sozlamada «Vizitka chiqarish»; har qator eniga sig'adigan eng katta qalin shriftda (Pillow bo'lsa TrueType, bo'lmasa printer shrifti); matn va yorliq o'lchami sozlamada | VERIFIED (test_kassa, rasm) / Xprinter'da IMPLEMENTED-UNVERIFIED |
| Chegirmasiz — pastda bitta katta qator: chapda «JAMI:», o'ngda summa | VERIFIED (brauzer) |
| Savdolar ikki marta chiqishi tuzatildi (kassa raqami "34 131" va 1C "34131" bir xil deb olinadi); 1C'da topilmagani belgilanadi | VERIFIED (test_kassa) |
| Savdoni o'chirish: kartani 5 soniya bosib turish → ogohlantirish → 1C hujjatiga o'chirish belgisi (qoldiq/qarz qaytadi, tiklash mumkin) | VERIFIED (test_kassa) / haqiqiy 1C IMPLEMENTED-UNVERIFIED |

## pc 42 (2026-11-21)
| Funksiya | Holat |
|---|---|
| **Mijoz qarzi ko'rinmasligining sababi topildi va tuzatildi:** kassa qarz so'rovlarida taxallus «КАК В» — «В» 1C so'rov tilida kalit so'z (IN), so'rov «Ожидается имя» xatosi bilan yiqilardi (foydalanuvchi rasmi). Taxallus «Вл» (telefon hisobotidagi kabi); kalit so'z taxallus bo'lmasligi testda | VERIFIED (test_kassa, foydalanuvchi xato matni) / haqiqiy 1C IMPLEMENTED-UNVERIFIED |
| Kompyuter klaviaturasida narx turi: F1–F4 (yoki Alt+1…4), PageDown/PageUp — keyingi/oldingi | VERIFIED (brauzer) |

## pc 41 (2026-11-21)
| Funksiya | Holat |
|---|---|
| Mijoz tanlanganda qarzi 1C'dan jonli (jami, barcha xodimlar bo'yicha); ro'yxatdagi qarz ham yangilanadi; olinmasa sababi shu yerda yoziladi | VERIFIED (test_kassa, brauzer demo) / haqiqiy 1C IMPLEMENTED-UNVERIFIED |
| Panel nomlari 1C'dagi kabi (katta harfga o'tkazilmaydi) | VERIFIED (brauzer) |
| Yuqorida (dasturchi almashtirgichi va oynalar orasida) QR tugmasi: to'lov QR kodi (Paynet), karta raqami, egasi ismi; sozlamada o'zgartiriladi; QR internetsiz chiziladi (qrcode.js, MIT) | VERIFIED (brauzer; chizilgan QR qayta o'qilib asl matnga teng) |
| Qo'lda o'zgartirilgan narx — tagiga chizilmaydi, faqat sal boshqa fon | VERIFIED (kod) |
| Oyna yozuvini uzoq bosish — ro'yxatni boshqa oynaga ko'chirish (u yerdagilar ostiga qo'shiladi, bu oyna bo'shaydi) | VERIFIED (brauzer) |
| Panel tavsiyasi: qo'yilgan tugmalar o'zgarmaydi — faqat bo'sh joylarga; tarozi (kg), zavod shtrix kodi yo'q (1C o'zi yaratgan / shtrixsiz) tovarlar, sotuv + qidiruvda tanlanish bo'yicha; tasdiqlashda keyin qo'lda qo'yilganlar ham saqlanadi | VERIFIED (test_kassa, test_kassa_panel) |
| Panel 8 sahifa tugmasi — pastda, eniga teng | VERIFIED (brauzer: 8 × 164 px) |

## pc 39 + 5.3.7 (2026-11-21)
| Funksiya | Holat |
|---|---|
| Panel yozuvlari ingichkaroq (16 px, 800) — sig'adi | VERIFIED (brauzer, rasm) |
| (pc 40) Qidiruv va boshqa joylardagi klaviatura ixchamroq: eni ko'pi bilan 860 px, ekran o'rtasida | VERIFIED (brauzer) |
| Oynalardagi N1 belgisi olib tashlandi; narx turi raqam maydoni boshida katta (30 px) rangli raqam | VERIFIED (brauzer) |
| Savatda uzun nom — «...» o'rniga kichikroq shrift bilan 2 qator (sig'masa avtomatik) | VERIFIED (kod; demo nomlar sig'adi) |
| Savatda Summadan keyin tor «PLU» ustuni (4 xonali son); nom oldidagi «PLU 412» olib tashlandi | VERIFIED (brauzer) |
| Savat ostidagi tugmalar teng kvadrat: eni = savat eni / tugmalar soni, bo'yi = eni (8 tugma × 111 px); sozlamada o'chirsa bo'ladi | VERIFIED (brauzer: 948 px / 8) |
| Savdolar ro'yxati: kimga, summa, vaqt — qalin; qolgani oddiy | VERIFIED (brauzer) |
| Kassa savdosi kompyuterda (1C'da) o'zgartirilsa — ro'yxatda 1C'dagi hozirgi holati («1C'da o'zgartirilgan») | VERIFIED (test_kassa) / haqiqiy 1C IMPLEMENTED-UNVERIFIED |
| «Takrorlash» — o'sha tovarlar, o'sha miqdor, hozirgi narx bilan bo'sh oynaga | VERIFIED (brauzer) |
| Dasturchi rejimida «Tahrirlash» — savdo oynaga asl narxda ochiladi, «SAQLASH» 1C hujjatini (ko'prik 1C'i orqali) yangilaydi va qayta o'tkazadi; mijoz, sana, kassir o'zgarmaydi | VERIFIED (test_kassa, brauzer demo) / haqiqiy 1C IMPLEMENTED-UNVERIFIED |
| Mijoz qarzi: zaxira so'rov (telefon hisobotidagi kabi, nom bo'yicha), qarz so'rovi xatosi va namunasi dasturchi rejimidagi «Texnik ma'lumot»da | VERIFIED (test_kassa) / haqiqiy 1C'da sababi IMPLEMENTED-UNVERIFIED |
| Yorliq (Android 5.3.7): Tailscale manzilida avval kompyuter ko'prigi orqali; agent xatosi «LPD» deb emas, aniq sababi bilan; «Kutilmagan xatolik» o'rniga xato turi | VERIFIED (unit test) / Xprinter IMPLEMENTED-UNVERIFIED |

## pc 38 (2026-11-20)
| Funksiya | Holat |
|---|---|
| **Monoblok sensori va kompyuterdagi kassa jonli bir xil:** savatlar, tanlangan oyna, narx turi, sotuvdan keyin tozalanish — biri o'zgartirsa ikkinchisida darhol (kutib turgan so'rov, ~0,15 soniya); sozlamalar ham. Ikkalasi bir xil manzilda (`?k=` bir xil yoki yo'q) bo'lishi kerak. To'lov oynasi ochiq bo'lsa, boshqa ekrandagi o'zgarish to'lovdan keyin keladi. Ikkala ekranda bir vaqtda o'zgartirilsa — oxirgisi qoladi | VERIFIED (test_kassa, ikki brauzer: A→B 144 ms, B→A 145 ms, oyna almashishi 131 ms) / monoblokda IMPLEMENTED-UNVERIFIED |
| **Tarozi PLU** savatda, qidiruvda, panelda, panel tavsiyasida, narx tekshirishda kichik belgi bilan — 1C'dan «tovar kodlari» (codes.json, har 6 soatda) orqali; PLU'si yo'q tovarda ko'rinmaydi | VERIFIED (test_kassa, brauzer) / haqiqiy 1C PLU manbasi topilishi IMPLEMENTED-UNVERIFIED |
| **Panel va panel tavsiyasi:** shaxmat taxtasidek (katakma-katak rang), yozuv o'rtada, yirikroq va qalinroq | VERIFIED (brauzer, rasm) |

## pc 36 — dasturchi rejimi (2026-11-20)
| Funksiya | Holat |
|---|---|
| Kassa yuqori qatorida, oynalar chap tomonida yozuvsiz almashtirgich — dasturchi rejimini yoqadi/o'chiradi (admin ruxsati; parolsiz kirish yoqiq bo'lsa parol so'ramaydi), qayta ochilganda saqlanadi | VERIFIED (brauzer, demo server) |
| (pc 37) Narx turi o'zgarganda pastdagi «Narx N» ogohlantirishi olib tashlandi (summani to'sardi); qaysi narx ekani oyna yozuvidagi N belgisida | VERIFIED (kod) |
| Dasturchi rejimida bazada yo'q shtrix kod skanerlansa: biriktirish oynasi — avval shu kodga yaqin tovarlar (mavjud shtrix kodlar bilan umumiy boshlanishi eng uzun, so'ng son jihatdan yaqin), nom yozilsa istalgan tovar; tanlab tasdiqlansa kod kassada darhol ishlaydi va 1C'ga telefon yozuvlari navbati («bc») orqali yoziladi; boshqa tovardagi kod rad etiladi; tarozi kodlari bu oynaga tushmaydi | VERIFIED (test_kassa, brauzer: biriktirildi, qayta skanda topildi) / haqiqiy 1C'ga yozilishi IMPLEMENTED-UNVERIFIED |

## 5.3.6 (versionCode 56) + Savdo Mijoz 5.4 (versionCode 32) + iPhone (veb) agent va mijoz — 2026-11-20
| Funksiya | Holat |
|---|---|
| **iPhone Mijoz (veb-ilova)** `web/mijoz` → `https://<sayt>/mijoz/`: do'kon kodi + telefon + parol bilan kirish (Android bilan bir xil shifrlangan yozuv), qarz, narxlar (Optom/Naqd/1-2-narx, $), guruhlar, qidiruv, savatcha va buyurtma (do'kon ruxsat bersa), buyurtmalar holati, hisob-kitob (kunlar bo'yicha, hujjat qatorlari), «Yangilash» (do'kondan yangi ma'lumot so'rash), internetsiz oxirgi ma'lumot, bosh ekranga o'rnatish | VERIFIED (mcore 6 test + Chromium E2E 14 tekshiruv) / iPhone Safari'da IMPLEMENTED-UNVERIFIED |
| Admin ▸ Mijoz ▸ telefon «Kirish ma'lumoti» matnida iPhone havolasi (do'kon kodi havolada — o'zi yoziladi) | VERIFIED (kompilyatsiya) |
| Sayt: CI `web/mijoz` ni Cloudflare Pages'dagi agent saytining `/mijoz/` qismiga joylaydi; ikki ilovaning keshi bir-birini o'chirmaydi | VERIFIED (workflow, ASCII) / joylash CI'da |
| **iPhone agent ilovaga o'xshash:** emoji o'rniga ilovadagi kabi chiziqli ikonkalar, plitkalar | VERIFIED (agent E2E o'tdi, rasm) |
| **Xodim (agent) soddalashtirildi:** shtrix kod partiyasi faqat Adminda (menyu va tugma), holat (diagnostika) kartasi xodimga faqat muammo bo'lsa ko'rinadi | VERIFIED (kompilyatsiya, 330 test) |
| **Savdo Mijoz (Android) yangi bosh sahifa:** hamma amallar yirik ikonkali plitkalarda (3 ustun) — ikki tanlanadigan tugma va «uzoq bosing» izohi o'rniga | VERIFIED (kompilyatsiya) / telefonda IMPLEMENTED-UNVERIFIED |
| Statistika (kun/hafta/oy/yil, eng ko'p sotilgan, foyda, mijozlar; mijoz bilan / mijozsiz) | VERIFIED (avvaldan bor: StatsFragment, `StatsClientsTest`) |
| iPhone Mijoz'da yo'q (faqat Android): mijozning o'z kassasi (POS), chek printeri, vidjet, statistika | NOT-IMPLEMENTED (iPhone brauzeri USB/Bluetooth printer va vidjetni qo'llamaydi) |
