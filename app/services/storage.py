"""Lapisan penyimpanan file: satu level folder.

    shared/<nama folder>/<file>

Nama folder ditentukan bebas oleh pengguna. File yang langsung berada di shared/ (tanpa folder)
tetap ditampilkan sebagai folder khusus bernama kosong "".
Penggantinya nanti (mis. penyimpanan lain) cukup meniru method kelas LocalStorage.
"""
import io
import mimetypes
import os
import re
import secrets
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
    sink = _Sink()
    with zipfile.ZipFile(sink, "w", zipfile.ZIP_STORED, allowZip64=True) as z:
        for p in paths:
            try:
                zi = zipfile.ZipInfo.from_file(p, arcname=p.name, strict_timestamps=False)
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
    def clean_folder(raw: str) -> str:
        """Bersihkan nama folder dari karakter terlarang Windows. Selain itu bebas."""
        name = _BAD_CHARS.sub("_", raw or "")
        name = re.sub(r"\s+", " ", name).strip().strip(". ")
        name = name[:FOLDER_MAX].rstrip(". ")
        if not name:
            raise StorageError("Nama folder tidak valid.")
        if name.split(".")[0].strip().upper() in _RESERVED:
            name = "_" + name
        return name

    @staticmethod
    def _unique(d: Path, name: str) -> str:
        if not (d / name).exists():
            return name
        stem, ext = os.path.splitext(name)
        i = 1
        while (d / f"{stem} ({i}){ext}").exists():
            i += 1
        return f"{stem} ({i}){ext}"

    # ---------- folder ----------
    def _folder_dir(self, folder: str) -> Path:
        """Path folder yang sudah ada ('' = folder awal). 404 jika tidak ada atau tidak aman."""
        if folder == "":
            return self.root
        try:
            if folder.startswith(".") or "/" in folder or "\\" in folder or "\x00" in folder:
                raise FileNotFoundError
            d = self.root / folder
            if not d.is_dir() or d.resolve().parent != self.root:
                raise FileNotFoundError
            return d
        except (OSError, ValueError):
            raise StorageError("Folder tidak ditemukan.", 404)

    def _find_folder_ci(self, name: str) -> Optional[str]:
        """Cari folder yang namanya sama tanpa membedakan huruf besar/kecil."""
        key = name.casefold()
        with os.scandir(self.root) as it:
            for e in it:
                if e.is_dir(follow_symlinks=False) and e.name.casefold() == key:
                    return e.name
        return None

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
                    latest = max(latest, st.st_mtime)
        except OSError:
            pass
        return count, size, latest

    def list_folders(self) -> dict:
        folders = []
        with os.scandir(self.root) as it:
            for e in it:
                if e.name.startswith(".") or not e.is_dir(follow_symlinks=False):
                    continue
                count, size, _ = self._scan(Path(e.path))
                folders.append(
                    {"name": e.name, "count": count, "size": size, "modified": e.stat().st_mtime}
                )
        folders.sort(key=lambda f: f["modified"], reverse=True)  # aktivitas terakhir di atas
        count, size, latest = self._scan(self.root)
        return {"folders": folders, "root": {"count": count, "size": size, "modified": latest}}

    def ensure_folder(self, raw: str) -> Tuple[str, bool]:
        """Buat folder jika belum ada. Return (nama sebenarnya, apakah baru dibuat)."""
        if raw == "":
            return "", False
        name = self.clean_folder(raw)
        existing = self._find_folder_ci(name)
        if existing:
            return existing, False
        try:
            (self.root / name).mkdir()
        except FileExistsError:
            return name, False
        except OSError:
            raise StorageError("Folder tidak bisa dibuat. Coba nama lain.")
        return name, True

    def rename_folder(self, name: str, new_raw: str) -> str:
        if name == "":
            raise StorageError("Folder awal tidak bisa diganti namanya.")
        src = self._folder_dir(name)
        new = self.clean_folder(new_raw)
        if new == name:
            return name
        existing = self._find_folder_ci(new)
        if existing and existing.casefold() != name.casefold():
            raise StorageError("Sudah ada folder dengan nama itu.", 409)
        try:
            os.rename(src, self.root / new)
        except OSError:
            raise StorageError("Folder tidak bisa diganti namanya. Mungkin sedang dibuka di program lain.", 409)
        return new

    def delete_folder(self, name: str, expect: int) -> int:
        """Hapus folder beserta file di dalamnya. `expect` = jumlah file yang dilihat pengguna saat konfirmasi."""
        if name == "":
            raise StorageError("Folder awal tidak bisa dihapus.")
        d = self._folder_dir(name)
        with os.scandir(d) as it:
            entries = list(it)
        if any(e.is_dir(follow_symlinks=False) for e in entries):
            raise StorageError("Folder ini berisi subfolder. Hapus dari PC saja.", 409)
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
        if folder == "":
            raise StorageError("Pilih folder yang ingin diunduh.")
        d = self._folder_dir(folder)
        with os.scandir(d) as it:
            paths = [
                Path(e.path)
                for e in it
                if not e.name.startswith(".") and e.is_file(follow_symlinks=False)
            ]
        if not paths:
            raise StorageError("Folder masih kosong.")
        paths.sort(key=lambda p: p.name.lower())
        return folder + ".zip", _zip_iter(paths)

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

    def new_tmp(self, folder: str) -> Path:
        return self._folder_dir(folder) / f".upload-{secrets.token_hex(6)}.part"

    def commit(self, tmp: Path, folder: str, name: str) -> str:
        d = self._folder_dir(folder)
        final = self._unique(d, name)
        os.replace(tmp, d / final)
        return final

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
