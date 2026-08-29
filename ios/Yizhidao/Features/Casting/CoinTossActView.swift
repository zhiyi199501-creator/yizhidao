import SwiftUI

/// 摇卦：三枚铜钱掷六次，自下而上成卦。一次只掷一爻，掷完当爻才能掷下一爻。
/// 摇手机或点铜钱都能掷；长按铜钱可手选四象（练习用）。
struct CoinTossActView: View {
    let question: String
    var onComplete: ([LineValue]) -> Void
    var onCancel: () -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var lines: [LineValue] = []
    @State private var toss: CoinToss?
    /// 已落定的枚数，0…3。掷的过程中逐枚翻出来。
    @State private var settledCoins = 0
    @State private var isTossing = false
    @State private var jitters = false
    @State private var showsManualPicker = false
    @State private var showsResetConfirm = false
    @State private var sequence: Task<Void, Never>?
    @State private var shakeDetector = ShakeDetector()

    /// 摇卦的节奏，与 `CastRevealView.Beat` 分开：这里是用户驱动的，只管一掷之内。
    private enum Beat {
        static let jitter: Double = 0.63
        static let betweenCoins: Double = 0.14
        static let beforeYao: Double = 0.45
        /// 一爻落定后的锁定，防止连点把六爻一秒摇完。
        static let afterYao: Double = 0.5
        static let beforeComplete: Double = 0.5

        static func nanoseconds(_ seconds: Double) -> UInt64 {
            UInt64(seconds * 1_000_000_000)
        }
    }

    private var isComplete: Bool { lines.count == 6 }
    private var canToss: Bool { !isTossing && !isComplete }

    var body: some View {
        ZStack {
            AppTheme.parchmentGradient.ignoresSafeArea()

            VStack(spacing: 0) {
                header
                Spacer(minLength: 16)
                VStack(spacing: 34) {
                    hexagramColumn
                    VStack(spacing: 8) {
                        coinRow
                        readout
                    }
                }
                Spacer(minLength: 16)
                instruction
            }
            .padding(.horizontal, 28)
            .padding(.top, 12)
            .padding(.bottom, 28)
        }
        .overlay(alignment: .top) { topBar }
        .onAppear {
            shakeDetector.onShake = { beginToss() }
            shakeDetector.start()
        }
        .onDisappear {
            shakeDetector.stop()
            sequence?.cancel()
            sequence = nil
        }
        .confirmationDialog("手选四象".zh, isPresented: $showsManualPicker, titleVisibility: .visible) {
            ForEach(Self.manualOptions, id: \.line) { option in
                Button(option.title.zh) { append(line: option.line) }
            }
            Button("取消".zh, role: .cancel) {}
        }
        .confirmationDialog("重新摇这一卦？".zh, isPresented: $showsResetConfirm, titleVisibility: .visible) {
            Button("重新摇".zh, role: .destructive) { reset() }
            Button("继续".zh, role: .cancel) {}
        } message: {
            Text("已摇的 \(lines.count) 爻会作废。".zh)
        }
    }

    // MARK: - 画面

