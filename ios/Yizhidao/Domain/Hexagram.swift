import Foundation

struct HexagramYong: Codable, Hashable, Sendable {
    let ci: String
    let xiang: String
}

struct HexagramWingChapter: Codable, Identifiable, Hashable, Sendable {
    let title: String
    let paragraphs: [String]
    var id: String { title }
}

struct HexagramWing: Codable, Identifiable, Hashable, Sendable {
    let id: String
    let title: String
    let chapters: [HexagramWingChapter]
}

struct HexagramsFile: Codable, Sendable {
    let source: String?
    let hexagrams: [Hexagram]
    let wings: [HexagramWing]
}

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
    var figure: String = ""
    var part: String = ""
    var title: String = ""
    var yong: HexagramYong? = nil
    var wenyan: [String] = []

    var id: Int { number }

    var figureLines: [LineValue] {
        binary.map { LineValue.from(isYang: $0 == "1", changing: false) }
    }

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

/// 卦名拼音与短别称。别称只帮忙认卦，不替代原文。
enum HexagramNames {
    static func pinyin(_ number: Int) -> String {
        entry(number)?.pinyin ?? ""
    }

    static func epithet(_ number: Int) -> String {
        entry(number)?.epithet ?? ""
    }

    private static func entry(_ number: Int) -> (pinyin: String, epithet: String)? {
        guard (1...64).contains(number) else { return nil }
        return table[number - 1]
    }

    private static let table: [(pinyin: String, epithet: String)] = [
        ("Qián", "Heaven"),
        ("Kūn", "Earth"),
        ("Zhūn", "Sprouting"),
        ("Méng", "Folly"),
        ("Xū", "Waiting"),
        ("Sòng", "Conflict"),
        ("Shī", "The Army"),
        ("Bǐ", "Holding Together"),
        ("Xiǎo Chù", "Small Restraint"),
        ("Lǚ", "Treading"),
        ("Tài", "Peace"),
        ("Pǐ", "Standstill"),
        ("Tóng Rén", "Fellowship"),
        ("Dà Yǒu", "Great Possession"),
        ("Qiān", "Modesty"),
        ("Yù", "Enthusiasm"),
        ("Suí", "Following"),
        ("Gǔ", "Decay"),
        ("Lín", "Approach"),
        ("Guān", "Contemplation"),
        ("Shì Kè", "Biting Through"),
        ("Bì", "Grace"),
        ("Bō", "Splitting"),
        ("Fù", "Return"),
        ("Wú Wàng", "Innocence"),
        ("Dà Chù", "Great Restraint"),
        ("Yí", "Nourishment"),
        ("Dà Guò", "Great Exceeding"),
        ("Kǎn", "The Abyss"),
        ("Lí", "Radiance"),
        ("Xián", "Influence"),
        ("Héng", "Duration"),
        ("Dùn", "Retreat"),
        ("Dà Zhuàng", "Great Power"),
        ("Jìn", "Progress"),
        ("Míng Yí", "Darkening"),
        ("Jiā Rén", "The Family"),
        ("Kuí", "Opposition"),
        ("Jiǎn", "Obstruction"),
        ("Xiè", "Deliverance"),
        ("Sǔn", "Decrease"),
        ("Yì", "Increase"),
        ("Guài", "Breakthrough"),
        ("Gòu", "Encounter"),
        ("Cuì", "Gathering"),
        ("Shēng", "Ascending"),
        ("Kùn", "Oppression"),
        ("Jǐng", "The Well"),
        ("Gé", "Revolution"),
        ("Dǐng", "The Cauldron"),
        ("Zhèn", "Thunder"),
        ("Gèn", "Mountain"),
        ("Jiàn", "Gradual"),
        ("Guī Mèi", "Marrying Maiden"),
        ("Fēng", "Abundance"),
        ("Lǚ", "The Wanderer"),
        ("Xùn", "Wind"),
        ("Duì", "Joy"),
        ("Huàn", "Dispersion"),
        ("Jié", "Limitation"),
        ("Zhōng Fú", "Inner Trust"),
        ("Xiǎo Guò", "Small Exceeding"),
        ("Jì Jì", "After Completion"),
        ("Wèi Jì", "Before Completion"),
    ]
}
