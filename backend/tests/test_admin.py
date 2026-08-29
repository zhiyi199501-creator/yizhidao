import os
import tempfile
import unittest
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from fastapi.testclient import TestClient
from sqlalchemy import inspect, select

from app.config import settings
from app import db as app_db
from app.db import init_db, reset_engine
from app.models import AIUsageEvent, User
from app.services.ai_rate_limit import limiter
from app.services.token import decode_access_token


def _tmp_db() -> tuple[str, str]:
    handle = tempfile.NamedTemporaryFile(prefix="yizhidao-admin-", suffix=".db", delete=False)
    handle.close()
    return handle.name, f"sqlite:///{handle.name}"


class AdminAndUsageTests(unittest.TestCase):
    def setUp(self):
        self._prev = {
            "database_url": settings.database_url,
            "admin_password": settings.admin_password,
            "app_env": settings.app_env,
            "ai_mode": settings.ai_mode,
            "ai_rate_interval_sec": settings.ai_rate_interval_sec,
            "ai_rate_daily_limit": settings.ai_rate_daily_limit,
            "jwt_secret": settings.jwt_secret,
        }
        self._db_path, url = _tmp_db()
        settings.database_url = url
        settings.admin_password = "test-admin-pass"
        settings.app_env = "development"
        settings.ai_mode = "mock"
        settings.ai_rate_interval_sec = 0
        settings.ai_rate_daily_limit = 40
        settings.jwt_secret = "test-admin-secret"
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

    def _login_admin(self) -> None:
        resp = self.client.post("/v1/admin/login", json={"password": "test-admin-pass"})
        self.assertEqual(resp.status_code, 200, resp.text)
        self.assertTrue(resp.json().get("ok"))

    def _create_user(self, email: str = "person@example.com") -> User:
        db = app_db.SessionLocal()
        try:
            user = User(
                id=f"u_{uuid.uuid4().hex[:12]}",
                email=email,
                nickname="测用户",
                created_at=datetime.now(timezone.utc),
            )
            db.add(user)
            db.commit()
            db.refresh(user)
            return user
        finally:
            db.close()

    def _auth_header(self, user: User) -> dict:
        from app.services.auth import issue_access_token

        return {"Authorization": f"Bearer {issue_access_token(user.id)}"}

    def test_admin_requires_cookie(self):
        resp = self.client.get("/v1/admin/overview")
        self.assertEqual(resp.status_code, 401)
        self.assertEqual(resp.json()["code"], 4003)

    def test_admin_wrong_password(self):
        resp = self.client.post("/v1/admin/login", json={"password": "nope"})
        self.assertEqual(resp.status_code, 401)
        self.assertEqual(resp.json()["code"], 4002)

    def test_admin_unconfigured(self):
        settings.admin_password = ""
        resp = self.client.post("/v1/admin/login", json={"password": "x"})
        self.assertEqual(resp.status_code, 503)
        self.assertEqual(resp.json()["code"], 4001)

    def test_admin_login_and_overview(self):
        self._login_admin()
        me = self.client.get("/v1/admin/me")
        self.assertEqual(me.status_code, 200)
        overview = self.client.get("/v1/admin/overview")
        self.assertEqual(overview.status_code, 200)
        body = overview.json()
        self.assertTrue(body["ok"])
        self.assertIn("users", body)
        self.assertIn("aiToday", body)
        self.assertIn("health", body)
        self.assertIn("loginMix", body["users"])

    def test_app_version_is_public(self):
        resp = self.client.get("/v1/app/version")
        self.assertEqual(resp.status_code, 200, resp.text)
        body = resp.json()
        self.assertTrue(body["ok"])
        self.assertTrue(body["ios"])
        self.assertTrue(body["android"])
        self.assertIn("id6804203617", body["iosStoreUrl"])
        self.assertIn("com.yizhidao.app", body["androidStoreUrl"])

    def test_app_token_cannot_access_admin(self):
        user = self._create_user()
        header = self._auth_header(user)
        resp = self.client.get("/v1/admin/overview", headers=header)
        self.assertEqual(resp.status_code, 401)

    def test_usage_event_has_no_question_column_or_text(self):
        user = self._create_user()
        secret = "SECRET_QUESTION_SHOULD_NOT_BE_STORED"
        resp = self.client.post(
            "/v1/ai/analyze",
            headers=self._auth_header(user),
            json={
                "question": secret,
                "method": "coin",
                "primaryNumber": 1,
                "movingPositions": [1],
                "lines": [9, 8, 8, 8, 8, 8],
            },
        )
        self.assertEqual(resp.status_code, 200, resp.text)
        db = app_db.SessionLocal()
        try:
            events = db.scalars(select(AIUsageEvent)).all()
            self.assertEqual(len(events), 1)
            event = events[0]
            self.assertTrue(event.ok)
            self.assertEqual(event.kind, "analyze")
            self.assertEqual(event.method, "coin")
            self.assertEqual(event.user_id, user.id)
            dumped = " ".join(str(value) for value in event.__dict__.values())
            self.assertNotIn(secret, dumped)
            cols = {c["name"] for c in inspect(db.get_bind()).get_columns("ai_usage_events")}
            self.assertFalse({"question", "analysis", "reply", "message"} & cols)
        finally:
            db.close()

    def test_rate_limit_is_recorded(self):
        settings.ai_rate_daily_limit = 1
        limiter.reset()
        user = self._create_user()
        payload = {
            "question": "换岗",
            "method": "digitalManual",
            "primaryNumber": 11,
            "resultingNumber": 26,
            "movingPositions": [1],
        }
        first = self.client.post("/v1/ai/analyze", headers=self._auth_header(user), json=payload)
        self.assertEqual(first.status_code, 200, first.text)
        second = self.client.post("/v1/ai/analyze", headers=self._auth_header(user), json=payload)
        self.assertEqual(second.status_code, 429)
        db = app_db.SessionLocal()
        try:
            events = db.scalars(select(AIUsageEvent).order_by(AIUsageEvent.id)).all()
            self.assertEqual(len(events), 2)
            self.assertTrue(events[0].ok)
            self.assertFalse(events[1].ok)
            self.assertEqual(events[1].error_code, 4290)
        finally:
            db.close()

    def test_users_mask_email_and_hide_subs(self):
        self._create_user("alice.smith@example.com")
        self._login_admin()
        resp = self.client.get("/v1/admin/users")
        self.assertEqual(resp.status_code, 200)
        users = resp.json()["users"]
        self.assertEqual(len(users), 1)
        self.assertTrue(users[0]["email"].startswith("a"))
        self.assertIn("***", users[0]["email"])
        self.assertNotIn("alice.smith", users[0]["email"])
        self.assertNotIn("apple_sub", users[0])
        self.assertNotIn("google_sub", users[0])
        blob = str(users)
        self.assertNotIn("SECRET", blob)

    def test_last_login_at_on_email_login(self):
        from app.models import EmailCode
        from app.services.auth import login_with_email

        db = app_db.SessionLocal()
        try:
            now = datetime.now(timezone.utc)
            db.add(
                EmailCode(
                    email="login@example.com",
                    code="123456",
                    expires_at=now + timedelta(minutes=5),
                    used=False,
                )
            )
            db.commit()
            user, token = login_with_email(db, "login@example.com", "123456")
            self.assertTrue(decode_access_token(token))
            self.assertIsNotNone(user.last_login_at)
        finally:
            db.close()

    def test_system_endpoint_has_no_secrets(self):
        self._login_admin()
        resp = self.client.get("/v1/admin/system")
        self.assertEqual(resp.status_code, 200)
        text = resp.text
        self.assertNotIn(settings.admin_password, text)
        self.assertNotIn("smtp_password", text.lower())
        body = resp.json()
        self.assertTrue(body["ok"])
        self.assertIn("rateLimitNote", body)

    def test_followup_records_kind(self):
        user = self._create_user()
        analyze = self.client.post(
            "/v1/ai/analyze",
            headers=self._auth_header(user),
            json={"question": "换岗", "method": "coin", "primaryNumber": 1, "movingPositions": [2]},
        )
        self.assertEqual(analyze.status_code, 200, analyze.text)
        analysis = analyze.json()["analysis"]
        follow = self.client.post(
            "/v1/ai/followup",
            headers=self._auth_header(user),
            json={
                "question": "换岗",
                "method": "coin",
                "primaryNumber": 1,
                "movingPositions": [2],
                "previousAnalysis": analysis,
                "conversation": [],
                "message": "如果对方反对呢",
            },
        )
        self.assertEqual(follow.status_code, 200, follow.text)
        db = app_db.SessionLocal()
        try:
            kinds = [row.kind for row in db.scalars(select(AIUsageEvent).order_by(AIUsageEvent.id)).all()]
            self.assertEqual(kinds, ["analyze", "followup"])
        finally:
            db.close()


if __name__ == "__main__":
    unittest.main()
