package com.yizhidao.app.ui.reading

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import com.yizhidao.app.ui.theme.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yizhidao.CastResult
import com.yizhidao.Hexagram
import com.yizhidao.HexagramStore
import com.yizhidao.HexagramText
import com.yizhidao.LineValue
import com.yizhidao.ReadingFocus
import com.yizhidao.ReadingGuide
import com.yizhidao.app.ima.ImaExplanationEntry
import com.yizhidao.app.ima.ImaExplanationId
import com.yizhidao.app.ima.ImaExplanationStore
import com.yizhidao.app.lang.LocalAppLanguage
import com.yizhidao.app.lang.listLabel
import com.yizhidao.app.lang.roleCaption
import com.yizhidao.app.ui.theme.AppTheme
import com.yizhidao.app.ui.theme.PaperSegmentedRow
import com.yizhidao.app.ui.theme.ui

private enum class HexTab(val zh: String, val en: String) {
    Primary("本卦", "Primary"),
    Resulting("之卦", "Relating"),
}

@Composable
fun HexagramReadingBody(
    result: CastResult,
    store: HexagramStore,
    imaStore: ImaExplanationStore,
) {
    HexagramReadingBody(
        primaryNumber = result.primaryNumber,
        resultingNumber = result.resultingNumber,
        lines = result.lines,
        movingPositions = result.movingPositions,
        store = store,
        imaStore = imaStore,
    )
}

@Composable
fun HexagramReadingBody(
    primaryNumber: Int,
    resultingNumber: Int?,
    lines: List<LineValue>,
    movingPositions: List<Int>,
    store: HexagramStore,
    imaStore: ImaExplanationStore,
) {
    var selectedEntry by remember { mutableStateOf<ImaExplanationEntry?>(null) }
    val language = LocalAppLanguage.current
    val primary = store.hexagram(primaryNumber)
    val resulting = resultingNumber?.let { store.hexagram(it) }
    val focus = remember(movingPositions) { ReadingGuide.focus(movingPositions) }
    var tab by remember { mutableStateOf(HexTab.Primary) }
    val tabs = if (resulting == null) listOf(HexTab.Primary) else HexTab.entries

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                if (primary != null) {
                    Text(
                        primary.listLabel(language),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.ink,
                        style = AppTheme.compactText,
                    )
                    Text(
                        primary.roleCaption(language, "本卦", "Primary"),
                        fontSize = 12.sp,
                        color = AppTheme.secondaryText,
                        style = AppTheme.compactText,
                    )
                }
                HexagramFigure(lines = lines, movingPositions = movingPositions)
            }
            if (resulting != null) {
                val changed = lines.map { if (it.isChanging) it.changed else it }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(
                        resulting.listLabel(language),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.ink,
                        style = AppTheme.compactText,
                    )
                    Text(
                        resulting.roleCaption(language, "之卦", "Relating"),
                        fontSize = 12.sp,
                        color = AppTheme.secondaryText,
                        style = AppTheme.compactText,
                    )
                    HexagramFigure(lines = changed, movingPositions = emptyList())
                }
            }
        }

        if (tabs.size > 1) {
            PaperSegmentedRow(
                options = tabs.map { ui(it.zh, it.en) },
                selectedIndex = tabs.indexOf(tab).coerceAtLeast(0),
                onSelect = { tab = tabs[it] },
            )
        }

        val hex = if (tab == HexTab.Primary) primary else resulting
        if (hex != null) {
            HexagramTextSection(
                hex = hex,
                tab = tab,
                focus = focus,
                movingPositions = movingPositions,
                imaStore = imaStore,
                onSelectExplanation = { selectedEntry = it },
            )
        }

        ScriptureSourceLine(modifier = Modifier.align(Alignment.End))
    }

    selectedEntry?.let { entry ->
        ImaExplanationSheet(
            entry = entry,
            source = imaStore.source,
            onDismiss = { selectedEntry = null },
        )
    }
}

