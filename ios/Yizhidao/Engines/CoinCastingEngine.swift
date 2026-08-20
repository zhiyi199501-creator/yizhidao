import Foundation

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

    static func tossLine(using rng: inout some RandomNumberGenerator) -> LineValue {
        var yang = 0
        for _ in 0..<3 {
            if Bool.random(using: &rng) { yang += 1 }
        }
        return line(fromYangCount: yang)
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
