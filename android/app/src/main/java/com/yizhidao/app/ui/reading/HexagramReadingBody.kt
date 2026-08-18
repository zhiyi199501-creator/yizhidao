package com.yizhidao.app.ui.reading

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import com.yizhidao.CastResult
import com.yizhidao.Hexagram
import com.yizhidao.HexagramStore
import com.yizhidao.HexagramText
import com.yizhidao.LineValue
import com.yizhidao.ReadingFocus
import com.yizhidao.ReadingGuide
import com.yizhidao.app.ui.theme.AppTheme
import com.yizhidao.app.ui.theme.PaperSegmentedRow

private enum class HexTab(val label: String) { Primary("本卦"), Resulting("之卦") }

@Composable
fun HexagramReadingBody(
    result: CastResult,
    store: HexagramStore,
) {
    HexagramReadingBody(
        primaryNumber = result.primaryNumber,
        resultingNumber = result.resultingNumber,
        lines = result.lines,
        movingPositions = result.movingPositions,
        store = store,
    )
}

@Composable
fun HexagramReadingBody(
    primaryNumber: Int,
    resultingNumber: Int?,
    lines: List<LineValue>,
    movingPositions: List<Int>,
    store: HexagramStore,
) {
    val primary = store.hexagram(primaryNumber)
    val resulting = resultingNumber?.let { store.hexagram(it) }
    val focus = remember(movingPositions) { ReadingGuide.focus(movingPositions) }
    var tab by remember { mutableStateOf(HexTab.Primary) }
    val tabs = if (resulting == null) listOf(HexTab.Primary) else HexTab.entries

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                if (primary != null) {
                    Text("${primary.symbol} ${primary.name}", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("第${primary.number}卦 · 本卦", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                }
                HexagramFigure(lines = lines, movingPositions = movingPositions)
            }
            if (resulting != null && resultingNumber != null) {
                val changed = lines.map { if (it.isChanging) it.changed else it }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text("${resulting.symbol} ${resulting.name}", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("第${resultingNumber}卦 · 之卦", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                    HexagramFigure(lines = changed, movingPositions = emptyList())
                }
            }
        }

        Text(focus.summary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = AppTheme.accent)

        if (tabs.size > 1) {
            PaperSegmentedRow(
                options = tabs.map { it.label },
                selectedIndex = tabs.indexOf(tab).coerceAtLeast(0),
                onSelect = { tab = tabs[it] },
            )
        }

        val hex = if (tab == HexTab.Primary) primary else resulting
        if (hex != null) {
            HexagramTextSection(hex = hex, tab = tab, focus = focus, movingPositions = movingPositions)
        }

        Text(
            "经文版本：《易经证释》所引",
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.align(Alignment.End),
        )
    }
}

@Composable
private fun HexagramTextSection(
    hex: Hexagram,
    tab: HexTab,
    focus: ReadingFocus,
    movingPositions: List<Int>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CardSection(showLead = shouldShowGuaciLead(tab, focus)) {
            Text(hex.guaci, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge, lineHeight = androidx.compose.material3.MaterialTheme.typography.bodyLarge.lineHeight)
        }
        CardSection {
            Text(HexagramText.prefixed("彖曰：", hex.tuanci), style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
        }
        CardSection {
            Text(HexagramText.prefixed("象曰：", hex.daxiang), style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
        }
        CardSection {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                (6 downTo 1).forEach { pos ->
                    LineBlock(hex, tab, pos, focus, movingPositions)
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
) {
    val moving = position in movingPositions
    val showLead = shouldShowLead(tab, position, movingPositions, focus)
    val color = if (moving) Color.Red else Color.Unspecified
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (showLead) LeadBadge()
        Text(hex.yaoCi(position).trim(), color = color, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
        Text(HexagramText.xiangLine(hex.xiaoXiang(position)), color = color, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun CardSection(showLead: Boolean = false, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(AppTheme.cardFill, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showLead) LeadBadge()
        content()
    }
}

@Composable
private fun LeadBadge() {
    Text(
        "主看",
        color = Color.White,
        fontWeight = FontWeight.Bold,
        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .background(Color.Red, RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 2.dp),
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
