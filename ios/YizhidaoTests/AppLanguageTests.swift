import XCTest
@testable import Yizhidao

final class AppLanguageTests: XCTestCase {
    func testEnglishSystemUsesEnglishUI() {
        let language = AppLanguage.from(Locale(identifier: "en_US"), preferred: ["en-US"])
        XCTAssertEqual(language.ui, .english)
        XCTAssertEqual(language.script, .simplified)
    }

    func testEnglishWithTaiwanPreferredKeepsTraditionalScript() {
        let language = AppLanguage.from(Locale(identifier: "en_US"), preferred: ["en-US", "zh-Hant-TW"])
        XCTAssertEqual(language.ui, .english)
        XCTAssertEqual(language.script, .traditional)
    }

    func testTaiwanUsesChineseUIAndTraditionalScript() {
        let language = AppLanguage.from(Locale(identifier: "zh_TW"), preferred: ["zh-Hant-TW"])
        XCTAssertEqual(language.ui, .chinese)
        XCTAssertEqual(language.script, .traditional)
    }

    func testPreferredEnglishWinsWhenLocaleCurrentStaysChinese() {
        let language = AppLanguage.from(Locale(identifier: "zh_CN"), preferred: ["en-US"])
        XCTAssertEqual(language.ui, .english)
        XCTAssertEqual(language.script, .simplified)
    }

    func testMainlandUsesChineseUIAndSimplifiedScript() {
        let language = AppLanguage.from(Locale(identifier: "zh_CN"), preferred: ["zh-Hans-CN"])
        XCTAssertEqual(language.ui, .chinese)
        XCTAssertEqual(language.script, .simplified)
    }

    func testHexagramNames() {
        XCTAssertEqual(HexagramNames.pinyin(1), "Qián")
        XCTAssertEqual(HexagramNames.epithet(1), "Heaven")
        XCTAssertEqual(HexagramNames.pinyin(2), "Kūn")
        XCTAssertEqual(HexagramNames.epithet(33), "Retreat")
        XCTAssertEqual(HexagramNames.pinyin(64), "Wèi Jì")
        XCTAssertEqual(HexagramNames.epithet(64), "Before Completion")
        XCTAssertEqual(HexagramNames.pinyin(0), "")
    }
}
