import SwiftUI

struct CastingHomeView: View {
    enum MethodTab: String, CaseIterable, Identifiable {
        case digital = "数字起卦"
        case coin = "金钱起卦"
        var id: String { rawValue }
    }

    @State private var methodTab: MethodTab = .digital
    @State private var question = ""
    @State private var latestResult: CastResult?
    @State private var showResult = false
    @State private var showRitual = false
    @Environment(AppNavigation.self) private var appNavigation

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Text("易玩家".zh)
                        .font(.largeTitle.weight(.bold))
                    Text("君子居则观象玩辞，动则观变玩占".zh)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)

                    castingRitualSection

                    HStack(alignment: .top, spacing: 6) {
                        TextField("所问何事（必填）", text: $question, axis: .vertical)
                            .lineLimit(2...5)
                        if !question.isEmpty {
                            Button {
                                question = ""
                            } label: {
                                Image(systemName: "xmark.circle.fill")
                                    .font(.body)
                                    .symbolRenderingMode(.hierarchical)
                                    .foregroundStyle(.secondary)
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel("清除所问".zh)
                        }
                    }
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

                    Picker("方法".zh, selection: $methodTab) {
                        ForEach(MethodTab.allCases) { tab in
                            Text(tab.rawValue.zh).tag(tab)
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
                            .fill(AppTheme.cardFill)
                    )
                }
                .padding()
            }
            .scrollDismissesKeyboard(.interactively)
            .parchmentBackground(hidesTabBar: false)
            .navigationDestination(isPresented: $showResult) {
                if let latestResult {
                    ResultView(result: latestResult, isNew: true)
                }
            }
            .onChange(of: appNavigation.dismissCastResultTick) { _, _ in
                var transaction = Transaction()
                transaction.disablesAnimations = true
                withTransaction(transaction) {
                    showResult = false
                }
            }
        }
    }

    private var castingRitualSection: some View {
        DisclosureGroup(isExpanded: $showRitual) {
            VStack(alignment: .leading, spacing: 10) {
                ForEach(Array(Self.ritualSteps.enumerated()), id: \.offset) { index, step in
                    Text("\(index + 1)、\(step)".zh)
                        .font(.footnote)
                        .foregroundStyle(.primary.opacity(0.85))
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            .padding(.top, 8)
        } label: {
            Text("起卦礼仪".zh)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(AppTheme.accent)
        }
        .tint(AppTheme.accent)
    }

    private static let ritualSteps: [String] = [
        "净手，择一静处，坐稳，桌面整洁无杂物。",
        "静穆身心，敬慎其意。",
        "行礼，默祷：爻变化之神在上，弟子某某某，今有某事（简单扼要讲清楚）不知休咎，望示一圣卦指示。",
        "得卦后，行礼：感谢爻变化之神的指示，弟子退。",
        "然后把起卦工具收好，开始解卦。",
    ]
}
