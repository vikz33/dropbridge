"""Login PIN -> token sesi (cookie, atau header Authorization: Bearer untuk klien lain).

Tiap sesi mewakili satu perangkat: nama, jenis, IP, dan waktu aktif terakhir tercatat
supaya pemilik PC bisa melihat siapa saja yang terhubung dan mengeluarkannya.

Pengaman (2.5.0):
- HP baru yang PIN-nya benar tetap "menunggu" sampai PC ini menekan Izinkan (bisa dimatikan: approve_new).
- Hak per perangkat: "full" (kirim, lihat, unduh, pindah, hapus) atau "send" (hanya mengirim).
- Perangkat yang sudah diizinkan diingat lewat kode perangkat (data/trusted.json), jadi login ulang tidak perlu izin lagi.
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

from . import events
from .config import save_setting, settings
from .netinfo import all_ips, is_tailscale

COOKIE = "db_token"
MAX_FAILS = 5
LOCK_SECONDS = 60
ONLINE_SECONDS = 75  # dianggap "online" jika aktif dalam rentang ini
PENDING_SECONDS = 120  # izin dari PC ditunggu selama ini, lalu otomatis ditolak
ROLES = ("full", "get", "view", "send")  # full = semua; get = lihat + unduh; view = lihat saja; send = hanya kirim
ROLE_RANK = {"send": 0, "view": 1, "get": 2, "full": 3}
GUEST_CODE_SECONDS = 600  # kode tamu berlaku sekian detik dan hanya sekali pakai

SAVE_EVERY = 60  # detik; waktu "aktif terakhir" disimpan paling sering sekali per menit

# token -> {id, created, last, expires, name, kind, ip, ua, local}
_sessions: dict[str, dict] = {}
_fails: dict[str, tuple[int, float]] = {}  # ip -> (jumlah gagal, terkunci sampai)

# Sesi disimpan di data/sessions.json supaya perangkat tidak perlu login ulang setiap server di-restart.
_SESS_FILE = settings.data_dir / "sessions.json"
_lock = threading.Lock()
_last_save = 0.0
_KEYS = ("id", "created", "last", "expires", "name", "kind", "ip", "ua", "local")
_EXTRA = {"status": "ok", "role": "full", "dev": "", "guest": False}  # kunci baru 2.5.0; sesi lama otomatis sudah diizinkan, hak penuh
_rejected: dict[str, float] = {}  # token yang ditolak PC -> waktu, supaya HP yang menunggu tahu hasilnya

# Perangkat yang sudah diizinkan: kode perangkat -> {role, name, approved}
_TRUST_FILE = settings.data_dir / "trusted.json"
try:
    _trusted: dict[str, dict] = json.loads(_TRUST_FILE.read_text(encoding="utf-8"))
    if not isinstance(_trusted, dict):
        _trusted = {}
except (OSError, ValueError):
    _trusted = {}


def _save_trust() -> None:
    try:
        _TRUST_FILE.parent.mkdir(parents=True, exist_ok=True)
        tmp = _TRUST_FILE.with_suffix(".tmp")
        tmp.write_text(json.dumps(_trusted), encoding="utf-8")
        os.replace(tmp, _TRUST_FILE)
    except OSError:
        pass


def _load() -> None:
    try:
        raw = json.loads(_SESS_FILE.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return
    now = time.time()
    for token, s in (raw.items() if isinstance(raw, dict) else []):
        if isinstance(s, dict) and all(k in s for k in _KEYS) and s["expires"] > now:
            _sessions[token] = {k: s[k] for k in _KEYS} | {k: s.get(k, v) for k, v in _EXTRA.items()}


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
    if "DropBridge-PC" in ua:
        return "Laptop"  # laptop lain yang mengirim lewat DropBridge-nya (kirim antarlaptop)
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
    if s.get("status") == "pending" and now - s["created"] > PENDING_SECONDS:
        return False
    limit = _idle_limit()
    if limit > 0 and not s["local"] and now - s["last"] > limit:
        return False
    return True


def _session_or_401(request: Request) -> dict:
    token = _token_from(request)
    s = _sessions.get(token) if token else None
    now = time.time()
    if not s or not _alive(s, now):
        if token:
            gone = _sessions.pop(token, None)
            if gone and gone.get("status") == "pending":
                _rejected[token] = now  # waktu tunggu habis = ditolak
        if token and token in _rejected:
            raise HTTPException(status_code=401, detail="Ditolak oleh PC. Minta pemilik PC menekan Izinkan, lalu masukkan PIN lagi.")
        raise HTTPException(status_code=401, detail="Belum login.")
    return s


def require_session(request: Request) -> None:
    """Sesi yang masih menunggu izin juga boleh (hanya untuk mengecek status)."""
    _session_or_401(request)


def require_auth(request: Request) -> None:
    s = _session_or_401(request)
    now = time.time()
    if s.get("status") == "pending":
        raise HTTPException(status_code=403, detail="Menunggu izin dari PC.")
    s["last"] = now
    s["ip"] = _client_ip(request)
    ua = request.headers.get("user-agent", "")[:300]
    if ua and ua != s["ua"]:
        s["ua"] = ua  # mis. aplikasi Android diperbarui: versinya ikut tercatat
    if now - _last_save > SAVE_EVERY:
        _save()


def require_full(request: Request) -> None:
    """Melihat, mengunduh, memindah, dan menghapus: hanya perangkat dengan hak penuh."""
    require_auth(request)
    if _current(request).get("role") != "full":
        raise HTTPException(status_code=403, detail="Perangkat ini hanya boleh mengirim file.")


def _need(request: Request, rank: int, msg: str) -> None:
    require_auth(request)
    if ROLE_RANK.get(_current(request).get("role"), 0) < rank:
        raise HTTPException(status_code=403, detail=msg)


def require_view(request: Request) -> None:
    """Melihat daftar file dan thumbnail: hak lihat, unduh, atau penuh."""
    _need(request, 1, "Perangkat ini tidak boleh melihat isi folder.")


def require_get(request: Request) -> None:
    """Mengunduh file: hak unduh atau penuh."""
    _need(request, 2, "Perangkat ini tidak boleh mengunduh.")


def sender(request: Request) -> dict:
    """Siapa yang mengirim: nama perangkat dan jenisnya ("pc" untuk laptop ini, "hp" untuk yang lain)."""
    token = _token_from(request)
    s = _sessions.get(token) if token else None
    if not s:
        return {"name": "", "dev": "hp"}
    local = bool(s.get("local"))
    return {"name": s.get("name") or ("Laptop ini" if local else s.get("kind", "")), "dev": "pc" if local else "hp"}


def device_key(request: Request) -> str:
    """Kunci tetap perangkat yang sedang meminta (dari aplikasi/browser), "" bila tidak ada."""
    token = _token_from(request)
    s = _sessions.get(token) if token else None
    return (s or {}).get("dev") or ""


def known_phones() -> list[dict]:
    """HP yang bisa dikirimi file dari laptop: yang pernah diizinkan, ditambah yang sedang masuk."""
    now = time.time()
    out: dict[str, dict] = {}
    for k, t in _trusted.items():
        out[k] = {"key": k, "name": t.get("name") or "HP", "app": "", "online": False, "last_seen": t.get("approved", 0), "pc": False}
    for s in _sessions.values():
        k = s.get("dev")
        if not k or s.get("local") or s.get("status") != "ok" or not _alive(s, now):
            continue
        cur = out.get(k, {"key": k, "last_seen": 0})
        cur.update({"name": s.get("name") or s.get("kind") or "HP", "app": _app_version(s.get("ua", "")),
                    "pc": "DropBridge-PC" in (s.get("ua") or ""),
                    "online": cur.get("online") or now - s["last"] <= ONLINE_SECONDS,
                    "last_seen": max(cur.get("last_seen", 0), s["last"])})
        out[k] = cur
    # Satu baris per nama + jenis (aplikasi/browser), yang paling baru; abaikan yang tak terlihat 30 hari
    best: dict[tuple, dict] = {}
    for v in out.values():
        if v.get("pc") or (not v.get("online") and now - v.get("last_seen", 0) > 30 * 86400):
            continue
        k = (v["name"].strip().lower(), bool(v.get("app")))
        if k not in best or v["last_seen"] > best[k]["last_seen"]:
            best[k] = v
    return sorted(best.values(), key=lambda v: (not v.get("online"), -v["last_seen"]))


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
    device: str = ""  # kode perangkat tetap (dibuat aplikasi/browser), untuk mengingat izin


def _devkey(raw: str) -> str:
    return re.sub(r"[^A-Za-z0-9_-]", "", raw or "")[:64]


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
    dev = _devkey(body.device)
    for t in [t for t, s in _sessions.items()
              if not _alive(s, now) or (s["ip"] == ip and s["ua"] == ua) or (dev and s.get("dev") == dev)]:
        _sessions.pop(t, None)  # sesi usang, atau sesi lama dari perangkat yang sama
    token = secrets.token_urlsafe(32)
    ttl = int(settings.session_hours * 3600)
    kind = _kind_of(ua)
    trust = _trusted.get(dev) if dev else None
    name = clean_name(body.name) or ("PC ini" if local else (trust or {}).get("name", ""))
    if local or not settings.approve_new:
        status, role = "ok", "full"
    elif trust:
        status, role = "ok", trust.get("role", "send")
    else:
        status, role = "pending", "send"
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
        "status": status,
        "role": role,
        "dev": dev,
    }
    _save()
    response.set_cookie(COOKIE, token, max_age=ttl, httponly=True, samesite="lax")
    if status == "pending":
        events.emit("approve", _public(token, _sessions[token], None, now))
    return {"ok": True, "token": token, "need_name": not name, "pending": status == "pending", "role": role}


@router.post("/logout")
def logout(request: Request, response: Response):
    token = _token_from(request)
    if token and _sessions.pop(token, None):
        _save()
    response.delete_cookie(COOKIE)
    return {"ok": True}


@router.get("/session", dependencies=[Depends(require_session)])
def session(request: Request):
    s = _current(request)
    left = max(0, int(PENDING_SECONDS - (time.time() - s["created"]))) if s["status"] == "pending" else 0
    return {"ok": True, "name": s["name"], "kind": s["kind"], "local": s["local"],
            "pending": s["status"] == "pending", "wait": left, "role": s["role"],
            # PIN hanya ditunjukkan ke laptop ini sendiri (Pengaturan > Laptop ini)
            "pin": settings.pin if s["local"] and is_this_pc(request) else None}


class DeviceIn(BaseModel):
    name: str


@router.post("/device", dependencies=[Depends(require_session)])
def rename_device(body: DeviceIn, request: Request):
    name = clean_name(body.name)
    if not name:
        raise HTTPException(status_code=400, detail="Nama perangkat tidak boleh kosong.")
    s = _current(request)
    s["name"] = name
    if s.get("dev") in _trusted:
        _trusted[s["dev"]]["name"] = name
        _save_trust()
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
        "pc": "DropBridge-PC" in (s.get("ua") or ""),  # laptop lain (kirim antarlaptop)
        "key": s.get("dev") or "",  # kunci perangkat yang tetap (untuk kotak kiriman laptop → HP)
        "tailscale": is_tailscale(s["ip"]),  # tersambung lewat internet (orbit putus-putus di peta)
        "role": s.get("role", "full"),
        "guest": bool(s.get("guest")),  # laptop tamu yang masuk lewat kode tamu
        "pending": s.get("status") == "pending",
        "wait": max(0, int(PENDING_SECONDS - (now - s["created"]))) if s.get("status") == "pending" else 0,
    }


def _app_version(ua: str) -> str:
    m = re.search(r"DropBridge-Android/([0-9.]+)", ua or "")
    return m.group(1) if m else ""


@router.get("/devices", dependencies=[Depends(require_auth)])
def devices(request: Request):
    now = time.time()
    mine = _token_from(request)
    me = _current(request)
    items = [_public(t, s, mine, now) for t, s in _sessions.items() if _alive(s, now)]
    if not me["local"]:
        # HP hanya melihat daftar perangkat yang sudah diizinkan (tanpa yang menunggu)
        items = [d for d in items if not d["pending"]]
        if me.get("role") != "full":
            items = [d for d in items if d["current"]]
    items.sort(key=lambda d: (not d["pending"], not d["current"], -d["last_seen"]))
    ok = [d for d in items if not d["pending"]]
    return {
        "devices": items,
        "online": sum(1 for d in ok if d["online"]),
        "phones": sum(1 for d in ok if d["online"] and not d["local"]),  # HP/perangkat lain, tanpa PC ini
        "pending": [d for d in items if d["pending"]],
    }


@router.get("/peers", dependencies=[Depends(require_auth)])
def peers(request: Request, fresh: int = 0):
    """Laptop DropBridge lain di Wi-Fi ini, untuk peta Hubungkan (orbit luar)."""
    from .discovery import peers as find
    if not _current(request)["local"] and _current(request).get("role") != "full":
        return {"peers": []}
    return {"peers": find(0 if fresh else 15)}


@router.get("/devices/pending", dependencies=[Depends(require_auth)])
def pending_devices(request: Request):
    _require_pc(request)
    now = time.time()
    return {"pending": [_public(t, s, None, now) for t, s in _sessions.items()
                        if _alive(s, now) and s.get("status") == "pending"],
            "approve_new": settings.approve_new}


def _require_pc(request: Request) -> None:
    """Mengatur perangkat hanya boleh dari PC, supaya HP tidak bisa mengeluarkan PC atau HP lain."""
    if not _current(request)["local"]:
        raise HTTPException(status_code=403, detail="Mengatur perangkat hanya bisa dari PC.")


def _find(device_id: str) -> tuple[str, dict]:
    for t, s in _sessions.items():
        if s["id"] == device_id:
            return t, s
    raise HTTPException(status_code=404, detail="Perangkat tidak ditemukan (mungkin waktu tunggunya habis).")


class RoleIn(BaseModel):
    role: str = "send"


def _trust(s: dict) -> None:
    if s.get("dev"):
        cur = _trusted.get(s["dev"], {})  # gabungkan: rahasia tamu tidak boleh hilang
        cur.update({"role": s["role"], "name": s["name"] or s["kind"], "approved": time.time()})
        _trusted[s["dev"]] = cur
        _save_trust()


@router.post("/devices/{device_id}/approve", dependencies=[Depends(require_auth)])
def approve_device(device_id: str, body: RoleIn, request: Request):
    _require_pc(request)
    if body.role not in ROLES:
        raise HTTPException(status_code=400, detail="Hak tidak dikenal.")
    _, s = _find(device_id)
    s["status"], s["role"] = "ok", body.role
    _trust(s)
    _save()
    return {"ok": True, "role": s["role"]}


@router.post("/devices/{device_id}/reject", dependencies=[Depends(require_auth)])
def reject_device(device_id: str, request: Request):
    _require_pc(request)
    t, s = _find(device_id)
    _sessions.pop(t, None)
    _rejected[t] = time.time()
    _save()
    return {"ok": True}


@router.post("/devices/{device_id}/role", dependencies=[Depends(require_auth)])
def set_role(device_id: str, body: RoleIn, request: Request):
    _require_pc(request)
    if body.role not in ROLES:
        raise HTTPException(status_code=400, detail="Hak tidak dikenal.")
    _, s = _find(device_id)
    if s["local"]:
        raise HTTPException(status_code=400, detail="PC ini selalu berhak penuh.")
    s["role"] = body.role
    if s.get("dev") in _trusted:
        _trusted[s["dev"]]["role"] = body.role
        _save_trust()
    _save()
    return {"ok": True, "role": s["role"]}


@router.delete("/devices/{device_id}", dependencies=[Depends(require_auth)])
def revoke_device(device_id: str, request: Request):
    """Putuskan: sesi dihapus dan izinnya dilupakan, jadi perangkat itu harus diizinkan lagi."""
    _require_pc(request)
    t, s = _find(device_id)
    _sessions.pop(t, None)
    if _trusted.pop(s.get("dev", ""), None) is not None:
        _save_trust()
    _save()
    return {"ok": True}


# ---------- Laptop tamu (3.4.0) ----------
# Host membuat kode tamu 6 angka (sekali pakai). Tamu menukarnya dengan token dan satu rahasia panjang;
# rahasia itu dipakai untuk masuk lagi sendiri sampai host memutuskan perangkatnya.
_guest = {"code": "", "until": 0.0}


def _guest_code(new: bool = False) -> tuple[str, int]:
    now = time.time()
    if new or not _guest["code"] or _guest["until"] < now:
        _guest["code"] = f"{secrets.randbelow(10**6):06d}"
        _guest["until"] = now + GUEST_CODE_SECONDS
    return _guest["code"], max(0, int(_guest["until"] - now))


def _sha(text: str) -> str:
    import hashlib
    return hashlib.sha256(text.encode()).hexdigest()


def _guest_gate(request: Request) -> tuple[str, float]:
    if settings.mode == "guest":
        raise HTTPException(status_code=503, detail="Laptop ini sedang jadi tamu, tidak menerima tamu lain.")
    ip = _client_ip(request)
    now = time.time()
    if _fails.get(ip, (0, 0.0))[1] > now:
        raise HTTPException(status_code=429, detail=f"Terlalu banyak percobaan. Coba lagi {int(_fails[ip][1] - now) + 1} detik.")
    return ip, now


def _guest_fail(ip: str, now: float, msg: str):
    count = _fails.get(ip, (0, 0.0))[0] + 1
    _fails[ip] = (0, now + LOCK_SECONDS) if count >= MAX_FAILS else (count, 0.0)
    raise HTTPException(status_code=401, detail=msg)


def _open_guest(request: Request, dev: str, name: str, role: str, now: float) -> str:
    ua = request.headers.get("user-agent", "")[:300]
    for t in [t for t, s in _sessions.items() if not _alive(s, now) or s.get("dev") == dev]:
        _sessions.pop(t, None)
    token = secrets.token_urlsafe(32)
    _sessions[token] = {"id": "d" + secrets.token_hex(4), "created": now, "last": now,
                        "expires": now + int(settings.session_hours * 3600), "name": name, "kind": _kind_of(ua),
                        "ip": _client_ip(request), "ua": ua, "local": False, "status": "ok", "role": role,
                        "dev": dev, "guest": True}
    _save()
    return token


@router.get("/guest/code", dependencies=[Depends(require_auth)])
def guest_code(request: Request, new: int = 0):
    """Kode tamu yang sedang berlaku (hanya untuk laptop ini). new=1 membuat kode baru."""
    _require_pc(request)
    code, left = _guest_code(bool(new))
    return {"code": code, "left": left}


class PairIn(BaseModel):
    code: str
    name: str = ""
    device: str = ""


@router.post("/guest/pair")
def guest_pair(body: PairIn, request: Request):
    ip, now = _guest_gate(request)
    dev = _devkey(body.device)
    if not dev:
        raise HTTPException(status_code=400, detail="Kode perangkat tamu kosong.")
    code, left = _guest_code(False)
    if not left or not secrets.compare_digest(body.code.strip().encode(), code.encode()):
        _guest_fail(ip, now, "Kode tamu salah atau sudah habis. Minta kode baru di laptop host.")
    _fails.pop(ip, None)
    _guest["code"] = ""  # sekali pakai
    name = clean_name(body.name) or "Laptop tamu"
    secret = secrets.token_urlsafe(24)
    token = _open_guest(request, dev, name, "send", now)  # hak awal: hanya kirim; host bisa menaikkan
    _trusted[dev] = {"role": "send", "name": name, "approved": now, "guest": True, "secret": _sha(secret)}
    _save_trust()
    import socket
    return {"ok": True, "token": token, "role": "send", "secret": secret, "host": socket.gethostname()}


class ResumeIn(BaseModel):
    device: str
    secret: str


@router.post("/guest/resume")
def guest_resume(body: ResumeIn, request: Request):
    """Tamu yang sudah dipercaya masuk lagi tanpa kode, selama host belum memutuskannya."""
    ip, now = _guest_gate(request)
    dev = _devkey(body.device)
    t = _trusted.get(dev)
    if not t or not t.get("guest") or not secrets.compare_digest(_sha(body.secret), t.get("secret", "")):
        _guest_fail(ip, now, "Izin tamu sudah dicabut host. Minta kode tamu baru.")
    _fails.pop(ip, None)
    token = _open_guest(request, dev, t.get("name") or "Laptop tamu", t.get("role", "send"), now)
    return {"ok": True, "token": token, "role": t.get("role", "send")}


class PinIn(BaseModel):
    pin: str = ""


@router.post("/pin", dependencies=[Depends(require_auth)])
def change_pin(body: PinIn, request: Request):
    """Ganti PIN (kosong = acak 6 angka). Perangkat yang sudah terhubung tidak ikut keluar."""
    _require_pc(request)
    new = body.pin.strip() or f"{secrets.randbelow(10**6):06d}"
    if not re.fullmatch(r"[0-9]{4,10}", new):
        raise HTTPException(status_code=400, detail="PIN harus 4 sampai 10 angka.")
    settings.pin = new
    save_setting("pin", new)
    return {"ok": True, "pin": new}


class ApproveIn(BaseModel):
    on: bool


@router.post("/settings/approve", dependencies=[Depends(require_auth)])
def set_approve(body: ApproveIn, request: Request):
    """Nyalakan/matikan izin untuk HP baru."""
    _require_pc(request)
    settings.approve_new = body.on
    save_setting("approve_new", body.on)
    return {"ok": True, "on": body.on}


@router.post("/devices/revoke-others", dependencies=[Depends(require_auth)])
def revoke_others(request: Request):
    _require_pc(request)
    mine = _token_from(request)
    n = 0
    for t in [t for t, s in _sessions.items() if t != mine and not s["local"]]:
        gone = _sessions.pop(t, None)
        _trusted.pop((gone or {}).get("dev", ""), None)
        n += 1
    _save_trust()
    _save()
    return {"ok": True, "revoked": n}
