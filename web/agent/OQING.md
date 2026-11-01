# Savdo Agent — iPhone (veb-ilova)

Android agentning "Admin orqali" rejimi bilan bir xil: kirish, katalog (4 narx), savdo, qaytarish, to'lov, qarz, hisobot,
1C tarixi. Mac va Apple to'lovi kerak emas.

## Joylash (bir marta)
**GitHub Pages** (repo ochiq — public bo'lsa): GitHub ▸ repo ▸ Settings ▸ Pages ▸ Source: **GitHub Actions**.
Keyingi build'dan keyin manzil Actions ▸ "pages" bosqichida chiqadi (masalan `https://<login>.github.io/<repo>/`).

**Repo yopiq (private) bo'lsa** — bepul Cloudflare Pages: dash.cloudflare.com ▸ Workers & Pages ▸ Create ▸ Pages ▸
Connect to Git ▸ shu repo ▸ Build command: bo'sh, Build output directory: `web/agent` ▸ Save and Deploy.
Har ZIP yuklanganda o'zi yangilanadi.

## iPhone'ga o'rnatish
1. Manzilni **Safari**'da oching.
2. Pastdagi **Ulashish** ▸ **"Bosh ekranga qo'shish"**.
3. Ilovani oching ▸ Admin bergan **IMSAG1:...** kodini joylang (Admin ▸ Boshqaruv ▸ Agentlar ko'prigi ▸ Kodni yuborish).
4. Agent telefon raqami va paroli bilan kiring.

## Cheklovlar
- Chek printeriga to'g'ridan-to'g'ri ulanmaydi: chek do'kon printerida (1/2) chiqadi.
- Admin telefoni yoqiq va internetda bo'lishi kerak (Android agentlar bilan bir xil).
- Internet yo'q bo'lsa savdo/to'lov navbatda turadi va internet kelganda o'zi yuboriladi (takrorlanmaydi).
