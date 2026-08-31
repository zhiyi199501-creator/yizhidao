import json
import os
import tempfile
import unittest
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from fastapi.testclient import TestClient

from app import db as app_db
from app.config import settings
from app.db import init_db, reset_engine
from app.models import User
from app.services.ai_rate_limit import limiter
from app.services.iap import daily_limit_for_user, effective_verify_mode


def _tmp_db() -> tuple[str, str]:
    handle = tempfile.NamedTemporaryFile(prefix="yizhidao-iap-", suffix=".db", delete=False)
    handle.close()
    return handle.name, f"sqlite:///{handle.name}"


def _receipt(transaction_id: str = "txn_iap_1", product_id: Optional[str] = None) -> str:
    return json.dumps(
        {
            "productId": product_id or settings.iap_product_id,
            "bundleId": settings.iap_bundle_id,
            "type": "Non-Consumable",
            "transactionId": transaction_id,
            "originalTransactionId": transaction_id,
            "purchaseDate": 1_700_000_000_000,
        }
    )


class IAPVerifyTests(unittest.TestCase):
    def setUp(self):
        self._prev = {
            "database_url": settings.database_url,
            "app_env": settings.app_env,
            "ai_mode": settings.ai_mode,
            "ai_rate_interval_sec": settings.ai_rate_interval_sec,
            "ai_rate_daily_limit": settings.ai_rate_daily_limit,
            "ai_rate_daily_limit_unlock": settings.ai_rate_daily_limit_unlock,
            "jwt_secret": settings.jwt_secret,
            "iap_verify_mode": settings.iap_verify_mode,
            "allow_insecure_mock_iap": settings.allow_insecure_mock_iap,
        }
        self._db_path, url = _tmp_db()
        settings.database_url = url
        settings.app_env = "development"
        settings.ai_mode = "mock"
        settings.ai_rate_interval_sec = 0
        settings.ai_rate_daily_limit = 3
        settings.ai_rate_daily_limit_unlock = 30
        settings.jwt_secret = "test-iap-secret"
        settings.iap_verify_mode = "mock"
        settings.allow_insecure_mock_iap = False
        reset_engine()
        init_db()
        limiter.reset()
        from app.main import app

        self.client = TestClient(app)

    def tearDown(self):
        self.client.close()
        limiter.reset()
        for key, value in self._prev.items():
            setattr(settings, key, value)
        reset_engine()
        try:
            os.unlink(self._db_path)
        except OSError:
            pass

    def _create_user(self, email: str = "iap@example.com") -> User:
        db = app_db.SessionLocal()
        try:
            user = User(
                id=f"u_{uuid.uuid4().hex[:12]}",
                email=email,
                nickname="购用户",
                created_at=datetime.now(timezone.utc),
            )
            db.add(user)
            db.commit()
            db.refresh(user)
            return user
        finally:
            db.close()

    def _auth(self, user: User) -> dict:
        from app.services.auth import issue_access_token

        return {"Authorization": f"Bearer {issue_access_token(user.id)}"}

    def _reload(self, user_id: str) -> User:
        db = app_db.SessionLocal()
        try:
            return db.get(User, user_id)
        finally:
            db.close()

    def test_me_includes_locked_entitlement(self):
        user = self._create_user()
        resp = self.client.get("/v1/me", headers=self._auth(user))
        self.assertEqual(resp.status_code, 200, resp.text)
        body = resp.json()["user"]
        self.assertFalse(body["iapUnlocked"])
        self.assertEqual(body["aiDailyLimit"], 3)
        self.assertEqual(body["aiDailyUsed"], 0)
        self.assertEqual(body["aiDailyRemaining"], 3)

    def test_verify_unlocks_and_restore_same_user(self):
        user = self._create_user()
        payload = {"platform": "ios", "signedTransaction": _receipt()}
        first = self.client.post("/v1/iap/verify", headers=self._auth(user), json=payload)
        self.assertEqual(first.status_code, 200, first.text)
        body = first.json()
        self.assertTrue(body["unlocked"])
        self.assertEqual(body["productId"], settings.iap_product_id)
        self.assertEqual(body["aiDailyLimit"], 30)
        stored = self._reload(user.id)
        self.assertTrue(stored.iap_unlocked)
        self.assertEqual(stored.iap_transaction_id, "txn_iap_1")
        self.assertEqual(daily_limit_for_user(stored), 30)
        again = self.client.post("/v1/iap/verify", headers=self._auth(user), json=payload)
        self.assertEqual(again.status_code, 200, again.text)
        me = self.client.get("/v1/me", headers=self._auth(user))
        self.assertTrue(me.json()["user"]["iapUnlocked"])
        self.assertEqual(me.json()["user"]["aiDailyLimit"], 30)

    def test_same_transaction_bound_to_other_user(self):
        first = self._create_user("a@example.com")
        second = self._create_user("b@example.com")
        payload = {"platform": "ios", "signedTransaction": _receipt("txn_shared")}
        self.assertEqual(
            self.client.post("/v1/iap/verify", headers=self._auth(first), json=payload).status_code,
            200,
        )
        taken = self.client.post("/v1/iap/verify", headers=self._auth(second), json=payload)
        self.assertEqual(taken.status_code, 409)
        self.assertEqual(taken.json()["code"], 4001)
        self.assertIn("其他账号", taken.json()["message"])
        self.assertFalse(self._reload(second.id).iap_unlocked)

    def test_wrong_product_rejected(self):
        user = self._create_user()
        resp = self.client.post(
            "/v1/iap/verify",
            headers=self._auth(user),
            json={"platform": "ios", "signedTransaction": _receipt(product_id="other.sku")},
        )
        self.assertEqual(resp.status_code, 400)
        self.assertFalse(self._reload(user.id).iap_unlocked)

    def test_production_rejects_mock_json(self):
        settings.app_env = "production"
        settings.iap_verify_mode = "mock"
        settings.allow_insecure_mock_iap = False
        self.assertEqual(effective_verify_mode(), "apple")
        user = self._create_user()
        resp = self.client.post(
            "/v1/iap/verify",
            headers=self._auth(user),
            json={"platform": "ios", "signedTransaction": _receipt()},
        )
        self.assertEqual(resp.status_code, 400)
        self.assertFalse(self._reload(user.id).iap_unlocked)

    def test_unlocked_user_uses_higher_daily_limit(self):
        settings.ai_rate_daily_limit = 1
        settings.ai_rate_daily_limit_unlock = 2
        user = self._create_user()
        payload = {
            "question": "换岗",
            "method": "digitalManual",
            "primaryNumber": 11,
            "resultingNumber": 26,
            "movingPositions": [1],
        }
        first = self.client.post("/v1/ai/analyze", headers=self._auth(user), json=payload)
        self.assertEqual(first.status_code, 200, first.text)
        me = self.client.get("/v1/me", headers=self._auth(user))
        self.assertEqual(me.json()["user"]["aiDailyUsed"], 1)
        self.assertEqual(me.json()["user"]["aiDailyRemaining"], 0)
        blocked = self.client.post("/v1/ai/analyze", headers=self._auth(user), json=payload)
        self.assertEqual(blocked.status_code, 429)
        verify = self.client.post(
            "/v1/iap/verify",
            headers=self._auth(user),
            json={"platform": "ios", "signedTransaction": _receipt("txn_quota")},
        )
        self.assertEqual(verify.status_code, 200, verify.text)
        limiter.reset()
        after = self.client.post("/v1/ai/analyze", headers=self._auth(user), json=payload)
        self.assertEqual(after.status_code, 200, after.text)
        second = self.client.post("/v1/ai/analyze", headers=self._auth(user), json=payload)
        self.assertEqual(second.status_code, 200, second.text)
        third = self.client.post("/v1/ai/analyze", headers=self._auth(user), json=payload)
        self.assertEqual(third.status_code, 429)


if __name__ == "__main__":
    unittest.main()
