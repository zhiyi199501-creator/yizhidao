package com.yizhidao.app.ui.casting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yizhidao.CastResult
import com.yizhidao.CoinCastingEngine
import com.yizhidao.LineValue
import com.yizhidao.ReadingGuide
import com.yizhidao.SecureRandomSource
import com.yizhidao.app.ui.reading.YaoBar
import com.yizhidao.app.ui.theme.AppTheme

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

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "三钱摇六次，自下而上成卦。字面为阳，背面为阴；也可点「选」手选四象。",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        (5 downTo 0).forEach { index ->
            val line = lines[index]
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ReadingGuide.yaoName(index + 1), modifier = Modifier.width(40.dp), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                if (line != null) {
                    YaoBar(line = line, highlighted = line.isChanging)
                    Spacer(Modifier.width(8.dp))
                    Text(line.displayLabel, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                } else {
                    Text("未摇", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                }
                Spacer(Modifier.weight(1f))
                Box {
                    var menu by remember { mutableStateOf(false) }
                    OutlinedButton(onClick = { menu = true }) { Text("选") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        manualOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.displayLabel) },
                                onClick = {
                                    lines = lines.toMutableList().also { it[index] = option }
                                    menu = false
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.width(6.dp))
                OutlinedButton(onClick = {
                    lines = lines.toMutableList().also { it[index] = CoinCastingEngine.tossLine(rng) }
                }) { Text("摇") }
            }
        }
        Row {
            OutlinedButton(onClick = {
                lines = List(6) { CoinCastingEngine.tossLine(rng) }
            }) { Text("一键摇满") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { lines = List(6) { null } }) { Text("清空") }
        }
        error?.let {
            Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = {
                val resolved = lines.filterNotNull()
                if (resolved.size != 6) {
                    error = "请摇满六爻"
                    return@Button
                }
                val q = question.trim().ifEmpty { null }
                onResult(CoinCastingEngine.cast(resolved, q))
            },
            enabled = filled == 6,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AppTheme.accent),
        ) { Text("起卦") }
    }
}
