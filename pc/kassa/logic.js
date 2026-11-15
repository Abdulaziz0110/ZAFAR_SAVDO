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

  function norm(s) {
    return String(s || '').toLowerCase().replace(/[ʻʼ'`‘’]/g, "'").replace(/\s+/g, ' ').trim();
  }

  /** Qidiruv: nom so'zlari (hammasi), kod (boshi), narx (tanlangan narx turida, 3+ raqam). */
  function search(goods, q, ptype, rate, limit) {
    q = norm(q);
    if (!q) return [];
    var words = q.split(' ');
    var digits = /^\d+$/.test(q) ? q : '';
    var priceC = digits.length >= 3 ? cents(digits) : -1;
    var out = [];
    for (var i = 0; i < goods.length; i++) {
      var g = goods[i];
      var rank = -1;
      if (digits && g.c === digits) rank = 0;
      else if (digits && g.c.indexOf(digits) === 0) rank = 1;
      else if (priceC > 0 && priceOf(g, ptype, rate) === priceC) rank = 2;
      else {
        var n = g._n || (g._n = norm(g.n));
        var ok = true;
        for (var w = 0; w < words.length; w++) if (n.indexOf(words[w]) < 0) { ok = false; break; }
        if (ok) rank = n.indexOf(words[0]) === 0 ? 3 : 4;
      }
      if (rank >= 0) out.push([rank, g]);
    }
    out.sort(function (a, b) { return a[0] - b[0] || (a[1].n < b[1].n ? -1 : a[1].n > b[1].n ? 1 : 0); });
    return out.slice(0, limit || 60).map(function (x) { return x[1]; });
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
    fmtMoney: fmtMoney, fmtQty: fmtQty, centsText: centsText, milliText: milliText, newId: newId
  };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.KassaLogic = api;
})(typeof window !== 'undefined' ? window : globalThis);
