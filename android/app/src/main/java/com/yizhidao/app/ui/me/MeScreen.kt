package com.yizhidao.app.ui.me

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.VolumeUp
import com.yizhidao.app.lang.AppLanguage
import com.yizhidao.app.lang.AppLanguageStore
import com.yizhidao.app.sound.TapSoundKind
import com.yizhidao.app.sound.TapSoundPlayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.TextButton
import com.yizhidao.app.ui.theme.Text
import com.yizhidao.app.ui.theme.zh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.yizhidao.Hexagram
import com.yizhidao.app.AppContainer
import com.yizhidao.app.HistoryTrashEntry
import com.yizhidao.app.classic.ClassicChapter
import com.yizhidao.app.classic.ClassicWing
import com.yizhidao.app.classic.YijingIntroBook
import com.yizhidao.app.classic.YijingIntroChapter
import com.yizhidao.app.classic.ZhengshiChapter
import com.yizhidao.app.ui.theme.AppTheme
import com.yizhidao.app.ui.theme.PaperBackHeader
import com.yizhidao.app.ui.theme.PaperChevron

private sealed interface MeRoute {
    data object Home : MeRoute
    data object Login : MeRoute
    data object AIHistory : MeRoute
    data object Settings : MeRoute
    data object TapSound : MeRoute
    data object Language : MeRoute
    data object Recycle : MeRoute
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
        MeRoute.Home -> MeHome(
            onLogin = { route = MeRoute.Login },
            onAIHistory = { route = MeRoute.Login },
            onIntro = { route = MeRoute.Intro },
            onHexagrams = { route = MeRoute.Hexagrams },
            onWings = { route = MeRoute.Wings },
            onSettings = { route = MeRoute.Settings },
        )
        MeRoute.Login -> PlaceholderPage(
            title = "登录",
            message = "支持手机号或微信登录。生产短信与微信登录尚未接入。",
            onBack = { route = MeRoute.Home },
        )
        MeRoute.AIHistory -> PlaceholderPage(
            title = "AI解读历史",
            message = "登录后可查看保存在本机的 AI 解读。",
            onBack = { route = MeRoute.Home },
        )
        MeRoute.Settings -> SettingsPage(
            container = container,
            onBack = { route = MeRoute.Home },
            onOpenRecycle = { route = MeRoute.Recycle },
            onOpenTapSound = { route = MeRoute.TapSound },
            onOpenLanguage = { route = MeRoute.Language },
        )
        MeRoute.TapSound -> TapSoundPage(
            onBack = { route = MeRoute.Settings },
        )
        MeRoute.Language -> LanguagePage(
            onBack = { route = MeRoute.Settings },
        )
        MeRoute.Recycle -> RecycleBinPage(
            container = container,
            onBack = { route = MeRoute.Settings },
        )
        MeRoute.Intro -> IntroListPage(
            book = intro,
            onBack = { route = MeRoute.Home },
            onOpen = { route = MeRoute.IntroChapter(it) },
        )
        is MeRoute.IntroChapter -> IntroChapterReader(page.item) { route = MeRoute.Intro }
        MeRoute.Hexagrams -> HexagramListPage(
            hexagrams = book.hexagrams,
            onBack = { route = MeRoute.Home },
            onOpen = { route = MeRoute.HexagramDetail(it) },
        )
        is MeRoute.HexagramDetail -> HexagramReader(page.item) { route = MeRoute.Hexagrams }
        MeRoute.Wings -> WingListPage(
            wings = book.wings,
            onBack = { route = MeRoute.Home },
            onOpen = { wing ->
                route = if (wing.chapters.size == 1) {
                    MeRoute.Chapter(wing.title, wing.chapters[0])
                } else {
                    MeRoute.Wing(wing)
                }
            },
        )
        is MeRoute.Wing -> WingChapterListPage(
            wing = page.item,
            onBack = { route = MeRoute.Wings },
            onOpen = { route = MeRoute.Chapter(page.item.title, it) },
        )
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
private fun MeHome(
    onLogin: () -> Unit,
    onAIHistory: () -> Unit,
    onIntro: () -> Unit,
    onHexagrams: () -> Unit,
    onWings: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "我的",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppTheme.ink,
                style = AppTheme.compactText,
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            MeCard {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.AccountCircle,
                        contentDescription = null,
                        tint = AppTheme.secondaryText,
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("未登录", fontSize = 17.sp, color = AppTheme.ink, style = AppTheme.compactText)
                        Text(
                            "支持手机号或微信登录",
                            fontSize = 12.sp,
                            color = AppTheme.secondaryText,
                            style = AppTheme.compactText,
                        )
                    }
                    Text(
                        "登录",
                        color = AppTheme.accent,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .clip(AppTheme.controlShape)
                            .clickable(onClick = onLogin)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        style = AppTheme.compactText,
                    )
                }
            }
            MeCard {
                MeRow(
                    icon = Icons.AutoMirrored.Outlined.MenuBook,
                    title = "AI解读历史",
                    trailing = {
                        Text("需登录", fontSize = 12.sp, color = AppTheme.secondaryText, style = AppTheme.compactText)
                    },
                    showChevron = false,
                    onClick = onAIHistory,
                )
            }
            MeCard {
                MeRow(
                    icon = Icons.AutoMirrored.Outlined.MenuBook,
                    title = "易经基础入门",
                    onClick = onIntro,
                )
                MeDivider()
                MeRow(
                    icon = Icons.Outlined.AutoStories,
                    title = "易经六十四卦",
                    onClick = onHexagrams,
                )
                MeDivider()
                MeRow(
                    icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                    title = "易经四传",
                    onClick = onWings,
                )
            }
            MeCard {
                MeRow(
                    icon = Icons.Outlined.Settings,
                    title = "设置",
                    onClick = onSettings,
                )
            }
        }
    }
}

