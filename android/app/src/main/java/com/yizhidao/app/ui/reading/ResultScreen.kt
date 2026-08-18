package com.yizhidao.app.ui.reading

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yizhidao.CastResult
import com.yizhidao.ReadingRecord
import com.yizhidao.VerificationStatus
import com.yizhidao.app.AppContainer
import com.yizhidao.app.ui.theme.AppTheme
import com.yizhidao.app.ui.theme.PaperSegmentedRow
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFmt = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm:ss").withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    result: CastResult,
    isNew: Boolean,
    container: AppContainer,
    onBack: () -> Unit,
    onOpenSimilar: ((Int) -> Unit)? = null,
    existing: ReadingRecord? = null,
) {
    val scope = rememberCoroutineScope()
    var record by remember { mutableStateOf(existing) }
    var question by remember { mutableStateOf(existing?.question ?: result.question ?: "") }
    var status by remember { mutableStateOf(existing?.verificationStatus ?: VerificationStatus.NONE) }
    var note by remember { mutableStateOf(existing?.verificationNote ?: "") }

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

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("卦象结果") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (onOpenSimilar != null) {
                        IconButton(onClick = { onOpenSimilar(result.primaryNumber) }) {
                            Icon(Icons.Outlined.Layers, contentDescription = "查看同类卦")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
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
                Text(result.method.displayName, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                Text("占卦时间：${timeFmt.format(result.createdAt)}", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                if (record != null) {
                    OutlinedTextField(
                        value = question,
                        onValueChange = {
                            question = it
                            val trimmed = it.trim().ifEmpty { null }
                            record?.let { rec ->
                                scope.launch { container.readingRepository.updateQuestion(rec.id, trimmed) }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("所问何事（可选）") },
                        minLines = 2,
                    )
                } else if (!result.question.isNullOrBlank()) {
                    Text("所问：${result.question}")
                }
                result.numbers?.let { nums ->
                    Text("取数：${nums.joinToString(" · ")}", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
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
                    OutlinedTextField(
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
                        placeholder = { Text("验证结果（可选）") },
                        minLines = 2,
                    )
                }
            }

            HexagramReadingBody(result = result, store = container.hexagramStore)
            Spacer(Modifier.height(24.dp))
        }
    }
}
