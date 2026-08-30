package com.yizhidao.app.ui.reading

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yizhidao.CastResult
import com.yizhidao.ReadingRecord
import com.yizhidao.VerificationStatus
import com.yizhidao.app.AppContainer
import com.yizhidao.app.ai.SavedAIAnalysis
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
import kotlinx.coroutines.delay
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
    var editingQuestion by remember { mutableStateOf(false) }
    val questionFocus = remember { FocusRequester() }
    var status by remember { mutableStateOf(existing?.verificationStatus ?: VerificationStatus.NONE) }
    var note by remember { mutableStateOf(existing?.verificationNote ?: "") }
    var showAI by remember { mutableStateOf(false) }
    var showLoginForAI by remember { mutableStateOf(false) }
    var didAutoOpenAI by remember { mutableStateOf(false) }
    var aiSaved by remember { mutableStateOf<SavedAIAnalysis?>(null) }
    var aiRecordId by remember { mutableStateOf<String?>(null) }
    var didSave by remember { mutableStateOf(existing != null || !isNew) }
    val session by container.authStore.session.collectAsState()

    LaunchedEffect(Unit) {
        onTabBarVisible(false)
    }
    DisposableEffect(Unit) {
        onDispose { onTabBarVisible(true) }
    }

    val resultForAnalysis = result.copy(
        question = question.trim().ifEmpty { null },
    )

    fun openAIAnalysis() {
        didAutoOpenAI = true
        var rec = record
        if (rec == null && isNew && !didSave) {
            didSave = true
            val inserted = ReadingRecord.from(result)
            scope.launch { container.readingRepository.insert(inserted) }
            record = inserted
            rec = inserted
        }
        aiRecordId = rec?.id
        aiSaved = container.savedAIStore.find(rec?.id, resultForAnalysis)
        if (aiSaved != null || session.isLoggedIn) {
            showAI = true
        } else {
            showLoginForAI = true
        }
    }

    LaunchedEffect(isNew) {
        if (!isNew || didAutoOpenAI) return@LaunchedEffect
        delay(2000)
        if (didAutoOpenAI || showAI || showLoginForAI || editingQuestion) return@LaunchedEffect
        openAIAnalysis()
    }

    if (showAI) {
        AIAnalysisScreen(
            result = resultForAnalysis,
            saved = aiSaved,
            readingRecordId = aiRecordId,
            hexagramStore = container.hexagramStore,
            authStore = container.authStore,
            analysisStore = container.savedAIStore,
            onBack = {
                aiSaved = container.savedAIStore.find(aiRecordId, resultForAnalysis)
                showAI = false
            },
            onOpenSimilar = onOpenSimilar,
        )
        return
    }
    if (showLoginForAI) {
        LoginScreen(
            authStore = container.authStore,
            onBack = { showLoginForAI = false },
            onSuccess = {
                var rec = record
                if (rec == null && isNew && !didSave) {
                    didSave = true
                    val inserted = ReadingRecord.from(result)
                    scope.launch { container.readingRepository.insert(inserted) }
                    record = inserted
                    rec = inserted
                }
                aiRecordId = rec?.id
                aiSaved = container.savedAIStore.find(rec?.id, resultForAnalysis)
                showLoginForAI = false
                showAI = true
            },
        )
        return
    }

    LaunchedEffect(result.createdAt, existing?.id) {
        if (existing != null) {
            record = existing
            didSave = true
            return@LaunchedEffect
        }
        if (!isNew || didSave) return@LaunchedEffect
        didSave = true
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
                        QuestionRow(
                            question = question,
                            editing = editingQuestion,
                            canCommit = question.trim().isNotEmpty(),
                            focus = questionFocus,
                            onQuestionChange = { question = it },
                            onBeginEdit = { editingQuestion = true },
                            onCommit = {
                                val trimmed = question.trim()
                                if (trimmed.isEmpty()) return@QuestionRow
                                question = trimmed
                                record?.let { rec ->
                                    scope.launch { container.readingRepository.updateQuestion(rec.id, trimmed) }
                                }
                                editingQuestion = false
                            },
                        )
                    } else if (!result.question.isNullOrBlank()) {
                        Text(
                            "所问：${result.question}",
                            fontSize = 16.sp,
                            color = AppTheme.ink,
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
                            placeholder = "验证结果",
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

    LaunchedEffect(editingQuestion) {
        if (editingQuestion) {
            delay(50)
            questionFocus.requestFocus()
        }
    }

        AIFloatingButton(
            onClick = { openAIAnalysis() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 24.dp),
        )
    }
}

@Composable
private fun QuestionRow(
    question: String,
    editing: Boolean,
    canCommit: Boolean,
    focus: FocusRequester,
    onQuestionChange: (String) -> Unit,
    onBeginEdit: () -> Unit,
    onCommit: () -> Unit,
) {
    val editLabel = zh("编辑所问")
    val doneLabel = zh("完成编辑所问")
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (editing) {
            PaperTextField(
                value = question,
                onValueChange = onQuestionChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focus),
                placeholder = "所问何事",
                singleLine = false,
                minLines = 2,
                maxLines = 5,
            )
            Icon(
                Icons.Outlined.Check,
                contentDescription = doneLabel,
                tint = AppTheme.accent.copy(alpha = if (canCommit) 1f else 0.35f),
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(22.dp)
                    .semantics { contentDescription = doneLabel }
                    .clickable(enabled = canCommit, onClick = onCommit),
            )
        } else {
            Text(
                question.ifBlank { "所问何事" },
                fontSize = 16.sp,
                color = if (question.isBlank()) AppTheme.secondaryText else AppTheme.ink,
                modifier = Modifier.weight(1f),
                style = AppTheme.compactText,
            )
            Icon(
                Icons.Outlined.Edit,
                contentDescription = editLabel,
                tint = AppTheme.accent,
                modifier = Modifier
                    .size(22.dp)
                    .semantics { contentDescription = editLabel }
                    .clickable(onClick = onBeginEdit),
            )
        }
    }
}
