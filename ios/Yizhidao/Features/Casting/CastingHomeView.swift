import SwiftUI

/// 只在冷启动播一次条幅浮现；切 Tab 再回来不再播。
private enum CastingHomeReveal {
    static var didPlay = false
}

struct CastingHomeView: View {
    @State private var latestResult: CastResult?
    @State private var showResult = false
    @State private var request: CastingRequest?
    @State private var revealedCount = 0
    @State private var showSeal = false
    @State private var spin = 0.0
    @Environment(AppNavigation.self) private var appNavigation
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// 左起是落款，右起是起句。竖排从右往左读。
    private static let columns = [
        "是以自天祐之吉无不利",
        "动则观其变而玩其占",
        "君子居则观其象而玩其辞",
    ]

    private static let spokenQuote =
        "君子居则观其象而玩其辞，动则观其变而玩其占，是以自天祐之，吉无不利。"

    var body: some View {
        NavigationStack {
            GeometryReader { geo in
                let charSize = min(22, max(18, geo.size.width / 16))
                let taiji = min(geo.size.width * 0.72, 280)
                VStack(spacing: 0) {
                    Spacer(minLength: 12)
                    ZStack {
                        CastingTaijiMark()
                            .frame(width: taiji, height: taiji)
                            .rotationEffect(.degrees(spin))
                            .allowsHitTesting(false)
                        scrollColumns(charSize: charSize)
                    }
                    Spacer(minLength: 28)
                    StartCastButton(visible: showSeal) {
                        showResult = false
                        request = CastingRequest()
                    }
                    RitualEnglishCaption(text: "Cast")
                        .padding(.top, 10)
                    Spacer(minLength: 20)
                }
                .padding(.bottom, 8)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            .parchmentBackground(hidesTabBar: false)
            .task { await reveal() }
            .navigationDestination(isPresented: $showResult) {
                if let latestResult {
                    ResultView(result: latestResult, isNew: true)
                }
            }
            .fullScreenCover(item: $request) { _ in
                CastingActView { result in
                    latestResult = result
                    showResult = true
                    // 结果页先落在盖层底下，再无动画收盖，避免先闪回首页。
                    DispatchQueue.main.async {
                        var transaction = Transaction()
                        transaction.disablesAnimations = true
                        withTransaction(transaction) {
                            self.request = nil
                        }
                    }
                } onCancel: {
                    self.request = nil
                }
            }
            .onChange(of: appNavigation.dismissCastResultTick) { _, _ in
                var transaction = Transaction()
                transaction.disablesAnimations = true
                withTransaction(transaction) {
                    request = nil
                    showResult = false
                }
            }
        }
    }

    private func scrollColumns(charSize: CGFloat) -> some View {
        HStack(alignment: .top, spacing: charSize * 1.15) {
            ForEach(Array(Self.columns.enumerated()), id: \.offset) { column, raw in
                let text = raw.zh
                let order = 2 - column
                VStack(spacing: charSize * 0.28) {
                    ForEach(Array(text.enumerated()), id: \.offset) { _, scalar in
                        Text(String(scalar))
                            .font(.system(size: charSize, weight: .regular, design: .serif))
                            .foregroundStyle(AppTheme.accent.opacity(column == 0 ? 0.95 : 0.82))
                    }
                }
                .opacity(revealedCount > order ? 1 : 0)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Self.spokenQuote.zh)
    }

    @MainActor
    private func reveal() async {
        if reduceMotion || CastingHomeReveal.didPlay {
            showSettled()
            return
        }
        CastingHomeReveal.didPlay = true
        revealedCount = 0
        showSeal = false
        try? await Task.sleep(nanoseconds: 180_000_000)
        for step in 1...3 {
            withAnimation(.easeIn(duration: 0.7)) {
                revealedCount = step
            }
            try? await Task.sleep(nanoseconds: 750_000_000)
        }
        withAnimation(.easeIn(duration: 0.55)) {
            showSeal = true
        }
        startSpin()
    }

    private func showSettled() {
        revealedCount = 3
        showSeal = true
        startSpin()
    }

    private func startSpin() {
        guard !reduceMotion, spin == 0 else { return }
        withAnimation(.linear(duration: 96).repeatForever(autoreverses: false)) {
            spin = 360
        }
    }

}

/// 极淡的太极底，像宣纸上隐出来的水印。
private struct CastingTaijiMark: View {
    var body: some View {
        Canvas { context, size in
            let s = min(size.width, size.height)
            let r = s / 2
            let c = CGPoint(x: size.width / 2, y: size.height / 2)
            let ink = AppTheme.accent.opacity(0.13)
            var circle = Path()
            circle.addEllipse(in: CGRect(x: c.x - r, y: c.y - r, width: s, height: s))
            context.stroke(circle, with: .color(ink), lineWidth: 1.1)

            var curve = Path()
            curve.addArc(
                center: CGPoint(x: c.x, y: c.y - r / 2),
                radius: r / 2,
                startAngle: .degrees(-90),
                endAngle: .degrees(90),
                clockwise: false
            )
            curve.addArc(
                center: CGPoint(x: c.x, y: c.y + r / 2),
                radius: r / 2,
                startAngle: .degrees(-90),
                endAngle: .degrees(90),
                clockwise: true
            )
            context.stroke(curve, with: .color(ink), lineWidth: 1.1)

            let dot = r * 0.085
            var yang = Path()
            yang.addEllipse(in: CGRect(x: c.x - dot, y: c.y - r / 2 - dot, width: dot * 2, height: dot * 2))
            var yin = Path()
            yin.addEllipse(in: CGRect(x: c.x - dot, y: c.y + r / 2 - dot, width: dot * 2, height: dot * 2))
            context.fill(yang, with: .color(ink))
            context.stroke(yin, with: .color(ink), lineWidth: 1.1)
        }
        .accessibilityHidden(true)
    }
}

/// 朱印：双圈、起卦竖排，像钤在纸上，不是系统按钮。
struct StartCastButton: View {
    var visible: Bool = true
    let action: () -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var breathes = false

    var body: some View {
        Button(action: action) {
            ZStack {
                Circle()
                    .stroke(AppTheme.accent.opacity(0.9), lineWidth: 1.8)
                    .padding(4)
                Circle()
                    .stroke(AppTheme.accent.opacity(0.9), lineWidth: 1.6)
                    .padding(9)
                VStack(spacing: 0) {
                    Text("起".zh)
                    Text("卦".zh)
                }
                .font(.system(size: 18, weight: .semibold, design: .serif))
                .foregroundStyle(AppTheme.accent)
            }
            .frame(width: 72, height: 72)
            .contentShape(Circle())
        }
        .buttonStyle(SealPressStyle())
        .background { inkHalo }
        .opacity(visible || reduceMotion ? 1 : 0)
        .onAppear {
            guard !reduceMotion else { return }
            withAnimation(.easeInOut(duration: 3.2).repeatForever(autoreverses: true)) {
                breathes = true
            }
        }
        .accessibilityLabel("起卦".ui("Cast"))
    }

    private var inkHalo: some View {
        Circle()
            .fill(
                RadialGradient(
                    colors: [
                        AppTheme.accent.opacity(0.18),
                        AppTheme.accent.opacity(0.05),
                        AppTheme.accent.opacity(0),
                    ],
                    center: .center,
                    startRadius: 10,
                    endRadius: 58
                )
            )
            .frame(width: 148, height: 148)
            .scaleEffect(breathes && !reduceMotion ? 1.1 : 0.92)
            .allowsHitTesting(false)
    }
}

private struct SealPressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.94 : 1)
            .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
    }
}
