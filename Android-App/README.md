# DropBridge Android

Aplikasi pendamping untuk **DropBridge** (server PC di `../PC-App`, tampilan web di `../Web`). Kotlin tanpa library tambahan. Dipakai untuk mengirim file dari HP ke PC dan sebaliknya tanpa kabel.

## Menu bawah
- **Kirim**: pilih tujuan **Ke PC** (folder, Pilih file, kecilkan foto, Kirim teks, antrean) atau **Ke HP lain** (pilih file, host menyala sendiri, teman scan QR).
- **File**: telusuri folder di PC, cari folder, unduh file/ZIP, atau "Kirim ke sini".
- **Hubungkan**: saklar "Berbagi dengan HP lain". Hotspot khusus menyala otomatis, lalu teman scan QR Wi-Fi (langkah 1, tanpa password) dan QR halaman (langkah 2). Bila hotspot khusus gagal, ada petunjuk memakai hotspot biasa.
- **Pengaturan** (ikon kanan atas): kirim otomatis, izin latar belakang, PIN dan alamat PC.

Warna: teal untuk PC, indigo untuk HP lain.

## Fitur
- **Menemukan PC otomatis** di Wi-Fi/hotspot yang sama (siaran UDP port 47800, cadangan: pindai jaringan). Tidak perlu mengetik IP.
- **Kirim otomatis**: antrean terkirim sendiri saat PC terjangkau.
- **Foto langsung ke folder**: tombol di tab Kirim membuka kamera berulang; setiap foto langsung dikecilkan dan masuk antrean folder itu, tidak ikut masuk Galeri. Tekan kembali di kamera untuk selesai.
- **Pengiriman dilanjutkan**: kiriman yang putus di tengah dilanjutkan dari bagian yang sudah sampai. File yang isinya sudah ada di folder PC dilewati.
- **Antrean per folder**: kartu di tab Kirim menampilkan 3 folder teratas; **Semua antrean** membuka halaman Antrean untuk melihat file per folder, memindahkan file ke folder lain, atau menghapusnya sebelum terkirim. Di Huawei, izinkan aplikasi jalan di latar belakang.
- **Kecilkan foto**: foto di atas 512 KB dikecilkan sebelum dikirim ke PC, bisa dimatikan di layar Kirim. Ke HP lain selalu resolusi asli.
- **Menu Bagikan**: dari Galeri/File Manager pilih Bagikan, DropBridge, pilih folder. Teks/link juga bisa dibagikan.
- **Mode host**: perangkat lain scan QR atau buka alamat dan ketik kode 6 angka; kode salah 5 kali dikunci 1 menit.
- **Foto dari HP lain masuk Galeri**: foto ke Pictures/DropBridge, video ke Movies/DropBridge, file lain ke Download/DropBridge.
- **Menu Bagikan**: dari Galeri pilih Bagikan, DropBridge, lalu pilih Ke HP lain atau PC.

## Membangun APK
Butuh Android SDK dan JDK 17.

1. Naikkan `versionName`/`versionCode` di `app/build.gradle.kts` bila rilis baru (major.minor sama dengan versi PC).
2. Klik dua kali `build_apk.bat`.
3. Hasil: `..\Rilis\DropBridge-<versi>.apk`. Log: `build_log.txt`.
   `PC-App\build_exe.bat` membungkus APK terbaru dari folder Rilis ke installer, dan HP bisa mengunduhnya dari menu Hubungkan.

APK ditandatangani dengan kunci debug (cukup untuk pemakaian pribadi).

## Server
Aplikasi ini bicara dengan API REST server DropBridge (login PIN di `POST /api/login`, upload di `POST /api/files/upload-raw`, discovery UDP 47800). Lihat `../README.md` untuk menjalankannya.
