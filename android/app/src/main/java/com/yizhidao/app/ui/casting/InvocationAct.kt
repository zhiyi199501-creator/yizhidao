package com.yizhidao.app.ui.casting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yizhidao.app.ui.theme.AppTheme
import com.yizhidao.app.ui.theme.PaperPrimaryButton
import com.yizhidao.app.ui.theme.Text
import kotlinx.coroutines.delay

@Composable
fun InvocationAct(
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val trimmed = text.trim()
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(350)
        focus.requestFocus()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(AppTheme.parchmentBrush)
            .statusBarsPadding()
            .imePadding()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RitualTopBar(onCancel = onCancel)
        Text(
            "所问何事",
            fontSize = 15.sp,
            color = AppTheme.accent,
            style = AppTheme.compactText,
            modifier = Modifier.padding(top = 12.dp),
        )
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "弟子今有",
                fontSize = 15.sp,
                color = AppTheme.ink.copy(alpha = 0.35f),
                style = AppTheme.compactText,
            )
            Spacer(Modifier.height(14.dp))
            Box(contentAlignment = Alignment.Center) {
                if (text.isEmpty()) {
                    Text(
                        "简单扼要讲清楚一件事",
                        fontSize = 20.sp,
                        color = AppTheme.ink.copy(alpha = 0.28f),
                        textAlign = TextAlign.Center,
                        style = AppTheme.compactText,
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                    textStyle = AppTheme.compactText.merge(
                        TextStyle(
                            fontSize = 20.sp,
                            color = AppTheme.ink,
                            textAlign = TextAlign.Center,
                        ),
                    ),
                    cursorBrush = SolidColor(AppTheme.accent),
                )
            }
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(AppTheme.fieldStroke),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "之事，不知休咎，望示一卦。",
                fontSize = 15.sp,
                color = AppTheme.ink.copy(alpha = 0.35f),
                textAlign = TextAlign.Center,
                style = AppTheme.compactText,
            )
        }
        Spacer(Modifier.weight(1f))
        PaperPrimaryButton(
            onClick = { onConfirm(trimmed) },
            enabled = trimmed.isNotEmpty(),
            label = "敬告",
            modifier = Modifier.padding(bottom = 24.dp),
        )
    }
}
