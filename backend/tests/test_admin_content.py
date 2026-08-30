import json
import os
import tempfile
import unittest
import uuid
from datetime import datetime, timezone
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from fastapi.testclient import TestClient
from sqlalchemy import func, select

from app.config import settings
from app import db as app_db
from app.db import init_db, reset_engine
from app.models import AIUsageEvent, User
from app.services.ai_rate_limit import limiter
from app.services.case_store import invalidate_cases_cache
from app.services.ima_store import get_entry, invalidate_ima_cache


def _tmp_db() -> tuple[str, str]:
    handle = tempfile.NamedTemporaryFile(prefix="yizhidao-cms-", suffix=".db", delete=False)
    handle.close()
    return handle.name, f"sqlite:///{handle.name}"


def _tmp_json(payload) -> str:
    handle = tempfile.NamedTemporaryFile(prefix="yizhidao-cms-", suffix=".json", delete=False)
    handle.write(json.dumps(payload, ensure_ascii=False).encode("utf-8"))
    handle.close()
    return handle.name


class _AdminClientMixin:
    def _boot(self, cases_payload=None, ima_payload=None):
        self._prev = {
            "database_url": settings.database_url,
            "admin_password": settings.admin_password,
            "app_env": settings.app_env,
            "ai_mode": settings.ai_mode,
            "jwt_secret": settings.jwt_secret,
            "cases_path": settings.cases_path,
            "ima_explanations_path": settings.ima_explanations_path,
        }
        self._cleanup_files = []
        self._db_path, url = _tmp_db()
        settings.database_url = url
        settings.admin_password = "test-admin-pass"
        settings.app_env = "development"
        settings.ai_mode = "mock"
        settings.jwt_secret = "test-cms-secret"
        if cases_payload is not None:
            settings.cases_path = _tmp_json(cases_payload)
            self._cleanup_files.append(settings.cases_path)
        if ima_payload is not None:
            settings.ima_explanations_path = _tmp_json(ima_payload)
            self._cleanup_files.append(settings.ima_explanations_path)
        invalidate_cases_cache()
        invalidate_ima_cache()
        reset_engine()
        init_db()
        limiter.reset()
        from app.main import app

        self.client = TestClient(app)

    def _shutdown(self):
        self.client.close()
        limiter.reset()
        for key, value in self._prev.items():
            setattr(settings, key, value)
        invalidate_cases_cache()
        invalidate_ima_cache()
        reset_engine()
        try:
            os.unlink(self._db_path)
        except OSError:
            pass
        for path in self._cleanup_files:
            try:
                os.unlink(path)
            except OSError:
                pass
            try:
                os.unlink(path + ".tmp")
            except OSError:
                pass

    def _login(self) -> None:
        resp = self.client.post("/v1/admin/login", json={"password": "test-admin-pass"})
        self.assertEqual(resp.status_code, 200, resp.text)


class CasesCmsTests(_AdminClientMixin, unittest.TestCase):
    def setUp(self):
        self._boot(cases_payload=[])

    def tearDown(self):
        self._shutdown()

    def test_cases_requires_admin(self):
        resp = self.client.get("/v1/admin/cases")
        self.assertEqual(resp.status_code, 401)

    def test_publish_updates_public_cases_version_and_fields(self):
        self._login()
        listed = self.client.get("/v1/admin/cases")
        self.assertEqual(listed.status_code, 200, listed.text)
        self.assertEqual(listed.json()["total"], 0)

        created = self.client.post(
            "/v1/admin/cases",
            json={
                "file": "01-1乾卦初爻",
                "hexagram": "乾卦",
                "position": "初爻",
                "background": "背景",
                "question": "所问摘要",
                "casting": "占得乾卦初爻动。",
                "explanation": "讲师解读",
                "verification": "应验",
            },
        )
        self.assertEqual(created.status_code, 200, created.text)
        self.assertEqual(created.json()["case"]["number"], 1)

        public_before = self.client.get("/v1/cases")
        self.assertEqual(public_before.status_code, 200)
        before = public_before.json()
        self.assertEqual(before["cases"], [])

        status = self.client.get("/v1/admin/cases/status")
        self.assertEqual(status.json()["draftCount"], 1)
        self.assertEqual(status.json()["publishedCount"], 0)

        published = self.client.post("/v1/admin/cases/publish")
        self.assertEqual(published.status_code, 200, published.text)
        version = published.json()["version"]
        self.assertTrue(version)

        public_after = self.client.get("/v1/cases")
        self.assertEqual(public_after.status_code, 200)
        body = public_after.json()
        self.assertEqual(body["version"], version)
        self.assertNotEqual(body["version"], before["version"])
        self.assertEqual(len(body["cases"]), 1)
        item = body["cases"][0]
        self.assertEqual(
            set(item),
            {
                "file",
                "hexagram",
                "position",
                "background",
                "question",
                "casting",
                "explanation",
                "verification",
                "number",
            },
        )
        self.assertEqual(item["file"], "01-1乾卦初爻")
        self.assertEqual(item["number"], 1)

        duplicate = self.client.post(
            "/v1/admin/cases",
            json={"file": "01-1乾卦初爻", "hexagram": "乾卦", "position": "初爻"},
        )
        self.assertEqual(duplicate.status_code, 400)

    def test_import_json_replaces_draft_only(self):
        self._login()
        payload = [
            {
                "file": "58-1兑卦初爻",
                "hexagram": "兑卦",
                "position": "初爻",
                "background": "",
                "question": "导入所问",
                "casting": "",
                "explanation": "",
                "verification": "",
                "number": 58,
            }
        ]
        imported = self.client.post(
            "/v1/admin/cases/import",
            files={"file": ("cases.json", json.dumps(payload, ensure_ascii=False).encode("utf-8"), "application/json")},
        )
        self.assertEqual(imported.status_code, 200, imported.text)
        self.assertEqual(imported.json()["total"], 1)
        public = self.client.get("/v1/cases").json()
        self.assertEqual(public["cases"], [])
        exported = self.client.get("/v1/admin/cases/export")
        self.assertEqual(exported.status_code, 200)
        items = exported.json()
        self.assertEqual(items[0]["file"], "58-1兑卦初爻")


