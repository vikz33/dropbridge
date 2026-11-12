"""Kabar kecil dari server ke jendela desktop (notifikasi file masuk, minta jendela muncul)."""
import threading
from typing import Callable

_lock = threading.Lock()
_listeners: dict[str, list[Callable]] = {}


def on(kind: str, fn: Callable) -> None:
    with _lock:
        _listeners.setdefault(kind, []).append(fn)


def emit(kind: str, *args) -> None:
    with _lock:
        fns = list(_listeners.get(kind, []))
    for fn in fns:
        try:
            fn(*args)
        except Exception:  # kabar tidak boleh mengganggu server
            pass
