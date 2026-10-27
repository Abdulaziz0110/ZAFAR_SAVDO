# CHANGELOG

## [Unreleased] — Phase 1, 1-qadam (2026-10-26)
Ilova kodi va xatti-harakati **o'zgarmadi** (`app/src/main` ga tegilmagan). Faqat build/test infratuzilmasi.

### Qo'shildi
* JUnit 4.13.2 (`testImplementation`, APK'ga kirmaydi).
* `app/src/test/.../data/remote/ProtocolFixtureTest.kt` — tur 1/3/4/5 matnlari fixture bilan bayt-bayt, 5 ta locale'da; tur 1 (12 maydon) va tur 4 (11 maydon, 4-maydon "0", 3=8) tuzilma testlari.
* `app/src/test/.../core/MoneyFixtureTest.kt` — `qty/plain/display/format/parse/round2` characterization testlari (P10).
* `app/src/test/resources/fixtures/protocol.txt`, `money.txt` — 26 + 29 ta fixture.
* `DECISIONS.md`, `CHANGELOG.md`, `docs/PHASE1_STEP1.md`.

### O'zgardi
* `.github/workflows/build.yml`: har push/PR da; wrapper-validation; `chmod +x gradlew`; avval `testDebugUnitTest`, keyin `assembleDebug`; test hisoboti har doim yuklanadi; APK artefakti `CI-ONLY` deb nomlandi.
* `README.md`: "Testlar" bo'limi.
