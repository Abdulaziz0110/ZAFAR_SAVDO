/*
 * IMSOFT Savdo Agent (iPhone / veb) — sof mantiq: pul formati, server protokoli (tur 1 va 4), narx qoidalari,
 * "Admin orqali" ko'prigi kodlari, katalog va 1C tarixi. Android agentdagi Kotlin kodi bilan aynan bir xil natija beradi
 * (test/core.test.js — Android'ning protokol fixture'lari bilan solishtiriladi).
 */
(function (root) {
  'use strict';

  // ---------------- pul va miqdor ----------------

  /** BigDecimal.setScale(n, HALF_UP) ga mos (musbat va manfiy). */
  function roundN(v, n) {
    if (!isFinite(v)) return 0;
    if (Math.abs(v) < 1e-9) return 0;
    var sign = v < 0 ? -1 : 1;
    var r = Number(Math.round(Number(Math.abs(v) + 'e' + n)) + 'e-' + n);
    return sign * r;
  }
  function round2(v) { return roundN(v, 2); }

  function trimZeros(s) {
    if (s.indexOf('.') < 0) return s;
    s = s.replace(/0+$/, '');
    return s.replace(/\.$/, '');
  }

  /** Serverga: guruhsiz, ortiqcha nollarsiz ("126750", "12.5"). Kotlin Money.plain. */
  function plain(v) {
    var r = round2(v);
    return trimZeros(r.toFixed(2)).replace(/^-0$/, '0');
  }

  /** Serverga miqdor: "5.0", "2.5", "0.333". Kotlin Money.qty. */
  function qty(v) {
    var s = trimZeros(roundN(v, 3).toFixed(3));
    return s.indexOf('.') < 0 ? s + '.0' : s;
  }

  /** Foydalanuvchi yozgan son: "1 250 000", "12,5" -> son; tushunarsiz -> null; bo'sh -> 0. */
  function parseUser(raw) {
    if (raw == null) return 0;
    var s = String(raw).replace(/[\s\u00A0\u202F'’]/g, '');
    if (!s) return 0;
    var neg = false;
    if (s[0] === '-') { neg = true; s = s.slice(1); }
    if (!/^[0-9.,]+$/.test(s)) return null;
    var commas = (s.match(/,/g) || []).length, dots = (s.match(/\./g) || []).length;
    var groupsOk = function (x, sep) {
      var p = x.split(sep);
      if (p.length < 2) return p[0].length > 0;
      if (!p[0].length || p[0].length > 3) return false;
      for (var i = 1; i < p.length; i++) if (p[i].length !== 3) return false;
      return true;
    };
    var norm;
    if (commas === 0 && dots === 0) norm = s;
    else if (commas > 0 && dots > 0) {
      var dec = s.lastIndexOf(',') > s.lastIndexOf('.') ? ',' : '.';
      var th = dec === ',' ? '.' : ',';
      if (s.split(dec).length !== 2) return null;
      var ip = s.split(dec)[0];
      if (!groupsOk(ip, th)) return null;
      norm = ip.split(th).join('') + '.' + s.split(dec)[1];
    } else if (commas > 1) { if (!groupsOk(s, ',')) return null; norm = s.split(',').join(''); }
    else if (dots > 1) { if (!groupsOk(s, '.')) return null; norm = s.split('.').join(''); }
    else if (commas === 1) norm = s.replace(',', '.');
    else norm = s;
    if (norm[0] === '.' || norm[norm.length - 1] === '.') return null;
    var v = Number(norm);
    if (!isFinite(v)) return null;
    return neg ? -v : v;
  }

  /** Katalogdagi matn son: "12 500,5" -> 12500.5; noto'g'ri -> 0. Kotlin Money.parse. */
  function parseNum(raw) {
    if (raw == null) return 0;
    var s = String(raw).replace(/[\s\u00A0]/g, '').replace(',', '.');
    var v = Number(s);
    return s && isFinite(v) ? v : 0;
  }

  /** Ekranda: "1 250 000", "12.5". */
  function display(v, decimals) {
    if (decimals == null) decimals = 2;
    var r = roundN(v, decimals);
    var neg = r < 0;
    var parts = Math.abs(r).toFixed(decimals).split('.');
    var ip = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, ' ');
    var fp = parts[1] ? parts[1].replace(/0+$/, '') : '';
    return (neg ? '-' : '') + ip + (fp ? '.' + fp : '');
  }
  function money(v, usd) { return usd ? display(v, 2) + ' $' : display(v, 2) + " so'm"; }

  // ---------------- protokol (savollar jadvali) ----------------

  function pad2(n) { return (n < 10 ? '0' : '') + n; }
  /** "dd.MM.yyyy HH:mm:ss" (telefon vaqti). */
  function timestamp(d) {
    return pad2(d.getDate()) + '.' + pad2(d.getMonth() + 1) + '.' + d.getFullYear() + ' ' + pad2(d.getHours()) + ':' + pad2(d.getMinutes()) + ':' + pad2(d.getSeconds());
  }
  function reportDate(d) { return pad2(d.getDate()) + '.' + pad2(d.getMonth() + 1) + '.' + d.getFullYear(); }

  function cleanText(s) {
    return String(s || '').replace(/[\^~@]/g, ' ').replace(/'/g, '`').replace(/[\r\n]/g, ' ').trim();
  }

  var CURRENCY = { SOM: '0', USD: '1' };
  var MODE = { SALE: '1', RETURN: '2' };
  /** 4 narx: 3 — Naqd ("0"), qolganlari serverga "1" (Optom); qator narxi har doim aniq yuboriladi. */
  function priceTypeCode(level) { return level === 3 ? '0' : '1'; }

  /**
   * Tur 1 (buyurtma): agent^do'kon^vaqt^valyuta^savdoTuri^naqd^dollar^plastik^chegirma^kurs^rejim^printer~tovar^soni^narx@...
   * o: {agentId, storeId, time: Date, currency: '0'|'1', priceType: '0'|'1', cashSom, cashUsd, card, discount, rate, mode, printer, lines:[{productId, qty, price}]}
   */
  function order(o) {
    var head = [
      o.agentId, o.storeId, timestamp(o.time), o.currency, o.priceType,
      plain(o.cashSom || 0), plain(o.cashUsd || 0), plain(o.card || 0), plain(o.discount || 0),
      String(o.rate || '').trim() || '0', o.mode, o.printer,
    ].join('^');
    var body = o.lines.map(function (l) { return l.productId + '^' + qty(l.qty) + '^' + plain(l.price) + '@'; }).join('');
    return head + '~' + body;
  }

  /** Tur 4 (to'lov): agent^do'kon^naqd^0^vaqt^kurs^izoh^naqd^o'tkazma^click^printer~ */
  function payment(o) {
    return [
      o.agentId, o.storeId, plain(o.cash || 0), '0', timestamp(o.time), String(o.rate || '').trim() || '0', cleanText(o.note),
      plain(o.cash || 0), plain(o.transfer || 0), plain(o.click || 0), o.printer,
    ].join('^') + '~';
  }

  function report(from, to, storeId) { return reportDate(from) + '#' + reportDate(to) + '#' + storeId; }

  // ---------------- kripto (WebCrypto) ----------------

  var subtle = (root.crypto && root.crypto.subtle) || null;
  function setCrypto(c) { subtle = c.subtle; root.crypto = root.crypto || c; }
  var enc = function (s) { return new TextEncoder().encode(s); };
  function hex(buf) { return Array.prototype.map.call(new Uint8Array(buf), function (b) { return (b < 16 ? '0' : '') + b.toString(16); }).join(''); }
  function b64(bytes, urlSafe, pad) {
    var bin = '';
    var u = new Uint8Array(bytes);
    for (var i = 0; i < u.length; i++) bin += String.fromCharCode(u[i]);
    var s = (typeof btoa === 'function') ? btoa(bin) : Buffer.from(bin, 'binary').toString('base64');
    if (urlSafe) s = s.replace(/\+/g, '-').replace(/\//g, '_');
    if (!pad) s = s.replace(/=+$/, '');
    return s;
  }
  function unb64(s) {
    s = String(s).replace(/\s/g, '').replace(/-/g, '+').replace(/_/g, '/');
    while (s.length % 4) s += '=';
    var bin = (typeof atob === 'function') ? atob(s) : Buffer.from(s, 'base64').toString('binary');
    var out = new Uint8Array(bin.length);
    for (var i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
    return out;
  }

  /** Savdo/to'lov kaliti: sha256(agent \u0001 tur \u0001 matn) — bitta savdo necha marta yuborilsa ham bitta yozuv. */
  function oid(userId, type, payload) {
    return subtle.digest('SHA-256', enc(userId + '\u0001' + type + '\u0001' + payload)).then(function (d) { return hex(d).slice(0, 40); });
  }

  var PROOF_ITER = 20000;
  /** Kirish isboti (parol Firebase'ga yozilmaydi): PBKDF2-SHA256("imsoft-agent:" + salt). */
  function proof(password, salt, iterations) {
    return subtle.importKey('raw', enc(password), 'PBKDF2', false, ['deriveBits']).then(function (k) {
      return subtle.deriveBits({ name: 'PBKDF2', hash: 'SHA-256', salt: enc('imsoft-agent:' + salt), iterations: iterations || PROOF_ITER }, k, 256);
    }).then(function (bits) { return b64(bits, true, false); });
  }

  var ALPHABET = 'abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  function newKey() {
    var a = new Uint8Array(32);
    root.crypto.getRandomValues(a);
    var s = '';
    for (var i = 0; i < 32; i++) s += ALPHABET[a[i] % ALPHABET.length];
    return s;
  }
  function isKey(s) { return typeof s === 'string' && s.length >= 24 && s.length <= 64 && /^[A-Za-z0-9]+$/.test(s); }

  function normalizeUrl(raw) {
    var u = String(raw || '').trim().replace(/\/+$/, '');
    if (!u) return '';
    if (!/^https?:\/\//.test(u)) u = 'https://' + u;
    return u.replace(/^http:\/\//, 'https://');
  }

  /**
   * Admin beradigan kod. Ikki xili ham qabul qilinadi:
   *   IMSAG1:<base64url(url \n kalit)>            - "Agent ulanish kodi"
   *   IMSHOP1:<base64url(url \n kalit \n ...)>    - "Do'kon kodi" (undan kerakli qismi olinadi)
   * Atrofidagi matn (xabar, havola, nuqta) e'tiborsiz: faqat kodning o'zi olinadi.
   */
  function decodeCode(text) {
    var t = String(text || '').replace(/\s/g, '');
    var m = /(IMSAG1|IMSHOP1):([A-Za-z0-9_\-]+)/.exec(t);
    if (!m) return null;
    try {
      var raw = new TextDecoder().decode(unb64(m[2]));
      var p = raw.split('\n');
      if (m[1] === 'IMSAG1' ? p.length !== 2 : p.length < 3) return null;
      var c = { dbUrl: normalizeUrl(p[0]), key: p[1].trim() };
      return /^https:\/\//.test(c.dbUrl) && isKey(c.key) ? c : null;
    } catch (e) { return null; }
  }

  // ---------------- gzip (bulutdagi bloblar) ----------------

  function gunzip(bytes) {
    if (typeof DecompressionStream === 'function') {
      var ds = new DecompressionStream('gzip');
      var stream = new Blob([bytes]).stream().pipeThrough(ds);
      return new Response(stream).arrayBuffer().then(function (b) { return new TextDecoder().decode(b); });
    }
    if (typeof require === 'function') return Promise.resolve(require('zlib').gunzipSync(Buffer.from(bytes)).toString('utf8'));
    return Promise.reject(new Error('gzip'));
  }
  /** Bulut blobi (base64 + gzip) -> matn. */
  function unpack(blob) { return gunzip(unb64(blob)); }

  // ---------------- katalog ----------------

  function str(v) {
    if (v == null) return '';
    if (typeof v === 'number') return Number.isInteger(v) ? String(v) : String(v);
    return String(v);
  }

  /** Admin joylagan katalog JSON'i -> obyektlar (Android RelayCodec.catalogFromJson bilan bir xil maydonlar). */
  function parseCatalog(json, levels) {
    var o = typeof json === 'string' ? JSON.parse(json) : json;
    var rows = function (k) { return (o[k] || []).map(function (r) { return (r || []).map(str); }); };
    var regions = {};
    rows('r').forEach(function (r) { regions[r[0]] = r[1] || ''; });
    var groups = rows('g').map(function (r) { return { id: r[0] || '', name: r[1] || '' }; });
    var stores = rows('s').map(function (r) {
      return { regionId: r[0] || '', id: r[1] || '', name: r[2] || '', region: regions[r[0]] || '', debtSom: parseNum(r[3]), debtUsd: parseNum(r[4]) };
    });
    var products = rows('p').map(function (r) {
      var block = parseNum(r[6]);
      return {
        groupId: r[0] || '', id: r[1] || '', name: r[2] || '', baseIsSom: String(r[3] || '').trim() === '1',
        wholesaleSom: parseNum(r[4]), wholesaleUsd: parseNum(r[5]), blockSize: block > 0 ? block : 1,
        cashSom: parseNum(r[9]), cashUsd: parseNum(r[10]), costSom: parseNum(r[11]), costUsd: parseNum(r[12]),
        l12: (levels && levels[r[1]]) || null,
      };
    });
    var cmp = function (a, b) { return a.name.toLowerCase() < b.name.toLowerCase() ? -1 : a.name.toLowerCase() > b.name.toLowerCase() ? 1 : 0; };
    products.sort(cmp); stores.sort(cmp); groups.sort(cmp);
    var rates = (o.k || []).map(str);
    var rateText = rates.length ? rates[rates.length - 1].trim() : '';
    var agents = rows('a').map(function (r) { return { id: r[0] || '', name: r[1] || '', boss: r[2] || '', phone: r[3] || '' }; });
    return { at: Number(o.at) || 0, groups: groups, stores: stores, products: products, rateText: rateText, rate: parseNum(rateText), agents: agents };
  }

  /** Narx: [level] 1..4 (3 — Naqd, 4 — Optom, 1/2 — 1C narxi, yo'q bo'lsa Optom). Kotlin Pricing.unitPrice. */
  function unitPrice(p, level, usd, rate) {
    if ((level === 1 || level === 2) && p.l12) {
      var som1c = Number(p.l12[level - 1]) || 0;
      if (som1c > 0) return round2(!usd ? som1c : rate > 0 ? som1c / rate : 0);
    }
    var som = level === 3 ? p.cashSom : p.wholesaleSom;
    var u = level === 3 ? p.cashUsd : p.wholesaleUsd;
    var baseSom, baseUsd;
    if (p.baseIsSom) { baseSom = som; baseUsd = rate > 0 ? som / rate : 0; } else { baseUsd = u; baseSom = u * rate; }
    return round2(usd ? baseUsd : baseSom);
  }

  /** Savdo cheki ko'zgusi (Admin 2-ilovaga yuboradi). Kotlin RelayCodec.saleToJson. */
  function saleJson(s) {
    return JSON.stringify({
      at: s.at, st: s.storeId, sn: s.storeName, m: s.mode, pt: s.priceType, c: s.currency, t: s.total, d: s.discount, pd: s.paid,
      code: s.code, l: s.lines.map(function (l) { return [l.productId, l.name, l.qty, l.price]; }),
    });
  }

  // ---------------- qidiruv ----------------

  var CYR = { 'а': 'a', 'б': 'b', 'в': 'v', 'г': 'g', 'д': 'd', 'е': 'e', 'ё': 'yo', 'ж': 'j', 'з': 'z', 'и': 'i', 'й': 'y', 'к': 'k', 'л': 'l', 'м': 'm', 'н': 'n', 'о': 'o', 'п': 'p', 'р': 'r', 'с': 's', 'т': 't', 'у': 'u', 'ф': 'f', 'х': 'x', 'ц': 's', 'ч': 'ch', 'ш': 'sh', 'щ': 'sh', 'ъ': '', 'ы': 'i', 'ь': '', 'э': 'e', 'ю': 'yu', 'я': 'ya', 'ў': 'o', 'қ': 'q', 'ғ': 'g', 'ҳ': 'h' };
  /** Qidiruv kaliti: kichik harf, kirill -> lotin, tutuq belgisiz, h~x, q~k. */
  function fold(s) {
    var out = '';
    var t = String(s || '').toLowerCase();
    for (var i = 0; i < t.length; i++) {
      var ch = t[i];
      if (CYR[ch] != null) out += CYR[ch];
      else if ("'’ʻʼ‘`´".indexOf(ch) >= 0) continue;
      else if (/[a-z0-9]/.test(ch)) out += ch;
      else out += ' ';
    }
    return out.replace(/h/g, 'x').replace(/q/g, 'k').replace(/w/g, 'v').replace(/\s+/g, ' ').trim();
  }
  /** Hamma so'z (har qanday tartibda) nomda bormi. */
  function matches(key, query) {
    var words = fold(query).split(' ').filter(Boolean);
    for (var i = 0; i < words.length; i++) if (key.indexOf(words[i]) < 0) return false;
    return true;
  }

  // ---------------- 1C tarixi (Admin yuborgan) ----------------

  var SEP = '\u001F';
  var KIND = { S: 'sale', R: 'return', P: 'payment', PR: 'paymentReturn', D: 'discount', O: 'other' };
  function m2(v, k) { var x = v && v[k]; return x ? { som: Number(x[0]) || 0, usd: Number(x[1]) || 0 } : { som: 0, usd: 0 }; }
  /** Yozuv: [stamp, tur, matn(+egasi), tovar, miqdor, {ustun: [so'm, $]}]. */
  function entry(a) {
    var text = String(a[2] || '');
    var i = text.indexOf(SEP);
    var owner = null;
    if (i >= 0) { var rest = text.slice(i + 1); owner = { storeId: rest.split(SEP)[0], name: rest.slice(rest.indexOf(SEP) + 1) }; text = text.slice(0, i); }
    var v = a[5] || {};
    var kind = KIND[a[1]] || 'other';
    var amt;
    if (kind === 'sale') amt = m2(v, 'sm');
    else if (kind === 'return') { amt = m2(v, 'rt'); if (!amt.som && !amt.usd) amt = m2(v, 'sm'); }
    else if (kind === 'payment') amt = m2(v, 'py');
    else if (kind === 'paymentReturn') { amt = m2(v, 'pt'); if (!amt.som && !amt.usd) amt = m2(v, 'py'); }
    else if (kind === 'discount') amt = m2(v, 'ds');
    else { amt = m2(v, 'sm'); if (!amt.som && !amt.usd) amt = m2(v, 'py'); }
    return { stamp: Number(a[0]) || 0, kind: kind, text: text, owner: owner, product: String(a[3] || ''), qty: Number(a[4]) || 0, price: m2(v, 'pr'), amount: amt };
  }
  /** Hujjatlar: bir soniyada bir turdagi (bir egasi) qatorlar — bitta hujjat. Yangisi birinchi. */
  function docs(entries) {
    var map = {};
    var order = [];
    entries.forEach(function (e) {
      var k = e.stamp + '|' + e.kind + '|' + e.text + '|' + (e.owner ? e.owner.storeId + e.owner.name : '');
      if (!map[k]) { map[k] = { stamp: e.stamp, kind: e.kind, text: e.text, owner: e.owner, lines: [], som: 0, usd: 0 }; order.push(map[k]); }
      var d = map[k];
      d.lines.push(e);
      d.som += e.amount.som; d.usd += e.amount.usd;
    });
    order.forEach(function (d) {
      d.key = fold((d.owner ? d.owner.name : '') + ' ' + d.text + ' ' + d.lines.map(function (l) { return l.product; }).join(' '));
    });
    return order.sort(function (a, b) { return b.stamp - a.stamp; });
  }
  function stampDate(st) { var s = String(st); return s.slice(6, 8) + '.' + s.slice(4, 6) + '.' + s.slice(0, 4); }
  function stampTime(st) { var s = String(st); return s.slice(8, 10) + ':' + s.slice(10, 12); }

  var Core = {
    roundN: roundN, round2: round2, plain: plain, qty: qty, parseUser: parseUser, parseNum: parseNum, display: display, money: money,
    timestamp: timestamp, reportDate: reportDate, cleanText: cleanText, CURRENCY: CURRENCY, MODE: MODE, priceTypeCode: priceTypeCode,
    order: order, payment: payment, report: report, oid: oid, proof: proof, PROOF_ITER: PROOF_ITER, newKey: newKey, isKey: isKey,
    normalizeUrl: normalizeUrl, decodeCode: decodeCode, b64: b64, unb64: unb64, unpack: unpack, gunzip: gunzip, setCrypto: setCrypto,
    parseCatalog: parseCatalog, unitPrice: unitPrice, saleJson: saleJson, fold: fold, matches: matches,
    entry: entry, docs: docs, stampDate: stampDate, stampTime: stampTime,
  };
  if (typeof module !== 'undefined' && module.exports) module.exports = Core;
  else root.Core = Core;
})(typeof self !== 'undefined' ? self : this);
