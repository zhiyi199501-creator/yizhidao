package com.yizhidao.app.ui.casting

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yizhidao.CastResult
import com.yizhidao.ChineseDateSource
import com.yizhidao.DigitalCastingEngine
import com.yizhidao.LunarCalendarHelper
import com.yizhidao.SecureRandomSource
import com.yizhidao.app.ui.theme.AppTheme
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val dateTimeFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DigitalCastPanel(
    question: String,
    chinese: ChineseDateSource,
    onResult: (CastResult) -> Unit,
) {
    var threeNumbers by remember { mutableStateOf(true) }
    var n1 by remember { mutableStateOf("") }
    var n2 by remember { mutableStateOf("") }
    var n3 by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(ZonedDateTime.now()) }
    var useSolar by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    val rng = remember { SecureRandomSource() }

    val ready = listOf(n1, n2, n3).all { it.toIntOrNull()?.let { n -> n > 0 } == true }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = threeNumbers,
                onClick = { threeNumbers = true; error = null },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text("输入三数") }
            SegmentedButton(
                selected = !threeNumbers,
                onClick = { threeNumbers = false; error = null },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text("时间起卦") }
        }

        if (threeNumbers) {
            NumberRow("上卦数", n1, { n1 = it.filter(Char::isDigit) }) {
                n1 = rng.nextInt(10..999).toString()
            }
            NumberRow("下卦数", n2, { n2 = it.filter(Char::isDigit) }) {
                n2 = rng.nextInt(10..999).toString()
            }
            NumberRow("动爻数", n3, { n3 = it.filter(Char::isDigit) }) {
                n3 = rng.nextInt(10..999).toString()
            }
            Row {
                OutlinedButton(onClick = {
                    n1 = rng.nextInt(10..999).toString()
                    n2 = rng.nextInt(10..999).toString()
                    n3 = rng.nextInt(10..999).toString()
                    error = null
                }) { Text("一键随机") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = {
                    n1 = ""; n2 = ""; n3 = ""; error = null
                }) { Text("清空") }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("占问时刻", modifier = Modifier.weight(1f))
                Text(
                    selected.format(dateTimeFmt),
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .background(AppTheme.fieldFill, RoundedCornerShape(8.dp))
                        .border(1.dp, AppTheme.fieldStroke, RoundedCornerShape(8.dp))
                        .clickable { showPicker = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("公历取数", modifier = Modifier.weight(1f))
                Switch(checked = useSolar, onCheckedChange = { useSolar = it })
            }
            val comps = if (useSolar) {
                LunarCalendarHelper.solarComponents(selected, chinese)
            } else {
                LunarCalendarHelper.components(selected, chinese)
            }
            Text(
                if (useSolar) {
                    "取数：${LunarCalendarHelper.branchName(comps.yearBranch)}年(${comps.yearBranch}) + 公历${comps.month}月${comps.day}日 + ${comps.hourBranch}时(1–24)"
                } else {
                    "取数：${LunarCalendarHelper.branchName(comps.yearBranch)}年(${comps.yearBranch}) + 农历${comps.month}月${comps.day}日 + ${LunarCalendarHelper.branchName(comps.hourBranch)}时(${comps.hourBranch})"
                },
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Text(
                "上卦=(年+月+日)÷8余；下卦与动爻=(年+月+日+时)分别÷8、÷6取余。",
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }

        error?.let {
            Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
        }

        Button(
            onClick = {
                error = null
                val q = question.trim().ifEmpty { null }
                if (threeNumbers) {
                    val a = n1.toIntOrNull()
                    val b = n2.toIntOrNull()
                    val c = n3.toIntOrNull()
                    if (a == null || b == null || c == null || a <= 0 || b <= 0 || c <= 0) {
                        error = "请填写三个正整数"
                        return@Button
                    }
                    onResult(DigitalCastingEngine.cast(a, b, c, q))
                } else {
                    val comps = if (useSolar) {
                        LunarCalendarHelper.solarComponents(selected, chinese)
                    } else {
                        LunarCalendarHelper.components(selected, chinese)
                    }
                    onResult(
                        DigitalCastingEngine.castTime(
                            yearBranch = comps.yearBranch,
                            month = comps.month,
                            day = comps.day,
                            hour = comps.hourBranch,
                            question = q,
                            at = selected.toInstant(),
                        ),
                    )
                }
            },
            enabled = !threeNumbers || ready,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AppTheme.accent),
        ) {
            Text("起卦")
        }
    }

    if (showPicker) {
        DateTimePickDialog(
            initial = selected,
            onDismiss = { showPicker = false },
            onConfirm = {
                selected = it
                showPicker = false
            },
        )
    }
}

@Composable
private fun NumberRow(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    onRandom: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, modifier = Modifier.width(56.dp), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("输入数字") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = AppTheme.fieldFill,
                unfocusedContainerColor = AppTheme.fieldFill,
            ),
        )
        OutlinedButton(onClick = onRandom) { Text("随机") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimePickDialog(
    initial: ZonedDateTime,
    onDismiss: () -> Unit,
    onConfirm: (ZonedDateTime) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = initial.toInstant().toEpochMilli(),
    )
    val timeState = rememberTimePickerState(initial.hour, initial.minute, is24Hour = true)
    var pickingTime by remember { mutableStateOf(false) }

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                if (!pickingTime) {
                    pickingTime = true
                } else {
                    val millis = dateState.selectedDateMillis ?: Instant.now().toEpochMilli()
                    val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
                    val time = LocalTime.of(timeState.hour, timeState.minute)
                    onConfirm(ZonedDateTime.of(date, time, zone))
                }
            }) {
                Text(if (pickingTime) "完成" else "下一步")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (pickingTime) pickingTime = false else onDismiss()
            }) { Text(if (pickingTime) "上一步" else "取消") }
        },
    ) {
        if (!pickingTime) {
            DatePicker(state = dateState)
        } else {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("时间")
                Spacer(Modifier.height(12.dp))
                TimePicker(state = timeState)
            }
        }
    }
}
