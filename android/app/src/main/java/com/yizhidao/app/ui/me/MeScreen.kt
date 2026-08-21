package com.yizhidao.app.ui.me

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Delete
import com.yizhidao.app.sound.TapSoundKind
import com.yizhidao.app.sound.TapSoundPlayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.TextButton
import com.yizhidao.app.ui.theme.Text
import com.yizhidao.app.ui.theme.zh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import com.yizhidao.HexagramText
import com.yizhidao.app.AppContainer
import com.yizhidao.app.auth.LocalAuthStore
import com.yizhidao.app.ima.ImaExplanationEntry
import com.yizhidao.app.ima.ImaExplanationId
import com.yizhidao.app.ima.ImaExplanationStore
import com.yizhidao.app.ui.reading.ImaExplanationSheet
import com.yizhidao.app.ui.reading.TappableScripture
import com.yizhidao.app.auth.LocalUserSession
import com.yizhidao.app.auth.AuthApi
import com.yizhidao.app.auth.LoginError
import com.yizhidao.app.HistoryTrashEntry
import com.yizhidao.app.ai.SavedAIAnalysis
import com.yizhidao.app.classic.ClassicChapter
import com.yizhidao.app.classic.ClassicWing
import com.yizhidao.app.classic.YijingIntroBook
import com.yizhidao.app.classic.YijingIntroChapter
import com.yizhidao.app.ui.reading.AIAnalysisScreen
import com.yizhidao.app.ui.reading.ScaledHexagramFigure
import com.yizhidao.app.ui.theme.AppTheme
import com.yizhidao.app.ui.theme.PaperBackHeader
import com.yizhidao.app.ui.theme.PaperChevron
import com.yizhidao.app.ui.theme.SwipeRevealDelete
import com.yizhidao.app.ui.theme.SwipeRevealActions
import com.yizhidao.app.ui.theme.SwipeAction
import com.yizhidao.app.ui.theme.PaperTextField
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val listTimeFmt = DateTimeFormatter.ofPattern("yyyy/M/d HH:mm").withZone(ZoneId.systemDefault())

private val AvatarOptions = listOf(
    "person.crop.circle.fill",
    "person.fill",
    "moon.stars.fill",
    "sun.max.fill",
    "sparkles",
    "leaf.fill",
    "flame.fill",
    "star.fill",
)

private fun avatarIcon(symbol: String): ImageVector = when (symbol) {
    "person.fill" -> Icons.Outlined.Person
    "moon.stars.fill" -> Icons.Outlined.DarkMode
    "sun.max.fill" -> Icons.Outlined.WbSunny
    "sparkles" -> Icons.Outlined.AutoAwesome
    "leaf.fill" -> Icons.Outlined.Eco
    "flame.fill" -> Icons.Outlined.LocalFireDepartment
    "star.fill" -> Icons.Outlined.Star
    else -> Icons.Outlined.AccountCircle
}

private sealed interface MeRoute {
    data object Home : MeRoute
    data object Login : MeRoute
    data object Profile : MeRoute
    data object AIHistory : MeRoute
    data class AIHistoryItem(val item: SavedAIAnalysis) : MeRoute
    data object Settings : MeRoute
    data object TapSound : MeRoute
    data object Recycle : MeRoute
    data object Intro : MeRoute
    data class IntroChapter(val item: YijingIntroChapter) : MeRoute
    data object Hexagrams : MeRoute
    data class HexagramDetail(val item: Hexagram) : MeRoute
    data object Wings : MeRoute
    data class Wing(val item: ClassicWing) : MeRoute
    data class Chapter(val wingTitle: String, val chapter: ClassicChapter) : MeRoute
}

