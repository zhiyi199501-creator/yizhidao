import logging
import smtplib
from email.message import EmailMessage

from app.config import settings
from app.errors import AppError

logger = logging.getLogger(__name__)


def email_test_address_set() -> set[str]:
    return {
        part.strip().lower()
        for part in (settings.email_test_addresses or "").split(",")
        if part.strip()
    }


def is_email_test_address(email: str) -> bool:
    return email.strip().lower() in email_test_address_set()


def deliver_email_code(email: str, code: str) -> None:
    provider = (settings.email_provider or "mock").lower()
    if provider == "smtp":
        _deliver_smtp(email, code)
        return
    if is_email_test_address(email):
        print(f"[email:test] to={_mask_email(email)} code={code}")
        return
    print(f"[email:mock] to={_mask_email(email)} code={code}")


def _deliver_smtp(email: str, code: str) -> None:
    if not settings.smtp_host or not settings.smtp_from:
        raise AppError("邮件通道未配置", code=5000, status_code=503)
    msg = EmailMessage()
    msg["Subject"] = "易玩家登录验证码"
    msg["From"] = settings.smtp_from
    msg["To"] = email
    msg.set_content(
        f"您的验证码为 {code}，{settings.email_code_expire_min} 分钟内有效。请勿泄露。"
    )
    try:
        with smtplib.SMTP(settings.smtp_host, settings.smtp_port, timeout=15) as smtp:
            if settings.smtp_use_tls:
                smtp.starttls()
            if settings.smtp_user:
                smtp.login(settings.smtp_user, settings.smtp_password)
            smtp.send_message(msg)
    except OSError as exc:
        logger.exception("smtp send failed")
        raise AppError("验证码发送失败", code=5000, status_code=503) from exc


def _mask_email(email: str) -> str:
    local, _, domain = email.partition("@")
    if not local:
        return email
    if len(local) <= 2:
        masked_local = local[0] + "*"
    else:
        masked_local = local[0] + "***" + local[-1]
    return f"{masked_local}@{domain}"
