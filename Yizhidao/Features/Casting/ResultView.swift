import SwiftUI
import SwiftData

struct ResultView: View {
    private enum HexTab: String, CaseIterable, Identifiable {
        case primary = "本卦"
        case resulting = "之卦"
        var id: String { rawValue }
    }

    private let result: CastResult
    private let isNew: Bool
    private let record: ReadingRecord?

    @Environment(\.modelContext) private var modelContext
    @Environment(AppNavigation.self) private var appNavigation
    @State private var didSave = false
    @State private var selectedTab: HexTab = .primary
    @State private var questionText: String = ""
    @State private var verificationStatus: VerificationStatus = .none
    @State private var verificationNote: String = ""
    @State private var savedRecord: ReadingRecord?

    private var store: HexagramStore { .shared }
    private var primary: Hexagram? { store.hexagram(number: result.primaryNumber) }
    private var resulting: Hexagram? {
        guard let n = result.resultingNumber else { return nil }
        return store.hexagram(number: n)
    }

    private var focus: ReadingFocus {
        ReadingGuide.focus(movingPositions: result.movingPositions)
    }

    private var availableTabs: [HexTab] {
        resulting == nil ? [.primary] : HexTab.allCases
    }

    private var editableRecord: ReadingRecord? {
        record ?? savedRecord
    }

    init(result: CastResult, isNew: Bool = true) {
        self.result = result
        self.isNew = isNew
        self.record = nil
        _questionText = State(initialValue: result.question ?? "")
    }

