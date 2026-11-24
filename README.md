# DropBridge

Kirim file dan teks antara PC dan HP lewat Wi-Fi yang sama. Tanpa kabel, tanpa scan QR,
tanpa memasang aplikasi di HP. Cukup browser.

## Isi folder ini

```
DropBridge/
  Web/           tampilan (index.html, app.js, style.css, tokens.css, fonts/), dipakai PC, browser HP, dan jendela desktop
  design/        sistem desain DropBridge 3: tokens.json (satu sumber warna, huruf, jarak), components.md,
                 build_tokens.py (membuat Web/tokens.css dan Android Tokens.kt, cek kontras)
  PC-App/        server + jendela desktop + bahan installer
                 Windows: start.bat, build_exe.bat · Linux: start.sh, build_deb.sh
  Android-App/   aplikasi Android (build_apk.bat)
  Rilis/         hasil jadi untuk dibagikan: DropBridge-Setup-<versi>.exe, dropbridge_<versi>_amd64.deb,
                 DropBridge-<versi>.apk
                 (tidak masuk Git)
  build_semua.bat  build APK lalu installer PC sekaligus, hasil ke Rilis
```

**Alur setiap pembaruan:** hentikan DropBridge (tray > Berhenti) > coba di `PC-App\start.bat` > kalau sudah oke,
tray > Berhenti > klik ganda **build_semua.bat** > pasang `Rilis\DropBridge-Setup-<versi>.exe` > kalau Android ikut
berubah, pasang APK baru di HP lewat QR di menu Hubungkan.

Folder ini hanya berisi kode. Data pribadi disimpan di luar:

| Apa | Di mana |
|---|---|
| File kiriman HP | `Documents\DropBridge` (bisa diganti, mis. ke OneDrive kantor, lewat ikon **Folder penyimpanan** di Home laptop atau Pengaturan › Lokasi simpan) |
| Pengaturan (PIN, folder) | Windows `%LOCALAPPDATA%\DropBridge\config.json` · Linux `~/.local/share/DropBridge/config.json` |
| Perangkat terhubung, izin, riwayat teks, log | folder `data` di sebelah config.json |

Versi dari kode (`start.bat`) dan versi terpasang (`.exe`) memakai pengaturan yang sama, jadi PIN dan HP
yang sudah terhubung tidak berbeda.

## DropBridge 3 (PC 3.0.0 · Android 3.0.0)

- **Home bersih**: kartu tujuan (laptop + folder), Foto langsung, Pilih file, lalu baris ikon berlabel kecil:
  Semua folder, Muat ulang, Teks, Antrean (aplikasi, hanya bila ada), Foto kecil, Tambah perangkat,
  dan di laptop juga Folder penyimpanan. Riwayat dan chip folder dihapus dari Home.
- **Pilih folder**: "Terakhir dipakai" di atas, lalu "Semua folder" dengan "Tanpa folder" paling atas.
- **Teks**: ikon Teks membuka lembar tulis/tempel. HP berakses Penuh melihat semua teks di laptop
  (asal teks: teal = laptop, indigo = HP), bisa Salin, Pilih sebagian, Hapus dengan Urungkan 30 detik.
- **Kabar file masuk** di Home laptop: "4 foto masuk dari HONOR Nudis ke TCKU 6177329 › 30-23 · Buka folder".
- **Pengaturan** dengan urutan yang sama di semua tempat: Perangkat ini, Pengiriman (aplikasi), Laptop ini
  (laptop: notifikasi, izin HP baru, PIN, lokasi simpan), Tentang (versi), Keluar. Kecilkan foto hanya di Home.
  Di aplikasi Android, PIN dan alamat manual pindah ke tab Hubungkan › Laptop.
- **Sistem desain**: warna dan huruf dari `design/tokens.json`. Huruf Plus Jakarta Sans dan IBM Plex Mono
  (nomor kontainer) ikut terpasang, jadi tetap jalan tanpa internet. Ubah warna: edit tokens.json, lalu
  `python design/build_tokens.py`.

## Antrean per folder, nama file, folder bawaan (PC 3.3.1 · Android 3.3.1)

