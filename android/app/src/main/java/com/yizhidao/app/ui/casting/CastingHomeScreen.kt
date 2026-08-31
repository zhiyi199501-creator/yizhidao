package com.yizhidao.app.ui.casting

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yizhidao.CastResult
import com.yizhidao.app.AppContainer
import com.yizhidao.app.lang.LocalAppLanguage
import com.yizhidao.app.ui.theme.AppTheme
import com.yizhidao.app.ui.theme.Text
import com.yizhidao.app.ui.theme.ui
import com.yizhidao.app.ui.theme.zh
import kotlinx.coroutines.delay

/** 左起是落款，右起是起句。竖排从右往左读。 */
private val quoteColumns = listOf(
    "是以自天祐之吉无不利",
    "动则观其变而玩其占",
    "君子居则观其象而玩其辞",
)

private const val spokenQuote =
    "君子居则观其象而玩其辞，动则观其变而玩其占，是以自天祐之，吉无不利。"

/** 只在冷启动播一次条幅浮现；切 Tab 再回来不再播。 */
private object CastingHomeReveal {
    var didPlay = false
}

@Composable
fun CastingHomeScreen(
    container: AppContainer,
    onResult: (CastResult) -> Unit,
    onTabBarVisible: (Boolean) -> Unit = {},
) {
    var ritualOpen by remember { mutableStateOf(false) }
    val reduceMotion = LocalContext.current.reduceMotionEnabled()
    var appeared by remember { mutableStateOf(reduceMotion || CastingHomeReveal.didPlay) }

    LaunchedEffect(ritualOpen) {
        onTabBarVisible(!ritualOpen)
    }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion || CastingHomeReveal.didPlay) {
            appeared = true
            return@LaunchedEffect
        }
        CastingHomeReveal.didPlay = true
        appeared = false
        delay(180)
        appeared = true
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val charSp = minOf(22f, maxOf(18f, maxWidth.value / 16f))
        val taiji = minOf(maxWidth * 0.72f, 280.dp)
        val spin = rememberInfiniteTransition(label = "taiji")
        val degrees by spin.animateFloat(
            initialValue = 0f,
            targetValue = if (reduceMotion) 0f else 360f,
            animationSpec = infiniteRepeatable(
                tween(96_000, easing = LinearEasing),
                RepeatMode.Restart,
            ),
            label = "spin",
        )
        Column(
            Modifier
                .fillMaxSize()
                .padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                CastingTaijiMark(
                    Modifier
                        .size(taiji)
                        .rotate(degrees),
                )
                ScrollColumns(
                    charSp = charSp,
                    appeared = appeared,
                    reduceMotion = reduceMotion,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                StartCastSeal(appeared = appeared, reduceMotion = reduceMotion) {
                    ritualOpen = true
                }
                RitualEnglishCaption("Cast")
            }
            Box(Modifier.weight(0.28f))
        }
    }

    if (ritualOpen) {
        CastingActOverlay(
            chinese = container.chineseDateSource,
            store = container.hexagramStore,
            onFinish = { result ->
                onResult(result)
            },
            onCancel = { ritualOpen = false },
        )
    }
}

@Composable
private fun ScrollColumns(
    charSp: Float,
    appeared: Boolean,
    reduceMotion: Boolean,
) {
    val language = LocalAppLanguage.current
    val quote = zh(spokenQuote)
    Row(
        horizontalArrangement = Arrangement.spacedBy((charSp * 1.15f).dp),
        verticalAlignment = Alignment.Top,
        modifier = Modifier.semantics { contentDescription = quote },
    ) {
        quoteColumns.forEachIndexed { column, raw ->
            val text = remember(raw, language) { language.convert(raw) }
            val delay = if (reduceMotion) 0 else 250 + (2 - column) * 800
            val visible by animateFloatAsState(
                targetValue = if (appeared) 1f else 0f,
                animationSpec = tween(
                    durationMillis = if (reduceMotion) 0 else 800,
                    delayMillis = delay,
                ),
                label = "col$column",
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy((charSp * 0.28f).dp),
                modifier = Modifier.alpha(visible),
            ) {
                text.forEach { scalar ->
                    Text(
                        scalar.toString(),
                        fontSize = charSp.sp,
                        fontFamily = FontFamily.Serif,
                        color = AppTheme.accent.copy(alpha = if (column == 0) 0.95f else 0.82f),
                        style = AppTheme.compactText,
                    )
                }
            }
        }
    }
}

@Composable
private fun CastingTaijiMark(modifier: Modifier = Modifier) {
    val ink = AppTheme.accent.copy(alpha = 0.13f)
    Canvas(modifier) {
        val s = size.minDimension
        val r = s / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val stroke = Stroke(width = 1.1.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(color = ink, radius = r, style = stroke)
        drawArc(
            color = ink,
            startAngle = -90f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(cx - r / 2f, cy - r),
            size = androidx.compose.ui.geometry.Size(r, r),
            style = stroke,
        )
        drawArc(
            color = ink,
            startAngle = 90f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(cx - r / 2f, cy),
            size = androidx.compose.ui.geometry.Size(r, r),
            style = stroke,
        )
        val dot = r * 0.085f
        drawCircle(
            color = ink,
            radius = dot,
            center = androidx.compose.ui.geometry.Offset(cx, cy - r / 2f),
        )
        drawCircle(
            color = ink,
            radius = dot,
            center = androidx.compose.ui.geometry.Offset(cx, cy + r / 2f),
            style = stroke,
        )
    }
}

@Composable
private fun StartCastSeal(
    appeared: Boolean,
    reduceMotion: Boolean,
    onBegin: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val enter by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (reduceMotion) 0 else 550,
            delayMillis = if (reduceMotion) 0 else 2550,
        ),
        label = "seal-enter",
    )
    val breath = rememberInfiniteTransition(label = "seal-breath")
    val haloScale by breath.animateFloat(
        initialValue = 0.92f,
        targetValue = if (reduceMotion) 0.92f else 1.1f,
        animationSpec = infiniteRepeatable(tween(3200), RepeatMode.Reverse),
        label = "halo",
    )
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = tween(120),
        label = "press",
    )
    val label = ui("起卦", "Cast")
    val density = LocalDensity.current
    val ring = with(density) { 1.8.dp.toPx() }
    val ringInner = with(density) { 1.6.dp.toPx() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.semantics { contentDescription = label },
    ) {
        Box(
            Modifier
                .size(148.dp)
                .scale(if (reduceMotion) 1f else haloScale)
                .alpha(enter)
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                AppTheme.accent.copy(alpha = 0.18f),
                                AppTheme.accent.copy(alpha = 0.05f),
                                Color.Transparent,
                            ),
                        ),
                    )
                },
        )
        Box(
            Modifier
                .size(72.dp)
                .alpha(enter)
                .scale(pressScale)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onBegin,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(72.dp)) {
                drawCircle(
                    color = AppTheme.accent.copy(alpha = 0.9f),
                    radius = size.minDimension / 2f - 4.dp.toPx(),
                    style = Stroke(width = ring),
                )
                drawCircle(
                    color = AppTheme.accent.copy(alpha = 0.9f),
                    radius = size.minDimension / 2f - 9.dp.toPx(),
                    style = Stroke(width = ringInner),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "起",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Serif,
                    color = AppTheme.accent,
                    style = AppTheme.compactText,
                )
                Text(
                    "卦",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Serif,
                    color = AppTheme.accent,
                    style = AppTheme.compactText,
                )
            }
        }
    }
}
