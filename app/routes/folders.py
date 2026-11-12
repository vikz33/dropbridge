"""Folder (bertingkat, mis. "A/B"): daftar, buat, ganti nama, hapus, dan unduh sebagai ZIP."""
import re
from typing import Optional
from urllib.parse import quote

from fastapi import APIRouter, Depends
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field

from ..auth import require_auth
from ..services.storage import StorageError, storage

router = APIRouter(prefix="/api/folders", dependencies=[Depends(require_auth)])


class FolderIn(BaseModel):
    name: str = Field(min_length=1, max_length=200)


def content_disposition(filename: str, disposition: str = "attachment") -> str:
    fallback = re.sub(r"[^A-Za-z0-9._ -]", "_", filename).strip() or "download"
    return f"{disposition}; filename=\"{fallback}\"; filename*=UTF-8''{quote(filename)}"


@router.get("")
def list_folders():
    return storage.list_folders()


@router.post("")
def create_folder(body: FolderIn):
    name, created = storage.ensure_folder(body.name)
    return {"name": name, "created": created, "changed": name != body.name.strip()}


@router.patch("/{name:path}")
def rename_folder(name: str, body: FolderIn):
    return {"name": storage.rename_folder(name, body.name)}


@router.delete("/{name:path}")
def delete_folder(name: str, expect: Optional[int] = None):
    # `expect` wajib: jumlah file yang ditampilkan ke pengguna saat konfirmasi.
    if expect is None:
        raise StorageError("Parameter expect (jumlah file) wajib diisi.", 422)
    deleted = storage.delete_folder(name, expect)
    return {"ok": True, "deleted": deleted}


@router.get("/{name:path}/zip")
def zip_folder(name: str):
    filename, chunks = storage.zip_stream(name)
    return StreamingResponse(
        chunks,
        media_type="application/zip",
        headers={"Content-Disposition": content_disposition(filename)},
    )
