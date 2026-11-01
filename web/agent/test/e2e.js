// Ishga tushirish (Chromium + playwright kerak): node web/agent/test/e2e.js web/agent
// E2E: soxta Firebase (REST) + soxta Admin; PWA Chromium'da: ulanish, kirish, katalog, savdo, to'lov, 1C tarixi.
const http = require('http'); const fs = require('fs'); const path = require('path'); const zlib = require('zlib'); const crypto = require('crypto');
const WEB = process.argv[2];
const KEY = 'abcdefghijkmnpqrstuvwxyzABCDEFGH';
const db = {};
function getP(p) { let o = db; for (const k of p) { if (o == null || typeof o !== 'object') return null; o = o[k]; } return o === undefined ? null : o; }
function setP(p, v) { let o = db; for (let i = 0; i < p.length - 1; i++) { if (typeof o[p[i]] !== 'object' || o[p[i]] === null) o[p[i]] = {}; o = o[p[i]]; } if (v === null) delete o[p[p.length - 1]]; else o[p[p.length - 1]] = v; }
function sv(v) { if (v && typeof v === 'object') { if (v['.sv'] === 'timestamp') return Date.now(); for (const k of Object.keys(v)) v[k] = sv(v[k]); } return v; }
const gz = (s) => zlib.gzipSync(Buffer.from(s)).toString('base64');
const log = [];
const srv = http.createServer((req, res) => {
  const u = new URL(req.url, 'http://x');
  if (u.pathname.endsWith('.json')) {
    const p = u.pathname.slice(1, -5).split('/').filter(Boolean);
    let body = '';
    req.on('data', (c) => body += c);
    req.on('end', () => {
      res.setHeader('Content-Type', 'application/json');
      if (req.method === 'GET') return res.end(JSON.stringify(getP(p)));
      if (req.method === 'DELETE') { setP(p, null); return res.end('null'); }
      if (req.method === 'PUT') {
        if (p[2] === 'ob' && getP(p) != null) { res.statusCode = 401; return res.end('{"error":"Permission denied"}'); }
        const v = sv(JSON.parse(body)); setP(p, v); log.push(p.join('/')); return res.end(JSON.stringify(v));
      }
      res.statusCode = 405; res.end();
    });
    return;
  }
  let f = path.join(WEB, u.pathname === '/' ? 'index.html' : u.pathname.replace(/^\//, ''));
  if (!fs.existsSync(f)) { res.statusCode = 404; return res.end(); }
  const ext = path.extname(f);
  res.setHeader('Content-Type', { '.html': 'text/html', '.js': 'application/javascript', '.png': 'image/png', '.webmanifest': 'application/manifest+json' }[ext] || 'text/plain');
  res.end(fs.readFileSync(f));
});
// --- soxta Admin ---
const G = (s) => ['g', KEY, ...s.split('/')];
const cat = { v: 1, at: Date.now(), g: [['G1', 'Pista']], r: [['R1', 'Toshkent']], s: [['R1', 'S9', 'Dadaxon Aka', '1500000', '0', '']], k: ['12650'],
  p: [['G1', 'T01', 'Pista Ahmadiy', '1', '100000', '0', '1', '5', '', '110000', '0', '1', '0'], ['G1', 'T02', 'Bodom', '0', '0', '8', '1', '5', '', '0', '9', '0', '0']],
  a: [['7', 'Ali Valiyev', '', '+998901112233']] };
setP(G('cat'), { at: Date.now(), b: gz(JSON.stringify(cat)), q: gz(JSON.stringify({ T01: [95000, 0] })) });
const tag = (t, n) => t + '\u001F' + 'S9' + '\u001F' + n;
setP(G('hist/meta'), { at: Date.now(), n: 2, b: { b2026110: 'h1' } });
setP(G('hist/m/b2026110'), gz(JSON.stringify([[20261101090000, 'S', tag('1C №5', 'Dadaxon Aka'), 'Pista', 2, { sm: [200000, 0] }, {}], [20261101100000, 'P', tag('Kassa', 'Dadaxon Aka'), 'naqd', 0, { py: [50000, 0] }, {}]])));
const PASS = 'secret12';
const proof = (salt) => crypto.pbkdf2Sync(PASS, 'imsoft-agent:' + salt, 20000, 32, 'sha256').toString('base64url');
const written = [];
setInterval(() => {
  const rq = getP(G('rq')) || {};
  for (const [id, o] of Object.entries(rq)) {
    const prm = JSON.parse(o.p); let r;
    if (o.op === 'login') r = { a: prm.ph === '+998901112233' && prm.pr === proof(prm.s) ? ['7', 'Ali Valiyev', '', '+998901112233'] : null };
    else if (o.op === 'debt') r = { s: '1750000', u: '0' };
    else if (o.op === 'report') r = '<table><tr><td>Hisobot ' + prm.p + '</td></tr></table>';
    setP(G('rs/' + id), { ok: true, r, at: Date.now() }); setP(G('rq/' + id), null);
  }
  const ob = getP(G('ob')) || {};
  for (const [oid, o] of Object.entries(ob)) {
    const want = crypto.createHash('sha256').update(o.u + '\u0001' + o.t + '\u0001' + o.p).digest('hex').slice(0, 40);
    if (want !== oid) { setP(G('obr/' + oid), { st: 'rej', m: 'bad_item' }); }
    else { written.push(o); setP(G('obr/' + oid), { st: 'ok', id: String(1000 + written.length), at: Date.now() }); }
    setP(G('ob/' + oid), null);
  }
}, 300);
srv.listen(0, async () => {
  const base = 'http://127.0.0.1:' + srv.address().port;
  const { chromium } = require('playwright');
  const b = await chromium.launch();
  const page = await b.newPage({ viewport: { width: 390, height: 844 } });
  const errors = []; page.on('pageerror', (e) => errors.push(e.message)); page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });
  await page.goto(base + '/index.html');
  await page.waitForSelector('#code');
  // haqiqiy kodni tekshirish (https) — keyin test uchun http manzil
  const code = 'IMSAG1:' + Buffer.from('my-db.firebaseio.com\n' + KEY).toString('base64url');
  await page.fill('#code', 'xato'); await page.click('[data-act=setup]');
  if (!(await page.textContent('#msg')).includes("noto'g'ri")) throw new Error('bad code accepted');
  await page.fill('#code', code); await page.click('[data-act=setup]');
  await page.waitForSelector('#phone');
  await page.evaluate((u) => { localStorage.setItem('ims_cfg', JSON.stringify({ dbUrl: u, key: 'abcdefghijkmnpqrstuvwxyzABCDEFGH' })); }, base);
  await page.reload(); await page.waitForSelector('#phone');
  await page.fill('#phone', '+998901112233'); await page.fill('#pass', 'xato'); await page.click('[data-act=login]');
  await page.waitForFunction(() => document.querySelector('#msg') && document.querySelector('#msg').textContent.includes("noto'g'ri"), null, { timeout: 15000 });
  await page.fill('#pass', PASS); await page.click('[data-act=login]');
  await page.waitForSelector('text=Salom, Ali Valiyev', { timeout: 20000 });
  await page.waitForFunction(() => document.body.innerText.includes('Katalog: ') && !document.body.innerText.includes('yuklanmagan'), null, { timeout: 40000 });
  await page.screenshot({ path: 'home.png' });
  // savdo
  await page.click('[data-act=new-sale]');
  await page.waitForSelector('.overlay [data-x=store]');
  await page.click('.overlay [data-x=store]');
  await page.click('[data-act=seg][data-k=level][data-v="1"]');
  const pr1 = await page.textContent('.list .item:has([data-id=T01]) .price');
  if (!pr1.includes('95 000')) throw new Error('level1 price ' + pr1);
  await page.click('[data-act=seg][data-k=level][data-v="4"]');
  await page.fill('#q', 'pista');
  await page.click('[data-act=plus][data-id=T01]'); await page.click('[data-act=plus][data-id=T01]');
  await page.fill('#q', '');
  await page.click('[data-act=plus][data-id=T02]');
  await page.screenshot({ path: 'sale.png' });
  await page.click('.bottom [data-act=cart]');
  await page.waitForSelector('#cash');
  await page.click('[data-act=pay-all]');
  await page.click('[data-act=debt]');
  await page.waitForFunction(() => document.body.innerText.includes('1 750 000'), null, { timeout: 20000 });
  await page.screenshot({ path: 'cart.png' });
  await page.click('[data-act=send]');
  await page.waitForSelector('text=Kvitansiya I-0001');
  await page.waitForSelector('text=Yuborildi · №1001', { timeout: 30000 });
  await page.screenshot({ path: 'done.png' });
  // to'lov
  await page.click('[data-act=home]');
  await page.click('[data-act=pay]');
  await page.fill('#pcash', '50 000'); await page.fill('#pnote', "qarz to'lovi");
  await page.click('[data-act=send-pay]');
  await page.waitForSelector('text=Yuborildi · №1002', { timeout: 30000 });
  // 1C tarixi
  await page.click('[data-act=back]');
  await page.click('[data-act=ledger]');
  await page.waitForFunction(() => document.querySelectorAll('.doc').length === 2, null, { timeout: 20000 });
  await page.fill('#hq', 'dadaxon pista');
  await page.waitForFunction(() => document.querySelectorAll('.doc').length === 1);
  await page.screenshot({ path: 'ledger.png' });
  // hisobot
  await page.click('[data-act=back]'); await page.click('[data-act=report]');
  await page.click('[data-act=run-report]');
  await page.waitForSelector('iframe.rep', { timeout: 20000 });
  // oflayn: savdo navbatda, internet kelganda yuboriladi
  await page.click('[data-act=back]');
  await page.context().setOffline(true);
  await page.click('[data-act=new-sale]');
  await page.click('.overlay [data-x=store]');
  await page.click('[data-act=plus][data-id=T01]');
  await page.click('.bottom [data-act=cart]');
  await page.click('[data-act=send]');
  await page.waitForSelector('text=Navbatda');
  await page.screenshot({ path: 'offline.png' });
  await page.context().setOffline(false);
  await page.evaluate(() => window.dispatchEvent(new Event('online')));
  await page.waitForSelector('text=Yuborildi · №1003', { timeout: 30000 });
  // tekshiruv: serverga ketgan matnlar
  const sale = written[0];
  console.log('SALE', sale.t, sale.p);
  console.log('PAY', written[1].t, written[1].p);
  console.log('MIRROR', sale.m);
  if (!/^7\^S9\^\d\d\.\d\d\.\d{4} \d\d:\d\d:\d\d\^0\^1\^301200\^0\^0\^0\^12650\^1\^1~T01\^2\.0\^100000@T02\^1\.0\^101200@$/.test(sale.p)) throw new Error('sale payload ' + sale.p);
  if (!/^7\^S9\^50000\^0\^.*\^12650\^qarz to`lovi\^50000\^0\^0\^1~$/.test(written[1].p)) throw new Error('pay payload');
  if (written.length !== 3) throw new Error('written ' + written.length);
  console.log('ERRORS', JSON.stringify(errors));
  await b.close(); srv.close();
  console.log('E2E OK'); process.exit(0);
});
