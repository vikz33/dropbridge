# DropBridge — Ringkasan Proyek untuk Melanjutkan

> Dokumen serah-terima untuk Claude di chat lain. Baca seluruhnya sebelum mengerjakan apa pun.
> Kondisi per **16 November 2026**, versi terakhir **PC 3.3.0 · Android 3.3.0** (commit `c094def`).

---

## 0. Aturan kerja dengan pengguna (WAJIB)

1. **Diskusi dulu, kerjakan belakangan.** Jangan mengubah kode sebelum pengguna memberi perintah eksplisit: "kerjakan", "gas", "gas, build", "lanjut". Kalimat seperti "kita diskusikan dulu" berarti hanya diskusi.
2. **Selalu sebut nomor versi per bagian**, misalnya "PC 3.3.0 · Android 3.3.0", supaya APK/installer tidak tertukar.
3. **Major.minor PC dan Android harus sama** (3.3.x ↔ 3.3.x). Kalau hanya satu sisi berubah, sisi lain tetap ikut naik nomor supaya cocok.
4. **Tampilan web dan aplikasi Android dibuat semirip mungkin.** Fitur baru dibuat untuk keduanya, kecuali memang khusus HP.
5. **Lokasi penyimpanan hanya bisa diganti dari laptop**, bukan dari HP.
6. **Jangan menyentuh struktur folder Documents 01–99** milik pengguna (folder rapi yang ia kurasi sendiri).
7. **Sebelum fitur yang mengubah tampilan, buat ilustrasi dulu** (lihat bagian 7) dan minta persetujuan.
8. Bahasa: **Bahasa Indonesia**, singkat, jelas. Pengguna bukan programmer penuh waktu; jelaskan dengan bahasa awam, beri tabel/daftar pilihan saat meminta keputusan.
9. Pengguna minta **hemat token**: jangan membaca ulang file besar tanpa perlu, kerjakan langsung setelah disetujui.
10. Linux tidak ditinggal: laptop Windows dan Linux Mint memakai kode PC-App + Web yang sama.

---

## 1. Apa itu DropBridge (non-teknis)

- Aplikasi **kirim file HP ↔ laptop lewat Wi-Fi yang sama, tanpa internet**, seperti AirDrop tetapi lintas merek (pengguna memakai HP Huawei/Honor; Quick Share tidak bisa ke iPhone).
- **Kebutuhan kerja utama:** pengguna memeriksa barang di gudang, memotret dokumen/barang, lalu foto harus langsung masuk ke folder per dokumen di laptop (contoh nama folder: `TCKU 6177329/30-23`, `27 012345`). Laptop sering jauh dari gudang, jadi **file ditahan di antrean HP dan terkirim otomatis saat tersambung**.
- Foto kerja dibatasi < 512 KB → ada fitur **Foto kecil** (opsional, karena foto pribadi perlu resolusi asli).
- Juga dipakai untuk **berbagi ke HP teman tanpa aplikasi** (HP jadi "host", teman scan QR, buka di browser). Pemakaian host ±1:1 dengan kirim ke laptop.
- Akses dari luar jaringan memakai **Tailscale** (laptop pribadi, hanya dari HP sendiri).
- Pengguna punya 2 laptop kerja: **Windows** (utama) dan **Linux Mint**. Folder antar laptop disinkron dengan Syncthing (di luar DropBridge).
- Tahap: pilot dipakai sendiri; rilis publik nanti bila stabil. Repo GitHub publik: `github.com/vikz33/dropbridge`.

---

## 2. Struktur repo

Lokasi di laptop pengguna: `C:\Dev\DropBridge` (satu repo Git untuk semua bagian, branch `main`).

```
DropBridge/
├─ PC-App/            Server laptop (Python FastAPI) + jendela desktop + tray + installer
│  ├─ run.py          jalankan server (uvicorn ws="none", http="h11", loop="asyncio")
│  ├─ desktop.py      jendela WebView2 + tray + satu instance + notifikasi
│  ├─ start.bat/.sh   mode pengembangan (selalu versi terbaru)
│  ├─ build_exe.bat   PyInstaller + Inno Setup → DropBridge-Setup.exe
│  ├─ build_deb.sh    paket .deb untuk Linux Mint
│  └─ app/
│     ├─ version.py   VERSION = "3.3.0"
│     ├─ main.py      app FastAPI, memasang semua router
│     ├─ auth.py      PIN, sesi, izin perangkat (trusted.json), hak full/send, known_phones()
│     ├─ config.py    settings (data_dir di AppData / ~/.local/share/DropBridge, config.json)
│     ├─ discovery.py siaran UDP "DROPBRIDGE?" port 47800, peers() (cache 15 dtk)
│     ├─ inbox.py     kelompok file masuk per pengirim (GET /api/files/incoming)
│     ├─ events.py, netinfo.py, icon.py
│     ├─ routes/      files.py, folders.py, clipboard.py (Teks), system.py, outbox.py, remote.py
│     └─ services/    storage.py, storage_admin.py, thumbs.py
├─ Web/               UI web (dipakai browser HP DAN jendela laptop): index.html, app.js, style.css, tokens.css, fonts/
├─ Android-App/       Aplikasi Android (Kotlin, tanpa XML layout; UI dibuat di kode)
│  └─ app/src/main/java/com/dropbridge/app/  (lihat bagian 4)
├─ design/            tokens.json (SUMBER TUNGGAL warna/ukuran), build_tokens.py → Web/tokens.css + Android Tokens.kt
├─ build_semua.bat    build APK + exe sekaligus
├─ README.md          dokumentasi pengguna + catatan per versi
└─ RINGKASAN_PROYEK.md  (file ini)
```

