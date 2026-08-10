import SwiftUI
import SwiftData

struct HistoryListView: View {
    @Query(sort: \ReadingRecord.createdAt, order: .reverse)
    private var records: [ReadingRecord]

    private var store: HexagramStore { .shared }

    var body: some View {
        NavigationStack {
            Group {
                if records.isEmpty {
                    ContentUnavailableView(
                        "暂无占问",
                        systemImage: "book.closed",
                        description: Text("起卦后会自动保存在这里")
                    )
                } else {
                    List(records) { record in
                        NavigationLink {
                            ResultView(result: record.toCastResult(), isNew: false)
                        } label: {
                            HistoryRow(record: record, store: store)
                        }
                    }
                }
            }
            .navigationTitle("历史")
        }
    }
}

private struct HistoryRow: View {
    let record: ReadingRecord
    let store: HexagramStore

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                if let hex = store.hexagram(number: record.primaryNumber) {
                    Text("\(hex.symbol) \(hex.name)")
                        .font(.headline)
                } else {
                    Text("第\(record.primaryNumber)卦")
                        .font(.headline)
                }
                Spacer()
                Text(record.method.displayName)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
            if let question = record.question, !question.isEmpty {
                Text(question)
                    .font(.subheadline)
                    .lineLimit(1)
            }
            Text(timeString(record.createdAt))
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .padding(.vertical, 2)
    }

    private func timeString(_ date: Date) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "zh_CN")
        f.dateFormat = "yyyy/M/d HH:mm"
        return f.string(from: date)
    }
}

extension ReadingRecord: Identifiable {}
