package com.yizhidao.app.ui.reading

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yizhidao.CastResult
import com.yizhidao.HexagramStore
import com.yizhidao.ReadingGuide
import com.yizhidao.digitalMovingYaoLabel
import com.yizhidao.app.ai.AIAnswerFormatter
import com.yizhidao.app.ai.SavedAIAnalysis
import com.yizhidao.app.ai.SavedAIAnalysisStore
import com.yizhidao.app.ai.SavedAIContent
import com.yizhidao.app.ai.SavedAIFollowUp
import com.yizhidao.app.ai.aiAdviceDisplayItems
import com.yizhidao.app.auth.AuthApi
import com.yizhidao.app.auth.LocalAuthStore
import com.yizhidao.app.ui.theme.AppTheme
import com.yizhidao.app.ui.theme.PaperBackHeader
import com.yizhidao.app.ui.theme.PaperHeaderButton
import com.yizhidao.app.ui.theme.PaperStackIcon
import com.yizhidao.app.ui.theme.PaperTextField
import com.yizhidao.app.lang.LocalAppLanguage
import com.yizhidao.app.lang.listLabel
import com.yizhidao.app.lang.numberLabel
import com.yizhidao.app.ui.theme.Text
import com.yizhidao.app.ui.theme.ui
import kotlinx.coroutines.launch

