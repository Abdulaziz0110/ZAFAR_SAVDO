// Mijoz veb-ilova: sof mantiq testlari (node test/mcore.test.js). Kalitlar Android LoginRecord/SnapshotCodec bilan bir xil.
const assert = require('assert');
const path = require('path');
global.self = global;
if (!global.crypto || !global.crypto.subtle) global.crypto = require('crypto').webcrypto;
global.Core = require(path.join(__dirname, '..', '..', 'agent', 'core.js'));
const M = require(path.join(__dirname, '..', 'mcore.js'));
const fx = require('./fixture.json');
let n = 0;
const t = async (name, fn) => { await fn(); n++; console.log('ok -', name); };
(async () => {
  await t('shop code -> url', () => {
    assert.strictEqual(M.shopUrl('dokon-test'), 'https://dokon-test-default-rtdb.firebaseio.com');
    assert.strictEqual(M.shopUrl('Dokon-1a2b3@europe-west1'), 'https://dokon-1a2b3-default-rtdb.europe-west1.firebasedatabase.app');
    assert.strictEqual(M.shopUrl('https://dokon-test-default-rtdb.firebaseio.com/'), 'https://dokon-test-default-rtdb.firebaseio.com');
    assert.strictEqual(M.shopUrl('ab'), null);
    assert.strictEqual(M.shopUrl('google.com'), null);
  });
  await t('phone', () => {
    assert.strictEqual(M.phone('90 123 45 67'), '998901234567');
    assert.strictEqual(M.phone('+998 (90) 123-45-67'), '998901234567');
    assert.strictEqual(M.prettyPhone('901234567'), '+998 90 123 45 67');
    assert.ok(!M.phoneOk('123'));
  });
  await t('login key = sha256 (Android bilan bir xil)', async () => {
    assert.strictEqual(await M.loginKey(fx.url, fx.phone), fx.loginKey);
  });
  await t('grant decrypt', async () => {
    const g = await M.decryptGrant(fx.rec, fx.pw);
    assert.deepStrictEqual(g, { readKey: 'READKEY1234567890abcdefgh', queueKey: 'QUEUEKEY1234567890abcdefgh', clientName: 'Dadaxon aka', memberName: 'Ali', clientId: '55' });
    await assert.rejects(M.decryptGrant(fx.rec, 'notogri'), /wrong_password/);
  });
  await t('snapshot unpack + prices', async () => {
    const s = M.parseSnapshot(await Core.unpack(fx.blob));
    assert.strictEqual(s.clientName, 'Dadaxon aka');
    assert.strictEqual(s.debt.som, 2500000);
    assert.strictEqual(s.products.length, 2);
    const olma = s.products[0], bodom = s.products[1];
    assert.strictEqual(M.price(olma, 3, false), 12000);
    assert.strictEqual(M.price(olma, 4, false), 10000);
    assert.strictEqual(M.price(olma, 1, false), 11000);
    assert.strictEqual(M.price(olma, 2, true), 0.85);
    assert.strictEqual(M.price(bodom, 1, false), 90000);   // 1C narxi yo'q — Optom
    assert.strictEqual(s.ledger.length, 3);
    const docs = Core.docs(s.ledger);
    assert.strictEqual(docs.length, 2);
    assert.strictEqual(docs[0].kind, 'payment');
    assert.strictEqual(docs[1].som, 215000);
    const days = M.byDay(docs);
    assert.deepStrictEqual(days.map(d => d.day), [20261120, 20261119]);
    assert.ok(Core.matches(olma.key, 'олма'));
  });
  await t('order body (OrderCodec bilan bir xil)', () => {
    const id = M.orderId(1763600000000);
    assert.ok(/^o1763600000000[a-z2-9]{4}$/.test(id));
    const b = M.orderBody('Ertaga', 'Narxlar', [{ name: 'Olma', qty: 2.5, id: '1001' }], 'Ali');
    assert.deepStrictEqual(JSON.parse(b.l), [['Olma', 2.5, '1001']]);
    assert.deepStrictEqual(b.at, { '.sv': 'timestamp' });
    const o = M.orderFrom(id, { at: 5, n: 'x', l: b.l, st: { s: 'taken', at: 9 } });
    assert.strictEqual(o.statusText, 'Qabul qilindi');
    assert.strictEqual(o.lines[0].qty, 2.5);
  });
  console.log(n + ' test o\'tdi');
})().catch(e => { console.error(e); process.exit(1); });
