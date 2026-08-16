import Foundation

struct SavedAIFollowUp: Codable, Hashable, Identifiable {
    let id: UUID
    let user: String
    let assistant: String

    init(id: UUID = UUID(), user: String, assistant: String) {
        self.id = id
        self.user = user
        self.assistant = assistant
    }
}

struct SavedAIContent: Codable, Hashable {
    var summary: String
    var focus: String
    var advice: [String]
}

struct SavedAIAnalysis: Codable, Identifiable, Hashable {
    let id: UUID
    let createdAt: Date
    var updatedAt: Date
    let methodRaw: String
    let question: String?
    let primaryNumber: Int
    let resultingNumber: Int?
    let lines: [Int]
    let movingPositions: [Int]
    var analysis: SavedAIContent
    var followUps: [SavedAIFollowUp]

    var method: CastingMethod {
        CastingMethod(rawValue: methodRaw) ?? .digitalManual
    }

    func toCastResult() -> CastResult {
        CastResult(
            method: method,
            createdAt: createdAt,
            question: question,
            numbers: nil,
            primaryNumber: primaryNumber,
            resultingNumber: resultingNumber,
            lines: lines.compactMap(LineValue.init(rawValue:)),
            movingPositions: movingPositions
        )
    }

    static func make(
        result: CastResult,
        analysis: SavedAIContent,
        followUps: [SavedAIFollowUp]
    ) -> SavedAIAnalysis {
        SavedAIAnalysis(
            id: UUID(),
            createdAt: Date(),
            updatedAt: Date(),
            methodRaw: result.method.rawValue,
            question: result.question,
            primaryNumber: result.primaryNumber,
            resultingNumber: result.resultingNumber,
            lines: result.lines.map(\.rawValue),
            movingPositions: result.movingPositions,
            analysis: analysis,
            followUps: followUps
        )
    }
}

enum SavedAIAnalysisStore {
    private static let key = "ai.saved.analyses.v1"

    static func load() -> [SavedAIAnalysis] {
        guard let data = UserDefaults.standard.data(forKey: key),
              let items = try? JSONDecoder().decode([SavedAIAnalysis].self, from: data)
        else { return [] }
        return items.sorted { $0.updatedAt > $1.updatedAt }
    }

    static func save(_ items: [SavedAIAnalysis]) {
        guard let data = try? JSONEncoder().encode(items) else { return }
        UserDefaults.standard.set(data, forKey: key)
    }

    static func upsert(_ item: SavedAIAnalysis) {
        var items = load()
        if let index = items.firstIndex(where: { $0.id == item.id }) {
            items[index] = item
        } else {
            items.insert(item, at: 0)
        }
        save(items)
    }

    static func remove(id: UUID) {
        var items = load()
        items.removeAll { $0.id == id }
        save(items)
    }
}
