import json
from functools import lru_cache
from pathlib import Path
from typing import Any, Dict, Optional, Tuple

from app.config import settings
from app.services.ima_format import prompt_text

_REPO_ROOT = Path(__file__).resolve().parents[3]
_DEFAULT_PATH = _REPO_ROOT / "ios/Yizhidao/Resources/ImaExplanations.json"
_BUNDLED_PATH = Path(__file__).resolve().parents[1] / "data" / "ImaExplanations.json"
_VOLUME_PATH = Path("/app/data/ImaExplanations.json")


def _resolve_path() -> Path:
    if settings.ima_explanations_path:
        return Path(settings.ima_explanations_path)
    if _VOLUME_PATH.exists():
        return _VOLUME_PATH
    if _BUNDLED_PATH.exists():
        return _BUNDLED_PATH
    return _DEFAULT_PATH


@lru_cache(maxsize=1)
def _load() -> Tuple[str, Dict[str, Dict[str, Any]]]:
    path = _resolve_path()
    if not path.exists():
        print(f"[ima] missing file: {path}")
        return "", {}
    raw = json.loads(path.read_text(encoding="utf-8"))
    entries = raw.get("entries") if isinstance(raw, dict) else None
    if not isinstance(entries, dict):
        print(f"[ima] invalid file: {path}")
        return "", {}
    source = str(raw.get("source") or "")
    return source, entries


def get_entry(entry_id: str) -> Optional[Dict[str, Any]]:
    return _load()[1].get(entry_id)


def formatted_answer(entry: Dict[str, Any]) -> str:
    return prompt_text(str(entry.get("answer") or ""))
