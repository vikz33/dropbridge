"""Aplikasi FastAPI: gabungkan router dan sajikan halaman web."""
import ipaddress

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from fastapi.staticfiles import StaticFiles

from . import auth, discovery
from .config import settings
from .netinfo import lan_urls
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
        "max_upload_mb": settings.max_upload_mb,
        "thumbnails": thumbs.AVAILABLE,
    }


app.include_router(auth.router)
app.include_router(folders.router)
app.include_router(files.router)
app.include_router(clipboard.router)

# Harus paling akhir agar tidak menimpa rute /api.
app.mount("/", StaticFiles(directory=settings.web_dir, html=True), name="web")
