import SwiftUI
import SwiftData

enum HexagramMethodTab: String, CaseIterable, Identifiable, Hashable {
    case digital = "数字起卦"
    case coin = "金钱起卦"
    var id: String { rawValue }
}

struct HexagramGroupListView: View {
    let records: [ReadingRecord]
    let store: HexagramStore

    private struct Group: Identifiable {
        var id: Int { primaryNumber }
        let primaryNumber: Int
        let records: [ReadingRecord]
    }

    private var groups: [Group] {
        let grouped = Dictionary(grouping: records, by: \.primaryNumber)
        return grouped.keys.sorted().map { number in
            let items = (grouped[number] ?? []).sorted { $0.createdAt > $1.createdAt }
            return Group(primaryNumber: number, records: items)
        }
    }

    var body: some View {
        List(groups) { group in
            NavigationLink {
                HexagramGroupDetailView(primaryNumber: group.primaryNumber)
            } label: {
                groupRow(group)
            }
        }
        .scrollContentBackground(.hidden)
    }

    private func groupRow(_ group: Group) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                if let hex = store.hexagram(number: group.primaryNumber) {
                    Text("\(hex.symbol) \(hex.name)")
                        .font(.headline)
                } else {
                    Text("第\(group.primaryNumber)卦")
                        .font(.headline)
                }
                Spacer()
                Text("\(group.records.count) 次")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            HStack(spacing: 8) {
                if let latest = group.records.first {
                    Text(ReadingRecordRow.timeString(latest.createdAt))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                if let summary = ReadingRecordRow.verificationSummary(for: group.records) {
                    Text(summary)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }
        }
        .padding(.vertical, 2)
    }
}

struct HexagramGroupDetailView: View {
    let primaryNumber: Int

    @Environment(\.modelContext) private var modelContext
    @Query private var records: [ReadingRecord]
    @State private var methodTab: HexagramMethodTab
    @State private var positionFilter: MovingPositionFilter
    @State private var countFilter: MovingCountFilter

    private var store: HexagramStore { .shared }