    private var topBar: some View {
        HStack {
            Button {
                onCancel()
            } label: {
                Image(systemName: "xmark")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(.secondary)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("退出摇卦".zh)
            Spacer()
            if !lines.isEmpty {
                Button("重来".zh) { showsResetConfirm = true }
                    .font(.subheadline)
                    .tint(AppTheme.accent)
                    .disabled(isTossing)
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 8)
    }

    private var header: some View {
        VStack(spacing: 6) {
            Text(question)
                .font(.footnote)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .lineLimit(2)
            Text(progressTitle.zh)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(AppTheme.accent)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 28)
    }

    private var progressTitle: String {
        if isComplete { return "六爻已成" }
        return "\(Self.yaoNames[lines.count])爻 · 共六爻"
    }

    private var hexagramColumn: some View {
        VStack(spacing: 8) {
            // 视觉上自上而下：上爻 → 初爻（数组仍是初爻在 index 0）
            ForEach((0..<6).reversed(), id: \.self) { index in
                yaoSlot(index: index)
            }
        }
    }

    private func yaoSlot(index: Int) -> some View {
        HStack(spacing: 10) {
            Text(Self.yaoNames[index].zh)
                .font(.caption)
                .foregroundStyle(index < lines.count ? .secondary : .tertiary)
                .frame(width: 24, alignment: .trailing)
            if index < lines.count {
                YaoBarView(
                    line: lines[index],
                    barWidth: 140,
                    barHeight: 12,
                    gapWidth: 12
                )
            } else {
                emptyBar(isNext: index == lines.count)
            }
        }
    }

    /// 空爻位。留住和 `YaoBarView` 一样的宽高，六爻不会随着摇卦上下跳。
    private func emptyBar(isNext: Bool) -> some View {
        HStack(spacing: 6) {
            Capsule()
                .strokeBorder(
                    Color.primary.opacity(isNext ? 0.28 : 0.12),
                    style: StrokeStyle(lineWidth: 1, dash: [4, 4])
                )
                .frame(width: 140, height: 12)
            Color.clear.frame(width: 12, height: 12)
        }
        .padding(4)
    }

    private var coinRow: some View {
        HStack(spacing: 20) {
            ForEach(0..<3, id: \.self) { index in
                CoinView(
                    isYangFace: settledCoins > index ? toss?.faces[index] : nil,
                    jitters: jitters,
                    wobbleSeed: index
                )
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
        .contentShape(Rectangle())
        .onTapGesture { beginToss() }
        .onLongPressGesture(minimumDuration: 0.5) {
            guard canToss else { return }
            showsManualPicker = true
        }
        .opacity(isComplete ? 0.35 : 1)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("三枚铜钱".zh)
        .accessibilityHint("轻点掷一爻，长按手选四象".zh)
        .accessibilityAddTraits(.isButton)
    }

    private var readout: some View {
        Text(readoutText.zh)
            .font(.subheadline.monospaced())
            .foregroundStyle(settledCoins == 3 ? AppTheme.accent : .secondary)
            .frame(height: 24)
    }

    private var readoutText: String {
        guard let toss, settledCoins > 0 else { return " " }
        let shown = toss.faces.prefix(settledCoins).map { $0 ? "字" : "背" }.joined(separator: " ")
        guard settledCoins == 3 else { return shown }
        return "\(shown)　\(Self.title(for: toss.line))"
    }

    private var instruction: some View {
        Text(instructionText.zh)
            .font(.caption)
            .foregroundStyle(.secondary)
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
    }

    private var instructionText: String {
        if isComplete { return "六爻已成，正在成卦" }
        if isTossing { return "静候铜钱落定" }
        if lines.isEmpty { return "摇一摇手机，或轻点铜钱掷出第一爻" }
        return "摇一摇手机，或轻点铜钱掷出下一爻"
    }

    // MARK: - 摇卦

    private func beginToss() {
        guard canToss else { return }
        isTossing = true
        settledCoins = 0
        let thisToss = CoinCastingEngine.toss()
        toss = thisToss

        if !reduceMotion {
            withAnimation(.easeInOut(duration: 0.09).repeatCount(7, autoreverses: true)) {
                jitters = true
            }
        }

        sequence = Task { @MainActor in
            do {
                if !reduceMotion {
                    try await Task.sleep(nanoseconds: Beat.nanoseconds(Beat.jitter))
                    jitters = false
                }
                for index in 0..<3 {
                    withAnimation(.spring(response: 0.3, dampingFraction: 0.6)) {
                        settledCoins = index + 1
                    }
                    RitualHaptics.yaoSettled(moving: false)
                    TapSoundPlayer.shared.play()
                    try await Task.sleep(nanoseconds: Beat.nanoseconds(Beat.betweenCoins))
                }
                try await Task.sleep(nanoseconds: Beat.nanoseconds(Beat.beforeYao))
                settle(line: thisToss.line)
                try await Task.sleep(nanoseconds: Beat.nanoseconds(Beat.afterYao))
                isTossing = false
                guard isComplete else { return }
                try await Task.sleep(nanoseconds: Beat.nanoseconds(Beat.beforeComplete))
            } catch {
                return
            }
            onComplete(lines)
        }
    }

    /// 手选四象走的是同一条落爻路径，只是没有铜钱动画。
    private func append(line: LineValue) {
        guard canToss else { return }
        toss = nil
        settledCoins = 0
        settle(line: line)
        guard isComplete else { return }
        sequence = Task { @MainActor in
            do {
                try await Task.sleep(nanoseconds: Beat.nanoseconds(Beat.beforeComplete))
            } catch {
                return
            }
            onComplete(lines)
        }
    }

    private func settle(line: LineValue) {
        withAnimation(.easeOut(duration: 0.3)) {
            lines.append(line)
        }
        RitualHaptics.yaoSettled(moving: line.isChanging)
    }

    private func reset() {
        sequence?.cancel()
        sequence = nil
        isTossing = false
        jitters = false
        settledCoins = 0
        toss = nil
        withAnimation(.easeOut(duration: 0.25)) {
            lines = []
        }
    }

    // MARK: - 文案

    private static let yaoNames = ["初", "二", "三", "四", "五", "上"]

    private static let manualOptions: [(title: String, line: LineValue)] = [
        ("少阳 7", .youngYang),
        ("少阴 8", .youngYin),
        ("阳动 9", .oldYang),
        ("阴动 6", .oldYin),
    ]

    private static func title(for line: LineValue) -> String {
        manualOptions.first { $0.line == line }?.title ?? ""
    }
}

/// 一枚乾隆通宝。`isYangFace` 为 nil 表示还没落定。
struct CoinView: View {
    let isYangFace: Bool?
    let jitters: Bool
    let wobbleSeed: Int
    var size: CGFloat = 72

    private static let brassLight = Color(red: 0.85, green: 0.71, blue: 0.45)
    private static let brassDark = Color(red: 0.64, green: 0.47, blue: 0.25)
    private static let ink = Color(red: 0.28, green: 0.18, blue: 0.10)

    /// 三枚各转不同角度，落面才像三枚而不是一枚的三份拷贝。
    private var wobble: Double {
        let magnitudes: [Double] = [9, -12, 7]
        return magnitudes[wobbleSeed % magnitudes.count]
    }

    var body: some View {
        ZStack {
            disc
            if isYangFace == true {
                characters
            } else if isYangFace == false {
                backRing
            }
        }
        .frame(width: size, height: size)
        .opacity(isYangFace == nil ? 0.55 : 1)
        .rotationEffect(.degrees(jitters ? wobble : 0))
        .offset(y: jitters ? -4 : 0)
    }

    /// 带方孔的钱身。方孔用 destinationOut 抠掉，才不会被宣纸渐变穿帮。
    private var disc: some View {
        LinearGradient(
            colors: [Self.brassLight, Self.brassDark],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
        .mask {
            ZStack {
                Circle()
                RoundedRectangle(cornerRadius: 1.5)
                    .frame(width: size * 0.24, height: size * 0.24)
                    .blendMode(.destinationOut)
            }
            .compositingGroup()
        }
        .overlay {
            Circle().strokeBorder(Color.black.opacity(0.16), lineWidth: 1.5)
        }
        .overlay {
            RoundedRectangle(cornerRadius: 1.5)
                .strokeBorder(Color.black.opacity(0.26), lineWidth: 1)
                .frame(width: size * 0.24, height: size * 0.24)
        }
    }

    /// 乾隆通宝，读序上下右左。
    private var characters: some View {
        ZStack {
            glyph("乾").offset(y: -size * 0.30)
            glyph("隆").offset(y: size * 0.30)
            glyph("通").offset(x: size * 0.30)
            glyph("宝").offset(x: -size * 0.30)
        }
    }

    private func glyph(_ text: String) -> some View {
        Text(text.zh)
            .font(.system(size: size * 0.17, weight: .semibold))
            .foregroundStyle(Self.ink)
    }

    private var backRing: some View {
        Circle()
            .strokeBorder(Color.black.opacity(0.10), lineWidth: 1)
            .frame(width: size * 0.6, height: size * 0.6)
    }
}