"""Uji mode tamu (host + tamu) dengan dua server sungguhan di satu mesin. Jalankan: python tools/test_guest.py"""
import json, os, subprocess, sys, tempfile, time, urllib.request, urllib.error
from pathlib import Path

PC = Path(__file__).resolve().parent.parent
HP, GP = 18101, 18102
tmp = Path(tempfile.mkdtemp(prefix="db_guest_"))
procs = []


def start(name, port):
    home = tmp / name
    (home / "local").mkdir(parents=True)
    env = {**os.environ, "HOME": str(home), "LOCALAPPDATA": str(home / "local"), "USERPROFILE": str(home)}
    cfg = home / "local" / "DropBridge"
    cfg.mkdir(parents=True)
    (cfg / "config.json").write_text(json.dumps({"port": port, "pin": "111222", "shared_dir": str(home / "shared"),
                                                 "update_check": False, "pc_id": "pc" + name}))
    p = subprocess.Popen([sys.executable, "-m", "uvicorn", "app.main:app", "--port", str(port), "--host", "127.0.0.1"],
                         cwd=PC, env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    procs.append(p)
    return f"http://127.0.0.1:{port}"


def call(base, method, path, body=None, token="", raw=None, ua="Mozilla/5.0 (Windows) Chrome/1"):
    data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
    h = {"User-Agent": ua}
    if body is not None:
        h["Content-Type"] = "application/json"
    if raw is not None:
        h["Content-Type"] = "application/octet-stream"
    if token:
        h["Authorization"] = "Bearer " + token
    req = urllib.request.Request(base + path, data=data, method=method, headers=h)
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            b = r.read()
            try:
                return r.status, json.loads(b.decode() or "{}")
            except ValueError:
                return r.status, b
    except urllib.error.HTTPError as e:
        b = e.read()
        try:
            return e.code, json.loads(b.decode() or "{}")
        except ValueError:
            return e.code, b


ok = fail = 0


def check(label, cond):
    global ok, fail
    print(("  ok   " if cond else "  GAGAL ") + label)
    ok, fail = ok + bool(cond), fail + (not cond)


try:
    H, G = start("host", HP), start("tamu", GP)
    for base in (H, G):
        for _ in range(60):
            try:
                urllib.request.urlopen(base + "/api/info", timeout=2).read()
                break
            except OSError:
                time.sleep(0.5)
    hl = call(H, "POST", "/api/login", {"pin": "111222", "name": "Host"})[1]["token"]
    st, _ = call(H, "POST", "/api/files/upload-raw?name=halo.txt&folder=", raw=b"isi rahasia", token=hl)
    check("host: unggah contoh file", st in (200, 201))

    print("Host: kode tamu dan izin bertingkat")
    code = call(H, "GET", "/api/guest/code", token=hl)[1]["code"]
    check("kode tamu 6 angka", len(code) == 6 and code.isdigit())
    st, _ = call(H, "POST", "/api/guest/pair", {"code": "000000" if code != "000000" else "000001", "device": "pc-x", "name": "Tamu"})
    check("kode salah ditolak", st == 401)
    st, d = call(H, "POST", "/api/guest/pair", {"code": code, "device": "pc-x", "name": "Laptop Tamu"})
    check("kode benar: masuk dengan hak kirim saja", st == 200 and d["role"] == "send" and d["secret"])
    gt, secret = d["token"], d["secret"]
    check("kode sekali pakai", call(H, "POST", "/api/guest/pair", {"code": code, "device": "pc-y"})[0] == 401)
    check("tamu: tidak boleh melihat (kirim saja)", call(H, "GET", "/api/files", token=gt)[0] == 403)
    devs = call(H, "GET", "/api/devices", token=hl)[1]["devices"]
    me = [x for x in devs if x.get("guest")]
    check("host melihat tamu bertanda guest", len(me) == 1 and me[0]["name"] == "Laptop Tamu")
    did = me[0]["id"]
    call(H, "POST", f"/api/devices/{did}/role", {"role": "view"}, token=hl)
    check("hak Lihat: daftar file boleh", call(H, "GET", "/api/files", token=gt)[0] == 200)
    check("hak Lihat: unduh dilarang", call(H, "GET", "/api/files/halo.txt", token=gt)[0] == 403)
    call(H, "POST", f"/api/devices/{did}/role", {"role": "get"}, token=hl)
    st, body = call(H, "GET", "/api/files/halo.txt", token=gt)
    check("hak Unduh: unduh boleh", st == 200 and body == b"isi rahasia")
    check("hak Unduh: hapus dilarang", call(H, "DELETE", "/api/files/halo.txt", token=gt)[0] == 403)
    check("hak Unduh: pindah dilarang", call(H, "POST", "/api/files/move", {"name": "halo.txt", "to": "x"}, token=gt)[0] == 403)

    print("Host: masuk lagi dan dicabut")
    st, d = call(H, "POST", "/api/guest/resume", {"device": "pc-x", "secret": secret})
    check("masuk lagi dengan rahasia (hak Unduh tetap)", st == 200 and d["role"] == "get")
    check("rahasia salah ditolak", call(H, "POST", "/api/guest/resume", {"device": "pc-x", "secret": "salah"})[0] == 401)
    did = [x for x in call(H, "GET", "/api/devices", token=hl)[1]["devices"] if x.get("guest")][0]["id"]  # id berganti saat masuk lagi
    check("host memutus tamu", call(H, "DELETE", f"/api/devices/{did}", token=hl)[0] == 200)
    check("setelah diputus host, tidak bisa masuk lagi", call(H, "POST", "/api/guest/resume", {"device": "pc-x", "secret": secret})[0] == 401)

    print("Tamu: lewat DropBridge laptop tamu")
    gl = call(G, "POST", "/api/login", {"pin": "111222", "name": "Tamu"})[1]["token"]
    code2 = call(H, "GET", "/api/guest/code?new=1", token=hl)[1]["code"]
    st, d = call(G, "POST", "/api/remote/pair", {"id": "pchost", "url": H, "code": code2, "name": "Host"}, token=gl, ua="DropBridge-PC/3.4.0 (Linux)")
    check("tamu: sambung dengan kode", st == 200 and d["status"] == "ok")
    check("tamu: hak kirim saja belum bisa melihat", call(G, "GET", "/api/remote/files?id=pchost", token=gl)[0] == 403)
    did2 = [x for x in call(H, "GET", "/api/devices", token=hl)[1]["devices"] if x.get("guest")][0]["id"]
    call(H, "POST", f"/api/devices/{did2}/role", {"role": "get"}, token=hl)
    st, d = call(G, "GET", "/api/remote/files?id=pchost", token=gl)
    check("tamu: melihat isi host", st == 200 and any(f["name"] == "halo.txt" for f in d["files"]))
    st, body = call(G, "GET", "/api/remote/download?id=pchost&name=halo.txt", token=gl)
    check("tamu: mengunduh dari host", st == 200 and body == b"isi rahasia")
    peers = Path(tmp / "tamu" / "local" / "DropBridge" / "data" / "peers.json")
    pj = json.loads(peers.read_text())
    pj["pchost"]["token"] = "token-basi"
    peers.write_text(json.dumps(pj))
    check("tamu: token basi, masuk lagi otomatis", call(G, "GET", "/api/remote/files?id=pchost", token=gl)[0] == 200)
    did2 = [x for x in call(H, "GET", "/api/devices", token=hl)[1]["devices"] if x.get("guest")][0]["id"]
    check("host memutus tamu (laptop)", call(H, "DELETE", f"/api/devices/{did2}", token=hl)[0] == 200)
    check("setelah host memutus: tamu ditolak", call(G, "GET", "/api/remote/files?id=pchost", token=gl)[0] == 401)

    print("Mode")
    check("mode awal host", call(H, "GET", "/api/mode", token=hl)[1]["mode"] == "host")
    call(H, "POST", "/api/mode", {"mode": "guest"}, token=hl)
    check("mode tamu: tidak menerima tamu lain", call(H, "POST", "/api/guest/pair", {"code": "123456", "device": "pc-z"})[0] == 503)
    call(H, "POST", "/api/mode", {"mode": "host"}, token=hl)
    check("kembali ke host", call(H, "GET", "/api/mode", token=hl)[1]["mode"] == "host")
finally:
    for p in procs:
        p.terminate()
print(f"\n{ok} lulus, {fail} gagal")
sys.exit(1 if fail else 0)
