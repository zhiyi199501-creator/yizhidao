package com.yizhidao.app.ui.reading

import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.yizhidao.app.ima.ImaAnswerBlock
import com.yizhidao.app.ima.ImaAnswerFormatter
import com.yizhidao.app.ima.ImaExplanationEntry
import com.yizhidao.app.ima.ImaExplanationStore
import com.yizhidao.app.ui.theme.AppTheme
import com.yizhidao.app.ui.theme.Text
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val SheetTopShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

private val WindowOriginPositionProvider = object : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset.Zero
}

@Composable
fun TappableScripture(
    explanationId: String,
    imaStore: ImaExplanationStore,
    onSelect: (ImaExplanationEntry) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val entry = remember(explanationId, imaStore) { imaStore.explanation(explanationId) }
    if (entry != null) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clickable { onSelect(entry) },
        ) {
            Column(Modifier.fillMaxWidth().padding(end = 18.dp)) {
                content()
            }
            Icon(
                Icons.AutoMirrored.Outlined.MenuBook,
                contentDescription = "查看讲解",
                tint = AppTheme.accent.copy(alpha = 0.75f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(14.dp),
            )
        }
    } else {
        Column(modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
fun ImaExplanationSheet(
    entry: ImaExplanationEntry,
    source: String,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val hostView = LocalView.current
    var overlayWidthPx by remember { mutableIntStateOf(hostView.rootView.width) }
    var overlayHeightPx by remember { mutableIntStateOf(hostView.rootView.height) }
    DisposableEffect(hostView) {
        val root = hostView.rootView
        val listener = View.OnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            overlayWidthPx = v.width
            overlayHeightPx = v.height
        }
        root.addOnLayoutChangeListener(listener)
        overlayWidthPx = root.width
        overlayHeightPx = root.height
        onDispose { root.removeOnLayoutChangeListener(listener) }
    }
    val overlayWidth = with(density) { overlayWidthPx.coerceAtLeast(1).toDp() }
    val overlayHeight = with(density) { overlayHeightPx.coerceAtLeast(1).toDp() }
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    var offsetY by remember { mutableFloatStateOf(0f) }
    var sheetHeightPx by remember { mutableFloatStateOf(1f) }

    fun settle() {
        scope.launch {
            val target = if (offsetY > sheetHeightPx / 4f) sheetHeightPx else 0f
            val anim = Animatable(offsetY)
            anim.animateTo(target, tween(220)) { offsetY = value }
            if (target > 0f) onDismiss()
        }
    }

    val nestedScroll = remember(scroll) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.Drag) return Offset.Zero
                if (available.y > 0f && scroll.value == 0) {
                    offsetY += available.y
                    return Offset(0f, available.y)
                }
                if (available.y < 0f && offsetY > 0f) {
                    val consumed = available.y.coerceAtLeast(-offsetY)
                    offsetY += consumed
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (offsetY > 0f) {
                    settle()
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    Popup(
        popupPositionProvider = WindowOriginPositionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            clippingEnabled = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(Modifier.requiredSize(overlayWidth, overlayHeight)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = (0.4f * (1f - (offsetY / sheetHeightPx).coerceIn(0f, 1f))))),
            )
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.93f)
                    .onSizeChanged { sheetHeightPx = it.height.toFloat().coerceAtLeast(1f) }
                    .offset { IntOffset(0, offsetY.roundToInt()) }
                    .clip(SheetTopShape)
                    .background(AppTheme.parchmentBrush),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .pointerInput(sheetHeightPx) {
                            detectVerticalDragGestures(
                                onVerticalDrag = { _, dy ->
                                    offsetY = (offsetY + dy).coerceAtLeast(0f)
                                },
                                onDragEnd = { settle() },
                                onDragCancel = { settle() },
                            )
                        },
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .width(36.dp)
                                .height(5.dp)
                                .background(Color.Black.copy(alpha = 0.18f), RoundedCornerShape(2.5.dp)),
                        )
                    }

                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .height(44.dp),
                    ) {
                        Text(
                            "关闭",
                            color = AppTheme.accent,
                            fontSize = 17.sp,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .clickable(onClick = onDismiss)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        )
                        Text(
                            entry.title,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp,
                            color = AppTheme.ink,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = AppTheme.compactText,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 64.dp),
                        )
                    }
                }

                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .nestedScroll(nestedScroll),
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(scroll)
                            .padding(horizontal = 16.dp)
                            .padding(top = 8.dp, bottom = 52.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            entry.scripture,
                            fontSize = 14.sp,
                            color = AppTheme.secondaryText,
                            lineHeight = 20.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(AppTheme.cardFill, RoundedCornerShape(12.dp))
                                .padding(16.dp),
                        )
                        ImaAnswerBody(
                            text = entry.answer,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(64.dp)
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    0.45f to AppTheme.parchmentBottom.copy(alpha = 0.65f),
                                    1f to AppTheme.parchmentBottom,
                                ),
                            ),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Text(
                            source,
                            fontSize = 11.sp,
                            color = AppTheme.secondaryText.copy(alpha = 0.55f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 14.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImaAnswerBody(
    text: String,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(text) { ImaAnswerFormatter.blocks(text) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is ImaAnswerBlock.Text -> Text(
                    block.text,
                    fontSize = 16.sp,
                    color = AppTheme.ink,
                    lineHeight = 24.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
                is ImaAnswerBlock.Table -> ImaAnswerTable(rows = block.rows)
            }
        }
    }
}

private fun imaCompactFirstColumn(rows: List<List<String>>): Boolean {
    val columns = rows.firstOrNull()?.size ?: return false
    if (columns == 2) return true
    if (columns < 3) return false
    return rows.drop(1).all { (it.firstOrNull() ?: "").length <= 4 }
}

@Composable
private fun ImaTableCell(
    text: String,
    isHeader: Boolean,
    isFirstColumn: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.TopStart) {
        Text(
            text,
            fontSize = 13.sp,
            fontWeight = if (isHeader || isFirstColumn) FontWeight.SemiBold else null,
            color = if (isHeader) AppTheme.accent else AppTheme.ink,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun ImaAnswerTable(rows: List<List<String>>) {
    val columnCount = rows.firstOrNull()?.size ?: return
    val compactFirst = imaCompactFirstColumn(rows)
    val firstColWidth = if (columnCount == 2) 96.dp else 52.dp
    val cellPaddingH = if (columnCount >= 4) 8.dp else 10.dp
    val shape = RoundedCornerShape(12.dp)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppTheme.cardFill)
            .border(0.6.dp, AppTheme.fieldStroke, shape),
    ) {
        rows.forEachIndexed { rowIndex, row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .background(
                        if (rowIndex == 0) AppTheme.accent.copy(alpha = 0.08f)
                        else Color.Transparent,
                    ),
            ) {
                row.forEachIndexed { colIndex, cell ->
                    if (colIndex > 0) {
                        Box(
                            Modifier
                                .width(0.6.dp)
                                .fillMaxHeight()
                                .background(AppTheme.fieldStroke),
                        )
                    }
                    val cellMod = if (compactFirst && colIndex == 0) {
                        Modifier.width(firstColWidth)
                    } else {
                        Modifier.weight(1f)
                    }
                    ImaTableCell(
                        text = cell,
                        isHeader = rowIndex == 0,
                        isFirstColumn = colIndex == 0,
                        modifier = cellMod
                            .fillMaxHeight()
                            .padding(horizontal = cellPaddingH, vertical = 8.dp),
                    )
                }
            }
            if (rowIndex < rows.lastIndex) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(0.6.dp)
                        .background(AppTheme.fieldStroke),
                )
            }
        }
    }
}
