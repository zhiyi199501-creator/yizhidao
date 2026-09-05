import io
import os
import tempfile
import unittest
import uuid
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from fastapi.testclient import TestClient

from app import db as app_db
from app.config import settings
from app.db import init_db, reset_engine
from app.models import User
from app.services.auth import issue_access_token, nickname_from_email
from app.services.avatar import avatars_dir


def _tmp_db() -> tuple[str, str]:
    handle = tempfile.NamedTemporaryFile(prefix="yizhidao-profile-", suffix=".db", delete=False)
    handle.close()
    return handle.name, f"sqlite:///{handle.name}"


def _jpeg_bytes() -> bytes:
    try:
        from PIL import Image
    except ImportError:
        return b"\xff\xd8\xff\xe0" + b"\x00" * 100
    buf = io.BytesIO()
    Image.new("RGB", (8, 8), color=(120, 80, 200)).save(buf, format="JPEG")
    return buf.getvalue()


class NicknameFromEmailTests(unittest.TestCase):
    def test_uses_local_part(self):
        self.assertEqual(nickname_from_email("luozhihao@foxmail.com"), "luozhihao")
        self.assertEqual(nickname_from_email("a@x.com"), "a用户")


class ProfileApiTests(unittest.TestCase):
    def setUp(self):
        self._prev = {
            "database_url": settings.database_url,
            "avatars_dir": settings.avatars_dir,
            "jwt_secret": settings.jwt_secret,
            "dev_email_fixed_code": settings.dev_email_fixed_code,
            "email_provider": settings.email_provider,
            "allow_insecure_mock_email": settings.allow_insecure_mock_email,
        }
        self._db_path, url = _tmp_db()
        self._avatar_root = tempfile.mkdtemp(prefix="yizhidao-avatars-")
        settings.database_url = url
        settings.avatars_dir = self._avatar_root
        settings.jwt_secret = "test-profile-secret"
        settings.dev_email_fixed_code = "123456"
        settings.email_provider = "mock"
        settings.allow_insecure_mock_email = True
        reset_engine()
        init_db()
        from app.main import app

        self.client = TestClient(app)
        with app_db.SessionLocal() as db:
            user = User(
                id=str(uuid.uuid4()),
                apple_sub="apple_test_sub",
                nickname="旧昵称",
            )
            db.add(user)
            db.commit()
            self.user_id = user.id
        self.token = issue_access_token(self.user_id)
        self.headers = {"Authorization": f"Bearer {self.token}"}

    def tearDown(self):
        self.client.close()
        for key, value in self._prev.items():
            setattr(settings, key, value)
        reset_engine()
        try:
            os.unlink(self._db_path)
        except OSError:
            pass
        for path in Path(self._avatar_root).glob("*"):
            path.unlink(missing_ok=True)
        try:
            os.rmdir(self._avatar_root)
        except OSError:
            pass

    def test_patch_nickname(self):
        resp = self.client.patch(
            "/v1/me",
            headers=self.headers,
            json={"nickname": "新昵称"},
        )
        self.assertEqual(resp.status_code, 200)
        body = resp.json()
        self.assertEqual(body["user"]["nickname"], "新昵称")
        me = self.client.get("/v1/me", headers=self.headers).json()
        self.assertEqual(me["user"]["nickname"], "新昵称")

    def test_patch_rejects_short_nickname(self):
        resp = self.client.patch(
            "/v1/me",
            headers=self.headers,
            json={"nickname": "a"},
        )
        self.assertIn(resp.status_code, (400, 422))

    def test_email_first_login_uses_local_part_as_nickname(self):
        email = "luozhihao@foxmail.com"
        send = self.client.post("/v1/auth/email/send", json={"email": email})
        self.assertEqual(send.status_code, 200)

        login = self.client.post(
            "/v1/auth/email/login",
            json={"email": email, "code": settings.dev_email_fixed_code},
        )
        self.assertEqual(login.status_code, 200)
        self.assertEqual(login.json()["user"]["nickname"], "luozhihao")
        self.assertEqual(login.json()["user"]["email"], email)

    def test_email_login_upgrades_old_masked_nickname(self):
        from app.services.auth import mask_email

        email = "olduser@example.com"
        with app_db.SessionLocal() as db:
            db.add(
                User(
                    id=str(uuid.uuid4()),
                    email=email,
                    nickname=f"用户{mask_email(email)}",
                )
            )
            db.commit()

        send = self.client.post("/v1/auth/email/send", json={"email": email})
        self.assertEqual(send.status_code, 200)
        login = self.client.post(
            "/v1/auth/email/login",
            json={"email": email, "code": settings.dev_email_fixed_code},
        )
        self.assertEqual(login.status_code, 200)
        self.assertEqual(login.json()["user"]["nickname"], "olduser")

    def test_bind_email(self):
        send = self.client.post(
            "/v1/me/email/send",
            headers=self.headers,
            json={"email": "bind@example.com"},
        )
        self.assertEqual(send.status_code, 200)

        bind = self.client.post(
            "/v1/me/email/bind",
            headers=self.headers,
            json={"email": "bind@example.com", "code": settings.dev_email_fixed_code},
        )
        self.assertEqual(bind.status_code, 200)
        self.assertEqual(bind.json()["user"]["email"], "bind@example.com")

        dup = self.client.post(
            "/v1/me/email/send",
            headers=self.headers,
            json={"email": "other@example.com"},
        )
        self.assertEqual(dup.status_code, 400)

    def test_bind_rejects_taken_email(self):
        with app_db.SessionLocal() as db:
            other = User(
                id=str(uuid.uuid4()),
                email="taken@example.com",
                nickname="other",
            )
            db.add(other)
            db.commit()

        resp = self.client.post(
            "/v1/me/email/send",
            headers=self.headers,
            json={"email": "taken@example.com"},
        )
        self.assertEqual(resp.status_code, 409)

    def test_avatar_roundtrip(self):
        jpeg = _jpeg_bytes()
        put = self.client.put("/v1/me/avatar", headers=self.headers, content=jpeg)
        self.assertEqual(put.status_code, 200)
        self.assertTrue(put.json()["user"]["hasAvatar"])
        self.assertTrue(put.json()["user"]["avatarUpdatedAt"])

        got = self.client.get("/v1/me/avatar", headers=self.headers)
        self.assertEqual(got.status_code, 200)
        self.assertEqual(got.content, jpeg)
        self.assertTrue(avatars_dir().joinpath(f"{self.user_id}.jpg").is_file())

        deleted = self.client.delete("/v1/me/avatar", headers=self.headers)
        self.assertEqual(deleted.status_code, 200)
        self.assertFalse(deleted.json()["user"]["hasAvatar"])
        self.assertEqual(self.client.get("/v1/me/avatar", headers=self.headers).status_code, 404)
