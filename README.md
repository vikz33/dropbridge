# DropBridge

Kirim file dan teks antara PC dan HP lewat Wi-Fi yang sama. Tanpa kabel, tanpa scan QR,
tanpa memasang aplikasi di HP. Cukup browser.

## Cara pakai

1. Pasang Python 3.10+ dari python.org (centang **Add python.exe to PATH**). Sekali saja.
2. Klik dua kali **start.bat**. Pertama kali akan memasang paket otomatis.
3. Jendela hitam menampilkan **alamat** dan **PIN**. Contoh:
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

- Login PIN (percobaan salah dibatasi: 5x, lalu kunci 60 detik)
- **Folder per barang/dokumen**: nama folder bebas diketik pengguna (mis. `27 012345`). Satu level, isinya datar.
  Folder dibuat otomatis saat file pertama dikirim.
- Kotak "Kirim ke folder": saran dari folder yang ada, chip folder terbaru, peringatan "Mirip dengan ..."
  jika nama hampir sama dengan folder lain (mencegah salah ketik), dan folder terakhir diingat.
- Kirim banyak foto/file sekaligus dari HP, dengan progress per file. Kualitas foto asli, nama file asli.
  File diurutkan menurut waktu foto aslinya (terlama dulu). Unggahan dikirim mentah dan langsung ditulis ke disk (cepat).
- Lihat isi folder sebagai kotak thumbnail; pratinjau dengan geser/panah; **Pindah** dan **Hapus** per file.
- Dari HP: ganti nama folder, hapus folder (dengan konfirmasi yang menyebut jumlah file), unduh folder sebagai ZIP.
- Di PC: seret file ke halaman (masuk ke folder yang tertulis di kotak), atau tempel gambar dengan Ctrl+V.
- Kirim teks atau link antar perangkat.
- Hanya menerima koneksi dari jaringan lokal.

Catatan: menghapus folder bersifat **permanen** (tidak masuk Recycle Bin). File yang ditaruh langsung di
`shared/` (tanpa folder) muncul sebagai "Tanpa folder". Thumbnail membutuhkan paket Pillow
(terpasang otomatis oleh start.bat saat ada internet).

## Aplikasi Android (opsional)

Folder `android/` berisi aplikasi pendamping (tanpa library, Kotlin). Fungsinya:
- **Menemukan PC otomatis** di Wi-Fi atau hotspot yang sama (siaran UDP port 47800, cadangan: pindai jaringan). Tidak perlu mengetik IP.
- **Menu Bagikan**: dari Galeri atau File Manager pilih foto, Bagikan, DropBridge, pilih folder.
- **Antrean**: kalau PC belum terjangkau, file ditahan di HP dan dikirim otomatis begitu PC ditemukan (aplikasi mencoba hingga 15 menit; sisanya bisa dikirim dari layar utama).
- Berbagi **teks** (mis. link) dari aplikasi lain langsung ke tab Teks di PC.
- **Lihat dan unduh dari PC**: telusuri folder di PC, ketuk file untuk mengunduh, atau unduh semua / ZIP. Hasil di folder Download/DropBridge di HP.
- **Mode host** (untuk iPhone atau HP lain tanpa aplikasi): HP ini jadi server mini. Nyalakan hotspot, ketuk Mulai host, lalu di perangkat lain scan QR (atau buka alamat dan ketik kode 6 angka). Perangkat lain bisa mengirim file ke HP ini (tersimpan di Download/DropBridge) dan mengambil file yang kamu bagikan. Kode salah 5 kali = dikunci 1 menit.
  Di halaman host ada tombol **Unduh semua**, **ZIP**, **Simpan ke Foto / Bagikan** (iPhone), dan kotak **Simpan otomatis file baru** (cukup centang sekali; browser mungkin meminta izin unduhan).
- **Terima otomatis** (HP Android penerima): sambungkan ke hotspot host, isi kode 6 angka, lalu setiap file yang dibagikan host tersimpan sendiri di Download/DropBridge.

Cara membangun: klik dua kali **build_android.bat** (butuh Android Studio/SDK dan JDK 17 seperti proyek lain).
Hasilnya `DropBridge.apk` ditaruh di `shared\DropBridge-APK\`, lalu unduh dari HP lewat halaman web DropBridge dan pasang.
Log build ada di `android\build_log.txt`.

Tips: isi `pin` tetap di `config.json` supaya PIN di aplikasi tidak perlu diisi ulang tiap start.bat dijalankan.

## Pengaturan (config.json)

| Kunci | Fungsi |
|---|---|
| `port` | Port server (default 8000) |
| `pin` | PIN login. Kosong = dibuat acak saat pertama jalan. Angka disarankan |
| `shared_dir` | Folder file bersama (relatif atau path absolut, mis. `D:/Transfer`) |
| `max_upload_mb` | Batas ukuran satu file unggahan |
| `session_hours` | Lama login bertahan |
| `lan_only` | `true` = tolak koneksi dari luar jaringan lokal |
| `enable_docs` | `true` = buka dokumentasi API di `/docs` |

Ubah lalu jalankan ulang start.bat.

## Struktur dan cara mengembangkan

```
dropbridge/
  start.bat            jalankan di Windows
  build_android.bat    bangun APK aplikasi Android
  run.py               titik masuk, menampilkan alamat dan PIN
  config.json          pengaturan (dibuat otomatis, tidak ikut GitHub)
  app/
    main.py            menggabungkan router, penjaga jaringan lokal
    auth.py            PIN -> token sesi (cookie atau header Bearer)
    config.py          membaca config.json
    discovery.py       membalas pencarian PC dari aplikasi Android (UDP 47800)
    routes/
      folders.py       /api/folders (daftar, buat, ganti nama, hapus, ZIP)
      files.py         /api/files (daftar, upload, unduh, pindah, thumbnail, hapus)
      clipboard.py     /api/clips (kirim teks)
    services/
      storage.py       penyimpanan file (sekarang folder lokal)
      thumbs.py        thumbnail foto (cache di data/thumbs)
  web/                 index.html, app.js, style.css
  android/             aplikasi Android pendamping (build_android.bat)
  shared/              file bersama (dibuat otomatis)
  data/                riwayat teks (clips.json, dibuat otomatis)
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
