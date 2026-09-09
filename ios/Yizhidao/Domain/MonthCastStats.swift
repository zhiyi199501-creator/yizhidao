import Foundation

enum MonthCastStats {
    static func calendar(timeZone: TimeZone = .current) -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        calendar.firstWeekday = 1
        return calendar
    }

    struct YearMonth: Equatable, Hashable, Comparable {
        var year: Int
        var month: Int

        static func current(now: Date = .now, calendar: Calendar) -> YearMonth {
            let parts = calendar.dateComponents([.year, .month], from: now)
            return YearMonth(year: parts.year ?? 1, month: parts.month ?? 1)
        }

        static func from(date: Date, calendar: Calendar) -> YearMonth {
            let parts = calendar.dateComponents([.year, .month], from: date)
            return YearMonth(year: parts.year ?? 1, month: parts.month ?? 1)
        }

        func adding(months: Int, calendar: Calendar) -> YearMonth {
            let start = calendar.date(from: DateComponents(year: year, month: month, day: 1)) ?? .now
            let shifted = calendar.date(byAdding: .month, value: months, to: start) ?? start
            return YearMonth.from(date: shifted, calendar: calendar)
        }

        func dayCount(calendar: Calendar) -> Int {
            guard let start = calendar.date(from: DateComponents(year: year, month: month, day: 1)),
                  let range = calendar.range(of: .day, in: .month, for: start)
            else { return 30 }
            return range.count
        }

        static func < (lhs: YearMonth, rhs: YearMonth) -> Bool {
            lhs.year < rhs.year || (lhs.year == rhs.year && lhs.month < rhs.month)
        }
    }

    struct Snapshot: Equatable {
        let totalCount: Int
        let elapsedDays: Int
        let dailyAverage: Double
        let countsByDay: [Int: Int]

        var formattedAverage: String { formatAverage(dailyAverage) }
    }

    static func elapsedDays(yearMonth: YearMonth, now: Date, calendar: Calendar) -> Int {
        let current = YearMonth.current(now: now, calendar: calendar)
        if yearMonth == current {
            return min(calendar.component(.day, from: now), yearMonth.dayCount(calendar: calendar))
        }
        if yearMonth < current {
            return yearMonth.dayCount(calendar: calendar)
        }
        return 0
    }

    static func snapshot(
        dates: [Date],
        yearMonth: YearMonth,
        calendar: Calendar,
        now: Date = .now
    ) -> Snapshot {
        var counts: [Int: Int] = [:]
        for date in dates {
            let parts = calendar.dateComponents([.year, .month, .day], from: date)
            guard parts.year == yearMonth.year, parts.month == yearMonth.month, let day = parts.day else {
                continue
            }
            counts[day, default: 0] += 1
        }
        let total = counts.values.reduce(0, +)
        let days = elapsedDays(yearMonth: yearMonth, now: now, calendar: calendar)
        let average = days == 0 ? 0.0 : Double(total) / Double(days)
        return Snapshot(totalCount: total, elapsedDays: days, dailyAverage: average, countsByDay: counts)
    }

    /// Sunday-first month cells. `nil` is a leading blank before day 1.
    static func calendarDays(yearMonth: YearMonth, calendar: Calendar) -> [Int?] {
        guard let start = calendar.date(from: DateComponents(year: yearMonth.year, month: yearMonth.month, day: 1)),
              let range = calendar.range(of: .day, in: .month, for: start)
        else { return [] }
        let weekday = calendar.component(.weekday, from: start)
        let leading = (weekday - calendar.firstWeekday + 7) % 7
        return Array(repeating: nil, count: leading) + Array(1...range.count)
    }

    static func defaultSelectedDay(
        yearMonth: YearMonth,
        now: Date,
        countsByDay: [Int: Int],
        calendar: Calendar
    ) -> Int {
        let current = YearMonth.current(now: now, calendar: calendar)
        if yearMonth == current {
            return min(calendar.component(.day, from: now), yearMonth.dayCount(calendar: calendar))
        }
        return countsByDay.keys.max() ?? 1
    }

    static func formatAverage(_ value: Double) -> String {
        if value == 0 { return "0" }
        let rounded = (value * 10).rounded() / 10
        if rounded == rounded.rounded() {
            return String(Int(rounded))
        }
        return String(format: "%.1f", rounded)
    }
}
