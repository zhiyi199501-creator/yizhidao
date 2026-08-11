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
            Group {
                if records.isEmpty {
                    ContentUnavailableView(
                        "暂无占问",
                        systemImage: "book.closed",
                        description: Text("起卦后会自动保存在这里")
                    )
                } else {
                    VStack(spacing: 0) {
                        Picker("浏览", selection: $browseMode) {
                            ForEach(BrowseMode.allCases) { mode in
                                Text(mode.rawValue).tag(mode)
                            }
                        }
                        .pickerStyle(.segmented)
                        .padding(.horizontal)
                        .padding(.vertical, 10)

                        switch browseMode {
                        case .timeline:
                            timelineList
                        case .byHexagram:
                            HexagramGroupListView(records: records, store: store)
                        }
                    }
                }
            }
            .navigationTitle("历史")
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
                    Button("删除", role: .destructive) {
                        modelContext.delete(record)
                        try? modelContext.save()
                    }
                }
            }
        }
        .scrollContentBackground(.hidden)
    }
}

extension ReadingRecord: Identifiable {}