@Composable
fun AIAnalysisScreen(
    result: CastResult,
    saved: SavedAIAnalysis? = null,
    readingRecordId: String? = null,
    hexagramStore: HexagramStore,
    authStore: LocalAuthStore,
    analysisStore: SavedAIAnalysisStore,
    onBack: () -> Unit,
    onOpenResult: (() -> Unit)? = null,
    onOpenSimilar: ((CastResult) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(saved == null) }
    var isFollowupLoading by remember { mutableStateOf(false) }
    var analysis by remember {
        mutableStateOf(
            saved?.analysis?.let {
                AuthApi.AIAnalyzeResponse.Analysis(
                    summary = it.summary,
                    focus = it.focus,
                    advice = it.advice,
                    direction = it.direction,
                    risks = it.risks,
                    askNext = it.askNext,
                )
            },
        )
    }
    var followUps by remember { mutableStateOf(saved?.followUps.orEmpty()) }
    var draft by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var savedID by remember { mutableStateOf(saved?.id) }
    val language = LocalAppLanguage.current

    val canSendFollowup = draft.trim().isNotEmpty() &&
        analysis != null &&
        !isLoading &&
        !isFollowupLoading
    val leadJingwen = remember(result.primaryNumber, result.resultingNumber, result.movingPositions) {
        ReadingGuide.leadJingwen(
            result.movingPositions,
            hexagramStore.hexagram(result.primaryNumber),
            result.resultingNumber?.let { hexagramStore.hexagram(it) },
        )
    }
    val openResult = onOpenResult ?: onBack

    fun persistCurrent(
        current: AuthApi.AIAnalyzeResponse.Analysis,
        currentFollowUps: List<SavedAIFollowUp>,
    ) {
        val content = SavedAIContent(
            summary = current.summary,
            focus = current.focus,
            advice = current.advice,
            direction = current.direction,
            risks = current.risks,
            askNext = current.askNext,
        )
        val existing = analysisStore.find(readingRecordId, result)
            ?: savedID?.let { id -> analysisStore.load().firstOrNull { it.id == id } }
        val item = SavedAIAnalysis.make(
            result = result,
            analysis = content,
            followUps = currentFollowUps,
            readingRecordId = readingRecordId ?: existing?.readingRecordId,
            existingId = existing?.id ?: savedID,
        )
        analysisStore.upsert(item)
        savedID = item.id
    }

    fun applySaved(existing: SavedAIAnalysis) {
        analysis = existing.analysis.let {
            AuthApi.AIAnalyzeResponse.Analysis(
                summary = it.summary,
                focus = it.focus,
                advice = it.advice,
                direction = it.direction,
                risks = it.risks,
                askNext = it.askNext,
            )
        }
        followUps = existing.followUps
        savedID = existing.id
        isLoading = false
    }

    fun runAnalysis() {
        val existing = analysisStore.find(readingRecordId, result)
        if (existing != null) {
            applySaved(existing)
            return
        }
        val token = authStore.session.value.accessToken
        if (token.isNullOrBlank()) {
            errorMessage = language.ui("请先登录", "Please sign in")
            isLoading = false
            return
        }
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                val response = AuthApi.analyzeReading(result, token)
                analysis = response.analysis
                followUps = emptyList()
                persistCurrent(response.analysis, emptyList())
            } catch (e: Exception) {
                errorMessage = AuthApi.describe(e)
            } finally {
                isLoading = false
            }
        }
    }

    fun sendFollowup(message: String, fromComposer: Boolean = false) {
        val token = authStore.session.value.accessToken
        val current = analysis
        val text = message.trim()
        if (token.isNullOrBlank()) {
            errorMessage = language.ui("请先登录", "Please sign in")
            return
        }
        if (current == null || text.isEmpty() || isLoading || isFollowupLoading) return
        if (fromComposer) draft = ""
        isFollowupLoading = true
        errorMessage = null
        scope.launch {
            try {
                val response = AuthApi.followupReading(
                    result = result,
                    analysis = current,
                    conversation = followUps,
                    message = text,
                    accessToken = token,
                )
                val nextFollowUps = followUps + SavedAIFollowUp(
                    user = text,
                    assistant = response.reply,
                    advice = response.advice,
                    askNext = response.askNext,
                )
                followUps = nextFollowUps
                persistCurrent(current, nextFollowUps)
            } catch (e: Exception) {
                draft = text
                errorMessage = AuthApi.describe(e)
            } finally {
                isFollowupLoading = false
            }
        }
    }

    LaunchedEffect(saved?.id, readingRecordId) {
        if (analysis != null) return@LaunchedEffect
        val existing = saved ?: analysisStore.find(readingRecordId, result)
        if (existing != null) {
            applySaved(existing)
        } else {
            runAnalysis()
        }
    }

    Column(Modifier.fillMaxSize()) {
        PaperBackHeader(
            title = "问答",
            titleEn = "Readings",
            onBack = onBack,
            trailing = if (onOpenSimilar != null) {
                {
                    PaperHeaderButton(
                        onClick = { onOpenSimilar(result) },
                        contentDescription = ui("同类", "Similar"),
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
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppTheme.cardFill)
                    .clickable(onClick = openResult)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HexagramPairTitle(
                    primaryNumber = result.primaryNumber,
                    resultingNumber = result.resultingNumber,
                    store = hexagramStore,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    movingLabel = digitalMovingYaoLabel(result.method, result.movingPositions),
                )
                result.question?.takeIf { it.isNotBlank() }?.let { q ->
                    Text(
                        q,
                        fontSize = 16.sp,
                        color = AppTheme.ink,
                        style = AppTheme.compactText,
                    )
                }
            }

            if (isLoading) {
                Text(
                    "正在玩辞…",
                    fontSize = 15.sp,
                    color = AppTheme.ink.copy(alpha = 0.35f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    textAlign = TextAlign.Center,
                    style = AppTheme.compactText,
                    en = "Reading the text…",
                )
            }

            analysis?.let { item ->
                leadJingwen?.let { Epigraph(it) }
                ReadingSection("事情背景", "Background", item.summary, prominent = false)
                ReadingSection("当下", "Now", item.focus, prominent = true)
                if (item.direction.isNotBlank()) {
                    ReadingSection("方向", "Direction", item.direction, prominent = false)
                }
                val adviceItems = aiAdviceDisplayItems(item.advice, item.risks)
                if (adviceItems.isNotEmpty()) {
                    BulletSection("建议", "Advice", adviceItems)
                }
                if (item.askNext.isNotEmpty() && followUps.isEmpty() && !isLoading && !isFollowupLoading) {
                    AskNextSection(
                        questions = item.askNext,
                        onPick = { sendFollowup(it) },
                    )
                }
            }

            followUps.forEachIndexed { index, turn ->
                val isLatest = index == followUps.lastIndex
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Text(
                            turn.user,
                            fontSize = 16.sp,
                            color = AppTheme.ink,
                            modifier = Modifier
                                .widthIn(max = 280.dp)
                                .background(AppTheme.accent.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            style = AppTheme.compactText,
                        )
                    }
                    val paragraphs = AIAnswerFormatter.paragraphs(turn.assistant)
                    paragraphs.forEach { paragraph ->
                        Text(
                            paragraph,
                            fontSize = 16.sp,
                            lineHeight = 27.sp,
                            color = AppTheme.ink,
                            style = AppTheme.compactText,
                        )
                    }
                    if (turn.advice.isNotEmpty()) {
                        BulletSection("建议", "Advice", aiAdviceDisplayItems(turn.advice))
                    }
                    if (isLatest && !isFollowupLoading) {
                        val nextQuestions = turn.askNext.ifEmpty { analysis?.askNext.orEmpty() }
                        if (nextQuestions.isNotEmpty()) {
                            AskNextSection(
                                questions = nextQuestions,
                                onPick = { sendFollowup(it) },
                            )
                        }
                    }
                }
            }

            if (isFollowupLoading) {
                Text(
                    "正在玩辞…",
                    fontSize = 15.sp,
                    color = AppTheme.ink.copy(alpha = 0.35f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    textAlign = TextAlign.Center,
                    style = AppTheme.compactText,
                    en = "Reading the text…",
                )
            }

            errorMessage?.let {
                Text(it, fontSize = 12.sp, color = AppTheme.yangRed, style = AppTheme.compactText)
            }
        }

        if (analysis != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding()
                    .background(AppTheme.cardFill)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PaperTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = "追问或补充背景",
                    placeholderEn = "Ask or add context",
                    singleLine = false,
                    minLines = 1,
                    maxLines = 4,
                )
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (canSendFollowup) AppTheme.accent else AppTheme.disabledFill)
                        .clickable(enabled = canSendFollowup) {
                            sendFollowup(draft, fromComposer = true)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = ui("发送", "Send"),
                        tint = if (canSendFollowup) Color.White else AppTheme.disabledText,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HexagramPairTitle(
    primaryNumber: Int,
    resultingNumber: Int?,
    store: HexagramStore,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    movingLabel: String? = null,
) {
    val language = LocalAppLanguage.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            store.hexagram(primaryNumber)?.listLabel(language) ?: numberLabel(language, primaryNumber),
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = AppTheme.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = AppTheme.compactText,
        )
        if (resultingNumber != null) {
            HexagramChangeArrow(movingLabel)
            Text(
                store.hexagram(resultingNumber)?.listLabel(language) ?: numberLabel(language, resultingNumber),
                fontSize = fontSize,
                fontWeight = fontWeight,
                color = AppTheme.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = AppTheme.compactText,
            )
        }
    }
}

@Composable
internal fun HexagramChangeArrow(movingLabel: String?) {
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
private fun Epigraph(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "主看",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppTheme.ink.copy(alpha = 0.35f),
            style = AppTheme.compactText,
            en = "Focus",
        )
        Text(
            text,
            fontSize = 16.sp,
            lineHeight = 26.sp,
            color = AppTheme.secondaryText,
            style = AppTheme.compactText,
        )
    }
}

