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
            Picker("模式", selection: $mode) {
                ForEach(Mode.allCases) { m in
                    Text(m.rawValue).tag(m)
                }
            }
            .pickerStyle(.segmented)

            switch mode {
            case .threeNumbers:
                threeNumbersForm
            case .time:
                timeForm
            }

            if let errorMessage {
                Text(errorMessage)
                    .font(.footnote)
                    .foregroundStyle(.red)
            }

            Button {
                cast()
            } label: {
                Text("起卦")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
            }
            .buttonStyle(.borderedProminent)
            .tint(Color(red: 0.45, green: 0.22, blue: 0.18))
        }
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
                Text("三个数分别对应上卦、下卦、动爻；可手输或点随机。")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Spacer(minLength: 8)
                Button("清空") {
                    n1 = ""
                    n2 = ""
                    n3 = ""
                    errorMessage = nil
                }
                .buttonStyle(.bordered)
                .disabled(n1.isEmpty && n2.isEmpty && n3.isEmpty)
            }
        }
    }

    private var timeForm: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("占问时刻")
                Spacer()
                Button {
                    showDatePicker = true
                } label: {
                    Text(hyphenDateTime(selectedDate))
                        .font(.body.monospacedDigit())
                        .foregroundStyle(.primary)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 7)
                        .background(Color(.tertiarySystemFill), in: RoundedRectangle(cornerRadius: 8, style: .continuous))
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
                            .environment(\.locale, Locale(identifier: "zh_CN"))
                            .environment(\.calendar, Calendar(identifier: .gregorian))
                            .labelsHidden()

                            Text("时间")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                                .padding(.horizontal, 4)

                            DatePicker(
                                "时间",
                                selection: $selectedDate,
                                displayedComponents: [.hourAndMinute]
                            )
                            .datePickerStyle(.wheel)
                            .environment(\.locale, Locale(identifier: "zh_CN"))
                            .environment(\.calendar, Calendar(identifier: .gregorian))
                            .labelsHidden()
                            .frame(maxWidth: .infinity)
                        }
                        .padding()
                    }
                    .navigationTitle("选择时刻")
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItem(placement: .confirmationAction) {
                            Button("完成") { showDatePicker = false }
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
                Text(
                    "取数：\(LunarCalendarHelper.branchName(comps.yearBranch))年(\(comps.yearBranch)) + 公历\(comps.month)月\(comps.day)日 + \(comps.hourBranch)时(1–24)"
                )
                .font(.caption)
                .foregroundStyle(.secondary)
            } else {
                Text(
                    "取数：\(LunarCalendarHelper.branchName(comps.yearBranch))年(\(comps.yearBranch)) + 农历\(comps.month)月\(comps.day)日 + \(LunarCalendarHelper.branchName(comps.hourBranch))时(\(comps.hourBranch))"
                )
                .font(.caption)
                .foregroundStyle(.secondary)
            }
            Text("上卦=(年+月+日)÷8余；下卦与动爻=(年+月+日+时)分别÷8、÷6取余。")
                .font(.caption2)
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
            Text(title)
                .frame(width: 56, alignment: .leading)
                .font(.subheadline)
            TextField("输入数字", text: text)
                .keyboardType(.numberPad)
                .textFieldStyle(.roundedBorder)
            Button("随机", action: onRandom)
                .buttonStyle(.bordered)
        }
    }

    private func cast() {
        errorMessage = nil
        let q = question.trimmingCharacters(in: .whitespacesAndNewlines)
        let questionValue = q.isEmpty ? nil : q

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
                question: questionValue,
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
                question: questionValue,
                at: selectedDate
            )
            onResult(result)
        }
    }
}
