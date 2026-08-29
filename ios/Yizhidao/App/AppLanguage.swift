import Foundation

enum AppLanguage {
    case simplified
    case traditional

    var locale: Locale {
        switch self {
        case .simplified: return Locale(identifier: "zh_CN")
        case .traditional: return Locale(identifier: "zh_Hant")
        }
    }

    static var current: AppLanguage { from(.current) }

    static func from(_ locale: Locale) -> AppLanguage {
        if isChinese(locale) {
            return isTraditional(locale) ? .traditional : .simplified
        }
        for identifier in Locale.preferredLanguages {
            let preferred = Locale(identifier: identifier)
            if isChinese(preferred) {
                return isTraditional(preferred) ? .traditional : .simplified
            }
        }
        return .simplified
    }

    static func display(_ text: String) -> String {
        guard current == .traditional, !text.isEmpty else { return text }
        return TraditionalScript.shared.convert(text)
    }

    private static func isChinese(_ locale: Locale) -> Bool {
        locale.language.languageCode?.identifier == "zh"
    }

    private static func isTraditional(_ locale: Locale) -> Bool {
        if locale.language.script?.identifier == "Hant" { return true }
        if locale.language.script?.identifier == "Hans" { return false }
        switch locale.language.region?.identifier ?? locale.region?.identifier {
        case "TW", "HK", "MO": return true
        default: return false
        }
    }
}

extension String {
    var zh: String { AppLanguage.display(self) }
}

private final class TraditionalScript: @unchecked Sendable {
    static let shared = TraditionalScript()
    private let lock = NSLock()
    private var cache: [String: String] = [:]

    func convert(_ text: String) -> String {
        lock.lock()
        defer { lock.unlock() }
        if let hit = cache[text] { return hit }
        let converted = text.applyingTransform(StringTransform(rawValue: "Hans-Hant"), reverse: false) ?? text
        cache[text] = converted
        return converted
    }
}

extension Hexagram {
    var zhDisplayed: Hexagram {
        guard AppLanguage.current == .traditional else { return self }
        return Hexagram(
            number: number,
            name: name.zh,
            symbol: symbol,
            binary: binary,
            guaci: guaci.zh,
            tuanci: tuanci.zh,
            yaoci: yaoci.map(\.zh),
            daxiang: daxiang.zh,
            xiaoxiang: xiaoxiang.map(\.zh),
            figure: figure.zh,
            part: part,
            title: title.zh,
            yong: yong.map { HexagramYong(ci: $0.ci.zh, xiang: $0.xiang.zh) },
            wenyan: wenyan.map(\.zh)
        )
    }
}

extension HexagramWing {
    var zhDisplayed: HexagramWing {
        guard AppLanguage.current == .traditional else { return self }
        return HexagramWing(
            id: id,
            title: title.zh,
            chapters: chapters.map {
                HexagramWingChapter(title: $0.title.zh, paragraphs: $0.paragraphs.map(\.zh))
            }
        )
    }
}

extension CaseStudy {
    var zhDisplayed: CaseStudy {
        guard AppLanguage.current == .traditional else { return self }
        return CaseStudy(
            file: file,
            hexagram: hexagram.zh,
            position: position.zh,
            background: background.zh,
            question: question.zh,
            casting: casting.zh,
            explanation: explanation.zh,
            verification: verification.zh,
            number: number
        )
    }
}

extension YijingIntroBlock {
    var zhDisplayed: YijingIntroBlock {
        guard AppLanguage.current == .traditional else { return self }
        switch self {
        case .paragraph(let text):
            return .paragraph(text.zh)
        case .quote(let text, let cite):
            return .quote(text: text.zh, cite: cite.zh)
        case .list(let items):
            return .list(items.map(\.zh))
        case .table(let rows):
            return .table(rows.map { $0.map(\.zh) })
        case .figure(let kind, let caption):
            return .figure(kind: kind, caption: caption.zh)
        case .links(let links):
            return .links(links.map {
                YijingIntroLink(title: $0.title.zh, subtitle: $0.subtitle.zh, route: $0.route)
            })
        }
    }
}

extension YijingIntroChapter {
    var zhDisplayed: YijingIntroChapter {
        guard AppLanguage.current == .traditional else { return self }
        return YijingIntroChapter(
            id: id,
            title: title.zh,
            subtitle: subtitle.zh,
            blocks: blocks.map(\.zhDisplayed)
        )
    }
}

extension ZhengshiSection {
    var zhDisplayed: ZhengshiSection {
        guard AppLanguage.current == .traditional else { return self }
        return ZhengshiSection(id: id, title: title.zh, paragraphs: paragraphs.map(\.zh))
    }
}

extension ZhengshiChapter {
    var zhDisplayed: ZhengshiChapter {
        guard AppLanguage.current == .traditional else { return self }
        return ZhengshiChapter(
            id: id,
            title: title.zh,
            subtitle: subtitle.zh,
            number: number,
            symbol: symbol,
            sections: sections.map(\.zhDisplayed)
        )
    }
}

extension ZhengshiPart {
    var zhDisplayed: ZhengshiPart {
        guard AppLanguage.current == .traditional else { return self }
        return ZhengshiPart(id: id, title: title.zh, chapters: chapters.map(\.zhDisplayed))
    }
}