- **Antrean per folder**: tiap folder satu baris (nama, jumlah file + ukuran, status Menunggu/Mengirim). Ketuk untuk buka atau tutup; "Buka semua / Ciutkan semua" di atas daftar. Satu folder langsung terbuka, dua atau lebih tertutup. Ganti folder dan Hapus folder ini ada di dalam folder yang terbuka. Folder yang sedang mengirim menampilkan progres di barisnya saat tertutup.
- **Nama file** (kartu baru di Kirim ke…): **Asli** (bawaan), **Otomatis** (Foto 01, Nama folder 01, atau Tanggal 01), atau **Sendiri** (nama dasar + nomor, atau ubah satu per satu; ekstensi tidak bisa terhapus). Pilihan Asli/Otomatis dan polanya diingat; Sendiri hanya untuk kiriman itu. Bawaan juga bisa diatur di Pengaturan › Pengiriman › Nama file bawaan; berlaku untuk Foto langsung dan Pilih file.
- **Nomor diberikan PC** saat file sampai (`seq` di `/api/files/upload-raw`): lanjut dari nomor terbesar di folder tujuan, jadi tetap benar walau PC mati saat difoto. Di Antrean nama tampil "Foto ##.jpg". Nama folder ber-garis-miring memakai bagian terakhirnya; karakter terlarang Windows dibersihkan. Kiriman yang diulang tidak tersimpan dua kali (isi file dibandingkan dengan semua file seukuran di folder itu).
- **Folder bawaan**: layar Kirim selalu dibuka dengan **Tanpa folder**. Pengaturan › Pengiriman › Folder bawaan: Tanpa folder (bawaan), Terakhir dipakai, atau Folder tertentu. Folder tertentu yang sudah tidak ada di PC kembali ke Tanpa folder. "Terakhir dipakai" tetap ada di lembar Pilih folder.

## Satu pintu kirim dan Ekosistem (PC 3.3.0 · Android 3.3.0)

- Tab Hubungkan sekarang bernama **Ekosistem** (ikon orbit); ikon Kirim jadi pesawat kertas. Web, Windows, Linux, dan aplikasi sama.
- Aplikasi: **Kirim › Ganti → Kirim ke mana?** berisi Laptop dan **HP tanpa aplikasi (Bagikan lewat QR)**. Saat memilih QR, kartu tujuan langsung menampilkan QR, kode, alamat, tombol "Pilih file untuk dibagikan", dan daftar HP tamu.
- Ketuk planet di Ekosistem › **Kirim ke sini**: tab Kirim terbuka dengan tujuan itu (planet tamu: Bagikan file ke HP ini).
- Laptop tujuan tidak terjangkau saat Foto langsung / Pilih file: muncul peringatan, file bisa masuk antrean dan terkirim sendiri nanti.
- Berikutnya (3.4.0): HP ke HP lewat aplikasi (sambung sekali dengan kode, antrean ke HP, mode Selalu menyala).

## Status laptop dan tamu (PC 3.2.1 · Android 3.2.1)

- Aplikasi mengecek laptop tujuan tiap 10 detik selama terbuka. Gagal 2 kali = "tidak terjangkau · terakhir HH.mm"; pulih sendiri saat laptop kembali.
- HP yang masuk lewat browser ke "Berbagi dengan HP lain" tampil sebagai tamu (mis. "HP Android · Chrome") di peta dan di bagian HP lain. Tidak ada identitas yang disimpan; hilang 2 menit setelah halamannya ditutup atau saat berbagi dimatikan.
- Judul tab Hubungkan menampilkan status laptop; status berbagi ("Berbagi aktif · kode") pindah ke bagian HP lain.

## Kirim ke laptop lain dan ke HP (PC 3.2.0 · Android 3.2.0)

- **Tujuan kirim di laptop**: kartu tujuan di Home laptop punya baris KE + tombol **Ganti** → "Kirim ke mana?":
  Laptop ini (bawaan), laptop DropBridge lain di Wi-Fi ini, atau HP yang pernah tersambung.
