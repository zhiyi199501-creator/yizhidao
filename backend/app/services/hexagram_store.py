import json
from functools import lru_cache
from pathlib import Path
from typing import Any, Dict, Optional

_REPO_ROOT = Path(__file__).resolve().parents[3]
_HEXAGRAMS_PATH = _REPO_ROOT / "Yizhidao/Resources/Hexagrams.json"


@lru_cache(maxsize=1)
def _load_hexagrams() -> Dict[int, Dict[str, Any]]:
    if not _HEXAGRAMS_PATH.exists():
        return {}
    raw = json.loads(_HEXAGRAMS_PATH.read_text(encoding="utf-8"))
    return {item["number"]: item for item in raw}


def get_hexagram(number: int) -> Optional[Dict[str, Any]]:
    return _load_hexagrams().get(number)
