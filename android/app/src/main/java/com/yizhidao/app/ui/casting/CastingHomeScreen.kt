package com.yizhidao.app.ui.casting

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.Icon
import com.yizhidao.app.R
import com.yizhidao.app.ui.theme.Text
import com.yizhidao.app.ui.theme.zh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yizhidao.CastResult
import com.yizhidao.app.AppContainer
import com.yizhidao.app.ui.theme.AppTheme
import com.yizhidao.app.ui.theme.PaperChevron
import com.yizhidao.app.ui.theme.PaperSegmentedRow
import com.yizhidao.app.ui.theme.PaperTextField

private val ritualSteps = listOf(
    "净手，择一静处，坐稳，桌面整洁无杂物。",
    "静穆身心，敬慎其意。",
    "行礼，默祷：爻变化之神在上，弟子某某某，今有某事（简单扼要讲清楚）不知休咎，望示一圣卦指示。",
    "得卦后，行礼：感谢爻变化之神的指示，弟子退。",
    "然后把起卦工具收好，开始解卦。",
)

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
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.app_name),
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.ink,
                lineHeight = 41.sp,
                style = AppTheme.compactText,
            )
            Text(
                "君子居则观象玩辞，动则观变玩占",
                fontSize = 15.sp,
                color = AppTheme.secondaryText,
                style = AppTheme.compactText,
            )
        }

        Column {
            Row(
                Modifier
                    .clickable { showRitual = !showRitual }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "起卦礼仪",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.accent,
                    style = AppTheme.compactText,
                )
                val chevronTurn by animateFloatAsState(
                    targetValue = if (showRitual) 90f else 0f,
                    label = "ritualChevron",
                )
                PaperChevron(
                    modifier = Modifier.padding(start = 6.dp),
                    color = AppTheme.accent.copy(alpha = 0.7f),
                    height = 11.dp,
                    rotation = chevronTurn,
                )
            }
            AnimatedVisibility(showRitual) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    ritualSteps.forEachIndexed { index, step ->
                        Text(
                            "${index + 1}、$step",
                            fontSize = 12.sp,
                            color = AppTheme.ink.copy(alpha = 0.85f),
                            lineHeight = 17.sp,
                            style = AppTheme.compactText,
                        )
                    }
                }
            }
        }

        PaperTextField(
            value = question,
            onValueChange = { question = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = "所问何事（必填）",
            singleLine = false,
            minLines = 2,
            maxLines = 5,
            trailing = if (question.isNotEmpty()) {
                {
                    Icon(
                        Icons.Filled.Cancel,
                        contentDescription = zh("清除所问"),
                        tint = AppTheme.secondaryText,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { question = "" },
                    )
                }
            } else {
                null
            },
        )

        PaperSegmentedRow(
            options = listOf("数字起卦", "金钱起卦"),
            selectedIndex = if (methodIsDigital) 0 else 1,
            onSelect = { methodIsDigital = it == 0 },
        )

        Column(
            Modifier
                .fillMaxWidth()
                .background(AppTheme.cardFill, RoundedCornerShape(16.dp))
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
