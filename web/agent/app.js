/*
 * IMSOFT Savdo Agent — iPhone (veb-ilova, PWA). Android agentning "Admin orqali" rejimi bilan bir xil:
 * kirish, katalog (4 narx), savdo/qaytarish, to'lov, qarz, hisobot, 1C tarixi. Hammasi Firebase ko'prigi orqali
 * (g/{kalit}/...) — Admin telefoni serverga yozadi. Internetsiz: katalog, tarix va yuborilmagan savdolar telefonda saqlanadi.
 */
(function () {
  'use strict';
  var C = window.Core;
  var VERSION = '1.0';
  var $app = document.getElementById('app');

  // ================= saqlash =================

  var LS = {
    get: function (k, d) { try { var v = localStorage.getItem('ims_' + k); return v == null ? d : JSON.parse(v); } catch (e) { return d; } },
    set: function (k, v) { try { localStorage.setItem('ims_' + k, JSON.stringify(v)); } catch (e) { /* xotira to'la */ } },
    del: function (k) { try { localStorage.removeItem('ims_' + k); } catch (e) { /* */ } },
  };

  var idbP = null;
  function idb() {
    if (idbP) return idbP;
    idbP = new Promise(function (res, rej) {
      var r = indexedDB.open('imsoft-agent', 1);
      r.onupgradeneeded = function () { r.result.createObjectStore('kv'); };
      r.onsuccess = function () { res(r.result); };
      r.onerror = function () { rej(r.error); };
    });
    return idbP;
  }
  function idbGet(k) {
    return idb().then(function (db) {
      return new Promise(function (res) {
        var q = db.transaction('kv').objectStore('kv').get(k);
        q.onsuccess = function () { res(q.result); };
        q.onerror = function () { res(undefined); };
      });
    }).catch(function () { return undefined; });
  }
  function idbSet(k, v) {
    return idb().then(function (db) {
      return new Promise(function (res) {
        var t = db.transaction('kv', 'readwrite');
        t.objectStore('kv').put(v, k);
        t.oncomplete = function () { res(true); };
        t.onerror = function () { res(false); };
      });
    }).catch(function () { return false; });
  }
  function idbDel(k) {
    return idb().then(function (db) {
      return new Promise(function (res) {
        var t = db.transaction('kv', 'readwrite');
        t.objectStore('kv').delete(k);
        t.oncomplete = function () { res(true); };
        t.onerror = function () { res(false); };
      });
    }).catch(function () { return false; });
  }

  // ================= holat =================

  var S = {
    cfg: LS.get('cfg', null), // {dbUrl, key}
    agent: LS.get('agent', null), // {id, name, boss, phone}
    cat: null, // parseCatalog natijasi
    catAt: LS.get('catAt', 0),
    draft: LS.get('draft', null),
    outbox: [],
    hb: null,
    hist: { at: LS.get('histAt', 0), parts: {}, docs: [] },
    view: 'home',
    params: {},
  };

  // v4.0: Admin yuborgan havola (".../#c=IMSAG1:...") — bosilishi bilan ulanadi, kod yozish shart emas.
  // Kod havolaning # qismida: serverga yuborilmaydi.
  (function linkFromHash() {
    var h = location.hash || '';
    if (h.indexOf('#c=') !== 0) return;
    var c = C.decodeCode(decodeURIComponent(h.slice(3)));
    if (c) { S.cfg = c; LS.set('cfg', c); }
    try { history.replaceState(null, '', location.pathname + location.search); } catch (e) { /* */ }
  })();
  function newDraft() { return { storeId: '', level: 4, usd: false, mode: '1', lines: [], discount: '', cash: '', card: '', printer: LS.get('printer', '1') }; }
  if (!S.draft) S.draft = newDraft();
  function saveDraft() { LS.set('draft', S.draft); }

  // ================= yordamchi =================

  function esc(s) { return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]; }); }
  function money(v, usd) { return C.money(v, usd); }
  function hm(ms) { var d = new Date(ms); return C.reportDate(d).slice(0, 5) + ' ' + C.timestamp(d).slice(11, 16); }
  var toastT = 0;
  function toast(text) {
    var old = document.querySelector('.toast'); if (old) old.remove();
    var t = document.createElement('div'); t.className = 'toast'; t.textContent = text; document.body.appendChild(t);
    clearTimeout(toastT); toastT = setTimeout(function () { t.remove(); }, 3500);
  }
  function online() { return navigator.onLine !== false; }
  function sleep(ms) { return new Promise(function (r) { setTimeout(r, ms); }); }
  function errText(e) {
    var m = (e && e.message) || String(e);
    if (m === 'admin_offline') return "Admin javob bermadi: Admin telefoni yoqiq va internetda bo'lishi kerak";
    if (m === 'no_cfg') return "Ulanish kodi kiritilmagan";
    if (/Failed to fetch|NetworkError|Load failed/i.test(m)) return "Internet yo'q yoki ulanib bo'lmadi";
    return m;
  }

  // ================= bulut (Firebase REST, "Admin orqali" kaliti) =================

  function url(sub) {
    if (!S.cfg) throw new Error('no_cfg');
    return S.cfg.dbUrl + '/g/' + S.cfg.key + '/' + sub + '.json';
  }
  function req(method, sub, body, timeoutMs) {
    var ctl = typeof AbortController === 'function' ? new AbortController() : null;
    var t = ctl ? setTimeout(function () { ctl.abort(); }, timeoutMs || 30000) : 0;
    var opt = { method: method, cache: 'no-store', signal: ctl ? ctl.signal : undefined };
    if (body !== undefined) { opt.body = JSON.stringify(body); opt.headers = { 'Content-Type': 'application/json' }; }
    return fetch(url(sub), opt).then(function (r) {
      clearTimeout(t);
      if (r.status === 401 || r.status === 403) { var e = new Error('denied'); e.denied = true; throw e; }
      if (!r.ok) throw new Error('HTTP ' + r.status);
      return r.text().then(function (x) { return x ? JSON.parse(x) : null; });
    }, function (e) { clearTimeout(t); throw e; });
  }
  var SV = { '.sv': 'timestamp' };
  function signal() { return req('PUT', 'sig', SV).catch(function () { /* */ }); }

  /** Admin'ga so'rov va javobni kutish (Android RelayClient.rpc bilan bir xil). */
  async function rpc(op, params, timeoutMs) {
    var rid = C.newKey();
    await req('PUT', 'rq/' + rid, { at: SV, op: op, p: JSON.stringify(params) });
    signal();
    var deadline = Date.now() + timeoutMs;
    var wait = 600;
    while (Date.now() < deadline) {
      await sleep(wait);
      wait = Math.min(wait + 200, 1500);
      var r = null;
      try { r = await req('GET', 'rs/' + rid); } catch (e) { continue; }
      if (!r) continue;
      req('DELETE', 'rs/' + rid).catch(function () { /* */ });
      if (r.ok === true) return r.r;
      throw new Error(r.e === 'rejected' ? 'Rad etildi' : (r.e || 'admin_error'));
    }
    throw new Error('admin_offline');
  }

  // ================= katalog =================

  async function loadLocal() {
    var cat = await idbGet('cat');
    if (cat) S.cat = cat;
    var ob = await idbGet('outbox');
    S.outbox = Array.isArray(ob) ? ob : [];
    var parts = await idbGet('hist');
    if (parts && typeof parts === 'object') { S.hist.parts = parts; rebuildHist(); }
  }

  /** Admin'dan yangi katalog: so'raydi, 25 soniyagacha kutadi; kelmasa — oxirgi joylangani. */
  async function syncCatalog() {
    var asked = Date.now();
    await req('PUT', 'creq', SV);
    signal();
    var at = Number(await req('GET', 'cat/at').catch(function () { return 0; })) || 0;
    var deadline = Date.now() + 25000;
    while (at < asked - 120000 && Date.now() < deadline) {
      await sleep(1500);
      at = Number(await req('GET', 'cat/at').catch(function () { return 0; })) || 0;
    }
    var b = await req('GET', 'cat/b', undefined, 120000);
    if (typeof b !== 'string') throw new Error("Admin hali katalog joylamagan");
    var q = await req('GET', 'cat/q', undefined, 60000).catch(function () { return null; });
    var levels = null;
    if (typeof q === 'string') { try { levels = JSON.parse(await C.unpack(q)); } catch (e) { levels = null; } }
    var cat = C.parseCatalog(await C.unpack(b), levels);
    S.cat = cat;
    S.catAt = Math.max(cat.at || 0, at);
    LS.set('catAt', S.catAt);
    await idbSet('cat', cat);
    if (S.agent) {
      var me = cat.agents.filter(function (a) { return a.id === S.agent.id; })[0];
      if (me) { S.agent = me; LS.set('agent', me); }
    }
    return cat;
  }

  function store(id) { return S.cat ? S.cat.stores.filter(function (s) { return s.id === id; })[0] : null; }
  function product(id) { return S.cat ? S.cat.products.filter(function (p) { return p.id === id; })[0] : null; }
  function rate() { return S.cat ? S.cat.rate : 0; }

  // ================= navbat (savdo va to'lov) =================

  function saveOutbox() { return idbSet('outbox', S.outbox.slice(0, 400)); }

  async function enqueue(type, payload, meta) {
    var id = await C.oid(S.agent.id, type, payload);
    var item = { oid: id, u: S.agent.id, t: type, p: payload, m: meta.mirror || null, at: Date.now(), st: 'wait', pushed: false,
      code: meta.code || nextCode(type), store: meta.store, storeName: meta.storeName, sum: meta.sum, usd: !!meta.usd, mode: meta.mode || '' };
    S.outbox.unshift(item);
    await saveOutbox();
    pump();
    return item;
  }

  /** Kvitansiya kodi: I-0001 (savdo), IT-0001 (to'lov). */
  function nextCode(type) {
    var n = (LS.get('seq', 0) || 0) + 1;
    LS.set('seq', n);
    return (type === '1' ? 'I-' : 'IT-') + ('000' + n).slice(-4);
  }

  var pumping = false;
  /** Navbatdagilarni Admin'ga joylaydi va natijani tekshiradi. Takror joylansa ham bitta yozuv (kalit — oid). */
  async function pump() {
    if (pumping || !S.cfg || !online()) return;
    pumping = true;
    var changed = false;
    try {
      var any = false;
      for (var i = S.outbox.length - 1; i >= 0; i--) {
        var it = S.outbox[i];
        if (it.st !== 'wait') continue;
        if (!it.pushed) {
          var body = { at: SV, u: it.u, t: it.t, p: it.p };
          if (it.m) body.m = it.m;
          try { await req('PUT', 'ob/' + it.oid, body); it.pushed = true; changed = true; any = true; } catch (e) {
            if (e.denied) { it.pushed = true; changed = true; } else break;
          }
        }
        var r = await req('GET', 'obr/' + it.oid).catch(function () { return null; });
        if (r && r.st === 'ok') { it.st = 'ok'; it.sid = r.id; changed = true; }
        else if (r && r.st === 'rej') { it.st = 'rej'; it.msg = r.m || ''; changed = true; }
      }
      if (any) signal();
    } finally {
      pumping = false;
      if (changed) { await saveOutbox(); if (S.view === 'home' || S.view === 'sent' || S.view === 'done') render(); }
    }
  }

  async function heartbeat() {
    if (!S.cfg || !online()) return;
    try { S.hb = await req('GET', 'hb'); } catch (e) { /* */ }
  }

  // ================= 1C tarixi (Admin yuboradi: g/{kalit}/hist) =================

  function rebuildHist() {
    var all = [];
    Object.keys(S.hist.parts).forEach(function (k) {
      var p = S.hist.parts[k];
      if (p && Array.isArray(p.e)) all = all.concat(p.e);
    });
    S.hist.docs = C.docs(all.map(C.entry));
  }

  var histBusy = false;
  async function syncHist() {
    if (histBusy || !S.cfg || !online()) return;
    histBusy = true;
    try {
      var meta = await req('GET', 'hist/meta');
      if (!meta || !meta.b) return;
      var keys = Object.keys(meta.b).sort().reverse(); // eng yangisidan
      var changed = false;
      for (var i = 0; i < keys.length; i++) {
        var k = keys[i];
        var have = S.hist.parts[k];
        if (have && have.h === meta.b[k]) continue;
        var blob = await req('GET', 'hist/m/' + k, undefined, 120000);
        if (typeof blob !== 'string') continue;
        var list = JSON.parse(await C.unpack(blob));
        S.hist.parts[k] = { h: meta.b[k], e: list };
        changed = true;
        if (i < 3) { rebuildHist(); if (S.view === 'ledger') renderList(); }
      }
      Object.keys(S.hist.parts).forEach(function (k) { if (!meta.b[k]) { delete S.hist.parts[k]; changed = true; } });
      S.hist.at = Number(meta.at) || Date.now();
      LS.set('histAt', S.hist.at);
      if (changed) { rebuildHist(); await idbSet('hist', S.hist.parts); }
      if (S.view === 'ledger') render();
    } catch (e) {
      if (S.view === 'ledger') toast(errText(e));
    } finally { histBusy = false; }
  }

  // ================= narx va savatcha =================

  function priceOf(p, d) { return C.unitPrice(p, d.level, d.usd, rate()); }
  function lineOf(id) { return S.draft.lines.filter(function (l) { return l.productId === id; })[0]; }
  function total() { return C.round2(S.draft.lines.reduce(function (s, l) { return s + l.qty * l.price; }, 0)); }
  function repriceAll() {
    S.draft.lines.forEach(function (l) {
      if (l.manual) return;
      var p = product(l.productId);
      if (p) l.price = priceOf(p, S.draft);
    });
  }
  function addQty(id, delta) {
    var p = product(id); if (!p) return;
    var l = lineOf(id);
    if (!l) {
      if (delta <= 0) return;
      l = { productId: id, name: p.name, qty: 0, price: priceOf(p, S.draft), manual: false };
      S.draft.lines.push(l);
    }
    l.qty = C.roundN(l.qty + delta, 3);
    if (l.qty <= 0) S.draft.lines = S.draft.lines.filter(function (x) { return x !== l; });
    saveDraft();
  }

  // ================= ekranlar =================

  function go(view, params) {
    S.view = view; S.params = params || {};
    if (view !== 'sale') listLimit = 150;
    render();
    window.scrollTo(0, 0);
  }

  function header(title, back) {
    return '<header class="bar">' + (back ? '<button class="icon-btn" data-act="back" aria-label="Orqaga">&#8592;</button>' : '') +
      '<h1>' + esc(title) + '</h1>' + (back ? '' : '<button class="icon-btn" data-act="settings" aria-label="Sozlamalar">&#9881;</button>') + '</header>';
  }

  function offlineBanner() {
    if (online()) return '';
    return '<div class="banner">Internet yo\'q — ' + (S.catAt ? hm(S.catAt) : '—') + ' holatidagi ma\'lumot bilan ishlayapsiz. ' +
      'Savdo va to\'lovlar navbatda turadi, internet kelganda o\'zi yuboriladi.</div>';
  }

  function render() {
    var v = S.view;
    if (!S.cfg) v = 'setup';
    else if (!S.agent) v = 'login';
    var html;
    switch (v) {
      case 'setup': html = vSetup(); break;
      case 'login': html = vLogin(); break;
      case 'sale': html = vSale(); break;
      case 'cart': html = vCart(); break;
      case 'done': html = vDone(); break;
      case 'pay': html = vPay(); break;
      case 'sent': html = vSent(); break;
      case 'ledger': html = vLedger(); break;
      case 'report': html = vReport(); break;
      case 'settings': html = vSettings(); break;
      default: html = vHome();
    }
    $app.innerHTML = html;
    if (v === 'sale' || v === 'ledger') renderList();
    var f = $app.querySelector('[autofocus]');
    if (f && v !== 'sale') f.focus();
  }

  function vSetup() {
    return header('Savdo Agent') + '<main>' +
      '<div class="card"><div class="label">Ulanish</div>' +
      '<p>Admin ilovasidan <b>agent ulanish kodini</b> oling (Admin ▸ Boshqaruv ▸ Agentlar ko\'prigi ▸ "Kodni yuborish") va shu yerga joylang. Kod <b>IMSAG1:</b> bilan boshlanadi.</p>' +
      '<textarea id="code" placeholder="IMSAG1:..." autocapitalize="off" autocomplete="off" spellcheck="false"></textarea>' +
      '<button class="btn" data-act="setup">Ulash</button><div id="msg" class="err"></div></div>' +
      installHint() + '</main>';
  }

  function installHint() {
    var standalone = window.navigator.standalone || (window.matchMedia && window.matchMedia('(display-mode: standalone)').matches);
    if (standalone) return '';
    return '<div class="card outlined"><div class="label">Telefonga o\'rnatish</div>' +
      '<p class="muted">iPhone: Safari\'da pastdagi <b>Ulashish</b> (kvadrat va strelka) ▸ <b>"Bosh ekranga qo\'shish"</b>. Shunda ilova alohida belgi bo\'lib ochiladi va internetsiz ham ishlaydi.</p></div>';
  }

  function vLogin() {
    return header('Kirish') + '<main>' + offlineBanner() +
      '<div class="card"><p class="muted">Agent telefon raqami va paroli (Android agentdagi bilan bir xil). Parol hech qayerga yozilmaydi — Admin tekshiradi.</p>' +
      '<input id="phone" type="tel" placeholder="Telefon" autocomplete="username" value="' + esc(LS.get('phone', '')) + '">' +
      '<input id="pass" type="password" placeholder="Parol" autocomplete="current-password">' +
      '<button class="btn" data-act="login">Kirish</button><div id="msg" class="err"></div></div>' +
      '<button class="btn outline" data-act="reset-cfg">Ulanish kodini almashtirish</button></main>';
  }

  function todaySummary() {
    var d0 = new Date(); d0.setHours(0, 0, 0, 0);
    var o = 0, os = 0, ou = 0, p = 0, ps = 0, wait = 0;
    S.outbox.forEach(function (it) {
      if (it.st === 'wait') wait++;
      if (it.at < d0.getTime() || it.st === 'rej') return;
      if (it.t === '1') { o++; if (it.usd) ou += it.sum; else os += (it.mode === '2' ? -it.sum : it.sum); } else { p++; ps += it.sum; }
    });
    return { o: o, os: os, ou: ou, p: p, ps: ps, wait: wait };
  }

  function vHome() {
    var t = todaySummary();
    var hb = S.hb && S.hb.at ? 'Admin: ' + hm(S.hb.at) + (S.hb.q ? ' · navbatda ' + S.hb.q : '') + (S.hb.e ? ' · ' + esc(S.hb.e) : '') : 'Admin holati noma\'lum';
    return header('Savdo Agent') + '<main>' + offlineBanner() +
      '<div class="card"><div class="big" style="font-size:22px">Salom, ' + esc(S.agent.name || 'Agent') + '</div>' +
      '<div class="muted">' + esc(S.agent.phone || '') + '</div>' +
      '<div style="margin-top:8px;color:var(--primary);font-size:18px">1 $ = ' + (rate() ? C.display(rate(), 2) : '—') + " so'm</div>" +
      '<div class="muted">Katalog: ' + (S.catAt ? hm(S.catAt) : 'yuklanmagan') + ' · ' + hb + '</div>' +
      '<button class="btn small tonal" data-act="sync-cat">Katalogni yangilash</button></div>' +
      (S.cat ? '' : '<div class="banner">Katalog hali yuklanmagan. "Katalogni yangilash" bosing (Admin internetda bo\'lishi kerak).</div>') +
      '<button class="btn" data-act="new-sale" style="min-height:60px">&#128722; Yangi savdo</button>' +
      '<div class="tiles">' +
      '<button class="tile" data-act="new-return"><span class="i">&#8617;</span>Qaytarib olish</button>' +
      '<button class="tile" data-act="pay"><span class="i">&#128179;</span>To\'lov</button>' +
      '<button class="tile" data-act="ledger"><span class="i">&#128338;</span>1C tarixi</button>' +
      '<button class="tile" data-act="report"><span class="i">&#128202;</span>Hisobot</button></div>' +
      '<div class="card"><div class="label">Bugun</div>' +
      '<div>Savdolar: ' + t.o + ' ta · ' + money(t.os, false) + (t.ou ? ' + ' + money(t.ou, true) : '') + '</div>' +
      '<div>To\'lovlar: ' + t.p + ' ta · ' + money(t.ps, false) + '</div>' +
      (t.wait ? '<div class="muted" style="color:var(--warn)">Navbatda (yuborilmagan): ' + t.wait + ' ta</div>' : '') +
      '<button class="btn small outline" data-act="sent">Yuborilganlar</button></div>' +
      (S.draft.lines.length ? '<button class="btn tonal" data-act="cart">Davom etish: savatchada ' + S.draft.lines.length + ' xil tovar · ' + money(total(), S.draft.usd) + '</button>' : '') +
      installHint() + '</main>';
  }

  // ---------- savdo ----------

  var listLimit = 150;
  function seg(name, items, cur) {
    return '<div class="seg">' + items.map(function (it) {
      return '<button data-act="seg" data-k="' + name + '" data-v="' + esc(it[0]) + '" class="' + (String(cur) === String(it[0]) ? 'on' : '') + '">' + esc(it[1]) + '</button>';
    }).join('') + '</div>';
  }

  function vSale() {
    var d = S.draft;
    var st = store(d.storeId);
    return '<header class="bar"><button class="icon-btn" data-act="back">&#8592;</button>' +
      '<div class="store" data-act="pick-store" style="flex:1;min-width:0"><div style="min-width:0"><div class="t" style="white-space:nowrap;overflow:hidden;text-overflow:ellipsis">' +
      esc(st ? st.name : 'Do\'konni tanlang') + ' &#9662;</div><div class="muted">' + (st ? debtText(st) : 'Bosing — do\'kon tanlash') + '</div></div></div>' +
      (d.lines.length ? '<button class="icon-btn" data-act="clear" aria-label="Tozalash">&#128465;</button>' : '') + '</header>' +
      '<main><div class="sticky">' + offlineBanner() +
      seg('level', [[1, '1'], [2, '2'], [3, 'Naqd'], [4, 'Optom']], d.level) +
      '<div class="row2">' + seg('usd', [['0', "So'm"], ['1', '$']], d.usd ? '1' : '0') + seg('mode', [['1', 'Savdo'], ['2', 'Qaytarish']], d.mode) + '</div>' +
      '<input id="q" type="search" placeholder="Qidirish: tovar nomi yoki kodi" value="' + esc(S.params.q || '') + '" autocomplete="off">' +
      '</div><div id="list" class="list"></div></main>' +
      '<div class="bottom"><div class="sum"><div class="muted">' + d.lines.length + ' xil tovar · ' + (d.mode === '2' ? 'QAYTARISH' : 'savdo') + '</div>' +
      '<div class="big">' + money(total(), d.usd) + '</div></div>' +
      '<button class="btn" data-act="cart"' + (d.lines.length ? '' : ' disabled') + '>Savatcha</button></div>';
  }

  function debtText(st) {
    var parts = [];
    if (st.debtSom) parts.push(money(st.debtSom, false));
    if (st.debtUsd) parts.push(money(st.debtUsd, true));
    return 'Qarzi: ' + (parts.join(' + ') || "0 so'm") + (st.region ? ' · ' + esc(st.region) : '');
  }

  var searchKeys = null;
  function productKeys() {
    if (!S.cat) return [];
    if (!searchKeys || searchKeys.cat !== S.cat) {
      searchKeys = { cat: S.cat, keys: S.cat.products.map(function (p) { return C.fold(p.name + ' ' + p.id); }) };
    }
    return searchKeys.keys;
  }

  function renderList() {
    var box = document.getElementById('list');
    if (!box) return;
    if (S.view === 'ledger') { box.innerHTML = ledgerList(); return; }
    if (!S.cat) { box.innerHTML = '<p class="muted">Katalog yuklanmagan — bosh sahifada "Katalogni yangilash".</p>'; return; }
    var q = S.params.q || '';
    var keys = productKeys();
    var shown = [];
    var count = 0;
    for (var i = 0; i < S.cat.products.length; i++) {
      if (q && !C.matches(keys[i], q)) continue;
      count++;
      if (shown.length < listLimit) shown.push(S.cat.products[i]);
    }
    var d = S.draft;
    var html = shown.map(function (p) {
      var l = lineOf(p.id);
      var pr = l ? l.price : priceOf(p, d);
      return '<div class="item' + (l ? ' in' : '') + '"><div class="n" data-act="edit" data-id="' + esc(p.id) + '"><div class="t">' + esc(p.name) + '</div>' +
        '<div><span class="price">' + (pr > 0 ? money(pr, d.usd) : "narx yo'q") + '</span> <span class="muted">' + esc(p.id) + (p.blockSize > 1 ? ' · blok ' + C.display(p.blockSize, 3) : '') + '</span></div></div>' +
        '<div class="step">' + (l ? '<button data-act="minus" data-id="' + esc(p.id) + '">&minus;</button><div class="q" data-act="edit" data-id="' + esc(p.id) + '">' + C.display(l.qty, 3) + '</div>' : '') +
        '<button class="plus" data-act="plus" data-id="' + esc(p.id) + '">+</button></div></div>';
    }).join('');
    if (count > shown.length) html += '<button class="btn outline" data-act="more">Yana ' + (count - shown.length) + ' ta ko\'rsatish</button>';
    if (!count) html = '<p class="muted">Hech narsa topilmadi</p>';
    box.innerHTML = html;
  }

  function refreshSaleBottom() {
    var b = document.querySelector('.bottom');
    if (!b) return;
    var d = S.draft;
    b.querySelector('.sum').innerHTML = '<div class="muted">' + d.lines.length + ' xil tovar · ' + (d.mode === '2' ? 'QAYTARISH' : 'savdo') + '</div><div class="big">' + money(total(), d.usd) + '</div>';
    b.querySelector('[data-act="cart"]').disabled = !d.lines.length;
  }

  // ---------- oynalar ----------

  function sheet(html, onClick) {
    var o = document.createElement('div');
    o.className = 'overlay';
    o.innerHTML = '<div class="sheet">' + html + '</div>';
    o.addEventListener('click', function (e) {
      if (e.target === o) { o.remove(); return; }
      var t = e.target.closest('[data-x]');
      if (t) onClick(t, o);
    });
    document.body.appendChild(o);
    var f = o.querySelector('[autofocus]'); if (f) setTimeout(function () { f.focus(); if (f.select) f.select(); }, 50);
    return o;
  }

  function pickStore(after) {
    if (!S.cat) { toast('Katalog yuklanmagan'); return; }
    var o = sheet('<h2>Do\'kon</h2><input id="sq" type="search" placeholder="Do\'kon yoki hudud" autofocus><div class="scroll list" id="sl"></div>' +
      '<button class="btn outline" data-x="close">Yopish</button>', function (t, ov) {
      if (t.dataset.x === 'close') { ov.remove(); return; }
      if (t.dataset.x === 'store') { ov.remove(); after(t.dataset.id); }
    });
    var fill = function () {
      var q = o.querySelector('#sq').value;
      var list = S.cat.stores.filter(function (s) { return !q || C.matches(C.fold(s.name + ' ' + s.region), q); }).slice(0, 200);
      o.querySelector('#sl').innerHTML = list.map(function (s) {
        return '<div class="item" data-x="store" data-id="' + esc(s.id) + '"><div class="n"><div class="t">' + esc(s.name) + '</div><div class="muted">' + debtText(s) + '</div></div></div>';
      }).join('') || '<p class="muted">Topilmadi</p>';
    };
    o.querySelector('#sq').addEventListener('input', fill);
    fill();
  }

  function editLine(id) {
    var p = product(id); if (!p) return;
    var l = lineOf(id);
    var d = S.draft;
    var pr = l ? l.price : priceOf(p, d);
    var html = '<h2>' + esc(p.name) + '</h2>' +
      '<label class="muted">Miqdor' + (p.blockSize > 1 ? ' (blokda ' + C.display(p.blockSize, 3) + ')' : '') + '</label>' +
      '<input id="eq" inputmode="decimal" value="' + (l ? C.display(l.qty, 3).replace(/ /g, '') : '1') + '" autofocus>' +
      (p.blockSize > 1 ? '<button class="btn small tonal" data-x="block">+1 blok</button>' : '') +
      '<label class="muted">Narx (' + (d.usd ? '$' : "so'm") + ')</label><input id="ep" inputmode="decimal" value="' + (pr > 0 ? C.plain(pr) : '') + '">' +
      '<div class="row2"><button class="btn outline" data-x="del">O\'chirish</button><button class="btn" data-x="ok">Saqlash</button></div>';
    sheet(html, function (t, ov) {
      var x = t.dataset.x;
      if (x === 'block') { var e = ov.querySelector('#eq'); e.value = String(C.roundN((C.parseUser(e.value) || 0) + p.blockSize, 3)); return; }
      if (x === 'del') { S.draft.lines = S.draft.lines.filter(function (z) { return z.productId !== id; }); saveDraft(); ov.remove(); afterLineChange(); return; }
      if (x === 'ok') {
        var q = C.parseUser(ov.querySelector('#eq').value);
        var np = C.parseUser(ov.querySelector('#ep').value);
        if (q == null || np == null || q < 0 || np < 0) { toast("Son noto'g'ri"); return; }
        var line = lineOf(id);
        if (q === 0) { S.draft.lines = S.draft.lines.filter(function (z) { return z.productId !== id; }); }
        else if (line) { line.qty = q; if (C.round2(np) !== line.price) { line.price = C.round2(np); line.manual = true; } }
        else S.draft.lines.push({ productId: id, name: p.name, qty: q, price: C.round2(np), manual: C.round2(np) !== priceOf(p, d) });
        saveDraft(); ov.remove(); afterLineChange();
      }
    });
  }
  function afterLineChange() { if (S.view === 'sale') { renderList(); refreshSaleBottom(); } else render(); }

  // ---------- savatcha va yuborish ----------

  function vCart() {
    var d = S.draft;
    var st = store(d.storeId);
    var tot = total();
    var disc = C.parseUser(d.discount) || 0;
    var payable = C.round2(Math.max(tot - Math.min(Math.max(disc, 0), Math.max(tot, 0)), 0));
    var cash = C.parseUser(d.cash) || 0, card = C.parseUser(d.card) || 0;
    var paid = d.usd ? cash : cash + card;
    var rows = d.lines.map(function (l, i) {
      return '<div class="item" data-act="edit" data-id="' + esc(l.productId) + '"><div class="n"><div class="t">' + (i + 1) + ') ' + esc(l.name) + '</div>' +
        '<div class="muted">' + C.display(l.qty, 3) + ' × ' + money(l.price, d.usd) + (l.manual ? ' · narx o\'zgartirilgan' : '') + '</div></div><div class="price">' + money(C.round2(l.qty * l.price), d.usd) + '</div></div>';
    }).join('');
    return header(d.mode === '2' ? 'Qaytarish' : 'Savatcha', true) + '<main>' + offlineBanner() +
      '<div class="card"><div class="store" data-act="pick-store"><div><div class="t">' + esc(st ? st.name : 'Do\'konni tanlang') + ' &#9662;</div><div class="muted">' + (st ? debtText(st) : '') + '</div></div></div>' +
      (st ? '<button class="btn small outline" data-act="debt">Qarzni yangilash</button>' : '') + '</div>' +
      '<div class="list">' + (rows || '<p class="muted">Savatcha bo\'sh</p>') + '</div>' +
      '<div class="card"><div class="tot"><span>Jami</span><span>' + money(tot, d.usd) + '</span></div>' +
      '<label class="muted">Chegirma (' + (d.usd ? '$' : "so'm") + ')</label><input id="disc" inputmode="decimal" value="' + esc(d.discount) + '">' +
      '<div class="tot b"><span>To\'lanadi</span><span id="payable">' + money(payable, d.usd) + '</span></div></div>' +
      (d.mode === '2' ? '' : '<div class="card"><div class="label">To\'lov</div>' +
        '<label class="muted">Naqd (' + (d.usd ? '$' : "so'm") + ')</label><input id="cash" inputmode="decimal" value="' + esc(d.cash) + '">' +
        (d.usd ? '' : '<label class="muted">Plastik karta</label><input id="card" inputmode="decimal" value="' + esc(d.card) + '">') +
        '<button class="btn small tonal" data-act="pay-all">Hammasini naqd</button>' +
        '<div class="muted" id="rest">' + restText(payable, paid, d.usd) + '</div></div>') +
      '<div class="card"><div class="label">Chek</div>' + seg('printer', [['1', '1-printer'], ['2', '2-printer'], ['3', 'Cheksiz']], d.printer) + '</div>' +
      '<button class="btn" data-act="send"' + (d.lines.length && st ? '' : ' disabled') + '>' + (d.mode === '2' ? 'Qaytarishni yuborish' : 'Yuborish') + '</button>' +
      '<p class="muted">Internet yo\'q bo\'lsa savdo navbatda turadi va internet kelganda o\'zi yuboriladi. Admin telefoni serverga yozadi.</p></main>';
  }
  function restText(payable, paid, usd) {
    var r = C.round2(payable - paid);
    if (r > 0.004) return 'Qarzga qoladi: ' + money(r, usd);
    if (r < -0.004) return 'Ortiqcha: ' + money(-r, usd);
    return "To'liq to'landi";
  }

  async function sendOrder() {
    var d = S.draft;
    var st = store(d.storeId);
    if (!st) { toast("Do'konni tanlang"); return; }
    if (!d.lines.length) return;
    var disc = C.parseUser(d.discount), cash = C.parseUser(d.cash), card = C.parseUser(d.card);
    if (disc == null || cash == null || card == null) { toast("Summa noto'g'ri yozilgan"); return; }
    if (d.lines.some(function (l) { return !(l.price > 0); }) && !confirm("Ba'zi tovarlarning narxi 0. Shunday yuborilsinmi?")) return;
    var tot = total();
    disc = Math.min(Math.max(disc, 0), Math.max(tot, 0));
    if (d.mode === '2') { cash = 0; card = 0; }
    var now = new Date();
    var currency = d.usd ? '1' : '0';
    var pt = C.priceTypeCode(d.level);
    var payload = C.order({
      agentId: S.agent.id, storeId: st.id, time: now, currency: currency, priceType: pt,
      cashSom: d.usd ? 0 : cash, cashUsd: d.usd ? cash : 0, card: d.usd ? 0 : card, discount: disc,
      rate: S.cat.rateText, mode: d.mode, printer: d.printer, lines: d.lines,
    });
    var paid = d.usd ? cash : cash + card;
    var code = nextCode('1');
    var mirror = C.saleJson({ at: now.getTime(), storeId: st.id, storeName: st.name, mode: d.mode, priceType: pt, currency: currency, total: tot, discount: disc, paid: paid, code: code, lines: d.lines });
    var item = await enqueue('1', payload, { mirror: mirror, code: code, store: st.id, storeName: st.name, sum: C.round2(tot - disc), usd: d.usd, mode: d.mode });
    LS.set('printer', d.printer);
    var keep = { storeId: d.storeId, level: d.level, usd: d.usd };
    S.draft = newDraft(); S.draft.level = keep.level; S.draft.usd = keep.usd;
    saveDraft();
    go('done', { oid: item.oid });
  }

  function statusChip(it) {
    if (it.st === 'ok') return '<span class="chip ok">Yuborildi' + (it.sid ? ' · №' + esc(it.sid) : '') + '</span>';
    if (it.st === 'rej') return '<span class="chip err">Rad etildi: ' + esc(it.msg) + '</span>';
    return '<span class="chip">' + (it.pushed ? "Admin'da, serverga yozilmoqda" : 'Navbatda') + '</span>';
  }

  function vDone() {
    var it = S.outbox.filter(function (x) { return x.oid === S.params.oid; })[0];
    if (!it) return vHome();
    return header(it.t === '1' ? (it.mode === '2' ? 'Qaytarish yuborildi' : 'Savdo yuborildi') : "To'lov yuborildi", true) + '<main>' +
      '<div class="card primary"><div class="label">Kvitansiya ' + esc(it.code) + '</div><div class="big">' + money(it.sum, it.usd) + '</div>' +
      '<div>' + esc(it.storeName) + '</div><div style="margin-top:8px">' + statusChip(it) + '</div></div>' +
      '<p class="muted">"Yuborildi" — Admin serverga yozib, server raqamini qaytargach. Internet yoki Admin bo\'lmasa navbatda kutadi va o\'zi yuboriladi (takrorlanmaydi).</p>' +
      '<button class="btn" data-act="new-sale">Yangi savdo</button><button class="btn tonal" data-act="home">Bosh sahifa</button></main>';
  }

  // ---------- to'lov ----------

  function vPay() {
    var p = S.params;
    var st = store(p.storeId || S.draft.storeId);
    return header("To'lov qabul qilish", true) + '<main>' + offlineBanner() +
      '<div class="card"><div class="store" data-act="pay-store"><div><div class="t">' + esc(st ? st.name : 'Do\'konni tanlang') + ' &#9662;</div><div class="muted">' + (st ? debtText(st) : '') + '</div></div></div>' +
      (st ? '<button class="btn small outline" data-act="debt" data-store="' + esc(st.id) + '">Qarzni yangilash</button>' : '') + '</div>' +
      '<div class="card"><label class="muted">Naqd (so\'m)</label><input id="pcash" inputmode="decimal" value="' + esc(p.cash || '') + '">' +
      '<label class="muted">O\'tkazma (so\'m)</label><input id="ptr" inputmode="decimal" value="' + esc(p.tr || '') + '">' +
      '<label class="muted">Click (so\'m)</label><input id="pclick" inputmode="decimal" value="' + esc(p.click || '') + '">' +
      '<label class="muted">Izoh</label><input id="pnote" value="' + esc(p.note || '') + '"></div>' +
      '<div class="card"><div class="label">Chek</div>' + seg('pprinter', [['1', '1-printer'], ['2', '2-printer'], ['3', 'Cheksiz']], p.printer || LS.get('printer', '1')) + '</div>' +
      '<button class="btn" data-act="send-pay"' + (st ? '' : ' disabled') + '>To\'lovni yuborish</button></main>';
  }
  function readPayForm() {
    var g = function (id) { var e = document.getElementById(id); return e ? e.value : ''; };
    S.params.cash = g('pcash'); S.params.tr = g('ptr'); S.params.click = g('pclick'); S.params.note = g('pnote');
  }
  async function sendPay() {
    readPayForm();
    var p = S.params;
    var st = store(p.storeId || S.draft.storeId);
    if (!st) { toast("Do'konni tanlang"); return; }
    var cash = C.parseUser(p.cash), tr = C.parseUser(p.tr), click = C.parseUser(p.click);
    if (cash == null || tr == null || click == null || cash < 0 || tr < 0 || click < 0) { toast("Summa noto'g'ri"); return; }
    if (cash + tr + click <= 0) { toast('Summani yozing'); return; }
    var printer = p.printer || LS.get('printer', '1');
    var payload = C.payment({ agentId: S.agent.id, storeId: st.id, time: new Date(), rate: S.cat.rateText, note: p.note, cash: cash, transfer: tr, click: click, printer: printer });
    var item = await enqueue('4', payload, { store: st.id, storeName: st.name, sum: C.round2(cash + tr + click), usd: false });
    go('done', { oid: item.oid });
  }

  async function refreshDebt(storeId) {
    var st = store(storeId);
    if (!st) return;
    toast('Qarz so\'ralmoqda…');
    try {
      var r = await rpc('debt', { a: S.agent.id, st: st.id }, 45000);
      st.debtSom = C.parseNum(r && r.s); st.debtUsd = C.parseNum(r && r.u);
      await idbSet('cat', S.cat);
      toast('Qarz: ' + debtText(st).replace(/^Qarzi: /, ''));
      render();
    } catch (e) { toast(errText(e)); }
  }

  // ---------- yuborilganlar ----------

  function vSent() {
    var rows = S.outbox.slice(0, 200).map(function (it) {
      return '<div class="doc"><div class="k' + (it.t === '4' ? ' pay' : it.mode === '2' ? ' ret' : '') + '">' + (it.t === '4' ? '&#128179;' : it.mode === '2' ? '&#8617;' : '&#128722;') + '</div>' +
        '<div class="m"><div><b>' + esc(it.code) + '</b> · ' + esc(it.storeName) + '</div><div class="muted">' + hm(it.at) + '</div><div>' + statusChip(it) + '</div></div>' +
        '<div class="a">' + money(it.sum, it.usd) + '</div></div>';
    }).join('');
    return header('Yuborilganlar', true) + '<main>' + offlineBanner() +
      '<button class="btn small tonal" data-act="pump">Hozir yuborish / tekshirish</button>' + (rows || '<p class="muted">Hali hech narsa yuborilmagan</p>') + '</main>';
  }

  // ---------- 1C tarixi ----------

  function vLedger() {
    var n = S.hist.docs.length;
    return header('1C tarixi', true) + '<main><div class="sticky">' + offlineBanner() +
      '<div class="muted" data-act="hist-sync" style="padding:6px 0">&#8635; ' + (S.hist.at ? "Admin'dan: " + hm(S.hist.at) + ' · ' + n + ' ta hujjat. Bosing — yangilash' : "Admin hali yubormagan yoki yuklanmoqda. Bosing — yangilash") +
      (histBusy ? ' <span class="sp"></span>' : '') + '</div>' +
      '<input id="hq" type="search" placeholder="Qidirish: mijoz, tovar, sana (01.11)" value="' + esc(S.params.hq || '') + '"></div>' +
      '<div id="list"></div></main>';
  }
  var KT = { sale: 'Savdo', return: 'Vozvrat', payment: "To'lov", paymentReturn: "To'lov qaytarildi", discount: 'Chegirma', other: 'Boshqa' };
  function ledgerList() {
    var q = (S.params.hq || '').trim();
    var dm = /^(\d{1,2})\.(\d{1,2})(?:\.(\d{4}))?$/.exec(q);
    var dateQ = dm ? ('0' + dm[1]).slice(-2) + '.' + ('0' + dm[2]).slice(-2) + (dm[3] ? '.' + dm[3] : '') : '';
    var shown = [];
    for (var i = 0; i < S.hist.docs.length && shown.length < listLimit; i++) {
      var d = S.hist.docs[i];
      if (dateQ) { if (C.stampDate(d.stamp).indexOf(dateQ) !== 0) continue; }
      else if (q && !C.matches(d.key, q)) continue;
      shown.push(d);
    }
    if (!shown.length) return '<p class="muted">' + (S.hist.docs.length ? 'Topilmadi' : "Hali ma'lumot yo'q") + '</p>';
    var html = '', day = '';
    shown.forEach(function (d) {
      var dd = C.stampDate(d.stamp);
      if (dd !== day) { day = dd; html += '<div class="day">' + dd + '</div>'; }
      var goods = d.lines.filter(function (l) { return l.product && (d.kind === 'sale' || d.kind === 'return'); });
      var sub = goods.length ? goods.map(function (l) { return l.product; }).join(', ') : (d.lines.map(function (l) { return l.product; }).filter(Boolean).join('; ') || d.text);
      var cls = d.kind === 'payment' ? ' pay' : d.kind === 'return' ? ' ret' : '';
      html += '<div class="doc" data-act="hdoc" data-i="' + S.hist.docs.indexOf(d) + '"><div class="k' + cls + '">' + (d.kind === 'payment' ? '&#128179;' : d.kind === 'return' ? '&#8617;' : '&#128722;') + '</div>' +
        '<div class="m"><div><b>' + KT[d.kind] + '</b>' + (goods.length ? ' · ' + goods.length + ' xil' : '') + '</div>' +
        '<div class="muted">' + C.stampTime(d.stamp) + (d.owner ? ' · ' + esc(d.owner.name) : '') + ' · ' + esc(sub).slice(0, 140) + '</div></div>' +
        '<div class="a">' + (d.som ? money(d.som, false) : '') + (d.usd ? ' ' + money(d.usd, true) : '') + '</div></div>';
    });
    if (shown.length >= listLimit) html += '<button class="btn outline" data-act="more">Ko\'proq ko\'rsatish</button>';
    return html;
  }
  function showDoc(i) {
    var d = S.hist.docs[i]; if (!d) return;
    var rows = d.lines.map(function (l) {
      return '<div class="tot"><span>' + esc(l.product || l.text) + (l.qty ? ' — ' + C.display(l.qty, 3) + (l.price.som ? ' × ' + C.display(l.price.som) : '') : '') + '</span><span>' +
        (l.amount.som ? C.display(l.amount.som) : '') + (l.amount.usd ? ' ' + C.display(l.amount.usd) + ' $' : '') + '</span></div>';
    }).join('');
    sheet('<h2>' + KT[d.kind] + ' · ' + C.stampDate(d.stamp) + ' ' + C.stampTime(d.stamp) + '</h2><div class="muted">' + esc(d.owner ? d.owner.name : '') + ' · ' + esc(d.text) + '</div>' +
      '<div class="scroll">' + rows + '<div class="tot b"><span>Jami</span><span>' + (d.som ? money(d.som, false) : '') + (d.usd ? ' ' + money(d.usd, true) : '') + '</span></div></div>' +
      '<button class="btn outline" data-x="close">Yopish</button>', function (t, o) { if (t.dataset.x === 'close') o.remove(); });
  }

  // ---------- hisobot (server, Admin orqali) ----------

  function vReport() {
    var p = S.params;
    var st = store(p.storeId || S.draft.storeId);
    var today = new Date();
    var iso = function (d) { return d.getFullYear() + '-' + ('0' + (d.getMonth() + 1)).slice(-2) + '-' + ('0' + d.getDate()).slice(-2); };
    var from = p.from || iso(new Date(today.getFullYear(), today.getMonth(), 1));
    var to = p.to || iso(today);
    return header('Hisobot', true) + '<main>' + offlineBanner() +
      '<div class="card"><div class="store" data-act="rep-store"><div><div class="t">' + esc(st ? st.name : 'Do\'konni tanlang') + ' &#9662;</div><div class="muted">' + (st ? debtText(st) : '') + '</div></div></div>' +
      '<div class="row2"><input id="rfrom" type="date" value="' + from + '"><input id="rto" type="date" value="' + to + '"></div>' +
      '<button class="btn" data-act="run-report"' + (st ? '' : ' disabled') + '>Hisobotni olish</button><div class="muted">Admin serverdan olib beradi (1–2 daqiqagacha).</div></div>' +
      (p.html ? '<iframe class="rep" sandbox="" srcdoc="' + esc(p.html) + '"></iframe>' : '') + '</main>';
  }
  async function runReport() {
    var p = S.params;
    p.from = document.getElementById('rfrom').value; p.to = document.getElementById('rto').value;
    var st = store(p.storeId || S.draft.storeId); if (!st) return;
    var parse = function (s) { var a = s.split('-'); return new Date(Number(a[0]), Number(a[1]) - 1, Number(a[2])); };
    var btn = document.querySelector('[data-act="run-report"]'); if (btn) { btn.disabled = true; btn.innerHTML = '<span class="sp"></span> Kutilmoqda…'; }
    try {
      p.html = await rpc('report', { a: S.agent.id, p: C.report(parse(p.from), parse(p.to), st.id) }, 100000);
      p.storeId = st.id;
    } catch (e) { toast(errText(e)); }
    if (S.view === 'report') render();
  }

  // ---------- sozlamalar ----------

  function vSettings() {
    var wait = S.outbox.filter(function (x) { return x.st === 'wait'; }).length;
    return header('Sozlamalar', true) + '<main>' +
      '<div class="card"><div class="label">Agent</div><div>' + esc(S.agent ? S.agent.name : '') + '</div><div class="muted">' + esc(S.agent ? S.agent.phone : '') + '</div></div>' +
      '<div class="card"><div class="label">Ulanish</div><div class="muted" style="word-break:break-all">' + esc(S.cfg ? S.cfg.dbUrl : '') + '</div>' +
      '<div class="muted">Navbatda: ' + wait + ' ta · versiya ' + VERSION + '</div></div>' +
      '<button class="btn tonal" data-act="sync-all">Hammasini yangilash</button>' +
      '<button class="btn outline" data-act="logout">Chiqish</button>' +
      '<button class="btn outline" data-act="reset-cfg">Ulanish kodini almashtirish</button>' +
      installHint() + '</main>';
  }

  // ================= hodisalar =================

  document.addEventListener('click', function (e) {
    var t = e.target.closest('[data-act]');
    if (!t || t.closest('.overlay')) return;
    var a = t.dataset.act;
    var id = t.dataset.id;
    switch (a) {
      case 'back': go(S.view === 'cart' ? 'sale' : 'home'); break;
      case 'home': go('home'); break;
      case 'settings': go('settings'); break;
      case 'setup': doSetup(); break;
      case 'login': doLogin(); break;
      case 'reset-cfg':
        if (confirm("Ulanish kodi o'chiriladi (katalog va navbat saqlanib qoladi). Davom etasizmi?")) { S.cfg = null; LS.del('cfg'); S.agent = null; LS.del('agent'); go('setup'); }
        break;
      case 'logout':
        if (S.outbox.some(function (x) { return x.st === 'wait'; }) && !confirm('Navbatda yuborilmagan savdo bor. Baribir chiqasizmi? (navbat saqlanib qoladi)')) break;
        S.agent = null; LS.del('agent'); go('login'); break;
      case 'sync-cat': doSyncCat(t); break;
      case 'sync-all': doSyncCat(t); syncHist(); pump(); break;
      case 'new-sale':
        if (S.draft.mode === '2') { S.draft.mode = '1'; saveDraft(); }
        go('sale'); if (!S.draft.storeId) setTimeout(function () { pickStore(setStore); }, 50); break;
      case 'new-return':
        S.draft.mode = '2'; saveDraft(); go('sale'); if (!S.draft.storeId) setTimeout(function () { pickStore(setStore); }, 50); break;
      case 'pay': go('pay', { storeId: S.draft.storeId || LS.get('lastStore', '') }); break;
      case 'ledger': go('ledger', { hq: '' }); syncHist(); break;
      case 'hist-sync': syncHist(); toast('Yangilanmoqda…'); break;
      case 'report': go('report', { storeId: S.draft.storeId || LS.get('lastStore', '') }); break;
      case 'sent': go('sent'); pump(); break;
      case 'pump': pump(); toast('Tekshirilmoqda…'); break;
      case 'pick-store': pickStore(setStore); break;
      case 'pay-store': readPayForm(); pickStore(function (sid) { S.params.storeId = sid; render(); }); break;
      case 'rep-store': pickStore(function (sid) { S.params.storeId = sid; S.params.html = ''; render(); }); break;
      case 'debt': refreshDebt(t.dataset.store || S.draft.storeId); break;
      case 'seg': onSeg(t.dataset.k, t.dataset.v); break;
      case 'plus': { var p = product(id); if (p && !(priceOf(p, S.draft) > 0) && !lineOf(id)) { editLine(id); break; } addQty(id, 1); afterLineChange(); break; }
      case 'minus': addQty(id, -1); afterLineChange(); break;
      case 'edit': editLine(id); break;
      case 'more': listLimit += 300; renderList(); break;
      case 'clear': if (confirm('Savatcha tozalansinmi?')) { S.draft.lines = []; saveDraft(); render(); } break;
      case 'cart': go('cart'); break;
      case 'pay-all': {
        var tot = total(); var disc = C.parseUser(S.draft.discount) || 0;
        S.draft.cash = C.plain(Math.max(tot - Math.max(disc, 0), 0)); S.draft.card = ''; saveDraft(); render(); break;
      }
      case 'send': t.disabled = true; sendOrder().catch(function (err) { toast(errText(err)); t.disabled = false; }); break;
      case 'send-pay': t.disabled = true; sendPay().catch(function (err) { toast(errText(err)); t.disabled = false; }); break;
      case 'hdoc': showDoc(Number(t.dataset.i)); break;
      case 'run-report': runReport(); break;
    }
  });

  function setStore(sid) {
    S.draft.storeId = sid; saveDraft(); LS.set('lastStore', sid); render();
  }

  function onSeg(k, v) {
    var d = S.draft;
    if (k === 'level') { d.level = Number(v); repriceAll(); }
    else if (k === 'usd') {
      var usd = v === '1';
      if (usd !== d.usd) { d.usd = usd; d.lines.forEach(function (l) { l.manual = false; }); repriceAll(); d.cash = ''; d.card = ''; d.discount = ''; }
    } else if (k === 'mode') d.mode = v;
    else if (k === 'printer') d.printer = v;
    else if (k === 'pprinter') { readPayForm(); S.params.printer = v; render(); return; }
    saveDraft();
    if (S.view === 'sale') { var y = window.scrollY; render(); window.scrollTo(0, y); } else render();
  }

  document.addEventListener('input', function (e) {
    var t = e.target;
    if (t.id === 'q') { S.params.q = t.value; listLimit = 150; renderList(); return; }
    if (t.id === 'hq') { S.params.hq = t.value; listLimit = 150; renderList(); return; }
    if (t.id === 'disc' || t.id === 'cash' || t.id === 'card') {
      S.draft[t.id === 'disc' ? 'discount' : t.id] = t.value; saveDraft();
      var d = S.draft, tot = total(), disc = C.parseUser(d.discount) || 0;
      var payable = C.round2(Math.max(tot - Math.min(Math.max(disc, 0), Math.max(tot, 0)), 0));
      var paid = d.usd ? (C.parseUser(d.cash) || 0) : (C.parseUser(d.cash) || 0) + (C.parseUser(d.card) || 0);
      var pe = document.getElementById('payable'); if (pe) pe.textContent = money(payable, d.usd);
      var re = document.getElementById('rest'); if (re) re.textContent = restText(payable, paid, d.usd);
    }
  });

  async function doSetup() {
    var c = C.decodeCode(document.getElementById('code').value);
    var msg = document.getElementById('msg');
    if (!c) { msg.textContent = "Kod noto'g'ri. Admin bergan IMSAG1:... kodini to'liq joylang."; return; }
    S.cfg = c; LS.set('cfg', c);
    msg.textContent = '';
    try { await heartbeat(); } catch (e) { /* */ }
    go('login');
  }

  async function doLogin() {
    var phone = document.getElementById('phone').value.trim();
    var pass = document.getElementById('pass').value;
    var msg = document.getElementById('msg');
    var btn = document.querySelector('[data-act="login"]');
    if (!phone || !pass) { msg.textContent = 'Telefon va parolni yozing'; return; }
    btn.disabled = true; btn.innerHTML = '<span class="sp"></span> Admin tekshirmoqda…';
    try {
      var salt = C.newKey();
      var pr = await C.proof(pass, salt);
      var r = await rpc('login', { ph: phone, s: salt, pr: pr }, 40000);
      var a = r && r.a;
      if (!a) { msg.textContent = "Telefon yoki parol noto'g'ri"; btn.disabled = false; btn.textContent = 'Kirish'; return; }
      S.agent = { id: String(a[0]), name: a[1] || '', boss: a[2] || '', phone: a[3] || phone };
      LS.set('agent', S.agent); LS.set('phone', phone);
      go('home');
      doSyncCat(null);
    } catch (e) {
      msg.textContent = errText(e); btn.disabled = false; btn.textContent = 'Kirish';
    }
  }

  var catBusy = false;
  async function doSyncCat(btn) {
    if (catBusy) return;
    catBusy = true;
    if (btn) { btn.disabled = true; btn.innerHTML = '<span class="sp"></span> Yangilanmoqda…'; }
    try { await syncCatalog(); toast('Katalog yangilandi: ' + S.cat.products.length + ' ta tovar'); } catch (e) { toast(errText(e)); }
    catBusy = false;
    heartbeat().then(function () { if (S.view === 'home' || S.view === 'settings') render(); });
    if (S.view === 'home' || S.view === 'settings' || S.view === 'sale') render();
  }

  window.addEventListener('online', function () { render(); pump(); });
  window.addEventListener('offline', function () { render(); });
  document.addEventListener('visibilitychange', function () { if (!document.hidden) { pump(); heartbeat(); } });

  // ================= ishga tushirish =================

  if ('serviceWorker' in navigator) navigator.serviceWorker.register('sw.js').catch(function () { /* */ });
  loadLocal().then(function () {
    render();
    if (S.cfg && S.agent) {
      pump();
      heartbeat().then(function () { if (S.view === 'home') render(); });
      if (online() && Date.now() - (S.catAt || 0) > 10 * 60000) doSyncCat(null);
    }
  });
  setInterval(function () { if (!document.hidden) pump(); }, 15000);
})();
