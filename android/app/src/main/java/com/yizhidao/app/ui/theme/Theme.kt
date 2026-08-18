package com.yizhidao.app.ui.theme

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object AppTheme {
    val parchmentTop = Color(0xFFF5EDE0)
    val parchmentBottom = Color(0xFFEBE5DB)
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
    CompositionLocalProvider(LocalDensity provides scaled) {
        MaterialTheme(
            colorScheme = LightColors,
            content = content,
        )
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
            .padding(
                horizontal = if (compact) 8.dp else 12.dp,
                vertical = if (compact) 5.dp else 7.dp,
            )
            .defaultMinSize(
                minWidth = if (compact) 28.dp else 44.dp,
                minHeight = if (compact) 26.dp else 32.dp,
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

@Composable
fun PaperCircleIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconSize: androidx.compose.ui.unit.Dp = 18.dp,
) {
    Box(
        Modifier
            .size(32.dp)
            .shadow(2.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(Color.White)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = AppTheme.ink,
            modifier = Modifier.size(iconSize),
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
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .height(44.dp),
    ) {
        Box(Modifier.align(Alignment.CenterStart)) {
            PaperCircleIconButton(
                onClick = onBack,
                contentDescription = "返回",
                icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                iconSize = 22.dp,
            )
        }
        Text(
            title,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppTheme.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 48.dp),
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