    init(
        primaryNumber: Int,
        methodTab: HexagramMethodTab = .digital,
        positionFilter: MovingPositionFilter = .all,
        countFilter: MovingCountFilter = .all
    ) {
        self.primaryNumber = primaryNumber
        let number = primaryNumber
        _records = Query(
            filter: #Predicate<ReadingRecord> { record in
                record.primaryNumber == number
            },
            sort: [SortDescriptor(\.createdAt, order: .reverse)]
        )
        _methodTab = State(initialValue: methodTab)
        _positionFilter = State(initialValue: positionFilter)
        _countFilter = State(initialValue: countFilter)
    }

    init(destination: SimilarHexagramDestination) {
        self.init(
            primaryNumber: destination.primaryNumber,
            methodTab: destination.methodTab,
            positionFilter: destination.positionFilter,
            countFilter: destination.countFilter
        )
    }

    private var methodFilteredRecords: [ReadingRecord] {
        switch methodTab {
        case .digital:
            return records.filter(\.isDigitalMethod)
        case .coin:
            return records.filter(\.isCoinMethod)
        }
    }

    private var filteredRecords: [ReadingRecord] {
        switch methodTab {
        case .digital:
            return methodFilteredRecords.filter {
                positionFilter.matches(movingPositions: $0.movingPositions)
            }
        case .coin:
            return methodFilteredRecords.filter {
                countFilter.matches(movingCount: $0.movingPositions.count)
            }
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 10) {
                Picker("方法", selection: $methodTab) {
                    ForEach(HexagramMethodTab.allCases) { tab in
                        Text(tab.rawValue).tag(tab)
                    }
                }
                .pickerStyle(.segmented)

                classificationChips
            }
            .padding(.horizontal)
            .padding(.vertical, 10)
            .background(AppTheme.cardFill)

            if methodFilteredRecords.isEmpty {
                ContentUnavailableView(
                    emptyMethodTitle,
                    systemImage: "tray",
                    description: Text(emptyMethodDescription)
                )
            } else if filteredRecords.isEmpty {
                ContentUnavailableView(
                    "无匹配记录",
                    systemImage: "line.3.horizontal.decrease.circle",
                    description: Text("试试调整上方分类")
                )
            } else {
                List {
                    ForEach(filteredRecords) { record in
                        NavigationLink {
                            ResultView(record: record, showSimilarHexagramButton: false)
                        } label: {
                            ReadingRecordRow(
                                record: record,
                                store: store,
                                showPrimaryTitle: true
                            )
                        }
                        .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                            Button(role: .destructive) {
                                HistoryTrashStore.archive(record)
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
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
            }
        }
        .navigationTitle(navigationTitle)
        .navigationBarTitleDisplayMode(.inline)
        .parchmentBackground()
    }

    @ViewBuilder
    private var classificationChips: some View {
        switch methodTab {
        case .digital:
            filterChips(
                items: MovingPositionFilter.allCases.map { ($0.id, $0.label) },
                selection: positionFilter.id
            ) { id in
                if let value = MovingPositionFilter.allCases.first(where: { $0.id == id }) {
                    positionFilter = value
                }
            }
        case .coin:
            filterChips(
                items: MovingCountFilter.allCases.map { ($0.id, $0.label) },
                selection: countFilter.id
            ) { id in
                if let value = MovingCountFilter.allCases.first(where: { $0.id == id }) {
                    countFilter = value
                }
            }
        }
    }

    private var emptyMethodTitle: String {
        switch methodTab {
        case .digital: return "暂无数字起卦"
        case .coin: return "暂无金钱卦"
        }
    }

    private var emptyMethodDescription: String {
        switch methodTab {
        case .digital: return "该卦尚无数字或时间起卦记录"
        case .coin: return "该卦尚无金钱卦记录"
        }
    }

    private var navigationTitle: String {
        if let hex = store.hexagram(number: primaryNumber) {
            return "\(hex.symbol) \(hex.name) · \(records.count) 次"
        }
        return "第\(primaryNumber)卦 · \(records.count) 次"
    }

    private func filterChips(
        items: [(String, String)],
        selection: String,
        onSelect: @escaping (String) -> Void
    ) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(items, id: \.0) { item in
                    let selected = item.0 == selection
                    Button {
                        onSelect(item.0)
                    } label: {
                        Text(item.1)
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

enum MovingCountFilter: String, CaseIterable, Identifiable, Hashable {
    case all
    case zero
    case one
    case two
    case three
    case four
    case five
    case six

    var id: String { rawValue }

    var label: String {
        switch self {
        case .all: return "全部"
        case .zero: return "0 动"
        case .one: return "1 动"
        case .two: return "2 动"
        case .three: return "3 动"
        case .four: return "4 动"
        case .five: return "5 动"
        case .six: return "6 动"
        }
    }

    func matches(movingCount: Int) -> Bool {
        switch self {
        case .all: return true
        case .zero: return movingCount == 0
        case .one: return movingCount == 1
        case .two: return movingCount == 2
        case .three: return movingCount == 3
        case .four: return movingCount == 4
        case .five: return movingCount == 5
        case .six: return movingCount == 6
        }
    }

    static func from(movingCount: Int) -> MovingCountFilter {
        switch movingCount {
        case 0: return .zero
        case 1: return .one
        case 2: return .two
        case 3: return .three
        case 4: return .four
        case 5: return .five
        case 6: return .six
        default: return .all
        }
    }
}

enum MovingPositionFilter: CaseIterable, Identifiable, Hashable {
    case all
    case chu
    case er
    case san
    case si
    case wu
    case shang

    var id: String {
        switch self {
        case .all: return "all"
        case .chu: return "1"
        case .er: return "2"
        case .san: return "3"
        case .si: return "4"
        case .wu: return "5"
        case .shang: return "6"
        }
    }

    var label: String {
        switch self {
        case .all: return "全部"
        case .chu: return "初"
        case .er: return "二"
        case .san: return "三"
        case .si: return "四"
        case .wu: return "五"
        case .shang: return "上"
        }
    }

    private var position: Int? {
        switch self {
        case .all: return nil
        case .chu: return 1
        case .er: return 2
        case .san: return 3
        case .si: return 4
        case .wu: return 5
        case .shang: return 6
        }
    }

    func matches(movingPositions: [Int]) -> Bool {
        guard let position else { return true }
        return movingPositions.contains(position)
    }

    static func from(position: Int) -> MovingPositionFilter? {
        switch position {
        case 1: return .chu
        case 2: return .er
        case 3: return .san
        case 4: return .si
        case 5: return .wu
        case 6: return .shang
        default: return nil
        }
    }
}

extension ReadingRecord {
    var isDigitalMethod: Bool {
        switch method {
        case .digitalManual, .digitalTime: return true
        case .coin: return false
        }
    }

    var isCoinMethod: Bool {
        method == .coin
    }
}
