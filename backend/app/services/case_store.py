import json
from functools import lru_cache
from pathlib import Path
from typing import Any, Dict, List

from app.config import settings

_REPO_ROOT = Path(__file__).resolve().parents[3]
_DEFAULT_CASES_PATH = _REPO_ROOT / "Yizhidao/Resources/cases.json"
_BUNDLED_CASES_PATH = Path(__file__).resolve().parents[1] / "data" / "cases.json"

_POSITION_ORDER = ["初爻", "二爻", "三爻", "四爻", "五爻", "上爻"]


def _resolve_cases_path() -> Path:
    if settings.cases_path:
        return Path(settings.cases_path)
    if _BUNDLED_CASES_PATH.exists():
        return _BUNDLED_CASES_PATH
    return _DEFAULT_CASES_PATH


@lru_cache(maxsize=1)
def _load_cases() -> List[Dict[str, Any]]:
    path = _resolve_cases_path()
    if not path.exists():
        print(f"[cases] missing file: {path}")
        return []
    raw = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(raw, list):
        return []
    return raw


def _position_rank(position: str) -> int:
    for index, name in enumerate(_POSITION_ORDER, start=1):
        if name in position:
            return index
    return 99


def cases_for_hexagram(number: int) -> List[Dict[str, Any]]:
    items = [item for item in _load_cases() if item.get("number") == number]
    return sorted(
        items,
        key=lambda item: (_position_rank(str(item.get("position") or "")), str(item.get("file") or "")),
    )
