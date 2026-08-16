import Foundation

/// 单个占卦案例（来自《张庆祥讲易经案例》转写稿总结）
struct CaseStudy: Codable, Identifiable, Hashable {
    let file: String
    /// 卦名，如「屯卦」
    let hexagram: String
    /// 爻位，如「初爻」，整卦/卦辞则为「卦辞」
    let position: String
    let background: String
    let question: String
    let casting: String
    let explanation: String
    let verification: String
    /// 本卦卦号（1-64），用于关联 HexagramStore
    let number: Int

    var id: String { file }

    /// 1-based 动爻位。支持「初爻」「三爻、四爻」等写法。
    var movingPositions: [Int] {
        let names = ["初爻", "二爻", "三爻", "四爻", "五爻", "上爻"]
        return names.enumerated().compactMap { index, name in
            position.contains(name) ? index + 1 : nil
        }
    }

    var lines: [LineValue] {
        guard (1...64).contains(number) else { return [] }
        let moving = Set(movingPositions)
        return KingWenTable.bits(ofNumber: number).enumerated().map { index, bit in
            LineValue.from(isYang: bit == 1, changing: moving.contains(index + 1))
        }
    }

    var resultingNumber: Int? {
        guard (1...64).contains(number) else { return nil }
        return KingWenTable.resultingNumber(
            primaryBits: KingWenTable.bits(ofNumber: number),
            movingPositions: movingPositions
        )
    }
}

/// 案例数据仓库，启动时从 bundle 的 cases.json 加载
struct CaseStore {
    static let shared = CaseStore()
    let cases: [CaseStudy]

    init() {
        guard let url = Bundle.main.url(forResource: "cases", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let decoded = try? JSONDecoder().decode([CaseStudy].self, from: data) else {
            cases = []
            return
        }
        cases = decoded
    }
}
