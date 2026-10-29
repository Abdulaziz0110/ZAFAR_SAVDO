# IMSOFT chek uzatgichi (kompyuter)

1C → Windows printer `filial2` (127.0.0.1:9100, Raw) → **forwarder.py** → Google Firebase → 2-filial telefonidagi
**IMSOFT Mijoz** ilovasi → USB chek printeri.

* Faqat Python standart kutubxonasi (pip kerak emas), Python 3.8+.
* Sozlama: `forwarder.ini` (1-ilova ▸ Sozlamalar ▸ Mijoz ilovasi ▸ "Kompyuter uchun chek sozlamasi"). Ichida secret bor — boshqalarga bermang.
* O'rnatish va tekshirish: `docs/EGA_QOLLANMA.md` ▸ (h).

| Buyruq | Nima qiladi |
|---|---|
| `python forwarder.py` | Ishga tushirish (odatda `forwarder.vbs` orqali oynasiz) |
| `python forwarder.py --test` | Sinov chekini yuborish |
| `python forwarder.py --status` | Diskdagi va bulutdagi navbat, 2-filial printer holati |
| `python forwarder.py --once` | Diskdagi navbatni bir marta yuborish |

Fayllar: `outbox/` (yuborilmagan cheklar), `failed/` (juda katta cheklar), `forwarder.log` (jurnal, 1 MB × 4).
Chek baytlari o'zgartirilmaydi. Bulutga o'zgarmas kalit bilan yoziladi — javob yo'qolib qayta yuborilsa ham bitta chek chiqadi.
