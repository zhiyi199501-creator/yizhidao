package com.yizhidao.app.ui.me

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yizhidao.app.auth.AuthApi
import com.yizhidao.app.auth.LocalAuthStore
import com.yizhidao.app.auth.LocalUserSession
import com.yizhidao.app.ui.theme.AppTheme
import com.yizhidao.app.ui.theme.PaperBackHeader
import com.yizhidao.app.ui.theme.Text
import com.yizhidao.app.ui.theme.ui
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object ProfileAvatarFile {
    const val FILE_NAME = "profile-avatar.jpg"

    fun file(context: android.content.Context): File = File(context.filesDir, FILE_NAME)

    fun load(context: android.content.Context): ImageBitmap? {
        val target = file(context)
        if (!target.exists()) return null
        return BitmapFactory.decodeFile(target.absolutePath)?.asImageBitmap()
    }

    fun save(context: android.content.Context, uri: Uri): ImageBitmap? {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        file(context).writeBytes(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }

    fun save(context: android.content.Context, bytes: ByteArray): ImageBitmap? {
        file(context).writeBytes(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }

    fun clear(context: android.content.Context) {
        file(context).delete()
    }
}

@Composable
fun ProfileAvatar(
    name: String,
    image: ImageBitmap?,
    size: androidx.compose.ui.unit.Dp,
    showCamera: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val letter = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(modifier.size(size), contentAlignment = Alignment.BottomEnd) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .then(
                    if (image == null) Modifier.background(avatarBrush(name))
                    else Modifier,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    letter,
                    color = Color.White,
                    fontSize = (size.value * 0.42f).sp,
                    fontWeight = FontWeight.SemiBold,
                    style = AppTheme.compactText,
                )
            }
        }
        if (showCamera) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.CameraAlt,
                    contentDescription = null,
                    tint = Color(0xFF387AEF),
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

private fun avatarBrush(name: String): Brush {
    val palettes = listOf(
        listOf(Color(0xFF9E6BEB), Color(0xFFF27AAD)),
        listOf(Color(0xFF6185E6), Color(0xFF8CC7EB)),
        listOf(Color(0xFFE67A61), Color(0xFFF5B86B)),
        listOf(Color(0xFF479E94), Color(0xFF7ACC9E)),
    )
    val pair = palettes[kotlin.math.abs(name.hashCode()) % palettes.size]
    return Brush.verticalGradient(pair)
}

internal object ProfileSync {
    suspend fun pullAvatar(
        context: android.content.Context,
        authStore: LocalAuthStore,
        user: AuthApi.AccountUser,
        accessToken: String,
    ) {
        val current = authStore.session.value
        if (user.hasAvatar == true) {
            if (user.avatarUpdatedAt == current.avatarUpdatedAt &&
                current.avatarImagePath != null &&
                ProfileAvatarFile.load(context) != null
            ) {
                return
            }
            val bytes = runCatching { AuthApi.fetchAvatar(accessToken) }.getOrNull() ?: return
            ProfileAvatarFile.save(context, bytes)
            authStore.save(
                current.copy(
                    avatarImagePath = ProfileAvatarFile.FILE_NAME,
                    avatarUpdatedAt = user.avatarUpdatedAt,
                ),
            )
            return
        }
        if (current.avatarImagePath != null && ProfileAvatarFile.load(context) != null) {
            val me = runCatching {
                AuthApi.uploadAvatar(ProfileAvatarFile.file(context).readBytes(), accessToken)
            }.getOrNull() ?: return
            applyRemoteUser(authStore, me.user)
            authStore.save(
                authStore.session.value.copy(
                    avatarImagePath = ProfileAvatarFile.FILE_NAME,
                    avatarUpdatedAt = me.user.avatarUpdatedAt,
                ),
            )
        }
    }

    fun applyRemoteUser(authStore: LocalAuthStore, user: AuthApi.AccountUser) {
        val current = authStore.session.value
        authStore.save(
            current.copy(
                displayName = user.nickname,
                email = user.email ?: current.email,
                avatarUpdatedAt = user.avatarUpdatedAt,
            ),
        )
    }

    suspend fun hydrateAfterLogin(
        context: android.content.Context,
        authStore: LocalAuthStore,
        resp: AuthApi.LoginResponse,
        email: String? = null,
    ) {
        authStore.save(
            LocalUserSession(
                isLoggedIn = true,
                displayName = resp.user.nickname,
                accessToken = resp.accessToken,
                email = resp.user.email ?: email,
            ).applying(resp.user),
        )
        pullAvatar(context, authStore, resp.user, resp.accessToken)
    }
}

@Composable
fun ProfileScreen(
    session: LocalUserSession,
    authStore: LocalAuthStore,
    onBack: () -> Unit,
    onLogout: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var nicknameDraft by remember { mutableStateOf(session.displayName) }
    var avatar by remember { mutableStateOf(if (session.avatarImagePath == null) null else ProfileAvatarFile.load(context)) }
    var saveJob by remember { mutableStateOf<Job?>(null) }
    var bindEmailDraft by remember { mutableStateOf("") }
    var bindCodeDraft by remember { mutableStateOf("") }
    var bindCooldownSec by remember { mutableIntStateOf(0) }
    var isSendingBindCode by remember { mutableStateOf(false) }
    var isBindingEmail by remember { mutableStateOf(false) }
    var bindMessage by remember { mutableStateOf<String?>(null) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    val codeSentMessage = ui("验证码已发送", "Code sent")

    val hasBoundEmail = !session.email.isNullOrBlank()

    LaunchedEffect(bindCooldownSec) {
        if (bindCooldownSec <= 0) return@LaunchedEffect
        delay(1_000)
        bindCooldownSec -= 1
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        avatar = ProfileAvatarFile.save(context, uri)
        authStore.save(authStore.session.value.copy(avatarImagePath = ProfileAvatarFile.FILE_NAME))
        val token = session.accessToken ?: return@rememberLauncherForActivityResult
        scope.launch {
            val me = runCatching { AuthApi.uploadAvatar(ProfileAvatarFile.file(context).readBytes(), token) }
                .getOrNull() ?: return@launch
            ProfileSync.applyRemoteUser(authStore, me.user)
            authStore.save(
                authStore.session.value.copy(
                    avatarImagePath = ProfileAvatarFile.FILE_NAME,
                    avatarUpdatedAt = me.user.avatarUpdatedAt,
                ),
            )
        }
    }

    fun persist(force: Boolean) {
        if (!authStore.session.value.isLoggedIn) return
        val limited = nicknameDraft.trim().take(20)
        if (limited.length !in 2..20) {
            if (force) nicknameDraft = session.displayName
            return
        }
        nicknameDraft = limited
        authStore.save(
            authStore.session.value.copy(
                displayName = limited,
                avatarImagePath = if (avatar == null) null else ProfileAvatarFile.FILE_NAME,
            ),
        )
        val token = session.accessToken ?: return
        scope.launch {
            val me = runCatching { AuthApi.updateMe(limited, token) }.getOrNull() ?: return@launch
            ProfileSync.applyRemoteUser(authStore, me.user)
        }
    }

    fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(700)
            persist(force = false)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            saveJob?.cancel()
            persist(force = true)
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        PaperBackHeader(title = "个人资料", titleEn = "Profile", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.clickable {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                ) {
                    ProfileAvatar(name = nicknameDraft, image = avatar, size = 88.dp, showCamera = true)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    nicknameDraft,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.ink,
                    style = AppTheme.compactText,
                )
            }

            Column {
                SectionLabel("资料", "Details")
                ProfileCard {
                    ProfileRow(title = "昵称", titleEn = "Name") {
                        PlainField(
                            value = nicknameDraft,
                            onValueChange = {
                                nicknameDraft = it.take(20)
                                scheduleSave()
                            },
                            alignEnd = true,
                        )
                    }
                }
            }

            Column {
                SectionLabel("账户", "Account")
                ProfileCard {
                    if (hasBoundEmail) {
                        ProfileRow(
                            title = "邮箱",
                            titleEn = "Email",
                            trailing = session.email,
                        )
                    } else {
                        ProfileRow(title = "邮箱", titleEn = "Email") {
                            PlainField(
                                value = bindEmailDraft,
                                onValueChange = { bindEmailDraft = it },
                                alignEnd = true,
                                placeholder = ui("绑定后可邮箱登录", "Link to sign in with email"),
                            )
                        }
                        ProfileDivider()
                        ProfileRow(title = "验证码", titleEn = "Code") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PlainField(
                                    value = bindCodeDraft,
                                    onValueChange = { bindCodeDraft = it.filter { ch -> ch.isDigit() } },
                                    alignEnd = true,
                                    placeholder = ui("验证码", "Code"),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (bindCooldownSec > 0) "${bindCooldownSec}s" else ui("发送", "Send"),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (!isSendingBindCode && bindCooldownSec <= 0 && isValidEmail(bindEmailDraft.trim())) {
                                        AppTheme.accent
                                    } else {
                                        AppTheme.secondaryText.copy(alpha = 0.5f)
                                    },
                                    modifier = Modifier.clickable(
                                        enabled = !isSendingBindCode && bindCooldownSec <= 0 && isValidEmail(bindEmailDraft.trim()),
                                    ) {
                                        val trimmed = bindEmailDraft.trim()
                                        val token = session.accessToken ?: return@clickable
                                        scope.launch {
                                            isSendingBindCode = true
                                            try {
                                                val resp = AuthApi.sendBindEmailCode(trimmed, token)
                                                bindCooldownSec = resp.cooldownSec.coerceAtLeast(0)
                                                bindMessage = codeSentMessage
                                            } catch (e: Exception) {
                                                bindMessage = AuthApi.describe(e)
                                            } finally {
                                                isSendingBindCode = false
                                            }
                                        }
                                    },
                                    style = AppTheme.compactText,
                                )
                            }
                        }
                        ProfileDivider()
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clickable(
                                    enabled = !isBindingEmail &&
                                        isValidEmail(bindEmailDraft.trim()) &&
                                        bindCodeDraft.isNotBlank(),
                                ) {
                                    val trimmedEmail = bindEmailDraft.trim()
                                    val trimmedCode = bindCodeDraft.trim()
                                    val token = session.accessToken ?: return@clickable
                                    scope.launch {
                                        isBindingEmail = true
                                        try {
                                            val me = AuthApi.bindEmail(trimmedEmail, trimmedCode, token)
                                            ProfileSync.applyRemoteUser(authStore, me.user)
                                            authStore.save(
                                                authStore.session.value.copy(
                                                    email = me.user.email ?: trimmedEmail,
                                                ),
                                            )
                                            bindMessage = null
                                        } catch (e: Exception) {
                                            bindMessage = AuthApi.describe(e)
                                        } finally {
                                            isBindingEmail = false
                                        }
                                    }
                                }
                                .padding(vertical = 13.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isBindingEmail) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = AppTheme.accent,
                                )
                            } else {
                                Text(
                                    ui("绑定邮箱", "Link email"),
                                    fontSize = 16.sp,
                                    color = AppTheme.accent,
                                    style = AppTheme.compactText,
                                )
                            }
                        }
                        bindMessage?.let { message ->
                            Text(
                                message,
                                fontSize = 12.sp,
                                color = if (message == codeSentMessage) AppTheme.secondaryText else Color(0xFFC0392B),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                style = AppTheme.compactText,
                            )
                        }
                    }
                    val phone = session.phone
                    if (!phone.isNullOrBlank()) {
                        ProfileDivider()
                        ProfileRow(title = "手机", titleEn = "Phone", trailing = phone)
                    }
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.92f))
                    .clickable { showLogoutConfirm = true }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "退出登录",
                    fontSize = 17.sp,
                    color = AppTheme.ink,
                    style = AppTheme.compactText,
                    en = "Sign Out",
                )
            }
        }
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("确认退出登录？", en = "Sign out?") },
            confirmButton = {
                TextButton(onClick = {
                    saveJob?.cancel()
                    onLogout()
                    showLogoutConfirm = false
                    onBack()
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
}

@Composable
private fun SectionLabel(zh: String, en: String) {
    Text(
        zh,
        fontSize = 13.sp,
        color = AppTheme.secondaryText,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        style = AppTheme.compactText,
        en = en,
    )
}

@Composable
private fun ProfileCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.92f)),
    ) { content() }
}

