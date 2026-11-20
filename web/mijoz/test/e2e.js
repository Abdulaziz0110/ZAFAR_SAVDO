// Ishga tushirish (Chromium + playwright kerak): node web/mijoz/test/e2e.js web [rasmlar_papkasi]
// E2E: soxta Firebase (Playwright route) + haqiqiy kirish yozuvi (fixture): kirish, qarz, narxlar, savatcha, buyurtma, hisob-kitob.
const http = require('http'); const fs = require('fs'); const path = require('path');
const { chromium } = require('playwright');
const WEB = process.argv[2]; const SHOTS = process.argv[3] || '';
const fx = require('./fixture.json');
const db = { a: {}, s: { READKEY1234567890abcdefgh: { meta: { at: 1763600000000, v: 1 }, blob: fx.blob } } };
db.a[fx.loginKey] = fx.rec;
function getP(p) { let o = db; for (const k of p) { if (o == null || typeof o !== 'object') return null; o = o[k]; } return o === undefined ? null : o; }
function setP(p, v) { let o = db; for (let i = 0; i < p.length - 1; i++) { if (typeof o[p[i]] !== 'object' || o[p[i]] === null) o[p[i]] = {}; o = o[p[i]]; } o[p[p.length - 1]] = v; }
function sv(v) { if (v && typeof v === 'object') { if (v['.sv'] === 'timestamp') return Date.now(); for (const k of Object.keys(v)) v[k] = sv(v[k]); } return v; }
const srv = http.createServer((req, res) => {
  const u = new URL(req.url, 'http://x');
  // sayt tuzilmasi (Cloudflare Pages): ildizda agent (web/agent), /mijoz/ da mijoz (web/mijoz)
  const rel = u.pathname.endsWith('/') ? u.pathname + 'index.html' : u.pathname;
  const f = path.join(path.resolve(WEB), rel.startsWith('/mijoz/') ? rel : '/agent' + rel);
  if (!f.startsWith(path.resolve(WEB)) || !fs.existsSync(f)) { res.statusCode = 404; return res.end(); }
  res.setHeader('Content-Type', { '.html': 'text/html', '.js': 'application/javascript', '.png': 'image/png', '.webmanifest': 'application/manifest+json' }[path.extname(f)] || 'text/plain');
  res.end(fs.readFileSync(f));
});
const ok = (c, m) => { if (!c) { throw new Error('FAIL: ' + m); } console.log('ok -', m); };
srv.listen(0, async () => {
  const port = srv.address().port;
  const b = await chromium.launch();
  const ctx = await b.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: SHOTS ? 2 : 1 });
  const p = await ctx.newPage();
  const errs = []; p.on('pageerror', (e) => errs.push(e.message));
  p.on('dialog', (d) => d.accept());
  await p.route('https://dokon-test-default-rtdb.firebaseio.com/**', async (route) => {
    const r = route.request(); const u = new URL(r.url());
    const parts = u.pathname.slice(1).replace(/\.json$/, '').split('/').filter(Boolean);
    if (r.method() === 'GET') return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(getP(parts)) });
    if (r.method() === 'PUT') { const v = sv(JSON.parse(r.postData())); setP(parts, v); return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(v) }); }
    return route.fulfill({ status: 405, body: '' });
  });
  try {
    await p.goto('http://127.0.0.1:' + port + '/mijoz/');
    await p.fill('#lCode', 'dokon-test');
    await p.fill('#lPhone', fx.phone);
    await p.fill('#lPw', 'notogri');
    await p.click('#lGo');
    await p.waitForFunction(() => document.querySelector('#lErr').textContent.length > 0);
    ok((await p.textContent('#lErr')).includes("noto'g'ri"), "noto'g'ri parol rad etildi");
    await p.fill('#lPw', fx.pw);
    await p.click('#lGo');
    await p.waitForSelector('.hero .amt');
    ok((await p.textContent('header h1')).includes('Dadaxon'), 'kirildi, mijoz nomi');
    ok((await p.textContent('.hero .amt')).replace(/\s/g, '').includes('2500000'), 'qarz ko\'rindi');
    if (SHOTS) await p.screenshot({ path: path.join(SHOTS, 'm_home.png') });
    await p.click('#tPrices');
    await p.waitForSelector('#plist .item');
    ok((await p.$$('#plist .item')).length === 2, 'narxlar ro\'yxati');
    await p.click('#plist [data-p]');
    await p.click('#plist [data-p]');
    ok((await p.textContent('#bCart')).includes('1 ta'), 'savatchaga qo\'shildi');
    if (SHOTS) await p.screenshot({ path: path.join(SHOTS, 'm_prices.png') });
    await p.click('#bCart');
    await p.fill('#cNote', 'Ertaga olaman');
    await p.click('#cSend');
    await p.waitForFunction(() => document.querySelector('header h1').textContent === 'Buyurtmalarim');
    const ord = getP(['s', 'READKEY1234567890abcdefgh', 'ord']) || {};
    const ids = Object.keys(ord);
    ok(ids.length === 1 && JSON.parse(ord[ids[0]].l)[0][1] === 2 && ord[ids[0]].n === 'Ertaga olaman', 'buyurtma bulutga yozildi (2 dona, izoh)');
    await p.waitForSelector('.card .st');
    ok((await p.textContent('.card .st')) === 'Yuborildi', 'buyurtma holati ko\'rinadi');
    ord[ids[0]].st = { s: 'taken', at: Date.now() };
    await p.click('[data-tab="home"]'); await p.click('[data-tab="orders"]');
    await p.waitForFunction(() => document.querySelector('.card .st') && document.querySelector('.card .st').textContent === 'Qabul qilindi');
    ok(true, 'holat yangilandi: Qabul qilindi');
    if (SHOTS) await p.screenshot({ path: path.join(SHOTS, 'm_orders.png') });
    await p.click('[data-tab="ledger"]');
    await p.waitForSelector('#llist .doc');
    ok((await p.$$('#llist .doc')).length === 2, 'hisob-kitob: 2 hujjat');
    await p.locator('#llist .doc').nth(1).click();
    await p.waitForSelector('.sheet');
    ok((await p.$$('.sheet .item')).length >= 1, 'hujjat qatorlari');
    if (SHOTS) await p.screenshot({ path: path.join(SHOTS, 'm_ledger.png') });
    await p.click('.sheet [data-x]');
    // qayta ochilganda (internetsiz ham) — saqlangan ma'lumot
    await p.reload();
    await p.waitForSelector('.hero .amt');
    ok(true, 'qayta ochilganda saqlangan ma\'lumot');
    ok(errs.length === 0, 'JS xatosi yo\'q ' + JSON.stringify(errs));
    console.log('mijoz e2e: hammasi o\'tdi');
  } catch (e) {
    console.error(e.message, JSON.stringify(errs));
    process.exitCode = 1;
  } finally {
    await b.close(); srv.close();
  }
});
