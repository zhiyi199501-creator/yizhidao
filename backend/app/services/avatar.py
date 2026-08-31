from pathlib import Path
from typing import Optional

from app.config import settings

MAX_AVATAR_BYTES = 512 * 1024
_JPEG_MAGIC = b"\xff\xd8\xff"


def avatars_dir() -> Path:
    if settings.avatars_dir.strip():
        return Path(settings.avatars_dir.strip())
    db_path = settings.database_url.removeprefix("sqlite:///")
    base = Path(db_path).resolve().parent
    return base / "avatars"


def avatar_path(user_id: str) -> Path:
    return avatars_dir() / f"{user_id}.jpg"


def save_avatar(user_id: str, data: bytes) -> None:
    if not data.startswith(_JPEG_MAGIC):
        raise ValueError("invalid_image")
    if len(data) > MAX_AVATAR_BYTES:
        raise ValueError("too_large")
    target = avatar_path(user_id)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(data)


def load_avatar(user_id: str) -> Optional[bytes]:
    path = avatar_path(user_id)
    if not path.is_file():
        return None
    return path.read_bytes()


def delete_avatar(user_id: str) -> None:
    path = avatar_path(user_id)
    if path.is_file():
        path.unlink()
