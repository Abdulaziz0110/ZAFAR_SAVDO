# Holat — Savdo (bitta ilova: Admin/xodim) 5.3 (versionCode 49) + kompyuter dasturi 5.4 (pc 5) + Savdo Mijoz 5.3 (versionCode 31), 2026-11-15

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
| Kassada yangi mijoz qo'shish, kunni yopish (ЗакрытяСмены), narxni qo'lda o'zgartirish | NOT-IMPLEMENTED |

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
| Agent yorliq chop etishi (lb navbati) — hali faqat Admin telefoni orqali | NOT-IMPLEMENTED (kompyuterga ko'chirilmagan) |

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
| Xodim (agent) ko'rinishini alohida soddalashtirish, statistika, Mijoz ilovasi dizayni, veb-agentni ilova bilan bir xil qilish | NOT-IMPLEMENTED (keyingi qism) |
| Sensor savdo oynasi (monoblok) | NOT-IMPLEMENTED (oxirgi bosqich) |

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
| **1C'ga yoziladigan shakllar 1C bilan to'liq bir xil** (yangi tovar: guruh, brend, o'lchov birligi, hajmi, shtrix/tarozi turi, avtomatik shtrix; kirim; qolgan hujjatlar) | NOT-IMPLEMENTED — keyingi ish. Hozircha faqat yuk beruvchi qarzi qo'shildi; to'liq moslash uchun 1C'dan guruh/brend/o'lchov/tarozi ro'yxatlarini kompyuter orqali yuborish va `onec_write.py` ni kengaytirish kerak |

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