---

## 3. Server PC-App (teknis)

- **Login:** HP memasukkan PIN laptop → sesi. Perangkat baru bisa berstatus `pending` (laptop harus menekan Izinkan). Hak: `full` (lihat/kelola file) atau `send` (hanya kirim). Kunci perangkat tetap = `dev`.
- Helper: `require_auth`, `require_full`, `_pc_only` (hanya dari laptop itu sendiri, di `routes/system.py`), `is_this_pc`.
- `auth._kind_of` → jenis perangkat; User-Agent `DropBridge-PC/<ver>` dianggap "Laptop".
- **Endpoint penting:**
  - File: `/api/files/upload-raw?folder&name&last_modified` (upload bisa dilanjutkan, cegah duplikat), `/api/files/incoming?since=`
  - Folder: `/api/folders` (bertingkat maks 3, "Tanpa folder" = folder `""`)
  - Teks: `/api/clips` (GET/DELETE butuh full; POST semua), `POST /api/clips/{id}/restore` (urungkan 30 dtk)
  - Laptop → laptop (relay): `/api/remote/*` → peers, login (PIN + izin di laptop tujuan), status, folders, upload. Token di `data/peers.json`.
  - Laptop → HP (model tarik / outbox): `/api/outbox/*`. File di `data/outbox/<devkey>/<id>.bin`, indeks `data/outbox.json`. HP aplikasi mengambil sendiri; browser lewat kartu Unduh (`?mark=1`). Menunggu 7 hari, lalu dibuang.
  - `/api/info`, `/api/session`, `/api/peers`, `/api/storage/open-data`.

## 4. Aplikasi Android (teknis)

File utama (paket `com.dropbridge.app`):

| File | Isi |
|---|---|
| `MainActivity` | Tab **Kirim**: kartu tujuan (laptop+folder ATAU HP tanpa aplikasi/QR), Foto langsung, Pilih file, baris ikon (Semua folder, Muat ulang, Teks, Antrean, Foto kecil, Tambah perangkat) |
| `BrowseActivity` | Tab **File**: daftar/ikon, isi folder, unduh + penanda "sudah di HP" (Downloaded.kt) |
| `HostActivity` | Tab **Ekosistem**: peta tata surya (OrbitMap), kartu Laptop (Ganti, isi alamat manual), bagian HP lain (Berbagi dengan HP lain + tamu) |
| `MoreActivity` | Pengaturan: Perangkat ini, Pengiriman, Tentang (versi cocok?), Keluar |
| `OrbitMap` | peta planet; ketuk planet → detail → **Kirim ke sini** |
| `Beat` | cek denyut laptop tiap 10 dtk saat layar terlihat; gagal 2× = tidak terjangkau |
| `HostServer`/`HostService`/`Hotspot` | server mini di HP untuk tamu browser (port 8080–8090, kode 6 angka), `Guest` = tamu tanpa identitas, hilang 2 menit |
| `Net`, `Conn`, `Pcs`, `PcPicker` | cari laptop (UDP), login, simpan banyak laptop, lembar "Kirim ke mana?" |
| `Queue`, `Uploader`, `UploadService`, `AutoSend` | antrean + kirim lanjut + kirim otomatis (JobScheduler) |
| `Inbox` (+`InboxJob`) | ambil kiriman laptop→HP (tiap 10 dtk saat terbuka, tiap 15 mnt di latar) |
| `Ui`, `Tokens` | komponen UI, warna dari tokens.json, font Plus Jakarta Sans + IBM Plex Mono (offline) |
| `Prefs` | penyimpanan; `sendTo` = "pc"/"qr", `hubList`, `receiveFromPc`, dll |

- Warna: **teal = laptop/utama, indigo = HP lain/berbagi**. Mode gelap/terang/ikuti HP.
- `Status.line` (teks status lama) masih dipakai; untuk "terjangkau?" pakai **`Beat.alive()`**, jangan `Status.line.startsWith("Tersambung")`.

## 5. Web

