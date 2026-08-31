import Foundation

enum CastingMethod: String, Codable, CaseIterable, Identifiable, Sendable {
    case digitalManual
    case digitalTime
    case coin

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .digitalManual: return "数字起卦·三数".ui("Three numbers")
        case .digitalTime: return "数字起卦·时间".ui("Time")
        case .coin: return "六爻金钱卦".ui("Three coins")
        }
    }

    var isDigital: Bool {
        switch self {
        case .digitalManual, .digitalTime: return true
        case .coin: return false
        }
    }
}
