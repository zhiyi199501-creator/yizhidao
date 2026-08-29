import Foundation

/// 一掷三枚的落面。摇卦画面要把三枚分别画出来，光有 `LineValue` 不够。
struct CoinToss: Hashable, Sendable {
    /// 初 → 末三枚，`true` 为字面（阳）。
    let faces: [Bool]

    var yangCount: Int { faces.filter { $0 }.count }
    var line: LineValue { CoinCastingEngine.line(fromYangCount: yangCount) }
}

enum CoinCastingEngine {
    /// Character side (字) = yang 3; Manchu/back (背) = yin 2.
    static func line(fromYangCount yangCount: Int) -> LineValue {
        switch yangCount {
        case 3: return .oldYang   // 9
        case 2: return .youngYin  // 8
        case 1: return .youngYang // 7
        case 0: return .oldYin    // 6
        default:
            preconditionFailure("yangCount must be 0...3")
        }
    }

    static func toss(using rng: inout some RandomNumberGenerator) -> CoinToss {
        CoinToss(faces: (0..<3).map { _ in Bool.random(using: &rng) })
    }

    static func toss() -> CoinToss {
        var rng = SystemRandomNumberGenerator()
        return toss(using: &rng)
    }

    static func tossLine(using rng: inout some RandomNumberGenerator) -> LineValue {
        toss(using: &rng).line
    }

    /// Six tosses bottom → top.
    static func cast(
        lines: [LineValue],
        question: String? = nil,
        at date: Date = .now
    ) -> CastResult {
        precondition(lines.count == 6)
        let bits = lines.map(\.bit)
        let primary = KingWenTable.number(fromBits: bits)
        let moving = lines.enumerated().compactMap { idx, line in
            line.isChanging ? idx + 1 : nil
        }
        let resulting = KingWenTable.resultingNumber(
            primaryBits: bits,
            movingPositions: moving
        )
        return CastResult(
            method: .coin,
            createdAt: date,
            question: question,
            numbers: lines.map(\.rawValue),
            primaryNumber: primary,
            resultingNumber: resulting,
            lines: lines,
            movingPositions: moving
        )
    }

    static func castRandom(
        question: String? = nil,
        at date: Date = .now,
        using rng: inout some RandomNumberGenerator
    ) -> CastResult {
        let lines = (0..<6).map { _ in tossLine(using: &rng) }
        return cast(lines: lines, question: question, at: date)
    }

    static func castRandom(question: String? = nil, at date: Date = .now) -> CastResult {
        var rng = SystemRandomNumberGenerator()
        return castRandom(question: question, at: date, using: &rng)
    }
}
