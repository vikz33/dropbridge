"""Logo DropBridge sebagai gambar (ikon tray dan ikon .exe): kotak teal, panah naik-turun di atas garis."""
from PIL import Image, ImageDraw


def icon_image(size: int = 64) -> "Image.Image":
    s = size / 64
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle([0, 0, size - 1, size - 1], radius=int(16 * s), fill=(15, 118, 110, 255))
    w = max(2, int(5 * s))
    white = (255, 255, 255, 255)
    d.line([(22 * s, 38 * s), (22 * s, 14 * s)], fill=white, width=w)
    d.line([(14 * s, 22 * s), (22 * s, 14 * s), (30 * s, 22 * s)], fill=white, width=w, joint="curve")
    d.line([(42 * s, 14 * s), (42 * s, 38 * s)], fill=white, width=w)
    d.line([(34 * s, 30 * s), (42 * s, 38 * s), (50 * s, 30 * s)], fill=white, width=w, joint="curve")
    d.line([(10 * s, 50 * s), (54 * s, 50 * s)], fill=white, width=w)
    return im


def save_ico(path: str) -> None:
    """Ikon Windows berisi beberapa ukuran (untuk .exe, installer, dan taskbar)."""
    big = icon_image(256)
    big.save(path, format="ICO", sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
