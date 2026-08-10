import SwiftUI

struct HexagramFigureView: View {
    let lines: [LineValue]
    let movingPositions: [Int]
    var title: String = ""

    var body: some View {
        VStack(spacing: 8) {
            if !title.isEmpty {
                Text(title)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.secondary)
            }
            VStack(spacing: 6) {
                ForEach((0..<6).reversed(), id: \.self) { index in
                    let position = index + 1
                    let line = lines[index]
                    HStack(spacing: 8) {
                        Text(yaoLabel(position: position, line: line))
                            .font(.caption2.monospaced())
                            .foregroundStyle(.secondary)
                            .frame(width: 36, alignment: .trailing)
                        YaoBarView(line: line, highlighted: movingPositions.contains(position))
                    }
                }
            }
            .padding(.vertical, 4)
        }
    }

    private func yaoLabel(position: Int, line: LineValue) -> String {
        let names = ["初", "二", "三", "四", "五", "上"]
        let stem = line.isYang ? "九" : "六"
        if position == 1 { return "初\(stem)" }
        if position == 6 { return "上\(stem)" }
        // 九二 / 六二 … 九五 / 六五
        return "\(stem)\(names[position - 1])"
    }
}

struct YaoBarView: View {
    let line: LineValue
    var highlighted: Bool = false

    var body: some View {
        HStack(spacing: 6) {
            if line.isYang {
                Capsule()
                    .fill(barColor)
                    .frame(height: 10)
            } else {
                Capsule()
                    .fill(barColor)
                    .frame(width: 44, height: 10)
                if line.isChanging {
                    Text("×")
                        .font(.caption.weight(.bold))
                        .foregroundStyle(barColor)
                } else {
                    Color.clear.frame(width: 10, height: 10)
                }
                Capsule()
                    .fill(barColor)
                    .frame(width: 44, height: 10)
            }
            if line.isYang && line.isChanging {
                Text("○")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(barColor)
            }
        }
        .frame(maxWidth: 160)
        .padding(4)
        .background(
            RoundedRectangle(cornerRadius: 6)
                .fill(highlighted ? Color.orange.opacity(0.15) : Color.clear)
        )
    }

    private var barColor: Color {
        line.isYang ? Color(red: 0.75, green: 0.2, blue: 0.2) : Color.primary.opacity(0.85)
    }
}
