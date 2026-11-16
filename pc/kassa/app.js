/* v5.4 kassa oynasi — klaviaturasiz sensor monoblok. 1C "РеализацияСенсор1" asosida.
   Ishonchlilik: savatlar har o'zgarishda kompyuterga ham saqlanadi (elektr o'chib monoblok o'chsa ham yo'qolmaydi),
   har so'rovning kutish vaqti cheklangan (hech qachon cheksiz "yozilmoqda…" qolmaydi), 1C ishlamasa sotuv kompyuterda
   navbatga tushadi, kompyuter javob bermasa — monoblokda navbatga (keyin o'zi yuboriladi, ikki marta yozilmaydi).
   Sozlamalar (oynalar soni, tugmalar, o'lcham/rang/matn) kompyuterda — hamma oyna va monoblokka birdek.
   Hisob-kitob logic.js'da (kassa_core.py bilan bir xil). */
(function () {
  'use strict';
  var L = window.KassaLogic;
  var $ = function (id) { return document.getElementById(id); };
  var TERM = (function () {   // bir nechta monoblok bo'lsa: manzil oxirida ?k=2
    var m = /[?&]k=([A-Za-z0-9]{1,16})/.exec(window.location.search);
    return m ? m[1] : '1';
  })();

  // ---------------- saqlash ----------------
  function load(key, def) {
    try { var v = window.localStorage.getItem(key); return v == null ? def : JSON.parse(v); } catch (e) { return def; }
  }
  function save(key, v) {
    try { window.localStorage.setItem(key, JSON.stringify(v)); } catch (e) { /* xotira yo'q */ }
  }

  var UI_DEFAULT = { wins: 3, stock: false, sound: true, autoprint: true, footer: 'Xaridingiz uchun rahmat!', hide: [], el: {} };
  var FN = [
    { id: 'panel', b: 'Panel', s: 'tez tovarlar', big: true },
    { id: 'search', b: 'Qidiruv', s: "nomi bo'yicha", big: true },
    { id: 'price', b: 'Narx', s: "narxi bo'yicha" },
    { id: 'code', b: 'Kod', s: 'tovar kodi' },
    { id: 'qty', b: 'Miqdor', s: 'tanlangan qator' },
    { id: 'disc', b: 'Chegirma', s: 'summa yoki %' },
    { id: 'ret', b: 'Qaytarish', s: 'tovar qaytdi' },
    { id: 'check', b: 'Narx tekshirish', s: "sotmasdan ko'rish" },
    { id: 'debt', b: "Qarz to'lash", s: "mijoz to'lovi" },
    { id: 'last', b: 'Oxirgi chek', s: 'qayta chiqarish' },
    { id: 'report', b: 'Kunlik hisobot', s: 'bugungi savdo' },
    { id: 'settings', b: 'Sozlamalar', s: 'admin paroli' }
  ];

  function emptyWin() { return { lines: [], mode: 'sale', dmode: 'sum', dval: 0, id: '' }; }

  var S = {
    token: load('k_token', ''),
    name: load('k_name', ''),
    data: null, ver: 0, byCode: {}, dataAt: 0,
    ui: mergeUi(load('k_ui', {})),
    wins: [],
    w: 0, sel: -1, disp: '', mult: 0,
    ptype: load('k_ptype', 'Цена1'),
    page: 0,
    pay: null,
    last: load('k_last', null),
    outbox: load('k_outbox', []),
    stateAt: load('k_state_at', 0),
    kb: null,
    check: false,
    grant: '', grantName: '',
    pcVer: '', pcQ: 0, pcQe: 0,
    edit: null
  };
  if (L.PRICE_TYPES.indexOf(S.ptype) < 0) S.ptype = 'Цена1';
  S.wins = fixWins(load('k_wins', []));
  function W() { return S.wins[S.w]; }

  function mergeUi(u) {
    var o = JSON.parse(JSON.stringify(UI_DEFAULT));
    if (u && typeof u === 'object') Object.keys(u).forEach(function (k) { o[k] = u[k]; });
    o.wins = Math.max(1, Math.min(4, parseInt(o.wins, 10) || 3));
    if (!Array.isArray(o.hide)) o.hide = [];
    if (!o.el || typeof o.el !== 'object') o.el = {};
    return o;
  }
  function fixWins(w) {
    if (!Array.isArray(w)) w = [];
    var n = S.ui.wins;
    var out = [];
    for (var i = 0; i < Math.max(n, w.length); i++) {
      var x = w[i] && Array.isArray(w[i].lines) ? w[i] : emptyWin();
      if (i < n || x.lines.length) out.push(x);     // tovari bor oyna o'chirilmaydi
    }
    return out;
  }

  // ---------------- yordamchilar ----------------
  var toastTimer = 0;
  function toast(msg, err) {
    var t = $('toast');
    t.textContent = msg;
    t.className = 'toast' + (err ? ' err' : '');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { t.className = 'toast hidden'; }, err ? 5000 : 2500);
    if (err) beep();
  }
  var audio = null;
  function beep() {
    if (!S.ui.sound) return;
    try {
      audio = audio || new (window.AudioContext || window.webkitAudioContext)();
      var o = audio.createOscillator(); var g = audio.createGain();
      o.frequency.value = 880; g.gain.value = 0.15;
      o.connect(g); g.connect(audio.destination); o.start(); o.stop(audio.currentTime + 0.18);
    } catch (e) { /* ovoz yo'q */ }
  }
  var busyAt = 0;
  function busy(on, text) {
    $('busyText').textContent = text || "1C'ga yozilmoqda…";
    $('busy').className = on ? 'busy' : 'busy hidden';
    busyAt = on ? Date.now() : 0;
  }
  /** Har so'rov cheklangan vaqt kutadi — kassa hech qachon cheksiz kutib qolmaydi. */
  function api(method, path, body, timeoutMs) {
    var ctrl = typeof AbortController === 'function' ? new AbortController() : null;
    var opt = { method: method, headers: {}, cache: 'no-store' };
    if (ctrl) opt.signal = ctrl.signal;
    if (body !== undefined) { opt.headers['Content-Type'] = 'application/json'; opt.body = JSON.stringify(body); }
    var timer = setTimeout(function () { if (ctrl) ctrl.abort(); }, timeoutMs || 20000);
    return fetch(path, opt).then(function (r) {
      clearTimeout(timer);
      if (r.status === 401) { lock('Qayta kiring'); throw new Error('login'); }
      return r.json().then(function (j) {
        if (!r.ok && !j.e) throw new Error('HTTP ' + r.status);
        return j;
      });
    }, function (e) {
      clearTimeout(timer);
      throw new Error(e && e.name === 'AbortError' ? 'kutish vaqti tugadi' : "ulanish yo'q");
    });
  }
  function money(c) { return L.fmtMoney(c); }
  function el(tag, cls, text) {
    var e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text != null) e.textContent = text;
    return e;
  }
  function ptypeLabel(p) { return 'Narx ' + (L.PRICE_TYPES.indexOf(p) + 1); }
  function hidden(id) { return $(id).classList.contains('hidden'); }

  // ---------------- savatlarni saqlash (monoblok + kompyuter) ----------------
  var syncTimer = 0;
  function saveWins() {
    S.stateAt = Date.now();
    save('k_wins', S.wins); save('k_state_at', S.stateAt);
    clearTimeout(syncTimer);
    syncTimer = setTimeout(pushState, 300);
  }
  function pushState() {
    if (!S.token) return;
    api('POST', '/api/state', { token: S.token, k: TERM, state: { at: S.stateAt, wins: S.wins, w: S.w, outbox: S.outbox } }, 10000)
      .catch(function () { /* kompyuter yo'q — keyingi o'zgarishda yana urinadi */ });
  }
  function pullState() {
    return api('GET', '/api/state?t=' + encodeURIComponent(S.token) + '&k=' + TERM, undefined, 10000).then(function (st) {
      if (!st || !st.at || st.at <= S.stateAt || !Array.isArray(st.wins)) return;
      // monoblok o'chib qolgan (xotirasi eski) — kompyuterdagi oxirgi holat tiklanadi
      S.wins = fixWins(st.wins);
      S.w = Math.min(st.w || 0, S.wins.length - 1);
      if (Array.isArray(st.outbox)) st.outbox.forEach(function (o) {
        if (!S.outbox.some(function (x) { return x.id === o.id; })) S.outbox.push(o);
      });
      S.stateAt = st.at;
      save('k_wins', S.wins); save('k_state_at', S.stateAt); save('k_outbox', S.outbox);
      renderAll();
      toast('Savatlar kompyuterdan tiklandi');
    }).catch(function () { /* kompyuter yo'q */ });
  }
  function touchWin() { W().id = ''; saveWins(); }

  // ---------------- so'rov oynasi ----------------
  var askCb = null;
  function ask(o) {
    askCb = o;
    $('askTitle').textContent = o.title || '';
    $('askText').textContent = o.text || '';
    $('askErr').textContent = '';
    $('askPw').value = '';
    $('askPw').classList.toggle('hidden', !o.pw);
    $('askPad').classList.toggle('hidden', !o.pw);
    var ch = $('askChoices');
    ch.textContent = '';
    (o.choices || []).forEach(function (c) {
      var b = el('button', c.on ? 'on' : '', c.label);
      b.addEventListener('click', function () { closeAsk(); c.pick(); });
      ch.appendChild(b);
    });
    $('askYes').classList.toggle('hidden', !!o.choices);
    $('askNo').classList.toggle('hidden', !!o.noCancel);
    $('askYes').textContent = o.yes || 'Tasdiqlash';
    $('askYes').disabled = false;
    $('askModal').classList.remove('hidden');
  }
  function closeAsk() { $('askModal').classList.add('hidden'); askCb = null; }

  function adminPw(what, fn) {
    ask({
      title: 'Admin paroli', text: what, pw: true, yes: 'Ruxsat',
      onYes: function (pw) {
        return api('POST', '/api/admin', { token: S.token, pw: pw }).then(function (j) {
          if (!j.ok) throw new Error(j.e || "Ruxsat yo'q");
          S.grant = j.grant || ''; S.grantName = j.name || '';
          fn();
        });
      }
    });
  }
  /** 1C kabi: kamaytirish/o'chirish — "ИзмененияВСенсореСпаролем" yoqiq bo'lsa admin paroli. */
  function needAdmin(what, fn) {
    if (!S.data || !S.data.lock) { fn(); return; }
    adminPw(what, fn);
  }

  // ---------------- kirish / qulf / chiqish ----------------
  function showLogin(msg) {
    $('main').classList.add('hidden');
    hideOverlays();
    $('login').classList.remove('hidden');
    $('loginErr').textContent = msg || '';
    $('pw').value = '';
  }
  function doLogin() {
    var pw = $('pw').value;
    if (!pw) { $('loginErr').textContent = 'Parolni kiriting'; return; }
    $('loginBtn').disabled = true;
    api('POST', '/api/login', { pw: pw }, 60000).then(function (j) {
      $('loginBtn').disabled = false;
      if (!j.ok) { $('pw').value = ''; $('loginErr').textContent = j.e || 'Xato'; beep(); return; }
      S.token = j.token; S.name = j.name;
      save('k_token', S.token); save('k_name', S.name);
      enter();
    }).catch(function (e) {
      $('loginBtn').disabled = false;
      if (e.message !== 'login') $('loginErr').textContent = "Kompyuterga ulanib bo'lmadi: " + e.message;
    });
  }
  function lock(msg) {
    S.token = ''; save('k_token', '');
    S.grant = '';
    showLogin(msg || '');
  }
  function enter() {
    $('login').classList.add('hidden');
    $('main').classList.remove('hidden');
    $('cashierName').textContent = S.name;
    renderAll();
    loadUi();
    pullState();
    refreshData(true);
    sendOutbox();
  }
  function closeKassa() {
    try { window.close(); } catch (e) { /* yopilmadi */ }
    setTimeout(function () {
      ask({
        title: 'Oyna yopilmadi', noCancel: true, yes: 'Tushunarli',
        text: "Barmoq bilan: ekranning CHAP chetidan o'rtaga suring → «+ Создать рабочий стол» → yangi stol.\n" +
          "Klaviatura bo'lsa: Alt+F4.",
        onYes: function () { return Promise.resolve(); }
      });
    }, 600);
  }

  // ---------------- ma'lumot ----------------
  function refreshData(first) {
    if (!S.token) return Promise.resolve();
    if (first && !S.data) busy(true, "1C'dan tovarlar olinmoqda…");
    return api('GET', '/api/data?t=' + encodeURIComponent(S.token) + '&v=' + (S.data ? S.ver : 0), undefined, 90000).then(function (j) {
      busy(false);
      S.dataAt = Date.now();
      if (j.same) { renderDot(); return; }
      if (j.ok === false) { toast(j.e || "Ma'lumot olinmadi", true); renderDot(true); return; }
      setData(j);
    }).catch(function (e) {
      busy(false);
      renderDot(true);
      if (e.message !== 'login' && first) toast('Kompyuter javob bermadi: ' + e.message, true);
    });
  }
  function setData(d) {
    S.data = d;
    S.ver = d.ver;
    S.byCode = {};
    for (var i = 0; i < d.g.length; i++) S.byCode[d.g[i].c] = d.g[i];
    $('storeName').textContent = d.title || d.store || 'Kassa';
    $('loginTitle').textContent = d.title || 'Kassa';
    var changed = repriceAll(false);
    if (changed) toast('Narxlar yangilandi: ' + changed + ' ta qator');
    renderAll();
    if (!hidden('panelView')) renderPanel();
  }
  function repriceAll(force) {
    if (!S.data) return 0;
    var n = 0;
    S.wins.forEach(function (w) {
      w.lines.forEach(function (l) {
        var g = S.byCode[l.code];
        if (!g) return;
        var p = L.priceOf(g, S.ptype, S.data.rate);
        if (p !== l.price) { l.price = p; n++; w.id = ''; }
      });
    });
    if (n || force) saveWins();
    return n;
  }
  function renderDot(bad) {
    var age = Date.now() - S.dataAt;
    $('dataDot').className = 'dot ' + (bad ? 'bad' : age < 120000 ? 'ok' : 'old');
  }
  /** Kompyuter holati: versiya (yangilansa sahifa o'zi yangilanadi), 1C navbati. */
  function ping() {
    return api('GET', '/api/ping', undefined, 10000).then(function (j) {
      if (S.pcVer && j.v && j.v !== S.pcVer && idle()) { window.location.reload(); return; }
      S.pcVer = j.v || S.pcVer;
      S.pcQ = j.q || 0; S.pcQe = j.qe || 0;
      renderQueue();
    }).catch(function () { renderDot(true); });
  }
  function idle() {
    return hidden('payModal') && hidden('askModal') && hidden('busy') && hidden('setView') && !S.edit;
  }
  function renderQueue() {
    var n = S.pcQ + S.pcQe + S.outbox.length;
    $('queueBadge').classList.toggle('hidden', n === 0);
    $('queueN').textContent = String(n) + (S.pcQe ? ' (' + S.pcQe + ' xato)' : '');
  }

  // ---------------- monoblok navbati (kompyuter javob bermasa) ----------------
  function sendOutbox() {
    if (!S.token) return;
    var o = S.outbox.filter(function (x) { return !x.dead; })[0];
    if (!o) return;
    o.body.token = S.token;
    api('POST', '/api/sale', o.body, 60000).then(function (j) {
      if (j.ok) {
        S.outbox = S.outbox.filter(function (x) { return x.id !== o.id; });
      } else if (!j.retry) {
        // kompyuter rad etdi (masalan narx o'zgargan) — o'chirilmaydi, admin "1C navbati"da ko'rib chiqadi
        o.dead = true; o.err = j.e || 'rad etildi';
        toast("Saqlangan sotuv kompyuterga o'tmadi: " + o.err, true);
      } else return;
      save('k_outbox', S.outbox); pushState(); renderQueue();
      setTimeout(sendOutbox, 500);
    }).catch(function () { /* keyingi safar */ });
  }

  // ---------------- savat ----------------
  function total(w) { return L.total((w || W()).lines); }
  function discount(w) {
    w = w || W();
    var t = total(w);
    // foiz chegirma butun so'mga yaxlitlanadi
    var d = w.dmode === 'pct' ? Math.round(t * w.dval / 1000000) * 100 : w.dval;
    return Math.max(0, Math.min(d, t));
  }
  function stockLeft(code, exceptIndex) {
    var g = S.byCode[code];
    var have = L.milli(g ? g.s : 0);
    W().lines.forEach(function (l, i) { if (i !== exceptIndex && l.code === code) have -= l.milli; });
    return have;
  }
  function addGoods(g, milli, weighted) {
    if (!g || !S.data) return;
    var w = W();
    var price = L.priceOf(g, S.ptype, S.data.rate);
    if (price <= 0) { toast(g.n + ": bu narx turida narx yo'q", true); return; }
    var mult = S.mult;
    S.mult = 0;
    if (mult > 0) milli = weighted ? Math.round(milli * mult / 1000) : Math.round(mult * (milli / 1000));
    if (milli <= 0) { toast('Miqdor 0', true); renderDisplay(); return; }
    var idx = -1;
    if (!weighted) w.lines.forEach(function (l, i) { if (idx < 0 && l.code === g.c && !l.w) idx = i; });
    var cur = idx >= 0 ? w.lines[idx].milli : 0;
    var want = cur + milli;
    if (w.mode === 'sale' && !S.data.neg) {
      var cap = L.capQty(want, stockLeft(g.c, idx) / 1000, false);
      if (cap.capped) {
        toast(g.n + ': qoldiq ' + L.fmtQty(Math.max(stockLeft(g.c, idx), 0)) + " — ko'proq sotib bo'lmaydi", true);
        want = cap.milli;
        if (want <= cur) { renderDisplay(); return; }
      }
    }
    if (idx >= 0) { w.lines[idx].milli = want; S.sel = idx; }
    else {
      w.lines.push({ code: g.c, name: g.n, unit: g.e || '', price: price, milli: want, w: !!weighted });
      S.sel = w.lines.length - 1;
    }
    touchWin();
    renderCart(true); renderDisplay(); renderWins();
  }
  function setQty(i, milli) {
    var l = W().lines[i];
    if (!l) return;
    if (milli <= 0) { removeLine(i); return; }
    if (W().mode === 'sale' && S.data && !S.data.neg) {
      var cap = L.capQty(milli, stockLeft(l.code, i) / 1000, false);
      if (cap.capped) { toast(l.name + ': qoldiq yetmaydi', true); milli = cap.milli; if (milli <= 0) return; }
    }
    l.milli = milli;
    touchWin();
    renderCart(); renderWins();
  }
  function removeLine(i) {
    W().lines.splice(i, 1);
    if (S.sel >= W().lines.length) S.sel = W().lines.length - 1;
    touchWin();
    renderCart(); renderWins();
  }
  function clearWin() {
    S.wins[S.w] = emptyWin();
    S.sel = -1; S.mult = 0; S.disp = '';
    if (S.w >= S.ui.wins) { S.wins.splice(S.w, 1); S.w = 0; }   // sozlamada kamaytirilgan oyna bo'shasa — yo'qoladi
    saveWins();
    renderAll();
  }
  function findCode(text) {
    var r = L.parseScan(text, S.data.rongta);
    var code = S.data.b[r.code];
    if (code && S.byCode[code]) return { g: S.byCode[code], milli: r.milli || 1000, w: r.weighted };
    if (/^[0-9]{1,7}$/.test(text) && S.byCode[text]) return { g: S.byCode[text], milli: 1000, w: false };
    return null;
  }
  /** "Kiritish" yoki skaner Enter'i: 1C ПолеТекПриИзменении. */
  function enterCode(text) {
    text = String(text || '').trim();
    S.disp = '';
    if (!text || !S.data) { renderDisplay(); return; }
    var f = findCode(text);
    if (S.check) { S.check = false; renderFn(); renderDisplay(); if (f) showGoods(f.g); else toast('Topilmadi: ' + text, true); return; }
    if (f) { addGoods(f.g, f.milli, f.w); return; }
    toast('Topilmadi: ' + text, true);
    renderDisplay();
  }

  // ---------------- chizish ----------------
  function renderAll() {
    $('ptypeName').textContent = ptypeLabel(S.ptype);
    renderMode(); renderWins(); renderCart(); renderDisplay(); renderDot(); renderFn(); renderQueue();
  }
  function renderMode() {
    var ret = W().mode === 'return';
    document.body.classList.toggle('ret', ret);
    $('modeBar').classList.toggle('hidden', !ret);
    $('payBtn').textContent = ret ? 'QAYTARISH' : "TO'LOV";
    $('tDueLabel').textContent = ret ? 'Qaytariladi' : "To'lanadi";
    renderFn();
  }
  function renderFn() {
    var box = $('fn');
    box.textContent = '';
    var ret = S.wins.length && W().mode === 'return';
    FN.forEach(function (f) {
      if (S.ui.hide.indexOf(f.id) >= 0 && f.id !== 'settings') return;
      var b = el('button', (f.id === 'panel' ? 'fn-main ' : '') + (f.big ? 'fn-big' : ''));
      b.setAttribute('data-fn', f.id);
      b.setAttribute('data-ui', 'fn-' + f.id);
      var sub = f.s;
      if (f.id === 'ret' && ret) { b.classList.add('on'); sub = 'yoqilgan — bekor'; }
      if (f.id === 'check' && S.check) { b.classList.add('on'); sub = 'skanerlang…'; }
      if (f.id === 'disc' && S.wins.length && discount()) sub = 'bekor qilish';
      b.appendChild(el('b', '', f.b));
      b.appendChild(el('small', '', sub));
      box.appendChild(b);
    });
  }
  function renderWins() {
    var box = $('wins');
    box.textContent = '';
    if (S.wins.length <= 1) return;
    S.wins.forEach(function (w, i) {
      var b = el('button', i === S.w ? 'on' : '');
      b.setAttribute('data-w', i);
      b.setAttribute('data-ui', 'wins');
      b.appendChild(el('span', '', (i + 1) + '-oyna'));
      b.appendChild(el('small', '', w.lines.length ? money(L.total(w.lines)) : "bo'sh"));
      box.appendChild(b);
    });
  }
  function renderCart(scroll) {
    var w = W();
    var c = $('cart');
    c.textContent = '';
    $('cartEmpty').classList.toggle('hidden', w.lines.length > 0);
    c.classList.toggle('hidden', w.lines.length === 0);
    w.lines.forEach(function (l, i) {
      var row = el('div', 'row' + (i === S.sel ? ' sel' : ''));
      row.setAttribute('role', 'listitem');
      row.setAttribute('data-i', i);
      row.appendChild(el('span', 'rn', String(i + 1)));
      var nm = el('span', 'nm', l.name);
      if (!S.byCode[l.code] && S.data) nm.appendChild(el('i', '', "1C'da yo'q"));
      row.appendChild(nm);
      row.appendChild(el('span', '', L.fmtQty(l.milli) + (l.unit ? ' ' + l.unit : '')));
      row.appendChild(el('span', '', money(l.price)));
      row.appendChild(el('span', 'sm', money(L.lineSum(l.price, l.milli))));
      c.appendChild(row);
    });
    var t = total(w), d = discount(w);
    $('tTotal').textContent = money(t);
    $('tDisc').textContent = money(d) + (w.dmode === 'pct' && w.dval ? ' (' + (w.dval / 100) + '%)' : '');
    $('tDiscLine').classList.toggle('hidden', d === 0);
    $('tDue').textContent = money(t - d);
    var info = $('selInfo');
    info.textContent = '';
    var s = w.lines[S.sel];
    if (s) {
      var g = S.byCode[s.code];
      info.appendChild(el('b', '', s.name));
      info.appendChild(el('span', '', L.fmtQty(s.milli) + ' × ' + money(s.price) + ' = ' + money(L.lineSum(s.price, s.milli)) +
        (g ? ' · qoldiq: ' + L.fmtQty(L.milli(g.s)) + (g.e ? ' ' + g.e : '') : '')));
    }
    if (scroll) {
      var r = c.querySelector('.row.sel');
      if (r && r.scrollIntoView) r.scrollIntoView({ block: 'nearest' });
    }
  }
  function renderDisplay() {
    $('dVal').textContent = S.disp.replace('.', ',');
    $('dMult').textContent = S.mult ? '× ' + L.fmtQty(S.mult) : '';
    $('dHint').textContent = S.check ? "Narxini ko'rish uchun skanerlang" : 'Raqam yozing yoki skanerlang';
    $('dHint').classList.toggle('hidden', !!S.disp || !!S.mult);
  }

  // ---------------- panel ----------------
  function tile(g) {
    var b = el('button', 'tile');
    b.setAttribute('data-code', g.c);
    b.setAttribute('data-ui', 'tile');
    b.appendChild(el('span', 'tn', g.n));
    var p = el('span', 'tp');
    p.appendChild(el('span', '', money(L.priceOf(g, S.ptype, S.data.rate))));
    if (S.ui.stock) p.appendChild(el('i', g.s > 0 ? '' : 'none', L.fmtQty(L.milli(g.s)) + (g.e ? ' ' + g.e : '')));
    b.appendChild(p);
    return b;
  }
  function openPanel() {
    if (!S.data) return;
    $('panelView').classList.remove('hidden');
    renderPanel();
  }
  function renderPanel() {
    var t = $('tabs');
    t.textContent = '';
    for (var i = 0; i < 8; i++) {
      var b = el('button', i === S.page ? 'on' : '', String(i + 1));
      b.setAttribute('data-page', i);
      t.appendChild(b);
    }
    var grid = $('grid');
    grid.textContent = '';
    var pnl = S.data.pnl || [];
    if (!pnl.length) { grid.appendChild(el('div', 'grid-msg', "Panel bo'sh — Admin ilovasida «Kassa paneli»dan to'ldiring")); return; }
    for (var k = S.page * 42; k < S.page * 42 + 42; k++) {
      var slot = pnl[k] || ['', ''];
      var g = slot[0] ? S.byCode[slot[0]] : null;
      grid.appendChild(g ? tile(g) : el('div', 'tile empty'));
    }
  }

  // ---------------- qidiruv / klaviatura ----------------
  var KB_LAT = ['1234567890', 'qwertyuiop', "asdfghjkl'", 'zxcvbnm-.'];
  var KB_CYR = ['1234567890', 'йцукенгшщзхъ', 'фывапролджэ', 'ячсмитьбюё', 'ўқғҳ'];
  function openKb(kind, title, onText) {
    S.kb = { kind: kind, text: '', cyr: false, onText: onText };
    $('kbTitle').textContent = title;
    $('kb').classList.toggle('hidden', kind === 'price');
    $('kbResults').classList.toggle('hidden', kind === 'text');
    $('kbView').classList.remove('hidden');
    renderKb();
  }
  function closeKb() { $('kbView').classList.add('hidden'); S.kb = null; }
  function renderKb() {
    var k = S.kb;
    $('kbText').textContent = k.text;
    var kb = $('kb');
    kb.textContent = '';
    (k.cyr ? KB_CYR : KB_LAT).forEach(function (row) {
      var r = el('div', 'kb-row');
      row.split('').forEach(function (ch) {
        var b = el('button', '', ch.toUpperCase());
        b.setAttribute('data-ch', ch);
        r.appendChild(b);
      });
      kb.appendChild(r);
    });
    var last = el('div', 'kb-row');
    var ops = [['lang', k.cyr ? 'ABC' : 'АБВ', 'w2'], ['space', "Bo'sh joy", 'w5'], ['back', '⌫', 'w2'], ['clear', 'Tozalash', 'w2']];
    if (k.kind === 'text') ops.push(['ok', 'Tayyor', 'w2']);
    ops.forEach(function (x) { var b = el('button', x[2], x[1]); b.setAttribute('data-op', x[0]); last.appendChild(b); });
    kb.appendChild(last);
    renderKbResults();
  }
  function renderKbResults() {
    var k = S.kb;
    var box = $('kbResults');
    box.textContent = '';
    if (!S.data || k.kind === 'text') return;
    if (k.kind === 'client') {
      var q = k.text.toLowerCase().trim();
      var digits = q.replace(/[^0-9]/g, '');
      var n = 0;
      for (var i = 0; i < S.data.cl.length && n < 60; i++) {
        var c = S.data.cl[i];
        if (q) {
          var okName = c[1].toLowerCase().indexOf(q) >= 0;
          var okTel = digits.length >= 3 && String(c[2]).replace(/[^0-9]/g, '').indexOf(digits) >= 0;
          if (!okName && !okTel) continue;
        } else if (!(c[3] > 0)) continue;
        var b = el('button', 'cl');
        b.setAttribute('data-ci', i);
        b.appendChild(el('b', '', c[1]));
        b.appendChild(el('small', '', c[2] || ''));
        if (c[3]) b.appendChild(el('small', c[3] > 0 ? 'debt' : '', 'qarz: ' + money(L.cents(c[3]))));
        box.appendChild(b);
        n++;
      }
      if (!n) box.appendChild(el('div', 'grid-msg', q ? 'Mijoz topilmadi' : "Ismi yoki telefonini yozing (qarzdorlar ko'rsatilgan)"));
      return;
    }
    var res = k.text.trim() ? L.search(S.data.g, k.text, S.ptype, S.data.rate, 60) : [];
    if (k.kind === 'price') {
      var pc = L.cents(k.text);
      res = S.data.g.filter(function (g) { return L.priceOf(g, S.ptype, S.data.rate) === pc; }).slice(0, 60);
    }
    res.forEach(function (g) { box.appendChild(tile(g)); });
    if (!res.length) box.appendChild(el('div', 'grid-msg', k.text.trim() ? 'Topilmadi' : 'Nomini yozing'));
  }

  // ---------------- ma'lumot oynasi ----------------
  function info(title, rows, btns) {
    $('infoTitle').textContent = title;
    var b = $('infoBody');
    b.textContent = '';
    rows.forEach(function (r) {
      if (r.node) { b.appendChild(r.node); return; }
      var d = el('div', 'ln' + (r.cls ? ' ' + r.cls : ''));
      d.appendChild(el('span', '', r.a));
      d.appendChild(el('b', '', r.b));
      b.appendChild(d);
    });
    if (btns && btns.length) {
      var row = el('div', 'row-btns');
      btns.forEach(function (x) {
        var bt = el('button', 'big ' + (x.cls || 'muted'), x.label);
        bt.addEventListener('click', x.fn);
        row.appendChild(bt);
      });
      b.appendChild(row);
    }
    $('infoModal').classList.remove('hidden');
  }
  function showGoods(g) {
    var rows = L.PRICE_TYPES.map(function (p) {
      return { a: ptypeLabel(p) + (p === S.ptype ? ' (hozirgi)' : ''), b: money(L.priceOf(g, p, S.data.rate)), cls: p === S.ptype ? 'big' : '' };
    });
    rows.push({ a: 'Qoldiq', b: L.fmtQty(L.milli(g.s)) + (g.e ? ' ' + g.e : ''), cls: g.s > 0 ? '' : 'warn' });
    rows.push({ a: 'Kod', b: g.c });
    info(g.n, rows, [{ label: "Savatga qo'shish", cls: 'primary', fn: function () { $('infoModal').classList.add('hidden'); addGoods(g, 1000, false); } }]);
  }
  function showReport() {
    busy(true, 'Hisobot tayyorlanmoqda…');
    api('GET', '/api/report?t=' + encodeURIComponent(S.token), undefined, 20000).then(function (r) {
      busy(false);
      var c = function (v) { return money(L.cents(v)); };
      var rows = [
        { a: 'Sana', b: r.day },
        { a: 'Hujjatlar soni', b: String(r.n) },
        { a: 'Savdo (jami)', b: c(r.sale), cls: 'big' },
        { a: 'Chegirma', b: c(r.disc) },
        { a: 'Qaytarish', b: c(r.ret), cls: r.ret ? 'warn' : '' },
        { a: 'Naqd', b: c(r.cash) }, { a: 'Plastik', b: c(r.card) }, { a: 'Click', b: c(r.click) },
        { a: 'Qarzga berildi', b: c(r.debt), cls: r.debt ? 'warn' : '' }, { a: "Qarz to'landi", b: c(r.debt_paid) }
      ];
      Object.keys(r.by || {}).forEach(function (k) { rows.push({ a: 'Kassir: ' + k, b: r.by[k].n + ' ta · ' + c(r.by[k].sum) }); });
      if (r.q || r.qe) rows.push({ a: "1C'ga hali yozilmagan", b: (r.q + r.qe) + ' ta', cls: 'warn' });
      info('Kunlik hisobot', rows, [{ label: 'Chop etish', fn: function () { printRows('Kunlik hisobot', rows); } }]);
    }).catch(function (e) { busy(false); if (e.message !== 'login') toast('Hisobot olinmadi: ' + e.message, true); });
  }
  function printRows(title, rows) {
    var R = $('receipt');
    R.textContent = '';
    R.appendChild(el('div', 'c h', title));
    rows.forEach(function (r) {
      var d = el('div', 'ln');
      d.appendChild(el('span', '', r.a)); d.appendChild(el('b', '', r.b));
      R.appendChild(d);
    });
    setTimeout(function () { window.print(); }, 50);
  }

  // ---------------- to'lov ----------------
  function openPay(debtOnly) {
    if (!S.data) return;
    var w = W();
    if (w.mode === 'return' && !w.lines.length) { toast("Qaytariladigan tovarni qo'shing", true); return; }
    if (!debtOnly && w.mode === 'sale' && !w.lines.length) { toast("Savat bo'sh", true); return; }
    if (!w.id) { w.id = L.newId(); saveWins(); }
    var t = total(w), d = discount(w);
    S.pay = { total: t, discount: d, cash: t - d, card: 0, click: 0, field: 'cash', buf: '', client: null };
    $('payTitle').textContent = w.mode === 'return' ? 'Qaytarish' : (t === 0 ? "Qarz to'lovi" : "To'lov");
    $('pDueLabel').textContent = w.mode === 'return' ? 'Qaytariladi' : "To'lanadi";
    $('printChk').checked = !!S.ui.autoprint;
    $('payErr').textContent = '';
    $('payModal').classList.remove('hidden');
    renderPay();
    if (debtOnly) openKb('client', "Qarz to'laydigan mijoz");
  }
  function closePay() { $('payModal').classList.add('hidden'); S.pay = null; }
  function payState() {
    var p = S.pay;
    return L.settle(p.total, p.discount, p.cash, p.card, p.click, !!p.client, W().mode === 'return');
  }
  function renderPay() {
    var p = S.pay;
    if (!p) return;
    $('pTotal').textContent = money(p.total);
    $('pDiscount').textContent = money(p.discount);
    $('pDue').textContent = money(p.total - p.discount);
    $('fCash').textContent = money(p.cash);
    $('fCard').textContent = money(p.card);
    $('fClick').textContent = money(p.click);
    var fs = document.querySelectorAll('.field');
    for (var i = 0; i < fs.length; i++) fs[i].classList.toggle('on', fs[i].getAttribute('data-f') === p.field);
    $('padTarget').textContent = { cash: 'Naqd', card: 'Plastik', click: 'Click' }[p.field];
    $('padVal').textContent = money(p[p.field]);
    var st = payState();
    var res = $('pResult');
    var ret = W().mode === 'return';
    if (st.e) { res.className = 'pay-result debt'; res.textContent = st.e; }
    else if (st.change > 0) { res.className = 'pay-result change'; res.textContent = 'Qaytim: ' + money(st.change); }
    else if (st.debt > 0) { res.className = 'pay-result debt'; res.textContent = (ret ? 'Mijoz qarzidan ayriladi: ' : 'Qarzga: ') + money(st.debt); }
    else if (st.debt < 0) { res.className = 'pay-result change'; res.textContent = "Qarz to'lovi: " + money(-st.debt); }
    else { res.className = 'pay-result'; res.textContent = "To'liq to'landi"; }
    var c = p.client;
    $('clientName').textContent = c ? c[1] : 'tanlanmagan';
    var dEl = $('clientDebt');
    dEl.textContent = c && (c[3] || c[4]) ? ('qarz: ' + money(L.cents(c[3])) + (c[4] ? ' / $' + c[4] : '')) : '';
    dEl.className = c && c[3] > 0 ? 'debt' : '';
    $('clientClear').classList.toggle('hidden', !c);
    $('payOk').disabled = !!st.e;
  }
  function submitPay() {
    var st = payState();
    if (st.e) { $('payErr').textContent = st.e; beep(); return; }
    var p = S.pay, w = W();
    var body = {
      id: w.id, token: S.token, kind: w.mode, ptype: S.ptype,
      client: p.client ? p.client[0] : '',
      discount: L.centsText(st.discount), cash: L.centsText(p.cash), card: L.centsText(p.card), click: L.centsText(p.click),
      lines: w.lines.map(function (l) { return { code: l.code, name: l.name, qty: L.milliText(l.milli), price: L.centsText(l.price) }; })
    };
    var print = $('printChk').checked;
    busy(true);
    api('POST', '/api/sale', body, 60000).then(function (j) {
      busy(false);
      if (!j.ok) {
        $('payErr').textContent = j.e || 'Xato';
        beep();
        if (/Narx o'zgargan/.test(j.e || '')) refreshData();
        return;
      }
      done(j, print);
    }).catch(function (e) {
      busy(false);
      if (e.message === 'login') return;
      // kompyuter javob bermadi: sotuv monoblok navbatiga (ikki marta yozilmaydi — raqami bir xil)
      var pay = payState();
      var local = {
        id: body.id, ok: true, queued: true, local: true, no: 'navbatda', kind: w.mode,
        doc: w.mode === 'return' ? 'Возврать' : 'Продажа', date: new Date().toLocaleString('ru-RU').slice(0, 17), cashier: S.name,
        total: pay.total / 100, discount: pay.discount / 100, due: pay.due / 100, cash: pay.cash / 100, card: pay.card / 100,
        click: pay.click / 100, change: pay.change / 100, debt: pay.debt / 100, client: body.client,
        cname: p.client ? p.client[1] : '', cdebt: 0,
        lines: w.lines.map(function (l) { return { n: l.name, q: l.milli / 1000, p: l.price / 100, s: L.lineSum(l.price, l.milli) / 100 }; })
      };
      if (!S.outbox.some(function (x) { return x.id === body.id; })) S.outbox.push({ id: body.id, body: body, at: Date.now() });
      save('k_outbox', S.outbox);
      toast('Kompyuter javob bermadi (' + e.message + ") — sotuv saqlandi, o'zi yuboriladi", true);
      done(local, print);
    });
  }
  function done(res, print) {
    S.last = res; save('k_last', res);
    closePay();
    var kind = res.kind === 'return' ? 'Qaytarish' : (res.doc === 'ОплатаДолга' ? "Qarz to'lovi" : 'Sotuv');
    $('doneTitle').textContent = res.queued ? "Qabul qilindi — 1C'ga keyin yoziladi" : "1C'ga yozildi";
    $('doneInfo').textContent = kind + (res.queued ? ' (navbatda)' : ' № ' + res.no) + ' · ' + res.date + ' · ' + money(L.cents(res.total));
    $('doneChange').textContent = res.change ? 'Qaytim: ' + money(L.cents(res.change)) :
      (res.debt > 0 && res.kind === 'sale' ? 'Qarzga: ' + money(L.cents(res.debt)) : '');
    $('doneModal').classList.remove('hidden');
    clearWin();
    renderQueue();
    refreshData();
    if (print) printReceipt(res);
  }
  function printReceipt(r) {
    var R = $('receipt');
    R.textContent = '';
    function line(a, b, cls) {
      var d = el('div', 'ln' + (cls ? ' ' + cls : ''));
      d.appendChild(el('span', '', a));
      d.appendChild(el('b', '', b));
      R.appendChild(d);
    }
    R.appendChild(el('div', 'c h', (S.data && (S.data.title || S.data.store)) || 'Kassa'));
    R.appendChild(el('div', 'c', (r.kind === 'return' ? 'QAYTARISH' : r.doc === 'ОплатаДолга' ? "QARZ TO'LOVI" : 'SOTUV') +
      (r.queued ? '' : ' № ' + r.no)));
    R.appendChild(el('div', 'c', r.date + ' · ' + r.cashier));
    R.appendChild(el('hr'));
    r.lines.forEach(function (l) {
      var it = el('div', 'item');
      it.appendChild(el('div', '', l.n));
      var ln = el('div', 'ln');
      ln.appendChild(el('span', '', L.fmtQty(L.milli(l.q)) + ' × ' + money(L.cents(l.p))));
      ln.appendChild(el('b', '', money(L.cents(l.s))));
      it.appendChild(ln);
      R.appendChild(it);
    });
    R.appendChild(el('hr'));
    line('Jami', money(L.cents(r.total)), 'big');
    if (r.discount) { line('Chegirma', money(L.cents(r.discount))); line("To'lanadi", money(L.cents(r.due)), 'big'); }
    if (r.cash) line('Naqd', money(L.cents(r.cash)));
    if (r.card) line('Plastik', money(L.cents(r.card)));
    if (r.click) line('Click', money(L.cents(r.click)));
    if (r.change) line('Qaytim', money(L.cents(r.change)));
    if (r.client) {
      R.appendChild(el('hr'));
      line('Mijoz', r.cname || r.client);
      if (r.debt) line(r.debt > 0 ? (r.kind === 'return' ? 'Qarzdan ayrildi' : 'Qarzga yozildi') : "Qarz to'landi", money(L.cents(Math.abs(r.debt))));
      if (!r.local) line('Jami qarz', money(L.cents(r.cdebt)));
    }
    R.appendChild(el('hr'));
    R.appendChild(el('div', 'c', S.ui.footer || ''));
    setTimeout(function () { window.print(); }, 50);
  }

  function hideOverlays() {
    ['panelView', 'kbView', 'payModal', 'askModal', 'doneModal', 'infoModal', 'setView', 'editor', 'editBar'].forEach(function (id) {
      $(id).classList.add('hidden');
    });
    S.pay = null; S.kb = null; askCb = null;
    if (S.edit) stopEdit(false);
  }

  // ---------------- interfeys sozlamalari ----------------
  function loadUi() {
    return api('GET', '/api/ui', undefined, 10000).then(function (u) {
      if (u && typeof u === 'object' && !u.e) applyUi(mergeUi(u));
    }).catch(function () { /* saqlangani bilan ishlaydi */ });
  }
  function applyUi(u) {
    S.ui = u;
    save('k_ui', u);
    S.wins = fixWins(S.wins);
    if (S.w >= S.wins.length) S.w = 0;
    var css = [];
    Object.keys(u.el || {}).forEach(function (id) {
      var e = u.el[id] || {};
      var sel = '[data-ui="' + id.replace(/[^a-z0-9-]/gi, '') + '"]';
      var r = [];
      if (e.fs) r.push('font-size:' + e.fs + 'px !important');
      if (e.bg) r.push('background:' + e.bg + ' !important');
      if (e.fg) r.push('color:' + e.fg + ' !important');
      if (e.h && id !== 'cart' && id !== 'right') r.push('min-height:' + e.h + 'px !important;height:auto');
      if (e.span) r.push('grid-column:span ' + e.span);
      if (e.rows) r.push('grid-row:span ' + e.rows);
      if (r.length) css.push(sel + '{' + r.join(';') + '}');
      if (e.fs) css.push(sel + ' b,' + sel + ' span,' + sel + ' small{font-size:inherit}');
      if (id === 'cart' && e.fs) css.push('.row{font-size:' + e.fs + 'px !important}');
      if (id === 'cart' && e.h) css.push('.row{min-height:' + e.h + 'px}');
      if (id === 'numkey' && e.h) css.push('.main-pad{grid-auto-rows:' + e.h + 'px !important}');
      if (id === 'right' && e.w) css.push('.body{grid-template-columns:1fr ' + e.w + 'px !important}');
      if (id === 'tile' && e.fs) css.push('.tile .tn{font-size:' + e.fs + 'px !important}');
    });
    var st = $('uiStyle');
    if (!st) { st = document.createElement('style'); st.id = 'uiStyle'; document.head.appendChild(st); }
    st.textContent = css.join('\n');
    renderAll();
  }
  function saveUi(u, then) {
    return api('POST', '/api/ui', { token: S.token, grant: S.grant, ui: u }, 15000).then(function (j) {
      if (!j.ok) throw new Error(j.e || 'Saqlanmadi');
      applyUi(mergeUi(u));
      if (then) then();
    }).catch(function (e) { if (e.message !== 'login') toast('Sozlama saqlanmadi: ' + e.message, true); });
  }
  function openSettings() {
    adminPw('Kassa sozlamalari', function () { $('setView').classList.remove('hidden'); renderSettings(); });
  }
  function card(title, text) {
    var c = el('div', 'set-card');
    c.appendChild(el('h3', '', title));
    if (text) c.appendChild(el('p', '', text));
    $('setBody').appendChild(c);
    return c;
  }
  function btn(parent, label, cls, fn) {
    var b = el('button', cls || 'big muted', label);
    b.addEventListener('click', fn);
    parent.appendChild(b);
    return b;
  }
  function renderSettings() {
    var u = JSON.parse(JSON.stringify(S.ui));
    $('setWho').textContent = 'Admin: ' + S.grantName;
    $('setBody').textContent = '';
    var c1 = card('Savdo oynalari soni', 'Bir vaqtda nechta xaridorga savat ochiladi');
    var seg = el('div', 'seg2'); c1.appendChild(seg);
    [1, 2, 3, 4].forEach(function (n) {
      btn(seg, String(n), u.wins === n ? 'on' : '', function () { u.wins = n; saveUi(u, renderSettings); });
    });
    var c2 = card('Chek va ovoz');
    [['autoprint', "To'lovdan keyin chek chiqarish"], ['sound', 'Xatoda ovozli signal'], ['stock', "Panel tugmalarida qoldiqni ko'rsatish"]]
      .forEach(function (x) {
        var b = el('button', 'tg' + (u[x[0]] ? ' on' : ''));
        b.appendChild(el('span', '', x[1])); b.appendChild(el('i', '', u[x[0]] ? 'HA' : "YO'Q"));
        b.addEventListener('click', function () { u[x[0]] = !u[x[0]]; saveUi(u, renderSettings); });
        c2.appendChild(b);
      });
    btn(c2, 'Chek pastidagi matn: ' + (u.footer || '—'), 'tg', function () {
      openKb('text', 'Chek pastidagi matn', function (t) { u.footer = t.slice(0, 60); saveUi(u, renderSettings); });
      S.kb.text = u.footer || ''; renderKb();
    });
    var c3 = card("Tugmalar", "Keraksizini yashiring (Sozlamalar tugmasi doim turadi)");
    FN.forEach(function (f) {
      if (f.id === 'settings') return;
      var on = u.hide.indexOf(f.id) < 0;
      var b = el('button', 'tg' + (on ? ' on' : ''));
      b.appendChild(el('span', '', f.b)); b.appendChild(el('i', '', on ? "KO'RINADI" : 'YASHIRIN'));
      b.addEventListener('click', function () {
        if (on) u.hide.push(f.id); else u.hide = u.hide.filter(function (x) { return x !== f.id; });
        saveUi(u, renderSettings);
      });
      c3.appendChild(b);
    });
    var c4 = card("Ko'rinishni o'zgartirish", "Istalgan joyning o'lchami, rangi va matn o'lchami. Hamma oynalarga birdek qo'llanadi.");
    btn(c4, 'Sozlashni boshlash', 'big primary', function () { $('setView').classList.add('hidden'); startEdit(); });
    btn(c4, 'Hammasini asl holiga qaytarish', 'big muted', function () {
      ask({ title: 'Asl holiga qaytarish', text: "O'lcham va ranglar boshlang'ich holatga qaytadi.", yes: 'Qaytarish',
        onYes: function () { u.el = {}; return saveUi(u, renderSettings); } });
    });
    var c5 = card("1C navbati", "Kompyuter yoki 1C ishlamagan paytdagi sotuvlar. Ular 1C tiklanganda o'zi yoziladi.");
    btn(c5, "Navbatni ko'rish (" + (S.pcQ + S.pcQe + S.outbox.length) + ')', 'big muted', showQueue);
    btn(c5, 'Kunlik hisobot', 'big muted', showReport);
    var c6 = card('Dastur', 'Kompyuter dasturi versiyasi: ' + (S.pcVer || '?') + ' · terminal: ' + TERM);
    btn(c6, "Kompyuter dasturini hozir yangilash", 'big primary', function () {
      api('POST', '/api/update', { token: S.token, grant: S.grant }, 15000).then(function (j) {
        if (!j.ok) throw new Error(j.e || 'Xato');
        toast("So'rov yuborildi: yangi versiya bo'lsa 1-2 daqiqada o'rnatiladi, kassa o'zi yangilanadi");
      }).catch(function (e) { if (e.message !== 'login') toast(e.message, true); });
    });
    btn(c6, "1C'dan ma'lumotni yangilash", 'big muted', function () {
      busy(true, "1C'dan yangilanmoqda…");
      api('POST', '/api/refresh', { token: S.token }, 90000).then(function (j) {
        busy(false);
        if (j.ok === false) { toast(j.e || 'Xato', true); return; }
        refreshData(); toast('Yangilandi');
      }).catch(function (e) { busy(false); if (e.message !== 'login') toast(e.message, true); });
    });
    btn(c6, 'Sahifani qayta yuklash', 'big muted', function () { window.location.reload(); });
    btn(c6, 'Kassani yopish', 'big danger', closeKassa);
  }
  function showQueue() {
    api('GET', '/api/queue?t=' + encodeURIComponent(S.token), undefined, 15000).then(function (j) {
      var rows = [];
      S.outbox.forEach(function (o) {
        var d = el('div', 'q-item');
        var a = el('span', '', 'Monoblokda: ' + new Date(o.at).toLocaleString('ru-RU').slice(0, 17) + ' · ' + money(L.total(
          o.body.lines.map(function (l) { return { price: L.cents(l.price), milli: L.milli(l.qty) }; }))));
        a.appendChild(el('small', '', o.dead ? 'XATO: ' + o.err : "kompyuterga hali yetmagan — o'zi yuboriladi"));
        d.appendChild(a);
        var r = el('button', 'big muted', 'Qayta');
        r.addEventListener('click', function () { o.dead = false; o.err = ''; save('k_outbox', S.outbox); sendOutbox(); showQueue(); });
        d.appendChild(r);
        var x = el('button', 'big danger', "O'chirish");
        x.addEventListener('click', function () {
          ask({ title: "Navbatdan o'chirish", text: "Bu sotuv 1C'ga YOZILMAYDI. Ishonchingiz komilmi?", yes: "O'chirish",
            onYes: function () {
              S.outbox = S.outbox.filter(function (y) { return y.id !== o.id; });
              save('k_outbox', S.outbox); pushState(); renderQueue(); showQueue();
              return Promise.resolve();
            } });
        });
        d.appendChild(x);
        rows.push({ node: d });
      });
      (j.items || []).forEach(function (it) {
        var d = el('div', 'q-item');
        var a = el('span', '', it.date + ' · ' + it.cashier + ' · ' + it.lines + ' ta tovar · ' + money(L.cents(it.total)));
        if (it.err) a.appendChild(el('small', '', (it.state === 'error' ? 'XATO: ' : '') + it.err));
        d.appendChild(a);
        var r = el('button', 'big muted', 'Qayta');
        r.addEventListener('click', function () { queueOp('retry', it.id); });
        d.appendChild(r);
        var x = el('button', 'big danger', "O'chirish");
        x.addEventListener('click', function () {
          ask({ title: "Navbatdan o'chirish", text: "Bu sotuv 1C'ga YOZILMAYDI. Ishonchingiz komilmi?", yes: "O'chirish",
            onYes: function () { queueOp('delete', it.id); return Promise.resolve(); } });
        });
        d.appendChild(x);
        rows.push({ node: d });
      });
      if (!rows.length) rows.push({ a: "Navbat bo'sh", b: "hammasi 1C'da" });
      info("1C navbati", rows);
    }).catch(function (e) { if (e.message !== 'login') toast(e.message, true); });
  }
  function queueOp(op, id) {
    api('POST', '/api/queue', { token: S.token, grant: S.grant, op: op, id: id }, 15000).then(function (j) {
      if (!j.ok) throw new Error(j.e || 'Xato');
      ping(); showQueue();
    }).catch(function (e) { if (e.message !== 'login') toast(e.message, true); });
  }

  // ---------------- interfeys muharriri ----------------
  var NAMES = {
    top: 'Yuqori qator', wins: 'Oyna tugmalari', hbtn: 'Yuqoridagi tugmalar', cart: 'Savat (qatorlar)', thead: 'Savat sarlavhasi',
    selinfo: 'Tanlangan tovar', tools: 'Qator tugmalari', totals: 'Jami bloki', due: "To'lanadi summasi", right: "O'ng ustun",
    display: 'Raqam maydoni', numkey: 'Raqam tugmalari', pay: "TO'LOV tugmasi", tile: 'Panel tugmalari', tabs: 'Panel sahifalari'
  };
  FN.forEach(function (f) { NAMES['fn-' + f.id] = 'Tugma: ' + f.b; });
  var COLORS = ['', '#0f6b45', '#1b5e20', '#2e7d32', '#00695c', '#1565c0', '#283593', '#6a1b9a', '#b4570b', '#c62828',
    '#f9a825', '#37474f', '#000000', '#ffffff', '#dcefe5', '#e3f2fd', '#fff3e0', '#fce4ec', '#f6f9f7'];
  function startEdit() {
    S.edit = { ui: JSON.parse(JSON.stringify(S.ui)), id: '' };
    document.body.classList.add('editing');
    $('editBar').classList.remove('hidden');
  }
  function stopEdit(saveIt) {
    var e = S.edit;
    S.edit = null;
    $('panelView').classList.add('hidden');
    document.body.classList.remove('editing');
    $('editBar').classList.add('hidden');
    $('editor').classList.add('hidden');
    var on = document.querySelectorAll('.ed-on');
    for (var i = 0; i < on.length; i++) on[i].classList.remove('ed-on');
    if (saveIt && e) saveUi(e.ui, function () { toast("Ko'rinish saqlandi"); });
    else applyUi(S.ui);
  }
  function pickEdit(id) {
    S.edit.id = id;
    var on = document.querySelectorAll('.ed-on');
    for (var i = 0; i < on.length; i++) on[i].classList.remove('ed-on');
    var all = document.querySelectorAll('[data-ui="' + id + '"]');
    for (var k = 0; k < all.length; k++) all[k].classList.add('ed-on');
    renderEditor();
    $('editor').classList.remove('hidden');
  }
  function renderEditor() {
    var id = S.edit.id;
    var e = S.edit.ui.el[id] || (S.edit.ui.el[id] = {});
    var node = document.querySelector('[data-ui="' + id + '"]');
    var cs = node ? window.getComputedStyle(node) : null;
    $('edName').textContent = NAMES[id] || id;
    var rows = $('edRows');
    rows.textContent = '';
    function stepper(label, key, cur, step, min, max, unit) {
      var r = el('div', 'ed-row');
      r.appendChild(el('span', '', label));
      var v = e[key] || cur;
      btn(r, '−', '', function () { e[key] = Math.max(min, v - step); live(); });
      r.appendChild(el('b', 'val', v + unit));
      btn(r, '+', '', function () { e[key] = Math.min(max, v + step); live(); });
      btn(r, 'Asl', '', function () { delete e[key]; live(); });
      rows.appendChild(r);
    }
    stepper("Matn o'lchami", 'fs', cs ? Math.round(parseFloat(cs.fontSize)) : 16, 1, 10, 60, 'px');
    if (id === 'right') stepper('Kenglik', 'w', node ? Math.round(node.getBoundingClientRect().width) : 470, 20, 320, 900, 'px');
    else stepper('Balandlik', 'h', node ? Math.round(node.getBoundingClientRect().height) : 50, 4, 24, 240, 'px');
    if (id === 'fn-panel' || id === 'tile' || id === 'tabs') {
      var rp = el('div', 'ed-row');
      rp.appendChild(el('span', '', 'Panel'));
      btn(rp, hidden('panelView') ? 'Panel sahifasini ochish (tugmalarini sozlash)' : 'Panelni yopish', 'on', function () {
        if (hidden('panelView')) openPanel(); else $('panelView').classList.add('hidden');
        renderEditor();
      });
      rows.appendChild(rp);
    }
    if (/^fn-/.test(id)) {
      var r1 = el('div', 'ed-row');
      r1.appendChild(el('span', '', 'Egallaydigan joy'));
      [[1, 1, '1 katak'], [2, 1, '2 ta eniga'], [3, 1, 'butun qator'], [1, 2, "2 ta bo'yiga"], [2, 2, '2×2']].forEach(function (x) {
        var on = (e.span || 1) === x[0] && (e.rows || 1) === x[1];
        btn(r1, x[2], on ? 'on' : '', function () {
          if (x[0] > 1) e.span = x[0]; else delete e.span;
          if (x[1] > 1) e.rows = x[1]; else delete e.rows;
          live();
        });
      });
      rows.appendChild(r1);
    }
    if (id !== 'right' && id !== 'cart') {
      [['bg', 'Fon rangi'], ['fg', 'Matn rangi']].forEach(function (x) {
        var r = el('div', 'ed-row');
        r.appendChild(el('span', '', x[1]));
        COLORS.forEach(function (c) {
          var b = el('button', 'sw' + ((e[x[0]] || '') === c ? ' on' : ''), c ? '' : 'Asl');
          if (c) b.style.background = c;
          b.addEventListener('click', function () { if (c) e[x[0]] = c; else delete e[x[0]]; live(); });
          r.appendChild(b);
        });
        rows.appendChild(r);
      });
    }
  }
  function live() {
    var saved = S.ui;
    applyUi(mergeUi(S.edit.ui));
    S.ui = saved;           // saqlanmaguncha — faqat ko'rinish
    save('k_ui', saved);
    var all = document.querySelectorAll('[data-ui="' + S.edit.id + '"]');
    for (var k = 0; k < all.length; k++) all[k].classList.add('ed-on');
    renderEditor();
  }

  // ---------------- raqam maydoni ----------------
  function padKey(buf, k, maxLen, allowDot) {
    if (k === 'C') return '';
    if (k === 'B') return buf.slice(0, -1);
    if (k === '.') {
      if (!allowDot || buf.indexOf('.') >= 0) return buf;
      return (buf || '0') + '.';
    }
    var nb = buf + k;
    if (nb.length > maxLen) return buf;
    if (nb.indexOf('.') >= 0 && nb.split('.')[1].length > 3) return buf;
    return nb.replace(/^0+(?=\d)/, '');
  }
  function takeDisp(what) {
    var v = S.disp;
    if (!v) { toast(what + ' — avval raqam paneli bilan yozing', true); return null; }
    S.disp = '';
    renderDisplay();
    return v;
  }

  // ---------------- funksiya tugmalari ----------------
  var ACT = {
    panel: openPanel,
    search: function () { openKb('goods', 'Qidiruv'); },
    price: function () {
      var v = takeDisp('Narx');
      if (v == null || !S.data) return;
      var pc = L.cents(v);
      var res = S.data.g.filter(function (g) { return L.priceOf(g, S.ptype, S.data.rate) === pc; });
      if (res.length === 1) { addGoods(res[0], 1000, false); return; }
      if (!res.length) { toast(money(pc) + " narxli tovar yo'q", true); return; }
      openKb('price', money(pc) + ' narxli tovarlar');
      S.kb.text = v;
      renderKbResults();
      $('kbText').textContent = money(pc);
    },
    code: function () {
      var v = takeDisp('Kod');
      if (v == null || !S.data) return;
      var code = v.replace('.', '');
      var g = S.byCode[code];
      if (!g) { var bc = S.data.b[code]; g = bc ? S.byCode[bc] : null; }
      if (!g) { toast('Kod topilmadi: ' + code, true); return; }
      addGoods(g, 1000, false);
    },
    qty: function () {
      if (S.sel < 0) { toast('Avval savatdan qatorni tanlang', true); return; }
      var v = takeDisp('Miqdor');
      if (v == null) return;
      var m = L.milli(v), i = S.sel;
      if (m < W().lines[i].milli) needAdmin('Miqdorni kamaytirish', function () { setQty(i, m); });
      else setQty(i, m);
    },
    disc: function () {
      var w = W();
      if (!S.disp && discount(w)) { w.dmode = 'sum'; w.dval = 0; touchWin(); renderCart(); renderFn(); return; }
      var v = takeDisp('Chegirma');
      if (v == null) return;
      ask({
        title: 'Chegirma: ' + v.replace('.', ','), choices: [
          { label: "So'm (summa)", pick: function () {
            var c = L.cents(v);
            if (c > total(w)) { toast('Chegirma jami summadan katta', true); return; }
            w.dmode = 'sum'; w.dval = c; touchWin(); renderCart(); renderFn();
          } },
          { label: 'Foiz (%)', pick: function () {
            var bp = L.cents(v);
            if (bp > 10000) { toast('Foiz 100 dan oshmaydi', true); return; }
            w.dmode = 'pct'; w.dval = bp; touchWin(); renderCart(); renderFn();
          } }
        ]
      });
    },
    ret: function () {
      var w = W();
      w.mode = w.mode === 'return' ? 'sale' : 'return';
      touchWin(); renderMode();
    },
    check: function () {
      if (S.disp) { var v = takeDisp('Kod'); var f = S.data && findCode(v); if (f) showGoods(f.g); else toast('Topilmadi: ' + v, true); return; }
      S.check = !S.check; renderFn(); renderDisplay();
    },
    debt: function () {
      if (W().lines.length) { toast("Avval savatdagi savdoni yakunlang yoki bo'sh oynaga o'ting", true); return; }
      if (W().mode === 'return') { W().mode = 'sale'; renderMode(); }
      openPay(true);
    },
    last: function () { if (S.last) printReceipt(S.last); else toast("Hali chek yo'q", true); },
    report: showReport,
    settings: openSettings
  };

  // ---------------- hodisalar ----------------
  function on(id, fn) { $(id).addEventListener('click', fn); }
  function bind() {
    // sozlash rejimida har qanday bosish — elementni tanlash (amal bajarilmaydi)
    document.addEventListener('click', function (e) {
      if (!S.edit) return;
      if (e.target.closest && (e.target.closest('#editor') || e.target.closest('#editBar'))) return;
      var t = e.target.closest ? e.target.closest('[data-ui]') : null;
      e.preventDefault(); e.stopPropagation();
      if (t) pickEdit(t.getAttribute('data-ui'));
    }, true);
    on('editSave', function () { stopEdit(true); });
    on('editCancel', function () { stopEdit(false); });
    on('edDone', function () { $('editor').classList.add('hidden'); });

    $('loginPad').addEventListener('click', function (e) {
      var k = e.target.getAttribute && e.target.getAttribute('data-k');
      if (!k) return;
      var pw = $('pw');
      pw.value = k === 'C' ? '' : k === 'B' ? pw.value.slice(0, -1) : (pw.value + k).slice(0, 20);
    });
    on('loginBtn', doLogin);
    on('loginExit', closeKassa);

    $('wins').addEventListener('click', function (e) {
      var b = e.target.closest ? e.target.closest('button[data-w]') : null;
      if (!b) return;
      S.w = parseInt(b.getAttribute('data-w'), 10); S.sel = -1; S.mult = 0; S.disp = '';
      saveWins();
      renderAll();
    });
    on('ptypeBtn', function () {
      ask({
        title: 'Narx turi',
        choices: L.PRICE_TYPES.map(function (p) {
          return { label: ptypeLabel(p), on: p === S.ptype, pick: function () {
            S.ptype = p; save('k_ptype', p);
            repriceAll(true);
            renderAll();
          } };
        })
      });
    });
    on('queueBadge', function () { adminPw("1C navbatini ko'rish", showQueue); });
    on('lockBtn', function () { lock(''); });
    on('exitBtn', function () {
      ask({ title: 'Kassadan chiqish', text: "Kassa oynasi yopiladi. Savatlar saqlanib qoladi.", yes: 'Ha, chiqish',
        onYes: function () { closeKassa(); return Promise.resolve(); } });
    });

    $('cart').addEventListener('click', function (e) {
      var r = e.target.closest ? e.target.closest('.row') : null;
      if (!r) return;
      S.sel = parseInt(r.getAttribute('data-i'), 10);
      renderCart();
    });
    on('upBtn', function () { if (S.sel > 0) { S.sel--; renderCart(true); } else if (W().lines.length) { S.sel = 0; renderCart(true); } });
    on('downBtn', function () { if (S.sel < W().lines.length - 1) { S.sel++; renderCart(true); } });
    on('plusBtn', function () { if (S.sel >= 0) setQty(S.sel, W().lines[S.sel].milli + 1000); });
    on('minusBtn', function () {
      if (S.sel < 0) return;
      var i = S.sel, m = W().lines[i].milli;
      needAdmin('Miqdorni kamaytirish', function () { setQty(i, m > 1000 ? m - 1000 : 0); });
    });
    on('delBtn', function () {
      if (S.sel < 0) { toast('Avval qatorni tanlang', true); return; }
      var i = S.sel;
      needAdmin("Qatorni o'chirish: " + W().lines[i].name, function () { removeLine(i); });
    });
    on('clearBtn', function () {
      if (!W().lines.length) return;
      needAdmin('Savatni tozalash', function () {
        ask({ title: 'Savatni tozalash', text: (S.w + 1) + "-oynadagi hamma tovar o'chiriladi.", yes: 'Tozalash',
          onYes: function () { clearWin(); return Promise.resolve(); } });
      });
    });

    $('mainPad').addEventListener('click', function (e) {
      var k = e.target.getAttribute && e.target.getAttribute('data-k');
      if (!k) return;
      if (k === 'OK') { enterCode(S.disp); return; }
      if (k === 'X') {
        var v = takeDisp('Miqdor (×)');
        if (v == null) return;
        S.mult = L.milli(v);
        renderDisplay();
        return;
      }
      if (k === 'C' && !S.disp && S.mult) { S.mult = 0; renderDisplay(); return; }
      S.disp = padKey(S.disp, k, 20, true);
      renderDisplay();
    });
    $('fn').addEventListener('click', function (e) {
      var b = e.target.closest ? e.target.closest('button[data-fn]') : null;
      if (b && ACT[b.getAttribute('data-fn')]) ACT[b.getAttribute('data-fn')]();
    });
    on('payBtn', function () { openPay(false); });

    $('tabs').addEventListener('click', function (e) {
      var p = e.target.getAttribute && e.target.getAttribute('data-page');
      if (p == null) return;
      S.page = parseInt(p, 10);
      renderPanel();
    });
    $('grid').addEventListener('click', function (e) {
      var t = e.target.closest ? e.target.closest('.tile[data-code]') : null;
      if (!t) return;
      $('panelView').classList.add('hidden');      // 1C kabi: tanlangach savdo oynasiga qaytadi
      addGoods(S.byCode[t.getAttribute('data-code')], 1000, false);
    });
    on('panelBack', function () { $('panelView').classList.add('hidden'); });

    $('kb').addEventListener('click', function (e) {
      var b = e.target.closest ? e.target.closest('button') : null;
      if (!b || !S.kb) return;
      var ch = b.getAttribute('data-ch'), op = b.getAttribute('data-op');
      if (ch) S.kb.text = (S.kb.text + ch).slice(0, 60);
      else if (op === 'space') S.kb.text = (S.kb.text + ' ').slice(0, 60);
      else if (op === 'back') S.kb.text = S.kb.text.slice(0, -1);
      else if (op === 'clear') S.kb.text = '';
      else if (op === 'lang') { S.kb.cyr = !S.kb.cyr; renderKb(); return; }
      else if (op === 'ok') { var k = S.kb; closeKb(); if (k.onText) k.onText(k.text); return; }
      $('kbText').textContent = S.kb.text;
      renderKbResults();
    });
    $('kbResults').addEventListener('click', function (e) {
      if (!S.kb) return;
      var c = e.target.closest ? e.target.closest('button[data-ci]') : null;
      if (c && S.kb.kind === 'client') {
        if (S.pay) { S.pay.client = S.data.cl[parseInt(c.getAttribute('data-ci'), 10)]; renderPay(); }
        closeKb();
        return;
      }
      var t = e.target.closest ? e.target.closest('.tile[data-code]') : null;
      if (!t) return;
      closeKb();
      addGoods(S.byCode[t.getAttribute('data-code')], 1000, false);
    });
    on('kbClose', closeKb);

    on('payClose', closePay);
    document.querySelector('.fields').addEventListener('click', function (e) {
      var f = e.target.closest ? e.target.closest('.field') : null;
      if (!f || !S.pay) return;
      S.pay.field = f.getAttribute('data-f'); S.pay.buf = '';
      renderPay();
    });
    document.querySelector('.quick').addEventListener('click', function (e) {
      var a = e.target.getAttribute && e.target.getAttribute('data-all');
      if (!a || !S.pay) return;
      S.pay.buf = '';
      S.pay.cash = 0; S.pay.card = 0; S.pay.click = 0;
      S.pay[a] = S.pay.total - S.pay.discount;
      S.pay.field = a;
      renderPay();
    });
    $('payPad').addEventListener('click', function (e) {
      var k = e.target.getAttribute && e.target.getAttribute('data-k');
      if (!k || !S.pay) return;
      S.pay.buf = padKey(S.pay.buf, k, 12, false);
      S.pay[S.pay.field] = S.pay.buf ? L.cents(S.pay.buf) : 0;
      renderPay();
    });
    on('clientPick', function () { openKb('client', 'Mijoz'); });
    on('clientClear', function () { if (S.pay) { S.pay.client = null; renderPay(); } });
    on('payOk', submitPay);

    $('askPad').addEventListener('click', function (e) {
      var k = e.target.getAttribute && e.target.getAttribute('data-k');
      if (!k) return;
      var pw = $('askPw');
      pw.value = k === 'C' ? '' : k === 'B' ? pw.value.slice(0, -1) : (pw.value + k).slice(0, 20);
    });
    on('askNo', closeAsk);
    on('askYes', function () {
      var o = askCb;
      if (!o) return;
      var p = o.onYes ? o.onYes($('askPw').value) : null;
      if (p && p.then) {
        $('askYes').disabled = true;
        p.then(function () { $('askYes').disabled = false; if (askCb === o) closeAsk(); })
          .catch(function (e) { $('askYes').disabled = false; $('askPw').value = ''; $('askErr').textContent = e.message; beep(); });
      } else closeAsk();
    });

    on('infoClose', function () { $('infoModal').classList.add('hidden'); });
    on('setClose', function () { $('setView').classList.add('hidden'); });
    on('donePrint', function () { if (S.last) printReceipt(S.last); });
    on('doneNew', function () { $('doneModal').classList.add('hidden'); });

    // skaner (klaviatura sifatida): raqamlar maydonga, Enter — kiritish
    document.addEventListener('keydown', function (e) {
      if (!hidden('login')) {
        if (e.key === 'Enter') doLogin();
        else if (/^[0-9]$/.test(e.key)) $('pw').value = ($('pw').value + e.key).slice(0, 20);
        else if (e.key === 'Backspace') $('pw').value = $('pw').value.slice(0, -1);
        return;
      }
      if (S.kb) {
        if (e.key.length === 1) S.kb.text = (S.kb.text + e.key.toLowerCase()).slice(0, 60);
        else if (e.key === 'Backspace') S.kb.text = S.kb.text.slice(0, -1);
        else return;
        $('kbText').textContent = S.kb.text;
        renderKbResults();
        e.preventDefault();
        return;
      }
      if (!hidden('payModal') || !hidden('askModal') || !hidden('setView') || S.edit) return;
      if (!hidden('doneModal')) $('doneModal').classList.add('hidden');
      if (!hidden('infoModal')) $('infoModal').classList.add('hidden');
      if (!hidden('panelView')) $('panelView').classList.add('hidden');
      if (e.key === 'Enter') { e.preventDefault(); enterCode(S.disp); return; }
      if (e.key === 'Backspace') { S.disp = S.disp.slice(0, -1); renderDisplay(); return; }
      if (e.key.length === 1 && /[0-9A-Za-z.\-]/.test(e.key)) { S.disp = (S.disp + e.key).slice(0, 40); renderDisplay(); e.preventDefault(); }
    });
  }

  // ---------------- o'zini tiklash ----------------
  window.addEventListener('error', function (ev) {
    busy(false);
    toast('Kutilmagan xato tuzatildi: ' + ((ev && ev.message) || ''), true);
  });
  window.addEventListener('unhandledrejection', function () { busy(false); });

  function tickClock() {
    var d = new Date();
    var p = function (n) { return (n < 10 ? '0' : '') + n; };
    $('clock').textContent = p(d.getHours()) + ':' + p(d.getMinutes());
    renderDot();
    if (busyAt && Date.now() - busyAt > 100000) { busy(false); toast("Javob kelmadi — qayta urinib ko'ring", true); }
  }

  // ---------------- boshlash ----------------
  bind();
  applyUi(S.ui);
  tickClock();
  setInterval(tickClock, 5000);
  setInterval(function () { if (S.token && hidden('login')) { refreshData(); sendOutbox(); } }, 30000);
  setInterval(ping, 15000);
  ping();
  if (S.token) enter(); else showLogin('');
})();
