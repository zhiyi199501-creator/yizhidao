package com.yizhidao.app.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yizhidao.CastingMethod
import com.yizhidao.Hexagram
import com.yizhidao.HexagramStore
import com.yizhidao.ReadingRecord
import com.yizhidao.VerificationStatus
import com.yizhidao.app.AppContainer
import com.yizhidao.app.ui.reading.ResultScreen
import com.yizhidao.app.ui.theme.AppTheme
import com.yizhidao.app.ui.theme.PaperSegmentedRow
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val rowFmt = DateTimeFormatter.ofPattern("yyyy/M/d HH:mm").withZone(ZoneId.systemDefault())

private enum class StatusFilter(val label: String) {
    All("全部状态"),
    None("未验证"),
    Fulfilled("应验"),
    Partial("部分应验"),
    Unfulfilled("未应验");

    fun matches(record: ReadingRecord): Boolean = when (this) {
        All -> true
        None -> record.verificationStatus == VerificationStatus.NONE
        Fulfilled -> record.verificationStatus == VerificationStatus.FULFILLED
        Partial -> record.verificationStatus == VerificationStatus.PARTIAL
        Unfulfilled -> record.verificationStatus == VerificationStatus.UNFULFILLED
    }
}

private enum class MovingPositionFilter(val id: String, val label: String, val position: Int?) {
    All("all", "全部", null),
    Chu("1", "初", 1),
    Er("2", "二", 2),
    San("3", "三", 3),
    Si("4", "四", 4),
    Wu("5", "五", 5),
    Shang("6", "上", 6);

    fun matches(movingPositions: List<Int>): Boolean {
        val p = position ?: return true
        return p in movingPositions
    }

    companion object {
        fun from(position: Int): MovingPositionFilter? = entries.find { it.position == position }
    }
}

private enum class MovingCountFilter(val id: String, val label: String, val count: Int?) {
    All("all", "全部", null),
    Zero("0", "0 动", 0),
    One("1", "1 动", 1),
    Two("2", "2 动", 2),
    Three("3", "3 动", 3),
    Four("4", "4 动", 4),
    Five("5", "5 动", 5),
    Six("6", "6 动", 6);

    fun matches(movingCount: Int): Boolean {
        val c = count ?: return true
        return movingCount == c
    }
}

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
    var statusFilter by remember { mutableStateOf(StatusFilter.All) }

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

    val detailPrimary = filterPrimary
    if (byHexagram && detailPrimary != null) {
        HexagramGroupDetail(
            container = container,
            records = records.filter { it.primaryNumber == detailPrimary },
            primaryNumber = detailPrimary,
            onBack = { filterPrimary = null },
            onOpenRecord = onOpenRecord,
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "历史",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.ink,
                style = AppTheme.compactText,
            )
            if (records.isNotEmpty()) {
                PaperSegmentedRow(
                    options = listOf("时间", "按卦"),
                    selectedIndex = if (byHexagram) 1 else 0,
                    onSelect = {
                        byHexagram = it == 1
                        if (it == 0) filterPrimary = null
                    },
                )
                if (!byHexagram) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StatusFilter.entries.forEach { filter ->
                            PaperChip(
                                label = filter.label,
                                selected = statusFilter == filter,
                                onClick = { statusFilter = filter },
                            )
                        }
                    }
                }
            }
        }

        if (records.isEmpty()) {
            EmptyHistory()
        } else if (byHexagram) {
            HexagramGroupList(
                records = records,
                store = container.hexagramStore,
                onOpenGroup = { filterPrimary = it },
            )
        } else {
            val visible = records.filter { statusFilter.matches(it) }
            if (visible.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("无匹配记录", color = AppTheme.secondaryText, fontSize = 15.sp)
                }
            } else {
                GroupedRecordList(
                    records = visible,
                    store = container.hexagramStore,
                    onOpenRecord = onOpenRecord,
                )
            }
        }
    }
}

@Composable
private fun EmptyHistory() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.AutoMirrored.Outlined.MenuBook,
                contentDescription = null,
                tint = AppTheme.secondaryText,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text("暂无占问", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = AppTheme.ink)
            Spacer(Modifier.height(6.dp))
            Text("起卦后会自动保存在这里", fontSize = 13.sp, color = AppTheme.secondaryText)
        }
    }
}

