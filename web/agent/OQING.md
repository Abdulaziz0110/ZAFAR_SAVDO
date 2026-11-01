# Savdo Agent — iPhone (veb-ilova)

Android agentning "Admin orqali" rejimi bilan bir xil: kirish, katalog (4 narx), savdo, qaytarish, to'lov, qarz, hisobot,
1C tarixi. Mac va Apple to'lovi kerak emas.

## Joylash
Workflow har build'da `web/agent` ni **Cloudflare Pages**'ga chiqaradi: https://zafar-savdo-agent.pages.dev
(GitHub secret'lari: CLOUDFLARE_API_TOKEN — Account ▸ Cloudflare Pages ▸ Edit, CLOUDFLARE_ACCOUNT_ID).

## iPhone'ga o'rnatish
1. Manzilni **Safari**'da oching.
2. Pastdagi **Ulashish** ▸ **"Bosh ekranga qo'shish"**.
3. Ilovani oching ▸ Admin bergan **IMSAG1:...** kodini joylang (Admin ▸ Boshqaruv ▸ Agentlar ko'prigi ▸ Kodni yuborish).
4. Agent telefon raqami va paroli bilan kiring.

## Cheklovlar
- Chek printeriga to'g'ridan-to'g'ri ulanmaydi: chek do'kon printerida (1/2) chiqadi.
- Admin telefoni yoqiq va internetda bo'lishi kerak (Android agentlar bilan bir xil).
- Internet yo'q bo'lsa savdo/to'lov navbatda turadi va internet kelganda o'zi yuboriladi (takrorlanmaydi).
