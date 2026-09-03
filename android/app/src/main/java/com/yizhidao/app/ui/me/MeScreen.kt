package com.yizhidao.app.ui.me

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.material.icons.outlined.Delete
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.TextButton
import com.yizhidao.app.lang.AppLanguage
import com.yizhidao.app.lang.LocalAppLanguage
import com.yizhidao.app.lang.listLabel
import com.yizhidao.app.lang.numberLabel
import com.yizhidao.app.ui.theme.Text
import com.yizhidao.app.ui.theme.ui
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.yizhidao.Hexagram
import com.yizhidao.HexagramText
import com.yizhidao.app.AppContainer
import com.yizhidao.app.BuildConfig
import com.yizhidao.app.auth.LocalAuthStore
import com.yizhidao.app.ima.ImaExplanationEntry
import com.yizhidao.app.ima.ImaExplanationId
import com.yizhidao.app.ima.ImaExplanationStore
import com.yizhidao.app.ui.reading.ImaExplanationSheet
import com.yizhidao.app.ui.cases.CaseListScreen
import com.yizhidao.app.ui.reading.TappableScripture
import com.yizhidao.app.auth.LocalUserSession
import com.yizhidao.app.auth.AuthApi
import com.yizhidao.app.auth.LoginError
import com.yizhidao.app.auth.isNewerAppVersion
import com.yizhidao.app.HistoryTrashEntry
import com.yizhidao.app.classic.ClassicChapter
import com.yizhidao.app.classic.ClassicWing
import com.yizhidao.app.classic.YijingIntroBook
import com.yizhidao.app.ui.reading.ScaledHexagramFigure
import com.yizhidao.app.ui.reading.ScriptureSourceLine
import com.yizhidao.app.ui.theme.AppTheme
import com.yizhidao.app.ui.theme.PaperBackHeader
import com.yizhidao.app.ui.theme.PaperTabTitle
import com.yizhidao.app.ui.theme.PaperChevron
import com.yizhidao.app.ui.theme.SwipeRevealActions
import com.yizhidao.app.ui.theme.SwipeAction
import com.yizhidao.app.ui.theme.PaperTextField
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val listTimeFmt = DateTimeFormatter.ofPattern("yyyy/M/d HH:mm").withZone(ZoneId.systemDefault())

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
    data object Cases : MeRoute
    data object Feedback : MeRoute
    data object Settings : MeRoute
    data object Recycle : MeRoute
    data object Intro : MeRoute
    data class IntroChapter(val index: Int) : MeRoute
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
    val session by container.authStore.session.collectAsState()
    val book = container.classicBook
    val language = LocalAppLanguage.current
    val intro = if (language.isEnglish) container.introBookEn else container.introBook
    val appContext = LocalContext.current.applicationContext

    LaunchedEffect(session.isLoggedIn, session.accessToken) {
        val token = session.accessToken
        if (!session.isLoggedIn || token.isNullOrBlank()) return@LaunchedEffect
        try {
            val me = AuthApi.fetchMe(token)
            container.authStore.save(session.applying(me.user))
            ProfileSync.pullAvatar(
                context = appContext,
                authStore = container.authStore,
                user = me.user,
                accessToken = token,
            )
        } catch (_: LoginError.Unauthorized) {
            container.authStore.logout()
        } catch (_: Exception) {
            // 网络异常时保留本地会话
        }
    }

    LaunchedEffect(route) {
        onTabBarVisible(route is MeRoute.Home)
    }
    DisposableEffect(Unit) {
        onDispose { onTabBarVisible(true) }
    }

    when (val page = route) {
        MeRoute.Home -> MeHome(
            session = session,
            onLogin = { route = MeRoute.Login },
            onEditProfile = { route = MeRoute.Profile },
            onCases = { route = MeRoute.Cases },
            onIntro = { route = MeRoute.Intro },
            onHexagrams = { route = MeRoute.Hexagrams },
            onWings = { route = MeRoute.Wings },
            onFeedback = { route = MeRoute.Feedback },
            onSettings = { route = MeRoute.Settings },
        )
        MeRoute.Profile -> ProfileScreen(
            session = session,
            authStore = container.authStore,
            onBack = { route = MeRoute.Home },
            onLogout = { container.authStore.logout() },
        )
        MeRoute.Login -> LoginScreen(
            authStore = container.authStore,
            onBack = { route = MeRoute.Home },
            onSuccess = { route = MeRoute.Home },
        )
        MeRoute.Cases -> CaseListScreen(
            container = container,
            onBack = { route = MeRoute.Home },
        )
        MeRoute.Feedback -> FeedbackPage(
            session = session,
            onBack = { route = MeRoute.Home },
        )
        MeRoute.Settings -> SettingsPage(
            container = container,
            session = session,
            onBack = { route = MeRoute.Home },
            onOpenRecycle = { route = MeRoute.Recycle },
            onLogout = { container.authStore.logout() },
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
        is MeRoute.IntroChapter -> IntroChapterReader(
            book = intro,
            index = page.index,
            onBack = { route = MeRoute.Intro },
            onOpenChapter = { route = MeRoute.IntroChapter(it) },
            onOpenLink = { dest ->
                route = when (dest) {
                    "hexagrams" -> MeRoute.Hexagrams
                    "cases" -> MeRoute.Cases
                    "wings" -> MeRoute.Wings
                    else -> route
                }
            },
        )
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
    onCases: () -> Unit,
    onIntro: () -> Unit,
    onHexagrams: () -> Unit,
    onWings: () -> Unit,
    onFeedback: () -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val language = LocalAppLanguage.current
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateTitle by remember { mutableStateOf<String?>(null) }
    var updateMessage by remember { mutableStateOf("") }
    var updateStoreUrl by remember { mutableStateOf<String?>(null) }

    fun checkForUpdate() {
        if (isCheckingUpdate) return
        isCheckingUpdate = true
        scope.launch {
            try {
                val info = AuthApi.fetchAppVersion()
                val latest = info.android.trim()
                val current = BuildConfig.VERSION_NAME
                if (isNewerAppVersion(latest, current) && info.androidStoreUrl.isNotBlank()) {
                    updateTitle = language.ui("发现新版本", "Update available")
                    updateMessage = language.ui("最新版本 $latest，可前往商店更新。", "Version $latest is available.")
                    updateStoreUrl = info.androidStoreUrl
                } else {
                    updateTitle = language.ui("已是最新版本", "You're up to date")
                    updateMessage = language.ui("当前版本 ${current.ifBlank { latest }}", "Version ${current.ifBlank { latest }}")
                    updateStoreUrl = null
                }
            } catch (error: Exception) {
                updateTitle = language.ui("检查失败", "Couldn't check")
                updateMessage = AuthApi.describe(error)
                updateStoreUrl = null
            } finally {
                isCheckingUpdate = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        PaperTabTitle("我的", "Me")
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
                    if (session.isLoggedIn) {
                        ProfileAvatar(
                            name = session.displayName,
                            image = if (session.avatarImagePath == null) {
                                null
                            } else {
                                ProfileAvatarFile.load(LocalContext.current)
                            },
                            size = 40.dp,
                        )
                    } else {
                        Icon(
                            avatarIcon(session.avatarSymbol),
                            contentDescription = null,
                            tint = AppTheme.secondaryText,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        if (session.isLoggedIn) {
                            Text(
                                session.displayName,
                                fontSize = 17.sp,
                                color = AppTheme.ink,
                                style = AppTheme.compactText,
                            )
                        } else {
                            Text(
                                "未登录",
                                fontSize = 17.sp,
                                color = AppTheme.ink,
                                style = AppTheme.compactText,
                                en = "Not signed in",
                            )
                        }
                        if (!session.isLoggedIn) {
                            Text(
                                "支持 Google / 邮箱登录",
                                fontSize = 12.sp,
                                color = AppTheme.secondaryText,
                                style = AppTheme.compactText,
                                en = "Google or email",
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
                            en = "Sign In",
                        )
                    }
                }
            }
            MeCard {
                MeRow(
                    icon = Icons.AutoMirrored.Outlined.MenuBook,
                    title = "基础入门",
                    titleEn = "Primer",
                    onClick = onIntro,
                )
                MeDivider()
                MeRow(
                    icon = Icons.Outlined.AutoStories,
                    title = "六十四卦",
                    titleEn = "64 Hexagrams",
                    onClick = onHexagrams,
                )
                MeDivider()
                MeRow(
                    icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                    title = "四传",
                    titleEn = "The Wings",
                    onClick = onWings,
                )
                MeDivider()
                MeRow(
                    icon = Icons.AutoMirrored.Outlined.MenuBook,
                    title = "案例",
                    titleEn = "Cases",
                    onClick = onCases,
                )
            }
            MeCard {
                MeRow(
                    icon = Icons.Outlined.Email,
                    title = "意见反馈",
                    titleEn = "Feedback",
                    onClick = onFeedback,
                )
                MeDivider()
                MeRow(
                    icon = Icons.Outlined.SystemUpdate,
                    title = "检查更新",
                    titleEn = "Check for Update",
                    trailing = {
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = AppTheme.accent,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(
                                BuildConfig.VERSION_NAME,
                                fontSize = 15.sp,
                                color = AppTheme.secondaryText,
                                style = AppTheme.compactText,
                            )
                        }
                    },
                    showChevron = false,
                    onClick = { checkForUpdate() },
                )
            }
            MeCard {
                MeRow(
                    icon = Icons.Outlined.Settings,
                    title = "设置",
                    titleEn = "Settings",
                    onClick = onSettings,
                )
            }
        }
    }
    updateTitle?.let { title ->
        AlertDialog(
            onDismissRequest = { updateTitle = null },
            title = { Text(title, color = AppTheme.ink, style = AppTheme.compactText) },
            text = { Text(updateMessage, color = AppTheme.ink, style = AppTheme.compactText) },
            confirmButton = {
                val storeUrl = updateStoreUrl
                if (storeUrl != null) {
                    TextButton(
                        onClick = {
                            updateTitle = null
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(storeUrl)))
                            }
                        },
                    ) {
                        Text("去更新", color = AppTheme.accent, style = AppTheme.compactText, en = "Update")
                    }
                } else {
                    TextButton(onClick = { updateTitle = null }) {
                        Text("好的", color = AppTheme.accent, style = AppTheme.compactText, en = "OK")
                    }
                }
            },
            dismissButton = if (updateStoreUrl != null) {
                {
                    TextButton(onClick = { updateTitle = null }) {
                        Text("以后再说", color = AppTheme.accent, style = AppTheme.compactText, en = "Later")
                    }
                }
            } else {
                null
            },
            containerColor = AppTheme.cardFill,
        )
    }
}

