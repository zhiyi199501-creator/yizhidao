import SwiftUI

enum AppTheme {
    static let parchmentTop = Color(red: 0.96, green: 0.93, blue: 0.88)
    static let parchmentBottom = Color(red: 0.92, green: 0.90, blue: 0.86)
    static let accent = Color(red: 0.45, green: 0.22, blue: 0.18)
    static let cardFill = Color.white.opacity(0.72)
    static let fieldFill = Color.white
    static let fieldStroke = Color.black.opacity(0.12)

    static var parchmentGradient: LinearGradient {
        LinearGradient(
            colors: [parchmentTop, parchmentBottom],
            startPoint: .top,
            endPoint: .bottom
        )
    }
}

struct AppTextFieldStyle: TextFieldStyle {
    func _body(configuration: TextField<Self._Label>) -> some View {
        configuration
            .padding(.horizontal, 10)
            .padding(.vertical, 8)
            .background(
                RoundedRectangle(cornerRadius: 8, style: .continuous)
                    .fill(AppTheme.fieldFill)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 8, style: .continuous)
                    .stroke(AppTheme.fieldStroke, lineWidth: 1)
            )
    }
}

extension View {
    func appTextFieldStyle() -> some View {
        textFieldStyle(AppTextFieldStyle())
    }

    func parchmentBackground() -> some View {
        background(AppTheme.parchmentGradient.ignoresSafeArea())
    }
}

struct AIBadgeIcon: View {
    var compact: Bool = false

    var body: some View {
        Text("AI".zh)
            .font(.system(size: compact ? 13 : 15, weight: .bold, design: .rounded))
            .foregroundStyle(AppTheme.accent)
            .accessibilityLabel("AI")
    }
}

struct AIFloatingButton: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text("AI".zh)
                .font(.system(size: 20, weight: .bold, design: .rounded))
                .foregroundStyle(.white)
                .frame(width: 50, height: 50)
                .background(Circle().fill(AppTheme.accent))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("AI 解读".zh)
    }
}
