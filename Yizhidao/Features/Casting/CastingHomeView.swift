import SwiftUI
import UIKit

struct CastingHomeView: View {
    enum MethodTab: String, CaseIterable, Identifiable {
        case digital = "数字起卦"
        case coin = "金钱卦"
        var id: String { rawValue }
    }

    @State private var methodTab: MethodTab = .digital
    @State private var question = ""
    @State private var latestResult: CastResult?
    @State private var showResult = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Text("易知道")
                        .font(.largeTitle.weight(.bold))
                    Text("选择起卦方法，记录占时，观象玩辞。")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)

                    TextField("所问何事（可选）", text: $question, axis: .vertical)
                        .textFieldStyle(.roundedBorder)
                        .lineLimit(2...4)

                    Picker("方法", selection: $methodTab) {
                        ForEach(MethodTab.allCases) { tab in
                            Text(tab.rawValue).tag(tab)
                        }
                    }
                    .pickerStyle(.segmented)

                    Group {
                        switch methodTab {
                        case .digital:
                            DigitalCastView(question: $question) { result in
                                latestResult = result
                                showResult = true
                            }
                        case .coin:
                            CoinCastView(question: $question) { result in
                                latestResult = result
                                showResult = true
                            }
                        }
                    }
                    .padding()
                    .background(
                        RoundedRectangle(cornerRadius: 16)
                            .fill(Color(.secondarySystemBackground))
                    )
                }
                .padding()
            }
            .scrollDismissesKeyboard(.interactively)
            .background {
                DismissKeyboardBackground()
            }
            .background(
                LinearGradient(
                    colors: [
                        Color(red: 0.96, green: 0.93, blue: 0.88),
                        Color(red: 0.92, green: 0.90, blue: 0.86)
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .ignoresSafeArea()
            )
            .navigationDestination(isPresented: $showResult) {
                if let latestResult {
                    ResultView(result: latestResult, isNew: true)
                }
            }
        }
    }
}

/// 点空白收起键盘，且不拦截按钮 / 输入框点击。
private struct DismissKeyboardBackground: UIViewRepresentable {
    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.backgroundColor = .clear
        let tap = UITapGestureRecognizer(
            target: context.coordinator,
            action: #selector(Coordinator.handleTap(_:))
        )
        tap.cancelsTouchesInView = false
        view.addGestureRecognizer(tap)
        context.coordinator.tapRecognizer = tap
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        DispatchQueue.main.async {
            guard let window = uiView.window else { return }
            guard let tap = context.coordinator.tapRecognizer else { return }
            if tap.view !== window {
                tap.view?.removeGestureRecognizer(tap)
                window.addGestureRecognizer(tap)
            }
        }
    }

    static func dismantleUIView(_ uiView: UIView, coordinator: Coordinator) {
        if let tap = coordinator.tapRecognizer {
            tap.view?.removeGestureRecognizer(tap)
        }
    }

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    final class Coordinator: NSObject {
        var tapRecognizer: UITapGestureRecognizer?

        @objc func handleTap(_ gesture: UITapGestureRecognizer) {
            if let root = gesture.view {
                let point = gesture.location(in: root)
                if let hit = root.hitTest(point, with: nil), isTextInput(hit) {
                    return
                }
            }
            UIApplication.shared.sendAction(
                #selector(UIResponder.resignFirstResponder),
                to: nil,
                from: nil,
                for: nil
            )
        }

        private func isTextInput(_ view: UIView) -> Bool {
            var current: UIView? = view
            while let node = current {
                if node is UITextField || node is UITextView {
                    return true
                }
                current = node.superview
            }
            return false
        }
    }
}