@Composable
private fun HexagramTextSection(
    hex: Hexagram,
    tab: HexTab,
    focus: ReadingFocus,
    movingPositions: List<Int>,
    imaStore: ImaExplanationStore,
    onSelectExplanation: (ImaExplanationEntry) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CardSection(title = scriptureTitle("卦辞", "Judgment"), showLead = shouldShowGuaciLead(tab, focus)) {
            TappableScripture(
                explanationId = ImaExplanationId.guaci(hex.number),
                imaStore = imaStore,
                onSelect = onSelectExplanation,
            ) {
                Text(hex.guaci, fontSize = 16.sp, color = AppTheme.ink, lineHeight = 24.sp)
            }
        }
        CardSection(title = scriptureTitle("彖辞", "Commentary")) {
            TappableScripture(
                explanationId = ImaExplanationId.tuanci(hex.number),
                imaStore = imaStore,
                onSelect = onSelectExplanation,
            ) {
                Text(HexagramText.prefixed("彖曰：", hex.tuanci), fontSize = 16.sp, color = AppTheme.ink, lineHeight = 24.sp)
            }
        }
        CardSection(title = scriptureTitle("大象", "The Image")) {
            TappableScripture(
                explanationId = ImaExplanationId.daxiang(hex.number),
                imaStore = imaStore,
                onSelect = onSelectExplanation,
            ) {
                Text(HexagramText.prefixed("象曰：", hex.daxiang), fontSize = 16.sp, color = AppTheme.ink, lineHeight = 24.sp)
            }
        }
        CardSection {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                (6 downTo 1).forEach { pos ->
                    LineBlock(hex, tab, pos, focus, movingPositions, imaStore, onSelectExplanation)
                }
            }
        }
    }
}

@Composable
private fun LineBlock(
    hex: Hexagram,
    tab: HexTab,
    position: Int,
    focus: ReadingFocus,
    movingPositions: List<Int>,
    imaStore: ImaExplanationStore,
    onSelectExplanation: (ImaExplanationEntry) -> Unit,
) {
    val moving = position in movingPositions
    val showLead = shouldShowLead(tab, position, movingPositions, focus)
    val color = if (moving) Color.Red else Color.Unspecified
    TappableScripture(
        explanationId = ImaExplanationId.yaoPair(hex.number, position),
        imaStore = imaStore,
        onSelect = onSelectExplanation,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (showLead) LeadBadge()
            Text(hex.yaoCi(position).trim(), color = color, fontSize = 16.sp, lineHeight = 24.sp)
            Text(HexagramText.xiangLine(hex.xiaoXiang(position)), color = color, fontSize = 16.sp, lineHeight = 24.sp)
        }
    }
}

@Composable
private fun scriptureTitle(zh: String, en: String): String? {
    val language = LocalAppLanguage.current
    return if (language.isEnglish) "$en · $zh" else null
}

@Composable
private fun CardSection(title: String? = null, showLead: Boolean = false, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(AppTheme.cardFill, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showLead) LeadBadge()
        if (title != null) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AppTheme.secondaryText)
        }
        content()
    }
}

@Composable
private fun LeadBadge() {
    Text(
        "主看",
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        style = AppTheme.compactText,
        modifier = Modifier
            .background(Color.Red, RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        en = "Focus",
    )
}

private fun shouldShowGuaciLead(tab: HexTab, focus: ReadingFocus): Boolean = when (focus.kind) {
    ReadingFocus.Kind.PrimaryGuaci, ReadingFocus.Kind.BothGuaci -> tab == HexTab.Primary
    ReadingFocus.Kind.ResultingGuaci -> tab == HexTab.Resulting
    else -> false
}

private fun shouldShowLead(
    tab: HexTab,
    position: Int,
    movingPositions: List<Int>,
    focus: ReadingFocus,
): Boolean {
    if (movingPositions.size < 2) return false
    return when (val kind = focus.kind) {
        is ReadingFocus.Kind.PrimaryLines -> tab == HexTab.Primary && kind.lead == position
        is ReadingFocus.Kind.ResultingLines -> tab == HexTab.Resulting && kind.lead == position
        else -> false
    }
}

@Composable
fun ScriptureSourceLine(modifier: Modifier = Modifier) {
    Text(
        "经文版本：《易经证释》所引",
        fontSize = 11.sp,
        color = AppTheme.secondaryText,
        style = AppTheme.compactText,
        modifier = modifier,
        en = "Text: as quoted in Yijing Zhengshi",
    )
}
