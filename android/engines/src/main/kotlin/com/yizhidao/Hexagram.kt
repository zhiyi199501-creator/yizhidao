package com.yizhidao

import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class Hexagram(
    val number: Int,
    val name: String,
    val symbol: String,
    val binary: String,
    val guaci: String,
    val tuanci: String,
    val yaoci: List<String>,
    val daxiang: String,
    val xiaoxiang: List<String>,
) {
    fun yaoCi(position: Int): String {
        if (position !in 1..6 || yaoci.size < position) return ""
        return yaoci[position - 1]
    }

    fun xiaoXiang(position: Int): String {
        if (position !in 1..6 || xiaoxiang.size < position) return ""
        return xiaoxiang[position - 1]
    }
}

data class CastResult(
    val method: CastingMethod,
    val createdAt: Instant,
    val question: String?,
    /** Raw inputs for digital methods: three numbers, or [yearBranch, month, day, hour]. */
    val numbers: List<Int>?,
    val primaryNumber: Int,
    val resultingNumber: Int?,
    /** Bottom (初爻) → top (上爻). */
    val lines: List<LineValue>,
    /** 1-based positions that change. */
    val movingPositions: List<Int>,
) {
    val hasChangingLines: Boolean
        get() = movingPositions.isNotEmpty()
}
