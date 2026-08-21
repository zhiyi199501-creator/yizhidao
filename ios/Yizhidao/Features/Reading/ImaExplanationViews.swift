import SwiftUI

struct TappableScripture<Content: View>: View {
    let explanationId: String
    @Binding var selection: ImaExplanationSelection?
    private let content: () -> Content

    init(
        explanationId: String,
        selection: Binding<ImaExplanationSelection?>,
        @ViewBuilder content: @escaping () -> Content
    ) {
        self.explanationId = explanationId
        self._selection = selection
        self.content = content
    }

    private var store: ImaExplanationStore { .shared }

    var body: some View {
        if let entry = store.explanation(for: explanationId) {
            Button {
                selection = ImaExplanationSelection(id: explanationId, entry: entry)
            } label: {
                content()
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.trailing, 18)
                    .overlay(alignment: .topTrailing) {
                        Image(systemName: "text.book.closed")
                            .font(.caption)
                            .foregroundStyle(AppTheme.accent.opacity(0.75))
                    }
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityHint("查看讲解".zh)
        } else {
            content()
        }
    }
}

struct ImaExplanationSheet: View {
    let entry: ImaExplanationEntry
    let source: String
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text(entry.scripture.zh)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .lineSpacing(4)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding()
                        .background(RoundedRectangle(cornerRadius: 12).fill(AppTheme.cardFill))

                    ImaAnswerBody(text: entry.answer)
                }
                .padding()
            }
            .navigationTitle(entry.title.zh)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭".zh) { dismiss() }
                }
            }
            .parchmentBackground()
            .safeAreaInset(edge: .bottom) {
                Text(source.zh)
                    .font(.caption2)
                    .foregroundStyle(.tertiary)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
            }
        }
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        .presentationBackground {
            AppTheme.parchmentGradient.ignoresSafeArea()
        }
    }
}

private struct ImaAnswerBody: View {
    let text: String

    private var blocks: [ImaAnswerBlock] {
        ImaAnswerFormatter.blocks(in: text)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            ForEach(Array(blocks.enumerated()), id: \.offset) { _, block in
                switch block {
                case .text(let paragraph):
                    Text(paragraph.zh)
                        .font(.body)
                        .lineSpacing(6)
                        .frame(maxWidth: .infinity, alignment: .leading)
                case .table(let rows):
                    ImaAnswerTable(rows: rows)
                }
            }
        }
    }
}

private struct ImaAnswerTable: View {
    let rows: [[String]]

    private var columnCount: Int { rows.first?.count ?? 0 }
    private var compactFirst: Bool {
        if columnCount == 2 { return true }
        guard columnCount >= 3 else { return false }
        return rows.dropFirst().allSatisfy { ($0.first ?? "").count <= 4 }
    }
    private var firstColumnWidth: CGFloat { columnCount == 2 ? 96 : 52 }
    private var cellPadding: CGFloat { columnCount >= 4 ? 8 : 10 }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ForEach(Array(rows.enumerated()), id: \.offset) { rowIndex, row in
                HStack(alignment: .top, spacing: 0) {
                    ForEach(Array(row.enumerated()), id: \.offset) { colIndex, cell in
                        if colIndex > 0 {
                            Divider()
                        }
                        cellView(cell, isHeader: rowIndex == 0, isFirstColumn: colIndex == 0)
                            .frame(
                                width: compactFirst && colIndex == 0 ? firstColumnWidth : nil,
                                alignment: .topLeading
                            )
                            .frame(
                                maxWidth: compactFirst && colIndex == 0 ? nil : .infinity,
                                alignment: .topLeading
                            )
                    }
                }
                .background(rowIndex == 0 ? AppTheme.accent.opacity(0.08) : AppTheme.cardFill)
                if rowIndex < rows.count - 1 {
                    Divider()
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(AppTheme.fieldStroke, lineWidth: 0.6)
        )
    }

    private func cellView(_ text: String, isHeader: Bool, isFirstColumn: Bool) -> some View {
        Text(text.zh)
            .font(.footnote.weight(isHeader || isFirstColumn ? .semibold : .regular))
            .foregroundStyle(isHeader ? AppTheme.accent : Color.primary)
            .lineSpacing(3)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.horizontal, cellPadding)
            .padding(.vertical, 8)
    }
}
