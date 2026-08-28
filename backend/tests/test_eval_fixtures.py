import unittest
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.services.ai import explanation_slots
from app.services.case_store import cases_for_ai_prompt
from app.services.hexagram_store import get_hexagram
from tests.eval_fixtures import SAMPLES


def _resulting(primary: int, moving: list[int]):
    if not moving:
        return None
    hexagram = get_hexagram(primary)
    bits = [int(ch) for ch in str(hexagram["binary"])]
    lookup = {}
    from app.services.hexagram_store import _load_hexagrams

    for number, item in _load_hexagrams().items():
        lookup[str(item.get("binary"))] = number
    for pos in moving:
        bits[pos - 1] = 1 - bits[pos - 1]
    return lookup["".join(str(b) for b in bits)]


class EvalFixtureTests(unittest.TestCase):
    def test_samples_match_slot_and_case_rules(self):
        ids = [sample["id"] for sample in SAMPLES]
        self.assertEqual(len(ids), len(set(ids)))
        self.assertGreaterEqual(len(SAMPLES), 8)
        for sample in SAMPLES:
            with self.subTest(sample["id"]):
                resulting = _resulting(sample["primary"], sample["moving"])
                self.assertEqual(resulting, sample["expect_resulting"])
                ima = [
                    entry_id
                    for _, entry_id in explanation_slots(
                        sample["primary"], resulting, sample["moving"]
                    )
                ]
                for entry_id in sample["expect_ima_ids"]:
                    self.assertIn(entry_id, ima)
                for prefix in sample["expect_ima_absent_prefixes"]:
                    self.assertFalse(
                        any(item == prefix or item.startswith(prefix) for item in ima),
                        msg=f"{prefix} should be absent from {ima}",
                    )
                _, cases = cases_for_ai_prompt(
                    sample["primary"], resulting, sample["moving"]
                )
                self.assertLessEqual(len(cases), sample["expect_case_count_max"])
                if sample["expect_cases_from"] is None:
                    self.assertEqual(cases, [])
                else:
                    self.assertTrue(cases)
                    self.assertTrue(
                        all(item.get("number") == sample["expect_cases_from"] for item in cases)
                    )


if __name__ == "__main__":
    unittest.main()
