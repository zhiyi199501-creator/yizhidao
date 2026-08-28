import Foundation

func aiAdviceDisplayItems(advice: [String], risks: [String]) -> [String] {
    let parts = risks.compactMap { raw -> String? in
        var text = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return nil }
        for prefix in ["须防：", "须防:", "須防：", "須防:"] where text.hasPrefix(prefix) {
            text = String(text.dropFirst(prefix.count)).trimmingCharacters(in: .whitespacesAndNewlines)
            break
        }
        return text.isEmpty ? nil : text
    }
    guard !parts.isEmpty else { return advice }
    return advice + ["须防：\(parts.joined(separator: "；"))"]
}

struct SavedAIFollowUp: Codable, Hashable, Identifiable {
    let id: UUID
    let user: String
    let assistant: String
    let advice: [String]
    let askNext: [String]

    enum CodingKeys: String, CodingKey {
        case id, user, assistant, advice, askNext
    }

    init(
        id: UUID = UUID(),
        user: String,
        assistant: String,
        advice: [String] = [],
        askNext: [String] = []
    ) {
        self.id = id
        self.user = user
        self.assistant = assistant
        self.advice = advice
        self.askNext = askNext
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decodeIfPresent(UUID.self, forKey: .id) ?? UUID()
        user = try container.decode(String.self, forKey: .user)
        assistant = try container.decode(String.self, forKey: .assistant)
        advice = try container.decodeIfPresent([String].self, forKey: .advice) ?? []
        askNext = try container.decodeIfPresent([String].self, forKey: .askNext) ?? []
    }
}

struct SavedAIContent: Codable, Hashable {
    var summary: String
    var focus: String
    var advice: [String]
    var direction: String
    var risks: [String]
    var askNext: [String]

    enum CodingKeys: String, CodingKey {
        case summary, focus, advice, direction, risks, askNext
    }

    init(
        summary: String,
        focus: String,
        advice: [String],
        direction: String = "",
        risks: [String] = [],
        askNext: [String] = []
    ) {
        self.summary = summary
        self.focus = focus
        self.advice = advice
        self.direction = direction
        self.risks = risks
        self.askNext = askNext
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        summary = try container.decode(String.self, forKey: .summary)
        focus = try container.decode(String.self, forKey: .focus)
        advice = try container.decode([String].self, forKey: .advice)
        direction = try container.decodeIfPresent(String.self, forKey: .direction) ?? ""
        risks = try container.decodeIfPresent([String].self, forKey: .risks) ?? []
        askNext = try container.decodeIfPresent([String].self, forKey: .askNext) ?? []
    }
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
