import SwiftUI

struct CoinCastView: View {
    @Binding var question: String
    var onResult: (CastResult) -> Void

    @State private var lines: [LineValue?] = Array(repeating: nil, count: 6)
    @State private var errorMessage: String?

    private var filledCount: Int { lines.compactMap { $0 }.count }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("三钱摇六次，自下而上成卦。字面为阳，背面为阴；也可点「选」手选四象。")
                .font(.caption)
                .foregroundStyle(.secondary)

            VStack(spacing: 8) {
                // 视觉上自上而下：上爻 → 初爻（数组仍是初爻在 index 0）
                ForEach((0..<6).reversed(), id: \.self) { index in
                    HStack {
                        Text(yaoTitle(index))
                            .frame(width: 40, alignment: .leading)
                            .font(.subheadline)
                        if let line = lines[index] {
                            YaoBarView(line: line, highlighted: line.isChanging)
                            Text(lineLabel(line))
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        } else {
                            Text("未摇")
                                .font(.caption)
                                .foregroundStyle(.tertiary)
                        }
                        Spacer()
                        Menu {
                            ForEach(Self.manualOptions, id: \.line) { option in
                                Button(option.title) {
                                    lines[index] = option.line
                                }
                            }
                        } label: {
                            Text("选")
                                .frame(minWidth: 28)
                        }
                        .buttonStyle(.bordered)
                        Button("摇") {
                            var rng = SystemRandomNumberGenerator()
                            lines[index] = CoinCastingEngine.tossLine(using: &rng)
                        }
                        .buttonStyle(.bordered)
                    }
                }
            }

            HStack {
                Button("一键摇满") {
                    var rng = SystemRandomNumberGenerator()
                    lines = (0..<6).map { _ in CoinCastingEngine.tossLine(using: &rng) }
                }
                .buttonStyle(.bordered)

                Button("清空") {
                    lines = Array(repeating: nil, count: 6)
                }
                .buttonStyle(.bordered)
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
            .tint(AppTheme.accent)
            .disabled(filledCount < 6)
        }
    }

    private static let manualOptions: [(title: String, line: LineValue)] = [
        ("少阳 7", .youngYang),
        ("少阴 8", .youngYin),
        ("阳动 9", .oldYang),
        ("阴动 6", .oldYin),
    ]

    private func yaoTitle(_ index: Int) -> String {
        switch index {
        case 0: return "初爻"
        case 1: return "二爻"
        case 2: return "三爻"
        case 3: return "四爻"
        case 4: return "五爻"
        case 5: return "上爻"
        default: return "爻"
        }
    }

    private func lineLabel(_ line: LineValue) -> String {
        switch line {
        case .oldYang: return "阳动 9"
        case .oldYin: return "阴动 6"
        case .youngYang: return "少阳 7"
        case .youngYin: return "少阴 8"
        }
    }

    private func cast() {
        errorMessage = nil
        let resolved = lines.compactMap { $0 }
        guard resolved.count == 6 else {
            errorMessage = "请摇满六爻"
            return
        }
        let q = question.trimmingCharacters(in: .whitespacesAndNewlines)
        let result = CoinCastingEngine.cast(
            lines: resolved,
            question: q.isEmpty ? nil : q,
            at: .now
        )
        onResult(result)
    }
}
