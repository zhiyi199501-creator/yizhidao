package com.yizhidao.app.ui.casting

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yizhidao.CastResult
import com.yizhidao.HexagramStore
import com.yizhidao.LineValue
import com.yizhidao.app.lang.LocalAppLanguage
import com.yizhidao.app.lang.displayName
import com.yizhidao.app.ui.theme.AppTheme
import com.yizhidao.app.ui.theme.Text
import kotlinx.coroutines.delay

private val cinnabar = Color(0xFFC73029)

@Composable
fun CastRevealAct(
    result: CastResult,
    store: HexagramStore,
    onFinish: () -> Unit,
) {
    val view = LocalView.current
    val reduceMotion = LocalContext.current.reduceMotionEnabled()
    var revealedCount by remember { mutableIntStateOf(0) }
    var showsSeal by remember { mutableStateOf(false) }
    var showsThanks by remember { mutableStateOf(false) }
    var didFinish by remember { mutableStateOf(false) }
    val language = LocalAppLanguage.current
    val primary = store.hexagram(result.primaryNumber)
    val resulting = result.resultingNumber
        ?.takeIf { it != result.primaryNumber }
        ?.let { store.hexagram(it) }
    val pulse = rememberInfiniteTransition(label = "cinnabar")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (result.movingPositions.isEmpty()) 1f else 1.3f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "pulse",
    )

    fun finish() {
        if (didFinish) return
        didFinish = true
        onFinish()
    }

    fun presentThanks() {
        if (showsThanks) return
        showsThanks = true
    }

    fun skipAnimation() {
        if (showsThanks || didFinish) return
        revealedCount = 6
        showsSeal = true
        presentThanks()
    }

    LaunchedEffect(Unit) {
        if (reduceMotion) {
            revealedCount = 6
            showsSeal = true
            RitualHaptics.seal(view)
            delay(450)
            presentThanks()
            return@LaunchedEffect
        }
        delay(500)
        repeat(6) { index ->
            if (index == 5) delay(220)
            revealedCount = index + 1
            RitualHaptics.yaoSettled(view, moving = result.movingPositions.contains(index + 1))
            delay(320)
        }
        delay(600)
        showsSeal = true
        RitualHaptics.seal(view)
        delay(450)
        presentThanks()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(AppTheme.parchmentBrush)
            .then(
                if (showsThanks) Modifier else Modifier.clickable { skipAnimation() },
            )
            .statusBarsPadding()
            .padding(horizontal = 32.dp, vertical = 44.dp),
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val question = result.question?.trim().orEmpty()
            if (question.isNotEmpty()) {
                Text(
                    question,
                    fontSize = 13.sp,
                    color = AppTheme.secondaryText,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                    style = AppTheme.compactText,
                )
            }
            Spacer(Modifier.weight(1f))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(36.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    (5 downTo 0).forEach { index ->
                        val shown = revealedCount > index
                        val moving = result.movingPositions.contains(index + 1)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .alpha(if (shown) 1f else 0f)
                                .scale(scaleX = if (shown) 1f else 0.8f, scaleY = 1f),
                        ) {
                            Box(Modifier.width(9.dp))
                            RevealYaoBar(result.lines[index])
                            Box(
                                Modifier
                                    .size(9.dp)
                                    .scale(if (shown && moving) pulseScale else 1f)
                                    .alpha(if (shown && moving) 1f else 0f)
                                    .background(cinnabar, CircleShape),
                            )
                        }
                    }
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .alpha(if (showsSeal) 1f else 0f)
                        .scale(if (showsSeal) 1f else 1.2f),
                ) {
                    Text(
                        primary?.displayName(language)
                            ?: language.ui("第${result.primaryNumber}卦", "Hexagram ${result.primaryNumber}"),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.accent,
                        style = AppTheme.compactText,
                    )
                    val resultingNumber = result.resultingNumber
                    if (resulting != null && resultingNumber != null) {
                        Text(
                            "之 ${resulting.displayName(language)}",
                            fontSize = 20.sp,
                            color = AppTheme.secondaryText,
                            modifier = Modifier.padding(top = 8.dp),
                            style = AppTheme.compactText,
                        )
                    }
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.alpha(if (showsThanks) 1f else 0f),
                ) {
                    Text(
                        "感谢爻变开化之神的指示",
                        fontSize = 13.sp,
                        color = AppTheme.secondaryText,
                        textAlign = TextAlign.Center,
                        style = AppTheme.compactText,
                    )
                    Box(
                        Modifier
                            .padding(top = 18.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppTheme.accent)
                            .clickable(enabled = showsThanks, onClick = { finish() })
                            .padding(horizontal = 28.dp, vertical = 10.dp),
                    ) {
                        Text(
                            "弟子退",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppTheme.parchmentTop,
                            style = AppTheme.compactText,
                        )
                    }
                    RitualEnglishCaption("Step back")
                }
            }
            Spacer(Modifier.weight(1f))
            if (language.isEnglish) {
                Text(
                    "Tap to skip",
                    fontSize = 11.sp,
                    color = AppTheme.ink.copy(alpha = if (showsThanks || didFinish) 0f else 0.28f),
                    style = AppTheme.compactText,
                )
            } else {
                Text(
                    "轻点跳过动画",
                    fontSize = 11.sp,
                    color = AppTheme.ink.copy(alpha = if (showsThanks || didFinish) 0f else 0.28f),
                    style = AppTheme.compactText,
                )
            }
        }
    }
}

@Composable
private fun RevealYaoBar(line: LineValue) {
    val color = if (line.isYang) Color.Black.copy(alpha = 0.88f) else Color.Black.copy(alpha = 0.85f)
    Canvas(Modifier.size(150.dp, 14.dp)) {
        val h = size.height
        val r = CornerRadius(h / 2, h / 2)
        if (line.isYang) {
            drawRoundRect(color, cornerRadius = r)
        } else {
            val gap = 14.dp.toPx()
            val w = (size.width - gap) / 2
            drawRoundRect(color, size = Size(w, h), cornerRadius = r)
            drawRoundRect(color, topLeft = Offset(w + gap, 0f), size = Size(w, h), cornerRadius = r)
        }
    }
}
