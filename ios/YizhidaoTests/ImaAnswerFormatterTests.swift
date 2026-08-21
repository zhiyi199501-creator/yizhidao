import XCTest
@testable import Yizhidao

final class ImaAnswerFormatterTests: XCTestCase {
    func testParsesTabTableAndHidesMarker() {
        let raw = """
        所以才能「亨」
        六、占断参考
        表格
        占问\t结果
        占婚姻\t小康之象。不会太富裕，但也不会饿死
        占失物\t往西边去找 2
        七、核心启示
        """
        let blocks = ImaAnswerFormatter.blocks(in: raw)
        XCTAssertEqual(blocks.count, 3)
        guard case .text(let before) = blocks[0] else { return XCTFail("text") }
        XCTAssertTrue(before.contains("占断参考"))
        XCTAssertFalse(before.contains("表格"))
        guard case .table(let rows) = blocks[1] else { return XCTFail("table") }
        XCTAssertEqual(rows.count, 3)
        XCTAssertEqual(rows[0], ["占问", "结果"])
        XCTAssertEqual(rows[1][0], "占婚姻")
        XCTAssertEqual(rows[2][1], "往西边去找")
        guard case .text(let after) = blocks[2] else { return XCTFail("after") }
        XCTAssertTrue(after.hasPrefix("七、核心启示"))
    }

    func testParsesMarkdownTable() {
        let raw = """
        对照如下
        | 占问 | 启示 |
        |:----:|:----:|
        | 占人事 | 要虚怀若谷 |
        | 女占男 | 非常好 |
        下文
        """
        let blocks = ImaAnswerFormatter.blocks(in: raw)
        XCTAssertEqual(blocks.count, 3)
        guard case .table(let rows) = blocks[1] else { return XCTFail("table") }
        XCTAssertEqual(rows[0], ["占问", "启示"])
        XCTAssertEqual(rows[1], ["占人事", "要虚怀若谷"])
        XCTAssertEqual(rows[2][0], "女占男")
    }

    func testStripsCitationFootnotes() {
        let text = """
        永远不行动 1
        这就是勇气 1。
        称为「初九」。1
        下卦为乾（天）1。
        凡提到「大」几乎都指阳2。
        1. 小畜不是小气
        第12卦仍是正文
        """
        let stripped = ImaAnswerFormatter.stripped(text)
        XCTAssertEqual(
            stripped,
            """
            永远不行动
            这就是勇气。
            称为「初九」。
            下卦为乾（天）。
            凡提到「大」几乎都指阳。
            1. 小畜不是小气
            第12卦仍是正文
            """
        )
    }

    func testStripsTableCellCitation() {
        XCTAssertEqual(ImaAnswerFormatter.stripped("祸起于萧墙之内 2"), "祸起于萧墙之内")
    }

    func testStripsThinkingProcessLabel() {
        let raw = """
        思考过程
        思考过程
        已浏览张庆祥讲易经_364.docx
        思考过程
        大壮卦九二爻详解
        """
        let blocks = ImaAnswerFormatter.blocks(in: raw)
        XCTAssertEqual(blocks.count, 1)
        guard case .text(let text) = blocks[0] else { return XCTFail("text") }
        XCTAssertFalse(text.contains("思考过程"))
        XCTAssertTrue(text.hasPrefix("已浏览"))
        XCTAssertTrue(text.contains("大壮卦九二爻详解"))
    }
}
