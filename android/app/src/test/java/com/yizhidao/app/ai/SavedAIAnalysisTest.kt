package com.yizhidao.app.ai

import org.junit.Assert.assertEquals
import org.junit.Test

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
}
