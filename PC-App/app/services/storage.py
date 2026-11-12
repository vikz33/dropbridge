"""Lapisan penyimpanan file: folder bertingkat, maksimal 3 tingkat.

    shared/<folder>/<subfolder>/<subfolder>/<file>

Folder ditulis sebagai jalur dengan "/" (mis. "TRHU-4134291/30-1"). Nama folder ditentukan bebas oleh pengguna. File yang langsung berada di shared/ (tanpa folder)
tetap ditampilkan sebagai folder khusus bernama kosong "".
Penggantinya nanti (mis. penyimpanan lain) cukup meniru method kelas LocalStorage.
"""
import hashlib
import io
import mimetypes
import os
import re
import secrets
import shutil
import time
import zipfile
from pathlib import Path
from typing import Iterator, Optional, Tuple

from ..config import settings
from . import thumbs

_BAD_CHARS = re.compile(r'[<>:"/\\|?*\x00-\x1f]')
_RESERVED = {"CON", "PRN", "AUX", "NUL", *(f"COM{i}" for i in range(1, 10)), *(f"LPT{i}" for i in range(1, 10))}
_ARCHIVE = {".zip", ".rar", ".7z", ".tar", ".gz", ".bz2", ".xz"}
_DOC = {".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".odt", ".ods", ".odp", ".csv", ".rtf"}
FOLDER_MAX = 100
MAX_DEPTH = 3
PATH_MAX = 160  # jaga agar jalur lengkap tetap aman untuk batas 260 karakter Windows


class StorageError(Exception):
    """Kesalahan yang pesannya aman ditampilkan ke pengguna."""

    def __init__(self, message: str, status: int = 400, **extra) -> None:
        super().__init__(message)
        self.message = message
        self.status = status
        self.extra = extra


def guess_mime(name: str) -> str:
    return mimetypes.guess_type(name)[0] or "application/octet-stream"


def is_inline_safe(mime: str) -> bool:
    """Hanya tipe yang aman dibuka langsung di browser (tanpa risiko menjalankan skrip)."""
    if mime in ("application/pdf", "text/plain"):
        return True
    if mime.startswith(("video/", "audio/")):
        return True
    return mime.startswith("image/") and mime != "image/svg+xml"


def kind_of(name: str, mime: str) -> str:
    ext = os.path.splitext(name)[1].lower()
    if mime.startswith("image/"):
        return "image"
    if mime.startswith("video/"):
        return "video"
    if mime.startswith("audio/"):
        return "audio"
    if mime == "application/pdf" or ext in _DOC:
        return "doc"
    if ext in _ARCHIVE:
        return "archive"
    if mime.startswith("text/"):
        return "text"
    return "other"


class _Sink(io.RawIOBase):
    """Penampung sementara agar ZIP bisa dialirkan tanpa file perantara."""

    def __init__(self) -> None:
        super().__init__()
        self._buf = bytearray()
        self._pos = 0

    def writable(self) -> bool:
        return True

    def write(self, b) -> int:
        self._buf += b
        self._pos += len(b)
        return len(b)

    def tell(self) -> int:
        return self._pos

    def flush(self) -> None:
        pass

    def drain(self) -> bytes:
        data = bytes(self._buf)
        self._buf.clear()
        return data


def _zip_iter(paths) -> Iterator[bytes]:
    """paths: daftar (Path, nama di dalam ZIP)."""
    sink = _Sink()
    with zipfile.ZipFile(sink, "w", zipfile.ZIP_STORED, allowZip64=True) as z:
        for p, arc in paths:
            try:
                zi = zipfile.ZipInfo.from_file(p, arcname=arc, strict_timestamps=False)
                zi.compress_type = zipfile.ZIP_STORED  # foto sudah terkompres, simpan apa adanya (cepat)
                with open(p, "rb") as src, z.open(zi, "w") as dst:
                    while True:
                        chunk = src.read(1024 * 1024)
                        if not chunk:
                            break
                        dst.write(chunk)
                        data = sink.drain()
                        if data:
                            yield data
            except OSError:
                continue  # file hilang atau terkunci di tengah jalan: lewati
            data = sink.drain()
            if data:
                yield data
    data = sink.drain()
    if data:
        yield data


def arrived(st) -> float:
    """Kapan file masuk ke PC. mtime bisa diset ke waktu foto diambil, jadi pakai juga waktu dibuat."""
    born = getattr(st, "st_birthtime", None) or st.st_ctime
    return max(st.st_mtime, born)


class LocalStorage:
    def __init__(self, root: Path) -> None:
        root.mkdir(parents=True, exist_ok=True)
        self.root = root.resolve()

    # ---------- nama ----------
    @staticmethod
    def sanitize_file(name: str) -> str:
        base = os.path.basename((name or "").replace("\\", "/"))
        name = _BAD_CHARS.sub("_", base).strip().strip(". ")
        if not name:
            name = "file"
        if name.split(".")[0].upper() in _RESERVED:
            name = "_" + name
        if len(name) > 200:
            stem, ext = os.path.splitext(name)
            name = stem[: 200 - len(ext)] + ext
        return name

    @staticmethod
    def _clean_part(raw: str) -> str:
        """Satu nama folder: buang karakter terlarang Windows. Selain itu bebas."""
        name = _BAD_CHARS.sub("_", raw or "")
        name = re.sub(r"\s+", " ", name).strip().strip(". ")
        name = name[:FOLDER_MAX].rstrip(". ")
        if not name:
            return ""
        if name.split(".")[0].strip().upper() in _RESERVED:
            name = "_" + name
        return name

    @classmethod
    def clean_folder(cls, raw: str) -> str:
        """Jalur folder dari ketikan pengguna: "A/B/C" (atau pakai \\). Bagian kosong dibuang."""
        parts = [cls._clean_part(x) for x in re.split(r"[/\\]", raw or "")]
        parts = [x for x in parts if x]
        if not parts:
            raise StorageError("Nama folder tidak valid.")
        if len(parts) > MAX_DEPTH:
            raise StorageError(f"Folder paling banyak {MAX_DEPTH} tingkat, mis. Kontainer/Pos/Tanggal.")
        path = "/".join(parts)
        if len(path) > PATH_MAX:
            raise StorageError("Nama folder terlalu panjang.")
        return path

    @staticmethod
    def _unique(d: Path, name: str) -> str:
        if not (d / name).exists():
            return name
        stem, ext = os.path.splitext(name)
        i = 1
        while (d / f"{stem} ({i}){ext}").exists():
            i += 1
        return f"{stem} ({i}){ext}"

    def set_root(self, root: Path) -> None:
        """Ganti folder penyimpanan saat berjalan (dipakai saat pindah ke OneDrive atau folder lain)."""
        root = Path(root)
        root.mkdir(parents=True, exist_ok=True)
        self.root = root.resolve()

    # ---------- folder ----------
    @staticmethod
    def _parts(folder: str) -> list:
        """Pecah jalur folder yang dikirim klien. Tolak bagian berbahaya (.., nama tersembunyi)."""
        parts = (folder or "").replace("\\", "/").split("/")
        if len(parts) > MAX_DEPTH + 2:
            raise StorageError("Folder tidak ditemukan.", 404)
        for x in parts:
            if not x or x.startswith(".") or "\x00" in x or x != x.strip():
                raise StorageError("Folder tidak ditemukan.", 404)
        return parts

    def _folder_dir(self, folder: str) -> Path:
        """Path folder yang sudah ada ('' = folder awal). 404 jika tidak ada atau keluar dari folder bersama."""
        if folder == "":
            return self.root
        parts = self._parts(folder)
        try:
            d = self.root.joinpath(*parts)
            if not d.is_dir():
                raise FileNotFoundError
            rel = d.resolve().relative_to(self.root)  # gagal bila lewat symlink keluar folder bersama
            if len(rel.parts) != len(parts):
                raise FileNotFoundError
            return d
        except (OSError, ValueError):
            raise StorageError("Folder tidak ditemukan.", 404)

    @staticmethod
    def _find_ci(parent: Path, name: str) -> Optional[str]:
        """Cari subfolder yang namanya sama tanpa membedakan huruf besar/kecil."""
        key = name.casefold()
        try:
            with os.scandir(parent) as it:
                for e in it:
                    if e.is_dir(follow_symlinks=False) and e.name.casefold() == key:
                        return e.name
        except OSError:
            pass
        return None

    def _walk(self, d: Path, path: str, depth: int, out: list) -> Tuple[int, int, float]:
        """Hitung isi folder d beserta subfoldernya. Folder sampai MAX_DEPTH dicatat ke out."""
        count = size = 0
        latest = 0.0
        subs = []
        try:
            with os.scandir(d) as it:
                for e in it:
                    if e.name.startswith("."):
                        continue
                    if e.is_file(follow_symlinks=False):
                        try:
                            st = e.stat()
                        except OSError:
                            continue
                        count += 1
                        size += st.st_size
                        latest = max(latest, arrived(st))
                    elif e.is_dir(follow_symlinks=False):
                        subs.append(e)
        except OSError:
            return 0, 0, 0.0
        files = count
        for e in subs:
            child = f"{path}/{e.name}" if path else e.name
            if depth >= 6:  # terlalu dalam: abaikan (dibuat manual di PC)
                continue
            c, z, t = self._walk(Path(e.path), child, depth + 1, out if depth < MAX_DEPTH else [])
            count += c
            size += z
            latest = max(latest, t)
        if path and depth <= MAX_DEPTH:
            try:
                mt = d.stat().st_mtime
            except OSError:
                mt = 0.0
            out.append({
                "name": path,                      # jalur lengkap, mis. "TRHU-4134291/30-1"
                "label": path.rsplit("/", 1)[-1],  # nama yang ditampilkan
                "parent": path.rsplit("/", 1)[0] if "/" in path else "",
                "depth": depth,
                "count": count,                    # semua file, termasuk di subfolder
                "size": size,
                "files": files,                    # file langsung di folder ini
                "subs": len([e for e in subs]),
                "modified": max(latest, mt),
            })
        return count, size, latest

    def list_folders(self) -> dict:
        """Semua folder (sampai 3 tingkat) sebagai daftar datar; klien menyusun pohonnya lewat `parent`."""
        out: list = []
        self._walk(self.root, "", 0, out)
        out.sort(key=lambda f: f["modified"], reverse=True)  # aktivitas terakhir di atas
        count, size, latest = self._scan(self.root)
        return {"folders": out, "root": {"count": count, "size": size, "modified": latest}, "max_depth": MAX_DEPTH}

    @staticmethod
    def _scan(d: Path) -> Tuple[int, int, float]:
        count = size = 0
        latest = 0.0
        try:
            with os.scandir(d) as it:
                for e in it:
                    if e.name.startswith(".") or not e.is_file(follow_symlinks=False):
                        continue
                    st = e.stat()
                    count += 1
                    size += st.st_size
                    latest = max(latest, arrived(st))
        except OSError:
            pass
        return count, size, latest

    def ensure_folder(self, raw: str) -> Tuple[str, bool]:
        """Buat folder (dan folder induknya) jika belum ada. Return (jalur sebenarnya, apakah ada yang baru dibuat)."""
        if raw == "":
            return "", False
        parts = self.clean_folder(raw).split("/")
        d = self.root
        real = []
        created = False
        for x in parts:
            existing = self._find_ci(d, x)
            if existing is None:
                try:
                    (d / x).mkdir()
                    created = True
                except FileExistsError:
                    pass
                except OSError:
                    raise StorageError("Folder tidak bisa dibuat. Coba nama lain.")
                existing = x
            real.append(existing)
            d = d / existing
        return "/".join(real), created

    def rename_folder(self, name: str, new_raw: str) -> str:
        """Ganti nama folder terakhir pada jalur; folder induknya tetap."""
        if name == "":
            raise StorageError("Folder awal tidak bisa diganti namanya.")
        src = self._folder_dir(name)
        new = self._clean_part(new_raw)
        if not new or "/" in (new_raw or "").strip("/") or "\\" in (new_raw or ""):
            raise StorageError("Nama baru tidak boleh berisi / . Untuk memindah folder, pakai File Explorer di PC.")
        parent = name.rsplit("/", 1)[0] if "/" in name else ""
        old = name.rsplit("/", 1)[-1]
        if new == old:
            return name
        existing = self._find_ci(src.parent, new)
        if existing and existing.casefold() != old.casefold():
            raise StorageError("Sudah ada folder dengan nama itu.", 409)
        full = f"{parent}/{new}" if parent else new
        if len(full) > PATH_MAX:
            raise StorageError("Nama folder terlalu panjang.")
        try:
            os.rename(src, src.parent / new)
        except OSError:
            raise StorageError("Folder tidak bisa diganti namanya. Mungkin sedang dibuka di program lain.", 409)
        return full

    def delete_folder(self, name: str, expect: int) -> int:
        """Hapus folder beserta file di dalamnya. `expect` = jumlah file yang dilihat pengguna saat konfirmasi."""
        if name == "":
            raise StorageError("Folder awal tidak bisa dihapus.")
        d = self._folder_dir(name)
        with os.scandir(d) as it:
            entries = list(it)
        if any(e.is_dir(follow_symlinks=False) and not e.name.startswith(".") for e in entries):
            raise StorageError("Folder ini berisi subfolder. Hapus subfoldernya dulu.", 409)
        files = [e for e in entries if e.is_file(follow_symlinks=False)]
        visible = [e for e in files if not e.name.startswith(".")]
        if len(visible) != expect:
            raise StorageError(
                "Isi folder berubah sejak konfirmasi. Cek lagi sebelum menghapus.", 409, count=len(visible)
            )
        for e in files:
            os.unlink(e.path)
        try:
            d.rmdir()
        except OSError:
            raise StorageError("Folder tidak kosong setelah dihapus. Hapus sisanya dari PC.", 409)
        return len(visible)

    def zip_stream(self, folder: str) -> Tuple[str, Iterator[bytes]]:
        """ZIP berisi semua file folder ini, termasuk subfolder (jalur relatif tetap)."""
        if folder == "":
            raise StorageError("Pilih folder yang ingin diunduh.")
        d = self._folder_dir(folder)
        items = []
        for base, dirs, names in os.walk(d):
            dirs[:] = sorted(x for x in dirs if not x.startswith("."))
            rel = os.path.relpath(base, d)
            for n in sorted(names, key=str.lower):
                if n.startswith("."):
                    continue
                p = Path(base) / n
                if p.is_symlink():
                    continue
                items.append((p, n if rel == "." else f"{rel.replace(os.sep, '/')}/{n}"))
        if not items:
            raise StorageError("Folder masih kosong.")
        return folder.rsplit("/", 1)[-1] + ".zip", _zip_iter(items)

    # ---------- file ----------
    def list_files(self, folder: str) -> list:
        d = self._folder_dir(folder)
        items = []
        with os.scandir(d) as it:
            for e in it:
                if e.name.startswith(".") or not e.is_file(follow_symlinks=False):
                    continue
                try:
                    st = e.stat()
                except OSError:
                    continue
                mime = guess_mime(e.name)
                items.append(
                    {
                        "name": e.name,
                        "size": st.st_size,
                        "modified": st.st_mtime,
                        "mime": mime,
                        "kind": kind_of(e.name, mime),
                        "previewable": is_inline_safe(mime),
                        "thumb": thumbs.thumbable(mime),
                    }
                )
        items.sort(key=lambda i: (i["modified"], i["name"].lower()))  # urutan waktu (foto sesuai urutan diambil)
        return items

    def recent_files(self, limit: int = 8) -> list:
        """File terbaru dari semua folder (untuk "Masuk terakhir" di halaman Kirim)."""
        found = []

        def take(d: Path, folder: str) -> None:
            try:
                with os.scandir(d) as it:
                    for e in it:
                        if e.name.startswith(".") or not e.is_file(follow_symlinks=False):
                            continue
                        try:
                            st = e.stat()
                        except OSError:
                            continue
                        found.append((arrived(st), folder, e.name, st.st_size))
            except OSError:
                pass

        take(self.root, "")
        for base, dirs, _ in os.walk(self.root):
            rel = os.path.relpath(base, self.root)
            depth = 0 if rel == "." else rel.count(os.sep) + 1
            dirs[:] = [x for x in dirs if not x.startswith(".")] if depth < MAX_DEPTH else []
            for x in dirs:
                folder = x if rel == "." else f"{rel.replace(os.sep, '/')}/{x}"
                take(Path(base) / x, folder)
        found.sort(reverse=True)
        out = []
        for mtime, folder, name, size in found[:limit]:
            mime = guess_mime(name)
            out.append({"folder": folder, "name": name, "size": size, "modified": mtime,
                        "kind": kind_of(name, mime), "thumb": thumbs.thumbable(mime)})
        return out

    def path_of(self, folder: str, name: str) -> Path:
        d = self._folder_dir(folder)
        try:
            if not name or name.startswith(".") or "/" in name or "\\" in name:
                raise FileNotFoundError
            p = d / name
            if not p.is_file() or p.resolve().parent != d.resolve():
                raise FileNotFoundError
            return p
        except (OSError, ValueError):
            raise StorageError("File tidak ditemukan.", 404)

    def _incoming(self) -> Path:
        settings.incoming_dir.mkdir(parents=True, exist_ok=True)
        return settings.incoming_dir

    def new_tmp(self, folder: str) -> Path:
        """File sementara di data/incoming (di luar folder shared), baru dipindah ke folder tujuan saat lengkap."""
        self._folder_dir(folder)  # pastikan folder tujuan sah
        return self._incoming() / f"upload-{secrets.token_hex(6)}.part"

    def resume_tmp(self, folder: str, uid: str) -> Path:
        """File sementara tetap per kiriman (uid dari HP), supaya kiriman yang putus bisa dilanjutkan."""
        uid = re.sub(r"[^A-Za-z0-9_-]", "", uid)[:64]
        if not uid:
            raise StorageError("ID kiriman tidak sah.", 400)
        self._folder_dir(folder)
        key = hashlib.sha1(f"{folder.casefold()}|{uid}".encode("utf-8")).hexdigest()[:20]
        return self._incoming() / f"upload-r-{key}.part"

    @staticmethod
    def _sha256(p: Path) -> str:
        h = hashlib.sha256()
        with open(p, "rb") as f:
            while chunk := f.read(1 << 20):
                h.update(chunk)
        return h.hexdigest()

    def _find_same(self, d: Path, name: str, tmp: Path) -> Optional[str]:
        """Cari file yang isinya persis sama dengan tmp (nama sama atau 'nama (n)')."""
        size = tmp.stat().st_size
        stem, ext = os.path.splitext(name)
        cands = [name] + [f"{stem} ({i}){ext}" for i in range(1, 51)]
        digest = None
        for c in cands:
            p = d / c
            try:
                if not p.is_file() or p.stat().st_size != size:
                    continue
            except OSError:
                continue
            if digest is None:
                digest = self._sha256(tmp)
            if self._sha256(p) == digest:
                return c
        return None

    def commit(self, tmp: Path, folder: str, name: str, dedupe: bool = True) -> Tuple[str, bool]:
        """Simpan tmp sebagai file final. Mengembalikan (nama, ganda).

        Bila di folder itu sudah ada file yang isinya persis sama, kiriman ini dibuang
        dan nama file yang sudah ada dikembalikan (ganda = True), supaya tidak ada 'foto (1).jpg'.
        """
        d = self._folder_dir(folder)
        if dedupe:
            same = self._find_same(d, name, tmp)
            if same:
                tmp.unlink(missing_ok=True)
                return same, True
        final = self._unique(d, name)
        try:
            os.replace(tmp, d / final)
        except OSError:
            # Folder shared di drive lain (mis. D:): salin lalu hapus, bukan sekadar ganti nama
            shutil.move(str(tmp), str(d / final))
        return final, False

    def clean_stale_parts(self, max_age_s: float = 3 * 86400) -> int:
        """Hapus sisa kiriman yang tidak pernah selesai (lebih dari 3 hari)."""
        n = 0
        now = time.time()
        try:
            for p in settings.incoming_dir.glob("upload-*.part"):
                if now - p.stat().st_mtime > max_age_s:
                    p.unlink(missing_ok=True)
                    n += 1
        except OSError:
            pass
        for base, dirs, _ in os.walk(self.root):  # sisa versi lama yang menulis .part di folder shared
            dirs[:] = [x for x in dirs if not x.startswith(".")]
            d = Path(base)
            try:
                for p in d.glob(".upload-*.part"):
                    if now - p.stat().st_mtime > max_age_s:
                        p.unlink(missing_ok=True)
                        n += 1
            except OSError:
                continue
        return n

    def discard(self, tmp: Path) -> None:
        tmp.unlink(missing_ok=True)

    def set_mtime(self, folder: str, name: str, ts_ms: int) -> None:
        """Pakai waktu asli file dari HP (jika masuk akal) supaya urutan foto tetap sesuai waktu diambil."""
        t = ts_ms / 1000
        if not (946684800 <= t <= time.time() + 86400):  # antara tahun 2000 dan besok
            return
        try:
            os.utime(self.path_of(folder, name), (t, t))
        except (OSError, StorageError):
            pass

    def delete(self, folder: str, name: str) -> None:
        self.path_of(folder, name).unlink()

    def move(self, folder: str, name: str, to_raw: str) -> Tuple[str, str]:
        src = self.path_of(folder, name)
        to_name, _ = self.ensure_folder(to_raw)
        if to_name.casefold() == folder.casefold():
            raise StorageError("File sudah berada di folder itu.")
        dest = self._folder_dir(to_name)
        final = self._unique(dest, name)
        os.replace(src, dest / final)
        return to_name, final


storage = LocalStorage(settings.shared_dir)
