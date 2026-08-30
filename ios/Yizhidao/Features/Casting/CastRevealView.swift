import SwiftUI

/// 揭卦：六爻自初至上逐根显形，动爻点朱砂，再压印卦名。
/// 压印后停住，完成礼仪第四步，点「弟子退」才进结果页。
struct CastRevealView: View {
    let result: CastResult
    var onFinish: () -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var revealedCount = 0
    @State private var showsSeal = false
    @State private var pulsesCinnabar = false
    @State private var sequence: Task<Void, Never>?
    @State private var didFinish = false
    @State private var showsThanks = false

    private static let cinnabar = Color(red: 0.78, green: 0.19, blue: 0.16)

    /// 揭卦的节奏。整幕约 4.4 秒，调快慢只动这里。
    private enum Beat {
        /// 盖层落定后先静一拍，别一上来就开始画。
        static let opening: Double = 0.5
        static let yaoAppear: Double = 0.32
        static let betweenYao: Double = 0.32
        /// 上爻是成卦的一下，落定前多停一拍。
        static let beforeFinalYao: Double = 0.22
        /// 六爻已满、卦名未出的悬停。
        static let beforeSeal: Double = 0.6
        /// 卦名压印后再停一拍，才出示致谢。
        static let beforeThanks: Double = 0.45

        static func nanoseconds(_ seconds: Double) -> UInt64 {
            UInt64(seconds * 1_000_000_000)
        }
    }

    private var primary: Hexagram? {
        HexagramStore.shared.hexagram(number: result.primaryNumber)
    }

    private var resulting: Hexagram? {
        guard let number = result.resultingNumber, number != result.primaryNumber else { return nil }
        return HexagramStore.shared.hexagram(number: number)
    }

    var body: some View {
        ZStack {
            AppTheme.parchmentGradient.ignoresSafeArea()

            VStack(spacing: 0) {
                questionLine
                Spacer(minLength: 24)
                VStack(spacing: 36) {
                    figure
                    seal
                    thanks
                }
                Spacer(minLength: 24)
                skipHint
            }
            .padding(.horizontal, 32)
            .padding(.vertical, 44)
        }
        .contentShape(Rectangle())
        .onTapGesture {
            guard !showsThanks else { return }
            skipAnimation()
        }
        .onAppear { start() }
        .onDisappear {
            sequence?.cancel()
            sequence = nil
        }
        .accessibilityElement(children: .contain)
        .accessibilityAction(named: Text("跳过动画".ui("Skip animation"))) { skipAnimation() }
    }

    @ViewBuilder
    private var questionLine: some View {
        if let question = result.question?.trimmingCharacters(in: .whitespacesAndNewlines),
           !question.isEmpty {
            Text(question)
                .font(.footnote)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .lineLimit(2)
                .frame(maxWidth: .infinity)
        }
    }

    private var figure: some View {
        VStack(spacing: 10) {
            // 视觉上自上而下：上爻 → 初爻（数组仍是初爻在 index 0）
            ForEach((0..<6).reversed(), id: \.self) { index in
                yaoRow(index: index)
            }
        }
    }

    private func yaoRow(index: Int) -> some View {
        let isRevealed = revealedCount > index
        let isMoving = result.movingPositions.contains(index + 1)
        return HStack(spacing: 12) {
            // 与右侧朱砂点对称，卦象才真正居中
            Color.clear.frame(width: 9, height: 1)
            YaoBarView(
                line: result.lines[index],
                barWidth: 150,
                barHeight: 14,
                gapWidth: 14,
                showsChangeMarker: false
            )
            cinnabarDot(visible: isRevealed && isMoving)
        }
        .opacity(isRevealed ? 1 : 0)
        .scaleEffect(x: isRevealed ? 1 : 0.8, y: 1)
        .offset(y: isRevealed ? 0 : 6)
    }

    private func cinnabarDot(visible: Bool) -> some View {
        Circle()
            .fill(Self.cinnabar)
            .frame(width: 9, height: 9)
            .scaleEffect(pulsesCinnabar ? 1.3 : 1)
            .opacity(visible ? 1 : 0)
    }

