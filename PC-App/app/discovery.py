"""Membalas pencarian dari aplikasi Android: HP menyiarkan 'DROPBRIDGE?' lewat UDP,
PC menjawab dengan nama dan port, sehingga HP tidak perlu mengetik alamat IP."""
import ipaddress
import json
import socket
import threading

DISCOVERY_PORT = 47800


def _is_local(host: str) -> bool:
    try:
        ip = ipaddress.ip_address(host)
    except ValueError:
        return False
    return ip.is_private or ip.is_loopback or ip.is_link_local


def _serve(http_port: int) -> None:
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        sock.bind(("", DISCOVERY_PORT))
    except OSError:
        return  # port dipakai (mis. jalan dua kali); pencarian otomatis dinonaktifkan, fitur lain tetap jalan
    from .config import os_label, settings
    from .version import VERSION
    reply = json.dumps({"name": "DropBridge", "port": http_port, "host": socket.gethostname(),
                        "id": settings.pc_id, "os": os_label(), "version": VERSION}).encode()
    while True:
        try:
            data, addr = sock.recvfrom(256)
            if data.strip() == b"DROPBRIDGE?" and _is_local(addr[0]) and settings.mode != "guest":
                sock.sendto(reply, addr)
        except OSError:
            continue


_peers_cache: tuple[float, list[dict]] = (0.0, [])
_peers_lock = threading.Lock()


def peers(max_age: float = 15.0) -> list[dict]:
    """Laptop DropBridge lain di Wi-Fi ini (siaran UDP yang sama dengan pencarian HP, ±1 detik).

    Untuk peta Hubungkan. Hasil disimpan sebentar supaya halaman yang dimuat ulang tidak menyiarkan terus.
    """
    import time
    from .config import settings
    from .netinfo import lan_ips
    global _peers_cache
    with _peers_lock:
        if time.time() - _peers_cache[0] < max_age:
            return list(_peers_cache[1])
        found: dict[str, dict] = {}
        targets = {"255.255.255.255"}
        for ip in lan_ips():
            parts = ip.split(".")
            if len(parts) == 4:
                targets.add(".".join(parts[:3] + ["255"]))  # anggap /24, jaringan rumah/kantor umumnya begitu
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
            sock.settimeout(0.25)
        except OSError:
            return []
        try:
            for _round in range(2):
                for t in targets:
                    try:
                        sock.sendto(b"DROPBRIDGE?", (t, DISCOVERY_PORT))
                    except OSError:
                        pass
                end = time.time() + 0.5
                while time.time() < end:
                    try:
                        data, addr = sock.recvfrom(1024)
                    except OSError:
                        continue
                    try:
                        j = json.loads(data.decode("utf-8", "replace"))
                    except ValueError:
                        continue
                    if j.get("name") != "DropBridge" or j.get("id") == settings.pc_id:
                        continue  # bukan DropBridge, atau laptop ini sendiri
                    key = j.get("id") or addr[0]
                    found[key] = {"id": j.get("id", ""), "name": j.get("host", ""), "os": j.get("os", ""),
                                  "version": j.get("version", ""), "url": f"http://{addr[0]}:{j.get('port', 8000)}"}
        finally:
            sock.close()
        _peers_cache = (time.time(), list(found.values()))
        return list(found.values())


def start(http_port: int, lan_only: bool = True) -> None:
    threading.Thread(target=_serve, args=(http_port,), daemon=True).start()
