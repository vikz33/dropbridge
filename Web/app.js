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
  // Tujuan kirim di laptop: { t: 'self' } | { t: 'peer', id, name, os } | { t: 'phone', key, name, app }
  let tgt = { t: 'self' };
  try { const x = JSON.parse(store.get('db_tgt') || 'null'); if (x && x.t) tgt = x; } catch { /* bawaan */ }
  let pcInfo = { version: '', os: '', name: '', url: '' };
  let upd = null; // status pembaruan dari /api/update (hanya di laptop ini)
  let updTimer = 0;
  let updLater = ''; // versi yang ditunda dengan "Nanti" (sampai dibuka lagi)

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

  // Tujuan "Tanpa folder" (langsung di folder penyimpanan) ditulis apa adanya di kotak folder
  const ROOT_LABEL = 'Tanpa folder';
  const isRootLabel = (s) => s.trim().toLowerCase() === ROOT_LABEL.toLowerCase();
  const findFolder = (name) => folders.find((f) => f.name.toLowerCase() === name.toLowerCase());

  // action: { label, onclick } mis. "Urungkan" setelah menghapus; tampil 4 detik
  function toast(msg, type = '', action = null) {
    const t = el('div', { class: 'toast ' + type }, el('span', { text: msg }));
    const ms = action ? 4000 : 2600;
    if (action) {
      t.classList.add('has-act');
      t.append(el('button', { type: 'button', class: 'toast-act', text: action.label, onclick: () => { t.remove(); action.onclick(); } }));
    }
    // Saat lembar/dialog terbuka, toast harus di dalamnya supaya tampil dan bisa diketuk (lapisan atas)
    const box = $('#toasts');
    const top = $$('dialog[open]').pop() || document.body;
    if (box.parentElement !== top) top.append(box);
    box.append(t);
    setTimeout(() => t.classList.add('out'), ms);
    setTimeout(() => t.remove(), ms + 400);
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
    renderRouteRow();
  }

  const pcLabel = () => (isOnPc ? 'PC ini' : (pcName || 'PC'));

  // Baris status di bawah judul: koneksi + jumlah perangkat
  function renderStatus() {
    const t = $('#statusText');
    if (!online) { t.textContent = 'Terputus dari ' + pcLabel() + ' · cek Wi-Fi'; return; }
    const n = devInfo.phones != null ? devInfo.phones : (devInfo.online || 0);
    // Jalur sambungan: Wi-Fi/hotspot yang sama (tanpa internet) atau Tailscale (lewat internet)
    t.textContent = isOnPc ? (n ? `${n} HP terhubung` : 'Belum ada HP terhubung') : `Terhubung ke ${pcLabel()}` + (via === 'tailscale' ? ' · lewat internet (Tailscale)' : ' · Wi-Fi lokal, tanpa internet');
  }

  function setTitle() {
    const where = isOnPc ? 'PC ini' : pcLabel();
    const page = route.type === 'send' ? 'Kirim ke ' + where : route.type === 'more' ? 'Ekosistem' : route.type === 'welcome' ? 'Selamat datang' : 'File di ' + where;
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
      if (d.notify != null) $('#notifyChk').checked = !!d.notify;
      pcInfo = { version: d.version || '', os: d.os || '', name: d.pc_name || '', url: (d.urls || [])[0] || '' };
      renderStore();
      renderApk();
      // Versi terpasang yang baru dipasang/diperbarui: tampilkan Selamat datang sekali
      if (welcomeDue && isOnPc && !location.hash) location.hash = '#/selamat-datang';
      if (d.this_pc) isOnPc = true;
      if (d.pc_name) pcName = d.pc_name;
      maxMB = d.max_upload_mb || maxMB;
      if (typeof d.photo_max_kb === 'number') photoMaxKB = d.photo_max_kb;
      renderShrink();
      renderRouteRow();
      renderSettings();
      renderAddr();
      renderRemote();
      setTitle();
      renderStatus();
    } catch { /* tidak kritis */ }
  }

  // ---------- Kotak nama folder (saran + petunjuk) ----------
  function makeCombo(input, list, hintEl, { onInput, tagEl } = {}) {
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
      if (isRootLabel(input.value)) items = [];
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

    // Petunjuk singkat di kanan atas (tagEl: "Sudah ada", "Subfolder baru") + catatan di bawah kotak (hintEl)
    function updateHint() {
      hintEl.replaceChildren();
      hintEl.className = 'hint';
      if (tagEl) { tagEl.textContent = ''; tagEl.className = 'folder-tag'; }
      const tag = (short, long, cls) => {
        if (tagEl) { tagEl.textContent = short; if (cls) tagEl.classList.add(cls); return; }
        hintEl.prepend(el('div', { text: long }));
        if (cls === 'ok') hintEl.classList.add('ok');
      };
      const raw = input.value;
      if (!raw.trim()) return;
      if (tagEl && isRootLabel(raw)) { tag('Langsung di penyimpanan', '', 'ok'); return; }
      const cleaned = cleanFolder(raw);
      if (!cleaned) { hintEl.classList.add('warn'); hintEl.textContent = 'Nama folder tidak valid.'; return; }
      if (depthOf(cleaned) > MAX_DEPTH) {
        tag(`Maks. ${MAX_DEPTH} tingkat`, '', 'bad');
        hintEl.classList.add('warn');
        hintEl.textContent = `Paling banyak ${MAX_DEPTH} tingkat folder, mis. Kontainer/Pos/Tanggal.`;
        return;
      }
      const exist = findFolder(cleaned);
      if (cleaned !== raw.trim() && cleaned !== raw.trim().replace(/\s*[\/\\]\s*/g, '/')) hintEl.append(el('div', { class: 'sub', text: 'Nama disesuaikan menjadi: ' + pathLabel(cleaned) }));
      if (exist) {
        tag('Sudah ada', `Folder sudah ada · ${exist.count} file`, 'ok');
        return;
      }
      const par = parentOf(cleaned);
      tag(par ? 'Subfolder baru' : 'Folder baru', par
        ? `Subfolder baru di ${pathLabel(findFolder(par) ? findFolder(par).name : par)}, dibuat saat file dikirim.`
        : 'Folder baru, dibuat saat file dikirim.');
      if (cleaned.length >= 4) {
        const sim = folders.filter((f) => withinOneEdit(f.name, cleaned)).slice(0, 3);
        if (sim.length) {
          hintEl.classList.add('warn', 'similar');
          hintEl.append(el('div', {},
            'Mirip dengan ',
            sim.map((f) => el('button', { type: 'button', class: 'link', text: pathLabel(f.name), onclick: () => choose(f.name) })),
            '. Salah ketik?'));
        }
      }
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

  const sendCombo = makeCombo($('#folderInput'), $('#suggest'), $('#folderHint'), { tagEl: $('#folderTag') });
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
    if (h === '#/ekosistem' || h === '#/hubungkan' || h === '#/lainnya') return { type: 'more', name: '' };
    if (h === '#/selamat-datang') return { type: 'welcome', name: '' };
    return { type: 'send', name: '' };
  }

  const inFolder = () => route.type === 'folder' || route.type === 'root';

  function applyRoute() {
    if (location.hash === '#/pengaturan') { // dari menu tray: buka Pengaturan
      history.replaceState(null, '', '#/');
      renderSettings();
      if (!$('#setDlg').open) $('#setDlg').showModal();
    }
    route = parseHash();
    if (myRole !== 'full' && route.type !== 'send' && route.type !== 'more' && route.type !== 'welcome') {
      route = { type: 'send' };
      if (location.hash && location.hash !== '#/') history.replaceState(null, '', '#/');
    }
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
    renderUpdated();
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
      hubSig = '';
      loadHub(true).catch(() => {});
      loadInfo();
    } else if (route.type === 'send') {
      if (isOnPc && myRole === 'full') loadIncoming().catch(() => {});
    }
  }

  window.addEventListener('hashchange', () => { if (loggedIn) applyRoute(); });

  const curFolder = () => (route.type === 'folder' ? route.name : '');

  // ---------- Daftar folder ----------
  async function loadFolders() {
    // Di halaman Kirim dengan tujuan laptop lain: saran dan Pilih folder memakai folder laptop itu
    const remote = isOnPc && tgt.t === 'peer' && route.type === 'send';
    let d;
    try { d = await api(remote ? '/api/remote/folders?id=' + enc(tgt.id) : '/api/folders'); }
    catch (e) { if (!remote) throw e; d = { folders: [], root: { count: 0 } }; }
    const sig = (remote ? 'r' + tgt.id : 'l') + JSON.stringify(d) + Math.floor(Date.now() / 60000);
    folders = d.folders;
    rootInfo = d.root || rootInfo;
    if (sig !== foldersSig) {
      foldersSig = sig;
      sendCombo.updateHint();
      if (route.type === 'home') renderHome();
      if (route.type === 'folder') { renderSubs(); renderMeta(); }
      if ($('#pickDlg').open) renderPick();
    }
  }


  // Folder yang terakhir dipakai HP/laptop ini (paling baru di depan), untuk panel Pilih folder
  const recentTargets = () => { try { return JSON.parse(store.get('db_recent') || '[]').filter((x) => typeof x === 'string'); } catch { return []; } };
  function rememberTarget(p) {
    if (!p) return;
    const list = [p, ...recentTargets().filter((x) => x.toLowerCase() !== p.toLowerCase())].slice(0, 8);
    store.set('db_recent', JSON.stringify(list));
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
    // Menu dari dalam dialog (mis. lembar Teks) harus ikut di dalam dialog supaya tampil di atasnya
    const host = btn.closest('dialog') || document.body;
    if (m.parentElement !== host) host.append(m);
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
          el('span', { class: 'name' + (!f.root && /\d{3}/.test(shown) ? ' code' : ''), text: shown, title: pathLabel(f.name) }),
          el('span', { class: 'sub', text: sub }))),
      dots);
  }

  function renderHome() {
    const list = $('#folderList');
    const q = $('#fSearch').value.trim().toLowerCase();
    list.replaceChildren();
    // Tanpa pencarian: folder tingkat atas saja. Dengan pencarian: semua tingkat, tampil jalur lengkap.
    const shown = sortBy(folders.filter((x) => (q ? x.name.toLowerCase().includes(q) : !x.parent)), pref.folderSort);
    // "Tanpa folder" selalu paling atas, apa pun urutannya
    if (rootInfo.count > 0 && (!q || 'tanpa folder'.includes(q))) {
      list.append(folderItem({ name: 'Tanpa folder', root: true, count: rootInfo.count, size: rootInfo.size, modified: rootInfo.modified }));
    }
    for (const f of shown) list.append(folderItem(f, !!q));
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
    if (!confirm(`Hapus "${f.name}" dari PC?\n\nFile dipindah ke Recycle Bin / Tempat Sampah di PC, jadi masih bisa dikembalikan dari sana.`)) return;
    try {
      const r = await api('/api/files/' + enc(f.name) + '?folder=' + enc(curFolder()), { method: 'DELETE' });
      toast('File dipindah ke ' + (r.trash || 'Tempat Sampah'));
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
      const ok = confirm(`Hapus folder "${pathLabel(name)}" beserta ${count} file di dalamnya dari PC?\n\nFolder dipindah ke Recycle Bin / Tempat Sampah di PC, jadi masih bisa dikembalikan dari sana.`);
      if (!ok) return;
      try {
        const r = await api('/api/folders/' + encPath(name) + '?expect=' + count, { method: 'DELETE' });
        if ($('#folderInput').value === name) setTarget('');
        if (store.get('db_target') === name) store.set('db_target', '');
        toast('Folder dipindah ke ' + (r.trash || 'Tempat Sampah'));
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
    if (isRootLabel(raw)) return '';
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
    const toPhone = isOnPc && tgt.t === 'phone';
    const folder = toPhone ? '' : folderOverride === undefined ? targetFolder() : folderOverride;
    if (folder === null) {
      pendingRoot = arr;
      $('#rootDlg').showModal();
      return false;
    }
    const where = toPhone ? tgt.name : (isOnPc && tgt.t === 'peer' ? tgt.name + ' › ' : '') + pathLabel(folder);
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
      queue.push({ file, row, folder, shrink: shrinkOn(), tgt: isOnPc ? { ...tgt } : { t: 'self' } });
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
    if (loggedIn) refresh();
  }

  function renderSum() {
    const busy = uploading || queue.length;
    const n = Math.min(batch.total, batch.done + (busy ? 1 : 0));
    $('#upSum').textContent = busy && batch.total ? `Mengirim ${n} dari ${batch.total}` : (batch.total ? `${batch.done} file selesai` : 'Pengiriman');
    $('#upCount').textContent = !batch.total ? '' : isOnPc && tgt.t === 'phone' ? 'ke ' + tgt.name
      : 'ke ' + (isOnPc && tgt.t === 'peer' ? tgt.name + ' › ' : '') + pathLabel(batch.folder || '');
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
    return sendFile(file, row, job.folder, job.tgt);
  }

  function sendFile(file, row, folder, to = { t: 'self' }) {
    return new Promise((resolve) => {
      const state = $('.up-state', row);
      const bar = $('.bar i', row);
      const xhr = new XMLHttpRequest();
      // Jalur cepat: isi file dikirim mentah (tanpa multipart), langsung ditulis ke disk oleh server.
      let q = '/api/files/upload-raw?folder=' + enc(folder) + '&name=' + enc(file.name);
      if (to.t === 'peer') q = '/api/remote/upload?id=' + enc(to.id) + '&folder=' + enc(folder) + '&name=' + enc(file.name);
      if (to.t === 'phone') q = '/api/outbox/upload?dev=' + enc(to.key) + '&name=' + enc(file.name);
      if (file.lastModified && to.t !== 'phone') q += '&last_modified=' + file.lastModified;
      xhr.open('POST', q);
      xhr.setRequestHeader('Content-Type', 'application/octet-stream');
      const t0 = performance.now();
      let tSent = 0, lastT = 0, lastB = 0, speed = 0;
      xhr.upload.onload = () => { tSent = performance.now(); state.textContent = to.t === 'peer' ? `Meneruskan ke ${to.name}...` : 'Terkirim, menunggu PC menyimpan...'; };
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
            if (to.t === 'phone') state.textContent = `Menunggu diambil ${to.name} · ${fmtSize(file.size)}`;
            else if (dupe) state.textContent = `Sudah ada di ${real || 'Tanpa folder'} (${d.saved[0].name}), tidak disimpan ulang`;
            else state.textContent = `Selesai · ${fmtSize(file.size)} · ${sec(now - t0)}` + (tSent && tSent - t0 > 300 ? ' · ' + fmtSpeed(file.size * 1000 / (tSent - t0)) : '');
            setTimeout(() => { row.remove(); syncUpCard(); }, 15000);
            if (to.t === 'peer') state.textContent = `Terkirim ke ${to.name} · ` + state.textContent;
            if (to.t === 'phone') loadSent().catch(() => {});
            if (real && to.t === 'self') { store.set('db_target', real); rememberTarget(real); }
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
    if (targetFolder() === null) {
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

  // ---------- Teks (lembar dari ikon Teks) ----------
  // Semua teks tersimpan di laptop. HP berakses Penuh dan laptop melihat daftarnya; HP "Hanya kirim" hanya bisa mengirim.
  async function loadClips() {
    const d = await api('/api/clips');
    const sig = JSON.stringify(d.clips) + Math.floor(Date.now() / 60000);
    clips = d.clips;
    if (sig !== clipsSig) { clipsSig = sig; renderClips(); }
  }

  const IC_COPY = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="8" y="8" width="12" height="12" rx="2"/><path d="M16 8V5a1 1 0 0 0-1-1H5a1 1 0 0 0-1 1v10a1 1 0 0 0 1 1h3"/></svg>';
  const IC_SEL = '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M5 6h14M5 12h14M5 18h9"/></svg>';
  const IC_LAP = '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="4" y="5" width="16" height="11" rx="1.5"/><path d="M2 19h20"/></svg>';
  const IC_HP = '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="7" y="3" width="10" height="18" rx="2"/><path d="M11 18h2"/></svg>';

  async function copyClip(c) {
    toast((await copyText(c.text)) ? 'Disalin. Tempel di aplikasi lain.' : 'Gagal menyalin, tahan lalu pilih teksnya', '');
  }

  function renderClips() {
    const list = $('#clipList');
    list.replaceChildren();
    $('#clipCount').textContent = 'SEMUA TEKS · ' + clips.length;
    if (!clips.length) {
      list.append(el('li', { class: 'empty', text: 'Belum ada teks. Tulis di atas, lalu salin di perangkat lain.' }));
      return;
    }
    for (const c of clips) {
      const fromPc = c.source === 'pc';
      const who = c.from || (fromPc ? 'Laptop' : c.source === 'hp' ? 'HP' : 'Perangkat');
      const body = el('div', { class: 'clip-text' }, linkify(c.text));
      const item = el('li', { class: 'clip' },
        el('div', { class: 'clip-main' },
          body,
          el('span', { class: 'clip-from ' + (fromPc ? 'pc' : 'hp') }, svg(fromPc ? IC_LAP : IC_HP), el('span', { text: who }), el('span', { class: 'muted', text: '· ' + fmtTime(c.ts) }))),
        el('div', { class: 'clip-acts' },
          el('button', { type: 'button', class: 'btn soft small', onclick: () => copyClip(c) }, svg(IC_COPY), 'Salin'),
          el('button', {
            type: 'button', class: 'dots', 'aria-label': 'Pilihan teks', 'aria-haspopup': 'menu', 'aria-expanded': 'false',
            onclick: (e) => openMenu(e.currentTarget, [
              { label: 'Salin', icon: IC_COPY, onclick: () => copyClip(c) },
              { label: 'Pilih sebagian', icon: IC_SEL, onclick: () => selectPart(item, body) },
              { label: 'Hapus', icon: IC.trash, danger: true, onclick: () => removeClip(c) },
            ]),
          }, svg(IC.dots))));
      list.append(item);
    }
  }

  // Tampilkan teks lengkap lalu pilih semuanya; pengguna bisa mempersempit pilihan sendiri
  function selectPart(item, body) {
    item.classList.add('open');
    const r = document.createRange();
    r.selectNodeContents(body);
    const sel = window.getSelection();
    sel.removeAllRanges();
    sel.addRange(r);
  }

  async function removeClip(c) {
    try {
      await api('/api/clips/' + c.id, { method: 'DELETE' });
      await loadClips();
      toast('Teks dihapus', '', {
        label: 'Urungkan',
        onclick: async () => {
          try { await api('/api/clips/' + c.id + '/restore', { method: 'POST' }); await loadClips(); }
          catch (e) { toast(e.message, 'err'); }
        },
      });
    } catch (e) { toast(e.message, 'err'); }
  }

  function openText() {
    const full = myRole === 'full';
    $('#clipHead').hidden = !full;
    $('#clipList').hidden = !full;
    $('#clipLocked').hidden = full;
    $('#clipSendLabel').textContent = isOnPc ? 'Kirim' : 'Kirim ke laptop';
    $('#clipPaste').hidden = !(navigator.clipboard && navigator.clipboard.readText && window.isSecureContext);
    $('#textDlg').showModal();
    if (full) loadClips().catch(() => {});
    if (deviceKind === 'pc') $('#clipText').focus();
  }
  $('#textClose').addEventListener('click', () => $('#textDlg').close());
  $('#textDlg').addEventListener('click', (e) => { if (e.target.id === 'textDlg') e.target.close(); });
  $('#clipPaste').addEventListener('click', async () => {
    try { const t = await navigator.clipboard.readText(); if (t) $('#clipText').value = t; } catch { toast('Tidak bisa membaca papan klip', 'err'); }
  });

  $('#clipForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const ta = $('#clipText');
    const text = ta.value.trim();
    if (!text) { ta.focus(); return; }
    try {
      await api('/api/clips', { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify({ text, source: deviceKind }) });
      ta.value = '';
      toast(isOnPc ? 'Teks tersimpan, bisa disalin dari HP' : 'Teks terkirim ke laptop');
      if (myRole === 'full') await loadClips();
    } catch (err) { toast(err.message, 'err'); }
  });
  $('#clipText').addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) { e.preventDefault(); $('#clipSend').click(); }
  });

  function syncUpCard() {
    const has = !!$('#uploads').children.length;
    $('#upCard').hidden = !has;
    if (!has && !uploading && !queue.length) { batch.total = batch.done = 0; }
    renderSum();
  }

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
  // Tingkat atas: "Terakhir dipakai" (langsung terpilih saat diketuk), lalu "Semua folder" dengan "Tanpa folder" paling atas.
  let pickPath = '';
  function chooseTarget(p) {
    setTarget(p);
    store.set('db_target', p);
    $('#pickDlg').close();
  }
  function pickRow(f, { onclick, chev = true, sel = false, root = false, meta } = {}) {
    return el('li', {},
      el('button', { type: 'button', class: 'pick-row' + (sel ? ' sel' : ''), onclick },
        el('span', { class: 'fic' + (root ? ' root' : '') }, svg(IC.folder)),
        el('span', { class: 'meta' },
          el('span', { class: 'name' + (root ? '' : ' code'), text: root ? ROOT_LABEL : (meta === undefined ? labelOf(f.name) : pathLabel(f.name)) }),
          el('span', { class: 'sub', text: meta !== undefined ? meta : (f.subs ? `${f.subs} folder · ` : '') + `${f.count} file` })),
        chev ? svg(IC.chev) : ''));
  }
  function renderPick() {
    const nav = $('#pickCrumbs');
    nav.replaceChildren();
    if (pickPath) {
      nav.append(el('button', { type: 'button', class: 'link', text: 'Semua folder', onclick: () => { pickPath = ''; renderPick(); } }));
      const parts = pickPath.split('/');
      parts.forEach((x, i) => {
        nav.append(el('span', { 'aria-hidden': 'true', text: ' › ' }));
        const p = parts.slice(0, i + 1).join('/');
        nav.append(i === parts.length - 1 ? el('b', { text: x }) : el('button', { type: 'button', class: 'link', text: x, onclick: () => { pickPath = p; renderPick(); } }));
      });
    }
    const cur = targetFolder();
    // Terakhir dipakai: hanya di tingkat atas, hanya folder yang masih ada
    const recent = pickPath ? [] : recentTargets().map((p) => findFolder(p)).filter(Boolean).slice(0, 3);
    $('#pickRecentCap').hidden = !recent.length;
    $('#pickRecent').replaceChildren(...recent.map((f) => pickRow(f, {
      onclick: () => chooseTarget(f.name), chev: false, sel: cur !== null && cur.toLowerCase() === f.name.toLowerCase(),
      meta: (f.subs ? `${f.subs} folder · ` : '') + `${f.count} file · ${fmtTime(f.modified)}`,
    })));
    $('#pickAllCap').hidden = !!pickPath;
    const kids = sortBy(childrenOf(pickPath), 'az');
    const rows = kids.map((f) => pickRow(f, { onclick: () => { pickPath = f.name; renderPick(); } }));
    if (!pickPath) {
      rows.unshift(pickRow({ name: '' }, {
        onclick: () => chooseTarget(ROOT_LABEL), chev: false, root: true, sel: cur === '',
        meta: 'Langsung di folder penyimpanan' + (rootInfo.count ? ` · ${rootInfo.count} file` : ''),
      }));
    }
    if (!kids.length && pickPath) rows.push(el('li', { class: 'empty', text: 'Belum ada subfolder di sini.' }));
    $('#pickList').replaceChildren(...rows);
    $('#pickUp').hidden = !pickPath;
    $('#pickOk').hidden = !pickPath;
    $('#pickOk').textContent = 'Pilih ' + (pickPath ? labelOf(pickPath) : '');
    $('#pickNew').hidden = depthOf(pickPath) >= MAX_DEPTH;
    $('.pick-foot').classList.toggle('one', !pickPath || depthOf(pickPath) >= MAX_DEPTH);
  }
  function openPick() {
    const cur = cleanFolder($('#folderInput').value || '');
    // Di dalam folder tujuan sekarang bila folder itu punya subfolder; selain itu mulai dari atas
    const f = cur && !isRootLabel($('#folderInput').value) ? findFolder(cur) : null;
    pickPath = f && childrenOf(f.name).length ? f.name : '';
    renderPick();
    $('#pickDlg').showModal();
  }
  $('#pickBtn').addEventListener('click', openPick);
  $('#pickUp').addEventListener('click', () => { pickPath = parentOf(pickPath); renderPick(); });
  $('#pickClose').addEventListener('click', () => $('#pickDlg').close());
  $('#pickDlg').addEventListener('click', (e) => { if (e.target.id === 'pickDlg') e.target.close(); });
  $('#pickOk').addEventListener('click', () => { if (pickPath) chooseTarget(pickPath); });
  $('#pickNew').addEventListener('click', () => {
    openNew(pickPath, (p) => chooseTarget(p));
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

  // Lokasi simpan hanya terlihat dari laptop ini: ikon "Folder penyimpanan" di Home dan baris di Pengaturan
  function renderStore() {
    $('#tStore').hidden = !storageDir;
    $('#storeNow').textContent = storageDir ? shortPath(storageDir) : '';
    $('#storeNow').title = storageDir;
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
  // Urutan sama di semua tempat: Perangkat ini, Laptop ini (hanya di laptop), Tentang, Keluar (tidak di laptop).
  function renderSettings() {
    const onPc = isOnPc;
    $('#setSub').textContent = onPc ? (pcInfo.name || 'Laptop ini') : (myName || (deviceKind === 'hp' ? 'HP ini' : 'Perangkat ini'));
    $('#nameLabel').textContent = onPc ? 'Nama laptop' : 'Nama perangkat';
    $('#nameSub').textContent = onPc ? 'Terlihat di HP saat memilih tujuan' : 'Terlihat di laptop saat minta izin';
    $('#renameBtn').hidden = onPc; // nama laptop = nama komputer; diubah dari pengaturan sistem
    $('#myName').textContent = myName || 'Belum diberi nama';
    $('#themeSys').textContent = deviceKind === 'hp' ? 'Ikuti HP' : 'Ikuti sistem';
    $('#pcSet').hidden = !myLocal;
    $('#verName').textContent = onPc ? 'DropBridge PC' : 'Laptop · ' + (pcInfo.name || pcLabel());
    const addr = pcInfo.url ? pcInfo.url.replace(/^https?:\/\//, '') : '';
    $('#verSub').textContent = [pcInfo.os, addr].filter(Boolean).join(' · ');
    $('#verNum').textContent = pcInfo.version;
    $('#dataRow').hidden = !myLocal;
    renderUpdate();
    if (myLocal) loadUpdate(false);
    $('#logout').hidden = onPc;
    $('#logout').textContent = 'Keluar dari ' + (pcInfo.name || 'laptop');
  }
  $('#setBtn').addEventListener('click', () => { renderSettings(); $('#setDlg').showModal(); });
  $('#setClose').addEventListener('click', () => $('#setDlg').close());
  $('#setDlg').addEventListener('click', (e) => { if (e.target.id === 'setDlg') e.target.close(); });
  $('#dataBtn').addEventListener('click', async () => {
    try { await api('/api/storage/open-data', { method: 'POST' }); toast('Folder data dibuka di laptop'); }
    catch (err) { toast(err.message, 'err'); }
  });

  // ---------- Pembaruan dari GitHub (hanya di laptop ini) ----------
  const updBusy = () => !!upd && ['downloading', 'verifying', 'installing'].includes(upd.state);
  const mb = (b) => (b / 1048576).toFixed(1).replace('.', ',');
  async function loadUpdate(check) {
    if (!myLocal) return;
    try {
      upd = await api(check ? '/api/update/check' : '/api/update', check ? { method: 'POST' } : {});
    } catch (err) { if (check) toast(err.message, 'err'); return; }
    renderUpdate();
    clearTimeout(updTimer);
    if (updBusy() && $('#setDlg').open) updTimer = setTimeout(() => loadUpdate(false), 1000);
  }
  function renderUpdate() {
    const row = $('#updRow'), card = $('#updCard');
    row.hidden = !myLocal;
    if (!myLocal || !upd) { card.hidden = true; return; }
    const busy = updBusy();
    const avail = upd.state === 'available' || busy;
    const hm = (t) => new Date(t * 1000).toLocaleTimeString('id-ID', { hour: '2-digit', minute: '2-digit' });
    $('#updSub').textContent = busy ? 'Sedang memperbarui…'
      : upd.check_error ? upd.check_error
      : avail ? 'Versi ' + upd.latest + ' tersedia'
      : upd.checked_at ? 'Sudah versi terbaru · dicek ' + hm(upd.checked_at) : 'Belum dicek';
    $('#updCheck').disabled = busy || upd.state === 'checking';
    const later = updLater === upd.latest && !busy && !upd.error;
    card.hidden = !avail || later;
    if (card.hidden) return;
    const canGo = upd.ready && upd.can_install;
    const note = upd.error ? upd.error : (!upd.ready ? upd.missing : (!upd.can_install ? upd.blocked : ''));
    card.classList.toggle('err', !!upd.error);
    card.classList.toggle('warn', !upd.error && !!note);
    $('#updTitle').textContent = 'Versi ' + upd.latest + ' tersedia';
    $('#updMeta').textContent = [upd.size ? '±' + mb(upd.size) + ' MB' : '', 'dari GitHub'].filter(Boolean).join(' · ');
    const ul = $('#updNotes');
    ul.replaceChildren(...(upd.notes || []).map((t) => el('li', { text: t })));
    let msg = note;
    if (upd.state === 'downloading') msg = 'Mengunduh ' + mb(upd.done) + (upd.total ? ' dari ' + mb(upd.total) : '') + ' MB';
    else if (upd.state === 'verifying') msg = 'Memeriksa keutuhan file (SHA-256)…';
    else if (upd.state === 'installing') msg = upd.platform === 'linux'
      ? 'Memasang… masukkan kata sandi laptop bila diminta. DropBridge akan tertutup lalu terbuka lagi.'
      : 'Memasang… izinkan administrator bila diminta. DropBridge akan tertutup lalu terbuka lagi.';
    $('#updMsg').textContent = msg;
    $('#updMsg').hidden = !msg;
    $('#updBar').hidden = !(upd.state === 'downloading' || upd.state === 'verifying');
    $('#updBar i').style.width = (upd.state === 'verifying' ? 100 : upd.total ? Math.min(100, Math.round((upd.done / upd.total) * 100)) : 0) + '%';
    $('#updGo').hidden = !canGo || upd.state === 'installing';
    $('#updGo').disabled = busy;
    $('#updGo').textContent = upd.error ? 'Coba lagi' : 'Perbarui sekarang';
    $('#updCancel').hidden = upd.state !== 'downloading';
    $('#updLater').hidden = busy;
    $('#updPage').hidden = canGo && !upd.error;
    if ((upd.page || '').startsWith('https://github.com/')) $('#updPage').href = upd.page;
    $('#updFoot').hidden = !canGo;
  }
  $('#updCheck').addEventListener('click', () => loadUpdate(true));
  $('#updLater').addEventListener('click', () => { if (upd) updLater = upd.latest; renderUpdate(); });
  $('#updCancel').addEventListener('click', async () => {
    try { upd = await api('/api/update/cancel', { method: 'POST' }); renderUpdate(); } catch (err) { toast(err.message, 'err'); }
  });
  $('#updGo').addEventListener('click', async () => {
    try {
      upd = await api('/api/update/install', { method: 'POST' });
      renderUpdate();
      loadUpdate(false);
    } catch (err) { toast(err.message, 'err'); loadUpdate(false); }
  });
  $('#setDlg').addEventListener('close', () => clearTimeout(updTimer));

  // ---------- Login / sesi ----------
  let lastRefresh = 0;
  async function refresh() {
    try {
      const jobs = [loadFolders()];
      if (myRole === 'full' && $('#textDlg').open) jobs.push(loadClips());
      if (inFolder() && myRole === 'full') jobs.push(loadFiles());
      if (route.type === 'more') jobs.push(loadHub());
      if (isOnPc && myRole === 'full') jobs.push(loadIncoming());
      if (route.type === 'send') jobs.push(isOnPc ? loadSent() : loadOutbox());
      await Promise.all(jobs);
      setOnline(true);
      lastRefresh = Date.now();
      renderUpdated();
    } catch (e) {
      if (e instanceof TypeError) setOnline(false); // jaringan putus
    }
  }

  // Ikon muat ulang di kepala halaman File (berputar saat memuat) + "diperbarui 10.19"
  function renderUpdated() {
    const files = route.type !== 'send' && route.type !== 'more' && route.type !== 'welcome';
    $('#refreshBtn').hidden = !files;
    $('#refreshBtn').title = lastRefresh
      ? 'Muat ulang · diperbarui ' + new Date(lastRefresh).toLocaleTimeString('id-ID', { hour: '2-digit', minute: '2-digit' })
      : 'Muat ulang';
  }
  $('#refreshBtn').addEventListener('click', async () => {
    const b = $('#refreshBtn');
    if (b.classList.contains('spin')) return;
    b.classList.add('spin');
    foldersSig = filesSig = '';
    try { await refresh(); toast('Diperbarui ' + new Date().toLocaleTimeString('id-ID', { hour: '2-digit', minute: '2-digit' })); }
    finally { setTimeout(() => b.classList.remove('spin'), 400); }
  });

  // ---------- Baris ikon di Home ----------
  // Foto kecil: hidup = foto dikecilkan ke bawah batas (bawaan 512 KB) sebelum dikirim. Hanya di Home, tidak di Pengaturan.
  const shrinkOn = () => photoMaxKB > 0 && store.get('db_shrink') !== '0';
  function renderShrink() {
    const b = $('#tShrink');
    b.hidden = photoMaxKB <= 0;
    b.setAttribute('aria-pressed', shrinkOn() ? 'true' : 'false');
    b.title = shrinkOn() ? `Foto dikecilkan ≤ ${photoMaxKB} KB. Ketuk untuk resolusi penuh.` : 'Foto dikirim resolusi penuh. Ketuk untuk mengecilkan.';
  }
  $('#tShrink').addEventListener('click', () => {
    store.set('db_shrink', shrinkOn() ? '0' : '1');
    renderShrink();
    toast(shrinkOn() ? `Foto dikecilkan ≤ ${photoMaxKB} KB` : 'Foto dikirim resolusi penuh');
  });
  renderShrink();
  $('#tAll').addEventListener('click', () => { location.hash = '#/folders'; });
  $('#tText').addEventListener('click', openText);
  $('#tStore').addEventListener('click', changeStorage);
  $('#tAdd').addEventListener('click', () => { location.hash = '#/ekosistem'; });
  $('#tRefresh').addEventListener('click', async () => {
    const b = $('#tRefresh');
    if (b.classList.contains('spin')) return;
    b.classList.add('spin');
    foldersSig = '';
    try { await refresh(); await loadInfo(); toast(online ? 'Diperbarui ' + new Date().toLocaleTimeString('id-ID', { hour: '2-digit', minute: '2-digit' }) : 'Laptop tidak terjangkau', online ? '' : 'err'); }
    finally { setTimeout(() => b.classList.remove('spin'), 400); }
  });

  // Kartu tujuan di HP: laptop yang dituju + titik status
  function renderRouteRow() {
    const lap = isOnPc;
    $('#routeRow').hidden = false;
    $('#routeBtn').hidden = !lap;
    const phone = lap && tgt.t === 'phone';
    $('#routeCap').textContent = !lap ? 'KE PC' : phone ? 'KE HP' : 'KE';
    $('#routeName').textContent = !lap ? (pcInfo.name || pcLabel()) : tgt.t === 'self' ? 'Laptop ini' : tgt.name;
    $('#routeOs').textContent = !lap ? (pcInfo.os ? ' · ' + pcInfo.os : '') : tgt.t === 'self' ? (pcInfo.os ? ' · ' + pcInfo.os : '') : tgt.t === 'peer' ? (tgt.os ? ' · ' + tgt.os : '') : (tgt.app ? ' · aplikasi' : ' · browser');
    $('#routeSub').textContent = !lap ? '' : tgt.t === 'self' ? shortPath(storageDir || '') : tgt.t === 'peer' ? 'File disimpan di ' + tgt.name : (tgt.app ? 'Tersimpan di Download/DropBridge di HP' : 'Muncul kartu Unduh di browser HP itu');
    $('#routeSub').hidden = !lap;
    $('#routeIc').classList.toggle('phone', phone);
    const ic = $('#routeIc svg');
    if (ic) ic.innerHTML = phone ? '<rect x="7" y="3" width="10" height="18" rx="2"/><path d="M11 18h2"/>' : '<rect x="4" y="5" width="16" height="11" rx="1.5"/><path d="M2 19h20"/>';
    $('#routeDot').classList.toggle('off', !online);
    $('#folderBlock').hidden = phone;
    $('.dz-title').textContent = !lap || tgt.t === 'self' ? 'Seret file ke sini' : 'Seret file untuk ' + tgt.name;
    $('.dz-sub').textContent = !lap || tgt.t === 'self' ? 'atau tempel gambar dengan Ctrl+V' : phone ? 'Folder tidak perlu: file masuk ke Download/DropBridge di HP' : 'Folder di atas adalah folder di ' + tgt.name;
    $('#sentCard').hidden = !phone || !sentItems.length;
  }

  // ---------- Tujuan kirim di laptop: Laptop ini, laptop lain, atau HP ----------
  let tgtPeers = [];
  let tgtPhones = [];
  let sentItems = [];
  const LAP_IC = '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="4" y="5" width="16" height="11" rx="1.5"/><path d="M2 19h20"/></svg>';
  const HP_IC = '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="7" y="3" width="10" height="18" rx="2"/><path d="M11 18h2"/></svg>';

  function setTgt(t) {
    tgt = t;
    store.set('db_tgt', JSON.stringify(t));
    foldersSig = '';
    sentItems = [];
    renderRouteRow();
    loadFolders().catch(() => {});
    if (t.t === 'phone') loadSent().catch(() => {});
    if (t.t === 'self') setTarget(store.get('db_target') || '');
    else if (t.t === 'peer') setTarget('');
  }

  function tgtRow({ icon, phone, app, title, sub, warn, sel, action, onclick }) {
    const ic = el('span', { class: 'tgt-ic' + (phone ? ' phone' + (app ? '' : ' web') : '') + (sel ? ' sel' : '') });
    ic.innerHTML = icon;
    return el('li', {}, el('div', { class: 'tgt-row' + (sel ? ' sel' : '') },
      el('button', { type: 'button', class: 'tgt-main', onclick }, ic,
        el('span', { class: 'tgt-tx' }, el('b', { text: title }), el('small', { class: warn ? 'warn' : 'muted', text: sub }))),
      sel ? svg(IC.check) : action || ''));
  }

  async function openTargets() {
    renderTargets();
    $('#tgtDlg').showModal();
    try {
      const [p, h] = await Promise.all([api('/api/remote/peers?fresh=1'), api('/api/outbox/phones')]);
      tgtPeers = p.peers || [];
      tgtPhones = h.phones || [];
    } catch (e) { toast(e.message, 'err'); }
    renderTargets();
  }

  function renderTargets() {
    const laps = [tgtRow({ icon: LAP_IC, title: 'Laptop ini' + (pcInfo.os ? ' · ' + pcInfo.os : ''), sub: 'Simpan di ' + (shortPath(storageDir || '') || 'Documents › DropBridge'), sel: tgt.t === 'self', onclick: () => { setTgt({ t: 'self' }); $('#tgtDlg').close(); } })];
    for (const p of tgtPeers) {
      const name = p.name || p.url.replace('http://', '');
      const sel = tgt.t === 'peer' && tgt.id === p.id;
      const go = () => {
        if (p.paired) { setTgt({ t: 'peer', id: p.id, name, os: p.os || '' }); $('#tgtDlg').close(); }
        else askPeerPin(p);
      };
      const sub = p.paired ? (p.here ? 'Wi-Fi ini · sudah tersambung' : 'Sudah tersambung · tidak terlihat sekarang') + (p.role === 'send' ? ' · hanya kirim' : '')
        : p.status === 'pending' ? 'Menunggu izin di ' + name : 'Wi-Fi ini · perlu PIN sekali';
      laps.push(tgtRow({ icon: LAP_IC, title: name + (p.os ? ' · ' + p.os : ''), sub, warn: !p.paired, sel, onclick: go,
        action: p.paired ? '' : el('button', { type: 'button', class: 'btn small', text: 'Sambungkan', onclick: () => askPeerPin(p) }) }));
    }
    if (!tgtPeers.length) laps.push(el('li', { class: 'muted small tgt-empty', text: 'Belum ada laptop lain dengan DropBridge di Wi-Fi ini.' }));
    $('#tgtLaps').replaceChildren(...laps);
    const phones = tgtPhones.map((h) => tgtRow({
      icon: HP_IC, phone: true, app: !!h.app, title: h.name,
      sub: (h.app ? 'Aplikasi' : 'Browser') + ' · ' + (h.online ? 'aktif' : 'terakhir aktif ' + ago(h.last_seen)) + ' · ' + (h.app ? (h.online ? 'langsung tersimpan di HP' : 'diambil saat aplikasi dibuka') : 'muncul kartu Unduh'),
      sel: tgt.t === 'phone' && tgt.key === h.key,
      onclick: () => { setTgt({ t: 'phone', key: h.key, name: h.name, app: h.app }); $('#tgtDlg').close(); },
    }));
    $('#tgtPhonesCap').hidden = !phones.length;
    $('#tgtPhones').replaceChildren(...phones);
  }
  $('#routeBtn').addEventListener('click', openTargets);
  $('#tgtClose').addEventListener('click', () => $('#tgtDlg').close());
  $('#tgtDlg').addEventListener('click', (e) => { if (e.target.id === 'tgtDlg') e.target.close(); });

  // PIN laptop lain, lalu tunggu izin di laptop itu
  let peerAsk = null;
  let peerPoll = 0;
  function askPeerPin(p) {
    peerAsk = p;
    const name = p.name || p.url.replace('http://', '');
    $('#peerTitle').textContent = 'Sambungkan ke ' + name;
    $('#peerHelp').textContent = `Masukkan PIN ${name}. Ada di DropBridge ${name} › Pengaturan › PIN.`;
    $('#peerPin').value = '';
    $('#peerPin').disabled = false;
    $('#peerOk').hidden = false;
    $('#peerWait').hidden = true;
    $('#peerErr').hidden = true;
    $('#peerDlg').showModal();
    $('#peerPin').focus();
  }
  function stopPeerPoll() { clearInterval(peerPoll); peerPoll = 0; }
  $('#peerCancel').addEventListener('click', () => { stopPeerPoll(); $('#peerDlg').close(); });
  $('#peerForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const p = peerAsk;
    const pin = $('#peerPin').value.trim();
    if (!p || !/^\d{4,8}$/.test(pin)) { $('#peerErr').textContent = 'Isi PIN 6 angka.'; $('#peerErr').hidden = false; return; }
    const name = p.name || p.url.replace('http://', '');
    $('#peerErr').hidden = true;
    try {
      const r = await api('/api/remote/login', { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify({ id: p.id, url: p.url, pin, name, os: p.os || '' }) });
      const done = () => { stopPeerPoll(); $('#peerDlg').close(); if ($('#tgtDlg').open) $('#tgtDlg').close(); setTgt({ t: 'peer', id: r.id, name, os: p.os || '' }); toast('Tersambung ke ' + name); if (route.type !== 'send') location.hash = '#/'; };
      if (r.status === 'ok') { done(); return; }
      $('#peerPin').disabled = true;
      $('#peerOk').hidden = true;
      $('#peerWait').hidden = false;
      $('#peerWait').textContent = `PIN benar. Menunggu izin di ${name}…`;
      stopPeerPoll();
      peerPoll = setInterval(async () => {
        try {
          const st = await api('/api/remote/status?id=' + enc(r.id));
          if (st.status === 'ok') done();
          else if (st.status === 'pending') $('#peerWait').textContent = `PIN benar. Menunggu izin di ${name}… ${mmss(st.wait || 0)}`;
          else { stopPeerPoll(); $('#peerWait').hidden = true; $('#peerErr').textContent = `${name} menolak atau waktunya habis.`; $('#peerErr').hidden = false; $('#peerPin').disabled = false; $('#peerOk').hidden = false; }
        } catch { /* coba lagi di putaran berikutnya */ }
      }, 2000);
    } catch (err) { $('#peerErr').textContent = err.message; $('#peerErr').hidden = false; }
  });

  // Status kiriman ke HP (menunggu diambil / diterima)
  async function loadSent() {
    if (!isOnPc || tgt.t !== 'phone') return;
    const d = await api('/api/outbox/sent?dev=' + enc(tgt.key));
    sentItems = d.items || [];
    const got = sentItems.filter((i) => i.status === 'received').length;
    $('#sentTitle').textContent = `${sentItems.length} file untuk ${tgt.name}`;
    $('#sentSum').textContent = `${got} diterima · ${sentItems.length - got} menunggu`;
    $('#sentList').replaceChildren(...sentItems.slice(0, 8).map((i) => el('li', {},
      el('span', { class: 'sent-name', text: i.name }),
      i.status === 'received' ? el('span', { class: 'sent-ok', text: '✓ Diterima ' + fmtClock(i.done) })
        : el('span', { class: 'sent-wait' }, 'Menunggu diambil HP ', el('button', { type: 'button', class: 'linkbtn', text: 'Batal', onclick: async () => { await api('/api/outbox/' + i.id, { method: 'DELETE' }); loadSent(); } })))));
    $('#sentCard').hidden = !sentItems.length;
  }
  const fmtClock = (ts) => new Date(ts * 1000).toLocaleTimeString('id-ID', { hour: '2-digit', minute: '2-digit' });

  // HP lewat browser: kiriman dari laptop untuk HP ini
  let obSig = '';
  async function loadOutbox() {
    if (isOnPc) return;
    const d = await api('/api/outbox');
    const items = d.items || [];
    const sig = items.map((i) => i.id).join();
    if (sig === obSig) return;
    obSig = sig;
    $('#obCard').hidden = !items.length;
    if (!items.length) return;
    $('#obTitle').textContent = `Ada ${items.length} file dari ${items[0].from || 'laptop'}`;
    $('#obList').replaceChildren(...items.map((i) => el('li', {},
      el('span', { class: 'sent-name' }, i.name, el('span', { class: 'muted', text: ' · ' + fmtSize(i.size) })),
      el('a', { class: 'btn soft small', href: '/api/outbox/' + i.id + '/file?mark=1', download: i.name, text: 'Unduh', onclick: () => setTimeout(() => { obSig = ''; loadOutbox(); }, 4000) }))));
  }

  // ---------- Kabar file masuk (Home laptop) ----------
  let inSeq = -1;
  let inShown = null;
  async function loadIncoming() {
    if (route.type !== 'send') return;
    const d = await api('/api/files/incoming?since=' + inSeq);
    const first = inSeq < 0;
    inSeq = d.seq;
    if (first || !d.batches.length) return;
    const b = d.batches[d.batches.length - 1];
    inShown = b;
    const what = b.images === b.count ? (b.count > 1 ? `${b.count} foto` : '1 foto') : `${b.count} file`;
    $('#inTitle').textContent = `${what} masuk` + (b.from && b.dev !== 'pc' ? ' dari ' + b.from : '');
    $('#inSub').replaceChildren('ke ', el('span', { class: 'code', text: pathLabel(b.folder) }), ' · baru saja');
    $('#inBanner').hidden = false;
  }
  $('#inOpen').addEventListener('click', () => { if (inShown) location.hash = inShown.folder ? '#/f/' + enc(inShown.folder) : '#/root'; $('#inBanner').hidden = true; });
  $('#inClose').addEventListener('click', () => { $('#inBanner').hidden = true; });

  // ---------- Identitas perangkat ----------
  let myName = '';
  let myLocal = false;
  let myRole = 'full';

  function applyRole(role) {
    const changed = role !== myRole;
    myRole = role || 'full';
    document.body.classList.toggle('role-send', myRole !== 'full');
    if (changed && loggedIn) applyRoute();
  }

  async function checkName() {
    try {
      const s = await api('/api/session');
      myName = s.name || '';
      myLocal = !!s.local;
      applyRole(s.role);
      if (s.pin) $('#pinNow').textContent = s.pin.replace(/^(\d{3})(\d{3})$/, '$1 $2');
      if (myLocal) startPendingWatch();
      renderSettings();
      $('#devOthers').hidden = !myLocal;
      if (myLocal) api('/api/devices/pending').then((d) => { $('#approveChk').checked = !!d.approve_new; }).catch(() => {});
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
      renderSettings();
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
    } catch { /* abaikan */ }
  }

  const roleName = (r) => (r === 'full' ? 'Penuh' : 'Hanya kirim');

  async function renderDevices() {
    const box = $('#devList');
    try {
      const d = await api('/api/devices');
      box.replaceChildren(...d.devices.map((v) => {
        const sub = v.pending
          ? `Menunggu izin · ${v.kind} · ${v.ip}`
          : `${v.app ? 'Aplikasi ' + v.app : v.kind} · ${v.ip} · ${v.online ? 'online' : ago(v.last_seen)}`;
        const btns = el('div', { class: 'dev-btns' });
        if (v.pending && myLocal) {
          btns.append(
            el('button', { class: 'btn small', type: 'button', text: 'Tolak', onclick: () => answer(v, false) }),
            el('button', { class: 'btn small primary', type: 'button', text: 'Izinkan', onclick: () => openApprove(v) }));
        } else if (!v.local) {
          const chip = myLocal
            ? el('button', {
              class: 'role-chip ' + v.role, type: 'button', text: roleName(v.role),
              title: 'Ketuk untuk mengubah hak', 'aria-label': 'Hak: ' + roleName(v.role) + '. Ketuk untuk mengubah.',
              onclick: async () => {
                const to = v.role === 'full' ? 'send' : 'full';
                if (!confirm(`Ubah hak ${v.name} menjadi "${roleName(to)}"?`)) return;
                try { await api('/api/devices/' + v.id + '/role', { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify({ role: to }) }); toast('Hak diubah: ' + roleName(to)); renderDevices(); }
                catch (err) { toast(err.message, 'err'); }
              },
            })
            : el('span', { class: 'role-chip ' + v.role, text: roleName(v.role) });
          btns.append(chip);
          if (myLocal && !v.current) btns.append(el('button', {
            class: 'btn ghost small danger', type: 'button', text: 'Putuskan',
            onclick: async () => {
              if (!confirm(`Putuskan ${v.name}? Untuk terhubung lagi, HP itu perlu PIN dan izin dari laptop ini.`)) return;
              try { await api('/api/devices/' + v.id, { method: 'DELETE' }); toast('Perangkat diputuskan'); renderDevices(); updateDevCount(); }
              catch (err) { toast(err.message, 'err'); }
            },
          }));
        }
        return el('div', { class: 'dev-row' + (v.pending ? ' pending' : '') + (appOld(v.app) ? ' dev-old' : '') },
          el('span', { class: 'dev-dot' + (v.online ? ' on' : ''), title: v.online ? 'Online' : 'Tidak aktif' }),
          el('div', { class: 'dev-info' },
            el('div', { class: 'dev-name', text: v.name + (v.current ? ' (perangkat ini)' : '') }),
            el('div', { class: 'dev-sub muted', text: sub }),
            appOld(v.app) ? el('div', { class: 'dev-old-note', text: `Versi lama. Perbarui ke ${android.version} lewat kartu Aplikasi Android.` }) : ''),
          btns);
      }));
    } catch (err) { box.textContent = err.message; }
  }

  // ---------- Hubungkan: peta tata surya ----------
  // Matahari = laptop (server yang dipakai semua perangkat). Orbit dalam = aktif sekarang, tengah = pernah
  // tersambung, luar = menunggu izin atau laptop lain di Wi-Fi ini, putus-putus = lewat Tailscale.
  // Berputar pelan (60-200 dtk per putaran) dan berhenti saat halaman tidak terlihat, "kurangi gerak", atau baterai lemah.
  let hubDevs = [];
  let hubPeers = [];
  let peersAt = 0;
  let hubSel = '';
  let hubSig = '';
  let hubView = store.get('db_hubview') === 'list' ? 'list' : 'map';
  const RING_T = [70, 110, 160, 200];
  const IC_PLANET = {
    lap: '<rect x="4" y="5" width="16" height="11" rx="1.5"/><path d="M2 19h20"/>',
    app: '<rect x="7" y="3" width="10" height="18" rx="2"/><path d="M11 18h2"/>',
    web: '<circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3c3 3.5 3 14.5 0 18M12 3c-3 3.5-3 14.5 0 18"/>',
  };
  const planetIcon = (k, s) => `<svg width="${s}" height="${s}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${IC_PLANET[k]}</svg>`;
  const osBadge = (os) => (/windows/i.test(os) ? 'W' : /linux|mint|ubuntu/i.test(os) ? 'L' : /mac/i.test(os) ? 'M' : '');

  async function loadHub(force = false) {
    const d = await api('/api/devices');
    devInfo = d;
    hubDevs = d.devices || [];
    if (force || Date.now() - peersAt > 15000) {
      peersAt = Date.now();
      try { hubPeers = (await api('/api/peers' + (force ? '?fresh=1' : ''))).peers || []; } catch { /* lama: tanpa laptop lain */ }
    }
    renderHub();
    if (hubView === 'list') renderDevices();
  }

  function hubPlanets() {
    const out = [];
    for (const v of hubDevs) {
      if (v.local) continue; // laptop ini sendiri = matahari
      const kind = v.pending ? 'pend' : v.pc ? 'lap' : v.app ? 'app' : 'web';
      const ring = v.pending ? 2 : v.online && v.tailscale ? 3 : v.online ? 0 : 1;
      const sub = v.pending ? 'Menunggu izin · ' + mmss(v.wait || 0)
        : (v.app ? 'Aplikasi ' + v.app : 'Browser') + ' · ' + (v.current ? 'HP ini' : v.online ? (v.tailscale ? 'lewat internet' : 'aktif') : ago(v.last_seen));
      out.push({ id: 'd' + v.id, dev: v, kind, ring, name: v.name, sub, ringRole: v.role === 'send' && !v.pending, dim: !v.online && !v.pending, badge: v.tailscale ? '↗' : '' });
    }
    for (const p of hubPeers) {
      out.push({ id: 'p' + (p.id || p.url), peer: p, kind: 'lap', ring: 2, name: p.name || p.url.replace('http://', ''), sub: (p.os || 'Laptop') + ' · Wi-Fi ini', badge: osBadge(p.os) });
    }
    return out;
  }

  function renderHub() {
    const planets = hubPlanets();
    const active = planets.filter((p) => p.ring === 0 || p.ring === 3).length;
    const pend = planets.filter((p) => p.kind === 'pend').length;
    $('#hubSum').textContent = [
      window.innerWidth < 520 ? 'Pusat: ' + (pcInfo.name || pcLabel()) + (isOnPc ? ' (laptop ini)' : ' (tujuan)') : '',
      active ? `${active} perangkat aktif` : 'Belum ada perangkat aktif',
      pend ? `${pend} menunggu izin` : '',
      hubPeers.length ? `${hubPeers.length} laptop lain di Wi-Fi ini` : '',
    ].filter(Boolean).join(' · ');
    $('#hvMap').setAttribute('aria-pressed', String(hubView === 'map'));
    $('#hvList').setAttribute('aria-pressed', String(hubView === 'list'));
    $('#hubMap').hidden = hubView !== 'map';
    $('#hubList').hidden = hubView !== 'list';
    renderPendingCards();
    if (hubSel && !planets.some((p) => p.id === hubSel) && hubSel !== 'sun') { hubSel = ''; }
    renderDetail(planets);
    if (hubView !== 'map') return;
    const box = $('#hubMap');
    const W = box.clientWidth || 600;
    const H = Math.round(Math.min(W, window.innerWidth >= 960 ? 640 : W * 1.02));
    const sig = JSON.stringify([W, H, hubSel, planets.map((p) => [p.id, p.ring, p.name, p.sub.replace(/\d+:\d+/, ''), p.ringRole, p.dim, p.kind]), pcInfo.name]);
    if (sig === hubSig) {
      // Hanya hitung mundur izin yang berubah: perbarui teksnya tanpa menggambar ulang (animasi tidak terulang)
      for (const p of planets) { const n = box.querySelector(`[data-pid="${CSS.escape(p.id)}"] small`); if (n) n.textContent = p.sub; }
      return;
    }
    hubSig = sig;
    box.style.height = H + 'px';
    const big = W >= 520;
    const sunPx = big ? 92 : 60;
    // Orbit dimulai di luar matahari (dan labelnya), berakhir dekat tepi peta
    const R = Math.min(W, H) / 2 - (big ? 64 : 30);
    const inner = sunPx / 2 + (big ? 56 : 34);
    const radius = (i) => inner + (R - inner) * [0, 0.38, 0.72, 1][i];
    const used = new Set(planets.map((p) => p.ring));
    used.add(0);
    const nodes = [];
    for (let i = 0; i < 4; i++) {
      if (!used.has(i)) continue;
      nodes.push(el('span', { class: 'orbit' + (i === 3 ? ' dash' : ''), style: `width:${2 * radius(i)}px;height:${2 * radius(i)}px` }));
    }
    const sun = el('button', { type: 'button', class: 'sun' + (hubSel === 'sun' ? ' sel' : ''), 'aria-label': (isOnPc ? 'Laptop ini' : 'Laptop tujuan') + ', tambah perangkat', onclick: () => selectPlanet('sun') });
    sun.innerHTML = `<span class="core" style="width:${sunPx}px;height:${sunPx}px">${planetIcon('lap', big ? 40 : 28)}</span>`;
    // Di layar kecil label matahari ada di atas peta (hubSum), supaya tidak tertimpa planet
    if (big) sun.append(el('span', { class: 'lbl' }, pcInfo.name || pcLabel(), el('small', { text: (pcInfo.os ? pcInfo.os + ' · ' : '') + (isOnPc ? 'laptop ini' : 'tujuan') })));
    nodes.push(sun);
    for (let ring = 0; ring < 4; ring++) {
      const list = planets.filter((p) => p.ring === ring).sort((a, b) => a.id.localeCompare(b.id));
      list.forEach((p, i) => {
        const ang = (360 / list.length) * i + ring * 47 + 20;
        const size = Math.round((big ? 58 : 44) - ring * (big ? 4 : 3));
        const r = radius(ring);
        const ball = el('span', { class: `ball ${p.kind}` + (p.ringRole ? ' ring' : '') + (hubSel === p.id ? ' sel' : ''), style: `width:${size}px;height:${size}px` });
        ball.innerHTML = planetIcon(p.kind === 'pend' ? 'app' : p.kind, Math.round(size * 0.46));
        if (p.badge) ball.append(el('span', { class: 'badge ' + (p.badge === '↗' ? 'ts' : 'os'), text: p.badge }));
        const pl = el('button', {
          type: 'button', class: 'pl' + (p.dim ? ' dim' : ''), role: 'listitem', 'data-pid': p.id,
          'aria-label': `${p.name}, ${p.sub}`, onclick: () => selectPlanet(p.id),
        }, ball, el('span', { class: 'lbl' }, p.name, el('small', { text: p.sub })));
        nodes.push(el('div', { class: 'spin', style: `--t:${RING_T[ring]}s;--a:${ang}deg` },
          el('div', { class: 'arm', style: `left:${r}px` }, pl)));
      });
    }
    if (!planets.length) {
      nodes.push(el('p', { class: 'space-empty', text: 'Belum ada HP yang terhubung. Scan QR di "Tambah perangkat".' }));
    }
    box.replaceChildren(...nodes);
    syncMotion();
  }

  // Gerak berhenti saat halaman tak terlihat atau baterai lemah (kurangi gerak diatur CSS)
  let lowBattery = false;
  function syncMotion() { $('#hubMap').classList.toggle('still', document.hidden || lowBattery || route.type !== 'more'); }
  document.addEventListener('visibilitychange', syncMotion);
  try {
    if (navigator.getBattery) navigator.getBattery().then((b) => {
      const upd = () => { lowBattery = !b.charging && b.level < 0.2; syncMotion(); };
      b.addEventListener('levelchange', upd); b.addEventListener('chargingchange', upd); upd();
    }).catch(() => {});
  } catch { /* tanpa info baterai */ }
  window.addEventListener('resize', () => { if (route.type === 'more') { hubSig = ''; renderHub(); } });

  function selectPlanet(id) {
    hubSel = hubSel === id && window.innerWidth >= 960 ? '' : id;
    hubSig = '';
    renderHub();
    if (window.innerWidth < 960 && hubSel) {
      if (hubSel === 'sun') { hubSel = ''; $('.add-card').scrollIntoView({ behavior: 'smooth', block: 'start' }); renderHub(); return; }
      $('#planetDlg').showModal();
    }
  }
  $('#planetClose').addEventListener('click', () => $('#planetDlg').close());
  $('#planetDlg').addEventListener('click', (e) => { if (e.target.id === 'planetDlg') e.target.close(); });
  $('#planetDlg').addEventListener('close', () => { hubSel = ''; hubSig = ''; if (route.type === 'more') renderHub(); });
  $('#hvMap').addEventListener('click', () => { hubView = 'map'; store.set('db_hubview', 'map'); hubSig = ''; renderHub(); });
  $('#hvList').addEventListener('click', () => { hubView = 'list'; store.set('db_hubview', 'list'); renderHub(); renderDevices(); });

  async function changeRole(v) {
    const to = v.role === 'full' ? 'send' : 'full';
    if (!confirm(`Ubah hak ${v.name} menjadi "${roleName(to)}"?`)) return;
    try { await api('/api/devices/' + v.id + '/role', { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify({ role: to }) }); toast('Hak diubah: ' + roleName(to)); loadHub(); }
    catch (err) { toast(err.message, 'err'); }
  }
  async function disconnect(v) {
    if (!confirm(`Putuskan ${v.name}? Untuk terhubung lagi, HP itu perlu PIN dan izin dari laptop ini.`)) return;
    try { await api('/api/devices/' + v.id, { method: 'DELETE' }); toast('Perangkat diputuskan'); hubSel = ''; if ($('#planetDlg').open) $('#planetDlg').close(); loadHub(); updateDevCount(); }
    catch (err) { toast(err.message, 'err'); }
  }

  // Isi detail planet terpilih: panel kanan (laptop) atau lembar bawah (HP)
  function detailNodes(p) {
    const head = (kind, name, sub, chip) => el('div', { class: 'hd-head' },
      (() => { const b = el('span', { class: 'ball ' + kind, style: 'width:48px;height:48px' }); b.innerHTML = planetIcon(kind === 'pend' ? 'app' : kind, 22); return b; })(),
      el('span', { class: 'hd-tx' }, el('b', { text: name }), el('small', { class: 'muted', text: sub })),
      chip || '');
    if (p.peer) {
      const x = p.peer;
      return [head('lap', p.name, [x.os, x.url.replace('http://', ''), x.version ? 'PC ' + x.version : ''].filter(Boolean).join(' · ')),
        el('p', { class: 'muted small', text: 'Laptop lain dengan DropBridge di Wi-Fi ini. Buka halamannya untuk mengirim file ke sana (perlu PIN laptop itu).' }),
        el('div', { class: 'hd-acts' + (isOnPc ? '' : ' one') },
          isOnPc ? el('button', { class: 'btn primary', type: 'button', text: 'Kirim ke sini', onclick: () => sendToPeer(x, p.name) }) : '',
          el('a', { class: 'btn', href: x.url, target: '_blank', rel: 'noopener', text: 'Buka ' + p.name }))];
    }
    const v = p.dev;
    const sub = [v.app ? 'Aplikasi ' + v.app : v.kind, v.ip, v.pending ? 'menunggu izin' : v.online ? (v.tailscale ? 'lewat internet' : 'aktif sekarang') : ago(v.last_seen)].join(' · ');
    const nodes = [head(p.kind, v.name + (v.current ? ' (HP ini)' : ''), sub, v.pending ? '' : el('span', { class: 'role-chip ' + v.role, text: roleName(v.role) }))];
    if (appOld(v.app)) nodes.push(el('p', { class: 'dev-old-note', text: `Versi lama. Perbarui ke ${android.version} lewat kartu Aplikasi Android.` }));
    if (myLocal && v.pending) {
      nodes.push(el('div', { class: 'hd-acts' },
        el('button', { class: 'btn', type: 'button', text: 'Tolak', onclick: () => answer(v, false) }),
        el('button', { class: 'btn primary', type: 'button', text: 'Izinkan…', onclick: () => { if ($('#planetDlg').open) $('#planetDlg').close(); openApprove(v); } })));
    } else if (myLocal) {
      nodes.push(el('div', { class: 'hd-acts' },
        el('button', { class: 'btn', type: 'button', text: v.role === 'full' ? 'Jadikan Hanya kirim' : 'Beri akses Penuh', onclick: () => changeRole(v) }),
        el('button', { class: 'btn danger', type: 'button', text: 'Putuskan', onclick: () => disconnect(v) })));
    } else if (!v.current) {
      nodes.push(el('p', { class: 'muted small', text: 'Hak dan pemutusan perangkat diatur dari laptop.' }));
    }
    return nodes;
  }

  // Dari peta: jadikan laptop itu tujuan kirim (PIN dulu bila belum tersambung)
  async function sendToPeer(x, name) {
    if ($('#planetDlg').open) $('#planetDlg').close();
    try { tgtPeers = (await api('/api/remote/peers')).peers || []; } catch { /* pakai yang ada */ }
    const p = tgtPeers.find((q) => q.id === x.id) || { ...x, paired: false };
    if (p.paired) { setTgt({ t: 'peer', id: p.id, name, os: p.os || '' }); location.hash = '#/'; toast('Tujuan: ' + name); }
    else askPeerPin({ ...p, name });
  }

  function renderDetail(planets) {
    const p = planets.find((x) => x.id === hubSel);
    const box = $('#hubDetail');
    box.hidden = !p;
    if (!p) { if ($('#planetDlg').open && hubSel !== 'sun') $('#planetDlg').close(); return; }
    box.replaceChildren(...detailNodes(p));
    $('#planetTitle').textContent = p.peer ? 'Laptop' : p.dev.pending ? 'Menunggu izin' : 'Perangkat';
    $('#planetBody').replaceChildren(...detailNodes(p));
  }

  // Kartu "ingin terhubung" di samping peta (hanya laptop ini yang bisa menjawab)
  function renderPendingCards() {
    const box = $('#hubPending');
    const list = myLocal ? hubDevs.filter((v) => v.pending) : [];
    box.replaceChildren(...list.map((v) => el('section', { class: 'card pend-card' },
      (() => { const b = el('span', { class: 'ball pend', style: 'width:44px;height:44px' }); b.innerHTML = planetIcon('app', 20); return b; })(),
      el('span', { class: 'hd-tx' }, el('b', { text: v.name + ' ingin terhubung' }), el('small', { class: 'muted', text: 'PIN benar · otomatis ditolak ' + mmss(v.wait || 0) })),
      el('button', { class: 'btn small', type: 'button', text: 'Tolak', onclick: () => answer(v, false) }),
      el('button', { class: 'btn small primary', type: 'button', text: 'Izinkan…', onclick: () => openApprove(v) }))));
  }

  // ---------- Izin HP baru (hanya di PC ini) ----------
  let apDev = null;
  let apRole = 'send';
  let apTimer = 0;
  let pendingTimer = 0;
  const apShown = new Set();
  const mmss = (n) => Math.floor(n / 60) + ':' + String(n % 60).padStart(2, '0');

  function startPendingWatch() {
    if (pendingTimer) return;
    const tick = async () => {
      if (!loggedIn || !myLocal) return;
      try {
        const d = await api('/api/devices/pending');
        const next = (d.pending || [])[0];
        // Munculkan sekali per permintaan; kalau ditutup, jawab lewat kartu di Ekosistem
        if (next && !$('#approveDlg').open && !apShown.has(next.id)) { apShown.add(next.id); openApprove(next); }
        if (!next && $('#approveDlg').open) $('#approveDlg').close();
        if (route.type === 'more') loadHub().catch(() => {});
      } catch { /* abaikan */ }
    };
    tick();
    pendingTimer = setInterval(tick, 2500);
  }

  function setApRole(r) {
    apRole = r;
    for (const b of $$('.ap-roles button')) b.setAttribute('aria-checked', String(b.dataset.role === r));
  }
  for (const b of $$('.ap-roles button')) b.addEventListener('click', () => setApRole(b.dataset.role));

  function openApprove(v) {
    apDev = v;
    setApRole('send');
    $('#apSub').textContent = [v.name, v.name === v.kind ? '' : v.kind, v.ip, 'PIN benar'].filter(Boolean).join(' · ');
    let left = v.wait || 120;
    $('#apLeft').textContent = mmss(left);
    clearInterval(apTimer);
    apTimer = setInterval(() => {
      left = Math.max(0, left - 1);
      $('#apLeft').textContent = mmss(left);
      if (!left) { clearInterval(apTimer); if ($('#approveDlg').open) $('#approveDlg').close(); }
    }, 1000);
    if (!$('#approveDlg').open) $('#approveDlg').showModal();
    $('#apAllow').focus();
  }

  async function answer(v, allow) {
    try {
      if (allow) await api('/api/devices/' + v.id + '/approve', { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify({ role: apRole }) });
      else await api('/api/devices/' + v.id + '/reject', { method: 'POST' });
      toast(allow ? `${v.name} diizinkan (${roleName(apRole)})` : `${v.name} ditolak`);
    } catch (err) { toast(err.message, 'err'); }
    clearInterval(apTimer);
    if ($('#approveDlg').open) $('#approveDlg').close();
    apDev = null;
    updateDevCount();
    if (route.type === 'more') loadHub().catch(() => {});
  }
  $('#apAllow').addEventListener('click', () => { if (apDev) answer(apDev, true); });
  $('#apReject').addEventListener('click', () => { if (apDev) answer(apDev, false); });
  $('#approveDlg').addEventListener('cancel', (e) => { e.preventDefault(); if (apDev) answer(apDev, false); });

  $('#approveChk').addEventListener('change', async (e) => {
    try { await api('/api/settings/approve', { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify({ on: e.target.checked }) }); }
    catch (err) { toast(err.message, 'err'); e.target.checked = !e.target.checked; }
  });

  $('#pinBtn').addEventListener('click', async () => {
    if (!confirm('Ganti PIN dengan angka acak baru?\n\nHP yang sudah terhubung tetap terhubung. HP baru memakai PIN baru.')) return;
    try {
      const r = await api('/api/pin', { method: 'POST', headers: JSON_HEADERS, body: '{}' });
      toast('PIN baru: ' + r.pin);
      $('#pinNow').textContent = String(r.pin).replace(/^(\d{3})(\d{3})$/, '$1 $2');
      renderAddr();
    } catch (err) { toast(err.message, 'err'); }
  });

  $('#renameBtn').addEventListener('click', openName);
  $('#devOthers').addEventListener('click', async () => {
    if (!confirm('Putuskan semua HP? Untuk terhubung lagi, mereka perlu PIN dan izin dari laptop ini.')) return;
    try { const r = await api('/api/devices/revoke-others', { method: 'POST' }); toast(r.revoked + ' perangkat diputuskan'); loadHub().catch(() => {}); updateDevCount(); }
    catch (err) { toast(err.message, 'err'); }
  });
  setInterval(() => { if (loggedIn && !document.hidden) updateDevCount(); }, 15000);

  $('#loginForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const pin = $('#pin').value.trim();
    if (!pin) return;
    try {
      $('#pin').value = '';
      await doLogin(pin);
    } catch (err) {
      $('#loginError').textContent = err.message;
      $('#pin').select();
    }
  });

  // Kode perangkat tetap: PC mengingat izin perangkat ini, jadi login ulang tidak perlu izin lagi
  function deviceId() {
    let id = store.get('db_devid');
    if (!id) { id = 'w' + Math.random().toString(36).slice(2) + Date.now().toString(36); store.set('db_devid', id); }
    return id;
  }

  async function doLogin(pin) {
    const r = await api('/api/login', { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify({ pin, name: store.get('db_devname'), device: deviceId() }) });
    if (r.pending) waitApproval(); else showApp();
  }

  let waitTimer = 0;
  function waitApproval() {
    loggedIn = false;
    $('#app').hidden = true;
    $('#login').hidden = false;
    $('#loginForm').hidden = true;
    $('#waitBox').hidden = false;
    clearInterval(waitTimer);
    const stop = (msg) => {
      clearInterval(waitTimer);
      $('#waitBox').hidden = true;
      $('#loginForm').hidden = false;
      showLogin();
      if (msg) $('#loginError').textContent = msg;
    };
    const poll = async () => {
      try {
        const r = await fetch('/api/session', { credentials: 'same-origin' });
        if (r.status === 401) {
          let d = {}; try { d = await r.json(); } catch { /* abaikan */ }
          stop(typeof d.detail === 'string' && d.detail !== 'Belum login.' ? d.detail : 'Waktu tunggu habis. Masukkan PIN lagi.');
          return;
        }
        const d = await r.json();
        if (!d.pending) { clearInterval(waitTimer); $('#waitBox').hidden = true; $('#loginForm').hidden = false; showApp(); return; }
        $('#waitLeft').textContent = mmss(d.wait || 0);
      } catch { /* jaringan: coba lagi */ }
    };
    poll();
    waitTimer = setInterval(poll, 2000);
    $('#waitCancel').onclick = async () => {
      try { await fetch('/api/logout', { method: 'POST', credentials: 'same-origin' }); } catch { /* abaikan */ }
      stop('');
    };
  }

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
        await doLogin(qpin.trim());
        return;
      } catch { /* lanjut ke login biasa */ }
    }
    try {
      const r = await fetch('/api/session', { credentials: 'same-origin' });
      if (r.ok) { const d = await r.json(); if (d.pending) waitApproval(); else showApp(); } else showLogin();
    } catch {
      showLogin();
      $('#loginError').textContent = 'Tidak bisa terhubung ke PC. Cek Wi-Fi dan alamatnya.';
    }
  })();
})();
