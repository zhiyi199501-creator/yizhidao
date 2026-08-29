import SwiftUI

struct CastingHomeView: View {
    enum MethodTab: String, CaseIterable, Identifiable {
        case numbers = "输入三数"
        case time = "时间起卦"
        case coin = "金钱起卦"
        var id: String { rawValue }
    }

    @State private var methodTab: MethodTab = .numbers
    @State private var latestResult: CastResult?
    @State private var showResult = false
    @State private var request: CastingRequest?
    /// 仪式盖层收完再 push 结果页，避免和 `navigationDestination` 抢转场。
    @State private var pendingResultPush = false
    @Environment(AppNavigation.self) private var appNavigation

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 0) {
                Text("易玩家".zh)
                    .font(.largeTitle.weight(.bold))
                Text("君子居则观象玩辞，动则观变玩占".zh)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .padding(.top, 4)

                Picker("方法".zh, selection: $methodTab) {
                    ForEach(MethodTab.allCases) { tab in
                        Text(tab.rawValue.zh).tag(tab)
                    }
                }
                .pickerStyle(.segmented)
                .padding(.top, 26)

                Group {
                    switch methodTab {
                    case .numbers:
                        NumberCastView {
                            begin(.digitalNumbers)
                        }
                    case .time:
                        TimeCastView { intent in
                            begin(intent)
                        }
                    case .coin:
                        CoinCastView {
                            begin(.coin)
                        }
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            .padding(.horizontal, 24)
            .padding(.top, 8)
            .padding(.bottom, 28)
            .parchmentBackground(hidesTabBar: false)
            .navigationDestination(isPresented: $showResult) {
                if let latestResult {
                    ResultView(result: latestResult, isNew: true)
                }
            }
            .fullScreenCover(item: $request, onDismiss: finishAct) { request in
                CastingActView(intent: request.intent) { result in
                    latestResult = result
                    pendingResultPush = true
                    self.request = nil
                } onCancel: {
                    self.request = nil
                }
            }
            .onChange(of: appNavigation.dismissCastResultTick) { _, _ in
                pendingResultPush = false
                var transaction = Transaction()
                transaction.disablesAnimations = true
                withTransaction(transaction) {
                    request = nil
                    showResult = false
                }
            }
        }
    }

    private func begin(_ intent: CastingIntent) {
        pendingResultPush = false
        request = CastingRequest(intent: intent)
    }

    private func finishAct() {
        guard pendingResultPush else { return }
        pendingResultPush = false
        showResult = true
    }

}

/// 三种方法共用的起卦按钮，只有标题不同。
struct StartCastButton: View {
    let title: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title.zh)
                .font(.headline)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
        }
        .buttonStyle(.borderedProminent)
        .tint(AppTheme.accent)
    }
}
