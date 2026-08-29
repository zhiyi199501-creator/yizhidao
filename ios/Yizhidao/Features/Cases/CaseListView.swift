import SwiftUI

/// 「案例」主页：按卦分组，呈现与历史页「按卦」一致。
struct CaseListView: View {
    private let hexStore = HexagramStore.shared
    @ObservedObject private var caseStore = CaseStore.shared

    private struct CaseGroup: Identifiable {
        var id: Int { number }
        let number: Int
        let cases: [CaseStudy]
    }

    private var groups: [CaseGroup] {
        let grouped = Dictionary(grouping: caseStore.cases, by: \.number)
        return grouped.keys.sorted().map { number in
            CaseGroup(number: number, cases: (grouped[number] ?? []).sorted { $0.file < $1.file })
        }
    }

    var body: some View {
        Group {
            if caseStore.cases.isEmpty {
                ContentUnavailableView(
                    "暂无案例",
                    systemImage: "books.vertical",
                    description: Text("案例数据未加载".zh)
                )
            } else {
                List(groups) { group in
                    NavigationLink {
                        CaseGroupDetailView(number: group.number, cases: group.cases)
                    } label: {
                        groupRow(group)
                    }
                }
                .scrollContentBackground(.hidden)
                .refreshable {
                    await caseStore.refresh()
                }
            }
        }
        .navigationTitle("案例".zh)
        .navigationBarTitleDisplayMode(.inline)
        .parchmentBackground()
        .task {
            await caseStore.refresh()
        }
    }

    private func groupRow(_ group: CaseGroup) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                if let hex = hexStore.hexagram(number: group.number) {
                    Text("\(hex.symbol) \(hex.name)".zh)
                        .font(.headline)
                } else {
                    Text("第\(group.number)卦".zh)
                        .font(.headline)
                }
                Spacer()
                Text("\(group.cases.count) 例".zh)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 2)
    }
}

/// 某卦的全部案例。参照历史「按卦」详情，不区分数字／金钱起卦，保留爻位筛选。
struct CaseGroupDetailView: View {
    let number: Int
    let cases: [CaseStudy]
    private let hexStore = HexagramStore.shared
    @State private var positionFilter: MovingPositionFilter = .all

    private var filteredCases: [CaseStudy] {
        cases.filter { positionFilter.matches(movingPositions: $0.movingPositions) }
    }

    private var title: String {
        if let hex = hexStore.hexagram(number: number) {
            return "\(hex.symbol) \(hex.name) · \(cases.count) 例"
        }
        return "第\(number)卦 · \(cases.count) 例"
    }

    var body: some View {
        VStack(spacing: 0) {
            filterChips
                .padding(.horizontal)
                .padding(.vertical, 10)
                .background(AppTheme.cardFill)

            if filteredCases.isEmpty {
                ContentUnavailableView(
                    "无匹配案例",
                    systemImage: "line.3.horizontal.decrease.circle",
                    description: Text("试试调整上方分类".zh)
                )
            } else {
                List(filteredCases) { study in
                    NavigationLink {
                        CaseDetailView(study: study)
                    } label: {
                        CaseRow(study: study, store: hexStore)
                    }
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
            }
        }
        .navigationTitle(title.zh)
        .navigationBarTitleDisplayMode(.inline)
        .parchmentBackground()
    }

    private var filterChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(MovingPositionFilter.allCases) { item in
                    let selected = item == positionFilter
                    Button {
                        positionFilter = item
                    } label: {
                        Text(item.label.zh)
                            .font(.caption.weight(.semibold))
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(
                                Capsule().fill(
                                    selected ? AppTheme.accent : Color.black.opacity(0.06)
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

/// 单个案例行。参照 `ReadingRecordRow`：本卦 ⟶ 之卦、所问、验证摘要；无时间、无应验徽章。
struct CaseRow: View {
    let study: CaseStudy
    let store: HexagramStore

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 6) {
                if let hex = store.hexagram(number: study.number) {
                    Text("\(hex.symbol) \(hex.name)".zh)
                        .font(.headline)
                        .lineLimit(1)
                } else {
                    Text("第\(study.number)卦".zh)
                        .font(.headline)
                        .lineLimit(1)
                }
                if let resulting = study.resultingNumber {
                    changeArrow
                    resultingTitle(number: resulting)
                        .lineLimit(1)
                } else if study.movingPositions.isEmpty {
                    Text("六爻不变".zh)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.secondary)
                } else {
                    Text("\(study.movingPositions.count) 爻变".zh)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.secondary)
                }
                Spacer(minLength: 0)
            }
            if !study.question.isEmpty {
                Text(study.question.zh)
                    .font(.subheadline)
                    .lineLimit(1)
            }
            if let summary = verificationSummary {
                Text(summary.zh)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
        }
        .padding(.vertical, 2)
    }

    private var movingLabel: String? {
        guard study.movingPositions.count == 1,
              let position = study.movingPositions.first,
              let label = MovingPositionFilter.from(position: position)?.label
        else { return nil }
        return label
    }

    private var changeArrow: some View {
        Text("⟶".zh)
            .font(.title2)
            .foregroundStyle(.secondary)
            .scaleEffect(x: 1.25, y: 1, anchor: .center)
            .frame(width: 28)
            .overlay(alignment: .top) {
                if let movingLabel {
                    Text(movingLabel.zh)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.red)
                        .offset(y: -1)
                }
            }
    }

    @ViewBuilder
    private func resultingTitle(number: Int) -> some View {
        if let hex = store.hexagram(number: number) {
            Text("\(hex.symbol) \(hex.name)".zh)
                .font(.headline)
        } else {
            Text("第\(number)卦".zh)
                .font(.headline)
        }
    }

    private var verificationSummary: String? {
        let value = study.verification.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty, value != "原文未提及" else { return nil }
        return value
    }
}

/// 案例详情：背景／所问／验证／讲师解读，其后本卦、之卦与历史结果页相同。
struct CaseDetailView: View {
    let study: CaseStudy
    private let hexStore = HexagramStore.shared

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                if !study.background.isEmpty {
                    section("背景") {
                        Text(study.background.zh).font(.body).lineSpacing(4)
                    }
                }
                if !study.question.isEmpty {
                    section("所问何事") {
                        Text(study.question.zh).font(.body).lineSpacing(4)
                    }
                }
                if !study.verification.isEmpty {
                    section("验证结果") {
                        Text(study.verification.zh).font(.body).lineSpacing(4)
                    }
                }
                if !study.explanation.isEmpty {
                    section("讲师解读") {
                        Text(study.explanation.zh).font(.body).lineSpacing(4)
                    }
                }
                HexagramReadingBody(
                    primaryNumber: study.number,
                    resultingNumber: study.resultingNumber,
                    lines: study.lines,
                    movingPositions: study.movingPositions
                )
            }
            .padding()
        }
        .navigationTitle(navigationTitle.zh)
        .navigationBarTitleDisplayMode(.inline)
        .parchmentBackground()
    }

    private var navigationTitle: String {
        if let hex = hexStore.hexagram(number: study.number) {
            return "\(hex.name)\(study.position)"
        }
        return study.position
    }

    private func section(_ title: String, @ViewBuilder content: () -> some View) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title.zh)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(AppTheme.accent)
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(RoundedRectangle(cornerRadius: 12).fill(AppTheme.cardFill))
    }
}
