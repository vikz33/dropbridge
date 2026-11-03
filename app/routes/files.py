"""File di dalam folder: daftar, unggah, unduh, thumbnail, pindah, dan hapus.

Semua endpoint menerima parameter `folder` (nama folder; kosong = file yang langsung di folder bersama).
"""
import time
from typing import Optional

from fastapi import APIRouter, Depends, File, Form, HTTPException, Request, UploadFile
from fastapi.responses import FileResponse
from pydantic import BaseModel, Field

from ..auth import require_auth
from ..config import settings
from ..services import thumbs
from ..services.storage import guess_mime, is_inline_safe, storage

router = APIRouter(prefix="/api/files", dependencies=[Depends(require_auth)])

CHUNK = 1024 * 1024


@router.get("")
def list_files(folder: str = ""):
    return {"folder": folder, "files": storage.list_files(folder)}


@router.post("/upload-raw")
async def upload_raw(request: Request, name: str, folder: str = "", last_modified: Optional[int] = None):
    """Unggah cepat: isi body = isi file mentah (tanpa multipart), langsung ditulis ke disk.

    Tidak ada parsing multipart dan tidak ada salinan sementara kedua, jadi begitu byte terakhir
    sampai, file praktis sudah selesai disimpan.
    """
    t0 = time.monotonic()
    folder_name, _ = storage.ensure_folder(folder)
    clean = storage.sanitize_file(name or "file")
    tmp = storage.new_tmp(folder_name)
    size = 0
    buf = bytearray()
    try:
        with open(tmp, "wb", buffering=0) as out:
            async for chunk in request.stream():
                size += len(chunk)
                if size > settings.max_upload_bytes:
                    raise HTTPException(status_code=413, detail=f"{clean}: melebihi batas {settings.max_upload_mb} MB")
                buf += chunk
                if len(buf) >= CHUNK:
                    out.write(buf)
                    buf.clear()
            if buf:
                out.write(buf)
    except BaseException:
        storage.discard(tmp)
        raise
    final = storage.commit(tmp, folder_name, clean)
    if last_modified:
        storage.set_mtime(folder_name, final, last_modified)
    took = time.monotonic() - t0
    print(f"  [unggah] {final} {size / 1048576:.2f} MB, {took:.2f} dtk di server ({size / 1048576 / max(took, 0.001):.1f} MB/dtk)", flush=True)
    return {"folder": folder_name, "saved": [{"name": final, "size": size}], "errors": [], "took": round(took, 3)}


@router.post("/upload")
async def upload(
    folder: str = "",
    files: list[UploadFile] = File(...),
    last_modified: Optional[int] = Form(None),
):
    folder_name, _ = storage.ensure_folder(folder)  # folder dibuat otomatis jika belum ada
    saved: list[dict] = []
    errors: list[str] = []
    for f in files:
        name = storage.sanitize_file(f.filename or "file")
        tmp = storage.new_tmp(folder_name)
        size = 0
        too_big = False
        try:
            with open(tmp, "wb") as out:
                while chunk := await f.read(CHUNK):
                    size += len(chunk)
                    if size > settings.max_upload_bytes:
                        too_big = True
                        break
                    out.write(chunk)
        except Exception:
            storage.discard(tmp)
            raise
        if too_big:
            storage.discard(tmp)
            errors.append(f"{name}: melebihi batas {settings.max_upload_mb} MB")
            continue
        final = storage.commit(tmp, folder_name, name)
        if last_modified and len(files) == 1:
            storage.set_mtime(folder_name, final, last_modified)
        saved.append({"name": final, "size": size})
    if errors and not saved:
        raise HTTPException(status_code=413, detail=errors[0])
    return {"folder": folder_name, "saved": saved, "errors": errors}


class MoveIn(BaseModel):
    folder: str = ""
    name: str = Field(min_length=1)
    to: str = Field(min_length=1, max_length=200)


@router.post("/move")
def move(body: MoveIn):
    to, final = storage.move(body.folder, body.name, body.to)
    return {"folder": to, "name": final}


@router.get("/{name}/thumb")
def thumb(name: str, folder: str = ""):
    path = storage.path_of(folder, name)
    if not thumbs.thumbable(guess_mime(path.name)):
        raise HTTPException(status_code=404, detail="Thumbnail tidak tersedia.")
    out = thumbs.make_thumb(path, f"{folder}/{name}")
    if out is None:
        raise HTTPException(status_code=404, detail="Thumbnail tidak tersedia.")
    return FileResponse(out, media_type="image/jpeg", headers={"Cache-Control": "private, max-age=86400"})


@router.get("/{name}")
def download(name: str, folder: str = "", inline: bool = False):
    path = storage.path_of(folder, name)
    mime = guess_mime(path.name)
    disposition = "inline" if inline and is_inline_safe(mime) else "attachment"
    return FileResponse(path, media_type=mime, filename=path.name, content_disposition_type=disposition)


@router.delete("/{name}")
def delete(name: str, folder: str = ""):
    storage.delete(folder, name)
    return {"ok": True}