- **Laptop lain**: pertama kali masukkan PIN laptop itu; laptop itu menekan Izinkan (hak bawaan Hanya kirim).
  Kolom folder dan Pilih folder memakai folder di laptop tujuan. File diteruskan oleh DropBridge laptop ini
  (`/api/remote/*`, token disimpan di data/peers.json). Juga dari Hubungkan › planet laptop › Kirim ke sini.
- **HP**: file ditaruh di kotak kiriman (data/outbox, `/api/outbox/*`). Aplikasi mengambilnya sendiri (tiap ±10 detik
  saat terbuka, ±15 menit di latar belakang), menyimpan ke Download/DropBridge, dan memberi notifikasi. Bisa dimatikan
  di Pengaturan › Terima file dari laptop. HP lewat browser melihat kartu "Ada file dari laptop" dengan tombol Unduh.
  Laptop melihat status tiap file: menunggu diambil / diterima. Yang tidak diambil 7 hari dibuang.
- **File yang sudah di HP (aplikasi)**: tanda ikon di tab File (centang hijau = sudah di HP, panah oranye = versi baru
  di laptop, cincin = sedang diunduh). Ketuk: Pasang (APK) / Buka, Buka lokasi file, Bagikan, Unduh ulang.
  Pasang APK memerlukan izin "Pasang aplikasi dari DropBridge" (ditanya sekali oleh Android).

## Hubungkan sebagai tata surya (PC 3.1.0 · Android 3.1.0)

- **Laptop dan browser**: matahari = laptop. Orbit dalam = perangkat aktif sekarang, tengah = pernah tersambung,
  luar = menunggu izin atau laptop DropBridge lain di Wi-Fi ini, putus-putus = lewat internet (Tailscale).
  Warna: putih = laptop (lencana W/L), teal = aplikasi Android, abu = browser, kuning = menunggu izin,
  cincin = hanya kirim. Ketuk planet untuk detail: ubah hak, putuskan, izinkan/tolak, atau buka laptop lain.
- **Aplikasi Android**: matahari = HP ini, planet = laptop (tersimpan, di Wi-Fi ini, atau baru). Ketuk untuk
  "Jadikan tujuan kirim", "Buka File di PC ini", atau "Lupakan PC". Cari ulang tiap 20 detik saat terbuka.
- Tombol **Peta / Daftar** selalu ada. Peta berputar pelan (60-200 detik per putaran) dan berhenti saat tidak
  terlihat, saat "kurangi gerak"/animasi HP dimatikan, mode hemat baterai, atau baterai laptop di bawah 20%.
- Permintaan izin HP baru muncul sekali sebagai jendela; setelah itu bisa dijawab dari kartu di samping peta.
- Laptop lain dicari lewat siaran UDP yang sama dengan pencarian HP (`GET /api/peers`, disimpan 15 detik).

## Cara pakai

**Pemakaian sehari-hari (versi terpasang):** pasang `DropBridge-Setup-<versi>.exe`, lalu buka **DropBridge** dari
Start Menu. Begitu dibuka, laptop langsung jadi server: HP bisa mengirim lewat Wi-Fi yang sama atau Tailscale.

- Jendela DropBridge berisi halaman yang sama dengan web di HP dan aplikasi Android (Kirim, File, Hubungkan, Pengaturan).
- Tombol **X** menyembunyikan jendela ke **ikon di dekat jam**; DropBridge tetap jalan supaya HP masih bisa mengirim.
  Berhenti total: klik kanan ikon > **Berhenti**.
- Saat Windows menyala, DropBridge jalan diam-diam di ikon tray (pilihan di installer atau menu tray).
- Dibuka lagi saat sudah jalan: jendela yang ada dimunculkan, tidak ada salinan kedua.
- Notifikasi Windows saat file masuk ("4 file masuk ke TRHU-4134291 › 30-1"); klik ikon tray untuk membuka foldernya.
- Pengaturan dan data ada di `%LOCALAPPDATA%\DropBridge`. Pengaturan versi lama (sebelum 2.4.1, tersimpan di
  `C:\DropBridge\config.json`) dipindahkan otomatis saat pertama jalan, jadi HP tidak perlu dihubungkan ulang.
