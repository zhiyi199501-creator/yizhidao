package com.yizhidao.app.ui.history

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
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.yizhidao.app.lang.AppLanguage
import com.yizhidao.app.lang.LocalAppLanguage
import com.yizhidao.app.lang.listLabel
import com.yizhidao.app.lang.localizedName
import com.yizhidao.app.lang.numberLabel
import com.yizhidao.app.ui.theme.Text
import com.yizhidao.app.ui.theme.ui
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yizhidao.CastingMethod
import com.yizhidao.CastResult
import com.yizhidao.Hexagram
import com.yizhidao.HexagramStore
import com.yizhidao.ReadingRecord
import com.yizhidao.VerificationStatus
import com.yizhidao.app.AppContainer
import com.yizhidao.app.ui.reading.ResultScreen
import com.yizhidao.app.ui.theme.AppTheme
import com.yizhidao.app.ui.theme.PaperBackHeader
import com.yizhidao.app.ui.theme.PaperChevron
import com.yizhidao.app.ui.theme.PaperSegmentedRow
import com.yizhidao.app.ui.theme.SwipeRevealDelete
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val rowFmt = DateTimeFormatter.ofPattern("yyyy/M/d HH:mm").withZone(ZoneId.systemDefault())

private enum class StatusFilter(val zh: String, val en: String) {
    All("全部状态", "All"),
    None("未验证", "Unverified"),
    Fulfilled("应验", "Fulfilled"),
    Partial("部分应验", "Partly fulfilled"),
    Unfulfilled("未应验", "Not fulfilled");

    fun matches(record: ReadingRecord): Boolean = when (this) {
        All -> true
        None -> record.verificationStatus == VerificationStatus.NONE
        Fulfilled -> record.verificationStatus == VerificationStatus.FULFILLED
        Partial -> record.verificationStatus == VerificationStatus.PARTIAL
        Unfulfilled -> record.verificationStatus == VerificationStatus.UNFULFILLED
    }
}

private enum class MovingPositionFilter(val id: String, val zh: String, val en: String, val position: Int?) {
    All("all", "全部", "All", null),
    Chu("1", "初", "1st", 1),
    Er("2", "二", "2nd", 2),
    San("3", "三", "3rd", 3),
    Si("4", "四", "4th", 4),
    Wu("5", "五", "5th", 5),
    Shang("6", "上", "Top", 6);

    fun matches(movingPositions: List<Int>): Boolean {
        val p = position ?: return true
        return p in movingPositions
    }

    companion object {
        fun from(position: Int): MovingPositionFilter? = entries.find { it.position == position }
    }
}

private enum class MovingCountFilter(val id: String, val zh: String, val en: String, val count: Int?) {
    All("all", "全部", "All", null),
    Zero("0", "0 动", "0 changing", 0),
    One("1", "1 动", "1 changing", 1),
    Two("2", "2 动", "2 changing", 2),
    Three("3", "3 动", "3 changing", 3),
    Four("4", "4 动", "4 changing", 4),
    Five("5", "5 动", "5 changing", 5),
    Six("6", "6 动", "6 changing", 6);

    fun matches(movingCount: Int): Boolean {
        val c = count ?: return true
        return movingCount == c
    }

    companion object {
        fun from(movingCount: Int): MovingCountFilter =
            entries.find { it.count == movingCount } ?: All
    }
}

/** 从卦象结果跳到历史同卦明细时预填方法与动爻筛选。 */
data class SimilarHexagramJump(
    val primaryNumber: Int,
    val digitalTab: Boolean,
    val position: Int?,
    val movingCount: Int?,
) {
    companion object {
        fun from(result: CastResult): SimilarHexagramJump {
            val digital = result.method != CastingMethod.COIN
            return SimilarHexagramJump(
                primaryNumber = result.primaryNumber,
                digitalTab = digital,
                position = result.movingPositions.singleOrNull().takeIf { digital },
                movingCount = result.movingPositions.size.takeIf { !digital },
            )
        }
    }
}