@Composable
private fun HexagramGroupList(
    records: List<ReadingRecord>,
    store: HexagramStore,
    onOpenGroup: (Int) -> Unit,
) {
    val groups = records.groupBy { it.primaryNumber }.toSortedMap()
    GroupedCard(Modifier.padding(horizontal = 16.dp)) {
        groups.entries.forEachIndexed { index, (number, items) ->
            val hex = store.hexagram(number)
            val latest = items.maxByOrNull { it.createdAtEpochMs }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpenGroup(number) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            hexTitle(hex, number),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppTheme.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                            style = AppTheme.compactText,
                        )
                        Text(
                            "${items.size} 次",
                            fontSize = 15.sp,
                            color = AppTheme.secondaryText,
                            style = AppTheme.compactText,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (latest != null) {
                            Text(
                                rowFmt.format(latest.createdAt),
                                fontSize = 12.sp,
                                color = AppTheme.secondaryText,
                                style = AppTheme.compactText,
                            )
                        }
                        verificationSummary(items)?.let { summary ->
                            Text(
                                summary,
                                fontSize = 12.sp,
                                color = AppTheme.secondaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = AppTheme.compactText,
                            )
                        }
                    }
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = AppTheme.secondaryText,
                    modifier = Modifier.size(18.dp),
                )
            }
            if (index < groups.size - 1) {
                HorizontalDivider(color = AppTheme.fieldStroke, thickness = 0.5.dp)
            }
        }
    }
}

@Composable
private fun HexagramGroupDetail(
    container: AppContainer,
    records: List<ReadingRecord>,
    primaryNumber: Int,
    onBack: () -> Unit,
    onOpenRecord: (String) -> Unit,
) {
    var digitalTab by remember { mutableStateOf(true) }
    var positionFilter by remember { mutableStateOf(MovingPositionFilter.All) }
    var countFilter by remember { mutableStateOf(MovingCountFilter.All) }
    val hex = container.hexagramStore.hexagram(primaryNumber)
    val methodFiltered = records.filter { rec ->
        if (digitalTab) rec.method.isDigital else rec.method == CastingMethod.COIN
    }
    val visible = methodFiltered.filter { rec ->
        if (digitalTab) positionFilter.matches(rec.movingPositions)
        else countFilter.matches(rec.movingPositions.size)
    }

    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(AppTheme.cardFill)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.clickable(onClick = onBack),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("‹", fontSize = 22.sp, color = AppTheme.accent)
                Spacer(Modifier.width(6.dp))
                Text(
                    "${hexTitle(hex, primaryNumber)} · ${records.size} 次",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.ink,
                    style = AppTheme.compactText,
                )
            }
            PaperSegmentedRow(
                options = listOf("数字起卦", "金钱起卦"),
                selectedIndex = if (digitalTab) 0 else 1,
                onSelect = { digitalTab = it == 0 },
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (digitalTab) {
                    MovingPositionFilter.entries.forEach { filter ->
                        PaperChip(
                            label = filter.label,
                            selected = positionFilter == filter,
                            onClick = { positionFilter = filter },
                        )
                    }
                } else {
                    MovingCountFilter.entries.forEach { filter ->
                        PaperChip(
                            label = filter.label,
                            selected = countFilter == filter,
                            onClick = { countFilter = filter },
                        )
                    }
                }
            }
        }

        when {
            methodFiltered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (digitalTab) "该卦尚无数字或时间起卦记录" else "该卦尚无金钱卦记录",
                    color = AppTheme.secondaryText,
                    fontSize = 15.sp,
                )
            }
            visible.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("无匹配记录", color = AppTheme.secondaryText, fontSize = 15.sp)
            }
            else -> GroupedRecordList(
                records = visible,
                store = container.hexagramStore,
                onOpenRecord = onOpenRecord,
            )
        }
    }
}

