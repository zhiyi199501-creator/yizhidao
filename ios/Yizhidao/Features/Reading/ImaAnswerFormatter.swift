import Foundation

enum ImaAnswerBlock: Equatable {
    case text(String)
    case table([[String]])
}

enum ImaAnswerFormatter {
    static func blocks(in raw: String) -> [ImaAnswerBlock] {
        let normalized = raw
            .replacingOccurrences(of: "\r\n", with: "\n")
            .replacingOccurrences(of: "\r", with: "\n")
        let lines = normalized.components(separatedBy: "\n")
        var blocks: [ImaAnswerBlock] = []
        var buffer: [String] = []
        var i = 0

        func flush() {
            let text = stripped(buffer.joined(separator: "\n").trimmingCharacters(in: .whitespacesAndNewlines))
            if !text.isEmpty {
                blocks.append(.text(text))
            }
            buffer.removeAll(keepingCapacity: true)
        }

        while i < lines.count {
            let line = lines[i]
            if line.trimmingCharacters(in: .whitespaces) == "表格" {
                var rows: [[String]] = []
                var j = i + 1
                while j < lines.count, lines[j].contains("\t") {
                    rows.append(lines[j].components(separatedBy: "\t").map { stripped($0.trimmingCharacters(in: .whitespaces)) })
                    j += 1
                }
                if !rows.isEmpty {
                    flush()
                    blocks.append(.table(padded(rows)))
                    i = j
                    continue
                }
            }
            if isPipeRow(line), i + 1 < lines.count, isMarkdownSeparator(lines[i + 1]) {
                var rows: [[String]] = [parsePipeRow(line).map { stripped($0) }]
                var j = i + 2
                while j < lines.count, isPipeRow(lines[j]) {
                    if isMarkdownSeparator(lines[j]) {
                        j += 1
                        continue
                    }
                    rows.append(parsePipeRow(lines[j]).map { stripped($0) })
                    j += 1
                }
                if rows.count >= 2 {
                    flush()
                    blocks.append(.table(padded(rows)))
                    i = j
                    continue
                }
            }
            buffer.append(line)
            i += 1
        }
        flush()
        return blocks
    }

    /// 去掉 IMA 界面漏进来的「思考过程」，以及点不开的出处脚注。
    static func stripped(_ text: String) -> String {
        let withoutThinking = text
            .components(separatedBy: "\n")
            .filter { $0.trimmingCharacters(in: .whitespaces) != "思考过程" }
            .joined(separator: "\n")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let chars = Array(withoutThinking)
        let n = chars.count
        var i = 0
        var bodyStart = 0
        var result = ""

        func flush(_ end: Int) {
            if end > bodyStart {
                result.append(contentsOf: chars[bodyStart..<end])
            }
            bodyStart = end
        }

        while i < n {
            let ch = chars[i]
            if ch >= "1", ch <= "9" {
                var j = i
                while j < n, j - i < 5, chars[j] >= "0", chars[j] <= "9" {
                    j += 1
                }
                let moreDigits = j < n && chars[j] >= "0" && chars[j] <= "9"
                let listMarker = j < n && (chars[j] == "." || chars[j] == "、" || chars[j] == "．")
                if !moreDigits && !listMarker {
                    var k = j
                    while k < n, chars[k] == " " || chars[k] == "\t" {
                        k += 1
                    }
                    let atEnd = k == n || chars[k] == "\n"
                    let beforePunct = j < n && isSentencePunct(chars[j])
                    if atEnd || beforePunct {
                        var p = i - 1
                        var spaceStart = i
                        while p >= 0, chars[p] == " " || chars[p] == "\t" {
                            spaceStart = p
                            p -= 1
                        }
                        let notLineStart = p >= 0 && chars[p] != "\n"
                        if notLineStart {
                            let afterPunct = isSentencePunct(chars[p])
                            let afterWord = !isDigit(chars[p])
                            if afterPunct || afterWord {
                                flush(spaceStart)
                                bodyStart = j
                                i = j
                                continue
                            }
                        }
                    }
                }
            }
            i += 1
        }
        flush(n)
        return result
    }

    private static func isDigit(_ ch: Character) -> Bool {
        ch >= "0" && ch <= "9"
    }

    private static func isSentencePunct(_ ch: Character) -> Bool {
        ch == "。" || ch == "！" || ch == "？"
    }

    private static func isPipeRow(_ line: String) -> Bool {
        let trimmed = line.trimmingCharacters(in: .whitespaces)
        guard trimmed.hasPrefix("|"), trimmed.hasSuffix("|") else { return false }
        return trimmed.filter { $0 == "|" }.count >= 2
    }

    private static func isMarkdownSeparator(_ line: String) -> Bool {
        let trimmed = line.trimmingCharacters(in: .whitespaces)
        guard trimmed.contains("-") else { return false }
        return trimmed.allSatisfy { "|-: ".contains($0) }
    }

    private static func parsePipeRow(_ line: String) -> [String] {
        var trimmed = line.trimmingCharacters(in: .whitespaces)
        if trimmed.hasPrefix("|") { trimmed.removeFirst() }
        if trimmed.hasSuffix("|") { trimmed.removeLast() }
        return trimmed.split(separator: "|", omittingEmptySubsequences: false).map {
            $0.trimmingCharacters(in: .whitespaces)
        }
    }

    private static func padded(_ rows: [[String]]) -> [[String]] {
        let width = rows.map(\.count).max() ?? 0
        guard width > 0 else { return rows }
        return rows.map { row in
            if row.count >= width { return Array(row.prefix(width)) }
            return row + Array(repeating: "", count: width - row.count)
        }
    }
}
