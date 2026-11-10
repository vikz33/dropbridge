"""DropBridge di ikon tray Windows: server jalan di latar belakang, tanpa jendela hitam.

Dijalankan oleh start.bat dengan pythonw.exe. Klik kanan ikon untuk membuka DropBridge,
menampilkan QR, membuka folder shared, mengatur mulai otomatis, atau berhenti.
Catatan server ditulis ke data/server.log.
"""
import os
import socket
import sys
import threading
import traceback
import webbrowser
from pathlib import Path

BASE = Path(__file__).resolve().parent
os.chdir(BASE)

# pythonw.exe tidak punya konsol: semua print diarahkan ke file log.
_LOG = BASE / "data" / "server.log"
_LOG.parent.mkdir(exist_ok=True)
try:
    if _LOG.exists() and _LOG.stat().st_size > 2 * 1024 * 1024:
        _LOG.replace(_LOG.with_suffix(".log.1"))
except OSError:
    pass
_log = open(_LOG, "a", encoding="utf-8", buffering=1)
if sys.stdout is None or not sys.stdout.isatty():
    sys.stdout = _log
    sys.stderr = _log


def message(text: str, title: str = "DropBridge") -> None:
    """Kotak pesan Windows (tetap terlihat walau tanpa konsol)."""
    print(text, flush=True)
    if os.name == "nt":
        try:
            import ctypes
            ctypes.windll.user32.MessageBoxW(None, text, title, 0x40)
        except Exception:
            pass


try:
    import uvicorn
    from PIL import Image, ImageDraw

    from app.config import settings
    from app.main import app
except Exception:
    traceback.print_exc()
    message("DropBridge gagal dimulai: paket belum lengkap.\nJalankan start.bat sekali saat ada internet.\n\nDetail di data\\server.log")
    sys.exit(1)


def url(path: str = "") -> str:
    return f"http://localhost:{settings.port}/{path}"


def port_in_use(port: int) -> bool:
    try:
        with socket.create_connection(("127.0.0.1", port), timeout=0.5):
            return True
    except OSError:
        return False


class Server:
    """uvicorn di thread terpisah supaya ikon tray tetap responsif."""

    def __init__(self) -> None:
        cfg = uvicorn.Config(app, host=settings.host, port=settings.port, log_level="warning")
        self.srv = uvicorn.Server(cfg)
        self.thread = threading.Thread(target=self._run, daemon=True)
        self.error = ""

    def _run(self) -> None:
        try:
            self.srv.run()
        except BaseException as e:  # SystemExit dari uvicorn bila port gagal dipakai
            self.error = str(e) or e.__class__.__name__
            traceback.print_exc()

    def start(self) -> None:
        self.thread.start()

    def stop(self) -> None:
        self.srv.should_exit = True
        self.thread.join(timeout=5)


def icon_image(size: int = 64) -> "Image.Image":
    """Logo DropBridge: kotak teal dengan panah naik-turun di atas garis."""
    s = size / 64
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle([0, 0, size - 1, size - 1], radius=int(16 * s), fill=(15, 118, 110, 255))
    w = max(2, int(5 * s))
    white = (255, 255, 255, 255)
    # panah naik
    d.line([(22 * s, 38 * s), (22 * s, 14 * s)], fill=white, width=w)
    d.line([(14 * s, 22 * s), (22 * s, 14 * s), (30 * s, 22 * s)], fill=white, width=w, joint="curve")
    # panah turun
    d.line([(42 * s, 14 * s), (42 * s, 38 * s)], fill=white, width=w)
    d.line([(34 * s, 30 * s), (42 * s, 38 * s), (50 * s, 30 * s)], fill=white, width=w, joint="curve")
    # lantai
    d.line([(10 * s, 50 * s), (54 * s, 50 * s)], fill=white, width=w)
    return im


# ---------- Mulai otomatis saat Windows menyala ----------
def _startup_file() -> Path:
    appdata = os.environ.get("APPDATA", "")
    return Path(appdata) / "Microsoft" / "Windows" / "Start Menu" / "Programs" / "Startup" / "DropBridge.vbs"


def autostart_on() -> bool:
    return os.name == "nt" and _startup_file().exists()


def set_autostart(on: bool) -> None:
    f = _startup_file()
    if not on:
        f.unlink(missing_ok=True)
        return
    pyw = Path(sys.executable).with_name("pythonw.exe")
    if not pyw.exists():
        pyw = Path(sys.executable)
    vbs = (
        'Set sh = CreateObject("WScript.Shell")\r\n'
        f'sh.CurrentDirectory = "{BASE}"\r\n'
        f'sh.Run """{pyw}"" ""{BASE / "tray.py"}""", 0, False\r\n'
    )
    f.parent.mkdir(parents=True, exist_ok=True)
    f.write_text(vbs, encoding="utf-8")


def main() -> None:
    if port_in_use(settings.port):
        # Sudah jalan (mis. dari mulai otomatis): cukup buka di browser.
        webbrowser.open(url())
        return

    try:
        import pystray
    except Exception:
        traceback.print_exc()
        message("Paket pystray belum terpasang. Jalankan start.bat sekali saat ada internet.")
        return

    server = Server()
    server.start()
    print(f"DropBridge aktif di {url()} · PIN {settings.pin} · folder {settings.shared_dir}", flush=True)

    def open_web(icon=None, item=None):
        webbrowser.open(url())

    def open_qr(icon=None, item=None):
        webbrowser.open(url("#/hubungkan"))

    def open_folder(icon=None, item=None):
        try:
            os.startfile(str(settings.shared_dir))  # type: ignore[attr-defined]
        except Exception:
            webbrowser.open(settings.shared_dir.as_uri())

    def toggle_autostart(icon, item):
        try:
            set_autostart(not autostart_on())
        except OSError as e:
            message(f"Gagal mengatur mulai otomatis: {e}")

    def quit_all(icon, item):
        icon.visible = False
        server.stop()
        icon.stop()

    menu = pystray.Menu(
        pystray.MenuItem("Buka DropBridge", open_web, default=True),
        pystray.MenuItem("Hubungkan HP (QR)", open_qr),
        pystray.MenuItem("Buka folder shared", open_folder),
        pystray.MenuItem(f"PIN: {settings.pin}", None, enabled=False),
        pystray.Menu.SEPARATOR,
        pystray.MenuItem("Mulai otomatis saat Windows menyala", toggle_autostart,
                         checked=lambda item: autostart_on(), visible=os.name == "nt"),
        pystray.MenuItem("Berhenti", quit_all),
    )
    icon = pystray.Icon("DropBridge", icon_image(), "DropBridge", menu)

    def ready(icon):
        icon.visible = True
        threading.Event().wait(1.5)
        if server.error or not server.thread.is_alive():
            message(f"Server gagal jalan (port {settings.port} dipakai program lain?).\n\nDetail di data\\server.log")
            icon.stop()
            return
        try:
            icon.notify(f"Aktif di {url()}  ·  PIN {settings.pin}", "DropBridge")
        except Exception:
            pass

    icon.run(setup=ready)


if __name__ == "__main__":
    main()
