package com.yizhidao.app.ui.month

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yizhidao.CastResult
import com.yizhidao.HexagramStore
import com.yizhidao.MonthCastStats
import com.yizhidao.ReadingRecord
import com.yizhidao.app.AppContainer
import com.yizhidao.app.lang.LocalAppLanguage
import com.yizhidao.app.ui.history.HistoryGroupedColumn
import kotlinx.coroutines.launch
import com.yizhidao.app.ui.reading.ResultScreen
import com.yizhidao.app.ui.theme.AppTheme
import com.yizhidao.app.ui.theme.Text
import com.yizhidao.app.ui.theme.ui
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private val countOrange = Color(0xFFD16B2E)

@Composable
fun MonthStatsScreen(
    container: AppContainer,
    onTabBarVisible: (Boolean) -> Unit = {},
    onOpenSimilar: ((CastResult) -> Unit)? = null,
) {
    val records by container.readingRepository.records.collectAsState()
    val language = LocalAppLanguage.current
    val zone = remember { ZoneId.systemDefault() }
    val nowMonth = remember { YearMonth.now(zone) }
    var yearMonth by remember { mutableStateOf(nowMonth) }
    var selectedDay by remember { mutableIntStateOf(Instant.now().atZone(zone).dayOfMonth) }
    var opened by remember { mutableStateOf<ReadingRecord?>(null) }
    val scope = rememberCoroutineScope()
    val onDelete: (ReadingRecord) -> Unit = { rec ->
        scope.launch { container.readingRepository.archive(rec.id) }
    }

    val today = Instant.now().atZone(zone).toLocalDate()
    val snapshot = remember(records, yearMonth, today) {
        MonthCastStats.snapshot(records.map { it.createdAtEpochMs }, yearMonth, zone, Instant.now())
    }
    val earliest = remember(records) {
        records.minOfOrNull { Instant.ofEpochMilli(it.createdAtEpochMs).atZone(zone).toLocalDate() }
            ?.let { YearMonth.from(it) }
            ?: nowMonth
    }
    val dayRecords = remember(records, yearMonth, selectedDay) {
        records
            .filter {
                val date = Instant.ofEpochMilli(it.createdAtEpochMs).atZone(zone).toLocalDate()
                date.year == yearMonth.year && date.monthValue == yearMonth.monthValue && date.dayOfMonth == selectedDay
            }
            .sortedByDescending { it.createdAtEpochMs }
    }

    LaunchedEffect(yearMonth) {
        selectedDay = MonthCastStats.defaultSelectedDay(yearMonth, Instant.now(), snapshot.countsByDay, zone)
    }
    LaunchedEffect(opened) {
        onTabBarVisible(opened == null)
    }
    DisposableEffect(Unit) {
        onDispose { onTabBarVisible(true) }
    }

    val current = opened
    if (current != null) {
        ResultScreen(
            result = current.toCastResult(),
            isNew = false,
            container = container,
            existing = current,
            onBack = { opened = null },
            onOpenSimilar = onOpenSimilar,
        )
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MonthHeader(
            title = monthTitle(yearMonth, language.isEnglish),
            canGoPrev = yearMonth > earliest,
            canGoNext = yearMonth < nowMonth,
            onPrev = { yearMonth = yearMonth.minusMonths(1) },
            onNext = { yearMonth = yearMonth.plusMonths(1) },
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(snapshot.totalCount.toString(), ui("总次数", "Total"), Modifier.weight(1f))
            StatCard(snapshot.elapsedDays.toString(), ui("天数", "Days"), Modifier.weight(1f))
            StatCard(snapshot.formattedAverage, ui("日均", "Daily avg"), Modifier.weight(1f))
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.72f))
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        ) {
            CalendarGrid(
                yearMonth = yearMonth,
                selectedDay = selectedDay,
                countsByDay = snapshot.countsByDay,
                english = language.isEnglish,
                onSelect = { selectedDay = it },
            )
        }
        }
        DayDetail(
            yearMonth = yearMonth,
            selectedDay = selectedDay,
            records = dayRecords,
            english = language.isEnglish,
            store = container.hexagramStore,
            onOpen = { opened = it },
            onDelete = onDelete,
        )
    }
}

