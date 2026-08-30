package com.yizhidao.app.ui.cases

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.yizhidao.app.lang.LocalAppLanguage
import com.yizhidao.app.lang.listLabel
import com.yizhidao.app.lang.numberLabel
import com.yizhidao.app.ui.theme.Text
import com.yizhidao.app.ui.theme.ui
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yizhidao.CaseStudy
import com.yizhidao.Hexagram
import com.yizhidao.HexagramStore
import com.yizhidao.app.AppContainer
import com.yizhidao.app.ui.reading.HexagramReadingBody
import com.yizhidao.app.ui.theme.AppTheme
import com.yizhidao.app.ui.theme.PaperBackHeader
import com.yizhidao.app.ui.theme.PaperChevron
import kotlinx.coroutines.launch

private enum class PositionFilter(val zh: String, val en: String, val position: Int?) {
    All("全部", "All", null),
    Chu("初", "1st", 1),
    Er("二", "2nd", 2),
    San("三", "3rd", 3),
    Si("四", "4th", 4),
    Wu("五", "5th", 5),
    Shang("上", "Top", 6);

    fun matches(movingPositions: List<Int>): Boolean {
        val p = position ?: return true
        return p in movingPositions
    }

    companion object {
        fun from(position: Int): PositionFilter? = entries.find { it.position == position }
    }
}

@Composable
fun CaseListScreen(
    container: AppContainer,
    onBack: (() -> Unit)? = null,
) {
    val cases by container.caseRepository.cases.collectAsState()
    var selectedHex by remember { mutableStateOf<Int?>(null) }
    var selectedCase by remember { mutableStateOf<CaseStudy?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        container.caseRepository.refresh()
    }

    when {
        selectedCase != null -> CaseDetailScreen(
            study = selectedCase!!,
            container = container,
            onBack = { selectedCase = null },
        )
        selectedHex != null -> CaseGroupDetailScreen(
            container = container,
            cases = cases.filter { it.number == selectedHex },
            number = selectedHex!!,
            onBack = { selectedHex = null },
            onOpenCase = { selectedCase = it },
        )
        else -> CaseGroupListScreen(
            container = container,
            cases = cases,
            onBack = onBack,
            onOpenGroup = { selectedHex = it },
            onRefresh = { scope.launch { container.caseRepository.refresh() } },
        )
    }
}

@Composable
private fun CaseGroupListScreen(
    container: AppContainer,
    cases: List<CaseStudy>,
    onBack: (() -> Unit)? = null,
    onOpenGroup: (Int) -> Unit,
    onRefresh: () -> Unit,
) {
    val grouped = cases.groupBy { it.number }.toSortedMap()
    val refreshButton: @Composable () -> Unit = {
        Text(
            "刷新",
            fontSize = 15.sp,
            color = AppTheme.accent,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onRefresh)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            style = AppTheme.compactText,
            en = "Refresh",
        )
    }
    Column(Modifier.fillMaxSize()) {
        if (onBack != null) {
            PaperBackHeader(
                title = "案例",
                titleEn = "Cases",
                onBack = onBack,
                trailing = refreshButton,
            )
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "案例",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.ink,
                    style = AppTheme.compactText,
                    modifier = Modifier.weight(1f),
                    en = "Cases",
                )
                refreshButton()
            }
        }
        if (cases.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.AutoMirrored.Outlined.MenuBook,
                        contentDescription = null,
                        tint = AppTheme.secondaryText,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("暂无案例", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = AppTheme.ink, en = "No cases")
                    Spacer(Modifier.height(6.dp))
                    Text("案例数据未加载", fontSize = 13.sp, color = AppTheme.secondaryText, en = "Cases didn’t load")
                }
            }
        } else {
            val entries = grouped.entries.toList()
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
                itemsIndexed(entries, key = { _, item -> item.key }) { index, (number, items) ->
                    val hex = container.hexagramStore.hexagram(number)
                    val shape = when {
                        entries.size == 1 -> AppTheme.cardShape
                        index == 0 -> RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
                        index == entries.lastIndex -> RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)
                        else -> RectangleShape
                    }
                    Column(Modifier.clip(shape).background(AppTheme.cardFill)) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpenGroup(number) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
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
                                ui("${items.size} 例", "${items.size} cases"),
                                fontSize = 15.sp,
                                color = AppTheme.secondaryText,
                                style = AppTheme.compactText,
                            )
                            Spacer(Modifier.width(8.dp))
                            PaperChevron()
                        }
                        if (index < entries.lastIndex) {
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
    }
}