    private var seal: some View {
        VStack(spacing: 8) {
            Text(primary?.displayName ?? "第\(result.primaryNumber)卦".ui("Hexagram \(result.primaryNumber)"))
                .font(.system(size: 36, weight: .bold))
                .foregroundStyle(AppTheme.accent)
            if let resulting {
                Text("之 \(resulting.displayName)".ui("Relating · \(resulting.displayName)"))
                    .font(.title3)
                    .foregroundStyle(.secondary)
            }
        }
        // 卦名始终参与布局（只是透明），压印出现时卦象不会跳位
        .opacity(showsSeal ? 1 : 0)
        .scaleEffect(showsSeal ? 1 : 1.2)
    }

    private var thanks: some View {
        VStack(spacing: 18) {
            Text("感谢爻变开化之神的指示".zh)
                .font(.footnote)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            Button {
                finish()
            } label: {
                VStack(spacing: 4) {
                    Text("弟子退".zh)
                        .font(.headline)
                    RitualEnglishCaption(text: "Step back")
                }
                .padding(.horizontal, 28)
                .padding(.vertical, 10)
            }
            .buttonStyle(.borderedProminent)
            .tint(AppTheme.accent)
        }
        .opacity(showsThanks ? 1 : 0)
        .offset(y: showsThanks ? 0 : 8)
        .allowsHitTesting(showsThanks)
        .accessibilityHidden(!showsThanks)
    }

    private var skipHint: some View {
        Text("轻点跳过动画".ui("Tap to skip"))
            .font(.caption2)
            .foregroundStyle(.tertiary)
            .opacity(showsThanks || didFinish ? 0 : 1)
    }

    private func start() {
        guard sequence == nil, !didFinish else { return }
        RitualHaptics.prepare()

        guard !reduceMotion else {
            startReduced()
            return
        }

        if !result.movingPositions.isEmpty {
            withAnimation(.easeInOut(duration: 1.2).repeatForever(autoreverses: true)) {
                pulsesCinnabar = true
            }
        }

        sequence = Task { @MainActor in
            do {
                try await Task.sleep(nanoseconds: Beat.nanoseconds(Beat.opening))
                for index in 0..<6 {
                    if index == 5 {
                        try await Task.sleep(nanoseconds: Beat.nanoseconds(Beat.beforeFinalYao))
                    }
                    withAnimation(.easeOut(duration: Beat.yaoAppear)) {
                        revealedCount = index + 1
                    }
                    RitualHaptics.yaoSettled(moving: result.movingPositions.contains(index + 1))
                    try await Task.sleep(nanoseconds: Beat.nanoseconds(Beat.betweenYao))
                }
                try await Task.sleep(nanoseconds: Beat.nanoseconds(Beat.beforeSeal))
                withAnimation(.spring(response: 0.5, dampingFraction: 0.75)) {
                    showsSeal = true
                }
                RitualHaptics.seal()
                try await Task.sleep(nanoseconds: Beat.nanoseconds(Beat.beforeThanks))
            } catch {
                return
            }
            presentThanks()
        }
    }

    /// 开了「减弱动态效果」就不要逐爻动画，整卦淡入后短暂停留。
    private func startReduced() {
        withAnimation(.easeOut(duration: 0.3)) {
            revealedCount = 6
            showsSeal = true
        }
        RitualHaptics.seal()
        sequence = Task { @MainActor in
            do {
                try await Task.sleep(nanoseconds: Beat.nanoseconds(Beat.beforeThanks))
            } catch {
                return
            }
            presentThanks()
        }
    }

    /// 只跳过逐爻动画，不离开揭卦页。
    private func skipAnimation() {
        guard !showsThanks, !didFinish else { return }
        sequence?.cancel()
        sequence = nil
        revealedCount = 6
        showsSeal = true
        presentThanks()
    }

    private func presentThanks() {
        guard !showsThanks else { return }
        withAnimation(.easeOut(duration: 0.35)) {
            showsThanks = true
        }
    }

    private func finish() {
        guard !didFinish else { return }
        didFinish = true
        onFinish()
    }
}
