import SwiftUI

/// 取数：梅花三数一个一个落，落满三个自动成卦。
/// 和摇卦幕对位——取数也在告神之后现场进行，起卦页不再先填表。
struct NumberDrawActView: View {
    let question: String
    var onComplete: (Int, Int, Int) -> Void
    var onCancel: () -> Void

    @State private var drawn: [Int] = []
    @State private var entry = ""
    /// 落定后的短锁，防止连点把三个数一口气填完。
    @State private var isSettling = false
    @State private var showsResetConfirm = false
    @State private var sequence: Task<Void, Never>?
    @FocusState private var focused: Bool

    /// 取数的节奏，与摇卦幕分开：这里一次落一个数。
    private enum Beat {
        /// 盖层转场没走完就聚焦，iPhone 11 会卡。
        static let focusDelay: Double = 0.35
        static let afterNumber: Double = 0.5
        static let beforeComplete: Double = 0.6

        static func nanoseconds(_ seconds: Double) -> UInt64 {
            UInt64(seconds * 1_000_000_000)
        }
    }

    private var isComplete: Bool { drawn.count == 3 }

    private var entryValue: Int? {
        guard let value = Int(entry), value > 0 else { return nil }
        return value
    }

    private var canSettle: Bool { !isSettling && !isComplete && entryValue != nil }

    var body: some View {
        ZStack {
            AppTheme.parchmentGradient.ignoresSafeArea()

            VStack(spacing: 0) {
                header
                Spacer(minLength: 16)
                VStack(spacing: 34) {
                    slotColumn
                    VStack(spacing: 14) {
                        entryRow
                        settleButton
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
        .task {
            try? await Task.sleep(nanoseconds: Beat.nanoseconds(Beat.focusDelay))
            focused = true
        }
        .onDisappear {
            sequence?.cancel()
            sequence = nil
        }
        .confirmationDialog("重新取这一卦？".zh, isPresented: $showsResetConfirm, titleVisibility: .visible) {
            Button("重新取".zh, role: .destructive) { reset() }
            Button("继续".zh, role: .cancel) {}
        } message: {
            Text("已取的 \(drawn.count) 个数会作废。".zh)
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
            .accessibilityLabel("退出取数".zh)
            Spacer()
            if !drawn.isEmpty {
                Button("重来".zh) { showsResetConfirm = true }
                    .font(.subheadline)
                    .tint(AppTheme.accent)
                    .disabled(isSettling)
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
        if isComplete { return "三数已取" }
        return "\(Self.slotNames[drawn.count]) · 共三数"
    }

    private var slotColumn: some View {
        VStack(spacing: 14) {
            ForEach(0..<3, id: \.self) { index in
                slotRow(index: index)
            }
        }
    }

    private func slotRow(index: Int) -> some View {
        HStack(spacing: 12) {
            Text(Self.slotNames[index].zh)
                .font(.subheadline)
                .foregroundStyle(index <= drawn.count ? .secondary : .tertiary)
                .frame(width: 56, alignment: .leading)

            if index < drawn.count {
                Text("\(drawn[index])")
                    .font(.title3.monospacedDigit().weight(.semibold))
                    .foregroundStyle(AppTheme.accent)
                    .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                Capsule()
                    .strokeBorder(
                        Color.primary.opacity(index == drawn.count ? 0.28 : 0.12),
                        style: StrokeStyle(lineWidth: 1, dash: [4, 4])
                    )
                    .frame(height: 12)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .frame(height: 34)
    }

    /// 只此一个输入框，不放进 `slotColumn` 的 `ForEach`：
    /// 落定一个数就换一行的话输入框会换身份，键盘会掉下去再弹上来。
    private var entryRow: some View {
        HStack(spacing: 12) {
            TextField("输入数字".zh, text: $entry)
                .keyboardType(.numberPad)
                .focused($focused)
                .appTextFieldStyle()
            Button("随机".zh) {
                TapSoundPlayer.shared.play()
                entry = String(Int.random(in: 10...999))
            }
            .buttonStyle(.bordered)
            .tint(AppTheme.accent)
        }
        .disabled(isSettling || isComplete)
        .opacity(isComplete ? 0 : 1)
    }

    private var settleButton: some View {
        Button {
            settle()
        } label: {
            Text((drawn.count == 2 ? "成卦" : "落定").zh)
                .font(.headline)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
        }
        .buttonStyle(.borderedProminent)
        .tint(AppTheme.accent)
        .disabled(!canSettle)
        .opacity(isComplete ? 0 : 1)
    }

    private var instruction: some View {
        Text(instructionText.zh)
            .font(.caption)
            .foregroundStyle(.secondary)
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
    }

    private var instructionText: String {
        if isComplete { return "三数已取，正在成卦" }
        if isSettling { return "静候落定" }
        if drawn.isEmpty { return "心中默一个数写下，或点「随机」随手取一个" }
        return "再默一个数"
    }

    // MARK: - 取数

    private func settle() {
        guard canSettle, let value = entryValue else { return }
        isSettling = true
        withAnimation(.easeOut(duration: 0.3)) {
            drawn.append(value)
        }
        entry = ""
        RitualHaptics.yaoSettled(moving: false)
        TapSoundPlayer.shared.play()

        sequence = Task { @MainActor in
            do {
                try await Task.sleep(nanoseconds: Beat.nanoseconds(Beat.afterNumber))
                isSettling = false
                guard isComplete else { return }
                focused = false
                try await Task.sleep(nanoseconds: Beat.nanoseconds(Beat.beforeComplete))
            } catch {
                return
            }
            onComplete(drawn[0], drawn[1], drawn[2])
        }
    }

    private func reset() {
        sequence?.cancel()
        sequence = nil
        isSettling = false
        entry = ""
        withAnimation(.easeOut(duration: 0.25)) {
            drawn = []
        }
        focused = true
    }

    // MARK: - 文案

    private static let slotNames = ["上卦数", "下卦数", "动爻数"]
}
