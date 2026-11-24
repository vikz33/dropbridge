# DropBridge — Panduan untuk Tim

> Versi yang dibahas: **PC 3.3.1 · Android 3.3.1** (20 November 2026).
> Repo: `github.com/vikz33/dropbridge` · lokasi kerja pemilik: `C:\Dev\DropBridge`.
> Catatan: 3.3.1 baru ditulis dan **belum diuji di HP sungguhan**. Versi 3.3.0 dan sebelumnya sudah dipakai harian.

## 1. Apa itu DropBridge

Aplikasi berbagi file dan teks lewat jaringan lokal, tanpa akun dan tanpa cloud pihak ketiga.
Dibuat untuk pekerjaan pemeriksaan barang di gudang: foto dan PDF dari HP langsung masuk ke folder yang rapi di laptop,
satu folder per dokumen (contoh nama folder: `27 012345`).

Tujuan jangka panjang: pengalaman seperti AirDrop yang lintas ekosistem (Android, Windows, Linux, iPhone lewat browser).
Tiga arah: (1) PC↔HP, HP↔PC, PC↔PC lewat jaringan lokal, (2) hal yang sama lewat internet, (3) HP↔HP lewat scan QR.
Tahap sekarang: dipakai sendiri (pilot). Rilis publik menyusul bila stabil. Fokus utama: yang benar-benar jalan tanpa internet.

## 2. Bagian-bagiannya

| Bagian | Isi | Teknologi |
|---|---|---|
| **Server PC** (`PC-App`) | Menerima, menyimpan, dan menampilkan file. Punya jendela sendiri, halaman web, dan API | Python, FastAPI |
| **Web** (`Web`) | Halaman yang dibuka dari browser HP/laptop mana pun, tanpa memasang aplikasi | HTML/JS dilayani server PC |
| **Aplikasi Android** (`Android-App`) | Kirim, antrean, mode host (HP menerima dari HP lain) | Kotlin, tanpa pustaka UI tambahan |
| **Desain** (`design/`) | Satu sumber warna dan huruf (`tokens.json`) untuk web, Windows, Linux, dan Android | `python design/build_tokens.py` |

Nomor versi PC dan Android **selalu sama** (major.minor sama). Setiap pembaruan harus menyebut nomor versinya.

## 3. Cara kerja singkat

1. HP dan laptop di Wi-Fi yang sama (atau hotspot HP). HP menemukan laptop lewat siaran UDP (port `47800`); server memakai port `8000`.
2. Sambung pertama kali: scan QR, atau ketik IP, lalu PIN dari layar laptop. HP baru perlu diizinkan di laptop.
   Setelah itu HP diingat (kode perangkat tetap), tidak perlu PIN lagi.
3. HP memilih file → memilih folder tujuan → file masuk **antrean** di HP → dikirim ke laptop.
4. Kalau laptop tidak terjangkau (mati, beda jaringan), file **tertahan di HP** dan terkirim sendiri begitu laptop terjangkau lagi (antrean kedaluwarsa 7 hari untuk tujuan non-laptop utama).
5. Di luar jaringan lokal: akses lewat **Tailscale** (dari HP sendiri ke laptop pribadi).

Hak HP di laptop ada dua: **full** (boleh melihat dan mengelola) atau **send** (hanya kirim).

## 4. Fitur utama (sampai 3.3.1)

- **Kirim file/foto/teks** HP → laptop; juga laptop → HP (kotak kiriman, aplikasi mengambil sendiri).
- **Folder per dokumen**: folder bebas diberi nama, bisa subfolder (maks. 3 tingkat), buat/ganti nama/hapus dari HP (dengan konfirmasi).
- **Antrean tahan di HP** dengan lanjut-unggah: kiriman yang putus dilanjutkan dari byte terakhir (bukan dari nol).
- **Kecilkan foto** otomatis ke maks. 512 KB (aturan pekerjaan), bisa dimatikan untuk foto pribadi.
- **Cegah duplikat**: isi file dibandingkan (SHA-256); kiriman yang sama tidak disimpan dua kali.
- **Mode host di HP**: HP menerima dari HP lain lewat QR, teman cukup memakai browser (tanpa aplikasi).
- **Ekosistem**: tab dengan peta "tata surya" perangkat; ketuk planet untuk langsung kirim ke sana. Tujuan kirim: laptop sendiri, laptop DropBridge lain di Wi-Fi yang sama, HP dengan aplikasi, HP tanpa aplikasi (QR).
- **Windows** dan **Linux Mint**: jendela sendiri; Windows dibagikan sebagai `Setup.exe`.

