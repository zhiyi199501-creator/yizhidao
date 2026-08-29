import XCTest
@testable import Yizhidao

final class DigitalCastingEngineTests: XCTestCase {
    func testModRules() {
        XCTAssertEqual(DigitalCastingEngine.mod8(8), 8)
        XCTAssertEqual(DigitalCastingEngine.mod8(16), 8)
        XCTAssertEqual(DigitalCastingEngine.mod8(22), 6)
        XCTAssertEqual(DigitalCastingEngine.mod8(42), 2)
        XCTAssertEqual(DigitalCastingEngine.mod6(42), 6)
        XCTAssertEqual(DigitalCastingEngine.mod6(6), 6)
        XCTAssertEqual(DigitalCastingEngine.mod6(7), 1)
    }

    /// 壬寅年十二月初七 戌时(11)：上坎下乾、三爻动 → 需卦
    func testTimeCastWithShichen() {
        let result = DigitalCastingEngine.cast(
            yearBranch: 3,
            month: 12,
            day: 7,
            hour: 11
        )
        XCTAssertEqual(result.primaryNumber, 5) // 需
        XCTAssertEqual(result.movingPositions, [3])
        // 需 111010，三爻动 → 110010 = 节 #60
        XCTAssertEqual(result.resultingNumber, 60)
    }

    func testShichenFromHour() {
        XCTAssertEqual(LunarCalendarHelper.shichen(fromHour: 23), 1) // 子
        XCTAssertEqual(LunarCalendarHelper.shichen(fromHour: 0), 1)
        XCTAssertEqual(LunarCalendarHelper.shichen(fromHour: 1), 2)  // 丑
        XCTAssertEqual(LunarCalendarHelper.shichen(fromHour: 8), 5)  // 辰
        XCTAssertEqual(LunarCalendarHelper.shichen(fromHour: 20), 11) // 戌
        XCTAssertEqual(LunarCalendarHelper.shichen(fromHour: 22), 12) // 亥
    }

    func testSolarComponentsUsesGregorianMonthDayAndHour24() {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(secondsFromGMT: 8 * 3600)!
        var comps = DateComponents()
        comps.year = 2026
        comps.month = 8
        comps.day = 10
        comps.hour = 16
        comps.minute = 0
        let date = cal.date(from: comps)!
        let solar = LunarCalendarHelper.solarComponents(from: date, calendar: cal)
        XCTAssertEqual(solar.month, 8)
        XCTAssertEqual(solar.day, 10)
        XCTAssertEqual(solar.hourBranch, 16)

        comps.hour = 0
        let midnight = cal.date(from: comps)!
        XCTAssertEqual(LunarCalendarHelper.solarComponents(from: midnight, calendar: cal).hourBranch, 24)
    }

    func testThreeNumbersUpperLowerMoving() {
        // number1=6 → 坎上, number2=2 → 兑下, number3=6 → 上爻
        let result = DigitalCastingEngine.cast(number1: 6, number2: 2, number3: 6)
        XCTAssertEqual(result.primaryNumber, 60)
        XCTAssertEqual(result.movingPositions, [6])
    }
}

final class CoinCastingEngineTests: XCTestCase {
    func testLineMapping() {
        XCTAssertEqual(CoinCastingEngine.line(fromYangCount: 3), .oldYang)
        XCTAssertEqual(CoinCastingEngine.line(fromYangCount: 0), .oldYin)
        XCTAssertEqual(CoinCastingEngine.line(fromYangCount: 1), .youngYang)
        XCTAssertEqual(CoinCastingEngine.line(fromYangCount: 2), .youngYin)
    }

    func testAllYangHexagramIsQian() {
        let lines = Array(repeating: LineValue.youngYang, count: 6)
        let result = CoinCastingEngine.cast(lines: lines)
        XCTAssertEqual(result.primaryNumber, 1)
        XCTAssertTrue(result.movingPositions.isEmpty)
        XCTAssertNil(result.resultingNumber)
    }

    func testChangingLineProducesResulting() {
        var lines = Array(repeating: LineValue.youngYang, count: 6)
        lines[0] = .oldYang // 初爻动
        let result = CoinCastingEngine.cast(lines: lines)
        XCTAssertEqual(result.primaryNumber, 1)
        XCTAssertEqual(result.movingPositions, [1])
        // 乾初爻变 → 姤 #44 binary 011111
        XCTAssertEqual(result.resultingNumber, 44)
    }
}

final class KingWenTableTests: XCTestCase {
    func testSixtyFourUnique() {
        var seen = Set<String>()
        for n in 1...64 {
            let b = KingWenTable.binary(ofNumber: n)
            XCTAssertEqual(b.count, 6)
            XCTAssertFalse(seen.contains(b))
            seen.insert(b)
            XCTAssertEqual(KingWenTable.number(fromBits: b.map { Int(String($0))! }), n)
        }
    }
}

final class ReadingGuideTests: XCTestCase {
    func testZeroMovingUsesPrimaryGuaci() {
        let f = ReadingGuide.focus(movingPositions: [])
        XCTAssertEqual(f.kind, .primaryGuaci)
    }

    func testOneMovingUsesThatLine() {
        let f = ReadingGuide.focus(movingPositions: [3])
        XCTAssertEqual(f.kind, .primaryLines(positions: [3], lead: 3))
    }

    func testTwoMovingUpperIsLead() {
        let f = ReadingGuide.focus(movingPositions: [2, 5])
        XCTAssertEqual(f.kind, .primaryLines(positions: [2, 5], lead: 5))
    }

