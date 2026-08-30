import SwiftUI

/// 告神之后选法门。时间卦只占此刻，选完直接成卦。
struct MethodPickView: View {
    let question: String
    var onPick: (CastingIntent) -> Void
    var onCancel: () -> Void

    var body: some View {
        ZStack {
            AppTheme.parchmentGradient.ignoresSafeArea()

            VStack(spacing: 0) {
                Text(question)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
                    .frame(maxWidth: .infinity)
                    .padding(.top, 40)

                Text("怎样取这一卦".zh)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(AppTheme.accent)
                    .padding(.top, 8)

                Spacer(minLength: 24)

                VStack(spacing: 16) {
                    methodButton(title: "数字起卦", subtitle: "三个数定上卦、下卦、动爻") {
                        onPick(.digitalNumbers)
                    }
                    methodButton(title: "时间起卦", subtitle: "以此刻十二时辰取数") {
                        onPick(.digitalTime)
                    }
                    methodButton(title: "金钱起卦", subtitle: "三枚铜钱摇六次") {
                        onPick(.coin)
                    }
                }

                Spacer(minLength: 24)
            }
            .padding(.horizontal, 32)
        }
        .overlay(alignment: .topLeading) {
            Button {
                onCancel()
            } label: {
                Image(systemName: "xmark")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(.secondary)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("取消起卦".zh)
            .padding(.horizontal, 20)
            .padding(.top, 8)
        }
    }

    private func methodButton(title: String, subtitle: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 6) {
                Text(title.zh)
                    .font(.headline)
                    .foregroundStyle(AppTheme.accent)
                Text(subtitle.zh)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 18)
            .background(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .stroke(AppTheme.accent.opacity(0.22), lineWidth: 1)
            )
            .contentShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}
