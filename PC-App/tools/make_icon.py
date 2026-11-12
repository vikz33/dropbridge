"""Membuat build/dropbridge.ico dari logo DropBridge (dipakai build_exe.bat)."""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

from app.icon import save_ico  # noqa: E402

out = ROOT / "build" / "dropbridge.ico"
out.parent.mkdir(exist_ok=True)
save_ico(str(out))
print(f"Ikon: {out}")
