package com.yizhidao.app

import android.app.Application
import com.yizhidao.CaseStudy
import com.yizhidao.CaseStudyCodec
import com.yizhidao.HexagramStore
import com.yizhidao.app.classic.ClassicYijingCodec
import com.yizhidao.app.classic.HexagramsBook
import com.yizhidao.app.classic.YijingIntroBook
import com.yizhidao.app.classic.YijingIntroCodec
import com.yizhidao.app.classic.ZhengshiBook
import com.yizhidao.app.classic.ZhengshiCodec
import com.yizhidao.app.lang.AppLanguageStore
import com.yizhidao.app.sound.TapSoundPlayer

class YizhidaoApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        TapSoundPlayer.init(this)
        AppLanguageStore.init(this)
        container = AppContainer(this)
    }
}

class AppContainer(app: Application) {
    private val hexagramsText: String =
        app.assets.open("Hexagrams.json").bufferedReader().use { it.readText() }
    val classicBook: HexagramsBook = ClassicYijingCodec.decode(hexagramsText)
    val hexagramStore: HexagramStore = HexagramStore(classicBook.hexagrams)
    val chineseDateSource = IcuChineseDateSource()
    val readingRepository = ReadingRepository(app)
    val caseRepository = CaseRepository(app)
    val introBook: YijingIntroBook = YijingIntroCodec.decode(
        app.assets.open("YijingIntro.json").bufferedReader().use { it.readText() },
    )
    val zhengshiBook: ZhengshiBook by lazy {
        ZhengshiCodec.decode(
            app.assets.open("Zhengshi.json").bufferedReader().use { it.readText() },
        )
    }
}

class CaseRepository(private val app: Application) {
    fun loadBundled(): List<CaseStudy> {
        val cached = app.filesDir.resolve("cases-cache.json")
        if (cached.exists()) {
            runCatching { return CaseStudyCodec.decodeList(cached.readText()) }
        }
        return CaseStudyCodec.decodeList(
            app.assets.open("cases.json").bufferedReader().use { it.readText() },
        )
    }
}
