"""Login PIN -> token sesi (cookie, atau header Authorization: Bearer untuk klien lain).

Tiap sesi mewakili satu perangkat: nama, jenis, IP, dan waktu aktif terakhir tercatat
supaya pemilik PC bisa melihat siapa saja yang terhubung dan mengeluarkannya.
"""
import ipaddress
import json
import os
import re
import secrets
import threading
import time

from fastapi import APIRouter, Depends, HTTPException, Request, Response
from pydantic import BaseModel

from .config import settings
from .netinfo import all_ips

COOKIE = "db_token"
MAX_FAILS = 5
LOCK_SECONDS = 60
ONLINE_SECONDS = 75  # dianggap "online" jika aktif dalam rentang ini

SAVE_EVERY = 60  # detik; waktu "aktif terakhir" disimpan paling sering sekali per menit

# token -> {id, created, last, expires, name, kind, ip, ua, local}
_sessions: dict[str, dict] = {}
_fails: dict[str, tuple[int, float]] = {}  # ip -> (jumlah gagal, terkunci sampai)

# Sesi disimpan di data/sessions.json supaya perangkat tidak perlu login ulang setiap server di-restart.
_SESS_FILE = settings.data_dir / "sessions.json"
_lock = threading.Lock()
_last_save = 0.0
_KEYS = ("id", "created", "last", "expires", "name", "kind", "ip", "ua", "local")


