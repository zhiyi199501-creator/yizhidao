import SwiftUI

private let introChapterMarks = ["一", "二", "三", "四", "五", "六", "七", "八", "九"]

private func introChapterMark(_ index: Int) -> String {
    introChapterMarks.indices.contains(index) ? introChapterMarks[index] : "\(index + 1)"
}

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
                ForEach(Array(store.chapters.enumerated()), id: \.element.id) { index, chapter in
                    NavigationLink {
                        YijingIntroChapterView(chapters: store.chapters, startIndex: index)
                    } label: {
                        HStack(alignment: .firstTextBaseline, spacing: 10) {
                            Text(introChapterMark(index).zh)
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(AppTheme.accent)
                                .frame(width: 22, alignment: .leading)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(chapter.title.zh)
                                    .font(.headline)
                                Text(chapter.subtitle.zh)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                }
            } footer: {
                if !store.source.isEmpty {
                    Text(store.source.zh)
                }
            }
        }
        .scrollContentBackground(.hidden)
        .navigationTitle("基础入门".ui("Primer"))
        .navigationBarTitleDisplayMode(.inline)
        .parchmentBackground()
    }
}

struct YijingIntroChapterView: View {
    let chapters: [YijingIntroChapter]
    @State private var index: Int

    init(chapters: [YijingIntroChapter], startIndex: Int) {
        self.chapters = chapters
        _index = State(initialValue: startIndex)
    }

