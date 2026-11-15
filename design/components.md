# DropBridge 3 · Komponen

Satu spesifikasi untuk Web (`Web/`), jendela laptop (Windows/Linux, memakai Web) dan Android (`Ui.kt`).
Semua warna, jarak, sudut dan huruf diambil dari `tokens.json`, tidak ada hex langsung di komponen.

Aturan umum
- Target sentuh minimal 48 px; aksi utama 56 px (dipakai di gudang, sering pakai sarung tangan atau satu tangan).
- Nomor kontainer/dokumen (mis. `TCKU 6177329 › 30-23`) selalu memakai huruf `code` (IBM Plex Mono).
- Teal (`accent`) = PC. Indigo (`guest`) = HP lain / tamu. Jangan dicampur dalam satu tombol.
- Fokus keyboard: cincin 2 px `accent`, jarak 2 px, di semua elemen yang bisa diklik.
- Gerak mengikuti `motion`; mati bila sistem meminta kurangi gerak.

---

Button
  variants  primary (accent), secondary (surface + border), soft (accent-soft), guest (guest), danger (danger, teks), ghost
  sizes     md 48, lg 56 (aksi utama layar), sm 40 (hanya di baris padat, web)
  states    default, hover (web: terang 6%), pressed (gelap 8%), focus-visible, disabled (opacity .4), loading (spinner + label tetap)
  tokens    radius md, type label, padding x space-4
  a11y      <button> asli; saat loading aria-busy, label tidak hilang
  used on   semua layar

IconButton
  variants  outline (surface + border), plain
  size      48 × 48, ikon 22
  states    default, pressed, focus-visible, spinning (muat ulang: ikon berputar, motion base)
  a11y      wajib aria-label / contentDescription ("Muat ulang", "Pengaturan", "Pilih folder")
  used on   kepala halaman (muat ulang, pengaturan), kolom folder

Header
  isi       logo 40 · judul (title) + status di bawahnya (small, titik warna) · ikon kanan (maks. 2)
  tinggi    64; di dalam folder: tombol kembali + jejak lokasi menggantikan logo
  status    hijau success "laptop-mint · Wi-Fi", oranye warning "Tidak terjangkau", abu "Mencari PC…"
  baris 2   "diperbarui 10.19" (caption, text-muted) setelah muat ulang
  used on   Kirim, File, Hubungkan

BottomNav (HP) / SegmentNav (laptop)
  item      Kirim, File, Hubungkan; ikon 24 + label caption
  aktif     kapsul accent-soft di belakang ikon, label on-accent-soft 700
  tinggi    72 (HP); laptop: segmen di kepala halaman, kapsul aktif accent
  a11y      nav dengan aria-current pada item aktif

RouteCard (tujuan kiriman)  ← baru, pusat halaman Kirim
  isi       baris 1: ikon laptop + nama PC + sistem (Windows/Linux) + tombol "Ganti"
            baris 2: kolom folder (FolderField) di dalam kartu yang sama
  status    titik success/warning di ikon laptop
  peringatan PC terpilih mati: pita warning-soft "laptop-acer ada di Wi-Fi ini · Kirim ke sana?" (tidak pindah otomatis)
  used on   Kirim (HP), Bagikan dari Galeri

FolderField
  isi       tombol ikon folder (surface-2, 48) + isian (type code, border-input 2 px saat fokus accent)
  petunjuk  kanan atas: "Sudah ada" (accent) · "Subfolder baru" / "Folder baru" (text-muted) · "Maks. 3 tingkat" (danger)
  saran     pita warning-soft "Mirip dengan … Salah ketik?" hanya bila nama hampir sama
  chips     "Terakhir:" lalu Chip (maks. 6, gulir samping)
  used on   Kirim, Bagikan

Chip
  variants  soft (accent-soft / on-accent-soft), outline (border), guest (guest-soft)
  size      tinggi 36, radius pill, type small 600; nomor kontainer pakai code 14
  states    pressed, selected (border accent 2 px)

FolderRow (daftar) / FolderTile (kotak)
  row       ikon 44 (accent-soft) · nama (type code bila berupa nomor) · "3 folder · 12 file · 2 jam lalu" · ⋮
  waktu     dari file terbaru di dalam folder (bukan tanggal folder)
  tile      thumbnail 1:1 radius lg, nama 2 baris maks, ⋮ di pojok
  states    pressed, focus-visible, empty ("Belum ada folder")
  used on   File, Pilih folder

FileTile
  isi       thumbnail atau ikon jenis file (warna kind), nama 2 baris, ukuran · waktu
  states    terkirim (centang success), menunggu (jam warning), gagal (danger + "Coba lagi")

ActionPad (Kirim)  ← baru
  isi       tombol "Foto langsung" lg penuh (aksi paling sering di gudang),
            di bawahnya dua tombol md berdampingan: "Pilih file", "Kirim teks"
  catatan   "Foto dikecilkan ≤ 512 KB · Ubah" sebagai caption di bawahnya

ProgressCard
  isi       "Mengirim 4 dari 11 ke TCKU 6177329 › 30-23" · bar 6 px radius pill (accent di atas surface-2) · ukuran · Jeda
  states    berjalan, dijeda, menunggu PC (warning), selesai (success, hilang setelah 4 dtk)
  a11y      role progressbar dengan aria-valuenow

RecentStrip
  isi       3 kolom thumbnail 1:1 + nama folder (code, caption) · "Lihat di File"
  empty     "Belum ada kiriman hari ini"

Sheet (lembar bawah)
  isi       pegangan 40×4 · judul title-sm + "Tutup" · isi gulir · tombol aksi di bawah
  tokens    surface, radius xl atas, shadow-md, scrim
  motion    sheet; tutup dengan geser ke bawah, tombol Tutup, atau tombol kembali
  used on   Pilih folder, Pilih PC, menu ⋮, Urutkan

Dialog
  isi       judul, isi, maks. 2 tombol (utama di kanan)
  used on   Folder baru, Hapus (menyebut jumlah file), Ganti folder penyimpanan (laptop)

DeviceRow  ← baru (pengaman)
  isi       ikon HP · nama · "Penuh" / "Hanya kirim" (Chip) · terakhir aktif · ⋮ (Ubah hak, Putuskan)
  used on   Hubungkan (laptop dan HP)

ApprovalPrompt  ← baru (pengaman)
  di laptop kartu mengambang + notifikasi sistem: "HP Redmi Note 12 ingin terhubung"
            pilihan hak: Penuh / Hanya kirim (segmen, bawaan Hanya kirim) · tombol Tolak (secondary) · Izinkan (primary)
  di HP     layar tunggu "Minta izin ke laptop-mint… Tekan Izinkan di laptop" + Batal
  waktu     kedaluwarsa 2 menit
  a11y      fokus pindah ke kartu; Esc = Tolak

PinInput
  isi       6 kotak 48×56, type display, border-input; terisi = border accent
  error     getar pendek (bila gerak diizinkan) + pesan danger di bawah; kunci 1 menit setelah 5 salah

Banner
  variants  warning (PC tak terjangkau, versi beda), danger (gagal), info (accent-soft)
  isi       ikon · teks small 600 · aksi opsional di kanan

Switch / Toggle
  ukuran    52×32, thumb 26; aktif accent, nonaktif border-input
  a11y      role switch, label di kiri bisa diketuk

Toast / Snackbar
  isi       teks small, maks. 2 baris, aksi opsional ("Urungkan" setelah hapus ke Tempat Sampah)
  posisi    di atas BottomNav; 4 detik
