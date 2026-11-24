#!/usr/bin/env bash
# Mengunggah paket .deb ke GitHub Releases, supaya DropBridge di laptop Linux bisa memperbarui diri.
# Urutan tiap rilis:  1) git push   2) bash build_deb.sh   3) bash rilis_github.sh
# Perlu sekali: sudo apt install gh   lalu   gh auth login
# Rilis dibuat sebagai draf bila belum ada (biasanya dari Windows), diisi berkas, lalu diterbitkan.
set -e
cd "$(dirname "$(readlink -f "$0")")"
ROOT="$(cd .. && pwd)"
VER=$(sed -n 's/^VERSION = "\(.*\)"/\1/p' app/version.py)
[ -n "$VER" ] || { echo "Versi tidak terbaca dari app/version.py"; exit 1; }
command -v gh >/dev/null 2>&1 || { echo "GitHub CLI belum terpasang: sudo apt install gh  lalu  gh auth login"; exit 1; }

DEB="$ROOT/Rilis/dropbridge_${VER}_amd64.deb"
[ -f "$DEB" ] || { echo "Belum ada $DEB. Jalankan: bash build_deb.sh"; exit 1; }

cd "$ROOT"
git fetch -q origin 2>/dev/null || true
AHEAD=$(git rev-list --count origin/main..HEAD)
if [ "$AHEAD" != "0" ]; then
  echo "Masih ada $AHEAD commit yang belum di-push. Jalankan  git push  dulu, supaya tag v$VER menunjuk kode yang benar."
  exit 1
fi

SHA=$(sha256sum "$DEB" | cut -d' ' -f1)
echo "$SHA  $(basename "$DEB")" > "$DEB.sha256"
echo "SHA-256: $SHA"

NEWREL=0
if ! gh release view "v$VER" >/dev/null 2>&1; then
  NEWREL=1
  if [ -f "Rilis/catatan-$VER.md" ]; then
    gh release create "v$VER" --draft --target main --title "DropBridge $VER" --notes-file "Rilis/catatan-$VER.md"
  else
    gh release create "v$VER" --draft --target main --title "DropBridge $VER" --notes "DropBridge $VER"
  fi
fi
gh release upload "v$VER" "$DEB" "$DEB.sha256" --clobber
[ "$NEWREL" = "1" ] && gh release edit "v$VER" --draft=false --latest
echo
echo "SELESAI. Rilis v$VER (Linux) ada di GitHub:"
gh release view "v$VER" --json url -q .url
