import SwiftUI

/// 静心：按住聚气，墨色由内晕开填满才往下走。
/// 这一幕不产出任何卦象数据，它要的就是一段停顿和一次身体上的落定。
struct StillnessView: View {
    var onReady: () -> Void
    var onCancel: () -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var progress: Double = 0
    @State private var isPressing = false
    @State private var breathes = false
    @State private var hold: Task<Void, Never>?
    @State private var didFinish = false

    private enum Beat {
        /// 按住多久算聚满。
        static let gather: Double = 2.5
        /// 满了之后停一下再走，让人看见它满了。
        static let settle: Double = 0.4
        static let release: Double = 0.35

        static func nanoseconds(_ seconds: Double) -> UInt64 {
            UInt64(seconds * 1_000_000_000)
        }
    }

    private static let ringSize: CGFloat = 168
    /// 偏暖的墨色。用主题的赭色画晕开会发灰粉，不像墨。
    private static let ink = Color(red: 0.14, green: 0.12, blue: 0.11)

    var body: some View {
        ZStack {
            AppTheme.parchmentGradient.ignoresSafeArea()

            VStack(spacing: 0) {
                Spacer()
                VStack(spacing: 52) {
                    ritualLines
                    VStack(spacing: 22) {
                        gatheringRing
                        Text(promptText.zh)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                        RitualEnglishCaption(text: "Hold to settle")
                            .frame(height: 18)
                    }
                }
                Spacer()
            }
            .padding(.horizontal, 40)
        }
        .overlay(alignment: .top) { topBar }
        .onAppear {
            guard !reduceMotion else { return }
            withAnimation(.easeInOut(duration: 2.4).repeatForever(autoreverses: true)) {
                breathes = true
            }
        }
        .onDisappear {
            hold?.cancel()
            hold = nil
        }
    }

    private var topBar: some View {
        HStack {
            Button {
                onCancel()
            } label: {
                Image(systemName: "xmark")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(.secondary)
            }
            .accessibilityLabel("取消起卦".ui("Cancel casting"))
            Spacer()
            Button("跳过".ui("Skip")) { finish() }
                .font(.subheadline)
                .foregroundStyle(.tertiary)
        }
        .padding(.horizontal, 20)
        .padding(.top, 8)
    }

    private var ritualLines: some View {
        VStack(spacing: 6) {
            ForEach(Self.ritualLines, id: \.self) { line in
                Text(line.zh)
                    .font(.footnote)
                    .foregroundStyle(.tertiary)
            }
        }
        .multilineTextAlignment(.center)
    }

    private var gatheringRing: some View {
        ZStack {
            Circle()
                .strokeBorder(AppTheme.accent.opacity(0.18), lineWidth: 1.5)
                .frame(width: Self.ringSize, height: Self.ringSize)
                .scaleEffect(breathes && !isPressing ? 1.04 : 1)

            // 墨色由内向外晕开，边缘化开一点，不要硬边
            Circle()
                .fill(
                    RadialGradient(
                        colors: [
                            Self.ink.opacity(0.92),
                            Self.ink.opacity(0.72),
                            Self.ink.opacity(0.20),
                        ],
                        center: .center,
                        startRadius: 0,
                        endRadius: Self.ringSize * 0.58
                    )
                )
                .frame(width: Self.ringSize, height: Self.ringSize)
                .scaleEffect(progress)
        }
        .contentShape(Circle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { _ in
                    guard !isPressing else { return }
                    beginGathering()
                }
                .onEnded { _ in
                    releaseGathering()
                }
        )
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("凝心一会".ui("Hold to settle"))
        .accessibilityHint("按住，满了继续".ui("Hold until the circle fills"))
        .accessibilityAddTraits(.isButton)
        .accessibilityAction { finish() }
    }

    private var promptText: String { "凝心一会" }

    private func beginGathering() {
        guard !didFinish else { return }
        isPressing = true
        withAnimation(.linear(duration: Beat.gather)) {
            progress = 1
        }
        hold = Task { @MainActor in
            do {
                try await Task.sleep(nanoseconds: Beat.nanoseconds(Beat.gather))
                RitualHaptics.seal()
                isPressing = false
                didFinish = true
                try await Task.sleep(nanoseconds: Beat.nanoseconds(Beat.settle))
            } catch {
                return
            }
            onReady()
        }
    }

    /// 松手即散。聚气要求的是一段不中断的专注，续上一半反而没有分量。
    private func releaseGathering() {
        guard isPressing, !didFinish else { return }
        isPressing = false
        hold?.cancel()
        hold = nil
        withAnimation(.easeOut(duration: Beat.release)) {
            progress = 0
        }
        RitualHaptics.yaoSettled(moving: false)
    }

    private func finish() {
        guard !didFinish else { return }
        didFinish = true
        hold?.cancel()
        hold = nil
        onReady()
    }

    private static let ritualLines: [String] = [
        "净手，择一静处，坐稳。",
        "静穆身心，敬慎其意。",
    ]
}
