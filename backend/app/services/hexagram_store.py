import json
from functools import lru_cache
from pathlib import Path
from typing import Any, Dict, Optional

from app.config import settings

_REPO_ROOT = Path(__file__).resolve().parents[3]
_DEFAULT_HEXAGRAMS_PATH = _REPO_ROOT / "ios/Yizhidao/Resources/Hexagrams.json"
_DOCKER_HEXAGRAMS_PATH = Path(__file__).resolve().parents[2] / "data" / "Hexagrams.json"


def _resolve_hexagrams_path() -> Path:
    if settings.hexagrams_path:
        return Path(settings.hexagrams_path)
    if _DOCKER_HEXAGRAMS_PATH.exists():
        return _DOCKER_HEXAGRAMS_PATH
    return _DEFAULT_HEXAGRAMS_PATH


@lru_cache(maxsize=1)
def _load_hexagrams() -> Dict[int, Dict[str, Any]]:
    path = _resolve_hexagrams_path()
    if not path.exists():
        print(f"[hexagram] missing file: {path}")
        return {}
    raw = json.loads(path.read_text(encoding="utf-8"))
    items = raw["hexagrams"] if isinstance(raw, dict) else raw
    return {item["number"]: item for item in items}


def get_hexagram(number: int) -> Optional[Dict[str, Any]]:
    return _load_hexagrams().get(number)


def hexagram_catalog() -> list:
    return [
        {
            "number": number,
            "name": item.get("name") or "",
            "symbol": item.get("symbol") or "",
            "title": item.get("title") or "",
        }
        for number, item in sorted(_load_hexagrams().items())
    ]


def hexagram_reading(number: int) -> Optional[Dict[str, Any]]:
    item = get_hexagram(number)
    if not item:
        return None
    return {
        "number": item.get("number"),
        "name": item.get("name") or "",
        "symbol": item.get("symbol") or "",
        "title": item.get("title") or "",
        "guaci": item.get("guaci") or "",
        "tuanci": item.get("tuanci") or "",
        "daxiang": item.get("daxiang") or "",
        "yaoci": item.get("yaoci") or [],
        "xiaoxiang": item.get("xiaoxiang") or [],
        "yong": item.get("yong"),
        "wenyan": item.get("wenyan") or [],
    }


def hexagram_name_to_number(name: str) -> Optional[int]:
    raw = (name or "").strip()
    if not raw:
        return None
    stripped = raw.rstrip("卦")
    for number, item in _load_hexagrams().items():
        label = str(item.get("name") or "")
        if raw == label or raw == label + "卦" or stripped == label:
            return number
    return None


def hexagram_catalog_status() -> Dict[str, Any]:
    path = _resolve_hexagrams_path()
    items = _load_hexagrams()
    return {
        "loaded": path.exists() and bool(items),
        "count": len(items),
        "path": str(path),
        "exists": path.exists(),
    }
