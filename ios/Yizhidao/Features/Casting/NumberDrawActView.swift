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
                drawGrid
                Spacer(minLength: 16)
                instruction
            }
            .padding(.horizontal, 28)
            .padding(.top, 12)
            .padding(.bottom, 28)
        }
        .overlay(alignment: .top) { topBar }
        .onDisappear {
            sequence?.cancel()
            sequence = nil
        }
        .confirmationDialog("重新取这一卦？".ui("Cast this hexagram again?"), isPresented: $showsResetConfirm, titleVisibility: .visible) {
            Button("重新取".ui("Start over"), role: .destructive) { reset() }
            Button("继续".ui("Keep going"), role: .cancel) {}
        } message: {
            Text("已取的 \(drawn.count) 个数会作废。".ui("The \(drawn.count) numbers already drawn will be discarded."))
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
            .accessibilityLabel("退出取数".ui("Leave"))
            Spacer()
            if !drawn.isEmpty {
                Button("重来".ui("Start over")) { showsResetConfirm = true }
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

    /// 三列对齐：标签｜虚框／输入框｜随机。虚框左右与实框同宽。
    /// 输入框仍只此一个，不放进 `ForEach`，以免落定后换身份、键盘掉再弹。
    private var drawGrid: some View {
        Grid(alignment: .center, horizontalSpacing: 12, verticalSpacing: 14) {
            ForEach(0..<3, id: \.self) { index in
                GridRow {
                    Text(Self.slotNames[index].zh)
                        .font(.subheadline)
                        .foregroundStyle(index <= drawn.count ? .secondary : .tertiary)
                        .frame(width: 56, alignment: .leading)
                    slotValue(index: index)
                    Color.clear
                        .frame(width: 0, height: 0)
                        .gridCellUnsizedAxes([.horizontal, .vertical])
                }
                .frame(height: 34)
            }
            if !isComplete {
                GridRow {
                    Color.clear
                        .frame(width: 56, height: 1)
                    TextField("输入数字".ui("Number"), text: $entry)
                        .keyboardType(.numberPad)
                        .multilineTextAlignment(.center)
                        .focused($focused)
                        .appTextFieldStyle()
                        .frame(maxWidth: .infinity)
                    Button("随机".ui("Random")) {
                        entry = String(Int.random(in: 10...999))
                    }
                    .buttonStyle(.bordered)
                    .tint(AppTheme.accent)
                }
                .disabled(isSettling)
                GridRow {
                    Color.clear
                        .frame(width: 56, height: 1)
                    settleButton
                        .padding(.top, 10)
                    Color.clear
                        .frame(width: 0, height: 0)
                        .gridCellUnsizedAxes([.horizontal, .vertical])
                }
            }
        }
        .frame(maxWidth: .infinity)
    }

    @ViewBuilder
    private func slotValue(index: Int) -> some View {
        if index < drawn.count {
            Text("\(drawn[index])")
                .font(.title3.monospacedDigit().weight(.semibold))
                .foregroundStyle(AppTheme.accent)
                .frame(maxWidth: .infinity, alignment: .center)
        } else {
            Capsule()
                .strokeBorder(
                    Color.primary.opacity(index == drawn.count ? 0.28 : 0.12),
                    style: StrokeStyle(lineWidth: 1, dash: [4, 4])
                )
                .frame(maxWidth: .infinity, maxHeight: 12, alignment: .leading)
        }
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
