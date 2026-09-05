from typing import Optional

from fastapi import APIRouter, Cookie, Depends, Query, Response
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session

from app.db import get_db
from app.errors import AppError
from app.services.admin_auth import (
    ADMIN_COOKIE,
    clear_admin_cookie,
    login_admin,
    set_admin_cookie,
    verify_admin_token,
)
from app.services import admin_stats
from app.services import feedback as feedback_store
from app.services.iap import admin_set_ai_unlimited, admin_set_unlock

router = APIRouter()


class AdminLoginBody(BaseModel):
    password: str = Field(min_length=1, max_length=200)


class AdminIapUnlockBody(BaseModel):
    unlocked: bool


class AdminAiUnlimitedBody(BaseModel):
    unlimited: bool


def require_admin(admin_session: Optional[str] = Cookie(default=None, alias=ADMIN_COOKIE)) -> str:
    if not admin_session:
        raise AppError("登录态无效", code=4003, status_code=401)
    verify_admin_token(admin_session)
    return "admin"


@router.post("/v1/admin/login")
def admin_login(body: AdminLoginBody, response: Response) -> dict:
    token = login_admin(body.password)
    set_admin_cookie(response, token)
    return {"ok": True}


@router.post("/v1/admin/logout")
def admin_logout(response: Response) -> dict:
    clear_admin_cookie(response)
    return {"ok": True}


@router.get("/v1/admin/me")
def admin_me(_: str = Depends(require_admin)) -> dict:
    return {"ok": True, "role": "admin"}


@router.get("/v1/admin/overview")
def admin_overview(
    _: str = Depends(require_admin),
    db: Session = Depends(get_db),
) -> dict:
    return admin_stats.overview(db)


@router.get("/v1/admin/users")
def admin_users(
    q: str = "",
    page: int = Query(default=1, ge=1),
    pageSize: int = Query(default=20, ge=1, le=100),
    _: str = Depends(require_admin),
    db: Session = Depends(get_db),
) -> dict:
    return admin_stats.list_users(db, q, page, pageSize)


@router.get("/v1/admin/users/{user_id}")
def admin_user_detail(
    user_id: str,
    _: str = Depends(require_admin),
    db: Session = Depends(get_db),
) -> dict:
    return admin_stats.user_detail(db, user_id)


@router.post("/v1/admin/users/{user_id}/iap-unlock")
def admin_user_iap_unlock(
    user_id: str,
    body: AdminIapUnlockBody,
    _: str = Depends(require_admin),
    db: Session = Depends(get_db),
) -> dict:
    user = admin_set_unlock(db, user_id, body.unlocked)
    return {"ok": True, "user": admin_stats.serialize_user(user)}


@router.post("/v1/admin/users/{user_id}/ai-unlimited")
def admin_user_ai_unlimited(
    user_id: str,
    body: AdminAiUnlimitedBody,
    _: str = Depends(require_admin),
    db: Session = Depends(get_db),
) -> dict:
    user = admin_set_ai_unlimited(db, user_id, body.unlimited)
    return {"ok": True, "user": admin_stats.serialize_user(user)}


@router.get("/v1/admin/ai")
def admin_ai(
    range: str = Query(default="today", alias="range"),
    _: str = Depends(require_admin),
    db: Session = Depends(get_db),
) -> dict:
    return admin_stats.ai_usage(db, range)


@router.get("/v1/admin/ai/events")
def admin_ai_events(
    range: str = Query(default="today", alias="range"),
    page: int = Query(default=1, ge=1),
    pageSize: int = Query(default=50, ge=1, le=100),
    _: str = Depends(require_admin),
    db: Session = Depends(get_db),
) -> dict:
    return admin_stats.list_ai_events(db, range, page, pageSize)


@router.get("/v1/admin/system")
def admin_system(_: str = Depends(require_admin)) -> dict:
    return admin_stats.system_status()


@router.get("/v1/admin/feedback")
def admin_feedback(
    q: str = "",
    unreadOnly: bool = False,
    page: int = Query(default=1, ge=1),
    pageSize: int = Query(default=20, ge=1, le=100),
    _: str = Depends(require_admin),
    db: Session = Depends(get_db),
) -> dict:
    return feedback_store.list_feedback(db, q, unreadOnly, page, pageSize)


class FeedbackReadBody(BaseModel):
    read: bool = True


@router.patch("/v1/admin/feedback/{feedback_id}")
def admin_feedback_read(
    feedback_id: int,
    body: FeedbackReadBody,
    _: str = Depends(require_admin),
    db: Session = Depends(get_db),
) -> dict:
    return feedback_store.set_read(db, feedback_id, body.read)