@Composable
private fun SettingsPage(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenRecycle: () -> Unit,
    onOpenTapSound: () -> Unit,
    onOpenLanguage: () -> Unit,
) {
    val trash by container.readingRepository.trash.collectAsState()
    var tapSound by remember { mutableStateOf(TapSoundPlayer.current()) }
    val language by AppLanguageStore.language.collectAsState()
    Column(Modifier.fillMaxSize()) {
        PaperBackHeader(title = "设置", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            MeCard {
                MeRow(
                    icon = Icons.Outlined.Language,
                    title = "语言",
                    trailing = {
                        Text(
                            language.title,
                            fontSize = 15.sp,
                            color = AppTheme.secondaryText,
                            style = AppTheme.compactText,
                        )
                    },
                    onClick = onOpenLanguage,
                )
            }
            Spacer(Modifier.height(12.dp))
            MeCard {
                MeRow(
                    icon = Icons.Outlined.VolumeUp,
                    title = "按键音效",
                    trailing = {
                        Text(
                            tapSound.title,
                            fontSize = 15.sp,
                            color = AppTheme.secondaryText,
                            style = AppTheme.compactText,
                        )
                    },
                    onClick = onOpenTapSound,
                )
            }
            Spacer(Modifier.height(12.dp))
            MeCard {
                MeRow(
                    icon = null,
                    title = "回收站",
                    trailing = {
                        Text(
                            "${trash.size}",
                            fontSize = 15.sp,
                            color = AppTheme.secondaryText,
                            style = AppTheme.compactText,
                        )
                    },
                    onClick = onOpenRecycle,
                )
            }
        }
    }
}

