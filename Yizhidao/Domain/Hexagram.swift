import Foundation

struct Hexagram: Codable, Identifiable, Hashable, Sendable {
    let number: Int
    let name: String
    let symbol: String
    let binary: String
    let guaci: String
    /// 彖辞（《彖》曰），不含「彖曰」前缀
    let tuanci: String
    let yaoci: [String]
    /// 大象辞（《象》曰总释），不含「象曰」前缀
    let daxiang: String
    /// 小象辞，初爻→上爻共六条
    let xiaoxiang: [String]

    var id: Int { number }

    func yaoCi(at position: Int) -> String {
        guard position >= 1, position <= 6, yaoci.count >= position else { return "" }
        return yaoci[position - 1]
    }

    func xiaoXiang(at position: Int) -> String {
        guard position >= 1, position <= 6, xiaoxiang.count >= position else { return "" }
        return xiaoxiang[position - 1]
    }
}

struct CastResult: Hashable, Sendable {
    let method: CastingMethod
    let createdAt: Date
    let question: String?
    /// Raw inputs for digital methods: three numbers, or [yearBranch, month, day, hour].
    let numbers: [Int]?
    let primaryNumber: Int
    let resultingNumber: Int?
    /// Bottom (初爻) → top (上爻).
    let lines: [LineValue]
    /// 1-based positions that change.
    let movingPositions: [Int]

    var hasChangingLines: Bool { !movingPositions.isEmpty }
}
