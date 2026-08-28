from __future__ import annotations

from datetime import datetime, timezone
from typing import Any, Dict, List, Optional

from sqlalchemy import delete, func, or_, select
from sqlalchemy.orm import Session

from app.errors import AppError
from app.models import ContentCase
from app.services.case_store import all_cases, cases_version, resolve_cases_path, write_published_cases
from app.services.hexagram_store import hexagram_name_to_number

_seeded_urls: set[str] = set()


def _now() -> datetime:
    return datetime.now(timezone.utc)


def serialize(row: ContentCase) -> Dict[str, Any]:
    return {
        "id": row.id,
        "file": row.file,
        "hexagram": row.hexagram,
        "position": row.position,
        "background": row.background or "",
        "question": row.question or "",
        "casting": row.casting or "",
        "explanation": row.explanation or "",
        "verification": row.verification or "",
        "number": row.number,
        "updatedAt": row.updated_at.isoformat() if row.updated_at else None,
    }


def to_published_item(row: ContentCase) -> Dict[str, Any]:
    return {
        "file": row.file,
        "hexagram": row.hexagram,
        "position": row.position,
        "background": row.background or "",
        "question": row.question or "",
        "casting": row.casting or "",
        "explanation": row.explanation or "",
        "verification": row.verification or "",
        "number": row.number,
    }


def _apply_number(payload: Dict[str, Any]) -> int:
    number = hexagram_name_to_number(str(payload.get("hexagram") or ""))
    if number is None:
        raise AppError("卦名无法识别", code=4001, status_code=400)
    return number


def _required(payload: Dict[str, Any]) -> None:
    file = str(payload.get("file") or "").strip()
    hexagram = str(payload.get("hexagram") or "").strip()
    position = str(payload.get("position") or "").strip()
    if not file or not hexagram or not position:
        raise AppError("编号、卦名、爻位不能为空", code=4001, status_code=400)


def ensure_seeded(db: Session) -> None:
    url = str(db.get_bind().url)
    if url in _seeded_urls:
        return
    count = int(db.scalar(select(func.count(ContentCase.id))) or 0)
    if count:
        _seeded_urls.add(url)
        return
    for item in all_cases():
        db.add(
            ContentCase(
                file=str(item.get("file") or "").strip(),
                hexagram=str(item.get("hexagram") or "").strip(),
                position=str(item.get("position") or "卦辞").strip() or "卦辞",
                background=str(item.get("background") or ""),
                question=str(item.get("question") or ""),
                casting=str(item.get("casting") or ""),
                explanation=str(item.get("explanation") or ""),
                verification=str(item.get("verification") or ""),
                number=int(item.get("number") or 0),
                updated_at=_now(),
            )
        )
    db.commit()
    _seeded_urls.add(url)


def list_cases(
    db: Session, q: str = "", number: Optional[int] = None, page: int = 1, page_size: int = 30
) -> Dict[str, Any]:
    ensure_seeded(db)
    page = max(1, page)
    page_size = min(100, max(1, page_size))
    stmt = select(ContentCase)
    needle = (q or "").strip()
    if needle:
        like = f"%{needle}%"
        stmt = stmt.where(
            or_(
                ContentCase.file.like(like),
                ContentCase.hexagram.like(like),
                ContentCase.question.like(like),
                ContentCase.position.like(like),
            )
        )
    if number:
        stmt = stmt.where(ContentCase.number == number)
    total = int(db.scalar(select(func.count()).select_from(stmt.subquery())) or 0)
    rows = db.scalars(
        stmt.order_by(ContentCase.number.asc(), ContentCase.file.asc())
        .offset((page - 1) * page_size)
        .limit(page_size)
    ).all()
    return {
        "ok": True,
        "total": total,
        "page": page,
        "pageSize": page_size,
        "cases": [serialize(row) for row in rows],
    }


def get_case(db: Session, case_id: int) -> Dict[str, Any]:
    ensure_seeded(db)
    row = db.get(ContentCase, case_id)
    if not row:
        raise AppError("案例不存在", code=4001, status_code=404)
    return {"ok": True, "case": serialize(row)}


def _file_taken(db: Session, file: str, skip_id: Optional[int] = None) -> bool:
    stmt = select(ContentCase.id).where(ContentCase.file == file)
    if skip_id:
        stmt = stmt.where(ContentCase.id != skip_id)
    return db.scalar(stmt) is not None


