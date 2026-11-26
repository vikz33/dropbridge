"""Pembaruan DropBridge dari GitHub Releases (Windows dan Linux).

Alur: cek rilis terbaru (API GitHub; repo publik, tanpa token) -> bila lebih baru dari versi terpasang dan
file untuk sistem ini ada -> unduh -> periksa SHA-256 dengan berkas <nama>.sha256 di rilis yang sama -> pasang.

Aturan keamanan (jangan dilonggarkan tanpa alasan kuat):
- Tidak ada berkas .sha256 di rilis = TIDAK dipasang otomatis (pengguna hanya diberi tautan ke halaman rilis).
- Unduhan hanya dari repo ini (jalur /<repo>/releases/download/), lewat HTTPS ke host GitHub; pengalihan
  (redirect) ke host lain ditolak.
- Versi di nama berkas harus sama dengan versi rilis dan lebih baru dari yang terpasang.
- Hash dibandingkan dengan yang dihitung saat mengunduh; ukuran dan batas maksimum juga dicek.
- Hanya dipasang dari versi terpasang (.exe / paket .deb), bukan dari kode (start.bat / start.sh).

Hanya memakai pustaka standar supaya mudah diuji dengan server tiruan.
Nama berkas rilis: Windows DropBridge-Setup-<versi>.exe, Linux dropbridge_<versi>_amd64.deb,
masing-masing dengan pasangan <nama>.sha256 (isi: hash heksadesimal 64 karakter, boleh diikuti nama berkas).
"""
import hashlib
import hmac
import json
import os
import re
import shutil
import subprocess
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Callable, Optional

REPO = "vikz33/dropbridge"
API_BASE = "https://api.github.com"
CHECK_EVERY = 12 * 3600  # detik
FIRST_CHECK_AFTER = 30  # detik setelah DropBridge jalan
MAX_DOWNLOAD = 600 * 1024 * 1024
INSTALL_WAIT = 240  # detik menunggu installer menutup DropBridge; lewat ini dianggap gagal

_VER_RE = re.compile(r"^[vV]?(\d+(?:\.\d+){1,3})$")
_SHA_RE = re.compile(r"\b([0-9a-fA-F]{64})\b")
_BUSY = ("downloading", "verifying", "installing")


class UpdateError(Exception):
    pass


class _Cancelled(Exception):
    pass


def _key(v: str) -> tuple:
    p = [int(x) for x in v.split(".")]
    return tuple(p + [0] * (4 - len(p)))


def _asset_re() -> Optional["re.Pattern"]:
    if os.name == "nt":
        return re.compile(r"^DropBridge-Setup-(\d+(?:\.\d+){1,3})\.exe$", re.IGNORECASE)
    if os.name == "posix":
        return re.compile(r"^dropbridge_(\d+(?:\.\d+){1,3})_amd64\.deb$")
    return None


def _plain_notes(body: str) -> list:
    """Catatan rilis (markdown) jadi beberapa baris teks polos. Ditampilkan sebagai teks, bukan HTML."""
    out = []
    for line in (body or "").splitlines():
        t = line.strip()
        if not t or t.startswith(("#", "```", "---")):
            continue
        t = re.sub(r"^[-*+]\s+", "", t)
        t = re.sub(r"\[([^\]]+)\]\([^)]*\)", r"\1", t)
        t = re.sub(r"[`*_>#]", "", t).strip()[:140]
        if t:
            out.append(t)
        if len(out) >= 6:
            break
    return out


class _Redirect(urllib.request.HTTPRedirectHandler):
    def __init__(self, ok: Callable[[str], bool]) -> None:
        self.ok = ok

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        if not self.ok(newurl):
            raise urllib.error.URLError("Pengalihan ke alamat yang tidak dipercaya ditolak")
        return super().redirect_request(req, fp, code, msg, headers, newurl)


