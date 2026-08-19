import SwiftUI

struct ReadingRecordRow: View {
    let record: ReadingRecord
    let store: HexagramStore
    var showPrimaryTitle: Bool = true

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 6) {
                if showPrimaryTitle {
                    if let hex = store.hexagram(number: record.primaryNumber) {
                        Text("\(hex.symbol) \(hex.name)".zh)
                            .font(.headline)
                            .lineLimit(1)
                    } else {
                        Text("第\(record.primaryNumber)卦".zh)
                            .font(.headline)
                            .lineLimit(1)
                    }
                    if let resulting = record.resultingNumber {
                        changeArrow
                        resultingTitle(number: resulting)
                            .lineLimit(1)
                    }
                    verificationBadge
                } else if let resulting = record.resultingNumber {
                    resultingTitle(number: resulting, prefix: "之卦 · ")
                    verificationBadge
                } else if record.movingPositions.isEmpty {
                    Text("六爻不变".zh)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.secondary)
                    verificationBadge
                } else {
                    Text("\(record.movingPositions.count) 爻变".zh)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.secondary)
                    verificationBadge
                }
                Spacer(minLength: 0)
            }
            Text(Self.timeString(record.createdAt).zh)
                .font(.caption)
                .foregroundStyle(.secondary)
            if let question = record.question, !question.isEmpty {
                Text(question.zh)
                    .font(.subheadline)
                    .lineLimit(1)
            }
            if let note = record.verificationNote, !note.isEmpty {
                Text(note.zh)
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

    @ViewBuilder
    private var verificationBadge: some View {
        if record.verificationStatus != .none {
            Text(record.verificationStatus.displayName.zh)
                .font(.caption2.weight(.semibold))
                .foregroundStyle(.white)
                .padding(.horizontal, 6)
                .padding(.vertical, 2)
                .background(Self.verificationColor(record.verificationStatus), in: Capsule())
        }
    }

    private var changeArrow: some View {
        Text("⟶".zh)
            .font(.title2)
            .foregroundStyle(.secondary)
            .scaleEffect(x: 1.25, y: 1, anchor: .center)
            .frame(width: 28)
            .overlay(alignment: .top) {
                if let digitalMovingLabel {
                    Text(digitalMovingLabel.zh)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.red)
                        .offset(y: -1)
                }
            }
    }

    @ViewBuilder
    private func resultingTitle(number: Int, prefix: String = "") -> some View {
        if let hex = store.hexagram(number: number) {
            Text("\(prefix)\(hex.symbol) \(hex.name)".zh)
                .font(prefix.isEmpty ? .headline : .subheadline.weight(.semibold))
        } else {
            Text("\(prefix)第\(number)卦".zh)
                .font(prefix.isEmpty ? .headline : .subheadline.weight(.semibold))
        }
    }

    static func timeString(_ date: Date) -> String {
        let f = DateFormatter()
        f.locale = AppLanguage.current.locale
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