def create_case(db: Session, payload: Dict[str, Any]) -> Dict[str, Any]:
    ensure_seeded(db)
    _required(payload)
    file = str(payload["file"]).strip()
    if _file_taken(db, file):
        raise AppError("编号已存在", code=4001, status_code=400)
    row = ContentCase(
        file=file,
        hexagram=str(payload["hexagram"]).strip(),
        position=str(payload.get("position") or "卦辞").strip() or "卦辞",
        background=str(payload.get("background") or ""),
        question=str(payload.get("question") or ""),
        casting=str(payload.get("casting") or ""),
        explanation=str(payload.get("explanation") or ""),
        verification=str(payload.get("verification") or ""),
        number=_apply_number(payload),
        updated_at=_now(),
    )
    db.add(row)
    db.commit()
    db.refresh(row)
    return {"ok": True, "case": serialize(row)}


def update_case(db: Session, case_id: int, payload: Dict[str, Any]) -> Dict[str, Any]:
    ensure_seeded(db)
    row = db.get(ContentCase, case_id)
    if not row:
        raise AppError("案例不存在", code=4001, status_code=404)
    _required(payload)
    file = str(payload["file"]).strip()
    if _file_taken(db, file, skip_id=case_id):
        raise AppError("编号已存在", code=4001, status_code=400)
    row.file = file
    row.hexagram = str(payload["hexagram"]).strip()
    row.position = str(payload.get("position") or "卦辞").strip() or "卦辞"
    row.background = str(payload.get("background") or "")
    row.question = str(payload.get("question") or "")
    row.casting = str(payload.get("casting") or "")
    row.explanation = str(payload.get("explanation") or "")
    row.verification = str(payload.get("verification") or "")
    row.number = _apply_number(payload)
    row.updated_at = _now()
    db.commit()
    db.refresh(row)
    return {"ok": True, "case": serialize(row)}


def delete_case(db: Session, case_id: int) -> Dict[str, Any]:
    row = db.get(ContentCase, case_id)
    if not row:
        raise AppError("案例不存在", code=4001, status_code=404)
    db.delete(row)
    db.commit()
    return {"ok": True}


def replace_all(db: Session, items: List[Dict[str, Any]]) -> Dict[str, Any]:
    if not items:
        raise AppError("导入内容为空", code=4001, status_code=400)
    files = [str(item.get("file") or "").strip() for item in items]
    if len(files) != len(set(files)):
        raise AppError("导入编号有重复", code=4001, status_code=400)
    for item in items:
        _required(item)
        resolved = hexagram_name_to_number(str(item.get("hexagram") or ""))
        item["number"] = resolved if resolved is not None else int(item.get("number") or 0)
    db.execute(delete(ContentCase))
    for item in items:
        db.add(
            ContentCase(
                file=str(item["file"]).strip(),
                hexagram=str(item["hexagram"]).strip(),
                position=str(item.get("position") or "卦辞").strip() or "卦辞",
                background=str(item.get("background") or ""),
                question=str(item.get("question") or ""),
                casting=str(item.get("casting") or ""),
                explanation=str(item.get("explanation") or ""),
                verification=str(item.get("verification") or ""),
                number=int(item["number"]),
                updated_at=_now(),
            )
        )
    db.commit()
    _seeded_urls.add(str(db.get_bind().url))
    return {"ok": True, "total": len(items)}


def status(db: Session) -> Dict[str, Any]:
    ensure_seeded(db)
    draft = int(db.scalar(select(func.count(ContentCase.id))) or 0)
    published = all_cases()
    return {
        "ok": True,
        "draftCount": draft,
        "publishedCount": len(published),
        "publishedVersion": cases_version(),
        "publishedPath": str(resolve_cases_path()),
    }


def publish(db: Session) -> Dict[str, Any]:
    ensure_seeded(db)
    rows = db.scalars(select(ContentCase).order_by(ContentCase.number.asc(), ContentCase.file.asc())).all()
    if not rows:
        raise AppError("工作副本为空，无法发布", code=4001, status_code=400)
    problems = []
    seen = set()
    items = []
    for row in rows:
        if not row.file or not row.hexagram or not row.position:
            problems.append(f"缺必填：{row.file or row.id}")
            continue
        if row.file in seen:
            problems.append(f"编号重复：{row.file}")
            continue
        if not row.number:
            problems.append(f"卦名无法识别：{row.hexagram}（{row.file}）")
            continue
        seen.add(row.file)
        items.append(to_published_item(row))
    if problems:
        raise AppError("无法发布：" + "；".join(problems[:8]), code=4001, status_code=400)
    version, path = write_published_cases(items)
    return {
        "ok": True,
        "version": version,
        "count": len(items),
        "path": str(path),
    }


def export_items(db: Session) -> List[Dict[str, Any]]:
    ensure_seeded(db)
    rows = db.scalars(select(ContentCase).order_by(ContentCase.number.asc(), ContentCase.file.asc())).all()
    return [to_published_item(row) for row in rows]
