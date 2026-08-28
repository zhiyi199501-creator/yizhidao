import unittest
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.services.case_store import (
    case_matches_yao,
    cases_for_ai_prompt,
    select_cases_by_yao,
)


def _item(file: str, position: str) -> dict:
    return {"file": file, "position": position, "hexagram": "测"}


class CaseMatchTests(unittest.TestCase):
    def test_dual_position_hits_both(self):
        self.assertTrue(case_matches_yao("三爻、四爻", 3))
        self.assertTrue(case_matches_yao("三爻、四爻", 4))
        self.assertFalse(case_matches_yao("三爻、四爻", 2))

    def test_lead_yao_comes_first_and_cap(self):
        items = [
            _item("a", "初爻"),
            _item("b", "上爻"),
            _item("c", "上爻"),
            _item("d", "上爻"),
            _item("e", "初爻"),
        ]
        picked = select_cases_by_yao(items, [1, 6], lead=6, limit=3)
        self.assertEqual([i["file"] for i in picked], ["b", "c", "d"])


class CasesForPromptTests(unittest.TestCase):
    def test_zero_and_three_moving_skip_yao_cases(self):
        caption, cases = cases_for_ai_prompt(58, None, [])
        self.assertIn("本卦卦辞", caption)
        self.assertEqual(cases, [])
        caption, cases = cases_for_ai_prompt(58, 1, [1, 2, 3])
        self.assertIn("本卦卦辞", caption)
        self.assertEqual(cases, [])

    def test_six_moving_skips_yao_cases(self):
        caption, cases = cases_for_ai_prompt(1, 2, [1, 2, 3, 4, 5, 6])
        self.assertIn("之卦卦辞", caption)
        self.assertEqual(cases, [])

    def test_one_moving_caps_at_three_same_yao(self):
        caption, cases = cases_for_ai_prompt(58, None, [6])
        self.assertLessEqual(len(cases), 3)
        self.assertTrue(cases)
        self.assertTrue(all("上爻" in str(c.get("position")) for c in cases))
        self.assertIn("本卦上爻", caption)

    def test_two_moving_prefers_upper(self):
        caption, cases = cases_for_ai_prompt(58, None, [2, 6])
        self.assertTrue(cases)
        self.assertIn("以上爻为主", caption)
        self.assertIn("上爻", str(cases[0].get("position")))

    def test_four_moving_uses_resulting_static(self):
        # 本卦动 3–6 → 之卦静初、二，主看初
        caption, cases = cases_for_ai_prompt(1, 58, [3, 4, 5, 6])
        self.assertIn("之卦", caption)
        self.assertIn("初爻", caption)
        if cases:
            self.assertTrue(
                any(case_matches_yao(str(c.get("position") or ""), 1) for c in cases)
                or any(case_matches_yao(str(c.get("position") or ""), 2) for c in cases)
            )


if __name__ == "__main__":
    unittest.main()
