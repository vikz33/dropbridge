"""Kirim teks atau link antar perangkat. Riwayat disimpan di data/clips.json (maks 50)."""
import json
import secrets
import threading
import time
from pathlib import Path

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field

from ..auth import require_auth, require_full
from ..config import settings

router = APIRouter(prefix="/api/clips", dependencies=[Depends(require_auth)])


class ClipStore:
    MAX = 50

    def __init__(self, path: Path) -> None:
        self.path = path
        self.lock = threading.Lock()
        self.items: list[dict] = []
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

    def add(self, text: str, source: str) -> dict:
        item = {"id": secrets.token_hex(6), "text": text, "source": source, "ts": time.time()}
        with self.lock:
            self.items.insert(0, item)
            del self.items[self.MAX :]
            self._save()
        return item

    def remove(self, clip_id: str) -> bool:
        with self.lock:
            before = len(self.items)
            self.items = [i for i in self.items if i["id"] != clip_id]
            if len(self.items) == before:
                return False
            self._save()
            return True

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
def add_clip(body: ClipIn):
    text = body.text.strip()
    if not text:
        raise HTTPException(status_code=400, detail="Teks kosong.")
    return store.add(text, body.source)


@router.delete("/{clip_id}", dependencies=[Depends(require_full)])
def remove_clip(clip_id: str):
    if not store.remove(clip_id):
        raise HTTPException(status_code=404, detail="Tidak ditemukan.")
    return {"ok": True}


@router.delete("", dependencies=[Depends(require_full)])
def clear_clips():
    store.clear()
    return {"ok": True}
