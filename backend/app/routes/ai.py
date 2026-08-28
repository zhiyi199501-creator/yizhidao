from fastapi import APIRouter, Depends

from app.deps import get_current_user_id
from app.schemas import AIAnalysisBody, AIAnalysisResponse, AIFollowupBody, AIFollowupResponse
from app.services.ai import analyze_reading, followup_reading
from app.services.ai_rate_limit import acquire_ai_call

router = APIRouter()


@router.post("/v1/ai/analyze", response_model=AIAnalysisResponse)
def ai_analyze(
    body: AIAnalysisBody,
    user_id: str = Depends(get_current_user_id),
) -> AIAnalysisResponse:
    with acquire_ai_call(user_id):
        analysis, usage = analyze_reading(body)
    return AIAnalysisResponse(analysis=analysis, usage=usage)


@router.post("/v1/ai/followup", response_model=AIFollowupResponse)
def ai_followup(
    body: AIFollowupBody,
    user_id: str = Depends(get_current_user_id),
) -> AIFollowupResponse:
    with acquire_ai_call(user_id):
        reply, advice, ask_next, usage = followup_reading(body)
    return AIFollowupResponse(reply=reply, advice=advice, askNext=ask_next, usage=usage)
