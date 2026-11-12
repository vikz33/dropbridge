"""Titik masuk DropBridge: `python run.py` (atau klik dua kali start.bat)."""
import sys

try:
    from app.config import settings
    from app.main import app
    from app.netinfo import lan_urls
except ModuleNotFoundError as e:
    print(f"\nPaket belum terpasang: {e}")
    print("Jalankan lewat start.bat, atau: pip install -r requirements.txt")
    sys.exit(1)

import uvicorn


def banner() -> None:
    urls = lan_urls(settings.port)
    line = "=" * 58
    print(line)
    print("  DropBridge aktif")
    print(line)
    print(f"  Di PC ini : http://localhost:{settings.port}")
    if urls:
        for u in urls:
            print(f"  Di HP     : {u}")
    else:
        print("  Di HP     : (IP tidak terdeteksi, cek dengan perintah: ipconfig)")
    print(f"  PIN       : {settings.pin}")
    print(f"  Folder    : {settings.shared_dir}")
    print(line)
    print("  HP dan PC harus di Wi-Fi yang sama. Ctrl+C untuk berhenti.")
    print()


if __name__ == "__main__":
    banner()
    uvicorn.run(app, host=settings.host, port=settings.port, log_level="warning")