@Composable
private fun FeedbackPage(
    session: LocalUserSession,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var bodyDraft by remember { mutableStateOf("") }
    var contactDraft by remember { mutableStateOf(session.email ?: session.phone.orEmpty()) }
    var isSubmitting by remember { mutableStateOf(false) }
    var showSuccess by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val trimmed = bodyDraft.trim()
    val canSubmit = trimmed.length >= 5 && !isSubmitting

    fun submit() {
        if (isSubmitting || trimmed.length < 5) return
        isSubmitting = true
        scope.launch {
            try {
                AuthApi.submitFeedback(
                    body = trimmed.take(2000),
                    contact = contactDraft.trim(),
                    accessToken = session.accessToken,
                )
                showSuccess = true
            } catch (error: Exception) {
                errorMessage = AuthApi.describe(error)
            } finally {
                isSubmitting = false
            }
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        PaperBackHeader(
            title = "意见反馈",
            titleEn = "Feedback",
            onBack = onBack,
            trailing = {
                Text(
                    if (isSubmitting) ui("提交中", "Sending") else ui("提交", "Send"),
                    color = if (canSubmit) AppTheme.accent else AppTheme.secondaryText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(AppTheme.controlShape)
                        .then(if (canSubmit) Modifier.clickable(onClick = { submit() }) else Modifier)
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
                    "意见",
                    fontSize = 13.sp,
                    color = AppTheme.secondaryText,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                    style = AppTheme.compactText,
                    en = "Feedback",
                )
                PaperTextField(
                    value = bodyDraft,
                    onValueChange = { bodyDraft = it.take(2000) },
                    placeholder = "想说的话（至少 5 个字）",
                    placeholderEn = "What you’d like to say (at least 5 characters)",
                    singleLine = false,
                    minLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
                Text(
                    "${trimmed.length}/2000",
                    fontSize = 12.sp,
                    color = AppTheme.secondaryText,
                    modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 12.dp),
                    style = AppTheme.compactText,
                )
            }
            MeCard {
                Text(
                    "联系方式",
                    fontSize = 13.sp,
                    color = AppTheme.secondaryText,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                    style = AppTheme.compactText,
                    en = "Contact (optional)",
                )
                PaperTextField(
                    value = contactDraft,
                    onValueChange = { contactDraft = it.take(120) },
                    placeholder = "邮箱或其它联系方式（选填）",
                    placeholderEn = "Email or other contact (optional)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
                Text(
                    if (session.isLoggedIn) {
                        ui("已登录时会带上账号，方便我们对照。", "We’ll include your account so we can follow up.")
                    } else {
                        ui("未登录也可以提交。留下联系方式，有进展时方便回你。", "You can send this without signing in. Leave a contact if you’d like a reply.")
                    },
                    fontSize = 12.sp,
                    color = AppTheme.secondaryText,
                    modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 14.dp),
                    style = AppTheme.compactText,
                )
            }
        }
    }
    if (showSuccess) {
        AlertDialog(
            onDismissRequest = onBack,
            title = { Text("已收到", color = AppTheme.ink, style = AppTheme.compactText, en = "Received") },
            text = { Text("感谢反馈，我们会尽快查看。", color = AppTheme.ink, style = AppTheme.compactText, en = "Thank you. We’ll look at this soon.") },
            confirmButton = {
                TextButton(onClick = onBack) {
                    Text("好的", color = AppTheme.accent, style = AppTheme.compactText, en = "OK")
                }
            },
            containerColor = AppTheme.cardFill,
        )
    }
    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("提交失败", color = AppTheme.ink, style = AppTheme.compactText, en = "Couldn't send") },
            text = { Text(message, color = AppTheme.ink, style = AppTheme.compactText) },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) {
                    Text("知道了", color = AppTheme.accent, style = AppTheme.compactText, en = "OK")
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
    onLogout: () -> Unit,
) {
    val trash by container.readingRepository.trash.collectAsState()
    val scope = rememberCoroutineScope()
    val language = LocalAppLanguage.current
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isDeletingAccount by remember { mutableStateOf(false) }
    var deleteErrorMessage by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        PaperBackHeader(title = "设置", titleEn = "Settings", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            MeCard {
                MeRow(
                    icon = Icons.Outlined.Delete,
                    title = "回收站",
                    titleEn = "Trash",
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
                        titleEn = "Sign Out",
                        showChevron = false,
                        onClick = { showLogoutConfirm = true },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = AppTheme.fieldStroke,
                        thickness = 0.5.dp,
                    )
                    MeRow(
                        icon = null,
                        title = "注销账号",
                        titleEn = "Delete Account",
                        titleColor = Color(0xFFFF3B30),
                        showChevron = false,
                        enabled = !isDeletingAccount,
                        onClick = { showDeleteConfirm = true },
                    )
                }
            }
        }
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("确认退出登录？", en = "Sign out?") },
            confirmButton = {
                TextButton(onClick = {
                    onLogout()
                    showLogoutConfirm = false
                }) {
                    Text("退出登录", color = AppTheme.ink, en = "Sign Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text("取消", color = AppTheme.accent, en = "Cancel")
                }
            },
            containerColor = AppTheme.parchmentTop,
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { if (!isDeletingAccount) showDeleteConfirm = false },
            title = { Text("确认注销账号？", en = "Delete this account?") },
            text = {
                Text(
                    "注销后，服务器上的账号信息将被永久删除且不可恢复。设备本地的起卦记录与保存的问答不会自动清除。",
                    color = AppTheme.ink,
                    style = AppTheme.compactText,
                    en = "This permanently deletes your account on the server. Casts and readings saved on this device stay until you remove them.",
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !isDeletingAccount,
                    onClick = {
                        val token = session.accessToken
                        if (token.isNullOrBlank()) {
                            deleteErrorMessage = language.ui(
                                "登录态已失效，请重新登录",
                                "Session expired. Please sign in again.",
                            )
                            showDeleteConfirm = false
                            return@TextButton
                        }
                        scope.launch {
                            isDeletingAccount = true
                            try {
                                AuthApi.deleteAccount(token)
                                onLogout()
                                showDeleteConfirm = false
                                onBack()
                            } catch (e: Exception) {
                                deleteErrorMessage = AuthApi.describe(e)
                                showDeleteConfirm = false
                            } finally {
                                isDeletingAccount = false
                            }
                        }
                    },
                ) {
                    if (isDeletingAccount) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color(0xFFFF3B30),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("注销账号", color = Color(0xFFFF3B30), en = "Delete Account")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isDeletingAccount,
                    onClick = { showDeleteConfirm = false },
                ) {
                    Text("取消", color = AppTheme.accent, en = "Cancel")
                }
            },
            containerColor = AppTheme.parchmentTop,
        )
    }

    deleteErrorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { deleteErrorMessage = null },
            title = { Text("注销失败", en = "Couldn’t delete account") },
            text = {
                Text(message, color = AppTheme.ink, style = AppTheme.compactText)
            },
            confirmButton = {
                TextButton(onClick = { deleteErrorMessage = null }) {
                    Text("知道了", color = AppTheme.accent, en = "OK")
                }
            },
            containerColor = AppTheme.parchmentTop,
        )
    }
}

