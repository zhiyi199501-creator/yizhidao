import SwiftUI

/// 起卦方式。取数不在这里——三种方式的数都在告神之后才落：
/// 金钱卦去摇卦幕，三数去取数幕，时间卦取敬告那一刻。
enum CastingIntent: Hashable {
    case coin
    case digitalNumbers
    /// `moment` 为 nil 表示「此刻」——真正的时刻等敬告那一下再取，
    /// 否则静心加告神这半分钟会让「以当前时刻起卦」用到过期的时间。
    case digitalTime(moment: Date?, useSolar: Bool)
}

/// 一次起卦请求。用 `UUID` 当 id，同样的取数连着起两次也能重新呈现。
struct CastingRequest: Identifiable {
    let id = UUID()
    let intent: CastingIntent
}

/// 起卦仪式的盖层：静心 → 告神 → 成卦 → 揭卦，一个 `fullScreenCover` 里换幕。
/// 宿主用 `fullScreenCover(item:)` 呈现——用 `isPresented` 配独立的可选状态，
/// 盖层可能在状态传到之前就先建了内容，会白屏。
struct CastingActView: View {
    let intent: CastingIntent
    var onFinish: (CastResult) -> Void
    var onCancel: () -> Void

    @State private var didSettle = false
    /// 告神写完才有值；金钱卦与三数要带着它进取数那一幕。
    @State private var question: String?
    @State private var revealResult: CastResult?

    var body: some View {
        ZStack {
            if let result = revealResult {
                CastRevealView(result: result) {
                    onFinish(result)
                }
                .transition(.opacity)
            } else if let question {
                drawStage(question: question)
                    .transition(.opacity)
            } else if didSettle {
                InvocationView { asked in
                    confirm(question: asked)
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

    /// 告神之后的取数那一幕。时间卦不进这里——它的数在敬告那一刻就取完了。
    @ViewBuilder
    private func drawStage(question: String) -> some View {
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

    /// 金钱卦与三数告完神还要去取数；时间卦的数已经齐了，直接成卦揭卦。
    private func confirm(question asked: String) {
        switch intent {
        case .coin, .digitalNumbers:
            withAnimation(.easeInOut(duration: 0.35)) {
                question = asked
            }
        case .digitalTime(let moment, let useSolar):
            let date = moment ?? .now
            let components = useSolar
                ? LunarCalendarHelper.solarComponents(from: date)
                : LunarCalendarHelper.components(from: date)
            let result = DigitalCastingEngine.cast(
                yearBranch: components.yearBranch,
                month: components.month,
                day: components.day,
                hour: components.hourBranch,
                question: asked,
                at: date
            )
            reveal(result)
        }
    }

    private func reveal(_ result: CastResult) {
        withAnimation(.easeInOut(duration: 0.35)) {
            revealResult = result
        }
    }
}
