package com.yizhidao.app.ui.casting

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yizhidao.app.ui.theme.AppTheme
import com.yizhidao.app.ui.theme.Text
import com.yizhidao.app.ui.theme.zh
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val stillnessInk = Color(0xFF241E1C)
private const val GatherMs = 2500L
private const val SettleMs = 400L
private const val ReleaseMs = 350

@Composable
fun StillnessAct(
    onReady: () -> Unit,
    onCancel: () -> Unit,
) {
    val view = LocalView.current
    val reduceMotion = LocalContext.current.reduceMotionEnabled()
    val scope = rememberCoroutineScope()
    var progress by remember { mutableFloatStateOf(0f) }
    var isPressing by remember { mutableStateOf(false) }
    var didFinish by remember { mutableStateOf(false) }
    var hold by remember { mutableStateOf<Job?>(null) }
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(
            durationMillis = if (isPressing) GatherMs.toInt() else ReleaseMs,
            easing = LinearEasing,
        ),
        label = "gather",
    )
    val breath = rememberInfiniteTransition(label = "breath")
    val breathScale by breath.animateFloat(
        initialValue = 1f,
        targetValue = if (reduceMotion) 1f else 1.04f,
        animationSpec = infiniteRepeatable(tween(2400), RepeatMode.Reverse),
        label = "breathScale",
    )

    DisposableEffect(Unit) {
        onDispose { hold?.cancel() }
    }

    fun finish() {
        if (didFinish) return
        didFinish = true
        hold?.cancel()
        hold = null
        onReady()
    }

    fun beginGathering() {
        if (didFinish || isPressing) return
        isPressing = true
        progress = 1f
        hold = scope.launch {
            delay(GatherMs)
            RitualHaptics.seal(view)
            isPressing = false
            didFinish = true
            delay(SettleMs)
            onReady()
        }
    }

    fun releaseGathering() {
        if (!isPressing || didFinish) return
        isPressing = false
        hold?.cancel()
        hold = null
        progress = 0f
        RitualHaptics.yaoSettled(view, moving = false)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(AppTheme.parchmentBrush),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RitualTopBar(
                onCancel = onCancel,
                trailing = {
                    Text(
                        "跳过",
                        fontSize = 15.sp,
                        color = AppTheme.ink.copy(alpha = 0.35f),
                        modifier = Modifier.clickable { finish() },
                        style = AppTheme.compactText,
                    )
                },
            )
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "净手，择一静处，坐稳。",
                    fontSize = 13.sp,
                    color = AppTheme.ink.copy(alpha = 0.35f),
                    style = AppTheme.compactText,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "静穆身心，敬慎其意。",
                    fontSize = 13.sp,
                    color = AppTheme.ink.copy(alpha = 0.35f),
                    style = AppTheme.compactText,
                )
                Spacer(Modifier.height(52.dp))
                val gatherLabel = zh("凝心一会")
                Box(
                    Modifier
                        .size(168.dp)
                        .scale(if (isPressing || reduceMotion) 1f else breathScale)
                        .semantics { contentDescription = gatherLabel }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    beginGathering()
                                    try {
                                        awaitRelease()
                                    } finally {
                                        releaseGathering()
                                    }
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Transparent, CircleShape),
                    )
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        drawCircle(
                            color = AppTheme.accent.copy(alpha = 0.18f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()),
                        )
                        val radius = size.minDimension / 2 * animatedProgress
                        if (radius > 0f) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        stillnessInk.copy(alpha = 0.92f),
                                        stillnessInk.copy(alpha = 0.72f),
                                        stillnessInk.copy(alpha = 0.20f),
                                    ),
                                    center = Offset(size.width / 2, size.height / 2),
                                    radius = (size.minDimension * 0.58f).coerceAtLeast(1f),
                                ),
                                radius = radius,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(22.dp))
                Text(
                    "凝心一会",
                    fontSize = 15.sp,
                    color = AppTheme.secondaryText,
                    modifier = Modifier.height(22.dp),
                    style = AppTheme.compactText,
                )
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
fun RitualTopBar(
    onCancel: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val cancelLabel = zh("取消起卦")
        Text(
            "✕",
            fontSize = 16.sp,
            color = AppTheme.secondaryText,
            modifier = Modifier
                .clickable(onClick = onCancel)
                .padding(8.dp)
                .semantics { contentDescription = cancelLabel },
            style = AppTheme.compactText,
        )
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}