def _why(e: BaseException) -> str:
    """Alasan singkat yang bisa ditindaklanjuti pengguna saat GitHub tidak terjangkau."""
    import socket
    import ssl
    r = getattr(e, "reason", e)
    if isinstance(r, ssl.SSLCertVerificationError) or "CERTIFICATE" in str(r).upper():
        return "Sertifikat HTTPS ditolak: cek jam dan tanggal laptop, atau antivirus yang memeriksa HTTPS."
    if isinstance(r, ssl.SSLError):
        return "Sambungan aman (HTTPS) gagal: cek antivirus atau VPN."
    if isinstance(r, socket.gaierror):
        return "Alamat github.com tidak ditemukan: cek sambungan internet."
    if isinstance(r, (TimeoutError, socket.timeout)):
        return "Waktu habis: cek sambungan internet."
    if isinstance(r, ConnectionRefusedError) or isinstance(r, ConnectionResetError):
        return "Sambungan ditolak atau diputus: cek firewall, proxy, atau VPN."
    if isinstance(e, ValueError):
        return "Balasan GitHub tidak terbaca."
    return f"({r.__class__.__name__})"


class Updater:
    def __init__(self, current: str, data_dir: Path, repo: str = REPO, frozen: bool = False,
                 api_base: str = API_BASE, test_hosts: tuple = ()) -> None:
        self.current = current
        self.data_dir = Path(data_dir)
        self.repo = repo
        self.frozen = frozen
        self.api_base = api_base.rstrip("/")
        self.test_hosts = tuple(test_hosts)  # hanya untuk uji: host http lokal yang boleh dipakai
        self.lock = threading.RLock()
        self._cancel = threading.Event()
        self._thread: Optional[threading.Thread] = None  # thread pemasangan
        self._loop: Optional[threading.Thread] = None  # thread cek berkala
        self.s = self._blank()

    # ---------- keadaan ----------
    def _blank(self) -> dict:
        return {
            "state": "idle", "latest": "", "newer": False, "notes": [], "size": 0,
            "page": f"https://github.com/{self.repo}/releases", "asset": "", "asset_url": "", "sha_url": "",
            "ready": False, "missing": "", "done": 0, "total": 0, "error": "", "checked_at": 0, "check_error": "",
        }

    def _blocked(self) -> str:
        """Alasan pemasangan otomatis tidak bisa dipakai di sini ('' = bisa)."""
        if os.name == "nt":
            return "" if self.frozen else "DropBridge berjalan dari kode (start.bat), bukan versi terpasang. Pasang lewat build."
        if os.name == "posix":
            if not str(Path(__file__).resolve()).startswith("/opt/dropbridge/"):
                return "DropBridge berjalan dari kode (start.sh), bukan paket .deb. Pasang lewat build_deb.sh."
            if not shutil.which("pkexec"):
                return "pkexec tidak ditemukan. Pasang manual: sudo apt install ./<berkas .deb dari halaman rilis>"
            return ""
        return "Sistem ini belum didukung untuk pembaruan otomatis."

    def status(self) -> dict:
        with self.lock:
            s = self.s
            blocked = self._blocked()
            return {
                "state": s["state"], "current": self.current, "latest": s["latest"], "newer": s["newer"],
                "notes": list(s["notes"]), "size": s["size"], "page": s["page"], "ready": s["ready"],
                "missing": s["missing"], "can_install": not blocked, "blocked": blocked,
                "done": s["done"], "total": s["total"], "error": s["error"],
                "checked_at": s["checked_at"], "check_error": s["check_error"],
                "platform": "windows" if os.name == "nt" else "linux",
            }

    # ---------- jaringan ----------
    def _host_ok(self, url: str) -> bool:
        try:
            u = urllib.parse.urlparse(url)
        except ValueError:
            return False
        host = (u.hostname or "").lower()
        if u.scheme == "http" and host in self.test_hosts:
            return True
        if u.scheme != "https":
            return False
        return host in ("github.com", "api.github.com") or host.endswith(".githubusercontent.com")

    def _asset_url_ok(self, url: str) -> bool:
        if not self._host_ok(url):
            return False
        path = urllib.parse.urlparse(url).path
        return path.lower().startswith(f"/{self.repo.lower()}/releases/download/")

    def _open(self, url: str, timeout: float):
        if not (self._host_ok(url) or url.startswith(self.api_base)):
            raise UpdateError("Alamat unduhan tidak dipercaya.")
        opener = urllib.request.build_opener(_Redirect(self._host_ok))
        req = urllib.request.Request(url, headers={
            "User-Agent": f"DropBridge-Updater/{self.current}", "Accept": "application/vnd.github+json, */*"})
        return opener.open(req, timeout=timeout)

    def _fetch(self, url: str, timeout: float, limit: int) -> bytes:
        with self._open(url, timeout) as r:
            data = r.read(limit + 1)
        if len(data) > limit:
            raise UpdateError("Balasan terlalu besar.")
        return data

    # ---------- cek ----------
    def check(self, quiet: bool = False) -> dict:
        with self.lock:
            if self.s["state"] in _BUSY or self.s["state"] == "checking":
                return self.status()
            prev = self.s["state"]
            self.s["state"] = "checking"
        err = ""
        try:
            raw = self._fetch(f"{self.api_base}/repos/{self.repo}/releases/latest", 10, 1024 * 1024)
            self._apply(json.loads(raw))
        except urllib.error.HTTPError as e:
            if e.code == 404:  # belum ada rilis sama sekali
                with self.lock:
                    self.s.update(state="uptodate", latest="", newer=False, ready=False, missing="", notes=[])
            else:
                err = f"GitHub menjawab {e.code}."
        except UpdateError as e:
            err = str(e)
        except (urllib.error.URLError, OSError, ValueError, TimeoutError) as e:
            err = "Tidak bisa terhubung ke GitHub. " + _why(e)
        with self.lock:
            if err:
                self.s["state"] = prev if prev in ("available", "uptodate") else "idle"
            self.s["check_error"] = "" if quiet and err else err
            if not err:
                self.s["checked_at"] = time.time()
            return self.status()

    def _apply(self, rel: dict) -> None:
        m = _VER_RE.match(str(rel.get("tag_name", "")).strip())
        if not m or rel.get("draft") or rel.get("prerelease"):
            raise UpdateError("Rilis terbaru tidak punya nomor versi yang dikenal.")
        ver = m.group(1)
        newer = _key(ver) > _key(self.current)
        assets = {a["name"]: a for a in (rel.get("assets") or [])
                  if isinstance(a, dict) and isinstance(a.get("name"), str) and isinstance(a.get("browser_download_url"), str)}
        rx = _asset_re()
        target = ""
        if rx:
            for name in assets:
                mm = rx.match(name)
                if mm and mm.group(1) == ver:
                    target = name
        a = assets.get(target) if target else None
        shaa = assets.get(target + ".sha256") if target else None
        url = a["browser_download_url"] if a else ""
        sha_url = shaa["browser_download_url"] if shaa else ""
        label = "Windows" if os.name == "nt" else "Linux"
        missing = ""
        if newer:
            if not a:
                missing = f"Rilis ini belum punya file untuk {label}."
            elif not shaa:
                missing = "File pembaruan belum punya berkas .sha256, jadi tidak dipasang otomatis."
            elif not (self._asset_url_ok(url) and self._asset_url_ok(sha_url)):
                missing = "Alamat file pembaruan tidak dari repo DropBridge, tidak dipasang."
        page = str(rel.get("html_url") or "")
        with self.lock:
            self.s.update(
                state="available" if newer else "uptodate", latest=ver, newer=newer,
                notes=_plain_notes(str(rel.get("body") or "")),
                size=int((a or {}).get("size") or 0), asset=target if newer else "",
                asset_url=url if newer else "", sha_url=sha_url if newer else "",
                ready=bool(newer and a and shaa and not missing), missing=missing,
                page=page if page.startswith("https://github.com/") else f"https://github.com/{self.repo}/releases",
                error="",
            )

    # ---------- pasang ----------
    def start_install(self) -> None:
        with self.lock:
            s = self.s
            if s["state"] != "available" or not s["newer"]:
                raise UpdateError("Tidak ada pembaruan yang siap dipasang.")
            if not s["ready"]:
                raise UpdateError(s["missing"] or "File pembaruan belum lengkap di rilis.")
            blocked = self._blocked()
            if blocked:
                raise UpdateError(blocked)
            self._cancel.clear()
            s.update(state="downloading", error="", done=0, total=s["size"])
        self._thread = threading.Thread(target=self._run_install, daemon=True)
        self._thread.start()

    def cancel(self) -> None:
        with self.lock:
            if self.s["state"] == "downloading":
                self._cancel.set()

    def _set(self, **kw) -> None:
        with self.lock:
            self.s.update(kw)

    def _run_install(self) -> None:
        upd = self.data_dir / "updates"
        try:
            with self.lock:
                name, url, sha_url, size = self.s["asset"], self.s["asset_url"], self.s["sha_url"], self.s["size"]
            want = self._expected_hash(sha_url)
            shutil.rmtree(upd, ignore_errors=True)
            upd.mkdir(parents=True, exist_ok=True)
            path = self._download(url, upd / name, want, size)
            self._set(state="installing")
            self._launch(path, want)
            end = time.time() + INSTALL_WAIT
            while time.time() < end:  # installer menutup DropBridge; bila masih hidup di sini, ada yang salah
                time.sleep(1)
            raise UpdateError("Pemasangan tidak selesai. Coba lagi, atau pasang manual dari halaman rilis.")
        except _Cancelled:
            shutil.rmtree(upd, ignore_errors=True)
            self._set(state="available", error="")
        except UpdateError as e:
            shutil.rmtree(upd, ignore_errors=True)
            self._set(state="available", error=str(e))
        except Exception as e:  # jaringan putus, disk penuh, dll.
            shutil.rmtree(upd, ignore_errors=True)
            self._set(state="available", error=f"Gagal memperbarui: {e.__class__.__name__}")

    def _expected_hash(self, sha_url: str) -> str:
        try:
            text = self._fetch(sha_url, 15, 4096).decode("utf-8", "replace")
        except (urllib.error.URLError, OSError, TimeoutError):
            raise UpdateError("Tidak bisa mengambil berkas .sha256 dari GitHub.")
        m = _SHA_RE.search(text)
        if not m:
            raise UpdateError("Berkas .sha256 di rilis tidak berisi hash yang benar. Tidak dipasang.")
        return m.group(1).lower()

    def _download(self, url: str, final: Path, want: str, size: int) -> Path:
        part = final.with_name(final.name + ".part")
        h = hashlib.sha256()
        done = 0
        limit = min(MAX_DOWNLOAD, size + 1024 * 1024) if size else MAX_DOWNLOAD
        try:
            with self._open(url, 30) as r, open(part, "wb") as f:
                try:
                    total = int(r.headers.get("Content-Length") or size or 0)
                except ValueError:
                    total = size
                self._set(total=total or size, done=0)
                while True:
                    if self._cancel.is_set():
                        raise _Cancelled()
                    chunk = r.read(256 * 1024)
                    if not chunk:
                        break
                    done += len(chunk)
                    if done > limit:
                        raise UpdateError("File pembaruan lebih besar dari yang diumumkan. Dibatalkan.")
                    f.write(chunk)
                    h.update(chunk)
                    self._set(done=done)
            self._set(state="verifying")
            if size and done != size:
                raise UpdateError("Ukuran file pembaruan tidak sama dengan yang diumumkan. Tidak dipasang.")
            if not hmac.compare_digest(h.hexdigest(), want):
                raise UpdateError("File pembaruan tidak cocok (SHA-256 berbeda), tidak dipasang. Versi lama tetap dipakai.")
            part.replace(final)
            return final
        except BaseException:
            try:
                part.unlink()
            except OSError:
                pass
            raise

    def _launch(self, path: Path, sha: str) -> None:
        if os.name == "nt":
            self._launch_windows(path)
        else:
            self._launch_linux(path, sha)

    def _launch_windows(self, path: Path) -> None:
        """Setup.exe minta izin administrator (UAC). Dijalankan senyap; installer menutup DropBridge dan membukanya lagi."""
        import ctypes
        fn = ctypes.windll.shell32.ShellExecuteW
        fn.restype = ctypes.c_void_p
        r = int(fn(None, "runas", str(path), "/SILENT /SUPPRESSMSGBOXES /NORESTART /CLOSEAPPLICATIONS",
                   str(path.parent), 1) or 0)
        if r <= 32:
            raise UpdateError("Izin administrator tidak diberikan, atau installer tidak bisa dimulai. Coba lagi.")

    _LINUX_SCRIPT = r"""#!/bin/sh
# Dibuat DropBridge untuk memasang pembaruan. Jalan terpisah karena paket .deb menghentikan DropBridge yang sedang jalan.
DEB="$1"
SHA="$2"
LOG="$3"
sleep 1
pkexec /bin/sh -c '
set -e
D=/var/tmp/dropbridge-update
rm -rf "$D"
install -d -m 755 "$D"
cp "$1" "$D/p.deb"
echo "$2  $D/p.deb" | sha256sum -c -
DEBIAN_FRONTEND=noninteractive apt-get install -y "$D/p.deb"
rm -rf "$D"
' sh "$DEB" "$SHA" >>"$LOG" 2>&1
RC=$?
rm -f "$DEB"
if ! pgrep -f /opt/dropbridge/PC-App/desktop.py >/dev/null 2>&1; then
  setsid dropbridge >/dev/null 2>&1 &
fi
if [ "$RC" -ne 0 ] && command -v notify-send >/dev/null 2>&1; then
  notify-send -a DropBridge "Pembaruan DropBridge tidak selesai" "Dibatalkan atau gagal. Versi lama tetap dipakai. Catatan: $LOG"
fi
exit "$RC"
"""

    def _launch_linux(self, path: Path, sha: str) -> None:
        if not shutil.which("pkexec"):
            raise UpdateError("pkexec tidak ditemukan. Pasang manual: sudo apt install " + str(path))
        script = path.parent / "apply.sh"
        script.write_text(self._LINUX_SCRIPT, encoding="utf-8")
        script.chmod(0o700)
        log = self.data_dir / "update.log"
        try:
            if log.exists() and log.stat().st_size > 200 * 1024:
                log.unlink()
        except OSError:
            pass
        # Salinan ke lokasi milik root, pengecekan hash, dan apt semuanya terjadi di dalam satu perintah pkexec,
        # supaya berkas tidak bisa ditukar setelah dicek. Proses terpisah (setsid) karena paket menutup DropBridge.
        subprocess.Popen(["/bin/sh", str(script), str(path), sha, str(log)], start_new_session=True,
                         stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, close_fds=True)

    # ---------- cek berkala ----------
    def _notified_file(self) -> Path:
        return self.data_dir / "update.json"

    def _was_notified(self, ver: str) -> bool:
        try:
            return json.loads(self._notified_file().read_text(encoding="utf-8")).get("notified") == ver
        except (OSError, ValueError, AttributeError):
            return False

    def _mark_notified(self, ver: str) -> None:
        try:
            self.data_dir.mkdir(parents=True, exist_ok=True)
            self._notified_file().write_text(json.dumps({"notified": ver}), encoding="utf-8")
        except OSError:
            pass

    def start(self, on_available: Optional[Callable[[str], None]] = None) -> None:
        """Cek di latar belakang (tanpa pesan bila gagal). Pemberitahuan sekali per versi."""
        if self._loop and self._loop.is_alive():
            return
        shutil.rmtree(self.data_dir / "updates", ignore_errors=True)  # sisa pembaruan sebelumnya

        def loop() -> None:
            time.sleep(FIRST_CHECK_AFTER)
            while True:
                try:
                    st = self.check(quiet=True)
                    if on_available and st["state"] == "available" and st["ready"] and not self._was_notified(st["latest"]):
                        self._mark_notified(st["latest"])
                        on_available(st["latest"])
                except Exception:
                    pass
                time.sleep(CHECK_EVERY)

        self._loop = threading.Thread(target=loop, daemon=True)
        self._loop.start()
