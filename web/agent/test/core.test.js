// iPhone (veb) agent: sof mantiq testlari — Android protokol fixture'lari bilan aynan bir xil bo'lishi shart.
//   node web/agent/test/core.test.js
'use strict';
const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');
const Core = require('../core.js');
Core.setCrypto(require('crypto').webcrypto);

const FIX = {};
for (const line of fs.readFileSync(path.join(__dirname, '../../../app/src/test/resources/fixtures/protocol.txt'), 'utf8').split('\n')) {
  if (!line || line.startsWith('#')) continue;
  const i = line.indexOf('\t');
  FIX[line.slice(0, i)] = line.slice(i + 1).replace(/\r$/, '');
}
const T = new Date(2026, 9, 26, 14, 5, 9);
const MORNING = new Date(2026, 0, 3, 7, 8, 9);
const L = (id, qty, price) => ({ productId: id, qty, price });
const order = (agentId, storeId, time, currency, priceType, cashSom, cashUsd, card, discount, rate, mode, printer, lines) =>
  Core.order({ agentId, storeId, time, currency, priceType, cashSom, cashUsd, card, discount, rate, mode, printer, lines });

test('tur 1 — buyurtma (Android fixture)', () => {
  assert.strictEqual(order('7', 'S9', T, '0', '1', 100000, 0, 5000, 0, '12650', '1', '1', [L('T01', 5, 12500), L('T02', 2.5, 3000)]), FIX['order.som.sale.basic']);
  assert.strictEqual(order('12', '105', MORNING, '0', '0', 250000, 0, 0, 12345.67, '12650.50', '1', '3', [L('A-1', 10, 26500), L('A-2', 1, 1999.99)]), FIX['order.som.cash.discount.printer3']);
  assert.strictEqual(order('7', 'S9', T, '1', '1', 0, 120.5, 0, 0, '12650', '1', '2', [L('T01', 3, 10.25), L('T09', 0.5, 7)]), FIX['order.usd.wholesale']);
  assert.strictEqual(order('7', 'S9', T, '0', '1', 0, 0, 0, 0, '12650', '2', '1', [L('T01', 2, 12500)]), FIX['order.return.mode2']);
  assert.strictEqual(order('7', 'S9', T, '0', '1', 0, 0, 0, 0, '', '1', '1', [L('T01', 1, 100)]), FIX['order.blank.rate.is.zero']);
  assert.strictEqual(order('7', 'S9', T, '0', '1', 0, 0, 0, 0, '12650', '1', '1',
    [L('Q1', 1 / 3, 99.999), L('Q2', 0.1 + 0.2, 0.1 + 0.2), L('Q3', 1234567, 0.5), L('Q4', 2.0005, 1e9)]), FIX['order.fraction.qty.and.price']);
  assert.strictEqual(order('1', '1', T, '0', '0', 5000, 0, 0, 0, '12650', '1', '1', [L('777', 1, 5000)]), FIX['order.single.line']);
});

test('tur 4 — to\'lov (Android fixture)', () => {
  const p = (a, s, t, rate, note, cash, tr, click, pr) => Core.payment({ agentId: a, storeId: s, time: t, rate, note, cash, transfer: tr, click, printer: pr });
  assert.strictEqual(p('7', 'S9', T, '12650', '', 1000, 2000, 3000, '2'), FIX['payment.basic']);
  assert.strictEqual(p('12', '105', MORNING, '12650.50', 'qarz uchun', 1500000, 0, 0, '1'), FIX['payment.cash.only']);
  assert.strictEqual(p('7', 'S9', T, '12650', '', 1250.5, 0.25, 99.999, '3'), FIX['payment.fraction']);
  assert.strictEqual(p('7', 'S9', T, '12650', "a^b~c@d'e\nf\rg  ", 1, 0, 0, '1'), FIX['payment.note.cleaned']);
  assert.strictEqual(p('7', 'S9', T, '12650', 'Қарз тўлови, ёғ', 1000, 0, 0, '1'), FIX['payment.note.cyrillic']);
  assert.strictEqual(p('7', 'S9', T, '', '', 1000, 0, 0, '1'), FIX['payment.blank.rate']);
  assert.strictEqual(Core.report(T, T, 'S9'), FIX['report.same.day']);
  assert.strictEqual(Core.report(MORNING, T, '105'), FIX['report.range']);
  assert.strictEqual(Core.timestamp(MORNING), FIX['timestamp.morning.padded']);
});

