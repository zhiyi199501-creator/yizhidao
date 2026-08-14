from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.db import get_db
from app.schemas import (
    HealthResponse,
    SMSLoginRequest,
    SMSLoginResponse,
    SMSSendRequest,
    SMSSendResponse,
    UserOut,
)
from app.services.auth import login_with_sms, send_sms_code

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