@Composable
fun MeScreen(
    container: AppContainer,
    onTabBarVisible: (Boolean) -> Unit = {},
) {
    var route by remember { mutableStateOf<MeRoute>(MeRoute.Home) }
    var pendingAfterLogin by remember { mutableStateOf<MeRoute?>(null) }
    val session by container.authStore.session.collectAsState()
    val book = container.classicBook
    val intro = container.introBook

    LaunchedEffect(Unit) {
        val current = container.authStore.load()
        if (!current.isLoggedIn || current.accessToken.isNullOrBlank()) return@LaunchedEffect
        try {
            val me = AuthApi.fetchMe(current.accessToken)
            container.authStore.save(
                current.copy(
                    isLoggedIn = true,
                    phone = me.user.phone ?: current.phone,
                ),
            )
        } catch (_: LoginError.Unauthorized) {
            container.authStore.logout()
        } catch (_: Exception) {
            // 网络异常时保留本地会话
        }
    }

    LaunchedEffect(route) {
        onTabBarVisible(route !is MeRoute.AIHistoryItem)
    }
    DisposableEffect(Unit) {
        onDispose { onTabBarVisible(true) }
    }

    when (val page = route) {
        MeRoute.Home -> MeHome(
            session = session,
            onLogin = { route = MeRoute.Login },
            onEditProfile = { route = MeRoute.Profile },
            onAIHistory = {
                if (session.isLoggedIn) route = MeRoute.AIHistory
                else {
                    pendingAfterLogin = MeRoute.AIHistory
                    route = MeRoute.Login
                }
            },
            onIntro = { route = MeRoute.Intro },
            onHexagrams = { route = MeRoute.Hexagrams },
            onWings = { route = MeRoute.Wings },
            onSettings = { route = MeRoute.Settings },
        )
        MeRoute.Profile -> ProfileEditPage(
            session = session,
            authStore = container.authStore,
            onBack = { route = MeRoute.Home },
        )
        MeRoute.Login -> LoginScreen(
            authStore = container.authStore,
            onBack = {
                pendingAfterLogin = null
                route = MeRoute.Home
            },
            onSuccess = {
                route = pendingAfterLogin ?: MeRoute.Home
                pendingAfterLogin = null
            },
        )
        MeRoute.AIHistory -> AIHistoryPage(
            container = container,
            onBack = { route = MeRoute.Home },
            onOpen = { route = MeRoute.AIHistoryItem(it) },
        )
        is MeRoute.AIHistoryItem -> AIAnalysisScreen(
            result = page.item.toCastResult(),
            saved = page.item,
            hexagramStore = container.hexagramStore,
            authStore = container.authStore,
            analysisStore = container.savedAIStore,
            onBack = { route = MeRoute.AIHistory },
        )
        MeRoute.Settings -> SettingsPage(
            container = container,
            session = session,
            onBack = { route = MeRoute.Home },
            onOpenRecycle = { route = MeRoute.Recycle },
            onOpenTapSound = { route = MeRoute.TapSound },
            onLogout = { container.authStore.logout() },
        )
        MeRoute.TapSound -> TapSoundPage(
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
        is MeRoute.HexagramDetail -> HexagramReader(
            hex = page.item,
            imaStore = container.imaExplanationStore,
            onBack = { route = MeRoute.Hexagrams },
        )
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
    }
}

@Composable
private fun MeHome(
    session: LocalUserSession,
    onLogin: () -> Unit,
    onEditProfile: () -> Unit,
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
                        .then(
                            if (session.isLoggedIn) Modifier.clickable(onClick = onEditProfile)
                            else Modifier,
                        )
                        .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        avatarIcon(session.avatarSymbol),
                        contentDescription = null,
                        tint = if (session.isLoggedIn) AppTheme.accent else AppTheme.secondaryText,
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (session.isLoggedIn) session.displayName else "未登录",
                            fontSize = 17.sp,
                            color = AppTheme.ink,
                            style = AppTheme.compactText,
                        )
                        if (!session.isLoggedIn) {
                            Text(
                                "支持手机号或微信登录",
                                fontSize = 12.sp,
                                color = AppTheme.secondaryText,
                                style = AppTheme.compactText,
                            )
                        }
                    }
                    if (session.isLoggedIn) {
                        PaperChevron()
                    } else {
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
            }
            MeCard {
                MeRow(
                    icon = Icons.AutoMirrored.Outlined.MenuBook,
                    title = "保存的AI解读",
                    trailing = {
                        if (!session.isLoggedIn) {
                            Text("需登录", fontSize = 12.sp, color = AppTheme.secondaryText, style = AppTheme.compactText)
                        }
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
private fun ProfileEditPage(
    session: LocalUserSession,
    authStore: LocalAuthStore,
    onBack: () -> Unit,
) {
    var nicknameDraft by remember { mutableStateOf(session.displayName) }
    var avatarDraft by remember { mutableStateOf(session.avatarSymbol) }
    var validationMessage by remember { mutableStateOf<String?>(null) }

    fun save() {
        val limited = nicknameDraft.trim().take(20)
        if (limited.length !in 2..20) {
            validationMessage = "昵称需为 2-20 个字符"
            return
        }
        authStore.save(
            session.copy(
                displayName = limited,
                avatarSymbol = avatarDraft,
            ),
        )
        onBack()
    }

    Column(Modifier.fillMaxSize()) {
        PaperBackHeader(
            title = "编辑资料",
            onBack = onBack,
            trailing = {
                Text(
                    "保存",
                    color = AppTheme.accent,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(AppTheme.controlShape)
                        .clickable(onClick = { save() })
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    style = AppTheme.compactText,
                )
            },
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            MeCard {
                Text(
                    "头像",
                    fontSize = 13.sp,
                    color = AppTheme.secondaryText,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                    style = AppTheme.compactText,
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AvatarOptions.forEach { symbol ->
                        val selected = avatarDraft == symbol
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (selected) AppTheme.accent else Color.Black.copy(alpha = 0.06f))
                                .clickable { avatarDraft = symbol },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                avatarIcon(symbol),
                                contentDescription = null,
                                tint = if (selected) Color.White else AppTheme.accent,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
            MeCard {
                Text(
                    "昵称",
                    fontSize = 13.sp,
                    color = AppTheme.secondaryText,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                    style = AppTheme.compactText,
                )
                PaperTextField(
                    value = nicknameDraft,
                    onValueChange = { nicknameDraft = it.take(20) },
                    placeholder = "输入昵称",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 14.dp),
                )
            }
        }
    }
    validationMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { validationMessage = null },
            title = { Text("保存失败", color = AppTheme.ink, style = AppTheme.compactText) },
            text = { Text(message, color = AppTheme.ink, style = AppTheme.compactText) },
            confirmButton = {
                TextButton(onClick = { validationMessage = null }) {
                    Text("知道了", color = AppTheme.accent, style = AppTheme.compactText)
                }
            },
            containerColor = AppTheme.cardFill,
        )
    }
}

@Composable
private fun SettingsPage(
    container: AppContainer,
    session: LocalUserSession,
    onBack: () -> Unit,
    onOpenRecycle: () -> Unit,
    onOpenTapSound: () -> Unit,
    onLogout: () -> Unit,
) {
    val trash by container.readingRepository.trash.collectAsState()
    val tapSound = TapSoundPlayer.current()
    var showLogoutConfirm by remember { mutableStateOf(false) }
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
                    icon = Icons.AutoMirrored.Outlined.VolumeUp,
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
                    icon = Icons.Outlined.Delete,
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
            if (session.isLoggedIn) {
                Spacer(Modifier.height(12.dp))
                MeCard {
                    MeRow(
                        icon = null,
                        title = "退出登录",
                        showChevron = false,
                        onClick = { showLogoutConfirm = true },
                    )
                }
            }
        }
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("确认退出登录？") },
            confirmButton = {
                TextButton(onClick = {
                    onLogout()
                    showLogoutConfirm = false
                }) {
                    Text("退出登录", color = AppTheme.ink)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text("取消", color = AppTheme.accent)
                }
            },
            containerColor = AppTheme.parchmentTop,
        )
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
            Text(
                "点按「随机」「一键随机」「摇」「一键摇满」时播放。系统静音时不会出声。",
                fontSize = 13.sp,
                color = AppTheme.secondaryText,
                lineHeight = 18.sp,
                style = AppTheme.compactText,
                modifier = Modifier.padding(start = 4.dp, top = 10.dp),
            )
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
            var revealedId by remember { mutableStateOf<String?>(null) }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp, top = 8.dp),
            ) {
                item {
                    MeCard {
                        entries.forEachIndexed { index, entry ->
                            SwipeRevealActions(
                                revealed = revealedId == entry.id,
                                onRevealedChange = { open ->
                                    revealedId = if (open) {
                                        entry.id
                                    } else if (revealedId == entry.id) {
                                        null
                                    } else {
                                        revealedId
                                    }
                                },
                                actions = listOf(
                                    SwipeAction(
                                        label = "恢复",
                                        background = Color(0xFF34C759),
                                        onClick = {
                                            revealedId = null
                                            scope.launch { container.readingRepository.restoreTrash(entry.id) }
                                        },
                                    ),
                                    SwipeAction(
                                        label = "彻底删除",
                                        background = Color(0xFFFF3B30),
                                        onClick = {
                                            revealedId = null
                                            scope.launch { container.readingRepository.removeTrash(entry.id) }
                                        },
                                    ),
                                ),
                                contentBackground = Color.White,
                            ) {
                                RecycleRow(
                                    entry = entry,
                                    hexTitle = { n ->
                                        val hex = store.hexagram(n)
                                        if (hex != null) "${hex.symbol} ${hex.name}" else "第${n}卦"
                                    },
                                    onClick = {
                                        if (revealedId == entry.id) revealedId = null
                                    },
                                )
                            }
                            if (index < entries.lastIndex) {
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
    onClick: () -> Unit,
) {
    val rec = entry.record
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                hexTitle(rec.primaryNumber),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppTheme.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
                style = AppTheme.compactText,
            )
            rec.resultingNumber?.let { resulting ->
                Text(
                    "→",
                    fontSize = 17.sp,
                    color = AppTheme.secondaryText,
                    style = AppTheme.compactText,
                )
                Text(
                    hexTitle(resulting),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                    style = AppTheme.compactText,
                )
            }
        }
        Text(
            listTimeFmt.format(rec.createdAt),
            fontSize = 12.sp,
            color = AppTheme.secondaryText,
            style = AppTheme.compactText,
        )
        rec.question?.takeIf { it.isNotBlank() }?.let { q ->
            Text(
                q,
                fontSize = 15.sp,
                color = AppTheme.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = AppTheme.compactText,
            )
        }
    }
}

@Composable
private fun AIHistoryPage(
    container: AppContainer,
    onBack: () -> Unit,
    onOpen: (SavedAIAnalysis) -> Unit,
) {
    val items by container.savedAIStore.items.collectAsState()
    var revealedId by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        PaperBackHeader(title = "保存的AI解读", onBack = onBack)
        if (items.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "还没有保存的解读",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.ink,
                    style = AppTheme.compactText,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "觉得合适的 AI 解读，可在结果页点「保存」",
                    fontSize = 13.sp,
                    color = AppTheme.secondaryText,
                    style = AppTheme.compactText,
                )
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)) {
                item {
                    MeCard {
                        items.forEachIndexed { index, item ->
                            SwipeRevealDelete(
                                revealed = revealedId == item.id,
                                onRevealedChange = { open ->
                                    revealedId = if (open) {
                                        item.id
                                    } else if (revealedId == item.id) {
                                        null
                                    } else {
                                        revealedId
                                    }
                                },
                                onDelete = {
                                    revealedId = null
                                    container.savedAIStore.remove(item.id)
                                },
                                contentBackground = Color.White,
                            ) {
                                val hex = container.hexagramStore.hexagram(item.primaryNumber)
                                SavedAIHistoryRow(
                                    item = item,
                                    title = hex?.let { "${it.symbol} ${it.name}" } ?: "第${item.primaryNumber}卦",
                                    onClick = {
                                        if (revealedId == item.id) {
                                            revealedId = null
                                        } else {
                                            onOpen(item)
                                        }
                                    },
                                )
                            }
                            if (index < items.lastIndex) {
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
        }
    }
}

@Composable
private fun SavedAIHistoryRow(
    item: SavedAIAnalysis,
    title: String,
    onClick: () -> Unit,
) {
    val question = item.question?.takeIf { it.isNotBlank() }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                title,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppTheme.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = AppTheme.compactText,
            )
            Text(
                listTimeFmt.format(item.updatedAt),
                fontSize = 12.sp,
                color = AppTheme.secondaryText,
                style = AppTheme.compactText,
            )
            if (question != null) {
                Text(
                    question,
                    fontSize = 15.sp,
                    color = AppTheme.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = AppTheme.compactText,
                )
            } else {
                Text(
                    item.analysis.summary,
                    fontSize = 15.sp,
                    color = AppTheme.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = AppTheme.compactText,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        PaperChevron()
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
private fun HexagramReader(hex: Hexagram, imaStore: ImaExplanationStore, onBack: () -> Unit) {
    var selectedEntry by remember { mutableStateOf<ImaExplanationEntry?>(null) }
    Column(Modifier.fillMaxSize()) {
        PaperBackHeader(title = hex.name, onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(AppTheme.cardFill, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ScaledHexagramFigure(
                    lines = hex.figureLines,
                    movingPositions = emptyList(),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                        style = AppTheme.compactText,
                    )
                }
            }
            ScriptureCard(
                title = "卦辞",
                body = hex.guaci,
                explanationId = ImaExplanationId.guaci(hex.number),
                imaStore = imaStore,
                onSelectExplanation = { selectedEntry = it },
            )
            ScriptureCard(
                title = "彖辞",
                body = HexagramText.prefixed("彖曰：", hex.tuanci),
                explanationId = ImaExplanationId.tuanci(hex.number),
                imaStore = imaStore,
                onSelectExplanation = { selectedEntry = it },
            )
            ScriptureCard(
                title = "大象",
                body = HexagramText.prefixed("象曰：", hex.daxiang),
                explanationId = ImaExplanationId.daxiang(hex.number),
                imaStore = imaStore,
                onSelectExplanation = { selectedEntry = it },
            )
            hex.yaoci.zip(hex.xiaoxiang).forEachIndexed { index, (ci, xiang) ->
                ScriptureCard(
                    body = ci,
                    footnote = "象曰：$xiang",
                    explanationId = ImaExplanationId.yaoPair(hex.number, index + 1),
                    imaStore = imaStore,
                    onSelectExplanation = { selectedEntry = it },
                )
            }
            hex.yong?.let {
                ScriptureCard(
                    body = it.ci,
                    footnote = "象曰：${it.xiang}",
                    explanationId = ImaExplanationId.yong(hex.number),
                    imaStore = imaStore,
                    onSelectExplanation = { selectedEntry = it },
                )
            }
            if (hex.wenyan.isNotEmpty()) {
                ScriptureCard(
                    title = "文言",
                    body = hex.wenyan.joinToString("\n\n"),
                    explanationId = ImaExplanationId.wenyan(hex.number),
                    imaStore = imaStore,
                    onSelectExplanation = { selectedEntry = it },
                )
            }
        }
    }
    selectedEntry?.let { entry ->
        ImaExplanationSheet(
            entry = entry,
            source = imaStore.source,
            onDismiss = { selectedEntry = null },
        )
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
private fun ScriptureCard(
    title: String? = null,
    body: String,
    footnote: String? = null,
    explanationId: String? = null,
    imaStore: ImaExplanationStore? = null,
    onSelectExplanation: ((ImaExplanationEntry) -> Unit)? = null,
) {
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
        val textContent: @Composable () -> Unit = {
            Column {
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
        if (explanationId != null && imaStore != null && onSelectExplanation != null) {
            TappableScripture(
                explanationId = explanationId,
                imaStore = imaStore,
                onSelect = onSelectExplanation,
            ) {
                textContent()
            }
        } else {
            textContent()
        }
    }
}
