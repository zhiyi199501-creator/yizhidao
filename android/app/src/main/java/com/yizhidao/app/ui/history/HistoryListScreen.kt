package com.yizhidao.app.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yizhidao.ReadingRecord
import com.yizhidao.app.AppContainer
import com.yizhidao.app.ui.reading.ResultScreen
import com.yizhidao.app.ui.theme.PaperSegmentedRow
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val rowFmt = DateTimeFormatter.ofPattern("yyyy/M/d HH:mm").withZone(ZoneId.systemDefault())

@Composable
fun HistoryListScreen(
    container: AppContainer,
    openRecordId: String?,
    similarPrimary: Int?,
    similarJumpTick: Int,
    onOpenRecord: (String) -> Unit,
    onCloseRecord: () -> Unit,
) {
    val records by container.readingRepository.records.collectAsState()
    var byHexagram by remember { mutableStateOf(false) }
    var filterPrimary by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(similarJumpTick) {
        if (similarJumpTick > 0) {
            byHexagram = true
            filterPrimary = similarPrimary
        }
    }

    val opened = openRecordId?.let { id -> records.find { it.id == id } }
    if (opened != null) {
        ResultScreen(
            result = opened.toCastResult(),
            isNew = false,
            container = container,
            existing = opened,
            onBack = onCloseRecord,
        )
        return
    }

    val visible = if (byHexagram && filterPrimary != null) {
        records.filter { it.primaryNumber == filterPrimary }
    } else {
        records
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("历史", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
        PaperSegmentedRow(
            options = listOf("时间", "按卦"),
            selectedIndex = if (byHexagram) 1 else 0,
            onSelect = {
                byHexagram = it == 1
                if (it == 0) filterPrimary = null
            },
            modifier = Modifier.padding(vertical = 12.dp),
        )

        if (byHexagram && filterPrimary == null) {
            val grouped = records.groupBy { it.primaryNumber }.toSortedMap()
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                grouped.forEach { (number, items) ->
                    val hex = container.hexagramStore.hexagram(number)
                    item(number) {
                        ListItem(
                            headlineContent = { Text("${hex?.symbol.orEmpty()} ${hex?.name ?: number}卦") },
                            supportingContent = { Text("${items.size} 条") },
                            modifier = Modifier.clickable { filterPrimary = number },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }
        } else {
            if (filterPrimary != null) {
                val hex = container.hexagramStore.hexagram(filterPrimary!!)
                Text(
                    "${hex?.symbol.orEmpty()} ${hex?.name ?: ""} · ${visible.size} 条",
                    modifier = Modifier.padding(bottom = 8.dp).clickable { filterPrimary = null },
                    color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                )
            }
            if (visible.isEmpty()) {
                Text("暂无记录", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(visible, key = { it.id }) { rec ->
                        HistoryRow(rec, container) { onOpenRecord(rec.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(rec: ReadingRecord, container: AppContainer, onClick: () -> Unit) {
    val hex = container.hexagramStore.hexagram(rec.primaryNumber)
    val resulting = rec.resultingNumber?.let { container.hexagramStore.hexagram(it) }
    val title = buildString {
        append(hex?.name ?: rec.primaryNumber)
        if (resulting != null) {
            append("之")
            append(resulting.name)
        }
    }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(
                listOfNotNull(
                    rec.method.displayName,
                    rec.question,
                    rowFmt.format(rec.createdAt),
                ).joinToString(" · "),
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
