import json
import unittest
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.services.ai import _hex_block, explanation_slots
from app.services.ima_format import blocks, clean_catalog_answers, prompt_text, stripped
from app.services.ima_store import get_entry, resolve_ima_path


class ImaFormatTests(unittest.TestCase):
    def test_strips_citation_footnotes(self):
        text = (
            "永远不行动 1\n"
            "这就是勇气 1。\n"
            "称为「初九」。1\n"
            "下卦为乾（天）1。\n"
            "凡提到「大」几乎都指阳2。\n"
            "1. 小畜不是小气\n"
            "第12卦仍是正文"
        )
        self.assertEqual(
            stripped(text),
            "永远不行动\n"
            "这就是勇气。\n"
            "称为「初九」。\n"
            "下卦为乾（天）。\n"
            "凡提到「大」几乎都指阳。\n"
            "1. 小畜不是小气\n"
            "第12卦仍是正文",
        )

    def test_strips_thinking_process(self):
        raw = "思考过程\n思考过程\n已浏览张庆祥讲易经_364.docx\n思考过程\n大壮卦九二爻详解"
        result = blocks(raw)
        self.assertEqual(len(result), 1)
        self.assertTrue(isinstance(result[0], str))
        self.assertNotIn("思考过程", result[0])
        self.assertTrue(result[0].startswith("已浏览"))

    def test_flattens_tab_table(self):
        raw = "所以才能「亨」\n六、占断参考\n表格\n占问\t结果\n占婚姻\t小康之象。不会太富裕，但也不会饿死\n七、核心启示"
        text = prompt_text(raw)
        self.assertIn("占问 | 结果", text)
        self.assertIn("占婚姻 | 小康之象", text)
        self.assertNotIn("表格", text)
        self.assertIn("核心启示", text)


class ExplanationSlotTests(unittest.TestCase):
    def test_zero_moving_includes_tuanci(self):
        ids = [entry_id for _, entry_id in explanation_slots(2, None, [])]
        self.assertEqual(ids, ["02-guaci", "02-tuanci", "02-daxiang"])

    def test_one_moving_skips_tuanci(self):
        ids = [entry_id for _, entry_id in explanation_slots(1, 14, [2])]
        self.assertEqual(ids[0], "01-guaci")
        self.assertNotIn("01-tuanci", ids)
        self.assertNotIn("01-wenyan", ids)
        self.assertIn("01-daxiang", ids)
        self.assertIn("01-yao-1", ids)

    def test_three_moving_adds_primary_tuanci_not_resulting(self):
        ids = [entry_id for _, entry_id in explanation_slots(1, 14, [1, 2, 3])]
        self.assertEqual(ids[0], "01-guaci")
        self.assertIn("01-tuanci", ids)
        self.assertIn("14-guaci", ids)
        self.assertNotIn("14-tuanci", ids)
        self.assertNotIn("14-daxiang", ids)

    def test_six_moving_adds_resulting_tuanci(self):
        ids = [entry_id for _, entry_id in explanation_slots(1, 2, [1, 2, 3, 4, 5, 6])]
        self.assertIn("01-guaci", ids)
        self.assertNotIn("01-tuanci", ids)
        self.assertIn("02-guaci", ids)
        self.assertIn("02-tuanci", ids)
        self.assertIn("02-daxiang", ids)

    def test_four_moving_uses_resulting_static_yao(self):
        ids = [entry_id for _, entry_id in explanation_slots(3, 8, [3, 4, 5, 6])]
        self.assertEqual(ids[0], "03-guaci")
        self.assertNotIn("03-tuanci", ids)
        self.assertIn("08-yao-0", ids)
        self.assertIn("08-yao-1", ids)


class ImaStoreTests(unittest.TestCase):
    def test_packaged_answers_are_prestripped(self):
        path = resolve_ima_path()
        payload = json.loads(path.read_text(encoding="utf-8"))
        entries = payload.get("entries") or {}
        self.assertGreater(len(entries), 100)
        dirty = [
            entry_id
            for entry_id, entry in entries.items()
            if stripped(entry.get("answer") or "") != (entry.get("answer") or "")
        ]
        self.assertEqual(dirty, [])

    def test_clean_catalog_answers_rewrites_footnotes(self):
        entries = {"01-guaci": {"answer": "永远不行动 1\n思考过程\n1. 小畜不是小气"}}
        self.assertEqual(clean_catalog_answers(entries), 1)
        self.assertEqual(entries["01-guaci"]["answer"], "永远不行动\n1. 小畜不是小气")
        self.assertEqual(clean_catalog_answers(entries), 0)

    def test_loads_qian_guaci(self):
        entry = get_entry("01-guaci")
        self.assertIsNotNone(entry)
        self.assertIn("元亨利贞", entry.get("scripture") or "")
        self.assertGreater(len(entry.get("answer") or ""), 200)

    def test_loads_qian_tuanci(self):
        entry = get_entry("01-tuanci")
        self.assertIsNotNone(entry)
        self.assertIn("大哉乾元", entry.get("scripture") or "")


class HexBlockTests(unittest.TestCase):
    def test_includes_tuanci_after_guaci(self):
        text = _hex_block(1, "本卦")
        self.assertIn("卦辞：", text)
        self.assertIn("彖辞：大哉乾元", text)
        self.assertLess(text.index("卦辞："), text.index("彖辞："))
        self.assertLess(text.index("彖辞："), text.index("大象："))
        self.assertNotIn("文言", text)


if __name__ == "__main__":
    unittest.main()