- Pertama kali dibuka (dan setelah update) muncul halaman **Selamat datang**: folder penyimpanan, QR untuk HP, dan QR
  untuk mengunduh aplikasi Android dari laptop ini.

**Saat mengembangkan (dari kode):**

1. Pasang Python 3.10+ dari python.org (centang **Add python.exe to PATH**). Sekali saja.
2. Klik dua kali **PC-App\start.bat**. Pertama kali akan memasang paket otomatis, lalu membuka jendela DropBridge yang sama
   seperti versi terpasang (`desktop.py`). Catatan server ada di `%LOCALAPPDATA%\DropBridge\data\server.log`.
3. Ingin jendela hitam seperti dulu? Jalankan `start.bat konsol`. Jendela itu menampilkan **alamat** dan **PIN**. Contoh:
   ```
   Di HP     : http://192.168.1.5:8000
   PIN       : 483920
   ```
4. Di HP, buka alamat itu di browser (Chrome/Safari), masukkan PIN. Selesai.

Di PC, buka `http://localhost:8000` untuk memakai halaman yang sama.

### Firewall Windows (penting)
Saat pertama kali jalan, Windows menanyakan izin untuk Python. **Centang "Private networks"**
lalu Allow. Jika terlewat: Windows Security > Firewall > Allow an app > izinkan Python di Private.
Pastikan juga Wi-Fi di PC bertipe **Private** (bukan Public).

## Fitur (v2)

- **Pengiriman bisa dilanjutkan**: dari aplikasi Android, kiriman yang putus di tengah (Wi-Fi gudang tidak stabil) dilanjutkan dari bagian yang sudah sampai, bukan dari nol. Sisa kiriman yang tidak pernah selesai dihapus otomatis setelah 3 hari.
- **Tidak ada file ganda**: bila file yang isinya persis sama sudah ada di folder itu, kiriman dilewati (tidak jadi `foto (1).jpg`). Web menampilkan "Sudah ada di ...".

**Tampilan:** tiga menu yang sama di PC, HP, dan aplikasi Android. **Kirim** (folder tujuan, Pilih file, kecilkan foto, tautan Kirim teks), **File** (cari, daftar atau kotak, isi folder) dan **Hubungkan** (QR agar HP lain masuk, perangkat terhubung). Nama perangkat dan Keluar ada di ikon pengaturan di kanan atas. Di PC menunya di atas; di HP menunya di bawah layar.

- Login PIN (percobaan salah dibatasi: 5x, lalu kunci 60 detik)
- **Folder per barang/dokumen, bisa bertingkat** (maks. 3 tingkat): ketik `TRHU-4134291/30-1` atau pilih lewat ikon folder di kolom tujuan
  (panel **Pilih folder**: masuk ke subfolder, buat folder baru, pilih). Folder dibuat otomatis saat file pertama dikirim.
  Di halaman File: ikon **folder+** membuat folder di tempat itu, subfolder tampil di atas file, jejak lokasi `File › A › B`
  bisa diketuk. Jumlah file folder induk menghitung isi subfolder; ZIP ikut membawa subfolder.
  Folder yang berisi subfolder tidak bisa dihapus dari HP (hapus subfoldernya dulu).
- Kotak "Kirim ke folder": saran dari folder yang ada, chip folder terbaru, peringatan "Mirip dengan ..."
  jika nama hampir sama dengan folder lain (mencegah salah ketik), dan folder terakhir diingat.
- Kirim banyak foto/file sekaligus dari HP, dengan progress per file. Kualitas foto asli, nama file asli.
  File diurutkan menurut waktu foto aslinya (terlama dulu). Unggahan dikirim mentah dan langsung ditulis ke disk (cepat).