    init(record: ReadingRecord) {
        self.result = record.toCastResult()
        self.isNew = false
        self.record = record
        _questionText = State(initialValue: record.question ?? "")
        _verificationStatus = State(initialValue: record.verificationStatus)
        _verificationNote = State(initialValue: record.verificationNote ?? "")
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                metaSection
                if editableRecord != nil {
                    verificationSection
                }
                figuresSection
                if availableTabs.count > 1 {
                    Picker("卦", selection: $selectedTab) {
                        ForEach(availableTabs) { tab in
                            Text(tab.rawValue).tag(tab)
                        }
                    }
                    .pickerStyle(.segmented)
                }
                hexagramTextSection(for: selectedTab)
                Text("经文版本：《易经证释》所引")
                    .font(.caption2)
                    .foregroundStyle(.tertiary)
                    .frame(maxWidth: .infinity, alignment: .trailing)
            }
            .padding()
        }
        .navigationTitle("卦象结果")
        .navigationBarTitleDisplayMode(.inline)
        .parchmentBackground()
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    appNavigation.openSimilarHexagram(for: result)
                } label: {
                    Label("同类", systemImage: "rectangle.stack")
                }
                .accessibilityLabel("查看同类卦")
            }
        }
        .onAppear {
            if resulting == nil { selectedTab = .primary }
            guard isNew, !didSave else { return }
            let inserted = ReadingRecord(from: result)
            modelContext.insert(inserted)
            try? modelContext.save()
            savedRecord = inserted
            questionText = inserted.question ?? ""
            verificationStatus = inserted.verificationStatus
            verificationNote = inserted.verificationNote ?? ""
            didSave = true
        }
    }

    private var metaSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label(result.method.displayName, systemImage: "seal")
                .font(.subheadline.weight(.semibold))
            Text(formattedTime(result.createdAt))
                .font(.footnote)
                .foregroundStyle(.secondary)
            if editableRecord != nil {
                    TextField("所问何事（可选）", text: $questionText, axis: .vertical)
                        .lineLimit(2...5)
                        .appTextFieldStyle()
                        .onChange(of: questionText) { _, newValue in
                            persistQuestion(newValue)
                        }
            } else if let question = result.question, !question.isEmpty {
                Text("所问：\(question)")
                    .font(.body)
            }
            if let numbers = result.numbers {
                Text("取数：\(numbers.map(String.init).joined(separator: " · "))")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(RoundedRectangle(cornerRadius: 12).fill(AppTheme.cardFill))
    }

    private var verificationSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Picker("状态", selection: $verificationStatus) {
                ForEach(VerificationStatus.allCases) { status in
                    Text(status.displayName).tag(status)
                }
            }
            .pickerStyle(.segmented)
            .onChange(of: verificationStatus) { _, newValue in
                persistVerification(status: newValue, note: verificationNote)
            }
            TextField("验证结果（可选）", text: $verificationNote, axis: .vertical)
                .lineLimit(2...5)
                .appTextFieldStyle()
                .onChange(of: verificationNote) { _, newValue in
                    persistVerification(status: verificationStatus, note: newValue)
                }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(RoundedRectangle(cornerRadius: 12).fill(AppTheme.cardFill))
    }

    private func persistQuestion(_ text: String) {
        guard let editableRecord else { return }
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        editableRecord.question = trimmed.isEmpty ? nil : trimmed
        try? modelContext.save()
    }

    private func persistVerification(status: VerificationStatus, note: String) {
        guard let editableRecord else { return }
        editableRecord.verificationStatus = status
        let trimmed = note.trimmingCharacters(in: .whitespacesAndNewlines)
        editableRecord.verificationNote = trimmed.isEmpty ? nil : trimmed
        try? modelContext.save()
    }

    private var figuresSection: some View {
        HStack(alignment: .top, spacing: 24) {
            VStack {
                if let primary {
                    Text("\(primary.symbol) \(primary.name)")
                        .font(.title3.weight(.bold))
                    Text("第\(primary.number)卦 · 本卦")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                HexagramFigureView(lines: result.lines, movingPositions: result.movingPositions)
            }
            .frame(maxWidth: .infinity)

            if let resultingNumber = result.resultingNumber,
               let resulting {
                let changedLines = changedLines(from: result.lines)
                VStack {
                    Text("\(resulting.symbol) \(resulting.name)")
                        .font(.title3.weight(.bold))
                    Text("第\(resultingNumber)卦 · 之卦")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    HexagramFigureView(lines: changedLines, movingPositions: [])
                }
                .frame(maxWidth: .infinity)
            }
        }
    }

    @ViewBuilder
    private func hexagramTextSection(for tab: HexTab) -> some View {
        let hex: Hexagram? = tab == .primary ? primary : resulting
        if let hex {
            VStack(alignment: .leading, spacing: 16) {
                section(showLead: shouldShowGuaciLead(tab: tab)) {
                    Text(hex.guaci)
                        .font(.body)
                        .lineSpacing(4)
                }
                section {
                    Text(prefixed("彖曰：", hex.tuanci))
                        .font(.body)
                        .lineSpacing(4)
                }
                section {
                    Text(prefixed("象曰：", hex.daxiang))
                        .font(.body)
                        .lineSpacing(4)
                }
                section {
                    VStack(alignment: .leading, spacing: 14) {
                        // 上爻在上，初爻在下（与卦象图一致）
                        ForEach((1...6).reversed(), id: \.self) { pos in
                            lineBlock(hex: hex, tab: tab, position: pos)
                        }
                    }
                }
            }
        }
    }

    private func prefixed(_ prefix: String, _ body: String) -> String {
        let text = body.trimmingCharacters(in: .whitespacesAndNewlines)
        if text.hasPrefix(prefix) { return text }
        let bare = String(prefix.dropLast()) // 「彖曰」/「象曰」
        if text.hasPrefix(bare) {
            let rest = text.dropFirst(bare.count).trimmingCharacters(in: .whitespaces)
            if rest.hasPrefix("：") || rest.hasPrefix(":") {
                return bare + rest
            }
            return prefix + rest
        }
        return prefix + text
    }

    /// 无动／三爻变：本卦卦辞主看；六爻变：之卦卦辞主看。
    private func shouldShowGuaciLead(tab: HexTab) -> Bool {
        switch focus.kind {
        case .primaryGuaci, .bothGuaci:
            return tab == .primary
        case .resultingGuaci:
            return tab == .resulting
        default:
            return false
        }
    }

    private func lineBlock(hex: Hexagram, tab: HexTab, position: Int) -> some View {
        let moving = result.movingPositions.contains(position)
        let showLead = shouldShowLead(tab: tab, position: position)
        let accent = moving ? Color.red : Color.primary

        return VStack(alignment: .leading, spacing: 6) {
            if showLead {
                leadBadge
            }
            Text(trimmedYaoCi(hex: hex, position: position))
                .font(.body)
                .foregroundStyle(accent)
                .lineSpacing(4)
            Text(xiangLine(hex: hex, position: position))
                .font(.body)
                .foregroundStyle(accent)
                .lineSpacing(4)
        }
    }

    private var leadBadge: some View {
        Text("主看")
            .font(.caption.weight(.bold))
            .foregroundStyle(.white)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(Color.red, in: Capsule())
    }

    private func trimmedYaoCi(hex: Hexagram, position: Int) -> String {
        hex.yaoCi(at: position)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func xiangLine(hex: Hexagram, position: Int) -> String {
        var xiang = hex.xiaoXiang(at: position)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        if xiang.hasPrefix("象曰：") {
            return xiang
        }
        if xiang.hasPrefix("象曰") {
            let rest = xiang.dropFirst(2).trimmingCharacters(in: .whitespaces)
            if rest.hasPrefix("：") || rest.hasPrefix(":") {
                return "象曰" + rest
            }
            return "象曰：" + rest
        }
        return "象曰：" + xiang
    }

    /// 多动爻且规则指定「为主」之爻时才标「主看」。
    private func shouldShowLead(tab: HexTab, position: Int) -> Bool {
        guard result.movingPositions.count >= 2 else { return false }
        switch focus.kind {
        case .primaryLines(_, let lead):
            return tab == .primary && lead == position
        case .resultingLines(_, let lead):
            return tab == .resulting && lead == position
        default:
            return false
        }
    }

    private func section(
        showLead: Bool = false,
        @ViewBuilder content: () -> some View
    ) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            if showLead {
                leadBadge
            }
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(RoundedRectangle(cornerRadius: 12).fill(AppTheme.cardFill))
    }

    private func changedLines(from lines: [LineValue]) -> [LineValue] {
        lines.map { $0.isChanging ? $0.changed : $0 }
    }

    private func formattedTime(_ date: Date) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "zh_CN")
        f.dateFormat = "yyyy年M月d日 HH:mm:ss"
        return "占卦时间：\(f.string(from: date))"
    }
}