- Satu halaman (`index.html` + `app.js`), rute hash: `#/` Kirim, `#/folders` File, `#/ekosistem` (alias lama `#/hubungkan`).
- Laptop: "Kirim ke mana?" (laptop ini, laptop lain lewat relay, HP lewat outbox). HP browser: kartu Unduh untuk file dari laptop.
- Peta tata surya pakai CSS rotation, berhenti saat tab tersembunyi/baterai < 20%/reduced motion.

---

## 6. Riwayat versi (ringkas)

| Versi | Isi |
|---|---|
| v1–v2 | server FastAPI + web; folder per barang, thumbnail, ZIP; Android pendamping; antrean + kirim otomatis; mode host HP; hotspot + QR; Tailscale; folder bertingkat; installer .exe; Linux .deb |
| 2.5.x | izin perangkat + hak per perangkat, pilih banyak PC, "Tanpa folder" paling atas |
| 2.6.0 | kirim laptop → laptop |
| **3.0.0** | sistem desain baru (tokens), Home bersih, Teks (log di HP, salin, hapus+urungkan), Pengaturan baru |
| **3.1.0** | Hubungkan sebagai **tata surya** |
| **3.2.0** | laptop → laptop lain (relay), laptop → HP (outbox), penanda file sudah di HP (Pasang/Buka/lokasi) |
| **3.2.1** | cek denyut 10 dtk (status laptop tidak basi), tamu browser tampil di peta & HP lain, "Host aktif" → "Berbagi aktif" |
| **3.3.0** | tab **Ekosistem** (ikon orbit), ikon Kirim pesawat kertas; **Kirim › Ganti → Kirim ke mana?** (Laptop / HP tanpa aplikasi lewat QR, QR langsung di tab Kirim); planet → Kirim ke sini; peringatan laptop tidak terjangkau + antrean |

Catatan: fitur Android 3.2.0–3.3.0 baru **lolos kompilasi**, belum terbukti di HP sungguhan (lihat bagian 9).

---

## 7. Cara kerja teknis Claude sebelumnya (alur kerja)

- Salinan kerja di cloud: `/mnt/user-data/outputs/DropBridge` (mungkin tidak ada di sesi baru — kalau tidak ada, kerjakan langsung di laptop lewat jembatan perangkat).
- **Laptop pengguna** dijangkau lewat alat perangkat jarak jauh (device bridge). Di sana repo terlihat sebagai `~/mnt/Dev/DropBridge` (= `C:\Dev\DropBridge`). Hapus file butuh izin hapus (sudah pernah diberikan untuk `C:\Dev`, per sesi).
- Sinkron: buat tar file yang berubah → kirim ke laptop → `tar --overwrite -xzf` → cocokkan md5 → hapus tar → commit.
- **Commit** memakai identitas: `git -c user.name=vikz33 -c user.email=nudistian.contact@gmail.com commit ...` dan diakhiri baris:
  ```
  Co-Authored-By: Claude <noreply@anthropic.com>
  ```
  (sesuaikan dengan baris atribusi sesi baru). Jangan pernah membagikan email pribadi pengguna ke layanan luar.
- **Push dilakukan pengguna sendiri** (`git push`). Per 16 Nov ada 6 commit belum ter-push.
- Cek kompilasi Android tanpa Gradle: `kotlinc -cp android34.jar *.kt` (android.jar API 34). Build APK sungguhan dilakukan pengguna lewat `build_semua.bat`.
- Uji web: jalankan `PC-App/run.py` dengan HOME terpisah (port 8765), uji pakai Playwright; HP disimulasikan dengan header `x-forwarded-for` + host `192.0.2.2`.
- **Ilustrasi desain** ada di kanvas Design "DropBridge Remake": https://claude.ai/artifact/YY9Szic7Nebz9i3fBwgXGY (bagian Batch 1, 2, 3, 3.2, 3.3 "Satu pintu kirim"). Ilustrasi 3.4.0 (HP↔HP) sudah ada di sana: papan `KH_Kode`, `KH_HPApp`, `KH_Terima`.

---

## 8. Keputusan desain yang sudah disetujui (jangan diulang diskusinya)

- Nama tab: **Kirim · File · Ekosistem**.
- **Semua tujuan kirim lewat tab Kirim › Ganti.** Ekosistem = melihat & mengelola perangkat; planet → "Kirim ke sini" sebagai jalan pintas.
- **Tamu browser (di Host HP):** tanpa identitas, nama otomatis "HP Android · Chrome" / "iPhone · Safari", dihapus setelah **2 menit** tidak ada tanda hadir atau saat berbagi dimatikan. Tidak bisa diantrekan (pola "taruh di Dibagikan").
- **Aturan tamu hanya untuk Host di HP.** HP yang membuka web laptop lewat browser **tetap diingat** laptop.
- **HP dengan aplikasi diperlakukan seperti laptop** (untuk 3.4.0): sambung sekali dengan kode 6 angka, disimpan, bisa "Lupakan" (kedua sisi). Tanpa pilihan folder: foto → Galeri › DropBridge, file lain → Download › DropBridge.
- **Mode menerima HP:** "Saat aplikasi terbuka" (bawaan) atau **"Selalu menyala"** (opsi, notifikasi tetap).
- **Perangkat tidak terjangkau → peringatan + antrean**; antrean ke HP kedaluwarsa **7 hari**.
- Batch besar dipecah: 3.3.0 tampilan, **3.4.0 HP↔HP**.

