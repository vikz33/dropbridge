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
            if data.strip() == b"DROPBRIDGE?" and _is_local(addr[0]):
                sock.sendto(reply, addr)
        except OSError:
            continue


def start(http_port: int, lan_only: bool = True) -> None:
    threading.Thread(target=_serve, args=(http_port,), daemon=True).start()
