package com.yizhidao

object HexagramText {
    fun prefixed(prefix: String, body: String): String {
        val text = body.trim()
        if (text.startsWith(prefix)) return text
        val bare = prefix.dropLast(1)
        if (text.startsWith(bare)) {
            val rest = text.drop(bare.length).trimStart()
            if (rest.startsWith("：") || rest.startsWith(":")) {
                return bare + rest
            }
            return prefix + rest
        }
        return prefix + text
    }

    fun xiangLine(text: String): String {
        val xiang = text.trim()
        if (xiang.startsWith("象曰：")) return xiang
        if (xiang.startsWith("象曰")) {
            val rest = xiang.drop(2).trimStart()
            if (rest.startsWith("：") || rest.startsWith(":")) {
                return "象曰$rest"
            }
            return "象曰：$rest"
        }
        return "象曰：$xiang"
    }

    fun yaoStemLabel(position: Int, line: LineValue): String {
        val names = listOf("初", "二", "三", "四", "五", "上")
        val stem = if (line.isYang) "九" else "六"
        return when (position) {
            1 -> "初$stem"
            6 -> "上$stem"
            else -> "$stem${names[position - 1]}"
        }
    }
}
