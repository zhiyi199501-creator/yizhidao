from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.db import get_db
from app.deps import get_current_user
from app.models import User
from app.schemas import (
    HealthResponse,
    MeResponse,
    OkResponse,
    SMSLoginRequest,
    SMSLoginResponse,
    SMSSendRequest,
    SMSSendResponse,
    UserOut,
)
from app.services.auth import delete_user_account, login_with_sms, send_sms_code

router = APIRouter()


@router.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    return HealthResponse()


@router.post("/v1/auth/sms/send", response_model=SMSSendResponse)
def sms_send(body: SMSSendRequest, db: Session = Depends(get_db)) -> SMSSendResponse:
    cooldown = send_sms_code(db, body.phone)
    return SMSSendResponse(cooldownSec=cooldown)


@router.post("/v1/auth/sms/login", response_model=SMSLoginResponse)
def sms_login(body: SMSLoginRequest, db: Session = Depends(get_db)) -> SMSLoginResponse:
    user, token = login_with_sms(db, body.phone, body.code)
    return SMSLoginResponse(
        accessToken=token,
        user=UserOut(id=user.id, nickname=user.nickname, phone=user.phone),
    )


@router.get("/v1/me", response_model=MeResponse)
def me(user: User = Depends(get_current_user)) -> MeResponse:
    return MeResponse(
        user=UserOut(id=user.id, nickname=user.nickname, phone=user.phone),
    )


@router.delete("/v1/me", response_model=OkResponse)
def delete_me(
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> OkResponse:
    delete_user_account(db, user)
    return OkResponse()