---

## 9. Yang masih tertunda

### Untuk dikerjakan (urut)
1. **Tunggu hasil uji pengguna untuk 3.2.1 + 3.3.0** (daftar cek di bawah). Perbaiki bug yang dilaporkan dulu.
2. **3.4.0 — HP ke HP lewat aplikasi** (task #92):
   - Aplikasi menjawab pencarian UDP (seperti laptop) supaya HP lain menemukannya; tampil di Ekosistem dan di "Kirim ke mana? › HP dengan aplikasi".
   - Sambung sekali: HP penerima menampilkan **Kode sambung** (Ekosistem › HP ini), pengirim memasukkannya; lalu saling ingat; **Lupakan** di kedua sisi.
   - Penerima: saklar **Bisa ditemukan** + pilihan **Saat aplikasi terbuka / Selalu menyala** (foreground service + notifikasi tetap).
   - Kirim pakai mesin antrean yang sama (ulang otomatis, lanjut dari potongan terakhir), antrean 7 hari, peringatan bila tidak terjangkau.
   - Cek kecocokan versi antar-HP.
   - Bisa dimulai dari ilustrasi yang sudah disetujui (KH_*).
3. **Catatan nanti — "Keluarkan tamu"** (task #89): tekan tamu → Keluarkan → tamu harus memasukkan kode lagi.

### Usulan yang belum diputuskan pengguna
- **Batch 4 HTTPS** (belum dikonfirmasi).
- "Salin info masalah" di Pengaturan › Tentang (versi, status, kejadian terakhir) untuk laporan bug.
- Kode Host baru setiap kali berbagi dinyalakan.
- Tautan "Pasang aplikasi DropBridge" di halaman tamu (APK diunduh langsung dari HP host).
- Arahkan lokasi simpan ke folder OneDrive kantor (hanya pengaturan, tanpa kode; pengguna selama ini memindah manual ke OneDrive).

### Tugas pengguna (ingatkan bila relevan)
- `git push` (6 commit).
- `build_semua.bat` → pasang APK 3.3.0 + installer; di Mint: `git pull` lalu `build_deb.sh`.
- Uji alur izin antarlaptop (Windows ↔ Mint).

### Daftar cek uji di HP (3.2.0 → 3.3.0)
1. Buka aplikasi, laptop menyala → judul "LAPTOP-… · Wi-Fi", planet laptop di cincin dalam.
2. Matikan DropBridge di laptop → ±20 dtk jadi "tidak terjangkau · terakhir HH.mm".
3. Nyalakan lagi → pulih sendiri.
4. Nyalakan Berbagi, HP kedua masuk lewat browser → muncul planet "HP Android · Chrome".
5. Tutup halaman HP kedua → planet meredup, hilang setelah 2 menit.
6. Kirim dari HP kedua → "kirim 1 file" di baris tamu.
7. Laptop → HP aplikasi → tersimpan sendiri di Download/DropBridge + kartu "file dari laptop".
8. Laptop → HP browser → kartu Unduh di halaman HP.
9. Tab File: unduh APK → penanda berubah → ketuk → Pasang/Buka/lokasi.
10. Laptop → laptop lain → minta PIN + izin, file masuk.
11. Kirim › Ganti → Bagikan lewat QR → QR tampil di tab Kirim, bisa discan.
12. "Pilih file untuk dibagikan" → bisa diambil dari HP kedua.
13. Ganti → pilih laptop lagi → kartu folder kembali.
14. Laptop mati, tekan Foto langsung → muncul peringatan "Masukkan antrean".

---

## 10. Bug yang pernah terjadi (agar tidak terulang)
- Kelas CSS `.lg` dipakai dua kali (legenda vs tombol besar) → "Pilih file" di HP rusak. Legenda kini `.lgd`.
- Toast/menu di dalam `<dialog>` tertutup lapisan atas → dipindah ke dialog yang terbuka.
- Status laptop basi saat laptop mati → diperbaiki dengan `Beat` (3.2.1).
- Di HP, orbit menimpa matahari → jari-jari orbit dihitung dari ukuran matahari.
- `tar` di laptop gagal menimpa file tanpa `--overwrite` (izin hapus terbatas).
