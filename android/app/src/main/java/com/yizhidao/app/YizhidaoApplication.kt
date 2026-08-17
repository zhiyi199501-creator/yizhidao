package com.yizhidao.app

import android.app.Application
import com.yizhidao.CaseStudy
import com.yizhidao.CaseStudyCodec
import com.yizhidao.HexagramStore

class YizhidaoApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(app: Application) {
    val hexagramStore: HexagramStore = HexagramStore.fromStream(
        app.assets.open("Hexagrams.json"),
    )
    val chineseDateSource = IcuChineseDateSource()
    val readingRepository = ReadingRepository(app)
    val caseRepository = CaseRepository(app)
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
