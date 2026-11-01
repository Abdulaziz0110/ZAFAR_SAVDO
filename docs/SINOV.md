# Har versiyadan keyin 5 daqiqalik sinov (v3.9)

Avval: APK nomidagi versiyani tekshiring (masalan `SavdoAdmin-3.9-OWN-KEY.apk`, `SavdoMijoz-1.13-...`).

## Admin (sizning telefoningiz, Tailscale yoqiq)
1. Tarix ▸ 1C tarixi: filtrlar ostidagi holat qatori — "Server: ... yuklandi", xato yo'q.
2. Qidiruvga mijoz nomini yozing (masalan "dadaxon") — uning hujjatlari chiqadi.
3. Boshqaruv ▸ Kompyuter (1C): "Kompyuter hali ulanmagan" emas, oxirgi xabar vaqti yangi.

## Agent
4. Tarix ▸ 1C tarixi: holat qatori "Admin'dan: ... yangilangan". Internetni o'chirib qayta oching — ro'yxat turibdi.
5. Bitta sinov savdo (yoki savatchaga qo'shib, yubormasdan tozalang).

## Mijoz (Dadaxon aka)
6. Bosh sahifa: qarz, "Yangi savdo", 4 plitka, "Bugun (o'z savdom)".
7. Savdo: narx turi (1, 2, Naqd, Optom) almashganda narxlar o'zgaradi; "+" bilan savatchaga; Savatcha ▸ Saqlash.
8. Internetni o'chiring: tepada qizil "Internet yo'q — ... holatidagi ma'lumot" chiqadi, savdo baribir saqlanadi.
9. Narxlar va Tarix ochiladi (internetsiz ham).

Biror qadam o'tmasa: shu qadam raqami + skrinshot (holat qatori ko'rinib tursin).

# Ma'lumot qayerdan qayerga boradi

```
Do'kon kompyuteri (1C) --[Firebase in/]--> Admin telefoni --[Firebase g/{kalit}/hist, cat]--> Agentlar
SQL server (Tailscale) ----------------->  Admin telefoni --[Firebase s/{kalit}]-----------> Mijoz (Dadaxon aka)
                                                         <--[so'rov, to'lov, buyurtma]------ Mijoz
Har qurilma oxirgi olganini O'Z XOTIRASIDA saqlaydi -> internet yo'q bo'lsa ham shu bilan ishlaydi.
```

| Ma'lumot | Qayerdan | Qachon | Qayerda saqlanadi |
|---|---|---|---|
| Barcha 1C savdolari (tovarlari bilan) | Kompyuter | har 5 daqiqada (oxirgi 30 kun), keyin 1 yilgacha orqaga | Admin: onec/sales.json |
| Mijozlar akt sverkasi (savdo, to'lov, qarz) | Server | avval 31 kun hammasi, keyin 1 yil; savdodan keyin 2.5 va 6 daq | Admin: hist/ |
| Agentlarga tarix | Admin | o'zgarsa, daqiqada ko'pi bilan bir | Agent: hist_agent/ |
| Mijozga narx, qarz, tarix | Admin | Mijoz "Yangilash" bosganda, savdo/to'lovdan keyin, har 10 daq | Mijoz: snapshot.blob |
| Mijozning o'z savdolari | Mijozning o'zi | darhol | Mijoz: kassa.db (faqat o'zida) |
