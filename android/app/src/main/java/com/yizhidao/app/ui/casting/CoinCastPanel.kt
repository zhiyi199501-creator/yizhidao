package com.yizhidao.app.ui.casting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.yizhidao.app.ui.theme.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yizhidao.CastResult
import com.yizhidao.CoinCastingEngine
import com.yizhidao.LineValue
import com.yizhidao.ReadingGuide
import com.yizhidao.SecureRandomSource
import com.yizhidao.app.sound.TapSoundPlayer
import com.yizhidao.app.ui.reading.YaoBar
import com.yizhidao.app.ui.theme.AppTheme
import com.yizhidao.app.ui.theme.PaperOutlinedButton
import com.yizhidao.app.ui.theme.PaperPrimaryButton

private val manualOptions = listOf(
    LineValue.YOUNG_YANG,
    LineValue.YOUNG_YIN,
    LineValue.OLD_YANG,
    LineValue.OLD_YIN,
)

@Composable
fun CoinCastPanel(
    question: String,
    onResult: (CastResult) -> Unit,
) {
    var lines by remember { mutableStateOf(List<LineValue?>(6) { null }) }
    var error by remember { mutableStateOf<String?>(null) }
    val rng = remember { SecureRandomSource() }
    val filled = lines.count { it != null }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "三钱摇六次，自下而上成卦。字面为阳，背面为阴；也可点「选」手选四象。",
            fontSize = 12.sp,
            color = AppTheme.secondaryText,
            lineHeight = 16.sp,
            style = AppTheme.compactText,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (5 downTo 0).forEach { index ->
                CoinYaoRow(
                    position = index + 1,
                    line = lines[index],
                    onSelect = { chosen ->
                        lines = lines.toMutableList().also { it[index] = chosen }
                    },
                    onToss = {
                        lines = lines.toMutableList().also { it[index] = CoinCastingEngine.tossLine(rng) }
                    },
                )
            }
        }
        Row {
            PaperOutlinedButton(onClick = {
                TapSoundPlayer.play()
                lines = List(6) { CoinCastingEngine.tossLine(rng) }
            }, label = "一键摇满")
            Spacer(Modifier.width(8.dp))
            PaperOutlinedButton(onClick = { lines = List(6) { null } }, label = "清空")
        }
        error?.let {
            Text(
                it,
                color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
                style = AppTheme.compactText,
            )
        }
        PaperPrimaryButton(
            onClick = {
                val resolved = lines.filterNotNull()
                if (resolved.size != 6) {
                    error = "请摇满六爻"
                    return@PaperPrimaryButton
                }
                val q = question.trim().ifEmpty { null }
                onResult(CoinCastingEngine.cast(resolved, q))
            },
            enabled = filled == 6,
            label = "起卦",
        )
    }
}

@Composable
private fun CoinYaoRow(
    position: Int,
    line: LineValue?,
    onSelect: (LineValue) -> Unit,
    onToss: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            ReadingGuide.yaoName(position),
            fontSize = 15.sp,
            color = AppTheme.ink,
            maxLines = 1,
            modifier = Modifier.width(40.dp),
            style = AppTheme.compactText,
        )
        BoxWithConstraints(Modifier.weight(1f)) {
            val barWidth = (maxWidth - 56.dp).coerceIn(72.dp, 110.dp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (line != null) {
                    YaoBar(line = line, highlighted = line.isChanging, barWidth = barWidth)
                    Text(
                        line.displayLabel,
                        fontSize = 12.sp,
                        color = AppTheme.secondaryText,
                        maxLines = 1,
                        style = AppTheme.compactText,
                    )
                } else {
                    Text(
                        "未摇",
                        fontSize = 12.sp,
                        color = AppTheme.placeholder,
                        style = AppTheme.compactText,
                    )
                }
                Spacer(Modifier.weight(1f))
            }
        }
        Box {
            PaperOutlinedButton(onClick = { menu = true }, compact = true, label = "选")
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                manualOptions.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                option.displayLabel,
                                fontSize = 15.sp,
                                style = AppTheme.compactText,
                            )
                        },
                        onClick = {
                            onSelect(option)
                            menu = false
                        },
                    )
                }
            }
        }
        PaperOutlinedButton(onClick = {
            TapSoundPlayer.play()
            onToss()
        }, compact = true, label = "摇")
    }
}
