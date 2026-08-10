import Foundation

/// Coin / yarrow line values: 6 old yin, 7 young yang, 8 young yin, 9 old yang.
enum LineValue: Int, Codable, Hashable, Sendable {
    case oldYin = 6
    case youngYang = 7
    case youngYin = 8
    case oldYang = 9

    var isYang: Bool {
        switch self {
        case .youngYang, .oldYang: return true
        case .youngYin, .oldYin: return false
        }
    }

    var isChanging: Bool {
        switch self {
        case .oldYin, .oldYang: return true
        case .youngYin, .youngYang: return false
        }
    }

    var changed: LineValue {
        switch self {
        case .oldYin: return .youngYang
        case .oldYang: return .youngYin
        case .youngYang, .youngYin: return self
        }
    }

    /// Binary bit for King Wen lookup: yang = 1, yin = 0.
    var bit: Int { isYang ? 1 : 0 }

    static func from(isYang: Bool, changing: Bool) -> LineValue {
        switch (isYang, changing) {
        case (true, true): return .oldYang
        case (true, false): return .youngYang
        case (false, true): return .oldYin
        case (false, false): return .youngYin
        }
    }
}
