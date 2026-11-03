"""Login PIN -> token sesi (cookie, atau header Authorization: Bearer untuk klien lain)."""
import secrets
import time

from fastapi import APIRouter, Depends, HTTPException, Request, Response
from pydantic import BaseModel

from .config import settings

COOKIE = "db_token"
MAX_FAILS = 5
LOCK_SECONDS = 60

_sessions: dict[str, float] = {}  # token -> waktu kedaluwarsa
_fails: dict[str, tuple[int, float]] = {}  # ip -> (jumlah gagal, terkunci sampai)


def _token_from(request: Request) -> str | None:
    header = request.headers.get("authorization", "")
    if header.lower().startswith("bearer "):
        return header[7:].strip()
    return request.cookies.get(COOKIE)


def require_auth(request: Request) -> None:
    token = _token_from(request)
    expires = _sessions.get(token) if token else None
    if not expires or expires < time.time():
        if token:
            _sessions.pop(token, None)
        raise HTTPException(status_code=401, detail="Belum login.")


router = APIRouter(prefix="/api")


class LoginIn(BaseModel):
    pin: str


@router.post("/login")
def login(body: LoginIn, request: Request, response: Response):
    ip = request.client.host if request.client else "?"
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
    for t in [t for t, exp in _sessions.items() if exp < now]:
        _sessions.pop(t, None)
    token = secrets.token_urlsafe(32)
    ttl = int(settings.session_hours * 3600)
    _sessions[token] = now + ttl
    response.set_cookie(COOKIE, token, max_age=ttl, httponly=True, samesite="lax")
    return {"ok": True, "token": token}


@router.post("/logout")
def logout(request: Request, response: Response):
    token = _token_from(request)
    if token:
        _sessions.pop(token, None)
    response.delete_cookie(COOKIE)
    return {"ok": True}


@router.get("/session", dependencies=[Depends(require_auth)])
def session():
    return {"ok": True}
