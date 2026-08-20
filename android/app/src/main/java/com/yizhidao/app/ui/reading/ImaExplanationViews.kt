package com.yizhidao.app.ui.reading

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yizhidao.app.ima.ImaExplanationEntry
import com.yizhidao.app.ima.ImaExplanationStore
import com.yizhidao.app.ui.theme.AppTheme
import com.yizhidao.app.ui.theme.Text
import kotlinx.coroutines.launch

private val SheetTopShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImaExplanationSheet(
    entry: ImaExplanationEntry,
    source: String,
    onDismiss: () -> Unit,
) {
    var allowHide by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { newValue ->
            newValue != SheetValue.Hidden || allowHide
        },
    )
    val scope = rememberCoroutineScope()
    val dismissAnimated: () -> Unit = {
        if (!allowHide) {
            allowHide = true
            scope.launch {
                sheetState.hide()
                onDismiss()
            }
        }
    }
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    ModalBottomSheet(
        onDismissRequest = dismissAnimated,
        sheetState = sheetState,
        shape = SheetTopShape,
        containerColor = Color.Transparent,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        dragHandle = null,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(screenHeight * 0.93f)
                .clip(SheetTopShape)
                .background(AppTheme.parchmentBrush),
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
                        .clickable(onClick = dismissAnimated)
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

            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
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
                Text(
                    entry.answer,
                    fontSize = 16.sp,
                    color = AppTheme.ink,
                    lineHeight = 24.sp,
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
