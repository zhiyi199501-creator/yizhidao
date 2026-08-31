package com.yizhidao.app.ui.casting

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yizhidao.app.ui.theme.AppTheme
import com.yizhidao.app.ui.theme.Text

@Composable
fun MethodPickAct(
    question: String,
    onPick: (CastingIntent) -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RitualTopBar(onCancel = onCancel)
        Text(
            question,
            fontSize = 13.sp,
            color = AppTheme.secondaryText,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            style = AppTheme.compactText,
        )
        Text(
            "怎样取这一卦",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppTheme.accent,
            modifier = Modifier.padding(top = 8.dp),
            style = AppTheme.compactText,
            en = "How will you take this hexagram?",
        )
        Spacer(Modifier.weight(1f))
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MethodButton(
                title = "数字起卦",
                subtitle = "三个数定上卦、下卦、动爻",
                subtitleEn = "Three numbers",
                onClick = { onPick(CastingIntent.DigitalNumbers) },
            )
            MethodButton(
                title = "时间起卦",
                subtitle = "以此刻十二时辰取数",
                subtitleEn = "Time",
                onClick = { onPick(CastingIntent.DigitalTime) },
            )
            MethodButton(
                title = "金钱起卦",
                subtitle = "三枚铜钱摇六次",
                subtitleEn = "Three coins",
                onClick = { onPick(CastingIntent.Coin) },
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun MethodButton(
    title: String,
    subtitle: String,
    subtitleEn: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(
                width = 1.dp,
                color = AppTheme.accent.copy(alpha = 0.22f),
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppTheme.accent,
            style = AppTheme.compactText,
        )
        Text(
            subtitle,
            fontSize = 13.sp,
            color = AppTheme.secondaryText,
            modifier = Modifier.padding(top = 6.dp),
            style = AppTheme.compactText,
            en = subtitleEn,
        )
    }
}
