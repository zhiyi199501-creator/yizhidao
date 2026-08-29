import SwiftUI

struct DigitalCastView: View {
    enum Mode: String, CaseIterable, Identifiable {
        case threeNumbers = "输入三数"
        case time = "时间起卦"
        var id: String { rawValue }
    }

    @Binding var question: String
    var onResult: (CastResult) -> Void

    @State private var mode: Mode = .threeNumbers
    @State private var n1 = ""
    @State private var n2 = ""
    @State private var n3 = ""
    @State private var selectedDate = Date()
    @State private var showDatePicker = false
    @State private var useSolarNumbers = false
    @State private var errorMessage: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Picker("模式".zh, selection: $mode) {
                ForEach(Mode.allCases) { m in
                    Text(m.rawValue.zh).tag(m)
                }
            }
            .pickerStyle(.segmented)
            .onChange(of: mode) { _, newMode in
                errorMessage = nil
                if newMode == .time {
                    selectedDate = .now
                }
            }

            switch mode {
            case .threeNumbers:
                threeNumbersForm
            case .time:
                timeForm
            }

            if let errorMessage {
                Text(errorMessage.zh)
                    .font(.footnote)
                    .foregroundStyle(.red)
            }

            Button {
                cast()
            } label: {
                Text("起卦".zh)
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
            }
            .buttonStyle(.borderedProminent)
            .tint(AppTheme.accent)
            .disabled(!questionReady || (mode == .threeNumbers && !threeNumbersReady))
        }
    }

    private var questionReady: Bool {
        !question.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private var threeNumbersReady: Bool {
        guard let a = Int(n1), let b = Int(n2), let c = Int(n3) else { return false }
        return a > 0 && b > 0 && c > 0
    }

    private var threeNumbersForm: some View {
        VStack(spacing: 12) {
            numberRow(title: "上卦数", text: $n1) {
                n1 = String(Int.random(in: 10...999))
            }
            numberRow(title: "下卦数", text: $n2) {
                n2 = String(Int.random(in: 10...999))
            }
            numberRow(title: "动爻数", text: $n3) {
                n3 = String(Int.random(in: 10...999))
            }
            HStack {
                Text("从上往下输入3个数起卦".zh)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Button("清空".zh) {
                    n1 = ""
                    n2 = ""
                    n3 = ""
                    errorMessage = nil
                }
                .buttonStyle(.bordered)
            }
        }
    }

    private var timeForm: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("占问时刻".zh)
                Spacer()
                Button {
                    showDatePicker = true
                } label: {
                    Text(hyphenDateTime(selectedDate).zh)
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
                            Button("取消".zh) { showDatePicker = false }
                        }
                        ToolbarItem(placement: .confirmationAction) {
                            Button("完成".zh) { showDatePicker = false }
                        }
                    }
                }
                .presentationDetents([.large])
                .presentationDragIndicator(.visible)
            }
            Toggle("公历取数", isOn: $useSolarNumbers)
            let comps = useSolarNumbers
                ? LunarCalendarHelper.solarComponents(from: selectedDate)
                : LunarCalendarHelper.components(from: selectedDate)
            if useSolarNumbers {
                Text("取数：\(LunarCalendarHelper.branchName(comps.yearBranch))年(\(comps.yearBranch)) + 公历\(comps.month)月\(comps.day)日 + \(comps.hourBranch)时(1–24)".zh)
                .font(.caption)
                .foregroundStyle(.secondary)
            } else {
                Text("取数：\(LunarCalendarHelper.branchName(comps.yearBranch))年(\(comps.yearBranch)) + 农历\(comps.month)月\(comps.day)日 + \(LunarCalendarHelper.branchName(comps.hourBranch))时(\(comps.hourBranch))".zh)
                .font(.caption)
                .foregroundStyle(.secondary)
            }
            Text("以当前时刻起卦，或者选择某个时刻起卦。".zh)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }

    private func hyphenDateTime(_ date: Date) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = .current
        f.dateFormat = "yyyy-MM-dd HH:mm"
        return f.string(from: date)
    }

    private func numberRow(title: String, text: Binding<String>, onRandom: @escaping () -> Void) -> some View {
        HStack(spacing: 10) {
            Text(title.zh)
                .frame(width: 56, alignment: .leading)
                .font(.subheadline)
            TextField("输入数字", text: text)
                .keyboardType(.numberPad)
                .appTextFieldStyle()
            Button("随机".zh) {
                TapSoundPlayer.shared.play()
                onRandom()
            }
                .buttonStyle(.bordered)
                .tint(AppTheme.accent)
        }
    }

    private func cast() {
        errorMessage = nil
        let q = question.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !q.isEmpty else {
            errorMessage = "请填写所问何事"
            return
        }

        switch mode {
        case .threeNumbers:
            guard let a = Int(n1), let b = Int(n2), let c = Int(n3), a > 0, b > 0, c > 0 else {
                errorMessage = "请填写三个正整数"
                return
            }
            let result = DigitalCastingEngine.cast(
                number1: a,
                number2: b,
                number3: c,
                question: q,
                at: .now
            )
            onResult(result)
        case .time:
            let comps = useSolarNumbers
                ? LunarCalendarHelper.solarComponents(from: selectedDate)
                : LunarCalendarHelper.components(from: selectedDate)
            let result = DigitalCastingEngine.cast(
                yearBranch: comps.yearBranch,
                month: comps.month,
                day: comps.day,
                hour: comps.hourBranch,
                question: q,
                at: selectedDate
            )
            onResult(result)
        }
    }
}
