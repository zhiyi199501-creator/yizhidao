package com.yizhidao

enum class VerificationStatus(val raw: String, val displayName: String) {
    NONE("none", "未验证"),
    FULFILLED("fulfilled", "应验"),
    PARTIAL("partial", "部分应验"),
    UNFULFILLED("unfulfilled", "未应验");

    companion object {
        fun fromRaw(raw: String): VerificationStatus =
            entries.find { it.raw == raw } ?: NONE
    }
}
