import Foundation

enum Trigram: Int, CaseIterable, Sendable {
    case qian = 1, dui = 2, li = 3, zhen = 4
    case xun = 5, kan = 6, gen = 7, kun = 8

    var name: String {
        switch self {
        case .qian: return "乾"
        case .dui: return "兑"
        case .li: return "离"
        case .zhen: return "震"
        case .xun: return "巽"
        case .kan: return "坎"
        case .gen: return "艮"
        case .kun: return "坤"
        }
    }

    /// Bottom → top, yang = 1.
    var bits: [Int] {
        switch self {
        case .qian: return [1, 1, 1]
        case .dui: return [1, 1, 0]
        case .li: return [1, 0, 1]
        case .zhen: return [1, 0, 0]
        case .xun: return [0, 1, 1]
        case .kan: return [0, 1, 0]
        case .gen: return [0, 0, 1]
        case .kun: return [0, 0, 0]
        }
    }

    static func fromMod8(_ value: Int) -> Trigram {
        let r = ((value % 8) + 8) % 8
        let n = r == 0 ? 8 : r
        return Trigram(rawValue: n) ?? .kun
    }
}

enum KingWenTable {
    /// King Wen binaries bottom→top (yang=1), index 0 = hexagram #1 乾.
    private static let orderedBinaries: [String] = [
        "111111", "000000", "100010", "010001",
        "111010", "010111", "010000", "000010",
        "111011", "110111", "111000", "000111",
        "101111", "111101", "001000", "000100",
        "100110", "011001", "110000", "000011",
        "100101", "101001", "000001", "100000",
        "100111", "111001", "100001", "011110",
        "010010", "101101", "001110", "011100",
        "001111", "111100", "000101", "101000",
        "101011", "110101", "001010", "010100",
        "110001", "100011", "111110", "011111",
        "000110", "011000", "010110", "011010",
        "101110", "011101", "100100", "001001",
        "001011", "110100", "101100", "001101",
        "011011", "110110", "010011", "110010",
        "110011", "001100", "101010", "010101",
    ]

    private static let binaryToNumber: [String: Int] = {
        var map: [String: Int] = [:]
        for (idx, binary) in orderedBinaries.enumerated() {
            map[binary] = idx + 1
        }
        return map
    }()

    static func number(fromBits bits: [Int]) -> Int {
        precondition(bits.count == 6)
        let key = bits.map(String.init).joined()
        guard let n = binaryToNumber[key] else {
            preconditionFailure("Unknown hexagram binary \(key)")
        }
        return n
    }

    static func number(lower: Trigram, upper: Trigram) -> Int {
        number(fromBits: lower.bits + upper.bits)
    }

    static func bits(ofNumber number: Int) -> [Int] {
        precondition((1...64).contains(number))
        return orderedBinaries[number - 1].map { Int(String($0))! }
    }

    static func binary(ofNumber number: Int) -> String {
        precondition((1...64).contains(number))
        return orderedBinaries[number - 1]
    }

    /// Flip changing positions (1-based) → resulting King Wen number; nil if none.
    static func resultingNumber(primaryBits: [Int], movingPositions: [Int]) -> Int? {
        guard !movingPositions.isEmpty else { return nil }
        var bits = primaryBits
        for p in movingPositions {
            let i = p - 1
            guard (0..<6).contains(i) else { continue }
            bits[i] = bits[i] == 1 ? 0 : 1
        }
        return number(fromBits: bits)
    }
}
