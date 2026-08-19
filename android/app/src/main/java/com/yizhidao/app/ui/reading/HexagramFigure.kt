package com.yizhidao.app.ui.reading

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import com.yizhidao.app.ui.theme.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yizhidao.HexagramText
import com.yizhidao.LineValue
import com.yizhidao.app.ui.theme.AppTheme

@Composable
fun HexagramFigure(
    lines: List<LineValue>,
    movingPositions: List<Int>,
    title: String = "",
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (title.isNotEmpty()) {
            Text(
                title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppTheme.secondaryText,
                style = AppTheme.compactText,
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // 爻名 36 + 间距 8 + 爻画内边距 8 + 标记间距 6 + 标记 12
            val barWidth = (maxWidth - 70.dp).coerceIn(64.dp, 110.dp)
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                (5 downTo 0).forEach { index ->
                    val position = index + 1
                    val line = lines.getOrNull(index) ?: return@forEach
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            HexagramText.yaoStemLabel(position, line),
                            fontSize = 11.sp,
                            color = AppTheme.secondaryText,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(36.dp),
                            style = AppTheme.compactText,
                        )
                        YaoBar(
                            line = line,
                            highlighted = position in movingPositions,
                            barWidth = barWidth,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun YaoBar(
    line: LineValue,
    highlighted: Boolean,
    barWidth: Dp = 110.dp,
) {
    val color = if (line.isYang) AppTheme.yangRed else Color.Black.copy(alpha = 0.85f)
    val showMarker = highlighted || line.isChanging
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(
                if (highlighted) AppTheme.movingHighlight else Color.Transparent,
                RoundedCornerShape(6.dp),
            )
            .padding(4.dp),
    ) {
        Canvas(Modifier.size(barWidth, 10.dp)) {
            val h = size.height
            val r = CornerRadius(h / 2, h / 2)
            if (line.isYang) {
                drawRoundRect(color, cornerRadius = r)
            } else {
                val gap = 10.dp.toPx()
                val w = (size.width - gap) / 2
                drawRoundRect(color, size = Size(w, h), cornerRadius = r)
                drawRoundRect(color, topLeft = Offset(w + gap, 0f), size = Size(w, h), cornerRadius = r)
            }
        }
        Spacer(Modifier.width(6.dp))
        Box(Modifier.size(12.dp), contentAlignment = Alignment.Center) {
            if (showMarker) {
                Canvas(Modifier.size(10.dp)) {
                    val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
                    if (line.isYang) {
                        drawCircle(
                            color = color,
                            radius = size.minDimension / 2 - stroke.width / 2,
                            style = stroke,
                        )
                    } else {
                        val pad = 1.2.dp.toPx()
                        drawLine(
                            color,
                            Offset(pad, pad),
                            Offset(size.width - pad, size.height - pad),
                            stroke.width,
                            StrokeCap.Round,
                        )
                        drawLine(
                            color,
                            Offset(size.width - pad, pad),
                            Offset(pad, size.height - pad),
                            stroke.width,
                            StrokeCap.Round,
                        )
                    }
                }
            }
        }
    }
}