### Baru di 3.3.1 (belum diuji di HP)

- **Antrean per folder**: tiap folder satu baris yang bisa dibuka/ditutup, plus "Buka semua / Ciutkan semua". Satu folder langsung terbuka; dua atau lebih tertutup.
- **Nama file**: Asli (bawaan), Otomatis (`Foto 01`, `Nama folder 01`, atau `Tanggal 01`), atau Sendiri (nama dasar + nomor, atau satu per satu). **Nomor diberikan PC saat file sampai**, lanjut dari nomor terbesar di folder, supaya benar walau PC mati saat difoto.
- **Folder bawaan = Tanpa folder** (bukan folder terakhir). Bisa diubah di Pengaturan › Pengiriman: Tanpa folder / Terakhir dipakai / Folder tertentu.

## 5. API yang paling sering disentuh

| Endpoint | Fungsi |
|---|---|
| `POST /api/files/upload-raw?folder=&name=&uid=&offset=&total=&last_modified=&seq=` | Unggah cepat (isi body = isi file). `uid`+`offset`+`total` = lanjut-unggah. `seq` = nama dasar penomoran otomatis |
| `GET /api/files/upload-offset?uid=&folder=` | Berapa byte kiriman ini yang sudah sampai |
| `GET /api/files/incoming` | Kabar file masuk |
| `/api/remote/*` | Meneruskan file ke laptop DropBridge lain |
| `/api/outbox/*` | Kotak kiriman laptop → HP |

Auth: token Bearer setelah PIN. Aturan nama: karakter terlarang Windows (`/ \ : * ? " < > |`) dibersihkan; nama folder bergaris miring pada penamaan otomatis memakai bagian terakhirnya.

## 6. Cara menjalankan dan membangun

- **Pengembangan**: jalankan `start.bat` (Windows) — selalu versi terbaru. Alur yang dipakai: coba dulu lewat `start.bat`, kalau cocok baru build.
- **Build semua**: `build_semua.bat` di folder root (menghasilkan APK Android dan `Setup.exe` PC). Build per folder tetap ada (`build_apk.bat` di `Android-App`).
- **Versi**: naikkan di `PC-App/app/version.py` dan `Android-App/app/build.gradle.kts` (`versionName` dan `versionCode`).
- **Linux Mint**: sasaran setara Windows (jendela sendiri, file dari HP masuk ke laptop itu). Verifikasi status skrip jalannya di README sebelum mengandalkannya.
- Lokasi simpan bawaan di `Documents`; tidak ada folder shared/kiriman di dalam folder proyek.

## 7. Aturan kerja pemilik (penting untuk kolaborator)

1. **Diskusi dulu, kerjakan setelah ada perintah eksplisit** ("gas"/"kerjakan").
2. **Tampilan disetujui dulu lewat ilustrasi** sebelum kode disentuh.
3. Web dan Android dibuat **semirip mungkin**.
4. Setiap pembaruan **menyebut nomor versi** PC dan Android.
5. Nama folder tidak dikunci ke satu format; pemakaian tidak terbatas pada pekerjaan.

## 8. Peta jalan dan hal yang belum selesai

- **3.4.0**: HP↔HP lewat aplikasi (sambung sekali dengan kode, antrean ke HP, opsi "Selalu menyala").
- Fitur "Keluarkan tamu" di Host HP.
- Usulan yang belum diputuskan: HTTPS, tombol "Salin info masalah", mengarahkan lokasi simpan ke OneDrive kantor.
- Folder bermasalah di Antrean (gagal/tertahan lama) belum punya status khusus.
- Versi internet sengaja ditunda; fokus dulu yang tanpa internet.

## 9. Pegangan untuk tim

- Mulai dari `README.md` (riwayat per versi) dan `RINGKASAN_PROYEK.md` (kondisi terkini, daftar cek uji HP).
- Sebelum mengubah tampilan, lihat papan desain; sebelum mengubah penamaan file, baca `storage.py` (`commit`, `commit_numbered`) dan `routes/files.py`.
- Jangan mengubah pencegah duplikat tanpa memahami lanjut-unggah: keduanya bergantung pada `uid` dan isi file, bukan hanya nama.
