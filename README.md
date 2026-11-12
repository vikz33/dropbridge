# DropBridge

Kirim file dan teks antara PC dan HP lewat Wi-Fi yang sama. Tanpa kabel, tanpa scan QR,
tanpa memasang aplikasi di HP. Cukup browser.

## Isi folder ini

```
DropBridge/
  Web/           tampilan (index.html, app.js, style.css), dipakai PC, browser HP, dan jendela desktop
  PC-App/        server + jendela desktop + bahan installer (start.bat, build_exe.bat)
  Android-App/   aplikasi Android (build_apk.bat)
  Rilis/         hasil jadi untuk dibagikan: DropBridge-Setup-<versi>.exe dan DropBridge-<versi>.apk
                 (tidak masuk Git)
  build_semua.bat  build APK lalu installer PC sekaligus, hasil ke Rilis
```

**Alur setiap pembaruan:** hentikan DropBridge (tray > Berhenti) > coba di `PC-App\start.bat` > kalau sudah oke,
tray > Berhenti > klik ganda **build_semua.bat** > pasang `Rilis\DropBridge-Setup-<versi>.exe` > kalau Android ikut
berubah, pasang APK baru di HP lewat QR di menu Hubungkan.

Folder ini hanya berisi kode. Data pribadi disimpan di luar:

| Apa | Di mana |
|---|---|
| File kiriman HP | `Documents\DropBridge` (bisa diganti, mis. ke OneDrive kantor, lewat **Ganti** di halaman Kirim) |
| Pengaturan (PIN, folder) | `%LOCALAPPDATA%\DropBridge\config.json` |
| Perangkat terhubung, riwayat teks, log | `%LOCALAPPDATA%\DropBridge\data` |

Versi dari kode (`start.bat`) dan versi terpasang (`.exe`) memakai pengaturan yang sama, jadi PIN dan HP
yang sudah terhubung tidak berbeda.

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

## Pengaturan (`%LOCALAPPDATA%\DropBridge\config.json`)

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

Ubah lalu jalankan ulang DropBridge (tray > Berhenti, lalu buka lagi).

## Struktur dan cara mengembangkan

```
Web/                   index.html, app.js, style.css
Android-App/           proyek Android (Kotlin), build_apk.bat
PC-App/
  start.bat            jalankan dari kode ("start.bat konsol" = jendela hitam)
  desktop.py           versi desktop: jendela sendiri (WebView2), ikon tray, notifikasi, satu instance
  tray.py              nama lama, meneruskan ke desktop.py
  run.py               server saja (dipakai "start.bat konsol"), menampilkan alamat dan PIN
  build_exe.bat        membangun Rilis\DropBridge-Setup-<versi>.exe
  DropBridge.spec      resep PyInstaller (dipakai build_exe.bat)
  installer/           resep installer Inno Setup
  tools/make_icon.py   membuat ikon .ico dari logo
  app/
    main.py            menggabungkan router, penjaga jaringan lokal
    auth.py            PIN -> token sesi (cookie atau header Bearer)
    config.py          membaca %LOCALAPPDATA%\DropBridge\config.json, letak folder Web dan Rilis
    discovery.py       membalas pencarian PC dari aplikasi Android (UDP 47800)
    events.py          kabar dari server ke jendela desktop (file masuk, munculkan jendela)
    icon.py            logo DropBridge sebagai gambar (tray dan .exe)
    version.py         nomor versi PC
    routes/
      folders.py       /api/folders (daftar, buat, ganti nama, hapus, ZIP)
      files.py         /api/files (daftar, upload, unduh, pindah, thumbnail, hapus)
      clipboard.py     /api/clips (kirim teks)
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
