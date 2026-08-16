import json
from functools import lru_cache
from pathlib import Path
from typing import Any, Dict, Optional

from app.config import settings

_REPO_ROOT = Path(__file__).resolve().parents[3]
_DEFAULT_HEXAGRAMS_PATH = _REPO_ROOT / "Yizhidao/Resources/Hexagrams.json"
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
    return {item["number"]: item for item in raw}


def get_hexagram(number: int) -> Optional[Dict[str, Any]]:
    return _load_hexagrams().get(number)
