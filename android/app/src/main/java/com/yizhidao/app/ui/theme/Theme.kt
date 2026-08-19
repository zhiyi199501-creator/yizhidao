package com.yizhidao.app.ui.theme

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text as MaterialText
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import com.yizhidao.app.lang.LocalAppLanguage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun zh(text: String): String {
    val language = LocalAppLanguage.current
    return remember(text, language) { language.convert(text) }
}

@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    style: TextStyle = LocalTextStyle.current,
) {
    val language = LocalAppLanguage.current
    val shown = remember(text, language) { language.convert(text) }
    MaterialText(
        text = shown,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        style = style,
    )
}

object AppTheme {
    // iOS 原值是 #F5EDE0 / #EBE5DB。安卓屏幕多为鲜艳模式，会把暖米色拉得更饱和，
    // 这里整体提亮并压一点黄，让观感与 iOS 持平。
    val parchmentTop = Color(0xFFF8F2E9)
    val parchmentBottom = Color(0xFFF1ECE3)
    val accent = Color(0xFF73382E)
    val accentSoft = Color(0xFFE8D4C8)
    val cardFill = Color.White.copy(alpha = 0.72f)
    val fieldFill = Color.White
    val fieldStroke = Color.Black.copy(alpha = 0.12f)
    val ink = Color(0xFF1C140F)
    val secondaryText = Color.Black.copy(alpha = 0.45f)
    val placeholder = Color.Black.copy(alpha = 0.35f)
    val yangRed = Color(0xFFBF3333)
    val movingHighlight = Color(0xFFFF9800).copy(alpha = 0.15f)
    val segmentTrack = Color(0xFF767680).copy(alpha = 0.14f)
    val disabledFill = Color(0xFFE4E3E1)
    val disabledText = Color(0xFF8E8E93)
    val controlShape = RoundedCornerShape(8.dp)
    val cardShape = RoundedCornerShape(14.dp)
    val segmentShape = RoundedCornerShape(9.dp)
    val pillShape = RoundedCornerShape(7.dp)

    val compactText = TextStyle(
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both,
        ),
    )

    val parchmentBrush = Brush.verticalGradient(
        colors = listOf(parchmentTop, parchmentBottom),
    )
}

/** 记录输入框窗口坐标，点空白时避开它们再失焦（对齐 iOS DismissKeyboardBackground）。 */
class TextFieldHitRegistry {
    private val rects = mutableMapOf<Any, Rect>()

    fun update(key: Any, rect: Rect) {
        rects[key] = rect
    }

    fun remove(key: Any) {
        rects.remove(key)
    }

    fun contains(windowOffset: Offset): Boolean = rects.values.any { it.contains(windowOffset) }
}

val LocalTextFieldHitRegistry = compositionLocalOf { TextFieldHitRegistry() }

private fun Modifier.dismissFocusOnTapOutside(): Modifier = composed {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val registry = LocalTextFieldHitRegistry.current
    val coordsRef = remember { arrayOfNulls<LayoutCoordinates>(1) }
    onGloballyPositioned { coordsRef[0] = it }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val pointerId = down.id
                val start = down.position
                val slop = viewConfiguration.touchSlop
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == pointerId } ?: return@awaitEachGesture
                    if (change.changedToUpIgnoreConsumed()) {
                        if ((change.position - start).getDistance() < slop) {
                            val box = coordsRef[0]
                            if (box != null && box.isAttached) {
                                val windowPos = box.localToWindow(change.position)
                                if (!registry.contains(windowPos)) {
                                    focusManager.clearFocus()
                                    keyboard?.hide()
                                }
                            }
                        }
                        break
                    }
                    if (!change.pressed) break
                }
            }
        }
}

private val LightColors = lightColorScheme(
    primary = AppTheme.accent,
    onPrimary = Color.White,
    primaryContainer = AppTheme.accentSoft,
    onPrimaryContainer = AppTheme.accent,
    secondary = AppTheme.accent,
    onSecondary = Color.White,
    secondaryContainer = AppTheme.accentSoft,
    onSecondaryContainer = AppTheme.accent,
    background = AppTheme.parchmentTop,
    surface = AppTheme.parchmentTop,
    onBackground = AppTheme.ink,
    onSurface = AppTheme.ink,
    surfaceVariant = AppTheme.accentSoft,
    onSurfaceVariant = AppTheme.ink,
    outline = AppTheme.fieldStroke,
    outlineVariant = AppTheme.fieldStroke,
)

