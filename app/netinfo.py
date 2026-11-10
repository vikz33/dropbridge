"""Mencari alamat IP PC di jaringan lokal."""
import socket


def lan_ips() -> list[str]:
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
    return [ip for ip in ips if not ip.startswith(("127.", "169.254.", "0."))]


def lan_urls(port: int) -> list[str]:
    return [f"http://{ip}:{port}" for ip in lan_ips()]