@Composable
private fun ProfileDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        color = Color.Black.copy(alpha = 0.06f),
        thickness = 0.5.dp,
    )
}

@Composable
private fun ProfileRow(
    title: String,
    titleEn: String? = null,
    trailing: String? = null,
    content: @Composable (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            fontSize = 17.sp,
            color = AppTheme.ink,
            style = AppTheme.compactText,
            en = titleEn,
        )
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (content != null) {
                content()
            } else if (trailing != null) {
                Text(
                    trailing,
                    fontSize = 16.sp,
                    color = AppTheme.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    style = AppTheme.compactText,
                )
            }
        }
    }
}

@Composable
private fun PlainField(
    value: String,
    onValueChange: (String) -> Unit,
    alignEnd: Boolean = false,
    placeholder: String = "",
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        cursorBrush = SolidColor(AppTheme.accent),
        textStyle = AppTheme.compactText.merge(
            TextStyle(
                fontSize = 16.sp,
                color = AppTheme.secondaryText,
                textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
            ),
        ),
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth(), contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(
                        placeholder,
                        fontSize = 16.sp,
                        color = AppTheme.secondaryText.copy(alpha = 0.45f),
                        textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
                        style = AppTheme.compactText,
                    )
                }
                inner()
            }
        },
    )
}

private fun isValidEmail(raw: String): Boolean {
    val trimmed = raw.trim()
    return trimmed.contains("@") && trimmed.contains(".") && trimmed.length >= 5
}
