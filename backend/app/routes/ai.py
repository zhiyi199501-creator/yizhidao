from fastapi import APIRouter, Depends

from app.deps import get_current_user_id
from app.schemas import AIAnalysisBody, AIAnalysisResponse, AIFollowupBody, AIFollowupResponse
from app.services.ai import analyze_reading, followup_reading

router = APIRouter()


@router.post("/v1/ai/analyze", response_model=AIAnalysisResponse)
def ai_analyze(
    body: AIAnalysisBody,
    _: str = Depends(get_current_user_id),
) -> AIAnalysisResponse:
    analysis, usage = analyze_reading(body)
    return AIAnalysisResponse(analysis=analysis, usage=usage)


@router.post("/v1/ai/followup", response_model=AIFollowupResponse)
def ai_followup(
    body: AIFollowupBody,
    _: str = Depends(get_current_user_id),
) -> AIFollowupResponse:
    reply, usage = followup_reading(body)
    return AIFollowupResponse(reply=reply, usage=usage)