def _load() -> None:
    try:
        raw = json.loads(_SESS_FILE.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return
    now = time.time()
    for token, s in (raw.items() if isinstance(raw, dict) else []):
        if isinstance(s, dict) and all(k in s for k in _KEYS) and s["expires"] > now:
            _sessions[token] = {k: s[k] for k in _KEYS}


def _save() -> None:
    global _last_save
    with _lock:
        _last_save = time.time()
        data = {t: dict(s) for t, s in list(_sessions.items())}
        try:
            _SESS_FILE.parent.mkdir(parents=True, exist_ok=True)
            tmp = _SESS_FILE.with_suffix(".tmp")
            tmp.write_text(json.dumps(data), encoding="utf-8")
            os.replace(tmp, _SESS_FILE)
        except OSError:
            pass  # gagal simpan tidak boleh menghentikan server; sesi tetap jalan di memori


_load()


def _token_from(request: Request) -> str | None:
    header = request.headers.get("authorization", "")
    if header.lower().startswith("bearer "):
        return header[7:].strip()
    return request.cookies.get(COOKIE)


def _client_ip(request: Request) -> str:
    return request.client.host if request.client else "?"


def is_this_pc(request: Request) -> bool:
    """True untuk akses langsung dari PC ini: lewat localhost, atau lewat alamat LAN milik PC sendiri.

    Akses lewat tunnel/proxy (ada header forwarded) tidak dianggap dari PC ini.
    """
    if request.headers.get("x-forwarded-for") or request.headers.get("forwarded"):
        return False
    try:
        ip = ipaddress.ip_address(_client_ip(request))
    except ValueError:
        return False
    if ip.version == 6 and ip.ipv4_mapped:
        ip = ip.ipv4_mapped
    if ip.is_loopback:
        return True
    return str(ip) in set(all_ips())


def _kind_of(ua: str) -> str:
    """Tebak jenis perangkat dan browser dari User-Agent."""
    if "DropBridge-Android" in ua:
        return "Aplikasi Android"
    if "iPhone" in ua:
        os_ = "iPhone"
    elif "iPad" in ua:
        os_ = "iPad"
    elif "Android" in ua:
        os_ = "Android"
    elif "Windows" in ua:
        os_ = "Windows"
    elif "Macintosh" in ua or "Mac OS X" in ua:
        os_ = "Mac"
    elif "Linux" in ua or "X11" in ua:
        os_ = "Linux"
    else:
        os_ = "Perangkat"
    if "Edg/" in ua or "EdgA/" in ua:
        br = "Edge"
    elif "OPR/" in ua or "Opera" in ua:
        br = "Opera"
    elif "Firefox/" in ua or "FxiOS" in ua:
        br = "Firefox"
    elif "SamsungBrowser" in ua:
        br = "Samsung Internet"
    elif "CriOS" in ua or "Chrome/" in ua:
        br = "Chrome"
    elif "Safari/" in ua:
        br = "Safari"
    else:
        br = ""
    return f"{os_} · {br}" if br else os_


def clean_name(raw: str) -> str:
    name = re.sub(r"[\x00-\x1f<>]", "", raw or "")
    return re.sub(r"\s+", " ", name).strip()[:40]


def _idle_limit() -> float:
    return float(settings.idle_hours) * 3600


def _alive(s: dict, now: float) -> bool:
    if s["expires"] < now:
        return False
    limit = _idle_limit()
    if limit > 0 and not s["local"] and now - s["last"] > limit:
        return False
    return True


def require_auth(request: Request) -> None:
    token = _token_from(request)
    s = _sessions.get(token) if token else None
    now = time.time()
    if not s or not _alive(s, now):
        if token:
            _sessions.pop(token, None)
        raise HTTPException(status_code=401, detail="Belum login.")
    s["last"] = now
    s["ip"] = _client_ip(request)
    ua = request.headers.get("user-agent", "")[:300]
    if ua and ua != s["ua"]:
        s["ua"] = ua  # mis. aplikasi Android diperbarui: versinya ikut tercatat
    if now - _last_save > SAVE_EVERY:
        _save()


def _current(request: Request) -> dict:
    token = _token_from(request)
    s = _sessions.get(token) if token else None
    if not s:
        raise HTTPException(status_code=401, detail="Belum login.")
    return s


router = APIRouter(prefix="/api")


class LoginIn(BaseModel):
    pin: str
    name: str = ""


@router.post("/login")
def login(body: LoginIn, request: Request, response: Response):
    ip = _client_ip(request)
    now = time.time()
    _, locked_until = _fails.get(ip, (0, 0.0))
    if locked_until > now:
        wait = int(locked_until - now) + 1
        raise HTTPException(status_code=429, detail=f"Terlalu banyak percobaan. Coba lagi {wait} detik.")

    if not secrets.compare_digest(body.pin.strip().encode(), settings.pin.encode()):
        count = _fails.get(ip, (0, 0.0))[0] + 1
        _fails[ip] = (0, now + LOCK_SECONDS) if count >= MAX_FAILS else (count, 0.0)
        raise HTTPException(status_code=401, detail="PIN salah.")

    _fails.pop(ip, None)
    ua = request.headers.get("user-agent", "")[:300]
    local = is_this_pc(request)
    for t in [t for t, s in _sessions.items() if not _alive(s, now) or (s["ip"] == ip and s["ua"] == ua)]:
        _sessions.pop(t, None)  # sesi usang, atau sesi lama dari perangkat yang sama
    token = secrets.token_urlsafe(32)
    ttl = int(settings.session_hours * 3600)
    kind = _kind_of(ua)
    name = clean_name(body.name) or ("PC ini" if local else "")
    _sessions[token] = {
        "id": "d" + secrets.token_hex(4),
        "created": now,
        "last": now,
        "expires": now + ttl,
        "name": name,
        "kind": kind,
        "ip": ip,
        "ua": ua,
        "local": local,
    }
    _save()
    response.set_cookie(COOKIE, token, max_age=ttl, httponly=True, samesite="lax")
    return {"ok": True, "token": token, "need_name": not name}


@router.post("/logout")
def logout(request: Request, response: Response):
    token = _token_from(request)
    if token and _sessions.pop(token, None):
        _save()
    response.delete_cookie(COOKIE)
    return {"ok": True}


@router.get("/session", dependencies=[Depends(require_auth)])
def session(request: Request):
    s = _current(request)
    return {"ok": True, "name": s["name"], "kind": s["kind"], "local": s["local"]}


class DeviceIn(BaseModel):
    name: str


@router.post("/device", dependencies=[Depends(require_auth)])
def rename_device(body: DeviceIn, request: Request):
    name = clean_name(body.name)
    if not name:
        raise HTTPException(status_code=400, detail="Nama perangkat tidak boleh kosong.")
    _current(request)["name"] = name
    _save()
    return {"ok": True, "name": name}


def _public(token: str, s: dict, mine: str | None, now: float) -> dict:
    return {
        "id": s["id"],
        "name": s["name"] or s["kind"],
        "kind": s["kind"],
        "ip": s["ip"],
        "created": s["created"],
        "last_seen": s["last"],
        "online": now - s["last"] <= ONLINE_SECONDS,
        "current": token == mine,
        "local": s["local"],
        "app": _app_version(s["ua"]),  # versi aplikasi Android ("" = lewat browser)
    }


def _app_version(ua: str) -> str:
    m = re.search(r"DropBridge-Android/([0-9.]+)", ua or "")
    return m.group(1) if m else ""


@router.get("/devices", dependencies=[Depends(require_auth)])
def devices(request: Request):
    now = time.time()
    mine = _token_from(request)
    items = [_public(t, s, mine, now) for t, s in _sessions.items() if _alive(s, now)]
    items.sort(key=lambda d: (not d["current"], -d["last_seen"]))
    return {"devices": items, "online": sum(1 for d in items if d["online"])}


def _require_pc(request: Request) -> None:
    """Mengeluarkan perangkat hanya boleh dari PC, supaya HP tidak bisa mengeluarkan PC atau HP lain."""
    if not _current(request)["local"]:
        raise HTTPException(status_code=403, detail="Mengeluarkan perangkat hanya bisa dari PC.")


@router.delete("/devices/{device_id}", dependencies=[Depends(require_auth)])
def revoke_device(device_id: str, request: Request):
    _require_pc(request)
    for t, s in list(_sessions.items()):
        if s["id"] == device_id:
            _sessions.pop(t, None)
            _save()
            return {"ok": True}
    raise HTTPException(status_code=404, detail="Perangkat tidak ditemukan.")


@router.post("/devices/revoke-others", dependencies=[Depends(require_auth)])
def revoke_others(request: Request):
    _require_pc(request)
    mine = _token_from(request)
    n = 0
    for t in [t for t in _sessions if t != mine]:
        _sessions.pop(t, None)
        n += 1
    _save()
    return {"ok": True, "revoked": n}
