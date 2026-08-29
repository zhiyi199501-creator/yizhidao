from __future__ import annotations

from datetime import datetime, timezone
from typing import Any, Dict, List, Optional

from sqlalchemy import and_, func, or_, select
from sqlalchemy.orm import Session

from app.errors import AppError
from app.models import User, UserFeedback
from app.services.admin_stats import iso
from app.services.auth import mask_email, mask_phone

MIN_BODY = 5
MAX_BODY = 2000
PLATFORMS = {"ios", "android"}


def normalize_body(raw: str) -> str:
    body = (raw or "").strip()
    if len(body) < MIN_BODY:
        raise AppError("请至少写 5 个字", code=4001, status_code=400)
    if len(body) > MAX_BODY:
        raise AppError("反馈过长", code=4001, status_code=400)
    return body


def normalize_platform(raw: Optional[str]) -> str:
    value = (raw or "").strip().lower()
    return value if value in PLATFORMS else ""


def serialize(row: UserFeedback, user: Optional[User] = None) -> Dict[str, Any]:
    return {
        "id": row.id,
        "createdAt": iso(row.created_at),
        "userId": row.user_id,
        "nickname": user.nickname if user else None,
        "email": mask_email(user.email) if user and user.email else None,
        "phone": mask_phone(user.phone) if user and user.phone else None,
        "body": row.body,
        "contact": row.contact or "",
        "platform": row.platform or "",
        "appVersion": row.app_version or "",
        "readAt": iso(row.read_at),
    }


def create_feedback(
    db: Session,
    *,
    body: str,
    contact: str = "",
    platform: str = "",
    app_version: str = "",
    user: Optional[User] = None,
) -> UserFeedback:
    row = UserFeedback(
        user_id=user.id if user else None,
        body=normalize_body(body),
        contact=(contact or "").strip()[:120],
        platform=normalize_platform(platform),
        app_version=(app_version or "").strip()[:32],
    )
    db.add(row)
    db.commit()
    db.refresh(row)
    return row


def counts(db: Session) -> Dict[str, int]:
    total = int(db.scalar(select(func.count(UserFeedback.id))) or 0)
    unread = int(
        db.scalar(select(func.count(UserFeedback.id)).where(UserFeedback.read_at.is_(None))) or 0
    )
    return {"total": total, "unread": unread}


def list_feedback(
    db: Session,
    q: str = "",
    unread_only: bool = False,
    page: int = 1,
    page_size: int = 20,
) -> Dict[str, Any]:
    filters: List[Any] = []
    if unread_only:
        filters.append(UserFeedback.read_at.is_(None))
    needle = (q or "").strip()
    if needle:
        like = f"%{needle}%"
        matched_users = db.scalars(
            select(User.id).where(
                or_(
                    User.id.like(like),
                    User.nickname.like(like),
                    User.email.like(like),
                    User.phone.like(like),
                )
            )
        ).all()
        clauses = [
            UserFeedback.body.like(like),
            UserFeedback.contact.like(like),
            UserFeedback.user_id.like(like),
        ]
        if matched_users:
            clauses.append(UserFeedback.user_id.in_(matched_users))
        filters.append(or_(*clauses))
    where = and_(*filters) if filters else True
    total = int(db.scalar(select(func.count(UserFeedback.id)).where(where)) or 0)
    rows = db.scalars(
        select(UserFeedback)
        .where(where)
        .order_by(UserFeedback.created_at.desc())
        .offset((page - 1) * page_size)
        .limit(page_size)
    ).all()
    user_ids = [row.user_id for row in rows if row.user_id]
    users = {}
    if user_ids:
        users = {user.id: user for user in db.scalars(select(User).where(User.id.in_(user_ids))).all()}
    return {
        "ok": True,
        "total": total,
        "unread": counts(db)["unread"],
        "page": page,
        "pageSize": page_size,
        "items": [serialize(row, users.get(row.user_id) if row.user_id else None) for row in rows],
    }


def set_read(db: Session, feedback_id: int, read: bool) -> Dict[str, Any]:
    row = db.scalar(select(UserFeedback).where(UserFeedback.id == feedback_id))
    if not row:
        raise AppError("反馈不存在", code=4001, status_code=404)
    row.read_at = datetime.now(timezone.utc) if read else None
    db.commit()
    db.refresh(row)
    user = None
    if row.user_id:
        user = db.scalar(select(User).where(User.id == row.user_id))
    return {"ok": True, "item": serialize(row, user)}
