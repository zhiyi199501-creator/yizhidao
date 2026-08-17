package com.yizhidao

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/** 单个占卦案例（来自《张庆祥讲易经案例》转写稿总结） */
@Serializable
data class CaseStudy(
    val file: String,
    val hexagram: String,
    val position: String,
    val background: String,
    val question: String,
    val casting: String,
    val explanation: String,
    val verification: String,
    val number: Int,
) {
    /** 1-based 动爻位。支持「初爻」「三爻、四爻」等写法。 */
    val movingPositions: List<Int>
        get() {
            val names = listOf("初爻", "二爻", "三爻", "四爻", "五爻", "上爻")
            return names.mapIndexedNotNull { index, name ->
                if (position.contains(name)) index + 1 else null
            }
        }

    val lines: List<LineValue>
        get() {
            if (number !in 1..64) return emptyList()
            val moving = movingPositions.toSet()
            return KingWenTable.bits(ofNumber = number).mapIndexed { index, bit ->
                LineValue.from(isYang = bit == 1, changing = (index + 1) in moving)
            }
        }

    val resultingNumber: Int?
        get() {
            if (number !in 1..64) return null
            return KingWenTable.resultingNumber(
                primaryBits = KingWenTable.bits(ofNumber = number),
                movingPositions = movingPositions,
            )
        }
}

object CaseStudyCodec {
    fun decodeList(text: String): List<CaseStudy> =
        HexagramStore.json.decodeFromString(ListSerializer(CaseStudy.serializer()), text)
}
