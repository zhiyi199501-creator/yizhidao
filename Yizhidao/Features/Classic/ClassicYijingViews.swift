import SwiftUI

struct YijingIntroListView: View {
    private let store = YijingIntroStore.shared

    var body: some View {
        List {
            if !store.note.isEmpty {
                Section {
                    Text(store.note.zh)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .listRowBackground(Color.clear)
                }
            }
            Section {
                ForEach(store.chapters) { chapter in
                    NavigationLink {
                        YijingIntroChapterView(chapter: chapter)
                    } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(chapter.title.zh)
                                .font(.headline)
                            Text(chapter.subtitle.zh)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            } footer: {
                Text(store.source.zh)
            }
        }
        .scrollContentBackground(.hidden)
        .navigationTitle("易经基础入门".zh)
        .navigationBarTitleDisplayMode(.inline)
        .parchmentBackground()
    }
}

struct YijingIntroChapterView: View {
    let chapter: YijingIntroChapter

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text(chapter.subtitle.zh)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                ForEach(Array(chapter.paragraphs.enumerated()), id: \.offset) { _, paragraph in
                    Text(paragraph.zh)
                        .font(.body)
                        .lineSpacing(6)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding()
                        .background(RoundedRectangle(cornerRadius: 12).fill(AppTheme.cardFill))
                }
            }
            .padding()
        }
        .navigationTitle(chapter.title.zh)
        .navigationBarTitleDisplayMode(.inline)
        .parchmentBackground()
    }
}

struct ClassicHexagramListView: View {
    private let store = HexagramStore.shared

    var body: some View {
        List {
            ForEach(["上经", "下经"], id: \.self) { part in
                Section(part.zh) {
                    ForEach(store.hexagrams.filter { $0.part == part }) { hex in
                        NavigationLink {
                            ClassicHexagramDetailView(hexagram: hex)
                        } label: {
                            HStack {
                                Text("\(hex.symbol) \(hex.name)".zh)
                                    .font(.headline)
                                Spacer()
                                Text(hex.title.zh)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                }
            }
        }
        .scrollContentBackground(.hidden)
        .navigationTitle("易经六十四卦".zh)
        .navigationBarTitleDisplayMode(.inline)
        .parchmentBackground()
    }
}

struct ClassicHexagramDetailView: View {
    let hexagram: Hexagram

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack(alignment: .top, spacing: 16) {
                    ScaledHexagramFigureView(
                        lines: hexagram.figureLines,
                        movingPositions: []
                    )
                    VStack(alignment: .leading, spacing: 6) {
                        Text(hexagram.title.zh)
                            .font(.title3.weight(.semibold))
                        Text(hexagram.figure.zh)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                    Spacer(minLength: 0)
                }
                .padding()
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(RoundedRectangle(cornerRadius: 12).fill(AppTheme.cardFill))

                card("卦辞") {
                    Text(hexagram.guaci.zh)
                }
                card("彖辞") {
                    Text(prefixed("彖曰：", hexagram.tuanci).zh)
                }
                card("大象") {
                    Text(prefixed("象曰：", hexagram.daxiang).zh)
                }
                ForEach(Array(zip(hexagram.yaoci, hexagram.xiaoxiang).enumerated()), id: \.offset) { _, pair in
                    card {
                        Text(pair.0.zh)
                        Text("象曰：\(pair.1)".zh)
                    }
                }
                if let yong = hexagram.yong {
                    card {
                        Text(yong.ci.zh)
                        Text("象曰：\(yong.xiang)".zh)
                    }
                }
                if !hexagram.wenyan.isEmpty {
                    card("文言") {
                        ForEach(Array(hexagram.wenyan.enumerated()), id: \.offset) { _, paragraph in
                            Text(paragraph.zh)
                        }
                    }
                }
            }
            .padding()
        }
        .navigationTitle(hexagram.name.zh)
        .navigationBarTitleDisplayMode(.inline)
        .parchmentBackground()
    }

    private func card(_ title: String? = nil, @ViewBuilder content: () -> some View) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            if let title {
                Text(title.zh)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(AppTheme.accent)
            }
            content()
                .font(.body)
                .lineSpacing(4)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(RoundedRectangle(cornerRadius: 12).fill(AppTheme.cardFill))
    }
}

struct ClassicWingListView: View {
    private let store = HexagramStore.shared

