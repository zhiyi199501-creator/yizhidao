import Foundation
import SwiftData

struct HistoryTrashEntry: Codable, Identifiable, Hashable {
    let id: UUID
    let deletedAt: Date
    let recordID: UUID
    let createdAt: Date
    let question: String?
    let methodRaw: String
    let numbersJSON: String?
    let primaryNumber: Int
    let resultingNumber: Int?
    let linesJSON: String
    let movingPositionsJSON: String
    let verificationStatusRaw: String
    let verificationNote: String?

    init(record: ReadingRecord, deletedAt: Date = .now) {
        self.id = UUID()
        self.deletedAt = deletedAt
        self.recordID = record.id
        self.createdAt = record.createdAt
        self.question = record.question
        self.methodRaw = record.methodRaw
        self.numbersJSON = record.numbersJSON
        self.primaryNumber = record.primaryNumber
        self.resultingNumber = record.resultingNumber
        self.linesJSON = record.linesJSON
        self.movingPositionsJSON = record.movingPositionsJSON
        self.verificationStatusRaw = record.verificationStatusRaw
        self.verificationNote = record.verificationNote
    }

    func toReadingRecord() -> ReadingRecord {
        ReadingRecord(
            id: recordID,
            createdAt: createdAt,
            question: question,
            methodRaw: methodRaw,
            numbersJSON: numbersJSON,
            primaryNumber: primaryNumber,
            resultingNumber: resultingNumber,
            linesJSON: linesJSON,
            movingPositionsJSON: movingPositionsJSON,
            verificationStatusRaw: verificationStatusRaw,
            verificationNote: verificationNote
        )
    }
}

enum HistoryTrashStore {
    private static let key = "history.trash.v1"

    static func load() -> [HistoryTrashEntry] {
        guard let data = UserDefaults.standard.data(forKey: key),
              let entries = try? JSONDecoder().decode([HistoryTrashEntry].self, from: data)
        else { return [] }
        return entries
    }

    static func save(_ entries: [HistoryTrashEntry]) {
        guard let data = try? JSONEncoder().encode(entries) else { return }
        UserDefaults.standard.set(data, forKey: key)
    }

    static func archive(_ record: ReadingRecord) {
        var entries = load()
        entries.insert(HistoryTrashEntry(record: record), at: 0)
        save(entries)
    }

    static func remove(entryID: UUID) {
        var entries = load()
        entries.removeAll { $0.id == entryID }
        save(entries)
    }

    static func clearAll() {
        UserDefaults.standard.removeObject(forKey: key)
    }
}

enum VerificationStatus: String, CaseIterable, Identifiable, Sendable {
    case none = "none"
    case fulfilled = "fulfilled"
    case partial = "partial"
    case unfulfilled = "unfulfilled"

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .none: return "未验证"
        case .fulfilled: return "应验"
        case .partial: return "部分应验"
        case .unfulfilled: return "未应验"
        }
    }
}

@Model
final class ReadingRecord {
    var id: UUID
    var createdAt: Date
    var question: String?
    var methodRaw: String
    var numbersJSON: String?
    var primaryNumber: Int
    var resultingNumber: Int?
    var linesJSON: String
    var movingPositionsJSON: String
    var verificationStatusRaw: String = VerificationStatus.none.rawValue
    var verificationNote: String?

    init(
        id: UUID = UUID(),
        createdAt: Date,
        question: String?,
        methodRaw: String,
        numbersJSON: String?,
        primaryNumber: Int,
        resultingNumber: Int?,
        linesJSON: String,
        movingPositionsJSON: String,
        verificationStatusRaw: String = VerificationStatus.none.rawValue,
        verificationNote: String?
    ) {
        self.id = id
        self.createdAt = createdAt
        self.question = question
        self.methodRaw = methodRaw
        self.numbersJSON = numbersJSON
        self.primaryNumber = primaryNumber
        self.resultingNumber = resultingNumber
        self.linesJSON = linesJSON
        self.movingPositionsJSON = movingPositionsJSON
        self.verificationStatusRaw = verificationStatusRaw
        self.verificationNote = verificationNote
    }

    convenience init(from result: CastResult) {
        let numbersJSON: String? = {
            guard let numbers = result.numbers,
                  let data = try? JSONEncoder().encode(numbers) else { return nil }
            return String(data: data, encoding: .utf8)
        }()
        let lineValues = result.lines.map(\.rawValue)
        let linesJSON = String(data: try! JSONEncoder().encode(lineValues), encoding: .utf8)!
        let movingPositionsJSON = String(
            data: try! JSONEncoder().encode(result.movingPositions),
            encoding: .utf8
        )!
        self.init(
            createdAt: result.createdAt,
            question: result.question,
            methodRaw: result.method.rawValue,
            numbersJSON: numbersJSON,
            primaryNumber: result.primaryNumber,
            resultingNumber: result.resultingNumber,
            linesJSON: linesJSON,
            movingPositionsJSON: movingPositionsJSON,
            verificationStatusRaw: VerificationStatus.none.rawValue,
            verificationNote: nil
        )
    }

    var method: CastingMethod {
        CastingMethod(rawValue: methodRaw) ?? .digitalManual
    }

    var verificationStatus: VerificationStatus {
        get { VerificationStatus(rawValue: verificationStatusRaw) ?? .none }
        set { verificationStatusRaw = newValue.rawValue }
    }

    var movingPositions: [Int] {
        (try? JSONDecoder().decode([Int].self, from: Data(movingPositionsJSON.utf8))) ?? []
    }

    func toCastResult() -> CastResult {
        let numbers: [Int]? = {
            guard let numbersJSON,
                  let data = numbersJSON.data(using: .utf8) else { return nil }
            return try? JSONDecoder().decode([Int].self, from: data)
        }()
        let lineInts = (try? JSONDecoder().decode([Int].self, from: Data(linesJSON.utf8))) ?? []
        let lines = lineInts.compactMap(LineValue.init(rawValue:))
        let moving = movingPositions
        return CastResult(
            method: method,
            createdAt: createdAt,
            question: question,
            numbers: numbers,
            primaryNumber: primaryNumber,
            resultingNumber: resultingNumber,
            lines: lines,
            movingPositions: moving
        )
    }
}