@Composable
fun YizhidaoTheme(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val scaled = Density(
        density = density.density,
        fontScale = 16f / 17f,
    )
    val hitRegistry = remember { TextFieldHitRegistry() }
    CompositionLocalProvider(
        LocalDensity provides scaled,
        LocalTextFieldHitRegistry provides hitRegistry,
    ) {
        MaterialTheme(
            colorScheme = LightColors,
        ) {
            Box(Modifier.fillMaxSize().dismissFocusOnTapOutside()) {
                content()
            }
        }
    }
}

@Composable
fun paperButtonColors() = ButtonDefaults.buttonColors(
    containerColor = AppTheme.accent,
    contentColor = Color.White,
    disabledContainerColor = AppTheme.disabledFill,
    disabledContentColor = AppTheme.disabledText,
)

@Composable
fun paperButtonElevation() = ButtonDefaults.buttonElevation(
    defaultElevation = 0.dp,
    pressedElevation = 0.dp,
    disabledElevation = 0.dp,
)

/** iOS UISegmentedControl：灰底 + 白色选中滑块。 */
@Composable
fun PaperSegmentedRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var trackWidth by remember { mutableStateOf(0.dp) }
    Box(
        modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(AppTheme.segmentTrack, AppTheme.segmentShape)
            .padding(1.5.dp)
            .onSizeChanged { trackWidth = with(density) { it.width.toDp() } },
    ) {
        val count = options.size.coerceAtLeast(1)
        val pillWidth = trackWidth / count
        val offset by animateDpAsState(
            targetValue = pillWidth * selectedIndex.coerceIn(0, count - 1),
            label = "segment",
        )
        Box(
            Modifier
                .offset(x = offset)
                .width(pillWidth)
                .fillMaxHeight()
                .shadow(1.5.dp, AppTheme.pillShape, clip = false)
                .background(Color.White, AppTheme.pillShape),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = AppTheme.ink.copy(alpha = if (selected) 1f else 0.72f),
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 13.sp,
                        style = AppTheme.compactText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun PaperTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailing: @Composable (() -> Unit)? = null,
) {
    val textStyle = AppTheme.compactText.merge(
        TextStyle(
            color = AppTheme.ink,
            fontSize = 17.sp,
            lineHeight = 22.sp,
        ),
    )
    val lineHeight = with(LocalDensity.current) { 22.sp.toDp() }
    val verticalPad = 8.dp
    val hitRegistry = LocalTextFieldHitRegistry.current
    val hitKey = remember { Any() }
    DisposableEffect(hitRegistry) {
        onDispose { hitRegistry.remove(hitKey) }
    }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .background(AppTheme.fieldFill, AppTheme.controlShape)
            .border(1.dp, AppTheme.fieldStroke, AppTheme.controlShape)
            .then(
                if (singleLine) {
                    Modifier.height(lineHeight + verticalPad * 2)
                } else {
                    Modifier.heightIn(min = lineHeight * minLines + verticalPad * 2)
                },
            )
            .onGloballyPositioned { hitRegistry.update(hitKey, it.boundsInWindow()) }
            .padding(horizontal = 10.dp, vertical = verticalPad),
        textStyle = textStyle,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        cursorBrush = SolidColor(AppTheme.accent),
        decorationBox = { inner ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(1f)
                        .then(if (singleLine) Modifier.fillMaxHeight() else Modifier),
                    contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
                ) {
                    if (value.isEmpty()) {
                        Text(placeholder, color = AppTheme.placeholder, style = textStyle)
                    }
                    inner()
                }
                trailing?.invoke()
            }
        },
    )
}

