/* v5.4 kassa oynasi — brauzer tomoni. Hisob-kitob logic.js'da (kassa_core.py bilan bir xil). */
(function () {
  'use strict';
  var L = window.KassaLogic;
  var $ = function (id) { return document.getElementById(id); };

  // ---------------- saqlash (brauzer xotirasi bo'lmasa ham ishlaydi) ----------------
  function load(key, def) {
    try { var v = window.localStorage.getItem(key); return v == null ? def : JSON.parse(v); } catch (e) { return def; }
  }
  function save(key, v) {
    try { window.localStorage.setItem(key, JSON.stringify(v)); } catch (e) { /* xotira yo'q */ }
  }

  var S = {
    token: load('k_token', ''),
    name: load('k_name', ''),
    data: null,
    ver: 0,
    byCode: {},
    dataAt: 0,
    cart: load('k_cart', []),
    sel: -1,
    mult: '',
    ptype: load('k_ptype', 'Цена1'),
    mode: 'sale',
    page: 0,
    query: '',
    print: load('k_print', true),
    pay: null,
    saleId: load('k_sale_id', ''),
    last: null
  };
  if (L.PRICE_TYPES.indexOf(S.ptype) < 0) S.ptype = 'Цена1';

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
      if (r.status === 401) { logout(true); throw new Error('login'); }
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

  function saveCart() { save('k_cart', S.cart); }
  function newSaleId() { S.saleId = ''; save('k_sale_id', ''); }

  // ---------------- kirish ----------------
  function showLogin(msg) {
    $('main').classList.add('hidden');
    $('login').classList.remove('hidden');
    $('loginErr').textContent = msg || '';
    $('pw').value = '';
    $('pw').focus();
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
      $('loginErr').textContent = "Kompyuterga ulanib bo'lmadi: " + e.message;
    });
  }

  function logout(expired) {
    S.token = ''; save('k_token', '');
    showLogin(expired ? 'Qayta kiring' : '');
  }

  function enter() {
    $('login').classList.add('hidden');
    $('main').classList.remove('hidden');
    $('cashierName').textContent = S.name;
    renderAll();
    refreshData(true);
    focusQ();
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
      if (e.message !== 'login') toast("Kompyuter javob bermadi: " + e.message, true);
    });
  }

  function setData(d) {
    S.data = d;
    S.ver = d.ver;
    S.byCode = {};
    for (var i = 0; i < d.g.length; i++) S.byCode[d.g[i].c] = d.g[i];
    $('storeName').textContent = d.title || d.store || 'Kassa';
    document.title = (d.title || 'Kassa');
    var changed = repriceCart(false);
    if (changed) toast('Narxlar yangilandi: ' + changed + ' ta qator');
    renderAll();
  }

  /** Narx turi yoki 1C narxi o'zgarsa savatdagi narxlar ham yangilanadi (1C ТипЦенаПриИзменении). */
  function repriceCart(force) {
    if (!S.data) return 0;
    var n = 0;
    for (var i = 0; i < S.cart.length; i++) {
      var g = S.byCode[S.cart[i].code];
      if (!g) continue;
      var p = L.priceOf(g, S.ptype, S.data.rate);
      if (p !== S.cart[i].price) { S.cart[i].price = p; n++; }
    }
    if (n || force) { saveCart(); newSaleId(); }
    return n;
  }

  function renderDot(bad) {
    var d = $('dataDot');
    var age = Date.now() - S.dataAt;
    d.className = 'dot ' + (bad ? 'bad' : age < 120000 ? 'ok' : 'old');
  }

  // ---------------- savat ----------------
  function stockLeft(code, exceptIndex) {
    var g = S.byCode[code];
    var have = L.milli(g ? g.s : 0);
    for (var i = 0; i < S.cart.length; i++) if (i !== exceptIndex && S.cart[i].code === code) have -= S.cart[i].milli;
    return have;
  }

  function takeMult() {
    var m = S.mult ? L.milli(S.mult) : 0;
    S.mult = '';
    renderMult();
    return m;
  }

  function addGoods(g, milli, weighted) {
    if (!g) return;
    var price = L.priceOf(g, S.ptype, S.data.rate);
    if (price <= 0) { toast(g.n + ': bu narx turida narx yo\'q', true); return; }
    var mult = takeMult();
    if (mult > 0) milli = weighted ? Math.round(milli * mult / 1000) : mult * (milli / 1000);
    milli = Math.round(milli);
    if (milli <= 0) { toast('Miqdor 0', true); return; }
    var idx = -1;
    if (!weighted) for (var i = 0; i < S.cart.length; i++) if (S.cart[i].code === g.c && !S.cart[i].w) { idx = i; break; }
    var cur = idx >= 0 ? S.cart[idx].milli : 0;
    var want = cur + milli;
    if (S.mode === 'sale' && !S.data.neg) {
      var cap = L.capQty(want, (stockLeft(g.c, idx) / 1000), false);
      if (cap.capped) {
        toast(g.n + ': qoldiq ' + L.fmtQty(Math.max(stockLeft(g.c, idx), 0)) + ' — ko\'proq sotib bo\'lmaydi', true);
        want = cap.milli;
        if (want <= cur) { return; }
      }
    }
    if (idx >= 0) {
      S.cart[idx].milli = want;
      S.sel = idx;
    } else {
      S.cart.push({ code: g.c, name: g.n, unit: g.e || '', price: price, milli: want, w: !!weighted });
      S.sel = S.cart.length - 1;
    }
    saveCart(); newSaleId();
    renderCart(true);
  }

  function setQty(i, milli) {
    var l = S.cart[i];
    if (!l) return;
    if (milli <= 0) { removeLine(i); return; }
    if (S.mode === 'sale' && !S.data.neg) {
      var cap = L.capQty(milli, stockLeft(l.code, i) / 1000, false);
      if (cap.capped) { toast(l.name + ': qoldiq yetmaydi', true); milli = cap.milli; if (milli <= 0) return; }
    }
    l.milli = milli;
    saveCart(); newSaleId();
    renderCart();
  }

  function removeLine(i) {
    S.cart.splice(i, 1);
    if (S.sel >= S.cart.length) S.sel = S.cart.length - 1;
    saveCart(); newSaleId();
    renderCart();
  }

  function clearCart() {
    S.cart = []; S.sel = -1; S.mult = '';
    saveCart(); newSaleId();
    renderCart(); renderMult();
  }

  function scan(text) {
    text = String(text || '').trim();
    if (!text || !S.data) return;
    if (/^[0-9]+$/.test(text)) {
      var r = L.parseScan(text, S.data.rongta);
      var code = S.data.b[r.code];
      if (code && S.byCode[code]) { addGoods(S.byCode[code], r.milli || 1000, r.weighted); clearQ(); return; }
      if (text.length <= 7 && S.byCode[text]) { addGoods(S.byCode[text], 1000, false); clearQ(); return; }
      if (text.length >= 8) { toast('Bunday shtrix kodli mahsulot topilmadi: ' + text, true); clearQ(); return; }
    }
    // nom/narx bo'yicha qidiruv natijasi panel o'rnida
    S.query = text;
    renderGrid();
  }

  function clearQ() {
    $('q').value = '';
    if (S.query) { S.query = ''; renderGrid(); }
  }

  // ---------------- chizish ----------------
  function renderAll() {
    renderPtypes(); renderMode(); renderTabs(); renderGrid(); renderCart(); renderMult(); renderDot();
  }

  function renderPtypes() {
    var bs = $('ptypes').querySelectorAll('button');
    for (var i = 0; i < bs.length; i++) bs[i].classList.toggle('on', bs[i].getAttribute('data-p') === S.ptype);
  }

  function renderMode() {
    document.body.classList.toggle('ret', S.mode === 'return');
    $('modeSale').classList.toggle('on', S.mode === 'sale');
    $('modeRet').classList.toggle('on', S.mode === 'return');
    $('payBtn').textContent = S.mode === 'return' ? 'Qaytarish' : "To'lov";
  }

  function renderTabs() {
    var t = $('tabs');
    t.textContent = '';
    var pages = 8;
    for (var i = 0; i < pages; i++) {
      var b = el('button', i === S.page && !S.query ? 'on' : '', String(i + 1));
      b.setAttribute('data-page', i);
      t.appendChild(b);
    }
    if (S.query) {
      var q = el('button', 'on', 'Qidiruv: ' + S.query.slice(0, 18));
      q.setAttribute('data-page', 'q');
      t.appendChild(q);
    }
  }

  function tile(g) {
    var b = el('button', 'tile');
    b.setAttribute('data-code', g.c);
    b.appendChild(el('span', 'tn', g.n));
    var p = el('span', 'tp');
    p.appendChild(el('span', '', money(L.priceOf(g, S.ptype, S.data.rate))));
    var st = el('i', g.s > 0 ? '' : 'none', L.fmtQty(L.milli(g.s)) + (g.e ? ' ' + g.e : ''));
    p.appendChild(st);
    b.appendChild(p);
    return b;
  }

  function renderGrid() {
    renderTabs();
    var grid = $('grid');
    grid.textContent = '';
    grid.className = 'grid';
    if (!S.data) { grid.appendChild(el('div', 'grid-msg', 'Yuklanmoqda…')); return; }
    if (S.query) {
      grid.className = 'grid list';
      var res = L.search(S.data.g, S.query, S.ptype, S.data.rate, 60);
      if (!res.length) grid.appendChild(el('div', 'grid-msg', 'Topilmadi: ' + S.query));
      for (var i = 0; i < res.length; i++) grid.appendChild(tile(res[i]));
      return;
    }
    var pnl = S.data.pnl || [];
    if (!pnl.length) { grid.appendChild(el('div', 'grid-msg', "Panel bo'sh — tovarni skanerlang yoki nomini yozing")); return; }
    for (var k = S.page * 42; k < S.page * 42 + 42; k++) {
      var slot = pnl[k] || ['', ''];
      var g = slot[0] ? S.byCode[slot[0]] : null;
      if (g) grid.appendChild(tile(g));
      else grid.appendChild(el('div', 'tile empty'));
    }
  }

  function renderCart(scrollToSel) {
    var c = $('cart');
    c.textContent = '';
    $('cartEmpty').classList.toggle('hidden', S.cart.length > 0);
    c.classList.toggle('hidden', S.cart.length === 0);
    var count = 0;
    for (var i = 0; i < S.cart.length; i++) {
      var l = S.cart[i];
      var row = el('div', 'row' + (i === S.sel ? ' sel' : ''));
      row.setAttribute('role', 'listitem');
      row.setAttribute('data-i', i);
      row.appendChild(el('div', 'n', (i + 1) + '. ' + l.name));
      row.appendChild(el('div', 's', money(L.lineSum(l.price, l.milli))));
      var d = el('div', 'd', L.fmtQty(l.milli) + (l.unit ? ' ' + l.unit : '') + ' × ' + money(l.price));
      var g = S.byCode[l.code];
      if (!g) d.appendChild(el('span', 'warn', ' · 1C\'da yo\'q'));
      row.appendChild(d);
      c.appendChild(row);
      count += 1;
    }
    var tot = L.total(S.cart);
    $('cartTotal').textContent = money(tot);
    $('cartCount').textContent = count + ' ta';
    var bar = $('selBar');
    if (S.sel >= 0 && S.cart[S.sel]) {
      bar.classList.remove('hidden');
      $('selQty').textContent = L.fmtQty(S.cart[S.sel].milli) + (S.cart[S.sel].unit ? ' ' + S.cart[S.sel].unit : '');
    } else bar.classList.add('hidden');
    if (scrollToSel) {
      var r = c.querySelector('.row.sel');
      if (r && r.scrollIntoView) r.scrollIntoView({ block: 'nearest' });
    }
  }

  function renderMult() {
    $('mult').textContent = S.mult ? S.mult.replace('.', ',') : '—';
    $('multBox').classList.toggle('on', !!S.mult);
  }

  // ---------------- raqam paneli ----------------
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

  // ---------------- to'lov ----------------
  function openPay() {
    if (!S.data) return;
    if (S.mode === 'return' && !S.cart.length) { toast("Qaytariladigan tovarni qo'shing", true); return; }
    if (!S.saleId) { S.saleId = L.newId(); save('k_sale_id', S.saleId); }
    var tot = L.total(S.cart);
    S.pay = { total: tot, discount: 0, cash: 0, card: 0, click: 0, field: 'cash', buf: '', client: null };
    if (tot > 0) S.pay.cash = tot;
    $('payTitle').textContent = S.mode === 'return' ? 'Qaytarish' : (tot === 0 ? "Qarz to'lovi" : "To'lov");
    $('pDueLabel').textContent = S.mode === 'return' ? 'Qaytariladi' : "To'lanadi";
    $('printChk').checked = !!S.print;
    $('clientQ').value = '';
    $('payErr').textContent = '';
    $('payModal').classList.remove('hidden');
    renderClients();
    renderPay();
  }

  function closePay() {
    $('payModal').classList.add('hidden');
    S.pay = null;
    focusQ();
  }

  function payState() {
    var p = S.pay;
    return L.settle(p.total, p.discount, p.cash, p.card, p.click, !!p.client, S.mode === 'return');
  }

  function renderPay() {
    var p = S.pay;
    if (!p) return;
    var due = p.total - p.discount;
    $('pTotal').textContent = money(p.total);
    $('pDiscount').textContent = money(p.discount);
    $('pDue').textContent = money(due > 0 ? due : p.total);
    $('fCash').textContent = money(p.cash);
    $('fCard').textContent = money(p.card);
    $('fClick').textContent = money(p.click);
    $('fDiscount').textContent = money(p.discount);
    var fs = document.querySelectorAll('.field');
    for (var i = 0; i < fs.length; i++) fs[i].classList.toggle('on', fs[i].getAttribute('data-f') === p.field);
    var names = { cash: 'Naqd', card: 'Plastik', click: 'Click', discount: 'Chegirma' };
    $('padTarget').textContent = names[p.field];
    $('padVal').textContent = p.buf ? L.fmtMoney(L.cents(p.buf)) : money(p[p.field]);
    var st = payState();
    var res = $('pResult');
    if (st.e) { res.className = 'pay-result debt'; res.textContent = st.e; }
    else if (st.change > 0) { res.className = 'pay-result change'; res.textContent = 'Qaytim: ' + money(st.change); }
    else if (st.debt > 0) { res.className = 'pay-result debt'; res.textContent = (S.mode === 'return' ? 'Mijoz qarzidan ayriladi: ' : 'Qarzga: ') + money(st.debt); }
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

  function commitBuf() {
    var p = S.pay;
    if (!p || !p.buf) return;
    p[p.field] = L.cents(p.buf);
    p.buf = '';
  }

  function renderClients() {
    var list = $('clientList');
    list.textContent = '';
    if (!S.data) return;
    var q = $('clientQ').value.toLowerCase().trim();
    var digits = q.replace(/[^0-9]/g, '');
    var shown = 0;
    for (var i = 0; i < S.data.cl.length && shown < 40; i++) {
      var c = S.data.cl[i];
      if (q) {
        var okName = c[1].toLowerCase().indexOf(q) >= 0;
        var okTel = digits.length >= 3 && String(c[2]).replace(/[^0-9]/g, '').indexOf(digits) >= 0;
        if (!okName && !okTel) continue;
      } else if (!(c[3] > 0)) continue;     // qidirilmasa — faqat qarzdorlar
      var b = el('button');
      b.setAttribute('data-ci', i);
      var left = el('span');
      left.appendChild(document.createTextNode(c[1]));
      if (c[2]) left.appendChild(el('small', '', c[2]));
      b.appendChild(left);
      if (c[3]) b.appendChild(el('span', c[3] > 0 ? 'debt' : '', money(L.cents(c[3]))));
      list.appendChild(b);
      shown++;
    }
    if (!shown) list.appendChild(el('div', 'grid-msg', q ? 'Mijoz topilmadi' : 'Mijozni ism yoki telefon bilan qidiring'));
  }

  function submitPay() {
    commitBuf();
    var st = payState();
    if (st.e) { $('payErr').textContent = st.e; beep(); return; }
    var p = S.pay;
    var body = {
      id: S.saleId, token: S.token, kind: S.mode, ptype: S.ptype,
      client: p.client ? p.client[0] : '',
      discount: L.centsText(st.discount), cash: L.centsText(p.cash), card: L.centsText(p.card), click: L.centsText(p.click),
      lines: S.cart.map(function (l) { return { code: l.code, name: l.name, qty: L.milliText(l.milli), price: L.centsText(l.price) }; })
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
      // javob kelmadi — so'rov raqami saqlanadi: qayta bosilsa 1C'ga ikkinchi marta yozilmaydi
      $('payErr').textContent = "Kompyuter javob bermadi (" + e.message + "). Qayta bosing — ikki marta yozilmaydi.";
      beep();
    });
  }

  function done(res) {
    S.last = res;
    $('payModal').classList.add('hidden');
    S.pay = null;
    var kind = res.kind === 'return' ? 'Qaytarish' : (res.doc === 'ОплатаДолга' ? "Qarz to'lovi" : 'Sotuv');
    $('doneInfo').textContent = kind + ' № ' + res.no + ' · ' + res.date + ' · ' + money(L.cents(res.total));
    $('doneChange').textContent = res.change ? 'Qaytim: ' + money(L.cents(res.change)) : (res.debt > 0 && res.kind === 'sale' ? 'Qarzga: ' + money(L.cents(res.debt)) : '');
    $('doneModal').classList.remove('hidden');
    S.cart = []; S.sel = -1; S.mult = '';
    saveCart(); newSaleId();
    if (S.mode === 'return') { S.mode = 'sale'; renderMode(); }
    renderCart(); renderMult();
    refreshData();
    if (S.print) printReceipt(res);
  }

  function clientOf(code) {
    if (!S.data || !code) return null;
    for (var i = 0; i < S.data.cl.length; i++) if (S.data.cl[i][0] === code) return S.data.cl[i];
    return null;
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
    for (var i = 0; i < r.lines.length; i++) {
      var l = r.lines[i];
      var it = el('div', 'item');
      it.appendChild(el('div', '', l.n));
      var ln = el('div', 'ln');
      ln.appendChild(el('span', '', L.fmtQty(L.milli(l.q)) + ' × ' + money(L.cents(l.p))));
      ln.appendChild(el('b', '', money(L.cents(l.s))));
      it.appendChild(ln);
      R.appendChild(it);
    }
    R.appendChild(el('hr'));
    line('Jami', money(L.cents(r.total)), 'big');
    if (r.discount) line('Chegirma', money(L.cents(r.discount)));
    if (r.discount) line("To'lanadi", money(L.cents(r.due)), 'big');
    if (r.cash) line('Naqd', money(L.cents(r.cash)));
    if (r.card) line('Plastik', money(L.cents(r.card)));
    if (r.click) line('Click', money(L.cents(r.click)));
    if (r.change) line('Qaytim', money(L.cents(r.change)));
    if (r.client) {
      var c = clientOf(r.client);
      R.appendChild(el('hr'));
      line('Mijoz', r.cname || (c ? c[1] : r.client));
      if (r.debt) line(r.debt > 0 ? (r.kind === 'return' ? 'Qarzdan ayrildi' : 'Qarzga yozildi') : "Qarz to'landi", money(L.cents(Math.abs(r.debt))));
      line('Jami qarz', money(L.cents(r.cdebt)));
    }
    R.appendChild(el('hr'));
    R.appendChild(el('div', 'c', 'Xaridingiz uchun rahmat!'));
    setTimeout(function () { window.print(); }, 50);
  }

  // ---------------- fokus ----------------
  function focusQ() {
    if (!$('payModal').classList.contains('hidden')) return;
    var q = $('q');
    if (document.activeElement !== q) q.focus({ preventScroll: true });
  }

  // ---------------- hodisalar ----------------
  function bind() {
    // kirish
    $('loginPad').addEventListener('click', function (e) {
      var k = e.target.getAttribute && e.target.getAttribute('data-k');
      if (!k) return;
      var pw = $('pw');
      pw.value = k === 'C' ? '' : k === 'B' ? pw.value.slice(0, -1) : (pw.value + k).slice(0, 20);
    });
    $('loginBtn').addEventListener('click', doLogin);
    $('pw').addEventListener('keydown', function (e) { if (e.key === 'Enter') doLogin(); });

    // yuqori qism
    $('ptypes').addEventListener('click', function (e) {
      var p = e.target.getAttribute && e.target.getAttribute('data-p');
      if (!p || p === S.ptype) return;
      S.ptype = p; save('k_ptype', p);
      repriceCart(true);
      renderPtypes(); renderGrid(); renderCart();
    });
    $('modeSale').addEventListener('click', function () { if (S.mode !== 'sale') { S.mode = 'sale'; newSaleId(); renderMode(); } });
    $('modeRet').addEventListener('click', function () { if (S.mode !== 'return') { S.mode = 'return'; newSaleId(); renderMode(); } });
    $('refreshBtn').addEventListener('click', function () {
      busy(true, "1C'dan yangilanmoqda…");
      api('POST', '/api/refresh', { token: S.token }).then(function (j) {
        busy(false);
        if (j.ok === false) { toast(j.e || 'Xato', true); return; }
        refreshData();
      }).catch(function (e) { busy(false); if (e.message !== 'login') toast(e.message, true); });
    });
    $('logoutBtn').addEventListener('click', function () {
      if (S.cart.length && !window.confirm("Savatda tovar bor. Chiqasizmi? (savat saqlanib qoladi)")) return;
      logout(false);
    });

    // skaner / qidiruv
    var q = $('q');
    q.addEventListener('keydown', function (e) {
      if (e.key === 'Enter') { e.preventDefault(); var v = q.value; q.value = ''; scan(v); }
      else if (e.key === 'Escape') clearQ();
    });
    q.addEventListener('input', function () {
      var v = q.value.trim();
      // harf yozilsa — darhol qidiruv (skaner raqamlari Enter bilan keladi)
      if (/[^0-9]/.test(v) && v.length >= 2) { S.query = v; renderGrid(); }
      else if (!v && S.query) { S.query = ''; renderGrid(); }
    });
    $('qClear').addEventListener('click', function () { clearQ(); q.setAttribute('inputmode', 'none'); focusQ(); });
    $('kbBtn').addEventListener('click', function () {
      q.setAttribute('inputmode', 'text');
      q.blur();
      setTimeout(function () { q.focus(); }, 30);
    });

    // panel
    $('tabs').addEventListener('click', function (e) {
      var p = e.target.getAttribute && e.target.getAttribute('data-page');
      if (p == null) return;
      if (p === 'q') return;
      S.page = parseInt(p, 10); S.query = ''; q.value = '';
      renderGrid();
    });
    $('grid').addEventListener('click', function (e) {
      var t = e.target.closest ? e.target.closest('.tile[data-code]') : null;
      if (!t) return;
      addGoods(S.byCode[t.getAttribute('data-code')], 1000, false);
      focusQ();
    });

    // savat
    $('cart').addEventListener('click', function (e) {
      var r = e.target.closest ? e.target.closest('.row') : null;
      if (!r) return;
      var i = parseInt(r.getAttribute('data-i'), 10);
      S.sel = S.sel === i ? -1 : i;
      renderCart();
    });
    $('selPlus').addEventListener('click', function () { if (S.sel >= 0) setQty(S.sel, S.cart[S.sel].milli + 1000); });
    $('selMinus').addEventListener('click', function () {
      if (S.sel < 0) return;
      var m = S.cart[S.sel].milli;
      setQty(S.sel, m > 1000 ? m - 1000 : 0);
    });
    $('selDel').addEventListener('click', function () { if (S.sel >= 0) removeLine(S.sel); });
    $('clearBtn').addEventListener('click', function () {
      if (!S.cart.length) return;
      if (window.confirm("Savatni tozalaysizmi?")) clearCart();
    });
    $('payBtn').addEventListener('click', openPay);

    // raqam paneli
    $('mainPad').addEventListener('click', function (e) {
      var k = e.target.getAttribute && e.target.getAttribute('data-k');
      if (!k) return;
      S.mult = padKey(S.mult, k, 9, true);
      renderMult();
    });
    $('setQtyBtn').addEventListener('click', function () {
      if (S.sel < 0) { toast('Avval savatdan qatorni tanlang', true); return; }
      var m = takeMult();
      if (m <= 0) { toast('Miqdorni raqam panelida yozing', true); return; }
      setQty(S.sel, m);
    });
    $('codeBtn').addEventListener('click', function () {
      var code = S.mult.replace('.', '');
      S.mult = ''; renderMult();
      if (!code) { toast('Tovar kodini raqam panelida yozing', true); return; }
      var g = S.byCode[code];
      if (!g && S.data) { var bc = S.data.b[code]; g = bc ? S.byCode[bc] : null; }
      if (!g) { toast('Kod topilmadi: ' + code, true); return; }
      addGoods(g, 1000, false);
    });

    // to'lov oynasi
    $('payClose').addEventListener('click', closePay);
    document.querySelector('.fields').addEventListener('click', function (e) {
      var f = e.target.closest ? e.target.closest('.field') : null;
      if (!f || !S.pay) return;
      commitBuf();
      S.pay.field = f.getAttribute('data-f');
      renderPay();
    });
    document.querySelector('.quick').addEventListener('click', function (e) {
      var a = e.target.getAttribute && e.target.getAttribute('data-all');
      if (!a || !S.pay) return;
      S.pay.buf = '';
      var due = S.pay.total - S.pay.discount;
      S.pay.cash = 0; S.pay.card = 0; S.pay.click = 0;
      S.pay[a] = due > 0 ? due : S.pay.total;
      S.pay.field = a;
      renderPay();
    });
    $('payPad').addEventListener('click', function (e) {
      var k = e.target.getAttribute && e.target.getAttribute('data-k');
      if (!k || !S.pay) return;
      S.pay.buf = padKey(S.pay.buf, k, 12, false);
      S.pay[S.pay.field] = L.cents(S.pay.buf || '0');
      if (S.pay.field === 'discount') {
        // 1C kabi: chegirma o'zgarsa to'lov qaytadan — qolgan summa naqdga qo'yiladi
        var due = S.pay.total - S.pay.discount;
        S.pay.cash = due > 0 ? due : 0; S.pay.card = 0; S.pay.click = 0;
      }
      renderPay();
    });
    $('clientQ').addEventListener('input', renderClients);
    $('clientList').addEventListener('click', function (e) {
      var b = e.target.closest ? e.target.closest('button[data-ci]') : null;
      if (!b || !S.pay) return;
      S.pay.client = S.data.cl[parseInt(b.getAttribute('data-ci'), 10)];
      $('clientQ').value = '';
      renderClients(); renderPay();
    });
    $('clientClear').addEventListener('click', function () { if (S.pay) { S.pay.client = null; renderPay(); } });
    $('payOk').addEventListener('click', submitPay);

    // natija
    $('donePrint').addEventListener('click', function () { if (S.last) printReceipt(S.last); });
    $('doneNew').addEventListener('click', function () { $('doneModal').classList.add('hidden'); focusQ(); });

    // har qanday bosishdan keyin skaner maydoniga qaytamiz (oynalar yopiq bo'lsa)
    document.addEventListener('pointerup', function (e) {
      var t = e.target;
      if (t && (t.tagName === 'INPUT')) return;
      setTimeout(focusQ, 0);
    });
    // skaner Enter'i boshqa joyda bo'lsa ham ushlanadi
    document.addEventListener('keydown', function (e) {
      if (!$('payModal').classList.contains('hidden') || $('main').classList.contains('hidden')) return;
      if (!$('doneModal').classList.contains('hidden') && e.key === 'Enter') { $('doneModal').classList.add('hidden'); }
      if (document.activeElement !== $('q') && e.key.length === 1) focusQ();
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
