import SwiftUI
import SwiftData

/// iPhone 11：TabView 会在冷启动就建好所有 Tab。本月页较重，等点进这个 Tab 再装，避免把主线程拖死被 SIGKILL。
struct MonthTabRoot: View {
    @Environment(AppNavigation.self) private var appNavigation
    @State private var loaded = false

    var body: some View {
        NavigationStack {
            Group {
                if loaded {
                    MonthStatsView()
                } else {
                    Color.clear
                        .parchmentBackground(hidesTabBar: false)
                }
            }
        }
        .onChange(of: appNavigation.selectedTab) { _, tab in
            if tab == .month {
                loaded = true
            }
        }
        .onAppear {
            if appNavigation.selectedTab == .month {
                loaded = true
            }
        }
    }
}

struct MonthStatsView: View {
    @Query(sort: \ReadingRecord.createdAt, order: .forward)
    private var records: [ReadingRecord]
    @Environment(\.locale) private var locale
    @Environment(\.modelContext) private var modelContext

    @State private var yearMonth: MonthCastStats.YearMonth
    @State private var selectedDay: Int

    private let calendar = MonthCastStats.calendar()
    private let store = HexagramStore.shared

    init() {
        let calendar = MonthCastStats.calendar()
        let current = MonthCastStats.YearMonth.current(calendar: calendar)
        _yearMonth = State(initialValue: current)
        _selectedDay = State(initialValue: calendar.component(.day, from: .now))
    }

    private var language: AppLanguage { AppLanguage.from(locale) }

    private var snapshot: MonthCastStats.Snapshot {
        MonthCastStats.snapshot(
            dates: records.map(\.createdAt),
            yearMonth: yearMonth,
            calendar: calendar
        )
    }

    private var currentMonth: MonthCastStats.YearMonth {
        MonthCastStats.YearMonth.current(calendar: calendar)
    }

    private var earliestMonth: MonthCastStats.YearMonth {
        records.map { MonthCastStats.YearMonth.from(date: $0.createdAt, calendar: calendar) }.min()
            ?? currentMonth
    }

    private var canGoPrev: Bool { yearMonth > earliestMonth }
    private var canGoNext: Bool { yearMonth < currentMonth }