- Lihat isi folder sebagai kotak thumbnail; pratinjau dengan geser/panah; **Pindah** dan **Hapus** per file.
- Dari HP: buat folder dan subfolder, ganti nama folder, hapus folder (dengan konfirmasi yang menyebut jumlah file), unduh folder sebagai ZIP.
- Di PC: seret file ke halaman (masuk ke folder yang tertulis di kotak), atau tempel gambar dengan Ctrl+V.
- Kirim teks atau link antar perangkat.
- **Identitas perangkat**: tiap perangkat diberi nama saat pertama masuk. Tombol **Perangkat** menampilkan siapa yang terhubung (nama, jenis, IP, aktif terakhir) dan bisa **Keluarkan** perangkat tak dikenal. Perangkat lain otomatis keluar setelah 8 jam tidak aktif (PC sendiri dikecualikan).
- **Foto otomatis dikecilkan**: foto JPG/PNG/WebP di atas 512 KB dikecilkan resolusi dan kualitasnya sebelum dikirim (di web dan aplikasi Android), hasilnya JPG di bawah 512 KB. Foto yang sudah kecil tidak diubah. Ada saklar **Kecilkan foto** di web dan di layar "Kirim ke PC" Android; matikan untuk mengirim resolusi penuh (mis. foto liburan). Pilihan terakhir diingat. Batas diatur lewat `photo_max_kb` di `config.json` (web; aplikasi Android memakai 512 KB tetap).
- Hanya menerima koneksi dari jaringan lokal.

Catatan: menghapus folder bersifat **permanen** (tidak masuk Recycle Bin). File yang ditaruh langsung di
folder penyimpanan (tanpa subfolder) muncul sebagai "Tanpa folder". Thumbnail membutuhkan paket Pillow
(terpasang otomatis oleh start.bat saat ada internet).

## Membangun installer (DropBridge-Setup.exe)

Sama seperti `build_apk.bat` untuk Android, `build_exe.bat` membuat "file jadi" untuk PC:

1. Sekali saja: jalankan `PC-App\start.bat` (menyiapkan `.venv`) dan pasang Inno Setup 6: `winget install JRSoftware.InnoSetup`.
2. Bangun APK dulu: **Android-App\build_apk.bat**. Hasilnya `Rilis\DropBridge-<versi>.apk`, dan ikut dibungkus ke installer.
3. Klik dua kali **PC-App\build_exe.bat**. Hasil: `Rilis\DropBridge-Setup-<versi>.exe` (versi dari `PC-App\app\version.py`).
   Tanpa Inno Setup, programnya tetap jadi di `PC-App\dist\DropBridge\DropBridge.exe` dan bisa dicoba langsung.
   Log: `PC-App\build_exe_log.txt`.

Paling mudah: **build_semua.bat** di folder induk menjalankan langkah 2 dan 3 sekaligus.

Installer memasang ke Program Files, membuat pintasan Start Menu, mengizinkan DropBridge di Windows Firewall
(Private dan Public, perlu untuk Tailscale), dan menawarkan jalan otomatis saat Windows menyala. Update: pasang
Setup versi baru di atas yang lama; pengaturan tidak hilang. Exe tidak bertanda tangan digital, jadi Windows
SmartScreen memperingatkan sekali: **Info selengkapnya > Tetap jalankan**.

## Aplikasi Android

Aplikasi Android pendamping (kirim otomatis, mode host, mencari PC otomatis, dan lain-lain) ada di `Android-App/`
(penjelasan di `Android-App/README.md`). Server tidak membutuhkannya untuk berjalan; web sudah cukup.

