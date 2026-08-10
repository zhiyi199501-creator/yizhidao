import SwiftUI
import SwiftData

struct ResultView: View {
    let result: CastResult
    var isNew: Bool = true

    @Environment(\.modelContext) private var modelContext
    @State private var didSave = false

    private var store: HexagramStore { .shared }
    private var primary: Hexagram? { store.hexagram(number: result.primaryNumber) }
    private var resulting: Hexagram? {
        guard let n = result.resultingNumber else { return nil }
        return store.hexagram(number: n)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                metaSection
                figuresSection
                textSection
            }
            .padding()
        }
        .navigationTitle("卦象结果")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
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
            if result.movingPositions.isEmpty {
                Text("六爻皆静，主看本卦卦辞。")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            } else {
                Text("动爻：\(result.movingPositions.map(yaoName).joined(separator: "、"))")
                    .font(.caption)
                    .foregroundStyle(.orange)
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

    private var textSection: some View {
        VStack(alignment: .leading, spacing: 16) {
            if let primary {
                section(title: "卦辞 · \(primary.name)") {
                    Text(primary.guaci)
                        .font(.body)
                        .lineSpacing(4)
                }

                section(title: "大象 · \(primary.name)") {
                    Text(primary.daxiang)
                        .font(.body)
                        .lineSpacing(4)
                }
            }

            if !result.movingPositions.isEmpty, let primary {
                section(title: "动爻爻辞与小象") {
                    VStack(alignment: .leading, spacing: 16) {
                        ForEach(result.movingPositions, id: \.self) { pos in
                            VStack(alignment: .leading, spacing: 8) {
                                Text(result.movingPositions.count == 1 ? "主看 · \(yaoName(pos))" : yaoName(pos))
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundStyle(.orange)
                                labeledBlock(title: "爻辞", text: primary.yaoCi(at: pos))
                                labeledBlock(title: "小象", text: primary.xiaoXiang(at: pos))
                            }
                        }
                        if result.movingPositions.count >= 2 {
                            Text("说明：多爻发动时列出全部动爻爻辞与小象；完整朱熹多变爻读法可后续增强。")
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            }

            if let resulting {
                section(title: "之卦卦辞 · \(resulting.name)") {
                    Text(resulting.guaci)
                        .font(.body)
                        .lineSpacing(4)
                }
                section(title: "之卦大象 · \(resulting.name)") {
                    Text(resulting.daxiang)
                        .font(.body)
                        .lineSpacing(4)
                }
            }

            Text("经文版本：《易经证释》所引")
                .font(.caption2)
                .foregroundStyle(.tertiary)
                .frame(maxWidth: .infinity, alignment: .trailing)
        }
    }

    private func labeledBlock(title: String, text: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
            Text(text)
                .font(.body)
                .lineSpacing(4)
        }
    }

    private func section(title: String, @ViewBuilder content: () -> some View) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.headline)
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(RoundedRectangle(cornerRadius: 12).fill(Color(.secondarySystemBackground)))
    }

    private func changedLines(from lines: [LineValue]) -> [LineValue] {
        lines.map { $0.isChanging ? $0.changed : $0 }
    }

    private func yaoName(_ position: Int) -> String {
        switch position {
        case 1: return "初爻"
        case 2: return "二爻"
        case 3: return "三爻"
        case 4: return "四爻"
        case 5: return "五爻"
        case 6: return "上爻"
        default: return "爻"
        }
    }

    private func formattedTime(_ date: Date) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "zh_CN")
        f.dateFormat = "yyyy年M月d日 HH:mm:ss"
        return "占卦时间：\(f.string(from: date))"
    }
}
