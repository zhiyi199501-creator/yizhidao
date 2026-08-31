from fastapi import APIRouter, Depends, Request, Response
from sqlalchemy.orm import Session

from app.db import get_db
from app.deps import get_current_user
from app.errors import AppError
from app.models import User
from app.config import settings
from app.schemas import (
    AppleLoginRequest,
    AppVersionResponse,
    AuthLoginResponse,
    EmailLoginRequest,
    EmailSendRequest,
    EmailSendResponse,
    EmailBindRequest,
    GoogleLoginRequest,
    HealthResponse,
    MeResponse,
    OkResponse,
    ProfileUpdateRequest,
    SMSLoginRequest,
    SMSLoginResponse,
    SMSSendRequest,
    SMSSendResponse,
    UserOut,
)
from app.services.auth import (
    bind_user_email,
    clear_user_avatar,
    delete_user_account,
    login_with_apple,
    login_with_email,
    login_with_google,
    login_with_sms,
    send_bind_email_code,
    send_email_code,
    send_sms_code,
    set_user_avatar,
    update_user_profile,
    user_to_out,
)
from app.services.avatar import load_avatar

router = APIRouter()


def _login_response(user: User, token: str) -> AuthLoginResponse:
    return AuthLoginResponse(accessToken=token, user=UserOut(**user_to_out(user)))


@router.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    return HealthResponse()


IOS_STORE_URL = "https://apps.apple.com/app/id6804203617"
ANDROID_STORE_URL = "https://play.google.com/store/apps/details?id=com.yizhidao.app"


@router.get("/v1/app/version", response_model=AppVersionResponse)
def app_version() -> AppVersionResponse:
    return AppVersionResponse(
        ios=(settings.app_ios_latest_version or "").strip() or "1.0",
        android=(settings.app_android_latest_version or "").strip() or "0.1.1",
        iosStoreUrl=IOS_STORE_URL,
        androidStoreUrl=ANDROID_STORE_URL,
    )


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


@router.patch("/v1/me", response_model=MeResponse)
def patch_me(
    body: ProfileUpdateRequest,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> MeResponse:
    user = update_user_profile(db, user, body.nickname)
    return MeResponse(user=UserOut(**user_to_out(user)))


@router.post("/v1/me/email/send", response_model=EmailSendResponse)
def me_email_send(
    body: EmailSendRequest,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> EmailSendResponse:
    cooldown = send_bind_email_code(db, user, body.email)
    return EmailSendResponse(cooldownSec=cooldown)


@router.post("/v1/me/email/bind", response_model=MeResponse)
def me_email_bind(
    body: EmailBindRequest,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> MeResponse:
    user = bind_user_email(db, user, body.email, body.code)
    return MeResponse(user=UserOut(**user_to_out(user)))


@router.put("/v1/me/avatar", response_model=MeResponse)
async def put_me_avatar(
    request: Request,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> MeResponse:
    data = await request.body()
    if not data:
        raise AppError("请上传 JPEG 图片", code=4001, status_code=400)
    user = set_user_avatar(db, user, data)
    return MeResponse(user=UserOut(**user_to_out(user)))


@router.get("/v1/me/avatar")
def get_me_avatar(user: User = Depends(get_current_user)) -> Response:
    data = load_avatar(user.id)
    if data is None:
        raise AppError("暂无头像", code=4001, status_code=404)
    return Response(content=data, media_type="image/jpeg")


@router.delete("/v1/me/avatar", response_model=MeResponse)
def delete_me_avatar(
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> MeResponse:
    user = clear_user_avatar(db, user)
    return MeResponse(user=UserOut(**user_to_out(user)))


@router.delete("/v1/me", response_model=OkResponse)
def delete_me(
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> OkResponse:
    delete_user_account(db, user)
    return OkResponse()
