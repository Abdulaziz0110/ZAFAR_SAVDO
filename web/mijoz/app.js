/* Savdo Mijoz — iPhone (veb-ilova). Android "Savdo Mijoz" bilan bir xil ma'lumot: narxlar, hisob-kitob (1C tarixi), qarz,
 * buyurtma (do'kon ruxsat bergan bo'lsa). Kirish: do'kon kodi + telefon + parol (do'kon egasi Admin ilovasida beradi). */
(function () {
  'use strict';
  var C = window.Core, M = window.MCore;
  var LS = {
    get: function (k, d) { try { var v = localStorage.getItem('mz_' + k); return v == null ? d : JSON.parse(v); } catch (e) { return d; } },
    set: function (k, v) { try { if (v == null) localStorage.removeItem('mz_' + k); else localStorage.setItem('mz_' + k, JSON.stringify(v)); } catch (e) { /* to'la */ } },
  };
  var LEVELS = [[4, 'Optom'], [3, 'Naqd'], [1, '1-narx'], [2, '2-narx']];
  var KIND = { sale: 'Savdo', return: 'Qaytarish', payment: "To'lov", paymentReturn: "To'lov qaytdi", discount: 'Chegirma', other: 'Boshqa' };
  var S = {
    link: LS.get('link', null), snap: null, metaAt: LS.get('at', 0), tab: 'home', q: '', group: '', level: LS.get('level', 4),
    usd: false, cart: LS.get('cart', {}), note: '', busy: false, waiting: 0, limit: 150, lq: '', orders: null, err: '',
  };
  var app = document.getElementById('app');

  // ---------------- yordamchilar ----------------
  var ICONS = {
    home: '<path d="M3 11 12 3l9 8"/><path d="M5 10v10h14V10"/>',
    tag: '<path d="M20.6 13.4 13.4 20.6a2 2 0 0 1-2.8 0L3 13V3h10l7.6 7.6a2 2 0 0 1 0 2.8z"/><path d="M7.5 7.5h.01"/>',
    book: '<path d="M4 4h11a3 3 0 0 1 3 3v13H7a3 3 0 0 1-3-3z"/><path d="M4 17a3 3 0 0 1 3-3h11"/>',
    cart: '<circle cx="9" cy="20" r="1.5"/><circle cx="18" cy="20" r="1.5"/><path d="M2 3h3l2.5 12h11l2-8H6.2"/>',
    refresh: '<path d="M20 11a8 8 0 1 0-2.3 5.7"/><path d="M20 4v7h-7"/>',
    out: '<path d="M15 4h4a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1h-4"/><path d="M10 17l5-5-5-5M15 12H3"/>',
    back: '<path d="M15 5l-7 7 7 7"/>',
    search: '<circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/>',
    sale: '<path d="M6 2h12v20l-3-2-3 2-3-2-3 2z"/><path d="M9 7h6M9 11h6M9 15h3"/>',
    pay: '<rect x="2.5" y="6" width="19" height="13" rx="2"/><path d="M2.5 10h19M16 14.5h2.5"/>',
    ret: '<path d="M9 14 4 9l5-5"/><path d="M4 9h11a5 5 0 0 1 0 10h-4"/>',
    list: '<path d="M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01"/>',
    store: '<path d="M3 9 5 4h14l2 5M3 9v11h18V9M3 9h18M9 20v-6h6v6"/>',
    send: '<path d="M22 2 11 13M22 2l-7 20-4-9-9-4z"/>',
  };
  function ic(n) { return '<svg class="i" viewBox="0 0 24 24">' + (ICONS[n] || '') + '</svg>'; }
  function esc(s) { return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]; }); }
  function pad(n) { return (n < 10 ? '0' : '') + n; }
  function dt(ms) { if (!ms) return '—'; var d = new Date(ms); return pad(d.getDate()) + '.' + pad(d.getMonth() + 1) + '.' + d.getFullYear() + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes()); }
  function dayText(day) { var s = String(day); return s.slice(6, 8) + '.' + s.slice(4, 6) + '.' + s.slice(0, 4); }
  function money(v, usd) { return C.money(v, usd); }
  function qt(q) { return Math.abs(q - Math.round(q)) < 1e-9 ? String(Math.round(q)) : C.qty(q); }
  function toast(t) {
    var el = document.createElement('div'); el.className = 'toast'; el.textContent = t; document.body.appendChild(el);
    setTimeout(function () { el.remove(); }, 2800);
  }
  function errText(e) {
    var m = e && e.message || String(e);
    if (m === 'wrong_password') return "Parol noto'g'ri";
    if (m === 'no_phone') return "Bu telefon raqami do'konda ro'yxatdan o'tmagan";
    if (m === 'bad_code') return "Do'kon kodi noto'g'ri";
    if (m === 'bad_phone') return "Telefon raqami noto'g'ri";
    if (m === 'too_new') return "Ilova eski — sahifani yangilang";
    if (/Failed to fetch|NetworkError|Load failed|aborted/i.test(m)) return "Internet yo'q yoki sekin";
    return m;
  }

  function req(url, method, path, body) {
    var ctl = typeof AbortController === 'function' ? new AbortController() : null;
    var t = ctl ? setTimeout(function () { ctl.abort(); }, 45000) : 0;
    var opt = { method: method, cache: 'no-store', signal: ctl ? ctl.signal : undefined };
    if (body !== undefined) { opt.body = JSON.stringify(body); opt.headers = { 'Content-Type': 'application/json' }; }
    return fetch(url + '/' + path + '.json', opt).then(function (r) {
      clearTimeout(t);
      if (r.status === 401 || r.status === 403) { var e = new Error('denied'); e.denied = true; throw e; }
      if (!r.ok) throw new Error('HTTP ' + r.status);
      return r.text().then(function (x) { return x ? JSON.parse(x) : null; });
    }, function (e) { clearTimeout(t); throw e; });
  }
  function rk() { return 's/' + S.link.rk; }

  // ---------------- ma'lumot ----------------
  function loadLocal() {
    var blob = LS.get('blob', null);
    if (!blob) return Promise.resolve(false);
    return C.unpack(blob).then(function (txt) { S.snap = M.parseSnapshot(txt); return true; }).catch(function () { return false; });
  }

  /** Bulutdan oxirgi ma'lumot. askFresh — do'konga "yangilab ber" so'rovi va javobni 3 daqiqagacha kutish. */
  function refresh(askFresh) {
    if (!S.link || S.busy) return Promise.resolve();
    S.busy = true; S.err = ''; render();
    var url = S.link.url, asked = Date.now();
    var first = askFresh ? req(url, 'PUT', rk() + '/req', { '.sv': 'timestamp' }).catch(function () { /* */ }) : Promise.resolve();
    function once() {
      return req(url, 'GET', rk() + '/meta').then(function (meta) {
        if (!meta || typeof meta !== 'object') { var e = new Error('no_data'); e.noData = true; throw e; }
        var at = Number(meta.at) || 0;
        if (at <= S.metaAt && S.snap) return false;
        return req(url, 'GET', rk() + '/blob').then(function (blob) {
          if (typeof blob !== 'string') throw new Error('blob');
          return C.unpack(blob).then(function (txt) {
            S.snap = M.parseSnapshot(txt);
            S.metaAt = at;
            LS.set('blob', blob); LS.set('at', at);
            return true;
          });
        });
      });
    }
    return first.then(once).then(function (changed) {
      if (askFresh && !changed) {
        // Do'kon (Admin telefoni) odatda 1 daqiqada javob beradi
        S.waiting = asked;
        render();
        return poll(asked);
      }
      if (changed) toast("Ma'lumot yangilandi");
    }).catch(function (e) {
      if (e.noData) S.err = "Do'kondan ma'lumot hali kelmagan yoki kirish bekor qilingan. «Yangilash» ni bosing yoki qayta kiring.";
      else S.err = errText(e);
    }).then(function () { S.busy = false; render(); });

    function poll(since) {
      return new Promise(function (resolve) {
        var n = 0;
        (function tick() {
          setTimeout(function () {
            n++;
            once().then(function (ch) {
              if (ch) { S.waiting = 0; toast("Ma'lumot yangilandi"); return resolve(); }
              if (n >= 18) { S.waiting = 0; S.err = "Do'kon hozir javob bermadi — keyinroq qayta urinib ko'ring"; return resolve(); }
              tick();
            }).catch(function () { if (n >= 18) { S.waiting = 0; return resolve(); } tick(); });
          }, 10000);
        })();
        void since;
      });
    }
  }

  function ordersOn() { return !!(S.snap && S.snap.features.indexOf('ord') >= 0); }
  function cartLines() {
    var out = [];
    if (!S.snap) return out;
    var by = {};
    S.snap.products.forEach(function (p) { by[p.id] = p; });
    Object.keys(S.cart).forEach(function (id) {
      var p = by[id], q = Number(S.cart[id]) || 0;
      if (p && q > 0) out.push({ p: p, qty: q, sum: C.round2(M.price(p, S.level, S.usd) * q) });
    });
    return out;
  }
  function setQty(id, q) {
    q = Math.max(0, C.round2(q));
    if (q > 0) S.cart[id] = q; else delete S.cart[id];
    LS.set('cart', S.cart);
  }

  // ---------------- kirish ----------------
  function loginView() {
    var saved = LS.get('last', {});
    // do'kon egasi yuborgan havola: .../mijoz/#s=dokon-kodi (kod o'zi yoziladi)
    var hm = /[#&]s=([^&]+)/.exec(location.hash || '');
    if (hm) { try { saved.code = decodeURIComponent(hm[1]); } catch (e) { /* */ } }
    app.innerHTML = '<header class="bar"><h1>Savdo Mijoz</h1></header><main>' +
      '<div class="hero"><div class="lb">Xush kelibsiz</div><div class="amt" style="font-size:24px">Do\'koningiz hisobini ko\'ring</div>' +
      '<div class="sub">Narxlar, qarz, hisob-kitob va buyurtma — telefoningizda</div></div>' +
      '<div class="card"><label class="f">Do\'kon kodi</label><input id="lCode" autocomplete="off" autocapitalize="off" placeholder="masalan: dokon-1a2b3" value="' + esc(saved.code || '') + '">' +
      '<label class="f">Telefon raqami</label><input id="lPhone" type="tel" inputmode="tel" placeholder="+998 90 123 45 67" value="' + esc(saved.phone || '') + '">' +
      '<label class="f">Parol</label><input id="lPw" type="password" autocomplete="current-password">' +
      '<div class="err" id="lErr"></div><button class="btn" id="lGo">Kirish</button>' +
      '<p class="muted">Kod, telefon va parolni do\'kon egasi beradi (Admin ilovasi ▸ Mijozlar ▸ mijoz ▸ «Kiradigan telefonlar»).</p></div>' +
      '<div class="card"><b>iPhone\'ga o\'rnatish</b><p class="muted">Safari\'da pastdagi <b>Ulashish</b> ▸ <b>«Bosh ekranga qo\'shish»</b>. ' +
      'Ilova alohida belgi bo\'lib ochiladi va internetsiz ham oxirgi ma\'lumotni ko\'rsatadi.</p></div></main>';
    document.getElementById('lGo').onclick = function () {
      var code = document.getElementById('lCode').value, ph = document.getElementById('lPhone').value, pw = document.getElementById('lPw').value;
      var err = document.getElementById('lErr'), btn = this;
      var url = M.shopUrl(code);
      if (!url) { err.textContent = errText(new Error('bad_code')); return; }
      if (!M.phoneOk(ph)) { err.textContent = errText(new Error('bad_phone')); return; }
      if (!pw) { err.textContent = 'Parolni kiriting'; return; }
      btn.disabled = true; btn.innerHTML = '<span class="sp"></span> Tekshirilmoqda…'; err.textContent = '';
      M.loginKey(url, ph).then(function (k) { return req(url, 'GET', 'a/' + k); }).then(function (rec) {
        if (!rec) throw new Error('no_phone');
        return M.decryptGrant(rec, pw);
      }).then(function (g) {
        S.link = { url: url, rk: g.readKey, qk: g.queueKey, cn: g.clientName, member: g.memberName, cid: g.clientId, phone: M.phone(ph) };
        LS.set('link', S.link); LS.set('last', { code: code.trim(), phone: ph });
        if (S.metaAt) { S.metaAt = 0; LS.set('at', 0); LS.set('blob', null); }
        S.snap = null; S.tab = 'home';
        render();
        return refresh(true);
      }).catch(function (e) {
        btn.disabled = false; btn.textContent = 'Kirish';
        err.textContent = errText(e);
      });
    };
  }

  function logout() {
    if (!confirm('Chiqasizmi? Telefondagi ma\'lumot o\'chiriladi.')) return;
    ['link', 'blob', 'at', 'cart', 'orders'].forEach(function (k) { LS.set(k, null); });
    S.link = null; S.snap = null; S.metaAt = 0; S.cart = {};
    render();
  }

  // ---------------- ekranlar ----------------
  function shell(title, body, opts) {
    opts = opts || {};
    var cnt = Object.keys(S.cart).length;
    var tabs = [['home', 'Bosh', 'home'], ['prices', 'Narxlar', 'tag'], ['ledger', 'Hisob', 'book'], ['orders', 'Buyurtma', 'list']];
    var right = opts.right || '<button class="ib" id="bRef" aria-label="Yangilash">' + (S.busy ? '<span class="sp" style="border-color:var(--line);border-top-color:var(--brand)"></span>' : ic('refresh')) + '</button>';
    app.innerHTML = '<header class="bar"><h1>' + esc(title) + '</h1>' + right + '</header><main>' + body + '</main>' +
      (opts.cartBar || '') +
      '<nav class="tabs"><div class="in">' + tabs.map(function (t) {
        return '<button data-tab="' + t[0] + '" class="' + (S.tab === t[0] ? 'on' : '') + '">' + ic(t[2]) + t[1] +
          (t[0] === 'orders' && cnt ? '<span class="badge">' + cnt + '</span>' : '') + '</button>';
      }).join('') + '</div></nav>';
    app.querySelectorAll('[data-tab]').forEach(function (b) { b.onclick = function () { S.tab = b.getAttribute('data-tab'); S.limit = 150; if (S.tab === 'orders') S.orders = null; render(); window.scrollTo(0, 0); }; });
    var r = document.getElementById('bRef'); if (r) r.onclick = function () { refresh(true); };
  }
  function statusBanner() {
    var h = '';
    if (S.waiting) h += '<div class="banner"><span class="sp" style="border-color:var(--amber-soft);border-top-color:var(--amber)"></span> Do\'konga so\'rov yuborildi — yangi ma\'lumot kutilmoqda…</div>';
    if (S.err) h += '<div class="banner">' + esc(S.err) + '</div>';
    if (!navigator.onLine) h += '<div class="banner">Internet yo\'q — oxirgi saqlangan ma\'lumot (' + dt(S.metaAt) + ')</div>';
    return h;
  }

  function homeView() {
    var s = S.snap;
    var name = (s && s.clientName) || S.link.cn || 'Mijoz';
    var h = statusBanner();
    if (!s) {
      h += '<div class="card empty">' + (S.busy ? '<span class="sp" style="border-color:var(--line);border-top-color:var(--brand)"></span><p>Ma\'lumot yuklanmoqda…</p>' :
        '<p>Ma\'lumot hali yo\'q</p><button class="btn" id="bAsk">' + ic('refresh') + ' Do\'kondan so\'rash</button>') + '</div>';
      shell(name, h, { right: '<button class="ib" id="bOut" aria-label="Chiqish">' + ic('out') + '</button>' });
      var a = document.getElementById('bAsk'); if (a) a.onclick = function () { refresh(true); };
      document.getElementById('bOut').onclick = logout;
      return;
    }
    var d = s.debt || { som: 0, usd: 0, at: 0 };
    var owe = d.som > 0.5 || d.usd > 0.005;
    var amt = (d.som || !d.usd ? money(Math.abs(d.som), false) : '') + (d.usd ? (d.som ? ' + ' : '') + money(Math.abs(d.usd), true) : '');
    h += '<div class="hero' + (owe ? '' : ' ok') + '"><div class="lb">' + (owe ? 'Qarzingiz' : (d.som < -0.5 || d.usd < -0.005 ? 'Haqingiz (oldindan to\'langan)' : 'Qarz yo\'q')) + '</div>' +
      '<div class="amt">' + (owe || d.som < -0.5 || d.usd < -0.005 ? esc(amt) : '0 so\'m') + '</div>' +
      '<div class="sub">Holat: ' + dt(d.at || s.at) + (s.rate ? ' · 1 $ = ' + esc(C.display(s.rate, 0)) + ' so\'m' : '') + '</div></div>';
    h += '<div class="tiles">' +
      tile('tPrices', 'tag', ordersOn() ? 'Buyurtma berish' : 'Narxlar') + tile('tLedger', 'book', 'Hisob-kitob') + tile('tOrders', 'list', 'Buyurtmalarim') +
      tile('tRef', 'refresh', 'Yangilash') + tile('tShop', 'store', "Do'kon") + tile('tOut', 'out', 'Chiqish') + '</div>';
    var docs = C.docs(s.ledger).slice(0, 4);
    if (docs.length) {
      h += '<div class="card"><b>Oxirgi harakatlar</b>' + docs.map(docRow).join('') +
        '<button class="btn line" id="bAll">Hammasini ko\'rish</button></div>';
    }
    h += '<p class="muted" style="text-align:center">Ma\'lumot: ' + dt(S.metaAt) + (S.link.member ? ' · ' + esc(S.link.member) : '') + '</p>';
    shell(name, h);
    on('tPrices', function () { S.tab = 'prices'; render(); });
    on('tLedger', function () { S.tab = 'ledger'; render(); });
    on('bAll', function () { S.tab = 'ledger'; render(); });
    on('tOrders', function () { S.tab = 'orders'; S.orders = null; render(); });
    on('tRef', function () { refresh(true); });
    on('tOut', logout);
    on('tShop', shopSheet);
    bindDocs();
  }
  function tile(id, icon, label) { return '<button class="tile" id="' + id + '"><span class="ic">' + ic(icon) + '</span>' + esc(label) + '</button>'; }
  function on(id, fn) { var e = document.getElementById(id); if (e) e.onclick = fn; }

  function shopSheet() {
    var s = S.snap || {};
    sheet("Do'kon", '<p><b>' + esc(s.shop || "Do'kon") + '</b></p>' + (s.footer ? '<p class="muted" style="white-space:pre-line">' + esc(s.footer) + '</p>' : '') +
      '<p class="muted">Kirgan: ' + esc(S.link.member || '') + ' · ' + esc(M.prettyPhone(S.link.phone || '')) + '</p>' +
      '<p class="muted">Narxlar: ' + dt(s.pricesAt) + '</p>');
  }

  // ---- narxlar ----
  function pricesView() {
    var s = S.snap;
    if (!s) { S.tab = 'home'; return homeView(); }
    var groups = [];
    s.products.forEach(function (p) { if (p.group && groups.indexOf(p.group) < 0) groups.push(p.group); });
    groups.sort();
    var anyUsd = s.products.some(function (p) { return p.cashUsd || p.whUsd; });
    var h = '<div class="sticky"><input id="pq" type="search" placeholder="Tovar qidirish…" value="' + esc(S.q) + '">' +
      '<div class="seg">' + LEVELS.filter(function (l) { return l[0] >= 3 || s.products.some(function (p) { return p.l12.length; }); }).map(function (l) {
        return '<button data-lv="' + l[0] + '" class="' + (S.level === l[0] ? 'on' : '') + '">' + l[1] + '</button>';
      }).join('') + (anyUsd ? '<button data-cur="1" class="' + (S.usd ? 'on' : '') + '">$</button>' : '') + '</div>' +
      (groups.length > 1 ? '<div class="chips"><button class="chip ' + (S.group ? '' : 'on') + '" data-g="">Hammasi</button>' +
        groups.map(function (g) { return '<button class="chip ' + (S.group === g ? 'on' : '') + '" data-g="' + esc(g) + '">' + esc(g) + '</button>'; }).join('') + '</div>' : '') +
      '</div><div id="plist"></div>';
    var lines = cartLines(), total = lines.reduce(function (a, l) { return a + l.sum; }, 0);
    var bar = ordersOn() && lines.length ? '<div class="cartbar"><button class="btn" id="bCart">' + ic('cart') + ' Savatcha: ' + lines.length + ' ta · ' + esc(money(total, S.usd)) + '</button></div>' : '';
    shell(ordersOn() ? 'Buyurtma' : 'Narxlar', h, { cartBar: bar });
    var q = document.getElementById('pq');
    q.oninput = function () { S.q = q.value; S.limit = 150; list(); };
    app.querySelectorAll('[data-lv]').forEach(function (b) { b.onclick = function () { S.level = Number(b.getAttribute('data-lv')); LS.set('level', S.level); pricesView(); }; });
    app.querySelectorAll('[data-cur]').forEach(function (b) { b.onclick = function () { S.usd = !S.usd; pricesView(); }; });
    app.querySelectorAll('[data-g]').forEach(function (b) { b.onclick = function () { S.group = b.getAttribute('data-g'); S.limit = 150; pricesView(); }; });
    on('bCart', cartSheet);
    list();

    function list() {
      var qq = C.fold(S.q);
      var found = s.products.filter(function (p) { return (!S.group || p.group === S.group) && (!qq || C.matches(p.key, S.q)); });
      var box = document.getElementById('plist');
      if (!found.length) { box.innerHTML = '<div class="empty">Topilmadi</div>'; return; }
      box.innerHTML = found.slice(0, S.limit).map(function (p) {
        var pr = M.price(p, S.level, S.usd), q0 = S.cart[p.id] || 0;
        return '<div class="item"><div class="n"><div class="t">' + esc(p.name) + '</div><div class="muted">' + esc(p.group) + '</div>' +
          '<div class="price">' + (pr ? esc(money(pr, S.usd)) : '—') + '</div></div>' +
          (ordersOn() && pr ? '<div class="step">' + (q0 ? '<button data-m="' + esc(p.id) + '">−</button><span class="q">' + esc(qt(q0)) + '</span>' : '') +
            '<button class="plus" data-p="' + esc(p.id) + '">+</button></div>' : '') + '</div>';
      }).join('') + (found.length > S.limit ? '<button class="btn line" id="pMore">Yana ' + (found.length - S.limit) + ' ta</button>' : '');
      box.querySelectorAll('[data-p]').forEach(function (b) { b.onclick = function () { var id = b.getAttribute('data-p'); setQty(id, (S.cart[id] || 0) + 1); pricesView(); }; });
      box.querySelectorAll('[data-m]').forEach(function (b) { b.onclick = function () { var id = b.getAttribute('data-m'); setQty(id, (S.cart[id] || 0) - 1); pricesView(); }; });
      on('pMore', function () { S.limit += 300; list(); });
    }
  }

  function cartSheet() {
    var lines = cartLines();
    var total = lines.reduce(function (a, l) { return a + l.sum; }, 0);
    var body = lines.map(function (l) {
      return '<div class="item"><div class="n"><div class="t">' + esc(l.p.name) + '</div><div class="muted">' + esc(money(M.price(l.p, S.level, S.usd), S.usd)) + '</div></div>' +
        '<div class="step"><button data-cm="' + esc(l.p.id) + '">−</button><span class="q">' + esc(qt(l.qty)) + '</span><button class="plus" data-cp="' + esc(l.p.id) + '">+</button></div></div>';
    }).join('') +
      '<div class="tot b"><span>Jami (taxminiy)</span><span>' + esc(money(total, S.usd)) + '</span></div>' +
      '<label class="f">Izoh (ixtiyoriy)</label><textarea id="cNote" placeholder="Masalan: ertaga ertalab olib ketaman">' + esc(S.note) + '</textarea>' +
      '<p class="muted">Bu savdo emas — buyurtma do\'konga boradi, do\'kon egasi ko\'rib tayyorlaydi. Yakuniy narx do\'konda belgilanadi.</p>' +
      '<div class="err" id="cErr"></div><button class="btn" id="cSend">' + ic('send') + ' Buyurtmani yuborish</button>' +
      '<button class="btn line" id="cClear">Savatchani tozalash</button>';
    var sh = sheet('Savatcha', body);
    sh.querySelectorAll('[data-cp]').forEach(function (b) { b.onclick = function () { var id = b.getAttribute('data-cp'); setQty(id, (S.cart[id] || 0) + 1); close(); cartSheet(); }; });
    sh.querySelectorAll('[data-cm]').forEach(function (b) {
      b.onclick = function () { var id = b.getAttribute('data-cm'); setQty(id, (S.cart[id] || 0) - 1); close(); if (cartLines().length) cartSheet(); else render(); };
    });
    sh.querySelector('#cNote').oninput = function () { S.note = this.value; };
    sh.querySelector('#cClear').onclick = function () { S.cart = {}; LS.set('cart', S.cart); close(); render(); };
    sh.querySelector('#cSend').onclick = function () {
      var btn = this, err = sh.querySelector('#cErr');
      btn.disabled = true; btn.innerHTML = '<span class="sp"></span> Yuborilmoqda…';
      var id = M.orderId(Date.now());
      var body0 = M.orderBody(S.note, 'Narxlar (iPhone)', cartLines().map(function (l) { return { name: l.p.name, qty: l.qty, id: l.p.id }; }), S.link.member);
      req(S.link.url, 'PUT', rk() + '/ord/' + id, body0).then(function () {
        var mine = LS.get('orders', []); mine.push(id); LS.set('orders', mine.slice(-50));
        S.cart = {}; S.note = ''; LS.set('cart', S.cart);
        close(); S.tab = 'orders'; S.orders = null; render();
        toast("Buyurtma yuborildi");
      }).catch(function (e) {
        btn.disabled = false; btn.innerHTML = ic('send') + ' Buyurtmani yuborish';
        err.textContent = e.denied ? "Buyurtma qabul qilinmadi — do'kon buyurtmani yopgan bo'lishi mumkin" : errText(e);
      });
    };
    function close() { var o = document.querySelector('.overlay'); if (o) o.remove(); }
  }

  // ---- hisob-kitob ----
  function docRow(d) {
    var k = d.kind === 'payment' || d.kind === 'paymentReturn' ? 'pay' : d.kind === 'return' ? 'ret' : '';
    var icon = k === 'pay' ? 'pay' : k === 'ret' ? 'ret' : 'sale';
    var amt = (d.som ? money(Math.abs(d.som), false) : '') + (d.usd ? (d.som ? ' + ' : '') + money(Math.abs(d.usd), true) : '');
    return '<div class="doc" data-doc="' + d.stamp + '|' + esc(d.kind) + '|' + esc(d.text) + '"><span class="k ' + k + '">' + ic(icon) + '</span>' +
      '<div class="m"><div><b>' + esc(KIND[d.kind] || d.kind) + '</b>' + (d.text && d.text !== KIND[d.kind] ? ' · ' + esc(d.text) : '') + '</div>' +
      '<div class="muted">' + C.stampDate(d.stamp) + ' ' + C.stampTime(d.stamp) + (d.kind === 'sale' || d.kind === 'return' ? ' · ' + d.lines.length + ' qator' : '') + '</div></div>' +
      '<div class="a' + (k === 'pay' ? ' plus' : '') + '">' + esc(amt || '0') + '</div></div>';
  }
  function bindDocs() {
    app.querySelectorAll('[data-doc]').forEach(function (el) {
      el.onclick = function () {
        var key = el.getAttribute('data-doc');
        var d = C.docs(S.snap.ledger).filter(function (x) { return x.stamp + '|' + x.kind + '|' + x.text === key; })[0];
        if (d) docSheet(d);
      };
    });
  }
  function docSheet(d) {
    var rows = d.lines.filter(function (l) { return l.product; }).map(function (l) {
      var usd = !l.amount.som && l.amount.usd;
      var pr = usd ? l.price.usd : l.price.som;
      return '<div class="item"><div class="n"><div class="t">' + esc(l.product) + '</div><div class="muted">' + esc(qt(l.qty)) + ' × ' + esc(money(pr, usd)) + '</div></div>' +
        '<div class="a"><b>' + esc(money(usd ? l.amount.usd : l.amount.som, usd)) + '</b></div></div>';
    }).join('');
    sheet((KIND[d.kind] || d.kind) + ' · ' + C.stampDate(d.stamp), (d.text ? '<p class="muted">' + esc(d.text) + '</p>' : '') + rows +
      '<div class="tot b"><span>Jami</span><span>' + esc((d.som ? money(d.som, false) : '') + (d.usd ? (d.som ? ' + ' : '') + money(d.usd, true) : '') || '0') + '</span></div>');
  }
  function ledgerView() {
    var s = S.snap;
    if (!s) { S.tab = 'home'; return homeView(); }
    var all = C.docs(s.ledger);
    var cov = s.coverage.length ? s.coverage.reduce(function (a, c) { return c.to > a ? c.to : a; }, 0) : 0;
    var h = statusBanner() + '<div class="sticky"><input id="lq" type="search" placeholder="Tovar yoki hujjat bo\'yicha qidirish…" value="' + esc(S.lq) + '"></div>' +
      (cov ? '<p class="muted">1C ma\'lumoti: ' + C.stampDate(cov) + ' gacha</p>' : '') + '<div id="llist"></div>';
    shell('Hisob-kitob', h);
    var q = document.getElementById('lq'), tm = 0;
    q.oninput = function () { clearTimeout(tm); tm = setTimeout(function () { S.lq = q.value; S.limit = 150; list(); }, 250); };
    list();
    function list() {
      var lq = C.fold(S.lq);
      var docs = lq ? all.filter(function (d) { return C.matches(d.key, S.lq); }) : all;
      var days = M.byDay(docs.slice(0, S.limit));
      document.getElementById('llist').innerHTML =
        (days.length ? days.map(function (g) { return '<div class="day">' + dayText(g.day) + '</div>' + g.docs.map(docRow).join(''); }).join('') : '<div class="empty">Harakat yo\'q</div>') +
        (docs.length > S.limit ? '<button class="btn line" id="lMore">Yana ko\'rsatish</button>' : '');
      on('lMore', function () { S.limit += 200; list(); });
      bindDocs();
    }
  }

  // ---- buyurtmalar ----
  function ordersView() {
    var mine = LS.get('orders', []);
    var lines = cartLines();
    var h = statusBanner();
    if (lines.length) h += '<button class="btn" id="oCart">' + ic('cart') + ' Savatcha: ' + lines.length + ' ta — yuborish</button>';
    if (!ordersOn() && S.snap) h += '<div class="banner">Do\'kon hozircha buyurtma qabul qilmayapti — narxlarni ko\'rishingiz mumkin.</div>';
    else if (S.snap) h += '<button class="btn soft" id="oNew">' + ic('tag') + ' Yangi buyurtma (narxlardan tanlang)</button>';
    if (S.orders == null) {
      h += '<div class="empty"><span class="sp" style="border-color:var(--line);border-top-color:var(--brand)"></span></div>';
      shell('Buyurtmalarim', h);
      req(S.link.url, 'GET', rk() + '/ord').then(function (all) {
        S.orders = Object.keys(all || {}).filter(function (id) { return mine.indexOf(id) >= 0; })
          .map(function (id) { return M.orderFrom(id, all[id]); }).filter(Boolean).sort(function (a, b) { return b.at - a.at; });
      }).catch(function (e) { S.orders = []; S.err = errText(e); }).then(function () { if (S.tab === 'orders') ordersView(); });
    } else {
      h += S.orders.length ? S.orders.map(function (o) {
        return '<div class="card"><div style="display:flex;justify-content:space-between;gap:8px"><b>' + dt(o.at) + '</b><span class="st ' + esc(o.status) + '">' +
          esc(o.statusText) + '</span></div>' + (o.note ? '<p class="muted">' + esc(o.note) + '</p>' : '') +
          o.lines.map(function (l) { return '<div class="tot"><span>' + esc(l.name) + '</span><span>' + esc(qt(l.qty)) + '</span></div>'; }).join('') + '</div>';
      }).join('') : '<div class="empty">Siz yuborgan buyurtmalar bu yerda chiqadi</div>';
      shell('Buyurtmalarim', h);
    }
    on('oCart', cartSheet);
    on('oNew', function () { S.tab = 'prices'; render(); });
  }

  function sheet(title, body) {
    var o = document.createElement('div');
    o.className = 'overlay';
    o.innerHTML = '<div class="sheet"><h2>' + esc(title) + '</h2><div class="scroll">' + body + '</div><button class="btn line" data-x>Yopish</button></div>';
    o.onclick = function (e) { if (e.target === o || e.target.hasAttribute('data-x')) o.remove(); };
    document.body.appendChild(o);
    return o;
  }

  function render() {
    if (!S.link) return loginView();
    if (S.tab === 'prices') return pricesView();
    if (S.tab === 'ledger') return ledgerView();
    if (S.tab === 'orders') return ordersView();
    return homeView();
  }

  // ---------------- ishga tushish ----------------
  window.addEventListener('online', render);
  window.addEventListener('offline', render);
  document.addEventListener('visibilitychange', function () { if (!document.hidden && S.link && Date.now() - (S.lastAuto || 0) > 120000) { S.lastAuto = Date.now(); refresh(false); } });
  if ('serviceWorker' in navigator) navigator.serviceWorker.register('sw.js').catch(function () { /* fayldan ochilgan */ });
  loadLocal().then(function () {
    render();
    if (S.link) { S.lastAuto = Date.now(); refresh(false); }
  });
})();
