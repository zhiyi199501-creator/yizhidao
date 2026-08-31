package com.yizhidao.app.ui.qa

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yizhidao.CastResult
import com.yizhidao.app.AppContainer
import com.yizhidao.app.ai.SavedAIAnalysis
import com.yizhidao.app.ui.reading.AIAnalysisScreen
import com.yizhidao.app.ui.reading.HexagramChangeArrow
import com.yizhidao.app.ui.reading.ResultScreen
import com.yizhidao.digitalMovingYaoLabel
import com.yizhidao.app.lang.LocalAppLanguage
import com.yizhidao.app.lang.listLabel
import com.yizhidao.app.lang.numberLabel
import com.yizhidao.app.ui.theme.AppTheme
import com.yizhidao.app.ui.theme.PaperChevron
import com.yizhidao.app.ui.theme.SwipeRevealDelete
import com.yizhidao.app.ui.theme.Text
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val listTimeFmt = DateTimeFormatter.ofPattern("yyyy/M/d HH:mm").withZone(ZoneId.systemDefault())

@Composable
fun QAListScreen(
    container: AppContainer,
    onTabBarVisible: (Boolean) -> Unit = {},
    onOpenSimilar: ((CastResult) -> Unit)? = null,
) {
    val language = LocalAppLanguage.current
    val items by container.savedAIStore.items.collectAsState()
    var opened by remember { mutableStateOf<SavedAIAnalysis?>(null) }
    var showResult by remember { mutableStateOf(false) }
    var revealedId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(opened) {
        onTabBarVisible(opened == null)
    }
    DisposableEffect(Unit) {
        onDispose { onTabBarVisible(true) }
    }

    val current = opened

    if (current != null && showResult) {
        ResultScreen(
            result = current.toCastResult(),
            isNew = false,
            container = container,
            onBack = { showResult = false },
            onOpenSimilar = onOpenSimilar,
        )
        return
    }
    if (current != null) {
        AIAnalysisScreen(
            result = current.toCastResult(),
            saved = current,
            readingRecordId = current.readingRecordId,
            hexagramStore = container.hexagramStore,
            authStore = container.authStore,
            analysisStore = container.savedAIStore,
            onBack = {
                showResult = false
                opened = null
            },
            onOpenResult = { showResult = true },
            onOpenSimilar = onOpenSimilar,
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "问答",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = AppTheme.ink,
            style = AppTheme.compactText,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            en = "Readings",
        )
        if (items.isEmpty()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "还没有解读",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.ink,
                    style = AppTheme.compactText,
                    en = "No readings yet",
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "起卦后点问，解读会自动出现在这里",
                    fontSize = 13.sp,
                    color = AppTheme.secondaryText,
                    style = AppTheme.compactText,
                    en = "After you cast, tap Ask. Readings appear here.",
                )
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)) {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(AppTheme.cardShape)
                            .background(Color.White.copy(alpha = 0.92f)),
                    ) {
                        items.forEachIndexed { index, item ->
                            SwipeRevealDelete(
                                revealed = revealedId == item.id,
                                onRevealedChange = { open ->
                                    revealedId = if (open) {
                                        item.id
                                    } else if (revealedId == item.id) {
                                        null
                                    } else {
                                        revealedId
                                    }
                                },
                                onDelete = {
                                    revealedId = null
                                    container.savedAIStore.remove(item.id)
                                },
                                contentBackground = Color.White,
                            ) {
                                val hex = container.hexagramStore.hexagram(item.primaryNumber)
                                val resultingTitle = item.resultingNumber?.let { n ->
                                    container.hexagramStore.hexagram(n)?.listLabel(language)
                                        ?: numberLabel(language, n)
                                }
                                QAHistoryRow(
                                    item = item,
                                    title = hex?.listLabel(language) ?: numberLabel(language, item.primaryNumber),
                                    resultingTitle = resultingTitle,
                                    onClick = {
                                        if (revealedId == item.id) {
                                            revealedId = null
                                        } else {
                                            opened = item
                                        }
                                    },
                                )
                            }
                            if (index < items.lastIndex) {
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
}

@Composable
private fun QAHistoryRow(
    item: SavedAIAnalysis,
    title: String,
    resultingTitle: String?,
    onClick: () -> Unit,
) {
    val question = item.question?.takeIf { it.isNotBlank() }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = AppTheme.compactText,
                )
                resultingTitle?.let { resulting ->
                    HexagramChangeArrow(
                        digitalMovingYaoLabel(item.method, item.movingPositions),
                    )
                    Text(
                        resulting,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppTheme.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = AppTheme.compactText,
                    )
                }
            }
            Text(
                listTimeFmt.format(item.updatedAt),
                fontSize = 12.sp,
                color = AppTheme.secondaryText,
                style = AppTheme.compactText,
            )
            if (question != null) {
                Text(
                    question,
                    fontSize = 15.sp,
                    color = AppTheme.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = AppTheme.compactText,
                )
            } else {
                Text(
                    item.analysis.summary,
                    fontSize = 15.sp,
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
