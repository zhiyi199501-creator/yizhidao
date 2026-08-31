import SwiftUI

private enum UnlockPromo {
    static let gold = Color(red: 0.93, green: 0.70, blue: 0.34)
    static let ember = Color(red: 0.82, green: 0.40, blue: 0.16)
    static let ink = Color(red: 0.78, green: 0.34, blue: 0.14)

    static var cardGradient: LinearGradient {
        LinearGradient(
            colors: [gold, ember],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    }

    static var buttonGradient: LinearGradient {
        LinearGradient(
            colors: [ink, gold],
            startPoint: .leading,
            endPoint: .trailing
        )
    }
}

struct UnlockReadingsView: View {
    var onUnlocked: (() -> Void)? = nil

    @Environment(UnlockStore.self) private var unlock
    @Environment(\.dismiss) private var dismiss
    @State private var legal: LegalDocKind?

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                header
                quotaCard
                featuresCard
                footer
            }
            .padding(.horizontal, 20)
            .padding(.top, 8)
            .padding(.bottom, 28)
        }
        .navigationTitle("解锁问答".ui("Unlock Readings"))
        .navigationBarTitleDisplayMode(.inline)
        .parchmentBackground()
        .navigationDestination(item: $legal) { kind in
            LegalDocumentView(title: kind.title, file: kind.file)
        }
        .task {
            await unlock.loadProduct()
            await unlock.refreshFromServer()
        }
    }

    private var header: some View {
        VStack(spacing: 12) {
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(UnlockPromo.cardGradient)
                .frame(width: 72, height: 72)
                .overlay {
                    Image(systemName: "bubble.left.and.bubble.right.fill")
                        .font(.system(size: 28, weight: .semibold))
                        .foregroundStyle(.white)
                }
            Text("易玩家 · 解锁问答".ui("Yiwanjia · Unlock Readings"))
                .font(.system(size: 22, weight: .bold))
        }
        .multilineTextAlignment(.center)
        .frame(maxWidth: .infinity)
        .padding(.top, 8)
        .padding(.bottom, 4)
    }

    private var quotaCard: some View {
        let limit = max(unlock.dailyLimit, 1)
        let remaining = min(max(unlock.dailyRemaining, 0), limit)
        let usedFraction = min(max(Double(unlock.dailyUsed) / Double(limit), 0), 1)
        return VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("每日问答".ui("Daily readings"))
                    .font(.subheadline.weight(.medium))
                Spacer()
                Text("剩余 \(remaining) / \(limit)".ui("\(remaining) / \(limit) left"))
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(UnlockPromo.ink)
            }
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule()
                        .fill(UnlockPromo.ink.opacity(0.12))
                    Capsule()
                        .fill(UnlockPromo.ink)
                        .frame(width: geo.size.width * usedFraction)
                }
            }
            .frame(height: 3)
            Text(quotaCaption(remaining: remaining, limit: limit))
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .padding(16)
        .background(Color.white.opacity(0.82), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    private func quotaCaption(remaining: Int, limit: Int) -> String {
        if remaining <= 0 {
            return unlock.isUnlocked
                ? "今天的次数用完了，明天再来。".ui("Today’s readings are used up. Come back tomorrow.")
                : "今天的次数用完了。买断后每天三十次。".ui("Today’s readings are used up. Unlock for thirty a day.")
        }
        if unlock.isUnlocked {
            return "今天还剩 \(remaining) 次，每天共 \(limit) 次。".ui("\(remaining) left today, \(limit) a day.")
        }
        return "今天还剩 \(remaining) 次。买断后每天三十次。".ui("\(remaining) left today. Thirty a day after unlock.")
    }

    private var featuresCard: some View {
        VStack(spacing: 0) {
            featureRow(
                icon: "bubble.left.and.bubble.right.fill",
                tint: Color(red: 0.85, green: 0.28, blue: 0.24),
                title: "每天三十次问答".ui("Thirty readings a day"),
                detail: "未购每天三次，买断后放宽到三十次。".ui("Three a day free. Thirty a day after unlock.")
            )
            Divider().padding(.leading, 56)
            featureRow(
                icon: "book.fill",
                tint: Color(red: 0.93, green: 0.55, blue: 0.22),
                title: "起卦与经文仍免费".ui("Casting and texts stay free"),
                detail: "起卦、经文、黄庭、案例不收费。".ui("Casting, classics, commentaries, and cases stay free.")
            )
            Divider().padding(.leading, 56)
            featureRow(
                icon: "checkmark.seal.fill",
                tint: Color(red: 0.35, green: 0.52, blue: 0.92),
                title: "一次买断".ui("One purchase"),
                detail: "买一次即可，不会按月扣款。".ui("Pay once. No monthly charge.")
            )
            Divider().padding(.leading, 56)
            featureRow(
                icon: "arrow.clockwise",
                tint: Color(red: 0.62, green: 0.42, blue: 0.86),
                title: "换机可恢复".ui("Restore on a new device"),
                detail: "同一账号登录即可。本机没有时再点恢复购买。".ui("Sign in to the same account. Use Restore Purchases only if this device doesn’t have it.")
            )
        }
        .padding(.vertical, 6)
        .background(Color.white.opacity(0.82), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    private func featureRow(icon: String, tint: Color, title: String, detail: String) -> some View {
        HStack(alignment: .top, spacing: 12) {
            RoundedRectangle(cornerRadius: 8, style: .continuous)
                .fill(tint)
                .frame(width: 32, height: 32)
                .overlay {
                    Image(systemName: icon)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(.white)
                }
            VStack(alignment: .leading, spacing: 3) {
                Text(title.zh)
                    .font(.subheadline.weight(.semibold))
                Text(detail.zh)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }

    private var footer: some View {
        VStack(spacing: 14) {
            if unlock.isUnlocked {
                Text("此账号已解锁问答".ui("This account has unlocked readings"))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            } else {
                Button {
                    Task { await buy() }
                } label: {
                    Text("\(unlock.displayPrice) 终身解锁".ui("\(unlock.displayPrice) lifetime unlock"))
                        .font(.headline)
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 15)
                        .background(UnlockPromo.buttonGradient, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
                .buttonStyle(.plain)
                .disabled(unlock.isBusy)
                .opacity(unlock.isBusy ? 0.65 : 1)
            }

            HStack(spacing: 6) {
                Button("恢复购买".ui("Restore Purchases")) {
                    Task { await restore() }
                }
                .disabled(unlock.isBusy)
                Text("·".zh).foregroundStyle(.tertiary)
                Button("隐私政策".ui("Privacy Policy")) { legal = .privacy }
                Text("·".zh).foregroundStyle(.tertiary)
                Button("使用条款".ui("Terms of Use")) { legal = .terms }
            }
            .font(.footnote)
            .foregroundStyle(UnlockPromo.ink)
            .buttonStyle(.plain)

            if unlock.isBusy {
                ProgressView()
            }
            if let errorMessage = unlock.errorMessage, !errorMessage.isEmpty {
                Text(errorMessage.zh)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
            if let statusMessage = unlock.statusMessage, !statusMessage.isEmpty {
                Text(statusMessage.zh)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }

            Text("内容仅供文化学习与参考。".ui("For cultural study and reflection only."))
                .font(.caption2)
                .foregroundStyle(.tertiary)
        }
        .padding(.top, 8)
    }

    private func buy() async {
        if await unlock.purchase() {
            onUnlocked?()
            dismiss()
        }
    }

    private func restore() async {
        if await unlock.restore() {
            onUnlocked?()
            dismiss()
        }
    }
}
