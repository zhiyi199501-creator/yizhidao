package com.yizhidao.app.ui.reading

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (title.isNotEmpty()) {
            Text(title, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
        (5 downTo 0).forEach { index ->
            val position = index + 1
            val line = lines.getOrNull(index) ?: return@forEach
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    HexagramText.yaoStemLabel(position, line),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier.width(36.dp),
                )
                YaoBar(line = line, highlighted = position in movingPositions)
            }
        }
    }
}

@Composable
fun YaoBar(line: LineValue, highlighted: Boolean) {
    val color = if (line.isYang) AppTheme.yangRed else Color.Black.copy(alpha = 0.85f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(
                if (highlighted) AppTheme.movingHighlight else Color.Transparent,
                RoundedCornerShape(6.dp),
            )
            .padding(4.dp),
    ) {
        Canvas(Modifier.size(110.dp, 10.dp)) {
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
        Box(Modifier.width(12.dp), contentAlignment = Alignment.Center) {
            if (line.isChanging) {
                Text(
                    if (line.isYang) "○" else "×",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = color,
                )
            }
        }
    }
}
