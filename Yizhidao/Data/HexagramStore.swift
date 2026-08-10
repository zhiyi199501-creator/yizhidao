import Foundation

@Observable
final class HexagramStore {
    private(set) var hexagrams: [Hexagram] = []
    private var byNumber: [Int: Hexagram] = [:]

    static let shared = HexagramStore()

    init(bundle: Bundle = .main) {
        load(from: bundle)
    }

    func hexagram(number: Int) -> Hexagram? {
        byNumber[number]
    }

    func load(from bundle: Bundle) {
        guard let url = bundle.url(forResource: "Hexagrams", withExtension: "json") else {
            assertionFailure("Hexagrams.json missing")
            return
        }
        do {
            let data = try Data(contentsOf: url)
            let decoded = try JSONDecoder().decode([Hexagram].self, from: data)
            hexagrams = decoded.sorted { $0.number < $1.number }
            byNumber = Dictionary(uniqueKeysWithValues: hexagrams.map { ($0.number, $0) })
        } catch {
            assertionFailure("Failed to load Hexagrams.json: \(error)")
        }
    }
}
