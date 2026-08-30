package com.yizhidao.app.ui.casting

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yizhidao.CoinCastingEngine
import com.yizhidao.CoinToss
import com.yizhidao.LineValue
import com.yizhidao.app.ui.reading.YaoBar
import com.yizhidao.app.lang.LocalAppLanguage
import com.yizhidao.app.ui.theme.AppTheme
import com.yizhidao.app.ui.theme.Text
import com.yizhidao.app.ui.theme.ui
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val yaoNames = listOf("初", "二", "三", "四", "五", "上")
private val manualOptions = listOf(
    LineValue.YOUNG_YANG,
    LineValue.YOUNG_YIN,
    LineValue.OLD_YANG,
    LineValue.OLD_YIN,
)
private val brassLight = Color(0xFFD9B573)
private val brassDark = Color(0xFFA37840)
private val coinInk = Color(0xFF472E1A)

@Composable
fun CoinTossAct(
    question: String,
    onComplete: (List<LineValue>) -> Unit,
    onCancel: () -> Unit,
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var lines by remember { mutableStateOf(listOf<LineValue>()) }
    var toss by remember { mutableStateOf<CoinToss?>(null) }
    var settledCoins by remember { mutableIntStateOf(0) }
    var isTossing by remember { mutableStateOf(false) }
    var jitters by remember { mutableStateOf(false) }
    var showManual by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }
    var sequence by remember { mutableStateOf<Job?>(null) }
    val language = LocalAppLanguage.current
    val complete = lines.size == 6
    val canToss = !isTossing && !complete

    fun settle(line: LineValue) {
        lines = lines + line
        RitualHaptics.yaoSettled(view, moving = line.isChanging)
    }

    fun reset() {
        sequence?.cancel()
        sequence = null
        isTossing = false
        jitters = false
        settledCoins = 0
        toss = null
        lines = emptyList()
    }

    fun beginToss() {
        if (!canToss) return
        isTossing = true
        settledCoins = 0
        val thisToss = CoinCastingEngine.toss()
        toss = thisToss
        jitters = true
        sequence = scope.launch {
            delay(630)
            jitters = false
            repeat(3) { index ->
                settledCoins = index + 1
                RitualHaptics.yaoSettled(view, moving = false)
                delay(140)
            }
            delay(450)
            settle(thisToss.line)
            delay(500)
            isTossing = false
            if (lines.size < 6) return@launch
            delay(500)
            onComplete(lines)
        }
    }

    fun append(line: LineValue) {
        if (!canToss) return
        toss = null
        settledCoins = 0
        settle(line)
        if (lines.size < 6) return
        sequence = scope.launch {
            delay(500)
            onComplete(lines)
        }
    }

    ShakeDetector(enabled = canToss) { beginToss() }

    Column(
        Modifier
            .fillMaxSize()
            .background(AppTheme.parchmentBrush)
            .statusBarsPadding()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RitualTopBar(
            onCancel = onCancel,
            trailing = if (lines.isNotEmpty()) {
                {
                    Text(
                        "重来",
                        fontSize = 15.sp,
                        color = AppTheme.accent.copy(alpha = if (isTossing) 0.4f else 1f),
                        modifier = Modifier.clickable(enabled = !isTossing) { showReset = true },
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
                ui("六爻已成", "Six lines complete")
            } else {
                language.ui("${yaoNames[lines.size]}爻 · 共六爻", "${yaoNames[lines.size]} · of 6")
            },
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppTheme.accent,
            modifier = Modifier.padding(top = 6.dp),
            style = AppTheme.compactText,
        )
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (5 downTo 0).forEach { index ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        yaoNames[index],
                        fontSize = 12.sp,
                        color = if (index < lines.size) AppTheme.secondaryText else AppTheme.placeholder,
                        modifier = Modifier.width(24.dp),
                        textAlign = TextAlign.End,
                        style = AppTheme.compactText,
                    )
                    if (index < lines.size) {
                        YaoBar(
                            line = lines[index],
                            highlighted = lines[index].isChanging,
                            barWidth = 140.dp,
                            barHeight = 12.dp,
                        )
                    } else {
                        Box(
                            Modifier
                                .width(140.dp)
                                .height(12.dp)
                                .border(
                                    1.dp,
                                    AppTheme.ink.copy(alpha = if (index == lines.size) 0.28f else 0.12f),
                                    RoundedCornerShape(50),
                                ),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(34.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .alpha(if (complete) 0.35f else 1f)
                .pointerInput(canToss) {
                    detectTapGestures(
                        onTap = { beginToss() },
                        onLongPress = { if (canToss) showManual = true },
                    )
                },
            horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
        ) {
            repeat(3) { index ->
                BrassCoin(
                    isYangFace = if (settledCoins > index) toss?.faces?.getOrNull(index) else null,
                    jitters = jitters,
                    wobbleSeed = index,
                    size = 72.dp,
                )
            }
        }
        Text(
            readout(toss, settledCoins),
            fontSize = 15.sp,
            fontFamily = FontFamily.Monospace,
            color = if (settledCoins == 3) AppTheme.accent else AppTheme.secondaryText,
            modifier = Modifier
                .height(24.dp)
                .padding(top = 8.dp),
            style = AppTheme.compactText,
        )
        Spacer(Modifier.weight(1f))
        Text(
            when {
                complete -> ui("六爻已成，正在成卦", "Forming the hexagram")
                isTossing -> ui("静候铜钱落定", "Coins settling")
                lines.isEmpty() -> ui("摇一摇手机，或轻点铜钱掷出第一爻", "Shake the phone, or tap the coins for the first line")
                else -> ui("摇一摇手机，或轻点铜钱掷出下一爻", "Shake the phone, or tap the coins for the next line")
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

    if (showManual) {
        AlertDialog(
            onDismissRequest = { showManual = false },
            title = { Text("手选四象", en = "Choose a line") },
            text = {
                Column {
                    manualOptions.forEach { line ->
                        Text(
                            line.displayLabel,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showManual = false
                                    append(line)
                                }
                                .padding(vertical = 10.dp),
                            style = AppTheme.compactText,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showManual = false }) { Text("取消", en = "Cancel") }
            },
        )
    }
    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text("重新摇这一卦？", en = "Toss this hexagram again?") },
            text = {
                Text(
                    "已摇的 ${lines.size} 爻会作废。",
                    en = "The ${lines.size} lines already tossed will be discarded.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showReset = false
                    reset()
                }) { Text("重新摇", en = "Start over") }
            },
            dismissButton = {
                TextButton(onClick = { showReset = false }) { Text("继续", en = "Continue") }
            },
        )
    }
}

private fun readout(toss: CoinToss?, settled: Int): String {
    if (toss == null || settled <= 0) return " "
    val shown = toss.faces.take(settled).joinToString(" ") { if (it) "字" else "背" }
    if (settled < 3) return shown
    return "$shown　${toss.line.displayLabel}"
}

@Composable
fun BrassCoin(
    isYangFace: Boolean?,
    jitters: Boolean,
    wobbleSeed: Int,
    size: Dp = 72.dp,
) {
    val wobble = listOf(9f, -12f, 7f)[wobbleSeed % 3]
    val infinite = rememberInfiniteTransition(label = "coin")
    val wobbleAnim by infinite.animateFloat(
        initialValue = 0f,
        targetValue = wobble,
        animationSpec = infiniteRepeatable(tween(90), RepeatMode.Reverse),
        label = "wobble",
    )
    Box(
        Modifier
            .size(size)
            .graphicsLayer {
                rotationZ = if (jitters) wobbleAnim else 0f
                translationY = if (jitters) -4f else 0f
                alpha = if (isYangFace == null) 0.55f else 1f
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val hole = size.toPx() * 0.24f
            val canvasSize = this.size
            val path = Path().apply {
                fillType = PathFillType.EvenOdd
                addOval(Rect(Offset.Zero, canvasSize))
                val origin = Offset((canvasSize.width - hole) / 2, (canvasSize.height - hole) / 2)
                addRoundRect(
                    RoundRect(
                        rect = Rect(origin, androidx.compose.ui.geometry.Size(hole, hole)),
                        cornerRadius = CornerRadius(1.5.dp.toPx()),
                    ),
                )
            }
            drawPath(
                path,
                brush = Brush.linearGradient(listOf(brassLight, brassDark)),
            )
            drawCircle(
                color = Color.Black.copy(alpha = 0.16f),
                style = Stroke(1.5.dp.toPx()),
            )
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.26f),
                topLeft = Offset((canvasSize.width - hole) / 2, (canvasSize.height - hole) / 2),
                size = androidx.compose.ui.geometry.Size(hole, hole),
                cornerRadius = CornerRadius(1.5.dp.toPx()),
                style = Stroke(1.dp.toPx()),
            )
            if (isYangFace == false) {
                drawCircle(
                    color = Color.Black.copy(alpha = 0.10f),
                    radius = this.size.minDimension * 0.3f,
                    style = Stroke(1.dp.toPx()),
                )
            }
        }
        if (isYangFace == true) {
            val glyph = (size.value * 0.17f).sp
            Text("乾", fontSize = glyph, color = coinInk, modifier = Modifier.padding(bottom = size * 0.58f), style = AppTheme.compactText)
            Text("隆", fontSize = glyph, color = coinInk, modifier = Modifier.padding(top = size * 0.58f), style = AppTheme.compactText)
            Text("通", fontSize = glyph, color = coinInk, modifier = Modifier.padding(start = size * 0.58f), style = AppTheme.compactText)
            Text("宝", fontSize = glyph, color = coinInk, modifier = Modifier.padding(end = size * 0.58f), style = AppTheme.compactText)
        }
    }
}
