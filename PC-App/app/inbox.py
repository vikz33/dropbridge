"""Kabar "file masuk" untuk halaman Home laptop: siapa mengirim berapa file ke folder mana.

File yang berurutan dari pengirim dan folder yang sama (jarak < 60 detik) digabung jadi satu kabar,
jadi 11 foto = "11 foto masuk dari HONOR Nudis". Hanya di memori; hilang saat server di-restart.
"""
import threading
import time

_lock = threading.Lock()
_batches: list[dict] = []
_seq = 0
MERGE = 60
KEEP = 30

_IMG = (".jpg", ".jpeg", ".png", ".webp", ".heic", ".heif", ".gif")


def add(folder: str, name: str, who: dict) -> None:
    global _seq
    now = time.time()
    img = name.lower().endswith(_IMG)
    with _lock:
        last = _batches[-1] if _batches else None
        if last and last["from"] == who.get("name", "") and last["folder"] == folder and now - last["ts"] < MERGE:
            last["count"] += 1
            last["images"] += 1 if img else 0
            last["ts"] = now
            last["name"] = name
            last["seq"] = _seq = _seq + 1
            return
        _seq += 1
        _batches.append({"seq": _seq, "from": who.get("name", ""), "dev": who.get("dev", "hp"), "folder": folder,
                         "count": 1, "images": 1 if img else 0, "name": name, "ts": now})
        del _batches[:-KEEP]


def since(seq: int) -> dict:
    with _lock:
        return {"seq": _seq, "batches": [dict(b) for b in _batches if b["seq"] > seq]}