    private var chapter: YijingIntroChapter { chapters[index] }

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: 22) {
                    Color.clear.frame(height: 0).id("top")
                    if !chapter.subtitle.isEmpty {
                        Text(chapter.subtitle.zh)
                            .font(.title3.weight(.semibold))
                            .foregroundStyle(.primary)
                    }
                    ForEach(Array(chapter.blocks.enumerated()), id: \.offset) { _, block in
                        introBlockView(block)
                    }
                    if index > 0 || index + 1 < chapters.count {
                        HStack(spacing: 10) {
                            if index > 0 {
                                introChapterJump(
                                    label: "上一章".ui("Previous"),
                                    title: chapters[index - 1].title,
                                    leadingChevron: true
                                ) {
                                    index -= 1
                                }
                            }
                            if index + 1 < chapters.count {
                                introChapterJump(
                                    label: "下一章".ui("Next"),
                                    title: chapters[index + 1].title,
                                    leadingChevron: false
                                ) {
                                    index += 1
                                }
                            }
                        }
                    }
                }
                .padding()
            }
            .onChange(of: index) { _, _ in
                proxy.scrollTo("top", anchor: .top)
            }
        }
        .navigationTitle(chapter.title.zh)
        .navigationBarTitleDisplayMode(.inline)
        .parchmentBackground()
    }

    private func introChapterJump(
        label: String,
        title: String,
        leadingChevron: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 6) {
                if leadingChevron {
                    Image(systemName: "chevron.left")
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(.tertiary)
                }
                VStack(alignment: leadingChevron ? .leading : .trailing, spacing: 3) {
                    Text(label.zh)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(AppTheme.accent)
                    Text(title.zh)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: leadingChevron ? .leading : .trailing)
                if !leadingChevron {
                    Image(systemName: "chevron.right")
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(.tertiary)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity, minHeight: 56, alignment: .center)
            .background(RoundedRectangle(cornerRadius: 12).fill(AppTheme.cardFill))
        }
        .buttonStyle(.plain)
        .frame(maxWidth: .infinity)
    }

    @ViewBuilder
    private func introBlockView(_ block: YijingIntroBlock) -> some View {
        switch block {
        case .paragraph(let text):
            Text(text.zh)
                .font(.body)
                .lineSpacing(8)
                .frame(maxWidth: .infinity, alignment: .leading)
        case .quote(let text, let cite):
            IntroQuoteView(text: text, cite: cite)
        case .list(let items):
            VStack(alignment: .leading, spacing: 10) {
                ForEach(Array(items.enumerated()), id: \.offset) { itemIndex, item in
                    HStack(alignment: .top, spacing: 8) {
                        Text("\(itemIndex + 1).")
                            .font(.body.weight(.semibold))
                            .foregroundStyle(AppTheme.accent)
                            .frame(width: 22, alignment: .leading)
                        Text(item.zh)
                            .font(.body)
                            .lineSpacing(6)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
            }
        case .table(let rows):
            IntroTableView(rows: rows)
        case .figure(let kind, let caption):
            IntroFigureView(kind: kind, caption: caption)
        case .links(let links):
            VStack(spacing: 0) {
                ForEach(Array(links.enumerated()), id: \.offset) { linkIndex, link in
                    introLinkRow(link)
                    if linkIndex < links.count - 1 {
                        Divider().padding(.leading, 16)
                    }
                }
            }
            .background(RoundedRectangle(cornerRadius: 12).fill(AppTheme.cardFill))
        }
    }

    @ViewBuilder
    private func introLinkRow(_ link: YijingIntroLink) -> some View {
        let label = HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(link.title.zh)
                    .font(.headline)
                if !link.subtitle.isEmpty {
                    Text(link.subtitle.zh)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            Spacer()
            Image(systemName: "chevron.right")
                .font(.footnote.weight(.semibold))
                .foregroundStyle(.tertiary)
        }
        .padding()
        .contentShape(Rectangle())

        switch link.route {
        case "hexagrams":
            NavigationLink { ClassicHexagramListView() } label: { label }.buttonStyle(.plain)
        case "cases":
            NavigationLink { CaseListView() } label: { label }.buttonStyle(.plain)
        case "wings":
            NavigationLink { ClassicWingListView() } label: { label }.buttonStyle(.plain)
        default:
            label
        }
    }
}

private struct IntroQuoteView: View {
    let text: String
    let cite: String

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            RoundedRectangle(cornerRadius: 1)
                .fill(AppTheme.accent)
                .frame(width: 3)
                .frame(maxHeight: .infinity)
            VStack(alignment: .leading, spacing: 8) {
                Text(text.zh)
                    .font(.body)
                    .lineSpacing(7)
                if !cite.isEmpty {
                    Text(cite.zh)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .fixedSize(horizontal: false, vertical: true)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct IntroTableView: View {
    let rows: [[String]]

    private var columnCount: Int { rows.first?.count ?? 0 }
    private var firstColumnWidth: CGFloat { columnCount == 2 ? 72 : 96 }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ForEach(Array(rows.enumerated()), id: \.offset) { rowIndex, row in
                HStack(alignment: .top, spacing: 0) {
                    ForEach(Array(row.enumerated()), id: \.offset) { colIndex, cell in
                        if colIndex > 0 { Divider() }
                        Text(cell.zh)
                            .font(.footnote.weight(rowIndex == 0 || colIndex == 0 ? .semibold : .regular))
                            .foregroundStyle(rowIndex == 0 ? AppTheme.accent : Color.primary)
                            .lineSpacing(3)
                            .fixedSize(horizontal: false, vertical: true)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 8)
                            .frame(
                                width: colIndex == 0 ? firstColumnWidth : nil,
                                alignment: .topLeading
                            )
                            .frame(
                                maxWidth: colIndex == 0 ? nil : .infinity,
                                alignment: .topLeading
                            )
                    }
                }
                .background(rowIndex == 0 ? AppTheme.accent.opacity(0.08) : AppTheme.cardFill)
                if rowIndex < rows.count - 1 { Divider() }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(AppTheme.fieldStroke, lineWidth: 0.6)
        )
    }
}

private struct IntroFigureView: View {
    let kind: String
    let caption: String

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            switch kind {
            case "yin-yang": IntroYinYangFigure()
            case "bagua": IntroBaguaFigure()
            case "six-lines": IntroSixLinesFigure()
            case "jing-chuan": IntroJingChuanFigure()
            default: EmptyView()
            }
            if !caption.isEmpty {
                Text(caption.zh)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(RoundedRectangle(cornerRadius: 12).fill(AppTheme.cardFill))
    }
}

private struct IntroYinYangFigure: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            introYaoRow(yang: true, title: "阳爻", detail: "刚健、主动、光明")
            introYaoRow(yang: false, title: "阴爻", detail: "柔顺、含藏、沉静")
        }
    }
}

