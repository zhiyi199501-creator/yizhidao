package com.yizhidao

enum class CastingMethod(val raw: String, val displayName: String) {
    DIGITAL_MANUAL("digitalManual", "数字起卦·三数"),
    DIGITAL_TIME("digitalTime", "数字起卦·时间"),
    COIN("coin", "六爻金钱卦");

    companion object {
        fun fromRaw(raw: String): CastingMethod =
            entries.find { it.raw == raw } ?: DIGITAL_MANUAL
    }
}
