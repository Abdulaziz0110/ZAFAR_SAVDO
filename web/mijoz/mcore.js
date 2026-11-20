/* Mijoz (iPhone veb-ilova) — sof mantiq: kirish (PBKDF2 + AES-GCM), ma'lumotni ochish, narxlar, buyurtma.
 * Android 2-ilova (mijoz) bilan bir xil formatlar: shared/Login.kt, report/Snapshot.kt, Orders.kt. */
(function (root) {
  'use strict';
  var Core = root.Core || (typeof require === 'function' ? require('./core.js') : null);
  var subtle = function () { return root.crypto.subtle; };
  var enc = function (s) { return new TextEncoder().encode(s); };
  function hex(buf) { return Array.prototype.map.call(new Uint8Array(buf), function (b) { return (b < 16 ? '0' : '') + b.toString(16); }).join(''); }

  /** "Do'kon kodi" -> Firebase manzili (ShopCode.toUrl). null — noto'g'ri. */
  function shopUrl(code0) {
    var code = String(code0 || '').trim().toLowerCase().replace(/\/+$/, '');
    if (!code) return null;
    if (code.indexOf('.') >= 0) {
      var u = Core.normalizeUrl(code);
      return /^https:\/\/[a-z0-9.-]+\.(firebaseio\.com|firebasedatabase\.app)$/.test(u) ? u : null;
    }
    var parts = code.split('@');
    var project = parts[0].replace(/-default-rtdb$/, '');
    if (!/^[a-z0-9][a-z0-9-]{3,62}$/.test(project)) return null;
    if (parts.length === 2 && /^[a-z0-9-]{3,30}$/.test(parts[1])) return 'https://' + project + '-default-rtdb.' + parts[1] + '.firebasedatabase.app';
    return parts.length === 1 ? 'https://' + project + '-default-rtdb.firebaseio.com' : null;
  }

  /** Telefon: faqat raqamlar, 998 bilan (Phone.normalize). */
  function phone(raw) {
    var d = String(raw || '').replace(/\D/g, '');
    if (d.length === 9) return '998' + d;
    if (d.length === 13 && d.indexOf('0998') === 0) return d.slice(1);
    return d;
  }
  function phoneOk(raw) { var n = phone(raw).length; return n >= 9 && n <= 15; }
  function prettyPhone(p) {
    var d = phone(p);
    return d.length === 12 && d.indexOf('998') === 0 ? '+998 ' + d.slice(3, 5) + ' ' + d.slice(5, 8) + ' ' + d.slice(8, 10) + ' ' + d.slice(10) : '+' + d;
  }

  /** a/{H}: H = sha256("savdo-login-v1|" + baza + "|" + telefon), 40 belgi (LoginRecord.key). */
  function loginKey(dbUrl, ph) {
    return subtle().digest('SHA-256', enc('savdo-login-v1|' + Core.normalizeUrl(dbUrl) + '|' + phone(ph))).then(function (d) { return hex(d).slice(0, 40); });
  }

  /** Yozuvni parol bilan ochadi (LoginRecord.decrypt). @return {readKey, queueKey, clientName, memberName, clientId}; xato: 'wrong_password' / 'bad_record'. */
  function decryptGrant(rec, password) {
    if (!rec || typeof rec !== 'object' || !rec.s || !rec.c) return Promise.reject(new Error('bad_record'));
    var salt = Core.unb64(rec.s);
    var blob = Core.unb64(rec.c);
    if (blob.length < 28) return Promise.reject(new Error('bad_record'));
    var iter = Math.min(2000000, Math.max(1000, Number(rec.i) || 120000));
    return subtle().importKey('raw', enc(password), 'PBKDF2', false, ['deriveKey']).then(function (k) {
      return subtle().deriveKey({ name: 'PBKDF2', hash: 'SHA-256', salt: salt, iterations: iter }, k, { name: 'AES-GCM', length: 256 }, false, ['decrypt']);
    }).then(function (key) {
      return subtle().decrypt({ name: 'AES-GCM', iv: blob.slice(0, 12), tagLength: 128 }, key, blob.slice(12)).catch(function () {
        throw new Error('wrong_password');
      });
    }).then(function (plain) {
      var p = JSON.parse(new TextDecoder().decode(plain));
      return { readKey: String(p.r || ''), queueKey: String(p.q || ''), clientName: String(p.n || ''), memberName: String(p.m || ''), clientId: String(p.cid || '') };
    });
  }

  /** Bulutdagi ma'lumot (SnapshotCodec.toJson) -> obyekt. */
  function parseSnapshot(text) {
    var o = typeof text === 'string' ? JSON.parse(text) : text;
    if ((o.v || 1) > 1) throw new Error('too_new');
    var num = function (x) { return Number(x) || 0; };
    var products = (o.p || []).map(function (l) {
      var p = { id: String(l[0]), name: String(l[1] || ''), group: String(l[2] || ''), cashSom: num(l[3]), cashUsd: num(l[4]),
        whSom: num(l[5]), whUsd: num(l[6]), plu: l[7] == null ? '' : String(l[7]), l12: Array.isArray(l[8]) ? l[8].map(num) : [] };
      p.key = Core.fold(p.name + ' ' + p.group + ' ' + p.plu);
      return p;
    }).filter(function (p) { return p.name; });
    var ledger = (o.l || []).map(function (a) { try { return Core.entry(a); } catch (e) { return null; } }).filter(Boolean);
    return {
      at: num(o.at), clientId: String(o.cid || ''), clientName: String(o.cn || ''), rate: num(o.rate), pricesAt: num(o.pat),
      debt: o.debt ? { som: num(o.debt.s), usd: num(o.debt.u), at: num(o.debt.at), src: String(o.debt.src || '') } : null,
      products: products, ledger: ledger,
      coverage: (o.cov || []).map(function (c) { return { from: num(c[0]), to: num(c[1]), at: num(c[2]), src: String(c[3] || '') }; }),
      balance: Array.isArray(o.rb) ? { stamp: num(o.rb[0]), som: num(o.rb[1]), usd: num(o.rb[2]) } : null,
      features: o.f || [], shop: String(o.shop || ''), footer: String(o.foot || ''),
    };
  }

  /** 1..4-narx (PriceItem.price): 3 — Naqd, 4 — Optom, 1/2 — 1C narxi (yo'q bo'lsa Optom). */
  function price(p, level, usd) {
    if (level === 3) return usd ? p.cashUsd : p.cashSom;
    if (level === 1 || level === 2) {
      var v = p.l12[(level - 1) * 2 + (usd ? 1 : 0)];
      if (v > 0) return v;
    }
    return usd ? p.whUsd : p.whSom;
  }

  var ALPHA = 'abcdefghijkmnpqrstuvwxyz23456789';
  /** OrderCodec.newId: "o" + ms + 4 belgi. */
  function orderId(now) {
    var a = new Uint8Array(4); root.crypto.getRandomValues(a);
    var s = 'o' + String(now || Date.now());
    for (var i = 0; i < 4; i++) s += ALPHA[a[i] % ALPHA.length];
    return s;
  }
  /** OrderCodec.toCloud — 1-ilova (Admin) buyurtmani savatchaga oladi. */
  function orderBody(note, source, lines, by) {
    var l = lines.slice(0, 200).map(function (x) { return [String(x.name).slice(0, 200), x.qty, String(x.id || '').slice(0, 64)]; });
    var o = { at: { '.sv': 'timestamp' }, v: 1, src: String(source || '').slice(0, 200), n: String(note || '').slice(0, 300), l: JSON.stringify(l) };
    if (by) o.by = String(by).slice(0, 80);
    return o;
  }
  var STATUS = { '': 'Yuborildi', seen: "Ko'rildi", taken: 'Qabul qilindi', rejected: 'Rad etildi' };
  function orderFrom(id, v) {
    if (!v || typeof v !== 'object') return null;
    var lines = [];
    try { lines = (JSON.parse(v.l || '[]') || []).map(function (x) { return { name: String(x[0] || ''), qty: Number(x[1]) || 0, id: String(x[2] || '') }; }); } catch (e) { lines = []; }
    var st = v.st && typeof v.st === 'object' ? String(v.st.s || '') : '';
    return { id: id, at: Number(v.at) || 0, note: String(v.n || ''), lines: lines.filter(function (x) { return x.name && x.qty > 0; }),
      status: st, statusText: STATUS[st] || STATUS[''], statusAt: v.st ? Number(v.st.at) || 0 : 0 };
  }

  /** Kunlar bo'yicha guruh (yangisi birinchi): [{day, docs}]. */
  function byDay(docs) {
    var out = [], cur = null;
    docs.forEach(function (d) {
      var day = Math.floor(d.stamp / 1000000);
      if (!cur || cur.day !== day) { cur = { day: day, docs: [] }; out.push(cur); }
      cur.docs.push(d);
    });
    return out;
  }

  var M = { shopUrl: shopUrl, phone: phone, phoneOk: phoneOk, prettyPhone: prettyPhone, loginKey: loginKey, decryptGrant: decryptGrant,
    parseSnapshot: parseSnapshot, price: price, orderId: orderId, orderBody: orderBody, orderFrom: orderFrom, byDay: byDay, STATUS: STATUS };
  if (typeof module !== 'undefined' && module.exports) module.exports = M;
  else root.MCore = M;
})(typeof self !== 'undefined' ? self : this);
