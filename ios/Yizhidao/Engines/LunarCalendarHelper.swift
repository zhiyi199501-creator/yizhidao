import Foundation

struct LunarTimeComponents: Equatable, Sendable {
    /// 地支数 1...12（子=1 ... 亥=12）
    let yearBranch: Int
    /// 农历月 1...12（闰月按所在月序）
    let month: Int
    /// 农历日 1...30
    let day: Int
    /// 时辰地支数 1...12（子时=1 ... 亥时=12）
    let hourBranch: Int
}

enum LunarCalendarHelper {
    private static let branchNames = ["子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"]

    static func branchName(_ n: Int) -> String {
        guard (1...12).contains(n) else { return "?" }
        return branchNames[n - 1]
    }

    /// 将公历小时 0...23 转为十二时辰序号（子=1 ... 亥=12）。
    /// 子时跨日：23:00–00:59。
    static func shichen(fromHour hour: Int) -> Int {
        let h = ((hour % 24) + 24) % 24
        if h == 23 || h == 0 { return 1 } // 子
        return (h + 1) / 2 + 1
    }

    static func components(from date: Date, calendar: Calendar = .current) -> LunarTimeComponents {
        var chinese = Calendar(identifier: .chinese)
        chinese.locale = Locale(identifier: "zh_CN")
        chinese.timeZone = calendar.timeZone

        let comps = chinese.dateComponents([.year, .month, .day], from: date)
        // Chinese calendar `year` is cycle year 1...60 in the current era.
        let cycleYear = comps.year ?? 1
        let yearBranch = ((cycleYear - 1) % 12) + 1
        let month = comps.month ?? 1
        let day = comps.day ?? 1

        let hour = calendar.component(.hour, from: date) // 0...23
        let hourBranch = shichen(fromHour: hour)

        return LunarTimeComponents(
            yearBranch: yearBranch,
            month: month,
            day: day,
            hourBranch: hourBranch
        )
    }

    /// 公历取数：年支仍取干支年，月/日用公历，时用 1...24（0 点记为 24）。
    static func solarComponents(from date: Date, calendar: Calendar = .current) -> LunarTimeComponents {
        let yearBranch = components(from: date, calendar: calendar).yearBranch
        let month = calendar.component(.month, from: date)
        let day = calendar.component(.day, from: date)
        let hour24 = calendar.component(.hour, from: date)
        let hour = hour24 == 0 ? 24 : hour24
        return LunarTimeComponents(
            yearBranch: yearBranch,
            month: month,
            day: day,
            hourBranch: hour
        )
    }

    static func summary(from date: Date, calendar: Calendar = .current) -> String {
        let c = components(from: date, calendar: calendar)
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "zh_CN")
        formatter.dateFormat = "yyyy/M/d HH:mm"
        let shi = "\(branchName(c.hourBranch))时(\(c.hourBranch))"
        return "\(formatter.string(from: date)) · 农历\(branchName(c.yearBranch))年\(c.month)月\(c.day)日 \(shi)"
    }
}
