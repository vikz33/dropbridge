# -*- mode: python ; coding: utf-8 -*-
# Resep PyInstaller untuk DropBridge.exe (dipakai build_exe.bat). Hasil: dist\DropBridge\DropBridge.exe
import os
from PyInstaller.utils.hooks import collect_submodules

datas = [("web", "web")]
apk = os.environ.get("DB_APK", "")
if apk and os.path.isfile(apk):
    datas.append((apk, "apk"))  # APK Android ikut terpasang, HP bisa mengunduhnya dari laptop

hiddenimports = (
    collect_submodules("uvicorn")
    + collect_submodules("app")
    + ["pystray._win32", "python_multipart", "multipart", "segno", "PIL.ImageDraw"]
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
    icon="build/dropbridge.ico",
    console=False,
    upx=False,
)
coll = COLLECT(exe, a.binaries, a.datas, name="DropBridge", upx=False)
