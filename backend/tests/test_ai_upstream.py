import json
import unittest
from pathlib import Path
import sys
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.errors import AppError
from app.services import ai as ai_mod
from app.services.ai import _delta_content, _parse_stream_line, _read_stream


class _FakeStream:
    def __init__(self, status_code, lines, body=b""):
        self.status_code = status_code
        self._lines = lines
        self._body = body

    def __enter__(self):
        return self

    def __exit__(self, *args):
        return False

    def iter_lines(self):
        yield from self._lines

    def read(self):
        return self._body


class _FakeClient:
    def __init__(self, streams):
        self._streams = list(streams)
        self.payloads = []

    def __enter__(self):
        return self

    def __exit__(self, *args):
        return False

    def stream(self, method, url, headers=None, json=None):
        self.payloads.append(json)
        if not self._streams:
            raise AssertionError("no more fake streams")
        return self._streams.pop(0)


def _sse(content_piece, usage=None, done=True):
    chunk = {"choices": [{"delta": {"content": content_piece}}]}
    if usage:
        chunk["usage"] = usage
    lines = [f"data: {json.dumps(chunk, ensure_ascii=False)}"]
    if done:
        lines.append("data: [DONE]")
    return lines


class StreamParseTests(unittest.TestCase):
    def test_skips_keep_alive_and_done(self):
        self.assertIsNone(_parse_stream_line(""))
        self.assertIsNone(_parse_stream_line(": keep-alive"))
        self.assertIsNone(_parse_stream_line("data: [DONE]"))
        chunk = _parse_stream_line('data: {"choices":[{"delta":{"content":"{"}}]}')
        self.assertEqual(_delta_content(chunk), "{")

    def test_read_stream_joins_deltas(self):
        resp = _FakeStream(
            200,
            [
                ": keep-alive",
                *_sse('{"summary":"背景"', done=False),
                *_sse(',"focus":"详细","askNext":["我呢？"]}', usage={"prompt_tokens": 10, "completion_tokens": 4}),
            ],
        )
        content, usage = _read_stream(resp)
        parsed = json.loads(content)
        self.assertEqual(parsed["summary"], "背景")
        self.assertEqual(usage.promptTokens, 10)
        self.assertEqual(usage.completionTokens, 4)


class CompleteJsonRetryTests(unittest.TestCase):
    def setUp(self):
        self._old_key = ai_mod.settings.openai_api_key
        self._old_model = ai_mod.settings.openai_model
        ai_mod.settings.openai_api_key = "test-key"
        ai_mod.settings.openai_model = "deepseek-flash"

    def tearDown(self):
        ai_mod.settings.openai_api_key = self._old_key
        ai_mod.settings.openai_model = self._old_model

    def test_retries_empty_stream_then_succeeds(self):
        empty = _FakeStream(200, [": keep-alive", "data: [DONE]"])
        ok = _FakeStream(
            200,
            _sse('{"summary":"背景","focus":"详细解读","askNext":["我接下来会怎样？"]}'),
        )
        client = _FakeClient([empty, ok])

        with patch("app.services.ai.httpx.Client", return_value=client), patch(
            "app.services.ai.time.sleep"
        ):
            parsed, usage = ai_mod._complete_json("sys", "user")
        self.assertEqual(parsed["summary"], "背景")
        self.assertEqual(parsed["focus"], "详细解读")
        self.assertEqual(len(client.payloads), 2)
        self.assertTrue(client.payloads[0].get("stream"))

    def test_exhausted_retries_raise_502(self):
        streams = [_FakeStream(200, ["data: [DONE]"]) for _ in range(3)]
        client = _FakeClient(streams)
        with patch("app.services.ai.httpx.Client", return_value=client), patch(
            "app.services.ai.time.sleep"
        ):
            with self.assertRaises(AppError) as ctx:
                ai_mod._complete_json("sys", "user")
        self.assertEqual(ctx.exception.status_code, 502)
        self.assertEqual(len(client.payloads), 3)


if __name__ == "__main__":
    unittest.main()
