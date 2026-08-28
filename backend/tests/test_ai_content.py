import unittest
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.schemas import AIAnalysisBody
from app.services.ai import _analysis_from_parsed, _analyze_mock, _previous_analysis_block


class AnalysisStructureTests(unittest.TestCase):
    def test_mock_has_followup_fields(self):
        body = AIAnalysisBody(
            question="该不该换岗",
            method="coin",
            primaryNumber=1,
            movingPositions=[2],
            lines=[7, 8, 7, 8, 8, 8],
        )
        analysis, _ = _analyze_mock(body)
        self.assertTrue(analysis.summary)
        self.assertTrue(analysis.focus)
        self.assertTrue(analysis.direction)
        self.assertGreaterEqual(len(analysis.advice), 1)
        self.assertGreaterEqual(len(analysis.risks), 1)
        self.assertEqual(len(analysis.risks), 1)
        self.assertGreaterEqual(len(analysis.askNext), 1)
        self.assertTrue(any("换岗" in q for q in analysis.askNext))
        self.assertTrue(all(q.startswith("我") for q in analysis.askNext))

    def test_empty_question_ask_next_is_career_and_relationship(self):
        from app.services.ai import EMPTY_QUESTION_ASK_NEXT

        body = AIAnalysisBody(
            question="",
            method="coin",
            primaryNumber=58,
            movingPositions=[],
        )
        analysis, _ = _analyze_mock(body)
        self.assertEqual(analysis.askNext, EMPTY_QUESTION_ASK_NEXT)
        self.assertEqual(analysis.askNext, ["我的事业会如何？", "我的感情会如何？"])
        self.assertTrue(any("事业" in a or "感情" in a for a in analysis.advice))

        model_ask = _analysis_from_parsed(
            {
                "summary": "背景",
                "focus": "当下",
                "direction": "方向",
                "risks": ["须防"],
                "advice": ["可做"],
                "askNext": ["模型自拟的问"],
            }
        )
        from app.services.ai import _apply_empty_question_ask_next

        self.assertEqual(_apply_empty_question_ask_next("", model_ask).askNext, ["我的事业会如何？", "我的感情会如何？"])
        with_q = _analysis_from_parsed(
            {
                "summary": "背景",
                "focus": "当下",
                "direction": "方向",
                "risks": ["须防"],
                "advice": ["可做"],
                "askNext": ["模型自拟的问"],
            }
        )
        self.assertEqual(_apply_empty_question_ask_next("换岗", with_q).askNext, ["模型自拟的问"])

    def test_mock_followup_includes_advice(self):
        from app.schemas import AIAnalysisContent, AIFollowupBody
        from app.services.ai import _followup_mock

        body = AIFollowupBody(
            question="该不该换岗",
            method="coin",
            primaryNumber=1,
            movingPositions=[2],
            previousAnalysis=AIAnalysisContent(
                summary="背景",
                focus="当下",
                advice=["建议"],
                direction="方向",
                risks=["须防"],
                askNext=["再问"],
            ),
            message="如果对方反对呢",
        )
        reply, advice, ask_next, _ = _followup_mock(body)
        self.assertIn("对方反对", reply)
        self.assertGreaterEqual(len(advice), 1)
        self.assertGreaterEqual(len(ask_next), 1)
        self.assertTrue(all("我" in q for q in ask_next))

    def test_parse_accepts_string_lists(self):
        analysis = _analysis_from_parsed(
            {
                "summary": "背景",
                "focus": "当下",
                "direction": "方向",
                "risks": "须防一项",
                "advice": ["可做"],
                "askNext": ["下一问"],
            }
        )
        self.assertEqual(analysis.risks, ["须防一项"])
        many = _analysis_from_parsed(
            {
                "summary": "背景",
                "focus": "当下",
                "direction": "方向",
                "risks": ["起步过急", "须防：勿忽戒惧"],
                "advice": ["可做"],
                "askNext": ["下一问"],
            }
        )
        self.assertEqual(many.risks, ["起步过急；勿忽戒惧"])
        block = _previous_analysis_block(analysis)
        self.assertIn("事情背景：背景", block)
        self.assertIn("可再问：下一问", block)

    def test_old_client_previous_analysis_defaults(self):
        from app.schemas import AIAnalysisContent

        old = AIAnalysisContent(summary="a", focus="b", advice=["c"])
        self.assertEqual(old.direction, "")
        self.assertEqual(old.risks, [])
        self.assertEqual(old.askNext, [])

    def test_followup_prompt_is_slim(self):
        from app.schemas import AIAnalysisContent, AIFollowupBody
        from app.services.ai import _build_prompt, _followup_prompt

        prev = AIAnalysisContent(
            summary="背景",
            focus="当下",
            advice=["建议"],
            direction="方向",
            risks=["须防"],
            askNext=["再问"],
        )
        body = AIFollowupBody(
            question="该不该签这份跳槽 offer",
            method="coin",
            primaryNumber=49,
            resultingNumber=55,
            movingPositions=[5],
            previousAnalysis=prev,
            message="如果对方反对、或原公司挽留呢",
        )
        slim = _followup_prompt(body)
        full = _build_prompt(body)
        self.assertNotIn("黄庭书院经文讲解", slim)
        self.assertNotIn("讲习案例", slim)
        self.assertNotIn("讲师解读", slim)
        self.assertIn("解卦焦点", slim)
        self.assertIn("焦点经文", slim)
        self.assertIn("大人虎变", slim)
        self.assertIn("如果对方反对", slim)
        self.assertIn("事情背景：背景", slim)
        self.assertIn("本卦", slim)
        self.assertLess(len(slim) * 2, len(full))
        self.assertLess(len(slim), 2000)

    def test_followup_zero_moving_skips_other_yao_ci(self):
        from app.schemas import AIAnalysisContent, AIFollowupBody
        from app.services.ai import _followup_prompt

        body = AIFollowupBody(
            question="今年还要不要换岗",
            method="coin",
            primaryNumber=58,
            previousAnalysis=AIAnalysisContent(summary="背景", focus="当下", advice=["建议"]),
            message="如果家人反对呢",
        )
        slim = _followup_prompt(body)
        self.assertIn("亨", slim)
        self.assertNotIn("来兑", slim)
        self.assertNotIn("孚于剥", slim)


if __name__ == "__main__":
    unittest.main()
