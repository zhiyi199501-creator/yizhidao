import Foundation

@Observable
final class HexagramStore {
    private(set) var hexagrams: [Hexagram] = []
    private(set) var wings: [HexagramWing] = []
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
            let file = try JSONDecoder().decode(HexagramsFile.self, from: data)
            hexagrams = file.hexagrams.sorted { $0.number < $1.number }
            wings = file.wings
            byNumber = Dictionary(uniqueKeysWithValues: hexagrams.map { ($0.number, $0) })
        } catch {
            assertionFailure("Failed to load Hexagrams.json: \(error)")
        }
    }
}
