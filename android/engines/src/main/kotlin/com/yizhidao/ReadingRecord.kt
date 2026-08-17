package com.yizhidao

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.time.Instant
import java.util.UUID

@Serializable
data class ReadingRecord(
    val id: String,
    val createdAtEpochMs: Long,
    val question: String? = null,
    val methodRaw: String,
    val numbers: List<Int>? = null,
    val primaryNumber: Int,
    val resultingNumber: Int? = null,
    val lines: List<Int>,
    val movingPositions: List<Int>,
    val verificationStatusRaw: String = VerificationStatus.NONE.raw,
    val verificationNote: String? = null,
) {
    val method: CastingMethod
        get() = CastingMethod.fromRaw(methodRaw)

    val verificationStatus: VerificationStatus
        get() = VerificationStatus.fromRaw(verificationStatusRaw)

    val createdAt: Instant
        get() = Instant.ofEpochMilli(createdAtEpochMs)

    fun toCastResult(): CastResult = CastResult(
        method = method,
        createdAt = createdAt,
        question = question,
        numbers = numbers,
        primaryNumber = primaryNumber,
        resultingNumber = resultingNumber,
        lines = lines.mapNotNull { LineValue.fromRaw(it) },
        movingPositions = movingPositions,
    )

    fun withQuestion(question: String?): ReadingRecord = copy(question = question)

    fun withVerification(status: VerificationStatus, note: String?): ReadingRecord =
        copy(verificationStatusRaw = status.raw, verificationNote = note)

    companion object {
        fun from(result: CastResult, id: String = UUID.randomUUID().toString()): ReadingRecord =
            ReadingRecord(
                id = id,
                createdAtEpochMs = result.createdAt.toEpochMilli(),
                question = result.question,
                methodRaw = result.method.raw,
                numbers = result.numbers,
                primaryNumber = result.primaryNumber,
                resultingNumber = result.resultingNumber,
                lines = result.lines.map { it.rawValue },
                movingPositions = result.movingPositions,
            )
    }
}

object ReadingRecordCodec {
    fun decodeList(text: String): List<ReadingRecord> =
        HexagramStore.json.decodeFromString(ListSerializer(ReadingRecord.serializer()), text)

    fun encodeList(records: List<ReadingRecord>): String =
        HexagramStore.json.encodeToString(ListSerializer(ReadingRecord.serializer()), records)
}
