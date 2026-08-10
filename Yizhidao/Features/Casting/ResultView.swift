import SwiftUI
import SwiftData

struct ResultView: View {
    private enum HexTab: String, CaseIterable, Identifiable {
        case primary = "本卦"
        case resulting = "之卦"
        var id: String { rawValue }
    }

    let result: CastResult
    var isNew: Bool = true

    @Environment(\.modelContext) private var modelContext
    @State private var didSave = false
    @State private var selectedTab: HexTab = .primary

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

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                metaSection
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
        .onAppear {
            if resulting == nil { selectedTab = .primary }
            guard isNew, !didSave else { return }
            modelContext.insert(ReadingRecord(from: result))
            try? modelContext.save()
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
            if let question = result.question, !question.isEmpty {
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
        .background(RoundedRectangle(cornerRadius: 12).fill(Color(.secondarySystemBackground)))
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
                section(title: "卦辞 · \(hex.name)", showLead: shouldShowGuaciLead(tab: tab)) {
                    Text(hex.guaci)
                        .font(.body)
                        .lineSpacing(4)
                }
                section(title: "大象 · \(hex.name)") {
                    Text(hex.daxiang)
                        .font(.body)
                        .lineSpacing(4)
                }
                section(title: "六爻") {
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

    /// 三爻变：本卦卦辞主看；六爻变：之卦卦辞主看。
    private func shouldShowGuaciLead(tab: HexTab) -> Bool {
        switch focus.kind {
        case .bothGuaci:
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
        var yao = hex.yaoCi(at: position)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        while yao.hasSuffix("。") || yao.hasSuffix("；") {
            yao = String(yao.dropLast())
        }
        return yao
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
        title: String,
        showLead: Bool = false,
        @ViewBuilder content: () -> some View
    ) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Text(title)
                    .font(.headline)
                if showLead {
                    leadBadge
                }
            }
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(RoundedRectangle(cornerRadius: 12).fill(Color(.secondarySystemBackground)))
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
