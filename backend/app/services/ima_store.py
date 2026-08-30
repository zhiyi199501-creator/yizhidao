import json
from functools import lru_cache
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

from app.config import settings
from app.errors import AppError
from app.services.ima_format import prompt_text, stripped

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


def invalidate_ima_cache() -> None:
    _load.cache_clear()


def resolve_ima_path() -> Path:
    return _resolve_path()


def resolve_ima_write_path() -> Path:
    if settings.ima_explanations_path:
        return Path(settings.ima_explanations_path)
    if Path("/app/data").is_dir():
        return _VOLUME_PATH
    return _resolve_path()


def ima_hexagram_index() -> List[Dict[str, Any]]:
    _, entries = _load()
    counts: Dict[int, int] = {}
    for entry_id in entries:
        if len(entry_id) < 3 or not entry_id[:2].isdigit():
            continue
        number = int(entry_id[:2])
        counts[number] = counts.get(number, 0) + 1
    from app.services.hexagram_store import hexagram_catalog

    catalog = {item["number"]: item for item in hexagram_catalog()}
    rows = []
    for number in range(1, 65):
        info = catalog.get(number) or {"name": "", "symbol": "", "title": ""}
        rows.append(
            {
                "number": number,
                "name": info.get("name") or "",
                "symbol": info.get("symbol") or "",
                "title": info.get("title") or "",
                "entryCount": counts.get(number, 0),
            }
        )
    return rows


def entries_for_hexagram(number: int) -> List[Dict[str, Any]]:
    prefix = f"{int(number):02d}-"
    _, entries = _load()
    order = (
        ["guaci", "tuanci", "daxiang"]
        + [f"yao-{index}" for index in range(6)]
        + ["yong", "wenyan"]
    )
    rank = {key: index for index, key in enumerate(order)}
    items = []
    for entry_id, entry in entries.items():
        if not entry_id.startswith(prefix):
            continue
        suffix = entry_id[len(prefix) :]
        items.append(
            {
                "id": entry_id,
                "title": entry.get("title") or "",
                "scripture": entry.get("scripture") or "",
                "answer": stripped(entry.get("answer") or ""),
                "_rank": rank.get(suffix, 99),
            }
        )
    items.sort(key=lambda item: (item["_rank"], item["id"]))
    for item in items:
        item.pop("_rank", None)
    return items


def save_entry_answer(entry_id: str, answer: str) -> Dict[str, Any]:
    read_path = _resolve_path()
    if not read_path.exists():
        raise AppError("黄庭讲解文件不存在", code=4001, status_code=404)
    raw = json.loads(read_path.read_text(encoding="utf-8"))
    entries = raw.get("entries") if isinstance(raw, dict) else None
    if not isinstance(entries, dict) or entry_id not in entries:
        raise AppError("找不到该条讲解", code=4001, status_code=404)
    entries[entry_id]["answer"] = stripped(answer)
    path = resolve_ima_write_path()
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(path.name + ".tmp")
    tmp.write_text(json.dumps(raw, ensure_ascii=False, indent=2), encoding="utf-8")
    tmp.replace(path)
    invalidate_ima_cache()
    entry = entries[entry_id]
    return {
        "id": entry_id,
        "title": entry.get("title") or "",
        "scripture": entry.get("scripture") or "",
        "answer": entry.get("answer") or "",
        "path": str(path),
    }


def formatted_answer(entry: Dict[str, Any]) -> str:
    return prompt_text(str(entry.get("answer") or ""))


def ima_catalog_status() -> Dict[str, Any]:
    path = _resolve_path()
    source, entries = _load()
    return {
        "loaded": path.exists() and bool(entries),
        "count": len(entries),
        "source": source,
        "path": str(path),
        "exists": path.exists(),
    }
