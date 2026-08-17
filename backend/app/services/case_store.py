import hashlib
import json
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

from app.config import settings

_REPO_ROOT = Path(__file__).resolve().parents[3]
_DEFAULT_CASES_PATH = _REPO_ROOT / "Yizhidao/Resources/cases.json"
_BUNDLED_CASES_PATH = Path(__file__).resolve().parents[1] / "data" / "cases.json"
_VOLUME_CASES_PATH = Path("/app/data/cases.json")

_POSITION_ORDER = ["初爻", "二爻", "三爻", "四爻", "五爻", "上爻"]

# mtime, size, version, items
_cache: Optional[Tuple[float, int, str, List[Dict[str, Any]]]] = None


def _resolve_cases_path() -> Path:
    if settings.cases_path:
        return Path(settings.cases_path)
    if _VOLUME_CASES_PATH.exists():
        return _VOLUME_CASES_PATH
    if _BUNDLED_CASES_PATH.exists():
        return _BUNDLED_CASES_PATH
    return _DEFAULT_CASES_PATH


def _load_cases() -> Tuple[str, List[Dict[str, Any]]]:
    """读盘；文件未变则走缓存，便于热更新 cases.json 后不必重启。"""
    global _cache
    path = _resolve_cases_path()
    if not path.exists():
        print(f"[cases] missing file: {path}")
        return "", []
    stat = path.stat()
    if _cache and _cache[0] == stat.st_mtime and _cache[1] == stat.st_size:
        return _cache[2], _cache[3]
    raw_bytes = path.read_bytes()
    parsed = json.loads(raw_bytes.decode("utf-8"))
    items = parsed if isinstance(parsed, list) else []
    version = hashlib.sha256(raw_bytes).hexdigest()[:16]
    _cache = (stat.st_mtime, stat.st_size, version, items)
    return version, items


def all_cases() -> List[Dict[str, Any]]:
    return _load_cases()[1]


def cases_version() -> str:
    return _load_cases()[0]


def _position_rank(position: str) -> int:
    for index, name in enumerate(_POSITION_ORDER, start=1):
        if name in position:
            return index
    return 99


def cases_for_hexagram(number: int) -> List[Dict[str, Any]]:
    items = [item for item in all_cases() if item.get("number") == number]
    return sorted(
        items,
        key=lambda item: (_position_rank(str(item.get("position") or "")), str(item.get("file") or "")),
    )
