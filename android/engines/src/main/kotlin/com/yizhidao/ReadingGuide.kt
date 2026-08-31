package com.yizhidao

/** 解卦焦点（据动爻数量：本卦／之卦、卦辞／爻辞，及何爻为主）。 */
data class ReadingFocus(
    val kind: Kind,
    /** 展示用说明，如「二爻变：本卦两动爻爻辞，以上爻为主」 */
    val summary: String,
) {
    sealed class Kind {
        /** 主看本卦卦辞（及大象） */
        data object PrimaryGuaci : Kind()

        /** 主看本卦若干爻的爻辞／小象；[lead] 为「为主」之爻（若有） */
        data class PrimaryLines(val positions: List<Int>, val lead: Int?) : Kind()

        /** 主看本卦、之卦卦辞；本卦为主 */
        data object BothGuaci : Kind()

        /** 主看之卦若干静爻的爻辞／小象；[lead] 为「为主」之爻（若有） */
        data class ResultingLines(val positions: List<Int>, val lead: Int?) : Kind()

        /** 主看之卦卦辞（及大象） */
        data object ResultingGuaci : Kind()
    }
}

object ReadingGuide {
    /**
     * @param movingPositions 1-based 动爻位，初爻=1 … 上爻=6
     */
    fun focus(movingPositions: List<Int>, movingCount: Int? = null): ReadingFocus {
        val moving = movingPositions.filter { it in 1..6 }.toSet().sorted()
        val count = movingCount ?: moving.size

        return when (count) {
            0 -> ReadingFocus(
                kind = ReadingFocus.Kind.PrimaryGuaci,
                summary = "六爻皆不变：主看本卦卦辞。",
            )
            1 -> {
                val pos = moving.firstOrNull()
                ReadingFocus(
                    kind = ReadingFocus.Kind.PrimaryLines(positions = moving, lead = pos),
                    summary = "一爻变：主看本卦${pos?.let { yaoName(it) } ?: "动爻"}爻辞。",
                )
            }
            2 -> {
                val lead = moving.lastOrNull()
                ReadingFocus(
                    kind = ReadingFocus.Kind.PrimaryLines(positions = moving, lead = lead),
                    summary = "二爻变：主看本卦两动爻爻辞，以${lead?.let { yaoName(it) } ?: "上爻"}为主。",
                )
            }
            3 -> ReadingFocus(
                kind = ReadingFocus.Kind.BothGuaci,
                summary = "三爻变：主看本卦、之卦卦辞，以本卦为主。",
            )
            4 -> {
                val statics = staticPositions(moving)
                val lead = statics.firstOrNull()
                ReadingFocus(
                    kind = ReadingFocus.Kind.ResultingLines(positions = statics, lead = lead),
                    summary = "四爻变：主看之卦两静爻爻辞，以${lead?.let { yaoName(it) } ?: "下爻"}为主。",
                )
            }
            5 -> {
                val statics = staticPositions(moving)
                ReadingFocus(
                    kind = ReadingFocus.Kind.ResultingLines(positions = statics, lead = statics.firstOrNull()),
                    summary = "五爻变：主看之卦静爻${statics.firstOrNull()?.let { yaoName(it) } ?: ""}爻辞。",
                )
            }
            else -> ReadingFocus(
                kind = ReadingFocus.Kind.ResultingGuaci,
                summary = "六爻皆变：主看之卦卦辞。",
            )
        }
    }

    /** 通则：本卦为目前，之卦为将来趋势。 */
    const val GENERAL_PRINCIPLE =
        "所问之事，以本卦为目前情况，之卦为将来趋势；并参照两卦卦辞。"

    /** 主看那一句经文（卦辞或爻辞），给问答页作引。经文不英译。 */
    fun leadJingwen(
        movingPositions: List<Int>,
        primary: Hexagram?,
        resulting: Hexagram?,
    ): String? {
        val raw = when (val kind = focus(movingPositions).kind) {
            ReadingFocus.Kind.PrimaryGuaci, ReadingFocus.Kind.BothGuaci -> primary?.guaci
            is ReadingFocus.Kind.PrimaryLines -> kind.lead?.let { primary?.yaoCi(it) }
            is ReadingFocus.Kind.ResultingLines -> kind.lead?.let { resulting?.yaoCi(it) }
            ReadingFocus.Kind.ResultingGuaci -> resulting?.guaci
        }
        val text = raw?.trim().orEmpty()
        return text.ifEmpty { null }
    }

    private fun staticPositions(moving: List<Int>): List<Int> =
        (1..6).filter { it !in moving }

    fun yaoName(position: Int): String = when (position) {
        1 -> "初爻"
        2 -> "二爻"
        3 -> "三爻"
        4 -> "四爻"
        5 -> "五爻"
        6 -> "上爻"
        else -> "爻"
    }
}
