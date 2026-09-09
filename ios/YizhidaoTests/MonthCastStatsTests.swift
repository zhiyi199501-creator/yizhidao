import XCTest
@testable import Yizhidao

final class MonthCastStatsTests: XCTestCase {
    private var calendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Asia/Shanghai")!
        calendar.firstWeekday = 1
        return calendar
    }

    func testSnapshotUsesElapsedCalendarDaysForAverage() {
        let ym = MonthCastStats.YearMonth(year: 2026, month: 9)
        let now = date(2026, 9, 7, 15, 0)
        let dates = [
            date(2026, 8, 31, 23, 50),
            date(2026, 9, 6, 10, 34),
            date(2026, 9, 6, 10, 36),
            date(2026, 9, 6, 10, 40),
            date(2026, 9, 6, 11, 0),
            date(2026, 9, 6, 12, 0),
            date(2026, 9, 6, 13, 0),
            date(2026, 9, 7, 10, 34),
        ]
        let snap = MonthCastStats.snapshot(dates: dates, yearMonth: ym, calendar: calendar, now: now)
        XCTAssertEqual(snap.totalCount, 7)
        XCTAssertEqual(snap.elapsedDays, 7)
        XCTAssertEqual(snap.countsByDay[6], 6)
        XCTAssertEqual(snap.countsByDay[7], 1)
        XCTAssertEqual(snap.formattedAverage, "1")
    }

    func testPastMonthUsesFullMonthLength() {
        let snap = MonthCastStats.snapshot(
            dates: [date(2026, 8, 3, 10, 0)],
            yearMonth: .init(year: 2026, month: 8),
            calendar: calendar,
            now: date(2026, 9, 7, 15, 0)
        )
        XCTAssertEqual(snap.elapsedDays, 31)
        XCTAssertEqual(snap.formattedAverage, "0")
    }

    func testFormatAverageKeepsOneDecimalWhenNeeded() {
        XCTAssertEqual(MonthCastStats.formatAverage(12.5), "12.5")
        XCTAssertEqual(MonthCastStats.formatAverage(3), "3")
        XCTAssertEqual(MonthCastStats.formatAverage(0), "0")
    }

    func testSeptember2026CalendarStartsTuesday() {
        let days = MonthCastStats.calendarDays(
            yearMonth: .init(year: 2026, month: 9),
            calendar: calendar
        )
        XCTAssertEqual(days.prefix(5).map { $0 }, [nil, nil, 1, 2, 3])
        XCTAssertEqual(days.last, 30)
        XCTAssertEqual(days.compactMap { $0 }.count, 30)
    }

    func testDefaultSelectedDayPrefersTodayInCurrentMonth() {
        let now = date(2026, 9, 7, 15, 0)
        let day = MonthCastStats.defaultSelectedDay(
            yearMonth: .init(year: 2026, month: 9),
            now: now,
            countsByDay: [6: 6],
            calendar: calendar
        )
        XCTAssertEqual(day, 7)
    }

    func testDefaultSelectedDayFallsBackToLastActiveDay() {
        let now = date(2026, 9, 7, 15, 0)
        let day = MonthCastStats.defaultSelectedDay(
            yearMonth: .init(year: 2026, month: 8),
            now: now,
            countsByDay: [3: 1, 18: 2],
            calendar: calendar
        )
        XCTAssertEqual(day, 18)
    }

    func testYearMonthOrderingAndShift() {
        let august = MonthCastStats.YearMonth(year: 2026, month: 8)
        let september = august.adding(months: 1, calendar: calendar)
        XCTAssertEqual(september, MonthCastStats.YearMonth(year: 2026, month: 9))
        XCTAssertTrue(august < september)
        XCTAssertEqual(september.adding(months: -1, calendar: calendar), august)
    }

    private func date(_ year: Int, _ month: Int, _ day: Int, _ hour: Int, _ minute: Int) -> Date {
        calendar.date(from: DateComponents(year: year, month: month, day: day, hour: hour, minute: minute))!
    }
}
