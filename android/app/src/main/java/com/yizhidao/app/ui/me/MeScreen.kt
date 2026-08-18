package com.yizhidao.app.ui.me

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yizhidao.Hexagram
import com.yizhidao.app.AppContainer
import com.yizhidao.app.classic.ClassicChapter
import com.yizhidao.app.classic.ClassicWing
import com.yizhidao.app.classic.YijingIntroChapter
import com.yizhidao.app.classic.ZhengshiChapter
import com.yizhidao.app.ui.theme.AppTheme

private sealed interface MeRoute {
    data object Home : MeRoute
    data object Intro : MeRoute
    data class IntroChapter(val item: YijingIntroChapter) : MeRoute
    data object Hexagrams : MeRoute
    data class HexagramDetail(val item: Hexagram) : MeRoute
    data object Wings : MeRoute
    data class Wing(val item: ClassicWing) : MeRoute
    data class Chapter(val wingTitle: String, val chapter: ClassicChapter) : MeRoute
    data object Zhengshi : MeRoute
    data class ZhengshiItem(val item: ZhengshiChapter) : MeRoute
    data class ZhengshiReader(
        val title: String,
        val paragraphs: List<String>,
        val parent: ZhengshiChapter? = null,
    ) : MeRoute
}

@Composable
fun MeScreen(container: AppContainer) {
    var route by remember { mutableStateOf<MeRoute>(MeRoute.Home) }
    val book = container.classicBook
    val intro = container.introBook

    when (val page = route) {
        MeRoute.Home -> Column(Modifier.fillMaxSize().padding(20.dp)) {
            Text("我的", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            ListItem(
                headlineContent = { Text("易经基础入门") },
                supportingContent = { Text("阴阳八卦 · 玩占观辞") },
                modifier = Modifier.clickable { route = MeRoute.Intro },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            ListItem(
                headlineContent = { Text("易经六十四卦") },
                supportingContent = { Text("上经 · 下经") },
                modifier = Modifier.clickable { route = MeRoute.Hexagrams },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            ListItem(
                headlineContent = { Text("易经四传") },
                supportingContent = { Text("系辞 · 说卦 · 序卦 · 杂卦") },
                modifier = Modifier.clickable { route = MeRoute.Wings },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "登录、AI 解读与回收站将在后续接入。",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = AppTheme.secondaryText,
            )
        }
        MeRoute.Intro -> Column(Modifier.fillMaxSize().padding(16.dp)) {
            BackLabel("我的") { route = MeRoute.Home }
            Text("易经基础入门", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
            Text(
                intro.note,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = AppTheme.secondaryText,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
            )
            LazyColumn {
                items(intro.chapters, key = { it.id }) { chapter ->
                    ListItem(
                        headlineContent = { Text(chapter.title) },
                        supportingContent = { Text(chapter.subtitle) },
                        modifier = Modifier.clickable { route = MeRoute.IntroChapter(chapter) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
        is MeRoute.IntroChapter -> IntroChapterReader(page.item) { route = MeRoute.Intro }
        MeRoute.Hexagrams -> Column(Modifier.fillMaxSize().padding(16.dp)) {
            BackLabel("我的") { route = MeRoute.Home }
            Text("易经六十四卦", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
            LazyColumn {
                items(book.hexagrams, key = { it.number }) { hex ->
                    ListItem(
                        headlineContent = { Text("${hex.symbol} ${hex.name}") },
                        supportingContent = { Text(hex.title) },
                        modifier = Modifier.clickable { route = MeRoute.HexagramDetail(hex) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
        is MeRoute.HexagramDetail -> HexagramReader(page.item) { route = MeRoute.Hexagrams }
        MeRoute.Wings -> Column(Modifier.fillMaxSize().padding(16.dp)) {
            BackLabel("我的") { route = MeRoute.Home }
            Text("易经四传", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
            book.wings.forEach { wing ->
                ListItem(
                    headlineContent = { Text(wing.title) },
                    supportingContent = {
                        Text(if (wing.chapters.size == 1) "${wing.chapters[0].paragraphs.size} 节" else "${wing.chapters.size} 章")
                    },
                    modifier = Modifier.clickable {
                        route = if (wing.chapters.size == 1) {
                            MeRoute.Chapter(wing.title, wing.chapters[0])
                        } else {
                            MeRoute.Wing(wing)
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
        is MeRoute.Wing -> Column(Modifier.fillMaxSize().padding(16.dp)) {
            BackLabel("易经四传") { route = MeRoute.Wings }
            Text(page.item.title, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
            page.item.chapters.forEach { chapter ->
                ListItem(
                    headlineContent = { Text(chapter.title) },
                    modifier = Modifier.clickable {
                        route = MeRoute.Chapter(page.item.title, chapter)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
        is MeRoute.Chapter -> ChapterReader(page.wingTitle, page.chapter) {
            val wing = book.wings.first { it.title == page.wingTitle }
            route = if (wing.chapters.size == 1) MeRoute.Wings else MeRoute.Wing(wing)
        }
        MeRoute.Zhengshi -> {
            val zhengshi = container.zhengshiBook
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                BackLabel("我的") { route = MeRoute.Home }
                Text("易经证释", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
                Text(
                    zhengshi.note,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = AppTheme.secondaryText,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                )
                LazyColumn {
                    zhengshi.parts.forEach { part ->
                        item(key = part.id) {
                            Text(
                                part.title,
                                fontWeight = FontWeight.SemiBold,
                                color = AppTheme.accent,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                            )
                        }
                        items(part.chapters, key = { it.id }) { chapter ->
                            ListItem(
                                headlineContent = {
                                    Text(
                                        if (chapter.symbol.isNotEmpty()) {
                                            "${chapter.symbol} ${chapter.title}"
                                        } else {
                                            chapter.title
                                        },
                                    )
                                },
                                supportingContent = {
                                    if (chapter.subtitle.isNotEmpty()) Text(chapter.subtitle)
                                },
                                modifier = Modifier.clickable {
                                    route = if (chapter.sections.size == 1) {
                                        MeRoute.ZhengshiReader(
                                            chapter.title,
                                            chapter.sections[0].paragraphs,
                                        )
                                    } else {
                                        MeRoute.ZhengshiItem(chapter)
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                    }
                }
            }
        }
        is MeRoute.ZhengshiItem -> Column(Modifier.fillMaxSize().padding(16.dp)) {
            BackLabel("易经证释") { route = MeRoute.Zhengshi }
            Text(page.item.title, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
            page.item.sections.forEach { section ->
                ListItem(
                    headlineContent = { Text(section.title) },
                    modifier = Modifier.clickable {
                        route = MeRoute.ZhengshiReader(section.title, section.paragraphs, page.item)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
        is MeRoute.ZhengshiReader -> ZhengshiSectionReader(page.title, page.paragraphs) {
            route = page.parent?.let { MeRoute.ZhengshiItem(it) } ?: MeRoute.Zhengshi
        }
    }
}

@Composable
private fun BackLabel(label: String, onClick: () -> Unit) {
    Text(
        "← $label",
        color = AppTheme.accent,
        modifier = Modifier.clickable(onClick = onClick).padding(bottom = 12.dp),
    )
}

@Composable
private fun IntroChapterReader(chapter: YijingIntroChapter, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        BackLabel("易经基础入门", onBack)
        Text(chapter.title, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
        Text(
            chapter.subtitle,
            color = AppTheme.secondaryText,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        chapter.paragraphs.forEach { paragraph ->
            ScriptureCard(body = paragraph)
        }
    }
}

@Composable
private fun HexagramReader(hex: Hexagram, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        BackLabel("六十四卦", onBack)
        Text("${hex.symbol} ${hex.title}", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
        Text(hex.figure, color = AppTheme.secondaryText, modifier = Modifier.padding(bottom = 12.dp))
        ScriptureCard("卦辞", hex.guaci)
        ScriptureCard("彖曰", hex.tuanci)
        ScriptureCard("象曰", hex.daxiang)
        hex.yaoci.zip(hex.xiaoxiang).forEach { (ci, xiang) ->
            ScriptureCard(body = ci, footnote = "象曰：$xiang")
        }
        hex.yong?.let { ScriptureCard(body = it.ci, footnote = "象曰：${it.xiang}") }
        if (hex.wenyan.isNotEmpty()) {
            ScriptureCard("文言", hex.wenyan.joinToString("\n\n"))
        }
    }
}

@Composable
private fun ChapterReader(wingTitle: String, chapter: ClassicChapter, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        BackLabel(wingTitle, onBack)
        Text(
            if (chapter.title == wingTitle) wingTitle else chapter.title,
            style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        chapter.paragraphs.forEach { paragraph ->
            ScriptureCard(body = paragraph)
        }
    }
}

@Composable
private fun ZhengshiSectionReader(title: String, paragraphs: List<String>, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        BackLabel("易经证释", onBack)
        Text(
            title,
            style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        paragraphs.forEach { paragraph ->
            ScriptureCard(body = paragraph)
        }
    }
}

@Composable
private fun ScriptureCard(title: String? = null, body: String, footnote: String? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .background(AppTheme.cardFill, RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        if (title != null) {
            Text(title, fontWeight = FontWeight.SemiBold, color = AppTheme.accent, modifier = Modifier.padding(bottom = 6.dp))
        }
        Text(body)
        if (footnote != null) {
            Text(footnote, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