@Composable
private fun ReadingSection(title: String, titleEn: String, text: String, prominent: Boolean) {
    val paragraphs = remember(text) { AIAnswerFormatter.paragraphs(text) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            title,
            fontSize = if (prominent) 17.sp else 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppTheme.accent,
            style = AppTheme.compactText,
            en = titleEn,
        )
        paragraphs.forEach { paragraph ->
            Text(
                paragraph,
                fontSize = if (prominent) 18.sp else 16.sp,
                lineHeight = if (prominent) 30.sp else 27.sp,
                color = AppTheme.ink,
                style = AppTheme.compactText,
            )
        }
    }
}

@Composable
private fun BulletSection(title: String, titleEn: String, items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppTheme.accent,
            style = AppTheme.compactText,
            en = titleEn,
        )
        items.forEachIndexed { index, item ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${index + 1}.",
                    fontSize = 15.sp,
                    lineHeight = 25.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.accent,
                    style = AppTheme.compactText,
                )
                Text(
                    item,
                    fontSize = 16.sp,
                    lineHeight = 25.sp,
                    color = AppTheme.ink,
                    style = AppTheme.compactText,
                )
            }
        }
    }
}

@Composable
private fun AskNextSection(questions: List<String>, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "可以接着问",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppTheme.accent,
            style = AppTheme.compactText,
            en = "Ask next",
        )
        Text(
            "点一句直接发出。",
            fontSize = 12.sp,
            color = AppTheme.secondaryText,
            style = AppTheme.compactText,
            en = "Tap a line to send it.",
        )
        questions.forEach { question ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, AppTheme.accent.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .clickable { onPick(question) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    question,
                    fontSize = 16.sp,
                    color = AppTheme.ink,
                    style = AppTheme.compactText,
                )
            }
        }
    }
}