    private var dayRecords: [ReadingRecord] {
        records.filter { record in
            let parts = calendar.dateComponents([.year, .month, .day], from: record.createdAt)
            return parts.year == yearMonth.year && parts.month == yearMonth.month && parts.day == selectedDay
        }
        .sorted { $0.createdAt > $1.createdAt }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                monthHeader
                VStack(spacing: 12) {
                    statsRow
                    calendarCard
                }
                dayDetail
            }
            .padding(.horizontal, 16)
            .padding(.top, 8)
            .padding(.bottom, 28)
        }
        .scrollIndicators(.hidden)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .navigationBar)
        .parchmentBackground(hidesTabBar: false)
        .onChange(of: yearMonth) { _, newValue in
            selectedDay = MonthCastStats.defaultSelectedDay(
                yearMonth: newValue,
                now: .now,
                countsByDay: MonthCastStats.snapshot(
                    dates: records.map(\.createdAt),
                    yearMonth: newValue,
                    calendar: calendar
                ).countsByDay,
                calendar: calendar
            )
        }
    }

    private var monthHeader: some View {
        HStack {
            monthArrow(systemName: "chevron.left", enabled: canGoPrev) {
                yearMonth = yearMonth.adding(months: -1, calendar: calendar)
            }
            Spacer()
            Text(monthTitle)
                .font(.title3.weight(.semibold))
            Spacer()
            monthArrow(systemName: "chevron.right", enabled: canGoNext) {
                yearMonth = yearMonth.adding(months: 1, calendar: calendar)
            }
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 6)
    }

    private var monthTitle: String {
        if language.isEnglish {
            let formatter = DateFormatter()
            formatter.locale = Locale(identifier: "en")
            formatter.dateFormat = "MMMM yyyy"
            let date = calendar.date(from: DateComponents(year: yearMonth.year, month: yearMonth.month, day: 1))
            return date.map { formatter.string(from: $0) } ?? "\(yearMonth.month)/\(yearMonth.year)"
        }
        return "\(yearMonth.year)年\(yearMonth.month)月"
    }

    private func monthArrow(systemName: String, enabled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.body.weight(.semibold))
                .foregroundStyle(enabled ? AppTheme.accent : AppTheme.accent.opacity(0.25))
                .frame(width: 36, height: 36)
                .contentShape(Rectangle())
        }
        .disabled(!enabled)
        .buttonStyle(.plain)
        .accessibilityLabel(systemName.contains("left") ? "上个月".ui("Previous month") : "下个月".ui("Next month"))
    }

    private var statsRow: some View {
        HStack(spacing: 10) {
            statCard(value: "\(snapshot.totalCount)", label: "总次数".ui("Total"))
            statCard(value: "\(snapshot.elapsedDays)", label: "天数".ui("Days"))
            statCard(value: snapshot.formattedAverage, label: "日均".ui("Daily avg"))
        }
    }

    private func statCard(value: String, label: String) -> some View {
        VStack(spacing: 6) {
            Text(value)
                .font(.title2.weight(.semibold))
                .foregroundStyle(AppTheme.accent)
            Text(label)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 16)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color.white.opacity(0.92))
        )
    }

    private var calendarCard: some View {
        calendarGrid
            .padding(.horizontal, 16)
            .padding(.top, 8)
            .padding(.bottom, 16)
            .background(
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .fill(Color.white.opacity(0.72))
            )
    }

    private var weekdayHeaders: [String] {
        language.isEnglish
            ? ["S", "M", "T", "W", "T", "F", "S"]
            : ["日", "一", "二", "三", "四", "五", "六"]
    }

    private var calendarGrid: some View {
        let days = MonthCastStats.calendarDays(yearMonth: yearMonth, calendar: calendar)
        let remainder = days.count % 7
        let padded = remainder == 0 ? days : days + Array(repeating: nil, count: 7 - remainder)
        let weekCount = padded.count / 7
        return VStack(spacing: 8) {
            HStack(spacing: 0) {
                ForEach(0..<7, id: \.self) { index in
                    Text(weekdayHeaders[index])
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity)
                }
            }
            ForEach(0..<weekCount, id: \.self) { week in
                HStack(spacing: 0) {
                    ForEach(0..<7, id: \.self) { column in
                        let day = padded[week * 7 + column]
                        if let day {
                            dayCell(day)
                        } else {
                            Color.clear.frame(maxWidth: .infinity, minHeight: 44)
                        }
                    }
                }
            }
        }
    }

    private func dayCell(_ day: Int) -> some View {
        let count = snapshot.countsByDay[day] ?? 0
        let selected = selectedDay == day
        return Button {
            selectedDay = day
        } label: {
            VStack(spacing: 2) {
                Text("\(day)")
                    .font(.subheadline.weight(selected ? .semibold : .regular))
                    .foregroundStyle(selected ? Color.white : .primary)
                if count > 0 {
                    Text("\(count)")
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(
                            selected
                            ? Color.white.opacity(0.9)
                            : Color(red: 0.82, green: 0.42, blue: 0.18)
                        )
                } else {
                    Color.clear.frame(height: 14)
                }
            }
            .frame(maxWidth: .infinity, minHeight: 44)
            .background(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(selected ? AppTheme.accent : Color.clear)
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(dayAccessibility(day: day, count: count))
    }

    private func dayAccessibility(day: Int, count: Int) -> String {
        if count == 0 {
            return "\(yearMonth.month)月\(day)日".ui("\(monthShort) \(day)")
        }
        return "\(yearMonth.month)月\(day)日 \(count) 次".ui("\(monthShort) \(day), \(count) casts")
    }

    private var monthShort: String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en")
        formatter.dateFormat = "MMM"
        let date = calendar.date(from: DateComponents(year: yearMonth.year, month: yearMonth.month, day: 1))
        return date.map { formatter.string(from: $0) } ?? ""
    }

    private var dayDetail: some View {
        let items = dayRecords
        return VStack(alignment: .leading, spacing: 10) {
            Text(dayTitle(count: items.count))
                .font(.subheadline.weight(.semibold))
            if items.isEmpty {
                Text("这一天还没有起卦".ui("No casts this day"))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .padding(.top, 4)
            } else {
                VStack(spacing: 0) {
                    ForEach(items, id: \.persistentModelID) { record in
                        NavigationLink {
                            ResultView(record: record)
                        } label: {
                            HStack(alignment: .center, spacing: 8) {
                                ReadingRecordRow(record: record, store: store)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                Image(systemName: "chevron.right")
                                    .font(.footnote.weight(.semibold))
                                    .foregroundStyle(.tertiary)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        if record.persistentModelID != items.last?.persistentModelID {
                            Divider()
                        }
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(Color.white.opacity(0.72))
                )
            }
        }
    }

    private func dayTitle(count: Int) -> String {
        if language.isEnglish {
            return "\(monthShort) \(selectedDay) · \(count) \(count == 1 ? "cast" : "casts")"
        }
        return "\(yearMonth.month)月\(selectedDay)日 · \(count) 次"
    }
}
