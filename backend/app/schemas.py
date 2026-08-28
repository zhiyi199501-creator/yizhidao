from typing import Optional

from pydantic import BaseModel, Field


class SMSSendRequest(BaseModel):
    phone: str = Field(min_length=11, max_length=11)


class SMSSendResponse(BaseModel):
    ok: bool = True
    cooldownSec: int


class SMSLoginRequest(BaseModel):
    phone: str = Field(min_length=11, max_length=11)
    code: str = Field(min_length=4, max_length=8)


class UserOut(BaseModel):
    id: str
    nickname: str
    phone: Optional[str] = None
    email: Optional[str] = None


class AuthLoginResponse(BaseModel):
    ok: bool = True
    accessToken: str
    user: UserOut


class SMSLoginResponse(AuthLoginResponse):
    pass


class EmailSendRequest(BaseModel):
    email: str = Field(min_length=3, max_length=255)


class EmailSendResponse(BaseModel):
    ok: bool = True
    cooldownSec: int


class EmailLoginRequest(BaseModel):
    email: str = Field(min_length=3, max_length=255)
    code: str = Field(min_length=4, max_length=8)


class AppleLoginRequest(BaseModel):
    identityToken: str = Field(min_length=10)
    fullName: Optional[str] = None


class GoogleLoginRequest(BaseModel):
    idToken: str = Field(min_length=10)


class MeResponse(BaseModel):
    ok: bool = True
    user: UserOut


class OkResponse(BaseModel):
    ok: bool = True


class HealthResponse(BaseModel):
    ok: bool = True
    service: str = "yizhidao-backend"


class AIAnalysisBody(BaseModel):
    question: Optional[str] = None
    method: str
    primaryNumber: int = Field(ge=1, le=64)
    resultingNumber: Optional[int] = Field(default=None, ge=1, le=64)
    movingPositions: list[int] = Field(default_factory=list)
    lines: list[int] = Field(default_factory=list)
    hexTextVersion: str = "yi-zhengshi-2026-08"


class AIAnalysisContent(BaseModel):
    summary: str
    focus: str
    advice: list[str]
    direction: str = ""
    risks: list[str] = Field(default_factory=list)
    askNext: list[str] = Field(default_factory=list)


class AIUsage(BaseModel):
    promptTokens: int
    completionTokens: int


class AIAnalysisResponse(BaseModel):
    ok: bool = True
    analysis: AIAnalysisContent
    usage: AIUsage


class AIChatTurn(BaseModel):
    user: str = Field(min_length=1, max_length=2000)
    assistant: str = Field(min_length=1, max_length=8000)
    advice: list[str] = Field(default_factory=list)


class AIFollowupBody(AIAnalysisBody):
    previousAnalysis: AIAnalysisContent
    conversation: list[AIChatTurn] = Field(default_factory=list)
    message: str = Field(min_length=1, max_length=2000)


class AIFollowupResponse(BaseModel):
    ok: bool = True
    reply: str
    advice: list[str] = Field(default_factory=list)
    askNext: list[str] = Field(default_factory=list)
    usage: AIUsage
