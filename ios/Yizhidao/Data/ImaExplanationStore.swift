import Foundation

struct ImaExplanationEntry: Codable {
    let title: String
    let scripture: String
    let answer: String
}

private struct ImaExplanationsFile: Codable {
    let version: Int
    let source: String
    let entries: [String: ImaExplanationEntry]
}

enum ImaExplanationId {
    static func guaci(number: Int) -> String { String(format: "%02d-guaci", number) }
    static func tuanci(number: Int) -> String { String(format: "%02d-tuanci", number) }
    static func daxiang(number: Int) -> String { String(format: "%02d-daxiang", number) }

    /// `position` 为 1…6（初爻=1）
    static func yaoPair(number: Int, position: Int) -> String {
        String(format: "%02d-yao-%d", number, position - 1)
    }

    static func yong(number: Int) -> String { String(format: "%02d-yong", number) }
    static func wenyan(number: Int) -> String { String(format: "%02d-wenyan", number) }
}

struct ImaExplanationSelection: Identifiable {
    let id: String
    let entry: ImaExplanationEntry
}

@Observable
final class ImaExplanationStore {
    static let shared = ImaExplanationStore()

    private var entries: [String: ImaExplanationEntry] = [:]
    private(set) var source = ""

    init(bundle: Bundle = .main) {
        load(from: bundle)
    }

    func explanation(for id: String) -> ImaExplanationEntry? {
        entries[id]
    }

    func hasExplanation(for id: String) -> Bool {
        entries[id] != nil
    }

    private func load(from bundle: Bundle) {
        guard let url = bundle.url(forResource: "ImaExplanations", withExtension: "json") else {
            return
        }
        do {
            let data = try Data(contentsOf: url)
            let file = try JSONDecoder().decode(ImaExplanationsFile.self, from: data)
            source = file.source
            entries = file.entries
        } catch {
            assertionFailure("Failed to load ImaExplanations.json: \(error)")
        }
    }
}
