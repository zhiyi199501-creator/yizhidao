import SwiftUI

/// 告神之后才选的取数法门。
enum CastingIntent: Hashable {
    case coin
    case digitalNumbers
    case digitalTime
}

/// 一次起卦请求。用 `UUID` 当 id，连着起两次也能重新呈现。
struct CastingRequest: Identifiable {
    let id = UUID()
}

/// 起卦仪式：静心 → 告神 → 选法门 → 取数 → 揭卦。
/// 宿主用 `fullScreenCover(item:)` 呈现，勿用 `isPresented` + 独立可选态。
struct CastingActView: View {
    var onFinish: (CastResult) -> Void
    var onCancel: () -> Void

    @State private var didSettle = false
    @State private var question: String?
    @State private var intent: CastingIntent?
    @State private var revealResult: CastResult?

    var body: some View {
        ZStack {
            if let result = revealResult {
                CastRevealView(result: result) {
                    onFinish(result)
                }
                .transition(.opacity)
            } else if let question, let intent {
                drawStage(question: question, intent: intent)
                    .transition(.opacity)
            } else if let question {
                MethodPickView(question: question) { chosen in
                    pick(chosen, question: question)
                } onCancel: {
                    onCancel()
                }
                .transition(.opacity)
            } else if didSettle {
                InvocationView { asked in
                    withAnimation(.easeInOut(duration: 0.35)) {
                        question = asked
                    }
                } onCancel: {
                    onCancel()
                }
                .transition(.opacity)
            } else {
                StillnessView {
                    withAnimation(.easeInOut(duration: 0.35)) {
                        didSettle = true
                    }
                } onCancel: {
                    onCancel()
                }
                .transition(.opacity)
            }
        }
    }

    @ViewBuilder
    private func drawStage(question: String, intent: CastingIntent) -> some View {
        switch intent {
        case .coin:
            CoinTossActView(question: question) { lines in
                reveal(
                    CoinCastingEngine.cast(lines: lines, question: question, at: .now)
                )
            } onCancel: {
                onCancel()
            }
        case .digitalNumbers:
            NumberDrawActView(question: question) { first, second, third in
                reveal(
                    DigitalCastingEngine.cast(
                        number1: first,
                        number2: second,
                        number3: third,
                        question: question,
                        at: .now
                    )
                )
            } onCancel: {
                onCancel()
            }
        case .digitalTime:
            EmptyView()
        }
    }

    /// 时间卦只占此刻、只用十二时辰；选的那一下才取 `.now`。
    private func pick(_ intent: CastingIntent, question: String) {
        switch intent {
        case .coin, .digitalNumbers:
            withAnimation(.easeInOut(duration: 0.35)) {
                self.intent = intent
            }
        case .digitalTime:
            let date = Date.now
            let components = LunarCalendarHelper.components(from: date)
            reveal(
                DigitalCastingEngine.cast(
                    yearBranch: components.yearBranch,
                    month: components.month,
                    day: components.day,
                    hour: components.hourBranch,
                    question: question,
                    at: date
                )
            )
        }
    }

    private func reveal(_ result: CastResult) {
        withAnimation(.easeInOut(duration: 0.35)) {
            revealResult = result
        }
    }
}
