package com.yizhidao.app.ui.reading

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yizhidao.CastResult
import com.yizhidao.ReadingRecord
import com.yizhidao.VerificationStatus
import com.yizhidao.app.AppContainer
import androidx.compose.runtime.collectAsState
import com.yizhidao.app.ui.me.LoginScreen
import com.yizhidao.app.ui.theme.AIFloatingButton
import com.yizhidao.app.ui.theme.AppTheme
import com.yizhidao.app.ui.theme.PaperBackHeader
import com.yizhidao.app.ui.theme.PaperHeaderButton
import com.yizhidao.app.ui.theme.PaperStackIcon
import com.yizhidao.app.ui.theme.PaperSegmentedRow
import com.yizhidao.app.ui.theme.PaperTextField
import com.yizhidao.app.ui.theme.Text
import com.yizhidao.app.ui.theme.zh
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFmt = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm:ss").withZone(ZoneId.systemDefault())

@Composable
fun ResultScreen(
    result: CastResult,
    isNew: Boolean,
    container: AppContainer,
    onBack: () -> Unit,
    onTabBarVisible: (Boolean) -> Unit = {},
    onOpenSimilar: ((CastResult) -> Unit)? = null,
    existing: ReadingRecord? = null,
) {
    val scope = rememberCoroutineScope()
    var record by remember { mutableStateOf(existing) }
    var question by remember { mutableStateOf(existing?.question ?: result.question ?: "") }
    var status by remember { mutableStateOf(existing?.verificationStatus ?: VerificationStatus.NONE) }
    var note by remember { mutableStateOf(existing?.verificationNote ?: "") }
    var showAI by remember { mutableStateOf(false) }
    var showLoginForAI by remember { mutableStateOf(false) }
    val session by container.authStore.session.collectAsState()

    LaunchedEffect(showAI) {
        onTabBarVisible(!showAI)
    }
    DisposableEffect(Unit) {
        onDispose { onTabBarVisible(true) }
    }

    val resultForAnalysis = result.copy(
        question = question.trim().ifEmpty { null },
    )

    if (showAI) {
        AIAnalysisScreen(
            result = resultForAnalysis,
            hexagramStore = container.hexagramStore,
            authStore = container.authStore,
            analysisStore = container.savedAIStore,
            onBack = { showAI = false },
        )
        return
    }
    if (showLoginForAI) {
        LoginScreen(
            authStore = container.authStore,
            onBack = { showLoginForAI = false },
            onSuccess = {
                showLoginForAI = false
                showAI = true
            },
        )
        return
    }

    LaunchedEffect(result.createdAt, existing?.id) {
        if (existing != null) {
            record = existing
            return@LaunchedEffect
        }
        if (!isNew) return@LaunchedEffect
        val inserted = ReadingRecord.from(result)
        container.readingRepository.insert(inserted)
        record = inserted
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            PaperBackHeader(
                title = "卦象结果",
                onBack = onBack,
                trailing = if (onOpenSimilar != null) {
                    {
                        PaperHeaderButton(
                            onClick = { onOpenSimilar(result) },
                            contentDescription = zh("查看同类卦"),
                        ) {
                            PaperStackIcon()
                        }
                    }
                } else {
                    null
                },
            )
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .padding(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(AppTheme.cardFill, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Outlined.WorkspacePremium,
                            contentDescription = null,
                            tint = AppTheme.ink,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            result.method.displayName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppTheme.ink,
                            style = AppTheme.compactText,
                        )
                    }
                    Text(
                        "占卦时间：${timeFmt.format(result.createdAt)}",
                        fontSize = 13.sp,
                        color = AppTheme.secondaryText,
                        style = AppTheme.compactText,
                    )
                    if (record != null) {
                        PaperTextField(
                            value = question,
                            onValueChange = {
                                question = it
                                val trimmed = it.trim().ifEmpty { null }
                                record?.let { rec ->
                                    scope.launch { container.readingRepository.updateQuestion(rec.id, trimmed) }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = "所问何事（可选）",
                            singleLine = false,
                            minLines = 2,
                            maxLines = 5,
                        )
                    } else if (!result.question.isNullOrBlank()) {
                        Text(
                            "所问：${result.question}",
                            fontSize = 16.sp,
                            color = AppTheme.ink,
                        )
                    }
                    result.numbers?.let { nums ->
                        Text(
                            "取数：${nums.joinToString(" · ")}",
                            fontSize = 12.sp,
                            color = AppTheme.secondaryText,
                            style = AppTheme.compactText,
                        )
                    }
                }

                if (record != null) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(AppTheme.cardFill, RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        PaperSegmentedRow(
                            options = VerificationStatus.entries.map { it.displayName },
                            selectedIndex = VerificationStatus.entries.indexOf(status),
                            onSelect = { index ->
                                val item = VerificationStatus.entries[index]
                                status = item
                                record?.let { rec ->
                                    scope.launch {
                                        container.readingRepository.updateVerification(
                                            rec.id,
                                            item,
                                            note.trim().ifEmpty { null },
                                        )
                                    }
                                }
                            },
                        )
                        PaperTextField(
                            value = note,
                            onValueChange = {
                                note = it
                                record?.let { rec ->
                                    scope.launch {
                                        container.readingRepository.updateVerification(
                                            rec.id,
                                            status,
                                            it.trim().ifEmpty { null },
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = "验证结果（可选）",
                            singleLine = false,
                            minLines = 2,
                            maxLines = 5,
                        )
                    }
                }

                HexagramReadingBody(
                    result = result,
                    store = container.hexagramStore,
                    imaStore = container.imaExplanationStore,
                )
            }
        }

        AIFloatingButton(
            onClick = {
                if (session.isLoggedIn) showAI = true else showLoginForAI = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 24.dp),
        )
    }
}
