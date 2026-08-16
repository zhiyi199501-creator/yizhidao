import SwiftUI
import SwiftData

struct ResultView: View {
    private let result: CastResult
    private let isNew: Bool
    private let record: ReadingRecord?
    private let showSimilarHexagramButton: Bool

    @Environment(\.modelContext) private var modelContext
    @Environment(AppNavigation.self) private var appNavigation
    @State private var didSave = false
    @State private var questionText: String = ""
    @State private var verificationStatus: VerificationStatus = .none
    @State private var verificationNote: String = ""
    @State private var savedRecord: ReadingRecord?
    @State private var showAIAnalysis = false
    @State private var showLoginForAI = false

    private var resultForAnalysis: CastResult {
        let trimmedQuestion = questionText.trimmingCharacters(in: .whitespacesAndNewlines)
        return CastResult(
            method: result.method,
            createdAt: result.createdAt,
            question: trimmedQuestion.isEmpty ? nil : trimmedQuestion,
            numbers: result.numbers,
            primaryNumber: result.primaryNumber,
            resultingNumber: result.resultingNumber,
            lines: result.lines,
            movingPositions: result.movingPositions
        )
    }

    private var editableRecord: ReadingRecord? {
        record ?? savedRecord
    }

    init(result: CastResult, isNew: Bool = true, showSimilarHexagramButton: Bool = true) {
        self.result = result
        self.isNew = isNew
        self.record = nil
        self.showSimilarHexagramButton = showSimilarHexagramButton
        _questionText = State(initialValue: result.question ?? "")
    }

    init(record: ReadingRecord, showSimilarHexagramButton: Bool = true) {
        self.result = record.toCastResult()
        self.isNew = false
        self.record = record
        self.showSimilarHexagramButton = showSimilarHexagramButton
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
                HexagramReadingBody(result: result)
            }
            .padding()
            .padding(.bottom, 80)
        }
        .overlay(alignment: .bottomTrailing) {
            AIFloatingButton {
                if LocalAuthStore.load().isLoggedIn {
                    showAIAnalysis = true
                } else {
                    showLoginForAI = true
                }
            }
            .padding(.trailing, 20)
            .padding(.bottom, 24)
        }
        .navigationTitle("卦象结果")
        .navigationBarTitleDisplayMode(.inline)
        .parchmentBackground()
        .toolbar {
            if showSimilarHexagramButton {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        appNavigation.openSimilarHexagram(for: result)
                    } label: {
                        Label("同类", systemImage: "rectangle.stack")
                    }
                    .accessibilityLabel("查看同类卦")
                }
            }
        }
        .navigationDestination(isPresented: $showAIAnalysis) {
            AIAnalysisView(result: resultForAnalysis)
        }
        .sheet(isPresented: $showLoginForAI) {
            LoginSheetView { newSession in
                LocalAuthStore.save(newSession)
                showLoginForAI = false
                showAIAnalysis = true
            }
        }
        .onAppear {
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

    private func formattedTime(_ date: Date) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "zh_CN")
        f.dateFormat = "yyyy年M月d日 HH:mm:ss"
        return "占卦时间：\(f.string(from: date))"
    }
}

/// 本卦／之卦卦象图与经文（卦辞、彖、象、爻辞），历史结果页与案例详情共用。
struct HexagramReadingBody: View {
    private enum HexTab: String, CaseIterable, Identifiable {
        case primary = "本卦"
        case resulting = "之卦"
        var id: String { rawValue }
    }

    let primaryNumber: Int
    let resultingNumber: Int?
    let lines: [LineValue]
    let movingPositions: [Int]

    @State private var selectedTab: HexTab = .primary

    private var store: HexagramStore { .shared }
    private var primary: Hexagram? { store.hexagram(number: primaryNumber) }
    private var resulting: Hexagram? {
        guard let n = resultingNumber else { return nil }
        return store.hexagram(number: n)
    }

    private var focus: ReadingFocus {
        ReadingGuide.focus(movingPositions: movingPositions)
    }

