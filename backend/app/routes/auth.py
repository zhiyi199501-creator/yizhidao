from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.db import get_db
from app.deps import get_current_user
from app.models import User
from app.schemas import (
    AppleLoginRequest,
    AuthLoginResponse,
    EmailLoginRequest,
    EmailSendRequest,
    EmailSendResponse,
    GoogleLoginRequest,
    HealthResponse,
    MeResponse,
    OkResponse,
    SMSLoginRequest,
    SMSLoginResponse,
    SMSSendRequest,
    SMSSendResponse,
    UserOut,
)
from app.services.auth import (
    delete_user_account,
    login_with_apple,
    login_with_email,
    login_with_google,
    login_with_sms,
    send_email_code,
    send_sms_code,
    user_to_out,
)

router = APIRouter()


def _login_response(user: User, token: str) -> AuthLoginResponse:
    return AuthLoginResponse(accessToken=token, user=UserOut(**user_to_out(user)))


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
    return SMSLoginResponse(accessToken=token, user=UserOut(**user_to_out(user)))


@router.post("/v1/auth/email/send", response_model=EmailSendResponse)
def email_send(body: EmailSendRequest, db: Session = Depends(get_db)) -> EmailSendResponse:
    cooldown = send_email_code(db, body.email)
    return EmailSendResponse(cooldownSec=cooldown)


@router.post("/v1/auth/email/login", response_model=AuthLoginResponse)
def email_login(body: EmailLoginRequest, db: Session = Depends(get_db)) -> AuthLoginResponse:
    user, token = login_with_email(db, body.email, body.code)
    return _login_response(user, token)


@router.post("/v1/auth/apple", response_model=AuthLoginResponse)
def apple_login(body: AppleLoginRequest, db: Session = Depends(get_db)) -> AuthLoginResponse:
    user, token = login_with_apple(db, body.identityToken, body.fullName)
    return _login_response(user, token)


@router.post("/v1/auth/google", response_model=AuthLoginResponse)
def google_login(body: GoogleLoginRequest, db: Session = Depends(get_db)) -> AuthLoginResponse:
    user, token = login_with_google(db, body.idToken)
    return _login_response(user, token)


@router.get("/v1/me", response_model=MeResponse)
def me(user: User = Depends(get_current_user)) -> MeResponse:
    return MeResponse(user=UserOut(**user_to_out(user)))


@router.delete("/v1/me", response_model=OkResponse)
def delete_me(
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> OkResponse:
    delete_user_account(db, user)
    return OkResponse()
