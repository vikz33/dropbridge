"""Kirim antarlaptop: laptop ini masuk ke DropBridge laptop lain (PIN + izin di sana), lalu meneruskan file.

Browser di laptop ini mengunggah ke laptop ini seperti biasa; DropBridge laptop ini meneruskannya ke laptop
tujuan dengan token yang disimpan di data/peers.json. Di laptop tujuan, laptop ini tampil sebagai
perangkat jenis "Laptop" (hak bawaan Hanya kirim, sama seperti HP baru).
"""
import http.client
import json
import os
import socket
import tempfile
import threading
import time
from urllib.parse import quote, urlsplit

from fastapi import APIRouter, Depends, HTTPException, Request
from fastapi.responses import StreamingResponse
from pydantic import BaseModel
from starlette.concurrency import run_in_threadpool

from .. import discovery
from ..config import os_label, settings
from ..version import VERSION
from .system import _pc_only

router = APIRouter(prefix="/api/remote", dependencies=[Depends(_pc_only)])

FILE = settings.data_dir / "peers.json"
UA = f"DropBridge-PC/{VERSION} ({os_label()})"
_lock = threading.Lock()


def _load() -> dict:
    try:
        return json.loads(FILE.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return {}


def _save(d: dict) -> None:
    FILE.parent.mkdir(parents=True, exist_ok=True)
    tmp = FILE.with_suffix(".tmp")
    tmp.write_text(json.dumps(d, ensure_ascii=False), encoding="utf-8")
    tmp.replace(FILE)


def _call(url: str, method: str, path: str, token: str = "", body: bytes | None = None,
          ctype: str = "application/json", timeout: float = 8.0, length: int | None = None, stream=None):
    """Panggil DropBridge laptop lain. Mengembalikan (status, json/dict)."""
    u = urlsplit(url)
    conn = http.client.HTTPConnection(u.hostname, u.port or 8000, timeout=timeout)
    headers = {"User-Agent": UA, "Content-Type": ctype}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    try:
        if stream is not None:
            headers["Content-Length"] = str(length or 0)
            conn.putrequest(method, path)
            for k, v in headers.items():
                conn.putheader(k, v)
            conn.endheaders()
            while True:
                chunk = stream.read(1024 * 1024)
                if not chunk:
                    break
                conn.send(chunk)
        else:
            conn.request(method, path, body=body, headers=headers)
        r = conn.getresponse()
        raw = r.read()
        try:
            data = json.loads(raw.decode("utf-8") or "{}")
        except ValueError:
            data = {}
        return r.status, data
    except (OSError, http.client.HTTPException) as e:
        raise HTTPException(status_code=502, detail=f"Laptop tujuan tidak terjangkau ({e.__class__.__name__}).")
    finally:
        conn.close()


def _peer(pid: str) -> dict:
    p = _load().get(pid)
    if not p or not p.get("token"):
        raise HTTPException(status_code=404, detail="Laptop tujuan belum tersambung.")
    return p


def _pcall(pid: str, method: str, path: str, **kw):
    """Panggil laptop host dengan token tersimpan. Bila token kedaluwarsa dan kita tamu, masuk lagi otomatis."""
    p = _peer(pid)
    code, data = _call(p["url"], method, path, token=p["token"], **kw)
    if code == 401 and p.get("secret"):
        c2, d2 = _call(p["url"], "POST", "/api/guest/resume",
                       body=json.dumps({"device": "pc-" + settings.pc_id, "secret": p["secret"]}).encode())
        if c2 == 200 and d2.get("token"):
            with _lock:
                d = _load()
                if pid in d:
                    d[pid].update({"token": d2["token"], "role": d2.get("role", d[pid].get("role", "send")), "status": "ok"})
                    _save(d)
            code, data = _call(p["url"], method, path, token=d2["token"], **kw)
        elif c2 == 401:
            with _lock:  # host mencabut izin tamu: lupakan rahasianya
                d = _load()
                if pid in d:
                    d[pid].update({"token": "", "secret": "", "status": "rejected"})
                    _save(d)
    return code, data


def _refresh_urls(found: list[dict]) -> None:
    """Alamat laptop lain bisa berubah (DHCP): perbarui dari hasil pencarian terbaru."""
    with _lock:
        d = _load()
        changed = False
        for f in found:
            p = d.get(f.get("id"))
            if p and p.get("url") != f["url"]:
                p["url"] = f["url"]
                changed = True
        if changed:
            _save(d)


@router.get("/peers")
def peers(fresh: int = 0):
    """Laptop untuk "Kirim ke mana?": yang sudah tersambung dan yang ditemukan di Wi-Fi ini."""
    found = discovery.peers(0 if fresh else 15)
    _refresh_urls(found)
    d = _load()
    out = {}
    for f in found:
        out[f["id"] or f["url"]] = {**f, "here": True, "paired": False, "status": ""}
    for pid, p in d.items():
        cur = out.get(pid, {"id": pid, "name": p.get("name", ""), "os": p.get("os", ""), "url": p.get("url", ""),
                            "version": p.get("version", ""), "here": False})
        cur.update({"paired": bool(p.get("token")) and p.get("status") == "ok", "status": p.get("status", ""),
                    "role": p.get("role", ""), "guest": bool(p.get("secret"))})
        out[pid] = cur
    return {"peers": sorted(out.values(), key=lambda p: (not p.get("paired"), not p.get("here"), p.get("name", "")))}


class LoginIn(BaseModel):
    id: str = ""
    url: str
    pin: str
    name: str = ""
    os: str = ""


@router.post("/login")
def login(body: LoginIn):
    """Masuk ke laptop lain dengan PIN-nya. Bila laptop itu meminta izin, status "pending" sampai diizinkan."""
    url = body.url.rstrip("/")
    if not url.startswith("http"):
        url = "http://" + url
    me = {"pin": body.pin.strip(), "name": socket.gethostname(), "device": "pc-" + settings.pc_id}
    code, data = _call(url, "POST", "/api/login", body=json.dumps(me).encode())
    if code == 401:
        raise HTTPException(status_code=401, detail="PIN salah.")
    if code == 429:
        raise HTTPException(status_code=429, detail=data.get("detail") or "Terlalu banyak percobaan.")
    if code != 200 or not data.get("token"):
        raise HTTPException(status_code=502, detail=data.get("detail") or f"Laptop tujuan menjawab {code}.")
    pid = body.id or url
    with _lock:
        d = _load()
        d[pid] = {"id": pid, "url": url, "name": body.name or urlsplit(url).hostname, "os": body.os,
                  "token": data["token"], "status": "pending" if data.get("pending") else "ok",
                  "role": data.get("role", "send"), "since": time.time()}
        _save(d)
    return {"id": pid, "status": d[pid]["status"], "role": d[pid]["role"]}


class PairIn(BaseModel):
    id: str = ""
    url: str
    code: str
    name: str = ""
    os: str = ""


@router.post("/pair")
def pair(body: PairIn):
    """Jadi tamu di laptop lain: tukar kode tamu 6 angka (dari layar host) dengan izin yang bisa dipakai lagi."""
    url = body.url.rstrip("/")
    if not url.startswith("http"):
        url = "http://" + url
    me = {"code": body.code.strip(), "name": socket.gethostname(), "device": "pc-" + settings.pc_id}
    code, data = _call(url, "POST", "/api/guest/pair", body=json.dumps(me).encode())
    if code in (401, 429, 503):
        raise HTTPException(status_code=code, detail=data.get("detail") or "Kode tamu ditolak.")
    if code != 200 or not data.get("token"):
        raise HTTPException(status_code=502, detail=data.get("detail") or f"Laptop host menjawab {code}.")
    pid = body.id or url
    with _lock:
        d = _load()
        d[pid] = {"id": pid, "url": url, "name": body.name or data.get("host") or urlsplit(url).hostname, "os": body.os,
                  "token": data["token"], "secret": data["secret"], "status": "ok",
                  "role": data.get("role", "send"), "since": time.time()}
        _save(d)
    return {"id": pid, "status": "ok", "role": d[pid]["role"]}


@router.get("/files")
def files(id: str, folder: str = ""):
    """Isi folder di laptop host (perlu hak Lihat)."""
    code, data = _pcall(id, "GET", f"/api/files?folder={quote(folder)}")
    if code >= 400:
        raise HTTPException(status_code=code if code in (401, 403, 404) else 502, detail=data.get("detail") or f"Laptop host menjawab {code}.")
    return data


@router.get("/download")
def download(id: str, name: str, folder: str = ""):
    """Unduh file dari laptop host lewat laptop ini (perlu hak Unduh)."""
    p = _peer(id)
    u = urlsplit(p["url"])
    path = f"/api/files/{quote(name)}?folder={quote(folder)}"

    def open_conn(token):
        conn = http.client.HTTPConnection(u.hostname, u.port or 8000, timeout=30)
        try:
            conn.request("GET", path, headers={"User-Agent": UA, "Authorization": f"Bearer {token}"})
            return conn, conn.getresponse()
        except (OSError, http.client.HTTPException) as e:
            conn.close()
            raise HTTPException(status_code=502, detail=f"Laptop host tidak terjangkau ({e.__class__.__name__}).")

    conn, r = open_conn(p["token"])
    if r.status == 401 and p.get("secret"):
        conn.close()
        _pcall(id, "GET", "/api/session")  # memperbarui token lewat resume
        conn, r = open_conn(_peer(id)["token"])
    if r.status != 200:
        raw = r.read()
        conn.close()
        try:
            detail = json.loads(raw.decode("utf-8") or "{}").get("detail")
        except ValueError:
            detail = ""
        raise HTTPException(status_code=r.status if r.status in (401, 403, 404) else 502, detail=detail or f"Laptop host menjawab {r.status}.")

    def gen():
        try:
            while True:
                chunk = r.read(1024 * 1024)
                if not chunk:
                    break
                yield chunk
        finally:
            conn.close()

    headers = {k: v for k in ("Content-Disposition", "Content-Length") if (v := r.getheader(k))}
    return StreamingResponse(gen(), media_type=r.getheader("Content-Type") or "application/octet-stream", headers=headers)


@router.get("/status")
def status(id: str):
    """Sudah diizinkan? (dipanggil berulang saat menunggu izin di laptop tujuan)"""
    with _lock:
        d = _load()
        p = d.get(id)
    if not p or not p.get("token"):
        return {"status": "none"}
    code, data = _pcall(id, "GET", "/api/session")
    if code == 401:
        st, wait, role = "rejected", 0, ""
    elif code == 200:
        st, wait, role = ("pending" if data.get("pending") else "ok"), data.get("wait", 0), data.get("role", "send")
    else:
        raise HTTPException(status_code=502, detail=f"Laptop tujuan menjawab {code}.")
    with _lock:
        d = _load()
        if id in d:
            d[id]["status"] = st
            d[id]["role"] = role or d[id].get("role", "")
            if st == "rejected":
                d[id]["token"] = ""
            _save(d)
    return {"status": st, "wait": wait, "role": role}


@router.delete("/peers/{pid:path}")
def forget(pid: str):
    with _lock:
        d = _load()
        p = d.pop(pid, None)
        _save(d)
    if p and p.get("token"):
        try:
            _call(p["url"], "POST", "/api/logout", token=p["token"], body=b"{}", timeout=3)
        except HTTPException:
            pass
    return {"ok": True}


@router.get("/folders")
def folders(id: str):
    """Daftar folder di laptop tujuan (untuk saran dan Pilih folder)."""
    code, data = _pcall(id, "GET", "/api/folders")
    if code == 401:
        raise HTTPException(status_code=401, detail="Sambungan ke laptop tujuan berakhir. Sambungkan lagi.")
    if code != 200:
        raise HTTPException(status_code=502, detail=f"Laptop tujuan menjawab {code}.")
    return data


@router.post("/upload")
async def upload(request: Request, id: str, name: str, folder: str = "", last_modified: int | None = None):
    """Terima file dari browser laptop ini, lalu teruskan ke laptop tujuan."""
    p = _peer(id)
    fd, tmp = tempfile.mkstemp(prefix="db_relay_", dir=str(settings.data_dir))
    size = 0
    try:
        with os.fdopen(fd, "wb") as out:
            async for chunk in request.stream():
                size += len(chunk)
                if size > settings.max_upload_bytes:
                    raise HTTPException(status_code=413, detail=f"{name}: melebihi batas {settings.max_upload_mb} MB")
                out.write(chunk)
        q = f"/api/files/upload-raw?folder={quote(folder)}&name={quote(name)}"
        if last_modified:
            q += f"&last_modified={last_modified}"

        def forward():
            with open(tmp, "rb") as fh:
                return _call(p["url"], "POST", q, token=p["token"], ctype="application/octet-stream",
                             timeout=120, length=size, stream=fh)

        code, data = await run_in_threadpool(forward)
    finally:
        try:
            os.remove(tmp)
        except OSError:
            pass
    if code == 401:
        raise HTTPException(status_code=409, detail="Sambungan ke laptop tujuan berakhir. Sambungkan lagi lewat Ganti.")
    if code >= 400:
        raise HTTPException(status_code=502 if code >= 500 else code, detail=data.get("detail") or f"Laptop tujuan menjawab {code}.")
    return {**data, "relay": p.get("name", "")}
