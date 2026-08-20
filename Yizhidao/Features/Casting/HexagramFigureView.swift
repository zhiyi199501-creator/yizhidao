import SwiftUI

struct HexagramFigureView: View {
    let lines: [LineValue]
    let movingPositions: [Int]
    var title: String = ""

    var body: some View {
        VStack(spacing: 8) {
            if !title.isEmpty {
                Text(title.zh)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.secondary)
            }
            VStack(spacing: 6) {
                ForEach((0..<6).reversed(), id: \.self) { index in
                    let position = index + 1
                    let line = lines[index]
                    HStack(spacing: 8) {
                        Text(yaoLabel(position: position, line: line).zh)
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

/// 详情页用：在完整卦象上等比例缩小，避免改内部尺寸参数。
struct ScaledHexagramFigureView: View {
    let lines: [LineValue]
    let movingPositions: [Int]
    var scale: CGFloat = 0.5

    // 与 HexagramFigureView 默认布局一致（180×146 pt）
    private var scaledWidth: CGFloat { 180 * scale }
    private var scaledHeight: CGFloat { 146 * scale }

    var body: some View {
        HexagramFigureView(lines: lines, movingPositions: movingPositions)
            .fixedSize()
            .scaleEffect(scale, anchor: .topLeading)
            .frame(width: scaledWidth, height: scaledHeight, alignment: .topLeading)
    }
}

struct YaoBarView: View {
    let line: LineValue
    var highlighted: Bool = false

    private let barWidth: CGFloat = 110
    private let gapWidth: CGFloat = 10
    private let markerWidth: CGFloat = 12

    var body: some View {
        HStack(spacing: 6) {
            barContent
                .frame(width: barWidth, height: 10)
            if line.isChanging {
                Text(line.isYang ? "○" : "×".zh)
                    .font(.caption.weight(.bold))
                    .foregroundStyle(barColor)
                    .frame(width: markerWidth, alignment: .center)
            } else {
                Color.clear.frame(width: markerWidth, height: 10)
            }
        }
        .padding(4)
        .background(
            RoundedRectangle(cornerRadius: 6)
                .fill(highlighted ? Color.orange.opacity(0.15) : Color.clear)
        )
    }

    @ViewBuilder
    private var barContent: some View {
        if line.isYang {
            Capsule()
                .fill(barColor)
        } else {
            HStack(spacing: 0) {
                Capsule()
                    .fill(barColor)
                    .frame(width: (barWidth - gapWidth) / 2)
                Color.clear
                    .frame(width: gapWidth)
                Capsule()
                    .fill(barColor)
                    .frame(width: (barWidth - gapWidth) / 2)
            }
        }
    }

    private var barColor: Color {
        line.isYang ? Color(red: 0.75, green: 0.2, blue: 0.2) : Color.primary.opacity(0.85)
    }
}
