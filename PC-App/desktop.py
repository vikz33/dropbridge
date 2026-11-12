"""DropBridge versi desktop: begitu dibuka, laptop ini langsung jadi server.

- Jendela sendiri (WebView2 bawaan Windows), isinya halaman yang sama dengan web dan aplikasi.
- Tombol X menyembunyikan jendela ke tray; server tetap jalan supaya HP masih bisa mengirim.
- Dibuka lagi saat sudah jalan: jendela yang ada dimunculkan (tidak ada salinan kedua).
- `--tray`: mulai tanpa jendela (dipakai saat Windows menyala).
- Notifikasi Windows saat file masuk.

Dijalankan oleh DropBridge.exe (hasil build_exe.bat) atau `pythonw desktop.py` (start.bat).
Catatan ada di data/server.log.
"""
import json
import os
import socket
import sys
import threading
import time
import traceback
import urllib.request
import webbrowser
from pathlib import Path

if not getattr(sys, "frozen", False):
    os.chdir(Path(__file__).resolve().parent)

# Jendela tanpa konsol: semua print ke file log.
try:
    from app.config import USER_DIR
    _LOG = USER_DIR / "data" / "server.log"
    _LOG.parent.mkdir(parents=True, exist_ok=True)
    if _LOG.exists() and _LOG.stat().st_size > 2 * 1024 * 1024:
        _LOG.replace(_LOG.with_suffix(".log.1"))
    _log = open(_LOG, "a", encoding="utf-8", buffering=1)
    if sys.stdout is None or not sys.stdout.isatty():
        sys.stdout = _log
        sys.stderr = _log
except Exception:
    pass


def message(text: str, title: str = "DropBridge") -> None:
    """Kotak pesan Windows (tetap terlihat walau tanpa konsol)."""
    print(text, flush=True)
    if os.name == "nt":
        try:
            import ctypes
            ctypes.windll.user32.MessageBoxW(None, text, title, 0x40 | 0x40000)
        except Exception:
            pass


def ask(text: str, title: str = "DropBridge", buttons: int = 0x4) -> int:
    """Ya/Tidak (0x4) atau Ya/Tidak/Batal (0x3). Hasil: 6 Ya, 7 Tidak, 2 Batal."""
    if os.name != "nt":
        return 2
    import ctypes
    return ctypes.windll.user32.MessageBoxW(None, text, title, buttons | 0x20 | 0x40000)


try:
    import uvicorn

    from app import events
    from app.config import FROZEN, USER_DIR, save_setting, settings
    from app.icon import icon_image
    from app.main import app
    from app.routes.system import android_info, welcome_due
    from app.services import storage_admin
    from app.services.storage import StorageError
    from app.version import VERSION
except Exception:
    traceback.print_exc()
    message("DropBridge gagal dimulai: ada berkas yang kurang.\n\nDetail di data\\server.log")
    sys.exit(1)

START_HIDDEN = "--tray" in sys.argv
LOCAL = f"http://127.0.0.1:{settings.port}"


def port_in_use(port: int) -> bool:
    try:
        with socket.create_connection(("127.0.0.1", port), timeout=0.5):
            return True
    except OSError:
        return False


def path_label(p: str) -> str:
    return p.replace("/", " › ") if p else "Tanpa folder"


class Server:
    """uvicorn di thread terpisah supaya jendela dan ikon tray tetap responsif."""

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


# ---------- Mulai otomatis saat Windows menyala (registry Run, sama dengan installer) ----------
RUN_KEY = r"Software\Microsoft\Windows\CurrentVersion\Run"


def _autostart_cmd() -> str:
    if FROZEN:
        return f'"{sys.executable}" --tray'
    pyw = Path(sys.executable).with_name("pythonw.exe")
    return f'"{pyw if pyw.exists() else sys.executable}" "{Path(__file__).resolve()}" --tray'


