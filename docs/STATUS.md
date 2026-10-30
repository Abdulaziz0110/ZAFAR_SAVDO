# Holat — Savdo Agent 2.7 (versionCode 9) + Savdo Mijoz 1.3 (versionCode 4), 2026-10-30

Belgilar:
- **VERIFIED** — shu muhitda kompilyatsiya qilindi va testdan o'tdi (yoki mustaqil vosita bilan tekshirildi).
- **IMPLEMENTED-UNVERIFIED** — kod yozilgan va kompilyatsiya bo'ladi, lekin haqiqiy qurilma/printer/1C'da sinalmagan.
- **NOT-IMPLEMENTED** — qilinmagan.

## Narx yorliqlari (1-ilova)
| Narsa | Holat |
|---|---|
| Tovar tanlash, nusxa soni, Naqd/Optom yoki qo'lda narx, filtrlar, skaner (kamera va ulanadigan) | IMPLEMENTED-UNVERIFIED (kompilyatsiya ✓, telefonda sinalmagan) |
| "PLU narx" va "Shtrixli narx" yorliq rasmi (1C shakllari kabi) | VERIFIED (Python nusxasi bilan 4 o'lchamda chizildi, shtrix kodlar zxing-cpp bilan o'qildi) |
| EAN-13 / EAN-8 / Code 128 kodlash | VERIFIED (`LabelsTest`, mustaqil dekoder) |
| Telefon ↔ `label_printer.py` protokoli (Wi-Fi TCP, takror chop etmaslik, kalit) | VERIFIED (Kotlin klient JVM'da ↔ Python server, file rejimi) |
| Internet orqali navbat (Firebase `L/`), holat (`P/`) | VERIFIED (soxta bulut bilan, `test_label_printer.py`) |
| Haqiqiy Xprinter'da chop etish (`gdi` va `tspl`) | IMPLEMENTED-UNVERIFIED (Windows va printer kerak) |
| UDP orqali kompyuterni topish | VERIFIED (localhost), haqiqiy Wi-Fi'da IMPLEMENTED-UNVERIFIED |

## Tovar kodlari: PLU va shtrix 1C'dan
| Narsa | Holat |
|---|---|
| `onec_export.py --codes`: manbani avto-topish, so'rov, birlashtirish, faqat o'zgarganda yuborish, telefon so'rovi | VERIFIED (soxta metama'lumot/COM bilan, 16/16) |
| Do'kon 1C bazasida PLU haqiqiy joyi | IMPLEMENTED-UNVERIFIED — `--codes` natijasi kerak; topilmasa `--discover` |
| 1-ilova: qabul qilish, katalogga bog'lash, "Kod · PLU" ro'yxatlarda | VERIFIED (format testi, Python→Kotlin) / ekran IMPLEMENTED-UNVERIFIED |
| 2-ilova: Narxlar'da "Kod · PLU", PLU bo'yicha qidirish, eski format bilan moslik | VERIFIED (`snapshotCarriesPlu`), ekran IMPLEMENTED-UNVERIFIED |

## Savdo Mijoz: "Qo'shimcha sozlamalar"
| Narsa | Holat |
|---|---|
| Kalit/ulanish/texnik bandlar olib tashlandi; ochilish sahifasi, "Ekran o'chmasin" | IMPLEMENTED-UNVERIFIED (kompilyatsiya ✓) |

## Qilinmagan
| Narsa | Holat |
|---|---|
| Telefondan to'g'ridan-to'g'ri printerga (kompyutersiz) yorliq | NOT-IMPLEMENTED (printer USB'da kompyuterga ulangan) |
| 1C ga PLU/shtrix yozish | NOT-IMPLEMENTED (ataylab: 1C faqat o'qiladi) |

## Testlar
- JUnit (`app` + `shared`): 145/145; `mijoz` kompilyatsiyasi: 0 xato.
- `pc/test_onec_export.py` 16/16, `pc/test_label_printer.py` 18/18, `pc/test_forwarder.py` 8/8.
- Satrlar (lotin + kirill), resurs havolalari, format argumentlari: xatosiz.
- Haqiqiy Gradle build (AAPT2, lint) — GitHub Actions'da (`android-build.yml`).
