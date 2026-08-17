package com.yizhidao

/** Coin / yarrow line values: 6 old yin, 7 young yang, 8 young yin, 9 old yang. */
enum class LineValue(val rawValue: Int) {
    OLD_YIN(6),
    YOUNG_YANG(7),
    YOUNG_YIN(8),
    OLD_YANG(9);

    val isYang: Boolean
        get() = this == YOUNG_YANG || this == OLD_YANG

    val isChanging: Boolean
        get() = this == OLD_YIN || this == OLD_YANG

    val changed: LineValue
        get() = when (this) {
            OLD_YIN -> YOUNG_YANG
            OLD_YANG -> YOUNG_YIN
            YOUNG_YANG, YOUNG_YIN -> this
        }

    /** Binary bit for King Wen lookup: yang = 1, yin = 0. */
    val bit: Int
        get() = if (isYang) 1 else 0

    val displayLabel: String
        get() = when (this) {
            OLD_YANG -> "阳动 9"
            OLD_YIN -> "阴动 6"
            YOUNG_YANG -> "少阳 7"
            YOUNG_YIN -> "少阴 8"
        }

    companion object {
        fun fromRaw(raw: Int): LineValue? = entries.find { it.rawValue == raw }

        fun from(isYang: Boolean, changing: Boolean): LineValue = when {
            isYang && changing -> OLD_YANG
            isYang && !changing -> YOUNG_YANG
            !isYang && changing -> OLD_YIN
            else -> YOUNG_YIN
        }
    }
}