@Composable
private fun GroupedRecordList(
    records: List<ReadingRecord>,
    store: HexagramStore,
    onOpenRecord: (String) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
    ) {
        itemsIndexed(records, key = { _, rec -> rec.id }) { index, rec ->
            val shape = when {
                records.size == 1 -> AppTheme.cardShape
                index == 0 -> RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
                index == records.lastIndex -> RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)
                else -> RectangleShape
            }
            Column(Modifier.clip(shape).background(AppTheme.cardFill)) {
                HistoryRecordRow(
                    rec = rec,
                    store = store,
                    onClick = { onOpenRecord(rec.id) },
                )
                if (index < records.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        color = AppTheme.fieldStroke,
                        thickness = 0.5.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryRecordRow(
    rec: ReadingRecord,
    store: HexagramStore,
    onClick: () -> Unit,
) {
    val primary = store.hexagram(rec.primaryNumber)
    val resulting = rec.resultingNumber?.let { store.hexagram(it) }
    val movingLabel = digitalMovingLabel(rec)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    hexTitle(primary, rec.primaryNumber),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = AppTheme.compactText,
                )
                if (rec.resultingNumber != null) {
                    ChangeArrow(movingLabel)
                    Text(
                        hexTitle(resulting, rec.resultingNumber!!),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppTheme.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                        style = AppTheme.compactText,
                    )
                }
                if (rec.verificationStatus != VerificationStatus.NONE) {
                    VerificationBadge(rec.verificationStatus)
                }
            }
            Text(
                rowFmt.format(rec.createdAt),
                fontSize = 12.sp,
                color = AppTheme.secondaryText,
                style = AppTheme.compactText,
            )
            rec.question?.takeIf { it.isNotBlank() }?.let { q ->
                Text(
                    q,
                    fontSize = 15.sp,
                    color = AppTheme.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = AppTheme.compactText,
                )
            }
            rec.verificationNote?.takeIf { it.isNotBlank() }?.let { note ->
                Text(
                    note,
                    fontSize = 12.sp,
                    color = AppTheme.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = AppTheme.compactText,
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = AppTheme.secondaryText,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun ChangeArrow(movingLabel: String?) {
    Box(
        Modifier.width(28.dp).height(22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "⟶",
            fontSize = 20.sp,
            color = AppTheme.secondaryText,
            modifier = Modifier.graphicsLayer { scaleX = 1.25f },
            style = AppTheme.compactText,
        )
        if (movingLabel != null) {
            Text(
                movingLabel,
                color = AppTheme.yangRed,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = (-2).dp),
                style = AppTheme.compactText,
            )
        }
    }
}

@Composable
private fun VerificationBadge(status: VerificationStatus) {
    Text(
        status.displayName,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color.White,
        modifier = Modifier
            .clip(CircleShape)
            .background(verificationColor(status), CircleShape)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        style = AppTheme.compactText,
    )
}

@Composable
private fun PaperChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) AppTheme.accent else Color.Black.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (selected) Color.White else AppTheme.ink,
        maxLines = 1,
        style = AppTheme.compactText,
    )
}

@Composable
private fun GroupedCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(AppTheme.cardShape)
            .background(AppTheme.cardFill),
    ) { content() }
}

private val CastingMethod.isDigital: Boolean
    get() = this == CastingMethod.DIGITAL_MANUAL || this == CastingMethod.DIGITAL_TIME

private fun hexTitle(hex: Hexagram?, number: Int): String =
    if (hex != null) "${hex.symbol} ${hex.name}" else "第${number}卦"

private fun digitalMovingLabel(record: ReadingRecord): String? {
    if (!record.method.isDigital) return null
    if (record.movingPositions.size != 1) return null
    val position = record.movingPositions.first()
    return MovingPositionFilter.from(position)?.label
}

private fun verificationColor(status: VerificationStatus): Color = when (status) {
    VerificationStatus.NONE -> Color.Gray
    VerificationStatus.FULFILLED -> Color(0xFF338C59)
    VerificationStatus.PARTIAL -> Color(0xFFBF8026)
    VerificationStatus.UNFULFILLED -> Color(0xFFA64040)
}

private fun verificationSummary(records: List<ReadingRecord>): String? {
    var fulfilled = 0
    var partial = 0
    var unfulfilled = 0
    records.forEach { rec ->
        when (rec.verificationStatus) {
            VerificationStatus.NONE -> Unit
            VerificationStatus.FULFILLED -> fulfilled++
            VerificationStatus.PARTIAL -> partial++
            VerificationStatus.UNFULFILLED -> unfulfilled++
        }
    }
    val parts = buildList {
        if (fulfilled > 0) add("应验 $fulfilled")
        if (partial > 0) add("部分 $partial")
        if (unfulfilled > 0) add("未应验 $unfulfilled")
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