test('kalit va kirish isboti — Kotlin bilan bir xil', async () => {
  // Kotlin RelayCodec.oid: sha256("7\u00011\u0001matn") hex, 40 belgi
  const want = require('crypto').createHash('sha256').update('7\u00011\u0001abc').digest('hex').slice(0, 40);
  assert.strictEqual(await Core.oid('7', '1', 'abc'), want);
  // Python hashlib.pbkdf2_hmac('sha256', 'secret12', 'imsoft-agent:' + salt, 20000) — Kotlin testida ham shu qiymat
  assert.strictEqual(await Core.proof('secret12', 'SaltSaltSaltSaltSaltSaltSalt2345'), '-xMrPWVmmOdF8kE9Ng3eZ-47qsFGhO2R-FcTzbLGoGg');
  assert.ok(Core.isKey(Core.newKey()));
});

test('ulanish kodi', () => {
  const code = 'IMSAG1:' + Buffer.from('my-db.firebaseio.com\nabcdefghijkmnpqrstuvwxyzABCD').toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  assert.deepStrictEqual(Core.decodeCode('  ' + code + '\n'), { dbUrl: 'https://my-db.firebaseio.com', key: 'abcdefghijkmnpqrstuvwxyzABCD' });
  assert.strictEqual(Core.decodeCode('IMSAG1:xxx'), null);
});

test('pul formati', () => {
  assert.strictEqual(Core.plain(1.005), '1.01');
  assert.strictEqual(Core.plain(-0), '0');
  assert.strictEqual(Core.qty(5), '5.0');
  assert.strictEqual(Core.parseUser('1 250 000'), 1250000);
  assert.strictEqual(Core.parseUser('12,5'), 12.5);
  assert.strictEqual(Core.parseUser('1,250,000.50'), 1250000.5);
  assert.strictEqual(Core.parseUser('abc'), null);
  assert.strictEqual(Core.display(1250000.5), '1 250 000.5');
});

test('katalog va narx qoidalari', async () => {
  const cat = {
    v: 1, at: 5, g: [['G1', 'Pista']], r: [['R1', 'Toshkent']], s: [['R1', 'S9', 'Dadaxon Aka', '1500000', '0', '']],
    k: ['12000', '12650'],
    p: [
      ['G1', 'T01', 'Pista', '1', '100000', '0', '10', '5', '', '110000', '0', '90000', '0'],
      ['G1', 'T02', 'Bodom', '0', '0', '8', '1', '5', '', '0', '9', '0', '7'],
    ],
    a: [['7', 'Ali', '', '+998']],
  };
  const blob = zlib.gzipSync(Buffer.from(JSON.stringify(cat))).toString('base64');
  const c = Core.parseCatalog(await Core.unpack(blob), { T01: [95000, 0] });
  assert.strictEqual(c.rate, 12650);
  assert.strictEqual(c.stores[0].region, 'Toshkent');
  const [bodom, pista] = c.products;
  assert.strictEqual(Core.unitPrice(pista, 4, false, c.rate), 100000);
  assert.strictEqual(Core.unitPrice(pista, 3, false, c.rate), 110000);
  assert.strictEqual(Core.unitPrice(pista, 1, false, c.rate), 95000);
  assert.strictEqual(Core.unitPrice(pista, 2, false, c.rate), 100000); // 2-narx yo'q — Optom
  assert.strictEqual(Core.unitPrice(pista, 4, true, c.rate), 7.91);
  assert.strictEqual(Core.unitPrice(bodom, 4, false, c.rate), 101200); // asosiy $: 8 * 12650
  assert.strictEqual(Core.unitPrice(bodom, 3, true, c.rate), 9);
  assert.strictEqual(pista.blockSize, 10);
});

test('1C tarixi bo\'laklari', () => {
  const tag = (t, sid, n) => t + '\u001F' + sid + '\u001F' + n;
  const e = [
    [20261101090000, 'S', tag('1C №5', 'S9', 'Dadaxon Aka'), 'Pista', 2, { pr: [100, 0], sm: [200, 0] }, {}],
    [20261101090000, 'S', tag('1C №5', 'S9', 'Dadaxon Aka'), 'Bodom', 1, { sm: [50, 0] }, {}],
    [20261101100000, 'P', tag('Kassa', 'S9', 'Dadaxon Aka'), 'naqd', 0, { py: [1000, 0] }, {}],
    [20261101090000, 'S', tag('1C №6', '', 'Chakana'), 'Pista', 1, { sm: [100, 0] }, {}],
  ].map(Core.entry);
  const d = Core.docs(e);
  assert.strictEqual(d.length, 3);
  assert.strictEqual(d[0].kind, 'payment');
  const sale = d.find((x) => x.text === '1C №5');
  assert.strictEqual(sale.som, 250);
  assert.strictEqual(sale.owner.name, 'Dadaxon Aka');
  assert.ok(Core.matches(sale.key, 'dadaxon pista'));
  assert.ok(!Core.matches(sale.key, 'chakana'));
  assert.strictEqual(Core.stampDate(20261101090000), '01.11.2026');
});
