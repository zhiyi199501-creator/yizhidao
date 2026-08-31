from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.db import get_db
from app.deps import get_current_user
from app.models import User
from app.schemas import IAPVerifyRequest, IAPVerifyResponse
from app.services.iap import daily_limit_for_user, verify_and_unlock

router = APIRouter()


@router.post("/v1/iap/verify", response_model=IAPVerifyResponse)
def iap_verify(
    body: IAPVerifyRequest,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> IAPVerifyResponse:
    user = verify_and_unlock(
        db,
        user,
        platform=body.platform,
        signed_transaction=body.signedTransaction,
    )
    return IAPVerifyResponse(
        unlocked=True,
        productId=user.iap_product_id or "",
        aiDailyLimit=daily_limit_for_user(user),
    )
