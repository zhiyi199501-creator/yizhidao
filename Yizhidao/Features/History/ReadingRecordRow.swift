import SwiftUI

struct ReadingRecordRow: View {
    let record: ReadingRecord
    let store: HexagramStore
    var showPrimaryTitle: Bool = true

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                if showPrimaryTitle {
                    HStack(spacing: 6) {
                        if let hex = store.hexagram(number: record.primaryNumber) {
                            Text("\(hex.symbol) \(hex.name)")
                                .font(.headline)
                                .lineLimit(1)
                        } else {
                            Text("第\(record.primaryNumber)卦")
                                .font(.headline)
                                .lineLimit(1)
                        }
                        if let resulting = record.resultingNumber {
                            changeArrow
                            resultingTitle(number: resulting)
                                .lineLimit(1)
                        }
                    }
                } else if let resulting = record.resultingNumber {
                    resultingTitle(number: resulting, prefix: "之卦 · ")
                } else if record.movingPositions.isEmpty {
                    Text("六爻不变")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.secondary)
                } else {
                    Text("\(record.movingPositions.count) 爻变")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Text(record.method.displayName)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
            HStack(spacing: 8) {
                Text(Self.timeString(record.createdAt))
                    .font(.caption)
                    .foregroundStyle(.secondary)
                if record.verificationStatus != .none {
                    Text(record.verificationStatus.displayName)
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(Self.verificationColor(record.verificationStatus), in: Capsule())
                }
            }
            if let question = record.question, !question.isEmpty {
                Text(question)
                    .font(.subheadline)
                    .lineLimit(1)
            }
            if let note = record.verificationNote, !note.isEmpty {
                Text(note)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
        }
        .padding(.vertical, 2)
    }

    /// 数字起卦单爻动时的动爻字（初…上）。
    private var digitalMovingLabel: String? {
        guard record.isDigitalMethod,
              record.movingPositions.count == 1,
              let position = record.movingPositions.first,
              let label = MovingPositionFilter.from(position: position)?.label
        else { return nil }
        return label
    }

    private var changeArrow: some View {
        Text("⟶")
            .font(.title3)
            .foregroundStyle(.secondary)
            .overlay(alignment: .top) {
                if let digitalMovingLabel {
                    Text(digitalMovingLabel)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.red)
                        .offset(y: -11)
                }
            }
    }

    @ViewBuilder
    private func resultingTitle(number: Int, prefix: String = "") -> some View {
        if let hex = store.hexagram(number: number) {
            Text("\(prefix)\(hex.symbol) \(hex.name)")
                .font(prefix.isEmpty ? .headline : .subheadline.weight(.semibold))
        } else {
            Text("\(prefix)第\(number)卦")
                .font(prefix.isEmpty ? .headline : .subheadline.weight(.semibold))
        }
    }

    static func timeString(_ date: Date) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "zh_CN")
        f.dateFormat = "yyyy/M/d HH:mm"
        return f.string(from: date)
    }

    static func verificationColor(_ status: VerificationStatus) -> Color {
        switch status {
        case .none:
            return .secondary
        case .fulfilled:
            return Color(red: 0.2, green: 0.55, blue: 0.35)
        case .partial:
            return Color(red: 0.75, green: 0.5, blue: 0.15)
        case .unfulfilled:
            return Color(red: 0.65, green: 0.25, blue: 0.25)
        }
    }

    static func verificationSummary(for records: [ReadingRecord]) -> String? {
        var fulfilled = 0
        var partial = 0
        var unfulfilled = 0
        for record in records {
            switch record.verificationStatus {
            case .none: break
            case .fulfilled: fulfilled += 1
            case .partial: partial += 1
            case .unfulfilled: unfulfilled += 1
            }
        }
        var parts: [String] = []
        if fulfilled > 0 { parts.append("应验 \(fulfilled)") }
        if partial > 0 { parts.append("部分 \(partial)") }
        if unfulfilled > 0 { parts.append("未应验 \(unfulfilled)") }
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
    }
}
