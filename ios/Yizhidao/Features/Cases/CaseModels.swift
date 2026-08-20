import Foundation
import Combine

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

/// 案例数据仓库：包内底稿 + 本地缓存；打开案例页时向服务端拉取最新。
@MainActor
final class CaseStore: ObservableObject {
    static let shared = CaseStore()

    @Published private var rawCases: [CaseStudy]
    var cases: [CaseStudy] { rawCases.map(\.zhDisplayed) }

    private let versionKey = "yizhidao.cases.version"
    private let cacheURL: URL

    init() {
        let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? FileManager.default.temporaryDirectory
        let dir = support.appendingPathComponent("Yizhidao", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        cacheURL = dir.appendingPathComponent("cases.json")
        rawCases = Self.load(from: cacheURL) ?? Self.loadBundle() ?? []
    }

    func refresh() async {
        do {
            switch try await AuthAPI.fetchCases(
                ifNoneMatch: UserDefaults.standard.string(forKey: versionKey)
            ) {
            case .notModified:
                return
            case .updated(let version, let remote):
                rawCases = remote
                UserDefaults.standard.set(version, forKey: versionKey)
                try? JSONEncoder().encode(remote).write(to: cacheURL, options: .atomic)
            }
        } catch {
            // 离线或服务不可用时沿用包内 / 缓存
        }
    }

    private static func loadBundle() -> [CaseStudy]? {
        guard let url = Bundle.main.url(forResource: "cases", withExtension: "json") else { return nil }
        return load(from: url)
    }

    private static func load(from url: URL) -> [CaseStudy]? {
        guard let data = try? Data(contentsOf: url) else { return nil }
        return try? JSONDecoder().decode([CaseStudy].self, from: data)
    }
}
