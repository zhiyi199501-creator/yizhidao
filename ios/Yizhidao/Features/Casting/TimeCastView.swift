import SwiftUI

/// 时间起卦入口。这里只挑「此刻」还是某个时刻、以及取农历还是公历，
/// 停在「此刻」时真正的取数要等敬告那一下（见 `CastingIntent.digitalTime`）。
struct TimeCastView: View {
    var onBegin: (CastingIntent) -> Void

    @State private var selectedDate = Date()
    /// false 表示还停在「此刻」，起卦时刻由敬告那一下决定。
    @State private var didPickTime = false
    @State private var dateBeforeEdit = Date()
    @State private var showDatePicker = false
    @State private var useSolarNumbers = false

    /// 停在「此刻」时按当下预览，取数要等敬告才真正落。
    private var previewDate: Date { didPickTime ? selectedDate : Date() }

    var body: some View {
        VStack(spacing: 0) {
            Spacer(minLength: 20)
            VStack(spacing: 36) {
                emblem
                timeForm
            }
            Spacer(minLength: 20)
            StartCastButton(title: "起卦") {
                onBegin(.digitalTime(moment: didPickTime ? selectedDate : nil, useSolar: useSolarNumbers))
            }
        }
    }

    /// 十二时辰盘，当前那一格点亮。
    private var emblem: some View {
        let current = LunarCalendarHelper.components(from: previewDate).hourBranch
        return ZStack {
            Circle()
                .strokeBorder(AppTheme.accent.opacity(0.3), lineWidth: 1.5)
            ForEach(1...12, id: \.self) { branch in
                Capsule()
                    .fill(AppTheme.accent.opacity(branch == current ? 0.9 : 0.36))
                    .frame(width: 2, height: branch == current ? 15 : 8)
                    .offset(y: -46)
                    .rotationEffect(.degrees(Double(branch - 1) * 30))
            }
        }
        .frame(width: 108, height: 108)
        .accessibilityHidden(true)
    }

    private var timeForm: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Text("占问时刻".zh)
                Spacer()
                if didPickTime {
                    Button("此刻".zh) {
                        selectedDate = .now
                        didPickTime = false
                    }
                    .font(.footnote)
                    .buttonStyle(.plain)
                    .foregroundStyle(AppTheme.accent)
                    .padding(.trailing, 4)
                }
                Button {
                    dateBeforeEdit = selectedDate
                    showDatePicker = true
                } label: {
                    Text((didPickTime ? hyphenDateTime(selectedDate) : "此刻").zh)
                        .font(.body.monospacedDigit())
                        .foregroundStyle(.primary)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 7)
                        .background(AppTheme.fieldFill, in: RoundedRectangle(cornerRadius: 8, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: 8, style: .continuous)
                                .stroke(AppTheme.fieldStroke, lineWidth: 1)
                        )
                }
                .buttonStyle(.plain)
            }
            .sheet(isPresented: $showDatePicker) {
                NavigationStack {
                    ScrollView {
                        VStack(alignment: .leading, spacing: 16) {
                            DatePicker(
                                "日期",
                                selection: $selectedDate,
                                displayedComponents: [.date]
                            )
                            .datePickerStyle(.graphical)
                            .environment(\.locale, AppLanguage.current.locale)
                            .environment(\.calendar, Calendar(identifier: .gregorian))
                            .labelsHidden()

                            Text("时间".zh)
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                                .padding(.horizontal, 4)

                            DatePicker(
                                "时间",
                                selection: $selectedDate,
                                displayedComponents: [.hourAndMinute]
                            )
                            .datePickerStyle(.wheel)
                            .environment(\.locale, AppLanguage.current.locale)
                            .environment(\.calendar, Calendar(identifier: .gregorian))
                            .labelsHidden()
                            .frame(maxWidth: .infinity)
                        }
                        .padding()
                    }
                    .navigationTitle("选择时刻".zh)
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) {
                            Button("取消".zh) {
                                selectedDate = dateBeforeEdit
                                showDatePicker = false
                            }
                        }
                        ToolbarItem(placement: .confirmationAction) {
                            Button("完成".zh) {
                                didPickTime = true
                                showDatePicker = false
                            }
                        }
                    }
                }
                .presentationDetents([.large])
                .presentationDragIndicator(.visible)
            }
            Toggle("公历取数".zh, isOn: $useSolarNumbers)
            Text(numbersLine.zh)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }

    private var numbersLine: String {
        let comps = useSolarNumbers
            ? LunarCalendarHelper.solarComponents(from: previewDate)
            : LunarCalendarHelper.components(from: previewDate)
        let year = "\(LunarCalendarHelper.branchName(comps.yearBranch))年(\(comps.yearBranch))"
        if useSolarNumbers {
            return "取数：\(year) + 公历\(comps.month)月\(comps.day)日 + \(comps.hourBranch)时(1–24)"
        }
        let hour = "\(LunarCalendarHelper.branchName(comps.hourBranch))时(\(comps.hourBranch))"
        return "取数：\(year) + 农历\(comps.month)月\(comps.day)日 + \(hour)"
    }

    private func hyphenDateTime(_ date: Date) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = .current
        f.dateFormat = "yyyy-MM-dd HH:mm"
        return f.string(from: date)
    }
}
