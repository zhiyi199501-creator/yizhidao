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
        self.assertEqual(analysis.direction, "")
        self.assertEqual(analysis.advice, [])
        self.assertEqual(analysis.risks, [])
        self.assertGreaterEqual(len(analysis.askNext), 1)
        self.assertTrue(any("换岗" in q for q in analysis.askNext))
        self.assertTrue(all(q.startswith("我") for q in analysis.askNext))

    def test_english_mock_quotes_question_and_sets_ask_next(self):
        body = AIAnalysisBody(
            question="Should I change jobs",
            method="coin",
            primaryNumber=1,
            movingPositions=[2],
            lines=[7, 8, 7, 8, 8, 8],
            uiLanguage="en",
        )
        analysis, _ = _analyze_mock(body)
        self.assertIn("Should I change jobs", analysis.summary)
        self.assertIn("Attend to", analysis.focus)
        self.assertTrue(any("What happens next" in q for q in analysis.askNext))
        self.assertTrue(all("我" not in q for q in analysis.askNext))

    def test_mock_followup_has_no_advice(self):
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
        self.assertEqual(advice, [])
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
        self.assertEqual(analysis.risks, [])
        self.assertEqual(analysis.direction, "")
        self.assertEqual(analysis.advice, [])
        dropped = _analysis_from_parsed(
            {
                "summary": "背景",
                "focus": "详细解读",
                "direction": "不该出现",
                "advice": ["一", "二", "三", "四"],
                "askNext": ["下一问"],
            }
        )
        self.assertEqual(dropped.advice, [])
        self.assertEqual(dropped.direction, "")
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
        self.assertEqual(many.risks, [])
        block = _previous_analysis_block(analysis)
        self.assertIn("事情背景：背景", block)
        self.assertIn("详细解读：当下", block)
        self.assertNotIn("方向", block)
        self.assertIn("可再问：下一问", block)
        self.assertNotIn("须防", block)
        self.assertNotIn("可做", block)
        self.assertNotIn("建议：", block)

    def test_old_client_previous_analysis_defaults(self):
        from app.schemas import AIAnalysisContent

        old = AIAnalysisContent(summary="a", focus="b")
        self.assertEqual(old.direction, "")
        self.assertEqual(old.risks, [])
        self.assertEqual(old.advice, [])
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
        self.assertIn("详细解读：当下", slim)
        self.assertIn("本卦", slim)
        self.assertNotIn("须防", slim)
        self.assertIn("不要输出建议", slim)
        self.assertIn("仍用类比", slim)
        self.assertNotIn("读辞分四层", slim)
        self.assertNotIn("建议：", slim)
        self.assertNotIn("必须给出针对这一轮的建议", slim)
        self.assertLess(len(slim) * 2, len(full))
        self.assertLess(len(slim), 2400)

    def test_analyze_prompt_has_analogy_rules_not_lecture_source(self):
        from app.schemas import AIAnalysisBody
        from app.services.ai import EN_ANALOGY, ZH_ANALOGY, _build_prompt

        body = AIAnalysisBody(
            question="该不该签这份跳槽 offer",
            method="coin",
            primaryNumber=49,
            resultingNumber=55,
            movingPositions=[5],
        )
        full = _build_prompt(body)
        self.assertIn("断卦用类比", full)
        self.assertIn("读辞分四层", full)
        self.assertIn("先听清真正在问什么", full)
        self.assertIn("爻位是阶段", full)
        self.assertIn("占到哪一爻就站在哪一爻看", full)
        self.assertIn("无咎", full)
        self.assertIn("悔亡", full)
        self.assertIn("勿用", full)
        self.assertIn("之卦只在", full)
        self.assertIn(ZH_ANALOGY, full)
        for banned in ("张庆祥", "万经之王", "筛土", "新冠", "尿布", "纳甲", "互卦"):
            self.assertNotIn(banned, ZH_ANALOGY)
            self.assertNotIn(banned, EN_ANALOGY)
        self.assertNotIn("万经之王", full)
        self.assertNotIn("筛土", full)
        english = _build_prompt(body.model_copy(update={"uiLanguage": "en"}))
        self.assertIn("Read by analogy", english)
        self.assertIn(EN_ANALOGY, english)

    def test_english_prompts_lock_output_language(self):
        from app.schemas import AIAnalysisContent, AIFollowupBody
        from app.services.ai import EN_OUTPUT_LOCK, _build_prompt, _followup_prompt

        prev = AIAnalysisContent(
            summary="Background",
            focus="Present",
            advice=["Do one thing"],
            direction="Direction",
            risks=["Haste"],
            askNext=["What next?"],
        )
        body = AIFollowupBody(
            question="Should I leave",
            method="coin",
            primaryNumber=49,
            resultingNumber=55,
            movingPositions=[5],
            previousAnalysis=prev,
            message="What am I afraid to let go of?",
            uiLanguage="en",
        )
        slim = _followup_prompt(body)
        full = _build_prompt(body)
        self.assertTrue(slim.startswith(EN_OUTPUT_LOCK))
        self.assertTrue(full.startswith(EN_OUTPUT_LOCK))
        self.assertIn("Earlier reading:", slim)
        self.assertIn("Detailed reading:", slim)
        self.assertIn("Latest follow-up:", slim)
        self.assertIn("Do not output advice", slim)
        self.assertIn("Keep analogizing", slim)
        self.assertNotIn("Advice:", slim)
        self.assertNotIn("Give advice for this turn", slim)
        self.assertNotIn("此前解读", slim)
        self.assertNotIn("请针对这条追问", slim)
        self.assertLess(len(slim), 2400)

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