    func testThreeMovingBothGuaci() {
        let f = ReadingGuide.focus(movingPositions: [1, 3, 6])
        XCTAssertEqual(f.kind, .bothGuaci)
    }

    func testFourMovingResultingStaticLowerIsLead() {
        let f = ReadingGuide.focus(movingPositions: [1, 2, 4, 6])
        // static: 3, 5 → lower lead = 3
        XCTAssertEqual(f.kind, .resultingLines(positions: [3, 5], lead: 3))
    }

    func testFiveMovingResultingStatic() {
        let f = ReadingGuide.focus(movingPositions: [1, 2, 3, 4, 6])
        XCTAssertEqual(f.kind, .resultingLines(positions: [5], lead: 5))
    }

    func testSixMovingResultingGuaci() {
        let f = ReadingGuide.focus(movingPositions: [1, 2, 3, 4, 5, 6])
        XCTAssertEqual(f.kind, .resultingGuaci)
    }
}

final class HexagramStoreTests: XCTestCase {
    func testAllHexagramsHaveXiangTexts() throws {
        let store = HexagramStore(bundle: Bundle(for: HexagramStore.self))
        XCTAssertEqual(store.hexagrams.count, 64)
        for h in store.hexagrams {
            XCTAssertFalse(h.tuanci.isEmpty, "missing tuanci #\(h.number)")
            XCTAssertFalse(h.daxiang.isEmpty, "missing daxiang #\(h.number)")
            XCTAssertEqual(h.xiaoxiang.count, 6, "xiaoxiang count #\(h.number)")
            XCTAssertEqual(h.yaoci.count, 6, "yaoci count #\(h.number)")
        }
        guard let qian = store.hexagram(number: 1) else {
            return XCTFail("missing hexagram 1")
        }
        XCTAssertTrue(qian.tuanci.contains("大哉乾元"))
        XCTAssertTrue(qian.daxiang.contains("自强不息"))
        XCTAssertTrue(qian.xiaoXiang(at: 1).contains("阳在下"))
        XCTAssertTrue(qian.guaci.contains("元"))
        XCTAssertEqual(qian.yong?.ci.contains("用九"), true)
        XCTAssertFalse(qian.wenyan.isEmpty)
        XCTAssertEqual(store.hexagram(number: 2)?.yong?.ci.contains("用六"), true)
        XCTAssertEqual(store.hexagram(number: 24)?.yaoci.last?.contains("十年"), true)
        XCTAssertEqual(store.wings.map(\.title), ["系辞传", "说卦传", "序卦传", "杂卦传"])
        let xici = store.wings.first { $0.title == "系辞传" }
        XCTAssertEqual(xici?.chapters.first?.paragraphs.first?.hasPrefix("天尊地卑"), true)
        let lead = try NSRegularExpression(pattern: #"^\d+\.\d+"#)
        for chapter in xici?.chapters ?? [] {
            for paragraph in chapter.paragraphs {
                let range = NSRange(paragraph.startIndex..., in: paragraph)
                XCTAssertEqual(lead.numberOfMatches(in: paragraph, range: range), 0, paragraph)
            }
        }
    }
}

final class YijingIntroStoreTests: XCTestCase {
    func testIntroHasNineChapters() {
        let store = YijingIntroStore(bundle: Bundle(for: YijingIntroStore.self))
        XCTAssertEqual(store.chapters.count, 9)
        XCTAssertEqual(store.chapters.map(\.id), [
            "what", "purpose", "yin-yang-bagua", "hexagrams-lines",
            "how-to-read", "play-the-text", "how-to-cast", "changing-lines", "path",
        ])
        XCTAssertTrue(store.note.isEmpty)
        XCTAssertTrue(store.chapters[5].plainText.contains("观其象"))
        XCTAssertTrue(store.chapters[6].plainText.contains("数字起卦"))
        XCTAssertTrue(store.chapters[6].plainText.contains("输入三数"))
        XCTAssertTrue(store.chapters[6].plainText.contains("时间起卦"))
        XCTAssertTrue(store.chapters[6].plainText.contains("金钱起卦"))
        XCTAssertTrue(store.chapters[7].plainText.contains("主看"))
        XCTAssertTrue(store.chapters[2].blocks.contains { if case .figure(let kind, _) = $0 { return kind == "bagua" }; return false })
        XCTAssertTrue(store.chapters[7].blocks.contains { if case .table = $0 { return true }; return false })
        XCTAssertTrue(store.chapters[8].blocks.contains { if case .links = $0 { return true }; return false })
    }
}

final class ZhengshiStoreTests: XCTestCase {
    func testZhengshiHasSixtyFourHexagrams() {
        let store = ZhengshiStore(bundle: Bundle(for: ZhengshiStore.self))
        store.loadIfNeeded()
        XCTAssertEqual(store.parts.map(\.id), ["front", "upper", "lower", "wings"])
        let upper = store.parts.first { $0.id == "upper" }?.chapters ?? []
        let lower = store.parts.first { $0.id == "lower" }?.chapters ?? []
        XCTAssertEqual(upper.count, 30)
        XCTAssertEqual(lower.count, 34)
        XCTAssertEqual(upper.first?.title, "乾卦")
        XCTAssertTrue(upper.first?.sections.contains(where: { $0.title == "总释象例" }) == true)
        let qianText = upper.first?.sections.flatMap(\.paragraphs).joined() ?? ""
        XCTAssertTrue(qianText.contains("潜龙"))
        XCTAssertEqual(store.parts.first { $0.id == "wings" }?.chapters.map(\.title), [
            "系辞传", "说卦传", "序卦传", "杂卦传",
        ])
        XCTAssertFalse(store.note.isEmpty)
    }
}
