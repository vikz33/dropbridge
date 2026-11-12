"""Fitur khusus laptop/PC ini: folder penyimpanan, buka Explorer, halaman Selamat datang, notifikasi,
dan unduhan aplikasi Android untuk HP."""
import re
from pathlib import Path
from typing import Optional

from fastapi import APIRouter, Depends, HTTPException, Request
from fastapi.responses import FileResponse
from pydantic import BaseModel

from .. import auth, events
from ..config import FROZEN, save_setting, settings
from ..services import storage_admin
from ..services.storage import StorageError
from ..version import VERSION

router = APIRouter(prefix="/api")

_APK_RE = re.compile(r"^DropBridge-(\d+(?:\.\d+){1,3})\.apk$", re.IGNORECASE)


def _ver_key(v: str) -> tuple:
    return tuple(int(x) for x in v.split("."))


def android_apk() -> Optional[tuple[Path, str]]:
    """APK Android terbaru (DropBridge-<versi>.apk) yang ikut terpasang, atau hasil build_apk.bat."""
    best = None
    for d in settings.apk_dirs:
        try:
            for p in d.iterdir():
                m = _APK_RE.match(p.name)
                if m and p.is_file() and (best is None or _ver_key(m.group(1)) > _ver_key(best[1])):
                    best = (p, m.group(1))
        except OSError:
            continue
    return best


def android_info() -> dict:
    a = android_apk()
    return {"available": bool(a), "version": a[1] if a else "", "size": a[0].stat().st_size if a else 0}


def _pc_only(request: Request) -> None:
    """Hanya dari laptop/PC ini (bukan dari HP), dan sudah masuk."""
    auth.require_auth(request)
    if not auth.is_this_pc(request):
        raise HTTPException(status_code=403, detail="Hanya bisa dari PC tempat DropBridge berjalan.")


# ---------- Aplikasi Android ----------
@router.get("/app/android")
def download_android():
    """Unduh APK dari PC ini (tanpa login, supaya HP baru bisa memasang aplikasinya)."""
    a = android_apk()
    if not a:
        raise HTTPException(status_code=404, detail="APK belum ada di PC ini.")
    return FileResponse(a[0], media_type="application/vnd.android.package-archive", filename=a[0].name)


# ---------- Folder penyimpanan ----------
class StorageIn(BaseModel):
    path: str
    move: bool = False


@router.get("/storage", dependencies=[Depends(_pc_only)])
def storage_info():
    od = storage_admin.onedrive_work()
    return {
        "path": str(settings.shared_dir),
        "files": storage_admin.count_files(settings.shared_dir),
        "onedrive": str(od) if od else "",
        "imported_from": settings.imported_from,
    }


@router.post("/storage/pick", dependencies=[Depends(_pc_only)])
def storage_pick():
    """Buka dialog pilih folder Windows di layar PC ini, lalu kembalikan pilihannya (belum dipakai)."""
    od = storage_admin.onedrive_work()
    p = storage_admin.pick_folder(od if od else settings.shared_dir)
    if p is None:
        return {"path": ""}
    try:
        storage_admin.check_target(p)
    except StorageError as e:
        return {"path": str(p), "error": e.message}
    return {"path": str(p), "files": storage_admin.count_files(settings.shared_dir)}


@router.post("/storage", dependencies=[Depends(_pc_only)])
def storage_switch(body: StorageIn):
    return storage_admin.switch(Path(body.path), body.move)


class OpenIn(BaseModel):
    folder: str = ""


@router.post("/storage/open", dependencies=[Depends(_pc_only)])
def storage_open(body: OpenIn):
    storage_admin.open_in_explorer(body.folder)
    return {"ok": True}


# ---------- Selamat datang dan notifikasi ----------
@router.post("/welcomed", dependencies=[Depends(_pc_only)])
def welcomed():
    save_setting("welcomed", VERSION)
    settings.welcomed = VERSION
    return {"ok": True}


class NotifyIn(BaseModel):
    on: bool


@router.post("/settings/notify", dependencies=[Depends(_pc_only)])
def set_notify(body: NotifyIn):
    save_setting("notify", body.on)
    settings.notify = body.on
    return {"ok": True, "notify": body.on}


# ---------- Jendela desktop ----------
@router.post("/desktop/show")
def desktop_show(request: Request, path: str = ""):
    """Dipanggil saat DropBridge dibuka lagi padahal sudah jalan: munculkan jendela yang ada."""
    host = request.client.host if request.client else ""
    if host not in ("127.0.0.1", "::1"):
        raise HTTPException(status_code=403, detail="Hanya dari PC ini.")
    events.emit("show", path)
    return {"ok": True}


def welcome_due() -> bool:
    """Halaman Selamat datang: sekali setelah pasang atau update versi terpasang."""
    return FROZEN and settings.welcomed != VERSION
