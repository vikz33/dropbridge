"""Mengumumkan nama tetap (mis. dropbridge.local) lewat mDNS, supaya HP tidak perlu mengetik IP.

Perlu paket `zeroconf`. Jika tidak ada atau gagal, fitur lain tetap jalan; hanya nama .local yang mati.
"""
import socket
import threading
import time

from .netinfo import lan_ips

_started = False


def _info(name: str, port: int, ips: list[str]):
    from zeroconf import ServiceInfo

    return ServiceInfo(
        "_http._tcp.local.",
        "DropBridge._http._tcp.local.",
        addresses=[socket.inet_aton(ip) for ip in ips],
        port=port,
        server=f"{name}.local.",
        properties={"path": "/"},
    )


def _run(name: str, port: int) -> None:
    try:
        from zeroconf import IPVersion, Zeroconf
    except ImportError:
        print("  (Paket zeroconf belum terpasang: nama .local tidak aktif. Jalankan start.bat sekali lagi.)")
        return
    zc = None
    ips: list[str] = []
    info = None
    while True:
        try:
            now_ips = lan_ips()
            if now_ips and now_ips != ips:
                new = _info(name, port, now_ips)
                if zc is None:
                    zc = Zeroconf(ip_version=IPVersion.V4Only)
                    zc.register_service(new, allow_name_change=True)
                else:
                    zc.update_service(new)
                info = new
                ips = now_ips
        except Exception as e:  # noqa: BLE001 - jangan sampai menjatuhkan aplikasi
            print(f"  (Nama .local gagal diumumkan: {e})")
            time.sleep(60)
        time.sleep(30)


def start(port: int, name: str) -> None:
    global _started
    name = (name or "").strip().lower()
    if _started or not name:
        return
    _started = True
    threading.Thread(target=_run, args=(name, port), daemon=True).start()
