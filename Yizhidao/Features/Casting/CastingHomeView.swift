import SwiftUI

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
