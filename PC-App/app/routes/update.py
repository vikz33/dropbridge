"""Pembaruan DropBridge dari GitHub Releases. Hanya dari laptop/PC ini (bukan dari HP)."""
from fastapi import APIRouter, Depends, HTTPException

from .. import events
from ..config import FROZEN, settings
from ..updater import UpdateError, Updater
from ..version import VERSION
from .system import _pc_only

router = APIRouter(prefix="/api/update", dependencies=[Depends(_pc_only)])
updater = Updater(VERSION, settings.data_dir, frozen=FROZEN)


def start() -> None:
    """Dipanggil main.py: cek berkala + kabar ke jendela desktop saat ada versi baru (sekali per versi)."""
    if settings.update_check:
        updater.start(lambda ver: events.emit("update", ver))


@router.get("")
def status():
    return updater.status()


@router.post("/check")
def check():
    return updater.check()


@router.post("/install")
def install():
    try:
        updater.start_install()
    except UpdateError as e:
        raise HTTPException(status_code=400, detail=str(e))
    return updater.status()


@router.post("/cancel")
def cancel():
    updater.cancel()
    return updater.status()
