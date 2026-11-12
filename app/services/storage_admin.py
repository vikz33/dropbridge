r"""Mengganti folder penyimpanan (mis. ke OneDrive kantor) tanpa menjalankan ulang DropBridge."""
import hashlib
import os
import shutil
import subprocess
from pathlib import Path
from typing import Optional

from ..config import save_setting, settings
from .storage import StorageError, storage

NO_WINDOW = 0x08000000 if os.name == "nt" else 0


def onedrive_work() -> Optional[Path]:
    """Folder OneDrive kantor/sekolah (mis. 'OneDrive - Kementerian Keuangan'), bila ada."""
    for key in ("OneDriveCommercial", "OneDrive"):
        v = os.environ.get(key, "")
        if v and Path(v).is_dir() and (key == "OneDriveCommercial" or " - " in Path(v).name):
            return Path(v)
    return None


def pick_folder(start: Path) -> Optional[Path]:
    """Dialog pilih folder Windows (lewat PowerShell, jadi bisa dipanggil dari server maupun tray)."""
    if os.name != "nt":
        return None
    ps = (
        "[Console]::OutputEncoding=[Text.Encoding]::UTF8;"
        "Add-Type -AssemblyName System.Windows.Forms;"
        "$f=New-Object System.Windows.Forms.FolderBrowserDialog;"
        "$f.Description='Pilih folder tempat DropBridge menyimpan kiriman';"
        "$f.ShowNewFolderButton=$true;"
        f"$f.SelectedPath='{str(start).replace(chr(39), chr(39) * 2)}';"
        "$w=New-Object System.Windows.Forms.Form;$w.TopMost=$true;"
        "if($f.ShowDialog($w) -eq 'OK'){$f.SelectedPath}"
    )
    try:
        out = subprocess.run(["powershell", "-NoProfile", "-STA", "-Command", ps],
                             capture_output=True, timeout=600, creationflags=NO_WINDOW)
        path = out.stdout.decode("utf-8", "replace").strip()
        return Path(path) if path else None
    except (OSError, subprocess.SubprocessError):
        return None


def count_files(d: Path) -> int:
    if not d.is_dir():
        return 0
    return sum(len([n for n in names if not n.startswith(".")]) for _, _, names in os.walk(d))


def _same_file(a: Path, b: Path) -> bool:
    if a.stat().st_size != b.stat().st_size:
        return False

    def h(p: Path) -> str:
        d = hashlib.sha256()
        with open(p, "rb") as f:
            while chunk := f.read(1 << 20):
                d.update(chunk)
        return d.hexdigest()

    return h(a) == h(b)


def merge_move(src: Path, dst: Path) -> tuple[int, int, int]:
    """Pindahkan isi src ke dst, folder digabung. File yang sudah ada di dst tidak ditimpa.
    Hasil: (dipindah, kembar dilewati, bentrok dibiarkan di src)."""
    moved = same = clash = 0
    for base, _dirs, names in os.walk(src):
        rel = Path(base).relative_to(src)
        for n in names:
            if n.startswith(".upload-"):
                continue
            a = Path(base) / n
            b = dst / rel / n
            try:
                if b.exists():
                    if _same_file(a, b):
                        same += 1
                    else:
                        clash += 1
                    continue
                b.parent.mkdir(parents=True, exist_ok=True)
                shutil.move(str(a), str(b))
                moved += 1
            except OSError:
                clash += 1
    for base, _dirs, _names in os.walk(src, topdown=False):  # folder lama yang sudah kosong
        if Path(base) != src:
            try:
                os.rmdir(base)
            except OSError:
                pass
    return moved, same, clash


def check_target(target: Path) -> Path:
    old = settings.shared_dir.resolve()
    target = target.expanduser().resolve()
    if target == old:
        raise StorageError("DropBridge sudah menyimpan di folder itu.")
    if target.is_relative_to(old) or old.is_relative_to(target):
        raise StorageError("Pilih folder yang tidak berada di dalam (atau berisi) folder penyimpanan sekarang.")
    if len(str(target)) > 120:
        raise StorageError("Jalur folder terlalu panjang. Pilih folder yang lebih dekat ke akar drive atau OneDrive.")
    return target


def switch(target: Path, move: bool) -> dict:
    """Pakai target sebagai folder penyimpanan mulai sekarang. move = pindahkan juga isi folder lama."""
    target = check_target(Path(target))
    old = settings.shared_dir
    try:
        target.mkdir(parents=True, exist_ok=True)
        probe = target / ".dropbridge-cek"
        probe.write_text("ok", encoding="utf-8")
        probe.unlink()
    except OSError as e:
        raise StorageError(f"Folder tidak bisa dipakai: {e}")
    moved = same = clash = 0
    if move and old.is_dir():
        moved, same, clash = merge_move(old, target)
    save_setting("shared_dir", str(target))
    settings.shared_dir = target
    storage.set_root(target)
    return {"path": str(target), "old": str(old), "moved": moved, "same": same, "clash": clash}


def open_in_explorer(folder: str) -> None:
    """Buka folder (jalur DropBridge, mis. 'A/B') di File Explorer."""
    if os.name != "nt":
        raise StorageError("Hanya bisa di Windows.")
    d = storage._folder_dir(folder)
    os.startfile(str(d))  # type: ignore[attr-defined]
