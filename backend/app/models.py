from datetime import datetime
from typing import Optional

from sqlalchemy import Boolean, DateTime, Index, Integer, String, Text, func
from sqlalchemy.orm import Mapped, mapped_column

from app.db import Base


class User(Base):
    __tablename__ = "users"

    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    phone: Mapped[Optional[str]] = mapped_column(String(20), unique=True, index=True, nullable=True)
    email: Mapped[Optional[str]] = mapped_column(String(255), unique=True, index=True, nullable=True)
    apple_sub: Mapped[Optional[str]] = mapped_column(String(255), unique=True, index=True, nullable=True)
    google_sub: Mapped[Optional[str]] = mapped_column(String(255), unique=True, index=True, nullable=True)
    nickname: Mapped[str] = mapped_column(String(64))
    avatar_updated_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    last_login_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    iap_unlocked: Mapped[bool] = mapped_column(Boolean, default=False, server_default="0")
    ai_unlimited: Mapped[bool] = mapped_column(Boolean, default=False, server_default="0")
    iap_platform: Mapped[Optional[str]] = mapped_column(String(16), nullable=True)
    iap_product_id: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    iap_transaction_id: Mapped[Optional[str]] = mapped_column(String(64), unique=True, nullable=True)
    iap_original_transaction_id: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    iap_purchased_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)


class SMSCode(Base):
    __tablename__ = "sms_codes"

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
    phone: Mapped[str] = mapped_column(String(20), index=True)
    code: Mapped[str] = mapped_column(String(8))
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    used: Mapped[bool] = mapped_column(Boolean, default=False, server_default="0")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())


class EmailCode(Base):
    __tablename__ = "email_codes"

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
    email: Mapped[str] = mapped_column(String(255), index=True)
    code: Mapped[str] = mapped_column(String(8))
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    used: Mapped[bool] = mapped_column(Boolean, default=False, server_default="0")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())


class AIUsageEvent(Base):
    """AI 调用用量。禁止写入所问、解读正文、追问对话。"""

    __tablename__ = "ai_usage_events"
    __table_args__ = (
        Index("ix_ai_usage_events_created_at", "created_at"),
        Index("ix_ai_usage_events_user_created", "user_id", "created_at"),
    )

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    user_id: Mapped[str] = mapped_column(String(36), index=True)
    kind: Mapped[str] = mapped_column(String(16))
    ok: Mapped[bool] = mapped_column(Boolean, default=False, server_default="0")
    error_code: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    latency_ms: Mapped[int] = mapped_column(Integer, default=0, server_default="0")
    prompt_tokens: Mapped[int] = mapped_column(Integer, default=0, server_default="0")
    completion_tokens: Mapped[int] = mapped_column(Integer, default=0, server_default="0")
    model: Mapped[str] = mapped_column(String(128), default="", server_default="")
    method: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)


class UserFeedback(Base):
    """App 意见反馈。可匿名；有登录则记下 user_id。"""

    __tablename__ = "user_feedback"
    __table_args__ = (
        Index("ix_user_feedback_created_at", "created_at"),
        Index("ix_user_feedback_read_at", "read_at"),
    )

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    user_id: Mapped[Optional[str]] = mapped_column(String(36), index=True, nullable=True)
    body: Mapped[str] = mapped_column(Text)
    contact: Mapped[str] = mapped_column(String(120), default="", server_default="")
    platform: Mapped[str] = mapped_column(String(16), default="", server_default="")
    app_version: Mapped[str] = mapped_column(String(32), default="", server_default="")
    read_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)


class ContentCase(Base):
    """案例工作副本。未发布前不影响 GET /v1/cases。"""

    __tablename__ = "content_cases"

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
    file: Mapped[str] = mapped_column(String(128), unique=True, index=True)
    hexagram: Mapped[str] = mapped_column(String(32))
    position: Mapped[str] = mapped_column(String(64), default="卦辞", server_default="卦辞")
    background: Mapped[str] = mapped_column(Text, default="", server_default="")
    question: Mapped[str] = mapped_column(Text, default="", server_default="")
    casting: Mapped[str] = mapped_column(Text, default="", server_default="")
    explanation: Mapped[str] = mapped_column(Text, default="", server_default="")
    verification: Mapped[str] = mapped_column(Text, default="", server_default="")
    number: Mapped[int] = mapped_column(Integer, default=0, server_default="0", index=True)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
