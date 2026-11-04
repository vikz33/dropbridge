"""Aplikasi FastAPI: gabungkan router dan sajikan halaman web."""
import io
import ipaddress

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse, Response
from fastapi.staticfiles import StaticFiles

from . import auth, discovery, mdns
from .config import settings
from .netinfo import lan_urls, local_url
from .routes import clipboard, files, folders
from .services import thumbs
from .services.storage import StorageError

app = FastAPI(
    title="DropBridge",
    docs_url="/docs" if settings.enable_docs else None,
    redoc_url=None,
    openapi_url="/openapi.json" if settings.enable_docs else None,
)

thumbs.cleanup()
discovery.start(settings.port)
mdns.start(settings.port, settings.mdns_name)


def _is_local(host: str) -> bool:
    try:
        ip = ipaddress.ip_address(host)
    except ValueError:
        return False
    if ip.version == 6 and ip.ipv4_mapped:
        ip = ip.ipv4_mapped
    return ip.is_private or ip.is_loopback or ip.is_link_local


@app.exception_handler(StorageError)
async def storage_error_handler(request: Request, exc: StorageError):
    return JSONResponse({"detail": exc.message, **exc.extra}, status_code=exc.status)


@app.middleware("http")
async def guard(request: Request, call_next):
    # Catatan: jika nanti memakai tunnel, koneksi tampak berasal dari 127.0.0.1.
    # Saat itu ganti pemeriksaan ini dengan aturan khusus tunnel.
    client = request.client.host if request.client else ""
    if settings.lan_only and not _is_local(client):
        return JSONResponse({"detail": "Hanya bisa diakses dari jaringan lokal."}, status_code=403)

    if request.method == "POST" and request.url.path in ("/api/files/upload", "/api/files/upload-raw"):
        try:
            length = int(request.headers.get("content-length", "0"))
        except ValueError:
            length = 0
        if length > settings.max_upload_bytes + 1024 * 1024:
            return JSONResponse(
                {"detail": f"File terlalu besar (maks {settings.max_upload_mb} MB)."}, status_code=413
            )

    response = await call_next(request)
    response.headers["X-Content-Type-Options"] = "nosniff"
    if not request.url.path.startswith("/api/"):
        response.headers["Cache-Control"] = "no-cache"
    return response


@app.get("/api/info")
def info():
    return {
        "name": "DropBridge",
        "urls": lan_urls(settings.port),
        "local_name": local_url(),
        "max_upload_mb": settings.max_upload_mb,
        "photo_max_kb": settings.photo_max_kb,
        "thumbnails": thumbs.AVAILABLE,
    }


def _from_this_pc(request: Request) -> bool:
    """True hanya untuk akses langsung dari PC ini (bukan lewat tunnel/proxy)."""
    client = request.client.host if request.client else ""
    if request.headers.get("x-forwarded-for") or request.headers.get("forwarded"):
        return False
    try:
        ip = ipaddress.ip_address(client)
    except ValueError:
        return False
    if ip.version == 6 and ip.ipv4_mapped:
        ip = ip.ipv4_mapped
    return ip.is_loopback


@app.get("/api/qr")
def qr_code(request: Request, i: int = 0, pin: int = 0, name: int = 0):
    """Kode QR (SVG) berisi alamat PC ini. Dari PC sendiri, bisa sekalian memuat PIN agar HP langsung masuk."""
    try:
        import segno
    except ImportError:
        return JSONResponse({"detail": "Paket segno belum terpasang."}, status_code=404)
    urls = lan_urls(settings.port)
    if name and local_url():
        urls = [local_url()]  # QR tetap untuk dicetak: tidak berubah walau IP berganti
        pin = 0
    if not urls:
        return JSONResponse({"detail": "Alamat PC tidak terdeteksi."}, status_code=404)
    text = urls[i] if 0 <= i < len(urls) else urls[0]
    if pin and _from_this_pc(request):
        text = text.rstrip("/") + "/?pin=" + settings.pin
    buf = io.BytesIO()
    segno.make(text, error="m").save(buf, kind="svg", scale=6, border=2, xmldecl=False, svgns=True, dark="#0b1f1c", light="#ffffff")
    return Response(buf.getvalue(), media_type="image/svg+xml", headers={"Cache-Control": "no-store"})


app.include_router(auth.router)
app.include_router(folders.router)
app.include_router(files.router)
app.include_router(clipboard.router)

# Harus paling akhir agar tidak menimpa rute /api.
app.mount("/", StaticFiles(directory=settings.web_dir, html=True), name="web")
