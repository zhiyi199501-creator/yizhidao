package com.yizhidao.app.ai

import android.content.Context
import com.yizhidao.CastResult
import com.yizhidao.CastingMethod
import com.yizhidao.LineValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID

fun aiAdviceDisplayItems(advice: List<String>, risks: List<String> = emptyList()): List<String> {
    val prefixes = listOf("须防：", "须防:", "須防：", "須防:")
    val parts = risks.mapNotNull { raw ->
        var text = raw.trim()
        if (text.isEmpty()) return@mapNotNull null
        val prefix = prefixes.firstOrNull { text.startsWith(it) }
        if (prefix != null) text = text.removePrefix(prefix).trim()
        text.ifEmpty { null }
    }
    if (parts.isEmpty()) return advice
    return advice + listOf("须防：${parts.joinToString("；")}")
}

@Serializable
data class SavedAIFollowUp(
    val id: String = UUID.randomUUID().toString(),
    val user: String,
    val assistant: String,
    val advice: List<String> = emptyList(),
    val askNext: List<String> = emptyList(),
)

@Serializable
data class SavedAIContent(
    val summary: String,
    val focus: String,
    val advice: List<String>,
    val direction: String = "",
    val risks: List<String> = emptyList(),
    val askNext: List<String> = emptyList(),
)

@Serializable
data class SavedAIAnalysis(
    val id: String = UUID.randomUUID().toString(),
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val readingRecordId: String? = null,
    val methodRaw: String,
    val question: String? = null,
    val primaryNumber: Int,
    val resultingNumber: Int? = null,
    val lines: List<Int>,
    val movingPositions: List<Int>,
    val analysis: SavedAIContent,
    val followUps: List<SavedAIFollowUp> = emptyList(),
) {
    val method: CastingMethod get() = CastingMethod.fromRaw(methodRaw)
    val createdAt: Instant get() = Instant.ofEpochMilli(createdAtEpochMs)
    val updatedAt: Instant get() = Instant.ofEpochMilli(updatedAtEpochMs)

    fun fingerprint(): String = fingerprint(methodRaw, createdAtEpochMs, primaryNumber, lines)

    fun toCastResult(): CastResult = CastResult(
        method = method,
        createdAt = createdAt,
        question = question,
        numbers = null,
        primaryNumber = primaryNumber,
        resultingNumber = resultingNumber,
        lines = lines.mapNotNull(LineValue::fromRaw),
        movingPositions = movingPositions,
    )

    companion object {
        fun fingerprint(
            methodRaw: String,
            createdAtEpochMs: Long,
            primaryNumber: Int,
            lines: List<Int>,
        ): String = "$methodRaw|$createdAtEpochMs|$primaryNumber|${lines.joinToString(",")}"

        fun fingerprint(result: CastResult): String = fingerprint(
            methodRaw = result.method.raw,
            createdAtEpochMs = result.createdAt.toEpochMilli(),
            primaryNumber = result.primaryNumber,
            lines = result.lines.map { it.rawValue },
        )

        fun make(
            result: CastResult,
            analysis: SavedAIContent,
            followUps: List<SavedAIFollowUp>,
            readingRecordId: String? = null,
            existingId: String? = null,
        ): SavedAIAnalysis {
            val now = System.currentTimeMillis()
            return SavedAIAnalysis(
                id = existingId ?: UUID.randomUUID().toString(),
                createdAtEpochMs = result.createdAt.toEpochMilli(),
                updatedAtEpochMs = now,
                readingRecordId = readingRecordId,
                methodRaw = result.method.raw,
                question = result.question,
                primaryNumber = result.primaryNumber,
                resultingNumber = result.resultingNumber,
                lines = result.lines.map { it.rawValue },
                movingPositions = result.movingPositions,
                analysis = analysis,
                followUps = followUps,
            )
        }
    }
}

class SavedAIAnalysisStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<SavedAIAnalysis>> = _items.asStateFlow()

    fun load(): List<SavedAIAnalysis> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<SavedAIAnalysis>>(raw) }
            .getOrDefault(emptyList())
            .sortedByDescending { it.updatedAtEpochMs }
    }

    fun upsert(item: SavedAIAnalysis) {
        val current = _items.value.toMutableList()
        val byRecord = item.readingRecordId
            ?.takeIf { it.isNotBlank() }
            ?.let { rid -> current.indexOfFirst { it.readingRecordId == rid } }
            ?: -1
        val byId = current.indexOfFirst { it.id == item.id }
        val byFingerprint = current.indexOfFirst { it.fingerprint() == item.fingerprint() }
        val index = when {
            byRecord >= 0 -> byRecord
            byId >= 0 -> byId
            byFingerprint >= 0 -> byFingerprint
            else -> -1
        }
        if (index >= 0) {
            val keep = current[index]
            current[index] = item.copy(
                id = keep.id,
                readingRecordId = item.readingRecordId ?: keep.readingRecordId,
            )
        } else {
            current.add(0, item)
        }
        save(current)
    }

    fun find(recordId: String?, result: CastResult): SavedAIAnalysis? {
        val items = _items.value
        if (!recordId.isNullOrBlank()) {
            items.firstOrNull { it.readingRecordId == recordId }?.let { return it }
        }
        val fp = SavedAIAnalysis.fingerprint(result)
        items.firstOrNull { it.fingerprint() == fp }?.let { return it }
        return items.firstOrNull { matches(it, recordId, result) }
    }

    fun remove(id: String) {
        save(_items.value.filterNot { it.id == id })
    }

    private fun save(items: List<SavedAIAnalysis>) {
        prefs.edit().putString(KEY, json.encodeToString(items)).apply()
        _items.value = items.sortedByDescending { it.updatedAtEpochMs }
    }

    companion object {
        private const val PREFS = "ai.saved.analyses.v1"
        private const val KEY = "items"

        fun matches(item: SavedAIAnalysis, recordId: String?, result: CastResult): Boolean {
            if (!recordId.isNullOrBlank() && item.readingRecordId == recordId) return true
            if (item.fingerprint() == SavedAIAnalysis.fingerprint(result)) return true
            return item.methodRaw == result.method.raw
                && item.primaryNumber == result.primaryNumber
                && item.lines == result.lines.map { it.rawValue }
                && kotlin.math.abs(item.createdAtEpochMs - result.createdAt.toEpochMilli()) < 1000
        }
    }
}