HP mengunduh APK langsung dari laptop: menu **Hubungkan** > kartu **Aplikasi Android** (QR). APK yang ditawarkan adalah
yang terbaru di `Rilis\` atau yang ikut di installer. Server membalas pencarian dari aplikasi lewat UDP port 47800 (`app/discovery.py`).

## Linux (Mint, Ubuntu, Debian)

Tampilan dan fitur sama dengan Windows: jendela sendiri (WebKitGTK), ikon di panel bawah, notifikasi file masuk,
mulai otomatis saat laptop menyala. Diuji untuk Linux Mint 22 (Xfce/Cinnamon).

**Pasang (sehari-hari):** salin `Rilis/dropbridge_<versi>_amd64.deb` ke laptop Linux, klik ganda, Install.
Atau di terminal: `sudo apt install ./dropbridge_<versi>_amd64.deb`. Buka **DropBridge** dari menu.

**Membangun .deb** (harus di laptop Linux itu sendiri, .deb tidak bisa dibuat di Windows):

```
sudo apt install git python3-venv dpkg-dev        # sekali saja
git clone https://github.com/vikz33/dropbridge.git ~/Dev/DropBridge   # sekali saja; selanjutnya: git pull
cd ~/Dev/DropBridge/PC-App
bash build_deb.sh                                  # hasil: ../Rilis/dropbridge_<versi>_amd64.deb
```

APK Android tidak ikut di Git. Supaya HP bisa mengunduh APK dari laptop Linux, salin `DropBridge-<versi>.apk`
ke folder `Rilis/` sebelum `build_deb.sh` (misalnya kirim dari laptop Windows lewat DropBridge).

**Dari kode** (mencoba pembaruan sebelum membangun .deb): `bash PC-App/start.sh`. Pertama kali dia menawarkan
memasang paket sistem yang kurang (`python3-gi`, `gir1.2-webkit2-4.1`, `gir1.2-ayatanaappindicator3-0.1`,
`libnotify-bin`, `zenity`). `bash start.sh konsol` = server saja di terminal.

Firewall: di Mint `ufw` bawaannya mati. Bila menyala, paket .deb membuka port 8000/tcp dan 47800/udp sendiri.

## Dua laptop atau lebih

Tiap laptop menjalankan DropBridge sendiri dengan PIN sendiri. Aplikasi Android menyimpan semua PC yang pernah
disambungkan: kartu PC di halaman Kirim menampilkan tujuan (mis. "laptop-mint · Linux") dan tombol **Ganti**.
HP selalu mengirim ke PC yang terakhir dipilih; kalau PC itu mati, file menunggu di antrean (tidak dikirim ke PC
lain diam-diam), dan kartunya menyarankan PC lain yang ada di Wi-Fi yang sama.

### Folder kedua laptop selalu sama (opsional, Syncthing)

DropBridge tidak menyinkron antarlaptop. Untuk itu pakai **Syncthing** (gratis, Windows dan Linux,
https://syncthing.net):

1. Pasang Syncthing di kedua laptop (Linux: `sudo apt install syncthing`, lalu buka http://127.0.0.1:8384).
2. Di laptop A: Add Remote Device, masukkan Device ID laptop B. Terima di laptop B.
3. Bagikan folder penyimpanan DropBridge (mis. `Documents\DropBridge` di Windows) ke laptop B, dan di laptop B
   arahkan ke folder penyimpanan DropBridge-nya (mis. `~/Documents/DropBridge`).
4. Nyalakan **File Versioning** (mis. Trash Can, 14 hari) di folder itu, supaya file yang terhapus masih bisa kembali.

Catatan: jangan pakai Syncthing di folder yang juga disinkron OneDrive. Syncthing hanya menerima perangkat yang
kamu setujui (Device ID), jadi orang yang tahu PIN DropBridge tidak bisa ikut sinkron.

## Pengaturan (config.json)

| Kunci | Fungsi |
|---|---|
| `port` | Port server (default 8000) |
| `pin` | PIN login. Kosong = dibuat acak saat pertama jalan. Angka disarankan |
| `shared_dir` | Folder penyimpanan (path absolut; bawaan `Documents\DropBridge`). Lebih mudah diganti lewat **Ganti** |
| `max_upload_mb` | Batas ukuran satu file unggahan |
| `session_hours` | Lama login bertahan |
| `photo_max_kb` | Batas ukuran foto dalam KB; lebih besar dikecilkan di web (default 512, 0 = nonaktif) |
| `idle_hours` | Perangkat lain otomatis keluar bila tidak aktif sekian jam (default 8, 0 = nonaktif) |
| `lan_only` | `true` = tolak koneksi dari luar jaringan lokal |
| `enable_docs` | `true` = buka dokumentasi API di `/docs` |
| `approve_new` | `true` = HP baru harus diizinkan dari laptop walau PIN-nya benar (bawaan). Bisa diubah di Pengaturan |

Ubah lalu jalankan ulang DropBridge (tray > Berhenti, lalu buka lagi).

## Struktur dan cara mengembangkan

```
Web/                   index.html, app.js, style.css, tokens.css (dibuat dari design/tokens.json), fonts/
Android-App/           proyek Android (Kotlin), build_apk.bat
PC-App/
  start.bat            jalankan dari kode ("start.bat konsol" = jendela hitam)
  desktop.py           versi desktop: jendela sendiri (WebView2), ikon tray, notifikasi, satu instance
  tray.py              nama lama, meneruskan ke desktop.py
  run.py               server saja (dipakai "start.bat konsol"), menampilkan alamat dan PIN
  build_exe.bat        membangun Rilis\DropBridge-Setup-<versi>.exe
  start.sh             Linux: jalankan dari kode ("start.sh konsol" = terminal)
  build_deb.sh         Linux: membangun Rilis/dropbridge_<versi>_amd64.deb
  DropBridge.spec      resep PyInstaller (dipakai build_exe.bat)
  installer/           resep installer Inno Setup
  tools/make_icon.py   membuat ikon .ico dari logo
  app/
    main.py            menggabungkan router, penjaga jaringan lokal
    auth.py            PIN -> token sesi (cookie atau header Bearer)
    config.py          membaca %LOCALAPPDATA%\DropBridge\config.json, letak folder Web dan Rilis
    discovery.py       membalas pencarian PC dari aplikasi Android (UDP 47800)
    events.py          kabar dari server ke jendela desktop (file masuk, munculkan jendela)
    inbox.py           kabar "file masuk" untuk Home laptop (/api/files/incoming)
    icon.py            logo DropBridge sebagai gambar (tray dan .exe)
    version.py         nomor versi PC
    routes/
      folders.py       /api/folders (daftar, buat, ganti nama, hapus, ZIP)
      files.py         /api/files (daftar, upload, unduh, pindah, thumbnail, hapus)
      clipboard.py     /api/clips (teks: kirim, daftar, hapus, kembalikan)
      system.py        khusus PC ini: folder penyimpanan, buka Explorer, Selamat datang, unduh APK
    services/
      storage.py       penyimpanan file (sekarang folder lokal)
      storage_admin.py ganti folder penyimpanan (mis. OneDrive) tanpa restart
      thumbs.py        thumbnail foto (cache di data/thumbs)
