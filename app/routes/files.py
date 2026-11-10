"""File di dalam folder: daftar, unggah, unduh, thumbnail, pindah, dan hapus.

Semua endpoint menerima parameter `folder` (nama folder; kosong = file yang langsung di folder bersama).
"""
import time
from typing import Optional

from fastapi import APIRouter, Depends, File, Form, HTTPException, Request, UploadFile
from fastapi.responses import FileResponse, JSONResponse
from starlette.requests import ClientDisconnect
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


@router.get("/upload-offset")
def upload_offset(uid: str, folder: str = ""):
    """Berapa byte kiriman ini yang sudah sampai di PC (0 bila belum ada). Untuk melanjutkan kiriman yang putus."""
    folder_name, _ = storage.ensure_folder(folder)
    tmp = storage.resume_tmp(folder_name, uid)
    try:
        return {"offset": tmp.stat().st_size}
    except OSError:
        return {"offset": 0}


@router.post("/upload-raw")
async def upload_raw(
    request: Request,
    name: str,
    folder: str = "",
    last_modified: Optional[int] = None,
    uid: Optional[str] = None,
    offset: int = 0,
    total: Optional[int] = None,
):
    """Unggah cepat: isi body = isi file mentah (tanpa multipart), langsung ditulis ke disk.

    Dengan `uid`, kiriman bisa dilanjutkan: potongan yang sudah sampai disimpan walau koneksi
    putus, lalu HP mengirim sisanya mulai dari `offset`. File baru dianggap selesai bila
    ukurannya sama dengan `total`.
    """
    t0 = time.monotonic()
    folder_name, _ = storage.ensure_folder(folder)
    clean = storage.sanitize_file(name or "file")
    resumable = bool(uid)
    if resumable:
        tmp = storage.resume_tmp(folder_name, uid)
        have = tmp.stat().st_size if tmp.exists() else 0
        if offset != have:
            return JSONResponse({"detail": "Posisi kiriman tidak cocok.", "offset": have}, status_code=409)
        mode = "ab" if offset > 0 else "wb"
    else:
        tmp = storage.new_tmp(folder_name)
        mode = "wb"
    size = offset
    buf = bytearray()
    out = open(tmp, mode, buffering=0)
    try:
        async for chunk in request.stream():
            size += len(chunk)
            if size > settings.max_upload_bytes:
                out.close()
                storage.discard(tmp)
                raise HTTPException(status_code=413, detail=f"{clean}: melebihi batas {settings.max_upload_mb} MB")
            buf += chunk
            if len(buf) >= CHUNK:
                out.write(buf)
                buf.clear()
        if buf:
            out.write(buf)
            buf.clear()
    except HTTPException:
        raise
    except ClientDisconnect:
        # Koneksi HP putus: simpan yang sudah sampai (bila bisa dilanjutkan), tanpa jejak galat panjang.
        try:
            if resumable and buf:
                out.write(buf)
        except OSError:
            pass
        out.close()
        if not resumable:
            storage.discard(tmp)
        else:
            print(f"  [unggah] {clean}: koneksi putus di {tmp.stat().st_size / 1048576:.2f} MB, bisa dilanjutkan", flush=True)
        return JSONResponse({"detail": "Koneksi putus."}, status_code=400)
    except BaseException:
        if resumable:
            # Simpan yang sudah sampai; HP melanjutkan dari sini.
            try:
                if buf:
                    out.write(buf)
            except OSError:
                pass
            out.close()
        else:
            out.close()
            storage.discard(tmp)
        raise
    finally:
        if not out.closed:
            out.close()
    if resumable and total is not None:
        if size < total:
            return {"folder": folder_name, "partial": True, "offset": size}
        if size > total:
            # Potongan tidak cocok dengan ukuran file: buang, minta HP kirim ulang dari awal.
            storage.discard(tmp)
            return JSONResponse({"detail": "Ukuran tidak cocok, kirim ulang dari awal.", "offset": 0}, status_code=409)
    final, dup = storage.commit(tmp, folder_name, clean)
    if last_modified and not dup:
        storage.set_mtime(folder_name, final, last_modified)
    took = time.monotonic() - t0
    sent = size - offset
    note = " (sudah ada, tidak disimpan ulang)" if dup else ""
    print(f"  [unggah] {final} {sent / 1048576:.2f} MB, {took:.2f} dtk{note}", flush=True)
    return {
        "folder": folder_name,
        "saved": [{"name": final, "size": size, "duplicate": dup}],
        "errors": [],
        "took": round(took, 3),
    }


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
        final, dup = storage.commit(tmp, folder_name, name)
        if last_modified and len(files) == 1 and not dup:
            storage.set_mtime(folder_name, final, last_modified)
        saved.append({"name": final, "size": size, "duplicate": dup})
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
