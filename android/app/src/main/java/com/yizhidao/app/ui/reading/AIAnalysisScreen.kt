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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yizhidao.CastResult
import com.yizhidao.HexagramStore
import com.yizhidao.app.ai.SavedAIAnalysis
import com.yizhidao.app.ai.SavedAIAnalysisStore
import com.yizhidao.app.ai.SavedAIContent
import com.yizhidao.app.ai.SavedAIFollowUp
import com.yizhidao.app.ai.aiAdviceDisplayItems
import com.yizhidao.app.auth.AuthApi
import com.yizhidao.app.auth.LocalAuthStore
import com.yizhidao.app.ui.theme.AppTheme
import com.yizhidao.app.ui.theme.PaperBackHeader
import com.yizhidao.app.ui.theme.PaperTextField
import com.yizhidao.app.ui.theme.Text
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

    val canSendFollowup = draft.trim().isNotEmpty() &&
        analysis != null &&
        !isLoading &&
        !isFollowupLoading

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

    fun runAnalysis() {
        val token = authStore.session.value.accessToken
        if (token.isNullOrBlank()) {
            errorMessage = "请先登录"
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
            errorMessage = "请先登录"
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

    LaunchedEffect(saved?.id) {
        if (saved == null && analysis == null) {
            runAnalysis()
        }
    }

    Column(Modifier.fillMaxSize()) {
        PaperBackHeader(
            title = "问答",
            onBack = onBack,
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(AppTheme.cardFill, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                hexagramStore.hexagram(result.primaryNumber)?.let { hex ->
                    Text(
                        "${hex.symbol} ${hex.name}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.ink,
                        style = AppTheme.compactText,
                    )
                }
                result.question?.takeIf { it.isNotBlank() }?.let { q ->
                    Text("所问：$q", fontSize = 16.sp, color = AppTheme.ink, style = AppTheme.compactText)
                }
            }

            if (isLoading) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = AppTheme.accent,
                        strokeWidth = 2.dp,
                    )
                    Text(
                        "解读中…",
                        fontSize = 15.sp,
                        color = AppTheme.secondaryText,
                        modifier = Modifier.padding(start = 10.dp),
                        style = AppTheme.compactText,
                    )
                }
            }

            analysis?.let { item ->
                AnalysisCard("事情背景", item.summary)
                AnalysisCard("当下", item.focus)
                if (item.direction.isNotBlank()) {
                    AnalysisCard("方向", item.direction)
                }
                val adviceItems = aiAdviceDisplayItems(item.advice, item.risks)
                if (adviceItems.isNotEmpty()) {
                    BulletCard("建议", adviceItems)
                }
                if (item.askNext.isNotEmpty() && followUps.isEmpty() && !isLoading && !isFollowupLoading) {
                    AskNextCard(
                        questions = item.askNext,
                        onPick = { sendFollowup(it) },
                    )
                }
            }

            followUps.forEachIndexed { index, turn ->
                val isLatest = index == followUps.lastIndex
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    AnalysisCard("回复", turn.assistant)
                    if (turn.advice.isNotEmpty()) {
                        BulletCard("建议", turn.advice)
                    }
                    if (isLatest && !isFollowupLoading) {
                        val nextQuestions = turn.askNext.ifEmpty { analysis?.askNext.orEmpty() }
                        if (nextQuestions.isNotEmpty()) {
                            AskNextCard(
                                questions = nextQuestions,
                                onPick = { sendFollowup(it) },
                            )
                        }
                    }
                }
            }

            if (isFollowupLoading) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = AppTheme.accent,
                        strokeWidth = 2.dp,
                    )
                    Text(
                        "回复中…",
                        fontSize = 13.sp,
                        color = AppTheme.secondaryText,
                        modifier = Modifier.padding(start = 8.dp),
                        style = AppTheme.compactText,
                    )
                }
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
                        contentDescription = "发送",
                        tint = if (canSendFollowup) Color.White else AppTheme.disabledText,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AnalysisCard(title: String, text: String) {
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
        )
        Text(text, fontSize = 16.sp, color = AppTheme.ink, style = AppTheme.compactText)
    }
}

@Composable
private fun BulletCard(title: String, items: List<String>) {
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
        )
        items.forEachIndexed { index, item ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${index + 1}.",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.accent,
                    style = AppTheme.compactText,
                )
                Text(item, fontSize = 16.sp, color = AppTheme.ink, style = AppTheme.compactText)
            }
        }
    }
}

@Composable
private fun AskNextCard(questions: List<String>, onPick: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(AppTheme.cardFill, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "可以接着问",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppTheme.accent,
            style = AppTheme.compactText,
        )
        Text(
            "点一句直接发出。",
            fontSize = 12.sp,
            color = AppTheme.secondaryText,
            style = AppTheme.compactText,
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
