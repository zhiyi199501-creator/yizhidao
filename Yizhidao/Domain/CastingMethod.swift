import Foundation

enum CastingMethod: String, Codable, CaseIterable, Identifiable, Sendable {
    case digitalManual
    case digitalTime
    case coin

    var id: String { rawValue }

    var displayName: String {
        let raw: String
        switch self {
        case .digitalManual: raw = "数字起卦·三数"
        case .digitalTime: raw = "数字起卦·时间"
        case .coin: raw = "六爻金钱卦"
        }
        return raw.zh
    }
}