@Composable
fun HistoryListScreen(
    container: AppContainer,
    openRecordId: String?,
    similarJump: SimilarHexagramJump?,
    similarJumpTick: Int,
    onOpenRecord: (String) -> Unit,
    onCloseRecord: () -> Unit,
    onTabBarVisible: (Boolean) -> Unit = {},
) {
    val records by container.readingRepository.records.collectAsState()
    var byHexagram by remember { mutableStateOf(false) }
    var filterPrimary by remember { mutableStateOf<Int?>(null) }
    var statusFilter by remember { mutableStateOf(StatusFilter.All) }
    var appliedJump by remember { mutableStateOf<SimilarHexagramJump?>(null) }
    var filterResetKey by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val onDelete: (ReadingRecord) -> Unit = { rec ->
        scope.launch { container.readingRepository.archive(rec.id) }
    }

    LaunchedEffect(similarJumpTick) {
        if (similarJumpTick > 0 && similarJump != null) {
            byHexagram = true
            filterPrimary = similarJump.primaryNumber
            appliedJump = similarJump
            filterResetKey += 1
            onCloseRecord()
        }
    }

    val opened = openRecordId?.let { id -> records.find { it.id == id } }
    val showingDetail = opened != null || (byHexagram && filterPrimary != null)
    LaunchedEffect(showingDetail) {
        onTabBarVisible(!showingDetail)
    }
    DisposableEffect(Unit) {
        onDispose { onTabBarVisible(true) }
    }
    if (opened != null) {
        ResultScreen(
            result = opened.toCastResult(),
            isNew = false,
            container = container,
            existing = opened,
            onBack = onCloseRecord,
            onOpenSimilar = { result ->
                appliedJump = SimilarHexagramJump.from(result)
                filterPrimary = result.primaryNumber
                byHexagram = true
                filterResetKey += 1
                onCloseRecord()
            },
        )
        return
    }

    val detailPrimary = filterPrimary
    if (byHexagram && detailPrimary != null) {
        HexagramGroupDetail(
            container = container,
            records = records.filter { it.primaryNumber == detailPrimary },
            primaryNumber = detailPrimary,
            initialDigitalTab = appliedJump?.digitalTab ?: true,
            initialPositionFilter = appliedJump?.position?.let { MovingPositionFilter.from(it) }
                ?: MovingPositionFilter.All,
            initialCountFilter = appliedJump?.movingCount?.let { MovingCountFilter.from(it) }
                ?: MovingCountFilter.All,
            filterResetKey = filterResetKey,
            onBack = {
                filterPrimary = null
                appliedJump = null
            },
            onOpenRecord = onOpenRecord,
            onDelete = onDelete,
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
                en = "History",
            )
            if (records.isNotEmpty()) {
                PaperSegmentedRow(
                    options = listOf(ui("时间", "Time"), ui("按卦", "By hexagram")),
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
                                label = ui(filter.zh, filter.en),
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
                onOpenGroup = {
                    appliedJump = null
                    filterResetKey += 1
                    filterPrimary = it
                },
            )
        } else {
            val visible = records.filter { statusFilter.matches(it) }
            if (visible.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("无匹配记录", color = AppTheme.secondaryText, fontSize = 15.sp, en = "No matching records")
                }
            } else {
                GroupedRecordList(
                    records = visible,
                    store = container.hexagramStore,
                    onOpenRecord = onOpenRecord,
                    onDelete = onDelete,
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
            Text("暂无占问", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = AppTheme.ink, en = "No readings yet")
            Spacer(Modifier.height(6.dp))
            Text("起卦后会自动保存在这里", fontSize = 13.sp, color = AppTheme.secondaryText, en = "Casts you make are saved here")
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
                            ui("${items.size} 次", "${items.size} times"),
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
                        verificationSummary(items, LocalAppLanguage.current)?.let { summary ->
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
                Spacer(Modifier.width(8.dp))
                PaperChevron()
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
    initialDigitalTab: Boolean,
    initialPositionFilter: MovingPositionFilter,
    initialCountFilter: MovingCountFilter,
    filterResetKey: Int,
    onBack: () -> Unit,
    onOpenRecord: (String) -> Unit,
    onDelete: (ReadingRecord) -> Unit,
) {
    var digitalTab by remember(filterResetKey) { mutableStateOf(initialDigitalTab) }
    var positionFilter by remember(filterResetKey) { mutableStateOf(initialPositionFilter) }
    var countFilter by remember(filterResetKey) { mutableStateOf(initialCountFilter) }
    val hex = container.hexagramStore.hexagram(primaryNumber)
    val methodFiltered = records.filter { rec ->
        if (digitalTab) rec.method.isDigital else rec.method == CastingMethod.COIN
    }
    val visible = methodFiltered.filter { rec ->
        if (digitalTab) positionFilter.matches(rec.movingPositions)
        else countFilter.matches(rec.movingPositions.size)
    }

    Column(Modifier.fillMaxSize()) {
        PaperBackHeader(
            title = "${hexTitle(hex, primaryNumber)} · ${ui("${records.size} 次", "${records.size} times")}",
            onBack = onBack,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .background(AppTheme.cardFill)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PaperSegmentedRow(
                options = listOf(ui("数字起卦", "Three numbers"), ui("金钱起卦", "Three coins")),
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
                            label = ui(filter.zh, filter.en),
                            selected = positionFilter == filter,
                            onClick = { positionFilter = filter },
                        )
                    }
                } else {
                    MovingCountFilter.entries.forEach { filter ->
                        PaperChip(
                            label = ui(filter.zh, filter.en),
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
                    if (digitalTab) {
                        ui("该卦尚无数字或时间起卦记录", "No three-number or time casts for this hexagram")
                    } else {
                        ui("该卦尚无金钱卦记录", "No coin casts for this hexagram")
                    },
                    color = AppTheme.secondaryText,
                    fontSize = 15.sp,
                )
            }
            visible.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("无匹配记录", color = AppTheme.secondaryText, fontSize = 15.sp, en = "No matching records")
            }
            else -> GroupedRecordList(
                records = visible,
                store = container.hexagramStore,
                onOpenRecord = onOpenRecord,
                onDelete = onDelete,
            )
        }
    }
}

@Composable
private fun GroupedRecordList(
    records: List<ReadingRecord>,
    store: HexagramStore,
    onOpenRecord: (String) -> Unit,
    onDelete: (ReadingRecord) -> Unit,
) {
    var revealedId by remember { mutableStateOf<String?>(null) }
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
                SwipeRevealDelete(
                    revealed = revealedId == rec.id,
                    onRevealedChange = { open ->
                        revealedId = if (open) rec.id else if (revealedId == rec.id) null else revealedId
                    },
                    onDelete = {
                        revealedId = null
                        onDelete(rec)
                    },
                ) {
                    HistoryRecordRow(
                        rec = rec,
                        store = store,
                        onClick = {
                            if (revealedId == rec.id) {
                                revealedId = null
                            } else {
                                onOpenRecord(rec.id)
                            }
                        },
                    )
                }
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
        Spacer(Modifier.width(8.dp))
        PaperChevron()
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
        status.localizedName(LocalAppLanguage.current),
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

@Composable
private fun hexTitle(hex: Hexagram?, number: Int): String {
    val language = LocalAppLanguage.current
    return hex?.listLabel(language) ?: numberLabel(language, number)
}

private fun digitalMovingLabel(record: ReadingRecord): String? {
    if (!record.method.isDigital) return null
    if (record.movingPositions.size != 1) return null
    val position = record.movingPositions.first()
    return MovingPositionFilter.from(position)?.zh
}

private fun verificationColor(status: VerificationStatus): Color = when (status) {
    VerificationStatus.NONE -> Color.Gray
    VerificationStatus.FULFILLED -> Color(0xFF338C59)
    VerificationStatus.PARTIAL -> Color(0xFFBF8026)
    VerificationStatus.UNFULFILLED -> Color(0xFFA64040)
}

private fun verificationSummary(records: List<ReadingRecord>, language: AppLanguage): String? {
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
        if (fulfilled > 0) add(language.ui("应验 $fulfilled", "Fulfilled $fulfilled"))
        if (partial > 0) add(language.ui("部分 $partial", "Partly $partial"))
        if (unfulfilled > 0) add(language.ui("未应验 $unfulfilled", "Not fulfilled $unfulfilled"))
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
