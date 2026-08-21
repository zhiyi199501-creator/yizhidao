package com.yizhidao.app.ima

sealed class ImaAnswerBlock {
    data class Text(val text: String) : ImaAnswerBlock()
    data class Table(val rows: List<List<String>>) : ImaAnswerBlock()
}

object ImaAnswerFormatter {
    fun blocks(raw: String): List<ImaAnswerBlock> {
        val lines = raw.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val blocks = mutableListOf<ImaAnswerBlock>()
        val buffer = mutableListOf<String>()
        var i = 0

        fun flush() {
            val text = stripped(buffer.joinToString("\n").trim())
            if (text.isNotEmpty()) blocks += ImaAnswerBlock.Text(text)
            buffer.clear()
        }

        while (i < lines.size) {
            val line = lines[i]
            if (line.trim() == "表格") {
                val rows = mutableListOf<List<String>>()
                var j = i + 1
                while (j < lines.size && '\t' in lines[j]) {
                    rows += lines[j].split('\t').map { stripped(it.trim()) }
                    j++
                }
                if (rows.isNotEmpty()) {
                    flush()
                    blocks += ImaAnswerBlock.Table(padded(rows))
                    i = j
                    continue
                }
            }
            if (isPipeRow(line) && i + 1 < lines.size && isMarkdownSeparator(lines[i + 1])) {
                val rows = mutableListOf(parsePipeRow(line).map(::stripped))
                var j = i + 2
                while (j < lines.size && isPipeRow(lines[j])) {
                    if (isMarkdownSeparator(lines[j])) {
                        j++
                        continue
                    }
                    rows += parsePipeRow(lines[j]).map(::stripped)
                    j++
                }
                if (rows.size >= 2) {
                    flush()
                    blocks += ImaAnswerBlock.Table(padded(rows))
                    i = j
                    continue
                }
            }
            buffer += line
            i++
        }
        flush()
        return blocks
    }

    /** 去掉 IMA 界面漏进来的「思考过程」，以及点不开的出处脚注。 */
    fun stripped(text: String): String {
        val withoutThinking = text
            .lineSequence()
            .filter { it.trim() != "思考过程" }
            .joinToString("\n")
            .trim()
        val n = withoutThinking.length
        var i = 0
        var bodyStart = 0
        val result = StringBuilder()

        fun flush(end: Int) {
            if (end > bodyStart) result.append(withoutThinking, bodyStart, end)
            bodyStart = end
        }

        fun isDigit(ch: Char) = ch in '0'..'9'
        fun isSentencePunct(ch: Char) = ch == '。' || ch == '！' || ch == '？'

        while (i < n) {
            val ch = withoutThinking[i]
            if (ch in '1'..'9') {
                var j = i
                while (j < n && j - i < 5 && isDigit(withoutThinking[j])) j++
                val moreDigits = j < n && isDigit(withoutThinking[j])
                val listMarker = j < n && (withoutThinking[j] == '.' || withoutThinking[j] == '、' || withoutThinking[j] == '．')
                if (!moreDigits && !listMarker) {
                    var k = j
                    while (k < n && (withoutThinking[k] == ' ' || withoutThinking[k] == '\t')) k++
                    val atEnd = k == n || withoutThinking[k] == '\n'
                    val beforePunct = j < n && isSentencePunct(withoutThinking[j])
                    if (atEnd || beforePunct) {
                        var p = i - 1
                        var spaceStart = i
                        while (p >= 0 && (withoutThinking[p] == ' ' || withoutThinking[p] == '\t')) {
                            spaceStart = p
                            p--
                        }
                        val notLineStart = p >= 0 && withoutThinking[p] != '\n'
                        if (notLineStart) {
                            val afterPunct = isSentencePunct(withoutThinking[p])
                            val afterWord = !isDigit(withoutThinking[p])
                            if (afterPunct || afterWord) {
                                flush(spaceStart)
                                bodyStart = j
                                i = j
                                continue
                            }
                        }
                    }
                }
            }
            i++
        }
        flush(n)
        return result.toString()
    }

    private fun isPipeRow(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed.startsWith("|") && trimmed.endsWith("|") && trimmed.count { it == '|' } >= 2
    }

    private fun isMarkdownSeparator(line: String): Boolean {
        val trimmed = line.trim()
        if ('-' !in trimmed) return false
        return trimmed.all { it == '|' || it == '-' || it == ':' || it == ' ' }
    }

    private fun parsePipeRow(line: String): List<String> {
        var trimmed = line.trim()
        if (trimmed.startsWith("|")) trimmed = trimmed.drop(1)
        if (trimmed.endsWith("|")) trimmed = trimmed.dropLast(1)
        return trimmed.split('|').map { it.trim() }
    }

    private fun padded(rows: List<List<String>>): List<List<String>> {
        val width = rows.maxOfOrNull { it.size } ?: 0
        if (width == 0) return rows
        return rows.map { row ->
            when {
                row.size == width -> row
                row.size > width -> row.take(width)
                else -> row + List(width - row.size) { "" }
            }
        }
    }
}
