import Foundation
import ObjectiveC
import SwiftUI

enum AppLanguage: String, CaseIterable, Identifiable {
    case simplified = "zh-Hans"
    case traditional = "zh-Hant"

    var id: String { rawValue }
    static let storageKey = "settings.appLanguage.v1"

    var title: String {
        switch self {
        case .simplified: return "简体中文"
        case .traditional: return "繁体中文"
        }
    }

    var locale: Locale {
        switch self {
        case .simplified: return Locale(identifier: "zh_CN")
        case .traditional: return Locale(identifier: "zh_Hant")
        }
    }

    static var current: AppLanguage {
        AppLanguage(rawValue: UserDefaults.standard.string(forKey: storageKey) ?? "") ?? .simplified
    }

    static func display(_ text: String) -> String {
        guard current == .traditional, !text.isEmpty else { return text }
        return TraditionalScript.shared.convert(text)
    }

    static func installBundleHook() {
        object_setClass(Bundle.main, LanguageAwareBundle.self)
        UIKitScriptHook.install()
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

private final class LanguageAwareBundle: Bundle, @unchecked Sendable {
    override func localizedString(forKey key: String, value: String?, table tableName: String?) -> String {
        let raw = super.localizedString(forKey: key, value: value, table: tableName)
        return AppLanguage.display(raw)
    }
}

#if canImport(UIKit)
import UIKit

private enum UIKitScriptHook {
    static func install() {
        exchange(UILabel.self, #selector(setter: UILabel.text), #selector(UILabel.yizhidao_setText(_:)))
        exchange(
            UILabel.self,
            #selector(setter: UILabel.attributedText),
            #selector(UILabel.yizhidao_setAttributedText(_:))
        )
        exchange(
            UIButton.self,
            #selector(UIButton.setTitle(_:for:)),
            #selector(UIButton.yizhidao_setTitle(_:for:))
        )
        exchange(
            UIBarButtonItem.self,
            #selector(setter: UIBarButtonItem.title),
            #selector(UIBarButtonItem.yizhidao_setTitle(_:))
        )
        exchange(
            UITabBarItem.self,
            #selector(setter: UITabBarItem.title),
            #selector(UITabBarItem.yizhidao_setTitle(_:))
        )
        exchange(
            UINavigationItem.self,
            #selector(setter: UINavigationItem.title),
            #selector(UINavigationItem.yizhidao_setTitle(_:))
        )
        exchange(
            UIViewController.self,
            #selector(setter: UIViewController.title),
            #selector(UIViewController.yizhidao_setTitle(_:))
        )
    }

    private static func exchange(_ cls: AnyClass, _ original: Selector, _ swizzled: Selector) {
        guard
            let from = class_getInstanceMethod(cls, original),
            let to = class_getInstanceMethod(cls, swizzled)
        else { return }
        method_exchangeImplementations(from, to)
    }
}

extension UILabel {
    @objc func yizhidao_setText(_ text: String?) {
        yizhidao_setText(text.map(AppLanguage.display))
    }

    @objc func yizhidao_setAttributedText(_ attributed: NSAttributedString?) {
        guard let attributed else {
            yizhidao_setAttributedText(nil)
            return
        }
        let converted = AppLanguage.display(attributed.string)
        guard converted != attributed.string else {
            yizhidao_setAttributedText(attributed)
            return
        }
        guard attributed.length > 0 else {
            yizhidao_setAttributedText(NSAttributedString(string: converted))
            return
        }
        let mutable = NSMutableAttributedString(attributedString: attributed)
        let range = NSRange(location: 0, length: mutable.length)
        if (converted as NSString).length == range.length {
            mutable.mutableString.replaceCharacters(in: range, with: converted)
            yizhidao_setAttributedText(mutable)
        } else {
            yizhidao_setAttributedText(
                NSAttributedString(string: converted, attributes: attributed.attributes(at: 0, effectiveRange: nil))
            )
        }
    }
}

extension UIButton {
    @objc func yizhidao_setTitle(_ title: String?, for state: UIControl.State) {
        yizhidao_setTitle(title.map(AppLanguage.display), for: state)
    }
}

extension UIBarButtonItem {
    @objc func yizhidao_setTitle(_ title: String?) {
        yizhidao_setTitle(title.map(AppLanguage.display))
    }
}

extension UITabBarItem {
    @objc func yizhidao_setTitle(_ title: String?) {
        yizhidao_setTitle(title.map(AppLanguage.display))
    }
}

extension UINavigationItem {
    @objc func yizhidao_setTitle(_ title: String?) {
        yizhidao_setTitle(title.map(AppLanguage.display))
    }
}

extension UIViewController {
    @objc func yizhidao_setTitle(_ title: String?) {
        yizhidao_setTitle(title.map(AppLanguage.display))
    }
}
#endif

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

extension YijingIntroChapter {
    var zhDisplayed: YijingIntroChapter {
        guard AppLanguage.current == .traditional else { return self }
        return YijingIntroChapter(
            id: id,
            title: title.zh,
            subtitle: subtitle.zh,
            paragraphs: paragraphs.map(\.zh)
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
