import Foundation

struct YijingIntroChapter: Codable, Identifiable, Hashable, Sendable {
    let id: String
    let title: String
    let subtitle: String
    let paragraphs: [String]
}

struct YijingIntroBook: Codable, Sendable {
    let source: String
    let note: String
    let chapters: [YijingIntroChapter]
}

final class YijingIntroStore {
    static let shared = YijingIntroStore()

    private var rawSource: String = ""
    private var rawNote: String = ""
    private var rawChapters: [YijingIntroChapter] = []

    var source: String { rawSource.zh }
    var note: String { rawNote.zh }
    var chapters: [YijingIntroChapter] { rawChapters.map(\.zhDisplayed) }

    init(bundle: Bundle = .main) {
        load(from: bundle)
    }

    func load(from bundle: Bundle) {
        guard let url = bundle.url(forResource: "YijingIntro", withExtension: "json") else {
            assertionFailure("YijingIntro.json missing")
            return
        }
        do {
            let book = try JSONDecoder().decode(YijingIntroBook.self, from: Data(contentsOf: url))
            rawSource = book.source
            rawNote = book.note
            rawChapters = book.chapters
        } catch {
            assertionFailure("Failed to load YijingIntro.json: \(error)")
        }
    }
}

struct ZhengshiSection: Codable, Identifiable, Hashable, Sendable {
    let id: String
    let title: String
    let paragraphs: [String]
}

struct ZhengshiChapter: Codable, Identifiable, Hashable, Sendable {
    let id: String
    let title: String
    let subtitle: String
    let number: Int?
    let symbol: String
    let sections: [ZhengshiSection]
}

struct ZhengshiPart: Codable, Identifiable, Hashable, Sendable {
    let id: String
    let title: String
    let chapters: [ZhengshiChapter]
}

struct ZhengshiBook: Codable, Sendable {
    let source: String
    let note: String
    let parts: [ZhengshiPart]
}

final class ZhengshiStore {
    static let shared = ZhengshiStore()

    private var book: ZhengshiBook?
    private let bundle: Bundle

    var source: String {
        loadIfNeeded()
        return book?.source.zh ?? ""
    }

    var note: String {
        loadIfNeeded()
        return book?.note.zh ?? ""
    }

    var parts: [ZhengshiPart] {
        loadIfNeeded()
        return book?.parts.map(\.zhDisplayed) ?? []
    }

    init(bundle: Bundle = .main) {
        self.bundle = bundle
    }

    func loadIfNeeded() {
        if book != nil { return }
        guard let url = bundle.url(forResource: "Zhengshi", withExtension: "json") else {
            assertionFailure("Zhengshi.json missing")
            return
        }
        do {
            book = try JSONDecoder().decode(ZhengshiBook.self, from: Data(contentsOf: url))
        } catch {
            assertionFailure("Failed to load Zhengshi.json: \(error)")
        }
    }
}
