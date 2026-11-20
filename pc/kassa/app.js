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

  var UI_DEFAULT = { wins: 3, stock: false, sound: true, autoprint: true, footer: '', hide: [], el: {}, ford: [], tord: [], thide: [], tsz: {}, tw: {}, lab: {},
    ptypeShow: false, chk1c: true, hideLock: false, auto: '', theme: 'oq', col: {}, notes: [50000, 100000, 200000] };
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
    { id: 'sales', b: 'Savdolar', s: 'istalgan chek' },
    { id: 'report', b: 'Kassa yopish', s: 'kun hisoboti' },
    { id: 'settings', b: 'Sozlamalar', s: 'admin paroli' }
  ];

  // pc 23: savat ostidagi tugmalar (id — tugma elementi, icon — kvadrat tugma). Tartib, o'lcham va yashirish sozlanadi.
  var TOOLS = [
    { id: 'up', btn: 'upBtn', n: '▲ Yuqoriga', icon: 1, d: '▲', ic: 'up' },
    { id: 'down', btn: 'downBtn', n: '▼ Pastga', icon: 1, d: '▼', ic: 'down' },
    { id: 'minus', btn: 'minusBtn', n: '− Kamaytirish', icon: 1, d: '−', ic: 'minus' },
    { id: 'plus', btn: 'plusBtn', n: "+ Ko'paytirish", icon: 1, d: '+', ic: 'plus' },
    { id: 'qty', btn: 'qtyBtn', n: 'Miqdor', d: 'Miqdor', ic: 'qty' },
    { id: 'price', btn: 'priceBtn', n: 'Narx', d: 'Narx', ic: 'price' },
    { id: 'del', btn: 'delBtn', n: "O'chirish", d: "O'chirish", ic: 'trash' },
    { id: 'clear', btn: 'clearBtn', n: 'Tozalash', d: 'Tozalash', ic: 'eraser' }
  ];
  var TSZ = { i: [[52, 0], [96, 1], [150, 2]], t: [[84, 0], [100, 1], [190, 2]] };   // [flex-basis px, flex-grow]
  /** Ro'yxatni saqlangan tartib bo'yicha qaytaradi; tartibda yo'qlari oxirida, asl tartibda. */
  function orderOf(list, ord) {
    var out = [], seen = {};
    (Array.isArray(ord) ? ord : []).forEach(function (id) {
      list.forEach(function (x) { if (x.id === id && !seen[id]) { seen[id] = 1; out.push(x); } });
    });
    list.forEach(function (x) { if (!seen[x.id]) out.push(x); });
    return out;
  }
  var RIGHT_W = 394;                                          // o'ng panelning asl kengligi (px), oldin 470
  /** Yorliq tugmaga sig'masa, shrift o'lchamini kichraytiradi; asl o'lchamga sig'sa 0 qaytaradi. */
  function fitFont(text, cols, base, hasIcon) {
    var rw = parseInt((((S.ui || {}).el || {}).right || {}).w, 10) || RIGHT_W;
    var avail = cols * ((rw - 16 - 30) / 6) + (cols - 1) * 6 - 8 - (hasIcon ? 40 : 0);
    var fs = Math.max(11, Math.floor(avail / (0.53 * Math.max(1, String(text).length))));
    return fs < base ? fs : 0;
  }
  /** Savat ostidagi tugmaning kengligi (px): qo'lda berilgan bo'lsa shu, aks holda tanlangan o'lchamniki. */
  function toolBasis(u, t) {
    var w = parseInt((u.tw || {})[t.id], 10);
    if (w >= 40) return w;
    return (t.icon ? TSZ.i : TSZ.t)[toolSize(t, u) - 1][0];
  }
  /** O'ng tomondagi tugma qatorning 6 ulushidan nechtasini egallaydi. */
  function fnCols(u, f) {
    var e = (u.el || {})['fn-' + f.id] || {};
    if (e.cols) return Math.max(1, Math.min(6, parseInt(e.cols, 10) || 1));
    var n = FN.filter(function (x) { return u.hide.indexOf(x.id) < 0 || x.id === 'settings'; }).length;
    return Math.min(6, (e.span || 1) * (n <= 4 ? 3 : 2));
  }
  function toolSize(t, u) {
    var n = parseInt(((u || S.ui).tsz || {})[t.id], 10);
    return n >= 1 && n <= 3 ? n : (t.icon ? 1 : 2);
  }
  function applyTools() {
    var box = document.querySelector('.row-tools');
    if (!box) return;
    var u = S.ui;
    orderOf(TOOLS, u.tord).forEach(function (t) {
      var b = $(t.btn);
      if (!b) return;
      box.appendChild(b);                                   // joyini almashtiradi, bosish hodisalari saqlanadi
      b.classList.toggle('hidden', (u.thide || []).indexOf(t.id) >= 0);
      var lb = (u.lab || {})['tool-' + t.id] || {}, mode = lb.m || 'text', name = lb.t || t.d;
      b.textContent = '';
      if (mode !== 'text') b.appendChild(icon(lb.i || t.ic));
      if (mode !== 'icon') b.appendChild(document.createTextNode(name));
      b.setAttribute('aria-label', name);
      if (mode === 'icon') b.title = name; else b.removeAttribute('title');
      var n = toolSize(t), d = (t.icon ? TSZ.i : TSZ.t)[n - 1], w = parseInt((u.tw || {})[t.id], 10);
      if (w >= 40) b.style.flex = '0 1 ' + w + 'px';        // qo'lda berilgan kenglik
      else b.style.flex = n === (t.icon ? 1 : 2) ? '' : d[1] + ' 1 ' + d[0] + 'px';   // asl o'lcham CSS'dan
    });
  }

  // pc 26: mavzular (savdo turiga mos tayyor ranglar) va ranglarni sozlash
  var THEMES = [
    { id: 'oq', n: 'Oq', s: 'kunduzgi, universal', dark: 0, v: { 'bg': '#e9eeeb', 'panel': '#ffffff', 'tile': '#f6f9f7', 'muted': '#e2e8e4', 'line': '#d3dcd7', 'ink': '#13201a', 'ink2': '#56665e', 'acc': '#0f6b45', 'acc-ink': '#ffffff', 'acc-soft': '#dcefe5', 'warn': '#b3261e', 'warn-soft': '#fbe3e1', 'ret': '#b4570b', 'ret-ink': '#ffffff', 'ret-soft': '#fdebd8', 'top': '#0f6b45', 'top-ink': '#ffffff', 'ret-top': '#b4570b', 'toast': '#1d2a24', 'toast-ink': '#ffffff', 'busy': 'rgba(255,255,255,.7)', 'flash': '#ffe58a' } },
    { id: 'qora', n: 'Qora', s: 'grafit, tungi', dark: 1, v: { 'bg': '#0c0d0e', 'panel': '#16181a', 'tile': '#1e2124', 'muted': '#282c30', 'line': '#363b41', 'ink': '#f4f6f7', 'ink2': '#b9c1c7', 'acc': '#4ade80', 'acc-ink': '#04140a', 'acc-soft': '#173a26', 'warn': '#ff9d94', 'warn-soft': '#3b1f1d', 'ret': '#f2a64d', 'ret-ink': '#1c1000', 'ret-soft': '#3a2a14', 'top': '#0f2a1c', 'top-ink': '#f4f6f7', 'ret-top': '#4a2c10', 'toast': '#eef1f3', 'toast-ink': '#0e1011', 'busy': 'rgba(0,0,0,.6)', 'flash': '#5f5410' } },
    { id: 'tun', n: 'Tungi ko\'k', s: 'elektronika, texnika', dark: 1, v: { 'bg': '#080d1c', 'panel': '#111a30', 'tile': '#19243f', 'muted': '#223052', 'line': '#2f3f68', 'ink': '#eef2ff', 'ink2': '#b0bbdc', 'acc': '#6a9bff', 'acc-ink': '#04102e', 'acc-soft': '#1b2f5e', 'warn': '#ff9fa8', 'warn-soft': '#3b1e29', 'ret': '#ffb85c', 'ret-ink': '#1e1200', 'ret-soft': '#3b2d15', 'top': '#14224f', 'top-ink': '#eef2ff', 'ret-top': '#4d3112', 'toast': '#eaf0ff', 'toast-ink': '#0c1226', 'busy': 'rgba(3,6,20,.65)', 'flash': '#5a4f14' } },
    { id: 'zumrad', n: 'Zumrad', s: 'oziq-ovqat, bozor', dark: 1, v: { 'bg': '#06130f', 'panel': '#0e2119', 'tile': '#143026', 'muted': '#1b3c30', 'line': '#265144', 'ink': '#effaf4', 'ink2': '#b0d3c1', 'acc': '#34e0a1', 'acc-ink': '#03150d', 'acc-soft': '#12432f', 'warn': '#ffa39a', 'warn-soft': '#3d2220', 'ret': '#f5b04f', 'ret-ink': '#1d1100', 'ret-soft': '#3b2c14', 'top': '#0c3a2a', 'top-ink': '#effaf4', 'ret-top': '#4a2f10', 'toast': '#eaf7f0', 'toast-ink': '#0a1511', 'busy': 'rgba(2,12,8,.65)', 'flash': '#5f5a12' } },
    { id: 'qahva', n: 'Qahva', s: 'kafe, qandolat', dark: 1, v: { 'bg': '#140e0a', 'panel': '#211812', 'tile': '#2b201a', 'muted': '#362a22', 'line': '#4a392e', 'ink': '#f8f0e7', 'ink2': '#d0bfae', 'acc': '#f0b35a', 'acc-ink': '#1f1300', 'acc-soft': '#4a3519', 'warn': '#ff9f90', 'warn-soft': '#402220', 'ret': '#6fd6c6', 'ret-ink': '#002a25', 'ret-soft': '#123a35', 'top': '#2e1f14', 'top-ink': '#f8f0e7', 'ret-top': '#124a43', 'toast': '#f6ece0', 'toast-ink': '#1a110a', 'busy': 'rgba(10,5,2,.65)', 'flash': '#6b5518' } },
    { id: 'binafsha', n: 'Binafsha', s: 'kosmetika, butik', dark: 1, v: { 'bg': '#110a1b', 'panel': '#1b122c', 'tile': '#251a3b', 'muted': '#30234d', 'line': '#44346b', 'ink': '#f6f0ff', 'ink2': '#c8b9e4', 'acc': '#c58bff', 'acc-ink': '#1c0a30', 'acc-soft': '#3d2764', 'warn': '#ff9eb0', 'warn-soft': '#40202c', 'ret': '#ffc266', 'ret-ink': '#221400', 'ret-soft': '#3d2e16', 'top': '#2a1850', 'top-ink': '#f6f0ff', 'ret-top': '#4d3412', 'toast': '#f1e9ff', 'toast-ink': '#160c26', 'busy': 'rgba(8,3,16,.65)', 'flash': '#5e4d18' } },
    { id: 'qizil', n: 'Qizil', s: 'go\'sht, fast-food', dark: 1, v: { 'bg': '#150909', 'panel': '#221111', 'tile': '#2d1717', 'muted': '#391e1e', 'line': '#4e2a2a', 'ink': '#fff1ee', 'ink2': '#e0bcb6', 'acc': '#ff7a66', 'acc-ink': '#2b0700', 'acc-soft': '#4d2019', 'warn': '#ffcf6b', 'warn-soft': '#40300f', 'ret': '#7ad6ff', 'ret-ink': '#00202e', 'ret-soft': '#11323f', 'top': '#4a1313', 'top-ink': '#fff1ee', 'ret-top': '#11405a', 'toast': '#fff0ec', 'toast-ink': '#200a07', 'busy': 'rgba(15,3,3,.65)', 'flash': '#5f4a12' } },
    { id: 'issiq', n: 'Issiq krem', s: 'novvoyxona, shirinlik', dark: 0, v: { 'bg': '#f2e8d8', 'panel': '#fffaf1', 'tile': '#f7eddd', 'muted': '#eadcc6', 'line': '#dccbb0', 'ink': '#2a1a0c', 'ink2': '#6b5640', 'acc': '#b4531a', 'acc-ink': '#ffffff', 'acc-soft': '#f7dcc4', 'warn': '#a8201a', 'warn-soft': '#fbe1dd', 'ret': '#0e6f8c', 'ret-ink': '#ffffff', 'ret-soft': '#d6eef5', 'top': '#8f3d10', 'top-ink': '#ffffff', 'ret-top': '#0e6f8c', 'toast': '#2a1a0c', 'toast-ink': '#ffffff', 'busy': 'rgba(255,250,241,.72)', 'flash': '#ffd98a' } },
    { id: 'dengiz', n: 'Dengiz', s: 'dorixona, toza', dark: 0, v: { 'bg': '#e3eef6', 'panel': '#ffffff', 'tile': '#f0f6fb', 'muted': '#dbe7f0', 'line': '#c3d5e2', 'ink': '#0e2130', 'ink2': '#496377', 'acc': '#0b6db3', 'acc-ink': '#ffffff', 'acc-soft': '#d3e8f7', 'warn': '#b3261e', 'warn-soft': '#fbe3e1', 'ret': '#b4570b', 'ret-ink': '#ffffff', 'ret-soft': '#fdebd8', 'top': '#0a5a94', 'top-ink': '#ffffff', 'ret-top': '#b4570b', 'toast': '#14283a', 'toast-ink': '#ffffff', 'busy': 'rgba(255,255,255,.7)', 'flash': '#ffe58a' } },
    { id: 'kontrast', n: 'Kontrast', s: 'yorug\' joy, qora-sariq', dark: 1, v: { 'bg': '#000000', 'panel': '#0c0c0c', 'tile': '#1a1a1a', 'muted': '#262626', 'line': '#505050', 'ink': '#ffffff', 'ink2': '#dcdcdc', 'acc': '#ffd400', 'acc-ink': '#000000', 'acc-soft': '#2f2800', 'warn': '#ff8a80', 'warn-soft': '#3a1512', 'ret': '#4dd0ff', 'ret-ink': '#00131c', 'ret-soft': '#0b2f3d', 'top': '#1c1c1c', 'top-ink': '#ffffff', 'ret-top': '#0b3a4d', 'toast': '#ffffff', 'toast-ink': '#000000', 'busy': 'rgba(0,0,0,.7)', 'flash': '#5a4a00' } }
  ];
  var THEME_BY_ID = {};
  THEMES.forEach(function (t) { THEME_BY_ID[t.id] = t; });
  // Sozlanadigan ranglar: [o'zgaruvchi, nomi]
  var COLN = [
    ['bg', 'Fon'], ['panel', "Panel (savat va o'ng panel foni)"], ['tile', 'Katak va maydonlar foni'], ['muted', 'Oddiy tugmalar'], ['line', 'Chiziqlar'],
    ['ink', 'Asosiy yozuv'], ['ink2', 'Ikkinchi darajali yozuv'],
    ['acc', 'Asosiy rang (ikonka, tugma, yozuv)'], ['acc-soft', 'Asosiy rangning yumshoq foni (pastdagi tugmalar)'], ['acc-ink', 'Asosiy rang ustidagi yozuv'],
    ['top', 'Yuqori panel'], ['top-ink', 'Yuqori panel yozuvi'],
    ['ret', 'Qaytarish rangi'], ['ret-soft', 'Qaytarishning yumshoq foni'], ['ret-ink', 'Qaytarish ustidagi yozuv'], ['ret-top', 'Qaytarish rejimida yuqori panel'],
    ['warn', 'Qarz va xato rangi'], ['warn-soft', 'Qarz va xatoning yumshoq foni'], ['toast', 'Xabar oynasi'], ['toast-ink', 'Xabar yozuvi']
  ];
  var COLK = COLN.map(function (c) { return c[0]; });
  function hex2rgb(h) { h = String(h).replace('#', ''); return [parseInt(h.slice(0, 2), 16) || 0, parseInt(h.slice(2, 4), 16) || 0, parseInt(h.slice(4, 6), 16) || 0]; }
  function rgb2hex(r, g, b) {
    return '#' + [r, g, b].map(function (x) { x = Math.max(0, Math.min(255, Math.round(x))); return (x < 16 ? '0' : '') + x.toString(16); }).join('');
  }
  function rgb2hsl(r, g, b) {
    r /= 255; g /= 255; b /= 255;
    var mx = Math.max(r, g, b), mn = Math.min(r, g, b), l = (mx + mn) / 2, h = 0, s = 0, d = mx - mn;
    if (d) {
      s = l > 0.5 ? d / (2 - mx - mn) : d / (mx + mn);
      h = mx === r ? (g - b) / d + (g < b ? 6 : 0) : mx === g ? (b - r) / d + 2 : (r - g) / d + 4;
      h *= 60;
    }
    return [Math.round(h) % 360, Math.round(s * 100), Math.round(l * 100)];
  }
  function hsl2hex(h, s, l) {
    s /= 100; l /= 100;
    var k = function (n) { return (n + h / 30) % 12; }, a = s * Math.min(l, 1 - l);
    var f = function (n) { return l - a * Math.max(-1, Math.min(k(n) - 3, Math.min(9 - k(n), 1))); };
    return rgb2hex(f(0) * 255, f(8) * 255, f(4) * 255);
  }
  function mixHex(a, b, t) {                                // a va b orasidagi rang; t — b ning ulushi
    var x = hex2rgb(a), y = hex2rgb(b);
    return rgb2hex(x[0] + (y[0] - x[0]) * t, x[1] + (y[1] - x[1]) * t, x[2] + (y[2] - x[2]) * t);
  }
  function lumOf(hex) {
    var c = hex2rgb(hex).map(function (v) { v /= 255; return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); });
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
  }
  function inkFor(hex) {                                      // shu rang ustida o'qiladigan (qora yoki oq) yozuv
    var l = lumOf(hex), dark = '#0a0f0c';
    return (l + 0.05) / (lumOf(dark) + 0.05) >= (1.05) / (l + 0.05) ? dark : '#ffffff';
  }
  function themeOf(id) { return THEME_BY_ID[id] || THEME_BY_ID.oq; }
  /** Mavzu ranglari + foydalanuvchi o'zgartirganlari. */
  function themeVars(u, id) {
    var t = themeOf(id || u.theme), v = {}, c = ((u && u.col) || {})[t.id] || {};
    Object.keys(t.v).forEach(function (k) { v[k] = t.v[k]; });
    Object.keys(c).forEach(function (k) { if (COLK.indexOf(k) >= 0 && /^#[0-9a-f]{6}$/i.test(c[k])) v[k] = c[k]; });
    return v;
  }
  function applyTheme(u) {
    var t = themeOf(u.theme), v = themeVars(u);
    var st = $('themeStyle');
    if (!st) { st = document.createElement('style'); st.id = 'themeStyle'; document.head.appendChild(st); }
    st.textContent = 'body{' + Object.keys(v).map(function (k) { return '--' + k + ':' + v[k]; }).join(';') + '}';
    document.body.classList.toggle('dark', !!t.dark);
    document.body.setAttribute('data-theme', t.id);
    var m = document.querySelector('meta[name="theme-color"]');
    if (m) m.setAttribute('content', v.top);
  }

  // pc 13: tugmalar ikonkasi (24x24, chiziqli)
  var ICON = {
    panel: '<rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/>',
    search: '<circle cx="10.5" cy="10.5" r="6.5"/><path d="M15.5 15.5 21 21"/>',
    price: '<path d="M3 12V4a1 1 0 0 1 1-1h8l9 9-9 9z"/><circle cx="8" cy="8" r="1.6"/>',
    code: '<path d="M4 5v14M7 5v14M11 5v14M14 5v14M18 5v14M20.5 5v14"/>',
    qty: '<path d="M12 4v16M4 12h16"/><rect x="2.5" y="2.5" width="19" height="19" rx="3"/>',
    disc: '<path d="M19 5 5 19"/><circle cx="7" cy="7" r="2.5"/><circle cx="17" cy="17" r="2.5"/>',
    ret: '<path d="M9 14 4 9l5-5"/><path d="M4 9h10a6 6 0 0 1 0 12h-3"/>',
    check: '<path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/>',
    debt: '<rect x="2.5" y="6" width="19" height="13" rx="2"/><path d="M2.5 10h19M16 14.5h2.5"/><path d="M6 6V4.5h12V6"/>',
    last: '<path d="M7 9V3h10v6"/><rect x="3" y="9" width="18" height="8" rx="2"/><path d="M7 14h10v7H7z"/>',
    sales: '<path d="M6 2h12v20l-3-2-3 2-3-2-3 2z"/><path d="M9 7h6M9 11h6M9 15h4"/>',
    report: '<path d="M4 20V10M10 20V4M16 20v-7M22 20H2"/>',
    settings: '<circle cx="12" cy="12" r="3.2"/><path d="M12 2v3M12 19v3M4.9 4.9l2.1 2.1M17 17l2.1 2.1M2 12h3M19 12h3M4.9 19.1 7 17M17 7l2.1-2.1"/>',
    up: '<path d="M12 19V5M5 12l7-7 7 7"/>',
    down: '<path d="M12 5v14M5 12l7 7 7-7"/>',
    minus: '<path d="M5 12h14"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    trash: '<path d="M4 7h16M10 7V4h4v3M6 7l1 13h10l1-13M10 11v6M14 11v6"/>',
    eraser: '<path d="M16 3l5 5-11 11H5l-2-2 13-14z"/><path d="M12 20h9"/>',
    star: '<path d="m12 3 2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z"/>',
    user: '<circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/>',
    cart: '<circle cx="9" cy="20" r="1.6"/><circle cx="18" cy="20" r="1.6"/><path d="M2 3h3l2.6 12h11L21 7H6"/>',
    cash: '<rect x="2.5" y="6" width="19" height="12" rx="2"/><circle cx="12" cy="12" r="2.8"/><path d="M6 12h.01M18 12h.01"/>',
    list: '<path d="M8 6h13M8 12h13M8 18h13M3.5 6h.01M3.5 12h.01M3.5 18h.01"/>',
    tick: '<path d="m4 12.5 5 5L20 6.5"/>',
    cross: '<path d="M6 6l12 12M18 6 6 18"/>',
    refresh: '<path d="M20 11a8 8 0 1 0-2.3 5.7"/><path d="M20 4v7h-7"/>',
    box: '<path d="M3 7.5 12 3l9 4.5v9L12 21l-9-4.5z"/><path d="M3 7.5 12 12l9-4.5M12 12v9"/>',
    home: '<path d="M3 11 12 3l9 8M5 10v10h14V10"/>',
    bell: '<path d="M6 16V11a6 6 0 0 1 12 0v5l2 2H4z"/><path d="M10 21h4"/>',
    phone: '<path d="M6 3h4l2 5-2.5 1.5a11 11 0 0 0 5 5L16 12l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 5a2 2 0 0 1 2-2z"/>'
  };
  function icon(id) {
    var NS = 'http://www.w3.org/2000/svg';
    var svg = document.createElementNS(NS, 'svg');
    svg.setAttribute('viewBox', '0 0 24 24');
    svg.setAttribute('class', 'ic');
    svg.setAttribute('aria-hidden', 'true');
    svg.innerHTML = ICON[id] || '';
    return svg;
  }

  /** Savatdagi miqdor: birliksiz, nuqta bilan — donali "1", "2"; kiloli "0.122", "1.5". */
  function qtyText(m) {
    var neg = m < 0; m = Math.abs(m);
    var t = String(Math.floor(m / 1000));
    var f = m % 1000;
    if (f) t += '.' + String(f + 1000).slice(1).replace(/0+$/, '');
    return (neg ? '-' : '') + t;
  }

  function emptyWin() { return { lines: [], mode: 'sale', dmode: 'sum', dval: 0, id: '', ptype: 'Цена1' }; }

  var S = {
    token: load('k_token', ''),
    name: load('k_name', ''),
    data: null, ver: 0, byCode: {}, dataAt: 0,
    ui: mergeUi(load('k_ui', {})),
    wins: [],
    w: 0, sel: -1, disp: '', mult: 0,
    ptype: load('k_ptype', 'Цена1'),      // eski umumiy narx turi: faqat eski savatlarni ko'chirish uchun (v5.3.1: narx turi har oynada alohida)
    page: 0,
    pay: null,
    last: load('k_last', null),
    code: load('k_code', ''),
    recent: load('k_recent', []),
    flash: -1,
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
  /** Oynaning o'z narx turi (har bir oynada alohida). */
  function WPT(w) { return w && L.PRICE_TYPES.indexOf(w.ptype) >= 0 ? w.ptype : 'Цена1'; }
  function PT() { return WPT(W()); }

  function mergeUi(u) {
    var o = JSON.parse(JSON.stringify(UI_DEFAULT));
    if (u && typeof u === 'object') Object.keys(u).forEach(function (k) { o[k] = u[k]; });
    o.wins = Math.max(1, Math.min(4, parseInt(o.wins, 10) || 3));
    if (!Array.isArray(o.hide)) o.hide = [];
    if (!o.el || typeof o.el !== 'object') o.el = {};
    ['ford', 'tord', 'thide'].forEach(function (k) {
      o[k] = Array.isArray(o[k]) ? o[k].filter(function (x) { return typeof x === 'string'; }) : [];
    });
    if (!o.tsz || typeof o.tsz !== 'object' || Array.isArray(o.tsz)) o.tsz = {};
    if (!o.tw || typeof o.tw !== 'object' || Array.isArray(o.tw)) o.tw = {};
    var th = String(o.theme || '');
    o.theme = THEME_BY_ID[th] ? th : (th === 'dark' ? 'qora' : 'oq');      // eski 'light'/'dark' qiymatlari ko'chiriladi
    if (!o.col || typeof o.col !== 'object' || Array.isArray(o.col)) o.col = {};
    Object.keys(o.col).forEach(function (tid) {
      var c = o.col[tid];
      if (!THEME_BY_ID[tid] || !c || typeof c !== 'object' || Array.isArray(c)) { delete o.col[tid]; return; }
      Object.keys(c).forEach(function (k) {
        if (COLK.indexOf(k) < 0 || !/^#[0-9a-fA-F]{6}$/.test(String(c[k]))) delete c[k]; else c[k] = String(c[k]).toLowerCase();
      });
    });
    if (!o.lab || typeof o.lab !== 'object' || Array.isArray(o.lab)) o.lab = {};
    Object.keys(o.lab).forEach(function (k) {
      var l = o.lab[k];
      if (!/^(fn|tool)-[a-z]+$/.test(k) || !l || typeof l !== 'object') { delete o.lab[k]; return; }
      o.lab[k] = { t: typeof l.t === 'string' ? l.t.slice(0, 24) : '', m: l.m === 'text' || l.m === 'icon' || l.m === 'both' ? l.m : '', i: typeof l.i === 'string' && ICON[l.i] ? l.i : '' };
    });
    Object.keys(o.tw).forEach(function (k) { var v = parseInt(o.tw[k], 10); if (v >= 40 && v <= 400) o.tw[k] = v; else delete o.tw[k]; });
    return o;
  }
  function fixWins(w) {
    if (!Array.isArray(w)) w = [];
    var n = S.ui.wins;
    var out = [];
    for (var i = 0; i < Math.max(n, w.length); i++) {
      var x = w[i] && Array.isArray(w[i].lines) ? w[i] : emptyWin();
      if (L.PRICE_TYPES.indexOf(x.ptype) < 0) x.ptype = x.lines.length && L.PRICE_TYPES.indexOf(S.ptype) >= 0 ? S.ptype : 'Цена1';
      if (i < n || x.lines.length) out.push(x);     // tovari bor oyna o'chirilmaydi
    }
    return out;
  }

  // ---------------- yordamchilar ----------------
  var toastTimer = 0;
  function toast(msg, err, ms) {
    var t = $('toast');
    t.textContent = msg;
    t.className = 'toast' + (err ? ' err' : '');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { t.className = 'toast hidden'; }, ms || (err ? 5000 : 2500));
    if (err) beep();
  }
  var audio = null;
  function tone(freq, sec, vol) {
    if (!S.ui.sound) return;
    try {
      audio = audio || new (window.AudioContext || window.webkitAudioContext)();
      var o = audio.createOscillator(); var g = audio.createGain();
      o.frequency.value = freq; g.gain.value = vol;
      o.connect(g); g.connect(audio.destination); o.start(); o.stop(audio.currentTime + sec);
    } catch (e) { /* ovoz yo'q */ }
  }
  function beep() { tone(330, 0.3, 0.2); }          // xato — past, uzunroq (muvaffaqiyatda ovoz yo'q: skanerning o'zi chiqaradi)
  /** pc 17: sotuv yuborilayotganda 0 dan 100 gacha to'ladigan chiziq (tez javobda umuman ko'rinmaydi). */
  var prog = { t0: 0, raf: 0, show: 0 };
  function progress(on) {
    var bar = $('progBar'), pct = $('progPct'), box = $('prog');
    cancelAnimationFrame(prog.raf); clearTimeout(prog.show);
    if (on) {
      prog.t0 = Date.now();
      bar.style.width = '0%'; pct.textContent = '0%';
      busyAt = Date.now();
      prog.show = setTimeout(function () { box.classList.remove('hidden'); }, 150);
      var step = function () {
        var p = Math.min(95, Math.round(95 * (1 - Math.exp(-(Date.now() - prog.t0) / 700))));
        bar.style.width = p + '%'; pct.textContent = p + '%';
        prog.raf = requestAnimationFrame(step);
      };
      prog.raf = requestAnimationFrame(step);
      return;
    }
    busyAt = 0;
    if (box.classList.contains('hidden')) return;
    bar.style.width = '100%'; pct.textContent = '100%';
    prog.show = setTimeout(function () { box.classList.add('hidden'); }, 180);
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
  function choosePtype() {
    var shown = S.ui.ptypeShow;
    ask({
      title: shown ? 'Narx turi' : 'Rejim',
      choices: L.PRICE_TYPES.map(function (p) {
        return { label: shown ? ptypeLabel(p) : String(L.PRICE_TYPES.indexOf(p) + 1), on: p === PT(), pick: function () {
          if (W().ptype !== p) { W().ptype = p; W().id = ''; }
          repriceAll(true);
          renderAll();
        } };
      })
    });
  }

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
    ch.classList.toggle('icons', !!o.icons);
    (o.choices || []).forEach(function (c) {
      var b = el('button', c.on ? 'on' : '', c.icon ? '' : c.label);
      if (c.icon) b.appendChild(icon(c.icon));
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
      S.token = j.token; S.name = j.name; S.code = j.code || '';
      save('k_token', S.token); save('k_name', S.name); save('k_code', S.code);
      enter();
    }).catch(function (e) {
      $('loginBtn').disabled = false;
      if (e.message !== 'login') $('loginErr').textContent = "Kompyuterga ulanib bo'lmadi: " + e.message;
    });
  }
  function lock(msg) {
    S.token = ''; save('k_token', '');
    S.grant = '';
    autoLogin().catch(function () { showLogin(msg || ''); });
  }
  /** Sozlamada "parol so'ralmasin" yoqilgan bo'lsa — parol oynasi chiqmaydi, tanlangan kassir nomidan kiriladi. */
  function autoLogin() {
    return api('GET', '/api/ui', undefined, 8000).then(function (u) {
      if (u && typeof u === 'object' && !u.e) applyUi(mergeUi(u));
      if (!S.ui.auto) throw new Error('auto yo\'q');
      return api('POST', '/api/login', { auto: 1 }, 60000);
    }).then(function (j) {
      if (!j.ok) throw new Error(j.e || 'Xato');
      S.token = j.token; S.name = j.name; S.code = j.code || '';
      save('k_token', S.token); save('k_name', S.name); save('k_code', S.code);
      enter();
      return true;
    });
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
        if (l.m) return;                                   // qo'lda yozilgan narx o'zgarmaydi
        var p = L.priceOf(g, WPT(w), S.data.rate);
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
      S.pcQ = j.q || 0; S.pcQa = j.qa || 0;
      if ((j.qe || 0) !== (S.pcQe || 0)) { S.pcQe = j.qe || 0; renderFn(); }
      S.pcv = j.v || '';
      showUpdating(j.up || '');
      if (!!j.ps !== !!S.ps) { S.ps = !!j.ps; renderFn(); }
      if (j.pe && j.pe !== S.pe) { if (S.pe !== undefined) toast("1C chekni chiqarmadi: " + j.pe, true, 8000); S.pe = j.pe; }
      else if (S.pe === undefined) S.pe = j.pe || '';
      renderQueue();
    }).catch(function () { renderDot(true); });
  }
  function idle() {
    return hidden('payModal') && hidden('askModal') && hidden('busy') && hidden('setView') && !S.edit;
  }
  /** pc 19: sariq belgi kassirni chalg'itmasin — faqat sotuv 1 daqiqadan ko'p kutib qolsa; xatolar Sozlamalarda. */
  function renderQueue() {
    var late = (S.pcQa || 0) >= 60 ? S.pcQ : 0;
    var n = late + S.outbox.length;
    $('queueBadge').classList.toggle('hidden', n === 0);
    $('queueN').textContent = String(n);
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
    var price = L.priceOf(g, WPT(w), S.data.rate);
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
    hideChange();
    S.flash = S.sel;
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
    // Tozalash va sotuvdan keyin shu oynaning narx turi 1 ga qaytadi (emptyWin), boshqa oynalarga tegmaydi
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
    $('ptypeName').textContent = ptypeLabel(PT());
    // xaridorga bilinmasin: yuqoridagi tugma faqat sozlamada yoqilsa; aks holda raqam maydoni burchagida kichik raqam
    $('ptypeBtn').classList.toggle('hidden', !S.ui.ptypeShow);
    $('lockBtn').classList.toggle('hidden', !!(S.ui.hideLock || S.ui.auto));
    $('ptypeTag').textContent = S.ui.ptypeShow ? '' : String(L.PRICE_TYPES.indexOf(PT()) + 1);
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
  /** Tugmalar: ikonka + nom. Ko'rinadiganlari 6 ustunli to'rga joylanadi — yashirilganlari o'rniga qolganlari
   *  kengayadi (oxirgi qator ham to'liq to'ladi), bo'sh joy qolmaydi. */
  function renderFn() {
    var box = $('fn');
    box.textContent = '';
    var ret = S.wins.length && W().mode === 'return';
    var list = orderOf(FN, S.ui.ford).filter(function (f) { return S.ui.hide.indexOf(f.id) < 0 || f.id === 'settings'; });
    var items = list.map(function (f) { return { f: f, w: fnCols(S.ui, f) }; });
    var rows = [], cur = [], used = 0;
    items.forEach(function (it) {
      if (used + it.w > 6 && cur.length) { rows.push(cur); cur = []; used = 0; }
      cur.push(it); used += it.w;
    });
    if (cur.length) rows.push(cur);
    rows.forEach(function (r) {
      var left = 6 - r.reduce(function (a, it) { return a + it.w; }, 0);
      for (var i = 0; left > 0; i = (i + 1) % r.length, left--) r[i].w++;
    });
    rows.forEach(function (r) {
      r.forEach(function (it) {
        var f = it.f;
        var b = el('button', (f.id === 'panel' ? 'fn-main ' : '') + (f.big ? 'fn-big' : ''));
        b.setAttribute('data-fn', f.id);
        b.setAttribute('data-ui', 'fn-' + f.id);
        b.style.gridColumn = 'span ' + it.w;
        var sub = f.s;
        if (f.id === 'ret' && ret) { b.classList.add('on'); sub = 'yoqilgan — bekor'; }
        if (f.id === 'check' && S.check) { b.classList.add('on'); sub = 'skanerlang…'; }
        if (f.id === 'disc' && S.wins.length && discount()) sub = 'bekor qilish';
        if (f.id === 'settings' && S.ps) sub = 'yangi panel tavsiyasi';
        var lb = (S.ui.lab || {})['fn-' + f.id] || {};
        var mode = lb.m || 'both', name = lb.t || f.b;
        var dot = f.id === 'settings' && (S.ps || S.pcQe);
        if (mode !== 'text') b.appendChild(icon(lb.i || f.id));
        if (mode !== 'icon') {
          var tx = el('span', 'tx');
          var bt = el('b', '', name);
          var fz = fitFont(name, it.w, f.big ? 22 : 18, f.id === 'settings' && mode !== 'text');
          if (fz) bt.style.fontSize = fz + 'px';
          if (dot) bt.appendChild(el('i', 'badge-dot'));
          tx.appendChild(bt);
          b.appendChild(tx);
        } else if (dot) b.appendChild(el('i', 'badge-dot'));
        b.setAttribute('aria-label', name);
        b.title = sub;
        box.appendChild(b);
      });
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
      if (S.ui.stock) {
        var sg = S.byCode[l.code];
        row.appendChild(el('span', 'st' + (sg && sg.s <= 0 ? ' none' : ''), sg ? qtyText(L.milli(sg.s)) : ''));
      }
      row.appendChild(el('span', '', qtyText(l.milli)));
      row.appendChild(el('span', '', money(l.price)));
      if (l.m) row.children[row.children.length - 1].classList.add('manual');
      row.appendChild(el('span', 'sm', money(L.lineSum(l.price, l.milli))));
      if (i === S.flash) row.classList.add('flash');
      c.appendChild(row);
    });
    S.flash = -1;
    document.body.classList.toggle('stockcol', !!S.ui.stock);
    $('thStock').classList.toggle('hidden', !S.ui.stock);
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
      info.appendChild(el('span', '', qtyText(s.milli) + ' × ' + money(s.price) + ' = ' + money(L.lineSum(s.price, s.milli)) +
        (g && S.ui.stock ? ' · qoldiq: ' + qtyText(L.milli(g.s)) : '')));
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
    p.appendChild(el('span', '', money(L.priceOf(g, PT(), S.data.rate))));
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
      var q = k.text.trim();
      var list = q ? L.searchClients(S.data.cl, q, 60) : S.data.cl.filter(function (c) { return c[3] > 0; }).slice(0, 60);
      var n = 0;
      for (var i = 0; i < list.length; i++) {
        var c = list[i];
        var b = el('button', 'cl');
        b.setAttribute('data-ci', S.data.cl.indexOf(c));
        b.appendChild(el('b', '', c[1]));
        b.appendChild(el('small', '', c[2] || ''));
        if (c[3]) b.appendChild(el('small', c[3] > 0 ? 'debt' : '', 'qarz: ' + money(L.cents(c[3]))));
        box.appendChild(b);
        n++;
      }
      if (!n) box.appendChild(el('div', 'grid-msg', q ? 'Mijoz topilmadi' : "Ismi yoki telefonini yozing (qarzdorlar ko'rsatilgan)"));
      return;
    }
    var recent = !k.text.trim() && k.kind === 'goods';
    var res = k.text.trim() ? L.search(S.data.g, k.text, PT(), S.data.rate, 60) :
      recent ? S.recent.map(function (c) { return S.byCode[c]; }).filter(Boolean) : [];
    if (recent && res.length) box.appendChild(el('div', 'kb-sub', 'Oxirgi qidirilganlar'));
    if (k.kind === 'price') {
      var pc = L.cents(k.text);
      res = S.data.g.filter(function (g) { return L.priceOf(g, PT(), S.data.rate) === pc; }).slice(0, 60);
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
    var rows = S.ui.ptypeShow ? L.PRICE_TYPES.map(function (p) {
      return { a: ptypeLabel(p) + (p === PT() ? ' (hozirgi)' : ''), b: money(L.priceOf(g, p, S.data.rate)), cls: p === PT() ? 'big' : '' };
    }) : [{ a: 'Narx', b: money(L.priceOf(g, PT(), S.data.rate)), cls: 'big' }];
    if (S.ui.stock) rows.push({ a: 'Qoldiq', b: qtyText(L.milli(g.s)), cls: g.s > 0 ? '' : 'warn' });
    rows.push({ a: 'Kod', b: g.c });
    info(g.n, rows, [{ label: "Savatga qo'shish", cls: 'primary', fn: function () { $('infoModal').classList.add('hidden'); addGoods(g, 1000, false); } }]);
  }
  // ---------------- savdolar ro'yxati: istalgan chekni qayta chiqarish ----------------
  function ymd(d) { var p = function (n) { return (n < 10 ? '0' : '') + n; }; return d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate()); }
  function showSales(day) {
    busy(true, "Savdolar olinmoqda…");
    api('GET', '/api/sales?t=' + encodeURIComponent(S.token) + (day ? '&d=' + day : ''), undefined, 50000).then(function (j) {
      busy(false);
      if (!j.ok) throw new Error(j.e || 'Xato');
      var d = new Date(j.day + 'T12:00:00');
      var rows = [];
      var nav = el('div', 'row-btns nav3');
      [[-1, '◀ Oldingi'], [0, 'Bugun'], [1, 'Keyingi ▶']].forEach(function (x) {
        var bt = el('button', 'big muted', x[1]);
        bt.addEventListener('click', function () {
          var n = x[0] === 0 ? new Date() : new Date(d.getTime() + x[0] * 86400000);
          showSales(ymd(n));
        });
        nav.appendChild(bt);
      });
      rows.push({ node: nav });
      if (j.e1c) rows.push({ a: "1C'dagi savdolar o'qilmadi", b: j.e1c.slice(0, 80), cls: 'warn' });
      (j.items || []).forEach(function (r) {
        var it = el('div', 'q-item sale-item');
        var a = el('span', '', (r.date || '').slice(11) + ' · ' + (r.kind === 'return' ? 'Qaytarish' : r.doc === 'ОплатаДолга' ? "Qarz to'lovi" : 'Sotuv') +
          ' № ' + r.no + ' · ' + money(L.cents(r.total)));
        a.appendChild(el('small', '', (r.cname || 'Savdo') + ' · ' + (r.cashier || '') + ' · ' + (r.lines || []).length + ' ta tovar' +
          (r.src === '1c' ? " · 1C'da qilingan" : '')));
        it.appendChild(a);
        var pr = el('button', 'big primary', 'Chek');
        pr.addEventListener('click', function () { reprint(r); });
        it.appendChild(pr);
        rows.push({ node: it });
      });
      if (!(j.items || []).length) rows.push({ a: 'Savdo yo\'q', b: d.toLocaleDateString('ru-RU') });
      info('Savdolar · ' + d.toLocaleDateString('ru-RU'), rows);
    }).catch(function (e) { busy(false); if (e.message !== 'login') toast('Savdolar olinmadi: ' + e.message, true); });
  }
  /** KASSA YOPISH: kun hisoboti (1C'dagi hamma savdolar), kun bo'yicha; chop etish — kompyuterdagi chek printeriga. */
  function showReport(day) {
    busy(true, 'Hisobot tayyorlanmoqda…');
    api('GET', '/api/report?t=' + encodeURIComponent(S.token) + (day ? '&d=' + day : ''), undefined, 50000).then(function (r) {
      busy(false);
      if (r.ok === false) throw new Error(r.e || 'Xato');
      var c = function (v) { return money(L.cents(v)); };
      var d = new Date((r.iso || ymd(new Date())) + 'T12:00:00');
      var nav = el('div', 'row-btns nav3');
      [[-1, '◀ Oldingi'], [0, 'Bugun'], [1, 'Keyingi ▶']].forEach(function (x) {
        var bt = el('button', 'big muted', x[1]);
        bt.addEventListener('click', function () { showReport(ymd(x[0] === 0 ? new Date() : new Date(d.getTime() + x[0] * 86400000))); });
        nav.appendChild(bt);
      });
      var rows = [
        { a: 'Sana', b: r.day },
        { a: 'Hujjatlar soni', b: String(r.n) },
        { a: 'Savdo (jami)', b: c(r.sale), cls: 'big' },
        { a: 'Chegirma', b: c(r.disc) },
        { a: 'Qaytarish', b: c(r.ret), cls: r.ret ? 'warn' : '' },
        { a: 'Naqd', b: c(r.cash), cls: 'big' }, { a: 'Plastik', b: c(r.card) }, { a: 'Click', b: c(r.click) },
        { a: 'Qarzga berildi', b: c(r.debt), cls: r.debt ? 'warn' : '' }, { a: "Qarz to'landi", b: c(r.debt_paid) }
      ];
      Object.keys(r.by || {}).forEach(function (k) { rows.push({ a: 'Kassir: ' + k, b: r.by[k].n + ' ta · ' + c(r.by[k].sum) }); });
      var view = [{ node: nav }].concat(rows);
      if (r.e1c) view.push({ a: "1C o'qilmadi — faqat kassa savdolari", b: r.e1c.slice(0, 60), cls: 'warn' });
      if (r.q || r.qe) view.push({ a: "1C'ga hali yozilmagan", b: (r.q + r.qe) + ' ta', cls: 'warn' });
      info('Kassa yopish · ' + r.day, view, [{ label: 'Chop etish', cls: 'primary', fn: function () {
        if (!S.ui.chk1c) { printRows('Kassa yopish', rows); return; }
        api('POST', '/api/print_text', { token: S.token, title: 'KASSA YOPISH ' + r.day,
          rows: rows.map(function (x) { return [x.a, x.b]; }) }, 45000).then(function (j) {
          if (j.ok === false) throw new Error(j.e || 'Xato');
          toast(j.wait ? "Hisobot 1C'ga yuborildi" : 'Hisobot chiqarildi');
        }).catch(function (e) { if (e.message !== 'login') toast('Chop etilmadi: ' + e.message, true); });
      } }]);
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
    renderNotes();
    $('payTitle').textContent = w.mode === 'return' ? 'Qaytarish' : (t === 0 ? "Qarz to'lovi" : "To'lov");
    $('pDueLabel').textContent = w.mode === 'return' ? 'Qaytariladi' : "To'lanadi";
    $('printChk').checked = !!S.ui.autoprint;
    $('payErr').textContent = '';
    $('payModal').classList.remove('hidden');
    renderPay();
    if (debtOnly) openKb('client', "Qarz to'laydigan mijoz");
  }
  function closePay() { $('payModal').classList.add('hidden'); S.pay = null; }
  function renderNotes() {
    var box = $('notes');
    box.textContent = '';
    (S.ui.notes || []).forEach(function (n) {
      var b = el('button', 'note', money(L.cents(n)));
      b.setAttribute('data-note', String(n));
      box.appendChild(b);
    });
  }
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
      id: w.id, token: S.token, kind: w.mode, ptype: WPT(w),
      client: p.client ? p.client[0] : '',
      discount: L.centsText(st.discount), cash: L.centsText(p.cash), card: L.centsText(p.card), click: L.centsText(p.click),
      lines: w.lines.map(function (l) { return { code: l.code, name: l.name, qty: L.milliText(l.milli), price: L.centsText(l.price), m: l.m ? 1 : 0 }; })
    };
    var print = $('printChk').checked;
    body.print1c = !!(print && S.ui.chk1c);             // chekni 1C'ning o'zi chiqaradi (kompyuter printeri)
    progress(true);
    api('POST', '/api/sale', body, 60000).then(function (j) {
      progress(false);
      if (!j.ok) {
        $('payErr').textContent = j.e || 'Xato';
        beep();
        if (/Narx o'zgargan/.test(j.e || '')) refreshData();
        return;
      }
      done(j, print);
    }).catch(function (e) {
      progress(false);
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
    // dialog yo'q: darhol bo'sh savdo oynasi; qaytim/qarz qisqa xabarda
    var msg = $('doneChange').textContent;
    if (msg) showChange(msg, res.change ? '' : 'debt');
    else toast((res.kind === 'return' ? 'Qaytarish' : 'Sotuv') + ' qabul qilindi');
    var lines = W().lines.slice();
    clearWin();
    renderQueue();
    applyLocal(res, lines);
    if (print && !S.ui.chk1c) setTimeout(function () { printReceipt(res); }, 30);
  }
  /** Dastur yangilanayotgani (kompyuter o'zi topdi va o'rnatyapti) — kassa ustida yozuv; tugagach sahifa o'zi yangilanadi. */
  function showUpdating(text) {
    var b = $('updBar');
    if (text) { b.textContent = text; b.classList.remove('hidden'); S.updAt = Date.now(); }
    else if (S.updAt && Date.now() - S.updAt > 3000) { b.classList.add('hidden'); S.updAt = 0; }
  }
  /** Sotuvdan keyin qaytim (yoki qarz) savat ustida katta yozuvda 6 soniya; bosilsa yoki yangi tovar qo'shilsa yo'qoladi. */
  var changeTimer = 0;
  function showChange(text, cls) {
    var b = $('changeBig');
    b.textContent = text.toUpperCase();
    b.className = 'change-big' + (cls ? ' ' + cls : '');
    clearTimeout(changeTimer);
    changeTimer = setTimeout(hideChange, 6000);
  }
  function hideChange() { clearTimeout(changeTimer); $('changeBig').className = 'change-big hidden'; }
  /** Chek: sozlamaga ko'ra 1C'ning o'zi (kompyuter printeriga, bazadagi chek) yoki shu oyna. */
  function reprint(r) {
    if (!r) { toast("Hali chek yo'q", true); return; }
    if (!S.ui.chk1c) { printReceipt(r); return; }
    var body = { token: S.token };
    if (r.src === '1c') { body.no = r.no; body.date = r.date; } else body.id = r.id;
    api('POST', '/api/print', body, 45000).then(function (j) {
      if (!j.ok) throw new Error(j.e || 'Xato');
      toast(j.wait ? "Chek 1C'ga yuborildi" : '1C chekni chiqardi');
    }).catch(function (e) { if (e.message !== 'login') toast('Chek: ' + e.message, true); });
  }
  /** Sotuvdan keyin qoldiq va mijoz qarzi shu yerning o'zida o'zgaradi — butun ro'yxat qayta yuklanmaydi (tez). */
  function applyLocal(res, lines) {
    if (!S.data || res.v0 == null || res.v0 !== S.ver) { refreshData(); return; }
    var sign = res.kind === 'return' ? 1 : -1;
    lines.forEach(function (l) {
      var g = S.byCode[l.code];
      if (g) g.s = Math.round((g.s + sign * l.milli / 1000) * 1000) / 1000;
    });
    if (res.client && res.debt) {
      var dd = (res.kind === 'return' ? -1 : 1) * res.debt;
      S.data.cl.forEach(function (c) { if (c[0] === res.client) c[3] = Math.round((c[3] + dd) * 100) / 100; });
    }
    S.ver = res.v1;
  }
  /** Chek — 1C'dagi "Чек1" maketi bilan bir xil tartib (sarlavha, vaqt/sana, mijoz, agent, qarz, tovarlar, summa,
   *  chegirma, to'lov, qarz qoldig'i, telefon). Qiymatlar 1C kabi: so'm — "1 446 750", dollar — "0,00". */
  function printReceipt(r) {
    var R = $('receipt');
    R.textContent = '';
    var k = (S.data && S.data.chk) || {};
    function row(cls, a, b) {
      var d = el('div', cls);
      d.appendChild(el('span', 'k', a));
      d.appendChild(el('span', 'v', b));
      R.appendChild(d);
    }
    function row3(c0, a, b) {
      var d = el('div', 't');
      d.appendChild(el('span', 'c0', c0)); d.appendChild(el('span', '', a)); d.appendChild(el('span', '', b));
      R.appendChild(d);
    }
    var som = function (v) { return money(L.cents(v || 0)); };
    var val0 = '0,00';
    var title = k.t || (S.data && (S.data.title || S.data.store)) || '';
    if (title) R.appendChild(el('div', 'ttl', title));
    if (k.t1 || k.t2) row('a', k.t1 || '', k.t2 || '');
    var now = new Date();
    var p2 = function (n) { return (n < 10 ? '0' : '') + n; };
    var m = /^(\d\d)\.(\d\d)\.\d\d(\d\d) (\d\d):(\d\d)/.exec(r.date || '');
    var tm = m ? m[4] + ':' + m[5] + ':' + p2(now.getSeconds()) : p2(now.getHours()) + ':' + p2(now.getMinutes()) + ':' + p2(now.getSeconds());
    var dt = m ? m[1] + '.' + m[2] + '.' + m[3] : p2(now.getDate()) + '.' + p2(now.getMonth() + 1) + '.' + String(now.getFullYear()).slice(2);
    row('a', 'Vaqt: ' + tm, 'Sana: ' + dt);
    if (r.kind === 'return') R.appendChild(el('div', 'ret', 'QAYTARISH'));
    if (r.client) {
      row('p', 'Mijoz:', r.client);
      row('p', 'Mijoz:', r.cname || '');
    }
    row('s ag', 'Agent:', r.cashier || '');
    var dd = (r.kind === 'return' ? -1 : 1) * (r.debt || 0);
    var after = r.client ? (r.cdebt || 0) : 0;
    var before = r.client ? after - dd : 0;
    row('s', "Qarz qoldig'i  (So'm):", som(before));
    row('s', "Qarz qoldig'i (Val):", val0);
    var tb = el('table');
    (r.lines || []).forEach(function (l, i) {
      var tr = el('tr'), td = el('td');
      td.appendChild(el('span', 'n', (i + 1) + ' || ' + l.n));
      td.appendChild(el('span', 'f', L.fmtQty(L.milli(l.q)) + ' х ' + som(l.p) + ' = ' + som(l.s)));
      tr.appendChild(td); tb.appendChild(tr);
    });
    R.appendChild(tb);
    R.appendChild(el('hr'));
    row3('Summa', 'Sum:', som(r.total));
    row3('', 'Val:', val0);
    R.appendChild(el('hr'));
    row3('Chegirma', 'Sum:', som(r.discount));
    row3('', 'Val:', val0);
    row3("To'lov", 'Naqd:', som(r.cash));
    row3('', 'Plastik:', som(r.card));
    if (r.click) row3('', 'Click:', som(r.click));
    row3('', 'Val ( $ ):', val0);
    row('s e', "Qarz qoldig'i (So'm)", som(after));
    row('s e', "Qarz qoldig'i (Val):", val0);
    var tel = [k.tel, k.tel1, k.tel2].filter(function (x) { return x; }).join(', ');
    if (tel) R.appendChild(el('div', 'tel', 'Tel : ' + tel));
    if (S.ui.footer) R.appendChild(el('div', 'foot', S.ui.footer));
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
    applyTheme(u);
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
    applyTools();
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
    $('setBody').firstChild.appendChild(c);
    return c;
  }
  var ICON_PICK = ['panel', 'search', 'price', 'code', 'qty', 'disc', 'ret', 'check', 'debt', 'last', 'sales', 'report', 'settings',
    'up', 'down', 'minus', 'plus', 'trash', 'eraser', 'star', 'user', 'cart', 'cash', 'list', 'tick', 'cross', 'refresh', 'box', 'home', 'bell', 'phone'];
  /** Tugma yorlig'i (nom, ko'rinish rejimi, ikonka) sozlamasini o'zgartiradi; hammasi asl bo'lsa yozuv o'chadi. */
  function setLab(u, key, patch) {
    var old = (u.lab || {})[key] || {}, l = {};
    ['t', 'm', 'i'].forEach(function (k) { l[k] = Object.prototype.hasOwnProperty.call(patch, k) ? patch[k] : (old[k] || ''); });
    if (!u.lab) u.lab = {};
    if (!l.t && !l.m && !l.i) delete u.lab[key]; else u.lab[key] = l;
  }
  function pickIcon(cur, def, done) {
    ask({
      title: 'Ikonka tanlang', text: '', icons: true,
      choices: ICON_PICK.map(function (k) {
        return { label: '', icon: k, on: k === (cur || def), pick: function () { done(k === def ? '' : k); } };
      })
    });
  }
  /** −/qiymat/+/Asl qatori. g: { label, step, reset, custom } */
  function stepRow(parent, title, g, u, t) {
    var wd = el('div', 'ord-wid');
    wd.appendChild(el('span', '', title));
    btn(wd, '−', 'wd', function () { g.step(u, t, -1); saveUi(u, renderSettings); });
    wd.appendChild(el('b', 'v', g.label(u, t)));
    btn(wd, '+', 'wd', function () { g.step(u, t, 1); saveUi(u, renderSettings); });
    btn(wd, 'Asl', 'wd asl' + (g.custom(u, t) ? ' on' : ''), function () { g.reset(u, t); saveUi(u, renderSettings); });
    parent.appendChild(wd);
  }
  /** Tugma balandligi (el[key].h, px): asl qiymat CSS'dan, qo'lda berilsa 10 px qadam bilan. */
  function heightOf(prefix, def) {
    return {
      label: function (u, t) { var h = (u.el[prefix + t.id] || {}).h; return h ? h + ' px' : 'Asl'; },
      step: function (u, t, d) {
        var e = u.el[prefix + t.id] || (u.el[prefix + t.id] = {});
        e.h = Math.max(30, Math.min(240, Math.round(((e.h || def) + d * 10) / 10) * 10));
      },
      reset: function (u, t) { var e = u.el[prefix + t.id]; if (e) delete e.h; },
      custom: function (u, t) { return !!(u.el[prefix + t.id] || {}).h; }
    };
  }
  // ---- rang tanlash oynasi: rang tusi + to'yinganlik + yorug'lik (hammasi bosish bilan)
  var COLS = null;
  function colorClose() { $('colModal').classList.add('hidden'); COLS = null; }
  function colorSet() { COLS.hex = hsl2hex(COLS.h, COLS.s, COLS.l); colorDraw(); }
  function colorDraw() {
    var box = $('colBox');
    box.textContent = '';
    $('colPrev').style.background = COLS.hex;
    $('colHex').textContent = COLS.hex.toUpperCase();
    function row(label, items) {
      var r = el('div', 'col-row2');
      r.appendChild(el('span', 'col-l', label));
      var g = el('div', 'col-sws');
      items.forEach(function (it) {
        var b = el('button', 'sw' + (it.on ? ' on' : ''));
        b.style.background = it.c;
        b.setAttribute('aria-label', it.c);
        b.addEventListener('click', it.pick);
        g.appendChild(b);
      });
      r.appendChild(g);
      box.appendChild(r);
    }
    var hues = [];
    for (var h = 0; h < 360; h += 15) hues.push(h);
    var hr = hues.map(function (hh) {
      return {
        c: hsl2hex(hh, 70, 50), on: COLS.s > 10 && Math.abs(((COLS.h - hh + 540) % 360) - 180) < 8,
        pick: function () { COLS.h = hh; if (COLS.s < 20) COLS.s = 70; if (COLS.l < 8 || COLS.l > 92) COLS.l = 50; colorSet(); }
      };
    });
    hr.push({ c: '#808080', on: COLS.s <= 10, pick: function () { COLS.s = 0; colorSet(); } });
    row('Rang', hr);
    row("To'yinganlik", [0, 25, 50, 75, 100].map(function (ss) {
      return { c: hsl2hex(COLS.h, ss, COLS.l), on: Math.abs(COLS.s - ss) < 13, pick: function () { COLS.s = ss; colorSet(); } };
    }));
    row("Yorug'lik", [6, 14, 24, 36, 50, 64, 76, 88, 96].map(function (ll) {
      return { c: hsl2hex(COLS.h, COLS.s, ll), on: Math.abs(COLS.l - ll) < 7, pick: function () { COLS.l = ll; colorSet(); } };
    }));
  }
  function colorPick(title, cur, done, reset) {
    var rgb = hex2rgb(cur), hsl = rgb2hsl(rgb[0], rgb[1], rgb[2]);
    COLS = { h: hsl[0], s: hsl[1], l: hsl[2], hex: String(cur).toLowerCase() };
    $('colTitle').textContent = title;
    $('colNo').onclick = colorClose;
    $('colYes').onclick = function () { var x = COLS.hex; colorClose(); done(x); };
    $('colReset').onclick = function () { colorClose(); reset(); };
    $('colModal').classList.remove('hidden');
    colorDraw();
  }
  /** Mavzu rangini o'zgartiradi; bog'liq ranglar (yozuv, yumshoq fon) mos ravishda hisoblanadi. */
  function setCol(u, tid, k, hex) {
    var base = themeVars(u, tid), c = u.col[tid] || (u.col[tid] = {});
    c[k] = hex;
    var panel = c.panel || base.panel;
    if (k === 'acc') { c['acc-ink'] = inkFor(hex); c['acc-soft'] = mixHex(panel, hex, 0.22); }
    else if (k === 'top') c['top-ink'] = inkFor(hex);
    else if (k === 'ret') { c['ret-ink'] = inkFor(hex); c['ret-soft'] = mixHex(panel, hex, 0.22); }
    else if (k === 'warn') c['warn-soft'] = mixHex(panel, hex, 0.2);
    else if (k === 'toast') c['toast-ink'] = inkFor(hex);
  }
  /** Qatorni barmoq yoki sichqoncha bilan sudrab joyini almashtirish (tutqich: .ord-h). onDrop(from, to). */
  function dragRows(g, onDrop) {
    var st = null, timer = 0;
    function rows() { return Array.prototype.slice.call(g.children); }
    function hit(x, y) {
      var rs = rows();
      for (var i = 0; i < rs.length; i++) {
        var r = rs[i].getBoundingClientRect();
        if (x >= r.left && x <= r.right && y >= r.top && y <= r.bottom) return i;
      }
      return -1;
    }
    function mark() {
      rows().forEach(function (r, i) { r.classList.toggle('drop', i === st.to && st.to !== st.from); });
    }
    function over() {
      var i = hit(st.x, st.y);
      if (i >= 0) { st.to = i; mark(); }
    }
    function move(e) {
      if (!st) return;
      st.x = e.clientX; st.y = e.clientY;
      over();
      if (e.cancelable) e.preventDefault();
    }
    function scrollTick() {                                 // ro'yxat chetiga yaqin tutilsa, o'zi aylanadi
      if (!st) return;
      var sb = $('setBody'), r = sb.getBoundingClientRect();
      if (st.y < r.top + 70) sb.scrollTop -= 16;
      else if (st.y > r.bottom - 70) sb.scrollTop += 16;
      else return;
      over();
    }
    function end(e) {
      if (!st) return;
      var s = st;
      st = null;
      clearInterval(timer);
      document.removeEventListener('pointermove', move);
      document.removeEventListener('pointerup', end);
      document.removeEventListener('pointercancel', end);
      rows().forEach(function (r) { r.classList.remove('drag', 'drop'); });
      if (e && e.type === 'pointerup' && s.to !== s.from) onDrop(s.from, s.to);
    }
    g.addEventListener('pointerdown', function (e) {
      var h = e.target.closest ? e.target.closest('.ord-h') : null;
      if (!h || st) return;
      var row = h.closest('.ord-row'), i = rows().indexOf(row);
      if (i < 0) return;
      st = { from: i, to: i, x: e.clientX, y: e.clientY };
      row.classList.add('drag');
      document.addEventListener('pointermove', move, { passive: false });
      document.addEventListener('pointerup', end);
      document.addEventListener('pointercancel', end);
      timer = setInterval(scrollTick, 40);
      if (e.cancelable) e.preventDefault();
    });
  }
  /** Tugmalar ro'yxati: har biri uchun tartib (sudrab yoki ▲▼), o'lcham, aniq kenglik (−/+) va ko'rinish. */
  function ctrlCard(u, title, text, list, o) {
    var c = card(title, text);
    c.classList.add('set-span');
    var g = el('div', 'ord-grid');
    c.appendChild(g);
    var items = orderOf(list, u[o.ord]);
    var ids = items.map(function (x) { return x.id; });
    function reorder(from, to) {
      var a = ids.slice(), x = a.splice(from, 1)[0];
      a.splice(to, 0, x);
      u[o.ord] = a;
      saveUi(u, renderSettings);
    }
    items.forEach(function (t, i) {
      var off = o.canHide(t) && o.hidden(u, t);
      var r = el('div', 'ord-row' + (off ? ' off' : ''));
      var top = el('div', 'ord-top');
      var h = el('div', 'ord-h', '☰');
      h.setAttribute('aria-label', "Sudrab joyini almashtirish");
      top.appendChild(h);
      top.appendChild(el('b', '', t.n || t.b));
      if (o.canHide(t)) {
        var tg = el('button', 'tg' + (off ? '' : ' on'));
        tg.appendChild(el('i', '', off ? 'YASHIRIN' : "KO'RINADI"));
        tg.addEventListener('click', function () { o.toggle(u, t); saveUi(u, renderSettings); });
        top.appendChild(tg);
      }
      r.appendChild(top);
      var bar = el('div', 'ord-bar');
      [['▲', -1], ['▼', 1]].forEach(function (m) {
        btn(bar, m[0], 'ord-mv', function () {
          var j = i + m[1];
          if (j >= 0 && j < ids.length) reorder(i, j);
        });
      });
      var cur = o.size(u, t), custom = o.width.custom(u, t);
      [[1, 'Kichik'], [2, "O'rta"], [3, 'Katta']].forEach(function (s) {
        btn(bar, s[1], !custom && cur === s[0] ? 'sz on' : 'sz', function () { o.setSize(u, t, s[0]); saveUi(u, renderSettings); });
      });
      r.appendChild(bar);
      stepRow(r, 'Kenglik', o.width, u, t);
      stepRow(r, 'Balandlik', o.height, u, t);
      var lk = o.key(t), lb = (u.lab || {})[lk] || {}, lmode = lb.m || o.defMode;
      var lm = el('div', 'ord-bar');
      [['both', 'Ikonka+nom'], ['text', 'Nom'], ['icon', 'Ikonka']].forEach(function (m) {
        btn(lm, m[1], lmode === m[0] ? 'sz on' : 'sz', function () {
          setLab(u, lk, { m: m[0] === o.defMode ? '' : m[0] });
          saveUi(u, renderSettings);
        });
      });
      r.appendChild(lm);
      var ln = el('div', 'ord-lab');
      btn(ln, 'Nom: ' + (lb.t || o.defName(t)), 'lb nm', function () {
        openKb('text', 'Tugma nomi', function (txt) { setLab(u, lk, { t: txt.trim().slice(0, 24) }); saveUi(u, renderSettings); });
        S.kb.text = lb.t || ''; renderKb();
      });
      var ib = btn(ln, '', 'lb ic-b', function () {
        pickIcon(lb.i, o.defIcon(t), function (k) { setLab(u, lk, { i: k }); saveUi(u, renderSettings); });
      });
      ib.appendChild(icon(lb.i || o.defIcon(t)));
      btn(ln, 'Asl', 'lb asl' + (lb.t || lb.i || lb.m ? ' on' : ''), function () { setLab(u, lk, { t: '', i: '', m: '' }); saveUi(u, renderSettings); });
      r.appendChild(ln);
      g.appendChild(r);
    });
    dragRows(g, reorder);
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
    var keepScroll = $('setBody').scrollTop;
    $('setBody').textContent = '';
    $('setBody').appendChild(el('div', 'set-cols'));
    var c1 = card('Savdo oynalari soni', 'Bir vaqtda nechta xaridorga savat ochiladi');
    var seg = el('div', 'seg2'); c1.appendChild(seg);
    [1, 2, 3, 4].forEach(function (n) {
      btn(seg, String(n), u.wins === n ? 'on' : '', function () { u.wins = n; saveUi(u, renderSettings); });
    });
    var c2 = card('Chek va ovoz');
    [['autoprint', "To'lovdan keyin chek chiqarish"], ['sound', 'Xatoda ovozli signal'], ['stock', "Ro'yxatda har tovarning qoldig'ini ko'rsatish"],
      ['ptypeShow', "Narx turini yuqorida ochiq ko'rsatish (o'chiq bo'lsa: raqam maydonini 1 soniya bosib turing)"],
      ['chk1c', "Chekni 1C'ning o'zi chiqaradi (kompyuterdagi chek printeri, bazadagi chek)"],
      ['hideLock', "«Blok» tugmasini yashirish"]]
      .forEach(function (x) {
        var b = el('button', 'tg' + (u[x[0]] ? ' on' : ''));
        b.appendChild(el('span', '', x[1])); b.appendChild(el('i', '', u[x[0]] ? 'HA' : "YO'Q"));
        b.addEventListener('click', function () { u[x[0]] = !u[x[0]]; saveUi(u, renderSettings); });
        c2.appendChild(b);
      });
    var ab = el('button', 'tg' + (u.auto ? ' on' : ''));
    ab.appendChild(el('span', '', "Kirishda parol so'ralmasin" + (u.auto ? '' : ' (kassir: ' + S.name + ')')));
    ab.appendChild(el('i', '', u.auto ? 'HA' : "YO'Q"));
    ab.addEventListener('click', function () {
      if (!u.auto && !S.code) { toast("Bir marta parol bilan qayta kiring, keyin yoqing", true); return; }
      u.auto = u.auto ? '' : S.code;
      saveUi(u, renderSettings);
    });
    c2.appendChild(ab);
    btn(c2, 'Chek pastidagi matn: ' + (u.footer || '—'), 'tg', function () {
      openKb('text', 'Chek pastidagi matn', function (t) { u.footer = t.slice(0, 60); saveUi(u, renderSettings); });
      S.kb.text = u.footer || ''; renderKb();
    });
    var cv = card("Ko'rinish va dastur", 'Kompyuter dasturi: versiya ' + (S.pcv || '?') +
      ". Telefondan «hozir yangilash» bosilganda kassada «Dastur yangilanmoqda» yozuvi chiqadi va kassa o'zi qayta ochiladi.");
    btn(cv, 'Yangilanishni hozir tekshirish', 'big muted', function () {
      api('POST', '/api/update', { token: S.token, grant: S.grant }, 20000).then(function (j) {
        toast(j.ok ? "Tekshirilmoqda — yangisi bo'lsa kassa o'zi yangilanadi" : (j.e || 'Xato'), !j.ok);
      }).catch(function (e) { toast(e.message, true); });
    });
    var cp = card('Panel', (S.data && S.data.pnls === 'kassa' ? 'Hozir: tasdiqlangan tavsiya.' : "Hozir: 1C'dagi panel.") +
      " Har hafta eng ko'p sotilgan tovarlardan tavsiya tayyorlanadi: tarozi tovarlari (pistalar, bodomlar, keshu… ketma-ket), " +
      'keyin shtrix kodlilar, keyin boshqalar. Siz tasdiqlaysiz.');
    btn(cp, "Tavsiyani ko'rish" + (S.ps ? ' (yangi)' : ''), 'big primary', function () { openSuggest(false); });
    btn(cp, 'Yangidan hisoblash', 'big muted', function () { openSuggest(true); });
    if (S.data && S.data.pnls === 'kassa') btn(cp, "1C'dagi panelga qaytish", 'big muted', function () {
      ask({ title: "1C'dagi panel", text: "Kassada yana 1C'dagi panel ishlatiladi.", yes: 'Qaytish',
        onYes: function () { return panelApi('reset').then(function () { return refreshData(); }).then(renderSettings); } });
    });
    var ct = card('Mavzu (rejim)', "Savdo turingizga mos tayyor ranglar. Tanlagach, ranglarni pastda alohida sozlash mumkin.");
    ct.classList.add('set-span');
    var tgr = el('div', 'th-grid');
    ct.appendChild(tgr);
    THEMES.forEach(function (t) {
      var tb = el('button', 'th-btn' + (u.theme === t.id ? ' on' : ''));
      var sw = el('div', 'th-sw');
      [t.v.bg, t.v.panel, t.v.acc, t.v.ink].forEach(function (cc) { var i = el('i'); i.style.background = cc; sw.appendChild(i); });
      tb.appendChild(sw);
      tb.appendChild(el('b', '', t.n));
      tb.appendChild(el('small', '', t.s));
      tb.addEventListener('click', function () { u.theme = t.id; saveUi(u, renderSettings); });
      tgr.appendChild(tb);
    });
    var thNow = themeOf(u.theme), eff = themeVars(u), mine = u.col[thNow.id] || {};
    var cr = card('Ranglar: ' + thNow.n, "Rangni bosib o'zgartiring. Asosiy rangni o'zgartirsangiz, yumshoq fon va yozuv rangi o'zi moslanadi (keyin alohida ham sozlash mumkin). Har bir mavzu o'z ranglarini eslab qoladi.");
    cr.classList.add('set-span');
    var cgr = el('div', 'col-grid');
    cr.appendChild(cgr);
    COLN.forEach(function (c) {
      var k = c[0], custom = !!mine[k];
      var it = el('div', 'col-item' + (custom ? ' custom' : ''));
      var pickIt = function () {
        colorPick(c[1], eff[k], function (hex) { setCol(u, thNow.id, k, hex); saveUi(u, renderSettings); },
          function () { if (u.col[thNow.id]) delete u.col[thNow.id][k]; saveUi(u, renderSettings); });
      };
      var sw = el('button', 'col-sw');
      sw.style.background = eff[k];
      sw.setAttribute('aria-label', c[1]);
      sw.addEventListener('click', pickIt);
      it.appendChild(sw);
      it.appendChild(el('span', 'col-n', c[1]));
      it.appendChild(el('b', 'col-h', eff[k].toUpperCase()));
      if (custom) btn(it, 'Asl', 'asl', function () { delete u.col[thNow.id][k]; saveUi(u, renderSettings); });
      cgr.appendChild(it);
    });
    btn(cr, "Shu mavzuning hamma ranglarini asl holiga qaytarish", 'big muted', function () { delete u.col[thNow.id]; saveUi(u, renderSettings); });
    ctrlCard(u, 'Tugmalar', "O'ng tomondagi tugmalar: ☰ ni sudrab yoki ▲▼ bilan joyini almashtiring; o'lchami, nomi va ikonkasi. Sozlamalar tugmasi doim turadi.", FN, {
      ord: 'ford',
      key: function (t) { return 'fn-' + t.id; }, defMode: 'both', defName: function (t) { return t.b; }, defIcon: function (t) { return t.id; },
      height: heightOf('fn-', 60),
      canHide: function (t) { return t.id !== 'settings'; },
      hidden: function (uu, t) { return uu.hide.indexOf(t.id) >= 0; },
      toggle: function (uu, t) { if (uu.hide.indexOf(t.id) >= 0) uu.hide = uu.hide.filter(function (x) { return x !== t.id; }); else uu.hide.push(t.id); },
      size: function (uu, t) { var n = (uu.el['fn-' + t.id] || {}).span || 1; return Math.max(1, Math.min(3, n)); },
      setSize: function (uu, t, n) {
        var e = uu.el['fn-' + t.id] || (uu.el['fn-' + t.id] = {});
        delete e.cols;
        if (n > 1) e.span = n; else delete e.span;
      },
      width: {                                              // qatorning 6 ulushidan nechtasini egallaydi
        label: function (uu, t) { return fnCols(uu, t) + ' / 6'; },
        step: function (uu, t, d) {
          var e = uu.el['fn-' + t.id] || (uu.el['fn-' + t.id] = {});
          e.cols = Math.max(1, Math.min(6, fnCols(uu, t) + d));
        },
        reset: function (uu, t) { var e = uu.el['fn-' + t.id]; if (e) delete e.cols; },
        custom: function (uu, t) { return !!(uu.el['fn-' + t.id] || {}).cols; }
      }
    });
    ctrlCard(u, 'Savat ostidagi tugmalar', "☰ ni sudrab yoki ▲▼ bilan joyini almashtiring; kengligi, balandligi, nomi va ikonkasi. Sig'masa, ikkinchi qatorga o'tadi.", TOOLS, {
      ord: 'tord',
      key: function (t) { return 'tool-' + t.id; }, defMode: 'text', defName: function (t) { return t.d; }, defIcon: function (t) { return t.ic; },
      height: heightOf('tool-', 50),
      canHide: function () { return true; },
      hidden: function (uu, t) { return (uu.thide || []).indexOf(t.id) >= 0; },
      toggle: function (uu, t) {
        var h = uu.thide || (uu.thide = []);
        if (h.indexOf(t.id) >= 0) uu.thide = h.filter(function (x) { return x !== t.id; }); else h.push(t.id);
      },
      size: function (uu, t) { return toolSize(t, uu); },
      setSize: function (uu, t, n) {
        var z = uu.tsz || (uu.tsz = {});
        if (uu.tw) delete uu.tw[t.id];
        if (n === (t.icon ? 1 : 2)) delete z[t.id]; else z[t.id] = n;
      },
      width: {                                              // piksel, 10 px qadam bilan
        label: function (uu, t) { return toolBasis(uu, t) + ' px'; },
        step: function (uu, t, d) {
          var z = uu.tw || (uu.tw = {});
          z[t.id] = Math.max(40, Math.min(400, Math.round((toolBasis(uu, t) + d * 10) / 10) * 10));
        },
        reset: function (uu, t) { if (uu.tw) delete uu.tw[t.id]; },
        custom: function (uu, t) { return !!(uu.tw || {})[t.id]; }
      }
    });
    var cw = card("O'ng panel kengligi", "Raqamlar va tugmalar qismi. Torroq qilsangiz, ro'yxat (savat) kengayadi.");
    stepRow(cw, 'Kenglik', {
      label: function (uu) { return (parseInt((uu.el.right || {}).w, 10) || RIGHT_W) + ' px'; },
      step: function (uu, _t, d) {
        var e = uu.el.right || (uu.el.right = {});
        e.w = Math.max(300, Math.min(700, Math.round(((parseInt(e.w, 10) || RIGHT_W) + d * 10) / 10) * 10));
      },
      reset: function (uu) { if (uu.el.right) delete uu.el.right.w; },
      custom: function (uu) { return !!(uu.el.right || {}).w; }
    }, u, null);
    var c4 = card("Ko'rinishni o'zgartirish", "Istalgan joyning o'lchami, rangi va matn o'lchami. Hamma oynalarga birdek qo'llanadi.");
    btn(c4, 'Sozlashni boshlash', 'big primary', function () { $('setView').classList.add('hidden'); startEdit(); });
    btn(c4, 'Hammasini asl holiga qaytarish', 'big muted', function () {
      ask({ title: 'Asl holiga qaytarish', text: "O'lcham, ranglar, nom/ikonkalar va tugmalar tartibi boshlang'ich holatga qaytadi.", yes: 'Qaytarish',
        onYes: function () { u.el = {}; u.ford = []; u.tord = []; u.tsz = {}; u.tw = {}; u.lab = {}; u.col = {}; return saveUi(u, renderSettings); } });
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
    $('setBody').scrollTop = keepScroll;                 // tugma bosilganda ro'yxat tepaga sakramasin
  }
  // ---------------- panel tavsiyasi ----------------
  function panelApi(op) {
    return api('POST', '/api/panel', { token: S.token, grant: S.grant, op: op }, 120000).then(function (j) {
      if (!j.ok) throw new Error(j.e || 'Xato');
      return j;
    });
  }
  function openSuggest(make) {
    busy(true, make ? "Oxirgi 4 hafta sotuvi tahlil qilinmoqda…" : 'Tavsiya ochilmoqda…');
    panelApi(make ? 'make' : 'get').then(function (j) {
      busy(false);
      if (!j.sug) {
        ask({ title: 'Panel tavsiyasi', text: "Hali tavsiya yo'q. Hozir hisoblansinmi?", yes: 'Hisoblash',
          onYes: function () { openSuggest(true); return Promise.resolve(); } });
        return;
      }
      S.sug = j.sug; S.sugPage = 0;
      $('sugView').classList.remove('hidden');
      renderSuggest();
    }).catch(function (e) { busy(false); if (e.message !== 'login') toast(e.message, true); });
  }
  function renderSuggest() {
    var sug = S.sug;
    var t = $('sugTabs');
    t.textContent = '';
    for (var i = 0; i < 8; i++) {
      var used = sug.pnl.slice(i * 42, i * 42 + 42).some(function (x) { return x[0]; });
      var b = el('button', (i === S.sugPage ? 'on' : '') + (used ? ' has' : ''), String(i + 1));
      b.setAttribute('data-page', i);
      t.appendChild(b);
    }
    var d = new Date((sug.at || 0) * 1000);
    var here = (sug.sec || []).filter(function (m) { return m.n && m.from < S.sugPage * 42 + 42 && m.to > S.sugPage * 42; })
      .map(function (m) { return m.t; });
    $('sugInfo').textContent = (S.sugPage + 1) + '-sahifa: ' + (here.join(', ') || "bo'sh") + ' · oxirgi ' + (sug.days || 28) +
      ' kun sotuvi (' + d.toLocaleDateString('ru-RU') + ') · ' + (sug.sec || []).map(function (m) { return m.t + ' ' + m.n; }).join(', ');
    var grid = $('sugGrid');
    grid.textContent = '';
    for (var k = S.sugPage * 42; k < S.sugPage * 42 + 42; k++) {
      var slot = sug.pnl[k] || ['', ''];
      var g = slot[0] && S.byCode ? S.byCode[slot[0]] : null;
      if (g) grid.appendChild(tile(g));
      else if (slot[0]) { var x = el('div', 'tile'); x.appendChild(el('span', 'tn', slot[1])); grid.appendChild(x); }
      else grid.appendChild(el('div', 'tile empty'));
    }
  }
  function closeSuggest() { $('sugView').classList.add('hidden'); S.sug = null; }

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
  TOOLS.forEach(function (t) { NAMES['tool-' + t.id] = 'Tugma: ' + t.n; });
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
    if (id === 'right') stepper('Kenglik', 'w', node ? Math.round(node.getBoundingClientRect().width) : RIGHT_W, 20, 320, 900, 'px');
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
          delete e.cols;
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
  /** pc 21: narxni o'zgartirish — raqam panelida yangi narxni yozib «Narx» bosiladi (parolsiz). Bo'sh bosilsa — asl narxga. */
  function setPriceSel() {
    if (S.sel < 0) { toast('Avval ro\'yxatdan tovarni tanlang', true); return; }
    var l = W().lines[S.sel];
    if (!S.disp) {
      if (!l.m) { toast("Yangi narxni raqam panelida yozing, keyin «Narx»ni bosing", true); return; }
      var g = S.byCode[l.code];
      l.m = false;
      if (g) l.price = L.priceOf(g, PT(), S.data.rate);
      touchWin(); renderCart(); renderWins();
      toast('Asl narx qaytarildi');
      return;
    }
    var v = L.cents(S.disp);
    S.disp = ''; renderDisplay();
    if (!(v > 0)) { toast("Narx 0 bo'lishi mumkin emas", true); beep(); return; }
    l.price = v; l.m = true;
    touchWin(); renderCart(); renderWins();
    toast(l.name + ': narx ' + money(v));
  }
  /** Miqdor: raqam yozilgan bo'lsa — shu; bo'lmasa tez tanlash tugmalari (tarozi tovari — kg, donali — dona). */
  function chooseQty() {
    if (S.sel < 0) { toast('Avval savatdan qatorni tanlang', true); return; }
    var i = S.sel, l = W().lines[i];
    function apply(m) {
      if (m < W().lines[i].milli) needAdmin('Miqdorni kamaytirish', function () { setQty(i, m); });
      else setQty(i, m);
    }
    if (S.disp) { var v = S.disp; S.disp = ''; renderDisplay(); apply(L.milli(v)); return; }
    var g = S.byCode[l.code];
    var kg = l.w || /^(kg|кг)$/i.test(String((g && g.e) || l.unit || '').trim());
    var opts = kg ? [100, 200, 250, 300, 400, 500, 750, 1000, 1500, 2000, 2500, 3000] : [1, 2, 3, 4, 5, 6, 8, 10, 12, 15, 20, 24].map(function (n) { return n * 1000; });
    ask({
      title: l.name + (kg ? ' — necha kg?' : ' — nechta?'),
      text: "Yoki bekor qilib, raqam panelida yozing va «Miqdor»ni bosing",
      choices: opts.map(function (m) {
        return { label: kg ? qtyText(m) + ' kg' : qtyText(m), on: m === l.milli, pick: function () { apply(m); } };
      })
    });
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
      var res = S.data.g.filter(function (g) { return L.priceOf(g, PT(), S.data.rate) === pc; });
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
    qty: chooseQty,
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
    last: function () { reprint(S.last); },
    report: function () { showReport(''); },
    sales: function () { showSales(''); },
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
    on('ptypeBtn', choosePtype);
    // yashirin rejim: raqam maydonini 1 soniya bosib turish — faqat xodimlar biladi
    var lpTimer = 0;
    var disp = $('display');
    function lpStop() { clearTimeout(lpTimer); lpTimer = 0; }
    disp.addEventListener('pointerdown', function () {
      lpStop();
      lpTimer = setTimeout(function () { lpTimer = 0; if (!S.edit) choosePtype(); }, 900);
    });
    ['pointerup', 'pointerleave', 'pointercancel'].forEach(function (ev) { disp.addEventListener(ev, lpStop); });
    disp.addEventListener('contextmenu', function (e) { e.preventDefault(); });
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
    on('qtyBtn', chooseQty);
    on('priceBtn', setPriceSel);
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
      var code = t.getAttribute('data-code');
      if (S.kb.kind === 'goods') {
        S.recent = [code].concat(S.recent.filter(function (x) { return x !== code; })).slice(0, 12);
        save('k_recent', S.recent);
      }
      closeKb();
      addGoods(S.byCode[code], 1000, false);
    });
    on('kbClose', closeKb);

    on('changeBig', hideChange);
    on('payClose', closePay);
    document.querySelector('.fields').addEventListener('click', function (e) {
      var f = e.target.closest ? e.target.closest('.field') : null;
      if (!f || !S.pay) return;
      S.pay.field = f.getAttribute('data-f'); S.pay.buf = '';
      renderPay();
    });
    $('notes').addEventListener('click', function (e) {
      var v = e.target.closest ? e.target.closest('button[data-note]') : null;
      if (!v || !S.pay) return;
      S.pay.buf = ''; S.pay.field = 'cash';
      S.pay.cash = L.cents(v.getAttribute('data-note'));
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
    on('sugBack', closeSuggest);
    $('sugTabs').addEventListener('click', function (e) {
      var b = e.target.closest ? e.target.closest('button[data-page]') : null;
      if (!b || !S.sug) return;
      S.sugPage = parseInt(b.getAttribute('data-page'), 10);
      renderSuggest();
    });
    on('sugApprove', function () {
      ask({ title: 'Panelni almashtirish', text: "Kassada shu panel ishlatiladi (1C'dagi panelga keyin qaytish mumkin).", yes: 'Tasdiqlash',
        onYes: function () {
          return panelApi('approve').then(function () {
            closeSuggest(); S.ps = false; renderFn();
            return refreshData();
          }).then(function () { if (!hidden('setView')) renderSettings(); toast('Panel yangilandi'); });
        } });
    });
    on('sugMake', function () { closeSuggest(); openSuggest(true); });
    on('sugDismiss', function () {
      panelApi('dismiss').then(function () { closeSuggest(); S.ps = false; renderFn(); if (!hidden('setView')) renderSettings(); })
        .catch(function (e) { if (e.message !== 'login') toast(e.message, true); });
    });
    on('donePrint', function () { if (S.last) printReceipt(S.last); });
    on('doneNew', function () { $('doneModal').classList.add('hidden'); });

    // skaner (klaviatura sifatida). pc 12: QAYSI oyna ochiq bo'lmasin, skaner o'qigan tovar savatga tushadi —
    // panel/qidiruv/chek/ma'lumot oynasi o'zi yopiladi. Skaner belgilarni juda tez (<50 ms) yuboradi va Enter bilan
    // tugatadi — shu bilan qo'lda yozishdan ajratiladi.
    var SC = { buf: '', t0: 0, last: 0, kb: 0, disp: 0, pw: 0, ask: 0 };
    function scanReset() { SC.buf = ''; SC.kb = SC.disp = SC.pw = SC.ask = 0; }
    function stripTail(text, n) { return n > 0 ? String(text).slice(0, Math.max(0, String(text).length - n)) : text; }
    function globalScan(code) {
      // tez kiritilgan belgilar qayerga tushgan bo'lsa — olib tashlanadi
      if (SC.kb && S.kb) { S.kb.text = stripTail(S.kb.text, SC.kb); }
      if (SC.disp) S.disp = stripTail(S.disp, SC.disp);
      if (SC.pw) $('pw').value = stripTail($('pw').value, SC.pw);
      if (SC.ask) $('askPw').value = stripTail($('askPw').value, SC.ask);
      if (!hidden('login') || !S.data) { toast('Avval kassaga kiring', true); beep(); return; }
      if (!hidden('busy') || !hidden('prog')) { toast('Kuting — oldingi amal tugamoqda', true); beep(); return; }
      if (S.edit) { toast("Ko'rinish sozlanmoqda — avval «Saqlash» yoki «Bekor qilish»", true); beep(); return; }
      ['doneModal', 'infoModal', 'panelView', 'setView', 'sugView'].forEach(function (id) { $(id).classList.add('hidden'); });
      if (S.sug) S.sug = null;
      if (S.kb) closeKb();
      if (!hidden('askModal')) closeAsk();
      if (!hidden('payModal')) closePay();
      S.disp = '';
      enterCode(code);
    }
    document.addEventListener('keydown', function (e) {
      var now = Date.now();
      if (e.key && e.key.length === 1) {
        if (now - SC.last > 80) { scanReset(); SC.t0 = now; }
        SC.buf += e.key; SC.last = now;
      } else if (e.key === 'Enter') {
        var n = SC.buf.length;
        var fast = n >= 4 && now - SC.last < 120 && (now - SC.t0) <= n * 50 + 80;
        var code = SC.buf;
        if (fast) { e.preventDefault(); e.stopPropagation(); globalScan(code); scanReset(); return; }
        scanReset();
      } else if (e.key !== 'Shift') scanReset();

      if (!hidden('login')) {
        if (e.key === 'Enter') doLogin();
        else if (/^[0-9]$/.test(e.key)) { $('pw').value = ($('pw').value + e.key).slice(0, 20); SC.pw++; }
        else if (e.key === 'Backspace') $('pw').value = $('pw').value.slice(0, -1);
        return;
      }
      if (S.kb) {
        if (e.key.length === 1) { S.kb.text = (S.kb.text + e.key.toLowerCase()).slice(0, 60); SC.kb++; }
        else if (e.key === 'Backspace') S.kb.text = S.kb.text.slice(0, -1);
        else return;
        $('kbText').textContent = S.kb.text;
        renderKbResults();
        e.preventDefault();
        return;
      }
      if (!hidden('askModal')) { if (e.key.length === 1 && document.activeElement === $('askPw')) SC.ask++; return; }
      if (!hidden('payModal') || !hidden('setView') || !hidden('sugView') || S.edit) return;
      if (!hidden('doneModal')) $('doneModal').classList.add('hidden');
      if (!hidden('infoModal')) $('infoModal').classList.add('hidden');
      if (!hidden('panelView')) $('panelView').classList.add('hidden');
      if (e.key === 'Enter') { e.preventDefault(); enterCode(S.disp); return; }
      if (e.key === 'Backspace') { S.disp = S.disp.slice(0, -1); renderDisplay(); return; }
      if (e.key.length === 1 && /[0-9A-Za-z.\-]/.test(e.key)) { S.disp = (S.disp + e.key).slice(0, 40); SC.disp++; renderDisplay(); e.preventDefault(); }
    }, true);
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
  if (S.token) enter(); else autoLogin().catch(function () { showLogin(''); });
})();
