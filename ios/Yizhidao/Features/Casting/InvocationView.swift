import SwiftUI

/// 告神：所问单独成屏。用户写的内容嵌在默祷句式里，写的过程本身就是「告」。
/// 句式取自起卦礼仪第三条。
struct InvocationView: View {
    var onConfirm: (String) -> Void
    var onCancel: () -> Void

    @State private var text = ""
    @FocusState private var isFocused: Bool

    private var trimmed: String {
        text.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var body: some View {
        ZStack {
            AppTheme.parchmentGradient.ignoresSafeArea()

            VStack(spacing: 0) {
                Text("所问何事".zh)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(AppTheme.accent)
                    .padding(.top, 40)

                Spacer(minLength: 24)

                invocation

                Spacer(minLength: 24)

                Button {
                    isFocused = false
                    onConfirm(trimmed)
                } label: {
                    Text("敬告".zh)
                        .font(.headline)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                }
                .buttonStyle(.borderedProminent)
                .tint(AppTheme.accent)
                .disabled(trimmed.isEmpty)
                .padding(.bottom, 24)
            }
            .padding(.horizontal, 32)
        }
        .overlay(alignment: .topLeading) {
            Button {
                isFocused = false
                onCancel()
            } label: {
                Image(systemName: "xmark")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(.secondary)
            }
            .accessibilityLabel("取消起卦".zh)
            .padding(.horizontal, 20)
            .padding(.top, 8)
        }
        .dismissKeyboardOnBlankTap()
        .task {
            // 等盖层转场走完再弹键盘，iPhone 11 上同时做这两件事会卡。
            try? await Task.sleep(nanoseconds: 350_000_000)
            isFocused = true
        }
    }

    private var invocation: some View {
        VStack(spacing: 14) {
            Text("弟子今有".zh)
                .font(.subheadline)
                .foregroundStyle(.tertiary)

            TextField("", text: $text, axis: .vertical)
                .textFieldStyle(.plain)
                .font(.title3)
                .multilineTextAlignment(.center)
                .lineLimit(1...6)
                .focused($isFocused)
                .overlay(alignment: .center) {
                    if text.isEmpty {
                        Text("简单扼要讲清楚一件事".zh)
                            .font(.title3)
                            .foregroundStyle(.tertiary)
                            .allowsHitTesting(false)
                    }
                }

            Rectangle()
                .fill(AppTheme.fieldStroke)
                .frame(height: 1)

            Text("之事，不知休咎，望示一卦。".zh)
                .font(.subheadline)
                .foregroundStyle(.tertiary)
                .multilineTextAlignment(.center)
        }
    }
}
