(async function () {
  const qr = document.getElementById('qr');
  const addr = document.getElementById('addr');
  const note = document.getElementById('note');
  try {
    const d = await (await fetch('/api/info')).json();
    if (d.local_name) {
      qr.src = '/api/qr?name=1';
      addr.textContent = d.local_name.replace('http://', '');
      note.textContent = 'QR ini tetap berlaku walau IP PC berubah. Jika tidak terbuka di Android lama, ketik alamat IP dari layar PC.';
    } else {
      qr.src = '/api/qr?i=0';
      addr.textContent = (d.urls[0] || '').replace('http://', '');
      note.textContent = 'Nama tetap (.local) nonaktif; QR ini memuat alamat IP saat ini.';
    }
  } catch (e) {
    note.textContent = 'Tidak bisa membaca alamat PC.';
  }
})();
