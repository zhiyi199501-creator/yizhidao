package com.yizhidao.app.ui.casting

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yizhidao.CastResult
import com.yizhidao.app.AppContainer
import com.yizhidao.app.ui.theme.AppTheme

private val ritualSteps = listOf(
    "净手，择一静处，坐稳，桌面整洁无杂物。",
    "静穆身心，敬慎其意。",
    "行礼，默祷：爻变化之神在上，弟子某某某，今有某事（简单扼要讲清楚）不知休咎，望示一圣卦指示。",
    "得卦后，行礼：感谢爻变化之神的指示，弟子退。",
    "然后把起卦工具收好，开始解卦。",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastingHomeScreen(
    container: AppContainer,
    onResult: (CastResult) -> Unit,
) {
    var question by remember { mutableStateOf("") }
    var methodIsDigital by remember { mutableStateOf(true) }
    var showRitual by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text("易知道", style = androidx.compose.material3.MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "君子居则观象玩辞，动则观变玩占",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(16.dp))

        TextButton(onClick = { showRitual = !showRitual }) {
            Text("起卦礼仪", color = AppTheme.accent, fontWeight = FontWeight.SemiBold)
        }
        AnimatedVisibility(showRitual) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                ritualSteps.forEachIndexed { index, step ->
                    Text("${index + 1}、$step", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                }
            }
        }

        Row(verticalAlignment = Alignment.Top) {
            OutlinedTextField(
                value = question,
                onValueChange = { question = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("所问何事（可选）") },
                minLines = 2,
                maxLines = 5,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = AppTheme.fieldFill,
                    unfocusedContainerColor = AppTheme.fieldFill,
                    focusedBorderColor = AppTheme.fieldStroke,
                    unfocusedBorderColor = AppTheme.fieldStroke,
                ),
                shape = RoundedCornerShape(8.dp),
            )
            if (question.isNotEmpty()) {
                IconButton(onClick = { question = "" }) {
                    Icon(Icons.Filled.Close, contentDescription = "清除所问")
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = methodIsDigital,
                onClick = { methodIsDigital = true },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text("数字起卦") }
            SegmentedButton(
                selected = !methodIsDigital,
                onClick = { methodIsDigital = false },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text("金钱卦") }
        }

        Spacer(Modifier.height(16.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .background(AppTheme.cardFill, RoundedCornerShape(16.dp))
                .border(1.dp, AppTheme.fieldStroke, RoundedCornerShape(16.dp))
                .padding(16.dp),
        ) {
            if (methodIsDigital) {
                DigitalCastPanel(
                    question = question,
                    chinese = container.chineseDateSource,
                    onResult = onResult,
                )
            } else {
                CoinCastPanel(question = question, onResult = onResult)
            }
        }
    }
}