    var body: some View {
        List(store.wings) { wing in
            NavigationLink {
                if wing.chapters.count == 1 {
                    ClassicChapterDetailView(wingTitle: wing.title, chapter: wing.chapters[0])
                } else {
                    ClassicChapterListView(wing: wing)
                }
            } label: {
                VStack(alignment: .leading, spacing: 2) {
                    Text(wing.title.zh)
                        .font(.headline)
                    Text(chapterSummary(wing).zh)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .scrollContentBackground(.hidden)
        .navigationTitle("易经四传".zh)
        .navigationBarTitleDisplayMode(.inline)
        .parchmentBackground()
    }

    private func chapterSummary(_ wing: HexagramWing) -> String {
        if wing.chapters.count == 1 {
            return "\(wing.chapters[0].paragraphs.count) 节"
        }
        return "\(wing.chapters.count) 章"
    }
}

struct ClassicChapterListView: View {
    let wing: HexagramWing

    var body: some View {
        List(wing.chapters) { chapter in
            NavigationLink {
                ClassicChapterDetailView(wingTitle: wing.title, chapter: chapter)
            } label: {
                Text(chapter.title.zh)
            }
        }
        .scrollContentBackground(.hidden)
        .navigationTitle(wing.title.zh)
        .navigationBarTitleDisplayMode(.inline)
        .parchmentBackground()
    }
}

struct ClassicChapterDetailView: View {
    let wingTitle: String
    let chapter: HexagramWingChapter

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                ForEach(Array(chapter.paragraphs.enumerated()), id: \.offset) { _, paragraph in
                    Text(paragraph.zh)
                        .font(.body)
                        .lineSpacing(6)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding()
                        .background(RoundedRectangle(cornerRadius: 12).fill(AppTheme.cardFill))
                }
            }
            .padding()
        }
        .navigationTitle(chapter.title == wingTitle ? wingTitle : chapter.title.zh)
        .navigationBarTitleDisplayMode(.inline)
        .parchmentBackground()
    }
}

struct ZhengshiListView: View {
    private let store = ZhengshiStore.shared

    var body: some View {
        List {
            Section {
                Text(store.note.zh)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .listRowBackground(Color.clear)
            } footer: {
                Text(store.source.zh)
            }
            ForEach(store.parts) { part in
                Section(part.title.zh) {
                    ForEach(part.chapters) { chapter in
                        NavigationLink {
                            ZhengshiChapterView(chapter: chapter)
                        } label: {
                            HStack {
                                if !chapter.symbol.isEmpty {
                                    Text(chapter.symbol.zh)
                                        .font(.headline)
                                }
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(chapter.title.zh)
                                        .font(.headline)
                                    if !chapter.subtitle.isEmpty {
                                        Text(chapter.subtitle.zh)
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                            .lineLimit(1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        .scrollContentBackground(.hidden)
        .navigationTitle("易经证释".zh)
        .navigationBarTitleDisplayMode(.inline)
        .parchmentBackground()
    }
}

struct ZhengshiChapterView: View {
    let chapter: ZhengshiChapter

    var body: some View {
        if chapter.sections.count == 1, let only = chapter.sections.first {
            ZhengshiSectionView(title: chapter.title, paragraphs: only.paragraphs)
        } else {
            List(chapter.sections) { section in
                NavigationLink {
                    ZhengshiSectionView(title: section.title, paragraphs: section.paragraphs)
                } label: {
                    Text(section.title.zh)
                }
            }
            .scrollContentBackground(.hidden)
            .navigationTitle(chapter.title.zh)
            .navigationBarTitleDisplayMode(.inline)
            .parchmentBackground()
        }
    }
}

struct ZhengshiSectionView: View {
    let title: String
    let paragraphs: [String]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                ForEach(Array(paragraphs.enumerated()), id: \.offset) { _, paragraph in
                    Text(paragraph.zh)
                        .font(.body)
                        .lineSpacing(6)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding()
                        .background(RoundedRectangle(cornerRadius: 12).fill(AppTheme.cardFill))
                }
            }
            .padding()
        }
        .navigationTitle(title.zh)
        .navigationBarTitleDisplayMode(.inline)
        .parchmentBackground()
    }
}

private func prefixed(_ prefix: String, _ body: String) -> String {
    let text = body.trimmingCharacters(in: .whitespacesAndNewlines)
    if text.hasPrefix(prefix) { return text }
    let bare = String(prefix.dropLast())
    if text.hasPrefix(bare) {
        let rest = text.dropFirst(bare.count).trimmingCharacters(in: .whitespaces)
        if rest.hasPrefix("：") || rest.hasPrefix(":") {
            return bare + rest
        }
        return prefix + rest
    }
    return prefix + text
}
