import Foundation

struct AppLanguage: Equatable, Hashable {
    enum UI: Equatable, Hashable {
        case chinese
        case english
    }

    enum Script: Equatable, Hashable {
        case simplified
        case traditional
    }

    var ui: UI
    var script: Script

    var isEnglish: Bool { ui == .english }
    var isTraditional: Bool { script == .traditional }

    /// 日期、数字跟界面语言；经文简繁另走 `script`。
    var locale: Locale {
        switch ui {
        case .english: return Locale(identifier: "en")
        case .chinese:
            return script == .traditional
                ? Locale(identifier: "zh_Hant")
                : Locale(identifier: "zh_CN")
        }
    }

    static var current: AppLanguage { from(.current) }

    static func from(_ locale: Locale) -> AppLanguage {
        from(locale, preferred: Locale.preferredLanguages)
    }

    /// 界面语言跟**首选语言列表**，不跟 `Locale.current`。
    /// 工程开发语言是 `zh-Hans` 且没有独立 en 资源时，系统已切英文，`Locale.current` 仍可能是中文。
    static func from(_ locale: Locale, preferred: [String]) -> AppLanguage {
        let primary = preferred.first.map { Locale(identifier: $0) } ?? locale
        if isChinese(primary) {
            return AppLanguage(
                ui: .chinese,
                script: isTraditional(primary) ? .traditional : .simplified
            )
        }
        var script: Script = .simplified
        for identifier in preferred {
            let item = Locale(identifier: identifier)
            if isChinese(item) {
                script = isTraditional(item) ? .traditional : .simplified
                break
            }
        }
        return AppLanguage(ui: .english, script: script)
    }

    static func display(_ text: String) -> String {
        guard current.script == .traditional, !text.isEmpty else { return text }
        return TraditionalScript.shared.convert(text)
    }

    static func ui(_ chinese: String, _ english: String) -> String {
        current.isEnglish ? english : display(chinese)
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
    /// 中文内容：只做简繁，不英译。
    var zh: String { AppLanguage.display(self) }

    /// 界面壳：系统非中文时出英文。
    func ui(_ english: String) -> String {
        AppLanguage.ui(self, english)
    }
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
        guard AppLanguage.current.isTraditional else { return self }
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

    var pinyin: String { HexagramNames.pinyin(number) }
    var epithet: String { HexagramNames.epithet(number) }

    var displayName: String {
        let raw = name.hasSuffix("卦") ? name : name + "卦"
        if AppLanguage.current.isEnglish {
            return "\(raw) \(pinyin)"
        }
        return raw.zh
    }

    /// 列表、结果页卦名：`䷀ 乾` 或 `䷀ 乾  Qián`。
    var listLabel: String {
        if AppLanguage.current.isEnglish {
            return "\(symbol) \(name)  \(pinyin)"
        }
        return "\(symbol) \(name)".zh
    }

    func roleCaption(roleZH: String, roleEN: String) -> String {
        "第\(number)卦 · \(roleZH)".ui("Hexagram \(number) · \(roleEN)")
    }

    var numberLabel: String {
        "第\(number)卦".ui("Hexagram \(number)")
    }
}

extension HexagramWing {
    var zhDisplayed: HexagramWing {
        guard AppLanguage.current.isTraditional else { return self }
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
        guard AppLanguage.current.isTraditional else { return self }
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
        guard AppLanguage.current.isTraditional else { return self }
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
        guard AppLanguage.current.isTraditional else { return self }
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
        guard AppLanguage.current.isTraditional else { return self }
        return ZhengshiSection(id: id, title: title.zh, paragraphs: paragraphs.map(\.zh))
    }
}

extension ZhengshiChapter {
    var zhDisplayed: ZhengshiChapter {
        guard AppLanguage.current.isTraditional else { return self }
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
        guard AppLanguage.current.isTraditional else { return self }
        return ZhengshiPart(id: id, title: title.zh, chapters: chapters.map(\.zhDisplayed))
    }
}