/**
 * iOS 列表右侧的 `chevron.right`。Material 的 KeyboardArrowRight 是粗直角箭头，
 * 这里手绘一个更细、更尖、圆头的折线，贴近 SF Symbol。
 * [rotation] 供 DisclosureGroup 式展开使用（90 度即指向下方）。
 */
@Composable
fun PaperChevron(
    modifier: Modifier = Modifier,
    color: Color = Color.Black.copy(alpha = 0.25f),
    height: Dp = 13.dp,
    rotation: Float = 0f,
) {
    Canvas(
        modifier
            .size(width = height * 7f / 12f, height = height)
            .graphicsLayer { rotationZ = rotation },
    ) {
        // 笔画随尺寸走，放大成返回键时才不会显得过细。
        val line = (height / 6.5f).toPx()
        val inset = line / 2
        drawPath(
            path = Path().apply {
                moveTo(inset, inset)
                lineTo(size.width - inset, size.height / 2f)
                lineTo(inset, size.height - inset)
            },
            color = color,
            style = Stroke(width = line, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/** iOS `.bordered`：浅赭石底 + 赭石字。`compact` 对应金钱卦行内「选 / 摇」（minWidth 28）。 */
@Composable
fun PaperOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
    label: String,
) {
    val bg = AppTheme.accent.copy(alpha = if (enabled) 0.12f else 0.06f)
    val fg = AppTheme.accent.copy(alpha = if (enabled) 1f else 0.4f)
    Box(
        modifier
            .clip(AppTheme.controlShape)
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            // defaultMinSize 必须在 padding 之前，否则最小尺寸叠加内边距，按钮会被撑大一圈。
            .defaultMinSize(
                minWidth = if (compact) 44.dp else 54.dp,
                minHeight = if (compact) 36.dp else 32.dp,
            )
            .padding(
                horizontal = if (compact) 8.dp else 12.dp,
                vertical = if (compact) 5.dp else 6.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = fg,
            fontSize = if (compact) 13.sp else 15.sp,
            style = AppTheme.compactText,
            maxLines = 1,
        )
    }
}

@Composable
fun PaperPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = paperButtonColors(),
        elevation = paperButtonElevation(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, style = AppTheme.compactText)
    }
}

/** iOS 导航栏按钮：只有图标，无底衬，44dp 的透明点击区保证手感。 */
@Composable
fun PaperHeaderButton(
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick, onClickLabel = contentDescription),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** iOS `rectangle.stack`：一张卡片 + 上方两道渐窄的叠层线。 */
@Composable
fun PaperStackIcon(
    modifier: Modifier = Modifier,
    color: Color = AppTheme.accent,
) {
    Canvas(modifier.size(width = 22.dp, height = 19.dp)) {
        val line = 1.6.dp.toPx()
        val half = line / 2
        val radius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx())
        drawRoundRect(
            color = color,
            topLeft = Offset(half, size.height * 0.36f),
            size = Size(size.width - line, size.height * 0.64f - half),
            cornerRadius = radius,
            style = Stroke(width = line),
        )
        drawLine(
            color,
            Offset(size.width * 0.10f, size.height * 0.19f),
            Offset(size.width * 0.90f, size.height * 0.19f),
            line,
            StrokeCap.Round,
        )
        drawLine(
            color,
            Offset(size.width * 0.24f, half),
            Offset(size.width * 0.76f, half),
            line,
            StrokeCap.Round,
        )
    }
}

@Composable
fun PaperBackHeader(
    title: String,
    onBack: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    BackHandler(onBack = onBack)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 8.dp)
            .height(44.dp),
    ) {
        Box(Modifier.align(Alignment.CenterStart)) {
            PaperHeaderButton(onClick = onBack, contentDescription = zh("返回")) {
                PaperChevron(color = AppTheme.accent, height = 18.dp, rotation = 180f)
            }
        }
        Text(
            title,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppTheme.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 50.dp),
            style = AppTheme.compactText,
        )
        if (trailing != null) {
            Box(Modifier.align(Alignment.CenterEnd)) {
                trailing()
            }
        }
    }
}

@Composable
fun AIFloatingButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(50.dp)
            .shadow(4.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(AppTheme.accent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "AI",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            style = AppTheme.compactText,
        )
    }
}
