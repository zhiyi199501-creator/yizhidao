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
        .onChange(of: appNavigation.pendingSimilar) { _, destination in
            guard let destination else { return }
            browseMode = .byHexagram
            path = NavigationPath()
            path.append(destination)
            appNavigation.pendingSimilar = nil
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
            }
            .onDelete(perform: deleteRecords)
        }
        .scrollContentBackground(.hidden)
    }

    private func deleteRecords(at offsets: IndexSet) {
        for index in offsets {
            modelContext.delete(records[index])
        }
        try? modelContext.save()
    }
}

extension ReadingRecord: Identifiable {}
