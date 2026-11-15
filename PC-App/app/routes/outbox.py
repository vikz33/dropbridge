"""Kotak kiriman laptop → HP.

Laptop menaruh file untuk HP tertentu (kunci perangkat tetap, bukan sesi). HP mengambilnya sendiri:
aplikasi saat terbuka atau saat cek latar belakang, browser lewat kartu "Ada file dari laptop".
File disimpan di data/outbox/<kunci>/ sampai diterima; yang tidak diambil 7 hari dibuang.
"""
import json
import secrets
import socket
import threading
import time
from pathlib import Path
from urllib.parse import quote

from fastapi import APIRouter, Depends, HTTPException, Request
from fastapi.responses import FileResponse

from .. import auth
from ..auth import require_auth
from ..config import settings
from ..services.storage import storage
from .system import _pc_only

router = APIRouter(prefix="/api/outbox", dependencies=[Depends(require_auth)])

ROOT = settings.data_dir / "outbox"
INDEX = settings.data_dir / "outbox.json"
KEEP_WAITING = 7 * 86400
KEEP_DONE = 86400
_lock = threading.Lock()


def _load() -> list[dict]:
    try:
        return json.loads(INDEX.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return []


def _save(items: list[dict]) -> None:
    INDEX.parent.mkdir(parents=True, exist_ok=True)
    tmp = INDEX.with_suffix(".tmp")
    tmp.write_text(json.dumps(items, ensure_ascii=False), encoding="utf-8")
    tmp.replace(INDEX)


def _tidy(items: list[dict]) -> list[dict]:
    now = time.time()
    keep = []
    for it in items:
        if it["status"] == "waiting" and now - it["ts"] > KEEP_WAITING:
            _path(it).unlink(missing_ok=True)
            continue
        if it["status"] == "received" and now - it.get("done", it["ts"]) > KEEP_DONE:
            continue
        keep.append(it)
    return keep


def _path(it: dict) -> Path:
    return ROOT / it["dev"] / f'{it["id"]}.bin'


def _mine(request: Request, item_id: str) -> dict:
    key = auth.device_key(request)
    with _lock:
        for it in _load():
            if it["id"] == item_id and key and it["dev"] == key:
                return it
    raise HTTPException(status_code=404, detail="Kiriman tidak ditemukan.")


def _mark(item_id: str) -> None:
    with _lock:
        items = _load()
        for it in items:
            if it["id"] == item_id and it["status"] == "waiting":
                it["status"] = "received"
                it["done"] = time.time()
                _path(it).unlink(missing_ok=True)
        _save(_tidy(items))


# ---------- Dari laptop ini ----------

@router.get("/phones", dependencies=[Depends(_pc_only)])
def phones():
    """HP yang bisa dikirimi file: pernah diizinkan atau sedang masuk."""
    return {"phones": auth.known_phones()}


@router.post("/upload", dependencies=[Depends(_pc_only)])
async def upload(request: Request, dev: str, name: str):
    """Isi body = file mentah, ditaruh di kotak kiriman HP `dev`."""
    key = auth._devkey(dev)
    if not key:
        raise HTTPException(status_code=400, detail="HP tujuan tidak dikenal.")
    clean = storage.sanitize_file(name or "file")
    it = {"id": secrets.token_hex(6), "dev": key, "name": clean, "size": 0, "ts": time.time(),
          "status": "waiting", "from": socket.gethostname() or "Laptop"}
    path = _path(it)
    path.parent.mkdir(parents=True, exist_ok=True)
    size = 0
    with open(path, "wb") as out:
        async for chunk in request.stream():
            size += len(chunk)
            if size > settings.max_upload_bytes:
                out.close()
                path.unlink(missing_ok=True)
                raise HTTPException(status_code=413, detail=f"{clean}: melebihi batas {settings.max_upload_mb} MB")
            out.write(chunk)
    it["size"] = size
    with _lock:
        items = _tidy(_load())
        items.append(it)
        _save(items)
    return it


@router.get("/sent", dependencies=[Depends(_pc_only)])
def sent(dev: str = ""):
    """Status kiriman ke HP (menunggu diambil / diterima), terbaru dulu."""
    with _lock:
        items = _tidy(_load())
    if dev:
        items = [i for i in items if i["dev"] == dev]
    return {"items": sorted(items, key=lambda i: -i["ts"])[:50]}


@router.delete("/{item_id}", dependencies=[Depends(_pc_only)])
def cancel(item_id: str):
    with _lock:
        items = _load()
        for it in items:
            if it["id"] == item_id and it["status"] == "waiting":
                _path(it).unlink(missing_ok=True)
        _save([i for i in items if not (i["id"] == item_id and i["status"] == "waiting")])
    return {"ok": True}


# ---------- Diambil oleh HP ----------

@router.get("")
def inbox(request: Request):
    """Kiriman dari laptop yang menunggu diambil HP ini."""
    key = auth.device_key(request)
    if not key:
        return {"items": []}
    with _lock:
        items = [i for i in _tidy(_load()) if i["dev"] == key and i["status"] == "waiting"]
    return {"items": [{k: i[k] for k in ("id", "name", "size", "ts", "from")} for i in items]}


@router.get("/{item_id}/file")
def take(item_id: str, request: Request, mark: int = 0):
    """Isi file. Browser memakai mark=1 (dianggap diterima begitu diunduh); aplikasi memanggil /done setelah tersimpan."""
    it = _mine(request, item_id)
    p = _path(it)
    if not p.exists():
        raise HTTPException(status_code=404, detail="File sudah tidak ada.")
    resp = FileResponse(p, media_type="application/octet-stream",
                        headers={"Content-Disposition": f"attachment; filename*=UTF-8''{quote(it['name'])}"})
    if mark:
        from starlette.background import BackgroundTask
        resp.background = BackgroundTask(_mark, item_id)
    return resp


@router.post("/{item_id}/done")
def done(item_id: str, request: Request):
    _mine(request, item_id)
    _mark(item_id)
    return {"ok": True}
