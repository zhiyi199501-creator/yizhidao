import hashlib
import json
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

from app.config import settings

_REPO_ROOT = Path(__file__).resolve().parents[3]
_DEFAULT_CASES_PATH = _REPO_ROOT / "ios/Yizhidao/Resources/cases.json"
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


_MAX_PROMPT_CASES = 3
_YAO_LABELS = {index: name for index, name in enumerate(_POSITION_ORDER, start=1)}


def case_matches_yao(position: str, yao: int) -> bool:
    label = _YAO_LABELS.get(yao)
    return bool(label) and label in (position or "")


def select_cases_by_yao(
    items: List[Dict[str, Any]],
    yaos: List[int],
    lead: Optional[int] = None,
    limit: int = _MAX_PROMPT_CASES,
) -> List[Dict[str, Any]]:
    """筛出命中任一爻位的案例；主爻优先，其次按初→上、文件名。"""
    wanted = [y for y in yaos if 1 <= y <= 6]
    if not wanted or limit <= 0:
        return []
    matched = [
        item
        for item in items
        if any(case_matches_yao(str(item.get("position") or ""), yao) for yao in wanted)
    ]

    def sort_key(item: Dict[str, Any]) -> Tuple[int, int, str]:
        position = str(item.get("position") or "")
        lead_hit = 0 if lead and case_matches_yao(position, lead) else 1
        return (lead_hit, _position_rank(position), str(item.get("file") or ""))

    matched.sort(key=sort_key)
    return matched[:limit]


def cases_for_ai_prompt(
    primary_number: int,
    resulting_number: Optional[int],
    moving: List[int],
    limit: int = _MAX_PROMPT_CASES,
) -> Tuple[str, List[Dict[str, Any]]]:
    """按解卦焦点挑讲习案例。主看卦辞时不附爻位案例，以免套错应事。

    返回 (说明句, 案例列表)。说明句已含「暂无 / 略去」。
    """
    moving = sorted(set(p for p in moving if 1 <= p <= 6))
    count = len(moving)

    if count in (0, 3):
        return ("讲习案例：本次主看本卦卦辞，爻位案例略去，以免套错应事。", [])
    if count == 6:
        return ("讲习案例：本次主看之卦卦辞，爻位案例略去，以免套错应事。", [])

    if count in (1, 2):
        lead = moving[-1] if count == 2 else moving[0]
        cases = select_cases_by_yao(
            cases_for_hexagram(primary_number), moving, lead=lead, limit=limit
        )
        labels = "、".join(_YAO_LABELS[p] for p in moving)
        if not cases:
            return (f"本卦{labels}讲习案例：暂无。", [])
        lead_note = f"，以{_YAO_LABELS[lead]}为主" if count == 2 else ""
        return (f"本卦{labels}讲习案例（{len(cases)}则{lead_note}；取象参照，不可照搬）：", cases)

    if not resulting_number:
        return ("之卦讲习案例：暂无。", [])

    statics = [p for p in range(1, 7) if p not in moving]
    lead = statics[0] if statics else 1
    target = statics if count == 4 else [lead]
    cases = select_cases_by_yao(
        cases_for_hexagram(resulting_number), target, lead=lead, limit=limit
    )
    labels = "、".join(_YAO_LABELS[p] for p in target)
    if not cases:
        return (f"之卦{labels}讲习案例：暂无。", [])
    lead_note = f"，以{_YAO_LABELS[lead]}为主" if count == 4 else ""
    return (f"之卦{labels}讲习案例（{len(cases)}则{lead_note}；取象参照，不可照搬）：", cases)
