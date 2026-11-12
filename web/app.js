(() => {
  'use strict';

  const $ = (s, root = document) => root.querySelector(s);
  const $$ = (s, root = document) => Array.from(root.querySelectorAll(s));

  // Pembuat elemen kecil: el('div', {class:'x', onclick: fn}, anak1, anak2)
  const el = (tag, props = {}, ...kids) => {
    const n = document.createElement(tag);
    for (const [k, v] of Object.entries(props)) {
      if (v === false || v == null) continue;
      if (k === 'class') n.className = v;
      else if (k === 'text') n.textContent = v;
      else if (k.startsWith('on')) n.addEventListener(k.slice(2), v);
      else n.setAttribute(k, v === true ? '' : v);
    }
    for (const kid of kids.flat()) if (kid !== false && kid != null) n.append(kid);
    return n;
  };

  const JSON_HEADERS = { 'Content-Type': 'application/json' };
  const deviceKind = /Android|iPhone|iPad|iPod|Mobile/i.test(navigator.userAgent) ? 'hp' : 'pc';
  let isOnPc = ['localhost', '127.0.0.1', '[::1]'].includes(location.hostname);
  const enc = encodeURIComponent;

  let loggedIn = false;
  let timer = null;
  let maxMB = 2048;
  let urls = [];
  let folders = [];
  let rootInfo = { count: 0, size: 0, modified: 0 };
  let files = [];
  let clips = [];
  let route = { type: 'home', name: '' };
  let foldersSig = '';
  let filesSig = '';
  let clipsSig = '';
  let pcName = '';
  let remoteUrls = [];
  let storageDir = '';
  let android = { available: false, version: '' };
  let welcomeDue = false;
  let remoteName = '';
  let via = 'lan';
  let online = true;
  let devInfo = { online: 0, devices: [] };

  // ---------- Util ----------
  const fmtSize = (b) => {
    if (b < 1024) return b + ' B';
    const units = ['KB', 'MB', 'GB', 'TB'];
    let i = -1;
    do { b /= 1024; i++; } while (b >= 1024 && i < units.length - 1);
    return b.toFixed(b >= 100 ? 0 : 1) + ' ' + units[i];
  };

  const fmtTime = (ts) => {
    const d = Date.now() / 1000 - ts;
    if (d < 45) return 'baru saja';
    if (d < 3600) return Math.round(d / 60) + ' mnt lalu';
    if (d < 86400) return Math.round(d / 3600) + ' jam lalu';
    return new Date(ts * 1000).toLocaleString('id-ID', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
  };

  const extOf = (name) => {
    const m = /\.([A-Za-z0-9]{1,5})$/.exec(name);
    return m ? m[1].toUpperCase() : 'FILE';
  };

  const stamp = () => {
    const d = new Date();
    const p = (n) => String(n).padStart(2, '0');
    return `${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}-${p(d.getHours())}${p(d.getMinutes())}${p(d.getSeconds())}`;
  };

  const store = {
    get(k) { try { return localStorage.getItem(k) || ''; } catch { return ''; } },
    set(k, v) { try { localStorage.setItem(k, v); } catch { /* abaikan */ } },
  };

  // Perkiraan pembersihan nama folder di sisi HP. Server tetap yang menentukan.
  // Satu nama folder (tanpa /), sama dengan aturan server
  const cleanPart = (s) => s
    .replace(/[<>:"/\\|?*\u0000-\u001f]/g, '_')
    .replace(/\s+/g, ' ')
    .trim()
    .replace(/^[. ]+|[. ]+$/g, '')
    .slice(0, 100);
  // Jalur folder bertingkat: "A/B/C" (boleh pakai \), bagian kosong dibuang
  const cleanFolder = (s) => s.split(/[\/\\]/).map(cleanPart).filter(Boolean).join('/');
  const MAX_DEPTH = 3;
  const depthOf = (p) => (p ? p.split('/').length : 0);
  const labelOf = (p) => p.split('/').pop();
  const parentOf = (p) => (p.includes('/') ? p.slice(0, p.lastIndexOf('/')) : '');
  const pathLabel = (p) => (p ? p.split('/').join(' › ') : 'Tanpa folder');
  const encPath = (p) => p.split('/').map(encodeURIComponent).join('/');

  // Apakah a dan b hanya berbeda satu huruf (tambah/kurang/ganti)?
  function withinOneEdit(a, b) {
    a = a.toLowerCase();
    b = b.toLowerCase();
    if (a === b) return false;
    if (Math.abs(a.length - b.length) > 1) return false;
    let i = 0;
    while (i < a.length && i < b.length && a[i] === b[i]) i++;
    if (a.length === b.length) return a.slice(i + 1) === b.slice(i + 1);
    const [s, l] = a.length < b.length ? [a, b] : [b, a];
    return s.slice(i) === l.slice(i + 1);
  }

  const findFolder = (name) => folders.find((f) => f.name.toLowerCase() === name.toLowerCase());

  function toast(msg, type = '') {
    const t = el('div', { class: 'toast ' + type, text: msg });
    $('#toasts').append(t);
    setTimeout(() => t.classList.add('out'), 2600);
    setTimeout(() => t.remove(), 3000);
  }

  // Clipboard API butuh HTTPS; di http://192.168.x.x harus pakai cara lama.
  async function copyText(text) {
    try {
      if (navigator.clipboard && window.isSecureContext) {
        await navigator.clipboard.writeText(text);
        return true;
      }
    } catch { /* lanjut ke cara lama */ }
    const ta = el('textarea', { readonly: true, style: 'position:fixed;top:0;left:0;opacity:0' });
    ta.value = text;
    document.body.append(ta);
    ta.focus();
    ta.select();
    ta.setSelectionRange(0, text.length);
    let ok = false;
    try { ok = document.execCommand('copy'); } catch { /* abaikan */ }
    ta.remove();
    return ok;
  }

  function linkify(text) {
    const frag = document.createDocumentFragment();
    const re = /https?:\/\/[^\s<>"']+/g;
    let last = 0;
    let m;
    while ((m = re.exec(text))) {
      if (m.index > last) frag.append(text.slice(last, m.index));
      frag.append(el('a', { href: m[0], target: '_blank', rel: 'noopener noreferrer', text: m[0] }));
      last = m.index + m[0].length;
    }
    if (last < text.length) frag.append(text.slice(last));
    return frag;
  }

  // ---------- API ----------
  async function api(path, opts = {}) {
    const r = await fetch(path, { credentials: 'same-origin', ...opts });
    if (r.status === 401 && path !== '/api/login') {
      showLogin();
      const e = new Error('Sesi berakhir, masukkan PIN lagi.');
      e.status = 401;
      throw e;
    }
    if (!r.ok) {
      let msg = 'Terjadi kesalahan (' + r.status + ')';
      let data = {};
      try {
        data = await r.json();
        if (typeof data.detail === 'string') msg = data.detail;
      } catch { /* abaikan */ }
      const e = new Error(msg);
      e.status = r.status;
      e.data = data;
      throw e;
    }
    return r.json();
  }

  function setOnline(ok) {
    online = ok;
    $('#dot').classList.toggle('off', !ok);
    renderStatus();
  }

  const pcLabel = () => (isOnPc ? 'PC ini' : (pcName || 'PC'));

  // Baris status di bawah judul: koneksi + jumlah perangkat
  function renderStatus() {
    const t = $('#statusText');
    if (!online) { t.textContent = 'Terputus dari ' + pcLabel() + ' · cek Wi-Fi'; return; }
    const n = devInfo.online || 0;
    // Jalur sambungan: Wi-Fi/hotspot yang sama (tanpa internet) atau Tailscale (lewat internet)
    t.textContent = isOnPc ? `${n} perangkat terhubung` : `Terhubung ke ${pcLabel()}` + (via === 'tailscale' ? ' · lewat internet (Tailscale)' : ' · Wi-Fi lokal, tanpa internet');
  }

  function setTitle() {
    const where = isOnPc ? 'PC ini' : pcLabel();
    const page = route.type === 'send' ? 'Kirim ke ' + where : route.type === 'more' ? 'Hubungkan' : route.type === 'welcome' ? 'Selamat datang' : 'File di ' + where;
    $('#pageTitle').textContent = page;
    document.title = page + ' · DropBridge';
  }

  // ---------- Layar ----------
  function showLogin() {
    loggedIn = false;
    clearInterval(timer);
    $('#app').hidden = true;
    $('#login').hidden = false;
    $('#pin').focus();
  }

  function showApp() {
    loggedIn = true;
    if (queue.length) setTimeout(runQueue, 0);
    $('#login').hidden = true;
    $('#app').hidden = false;
    $('#loginError').textContent = '';
    foldersSig = filesSig = clipsSig = '';
    applyRoute();
    refresh();
    checkName();
    clearInterval(timer);
    timer = setInterval(() => { if (!document.hidden) refresh(); }, 4000);
  }

  let qrIdx = 0;
  let photoMaxKB = 512;

  function renderAddr() {
    for (const box of $$('[data-addr]')) {
      box.replaceChildren();
      if (!urls.length || (!isOnPc && box.closest('#login'))) { box.hidden = true; continue; }
      box.hidden = false;
      const img = el('img', {
        class: 'addr-qr', width: 168, height: 168,
        alt: 'Kode QR untuk membuka DropBridge dari HP',
        src: '/api/qr?pin=1&i=' + qrIdx + '&t=' + Date.now(),
      });
      const qrBox = el('div', { class: 'addr-qrbox' }, img,
        el('div', { class: 'addr-qrcap', text: isOnPc ? 'Scan dengan kamera HP: langsung terbuka dan masuk, tanpa mengetik PIN.' : 'Teman scan QR ini, lalu masukkan PIN dari layar PC.' }));
      img.addEventListener('error', () => { qrBox.hidden = true; });
      box.append(
        el('div', { class: 'addr-wrap' },
          qrBox,
          el('div', { class: 'addr-side' },
            el('div', { class: 'addr-title', text: 'Atau ketik alamat ini di browser HP (ketuk untuk menyalin dan memilih QR):' }),
            el('div', { class: 'addr-list' },
              urls.map((u, idx) => el('button', {
                type: 'button', text: u, 'aria-pressed': idx === qrIdx ? 'true' : 'false',
                onclick: async () => { qrIdx = idx; renderAddr(); toast((await copyText(u)) ? 'Alamat disalin' : 'Gagal menyalin'); },
              }))))));
    }
  }

  // Kartu "Dari luar jaringan" (Tailscale): QR alamat 100.x, atau langkah memasang
  function renderRemote() {
    const has = remoteUrls.length > 0;
    $('#remoteCard').hidden = !has && !isOnPc;
    $('#remoteOn').hidden = !has;
    $('#remoteOff').hidden = has;
    if (!has) return;
    $('#remoteQr').src = '/api/qr?remote=1&pin=1&t=' + Date.now();
    const list = remoteName ? [...remoteUrls, `http://${remoteName}:${location.port || 8000}`] : remoteUrls;
    $('#remoteList').replaceChildren(...list.map((u) => el('button', {
      type: 'button', text: u,
      onclick: async () => toast((await copyText(u)) ? 'Alamat disalin' : 'Gagal menyalin'),
    })));
  }

  async function loadInfo() {
    try {
      const r = await fetch('/api/info');
      const d = await r.json();
      urls = d.urls || [];
      remoteUrls = d.remote_urls || [];
      remoteName = d.remote_name || '';
      via = d.via || 'lan';
      storageDir = d.storage_dir || '';
      android = d.android || android;
      welcomeDue = !!d.welcome;
      $('#notifyRow').hidden = d.notify == null;
      if (d.notify != null) $('#notifyChk').checked = !!d.notify;
      renderStore();
      renderApk();
      // Versi terpasang yang baru dipasang/diperbarui: tampilkan Selamat datang sekali
      if (welcomeDue && isOnPc && !location.hash) location.hash = '#/selamat-datang';
      $('#verLine').textContent = 'DropBridge PC versi ' + (d.version || '?') + (pcName ? ' · ' + pcName : '')
        + (d.storage_dir ? '\nDisimpan di: ' + d.storage_dir : '');
      if (d.this_pc) isOnPc = true;
      if (d.pc_name) pcName = d.pc_name;
      maxMB = d.max_upload_mb || maxMB;
      if (typeof d.photo_max_kb === 'number') photoMaxKB = d.photo_max_kb;
      $('#shrinkKB').textContent = photoMaxKB;
      $('#shrinkRow').hidden = photoMaxKB <= 0;
      renderShrink();
      renderAddr();
      renderRemote();
      setTitle();
      renderStatus();
    } catch { /* tidak kritis */ }
  }

  // ---------- Kotak nama folder (saran + petunjuk) ----------
  function makeCombo(input, list, hintEl, { onInput } = {}) {
    let active = -1;
    let items = [];

    function close() { list.hidden = true; active = -1; }

    function choose(name) {
      input.value = name;
      close();
      updateHint();
      if (onInput) onInput();
      input.focus();
    }

    function openList() {
      const q = input.value.trim().toLowerCase();
      items = folders.filter((f) => !q || f.name.toLowerCase().includes(q)).slice(0, 8);
      if (!items.length || (items.length === 1 && items[0].name.toLowerCase() === q)) { close(); return; }
      list.replaceChildren(...items.map((f, i) => el('li', {},
        el('button', {
          type: 'button',
          tabindex: '-1',
          onmousedown: (e) => e.preventDefault(), // jangan hilangkan fokus dari input
          onclick: () => choose(f.name),
        }, el('span', { text: pathLabel(f.name) }), el('span', { class: 'cnt', text: f.count + ' file' })))));
      active = -1;
      list.hidden = false;
    }

    function mark() {
      $$('button', list).forEach((b, i) => { b.style.background = i === active ? 'var(--bg)' : ''; });
    }

    function updateHint() {
      hintEl.replaceChildren();
      hintEl.className = 'hint';
      const raw = input.value;
      if (!raw.trim()) return;
      const cleaned = cleanFolder(raw);
      if (!cleaned) { hintEl.classList.add('warn'); hintEl.textContent = 'Nama folder tidak valid.'; return; }
      if (depthOf(cleaned) > MAX_DEPTH) { hintEl.classList.add('warn'); hintEl.textContent = `Paling banyak ${MAX_DEPTH} tingkat folder, mis. Kontainer/Pos/Tanggal.`; return; }
      const parts = [];
      const exist = findFolder(cleaned);
      if (cleaned !== raw.trim() && cleaned !== raw.trim().replace(/\s*[\/\\]\s*/g, '/')) parts.push(el('div', { class: 'sub', text: 'Nama disesuaikan menjadi: ' + pathLabel(cleaned) }));
      if (exist) {
        hintEl.classList.add('ok');
        parts.unshift(el('div', { text: `Folder sudah ada · ${exist.count} file` }));
      } else {
        const par = parentOf(cleaned);
        parts.unshift(el('div', { text: par
          ? `Subfolder baru di ${pathLabel(findFolder(par) ? findFolder(par).name : par)}, dibuat saat file dikirim.`
          : 'Folder baru, dibuat saat file dikirim.' }));
        if (cleaned.length >= 4) {
          const sim = folders.filter((f) => withinOneEdit(f.name, cleaned)).slice(0, 3);
          if (sim.length) {
            hintEl.classList.add('warn');
            parts.push(el('div', {},
              'Mirip dengan: ',
              sim.map((f) => el('button', { type: 'button', class: 'link', text: pathLabel(f.name), onclick: () => choose(f.name) })),
              ' — salah ketik?'));
          }
        }
      }
      hintEl.append(...parts);
    }

    input.addEventListener('input', () => { openList(); updateHint(); if (onInput) onInput(); });
    input.addEventListener('focus', openList);
    input.addEventListener('blur', () => setTimeout(close, 120));
    input.addEventListener('keydown', (e) => {
      if (e.key === 'ArrowDown' && !list.hidden) { e.preventDefault(); active = Math.min(items.length - 1, active + 1); mark(); }
      else if (e.key === 'ArrowUp' && !list.hidden) { e.preventDefault(); active = Math.max(0, active - 1); mark(); }
      else if (e.key === 'Enter' && !list.hidden && active >= 0) { e.preventDefault(); choose(items[active].name); }
      else if (e.key === 'Escape' && !list.hidden) { e.stopPropagation(); close(); }
    });
    return { updateHint, close };
  }

  const sendCombo = makeCombo($('#folderInput'), $('#suggest'), $('#folderHint'));
  const moveCombo = makeCombo($('#moveInput'), $('#moveSuggest'), $('#moveHint'));

  function setTarget(name) {
    $('#folderInput').value = name;
    sendCombo.updateHint();
  }

  // ---------- Rute ----------
  function parseHash() {
    const h = location.hash;
    if (h === '#/root') return { type: 'root', name: '' };
    if (h.startsWith('#/f/')) {
      try { return { type: 'folder', name: decodeURIComponent(h.slice(4)) }; } catch { /* jatuh ke beranda */ }
    }
    if (h === '#/folders') return { type: 'home', name: '' };
    if (h === '#/hubungkan' || h === '#/lainnya') return { type: 'more', name: '' };
    if (h === '#/selamat-datang') return { type: 'welcome', name: '' };
    return { type: 'send', name: '' };
  }

  const inFolder = () => route.type === 'folder' || route.type === 'root';

  function applyRoute() {
    route = parseHash();
    files = [];
    filesSig = '';
    const home = route.type === 'home';
    const page = route.type === 'send' ? 'send' : route.type === 'more' ? 'more' : route.type === 'welcome' ? 'welcome' : 'folders';
    $('#pageWelcome').hidden = page !== 'welcome';
    if (page === 'welcome') loadStorage();
    $('#pageSend').hidden = page !== 'send';
    $('#pageFolders').hidden = page !== 'folders';
    $('#pageMore').hidden = page !== 'more';
    for (const a of $$('.mainnav a')) {
      if (a.dataset.nav === page) a.setAttribute('aria-current', 'page'); else a.removeAttribute('aria-current');
    }
    $('#homeView').hidden = !home;
    $('#folderView').hidden = !inFolder();
    closeMenu();
    setTitle();
    window.scrollTo(0, 0);
    if (route.type === 'folder') {
      setTarget(route.name);
      store.set('db_target', route.name);
    }
    if (inFolder()) {
      const root = route.type === 'root';
      $('#fvTitle').textContent = root ? 'Tanpa folder' : labelOf(route.name);
      $('#fvMeta').textContent = '';
      $('#fvMenu').hidden = root;
      $('#gNew').hidden = root || depthOf(route.name) >= MAX_DEPTH;
      $('.back', $('#folderView')).href = root || !parentOf(route.name) ? '#/folders' : '#/f/' + enc(parentOf(route.name));
      renderCrumbs();
      renderSubs();
      $('#grid').replaceChildren();
      loadFiles().catch(() => {});
    } else if (home) {
      renderHome();
    } else if (route.type === 'more') {
      $('#qrOff').hidden = !!urls.length;
      renderDevices();
      loadInfo();
    } else if (route.type === 'send') {
      loadRecent().catch(() => {});
    }
  }

  window.addEventListener('hashchange', () => { if (loggedIn) applyRoute(); });

  const curFolder = () => (route.type === 'folder' ? route.name : '');

  // ---------- Daftar folder ----------
  async function loadFolders() {
    const d = await api('/api/folders');
    const sig = JSON.stringify(d) + Math.floor(Date.now() / 60000);
    folders = d.folders;
    rootInfo = d.root || rootInfo;
    if (sig !== foldersSig) {
      foldersSig = sig;
      sendCombo.updateHint();
      renderChips();
      if (route.type === 'home') renderHome();
      if (route.type === 'folder') { renderSubs(); renderMeta(); }
      if ($('#pickDlg').open) renderPick();
    }
  }


  const RECENT_CHIPS = 3;

  function renderChips() {
    const box = $('#recentChips');
    box.replaceChildren();
    const recent = folders.slice(0, RECENT_CHIPS);
    box.hidden = !recent.length;
    if (!recent.length) return;
    box.append(el('span', { class: 'chips-label', text: 'Terakhir:' }));
    for (const f of recent) {
      box.append(el('button', {
        type: 'button', class: 'chip', text: pathLabel(f.name), title: pathLabel(f.name),
        onclick: () => { setTarget(f.name); store.set('db_target', f.name); },
      }));
    }
  }

  // ---------- Ikon kecil ----------
  const svg = (html) => { const t = document.createElement('template'); t.innerHTML = html.trim(); return t.content.firstChild; };
  const IC = {
    dots: '<svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><circle cx="12" cy="5" r="2"/><circle cx="12" cy="12" r="2"/><circle cx="12" cy="19" r="2"/></svg>',
    folder: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round" aria-hidden="true"><path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/></svg>',
    up: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 19V5"/><path d="M5 12l7-7 7 7"/></svg>',
    down: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 4v12"/><path d="M6 11l6 6 6-6"/><path d="M4 20h16"/></svg>',
    move: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><path d="M10 13h6M13 10l3 3-3 3"/></svg>',
    edit: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 20h4L19 9l-4-4L4 16z"/></svg>',
    trash: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"/></svg>',
    share: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="18" cy="5" r="2.5"/><circle cx="6" cy="12" r="2.5"/><circle cx="18" cy="19" r="2.5"/><path d="M8.2 10.8l7.6-4.4M8.2 13.2l7.6 4.4"/></svg>',
    check: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M5 12l5 5 9-10"/></svg>',
    newf: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><path d="M12 10.5v5M9.5 13h5"/></svg>',
    chev: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M9 6l6 6-6 6"/></svg>',
    play: '<svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M8 5v14l11-7z"/></svg>',
  };

  // ---------- Menu titik tiga ----------
  let menuFrom = null;
  function closeMenu() {
    const m = $('#menu');
    if (m.hidden) return;
    m.hidden = true;
    m.replaceChildren();
    if (menuFrom) { menuFrom.setAttribute('aria-expanded', 'false'); menuFrom.focus({ preventScroll: true }); }
    menuFrom = null;
  }
  // items: [{ label, icon, onclick, danger, href, download }]
  function openMenu(btn, items) {
    if (menuFrom === btn) { closeMenu(); return; }
    closeMenu();
    const m = $('#menu');
    for (const it of items) {
      const node = el(it.href ? 'a' : 'button', {
        role: 'menuitem', class: 'mi' + (it.danger ? ' danger' : ''),
        type: it.href ? null : 'button', href: it.href || null, download: it.download || null,
        onclick: () => { closeMenu(); if (it.onclick) it.onclick(); },
      }, svg(it.icon), el('span', { text: it.label }));
      m.append(node);
    }
    m.hidden = false;
    menuFrom = btn;
    btn.setAttribute('aria-expanded', 'true');
    const r = btn.getBoundingClientRect();
    const w = m.offsetWidth, h = m.offsetHeight;
    let left = Math.min(window.innerWidth - w - 8, r.right - w);
    let top = r.bottom + 4;
    if (top + h > window.innerHeight - 8) top = Math.max(8, r.top - h - 4);
    m.style.left = Math.max(8, left) + 'px';
    m.style.top = top + 'px';
    const first = $('.mi', m);
    if (first) first.focus({ preventScroll: true });
  }
  document.addEventListener('pointerdown', (e) => {
    if (!$('#menu').hidden && !e.target.closest('#menu') && !(menuFrom && menuFrom.contains(e.target))) closeMenu();
  });
  document.addEventListener('keydown', (e) => {
    if ($('#menu').hidden) return;
    if (e.key === 'Escape') { e.preventDefault(); closeMenu(); return; }
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      e.preventDefault();
      const all = $$('.mi', $('#menu'));
      const i = all.indexOf(document.activeElement);
      all[(i + (e.key === 'ArrowDown' ? 1 : all.length - 1)) % all.length].focus();
    }
  });
  window.addEventListener('resize', closeMenu);
  window.addEventListener('scroll', closeMenu, { passive: true });

  // ---------- Urutkan ----------
  const SORTS = {
    folder: [['new', 'Terbaru diubah'], ['old', 'Terlama diubah'], ['az', 'Nama A–Z'], ['za', 'Nama Z–A'], ['big', 'Ukuran terbesar']],
    file: [['new', 'Terbaru diambil'], ['old', 'Terlama diambil'], ['az', 'Nama A–Z'], ['za', 'Nama Z–A'], ['big', 'Ukuran terbesar']],
  };
  const pref = {
    folderSort: store.get('db_fsort') || 'new',
    fileSort: store.get('db_gsort') || 'new',
    folderView: store.get('db_allview') === 'grid' ? 'grid' : 'list',
    fileView: store.get('db_gview') === 'list' ? 'list' : 'grid', // isi folder: foto paling enak dilihat sebagai kotak
  };
  const coll = new Intl.Collator('id', { numeric: true, sensitivity: 'base' });
  function sortBy(arr, how) {
    const a = arr.slice();
    if (how === 'az') a.sort((x, y) => coll.compare(x.name, y.name));
    else if (how === 'za') a.sort((x, y) => coll.compare(y.name, x.name));
    else if (how === 'big') a.sort((x, y) => y.size - x.size);
    else if (how === 'old') a.sort((x, y) => x.modified - y.modified || coll.compare(x.name, y.name));
    else a.sort((x, y) => y.modified - x.modified || coll.compare(x.name, y.name));
    return a;
  }
  const sortName = (kind, key) => (SORTS[kind].find((o) => o[0] === key) || SORTS[kind][0])[1];

  let sortKind = 'folder';
  function openSort(kind) {
    sortKind = kind;
    const cur = kind === 'folder' ? pref.folderSort : pref.fileSort;
    $('#sortOpts').replaceChildren(...SORTS[kind].map(([key, label]) => el('button', {
      type: 'button', role: 'radio', class: 'sort-opt', 'aria-checked': key === cur ? 'true' : 'false',
      onclick: () => {
        if (sortKind === 'folder') { pref.folderSort = key; store.set('db_fsort', key); renderHome(); renderSubs(); }
        else { pref.fileSort = key; store.set('db_gsort', key); renderGrid(); }
        syncTools();
        $('#sortDlg').close();
      },
    }, el('span', { text: label }), key === cur ? svg(IC.check) : '')));
    $('#sortDlg').showModal();
    const on = $('.sort-opt[aria-checked="true"]', $('#sortOpts'));
    if (on) on.focus();
  }
  $('#sortDlg').addEventListener('click', (e) => { if (e.target.id === 'sortDlg') e.target.close(); });
  $('#fSort').addEventListener('click', () => openSort('folder'));
  $('#gSort').addEventListener('click', () => openSort('file'));

  function syncTools() {
    $('.sort-label', $('#fSort')).textContent = sortName('folder', pref.folderSort);
    $('.sort-label', $('#gSort')).textContent = sortName('file', pref.fileSort);
    $('#fgList').setAttribute('aria-pressed', pref.folderView === 'list');
    $('#fgGrid').setAttribute('aria-pressed', pref.folderView === 'grid');
    $('#gvList').setAttribute('aria-pressed', pref.fileView === 'list');
    $('#gvGrid').setAttribute('aria-pressed', pref.fileView === 'grid');
    $('#folderList').classList.toggle('grid-view', pref.folderView === 'grid');
    $('#grid').classList.toggle('list-view', pref.fileView === 'list');
  }
  const setFolderView = (v) => { pref.folderView = v; store.set('db_allview', v); syncTools(); renderHome(); renderSubs(); };
  const setFileView = (v) => { pref.fileView = v; store.set('db_gview', v); syncTools(); renderGrid(); };
  $('#fgList').addEventListener('click', () => setFolderView('list'));
  $('#fgGrid').addEventListener('click', () => setFolderView('grid'));
  $('#gvList').addEventListener('click', () => setFileView('list'));
  $('#gvGrid').addEventListener('click', () => setFileView('grid'));
  syncTools();

  // ---------- Daftar folder ----------
  // Kirim langsung ke folder tertentu: pilih file, lalu lihat kemajuannya di halaman Kirim.
  let pickFor = null;
  function sendTo(folder) {
    pickFor = folder;
    $('#picker2').click();
  }
  $('#picker2').addEventListener('change', (e) => {
    const list = Array.from(e.target.files || []);
    e.target.value = '';
    if (!list.length || pickFor === null) return;
    const folder = pickFor;
    pickFor = null;
    if (folder) { setTarget(folder); store.set('db_target', folder); }
    location.hash = '#/';
    enqueue(list, folder);
  });

  function folderMenu(f, btn) {
    const items = [{ label: 'Kirim ke sini', icon: IC.up, onclick: () => sendTo(f.name) }];
    if (!f.root && depthOf(f.name) < MAX_DEPTH) items.push({ label: 'Folder baru di dalamnya', icon: IC.newf, onclick: () => openNew(f.name) });
    if (!f.root) items.push({ label: 'Unduh semua (ZIP)', icon: IC.down, href: '/api/folders/' + encPath(f.name) + '/zip', download: labelOf(f.name) + '.zip' });
    openMenu(btn, items);
  }

  function folderItem(f, fullPath = false) {
    const href = f.root ? '#/root' : '#/f/' + enc(f.name);
    const sub = (f.subs ? `${f.subs} folder · ` : '') + `${f.count} file · ${fmtSize(f.size)}` + (f.modified && !f.root ? ' · ' + fmtTime(f.modified) : '');
    const shown = f.root ? f.name : fullPath ? pathLabel(f.name) : labelOf(f.name);
    const dots = el('button', {
      type: 'button', class: 'dots', 'aria-label': 'Pilihan untuk ' + f.name, 'aria-haspopup': 'menu', 'aria-expanded': 'false',
      onclick: (e) => folderMenu(f, e.currentTarget),
    }, svg(IC.dots));
    return el('li', { class: 'frow' + (f.root ? ' root' : '') },
      el('a', { class: 'frow-main', href },
        el('span', { class: 'fic' }, svg(IC.folder)),
        el('span', { class: 'meta' },
          el('span', { class: 'name', text: shown, title: pathLabel(f.name) }),
          el('span', { class: 'sub', text: sub }))),
      dots);
  }

  function renderHome() {
    const list = $('#folderList');
    const q = $('#fSearch').value.trim().toLowerCase();
    list.replaceChildren();
    // Tanpa pencarian: folder tingkat atas saja. Dengan pencarian: semua tingkat, tampil jalur lengkap.
    const shown = sortBy(folders.filter((x) => (q ? x.name.toLowerCase().includes(q) : !x.parent)), pref.folderSort);
    for (const f of shown) list.append(folderItem(f, !!q));
    if (rootInfo.count > 0 && (!q || 'tanpa folder'.includes(q))) {
      list.append(folderItem({ name: 'Tanpa folder', root: true, count: rootInfo.count, size: rootInfo.size, modified: rootInfo.modified }));
    }
    if (!list.children.length) {
      list.append(el('li', { class: 'empty', text: q ? 'Tidak ada folder yang cocok.' : 'Belum ada folder. Kirim file ke sebuah nama folder dan folder itu akan dibuat otomatis.' }));
    }
  }
  $('#fSearch').addEventListener('input', renderHome);

  // ---------- Isi folder ----------
  async function loadFiles() {
    if (!inFolder()) return;
    const asked = route;
    let d;
    try {
      d = await api('/api/files?folder=' + enc(curFolder()));
    } catch (e) {
      if (e.status === 404 && asked === route) {
        toast('Folder tidak ditemukan', 'err');
        location.hash = '#/folders';
        return;
      }
      throw e;
    }
    if (asked !== route) return; // pengguna sudah pindah halaman
    const sig = JSON.stringify(d.files) + Math.floor(Date.now() / 60000);
    files = d.files;
    if (sig !== filesSig) { filesSig = sig; renderGrid(); }
  }

  const childrenOf = (p) => folders.filter((f) => (f.parent || '') === p);

  function renderCrumbs() {
    const nav = $('#fvCrumbs');
    nav.replaceChildren();
    if (route.type !== 'folder') return;
    const parts = route.name.split('/');
    nav.append(el('a', { href: '#/folders', text: 'File' }));
    parts.forEach((x, i) => {
      nav.append(el('span', { 'aria-hidden': 'true', text: '›' }));
      const p = parts.slice(0, i + 1).join('/');
      nav.append(i === parts.length - 1 ? el('span', { class: 'here', text: x, 'aria-current': 'page' }) : el('a', { href: '#/f/' + enc(p), text: x }));
    });
  }

  function renderSubs() {
    const subs = route.type === 'folder' ? sortBy(childrenOf(route.name), pref.folderSort) : [];
    $('#subWrap').hidden = !subs.length;
    $('#subList').classList.toggle('grid-view', pref.folderView === 'grid');
    $('#subList').replaceChildren(...subs.map((f) => folderItem(f)));
  }

  function renderMeta() {
    const subs = route.type === 'folder' ? childrenOf(route.name).length : 0;
    const total = files.reduce((n, f) => n + f.size, 0);
    $('#fvMeta').textContent = (subs ? `${subs} folder · ` : '') + `${files.length} file · ${fmtSize(total)}`;
  }

  const fileUrl = (f, folder) => '/api/files/' + enc(f.name) + '?folder=' + enc(folder);

  function fileMenu(f, btn) {
    const folder = curFolder();
    const items = [{ label: 'Unduh', icon: IC.down, href: fileUrl(f, folder), download: f.name }];
    if (navigator.canShare && window.isSecureContext) {
      items.push({ label: 'Bagikan', icon: IC.share, onclick: () => shareFile(f) });
    }
    items.push({ label: 'Pindah ke folder lain', icon: IC.move, onclick: () => openMove(f) });
    items.push({ label: 'Hapus', icon: IC.trash, danger: true, onclick: () => removeFile(f) });
    openMenu(btn, items);
  }

  async function shareFile(f) {
    try {
      const r = await fetch(fileUrl(f, curFolder()), { credentials: 'same-origin' });
      const file = new File([await r.blob()], f.name, { type: f.mime || 'application/octet-stream' });
      if (navigator.canShare({ files: [file] })) await navigator.share({ files: [file] });
      else toast('HP ini tidak bisa membagikan file dari browser', 'err');
    } catch (e) { if (e && e.name !== 'AbortError') toast('Gagal membagikan', 'err'); }
  }

  function renderGrid() {
    const grid = $('#grid');
    grid.replaceChildren();
    renderMeta();
    $('#fileLabel').hidden = !files.length;
    if (!files.length) {
      if ($('#subWrap').hidden) grid.append(el('div', { class: 'empty', text: 'Folder ini kosong. Ketuk Kirim ke folder ini, atau buat subfolder dengan ikon folder+.' }));
      return;
    }
    const folder = curFolder();
    const asList = pref.fileView === 'list';
    for (const f of sortBy(files, pref.fileSort)) {
      const url = fileUrl(f, folder);
      const badge = () => el('span', { class: 'ext big', 'data-kind': f.kind, text: extOf(f.name) });
      const pic = el('span', { class: 'pic' });
      if (f.thumb) {
        const img = el('img', { class: 'thumb', loading: 'lazy', alt: '', src: `/api/files/${enc(f.name)}/thumb?folder=${enc(folder)}` });
        img.addEventListener('error', () => img.replaceWith(badge()), { once: true });
        pic.append(img);
      } else {
        pic.append(badge());
      }
      if (f.kind === 'video') pic.append(el('span', { class: 'play' }, svg(IC.play)));
      const open = () => (f.previewable ? openPreview(f) : (location.href = url));
      const main = el('button', { type: 'button', class: 'tile-main', title: f.name, 'aria-label': f.name, onclick: open }, pic);
      if (asList) {
        main.append(el('span', { class: 'meta' },
          el('span', { class: 'name', text: f.name }),
          el('span', { class: 'sub', text: fmtSize(f.size) + ' · ' + fmtTime(f.modified) })));
      } else if (!f.thumb) {
        main.append(el('span', { class: 'tile-name', text: f.name }));
      }
      const dots = el('button', {
        type: 'button', class: 'dots', 'aria-label': 'Pilihan untuk ' + f.name, 'aria-haspopup': 'menu', 'aria-expanded': 'false',
        onclick: (e) => fileMenu(f, e.currentTarget),
      }, svg(IC.dots));
      grid.append(el('div', { class: 'tile' + (f.thumb ? ' has-thumb' : '') }, main, dots));
    }
  }

  async function removeFile(f) {
    if (!confirm(`Hapus "${f.name}" dari PC? Tindakan ini tidak bisa dibatalkan.`)) return;
    try {
      await api('/api/files/' + enc(f.name) + '?folder=' + enc(curFolder()), { method: 'DELETE' });
      toast('File dihapus');
      await Promise.all([loadFiles(), loadFolders()]);
    } catch (e) { toast(e.message, 'err'); }
  }

  // ---------- Aksi folder ----------
  $('#fvSend').addEventListener('click', () => sendTo(curFolder()));
  $('#fvMenu').addEventListener('click', (e) => {
    if (route.type !== 'folder') return;
    openMenu(e.currentTarget, [
      ...(storageDir ? [{ label: 'Buka di File Explorer', icon: IC.folder, onclick: () => openExplorer(route.name) }] : []),
      { label: 'Unduh semua (ZIP)', icon: IC.down, href: '/api/folders/' + encPath(route.name) + '/zip', download: labelOf(route.name) + '.zip' },
      { label: 'Ganti nama', icon: IC.edit, onclick: renameFolder },
      { label: 'Hapus folder', icon: IC.trash, danger: true, onclick: deleteFolder },
    ]);
  });

  async function renameFolder() {
    const old = route.name;
    const next = prompt('Nama baru untuk folder ini:', labelOf(old));
    if (next == null || !next.trim() || next.trim() === labelOf(old)) return;
    try {
      const d = await api('/api/folders/' + encPath(old), { method: 'PATCH', headers: JSON_HEADERS, body: JSON.stringify({ name: next }) });
      if ($('#folderInput').value === old) { setTarget(d.name); store.set('db_target', d.name); }
      toast('Nama folder diganti');
      location.hash = '#/f/' + enc(d.name);
      loadFolders().catch(() => {});
    } catch (e) { toast(e.message, 'err'); }
  }

  async function deleteFolder() {
    const name = route.name;
    if (childrenOf(name).length) { toast('Folder ini berisi subfolder. Hapus subfoldernya dulu.', 'err'); return; }
    let count = files.length;
    for (let attempt = 0; attempt < 3; attempt++) {
      const ok = confirm(`Hapus folder "${pathLabel(name)}" beserta ${count} file di dalamnya dari PC?\n\nTindakan ini permanen dan tidak bisa dibatalkan.`);
      if (!ok) return;
      try {
        await api('/api/folders/' + encPath(name) + '?expect=' + count, { method: 'DELETE' });
        if ($('#folderInput').value === name) setTarget('');
        if (store.get('db_target') === name) store.set('db_target', '');
        toast('Folder dihapus');
        location.hash = parentOf(name) ? '#/f/' + enc(parentOf(name)) : '#/folders';
        loadFolders().catch(() => {});
        return;
      } catch (e) {
        if (e.status === 409 && e.data && typeof e.data.count === 'number') {
          count = e.data.count; // isi folder berubah sejak dibuka; minta konfirmasi lagi
          toast('Isi folder berubah, cek lagi jumlah filenya', 'err');
          continue;
        }
        toast(e.message, 'err');
        return;
      }
    }
  }

  // ---------- Pratinjau ----------
  let pvList = [];
  let pvIndex = 0;

  function openPreview(f) {
    pvList = files.filter((x) => x.previewable);
    pvIndex = Math.max(0, pvList.findIndex((x) => x.name === f.name));
    showPreview();
    if (!$('#preview').open) $('#preview').showModal();
  }

  function showPreview() {
    const f = pvList[pvIndex];
    if (!f) return;
    const src = `/api/files/${enc(f.name)}?folder=${enc(curFolder())}&inline=1`;
    let node;
    if (f.kind === 'image') node = el('img', { src, alt: f.name });
    else if (f.kind === 'video') node = el('video', { src, controls: true, playsinline: true });
    else if (f.kind === 'audio') node = el('audio', { src, controls: true });
    else node = el('iframe', { src, title: f.name });
    $('#previewTitle').textContent = `${f.name}  (${pvIndex + 1}/${pvList.length})`;
    $('#pvDl').href = fileUrl(f, curFolder());
    $('#pvDl').setAttribute('download', f.name);
    $('#previewBody').replaceChildren(node);
    $('#pvPrev').disabled = pvIndex === 0;
    $('#pvNext').disabled = pvIndex >= pvList.length - 1;
  }

  function stepPreview(d) {
    const n = pvIndex + d;
    if (n < 0 || n >= pvList.length) return;
    pvIndex = n;
    showPreview();
  }

  $('#pvPrev').addEventListener('click', () => stepPreview(-1));
  $('#pvNext').addEventListener('click', () => stepPreview(1));
  $('#previewClose').addEventListener('click', () => $('#preview').close());
  $('#preview').addEventListener('click', (e) => { if (e.target.id === 'preview') e.target.close(); });
  $('#preview').addEventListener('close', () => $('#previewBody').replaceChildren()); // hentikan video/audio
  $('#preview').addEventListener('keydown', (e) => {
    if (e.key === 'ArrowLeft') stepPreview(-1);
    else if (e.key === 'ArrowRight') stepPreview(1);
  });
  let touchX = null;
  $('#previewBody').addEventListener('touchstart', (e) => { touchX = e.touches.length === 1 ? e.touches[0].clientX : null; }, { passive: true });
  $('#previewBody').addEventListener('touchend', (e) => {
    if (touchX == null) return;
    const dx = e.changedTouches[0].clientX - touchX;
    touchX = null;
    if (Math.abs(dx) > 60) stepPreview(dx < 0 ? 1 : -1);
  });

  // ---------- Pindahkan file ----------
  let moving = null;

  function openMove(f) {
    moving = f;
    $('#moveName').textContent = f.name;
    $('#moveInput').value = '';
    moveCombo.updateHint();
    $('#moveDlg').showModal();
    $('#moveInput').focus();
  }

  $('#moveCancel').addEventListener('click', () => $('#moveDlg').close());
  $('#moveDlg').addEventListener('click', (e) => { if (e.target.id === 'moveDlg') e.target.close(); });
  $('#moveForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const to = $('#moveInput').value.trim();
    if (!to || !moving) { toast('Isi nama folder tujuan', 'err'); return; }
    if (cleanFolder(to).toLowerCase() === curFolder().toLowerCase()) { toast('File sudah ada di folder itu', 'err'); return; }
    try {
      const d = await api('/api/files/move', {
        method: 'POST', headers: JSON_HEADERS,
        body: JSON.stringify({ folder: curFolder(), name: moving.name, to }),
      });
      $('#moveDlg').close();
      toast('Dipindahkan ke ' + (d.folder || to));
      await Promise.all([loadFiles(), loadFolders()]);
    } catch (err) { toast(err.message, 'err'); }
  });

  // ---------- Unggah ----------
  const queue = [];
  let uploading = false;
  // Ringkasan kiriman yang sedang berjalan ("Mengirim 3 dari 8 ke ...")
  const batch = { total: 0, done: 0, folder: '' };

  function targetFolder() {
    const raw = $('#folderInput').value;
    if (!raw.trim()) return null;
    return cleanFolder(raw) || null;
  }

  // Mengembalikan false bila nama folder belum diisi.
  let pendingRoot = null;
  $('#rootFill').addEventListener('click', () => { pendingRoot = null; $('#rootDlg').close(); $('#folderInput').focus(); });
  $('#rootGo').addEventListener('click', () => {
    const arr = pendingRoot;
    pendingRoot = null;
    $('#rootDlg').close();
    if (arr) enqueue(arr, '');
  });

  // folderOverride: '' = tanpa folder (langsung di folder shared).
  function enqueue(list, folderOverride) {
    const arr = Array.from(list);
    if (!arr.length) return false;
    const folder = folderOverride === undefined ? targetFolder() : folderOverride;
    if (folder === null) {
      pendingRoot = arr;
      $('#rootDlg').showModal();
      return false;
    }
    const where = pathLabel(folder);
    for (const file of arr) {
      // Folder tujuan selalu terlihat di baris, juga saat mengirim atau gagal
      const dest = el('span', { class: 'up-dest', title: 'Folder tujuan: ' + where });
      dest.innerHTML = '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linejoin="round" aria-hidden="true"><path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/></svg>';
      dest.append(document.createTextNode(where));
      const row = el('li', { class: 'up' },
        el('div', { class: 'up-name', text: file.name, title: file.name }),
        dest,
        el('div', { class: 'up-state', text: 'Menunggu giliran' }),
        el('div', { class: 'bar' }, el('i')));
      $('#uploads').prepend(row);
      $('#upCard').hidden = false;
      if (file.size > maxMB * 1024 * 1024) {
        fail(row, `Terlalu besar (maks ${maxMB} MB)`);
        continue;
      }
      queue.push({ file, row, folder, shrink: $('#shrinkPhotos').checked });
      batch.total++;
    }
    syncUpCard();
    runQueue();
    return true;
  }

  // ---------- Kecilkan foto > batas (default 512 KB) ----------
  const SHRINKABLE = /^image\/(jpeg|png|webp)$/;

  async function shrinkImage(file, maxBytes) {
    let bmp;
    try { bmp = await createImageBitmap(file, { imageOrientation: 'from-image' }); }
    catch { return file; } // format tidak bisa dibaca browser: kirim apa adanya
    const encode = (w, h, q) => new Promise((res) => {
      const c = document.createElement('canvas');
      c.width = w; c.height = h;
      const g = c.getContext('2d');
      g.fillStyle = '#fff'; g.fillRect(0, 0, w, h); // PNG transparan -> latar putih
      g.drawImage(bmp, 0, 0, w, h);
      c.toBlob((b) => res(b), 'image/jpeg', q);
    });
    let scale = Math.min(1, 2048 / Math.max(bmp.width, bmp.height));
    let best = null;
    for (let round = 0; round < 12; round++) {
      const w = Math.max(1, Math.round(bmp.width * scale));
      const h = Math.max(1, Math.round(bmp.height * scale));
      for (const q of [0.85, 0.75, 0.65]) {
        const b = await encode(w, h, q);
        if (!b) continue;
        best = b;
        if (b.size <= maxBytes) { round = 99; break; }
      }
      if (round === 99) break;
      if (Math.max(w, h) <= 640) break;
      scale *= 0.85;
    }
    bmp.close && bmp.close();
    if (!best || best.size >= file.size) return file;
    const name = /\.jpe?g$/i.test(file.name) ? file.name : file.name.replace(/\.[^.]+$/, '') + '.jpg';
    return new File([best], name, { type: 'image/jpeg', lastModified: file.lastModified || Date.now() });
  }

  function fail(row, msg, retry) {
    row.classList.add('err');
    const close = () => { row.remove(); syncUpCard(); };
    if (!retry) {
      $('.up-state', row).textContent = msg + ' (ketuk untuk menutup)';
      row.addEventListener('click', close, { once: true });
      return;
    }
    $('.up-state', row).textContent = msg;
    const actions = el('div', { class: 'up-actions' },
      el('button', {
        type: 'button', class: 'btn small primary', text: 'Coba lagi',
        onclick: (e) => {
          e.stopPropagation();
          actions.remove();
          row.classList.remove('err');
          $('.bar i', row).style.width = '0%';
          $('.up-state', row).textContent = 'Menunggu giliran...';
          retry();
        },
      }),
      el('button', { type: 'button', class: 'btn ghost small', text: 'Tutup', onclick: (e) => { e.stopPropagation(); close(); } }));
    row.append(actions);
  }

  async function runQueue() {
    if (uploading || !loggedIn) return;
    uploading = true;
    $('#upNote').hidden = false;
    while (queue.length) {
      const job = queue.shift();
      batch.folder = job.folder;
      renderSum();
      const r = await uploadOne(job);
      if (r === 'ok' || r === 'fail') { batch.done++; renderSum(); }
      if (r === 'auth') {
        // Sesi habis: simpan sisa antrean, minta login, lalu lanjut sendiri setelah masuk.
        queue.unshift(job);
        for (const j of queue) $('.up-state', j.row).textContent = 'Menunggu login ulang, lalu lanjut sendiri';
        uploading = false;
        $('#upNote').hidden = true;
        showLogin();
        $('#loginError').textContent = 'Sesi berakhir. Masuk lagi, pengiriman yang tertunda lanjut sendiri.';
        return;
      }
      if (r && r.retry) fail(job.row, r.retry, () => { queue.push(job); runQueue(); });
    }
    uploading = false;
    $('#upNote').hidden = true;
    renderSum();
    if (!queue.length) { batch.total = batch.done = 0; }
    if (loggedIn) { refresh(); loadRecent().catch(() => {}); }
  }

  function renderSum() {
    const busy = uploading || queue.length;
    const n = Math.min(batch.total, batch.done + (busy ? 1 : 0));
    $('#upSum').textContent = busy && batch.total
      ? `Mengirim ${n} dari ${batch.total} ke ${batch.folder || 'Tanpa folder'}`
      : (batch.total ? `${batch.done} file selesai` : '');
    $('#upSumBar').style.width = batch.total ? Math.round((batch.done / batch.total) * 100) + '%' : '0%';
    $('.sum-bar').hidden = !batch.total || batch.total < 2;
  }

  // Peringatan bila halaman ditutup saat masih mengirim.
  window.addEventListener('beforeunload', (e) => {
    if (uploading || queue.length) { e.preventDefault(); e.returnValue = ''; }
  });

  function fmtSpeed(bps) {
    return bps >= 1048576 ? (bps / 1048576).toFixed(1).replace('.', ',') + ' MB/s' : Math.round(bps / 1024) + ' KB/s';
  }
  function fmtDur(sec) {
    sec = Math.max(0, Math.round(sec));
    return sec < 60 ? sec + ' dtk' : Math.floor(sec / 60) + ' mnt ' + (sec % 60) + ' dtk';
  }

  async function uploadOne(job) {
    let { file } = job;
    const { row } = job;
    if (photoMaxKB > 0 && job.shrink && SHRINKABLE.test(file.type) && file.size > photoMaxKB * 1024) {
      $('.up-state', row).textContent = `Mengecilkan foto (${fmtSize(file.size)}) agar di bawah ${photoMaxKB} KB...`;
      try {
        const small = await shrinkImage(file, photoMaxKB * 1024);
        if (small !== file) { file = small; $('.up-name', row).textContent = small.name; }
      } catch { /* kirim asli */ }
      job.file = file; // kalau perlu dikirim ulang, tidak dikecilkan dua kali
      job.shrink = false;
    }
    return sendFile(file, row, job.folder);
  }

  function sendFile(file, row, folder) {
    return new Promise((resolve) => {
      const state = $('.up-state', row);
      const bar = $('.bar i', row);
      const xhr = new XMLHttpRequest();
      // Jalur cepat: isi file dikirim mentah (tanpa multipart), langsung ditulis ke disk oleh server.
      let q = '/api/files/upload-raw?folder=' + enc(folder) + '&name=' + enc(file.name);
      if (file.lastModified) q += '&last_modified=' + file.lastModified;
      xhr.open('POST', q);
      xhr.setRequestHeader('Content-Type', 'application/octet-stream');
      const t0 = performance.now();
      let tSent = 0, lastT = 0, lastB = 0, speed = 0;
      xhr.upload.onload = () => { tSent = performance.now(); state.textContent = 'Terkirim, menunggu PC menyimpan...'; };
      xhr.upload.onprogress = (e) => {
        if (!e.lengthComputable || tSent) return;
        const pct = Math.round((e.loaded / e.total) * 100);
        bar.style.width = pct + '%';
        const nowT = performance.now();
        if (lastT && nowT - lastT >= 500) {
          const inst = ((e.loaded - lastB) * 1000) / (nowT - lastT);
          speed = speed ? speed * 0.6 + inst * 0.4 : inst;
          lastT = nowT; lastB = e.loaded;
        } else if (!lastT) { lastT = nowT; lastB = e.loaded; }
        const eta = speed > 1024 ? ` · sisa ${fmtDur((e.total - e.loaded) / speed)}` : '';
        state.textContent = `${pct}% · ${fmtSize(e.loaded)} / ${fmtSize(e.total)}` + (speed ? ` · ${fmtSpeed(speed)}` : '') + eta;
      };
      xhr.onload = () => {
        let result = 'ok';
        if (xhr.status >= 200 && xhr.status < 300) {
          let d = {};
          try { d = JSON.parse(xhr.responseText); } catch { /* abaikan */ }
          const real = d.folder || folder;
          if (d.errors && d.errors.length && !(d.saved && d.saved.length)) {
            fail(row, String(d.errors[0]));
            result = 'fail';
          } else {
            row.classList.add('done');
            const now = performance.now();
            const sec = (ms) => (ms / 1000).toFixed(1) + ' dtk';
            const dupe = d.saved && d.saved[0] && d.saved[0].duplicate;
            if (dupe) state.textContent = `Sudah ada di ${real || 'Tanpa folder'} (${d.saved[0].name}), tidak disimpan ulang`;
            else state.textContent = `Selesai · ${fmtSize(file.size)} · ${sec(now - t0)}` + (tSent && tSent - t0 > 300 ? ' · ' + fmtSpeed(file.size * 1000 / (tSent - t0)) : '');
            setTimeout(() => { row.remove(); syncUpCard(); }, 15000);
            if (real) store.set('db_target', real);
            if (real && $('#folderInput').value.trim() && cleanFolder($('#folderInput').value).toLowerCase() === folder.toLowerCase()) {
              $('#folderInput').value = real; // pakai nama final dari server
            }
          }
          if (loggedIn) loadFolders().then(() => { sendCombo.updateHint(); }).catch(() => {});
          if (loggedIn && inFolder()) loadFiles().catch(() => {});
        } else if (xhr.status === 401) {
          result = 'auth';
        } else {
          let msg = 'Gagal (' + xhr.status + ')';
          try { const dt = JSON.parse(xhr.responseText).detail; if (typeof dt === 'string') msg = dt; } catch { /* abaikan */ }
          if (xhr.status >= 500) result = { retry: msg };
          else { fail(row, msg); result = 'fail'; }
        }
        resolve(result);
      };
      xhr.onerror = () => resolve({ retry: 'Gagal terhubung (koneksi putus?)' });
      xhr.send(file);
    });
  }

  function pickWith(input) {
    if (!targetFolder()) {
      toast('Isi nama folder tujuan dulu', 'err');
      $('#folderInput').focus();
      return;
    }
    input.click();
  }
  $('#pick').addEventListener('click', () => pickWith($('#picker')));
  $('#pick2').addEventListener('click', () => pickWith($('#picker')));
  // Foto langsung: kamera HP terbuka lewat browser, hasilnya langsung dikirim.
  $('#snap').addEventListener('click', () => pickWith($('#camera')));
  $('#picker').addEventListener('change', (e) => { enqueue(e.target.files); e.target.value = ''; });
  $('#camera').addEventListener('change', (e) => {
    const f = e.target.files && e.target.files[0];
    e.target.value = '';
    if (!f) return;
    const ext = (f.type.split('/')[1] || 'jpg').replace('jpeg', 'jpg');
    enqueue([/^image\.(jpe?g|png)$/i.test(f.name) || !f.name ? new File([f], `foto-${stamp()}.${ext}`, { type: f.type, lastModified: f.lastModified }) : f]);
  });
  $('#folderInput').addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && $('#suggest').hidden) { e.preventDefault(); $('#pick').click(); }
  });

  // Seret dan lepas ke seluruh halaman; tujuannya folder yang tertulis di kotak
  let dragDepth = 0;
  const hasFiles = (e) => e.dataTransfer && Array.from(e.dataTransfer.types || []).includes('Files');
  window.addEventListener('dragenter', (e) => { if (!hasFiles(e)) return; e.preventDefault(); dragDepth++; $('#overlay').hidden = !loggedIn; });
  window.addEventListener('dragover', (e) => { if (hasFiles(e)) e.preventDefault(); });
  window.addEventListener('dragleave', (e) => {
    if (!hasFiles(e)) return;
    dragDepth = Math.max(0, dragDepth - 1);
    if (!dragDepth) $('#overlay').hidden = true;
  });
  window.addEventListener('drop', (e) => {
    if (!hasFiles(e)) return;
    e.preventDefault();
    dragDepth = 0;
    $('#overlay').hidden = true;
    if (loggedIn) enqueue(e.dataTransfer.files);
  });

  // Tempel gambar/file dari clipboard (Ctrl+V)
  window.addEventListener('paste', (e) => {
    if (!loggedIn) return;
    const pasted = Array.from((e.clipboardData && e.clipboardData.files) || []);
    if (!pasted.length) return;
    e.preventDefault();
    enqueue(pasted.map((f) => {
      if (f.name && f.name !== 'image.png') return f;
      const ext = (f.type.split('/')[1] || 'bin').replace('jpeg', 'jpg');
      return new File([f], `tempel-${stamp()}.${ext}`, { type: f.type });
    }));
  });

  // ---------- Teks ----------
  async function loadClips() {
    const d = await api('/api/clips');
    const sig = JSON.stringify(d.clips) + Math.floor(Date.now() / 60000);
    clips = d.clips;
    if (sig !== clipsSig) { clipsSig = sig; renderClips(); }
  }

  function renderClips() {
    const list = $('#clipList');
    list.replaceChildren();
    if (!clips.length) {
      list.append(el('li', { class: 'empty', text: 'Belum ada teks. Kirim dari PC, lalu salin di HP (atau sebaliknya).' }));
      return;
    }
    for (const c of clips) {
      list.append(
        el('li', { class: 'clip' },
          el('div', { class: 'clip-text' }, linkify(c.text)),
          el('div', { class: 'clip-foot' },
            el('span', { text: (c.source === 'hp' ? 'dari HP' : c.source === 'pc' ? 'dari PC' : 'dikirim') + ' · ' + fmtTime(c.ts) }),
            el('span', { class: 'actions' },
              el('button', { class: 'btn small primary', type: 'button', text: 'Salin', onclick: async () => toast((await copyText(c.text)) ? 'Tersalin' : 'Gagal menyalin, tahan lalu pilih teksnya') }),
              el('button', { class: 'btn small danger', type: 'button', text: 'Hapus', onclick: () => removeClip(c) }))))
      );
    }
  }

  async function removeClip(c) {
    try { await api('/api/clips/' + c.id, { method: 'DELETE' }); await loadClips(); }
    catch (e) { toast(e.message, 'err'); }
  }

  $('#clipForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const ta = $('#clipText');
    const text = ta.value.trim();
    if (!text) return;
    try {
      await api('/api/clips', { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify({ text, source: deviceKind }) });
      ta.value = '';
      await loadClips();
    } catch (err) { toast(err.message, 'err'); }
  });
  $('#clipText').addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) { e.preventDefault(); $('#clipSend').click(); }
  });

  // ---------- Mode kirim: File / Teks ----------
  function syncUpCard() {
    const has = !!$('#uploads').children.length;
    $('#upCard').hidden = !has;
    if (!has && !uploading && !queue.length) { batch.total = batch.done = 0; }
    renderSum();
  }
  function setMode(m) {
    $('#sendFile').hidden = m !== 'file';
    $('#sendText').hidden = m !== 'text';
    if (m === 'text') $('#clipText').focus();
  }
  $('#modeFile').addEventListener('click', () => setMode('file'));
  $('#modeText').addEventListener('click', () => setMode('text'));

  // ---------- Folder baru (ikon folder+) ----------
  let newParent = '';
  let newDone = null;
  // after(path): dipanggil setelah folder dibuat; bawaan = buka folder baru itu
  function openNew(parent, after) {
    newParent = parent || '';
    newDone = after || ((p) => { location.hash = '#/f/' + enc(p); });
    $('#newWhere').textContent = 'di ' + (newParent ? 'File › ' + pathLabel(newParent) : 'File (tingkat atas)');
    $('#newName').value = '';
    $('#newErr').textContent = '';
    $('#newDlg').showModal();
    $('#newName').focus();
  }
  $('#fNew').addEventListener('click', () => openNew(''));
  $('#gNew').addEventListener('click', () => openNew(curFolder()));
  $('#newCancel').addEventListener('click', () => $('#newDlg').close());
  $('#newForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const nm = cleanPart($('#newName').value);
    if (!nm) { $('#newErr').textContent = 'Isi nama folder.'; return; }
    if ($('#newName').value.includes('/') || $('#newName').value.includes('\\')) { $('#newErr').textContent = 'Nama folder tidak boleh berisi / atau \\.'; return; }
    const full = newParent ? newParent + '/' + nm : nm;
    try {
      const d = await api('/api/folders', { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify({ name: full }) });
      $('#newDlg').close();
      toast(d.created ? 'Folder dibuat: ' + pathLabel(d.name) : 'Folder sudah ada: ' + pathLabel(d.name));
      await loadFolders().catch(() => {});
      if (newDone) newDone(d.name);
    } catch (err) { $('#newErr').textContent = err.message; }
  });

  // ---------- Panel Pilih folder (halaman Kirim) ----------
  let pickPath = '';
  function renderPick() {
    const nav = $('#pickCrumbs');
    nav.replaceChildren(el('button', { type: 'button', class: 'link', text: 'File', onclick: () => { pickPath = ''; renderPick(); } }));
    if (pickPath) {
      const parts = pickPath.split('/');
      parts.forEach((x, i) => {
        nav.append(el('span', { 'aria-hidden': 'true', text: ' › ' }));
        const p = parts.slice(0, i + 1).join('/');
        nav.append(i === parts.length - 1 ? el('b', { text: x }) : el('button', { type: 'button', class: 'link', text: x, onclick: () => { pickPath = p; renderPick(); } }));
      });
    }
    const kids = sortBy(childrenOf(pickPath), 'az');
    $('#pickList').replaceChildren(...(kids.length ? kids.map((f) => el('li', {},
      el('button', { type: 'button', class: 'pick-row', onclick: () => { pickPath = f.name; renderPick(); } },
        el('span', { class: 'fic' }, svg(IC.folder)),
        el('span', { class: 'meta' },
          el('span', { class: 'name', text: labelOf(f.name) }),
          el('span', { class: 'sub', text: (f.subs ? `${f.subs} folder · ` : '') + `${f.count} file` })),
        svg(IC.chev)))) : [el('li', { class: 'empty', text: pickPath ? 'Belum ada subfolder di sini.' : 'Belum ada folder.' })]));
    $('#pickUp').hidden = !pickPath;
    $('#pickOk').hidden = !pickPath;
    $('#pickOk').textContent = 'Pilih ' + (pickPath ? labelOf(pickPath) : '');
    $('#pickNew').hidden = depthOf(pickPath) >= MAX_DEPTH;
  }
  $('#pickBtn').addEventListener('click', () => {
    const cur = cleanFolder($('#folderInput').value || '');
    // Mulai dari folder tujuan sekarang (bila ada), supaya gampang pilih subfoldernya
    pickPath = cur && findFolder(cur) ? findFolder(cur).name : (cur && findFolder(parentOf(cur)) ? findFolder(parentOf(cur)).name : '');
    renderPick();
    $('#pickDlg').showModal();
  });
  $('#pickUp').addEventListener('click', () => { pickPath = parentOf(pickPath); renderPick(); });
  $('#pickClose').addEventListener('click', () => $('#pickDlg').close());
  $('#pickDlg').addEventListener('click', (e) => { if (e.target.id === 'pickDlg') e.target.close(); });
  $('#pickOk').addEventListener('click', () => {
    if (!pickPath) return;
    setTarget(pickPath);
    store.set('db_target', pickPath);
    $('#pickDlg').close();
  });
  $('#pickNew').addEventListener('click', () => {
    openNew(pickPath, (p) => {
      setTarget(p);
      store.set('db_target', p);
      $('#pickDlg').close();
    });
  });

  // ---------- Khusus laptop/PC ini: folder penyimpanan, Explorer, Selamat datang ----------
  // Ringkas jalur panjang: "C:\Users\x\OneDrive - Kantor\DropBridge" -> "OneDrive - Kantor › DropBridge"
  function shortPath(p) {
    const parts = p.split(/[\\/]/).filter(Boolean);
    const od = parts.findIndex((x) => /^OneDrive/i.test(x));
    return (od >= 0 ? parts.slice(od) : parts.slice(-2)).join(' › ');
  }
  const isCloud = (p) => /OneDrive|Google Drive|Dropbox/i.test(p);
  const ICON_CLOUD = '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round" aria-hidden="true"><path d="M7 18h10a4 4 0 0 0 .5-7.97A6 6 0 0 0 6.1 9.5 4.25 4.25 0 0 0 7 18z"/></svg>';

  function renderStore() {
    $('#storeLine').hidden = !storageDir;
    if (!storageDir) return;
    $('#storePath').textContent = shortPath(storageDir);
    $('#storeChip').title = storageDir;
    $('#storeChip').classList.toggle('cloud', isCloud(storageDir));
    $('#storeIc').replaceChildren(svg(isCloud(storageDir) ? ICON_CLOUD : IC.folder));
  }

  async function loadStorage() {
    try {
      const d = await api('/api/storage');
      $('#welStore').textContent = shortPath(d.path);
      $('#welStore').title = d.path;
      $('#welStoreIc').replaceChildren(svg(isCloud(d.path) ? ICON_CLOUD : IC.folder));
      $('#welStoreIc').classList.toggle('cloud', isCloud(d.path));
      $('#welStoreSub').textContent = (d.imported_from ? 'Diimpor dari pengaturan lama · ' : '') + d.files + ' file' + (isCloud(d.path) ? ' · tersinkron ke cloud' : '');
    } catch { /* bukan dari PC ini */ }
  }

  let storeTarget = '';
  async function changeStorage() {
    toast('Pilih folder di jendela yang terbuka…');
    let r;
    try { r = await api('/api/storage/pick', { method: 'POST' }); } catch (err) { toast(err.message, 'err'); return; }
    if (!r.path) return;
    if (r.error) { toast(r.error, 'err'); return; }
    storeTarget = r.path;
    if (r.files > 0) {
      $('#storeAsk').textContent = `Simpan kiriman baru di ${shortPath(r.path)}. Pindahkan juga ${r.files} file yang sudah ada ke sana? File yang sudah ada di tujuan tidak ditimpa.`;
      $('#storeDlg').showModal();
    } else {
      doSwitch(false);
    }
  }
  async function doSwitch(move) {
    $('#storeDlg').close();
    try {
      const rep = await api('/api/storage', { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify({ path: storeTarget, move }) });
      let msg = 'Disimpan di ' + shortPath(rep.path);
      if (move) msg += ` · ${rep.moved} file dipindah` + (rep.same ? `, ${rep.same} kembar dilewati` : '') + (rep.clash ? `, ${rep.clash} dibiarkan (nama sama, isi beda)` : '');
      toast(msg);
      await loadInfo();
      loadFolders().catch(() => {});
      if (route.type === 'welcome') loadStorage();
    } catch (err) { toast(err.message, 'err'); }
  }
  for (const b of $$('.store-change')) b.addEventListener('click', changeStorage);
  $('#storeMove').addEventListener('click', () => doSwitch(true));
  $('#storeNew').addEventListener('click', () => doSwitch(false));
  $('#storeCancel').addEventListener('click', () => $('#storeDlg').close());
  window.addEventListener('db-storage-changed', async () => { await loadInfo(); loadFolders().catch(() => {}); });

  async function openExplorer(folder) {
    try { await api('/api/storage/open', { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify({ folder }) }); }
    catch (err) { toast(err.message, 'err'); }
  }

  $('#notifyChk').addEventListener('change', async (e) => {
    try { await api('/api/settings/notify', { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify({ on: e.target.checked }) }); }
    catch (err) { toast(err.message, 'err'); e.target.checked = !e.target.checked; }
  });

  $('#welDone').addEventListener('click', async () => {
    try { await api('/api/welcomed', { method: 'POST' }); } catch { /* tetap lanjut */ }
    welcomeDue = false;
    location.hash = '#/';
  });

  // ---------- Aplikasi Android: QR unduh dari PC ini (atau tombol langsung di HP Android) ----------
  const mm = (v) => (v || '0.0').split('.').slice(0, 2).map(Number);
  const appOld = (v) => {
    if (!v || !android.version) return false;
    const [a1, a2] = mm(v); const [b1, b2] = mm(android.version);
    return a1 < b1 || (a1 === b1 && a2 < b2);
  };
  function renderApk() {
    const isAndroidPhone = /Android/i.test(navigator.userAgent) && !isOnPc;
    $('#apkCard').hidden = !android.available || (!isOnPc && !isAndroidPhone);
    $('#apkTag').textContent = android.version || '';
    for (const box of $$('[data-apk]')) {
      box.replaceChildren();
      if (!android.available) {
        box.append(el('p', { class: 'muted small', text: 'APK belum ada di PC ini. Bangun dengan build_apk.bat, atau pakai DropBridge lewat browser HP.' }));
        continue;
      }
      const name = `DropBridge-${android.version}.apk`;
      const dl = el('a', { class: 'btn primary', href: '/api/app/android', download: name }, svg(IC.down), el('span', { text: isOnPc ? 'Unduh ' + name : 'Pasang aplikasi Android ' + android.version }));
      if (isOnPc && urls.length) {
        const img = el('img', { class: 'addr-qr', width: 168, height: 168, alt: 'Kode QR untuk mengunduh aplikasi Android', src: '/api/qr?apk=1&i=' + qrIdx + '&t=' + Date.now() });
        box.append(el('div', { class: 'addr-wrap' },
          el('div', { class: 'addr-qrbox' }, img, el('div', { class: 'addr-qrcap', text: 'Scan dengan kamera HP untuk mengunduh APK dari laptop ini, tanpa Play Store.' })),
          el('div', { class: 'addr-side' },
            el('b', { text: `DropBridge ${android.version} untuk Android` }),
            el('p', { class: 'muted small', text: 'Versinya selalu cocok dengan DropBridge di laptop ini. Di HP, izinkan "Instal aplikasi tak dikenal" untuk browser, sekali saja.' }),
            dl)));
      } else {
        box.append(el('p', { class: 'muted small', text: 'Untuk Foto langsung, antrean saat PC tak terjangkau, dan kirim di latar belakang.' }), dl);
      }
    }
  }

  // ---------- Tampilan terang / gelap ----------
  function applyTheme(t) {
    if (t === 'light' || t === 'dark') document.documentElement.dataset.theme = t;
    else delete document.documentElement.dataset.theme;
    for (const b of $$('[data-theme-set]')) b.setAttribute('aria-pressed', b.dataset.themeSet === (t || 'system') ? 'true' : 'false');
    const dark = t === 'dark' || (t !== 'light' && window.matchMedia('(prefers-color-scheme: dark)').matches);
    const meta = $('meta[name="theme-color"]');
    if (meta) meta.content = dark ? '#0a1a18' : '#0f766e';
  }
  for (const b of $$('[data-theme-set]')) {
    b.addEventListener('click', () => { store.set('db_theme', b.dataset.themeSet); applyTheme(b.dataset.themeSet); });
  }
  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => applyTheme(store.get('db_theme')));
  applyTheme(store.get('db_theme'));

  // ---------- Pengaturan (ikon di kanan atas) ----------
  $('#setBtn').addEventListener('click', () => { $('#myName').textContent = myName || 'Belum diberi nama'; $('#setDlg').showModal(); });
  $('#setClose').addEventListener('click', () => $('#setDlg').close());

  // ---------- Login / sesi ----------
  async function refresh() {
    try {
      const jobs = [loadFolders(), loadClips()];
      if (inFolder()) jobs.push(loadFiles());
      if (route.type === 'more') jobs.push(renderDevices());
      if (route.type === 'send') jobs.push(loadRecent());
      await Promise.all(jobs);
      setOnline(true);
    } catch (e) {
      if (e instanceof TypeError) setOnline(false); // jaringan putus
    }
  }

  // Kecilkan foto: diatur dari Pengaturan, ringkasannya tampil di halaman Kirim.
  function renderShrink() {
    const on = $('#shrinkPhotos').checked;
    $('#shrinkLine').hidden = photoMaxKB <= 0;
    $('#shrinkText').textContent = on ? `Foto dikecilkan ≤ ${photoMaxKB} KB` : 'Foto dikirim resolusi penuh';
  }
  $('#shrinkPhotos').checked = store.get('db_shrink') !== '0';
  $('#shrinkPhotos').addEventListener('change', (e) => { store.set('db_shrink', e.target.checked ? '1' : '0'); renderShrink(); });
  $('#shrinkEdit').addEventListener('click', () => $('#setBtn').click());
  renderShrink();

  // ---------- Masuk terakhir ----------
  let recentSig = '';
  async function loadRecent() {
    if (route.type !== 'send') return;
    const d = await api('/api/files/recent?limit=' + (window.innerWidth >= 960 ? 6 : 4));
    const sig = JSON.stringify(d.files) + Math.floor(Date.now() / 60000);
    if (sig === recentSig) return;
    recentSig = sig;
    const list = $('#recentList');
    list.replaceChildren(...d.files.map((f) => {
      const pic = el('span', { class: 'pic' });
      if (f.thumb) {
        const img = el('img', { class: 'thumb', loading: 'lazy', alt: '', src: `/api/files/${enc(f.name)}/thumb?folder=${enc(f.folder)}` });
        img.addEventListener('error', () => img.replaceWith(el('span', { class: 'ext', 'data-kind': f.kind, text: extOf(f.name) })), { once: true });
        pic.append(img);
      } else pic.append(el('span', { class: 'ext', 'data-kind': f.kind, text: extOf(f.name) }));
      return el('li', {},
        el('a', { class: 'rrow', href: f.folder ? '#/f/' + enc(f.folder) : '#/root' },
          pic,
          el('span', { class: 'meta' },
            el('span', { class: 'name', text: f.name, title: f.name }),
            el('span', { class: 'sub', text: pathLabel(f.folder) + ' · ' + fmtTime(f.modified) })),
          el('span', { class: 'ok', title: 'Sudah di PC' }, svg(IC.check))));
    }));
    $('#recentCard').hidden = !d.files.length;
  }

  // ---------- Identitas perangkat ----------
  let myName = '';
  let myLocal = false;

  async function checkName() {
    try {
      const s = await api('/api/session');
      myName = s.name || '';
      myLocal = !!s.local;
      $('#myName').textContent = myName || 'Belum diberi nama';
      $('#devOthers').hidden = !myLocal;
      $('#devNote').hidden = myLocal;
      if (!myName) {
        const saved = store.get('db_devname');
        if (saved) { await api('/api/device', { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify({ name: saved }) }); myName = saved; }
        // tanpa nama pun boleh: perangkat tampil sebagai jenisnya (mis. Android). Nama bisa diisi dari Lainnya.
      }
      updateDevCount();
    } catch { /* abaikan */ }
  }

  function openName() {
    $('#nameInput').value = myName;
    if (!$('#nameDlg').open) $('#nameDlg').showModal();
    $('#nameInput').focus();
  }

  $('#nameForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const name = $('#nameInput').value.trim();
    if (!name) return;
    try {
      const r = await api('/api/device', { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify({ name }) });
      myName = r.name;
      store.set('db_devname', r.name);
      $('#nameDlg').close();
      $('#myName').textContent = myName;
      if (route.type === 'more') renderDevices();
    } catch (err) { toast(err.message, 'err'); }
  });

  const ago = (t) => {
    const s = Math.max(0, Math.round(Date.now() / 1000 - t));
    if (s < 60) return 'baru saja';
    if (s < 3600) return Math.floor(s / 60) + ' menit lalu';
    if (s < 86400) return Math.floor(s / 3600) + ' jam lalu';
    return Math.floor(s / 86400) + ' hari lalu';
  };

  async function updateDevCount() {
    try {
      devInfo = await api('/api/devices');
      renderStatus();
      // Di PC: kartu kecil perangkat yang sedang terhubung di halaman Kirim
      const others = devInfo.devices.filter((v) => !v.current && v.online);
      $('#devCard').hidden = !isOnPc;
      $('#devChips').replaceChildren(...(others.length
        ? others.map((v) => el('span', { class: 'chip dev-chip' }, el('span', { class: 'dev-dot on' }), v.name))
        : [el('span', { class: 'muted small', text: 'Belum ada HP yang terhubung.' })]));
    } catch { /* abaikan */ }
  }

  async function renderDevices() {
    const box = $('#devList');
    try {
      const d = await api('/api/devices');
      box.replaceChildren(...d.devices.map((v) => el('div', { class: 'dev-row' + (appOld(v.app) ? ' dev-old' : '') },
        el('span', { class: 'dev-dot' + (v.online ? ' on' : ''), title: v.online ? 'Online' : 'Tidak aktif' }),
        el('div', { class: 'dev-info' },
          el('div', { class: 'dev-name', text: v.name + (v.current ? ' (perangkat ini)' : '') }),
          el('div', { class: 'dev-sub muted', text: `${v.app ? 'Aplikasi ' + v.app : v.kind} · ${v.ip} · ${v.online ? 'online' : ago(v.last_seen)}` }),
          appOld(v.app) ? el('div', { class: 'dev-old-note', text: `Versi lama. Perbarui ke ${android.version} lewat kartu Aplikasi Android.` }) : ''),
        v.current || !myLocal ? '' : el('button', {
          class: 'btn ghost small danger', type: 'button', text: 'Keluarkan',
          onclick: async () => {
            try { await api('/api/devices/' + v.id, { method: 'DELETE' }); toast('Perangkat dikeluarkan'); renderDevices(); updateDevCount(); }
            catch (err) { toast(err.message, 'err'); }
          },
        }))));
    } catch (err) { box.textContent = err.message; }
  }

  $('#renameBtn').addEventListener('click', openName);
  $('#devOthers').addEventListener('click', async () => {
    if (!confirm('Keluarkan semua perangkat lain? Mereka harus memasukkan PIN lagi.')) return;
    try { const r = await api('/api/devices/revoke-others', { method: 'POST' }); toast(r.revoked + ' perangkat dikeluarkan'); renderDevices(); updateDevCount(); }
    catch (err) { toast(err.message, 'err'); }
  });
  setInterval(() => { if (loggedIn && !document.hidden) updateDevCount(); }, 15000);

  $('#loginForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const pin = $('#pin').value.trim();
    if (!pin) return;
    try {
      await api('/api/login', { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify({ pin, name: store.get('db_devname') }) });
      $('#pin').value = '';
      showApp();
    } catch (err) {
      $('#loginError').textContent = err.message;
      $('#pin').select();
    }
  });

  $('#logout').addEventListener('click', async () => {
    try { await fetch('/api/logout', { method: 'POST', credentials: 'same-origin' }); } catch { /* abaikan */ }
    showLogin();
  });

  document.addEventListener('visibilitychange', () => { if (!document.hidden && loggedIn) refresh(); });

  (async function init() {
    const last = store.get('db_target');
    if (last) $('#folderInput').value = last;
    await loadInfo();
    const qpin = new URLSearchParams(location.search).get('pin');
    if (qpin) {
      // Dibuka dari kode QR di PC: hapus PIN dari alamat, lalu masuk otomatis.
      history.replaceState(null, '', location.pathname + location.hash);
      try {
        await api('/api/login', { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify({ pin: qpin.trim(), name: store.get('db_devname') }) });
        showApp();
        return;
      } catch { /* lanjut ke login biasa */ }
    }
    try {
      const r = await fetch('/api/session', { credentials: 'same-origin' });
      if (r.ok) showApp(); else showLogin();
    } catch {
      showLogin();
      $('#loginError').textContent = 'Tidak bisa terhubung ke PC. Cek Wi-Fi dan alamatnya.';
    }
  })();
})();
