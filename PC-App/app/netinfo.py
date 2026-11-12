"""Mencari alamat IP PC: di jaringan lokal (Wi-Fi/LAN) dan di Tailscale (akses dari luar jaringan)."""
import ipaddress
import json
import os
import shutil
import socket
import subprocess
import time

# Alamat perangkat Tailscale selalu di rentang ini (IPv4 100.64.0.0/10, IPv6 fd7a:115c:a1e0::/48).
TAILSCALE_V4 = ipaddress.ip_network("100.64.0.0/10")
TAILSCALE_V6 = ipaddress.ip_network("fd7a:115c:a1e0::/48")


def is_tailscale(ip: str) -> bool:
    try:
        a = ipaddress.ip_address(ip)
    except ValueError:
        return False
    if a.version == 6 and a.ipv4_mapped:
        a = a.ipv4_mapped
    return a in (TAILSCALE_V4 if a.version == 4 else TAILSCALE_V6)


def all_ips() -> list[str]:
    """Semua alamat IPv4 PC ini, termasuk Tailscale."""
    ips: list[str] = []
    try:
        # Trik UDP: tidak ada paket yang dikirim, hanya memilih antarmuka utama.
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
            s.connect(("10.255.255.255", 1))
            ips.append(s.getsockname()[0])
    except OSError:
        pass
    try:
        for ip in socket.gethostbyname_ex(socket.gethostname())[2]:
            if ip not in ips:
                ips.append(ip)
    except OSError:
        pass
    for ip in _tailscale_cli()[0]:
        if ip not in ips:
            ips.append(ip)
    return [ip for ip in ips if not ip.startswith(("127.", "169.254.", "0."))]


def lan_ips() -> list[str]:
    """Alamat di jaringan lokal saja (untuk QR Wi-Fi)."""
    return [ip for ip in all_ips() if not is_tailscale(ip)]


def lan_urls(port: int) -> list[str]:
    return [f"http://{ip}:{port}" for ip in lan_ips()]


# ---------- Tailscale ----------
_cache: dict = {"at": 0.0, "val": ([], "")}


def _tailscale_exe() -> str:
    found = shutil.which("tailscale")
    if found:
        return found
    if os.name == "nt":
        for base in (os.environ.get("ProgramFiles", r"C:\Program Files"), os.environ.get("ProgramFiles(x86)", "")):
            p = os.path.join(base, "Tailscale", "tailscale.exe") if base else ""
            if p and os.path.exists(p):
                return p
    return ""


def _tailscale_cli() -> tuple[list[str], str]:
    """(alamat IPv4, nama MagicDNS) dari perintah `tailscale status --json`. Disimpan 30 detik."""
    now = time.monotonic()
    if now - _cache["at"] < 30:
        return _cache["val"]
    ips: list[str] = []
    dns = ""
    exe = _tailscale_exe()
    if exe:
        try:
            flags = 0x08000000 if os.name == "nt" else 0  # CREATE_NO_WINDOW: tanpa kedip jendela hitam
            out = subprocess.run([exe, "status", "--json"], capture_output=True, timeout=3, creationflags=flags)
            if out.returncode == 0:
                me = json.loads(out.stdout.decode("utf-8", "replace")).get("Self") or {}
                if me.get("Online", True):
                    ips = [ip for ip in me.get("TailscaleIPs") or [] if ":" not in ip]
                    dns = (me.get("DNSName") or "").rstrip(".")
        except (OSError, ValueError, subprocess.SubprocessError):
            pass
    _cache["at"] = now
    _cache["val"] = (ips, dns)
    return _cache["val"]


def tailscale_ips() -> list[str]:
    ips = [ip for ip in all_ips() if is_tailscale(ip)]
    return ips


def remote_urls(port: int) -> list[str]:
    """Alamat untuk masuk dari luar jaringan lewat Tailscale (kosong bila Tailscale tidak aktif)."""
    return [f"http://{ip}:{port}" for ip in tailscale_ips()]


def tailscale_name() -> str:
    return _tailscale_cli()[1]
