/* v5.4 kassa oynasi — klaviaturasiz sensor monoblok. 1C "РеализацияСенсор1" asosida:
   raqam maydoni (ПолеТек) + raqam paneli, × (НормаУмножить), Kod, Narx bo'yicha, Miqdor, Chegirma (summa / %),
   Qaytarish, 3 ta parallel oyna (1-, 2-, 3-oyna), Panel alohida sahifada (tanlansa savdo oynasiga qaytadi),
   Blok (qulflash), o'zgartirishlar admin paroli bilan (Константы.ИзмененияВСенсореСпаролем).
   Hisob-kitob logic.js'da (kassa_core.py bilan bir xil). */
(function () {
  'use strict';
  var L = window.KassaLogic;
  var $ = function (id) { return document.getElementById(id); };
  var WINS = 3;

  // ---------------- saqlash (brauzer xotirasi bo'lmasa ham ishlaydi) ----------------
  function load(key, def) {
    try { var v = window.localStorage.getItem(key); return v == null ? def : JSON.parse(v); } catch (e) { return def; }
  }
  function save(key, v) {
    try { window.localStorage.setItem(key, JSON.stringify(v)); } catch (e) { /* xotira yo'q */ }
  }

  function emptyWin() { return { lines: [], mode: 'sale', dmode: 'sum', dval: 0, id: '' }; }
  function loadWins() {
    var w = load('k_wins', null);
    if (!w || !w.length) w = [];
    for (var i = 0; i < WINS; i++) if (!w[i] || !w[i].lines) w[i] = emptyWin();
    return w.slice(0, WINS);
  }

  var S = {
    token: load('k_token', ''),
    name: load('k_name', ''),
    data: null, ver: 0, byCode: {}, dataAt: 0,
    wins: loadWins(),
    w: 0,
    sel: -1,
    disp: '',
    mult: 0,
    ptype: load('k_ptype', 'Цена1'),
    page: 0,
    print: load('k_print', true),
    pay: null,
    last: load('k_last', null),
    kb: null
  };
  if (L.PRICE_TYPES.indexOf(S.ptype) < 0) S.ptype = 'Цена1';
  function W() { return S.wins[S.w]; }

  // ---------------- yordamchilar ----------------
  var toastTimer = 0;
  function toast(msg, err) {
    var t = $('toast');
    t.textContent = msg;
    t.className = 'toast' + (err ? ' err' : '');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { t.className = 'toast hidden'; }, err ? 4500 : 2500);
    if (err) beep();
  }
  var audio = null;
  function beep() {
    try {
      audio = audio || new (window.AudioContext || window.webkitAudioContext)();
      var o = audio.createOscillator(); var g = audio.createGain();
      o.frequency.value = 880; g.gain.value = 0.15;
      o.connect(g); g.connect(audio.destination); o.start(); o.stop(audio.currentTime + 0.18);
    } catch (e) { /* ovoz yo'q */ }
  }
  function busy(on, text) {
    $('busyText').textContent = text || "1C'ga yozilmoqda…";
    $('busy').className = on ? 'busy' : 'busy hidden';
  }
  function api(method, path, body) {
    var opt = { method: method, headers: {} };
    if (body !== undefined) { opt.headers['Content-Type'] = 'application/json'; opt.body = JSON.stringify(body); }
    return fetch(path, opt).then(function (r) {
      if (r.status === 401) { lock('Qayta kiring'); throw new Error('login'); }
      return r.json().then(function (j) {
        if (!r.ok && !j.e) throw new Error('HTTP ' + r.status);
        return j;
      });
    });
  }
  function money(c) { return L.fmtMoney(c); }
  function el(tag, cls, text) {
    var e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text != null) e.textContent = text;
    return e;
  }
  function saveWins() { save('k_wins', S.wins); }
  function touchWin() { W().id = ''; saveWins(); }
  function ptypeLabel(p) { return 'Narx ' + (L.PRICE_TYPES.indexOf(p) + 1); }

  // ---------------- so'rov oynasi (parol / tanlov / tasdiq) ----------------
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
    $('askModal').classList.remove('hidden');
  }
  function closeAsk() { $('askModal').classList.add('hidden'); askCb = null; }

  /** 1C kabi: kamaytirish/o'chirish — "ИзмененияВСенсореСпаролем" yoqiq bo'lsa admin paroli so'raladi. */
  function needAdmin(what, fn) {
    if (!S.data || !S.data.lock) { fn(); return; }
    ask({
      title: 'Admin paroli', text: what, pw: true, yes: 'Ruxsat',
      onYes: function (pw) {
        return api('POST', '/api/admin', { token: S.token, pw: pw }).then(function (j) {
          if (!j.ok) throw new Error(j.e || "Ruxsat yo'q");
          fn();
        });
      }
    });
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
    api('POST', '/api/login', { pw: pw }).then(function (j) {
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
    showLogin(msg || '');
  }
  function enter() {
    $('login').classList.add('hidden');
    $('main').classList.remove('hidden');
    $('cashierName').textContent = S.name;
    renderAll();
    refreshData(true);
  }
  /** Brauzer oynasini yopadi (kiosk). Yopilmasa — barmoq bilan chiqish yo'li ko'rsatiladi. */
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
    return api('GET', '/api/data?t=' + encodeURIComponent(S.token) + '&v=' + (S.data ? S.ver : 0)).then(function (j) {
      busy(false);
      S.dataAt = Date.now();
      if (j.same) { renderDot(); return; }
      if (j.ok === false) { toast(j.e || "Ma'lumot olinmadi", true); renderDot(true); return; }
      setData(j);
    }).catch(function (e) {
      busy(false);
      renderDot(true);
      if (e.message !== 'login') toast('Kompyuter javob bermadi: ' + e.message, true);
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
  }
  /** Narx turi yoki 1C narxi o'zgarsa — savatdagi narxlar ham (1C ТипЦенаПриИзменении). */
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

  // ---------------- savat ----------------
  function total(w) { return L.total((w || W()).lines); }
  function discount(w) {
    w = w || W();
    var t = total(w);
    // foiz chegirma butun so'mga yaxlitlanadi (tiyin chiqmasin)
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
    if (W().mode === 'sale' && !S.data.neg) {
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
    saveWins();
    renderAll();
  }

  /** "Kiritish" yoki skaner Enter'i: 1C ПолеТекПриИзменении. */
  function enterCode(text) {
    text = String(text || '').trim();
    S.disp = '';
    if (!text || !S.data) { renderDisplay(); return; }
    var r = L.parseScan(text, S.data.rongta);
    var code = S.data.b[r.code];
    if (code && S.byCode[code]) { addGoods(S.byCode[code], r.milli || 1000, r.weighted); return; }
    if (/^[0-9]{1,7}$/.test(text) && S.byCode[text]) { addGoods(S.byCode[text], 1000, false); return; }
    toast('Topilmadi: ' + text, true);
    renderDisplay();
  }

  // ---------------- chizish ----------------
  function renderAll() {
    $('ptypeName').textContent = ptypeLabel(S.ptype);
    renderMode(); renderWins(); renderCart(); renderDisplay(); renderDot();
  }
  function renderMode() {
    var ret = W().mode === 'return';
    document.body.classList.toggle('ret', ret);
    $('modeBar').classList.toggle('hidden', !ret);
    $('retBtn').classList.toggle('on', ret);
    $('retSub').textContent = ret ? 'yoqilgan — bekor' : 'tovar qaytdi';
    $('payBtn').textContent = ret ? 'QAYTARISH' : "TO'LOV";
    $('tDueLabel').textContent = ret ? 'Qaytariladi' : "To'lanadi";
  }
  function renderWins() {
    var box = $('wins');
    box.textContent = '';
    S.wins.forEach(function (w, i) {
      var b = el('button', i === S.w ? 'on' : '');
      b.setAttribute('data-w', i);
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
    $('discSub').textContent = d ? 'bekor qilish' : 'summa';
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
    $('dHint').classList.toggle('hidden', !!S.disp || !!S.mult);
  }

  // ---------------- panel ----------------
  function tile(g) {
    var b = el('button', 'tile');
    b.setAttribute('data-code', g.c);
    b.appendChild(el('span', 'tn', g.n));
    var p = el('span', 'tp');
    p.appendChild(el('span', '', money(L.priceOf(g, S.ptype, S.data.rate))));
    p.appendChild(el('i', g.s > 0 ? '' : 'none', L.fmtQty(L.milli(g.s)) + (g.e ? ' ' + g.e : '')));
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
  function openKb(kind, title) {
    S.kb = { kind: kind, text: '', cyr: false };
    $('kbTitle').textContent = title;
    $('kb').classList.toggle('hidden', kind === 'price');
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
    [['lang', k.cyr ? 'ABC' : 'АБВ', 'w2'], ['space', "Bo'sh joy", 'w5'], ['back', '⌫', 'w2'], ['clear', 'Tozalash', 'w2']]
      .forEach(function (x) { var b = el('button', x[2], x[1]); b.setAttribute('data-op', x[0]); last.appendChild(b); });
    kb.appendChild(last);
    renderKbResults();
  }
  function renderKbResults() {
    var k = S.kb;
    var box = $('kbResults');
    box.textContent = '';
    if (!S.data) return;
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
      if (!n) box.appendChild(el('div', 'grid-msg', q ? 'Mijoz topilmadi' : 'Ismi yoki telefonini yozing (qarzdorlar ko\'rsatilgan)'));
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
    $('printChk').checked = !!S.print;
    $('payErr').textContent = '';
    $('payModal').classList.remove('hidden');
    renderPay();
    if (debtOnly) openKb('client', 'Qarz to\'laydigan mijoz');
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
    S.print = $('printChk').checked;
    save('k_print', S.print);
    busy(true);
    api('POST', '/api/sale', body).then(function (j) {
      busy(false);
      if (!j.ok) {
        $('payErr').textContent = j.e || 'Xato';
        beep();
        if (/Narx o'zgargan/.test(j.e || '')) refreshData();
        return;
      }
      done(j);
    }).catch(function (e) {
      busy(false);
      if (e.message === 'login') return;
      // javob kelmadi — so'rov raqami saqlanadi: qayta bosilsa 1C'ga ikkinchi marta yozilmaydi
      $('payErr').textContent = 'Kompyuter javob bermadi (' + e.message + '). Qayta bosing — ikki marta yozilmaydi.';
      beep();
    });
  }
  function done(res) {
    S.last = res; save('k_last', res);
    closePay();
    var kind = res.kind === 'return' ? 'Qaytarish' : (res.doc === 'ОплатаДолга' ? "Qarz to'lovi" : 'Sotuv');
    $('doneInfo').textContent = kind + ' № ' + res.no + ' · ' + res.date + ' · ' + money(L.cents(res.total));
    $('doneChange').textContent = res.change ? 'Qaytim: ' + money(L.cents(res.change)) :
      (res.debt > 0 && res.kind === 'sale' ? 'Qarzga: ' + money(L.cents(res.debt)) : '');
    $('doneModal').classList.remove('hidden');
    clearWin();
    refreshData();
    if (S.print) printReceipt(res);
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
    R.appendChild(el('div', 'c', (r.kind === 'return' ? 'QAYTARISH' : r.doc === 'ОплатаДолга' ? "QARZ TO'LOVI" : 'SOTUV') + ' № ' + r.no));
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
      line('Jami qarz', money(L.cents(r.cdebt)));
    }
    R.appendChild(el('hr'));
    R.appendChild(el('div', 'c', 'Xaridingiz uchun rahmat!'));
    setTimeout(function () { window.print(); }, 50);
  }

  function hideOverlays() {
    ['panelView', 'kbView', 'payModal', 'askModal', 'doneModal'].forEach(function (id) { $(id).classList.add('hidden'); });
    S.pay = null; S.kb = null; askCb = null;
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

  // ---------------- hodisalar ----------------
  function on(id, fn) { $(id).addEventListener('click', fn); }
  function bind() {
    // kirish
    $('loginPad').addEventListener('click', function (e) {
      var k = e.target.getAttribute && e.target.getAttribute('data-k');
      if (!k) return;
      var pw = $('pw');
      pw.value = k === 'C' ? '' : k === 'B' ? pw.value.slice(0, -1) : (pw.value + k).slice(0, 20);
    });
    on('loginBtn', doLogin);
    on('loginExit', closeKassa);

    // yuqori qism
    $('wins').addEventListener('click', function (e) {
      var b = e.target.closest ? e.target.closest('button[data-w]') : null;
      if (!b) return;
      S.w = parseInt(b.getAttribute('data-w'), 10); S.sel = -1; S.mult = 0; S.disp = '';
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
    on('lockBtn', function () { lock(''); });
    on('exitBtn', function () {
      ask({ title: 'Kassadan chiqish', text: "Kassa oynasi yopiladi. Savatlar saqlanib qoladi.", yes: 'Ha, chiqish',
        onYes: function () { closeKassa(); return Promise.resolve(); } });
    });

    // savat
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

    // raqam paneli
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

    // funksiya tugmalari
    on('panelBtn', openPanel);
    on('searchBtn', function () { openKb('goods', 'Qidiruv'); });
    on('priceBtn', function () {
      var v = takeDisp('Narx');
      if (v == null) return;
      var pc = L.cents(v);
      var res = S.data ? S.data.g.filter(function (g) { return L.priceOf(g, S.ptype, S.data.rate) === pc; }) : [];
      if (res.length === 1) { addGoods(res[0], 1000, false); return; }
      if (!res.length) { toast(money(pc) + " narxli tovar yo'q", true); return; }
      openKb('price', money(pc) + ' narxli tovarlar');
      S.kb.text = v;
      renderKbResults();
      $('kbText').textContent = money(pc);
    });
    on('codeBtn', function () {
      var v = takeDisp('Kod');
      if (v == null) return;
      var code = v.replace('.', '');
      var g = S.byCode[code];
      if (!g && S.data) { var bc = S.data.b[code]; g = bc ? S.byCode[bc] : null; }
      if (!g) { toast('Kod topilmadi: ' + code, true); return; }
      addGoods(g, 1000, false);
    });
    on('qtyBtn', function () {
      if (S.sel < 0) { toast('Avval savatdan qatorni tanlang', true); return; }
      var v = takeDisp('Miqdor');
      if (v == null) return;
      var m = L.milli(v), i = S.sel;
      if (m < W().lines[i].milli) needAdmin('Miqdorni kamaytirish', function () { setQty(i, m); });
      else setQty(i, m);
    });
    on('discBtn', function () {
      var w = W();
      if (!S.disp && discount(w)) { w.dmode = 'sum'; w.dval = 0; touchWin(); renderCart(); return; }
      var v = takeDisp('Chegirma summasi');
      if (v == null) return;
      w.dmode = 'sum'; w.dval = L.cents(v);
      if (w.dval > total(w)) { toast('Chegirma jami summadan katta', true); w.dval = 0; }
      touchWin(); renderCart();
    });
    on('discPBtn', function () {
      var v = takeDisp('Chegirma foizi');
      if (v == null) return;
      var bp = L.cents(v);   // 12,5% -> 1250
      if (bp > 10000) { toast("Foiz 100 dan oshmaydi", true); return; }
      W().dmode = 'pct'; W().dval = bp;
      touchWin(); renderCart();
    });
    on('retBtn', function () {
      var w = W();
      w.mode = w.mode === 'return' ? 'sale' : 'return';
      touchWin(); renderMode();
    });
    on('debtBtn', function () {
      if (W().lines.length) { toast("Avval savatdagi savdoni yakunlang yoki bo'sh oynaga o'ting", true); return; }
      if (W().mode === 'return') { W().mode = 'sale'; renderMode(); }
      openPay(true);
    });
    on('lastBtn', function () { if (S.last) printReceipt(S.last); else toast("Hali chek yo'q", true); });
    on('refreshBtn', function () {
      busy(true, "1C'dan yangilanmoqda…");
      api('POST', '/api/refresh', { token: S.token }).then(function (j) {
        busy(false);
        if (j.ok === false) { toast(j.e || 'Xato', true); return; }
        refreshData();
      }).catch(function (e) { busy(false); if (e.message !== 'login') toast(e.message, true); });
    });
    on('payBtn', function () { openPay(false); });

    // panel
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

    // klaviatura
    $('kb').addEventListener('click', function (e) {
      var b = e.target.closest ? e.target.closest('button') : null;
      if (!b || !S.kb) return;
      var ch = b.getAttribute('data-ch'), op = b.getAttribute('data-op');
      if (ch) S.kb.text = (S.kb.text + ch).slice(0, 40);
      else if (op === 'space') S.kb.text = (S.kb.text + ' ').slice(0, 40);
      else if (op === 'back') S.kb.text = S.kb.text.slice(0, -1);
      else if (op === 'clear') S.kb.text = '';
      else if (op === 'lang') { S.kb.cyr = !S.kb.cyr; renderKb(); return; }
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

    // to'lov
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

    // so'rov oynasi
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

    // natija
    on('donePrint', function () { if (S.last) printReceipt(S.last); });
    on('doneNew', function () { $('doneModal').classList.add('hidden'); });

    // skaner (klaviatura sifatida ishlaydi): raqamlar maydonga, Enter — kiritish
    document.addEventListener('keydown', function (e) {
      if ($('main').classList.contains('hidden')) {
        if (e.key === 'Enter') doLogin();
        else if (/^[0-9]$/.test(e.key)) $('pw').value = ($('pw').value + e.key).slice(0, 20);
        else if (e.key === 'Backspace') $('pw').value = $('pw').value.slice(0, -1);
        return;
      }
      if (S.kb) {
        if (e.key.length === 1) S.kb.text = (S.kb.text + e.key.toLowerCase()).slice(0, 40);
        else if (e.key === 'Backspace') S.kb.text = S.kb.text.slice(0, -1);
        else return;
        $('kbText').textContent = S.kb.text;
        renderKbResults();
        e.preventDefault();
        return;
      }
      if (!$('payModal').classList.contains('hidden') || !$('askModal').classList.contains('hidden')) return;
      if (!$('doneModal').classList.contains('hidden')) $('doneModal').classList.add('hidden');
      if (!$('panelView').classList.contains('hidden')) $('panelView').classList.add('hidden');
      if (e.key === 'Enter') { e.preventDefault(); enterCode(S.disp); return; }
      if (e.key === 'Backspace') { S.disp = S.disp.slice(0, -1); renderDisplay(); return; }
      if (e.key.length === 1 && /[0-9A-Za-z.\-]/.test(e.key)) { S.disp = (S.disp + e.key).slice(0, 40); renderDisplay(); e.preventDefault(); }
    });
  }

  function tickClock() {
    var d = new Date();
    var p = function (n) { return (n < 10 ? '0' : '') + n; };
    $('clock').textContent = p(d.getHours()) + ':' + p(d.getMinutes());
    renderDot();
  }

  // ---------------- boshlash ----------------
  bind();
  tickClock();
  setInterval(tickClock, 15000);
  setInterval(function () { if (S.token && !$('main').classList.contains('hidden')) refreshData(); }, 30000);
  if (S.token) enter(); else showLogin('');
})();
