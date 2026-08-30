package com.yizhidao.app.ai

/**
 * 与 iOS `AIAnswerFormatter` 同规则：AI 常把解读写成一整段，手机上读起来很挤。
 * 只在展示层分段，不动存下来的原文：先按换行切块，再只在句末（。！？…）断开，
 * 累计约 60 字成段，末段过短并回上一段。
 */
object AIAnswerFormatter {
    private const val SOFT_LIMIT = 60
    private const val MIN_TAIL_LENGTH = 24
    private val ENDERS = setOf('。', '！', '？', '…', '!', '?', '.')
    private val CLOSERS = setOf('」', '』', '”', '’', '）', ')', '》', '〉', '】', '、')

    fun paragraphs(raw: String): List<String> = raw
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .split('\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .flatMap { paragraphsInBlock(it) }

    private fun paragraphsInBlock(block: String): List<String> {
        if (block.length <= SOFT_LIMIT) return listOf(block)
        val result = mutableListOf<String>()
        val current = StringBuilder()
        for (sentence in sentences(block)) {
            current.append(sentence)
            if (current.length >= SOFT_LIMIT) {
                result.add(current.toString())
                current.setLength(0)
            }
        }
        if (current.isNotEmpty()) {
            if (current.length < MIN_TAIL_LENGTH && result.isNotEmpty()) {
                result[result.lastIndex] = result.last() + current
            } else {
                result.add(current.toString())
            }
        }
        return result.ifEmpty { listOf(block) }
    }

    /** 句末标点后紧跟的引号、括号归上一句；连写的「？！」也算同一句。 */
    private fun sentences(block: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var ended = false
        for (ch in block) {
            if (ended) {
                if (ch in ENDERS || ch in CLOSERS) {
                    current.append(ch)
                    continue
                }
                result.add(current.toString())
                current.setLength(0)
                ended = false
            }
            current.append(ch)
            if (ch in ENDERS) ended = true
        }
        if (current.isNotEmpty()) result.add(current.toString())
        return result
    }
}