@Composable
private fun RecycleBinPage(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val entries by container.readingRepository.trash.collectAsState()
    val scope = rememberCoroutineScope()
    val language = LocalAppLanguage.current
    var showClearConfirm by remember { mutableStateOf(false) }
    val store = container.hexagramStore

    Column(Modifier.fillMaxSize()) {
        PaperBackHeader(
            title = "回收站",
            titleEn = "Trash",
            onBack = onBack,
            trailing = if (entries.isNotEmpty()) {
                {
                    Text(
                        "清空",
                        color = Color(0xFFA64040),
                        fontSize = 16.sp,
                        modifier = Modifier.clickable { showClearConfirm = true },
                        style = AppTheme.compactText,
                        en = "Empty",
                    )
                }
            } else {
                null
            },
        )
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("回收站为空", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = AppTheme.ink, en = "Trash is empty")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "删除的记录会先放在这里，可恢复",
                        fontSize = 13.sp,
                        color = AppTheme.secondaryText,
                        en = "Deleted records stay here until you restore or remove them",
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
                                        label = ui("恢复", "Restore"),
                                        background = Color(0xFF34C759),
                                        onClick = {
                                            revealedId = null
                                            scope.launch { container.readingRepository.restoreTrash(entry.id) }
                                        },
                                    ),
                                    SwipeAction(
                                        label = ui("彻底删除", "Delete"),
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
                                        if (hex != null) hex.listLabel(language) else numberLabel(language, n)
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
            title = { Text("确认清空？", en = "Empty trash?") },
            text = { Text("回收站中的记录将被彻底删除，无法恢复。", en = "These records will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    scope.launch { container.readingRepository.clearTrash() }
                }) {
                    Text("确定", color = Color(0xFFA64040), en = "OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("取消", color = AppTheme.accent, en = "Cancel")
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
    titleEn: String? = null,
    titleColor: Color = AppTheme.ink,
    trailing: @Composable (() -> Unit)? = null,
    showChevron: Boolean = true,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
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
            color = if (enabled) titleColor else AppTheme.disabledText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            style = AppTheme.compactText,
            en = titleEn,
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
    onOpen: (Int) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        PaperBackHeader(title = "基础入门", titleEn = "Primer", onBack = onBack)
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
                        Column {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpen(index) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    introChapterMark(index),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppTheme.accent,
                                    modifier = Modifier.width(22.dp),
                                    style = AppTheme.compactText,
                                )
                                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        chapter.title,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AppTheme.ink,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = AppTheme.compactText,
                                    )
                                    if (chapter.subtitle.isNotBlank()) {
                                        Text(
                                            chapter.subtitle,
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
                            if (index < book.chapters.lastIndex) {
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
        PaperBackHeader(title = "四传", titleEn = "The Wings", onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            item(key = "wings") {
                MeCard {
                    wings.forEachIndexed { index, wing ->
                        PaperNavRow(
                            title = wing.title,
                            subtitle = if (wing.chapters.size == 1) {
                                ui("${wing.chapters[0].paragraphs.size} 节", "${wing.chapters[0].paragraphs.size} sections")
                            } else {
                                ui("${wing.chapters.size} 章", "${wing.chapters.size} chapters")
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
    val language = LocalAppLanguage.current
    val upper = hexagrams.filter { it.part == "上经" }.ifEmpty { hexagrams.take(30) }
    val lower = hexagrams.filter { it.part == "下经" }.ifEmpty { hexagrams.drop(upper.size) }
    Column(Modifier.fillMaxSize()) {
        PaperBackHeader(title = "六十四卦", titleEn = "64 Hexagrams", onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            hexagramSection("上经", "Upper Canon", upper, language, onOpen)
            hexagramSection("下经", "Lower Canon", lower, language, onOpen)
        }
    }
}

private fun LazyListScope.hexagramSection(
    title: String,
    titleEn: String,
    hexagrams: List<Hexagram>,
    language: AppLanguage,
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
            en = titleEn,
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
                        hex.listLabel(language),
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
private fun HexagramReader(hex: Hexagram, imaStore: ImaExplanationStore, onBack: () -> Unit) {
    var selectedEntry by remember { mutableStateOf<ImaExplanationEntry?>(null) }
    Column(Modifier.fillMaxSize()) {
        PaperBackHeader(title = hex.listLabel(LocalAppLanguage.current), onBack = onBack)
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
            ScriptureSourceLine(Modifier.align(Alignment.End))
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
            ScriptureSourceLine(Modifier.align(Alignment.End))
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