    private var availableTabs: [HexTab] {
        resulting == nil ? [.primary] : HexTab.allCases
    }

    init(primaryNumber: Int, resultingNumber: Int?, lines: [LineValue], movingPositions: [Int]) {
        self.primaryNumber = primaryNumber
        self.resultingNumber = resultingNumber
        self.lines = lines
        self.movingPositions = movingPositions
    }

    init(result: CastResult) {
        self.init(
            primaryNumber: result.primaryNumber,
            resultingNumber: result.resultingNumber,
            lines: result.lines,
            movingPositions: result.movingPositions
        )
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
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
        .onAppear {
            if resulting == nil { selectedTab = .primary }
        }
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
                HexagramFigureView(lines: lines, movingPositions: movingPositions)
            }
            .frame(maxWidth: .infinity)

            if let resultingNumber,
               let resulting {
                let changedLines = lines.map { $0.isChanging ? $0.changed : $0 }
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
        let bare = String(prefix.dropLast())
        if text.hasPrefix(bare) {
            let rest = text.dropFirst(bare.count).trimmingCharacters(in: .whitespaces)
            if rest.hasPrefix("：") || rest.hasPrefix(":") {
                return bare + rest
            }
            return prefix + rest
        }
        return prefix + text
    }

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
        let moving = movingPositions.contains(position)
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
        let xiang = hex.xiaoXiang(at: position)
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

    private func shouldShowLead(tab: HexTab, position: Int) -> Bool {
        guard movingPositions.count >= 2 else { return false }
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
}

struct AIAnalysisView: View {
    let result: CastResult

    @State private var isLoading = false
    @State private var analysis: AuthAPI.AIAnalyzeResponse.Analysis?
    @State private var errorMessage: String?

    private var store: HexagramStore { .shared }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                headerSection

                if isLoading {
                    HStack {
                        Spacer()
                        ProgressView("解读中…")
                        Spacer()
                    }
                    .padding(.vertical, 24)
                }

                if let analysis {
                    analysisSection(title: "总览", text: analysis.summary)
                    analysisSection(title: "焦点", text: analysis.focus)
                    VStack(alignment: .leading, spacing: 8) {
                        Text("建议")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(AppTheme.accent)
                        ForEach(Array(analysis.advice.enumerated()), id: \.offset) { index, item in
                            HStack(alignment: .top, spacing: 8) {
                                Text("\(index + 1).")
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundStyle(AppTheme.accent)
                                Text(item)
                                    .font(.body)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding()
                    .background(RoundedRectangle(cornerRadius: 12).fill(AppTheme.cardFill))
                }

                if let errorMessage {
                    Text(errorMessage)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                if !isLoading {
                    Button(analysis == nil ? "开始解读" : "重新解读") {
                        Task { await runAnalysis() }
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(AppTheme.accent)
                    .frame(maxWidth: .infinity)
                }
            }
            .padding()
        }
        .navigationTitle("AI 解读")
        .navigationBarTitleDisplayMode(.inline)
        .parchmentBackground()
        .task {
            if analysis == nil, !isLoading {
                await runAnalysis()
            }
        }
    }

    private var headerSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            if let primary = store.hexagram(number: result.primaryNumber) {
                Text("\(primary.symbol) \(primary.name)")
                    .font(.title3.weight(.bold))
            }
            if let question = result.question, !question.isEmpty {
                Text("所问：\(question)")
                    .font(.body)
            }
            Text(ReadingGuide.generalPrinciple)
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(RoundedRectangle(cornerRadius: 12).fill(AppTheme.cardFill))
    }

    private func analysisSection(title: String, text: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(AppTheme.accent)
            Text(text)
                .font(.body)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(RoundedRectangle(cornerRadius: 12).fill(AppTheme.cardFill))
    }

    @MainActor
    private func runAnalysis() async {
        guard let token = LocalAuthStore.load().accessToken else {
            errorMessage = "请先登录"
            return
        }
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }
        do {
            let response = try await AuthAPI.analyzeReading(result: result, accessToken: token)
            analysis = response.analysis
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
