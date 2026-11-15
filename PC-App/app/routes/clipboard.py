"""Teks atau link antar perangkat. Riwayat disimpan di data/clips.json (maks 200).

Tiap teks mencatat pengirimnya (nama perangkat + "pc"/"hp") supaya daftar bisa menunjukkan asalnya.
Teks yang dihapus disimpan sebentar (30 detik) supaya bisa dikembalikan lewat "Urungkan".
"""
import json
import secrets
import threading
import time
from pathlib import Path

from fastapi import APIRouter, Depends, HTTPException, Request
from pydantic import BaseModel, Field

from ..auth import require_auth, require_full, sender
from ..config import settings

router = APIRouter(prefix="/api/clips", dependencies=[Depends(require_auth)])

UNDO_SECONDS = 30


class ClipStore:
    MAX = 200

    def __init__(self, path: Path) -> None:
        self.path = path
        self.lock = threading.Lock()
        self.items: list[dict] = []
        self.removed: dict[str, tuple[float, int, dict]] = {}  # id -> (waktu hapus, posisi, isi)
        path.parent.mkdir(parents=True, exist_ok=True)
        if path.exists():
            try:
                self.items = json.loads(path.read_text(encoding="utf-8"))
            except (OSError, ValueError):
                self.items = []

    def _save(self) -> None:
        tmp = self.path.with_suffix(".tmp")
        tmp.write_text(json.dumps(self.items, ensure_ascii=False), encoding="utf-8")
        tmp.replace(self.path)

    def all(self) -> list[dict]:
        with self.lock:
            return list(self.items)

    def add(self, text: str, source: str, name: str) -> dict:
        item = {"id": secrets.token_hex(6), "text": text, "source": source, "from": name, "ts": time.time()}
        with self.lock:
            self.items.insert(0, item)
            del self.items[self.MAX :]
            self._save()
        return item

    def remove(self, clip_id: str) -> bool:
        now = time.time()
        with self.lock:
            self.removed = {k: v for k, v in self.removed.items() if now - v[0] < UNDO_SECONDS}
            for i, it in enumerate(self.items):
                if it["id"] == clip_id:
                    self.removed[clip_id] = (now, i, it)
                    del self.items[i]
                    self._save()
                    return True
            return False

    def restore(self, clip_id: str) -> dict | None:
        with self.lock:
            got = self.removed.pop(clip_id, None)
            if not got or time.time() - got[0] > UNDO_SECONDS:
                return None
            _, pos, item = got
            self.items.insert(min(pos, len(self.items)), item)
            self._save()
            return item

    def clear(self) -> None:
        with self.lock:
            self.items = []
            self._save()


store = ClipStore(settings.data_dir / "clips.json")


class ClipIn(BaseModel):
    text: str = Field(min_length=1, max_length=20000)
    source: str = Field("lain", max_length=10)


@router.get("", dependencies=[Depends(require_full)])
def list_clips():
    return {"clips": store.all()}


@router.post("", status_code=201)
def add_clip(body: ClipIn, request: Request):
    text = body.text.strip()
    if not text:
        raise HTTPException(status_code=400, detail="Teks kosong.")
    who = sender(request)
    return store.add(text, who["dev"], who["name"])


@router.delete("/{clip_id}", dependencies=[Depends(require_full)])
def remove_clip(clip_id: str):
    if not store.remove(clip_id):
        raise HTTPException(status_code=404, detail="Tidak ditemukan.")
    return {"ok": True, "undo": UNDO_SECONDS}


@router.post("/{clip_id}/restore", dependencies=[Depends(require_full)])
def restore_clip(clip_id: str):
    item = store.restore(clip_id)
    if not item:
        raise HTTPException(status_code=410, detail="Sudah tidak bisa dikembalikan.")
    return item


@router.delete("", dependencies=[Depends(require_full)])
def clear_clips():
    store.clear()
    return {"ok": True}
