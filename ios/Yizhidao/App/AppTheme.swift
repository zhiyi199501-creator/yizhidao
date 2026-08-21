import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

enum AppTheme {
    static let parchmentTop = Color(red: 0.98, green: 0.96, blue: 0.93)
    static let parchmentBottom = Color(red: 0.96, green: 0.94, blue: 0.90)
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

extension View {
    func appTextFieldStyle() -> some View {
        textFieldStyle(.plain)
            .loginFieldChrome()
    }

    /// 装饰画在输入框外面，避免自定义 TextFieldStyle 拖慢 iPhone 11 弹出键盘。
    func loginFieldChrome() -> some View {
        padding(.horizontal, 10)
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

    func parchmentBackground() -> some View {
        background(AppTheme.parchmentGradient.ignoresSafeArea())
    }

    /// 点空白收键盘。手势在碰到输入框时直接不接收，避免和弹出键盘抢焦点。
    func dismissKeyboardOnBlankTap() -> some View {
        #if canImport(UIKit)
        background { DismissKeyboardTapInstaller() }
        #else
        self
        #endif
    }
}

#if canImport(UIKit)
/// SwiftUI `TextField` 在 sheet 里会走自动填充和键盘协调，iPhone 11 上经常几秒才弹出。
struct LoginNumberField: UIViewRepresentable {
    @Binding var text: String
    var placeholder: String

    func makeCoordinator() -> Coordinator {
        Coordinator(text: $text)
    }

    func makeUIView(context: Context) -> UITextField {
        let field = UITextField()
        field.placeholder = placeholder
        field.keyboardType = .numberPad
        field.keyboardAppearance = .light
        field.autocorrectionType = .no
        field.spellCheckingType = .no
        field.autocapitalizationType = .none
        field.smartDashesType = .no
        field.smartQuotesType = .no
        field.smartInsertDeleteType = .no
        field.textContentType = nil
        field.borderStyle = .none
        field.font = .preferredFont(forTextStyle: .body)
        field.adjustsFontForContentSizeCategory = true
        field.delegate = context.coordinator
        field.addTarget(
            context.coordinator,
            action: #selector(Coordinator.editingChanged(_:)),
            for: .editingChanged
        )
        field.setContentHuggingPriority(.defaultLow, for: .horizontal)
        field.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        field.setContentHuggingPriority(.required, for: .vertical)
        field.setContentCompressionResistancePriority(.required, for: .vertical)
        return field
    }

    func sizeThatFits(_ proposal: ProposedViewSize, uiView: UITextField, context: Context) -> CGSize? {
        let height = max(uiView.intrinsicContentSize.height, 22)
        let width = proposal.width ?? uiView.intrinsicContentSize.width
        return CGSize(width: width, height: height)
    }

    func updateUIView(_ field: UITextField, context: Context) {
        context.coordinator.text = $text
        if field.text != text {
            field.text = text
        }
        if field.placeholder != placeholder {
            field.placeholder = placeholder
        }
    }

    final class Coordinator: NSObject, UITextFieldDelegate {
        var text: Binding<String>

        init(text: Binding<String>) {
            self.text = text
        }

        @objc func editingChanged(_ sender: UITextField) {
            text.wrappedValue = sender.text ?? ""
        }
    }
}

/// 点空白收起键盘；不挂到输入框上，所以不会拖慢弹键盘。
private struct DismissKeyboardTapInstaller: UIViewRepresentable {
    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.isUserInteractionEnabled = false
        let tap = UITapGestureRecognizer(
            target: context.coordinator,
            action: #selector(Coordinator.handleTap)
        )
        tap.cancelsTouchesInView = false
        tap.delegate = context.coordinator
        context.coordinator.recognizer = tap
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        DispatchQueue.main.async {
            guard let window = uiView.window else { return }
            guard let tap = context.coordinator.recognizer else { return }
            if tap.view !== window {
                tap.view?.removeGestureRecognizer(tap)
                window.addGestureRecognizer(tap)
            }
        }
    }

    static func dismantleUIView(_ uiView: UIView, coordinator: Coordinator) {
        guard let tap = coordinator.recognizer else { return }
        tap.view?.removeGestureRecognizer(tap)
    }

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var recognizer: UITapGestureRecognizer?

        @objc func handleTap() {
            UIApplication.shared.sendAction(
                #selector(UIResponder.resignFirstResponder),
                to: nil,
                from: nil,
                for: nil
            )
        }

        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
            !Self.isTextInput(touch.view)
        }

        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
        ) -> Bool {
            true
        }

        private static func isTextInput(_ view: UIView?) -> Bool {
            var node = view
            while let current = node {
                if current is UITextField || current is UITextView {
                    return true
                }
                let name = NSStringFromClass(type(of: current))
                if name.contains("TextField")
                    || name.contains("TextView")
                    || name.contains("TextInput")
                    || name.contains("FieldEditor")
                    || name.contains("Keyboard")
                {
                    return true
                }
                node = current.superview
            }
            return false
        }
    }
}
#endif

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
