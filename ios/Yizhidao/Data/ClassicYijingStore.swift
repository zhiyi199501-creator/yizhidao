import Foundation

struct YijingIntroLink: Codable, Hashable, Sendable {
    let title: String
    let subtitle: String
    let route: String
}

enum YijingIntroBlock: Hashable, Sendable {
    case paragraph(String)
    case quote(text: String, cite: String)
    case list([String])
    case table([[String]])
    case figure(kind: String, caption: String)
    case links([YijingIntroLink])

    var plainText: String {
        switch self {
        case .paragraph(let text): return text
        case .quote(let text, let cite): return cite.isEmpty ? text : "\(text) \(cite)"
        case .list(let items): return items.joined(separator: " ")
        case .table(let rows): return rows.joined().joined(separator: " ")
        case .figure(_, let caption): return caption
        case .links(let links): return links.map { "\($0.title) \($0.subtitle)" }.joined(separator: " ")
        }
    }
}

extension YijingIntroBlock: Codable {
    private enum CodingKeys: String, CodingKey {
        case type, text, cite, items, rows, kind, caption, links
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let type = try c.decode(String.self, forKey: .type)
        switch type {
        case "p":
            self = .paragraph(try c.decode(String.self, forKey: .text))
        case "quote":
            self = .quote(
                text: try c.decode(String.self, forKey: .text),
                cite: try c.decodeIfPresent(String.self, forKey: .cite) ?? ""
            )
        case "list":
            self = .list(try c.decode([String].self, forKey: .items))
        case "table":
            self = .table(try c.decode([[String]].self, forKey: .rows))
        case "figure":
            self = .figure(
                kind: try c.decode(String.self, forKey: .kind),
                caption: try c.decodeIfPresent(String.self, forKey: .caption) ?? ""
            )
        case "links":
            self = .links(try c.decode([YijingIntroLink].self, forKey: .links))
        default:
            throw DecodingError.dataCorruptedError(forKey: .type, in: c, debugDescription: "Unknown intro block type \(type)")
        }
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        switch self {
        case .paragraph(let text):
            try c.encode("p", forKey: .type)
            try c.encode(text, forKey: .text)
        case .quote(let text, let cite):
            try c.encode("quote", forKey: .type)
            try c.encode(text, forKey: .text)
            try c.encode(cite, forKey: .cite)
        case .list(let items):
            try c.encode("list", forKey: .type)
            try c.encode(items, forKey: .items)
        case .table(let rows):
            try c.encode("table", forKey: .type)
            try c.encode(rows, forKey: .rows)
        case .figure(let kind, let caption):
            try c.encode("figure", forKey: .type)
            try c.encode(kind, forKey: .kind)
            try c.encode(caption, forKey: .caption)
        case .links(let links):
            try c.encode("links", forKey: .type)
            try c.encode(links, forKey: .links)
        }
    }
}

struct YijingIntroChapter: Codable, Identifiable, Hashable, Sendable {
    let id: String
    let title: String
    let subtitle: String
    let blocks: [YijingIntroBlock]

    var plainText: String { blocks.map(\.plainText).joined(separator: " ") }
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
    private var englishChapters: [YijingIntroChapter] = []

    var source: String { rawSource.zh }
    var note: String { rawNote.zh }
    var chapters: [YijingIntroChapter] { displayedChapters(english: AppLanguage.current.isEnglish) }

    init(bundle: Bundle = .main) {
        load(from: bundle)
    }

    func displayedChapters(english: Bool) -> [YijingIntroChapter] {
        if english, !englishChapters.isEmpty {
            return englishChapters.map(\.zhDisplayed)
        }
        return rawChapters.map(\.zhDisplayed)
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
        if let enURL = bundle.url(forResource: "YijingIntro.en", withExtension: "json") {
            do {
                englishChapters = try JSONDecoder().decode(YijingIntroBook.self, from: Data(contentsOf: enURL)).chapters
            } catch {
                assertionFailure("Failed to load YijingIntro.en.json: \(error)")
            }
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
