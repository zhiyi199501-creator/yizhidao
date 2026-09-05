"""StoreKit 2 买断验单。不存所问或解读正文。"""

from __future__ import annotations

import base64
import json
from datetime import datetime, timezone
from typing import Any, Dict, Optional

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.config import settings
from app.errors import AppError
from app.models import User

PRODUCT_UNLOCK = "com.yizhidao.app.ai.unlock"
MSG_BAD_RECEIPT = "购买凭证无效"
MSG_BOUND = "该购买已绑定其他账号"


def daily_limit_for_user(user: Optional[User]) -> int:
    """当日上限。返回 0 表示不限次（仍受间隔 / 并发约束）。"""
    if user is not None and bool(getattr(user, "ai_unlimited", False)):
        return 0
    if user is not None and user.iap_unlocked:
        return int(settings.ai_rate_daily_limit_unlock)
    return int(settings.ai_rate_daily_limit)


def is_paid_iap(user: User) -> bool:
    """真实商店买断：有 transaction id。后台手动解锁没有这笔记录。"""
    return bool((user.iap_transaction_id or "").strip())


def iap_source(user: User) -> str:
    if not user.iap_unlocked:
        return "none"
    if is_paid_iap(user):
        return "purchase"
    platform = (user.iap_platform or "").strip().lower()
    if platform == "android":
        return "android"
    return "admin"


def grant_android_complimentary_unlock(db: Session, user: User) -> User:
    """安卓尚未接 Play Billing：登录 / 拉 me 时赠送解锁（非付费，后台可取消）。"""
    if not settings.android_complimentary_unlock:
        return user
    if is_paid_iap(user):
        return user
    if user.iap_unlocked and (user.iap_platform or "").strip().lower() in ("android", "admin"):
        return user
    user.iap_unlocked = True
    user.iap_platform = "android"
    user.iap_product_id = settings.iap_product_id or PRODUCT_UNLOCK
    user.iap_transaction_id = None
    user.iap_original_transaction_id = None
    if user.iap_purchased_at is None:
        user.iap_purchased_at = datetime.now(timezone.utc)
    db.add(user)
    db.commit()
    db.refresh(user)
    return user


def normalize_client_platform(raw: Optional[str]) -> str:
    value = (raw or "").strip().lower()
    if value in ("android", "ios", "apple"):
        return "android" if value == "android" else "ios"
    return ""


def admin_set_unlock(db: Session, user_id: str, unlocked: bool) -> User:
    user = db.scalar(select(User).where(User.id == user_id))
    if user is None:
        raise AppError("用户不存在", code=4001, status_code=404)
    if unlocked:
        if is_paid_iap(user):
            # 已付费保持商店凭证，只确保已解锁标记为真
            user.iap_unlocked = True
            db.add(user)
            db.commit()
            db.refresh(user)
            return user
        user.iap_unlocked = True
        user.iap_platform = "admin"
        user.iap_product_id = settings.iap_product_id or PRODUCT_UNLOCK
        user.iap_transaction_id = None
        user.iap_original_transaction_id = None
        user.iap_purchased_at = datetime.now(timezone.utc)
        db.add(user)
        db.commit()
        db.refresh(user)
        return user

    if is_paid_iap(user):
        raise AppError("付费解锁不可取消", code=4001, status_code=400)
    user.iap_unlocked = False
    user.ai_unlimited = False
    user.iap_platform = None
    user.iap_product_id = None
    user.iap_transaction_id = None
    user.iap_original_transaction_id = None
    user.iap_purchased_at = None
    db.add(user)
    db.commit()
    db.refresh(user)
    return user


def admin_set_ai_unlimited(db: Session, user_id: str, unlimited: bool) -> User:
    """后台给指定用户不限次（自用／抽检）。开启时顺带解锁问答。"""
    user = db.scalar(select(User).where(User.id == user_id))
    if user is None:
        raise AppError("用户不存在", code=4001, status_code=404)
    if unlimited:
        user.ai_unlimited = True
        if not user.iap_unlocked:
            user.iap_unlocked = True
            if not is_paid_iap(user):
                user.iap_platform = "admin"
                user.iap_product_id = settings.iap_product_id or PRODUCT_UNLOCK
                user.iap_transaction_id = None
                user.iap_original_transaction_id = None
                user.iap_purchased_at = datetime.now(timezone.utc)
    else:
        user.ai_unlimited = False
    db.add(user)
    db.commit()
    db.refresh(user)
    return user


def effective_verify_mode() -> str:
    mode = (settings.iap_verify_mode or "mock").strip().lower()
    if mode == "mock" and settings.app_env == "production" and not settings.allow_insecure_mock_iap:
        return "apple"
    if mode in ("mock", "apple"):
        return mode
    return "apple"


def _b64url_decode(raw: str) -> bytes:
    padding = "=" * (-len(raw) % 4)
    return base64.urlsafe_b64decode(raw + padding)


def _decode_jws_payload_unverified(token: str) -> Dict[str, Any]:
    parts = token.split(".")
    if len(parts) != 3:
        raise AppError(MSG_BAD_RECEIPT, code=4001, status_code=400)
    try:
        return json.loads(_b64url_decode(parts[1]))
    except (ValueError, json.JSONDecodeError) as exc:
        raise AppError(MSG_BAD_RECEIPT, code=4001, status_code=400) from exc


