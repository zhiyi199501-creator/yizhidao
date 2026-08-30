package com.yizhidao.app.ui.casting

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yizhidao.SecureRandomSource
import com.yizhidao.app.lang.LocalAppLanguage
import com.yizhidao.app.ui.theme.AppTheme
import com.yizhidao.app.ui.theme.PaperOutlinedButton
import com.yizhidao.app.ui.theme.PaperPrimaryButton
import com.yizhidao.app.ui.theme.PaperTextField
import com.yizhidao.app.ui.theme.Text
import com.yizhidao.app.ui.theme.ui
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val slotNames = listOf("上卦数", "下卦数", "动爻数")

@Composable
fun NumberDrawAct(
    question: String,
    onComplete: (Int, Int, Int) -> Unit,
    onCancel: () -> Unit,
) {
    val view = LocalView.current
    val rng = remember { SecureRandomSource() }
    val scope = rememberCoroutineScope()
    var drawn by remember { mutableStateOf(listOf<Int>()) }
    var entry by remember { mutableStateOf("") }
    var isSettling by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }
    var sequence by remember { mutableStateOf<Job?>(null) }
    val focus = remember { FocusRequester() }
    val language = LocalAppLanguage.current
    val complete = drawn.size == 3
    val value = entry.toIntOrNull()?.takeIf { it > 0 }
    val canSettle = !isSettling && !complete && value != null
    val slotLabels = listOf(
        ui("上卦数", "Upper"),
        ui("下卦数", "Lower"),
        ui("动爻数", "Changing"),
    )

    fun settle() {
        val number = value ?: return
        if (!canSettle) return
        isSettling = true
        drawn = drawn + number
        entry = ""
        RitualHaptics.yaoSettled(view, moving = false)
        sequence = scope.launch {
            delay(500)
            isSettling = false
            if (drawn.size < 3) return@launch
            delay(600)
            onComplete(drawn[0], drawn[1], drawn[2])
        }
    }

    fun reset() {
        sequence?.cancel()
        sequence = null
        isSettling = false
        entry = ""
        drawn = emptyList()
        focus.requestFocus()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(AppTheme.parchmentBrush)
            .statusBarsPadding()
            .imePadding()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RitualTopBar(
            onCancel = onCancel,
            trailing = if (drawn.isNotEmpty()) {
                {
                    Text(
                        "重来",
                        fontSize = 15.sp,
                        color = AppTheme.accent.copy(alpha = if (isSettling) 0.4f else 1f),
                        modifier = Modifier.clickable(enabled = !isSettling) { showReset = true },
                        style = AppTheme.compactText,
                        en = "Again",
                    )
                }
            } else {
                null
            },
        )
        Text(
            question,
            fontSize = 13.sp,
            color = AppTheme.secondaryText,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            style = AppTheme.compactText,
        )
        Text(
            if (complete) {
                ui("三数已取", "Three numbers taken")
            } else {
                language.ui("${slotNames[drawn.size]} · 共三数", "${slotLabels[drawn.size]} · of 3")
            },
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppTheme.accent,
            modifier = Modifier.padding(top = 6.dp),
            style = AppTheme.compactText,
        )
        Spacer(Modifier.weight(1f))
        Column(
            Modifier
                .fillMaxWidth()
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            slotLabels.forEachIndexed { index, name ->
            DrawAlignRow(
                leading = {
                    Text(
                        name,
                        fontSize = 15.sp,
                        color = AppTheme.secondaryText.copy(
                            alpha = if (index <= drawn.size) 1f else 0.55f,
                        ),
                        style = AppTheme.compactText,
                    )
                },
                compact = true,
                trailingVisible = false,
                onRandom = {},
                randomEnabled = false,
            ) {
                    if (index < drawn.size) {
                        Text(
                            drawn[index].toString(),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            color = AppTheme.accent,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                            style = AppTheme.compactText,
                        )
                    } else {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .border(
                                    width = 1.dp,
                                    color = AppTheme.ink.copy(
                                        alpha = if (index == drawn.size) 0.28f else 0.12f,
                                    ),
                                    shape = RoundedCornerShape(50),
                                ),
                        )
                    }
                }
            }
        }
        if (!complete) {
            Spacer(Modifier.height(34.dp))
            DrawAlignRow(
                leading = {},
                compact = false,
                trailingVisible = true,
                onRandom = {
                    entry = rng.nextInt(10..999).toString()
                },
                randomEnabled = !isSettling,
            ) {
                PaperTextField(
                    value = entry,
                    onValueChange = { if (!isSettling) entry = it.filter(Char::isDigit) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                    placeholder = "输入数字",
                    placeholderEn = "Number",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(24.dp))
            DrawAlignRow(
                leading = {},
                compact = false,
                trailingVisible = false,
                onRandom = {},
                randomEnabled = false,
            ) {
                PaperPrimaryButton(
                    onClick = { settle() },
                    enabled = canSettle,
                    label = if (drawn.size == 2) "成卦" else "落定",
                    en = if (drawn.size == 2) "Cast" else "Settle",
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            when {
                complete -> ui("三数已取，正在成卦", "Forming the hexagram")
                isSettling -> ui("静候落定", "Settling")
                drawn.isEmpty() -> ui("心中默一个数写下，或点「随机」随手取一个", "Hold a number in mind, or tap Random")
                else -> ui("再默一个数", "Another number")
            },
            fontSize = 12.sp,
            color = AppTheme.secondaryText,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 28.dp),
            style = AppTheme.compactText,
        )
    }

    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text("重新取这一卦？", en = "Take this hexagram again?") },
            text = {
                Text(
                    "已取的 ${drawn.size} 个数会作废。",
                    en = "The ${drawn.size} numbers already taken will be discarded.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showReset = false
                    reset()
                }) { Text("重新取", en = "Start over") }
            },
            dismissButton = {
                TextButton(onClick = { showReset = false }) { Text("继续", en = "Continue") }
            },
        )
    }
}

@Composable
private fun DrawAlignRow(
    leading: @Composable () -> Unit,
    compact: Boolean,
    trailingVisible: Boolean,
    onRandom: () -> Unit,
    randomEnabled: Boolean,
    middle: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (compact) Modifier.height(34.dp) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(56.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            leading()
        }
        Box(
            Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart,
        ) {
            middle()
        }
        PaperOutlinedButton(
            onClick = onRandom,
            enabled = randomEnabled && trailingVisible,
            label = "随机",
            en = "Random",
            modifier = Modifier.alpha(if (trailingVisible) 1f else 0f),
        )
    }
}
