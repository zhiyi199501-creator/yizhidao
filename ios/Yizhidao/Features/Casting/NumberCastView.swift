import SwiftUI

/// 三数入口。取数本身在全屏的 `NumberDrawActView` 里进行，这里只留一句话和一个入口。
struct NumberCastView: View {
    var onBegin: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Spacer(minLength: 24)
            emblem
            Text("三个数定上卦、下卦、动爻".zh)
                .font(.footnote)
                .foregroundStyle(.secondary)
                .padding(.top, 30)
            Spacer(minLength: 24)
            StartCastButton(title: "开始取数", action: onBegin)
        }
    }

    /// 三个待填的空位，和取数幕里的槽位同一个样子。
    private var emblem: some View {
        VStack(spacing: 20) {
            ForEach(0..<3, id: \.self) { _ in
                Capsule()
                    .strokeBorder(
                        Color.primary.opacity(0.3),
                        style: StrokeStyle(lineWidth: 1.5, dash: [6, 6])
                    )
                    .frame(width: 150, height: 16)
            }
        }
        .accessibilityHidden(true)
    }
}
