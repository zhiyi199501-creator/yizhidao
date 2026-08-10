import Foundation

enum DigitalCastingEngine {
    /// Remainder rules from lecture notes: mod8→0 means 坤(8); mod6→0 means 上爻(6).
    static func mod8(_ value: Int) -> Int {
        let r = ((value % 8) + 8) % 8
        return r == 0 ? 8 : r
    }

    static func mod6(_ value: Int) -> Int {
        let r = ((value % 6) + 6) % 6
        return r == 0 ? 6 : r
    }

    /// Three-number method: n1 upper, n2 lower, n3 moving line (1...6).
    static func cast(
        number1: Int,
        number2: Int,
        number3: Int,
        question: String? = nil,
        at date: Date = .now
    ) -> CastResult {
        let upper = Trigram.fromMod8(number1)
        let lower = Trigram.fromMod8(number2)
        let moving = mod6(number3)
        return buildResult(
            lower: lower,
            upper: upper,
            movingPosition: moving,
            method: .digitalManual,
            numbers: [number1, number2, number3],
            question: question,
            date: date
        )
    }

    /// Time method: yearBranch / month / day / hourBranch 均为 1...12（时为时辰）。
    static func cast(
        yearBranch: Int,
        month: Int,
        day: Int,
        hour: Int,
        question: String? = nil,
        at date: Date = .now
    ) -> CastResult {
        let upperSum = yearBranch + month + day
        let total = upperSum + hour
        let upper = Trigram.fromMod8(upperSum)
        let lower = Trigram.fromMod8(total)
        let moving = mod6(total)
        return buildResult(
            lower: lower,
            upper: upper,
            movingPosition: moving,
            method: .digitalTime,
            numbers: [yearBranch, month, day, hour],
            question: question,
            date: date
        )
    }

    private static func buildResult(
        lower: Trigram,
        upper: Trigram,
        movingPosition: Int,
        method: CastingMethod,
        numbers: [Int],
        question: String?,
        date: Date
    ) -> CastResult {
        let bits = lower.bits + upper.bits
        let primary = KingWenTable.number(fromBits: bits)
        var lines: [LineValue] = bits.map { bit in
            LineValue.from(isYang: bit == 1, changing: false)
        }
        let idx = movingPosition - 1
        if (0..<6).contains(idx) {
            lines[idx] = LineValue.from(isYang: bits[idx] == 1, changing: true)
        }
        let resulting = KingWenTable.resultingNumber(
            primaryBits: bits,
            movingPositions: [movingPosition]
        )
        return CastResult(
            method: method,
            createdAt: date,
            question: question,
            numbers: numbers,
            primaryNumber: primary,
            resultingNumber: resulting,
            lines: lines,
            movingPositions: [movingPosition]
        )
    }
}
