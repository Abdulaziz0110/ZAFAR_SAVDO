# Phase 1 · 1-qadam hisoboti — CI, unit test infratuzilmasi, protokol fixture'lari

Sana: 2026-10-26. 2–6-qadamlarga tegilmadi. `app/src/main` (ilova kodi) **o'zgarmagan**.

## Maqsad
CI'da build yashil bo'lishi, testlar uchun asos, va Money/outbox'ga tegishdan oldin protokol formatini qotirib qo'yish (P10, C1).

## O'zgargan fayllar
| Fayl | O'zgarish |
|---|---|
| `gradle/libs.versions.toml` | `junit = "4.13.2"` |
| `app/build.gradle.kts` | `testImplementation(libs.junit)` |
| `.github/workflows/build.yml` | push/PR, wrapper-validation, `chmod +x`, `testDebugUnitTest` → `assembleDebug`, test hisoboti artefakti |
| `app/src/test/java/.../testutil/Fixtures.kt` | fixture o'qish, locale/vaqt zonasi almashtirish |
| `app/src/test/java/.../data/remote/ProtocolCases.kt` | 26 ta protokol holati |
| `app/src/test/java/.../data/remote/ProtocolFixtureTest.kt` | bayt-bayt + tuzilma testlari |
| `app/src/test/java/.../core/MoneyFixtureTest.kt` | 29 ta Money holati |
| `app/src/test/resources/fixtures/{protocol,money}.txt` | fixture'lar |
| `README.md`, `CHANGELOG.md`, `DECISIONS.md`, `docs/PHASE1_STEP1.md` | hujjatlar |

## Nima tekshirildi (bu muhitda)
* Test kodi + `Money`, `Translit`, `Models`, `Pricing`, `Protocol` — Kotlin 2.0.21 kompilyatori bilan, `-Werror` (ogohlantirish = xato): **0 xato**.
* JUnit 4.13.2 bilan: **30/30 test o'tdi** (6 test metodi × 5 locale).
* Testlar haqiqatan xatoni ushlashi tekshirildi (mutatsiya): `Money.qty` dan ".0" olib tashlansa va vaqt arabcha raqam bilan yozilsa — testlar **yiqildi** ✓. Mutatsiyalar loyihaga kirmagan.
* Statik resurs tekshiruvi: XML va Kotlin'dagi barcha `@string/@drawable/@id/R.*` havolalari mavjud; lotin/kirill matn kalitlari va `%1$s` argumentlari mos (yagona "topilmagan" — `appbar_scrolling_view_behavior`, u Material kutubxonasida bor).

## Nima tekshirilMAdi (yashirmasdan)
* **To'liq Gradle build va GitHub Actions bu yerda ishga tushirilmadi**: muhitda Google Maven / Gradle / Maven Central tarmoq siyosati bilan yopiq (403), GitHub akkaunt ulanmagan. AAPT2, Android'ga bog'liq Kotlin fayllar kompilyatsiyasi, `assembleDebug` — hali **noma'lum**.
* Shuning uchun 1-qadam "tayyor" deb belgilanMAYDI: CI birinchi marta yashil bo'lgandan keyingina.

## Risklar
* Birinchi CI ishga tushishida v2.2 ning o'zidagi (hech qachon build qilinmagan) xatolar chiqishi mumkin — bu kutilgan; log bo'yicha tuzatiladi (faqat build xatolari, xatti-harakat o'zgarmaydi).
* Fixture = hozirgi chiqish (golden master). Eski ilovadan farq bo'lsa (masalan, vergulli summa — audit L-2), fixture uni ko'rmaydi.

## Rollback
`git revert` shu commit; yoki `app/src/test/` papkasini, `junit` qatorlarini o'chirish va eski `build.yml` ni qaytarish. Ilova xatti-harakatiga ta'sir yo'q.

## Acceptance / Definition of Done
- [x] Protokol fixture'lari (tur 1, 3, 4, 5) bayt-bayt, ikkala o'zbek locale'da (+3) — lokal o'tdi
- [x] Money characterization fixture'lari — lokal o'tdi
- [x] Testlar xatoni ushlashi isbotlangan (mutatsiya)
- [ ] GitHub Actions: `testDebugUnitTest` yashil
- [ ] GitHub Actions: `assembleDebug` yashil
