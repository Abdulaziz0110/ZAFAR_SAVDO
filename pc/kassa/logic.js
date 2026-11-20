/* v5.4 kassa: sof mantiq (brauzer va node testlari). Pul — tiyin (butun son), miqdor — 1/1000. kassa_core.py bilan bir xil. */
(function (root) {
  'use strict';

  var PRICE_TYPES = ['Цена1', 'Цена2', 'Цена3', 'Цена4'];

  function digitsNumber(raw) {
    var s = String(raw == null ? '' : raw).replace(/[^0-9]/g, '');
    if (!s) return '';
    s = s.replace(/^0+/, '');
    return s === '' ? '0' : s;
  }

  function num(s) {
    s = String(s || '').replace(/[^0-9]/g, '');
    return s ? parseInt(s, 10) : 0;
  }

  /** 1C ПолучитьНачалоБуква + ПолеТекПриИзменении. @return {code, milli, weighted} */
  function parseScan(raw, rongta) {
    raw = String(raw == null ? '' : raw).trim();
    var t = digitsNumber(raw);
    if (!t) return { code: '', milli: 0, weighted: false };
    if (t.indexOf('29') === 0) return { code: t.slice(0, 7), milli: num(t.slice(7, 12)), weighted: true };
    if (t.indexOf('28') === 0) return { code: t.slice(0, 7), milli: (rongta ? num(t.slice(7, 9)) : num(t.slice(7, 12))) * 1000, weighted: false };
    if (t.indexOf('21') === 0) return { code: t, milli: 1000, weighted: false };
    if (t.indexOf('25') === 0) return { code: t.slice(0, 12), milli: 1000, weighted: false };
    if (raw.charAt(0) === '0') return { code: raw, milli: 1000, weighted: false };
    return { code: t, milli: 1000, weighted: false };
  }

  /** Decimal(str(x)).quantize(10^-places, ROUND_HALF_UP) butun son sifatida (x * 10^places). */
  function scaled(x, places) {
    if (x == null || x === '') return 0;
    var s = typeof x === 'number' ? String(x) : String(x).replace(',', '.').replace(/\s/g, '');
    if (!/^-?\d*\.?\d*(e[-+]?\d+)?$/i.test(s) || s === '' || s === '-' || s === '.') return 0;
    if (/e/i.test(s)) return Math.round(parseFloat(s) * Math.pow(10, places));
    var neg = s.charAt(0) === '-';
    if (neg) s = s.slice(1);
    var parts = s.split('.');
    var ip = parts[0] || '0';
    var fp = (parts[1] || '');
    var keep = (fp + new Array(places + 2).join('0')).slice(0, places);
    var next = fp.length > places ? parseInt(fp.charAt(places), 10) : 0;
    var v = parseInt(ip, 10) * Math.pow(10, places) + (keep ? parseInt(keep, 10) : 0);
    if (next >= 5) v += 1;
    return neg ? -v : v;
  }

  function cents(x) { return scaled(x, 2); }
  function milli(x) { return scaled(x, 3); }

  function divRound(a, b) {
    // a, b >= 0 butun; ROUND_HALF_UP
    var q = Math.floor(a / b);
    var r = a - q * b;
    if (r * 2 >= b) q += 1;
    return q;
  }

  function priceIndex(ptype) {
    var i = PRICE_TYPES.indexOf(ptype);
    return i < 0 ? 0 : i;
  }

  /** goods: {u, p:[4], v:[4]} -> tiyin. $ tovar: ПродажВалN × Курс (4 xona) — 2 xonaga. */
  function priceOf(g, ptype, rate) {
    var i = priceIndex(ptype);
    if (g.u) {
      var v = scaled((g.v || [])[i] || 0, 4);
      var r = scaled(rate || 0, 4);
      if (typeof BigInt === 'function') {
        var p = BigInt(v) * BigInt(r);
        var b = BigInt(1000000);
        var q = p / b;
        if ((p - q * b) * BigInt(2) >= b) q += BigInt(1);
        return Number(q);
      }
      return divRound(v * r, 1000000);
    }
    return cents((g.p || [])[i] || 0);
  }

  function lineSum(priceCents, qtyMilli) {
    return divRound(priceCents * qtyMilli, 1000);
  }

  function total(lines) {
    var s = 0;
    for (var i = 0; i < lines.length; i++) s += lineSum(lines[i].price, lines[i].milli);
    return s;
  }

  /** 1C ЗапистьКолво. @return {milli, capped} */
  function capQty(wantMilli, stock, allowNeg) {
    var have = milli(stock);
    if (allowNeg || wantMilli <= have) return { milli: wantMilli, capped: false };
    return { milli: have > 0 ? have : 0, capped: true };
  }

  /** kassa_core.settle bilan bir xil (xato — {e}). */
  function settle(totalC, discount, cash, card, click, hasClient, isReturn) {
    if ([totalC, discount, cash, card, click].some(function (v) { return v < 0; })) return { e: "Summa manfiy bo'lishi mumkin emas" };
    if (discount > totalC) return { e: 'Chegirma jami summadan katta' };
    var due = totalC - discount;
    if (due === 0) { discount = 0; due = totalC; }
    var paid = cash + card + click;
    var change = 0;
    if (isReturn) {
      if (totalC <= 0) return { e: "Qaytariladigan tovar yo'q" };
      var restR = due - paid;
      if (restR !== 0 && !hasClient) return { e: "Avval mijozni tanlang (pul to'liq qaytarilmayapti)" };
      return { total: totalC, discount: discount, due: due, cash: cash, card: card, click: click, change: 0, debt: restR };
    }
    if (totalC === 0 && paid === 0) return { e: "Savat bo'sh" };
    if (totalC === 0 && !hasClient) return { e: "Qarz to'lovi uchun mijozni tanlang" };
    if (paid > due && !hasClient) {
      var over = paid - due;
      if (cash < over) return { e: "Plastik/Click summasi to'lanadigandan ko'p" };
      change = over; cash -= over; paid = due;
    }
    var rest = due - paid;
    if (rest !== 0 && !hasClient) return { e: "Avval mijozni tanlang (to'liq to'lanmadi — qarzga yoziladi)" };
    return { total: totalC, discount: discount, due: due, cash: cash, card: card, click: click, change: change, debt: rest };
  }

  // ---------------- qidiruv: telefondagi ilova bilan BIR XIL algoritm (shared/Search.kt dan ko'chirilgan) -------------
  var CYR = {
    'а': 'a', 'б': 'b', 'в': 'v', 'г': 'g', 'д': 'd', 'е': 'e', 'ё': 'yo', 'ж': 'j', 'з': 'z', 'и': 'i', 'й': 'y',
    'к': 'k', 'л': 'l', 'м': 'm', 'н': 'n', 'о': 'o', 'п': 'p', 'р': 'r', 'с': 's', 'т': 't', 'у': 'u', 'ф': 'f',
    'х': 'x', 'ц': 's', 'ч': 'ch', 'ш': 'sh', 'щ': 'sh', 'ъ': '', 'ы': 'i', 'ь': '', 'э': 'e', 'ю': 'yu', 'я': 'ya',
    'ў': 'o', 'қ': 'q', 'ғ': 'g', 'ҳ': 'h', 'і': 'i', 'ї': 'i', 'є': 'e', 'ґ': 'g'
  };
  var APOS = "'’ʻʼ‘`´ʹ";
  var LETTER = /[\p{L}\p{N}]/u;

  /** Buklash: kichik harf, kirill -> lotin, tutuq belgisisiz, w -> v, qolgani bo'shliq. */
  function fold(input) {
    var s = String(input == null ? '' : input).toLowerCase();
    var out = '';
    var lastSpace = true;
    for (var i = 0; i < s.length; i++) {
      var ch = s.charAt(i);
      var piece;
      if (Object.prototype.hasOwnProperty.call(CYR, ch)) piece = CYR[ch];
      else if (APOS.indexOf(ch) >= 0) piece = '';
      else if (ch === 'w') piece = 'v';
      else if (LETTER.test(ch)) piece = ch;
      else piece = ' ';
      for (var j = 0; j < piece.length; j++) {
        var p = piece.charAt(j);
        if (p === ' ') { if (lastSpace) continue; lastSpace = true; } else lastSpace = false;
        out += p;
      }
    }
    return out.replace(/ +$/, '');
  }

  /** Talaffuzga yaqin: h~x, q~k, qo'sh harf bitta. */
  function loose(t) {
    var out = '';
    for (var i = 0; i < t.length; i++) {
      var c = t.charAt(i);
      if (c === 'h') c = 'x'; else if (c === 'q') c = 'k';
      if (out && out.charAt(out.length - 1) === c && !/[0-9]/.test(c)) continue;
      out += c;
    }
    return out;
  }

  var RU = 'йцукенгшщзхъфывапролджэячсмитьбю';
  var EN = "qwertyuiop[]asdfghjkl;'zxcvbnm,.";
  /** Klaviatura tartibi adashganda (ЙЦУКЕН <-> QWERTY). */
  function swapLayout(q) {
    var lower = String(q).toLowerCase();
    var hasCyr = false;
    for (var i = 0; i < lower.length; i++) if (RU.indexOf(lower.charAt(i)) >= 0) { hasCyr = true; break; }
    var out = '';
    for (var k = 0; k < lower.length; k++) {
      var c = lower.charAt(k);
      var idx = hasCyr ? RU.indexOf(c) : EN.indexOf(c);
      out += idx >= 0 ? (hasCyr ? EN : RU).charAt(idx) : c;
    }
    return out;
  }

  function isDigit(c) { return c >= '0' && c <= '9'; }

  /** So'zlar: bo'shliq va harf/raqam chegarasida ("mayiz134" -> mayiz, 134). */
  function tokens(f) {
    var out = [];
    var i = 0;
    while (i < f.length) {
      if (f.charAt(i) === ' ') { i++; continue; }
      var start = i;
      var d = isDigit(f.charAt(i));
      while (i < f.length && f.charAt(i) !== ' ' && isDigit(f.charAt(i)) === d) i++;
      var t = f.slice(start, i);
      out.push({ text: t, loose: loose(t), digits: d });
    }
    return out;
  }

  /** Optimal string alignment (Damerau-Levenshtein). */
  function osa(a, b, max) {
    if (a === b) return 0;
    if (Math.abs(a.length - b.length) > max) return max + 1;
    var n = a.length, m = b.length, j;
    var prev2 = new Array(m + 1).fill(0), prev = [], cur = new Array(m + 1).fill(0);
    for (j = 0; j <= m; j++) prev.push(j);
    for (var i = 1; i <= n; i++) {
      cur[0] = i;
      var rowMin = cur[0];
      for (j = 1; j <= m; j++) {
        var cost = a.charAt(i - 1) === b.charAt(j - 1) ? 0 : 1;
        var v = Math.min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost);
        if (i > 1 && j > 1 && a.charAt(i - 1) === b.charAt(j - 2) && a.charAt(i - 2) === b.charAt(j - 1)) v = Math.min(v, prev2[j - 2] + 1);
        cur[j] = v;
        if (v < rowMin) rowMin = v;
      }
      if (rowMin > max) return max + 1;
      var t = prev2; prev2 = prev; prev = cur; cur = t;
    }
    return prev[m];
  }

  /** Bitta so'rov so'zi va bitta nom so'zi: [ball, imlo tuzatildimi] yoki null. */
  function tokenScore(qt, nt, fuzzy) {
    var q = qt.text, n = nt.text;
    if (n === q) return [100, false];
    if (n.indexOf(q) === 0) return [85 - Math.min(15, n.length - q.length), false];
    if (qt.digits || nt.digits) {
      if (qt.digits && q.length >= 3 && n.indexOf(q) > 0) return [50, false];
      return null;
    }
    if (nt.loose === qt.loose) return [80, true];
    if (qt.loose.length >= 2 && nt.loose.indexOf(qt.loose) === 0) return [70, true];
    if (q.length >= 2 && n.indexOf(q) > 0) return [55, false];
    if (!fuzzy || q.length < 4) return null;
    var maxD = q.length >= 7 ? 2 : 1;
    var bestD = 1e9;
    for (var len = q.length - 1; len <= q.length + 1; len++) {
      if (len < 3 || len > n.length) continue;
      var d = osa(q, n.slice(0, len), maxD);
      if (d < bestD) bestD = d;
    }
    if (bestD <= maxD) return [45 - 10 * bestD, true];
    return null;
  }

  function makeIndex(items, nameOf, codeOf, extraOf) {
    return items.map(function (it, i) {
      var f = fold(nameOf(it));
      return { item: it, folded: f, compact: f.replace(/ /g, ''), toks: tokens(f),
        code: codeOf ? fold(codeOf(it)).replace(/ /g, '') : '', extra: extraOf ? tokens(fold(extraOf(it))) : [], order: i };
    });
  }

  function bestMatch(e, qt, fuzzy) {
    var best = null;
    function offer(score, ti, fz) { if (!best || score > best.score) best = { score: score, ti: ti, fuzzy: fz }; }
    if (e.code) {
      if (e.code === qt.text) offer(120, -1, false);
      else if (qt.text.length >= 2 && e.code.indexOf(qt.text) === 0) offer(90, -1, false);
    }
    for (var ti = 0; ti < e.toks.length; ti++) {
      var s = tokenScore(qt, e.toks[ti], fuzzy);
      if (s) offer(s[0], ti, s[1]);
    }
    for (var x = 0; x < e.extra.length; x++) {
      var s2 = tokenScore(qt, e.extra[x], false);
      if (s2) offer(s2[0] * 0.5, -1, false);
    }
    if (!best && qt.text.length >= 4 && !qt.digits && e.compact.indexOf(qt.text) >= 0) offer(40, -1, true);
    return best;
  }

  function runQuery(index, q, fuzzy, pop, approximate) {
    var qt = tokens(q);
    if (!qt.length) return [];
    var qCompact = q.replace(/ /g, '');
    var out = [], partial = [];
    for (var k = 0; k < index.length; k++) {
      var e = index[k];
      var total = 0, matched = 0, fz = false, lastPos = -1, inOrder = true, firstFirst = false;
      for (var qi = 0; qi < qt.length; qi++) {
        var m = bestMatch(e, qt[qi], fuzzy);
        if (!m) continue;
        matched++;
        total += m.score;
        if (m.fuzzy) fz = true;
        if (m.ti >= 0) {
          if (m.ti < lastPos) inOrder = false;
          lastPos = m.ti;
          if (qi === 0 && m.ti === 0) firstFirst = true;
        }
      }
      if (!matched) continue;
      if (firstFirst) total += 25;
      if (inOrder && qt.length > 1) total += 15;
      if (e.folded.indexOf(q) === 0) total += 20;
      if (e.folded === q) total += 40;
      if (e.code && e.code === qCompact) total += 60;
      total -= Math.min(20, e.folded.length * 0.08);
      if (pop) total += Math.min(25, pop(e.item) || 0);
      var hit = { e: e, score: total, approx: approximate || fz || matched < qt.length };
      if (matched === qt.length) out.push(hit);
      else if (qt.length >= 2 && matched >= qt.length - 1) partial.push(hit);
    }
    if (!out.length) return partial.map(function (h) { h.score *= 0.6; h.approx = true; return h; });
    return out;
  }

  /** Telefondagi qidiruv: so'zlar istalgan tartibda, lotin/kirill, imlo xatolari, h/x q/k, klaviatura tartibi. */
  function query(index, raw, pop, limit) {
    var q = fold(raw);
    if (!q) return [];
    var hits = runQuery(index, q, true, pop, false);
    if (hits.filter(function (h) { return !h.approx; }).length < 3) {
      var alt = fold(swapLayout(raw));
      if (alt && alt !== q) {
        var have = {};
        hits.forEach(function (h) { have[h.e.order] = 1; });
        var strongest = hits.reduce(function (a, h) { return Math.max(a, h.score); }, 0);
        var pen = strongest >= 80 ? 0.5 : 0.9;
        runQuery(index, alt, true, pop, true).forEach(function (h) {
          if (!have[h.e.order]) { h.score *= pen; h.approx = true; hits.push(h); }
        });
      }
    }
    hits.sort(function (a, b) { return b.score - a.score || a.e.order - b.e.order; });
    return hits.slice(0, limit || 60);
  }

  function popularity(g) { return g.f ? Math.min(25, 6 * Math.log(1 + g.f)) : 0; }

  /** Tovar qidiruvi: nom (telefondagi algoritm), kod, narx (tanlangan narx turida, 3+ raqam). */
  function search(goods, q, ptype, rate, limit) {
    if (!goods.length) return [];
    if (!goods.__idx) Object.defineProperty(goods, '__idx', { value: makeIndex(goods, function (g) { return g.n; }, function (g) { return g.c; }), enumerable: false, writable: true, configurable: true });
    var hits = query(goods.__idx, q, popularity, 400);
    var raw = String(q || '').replace(/\s/g, '');
    if (/^\d{3,}$/.test(raw)) {
      var priceC = cents(raw);
      var have = {};
      hits.forEach(function (h) { have[h.e.order] = 1; });
      goods.__idx.forEach(function (e) {
        if (!have[e.order] && priceOf(e.item, ptype, rate) === priceC) hits.push({ e: e, score: 75 + popularity(e.item), approx: false });
      });
      hits.sort(function (a, b) { return b.score - a.score || a.e.order - b.e.order; });
    }
    return hits.slice(0, limit || 60).map(function (h) { return h.e.item; });
  }

  /** Mijoz qidiruvi: ism (xuddi shu algoritm) yoki telefon raqami (3+ raqam). */
  function searchClients(cl, q, limit) {
    if (!cl.length) return [];
    if (!cl.__idx) Object.defineProperty(cl, '__idx', { value: makeIndex(cl, function (c) { return c[1]; }, function (c) { return c[0]; }), enumerable: false, writable: true, configurable: true });
    var digits = String(q || '').replace(/[^0-9]/g, '');
    var hits = query(cl.__idx, q, null, 200);
    if (digits.length >= 3) {
      var have = {};
      hits.forEach(function (h) { have[h.e.order] = 1; });
      cl.__idx.forEach(function (e) {
        if (!have[e.order] && String(e.item[2] || '').replace(/[^0-9]/g, '').indexOf(digits) >= 0) hits.push({ e: e, score: 70, approx: false });
      });
      hits.sort(function (a, b) { return b.score - a.score || a.e.order - b.e.order; });
    }
    return hits.slice(0, limit || 60).map(function (h) { return h.e.item; });
  }

  function fmtMoney(c) {
    var neg = c < 0;
    c = Math.abs(c);
    var s = String(Math.floor(c / 100)).replace(/\B(?=(\d{3})+(?!\d))/g, ' ');
    var f = c % 100;
    return (neg ? '−' : '') + s + (f ? ',' + (f < 10 ? '0' : '') + f : '');
  }

  function fmtQty(m) {
    var s = String(Math.floor(Math.abs(m) / 1000));
    var f = Math.abs(m) % 1000;
    if (f) s += ',' + String(f + 1000).slice(1).replace(/0+$/, '');
    return (m < 0 ? '−' : '') + s;
  }

  function centsText(c) {
    var neg = c < 0; c = Math.abs(c);
    var f = c % 100;
    return (neg ? '-' : '') + Math.floor(c / 100) + '.' + (f < 10 ? '0' : '') + f;
  }

  function milliText(m) {
    var f = m % 1000;
    return Math.floor(m / 1000) + '.' + String(f + 1000).slice(1);
  }

  function newId() {
    var a = '';
    var chars = 'abcdefghijklmnopqrstuvwxyz0123456789';
    var rnd = (root.crypto && root.crypto.getRandomValues) ? root.crypto.getRandomValues(new Uint8Array(16)) : null;
    for (var i = 0; i < 16; i++) a += chars.charAt((rnd ? rnd[i] : Math.floor(Math.random() * 256)) % chars.length);
    return 'k' + Date.now().toString(36) + a;
  }

  var api = {
    PRICE_TYPES: PRICE_TYPES, digitsNumber: digitsNumber, parseScan: parseScan, cents: cents, milli: milli,
    priceOf: priceOf, lineSum: lineSum, total: total, capQty: capQty, settle: settle, search: search,
    searchClients: searchClients, fold: fold, swapLayout: swapLayout, osa: osa,
    fmtMoney: fmtMoney, fmtQty: fmtQty, centsText: centsText, milliText: milliText, newId: newId
  };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.KassaLogic = api;
})(typeof window !== 'undefined' ? window : globalThis);