private func introYaoRow(yang: Bool, title: String, detail: String) -> some View {
    HStack(spacing: 12) {
        YaoBarView(
            line: LineValue.from(isYang: yang, changing: false),
            barWidth: 72,
            barHeight: 8,
            gapWidth: 8,
            showsChangeMarker: false
        )
        VStack(alignment: .leading, spacing: 2) {
            Text(title.zh).font(.subheadline.weight(.semibold))
            Text(detail.zh).font(.caption).foregroundStyle(.secondary)
        }
        Spacer(minLength: 0)
    }
}

private struct IntroBaguaItem {
    let trigram: Trigram
    let nature: String
    let qi: String
    let family: String
}

private let introBaguaItems: [IntroBaguaItem] = [
    .init(trigram: .qian, nature: "天", qi: "健", family: "父"),
    .init(trigram: .dui, nature: "泽", qi: "悦", family: "少女"),
    .init(trigram: .li, nature: "火", qi: "丽", family: "中女"),
    .init(trigram: .zhen, nature: "雷", qi: "动", family: "长男"),
    .init(trigram: .xun, nature: "风", qi: "入", family: "长女"),
    .init(trigram: .kan, nature: "水", qi: "陷", family: "中男"),
    .init(trigram: .gen, nature: "山", qi: "止", family: "少男"),
    .init(trigram: .kun, nature: "地", qi: "顺", family: "母"),
]

private struct IntroBaguaFigure: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            ForEach(introBaguaItems, id: \.trigram) { item in
                HStack(spacing: 12) {
                    IntroTrigramBars(bits: item.trigram.bits)
                    Text(item.trigram.name.zh)
                        .font(.subheadline.weight(.semibold))
                        .frame(width: 28, alignment: .leading)
                    Text("\(item.nature) · \(item.qi) · \(item.family)".zh)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    Spacer(minLength: 0)
                }
            }
        }
    }
}

private struct IntroTrigramBars: View {
    let bits: [Int]

    var body: some View {
        VStack(spacing: 3) {
            ForEach(Array(bits.reversed().enumerated()), id: \.offset) { _, bit in
                YaoBarView(
                    line: LineValue.from(isYang: bit == 1, changing: false),
                    barWidth: 36,
                    barHeight: 5,
                    gapWidth: 5,
                    showsChangeMarker: false
                )
            }
        }
    }
}

private struct IntroSixLinesFigure: View {
    private let lines: [LineValue] = [
        .youngYin, .youngYang, .youngYang, .youngYang, .youngYang, .youngYang,
    ]
    private let names = ["初", "二", "三", "四", "五", "上"]

    var body: some View {
        HStack(alignment: .center, spacing: 12) {
            VStack(spacing: 6) {
                ForEach((0..<6).reversed(), id: \.self) { index in
                    HStack(spacing: 8) {
                        Text(names[index].zh)
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(.secondary)
                            .frame(width: 22, alignment: .trailing)
                        YaoBarView(
                            line: lines[index],
                            barWidth: 88,
                            barHeight: 8,
                            gapWidth: 8,
                            showsChangeMarker: false
                        )
                    }
                }
            }
            VStack(spacing: 0) {
                introBracketLabel("外卦")
                    .frame(height: 54)
                introBracketLabel("内卦")
                    .frame(height: 54)
            }
        }
    }
}

private func introBracketLabel(_ title: String) -> some View {
    HStack(spacing: 6) {
        RoundedRectangle(cornerRadius: 1)
            .fill(AppTheme.accent.opacity(0.45))
            .frame(width: 2)
        Text(title.zh)
            .font(.caption.weight(.semibold))
            .foregroundStyle(AppTheme.accent)
    }
}

private struct IntroJingChuanFigure: View {
    private let rows: [(group: String, name: String, detail: String)] = [
        ("经", "卦辞", "一卦的整体气氛"),
        ("", "爻辞", "这一爻的时位"),
        ("传", "彖辞", "解释卦辞"),
        ("", "大象", "君子以……"),
        ("", "小象", "解释该爻"),
        ("", "文言", "只附乾、坤"),
        ("", "四传", "系辞、说卦、序卦、杂卦"),
    ]

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ForEach(Array(rows.enumerated()), id: \.offset) { index, row in
                HStack(alignment: .firstTextBaseline, spacing: 10) {
                    Text(row.group.zh)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(AppTheme.accent)
                        .frame(width: 22, alignment: .leading)
                    Text(row.name.zh)
                        .font(.subheadline.weight(.semibold))
                        .frame(width: 36, alignment: .leading)
                    Text(row.detail.zh)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    Spacer(minLength: 0)
                }
                .padding(.vertical, 7)
                if index == 1 {
                    Divider()
                }
            }
        }
    }
}

