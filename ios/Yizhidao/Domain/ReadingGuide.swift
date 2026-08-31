import Foundation

/// 解卦焦点（据动爻数量：本卦／之卦、卦辞／爻辞，及何爻为主）。
struct ReadingFocus: Equatable, Sendable {
    enum Kind: Equatable, Sendable {
        /// 主看本卦卦辞（及大象）
        case primaryGuaci
        /// 主看本卦若干爻的爻辞／小象；`lead` 为「为主」之爻（若有）
        case primaryLines(positions: [Int], lead: Int?)
        /// 主看本卦、之卦卦辞；本卦为主
        case bothGuaci
        /// 主看之卦若干静爻的爻辞／小象；`lead` 为「为主」之爻（若有）
        case resultingLines(positions: [Int], lead: Int?)
        /// 主看之卦卦辞（及大象）
        case resultingGuaci
    }

    let kind: Kind
    /// 展示用说明，如「二爻变：本卦两动爻爻辞，以上爻为主」
    let summary: String
}

enum ReadingGuide {
    /// - Parameter movingPositions: 1-based 动爻位，初爻=1 … 上爻=6
    static func focus(movingCount: Int? = nil, movingPositions: [Int]) -> ReadingFocus {
        let moving = Array(Set(movingPositions.filter { (1...6).contains($0) })).sorted()
        let count = movingCount ?? moving.count

        switch count {
        case 0:
            return ReadingFocus(
                kind: .primaryGuaci,
                summary: "六爻皆不变：主看本卦卦辞。"
            )
        case 1:
            let pos = moving.first
            return ReadingFocus(
                kind: .primaryLines(positions: moving, lead: pos),
                summary: "一爻变：主看本卦\(pos.map(yaoName) ?? "动爻")爻辞。"
            )
        case 2:
            let lead = moving.last // 上爻为主
            return ReadingFocus(
                kind: .primaryLines(positions: moving, lead: lead),
                summary: "二爻变：主看本卦两动爻爻辞，以\(lead.map(yaoName) ?? "上爻")为主。"
            )
        case 3:
            return ReadingFocus(
                kind: .bothGuaci,
                summary: "三爻变：主看本卦、之卦卦辞，以本卦为主。"
            )
        case 4:
            let statics = staticPositions(moving: moving)
            let lead = statics.first // 下爻为主
            return ReadingFocus(
                kind: .resultingLines(positions: statics, lead: lead),
                summary: "四爻变：主看之卦两静爻爻辞，以\(lead.map(yaoName) ?? "下爻")为主。"
            )
        case 5:
            let statics = staticPositions(moving: moving)
            return ReadingFocus(
                kind: .resultingLines(positions: statics, lead: statics.first),
                summary: "五爻变：主看之卦静爻\(statics.first.map(yaoName) ?? "")爻辞。"
            )
        default: // 6
            return ReadingFocus(
                kind: .resultingGuaci,
                summary: "六爻皆变：主看之卦卦辞。"
            )
        }
    }

    /// 通则：本卦为目前，之卦为将来趋势。
    static let generalPrinciple =
        "所问之事，以本卦为目前情况，之卦为将来趋势；并参照两卦卦辞。"

    /// 主看那一句经文（卦辞或爻辞），给问答页作引。经文不英译。
    static func leadJingwen(
        movingPositions: [Int],
        primary: Hexagram?,
        resulting: Hexagram?
    ) -> String? {
        let raw: String?
        switch focus(movingPositions: movingPositions).kind {
        case .primaryGuaci, .bothGuaci:
            raw = primary?.guaci
        case .primaryLines(_, let lead):
            raw = lead.flatMap { primary?.yaoCi(at: $0) }
        case .resultingLines(_, let lead):
            raw = lead.flatMap { resulting?.yaoCi(at: $0) }
        case .resultingGuaci:
            raw = resulting?.guaci
        }
        let text = raw?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return text.isEmpty ? nil : text
    }

    private static func staticPositions(moving: [Int]) -> [Int] {
        (1...6).filter { !moving.contains($0) }
    }

    private static func yaoName(_ position: Int) -> String {
        switch position {
        case 1: return "初爻"
        case 2: return "二爻"
        case 3: return "三爻"
        case 4: return "四爻"
        case 5: return "五爻"
        case 6: return "上爻"
        default: return "爻"
        }
    }
}
