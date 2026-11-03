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
  const isOnPc = ['localhost', '127.0.0.1', '[::1]'].includes(location.hostname);
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
  const cleanFolder = (s) => s
    .replace(/[<>:"/\\|?*\u0000-\u001f]/g, '_')
    .replace(/\s+/g, ' ')
    .trim()
    .replace(/^[. ]+|[. ]+$/g, '')
    .slice(0, 100);

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
    const dot = $('#dot');
    dot.classList.toggle('off', !ok);
    dot.title = ok ? 'Terhubung ke PC' : 'Terputus dari PC';
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
    $('#login').hidden = true;
    $('#app').hidden = false;
    $('#loginError').textContent = '';
    foldersSig = filesSig = clipsSig = '';
    applyRoute();
    refresh();
    clearInterval(timer);
    timer = setInterval(() => { if (!document.hidden) refresh(); }, 4000);
  }

  function renderAddr() {
    for (const box of $$('[data-addr]')) {
      box.replaceChildren();
      if (!isOnPc || !urls.length) { box.hidden = true; continue; }
      box.hidden = false;
      box.append(
        el('div', { class: 'addr-title', text: 'Buka salah satu alamat ini di browser HP (ketuk untuk menyalin):' }),
        el('div', { class: 'addr-list' },
          urls.map((u) => el('button', { type: 'button', text: u, onclick: async () => toast((await copyText(u)) ? 'Alamat disalin' : 'Gagal menyalin') })))
      );
    }
  }

  async function loadInfo() {
    try {
      const r = await fetch('/api/info');
      const d = await r.json();
      urls = d.urls || [];
      maxMB = d.max_upload_mb || maxMB;
      renderAddr();
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
        }, el('span', { text: f.name }), el('span', { class: 'cnt', text: f.count + ' file' })))));
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
      const parts = [];
      const exist = findFolder(cleaned);
      if (cleaned !== raw.trim()) parts.push(el('div', { class: 'sub', text: 'Nama disesuaikan menjadi: ' + cleaned }));
      if (exist) {
        hintEl.classList.add('ok');
        parts.unshift(el('div', { text: `Folder sudah ada · ${exist.count} file` }));
      } else {
        parts.unshift(el('div', { text: 'Folder baru, dibuat saat file dikirim.' }));
        if (cleaned.length >= 4) {
          const sim = folders.filter((f) => withinOneEdit(f.name, cleaned)).slice(0, 3);
          if (sim.length) {
            hintEl.classList.add('warn');
            parts.push(el('div', {},
              'Mirip dengan: ',
              sim.map((f) => el('button', { type: 'button', class: 'link', text: f.name, onclick: () => choose(f.name) })),
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

  function renderRecent() {
    const box = $('#recent');
    box.replaceChildren(...folders.slice(0, 5).map((f) =>
      el('button', { type: 'button', class: 'chip', text: f.name, title: f.name, onclick: () => { setTarget(f.name); store.set('db_target', f.name); } })));
  }

  // ---------- Rute ----------
  function parseHash() {
    const h = location.hash;
    if (h === '#/root') return { type: 'root', name: '' };
    if (h.startsWith('#/f/')) {
      try { return { type: 'folder', name: decodeURIComponent(h.slice(4)) }; } catch { /* jatuh ke beranda */ }
    }
    return { type: 'home', name: '' };
  }

  function applyRoute() {
    route = parseHash();
    files = [];
    filesSig = '';
    const home = route.type === 'home';
    $('#homeView').hidden = !home;
    $('#folderView').hidden = home;
    if (route.type === 'folder') {
      setTarget(route.name);
      store.set('db_target', route.name);
    }
    if (!home) {
      const root = route.type === 'root';
      $('#fvTitle').textContent = root ? 'Tanpa folder' : route.name;
      $('#fvMeta').textContent = '';
      $('#fvZip').hidden = root;
      $('#fvRename').hidden = root;
      $('#fvDelete').hidden = root;
      if (!root) $('#fvZip').href = '/api/folders/' + enc(route.name) + '/zip';
      $('#grid').replaceChildren();
      $$('.tabs button')[0].click();
      loadFiles().catch(() => {});
    } else {
      renderHome();
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
      $('#folderCount').textContent = folders.length || '';
      renderRecent();
      sendCombo.updateHint();
      if (route.type === 'home') renderHome();
    }
  }

  function folderRow(href, name, count, size, modified) {
    return el('li', { class: 'frow' },
      el('a', { class: 'frow-main', href },
        el('div', { class: 'ext', 'data-kind': 'folder' }),
        el('div', { class: 'meta' },
          el('div', { class: 'name', text: name, title: name }),
          el('div', { class: 'sub', text: `${count} file · ${fmtSize(size)}` + (modified ? ' · ' + fmtTime(modified) : '') })),
        el('span', { class: 'chev', text: '›' })));
  }

  function renderHome() {
    const list = $('#folderList');
    list.replaceChildren();
    const q = $('#search').value.trim().toLowerCase();
    const shown = folders.filter((f) => !q || f.name.toLowerCase().includes(q));
    for (const f of shown) list.append(folderRow('#/f/' + enc(f.name), f.name, f.count, f.size, f.modified));
    if (!q && rootInfo.count > 0) list.append(folderRow('#/root', 'Tanpa folder', rootInfo.count, rootInfo.size, rootInfo.modified));
    if (!list.children.length) {
      list.append(el('li', { class: 'empty', text: q
        ? 'Tidak ada folder yang cocok.'
        : 'Belum ada folder. Ketik nama folder di atas, lalu pilih file untuk dikirim.' }));
    }
  }
  $('#search').addEventListener('input', renderHome);

  // ---------- Isi folder ----------
  async function loadFiles() {
    if (route.type === 'home') return;
    const asked = route;
    let d;
    try {
      d = await api('/api/files?folder=' + enc(curFolder()));
    } catch (e) {
      if (e.status === 404 && asked === route) {
        toast('Folder tidak ditemukan', 'err');
        location.hash = '#/';
        return;
      }
      throw e;
    }
    if (asked !== route) return; // pengguna sudah pindah halaman
    const sig = JSON.stringify(d.files) + Math.floor(Date.now() / 60000);
    files = d.files;
    if (sig !== filesSig) { filesSig = sig; renderGrid(); }
  }

  function renderGrid() {
    const grid = $('#grid');
    grid.replaceChildren();
    const total = files.reduce((n, f) => n + f.size, 0);
    $('#fvMeta').textContent = `${files.length} file · ${fmtSize(total)}`;
    if (!files.length) {
      grid.append(el('div', { class: 'empty', text: 'Folder ini kosong.' }));
      return;
    }
    const folder = curFolder();
    for (const f of files) {
      const url = '/api/files/' + enc(f.name) + '?folder=' + enc(folder);
      const badge = () => el('div', { class: 'ext big', 'data-kind': f.kind, text: extOf(f.name) });
      const main = el('button', {
        type: 'button',
        class: 'tile-main',
        title: f.name,
        onclick: () => (f.previewable ? openPreview(f) : (location.href = url)),
      });
      if (f.thumb) {
        const img = el('img', { class: 'thumb', loading: 'lazy', alt: f.name, src: `/api/files/${enc(f.name)}/thumb?folder=${enc(folder)}` });
        img.addEventListener('error', () => img.replaceWith(badge()), { once: true });
        main.append(img);
      } else {
        main.append(badge());
      }
      grid.append(el('div', { class: 'tile' },
        main,
        el('div', { class: 'tile-name', text: f.name, title: f.name }),
        el('div', { class: 'tile-sub', text: fmtSize(f.size) + ' · ' + fmtTime(f.modified) }),
        el('div', { class: 'tile-actions' },
          el('a', { class: 'btn', href: url, download: f.name, text: 'Unduh' }),
          el('button', { class: 'btn', type: 'button', text: 'Pindah', onclick: () => openMove(f) }),
          el('button', { class: 'btn danger', type: 'button', text: 'Hapus', onclick: () => removeFile(f) }))));
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
  $('#fvRename').addEventListener('click', async () => {
    const old = route.name;
    const next = prompt('Nama baru untuk folder ini:', old);
    if (next == null || !next.trim() || next.trim() === old) return;
    try {
      const d = await api('/api/folders/' + enc(old), { method: 'PATCH', headers: JSON_HEADERS, body: JSON.stringify({ name: next }) });
      if ($('#folderInput').value === old) { setTarget(d.name); store.set('db_target', d.name); }
      toast('Nama folder diganti');
      location.hash = '#/f/' + enc(d.name);
      loadFolders().catch(() => {});
    } catch (e) { toast(e.message, 'err'); }
  });

  $('#fvDelete').addEventListener('click', async () => {
    const name = route.name;
    let count = files.length;
    for (let attempt = 0; attempt < 3; attempt++) {
      const ok = confirm(`Hapus folder "${name}" beserta ${count} file di dalamnya dari PC?\n\nTindakan ini permanen dan tidak bisa dibatalkan.`);
      if (!ok) return;
      try {
        await api('/api/folders/' + enc(name) + '?expect=' + count, { method: 'DELETE' });
        if ($('#folderInput').value === name) setTarget('');
        if (store.get('db_target') === name) store.set('db_target', '');
        toast('Folder dihapus');
        location.hash = '#/';
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
  });

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

  function targetFolder() {
    const raw = $('#folderInput').value;
    if (!raw.trim()) return null;
    return cleanFolder(raw) || null;
  }

  // Mengembalikan false bila nama folder belum diisi.
  function enqueue(list) {
    const arr = Array.from(list);
    if (!arr.length) return false;
    const folder = targetFolder();
    if (!folder) {
      toast('Isi nama folder tujuan dulu', 'err');
      $('#folderInput').focus();
      return false;
    }
    for (const file of arr) {
      const row = el('li', { class: 'up' },
        el('div', { class: 'up-name', text: file.name, title: file.name }),
        el('div', { class: 'up-state', text: `Menunggu → ${folder}` }),
        el('div', { class: 'bar' }, el('i')));
      $('#uploads').prepend(row);
      if (file.size > maxMB * 1024 * 1024) {
        fail(row, `Terlalu besar (maks ${maxMB} MB)`);
        continue;
      }
      queue.push({ file, row, folder });
    }
    runQueue();
    return true;
  }

  function fail(row, msg) {
    row.classList.add('err');
    $('.up-state', row).textContent = msg + ' (ketuk untuk menutup)';
    row.addEventListener('click', () => row.remove(), { once: true });
  }

  async function runQueue() {
    if (uploading) return;
    uploading = true;
    while (queue.length) {
      const job = queue.shift();
      await uploadOne(job);
    }
    uploading = false;
    if (loggedIn) refresh();
  }

  function uploadOne({ file, row, folder }) {
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
      let tSent = 0;
      xhr.upload.onload = () => { tSent = performance.now(); state.textContent = 'Terkirim, menunggu PC menyimpan...'; };
      xhr.upload.onprogress = (e) => {
        if (!e.lengthComputable || tSent) return;
        const pct = Math.round((e.loaded / e.total) * 100);
        bar.style.width = pct + '%';
        state.textContent = `${pct}% · ${fmtSize(e.loaded)} / ${fmtSize(e.total)}`;
      };
      xhr.onload = () => {
        if (xhr.status >= 200 && xhr.status < 300) {
          let d = {};
          try { d = JSON.parse(xhr.responseText); } catch { /* abaikan */ }
          const real = d.folder || folder;
          if (d.errors && d.errors.length && !(d.saved && d.saved.length)) {
            fail(row, String(d.errors[0]));
          } else {
            row.classList.add('done');
            const now = performance.now();
            const sec = (ms) => (ms / 1000).toFixed(1) + ' dtk';
            state.textContent = `Selesai · ${fmtSize(file.size)} → ${real} · kirim ${sec((tSent || now) - t0)}, tunggu ${sec(tSent ? now - tSent : 0)}` + (typeof d.took === 'number' ? `, PC simpan ${d.took.toFixed(2)} dtk` : '');
            setTimeout(() => row.remove(), 15000);
            store.set('db_target', real);
            if ($('#folderInput').value.trim() && cleanFolder($('#folderInput').value).toLowerCase() === folder.toLowerCase()) {
              $('#folderInput').value = real; // pakai nama final dari server
            }
          }
          if (loggedIn) loadFolders().then(() => { sendCombo.updateHint(); }).catch(() => {});
          if (loggedIn && route.type !== 'home') loadFiles().catch(() => {});
        } else if (xhr.status === 401) {
          showLogin();
          fail(row, 'Sesi berakhir');
        } else {
          let msg = 'Gagal (' + xhr.status + ')';
          try { const dt = JSON.parse(xhr.responseText).detail; if (typeof dt === 'string') msg = dt; } catch { /* abaikan */ }
          fail(row, msg);
        }
        resolve();
      };
      xhr.onerror = () => { fail(row, 'Gagal terhubung (koneksi putus?)'); resolve(); };
      xhr.send(file);
    });
  }

  $('#pick').addEventListener('click', () => {
    if (!targetFolder()) {
      toast('Isi nama folder tujuan dulu', 'err');
      $('#folderInput').focus();
      return;
    }
    $('#picker').click();
  });
  $('#picker').addEventListener('change', (e) => { enqueue(e.target.files); e.target.value = ''; });
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

  // ---------- Tab ----------
  for (const btn of $$('.tabs button')) {
    btn.addEventListener('click', () => {
      for (const b of $$('.tabs button')) b.setAttribute('aria-selected', String(b === btn));
      for (const p of $$('.panel')) p.classList.toggle('active', p.dataset.panel === btn.dataset.tab);
    });
  }

  // ---------- Login / sesi ----------
  async function refresh() {
    try {
      const jobs = [loadFolders(), loadClips()];
      if (route.type !== 'home') jobs.push(loadFiles());
      await Promise.all(jobs);
      setOnline(true);
    } catch (e) {
      if (e instanceof TypeError) setOnline(false); // jaringan putus
    }
  }

  $('#loginForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const pin = $('#pin').value.trim();
    if (!pin) return;
    try {
      await api('/api/login', { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify({ pin }) });
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
    try {
      const r = await fetch('/api/session', { credentials: 'same-origin' });
      if (r.ok) showApp(); else showLogin();
    } catch {
      showLogin();
      $('#loginError').textContent = 'Tidak bisa terhubung ke PC. Cek Wi-Fi dan alamatnya.';
    }
  })();
})();