@Composable
private fun MonthHeader(
    title: String,
    canGoPrev: Boolean,
    canGoNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev, enabled = canGoPrev) {
            Icon(
                Icons.Outlined.ChevronLeft,
                contentDescription = ui("上个月", "Previous month"),
                tint = if (canGoPrev) AppTheme.accent else AppTheme.accent.copy(alpha = 0.25f),
            )
        }
        Text(
            title,
            modifier = Modifier.weight(1f),
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppTheme.ink,
            textAlign = TextAlign.Center,
            style = AppTheme.compactText,
        )
        IconButton(onClick = onNext, enabled = canGoNext) {
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = ui("下个月", "Next month"),
                tint = if (canGoNext) AppTheme.accent else AppTheme.accent.copy(alpha = 0.25f),
            )
        }
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.92f))
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            value,
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppTheme.accent,
            style = AppTheme.compactText,
        )
        Text(
            label,
            fontSize = 12.sp,
            color = AppTheme.secondaryText,
            style = AppTheme.compactText,
        )
    }
}

@Composable
private fun CalendarGrid(
    yearMonth: YearMonth,
    selectedDay: Int,
    countsByDay: Map<Int, Int>,
    english: Boolean,
    onSelect: (Int) -> Unit,
) {
    val headers = if (english) listOf("S", "M", "T", "W", "T", "F", "S") else listOf("日", "一", "二", "三", "四", "五", "六")
    val days = MonthCastStats.calendarDays(yearMonth)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            headers.forEach { title ->
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.secondaryText,
                    textAlign = TextAlign.Center,
                    style = AppTheme.compactText,
                )
            }
        }
        days.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (day != null) {
                            val count = countsByDay[day] ?: 0
                            val selected = selectedDay == day
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) AppTheme.accent else Color.Transparent)
                                    .clickable { onSelect(day) }
                                    .padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    day.toString(),
                                    fontSize = 15.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (selected) Color.White else AppTheme.ink,
                                    style = AppTheme.compactText,
                                )
                                if (count > 0) {
                                    Text(
                                        count.toString(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (selected) Color.White.copy(alpha = 0.9f) else countOrange,
                                        style = AppTheme.compactText,
                                    )
                                } else {
                                    Spacer(Modifier.height(14.dp))
                                }
                            }
                        } else {
                            Spacer(Modifier.height(44.dp))
                        }
                    }
                }
                repeat(7 - week.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DayDetail(
    yearMonth: YearMonth,
    selectedDay: Int,
    records: List<ReadingRecord>,
    english: Boolean,
    store: HexagramStore,
    onOpen: (ReadingRecord) -> Unit,
    onDelete: (ReadingRecord) -> Unit,
) {
    val count = records.size
    val title = if (english) {
        val month = yearMonth.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
        val noun = if (count == 1) "cast" else "casts"
        "$month $selectedDay · $count $noun"
    } else {
        "${yearMonth.monthValue}月${selectedDay}日 · $count 次"
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            title,
            modifier = Modifier.padding(horizontal = 4.dp),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppTheme.ink,
            style = AppTheme.compactText,
        )
        if (count == 0) {
            Text(
                "这一天还没有起卦",
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                fontSize = 15.sp,
                color = AppTheme.secondaryText,
                style = AppTheme.compactText,
                en = "No casts this day",
            )
        } else {
            HistoryGroupedColumn(
                records = records,
                store = store,
                onOpenRecord = { id -> records.find { it.id == id }?.let(onOpen) },
                onDelete = onDelete,
            )
        }
    }
}

private fun monthTitle(yearMonth: YearMonth, english: Boolean): String {
    if (english) {
        val month = yearMonth.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        return "$month ${yearMonth.year}"
    }
    return "${yearMonth.year}年${yearMonth.monthValue}月"
}
