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

final class HexagramStoreTests: XCTestCase {
    func testAllHexagramsHaveXiangTexts() {
        let store = HexagramStore(bundle: Bundle(for: HexagramStore.self))
        XCTAssertEqual(store.hexagrams.count, 64)
        for h in store.hexagrams {
            XCTAssertFalse(h.daxiang.isEmpty, "missing daxiang #\(h.number)")
            XCTAssertEqual(h.xiaoxiang.count, 6, "xiaoxiang count #\(h.number)")
            XCTAssertEqual(h.yaoci.count, 6, "yaoci count #\(h.number)")
        }
        guard let qian = store.hexagram(number: 1) else {
            return XCTFail("missing hexagram 1")
        }
        XCTAssertTrue(qian.daxiang.contains("自强不息"))
        XCTAssertTrue(qian.xiaoXiang(at: 1).contains("阳在下"))
        XCTAssertTrue(qian.guaci.contains("元"))
    }
}