def autostart_on() -> bool:
    if os.name != "nt":
        return False
    import winreg
    try:
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, RUN_KEY) as k:
            winreg.QueryValueEx(k, "DropBridge")
            return True
    except OSError:
        return False


def set_autostart(on: bool) -> None:
    import winreg
    with winreg.OpenKey(winreg.HKEY_CURRENT_USER, RUN_KEY, 0, winreg.KEY_SET_VALUE) as k:
        if on:
            winreg.SetValueEx(k, "DropBridge", 0, winreg.REG_SZ, _autostart_cmd())
        else:
            try:
                winreg.DeleteValue(k, "DropBridge")
            except OSError:
                pass


def migrate_old_autostart() -> None:
    """Versi lama memakai DropBridge.vbs di folder Startup: ganti ke registry (yang dipakai installer juga)."""
    if os.name != "nt":
        return
    vbs = Path(os.environ.get("APPDATA", "")) / "Microsoft" / "Windows" / "Start Menu" / "Programs" / "Startup" / "DropBridge.vbs"
    if vbs.exists():
        try:
            vbs.unlink()
            set_autostart(True)
        except OSError:
            pass
    elif autostart_on():
        # Perbarui jalurnya (mis. folder kode dipindah, atau pindah dari start.bat ke .exe).
        # Versi dari kode tidak menimpa autostart milik versi terpasang.
        if FROZEN or "DropBridge.exe" not in _autostart_value():
            set_autostart(True)


def _autostart_value() -> str:
    import winreg
    try:
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, RUN_KEY) as k:
            return str(winreg.QueryValueEx(k, "DropBridge")[0])
    except OSError:
        return ""


