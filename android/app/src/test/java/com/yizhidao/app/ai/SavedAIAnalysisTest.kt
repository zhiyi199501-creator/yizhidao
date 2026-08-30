package com.yizhidao.app.ai

import com.yizhidao.CastResult
import com.yizhidao.CastingMethod
import com.yizhidao.LineValue
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class SavedAIAnalysisTest {
    @Test
    fun foldsRisksIntoAdviceWithPrefix() {
        val items = aiAdviceDisplayItems(
            advice = listOf("先明确边界"),
            risks = listOf("起步过急易失节奏", "须防：已带前缀", "  "),
        )
        assertEquals(
            listOf("先明确边界", "须防：起步过急易失节奏；已带前缀"),
            items,
        )
    }

    @Test
    fun oneReadingPerCastReusesId() {
        val result = sampleResult(createdAtMs = 1_700_000_000_000L)
        val first = SavedAIAnalysis.make(
            result = result,
            analysis = SavedAIContent("背景", "当下", listOf("建议")),
            followUps = emptyList(),
            readingRecordId = "rec-1",
        )
        val second = SavedAIAnalysis.make(
            result = result,
            analysis = SavedAIContent("新背景", "新当下", listOf("新建议")),
            followUps = emptyList(),
            readingRecordId = "rec-1",
            existingId = first.id,
        )
        assertEquals(first.id, second.id)
        assertEquals("rec-1", second.readingRecordId)
        assertEquals(result.createdAt.toEpochMilli(), second.createdAtEpochMs)
        assertEquals(SavedAIAnalysis.fingerprint(result), second.fingerprint())
    }

    @Test
    fun matchesSameCastWithoutRecordId() {
        val result = sampleResult(createdAtMs = 1_700_000_000_123L)
        val saved = SavedAIAnalysis.make(
            result = result,
            analysis = SavedAIContent("背景", "当下", listOf("建议")),
            followUps = emptyList(),
        )
        assertEquals(true, SavedAIAnalysisStore.matches(saved, recordId = "later-id", result = result))
        val nearby = result.copy(createdAt = Instant.ofEpochMilli(1_700_000_000_800L))
        assertEquals(true, SavedAIAnalysisStore.matches(saved, recordId = null, result = nearby))
    }

    private fun sampleResult(createdAtMs: Long) = CastResult(
        method = CastingMethod.DIGITAL_MANUAL,
        createdAt = Instant.ofEpochMilli(createdAtMs),
        question = "事业",
        numbers = listOf(1, 2, 3),
        primaryNumber = 1,
        resultingNumber = 2,
        lines = listOf(
            LineValue.YOUNG_YANG,
            LineValue.YOUNG_YIN,
            LineValue.YOUNG_YANG,
            LineValue.YOUNG_YIN,
            LineValue.YOUNG_YANG,
            LineValue.YOUNG_YIN,
        ),
        movingPositions = listOf(1),
    )
}
