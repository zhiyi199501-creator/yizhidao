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

                    Text(entry.answer.zh)
                        .font(.body)
                        .lineSpacing(6)
                        .frame(maxWidth: .infinity, alignment: .leading)
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
    }
}