```

- **Fitur baru** = buat file di `app/routes/`, lalu daftarkan dengan `app.include_router(...)` di `main.py`.
- **Ganti penyimpanan** (mis. Google Drive): buat kelas dengan method yang sama seperti `LocalStorage`
  (`list_folders`, `list_files`, `path_of`, `new_tmp`, `commit`, `discard`, `move`, `delete`, dst.) lalu ganti isi variabel `storage`.
- **Klien lain** (mis. app Android): API-nya REST biasa. Login di `POST /api/login` dengan `{"pin": "..."}`,
  lalu kirim token yang dikembalikan sebagai header `Authorization: Bearer <token>`.

### Rencana berikutnya
- Mode host dengan hotspot + QR/kode angka untuk iPhone dan HP lain
- Android: lihat dan unduh folder dari PC di dalam aplikasi; Bluetooth dan Wi-Fi Direct
- Versi internet menyusul belakangan

## Simpan langsung ke OneDrive (atau folder lain)

Klik kanan ikon DropBridge di tray > **Folder penyimpanan**:

- **Simpan di OneDrive kantor (folder DropBridge)**: kiriman masuk ke `OneDrive - <kantor>\DropBridge`.
- **Pilih folder lain…**: pilih folder mana saja, misalnya folder OneDrive yang sudah kamu pakai.

DropBridge menawarkan untuk memindahkan isi folder lama ke lokasi baru (folder digabung, file yang sudah ada di tujuan
tidak ditimpa), lalu menjalankan dirinya ulang. Pilihan tersimpan di `config.json` (`shared_dir`).

Kiriman yang belum lengkap ditulis dulu di `data\incoming`, baru dipindah ke folder tujuan saat utuh, jadi OneDrive
tidak pernah mengunggah file setengah jadi. Saran: klik kanan folder DropBridge di OneDrive > **Selalu simpan di
perangkat ini**, supaya thumbnail dan pengecekan file ganda tetap cepat.

## Kirim dari luar jaringan (Tailscale)

Untuk mengirim dari HP sendiri walau tidak satu Wi-Fi dengan PC, misalnya dari gudang lewat data seluler:

1. Pasang Tailscale di PC dan di HP (https://tailscale.com/download), login dengan akun yang sama.
2. Sambungkan aplikasi DropBridge sekali lewat Wi-Fi seperti biasa. Aplikasi mencatat alamat Tailscale PC (`100.x.x.x`).
3. Di luar jaringan, nyalakan Tailscale di HP. Aplikasi mencoba alamat Wi-Fi dulu, lalu pindah sendiri ke alamat Tailscale.
   Status di halaman Kirim menjadi "... terhubung · lewat internet".

Dari browser HP, buka halaman Hubungkan di PC dan scan QR "Dari luar jaringan".
Server menerima alamat Tailscale (100.64.0.0/10) seperti alamat LAN; alamat internet lain tetap ditolak.
Kirim otomatis di latar belakang memakai data seluler hanya jika "Boleh pakai data seluler" aktif di Pengaturan aplikasi.

## Keamanan

- **Izin HP baru** (2.5.0): PIN benar saja belum cukup. Laptop memunculkan "HP baru ingin terhubung" dengan pilihan
  hak **Hanya kirim** (bisa mengirim, tidak bisa melihat/mengunduh/menghapus) atau **Penuh**, lalu Izinkan/Tolak.
  Tidak dijawab 2 menit = ditolak. HP yang sudah diizinkan diingat, jadi login ulang tidak perlu izin lagi.
- Di Hubungkan (dari laptop): ketuk hak sebuah HP untuk mengubahnya, **Putuskan** untuk mengeluarkannya (harus
  diizinkan lagi untuk masuk), dan **Ganti PIN**. HP yang sudah terhubung tetap terhubung setelah PIN diganti.
- **Hapus = Recycle Bin / Tempat Sampah**: file atau folder yang dihapus dari HP masih bisa dikembalikan dari PC.
- Jaringan lokal saja. **Jangan** membuka port ini ke internet (port forwarding) tanpa HTTPS dan pengamanan tambahan.
- Koneksi memakai HTTP biasa, jadi PIN terkirim tanpa enkripsi di dalam Wi-Fi. Pakai Wi-Fi rumah atau kantor
  yang dipercaya, jangan Wi-Fi publik.
- Nama file dibersihkan, pencegahan path traversal aktif, dan hanya tipe yang aman yang boleh dibuka langsung
  di browser (HTML dan SVG selalu diunduh, tidak dijalankan).
- Riwayat teks tersimpan sebagai teks biasa di `data/clips.json`. Hapus dari halaman jika berisi hal sensitif.

## Jika HP tidak bisa membuka alamatnya

1. HP dan PC harus di jaringan yang sama. Wi-Fi tamu (guest) sering memisahkan perangkat.
2. Cek firewall (lihat bagian di atas).
3. Beberapa router punya "AP isolation" atau "client isolation". Matikan di pengaturan router.
4. Alamat IP PC bisa berubah. Atur "DHCP reservation" di router agar tetap sama, lalu simpan alamatnya
   sebagai bookmark atau "Tambahkan ke layar utama" di browser HP.
5. Bisa juga memakai hotspot dari HP: sambungkan PC ke hotspot, jalankan start.bat, lalu buka alamat
   yang tampil dari HP yang sama.
6. Port 8000 dipakai program lain? Ganti `port` di config.json.

Catatan: karena memakai HTTP (bukan HTTPS), browser tidak mengizinkan instal sebagai PWA penuh. Fitur
"Tambahkan ke layar utama" tetap bisa dipakai sebagai pintasan.
