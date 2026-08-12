import SwiftUI
import SwiftData

struct HistoryListView: View {
    private enum BrowseMode: String, CaseIterable, Identifiable {
        case timeline = "时间"
        case byHexagram = "按卦"
        var id: String { rawValue }
    }

    @Environment(AppNavigation.self) private var appNavigation
    @Environment(\.modelContext) private var modelContext
    @Query(sort: \ReadingRecord.createdAt, order: .reverse)
    private var records: [ReadingRecord]
    @State private var browseMode: BrowseMode = .timeline
    @State private var path = NavigationPath()

    private var store: HexagramStore { .shared }

    var body: some View {
        NavigationStack(path: $path) {
            VStack(alignment: .leading, spacing: 0) {
                VStack(alignment: .leading, spacing: 20) {
                    Text("历史")
                        .font(.largeTitle.weight(.bold))

                    if !records.isEmpty {
                        Picker("浏览", selection: $browseMode) {
                            ForEach(BrowseMode.allCases) { mode in
                                Text(mode.rawValue).tag(mode)
                            }
                        }
                        .pickerStyle(.segmented)
                    }
                }
                .padding()

                if records.isEmpty {
                    Spacer(minLength: 0)
                    ContentUnavailableView(
                        "暂无占问",
                        systemImage: "book.closed",
                        description: Text("起卦后会自动保存在这里")
                    )
                    Spacer(minLength: 0)
                } else {
                    switch browseMode {
                    case .timeline:
                        timelineList
                    case .byHexagram:
                        HexagramGroupListView(records: records, store: store)
                    }
                }
            }
            .parchmentBackground()
            .navigationDestination(for: SimilarHexagramDestination.self) { destination in
                HexagramGroupDetailView(destination: destination)
            }
        }
        .onChange(of: appNavigation.similarJumpTick) { _, _ in
            guard let destination = appNavigation.pendingSimilar else { return }
            browseMode = .byHexagram
            // 先无动画清栈，再以默认 push 动画进入同卦页（接近时间线进结果）。
            var clearTransaction = Transaction()
            clearTransaction.disablesAnimations = true
            withTransaction(clearTransaction) {
                path = NavigationPath()
            }
            DispatchQueue.main.async {
                path.append(destination)
                appNavigation.pendingSimilar = nil
            }
        }
    }

    private var timelineList: some View {
        List {
            ForEach(records) { record in
                NavigationLink {
                    ResultView(record: record)
                } label: {
                    ReadingRecordRow(record: record, store: store, showPrimaryTitle: true)
                }
                .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                    Button(role: .destructive) {
                        modelContext.delete(record)
                        try? modelContext.save()
                    } label: {
                        Image(systemName: "trash.fill")
                    }
                    .tint(.red)
                    .accessibilityLabel("删除")
                }
            }
        }
        .scrollContentBackground(.hidden)
    }
}

extension ReadingRecord: Identifiable {}