@Composable
private fun LanguagePage(onBack: () -> Unit) {
    val language by AppLanguageStore.language.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(Modifier.fillMaxSize()) {
        PaperBackHeader(title = "语言", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            MeCard {
                AppLanguage.entries.forEachIndexed { index, item ->
                    MeRow(
                        icon = null,
                        title = item.title,
                        showChevron = false,
                        trailing = {
                            if (language == item) {
                                Icon(
                                    Icons.Outlined.Check,
                                    contentDescription = zh("已选"),
                                    tint = AppTheme.accent,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        },
                        onClick = { AppLanguageStore.set(context, item) },
                    )
                    if (index < AppLanguage.entries.lastIndex) {
                        MeDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun TapSoundPage(onBack: () -> Unit) {
    var selected by remember { mutableStateOf(TapSoundPlayer.current()) }
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(Modifier.fillMaxSize()) {
        PaperBackHeader(title = "按键音效", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                "点按按钮、标签和列表时播放。系统静音时可能不出声。",
                fontSize = 13.sp,
                color = AppTheme.secondaryText,
                style = AppTheme.compactText,
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
            )
            MeCard {
                TapSoundKind.entries.forEachIndexed { index, kind ->
                    MeRow(
                        icon = null,
                        title = kind.title,
                        showChevron = false,
                        trailing = {
                            if (selected == kind) {
                                Icon(
                                    Icons.Outlined.Check,
                                    contentDescription = zh("已选"),
                                    tint = AppTheme.accent,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        },
                        onClick = {
                            selected = kind
                            TapSoundPlayer.setKind(context, kind)
                        },
                    )
                    if (index < TapSoundKind.entries.lastIndex) {
                        MeDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun RecycleBinPage(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val entries by container.readingRepository.trash.collectAsState()
    val scope = rememberCoroutineScope()
    var showClearConfirm by remember { mutableStateOf(false) }
    val store = container.hexagramStore

    Column(Modifier.fillMaxSize()) {
        PaperBackHeader(
            title = "回收站",
            onBack = onBack,
            trailing = if (entries.isNotEmpty()) {
                {
                    Text(
                        "清空",
                        color = Color(0xFFA64040),
                        fontSize = 16.sp,
                        modifier = Modifier.clickable { showClearConfirm = true },
                        style = AppTheme.compactText,
                    )
                }
            } else {
                null
            },
        )
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("回收站为空", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = AppTheme.ink)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "删除的记录会先放在这里，可恢复",
                        fontSize = 13.sp,
                        color = AppTheme.secondaryText,
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(entries, key = { it.id }) { entry ->
                    RecycleRow(
                        entry = entry,
                        hexTitle = { n ->
                            val hex = store.hexagram(n)
                            if (hex != null) "${hex.symbol} ${hex.name}" else "第${n}卦"
                        },
                        onRestore = { scope.launch { container.readingRepository.restoreTrash(entry.id) } },
                        onRemove = { scope.launch { container.readingRepository.removeTrash(entry.id) } },
                    )
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("确认清空？") },
            text = { Text("回收站中的记录将被彻底删除，无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    scope.launch { container.readingRepository.clearTrash() }
                }) {
                    Text("确定", color = Color(0xFFA64040))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("取消", color = AppTheme.accent)
                }
            },
            containerColor = AppTheme.parchmentTop,
        )
    }
}

@Composable
private fun RecycleRow(
    entry: HistoryTrashEntry,
    hexTitle: (Int) -> String,
    onRestore: () -> Unit,
    onRemove: () -> Unit,
) {
    val rec = entry.record
    Column(
        Modifier
            .fillMaxWidth()
            .clip(AppTheme.cardShape)
            .background(AppTheme.cardFill)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            buildString {
                append(hexTitle(rec.primaryNumber))
                rec.resultingNumber?.let { append(" → "); append(hexTitle(it)) }
            },
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppTheme.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = AppTheme.compactText,
        )
        rec.question?.takeIf { it.isNotBlank() }?.let { q ->
            Text(q, fontSize = 15.sp, color = AppTheme.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "恢复",
                color = Color(0xFF338C59),
                fontSize = 15.sp,
                modifier = Modifier.clickable(onClick = onRestore),
                style = AppTheme.compactText,
            )
            Text(
                "彻底删除",
                color = Color(0xFFA64040),
                fontSize = 15.sp,
                modifier = Modifier.clickable(onClick = onRemove),
                style = AppTheme.compactText,
            )
        }
    }
}

@Composable
private fun PlaceholderPage(title: String, message: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        BackLabel("我的", onBack)
        Text(
            title,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppTheme.ink,
            style = AppTheme.compactText,
        )
        Spacer(Modifier.height(16.dp))
        Text(message, fontSize = 15.sp, color = AppTheme.secondaryText, lineHeight = 22.sp)
    }
}

@Composable
private fun MeCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(AppTheme.cardShape)
            .background(Color.White.copy(alpha = 0.92f)),
    ) { content() }
}

@Composable
private fun MeRow(
    icon: ImageVector?,
    title: String,
    trailing: @Composable (() -> Unit)? = null,
    showChevron: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = AppTheme.accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
        }
        Text(
            title,
            fontSize = 17.sp,
            color = AppTheme.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            style = AppTheme.compactText,
        )
        trailing?.invoke()
        if (showChevron) {
            Spacer(Modifier.width(6.dp))
            PaperChevron()
        }
    }
}

@Composable
private fun MeDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 50.dp),
        color = AppTheme.fieldStroke,
        thickness = 0.5.dp,
    )
}

@Composable
private fun IntroListPage(
    book: YijingIntroBook,
    onBack: () -> Unit,
    onOpen: (YijingIntroChapter) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        PaperBackHeader(title = "易经基础入门", onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            if (book.note.isNotBlank()) {
                item(key = "note") {
                    Text(
                        book.note,
                        fontSize = 13.sp,
                        color = AppTheme.secondaryText,
                        lineHeight = 18.sp,
                        style = AppTheme.compactText,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 12.dp),
                    )
                }
            }
            item(key = "chapters") {
                MeCard {
                    book.chapters.forEachIndexed { index, chapter ->
                        PaperNavRow(
                            title = chapter.title,
                            subtitle = chapter.subtitle,
                            showDivider = index < book.chapters.lastIndex,
                            onClick = { onOpen(chapter) },
                        )
                    }
                }
            }
            if (book.source.isNotBlank()) {
                item(key = "source") {
                    Text(
                        book.source,
                        fontSize = 12.sp,
                        color = AppTheme.secondaryText,
                        style = AppTheme.compactText,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WingListPage(
    wings: List<ClassicWing>,
    onBack: () -> Unit,
    onOpen: (ClassicWing) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        PaperBackHeader(title = "易经四传", onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            item(key = "wings") {
                MeCard {
                    wings.forEachIndexed { index, wing ->
                        PaperNavRow(
                            title = wing.title,
                            subtitle = if (wing.chapters.size == 1) {
                                "${wing.chapters[0].paragraphs.size} 节"
                            } else {
                                "${wing.chapters.size} 章"
                            },
                            showDivider = index < wings.lastIndex,
                            onClick = { onOpen(wing) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WingChapterListPage(
    wing: ClassicWing,
    onBack: () -> Unit,
    onOpen: (ClassicChapter) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        PaperBackHeader(title = wing.title, onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            item(key = "chapters") {
                MeCard {
                    wing.chapters.forEachIndexed { index, chapter ->
                        PaperNavRow(
                            title = chapter.title,
                            showDivider = index < wing.chapters.lastIndex,
                            onClick = { onOpen(chapter) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PaperNavRow(
    title: String,
    subtitle: String? = null,
    showDivider: Boolean,
    onClick: () -> Unit,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .then(if (subtitle.isNullOrBlank()) Modifier.heightIn(min = 44.dp) else Modifier)
                .padding(horizontal = 16.dp, vertical = if (subtitle.isNullOrBlank()) 0.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = AppTheme.compactText,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        fontSize = 13.sp,
                        color = AppTheme.secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = AppTheme.compactText,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            PaperChevron()
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 16.dp),
                color = AppTheme.fieldStroke,
                thickness = 0.5.dp,
            )
        }
    }
}

@Composable
private fun HexagramListPage(
    hexagrams: List<Hexagram>,
    onBack: () -> Unit,
    onOpen: (Hexagram) -> Unit,
) {
    val upper = hexagrams.filter { it.part == "上经" }.ifEmpty { hexagrams.take(30) }
    val lower = hexagrams.filter { it.part == "下经" }.ifEmpty { hexagrams.drop(upper.size) }
    Column(Modifier.fillMaxSize()) {
        PaperBackHeader(title = "易经六十四卦", onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            hexagramSection("上经", upper, onOpen)
            hexagramSection("下经", lower, onOpen)
        }
    }
}

private fun LazyListScope.hexagramSection(
    title: String,
    hexagrams: List<Hexagram>,
    onOpen: (Hexagram) -> Unit,
) {
    if (hexagrams.isEmpty()) return
    item(key = "header-$title") {
        Text(
            title,
            fontSize = 13.sp,
            color = AppTheme.secondaryText,
            style = AppTheme.compactText,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 6.dp),
        )
    }
    item(key = "card-$title") {
        Column(
            Modifier
                .clip(AppTheme.cardShape)
                .background(Color.White.copy(alpha = 0.92f)),
        ) {
            hexagrams.forEachIndexed { index, hex ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(hex) }
                        .heightIn(min = 44.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${hex.symbol} ${hex.name}",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppTheme.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = AppTheme.compactText,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        hex.title,
                        fontSize = 13.sp,
                        color = AppTheme.secondaryText,
                        maxLines = 1,
                        style = AppTheme.compactText,
                    )
                    Spacer(Modifier.width(8.dp))
                    PaperChevron()
                }
                if (index < hexagrams.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        color = AppTheme.fieldStroke,
                        thickness = 0.5.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun BackLabel(label: String, onClick: () -> Unit) {
    BackHandler(onBack = onClick)
    Text(
        "← $label",
        color = AppTheme.accent,
        modifier = Modifier.clickable(onClick = onClick).padding(bottom = 12.dp),
    )
}

@Composable
private fun IntroChapterReader(chapter: YijingIntroChapter, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PaperBackHeader(title = chapter.title, onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            if (chapter.subtitle.isNotBlank()) {
                Text(
                    chapter.subtitle,
                    fontSize = 15.sp,
                    color = AppTheme.secondaryText,
                    style = AppTheme.compactText,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }
            chapter.paragraphs.forEach { paragraph ->
                ScriptureCard(body = paragraph)
            }
        }
    }
}

@Composable
private fun HexagramReader(hex: Hexagram, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PaperBackHeader(title = hex.name, onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(
                hex.title,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppTheme.ink,
                style = AppTheme.compactText,
            )
            Text(
                hex.figure,
                fontSize = 15.sp,
                color = AppTheme.secondaryText,
                modifier = Modifier.padding(bottom = 12.dp),
            )
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
}

@Composable
private fun ChapterReader(wingTitle: String, chapter: ClassicChapter, onBack: () -> Unit) {
    val title = if (chapter.title == wingTitle) wingTitle else chapter.title
    Column(Modifier.fillMaxSize()) {
        PaperBackHeader(title = title, onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            chapter.paragraphs.forEach { paragraph ->
                ScriptureCard(body = paragraph)
            }
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
        Text(
            body,
            fontSize = 16.sp,
            color = AppTheme.ink,
            lineHeight = 24.sp,
        )
        if (footnote != null) {
            Text(
                footnote,
                fontSize = 16.sp,
                color = AppTheme.ink,
                lineHeight = 24.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
