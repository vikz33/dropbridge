# -*- mode: python ; coding: utf-8 -*-
# Resep PyInstaller untuk DropBridge.exe (dipakai build_exe.bat). Hasil: dist\DropBridge\DropBridge.exe
import os
from PyInstaller.utils.hooks import collect_submodules

datas = [(os.path.join("..", "Web"), "web")]  # tampilan dari folder Web di sebelah PC-App
apk = os.environ.get("DB_APK", "")
if apk and os.path.isfile(apk):
    datas.append((apk, "apk"))  # APK Android ikut terpasang, HP bisa mengunduhnya dari laptop

import sys
if sys.platform == "win32":
    platform_mods = ["pystray._win32"]
else:  # Linux: ikon panel lewat AppIndicator/GTK, jendela lewat WebKitGTK
    platform_mods = ["pystray._appindicator", "pystray._gtk", "pystray._xorg", "webview.platforms.gtk"]
hiddenimports = (
    collect_submodules("uvicorn")
    + collect_submodules("app")
    + collect_submodules("send2trash")
    + platform_mods
    + ["python_multipart", "multipart", "segno", "PIL.ImageDraw"]
)

a = Analysis(
    ["desktop.py"],
    pathex=["."],
    datas=datas,
    hiddenimports=hiddenimports,
    excludes=["tkinter", "pytest", "unittest"],
    noarchive=False,
)
pyz = PYZ(a.pure)
exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name="DropBridge",
    icon="build/dropbridge.ico" if sys.platform == "win32" else None,
    console=False,
    upx=False,
)
coll = COLLECT(exe, a.binaries, a.datas, name="DropBridge", upx=False)