def _verify_jws_es256(token: str) -> Dict[str, Any]:
    from cryptography import x509
    from cryptography.exceptions import InvalidSignature
    from cryptography.hazmat.primitives import hashes
    from cryptography.hazmat.primitives.asymmetric import ec
    from cryptography.hazmat.primitives.asymmetric.utils import encode_dss_signature
    from cryptography.x509.oid import NameOID

    parts = token.split(".")
    if len(parts) != 3:
        raise AppError(MSG_BAD_RECEIPT, code=4001, status_code=400)
    header_b64, payload_b64, sig_b64 = parts
    try:
        header = json.loads(_b64url_decode(header_b64))
        payload = json.loads(_b64url_decode(payload_b64))
        signature = _b64url_decode(sig_b64)
    except (ValueError, json.JSONDecodeError) as exc:
        raise AppError(MSG_BAD_RECEIPT, code=4001, status_code=400) from exc
    chain = header.get("x5c") or []
    if not chain:
        raise AppError(MSG_BAD_RECEIPT, code=4001, status_code=400)
    try:
        cert = x509.load_der_x509_certificate(base64.b64decode(chain[0]))
    except ValueError as exc:
        raise AppError(MSG_BAD_RECEIPT, code=4001, status_code=400) from exc
    orgs = cert.subject.get_attributes_for_oid(NameOID.ORGANIZATION_NAME)
    org = orgs[0].value if orgs else ""
    if "Apple" not in str(org):
        raise AppError(MSG_BAD_RECEIPT, code=4001, status_code=400)
    if len(signature) != 64:
        raise AppError(MSG_BAD_RECEIPT, code=4001, status_code=400)
    der_sig = encode_dss_signature(
        int.from_bytes(signature[:32], "big"),
        int.from_bytes(signature[32:], "big"),
    )
    try:
        cert.public_key().verify(
            der_sig,
            f"{header_b64}.{payload_b64}".encode("ascii"),
            ec.ECDSA(hashes.SHA256()),
        )
    except InvalidSignature as exc:
        raise AppError(MSG_BAD_RECEIPT, code=4001, status_code=400) from exc
    return payload


def _parse_signed_transaction(signed_transaction: str) -> Dict[str, Any]:
    raw = (signed_transaction or "").strip()
    if not raw:
        raise AppError(MSG_BAD_RECEIPT, code=4001, status_code=400)
    mode = effective_verify_mode()
    if raw.startswith("{") and mode == "mock":
        try:
            payload = json.loads(raw)
        except json.JSONDecodeError as exc:
            raise AppError(MSG_BAD_RECEIPT, code=4001, status_code=400) from exc
        if not isinstance(payload, dict):
            raise AppError(MSG_BAD_RECEIPT, code=4001, status_code=400)
        return payload
    if mode == "apple":
        return _verify_jws_es256(raw)
    return _decode_jws_payload_unverified(raw)


def _as_datetime(value: Any) -> datetime:
    if isinstance(value, datetime):
        return value if value.tzinfo else value.replace(tzinfo=timezone.utc)
    if isinstance(value, (int, float)):
        ts = float(value)
        if ts > 10_000_000_000:
            ts = ts / 1000.0
        return datetime.fromtimestamp(ts, tz=timezone.utc)
    return datetime.now(timezone.utc)


def verify_and_unlock(
    db: Session,
    user: User,
    *,
    platform: str,
    signed_transaction: str,
) -> User:
    if (platform or "").strip().lower() not in ("ios", "apple"):
        raise AppError("暂不支持该平台的购买", code=4001, status_code=400)
    payload = _parse_signed_transaction(signed_transaction)
    product_id = str(payload.get("productId") or "").strip()
    bundle_id = str(payload.get("bundleId") or "").strip()
    txn_type = str(payload.get("type") or "Non-Consumable").strip()
    transaction_id = str(payload.get("transactionId") or "").strip()
    original_id = str(payload.get("originalTransactionId") or transaction_id).strip()
    expected_product = settings.iap_product_id or PRODUCT_UNLOCK
    expected_bundle = settings.iap_bundle_id or "com.yizhidao.app"
    if product_id != expected_product:
        raise AppError(MSG_BAD_RECEIPT, code=4001, status_code=400)
    if bundle_id and bundle_id != expected_bundle:
        raise AppError(MSG_BAD_RECEIPT, code=4001, status_code=400)
    if txn_type and txn_type not in ("Non-Consumable", "NonConsumable"):
        raise AppError(MSG_BAD_RECEIPT, code=4001, status_code=400)
    if not transaction_id:
        raise AppError(MSG_BAD_RECEIPT, code=4001, status_code=400)

    taken = db.scalar(
        select(User).where(
            User.iap_transaction_id == transaction_id,
            User.id != user.id,
        )
    )
    if taken is not None:
        raise AppError(MSG_BOUND, code=4001, status_code=409)

    user.iap_unlocked = True
    user.iap_platform = "ios"
    user.iap_product_id = product_id
    user.iap_transaction_id = transaction_id
    user.iap_original_transaction_id = original_id or transaction_id
    user.iap_purchased_at = _as_datetime(payload.get("purchaseDate"))
    db.add(user)
    db.commit()
    db.refresh(user)
    return user
