import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.config import settings
from app.errors import AppError
from app.services.ai_rate_limit import MSG_DAILY_DONE, MSG_TOO_FAST, acquire_ai_call, limiter


_TZ = timezone(timedelta(hours=8))


class AIRateLimitTests(unittest.TestCase):
    def setUp(self):
        self._interval = settings.ai_rate_interval_sec
        self._daily = settings.ai_rate_daily_limit
        settings.ai_rate_interval_sec = 8
        settings.ai_rate_daily_limit = 40
        limiter.reset()
        self._clock = 1_700_000_000.0
        limiter._now = lambda: self._clock

    def tearDown(self):
        settings.ai_rate_interval_sec = self._interval
        settings.ai_rate_daily_limit = self._daily
        limiter.reset()
        limiter._now = __import__("time").time

    def _acquire(self, user: str = "u1") -> None:
        with acquire_ai_call(user):
            pass

    def test_second_call_within_interval_is_4290(self):
        self._acquire()
        with self.assertRaises(AppError) as ctx:
            self._acquire()
        self.assertEqual(ctx.exception.code, 4290)
        self.assertEqual(ctx.exception.status_code, 429)
        self.assertEqual(ctx.exception.message, MSG_TOO_FAST)

    def test_call_after_interval_ok(self):
        self._acquire()
        self._clock += 8
        self._acquire()

    def test_analyze_and_followup_share_daily_bucket(self):
        settings.ai_rate_daily_limit = 2
        settings.ai_rate_interval_sec = 0
        self._acquire()
        self._acquire()
        with self.assertRaises(AppError) as ctx:
            self._acquire()
        self.assertEqual(ctx.exception.code, 4290)
        self.assertEqual(ctx.exception.message, MSG_DAILY_DONE)

    def test_calendar_day_utc8_resets_count(self):
        settings.ai_rate_daily_limit = 1
        settings.ai_rate_interval_sec = 0
        # 2026-08-28 23:59:50 UTC+8
        before = datetime(2026, 8, 28, 23, 59, 50, tzinfo=_TZ).timestamp()
        after = datetime(2026, 8, 29, 0, 0, 10, tzinfo=_TZ).timestamp()
        self._clock = before
        self._acquire()
        with self.assertRaises(AppError) as ctx:
            self._clock = before + 1
            self._acquire()
        self.assertEqual(ctx.exception.message, MSG_DAILY_DONE)
        self._clock = after
        self._acquire()

    def test_concurrent_second_inflight_blocked(self):
        settings.ai_rate_interval_sec = 0
        limiter.acquire("u1")
        with self.assertRaises(AppError) as ctx:
            limiter.acquire("u1")
        self.assertEqual(ctx.exception.code, 4290)
        self.assertEqual(ctx.exception.message, MSG_TOO_FAST)
        limiter.release("u1")
        limiter.acquire("u1")
        limiter.release("u1")

    def test_users_are_independent(self):
        settings.ai_rate_interval_sec = 0
        self._acquire("a")
        self._acquire("b")

    def test_snapshot_does_not_consume_and_tracks_remaining(self):
        settings.ai_rate_interval_sec = 0
        used, remaining = limiter.snapshot("u1", 3)
        self.assertEqual((used, remaining), (0, 3))
        self._acquire()
        used, remaining = limiter.snapshot("u1", 3)
        self.assertEqual((used, remaining), (1, 2))
        used_again, _ = limiter.snapshot("u1", 3)
        self.assertEqual(used_again, 1)

    def test_acquire_honors_explicit_daily_limit(self):
        settings.ai_rate_interval_sec = 0
        settings.ai_rate_daily_limit = 40
        with acquire_ai_call("u1", daily_limit=1):
            pass
        with self.assertRaises(AppError) as ctx:
            with acquire_ai_call("u1", daily_limit=1):
                pass
        self.assertEqual(ctx.exception.message, MSG_DAILY_DONE)


if __name__ == "__main__":
    unittest.main()