struct ClassicHexagramListView: View {
    private let store = HexagramStore.shared

    var body: some View {
        List {
            ForEach(["上经", "下经"], id: \.self) { part in
                Section(part.ui(part == "上经" ? "Upper" : "Lower")) {
                    ForEach(store.hexagrams.filter { $0.part == part }) { hex in
                        NavigationLink {
                            ClassicHexagramDetailView(hexagram: hex)
                        } label: {
                            HStack {
                                Text(hex.listLabel)
                                    .font(.headline)
                                Spacer()
                                Text(AppLanguage.current.isEnglish ? hex.epithet : hex.title.zh)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                }
            }
        }
        .scrollContentBackground(.hidden)
        .navigationTitle("六十四卦".ui("64 Hexagrams"))
        .navigationBarTitleDisplayMode(.inline)
        .parchmentBackground()
    }
}

struct ClassicHexagramDetailView: View {
    let hexagram: Hexagram

    @State private var imaSelection: ImaExplanationSelection?

    private var imaStore: ImaExplanationStore { .shared }

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

                scriptureCard(scriptureTitle("卦辞", "Judgment"), explanationId: ImaExplanationId.guaci(number: hexagram.number)) {
                    Text(hexagram.guaci.zh)
                }
                scriptureCard(scriptureTitle("彖辞", "Commentary"), explanationId: ImaExplanationId.tuanci(number: hexagram.number)) {
                    Text(prefixed("彖曰：", hexagram.tuanci).zh)
                }
                scriptureCard(scriptureTitle("大象", "The Image"), explanationId: ImaExplanationId.daxiang(number: hexagram.number)) {
                    Text(prefixed("象曰：", hexagram.daxiang).zh)
                }
                ForEach(Array(zip(hexagram.yaoci, hexagram.xiaoxiang).enumerated()), id: \.offset) { index, pair in
                    scriptureCard(explanationId: ImaExplanationId.yaoPair(number: hexagram.number, position: index + 1)) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(pair.0.zh)
                            Text("象曰：\(pair.1)".zh)
                        }
                    }
                }
                if let yong = hexagram.yong {
                    scriptureCard(explanationId: ImaExplanationId.yong(number: hexagram.number)) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(yong.ci.zh)
                            Text("象曰：\(yong.xiang)".zh)
                        }
                    }
                }
                if !hexagram.wenyan.isEmpty {
                    scriptureCard(scriptureTitle("文言", "Wenyan"), explanationId: ImaExplanationId.wenyan(number: hexagram.number)) {
                        VStack(alignment: .leading, spacing: 8) {
                            ForEach(Array(hexagram.wenyan.enumerated()), id: \.offset) { _, paragraph in
                                Text(paragraph.zh)
                            }
                        }
                    }
                }
                ScriptureSourceLine()
            }
            .padding()
        }
        .navigationTitle(hexagram.listLabel)
        .navigationBarTitleDisplayMode(.inline)
        .parchmentBackground()
        .sheet(item: $imaSelection) { selection in
            ImaExplanationSheet(entry: selection.entry, source: imaStore.source)
        }
    }

    private func scriptureTitle(_ zh: String, _ en: String) -> String {
        AppLanguage.current.isEnglish ? "\(en) · \(zh)" : zh
    }

    private func scriptureCard(
        _ title: String? = nil,
        explanationId: String,
        @ViewBuilder content: @escaping () -> some View
    ) -> some View {
        card(title) {
            TappableScripture(explanationId: explanationId, selection: $imaSelection, content: content)
        }
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
        .navigationTitle("四传".ui("The Wings"))
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
                ScriptureSourceLine()
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
        .navigationTitle("易经证释".ui("Yijing Zhengshi"))
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

private struct ScriptureSourceLine: View {
    var body: some View {
        Text("经文版本：《易经证释》所引".ui("Text: as quoted in Yijing Zhengshi"))
            .font(.caption2)
            .foregroundStyle(.tertiary)
            .frame(maxWidth: .infinity, alignment: .trailing)
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
