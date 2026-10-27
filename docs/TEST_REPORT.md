# Test hisoboti — v2.3 (2026-10-27)

## Natija (shu muhitda bajarilgan)
| Tekshiruv | Natija |
|---|---|
| Kotlin kompilyatsiyasi: barcha `app/src/main` (Android 35 API, AndroidX, WorkManager stub'lari, generatsiya qilingan R/ViewBinding) | **0 xato**, 4 ta eskirgan API ogohlantirishi |
| JUnit, jami | **56/56 o'tdi** |
| `ProtocolFixtureTest` — tur 1/3/4/5 bayt-bayt, 5 locale × 4 test (fixture fayllari o'zgarmagan) | 20/20 |
| `MoneyFixtureTest` — eski `Money` xatti-harakati (o'zgarmagan) | 10/10 |
| `MoneyParseUserTest` — minglik ajratgich, o'nlik vergul, rad etish, 5 locale | 6/6 |
| `OutboxEngineTest` — 300 tasodifiy nosozlik ssenariysi (35 % xato, 15 % jarayon o'limi), 150 ta "qatorlarni o'chiradigan server", 40 qatorga 3 parallel ishchi, 7 aniq ssenariy | 10/10 · **1038 o'lim, 0 takroriy, 0 yo'qolgan**; 186 ta qator NEEDS_CHECK ga tushdi (kutilgan: bu ssenariylarda guvoh yo'q) |
| `BusinessRulesTest` — C8 (kirimdan arzon narx to'smaydi, kurs 0), tekshiruvchi faqat ≤2 ogohlantirish, "Shunday qoldir", oxirgi narx, taklif izohi va fikri, lokal hisobot, CSV qochirish, parol manbada yo'qligi | 6/6 |
| `BackupCryptoAndMatchTest` — zaxira shifri, noto'g'ri parol, payload solishtirish (varchar `?`, char probellar) | 4/4 |
| `tools/sql_smoke_test.py` — `HistoryDb.kt` dagi haqiqiy SQL bilan v2 → v3 migratsiya (50 + 3 qator saqlandi, eski navbat → UNCERTAIN), atomik egallash, lease, `draft_cart` | **OK** |
| Mutatsiya: probe o'chirilsa navbat testlari yiqiladimi | Ha, 5 test yiqildi (testlar ishlaydi) |
| Statik resurs tekshiruvi: barcha `@string/@drawable/@id` havolalari, XML yaxlitligi, lotin/kirill kalitlari va `%1$s` argumentlari mos | OK |

## Tekshirib bo'lmagan narsalar (ochiq)
1. **Haqiqiy Gradle build** (AAPT2 resurs bog'lash, manifest birlashtirish, R8, aniq kutubxona versiyalari). Bu muhitda Google
   Maven va Gradle serverlari yopiq. GitHub Actions'dagi birinchi ishga tushish shuni tekshiradi; xato chiqsa logni yuboring.
2. **Haqiqiy telefon** (Xiaomi HyperOS 3): UI ko'rinishi, bosishlar, WorkManager fonda ishlashi, batareya tejash, Android Keystore.
3. **Haqiqiy server**: `savollar` ustun turlari (unicode/uzunlik), server dasturi qatorlarni o'chiradimi, `SELECT MAX(ID)` va
   probe so'rovlari tezligi, `GETDATE()` ruxsati. Server tekshirilmaydi va o'zgartirilmaydi (egasining qarori).
4. **Tailscale orqali** ishlash.
5. **Eski ilova ma'lumotini ko'chirish** (`run-as`) — buyruqlar qo'llanmada, telefonda bajarilmagan.
6. Android SQLite'da migratsiya (Python sqlite3 da sinaldi, Android'da emas).
7. 50 000 tovarli katalogda tezlik va xotira.

Bu ro'yxatdagi narsalar uchun: `docs/EGA_QOLLANMA.md` (b) bo'limidagi 1 telefon, 1 kichik buyurtma sinovi.