class ImaEditTests(_AdminClientMixin, unittest.TestCase):
    def setUp(self):
        self._boot(
            ima_payload={
                "source": "test",
                "entries": {
                    "01-guaci": {
                        "title": "乾卦卦辞",
                        "scripture": "乾，元亨利贞。",
                        "answer": "永远不行动 1\n1. 小畜不是小气",
                    }
                },
            }
        )

    def tearDown(self):
        self._shutdown()

    def test_save_ima_answer_is_read_by_explanation_slots(self):
        self._login()
        marker = f"NEW_IMA_{uuid.uuid4().hex[:8]}"
        saved = self.client.put(
            "/v1/admin/ima/entries/01-guaci",
            json={"answer": marker},
        )
        self.assertEqual(saved.status_code, 200, saved.text)
        self.assertEqual(saved.json()["entry"]["answer"], marker)
        self.assertIn("下次发版", saved.json()["note"])

        from app.services.ai import explanation_slots

        slots = explanation_slots(1, 44, [1])
        ids = [entry_id for _, entry_id in slots]
        self.assertIn("01-guaci", ids)
        entry = get_entry("01-guaci")
        self.assertIsNotNone(entry)
        self.assertEqual(entry["answer"], marker)

    def test_ima_per_hexagram_not_full_dump(self):
        self._login()
        index = self.client.get("/v1/admin/ima")
        self.assertEqual(index.status_code, 200)
        self.assertEqual(len(index.json()["hexagrams"]), 64)
        one = self.client.get("/v1/admin/ima/1")
        self.assertEqual(one.status_code, 200)
        self.assertEqual(one.json()["entries"][0]["id"], "01-guaci")
        self.assertEqual(
            one.json()["entries"][0]["answer"],
            "永远不行动\n1. 小畜不是小气",
        )
        self.assertNotIn("entries", index.json())

    def test_save_ima_answer_strips_citation_footnotes(self):
        self._login()
        saved = self.client.put(
            "/v1/admin/ima/entries/01-guaci",
            json={"answer": "这就是勇气 1。\n1. 小畜不是小气"},
        )
        self.assertEqual(saved.status_code, 200, saved.text)
        cleaned = "这就是勇气。\n1. 小畜不是小气"
        self.assertEqual(saved.json()["entry"]["answer"], cleaned)
        entry = get_entry("01-guaci")
        self.assertIsNotNone(entry)
        self.assertEqual(entry["answer"], cleaned)


class JingwenAndEvalTests(_AdminClientMixin, unittest.TestCase):
    def setUp(self):
        self._boot()

    def tearDown(self):
        self._shutdown()

    def _create_user(self) -> User:
        db = app_db.SessionLocal()
        try:
            user = User(
                id=f"u_{uuid.uuid4().hex[:12]}",
                email="cms@example.com",
                nickname="测用户",
                created_at=datetime.now(timezone.utc),
            )
            db.add(user)
            db.commit()
            db.refresh(user)
            return user
        finally:
            db.close()

    def test_jingwen_is_read_only(self):
        self._login()
        catalog = self.client.get("/v1/admin/hexagrams")
        self.assertEqual(catalog.status_code, 200)
        self.assertEqual(len(catalog.json()["hexagrams"]), 64)
        one = self.client.get("/v1/admin/hexagrams/1")
        self.assertEqual(one.status_code, 200)
        body = one.json()["hexagram"]
        self.assertEqual(body["name"], "乾")
        self.assertTrue(body["guaci"])
        self.assertTrue(body["yaoci"])
        self.assertIn("wenyan", body)
        self.assertEqual(self.client.post("/v1/admin/hexagrams").status_code, 405)
        self.assertEqual(self.client.put("/v1/admin/hexagrams/1", json={"guaci": "x"}).status_code, 405)
        self.assertEqual(self.client.delete("/v1/admin/hexagrams/1").status_code, 405)

    def test_eval_uses_fixtures_and_skips_usage_events(self):
        self._create_user()
        self._login()
        db = app_db.SessionLocal()
        try:
            users_before = int(db.scalar(select(func.count(User.id))) or 0)
            events_before = int(db.scalar(select(func.count(AIUsageEvent.id))) or 0)
        finally:
            db.close()

        samples = self.client.get("/v1/admin/eval/samples")
        self.assertEqual(samples.status_code, 200)
        self.assertGreaterEqual(len(samples.json()["samples"]), 1)

        dry = self.client.post("/v1/admin/eval/run", json={"live": False, "ids": ["zero", "one"]})
        self.assertEqual(dry.status_code, 200, dry.text)
        self.assertTrue(dry.json()["pass"])
        self.assertIsNone(dry.json()["followup"])

        live = self.client.post("/v1/admin/eval/run", json={"live": True, "ids": ["career"]})
        self.assertEqual(live.status_code, 200, live.text)
        self.assertTrue(live.json()["samples"][0]["analysis"])
        self.assertEqual(live.json()["aiMode"], "mock")

        db = app_db.SessionLocal()
        try:
            self.assertEqual(int(db.scalar(select(func.count(User.id))) or 0), users_before)
            self.assertEqual(int(db.scalar(select(func.count(AIUsageEvent.id))) or 0), events_before)
        finally:
            db.close()


if __name__ == "__main__":
    unittest.main()
