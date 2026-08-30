package com.yizhidao.app.lang

import com.yizhidao.CastingMethod
import com.yizhidao.Hexagram
import com.yizhidao.HexagramNames
import com.yizhidao.VerificationStatus

fun Hexagram.listLabel(language: AppLanguage): String =
    if (language.isEnglish) {
        "$symbol $name  ${HexagramNames.pinyin(number)}"
    } else {
        language.convert("$symbol $name")
    }

fun Hexagram.displayName(language: AppLanguage): String {
    val raw = if (name.endsWith("卦")) name else name + "卦"
    return if (language.isEnglish) {
        "$raw ${HexagramNames.pinyin(number)}"
    } else {
        language.convert(raw)
    }
}

fun Hexagram.roleCaption(language: AppLanguage, roleZH: String, roleEN: String): String =
    language.ui("第${number}卦 · $roleZH", "Hexagram $number · $roleEN")

fun Hexagram.numberLabel(language: AppLanguage): String =
    language.ui("第${number}卦", "Hexagram $number")

fun numberLabel(language: AppLanguage, number: Int): String =
    language.ui("第${number}卦", "Hexagram $number")

fun CastingMethod.localizedName(language: AppLanguage): String = when (this) {
    CastingMethod.DIGITAL_MANUAL -> language.ui("数字起卦·三数", "Three numbers")
    CastingMethod.DIGITAL_TIME -> language.ui("数字起卦·时间", "Time")
    CastingMethod.COIN -> language.ui("六爻金钱卦", "Three coins")
}

fun VerificationStatus.localizedName(language: AppLanguage): String = when (this) {
    VerificationStatus.NONE -> language.ui("未验证", "Unverified")
    VerificationStatus.FULFILLED -> language.ui("应验", "Fulfilled")
    VerificationStatus.PARTIAL -> language.ui("部分应验", "Partly fulfilled")
    VerificationStatus.UNFULFILLED -> language.ui("未应验", "Not fulfilled")
}
