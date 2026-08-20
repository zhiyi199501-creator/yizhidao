import Foundation

@Observable
final class HexagramStore {
    private var rawHexagrams: [Hexagram] = []
    private var rawWings: [HexagramWing] = []
    private var byNumber: [Int: Hexagram] = [:]

    var hexagrams: [Hexagram] { rawHexagrams.map(\.zhDisplayed) }
    var wings: [HexagramWing] { rawWings.map(\.zhDisplayed) }

    static let shared = HexagramStore()

    init(bundle: Bundle = .main) {
        load(from: bundle)
    }

    func hexagram(number: Int) -> Hexagram? {
        byNumber[number]?.zhDisplayed
    }

    func load(from bundle: Bundle) {
        guard let url = bundle.url(forResource: "Hexagrams", withExtension: "json") else {
            assertionFailure("Hexagrams.json missing")
            return
        }
        do {
            let data = try Data(contentsOf: url)
            let file = try JSONDecoder().decode(HexagramsFile.self, from: data)
            rawHexagrams = file.hexagrams.sorted { $0.number < $1.number }
            rawWings = file.wings
            byNumber = Dictionary(uniqueKeysWithValues: rawHexagrams.map { ($0.number, $0) })
        } catch {
            assertionFailure("Failed to load Hexagrams.json: \(error)")
        }
    }
}
