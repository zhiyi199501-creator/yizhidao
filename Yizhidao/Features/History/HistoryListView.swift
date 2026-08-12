import SwiftUI
import SwiftData

struct HistoryListView: View {
    private enum BrowseMode: String, CaseIterable, Identifiable {
        case timeline = "时间"
        case byHexagram = "按卦"
        var id: String { rawValue }
    }

    private enum StatusFilter: String, CaseIterable, Identifiable {
        case all = "全部状态"
        case none = "未验证"
        case fulfilled = "应验"
        case partial = "部分应验"
        case unfulfilled = "未应验"

        var id: String { rawValue }

        func matches(_ record: ReadingRecord) -> Bool {
            switch self {
            case .all: return true
            case .none: return record.verificationStatus == .none
            case .fulfilled: return record.verificationStatus == .fulfilled
            case .partial: return record.verificationStatus == .partial
            case .unfulfilled: return record.verificationStatus == .unfulfilled
            }
        }
    }

    @Environment(AppNavigation.self) private var appNavigation
    @Environment(\.modelContext) private var modelContext
    @Query(sort: \ReadingRecord.createdAt, order: .reverse)
    private var records: [ReadingRecord]
    @State private var browseMode: BrowseMode = .timeline
    @State private var statusFilter: StatusFilter = .all
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

                        if browseMode == .timeline {
                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 8) {
                                    ForEach(StatusFilter.allCases) { filter in
                                        let selected = statusFilter == filter
                                        Button {
                                            statusFilter = filter
                                        } label: {
                                            Text(filter.rawValue)
                                                .font(.caption.weight(.semibold))
                                                .padding(.horizontal, 10)
                                                .padding(.vertical, 6)
                                                .background(
                                                    Capsule().fill(
                                                        selected
                                                        ? AppTheme.accent
                                                        : Color.black.opacity(0.06)
                                                    )
                                                )
                                                .foregroundStyle(selected ? .white : .primary)
                                        }
                                        .buttonStyle(.plain)
                                    }
                                }
                            }
                        }
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
            ForEach(timelineFilteredRecords) { record in
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

    private var timelineFilteredRecords: [ReadingRecord] {
        records.filter { statusFilter.matches($0) }
    }
}

extension ReadingRecord: Identifiable {}
