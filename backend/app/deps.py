from typing import Optional

from fastapi import Depends, Header

from app.errors import AppError
from app.services.token import decode_access_token


def get_bearer_token(authorization: Optional[str] = Header(default=None)) -> str:
    if not authorization or not authorization.startswith("Bearer "):
        raise AppError("登录态无效", code=4003, status_code=401)
    token = authorization[7:].strip()
    if not token:
        raise AppError("登录态无效", code=4003, status_code=401)
    return token


def get_current_user_id(token: str = Depends(get_bearer_token)) -> str:
    return decode_access_token(token)
