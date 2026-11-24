"""Uji updater dengan server GitHub tiruan (tanpa internet). Jalankan: python tools/test_updater.py"""
import hashlib, http.server, json, os, sys, tempfile, threading, time
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from app.updater import Updater, UpdateError

REPO = "vikz33/dropbridge"
WIN = os.name == "nt"
def asset(v): return f"DropBridge-Setup-{v}.exe" if WIN else f"dropbridge_{v}_amd64.deb"

BLOB = os.urandom(300_000)
SHA = hashlib.sha256(BLOB).hexdigest()
CFG = {}

class H(http.server.BaseHTTPRequestHandler):
    def log_message(self, *a): pass
    def _send(self, code, data, ct="application/octet-stream"):
        self.send_response(code); self.send_header("Content-Type", ct)
        self.send_header("Content-Length", str(len(data))); self.end_headers(); self.wfile.write(data)
    def do_GET(self):
        base = f"http://127.0.0.1:{self.server.server_port}"
        if self.path == f"/repos/{REPO}/releases/latest":
            if CFG.get("404"): return self._send(404, b"{}")
            tag, v = CFG["tag"], CFG["ver"]
            name = asset(v); assets = []
            if not CFG.get("noasset"):
                assets.append({"name": name, "size": len(BLOB), "browser_download_url": f"{base}/{CFG.get('path', REPO)}/releases/download/{tag}/{name}"})
            if not CFG.get("nosha"):
                assets.append({"name": name + ".sha256", "size": 90, "browser_download_url": f"{base}/{REPO}/releases/download/{tag}/{name}.sha256"})
            return self._send(200, json.dumps({"tag_name": tag, "html_url": "https://github.com/vikz33/dropbridge/releases/tag/" + tag,
                                               "body": "## Baru\n- **Cepat** [x](http://a)\n- Perbaikan", "assets": assets}).encode(), "application/json")
        if self.path.endswith(".sha256"):
            return self._send(200, f"{CFG.get('sha', SHA)}  {asset(CFG['ver'])}\n".encode())
        if "/releases/download/" in self.path:
            return self._send(200, CFG.get("blob", BLOB))
        self._send(404, b"")

srv = http.server.ThreadingHTTPServer(("127.0.0.1", 0), H)
threading.Thread(target=srv.serve_forever, daemon=True).start()
API = f"http://127.0.0.1:{srv.server_port}"
fails = []
def ok(c, m):
    print(("OK   " if c else "GAGAL"), m)
    if not c: fails.append(m)

def mk(cur="3.3.2"):
    d = tempfile.mkdtemp()
    u = Updater(cur, Path(d), repo=REPO, frozen=True, api_base=API, test_hosts=("127.0.0.1",))
    launched = []
    u._launch = lambda p, s: launched.append((p, s))
    u._blocked = lambda: ""
    return u, launched, Path(d)

def wait(u, states, t=15):
    end = time.time() + t
    while time.time() < end:
        if u.status()["state"] in states: return True
        time.sleep(0.05)
    return False

# 1 versi lebih baru, lengkap
CFG.update(tag="v3.4.0", ver="3.4.0", path=REPO)
u, L, d = mk(); s = u.check()
ok(s["state"] == "available" and s["newer"] and s["latest"] == "3.4.0" and s["ready"], "rilis lebih baru terdeteksi dan siap")
ok(len(s["notes"]) >= 1 and all("**" not in n and "](" not in n for n in s["notes"]), "catatan rilis dibersihkan dari markdown")
u.start_install(); ok(wait(u, ("installing", "idle")), "unduh selesai")
ok(len(L) == 1 and L[0][0].read_bytes() == BLOB and L[0][1] == SHA, "installer dipanggil dengan file utuh dan hash benar")

# 2 versi sama / lebih lama
CFG.update(tag="v3.3.2", ver="3.3.2"); u, L, d = mk(); s = u.check()
ok(s["state"] == "uptodate" and not s["newer"], "versi sama: tidak ada pembaruan")
CFG.update(tag="v3.3.1", ver="3.3.1"); u, L, d = mk(); s = u.check()
ok(not s["newer"], "versi lebih lama diabaikan")

# 3 tanpa .sha256
CFG.update(tag="v3.4.0", ver="3.4.0", nosha=True); u, L, d = mk(); s = u.check()
ok(s["newer"] and not s["ready"] and s["missing"], "tanpa .sha256: tidak siap dipasang")
try: u.start_install(); ok(False, "start_install harus ditolak tanpa sha256")
except UpdateError: ok(True, "start_install ditolak tanpa sha256")
CFG.pop("nosha")

# 4 hash salah
CFG.update(sha="0" * 64); u, L, d = mk(); u.check(); u.start_install(); wait(u, ("idle", "available", "installing"))
st = u.status()
ok(not L and st["error"], "hash salah: tidak dipasang, ada pesan galat")
ok(not list((d / "updates").glob("*.part")) if (d / "updates").exists() else True, "file .part dibersihkan")
CFG.pop("sha")

# 5 ukuran/isi berbeda dari yang diumumkan
CFG.update(blob=BLOB + b"x" * 5_000_000); u, L, d = mk(); u.check(); u.start_install(); wait(u, ("idle", "available", "installing"))
ok(not L and u.status()["error"], "isi lebih besar dari diumumkan ditolak")
CFG.pop("blob")

# 6 tag tidak cocok dengan nama file
CFG.update(tag="v3.5.0", ver="3.4.0"); u, L, d = mk(); s = u.check()
ok(not s["ready"], "versi di nama file beda dengan tag: tidak siap")

# 7 URL unduhan di luar repo
CFG.update(tag="v3.4.0", ver="3.4.0", path="evil/other"); u, L, d = mk(); s = u.check()
ok(not s["ready"], "URL unduhan di luar repo ditolak")
CFG.update(path=REPO)

# 8 belum ada rilis (404)
CFG["404"] = True; u, L, d = mk(); s = u.check()
ok(s["state"] == "uptodate" and not s["newer"], "belum ada rilis: dianggap terbaru")
CFG.pop("404")

# 9 server mati
srv.shutdown(); srv.server_close()
u, L, d = mk(); s = u.check()
ok(s["check_error"] and s["state"] in ("idle", "uptodate"), "tanpa jaringan: galat ramah, tidak macet")
print("\nSEMUA LULUS" if not fails else f"\n{len(fails)} GAGAL: {fails}")
sys.exit(1 if fails else 0)
