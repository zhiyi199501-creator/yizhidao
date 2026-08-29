import SwiftUI

/// 金钱卦入口。摇卦本身在全屏的 `CoinTossActView` 里进行，这里只留一句话和一个入口。
struct CoinCastView: View {
    var onBegin: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Spacer(minLength: 24)
            emblem
            Text("三枚铜钱摇六次，自下而上成卦".zh)
                .font(.footnote)
                .foregroundStyle(.secondary)
                .padding(.top, 30)
            Spacer(minLength: 24)
            StartCastButton(title: "开始摇卦", action: onBegin)
        }
    }

    private var emblem: some View {
        HStack(spacing: 18) {
            CoinView(isYangFace: true, jitters: false, wobbleSeed: 0, size: 62)
            CoinView(isYangFace: false, jitters: false, wobbleSeed: 1, size: 62)
            CoinView(isYangFace: true, jitters: false, wobbleSeed: 2, size: 62)
        }
        .accessibilityHidden(true)
    }
}