@Composable
private fun CaseGroupDetailScreen(
    container: AppContainer,
    cases: List<CaseStudy>,
    number: Int,
    onBack: () -> Unit,
    onOpenCase: (CaseStudy) -> Unit,
) {
    var positionFilter by remember { mutableStateOf(PositionFilter.All) }
    val hex = container.hexagramStore.hexagram(number)
    val visible = cases.filter { positionFilter.matches(it.movingPositions) }

    Column(Modifier.fillMaxSize()) {
        PaperBackHeader(
            title = "${hexTitle(hex, number)} · ${ui("${cases.size} 例", "${cases.size} cases")}",
            onBack = onBack,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .background(AppTheme.cardFill)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PositionFilter.entries.forEach { filter ->
                    Text(
                        ui(filter.zh, filter.en),
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (positionFilter == filter) AppTheme.accent else Color.Black.copy(alpha = 0.06f))
                            .clickable { positionFilter = filter }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (positionFilter == filter) Color.White else AppTheme.ink,
                        maxLines = 1,
                        style = AppTheme.compactText,
                    )
                }
            }
        }

        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("无匹配案例", color = AppTheme.secondaryText, fontSize = 15.sp, en = "No matching cases")
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp, top = 12.dp)) {
                itemsIndexed(visible, key = { _, study -> study.file }) { index, study ->
                    val shape = when {
                        visible.size == 1 -> AppTheme.cardShape
                        index == 0 -> RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
                        index == visible.lastIndex -> RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)
                        else -> RectangleShape
                    }
                    Column(Modifier.clip(shape).background(AppTheme.cardFill)) {
                        CaseRow(
                            study = study,
                            store = container.hexagramStore,
                            onClick = { onOpenCase(study) },
                        )
                        if (index < visible.lastIndex) {
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
    }
}

@Composable
private fun CaseRow(
    study: CaseStudy,
    store: HexagramStore,
    onClick: () -> Unit,
) {
    val primary = store.hexagram(study.number)
    val resulting = study.resultingNumber?.let { store.hexagram(it) }
    val movingLabel = PositionFilter.from(study.movingPositions.singleOrNull() ?: -1)?.zh
    val summary = study.verification.trim().takeIf { it.isNotEmpty() && it != "原文未提及" }

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
                    hexTitle(primary, study.number),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = AppTheme.compactText,
                )
                when {
                    study.resultingNumber != null -> {
                        ChangeArrow(movingLabel)
                        Text(
                            hexTitle(resulting, study.resultingNumber!!),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppTheme.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                            style = AppTheme.compactText,
                        )
                    }
                    study.movingPositions.isEmpty() -> {
                        Text(
                            "六爻不变",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppTheme.secondaryText,
                            style = AppTheme.compactText,
                            en = "No changing lines",
                        )
                    }
                    else -> {
                        Text(
                            ui("${study.movingPositions.size} 爻变", "${study.movingPositions.size} changing"),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppTheme.secondaryText,
                            style = AppTheme.compactText,
                        )
                    }
                }
            }
            if (study.question.isNotBlank()) {
                Text(
                    study.question,
                    fontSize = 15.sp,
                    color = AppTheme.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = AppTheme.compactText,
                )
            }
            if (summary != null) {
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
        Spacer(Modifier.width(8.dp))
        PaperChevron()
    }
}

@Composable
private fun CaseDetailScreen(study: CaseStudy, container: AppContainer, onBack: () -> Unit) {
    val language = LocalAppLanguage.current
    val hex = container.hexagramStore.hexagram(study.number)
    val title = if (hex != null) "${hex.listLabel(language)}${study.position}" else study.position
    Column(Modifier.fillMaxSize()) {
        PaperBackHeader(title = title, onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (study.background.isNotBlank()) CaseBlock("背景", "Background", study.background)
            if (study.question.isNotBlank()) CaseBlock("所问何事", "What you ask", study.question)
            if (study.verification.isNotBlank()) CaseBlock("验证结果", "Outcome", study.verification)
            if (study.explanation.isNotBlank()) CaseBlock("讲师解读", "Teacher’s reading", study.explanation)
            HexagramReadingBody(
                primaryNumber = study.number,
                resultingNumber = study.resultingNumber,
                lines = study.lines,
                movingPositions = study.movingPositions,
                store = container.hexagramStore,
                imaStore = container.imaExplanationStore,
            )
        }
    }
}

@Composable
private fun CaseBlock(title: String, titleEn: String, body: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(AppTheme.cardFill, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppTheme.accent,
            style = AppTheme.compactText,
            en = titleEn,
        )
        Text(body, fontSize = 16.sp, color = AppTheme.ink, lineHeight = 24.sp)
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
private fun hexTitle(hex: Hexagram?, number: Int): String {
    val language = LocalAppLanguage.current
    return hex?.listLabel(language) ?: numberLabel(language, number)
}