# ---------- Aplikasi ----------
class Desktop:
    def __init__(self) -> None:
        self.server = Server()
        self.window = None
        self.icon = None
        self.quitting = False
        self.hid_once = False
        self.pending: dict[str, int] = {}
        self.pending_timer: threading.Timer | None = None
        self.last_folder = ""
        self.last_folder_at = 0.0
        self.lock = threading.Lock()

    # ----- jendela -----
    def start_url(self) -> str:
        route = "#/selamat-datang" if welcome_due() else ""
        return f"{LOCAL}/?pin={settings.pin}{route}"

    def show(self, route: str | None = None) -> None:
        if self.window is None:
            webbrowser.open(f"http://localhost:{settings.port}/{route or ''}")
            return
        try:
            if route:
                self.window.evaluate_js(f"location.hash = {json.dumps(route)}")
            self.window.show()
            self.window.restore()
            # bawa ke depan
            self.window.on_top = True
            self.window.on_top = False
        except Exception:
            traceback.print_exc()

    def on_closing(self):
        if self.quitting:
            return True
        # X = sembunyikan ke tray; server tetap jalan
        self.window.hide()
        if not self.hid_once:
            self.hid_once = True
            self.notify("DropBridge tetap berjalan di tray, HP masih bisa mengirim. Klik ikonnya untuk membuka lagi.")
        return False

    # ----- notifikasi -----
    def notify(self, text: str, title: str = "DropBridge") -> None:
        try:
            if self.icon is not None:
                self.icon.notify(text, title)
        except Exception:
            pass

    def on_file(self, folder: str, name: str) -> None:
        if not settings.notify:
            return
        with self.lock:
            self.pending[folder] = self.pending.get(folder, 0) + 1
            if self.pending_timer:
                self.pending_timer.cancel()
            # kumpulkan dulu: 10 foto = 1 notifikasi
            self.pending_timer = threading.Timer(3.0, self.flush_files)
            self.pending_timer.daemon = True
            self.pending_timer.start()

    def flush_files(self) -> None:
        with self.lock:
            got, self.pending = self.pending, {}
        if not got:
            return
        total = sum(got.values())
        if len(got) == 1:
            folder = next(iter(got))
            text = f"{total} file masuk ke {path_label(folder)}"
        else:
            folder = max(got, key=got.get)
            text = f"{total} file masuk ke {len(got)} folder"
        self.last_folder, self.last_folder_at = folder, time.time()
        self.notify(text + "\nKlik ikon DropBridge untuk membuka.", "File masuk")

    def open_default(self, icon=None, item=None) -> None:
        # Baru saja ada file masuk: langsung buka foldernya
        if self.last_folder_at and time.time() - self.last_folder_at < 300:
            self.last_folder_at = 0
            f = self.last_folder
            self.show("#/f/" + urllib.request.quote(f, safe="") if f else "#/root")
        else:
            self.show()

    # ----- folder penyimpanan -----
    def switch_storage(self, target: Path | None) -> None:
        if target is None:
            return
        try:
            storage_admin.check_target(target)
        except StorageError as e:
            message(e.message)
            return
        n = storage_admin.count_files(settings.shared_dir)
        move = False
        if n:
            r = ask(
                f"Simpan kiriman baru di:\n{target}\n\n"
                f"Pindahkan juga {n} file yang sudah ada di\n{settings.shared_dir}\nke folder baru itu?\n\n"
                "Ya = pindahkan (file yang sudah ada di tujuan tidak ditimpa)\n"
                "Tidak = biarkan di tempat lama\nBatal = jangan ganti folder",
                buttons=0x3,
            )
            if r == 2:
                return
            move = r == 6
        try:
            rep = storage_admin.switch(target, move)
        except StorageError as e:
            message(e.message)
            return
        text = f"Folder penyimpanan sekarang:\n{rep['path']}"
        if move:
            text += f"\n\nDipindah: {rep['moved']} file."
            if rep["same"]:
                text += f"\nSudah ada (kembar, dilewati): {rep['same']} file."
            if rep["clash"]:
                text += f"\nNama sama tapi isi beda, dibiarkan di folder lama: {rep['clash']} file."
        text += "\n\nTips OneDrive: klik kanan folder itu > 'Selalu simpan di perangkat ini' supaya thumbnail tetap cepat."
        message(text)
        if self.icon:
            self.icon.update_menu()
        if self.window is not None:
            try:
                self.window.evaluate_js("window.dispatchEvent(new Event('db-storage-changed'))")
            except Exception:
                pass

    # ----- tray -----
    def build_tray(self):
        import pystray

        def short(p: Path) -> str:
            s = str(p)
            home = str(Path.home())
            if s.lower().startswith(home.lower()):
                s = "~" + s[len(home):]
            return s if len(s) <= 60 else s[:25] + "…" + s[-32:]

        def open_folder(icon, item):
            try:
                os.startfile(str(settings.shared_dir))  # type: ignore[attr-defined]
            except Exception:
                webbrowser.open(settings.shared_dir.as_uri())

        def to_onedrive(icon, item):
            od = storage_admin.onedrive_work()
            if od:
                self.switch_storage(od / "DropBridge")

        def choose(icon, item):
            od = storage_admin.onedrive_work()
            self.switch_storage(storage_admin.pick_folder(od if od else settings.shared_dir))

        def toggle_autostart(icon, item):
            try:
                set_autostart(not autostart_on())
            except OSError as e:
                message(f"Gagal mengatur mulai otomatis: {e}")

        def toggle_notify(icon, item):
            settings.notify = not settings.notify
            save_setting("notify", settings.notify)

        def quit_all(icon, item):
            self.quit()

        android = android_info()
        ver = f"Versi {VERSION}" + (f" · Android {android['version']}" if android["available"] else "")
        menu = pystray.Menu(
            pystray.MenuItem("Buka DropBridge", self.open_default, default=True),
            pystray.MenuItem("Hubungkan HP (QR)", lambda i, it: self.show("#/hubungkan")),
            pystray.MenuItem("Buka folder penyimpanan", open_folder),
            pystray.MenuItem(
                "Folder penyimpanan",
                pystray.Menu(
                    pystray.MenuItem(lambda item: f"Sekarang: {short(settings.shared_dir)}", None, enabled=False),
                    pystray.MenuItem("Simpan di OneDrive kantor (folder DropBridge)", to_onedrive,
                                     visible=storage_admin.onedrive_work() is not None),
                    pystray.MenuItem("Pilih folder lain…", choose),
                ),
            ),
            pystray.MenuItem(f"PIN: {settings.pin}", None, enabled=False),
            pystray.MenuItem(ver, None, enabled=False),
            pystray.Menu.SEPARATOR,
            pystray.MenuItem("Mulai otomatis saat Windows menyala", toggle_autostart,
                             checked=lambda item: autostart_on(), visible=os.name == "nt"),
            pystray.MenuItem("Notifikasi saat file masuk", toggle_notify, checked=lambda item: settings.notify),
            pystray.MenuItem("Berhenti", quit_all),
        )
        return pystray.Icon("DropBridge", icon_image(), f"DropBridge {VERSION}", menu)

    def quit(self) -> None:
        self.quitting = True
        try:
            if self.icon:
                self.icon.visible = False
                self.icon.stop()
        except Exception:
            pass
        self.server.stop()
        if self.window is not None:
            try:
                self.window.destroy()
            except Exception:
                pass
        else:
            os._exit(0)

    # ----- jalan -----
    def run(self) -> None:
        self.server.start()
        print(f"DropBridge {VERSION} aktif di {LOCAL} · PIN {settings.pin} · folder {settings.shared_dir}", flush=True)
        time.sleep(1.0)
        if self.server.error or not self.server.thread.is_alive():
            message(f"Server gagal jalan (port {settings.port} dipakai program lain?).\n\nDetail di data\\server.log")
            return
        events.on("file", self.on_file)
        events.on("show", lambda route: self.show(route or None))
        migrate_old_autostart()

        try:
            import pystray  # noqa: F401
            self.icon = self.build_tray()
            threading.Thread(target=self.icon.run, daemon=True).start()
        except Exception:
            traceback.print_exc()

        try:
            import webview
        except Exception:
            traceback.print_exc()
            webview = None

        if webview is None:
            # Tanpa pywebview (mis. saat mengembangkan): buka di browser seperti dulu, tray tetap ada
            if not START_HIDDEN:
                webbrowser.open(f"http://localhost:{settings.port}/" + ("#/selamat-datang" if welcome_due() else ""))
            while not self.quitting:
                time.sleep(0.5)
            return

        try:
            webview.settings["ALLOW_DOWNLOADS"] = True
            webview.settings["OPEN_EXTERNAL_LINKS_IN_BROWSER"] = True
        except Exception:
            pass
        self.window = webview.create_window(
            "DropBridge", self.start_url(), width=1240, height=820, min_size=(400, 560),
            hidden=START_HIDDEN, text_select=True, background_color="#F7F8F7",
        )
        self.window.events.closing += self.on_closing
        storage = str(USER_DIR / "webview")
        try:
            webview.start(private_mode=False, storage_path=storage)
        except Exception:
            traceback.print_exc()
            message("Jendela DropBridge tidak bisa dibuka (WebView2 tidak tersedia?).\n"
                    "DropBridge tetap jalan dan dibuka di browser.")
            self.window = None
            webbrowser.open(f"http://localhost:{settings.port}/")
            while not self.quitting:
                time.sleep(0.5)
        # jendela ditutup lewat Berhenti
        self.quitting = True
        self.server.stop()


def main() -> None:
    if port_in_use(settings.port):
        # Sudah jalan: minta jendela yang ada muncul, lalu selesai (kecuali saat Windows menyala)
        if not START_HIDDEN:
            try:
                urllib.request.urlopen(urllib.request.Request(f"{LOCAL}/api/desktop/show", method="POST", data=b""), timeout=3)
            except Exception:
                webbrowser.open(f"http://localhost:{settings.port}/")
        return
    Desktop().run()


if __name__ == "__main__":
    main()
