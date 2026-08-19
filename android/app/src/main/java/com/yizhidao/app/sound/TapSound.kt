package com.yizhidao.app.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.yizhidao.app.R

enum class TapSoundKind(val id: String, val title: String, val rawRes: Int?) {
    None("none", "无音效", null),
    Bubble("bubble", "气泡音", R.raw.tap_bubble),
    Click("click", "按键音", R.raw.tap_click),
    Wood("wood", "木鱼", R.raw.tap_wood),
    Jade("jade", "玉磬", R.raw.tap_jade),
    ;

    companion object {
        fun fromId(id: String?): TapSoundKind =
            entries.firstOrNull { it.id == id } ?: None
    }
}

object TapSoundPlayer {
    private const val PREF = "tap_sound"
    private const val KEY = "kind"

    @Volatile
    private var kind: TapSoundKind = TapSoundKind.None
    private var pool: SoundPool? = null
    private val streamIds = mutableMapOf<TapSoundKind, Int>()
    private var lastPlayAtMs = 0L

    fun init(context: Context) {
        val app = context.applicationContext
        kind = TapSoundKind.fromId(
            app.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, TapSoundKind.None.id),
        )
        val soundPool = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
        pool = soundPool
        for (item in TapSoundKind.entries) {
            val res = item.rawRes ?: continue
            streamIds[item] = soundPool.load(app, res, 1)
        }
    }

    fun current(): TapSoundKind = kind

    fun setKind(context: Context, value: TapSoundKind) {
        kind = value
        context.applicationContext
            .getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, value.id)
            .apply()
        play()
    }

    fun play() {
        val target = kind
        if (target == TapSoundKind.None) return
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastPlayAtMs < 50) return
        lastPlayAtMs = now
        val id = streamIds[target] ?: return
        pool?.play(id, 0.82f, 0.82f, 1, 0, 1f)
    }
}
