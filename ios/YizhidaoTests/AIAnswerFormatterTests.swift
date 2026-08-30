import XCTest
@testable import Yizhidao

final class AIAnswerFormatterTests: XCTestCase {
    func testKeepsShortTextAsOneParagraph() {
        let raw = "壮马象征可靠而有力的援助。"
        XCTAssertEqual(AIAnswerFormatter.paragraphs(in: raw), [raw])
    }

    func testSplitsLongTextAtSentenceEnd() {
        let raw = "判断哪一位中医师最适合你，关键不在名声或价格，而在你是否能借到那匹「壮马」。"
            + "二爻说「用拯马壮吉」，壮马象征可靠而有力的援助，因此适合的医师应具备三个特质。"
            + "你可以先约一两位医师做初诊，观察对方是否顺守医道、不炫技。"
            + "若初诊后你感到安心、被理解，那就是适合你的人选。"
        let paragraphs = AIAnswerFormatter.paragraphs(in: raw)
        XCTAssertTrue(paragraphs.count > 1)
        XCTAssertEqual(paragraphs.joined(), raw)
        for paragraph in paragraphs {
            XCTAssertFalse(paragraph.hasPrefix("。"))
            XCTAssertFalse(paragraph.hasPrefix("」"))
        }
    }

    func testHonorsExistingLineBreaks() {
        let raw = "第一段。\n\n第二段。"
        XCTAssertEqual(AIAnswerFormatter.paragraphs(in: raw), ["第一段。", "第二段。"])
    }

    func testKeepsClosingQuoteWithPreviousSentence() {
        let raw = String(repeating: "他说「顺以则也。」", count: 6) + "尾。"
        let paragraphs = AIAnswerFormatter.paragraphs(in: raw)
        XCTAssertEqual(paragraphs.joined(), raw)
        for paragraph in paragraphs {
            XCTAssertFalse(paragraph.hasPrefix("」"))
        }
    }

    func testMergesShortTailIntoPreviousParagraph() {
        let raw = String(repeating: "这是一句足够长的话用来占满一段。", count: 4) + "短尾。"
        let paragraphs = AIAnswerFormatter.paragraphs(in: raw)
        XCTAssertEqual(paragraphs.joined(), raw)
        XCTAssertFalse(paragraphs.contains("短尾。"))
    }
}
