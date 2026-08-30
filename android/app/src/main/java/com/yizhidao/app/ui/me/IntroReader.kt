package com.yizhidao.app.ui.me

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yizhidao.LineValue
import com.yizhidao.Trigram
import com.yizhidao.app.classic.YijingIntroBlock
import com.yizhidao.app.classic.YijingIntroBook
import com.yizhidao.app.classic.YijingIntroLink
import com.yizhidao.app.ui.reading.YaoBar
import com.yizhidao.app.ui.theme.AppTheme
import com.yizhidao.app.ui.theme.PaperBackHeader
import com.yizhidao.app.ui.theme.PaperChevron
import com.yizhidao.app.ui.theme.Text

private val introChapterMarks = listOf("一", "二", "三", "四", "五", "六", "七", "八", "九")

internal fun introChapterMark(index: Int): String =
    introChapterMarks.getOrElse(index) { "${index + 1}" }

@Composable
internal fun IntroChapterReader(
    book: YijingIntroBook,
    index: Int,
    onBack: () -> Unit,
    onOpenChapter: (Int) -> Unit,
    onOpenLink: (String) -> Unit,
) {
    val chapter = book.chapters.getOrNull(index) ?: return
    val scroll = rememberScrollState()
    LaunchedEffect(chapter.id) { scroll.scrollTo(0) }
    Column(Modifier.fillMaxSize()) {
        PaperBackHeader(title = chapter.title, onBack = onBack)
        key(chapter.id) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                if (chapter.subtitle.isNotBlank()) {
                    Text(
                        chapter.subtitle,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppTheme.ink,
                        lineHeight = 28.sp,
                        style = AppTheme.compactText,
                    )
                }
                chapter.blocks.forEach { block ->
                    IntroBlock(block, onOpenLink)
                }
                if (index > 0 || index + 1 < book.chapters.size) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (index > 0) {
                            val prev = book.chapters[index - 1]
                            IntroChapterJump(
                                label = "上一章",
                                title = prev.title,
                                leadingChevron = true,
                                onClick = { onOpenChapter(index - 1) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (index + 1 < book.chapters.size) {
                            val next = book.chapters[index + 1]
                            IntroChapterJump(
                                label = "下一章",
                                title = next.title,
                                leadingChevron = false,
                                onClick = { onOpenChapter(index + 1) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun IntroChapterJump(
    label: String,
    title: String,
    leadingChevron: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AppTheme.cardFill)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (leadingChevron) {
            PaperChevron(rotation = 180f)
        }
        Column(
            Modifier.weight(1f),
            horizontalAlignment = if (leadingChevron) Alignment.Start else Alignment.End,
        ) {
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppTheme.accent,
                style = AppTheme.compactText,
                en = if (leadingChevron) "Previous" else "Next",
            )
            Text(
                title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppTheme.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
                style = AppTheme.compactText,
            )
        }
        if (!leadingChevron) {
            PaperChevron()
        }
    }
}

@Composable
private fun IntroBlock(block: YijingIntroBlock, onOpenLink: (String) -> Unit) {
    when (block.type) {
        "p" -> Text(
            block.text,
            fontSize = 16.sp,
            color = AppTheme.ink,
            lineHeight = 26.sp,
        )
        "quote" -> IntroQuote(block.text, block.cite)
        "list" -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            block.items.forEachIndexed { itemIndex, item ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        "${itemIndex + 1}.",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppTheme.accent,
                        modifier = Modifier.width(22.dp),
                        style = AppTheme.compactText,
                    )
                    Text(
                        item,
                        fontSize = 16.sp,
                        color = AppTheme.ink,
                        lineHeight = 24.sp,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        "table" -> IntroTable(block.rows)
        "figure" -> IntroFigure(block.kind, block.caption)
        "links" -> IntroLinks(block.links, onOpenLink)
    }
}

@Composable
private fun IntroQuote(text: String, cite: String) {
    Row(
        Modifier.height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(1.dp))
                .background(AppTheme.accent),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            Text(text, fontSize = 16.sp, color = AppTheme.ink, lineHeight = 26.sp)
            if (cite.isNotBlank()) {
                Text(
                    cite,
                    fontSize = 12.sp,
                    color = AppTheme.secondaryText,
                    style = AppTheme.compactText,
                )
            }
        }
    }
}

@Composable
private fun IntroTable(rows: List<List<String>>) {
    val shape = RoundedCornerShape(12.dp)
    val columnCount = rows.firstOrNull()?.size ?: return
    val firstColWidth = if (columnCount == 2) 72.dp else 96.dp
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppTheme.cardFill)
            .border(0.6.dp, AppTheme.fieldStroke, shape),
    ) {
        rows.forEachIndexed { rowIndex, row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .background(
                        if (rowIndex == 0) AppTheme.accent.copy(alpha = 0.08f)
                        else Color.Transparent,
                    ),
            ) {
                row.forEachIndexed { colIndex, cell ->
                    if (colIndex > 0) {
                        Box(
                            Modifier
                                .width(0.6.dp)
                                .fillMaxHeight()
                                .background(AppTheme.fieldStroke),
                        )
                    }
                    Text(
                        cell,
                        fontSize = 13.sp,
                        fontWeight = if (rowIndex == 0 || colIndex == 0) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (rowIndex == 0) AppTheme.accent else AppTheme.ink,
                        lineHeight = 18.sp,
                        modifier = (if (colIndex == 0) Modifier.width(firstColWidth) else Modifier.weight(1f))
                            .fillMaxHeight()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        style = AppTheme.compactText,
                    )
                }
            }
            if (rowIndex < rows.lastIndex) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(0.6.dp)
                        .background(AppTheme.fieldStroke),
                )
            }
        }
    }
}

