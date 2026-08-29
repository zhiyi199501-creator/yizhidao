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
                persistNewRecordIfNeeded()
                let existing = SavedAIAnalysisStore.find(
                    recordID: editableRecord?.id,
                    result: resultForAnalysis
                )
                if existing != nil || LocalAuthStore.load().isLoggedIn {
                    showAIAnalysis = true
                } else {
                    showLoginForAI = true
                }
            }
            .padding(.trailing, 20)
            .padding(.bottom, 24)
        }
        .navigationTitle("卦象结果".zh)
        .navigationBarTitleDisplayMode(.inline)
        .parchmentBackground()
        .toolbar {
            if showSimilarHexagramButton {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        appNavigation.openSimilarHexagram(for: result)
                    } label: {
                        Label("同类".zh, systemImage: "rectangle.stack")
                    }
                    .accessibilityLabel("查看同类卦".zh)
                }
            }
        }
        .navigationDestination(isPresented: $showAIAnalysis) {
            AIAnalysisView(result: resultForAnalysis, readingRecordID: editableRecord?.id)
        }
        .sheet(isPresented: $showLoginForAI) {
            LoginSheetView { newSession in
                LocalAuthStore.save(newSession)
                showLoginForAI = false
                showAIAnalysis = true
            }
        }
        .onAppear {
            persistNewRecordIfNeeded()
        }
    }

    private func persistNewRecordIfNeeded() {
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

    private var metaSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label(result.method.displayName.zh, systemImage: "seal")
                .font(.subheadline.weight(.semibold))
            Text(formattedTime(result.createdAt).zh)
                .font(.footnote)
                .foregroundStyle(.secondary)
            if editableRecord != nil {
                    TextField("所问何事", text: $questionText, axis: .vertical)
                        .lineLimit(2...5)
                        .appTextFieldStyle()
                        .onChange(of: questionText) { _, newValue in
                            persistQuestion(newValue)
                        }
            } else if let question = result.question, !question.isEmpty {
                Text("所问：\(question)".zh)
                    .font(.body)
            }
            if let numbers = result.numbers {
                Text("取数：\(numbers.map(String.init).joined(separator: " · "))".zh)
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
            Picker("状态".zh, selection: $verificationStatus) {
                ForEach(VerificationStatus.allCases) { status in
                    Text(status.displayName.zh).tag(status)
                }
            }
            .pickerStyle(.segmented)
            .onChange(of: verificationStatus) { _, newValue in
                persistVerification(status: newValue, note: verificationNote)
            }
            TextField("验证结果", text: $verificationNote, axis: .vertical)
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
        f.locale = AppLanguage.current.locale
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
    @State private var imaSelection: ImaExplanationSelection?

    private var store: HexagramStore { .shared }
    private var imaStore: ImaExplanationStore { .shared }
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
                Picker("卦".zh, selection: $selectedTab) {
                    ForEach(availableTabs) { tab in
                        Text(tab.rawValue.zh).tag(tab)
                    }
                }
                .pickerStyle(.segmented)
            }
            hexagramTextSection(for: selectedTab)
            Text("经文版本：《易经证释》所引".zh)
                .font(.caption2)
                .foregroundStyle(.tertiary)
                .frame(maxWidth: .infinity, alignment: .trailing)
        }
        .onAppear {
            if resulting == nil { selectedTab = .primary }
        }
        .sheet(item: $imaSelection) { selection in
            ImaExplanationSheet(entry: selection.entry, source: imaStore.source)
        }
    }

    private var figuresSection: some View {
        HStack(alignment: .top, spacing: 24) {
            VStack {
                if let primary {
                    Text("\(primary.symbol) \(primary.name)".zh)
                        .font(.title3.weight(.bold))
                    Text("第\(primary.number)卦 · 本卦".zh)
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
                    Text("\(resulting.symbol) \(resulting.name)".zh)
                        .font(.title3.weight(.bold))
                    Text("第\(resultingNumber)卦 · 之卦".zh)
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
                scriptureSection(
                    explanationId: ImaExplanationId.guaci(number: hex.number),
                    showLead: shouldShowGuaciLead(tab: tab)
                ) {
                    Text(hex.guaci.zh)
                        .font(.body)
                        .lineSpacing(4)
                }
                scriptureSection(explanationId: ImaExplanationId.tuanci(number: hex.number)) {
                    Text(prefixed("彖曰：", hex.tuanci).zh)
                        .font(.body)
                        .lineSpacing(4)
                }
                scriptureSection(explanationId: ImaExplanationId.daxiang(number: hex.number)) {
                    Text(prefixed("象曰：", hex.daxiang).zh)
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

        let explanationId = ImaExplanationId.yaoPair(number: hex.number, position: position)

        return TappableScripture(explanationId: explanationId, selection: $imaSelection) {
            VStack(alignment: .leading, spacing: 6) {
                if showLead {
                    leadBadge
                }
                Text(trimmedYaoCi(hex: hex, position: position).zh)
                    .font(.body)
                    .foregroundStyle(accent)
                    .lineSpacing(4)
                Text(xiangLine(hex: hex, position: position).zh)
                    .font(.body)
                    .foregroundStyle(accent)
                    .lineSpacing(4)
            }
        }
    }

    private var leadBadge: some View {
        Text("主看".zh)
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

    private func scriptureSection(
        explanationId: String,
        showLead: Bool = false,
        @ViewBuilder content: @escaping () -> some View
    ) -> some View {
        section(showLead: showLead) {
            TappableScripture(explanationId: explanationId, selection: $imaSelection, content: content)
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
    private let readingRecordID: UUID?

    @State private var isLoading: Bool
    @State private var isFollowupLoading = false
    @State private var analysis: AuthAPI.AIAnalyzeResponse.Analysis?
    @State private var followUps: [SavedAIFollowUp]
    @State private var draft = ""
    @State private var errorMessage: String?
    @State private var savedID: UUID?

    private var store: HexagramStore { .shared }
    private var canSendFollowup: Bool {
        !draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && analysis != nil
            && !isLoading
            && !isFollowupLoading
    }

    init(result: CastResult, readingRecordID: UUID? = nil) {
        self.result = result
        self.readingRecordID = readingRecordID
        if let saved = SavedAIAnalysisStore.find(recordID: readingRecordID, result: result) {
            _analysis = State(initialValue: AuthAPI.AIAnalyzeResponse.Analysis(saved: saved.analysis))
            _followUps = State(initialValue: saved.followUps)
            _savedID = State(initialValue: saved.id)
            _isLoading = State(initialValue: false)
        } else {
            _analysis = State(initialValue: nil)
            _followUps = State(initialValue: [])
            _savedID = State(initialValue: nil)
            _isLoading = State(initialValue: true)
        }
    }

    init(saved: SavedAIAnalysis) {
        self.result = saved.toCastResult()
        self.readingRecordID = saved.readingRecordID
        _analysis = State(initialValue: AuthAPI.AIAnalyzeResponse.Analysis(saved: saved.analysis))
        _followUps = State(initialValue: saved.followUps)
        _savedID = State(initialValue: saved.id)
        _isLoading = State(initialValue: false)
    }

    var body: some View {
        VStack(spacing: 0) {
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
                        analysisSection(title: "事情背景", text: analysis.summary)
                        analysisSection(title: "当下", text: analysis.focus)
                        if !analysis.direction.isEmpty {
                            analysisSection(title: "方向", text: analysis.direction)
                        }
                        let adviceItems = aiAdviceDisplayItems(advice: analysis.advice, risks: analysis.risks)
                        if !adviceItems.isEmpty {
                            bulletSection(title: "建议", items: adviceItems)
                        }
                        if !analysis.askNext.isEmpty, followUps.isEmpty, !isLoading, !isFollowupLoading {
                            askNextSection(analysis.askNext)
                        }
                    }

                    ForEach(Array(followUps.enumerated()), id: \.element.id) { index, turn in
                        followUpTurn(turn, isLatest: index == followUps.count - 1)
                    }

                    if isFollowupLoading {
                        HStack {
                            Spacer()
                            ProgressView("回复中…")
                            Spacer()
                        }
                        .padding(.vertical, 8)
                    }

                    if let errorMessage {
                        Text(errorMessage.zh)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
                .padding()
            }
            .scrollDismissesKeyboard(.interactively)

            if analysis != nil {
                composerBar
            }
        }
        .navigationTitle("问答".zh)
        .navigationBarTitleDisplayMode(.inline)
        .parchmentBackground()
        .task {
            if analysis == nil {
                await runAnalysis()
            }
        }
    }

    private var composerBar: some View {
        HStack(alignment: .bottom, spacing: 8) {
            TextField("追问或补充背景", text: $draft, axis: .vertical)
                .lineLimit(1...4)
                .appTextFieldStyle()
            Button {
                Task { await sendFollowup() }
            } label: {
                Image(systemName: "arrow.up.circle.fill")
                    .font(.system(size: 32))
                    .foregroundStyle(canSendFollowup ? AppTheme.accent : Color.secondary.opacity(0.4))
            }
            .disabled(!canSendFollowup)
            .accessibilityLabel("发送".zh)
        }
        .padding(.horizontal)
        .padding(.vertical, 10)
        .background(AppTheme.cardFill)
    }

    private func followUpTurn(_ turn: SavedAIFollowUp, isLatest: Bool) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Spacer(minLength: 40)
                Text(turn.user.zh)
                    .font(.body)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(
                        RoundedRectangle(cornerRadius: 12)
                            .fill(AppTheme.accent.opacity(0.12))
                    )
            }
            analysisSection(title: "回复", text: turn.assistant)
            if !turn.advice.isEmpty {
                bulletSection(title: "建议", items: turn.advice)
            }
            if isLatest, !isFollowupLoading {
                let nextQuestions = turn.askNext.isEmpty ? (analysis?.askNext ?? []) : turn.askNext
                if !nextQuestions.isEmpty {
                    askNextSection(nextQuestions)
                }
            }
        }
    }

    private var headerSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            if let primary = store.hexagram(number: result.primaryNumber) {
                Text("\(primary.symbol) \(primary.name)".zh)
                    .font(.title3.weight(.bold))
            }
            if let question = result.question, !question.isEmpty {
                Text("所问：\(question)".zh)
                    .font(.body)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(RoundedRectangle(cornerRadius: 12).fill(AppTheme.cardFill))
    }

    private func analysisSection(title: String, text: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title.zh)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(AppTheme.accent)
            Text(text.zh)
                .font(.body)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(RoundedRectangle(cornerRadius: 12).fill(AppTheme.cardFill))
    }

    private func bulletSection(title: String, items: [String]) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title.zh)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(AppTheme.accent)
            ForEach(Array(items.enumerated()), id: \.offset) { index, item in
                HStack(alignment: .top, spacing: 8) {
                    Text("\(index + 1).".zh)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(AppTheme.accent)
                    Text(item.zh)
                        .font(.body)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(RoundedRectangle(cornerRadius: 12).fill(AppTheme.cardFill))
    }

    private func askNextSection(_ questions: [String]) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("可以接着问".zh)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(AppTheme.accent)
            Text("点一句直接发出。".zh)
                .font(.caption)
                .foregroundStyle(.secondary)
            ForEach(Array(questions.enumerated()), id: \.offset) { _, question in
                Button {
                    Task { await sendFollowup(prefilled: question) }
                } label: {
                    Text(question.zh)
                        .font(.body)
                        .multilineTextAlignment(.leading)
                        .fixedSize(horizontal: false, vertical: true)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 10)
                        .background(
                            RoundedRectangle(cornerRadius: 10)
                                .stroke(AppTheme.accent.opacity(0.35), lineWidth: 1)
                        )
                        .contentShape(RoundedRectangle(cornerRadius: 10))
                }
                .buttonStyle(.plain)
                .disabled(isFollowupLoading || isLoading)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(RoundedRectangle(cornerRadius: 12).fill(AppTheme.cardFill))
    }

    private func persistCurrent(
        analysis: AuthAPI.AIAnalyzeResponse.Analysis,
        followUps: [SavedAIFollowUp]
    ) {
        let content = analysis.savedContent()
        let existing = SavedAIAnalysisStore.find(recordID: readingRecordID, result: result)
            ?? savedID.flatMap { id in SavedAIAnalysisStore.load().first { $0.id == id } }
        let item = SavedAIAnalysis.make(
            result: result,
            analysis: content,
            followUps: followUps,
            readingRecordID: readingRecordID ?? existing?.readingRecordID,
            existingID: existing?.id ?? savedID
        )
        SavedAIAnalysisStore.upsert(item)
        savedID = item.id
    }

    @MainActor
    private func runAnalysis() async {
        guard let token = LocalAuthStore.load().accessToken else {
            errorMessage = "请先登录"
            isLoading = false
            return
        }
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }
        do {
            let response = try await AuthAPI.analyzeReading(result: result, accessToken: token)
            analysis = response.analysis
            followUps = []
            persistCurrent(analysis: response.analysis, followUps: [])
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    @MainActor
    private func sendFollowup(prefilled: String? = nil) async {
        let message = (prefilled ?? draft).trimmingCharacters(in: .whitespacesAndNewlines)
        guard !message.isEmpty, analysis != nil, !isLoading, !isFollowupLoading else {
            if LocalAuthStore.load().accessToken == nil {
                errorMessage = "请先登录"
            }
            return
        }
        guard let analysis, let token = LocalAuthStore.load().accessToken else {
            errorMessage = "请先登录"
            return
        }
        if prefilled == nil {
            draft = ""
        }
        isFollowupLoading = true
        errorMessage = nil
        defer { isFollowupLoading = false }
        do {
            let response = try await AuthAPI.followupReading(
                result: result,
                analysis: analysis,
                conversation: followUps,
                message: message,
                accessToken: token
            )
            let nextFollowUps = followUps + [
                SavedAIFollowUp(
                    user: message,
                    assistant: response.reply,
                    advice: response.advice,
                    askNext: response.askNext
                )
            ]
            followUps = nextFollowUps
            persistCurrent(analysis: analysis, followUps: nextFollowUps)
        } catch {
            draft = message
            errorMessage = error.localizedDescription
        }
    }
}
