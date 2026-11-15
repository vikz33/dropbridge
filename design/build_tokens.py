"""Membuat Web/tokens.css dan Android Tokens.kt dari tokens.json, lalu memeriksa kontras WCAG.

Jalankan setelah mengubah tokens.json:  python design/build_tokens.py
"""
import json, re, sys, pathlib
here = pathlib.Path(__file__).parent
t = json.loads((here / "tokens.json").read_text())

def lum(h):
    h = h.lstrip("#"); r, g, b = (int(h[i:i+2], 16) / 255 for i in (0, 2, 4))
    f = lambda c: c / 12.92 if c <= 0.03928 else ((c + .055) / 1.055) ** 2.4
    return .2126 * f(r) + .7152 * f(g) + .0722 * f(b)
def ratio(a, b):
    la, lb = sorted((lum(a), lum(b)), reverse=True); return (la + .05) / (lb + .05)

# ---- tokens.css
def css_block(mode):
    c = t["color"][mode]; k = t["kind"][mode]
    lines = [f"  --{n}: {v};" for n, v in c.items()] + [f"  --k-{n}: {v};" for n, v in k.items()]
    return "\n".join(lines)
FONTS = [("Plus Jakarta Sans", 500, "jakarta-500"), ("Plus Jakarta Sans", 700, "jakarta-700"),
         ("Plus Jakarta Sans", 800, "jakarta-800"), ("IBM Plex Mono", 600, "plexmono-600")]
s = ["/* Dibuat otomatis oleh design/build_tokens.py dari tokens.json. Jangan diedit langsung. */"]
# Huruf ikut terpasang (Web/fonts), jadi tetap jalan tanpa internet. Lisensi OFL ada di folder yang sama.
s += [f"@font-face {{ font-family: \"{f}\"; font-weight: {w}; font-style: normal; font-display: swap; src: url(\"fonts/{n}.woff2\") format(\"woff2\"); }}" for f, w, n in FONTS]
s += [":root {",
     "  color-scheme: light dark;",
     f"  --font-ui: \"{t['font']['ui']}\", system-ui, sans-serif;", f"  --font-code: \"{t['font']['code']}\", ui-monospace, monospace;"]
s += [f"  --space-{k}: {v}px;" for k, v in t["space"].items()]
s += [f"  --radius-{k}: {v}px;" for k, v in t["radius"].items()]
s += [f"  --{k}: {v}px;" for k, v in t["size"].items()]
for k, v in t["type"].items():
    s.append(f"  --type-{k}: {v['weight']} {v['size']}px/{v['line']}px {'var(--font-code)' if k == 'code' else 'var(--font-ui)'};")
s += [f"  --motion-{k}: {v};" for k, v in t["motion"].items() if k != "reduce"]
s.append(css_block("light")); s.append("}")
s.append("@media (prefers-color-scheme: dark) {\n  :root:not([data-theme=\"light\"]) {\n" + css_block("dark").replace("\n  ", "\n    ").replace("  --", "    --", 1) + "\n  }\n}")
s.append(":root[data-theme=\"dark\"] {\n  color-scheme: dark;\n" + css_block("dark") + "\n}")
s.append(":root[data-theme=\"light\"] { color-scheme: light; }")
s.append("@media (prefers-reduced-motion: reduce) { :root { --motion-fast: 0ms; --motion-base: 0ms; --motion-sheet: 0ms; } }")
root = here.parent
(root / "Web" / "tokens.css").write_text("\n".join(s) + "\n")

# ---- Tokens.kt
def kname(n): return re.sub(r"[^A-Z0-9]", "_", n.upper())
kt = ["package com.dropbridge.app", "", "import android.graphics.Color", "",
      "/** Dibuat otomatis oleh design/build_tokens.py dari tokens.json. Jangan diedit langsung. */",
      "object Tokens {", "    var dark = false", "    private fun c(l: String, d: String): Int = Color.parseColor(if (dark) d else l)", ""]
for n, v in t["color"]["light"].items():
    if v.startswith("#"):
        kt.append(f"    val {kname(n)}: Int get() = c(\"{v}\", \"{t['color']['dark'][n]}\")")
for n, v in t["kind"]["light"].items():
    kt.append(f"    val K_{kname(n)}: Int get() = c(\"{v}\", \"{t['kind']['dark'][n]}\")")
kt.append("")
kt += [f"    const val SPACE_{k} = {v}" for k, v in t["space"].items()]
kt += [f"    const val RADIUS_{kname(k)} = {v}" for k, v in t["radius"].items()]
kt += [f"    const val {kname(k)} = {v}" for k, v in t["size"].items() if isinstance(v, int)]
for k, v in t["type"].items():
    kt.append(f"    const val TYPE_{kname(k)} = {v['size']}f")
kt.append("}")
(root / "Android-App/app/src/main/java/com/dropbridge/app/Tokens.kt").write_text("\n".join(kt) + "\n")

# ---- kontras
pairs = [  # (teks, latar, jenis, min)
 ("text","bg","teks utama",4.5),("text","surface","teks utama",4.5),("text","surface-2","teks utama",4.5),
 ("text-muted","bg","teks redup",4.5),("text-muted","surface","teks redup",4.5),("text-muted","surface-2","teks redup",4.5),
 ("accent","surface","tautan/ikon teal",4.5),("accent","bg","tautan/ikon teal",4.5),("on-accent","accent","tombol utama",4.5),
 ("on-accent-soft","accent-soft","chip/tombol lunak",4.5),
 ("guest","surface","teks indigo",4.5),("on-guest","guest","tombol HP lain",4.5),("on-guest-soft","guest-soft","chip indigo",4.5),
 ("success","surface","status berhasil",4.5),("warning","warning-soft","peringatan",4.5),("warning","surface","teks peringatan",4.5),
 ("danger","surface","teks bahaya",4.5),("danger","danger-soft","kotak bahaya",4.5),
 ("border-input","surface","garis isian (non-teks)",3.0),("accent","surface","cincin fokus (non-teks)",3.0),
]
rows=[]; fails=0
for mode in ("light","dark"):
    c=t["color"][mode]
    for fg,bg,what,mn in pairs:
        r=ratio(c[fg],c[bg]); ok=r>=mn; fails+=not ok
        rows.append(f"| {mode} | {what} | `{fg}` {c[fg]} on `{bg}` {c[bg]} | {r:.2f} | {mn} | {'lulus' if ok else 'GAGAL'} |")
    for n in t["kind"][mode]:
        r=ratio(t["kind"][mode][n],c["surface"]); ok=r>=3.0; fails+=not ok
        rows.append(f"| {mode} | ikon jenis file {n} (non-teks) | {t['kind'][mode][n]} on surface | {r:.2f} | 3.0 | {'lulus' if ok else 'GAGAL'} |")
rep=["# Laporan kontras WCAG", "", f"Dibuat otomatis dari tokens.json. Gagal: **{fails}**.", "",
     "| Mode | Pasangan | Warna | Rasio | Minimum | Hasil |","|---|---|---|---|---|---|"]+rows
(here/"contrast.md").write_text("\n".join(rep)+"\n")
print("fails", fails)
for r in rows:
    if "GAGAL" in r: print(r)