@Composable
private fun IntroLinks(links: List<YijingIntroLink>, onOpenLink: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppTheme.cardFill),
    ) {
        links.forEachIndexed { index, link ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpenLink(link.route) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        link.title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppTheme.ink,
                        style = AppTheme.compactText,
                    )
                    if (link.subtitle.isNotBlank()) {
                        Text(
                            link.subtitle,
                            fontSize = 13.sp,
                            color = AppTheme.secondaryText,
                            modifier = Modifier.padding(top = 2.dp),
                            style = AppTheme.compactText,
                        )
                    }
                }
                PaperChevron()
            }
            if (index < links.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 16.dp),
                    color = AppTheme.fieldStroke,
                    thickness = 0.5.dp,
                )
            }
        }
    }
}

@Composable
private fun IntroFigure(kind: String, caption: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppTheme.cardFill)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when (kind) {
            "yin-yang" -> IntroYinYangFigure()
            "bagua" -> IntroBaguaFigure()
            "six-lines" -> IntroSixLinesFigure()
            "jing-chuan" -> IntroJingChuanFigure()
        }
        if (caption.isNotBlank()) {
            Text(
                caption,
                fontSize = 12.sp,
                color = AppTheme.secondaryText,
                style = AppTheme.compactText,
            )
        }
    }
}

@Composable
private fun IntroYinYangFigure() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        IntroYaoRow(yang = true, title = "阳爻", detail = "刚健、主动、光明")
        IntroYaoRow(yang = false, title = "阴爻", detail = "柔顺、含藏、沉静")
    }
}

@Composable
private fun IntroYaoRow(yang: Boolean, title: String, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        YaoBar(
            line = LineValue.from(yang, false),
            highlighted = false,
            barWidth = 72.dp,
            barHeight = 8.dp,
            reserveMarkerSpace = false,
        )
        Column {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AppTheme.ink, style = AppTheme.compactText)
            Text(detail, fontSize = 12.sp, color = AppTheme.secondaryText, style = AppTheme.compactText)
        }
    }
}

private data class IntroBaguaItem(
    val trigram: Trigram,
    val nature: String,
    val qi: String,
    val family: String,
)

