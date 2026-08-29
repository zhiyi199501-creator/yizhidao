import Foundation

/// AI 常把解读写成一整段，手机上读起来很挤。这里只在展示层分段，不动存下来的原文：
/// 先按换行切块，再只在句末（。！？…）断开，累计约 60 字成段，末段过短并回上一段。
enum AIAnswerFormatter {
    static func paragraphs(in raw: String) -> [String] {
        let blocks = raw
            .replacingOccurrences(of: "\r\n", with: "\n")
            .replacingOccurrences(of: "\r", with: "\n")
            .components(separatedBy: "\n")
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty }
        return blocks.flatMap(paragraphs(inBlock:))
    }

    private static let softLimit = 60
    private static let minTailLength = 24
    private static let enders: Set<Character> = ["。", "！", "？", "…", "!", "?"]
    private static let closers: Set<Character> = ["」", "』", "”", "’", "）", ")", "》", "〉", "】", "、"]

    private static func paragraphs(inBlock block: String) -> [String] {
        guard block.count > softLimit else { return [block] }
        var result: [String] = []
        var current = ""
        for sentence in sentences(in: block) {
            current += sentence
            if current.count >= softLimit {
                result.append(current)
                current = ""
            }
        }
        if !current.isEmpty {
            if current.count < minTailLength, let last = result.popLast() {
                result.append(last + current)
            } else {
                result.append(current)
            }
        }
        return result.isEmpty ? [block] : result
    }

    /// 句末标点后紧跟的引号、括号归上一句；连写的「？！」也算同一句。
    private static func sentences(in block: String) -> [String] {
        var result: [String] = []
        var current = ""
        var ended = false
        for ch in block {
            if ended {
                if enders.contains(ch) || closers.contains(ch) {
                    current.append(ch)
                    continue
                }
                result.append(current)
                current = ""
                ended = false
            }
            current.append(ch)
            if enders.contains(ch) { ended = true }
        }
        if !current.isEmpty { result.append(current) }
        return result
    }
}
