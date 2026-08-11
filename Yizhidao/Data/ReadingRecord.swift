import Foundation
import SwiftData

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

    init(from result: CastResult) {
        self.id = UUID()
        self.createdAt = result.createdAt
        self.question = result.question
        self.methodRaw = result.method.rawValue
        if let numbers = result.numbers,
           let data = try? JSONEncoder().encode(numbers),
           let str = String(data: data, encoding: .utf8) {
            self.numbersJSON = str
        } else {
            self.numbersJSON = nil
        }
        self.primaryNumber = result.primaryNumber
        self.resultingNumber = result.resultingNumber
        let lineValues = result.lines.map(\.rawValue)
        self.linesJSON = String(data: try! JSONEncoder().encode(lineValues), encoding: .utf8)!
        self.movingPositionsJSON = String(
            data: try! JSONEncoder().encode(result.movingPositions),
            encoding: .utf8
        )!
        self.verificationStatusRaw = VerificationStatus.none.rawValue
        self.verificationNote = nil
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
