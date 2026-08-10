import Foundation
import SwiftData

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
    }

    var method: CastingMethod {
        CastingMethod(rawValue: methodRaw) ?? .digitalManual
    }

    func toCastResult() -> CastResult {
        let numbers: [Int]? = {
            guard let numbersJSON,
                  let data = numbersJSON.data(using: .utf8) else { return nil }
            return try? JSONDecoder().decode([Int].self, from: data)
        }()
        let lineInts = (try? JSONDecoder().decode([Int].self, from: Data(linesJSON.utf8))) ?? []
        let lines = lineInts.compactMap(LineValue.init(rawValue:))
        let moving = (try? JSONDecoder().decode([Int].self, from: Data(movingPositionsJSON.utf8))) ?? []
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
