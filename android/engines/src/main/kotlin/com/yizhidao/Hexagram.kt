package com.yizhidao

import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class HexagramYong(
    val ci: String,
    val xiang: String,
)

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
    val figure: String = "",
    val part: String = "",
    val title: String = "",
    val yong: HexagramYong? = null,
    val wenyan: List<String> = emptyList(),
) {
    fun yaoCi(position: Int): String {
        if (position !in 1..6 || yaoci.size < position) return ""
        return yaoci[position - 1]
    }

    fun xiaoXiang(position: Int): String {
        if (position !in 1..6 || xiaoxiang.size < position) return ""
        return xiaoxiang[position - 1]
    }

    /** 初爻→上爻，由 binary 解析（1 阳 0 阴）。 */
    val figureLines: List<LineValue>
        get() = binary.map { ch -> LineValue.from(isYang = ch == '1', changing = false) }
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