private val introBaguaItems = listOf(
    IntroBaguaItem(Trigram.QIAN, "天", "健", "父"),
    IntroBaguaItem(Trigram.DUI, "泽", "悦", "少女"),
    IntroBaguaItem(Trigram.LI, "火", "丽", "中女"),
    IntroBaguaItem(Trigram.ZHEN, "雷", "动", "长男"),
    IntroBaguaItem(Trigram.XUN, "风", "入", "长女"),
    IntroBaguaItem(Trigram.KAN, "水", "陷", "中男"),
    IntroBaguaItem(Trigram.GEN, "山", "止", "少男"),
    IntroBaguaItem(Trigram.KUN, "地", "顺", "母"),
)

@Composable
private fun IntroBaguaFigure() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        introBaguaItems.forEach { item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IntroTrigramBars(item.trigram.bits)
                Text(
                    item.trigram.displayName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.ink,
                    modifier = Modifier.width(28.dp),
                    style = AppTheme.compactText,
                )
                Text(
                    "${item.nature} · ${item.qi} · ${item.family}",
                    fontSize = 15.sp,
                    color = AppTheme.secondaryText,
                    style = AppTheme.compactText,
                )
            }
        }
    }
}

@Composable
private fun IntroTrigramBars(bits: List<Int>) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        bits.asReversed().forEach { bit ->
            YaoBar(
                line = LineValue.from(bit == 1, false),
                highlighted = false,
                barWidth = 36.dp,
                barHeight = 5.dp,
                reserveMarkerSpace = false,
            )
        }
    }
}

@Composable
private fun IntroSixLinesFigure() {
    val lines = listOf(
        LineValue.YOUNG_YIN,
        LineValue.YOUNG_YANG,
        LineValue.YOUNG_YANG,
        LineValue.YOUNG_YANG,
        LineValue.YOUNG_YANG,
        LineValue.YOUNG_YANG,
    )
    val names = listOf("初", "二", "三", "四", "五", "上")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            (5 downTo 0).forEach { index ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        names[index],
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppTheme.secondaryText,
                        modifier = Modifier.width(22.dp),
                        style = AppTheme.compactText,
                    )
                    YaoBar(
                        line = lines[index],
                        highlighted = false,
                        barWidth = 88.dp,
                        barHeight = 8.dp,
                        reserveMarkerSpace = false,
                    )
                }
            }
        }
        Column {
            IntroBracketLabel("外卦", Modifier.height(54.dp))
            IntroBracketLabel("内卦", Modifier.height(54.dp))
        }
    }
}

@Composable
private fun IntroBracketLabel(title: String, modifier: Modifier = Modifier) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .width(2.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(1.dp))
                .background(AppTheme.accent.copy(alpha = 0.45f)),
        )
        Text(
            title,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppTheme.accent,
            style = AppTheme.compactText,
        )
    }
}

@Composable
private fun IntroJingChuanFigure() {
    data class RowSpec(val group: String, val name: String, val detail: String)
    val rows = listOf(
        RowSpec("经", "卦辞", "一卦的整体气氛"),
        RowSpec("", "爻辞", "这一爻的时位"),
        RowSpec("传", "彖辞", "解释卦辞"),
        RowSpec("", "大象", "君子以……"),
        RowSpec("", "小象", "解释该爻"),
        RowSpec("", "文言", "只附乾、坤"),
        RowSpec("", "四传", "系辞、说卦、序卦、杂卦"),
    )
    Column {
        rows.forEachIndexed { index, row ->
            Row(
                Modifier.padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    row.group,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.accent,
                    modifier = Modifier.width(22.dp),
                    style = AppTheme.compactText,
                )
                Text(
                    row.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.ink,
                    modifier = Modifier.width(36.dp),
                    style = AppTheme.compactText,
                )
                Text(
                    row.detail,
                    fontSize = 15.sp,
                    color = AppTheme.secondaryText,
                    style = AppTheme.compactText,
                )
            }
            if (index == 1) {
                HorizontalDivider(color = AppTheme.fieldStroke, thickness = 0.5.dp)
            }
        }
    }
}
