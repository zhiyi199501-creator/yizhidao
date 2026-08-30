import os
import tempfile
import unittest
import uuid
from datetime import datetime, timezone
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from fastapi.testclient import TestClient

from app.config import settings
from app import db as app_db
from app.db import init_db, reset_engine
from app.models import User


def _tmp_db() -> tuple[str, str]:
    handle = tempfile.NamedTemporaryFile(prefix="yizhidao-feedback-", suffix=".db", delete=False)
    handle.close()
    return handle.name, f"sqlite:///{handle.name}"


class FeedbackTests(unittest.TestCase):
    def setUp(self):
        self._prev = {
            "database_url": settings.database_url,
            "admin_password": settings.admin_password,
            "jwt_secret": settings.jwt_secret,
        }
        self._db_path, url = _tmp_db()
        settings.database_url = url
        settings.admin_password = "test-admin-pass"
        settings.jwt_secret = "test-feedback-secret"
        reset_engine()
        init_db()
        from app.main import app

        self.client = TestClient(app)

    def tearDown(self):
        self.client.close()
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

    def test_anonymous_submit(self):
        resp = self.client.post(
            "/v1/feedback",
            json={
                "body": "希望增加夜间模式",
                "contact": "me@example.com",
                "platform": "ios",
                "appVersion": "1.0",
            },
        )
        self.assertEqual(resp.status_code, 200, resp.text)
        self.assertTrue(resp.json().get("ok"))

        self._login_admin()
        listed = self.client.get("/v1/admin/feedback")
        self.assertEqual(listed.status_code, 200, listed.text)
        body = listed.json()
        self.assertEqual(body["total"], 1)
        self.assertEqual(body["unread"], 1)
        item = body["items"][0]
        self.assertIsNone(item["userId"])
        self.assertEqual(item["body"], "希望增加夜间模式")
        self.assertEqual(item["contact"], "me@example.com")
        self.assertEqual(item["platform"], "ios")
        self.assertEqual(item["appVersion"], "1.0")
        self.assertIsNone(item["readAt"])

    def test_logged_in_submit_attaches_user(self):
        user = self._create_user()
        resp = self.client.post(
            "/v1/feedback",
            headers=self._auth_header(user),
            json={"body": "登录后也能反馈", "platform": "android", "appVersion": "1.0.1"},
        )
        self.assertEqual(resp.status_code, 200, resp.text)

        self._login_admin()
        listed = self.client.get("/v1/admin/feedback")
        item = listed.json()["items"][0]
        self.assertEqual(item["userId"], user.id)
        self.assertEqual(item["nickname"], "测用户")
        self.assertEqual(item["platform"], "android")
        self.assertTrue(item["email"])

    def test_stale_token_still_accepted(self):
        resp = self.client.post(
            "/v1/feedback",
            headers={"Authorization": "Bearer not-a-token"},
            json={"body": "过期登录也能提交"},
        )
        self.assertEqual(resp.status_code, 200, resp.text)
        self._login_admin()
        item = self.client.get("/v1/admin/feedback").json()["items"][0]
        self.assertIsNone(item["userId"])

    def test_short_body_rejected(self):
        resp = self.client.post("/v1/feedback", json={"body": "短"})
        self.assertEqual(resp.status_code, 400)
        self.assertEqual(resp.json()["code"], 4001)

    def test_admin_requires_cookie(self):
        resp = self.client.get("/v1/admin/feedback")
        self.assertEqual(resp.status_code, 401)

    def test_mark_read_and_unread_filter(self):
        self.client.post("/v1/feedback", json={"body": "第一条意见反馈"})
        self.client.post("/v1/feedback", json={"body": "第二条意见反馈"})
        self._login_admin()
        listed = self.client.get("/v1/admin/feedback")
        first_id = listed.json()["items"][0]["id"]
        marked = self.client.patch(f"/v1/admin/feedback/{first_id}", json={"read": True})
        self.assertEqual(marked.status_code, 200, marked.text)
        self.assertIsNotNone(marked.json()["item"]["readAt"])

        unread = self.client.get("/v1/admin/feedback?unreadOnly=true")
        self.assertEqual(unread.json()["total"], 1)
        self.assertEqual(unread.json()["items"][0]["body"], "第一条意见反馈")

        overview = self.client.get("/v1/admin/overview")
        self.assertEqual(overview.json()["feedback"]["total"], 2)
        self.assertEqual(overview.json()["feedback"]["unread"], 1)

    def test_search_by_body(self):
        self.client.post("/v1/feedback", json={"body": "想看更多案例讲解"})
        self.client.post("/v1/feedback", json={"body": "起卦页按钮太小"})
        self._login_admin()
        found = self.client.get("/v1/admin/feedback", params={"q": "案例"})
        self.assertEqual(found.json()["total"], 1)
        self.assertIn("案例", found.json()["items"][0]["body"])


if __name__ == "__main__":
    unittest.main()
