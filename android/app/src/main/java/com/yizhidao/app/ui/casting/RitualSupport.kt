package com.yizhidao.app.ui.casting

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.yizhidao.app.lang.LocalAppLanguage
import com.yizhidao.app.ui.theme.AppTheme
import com.yizhidao.app.ui.theme.Text
import kotlin.math.sqrt

/** 告神之后才选的取数法门。时间卦只占此刻。 */
sealed class CastingIntent {
    data object Coin : CastingIntent()
    data object DigitalNumbers : CastingIntent()
    data object DigitalTime : CastingIntent()
}

object RitualHaptics {
    fun yaoSettled(view: View, moving: Boolean) {
        view.performHapticFeedback(
            if (moving) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.CLOCK_TICK,
        )
    }

    fun seal(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
}

fun Context.reduceMotionEnabled(): Boolean {
    val scale = Settings.Global.getFloat(
        contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    )
    return scale == 0f
}

@Composable
fun ShakeDetector(enabled: Boolean, onShake: () -> Unit) {
    val context = LocalContext.current
    val latest = rememberUpdatedState(onShake)
    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose { }
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            ?: return@DisposableEffect onDispose { }
        var lastFiredAt = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val g = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
                if (g <= 2.3f) return
                val now = SystemClock.uptimeMillis()
                if (now - lastFiredAt < 1_200) return
                lastFiredAt = now
                latest.value()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { manager.unregisterListener(listener) }
    }
}

@Composable
fun RitualEnglishCaption(text: String) {
    if (!LocalAppLanguage.current.isEnglish) return
    Text(
        text,
        fontSize = 12.sp,
        color = AppTheme.secondaryText,
        style = AppTheme.compactText,
    )
}
