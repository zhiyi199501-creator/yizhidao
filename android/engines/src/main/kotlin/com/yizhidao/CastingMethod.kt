package com.yizhidao

enum class CastingMethod(val raw: String, val displayName: String) {
    DIGITAL_MANUAL("digitalManual", "数字起卦·三数"),
    DIGITAL_TIME("digitalTime", "数字起卦·时间"),
    COIN("coin", "六爻金钱卦");

    val isDigital: Boolean
        get() = this == DIGITAL_MANUAL || this == DIGITAL_TIME

    companion object {
        fun fromRaw(raw: String): CastingMethod =
            entries.find { it.raw == raw } ?: DIGITAL_MANUAL
    }
}

/** 数字起卦单爻动时，箭头上的动爻字（初…上）。 */
fun digitalMovingYaoLabel(method: CastingMethod, movingPositions: List<Int>): String? {
    if (!method.isDigital || movingPositions.size != 1) return null
    return when (movingPositions.first()) {
        1 -> "初"
        2 -> "二"
        3 -> "三"
        4 -> "四"
        5 -> "五"
        6 -> "上"
        else -> null
    }
}
