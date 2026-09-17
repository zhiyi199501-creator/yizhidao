import json
import unittest
from pathlib import Path
import sys
from unittest.mock import patch

from pydantic import BaseModel

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.errors import AppError
from app.services import ai as ai_mod
from app.services.ai_keepalive import _chunks, stream_json


class _FakeResp:
    def __init__(self, status_code, payload, text=None):
        self.status_code = status_code
        self._payload = payload
        self.text = text if text is not None else (
            json.dumps(payload) if isinstance(payload, dict) else ""
        )

    def json(self):
        if isinstance(self._payload, dict):
            return self._payload
        raise json.JSONDecodeError("empty", "", 0)


class _FakeClient:
    def __init__(self, responses):
        self._responses = list(responses)
        self.payloads = []

    def __enter__(self):
        return self

    def __exit__(self, *args):
        return False

    def post(self, url, headers=None, json=None):
        self.payloads.append(json)
        if not self._responses:
            raise AssertionError("no more fake responses")
        return self._responses.pop(0)


def _ok_payload(content: str, prompt=3, completion=5):
    return {
        "choices": [{"message": {"content": content}}],
        "usage": {"prompt_tokens": prompt, "completion_tokens": completion},
    }


class CompleteJsonRetryTests(unittest.TestCase):
    def setUp(self):
        self._old_key = ai_mod.settings.openai_api_key
        self._old_model = ai_mod.settings.openai_model
        ai_mod.settings.openai_api_key = "test-key"
        ai_mod.settings.openai_model = "deepseek-flash"

    def tearDown(self):
        ai_mod.settings.openai_api_key = self._old_key
        ai_mod.settings.openai_model = self._old_model

    def test_retries_empty_content_then_succeeds(self):
        empty = _FakeResp(200, {"choices": [{"message": {"content": ""}}]})
        ok = _FakeResp(
            200,
            _ok_payload('{"summary":"背景","focus":"详细解读","askNext":["我接下来会怎样？"]}'),
        )
        client = _FakeClient([empty, ok])
        with patch("app.services.ai.httpx.Client", return_value=client), patch(
            "app.services.ai.time.sleep"
        ):
            parsed, usage = ai_mod._complete_json("sys", "user")
        self.assertEqual(parsed["summary"], "背景")
        self.assertEqual(usage.completionTokens, 5)
        self.assertEqual(len(client.payloads), 2)
        self.assertNotIn("stream", client.payloads[0])
        self.assertEqual(client.payloads[0].get("thinking"), {"type": "disabled"})

    def test_exhausted_retries_raise_502(self):
        client = _FakeClient([_FakeResp(200, {"choices": [{"message": {"content": ""}}]})] * 3)
        with patch("app.services.ai.httpx.Client", return_value=client), patch(
            "app.services.ai.time.sleep"
        ):
            with self.assertRaises(AppError) as ctx:
                ai_mod._complete_json("sys", "user")
        self.assertEqual(ctx.exception.status_code, 502)


class KeepaliveTests(unittest.TestCase):
    def test_leading_newline_then_json(self):
        class Dummy(BaseModel):
            ok: bool = True
            n: int = 1

        chunks = list(_chunks(lambda: Dummy(n=7)))
        self.assertEqual(chunks[0], b"\n")
        self.assertIn(b'"n":7', chunks[-1])
        parsed = json.loads(b"".join(chunks))
        self.assertEqual(parsed["n"], 7)

    def test_stream_json_response_type(self):
        class Dummy(BaseModel):
            ok: bool = True

        resp = stream_json(lambda: Dummy())
        self.assertEqual(resp.media_type, "application/json")


if __name__ == "__main__":
    unittest.main()
