package com.yizhidao.app.ui.me

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yizhidao.app.BuildConfig
import com.yizhidao.app.auth.AuthApi
import com.yizhidao.app.auth.GoogleSignInHelper
import com.yizhidao.app.auth.LocalAuthStore
import com.yizhidao.app.auth.LocalUserSession
import com.yizhidao.app.ui.theme.AppTheme
import com.yizhidao.app.ui.theme.PaperBackHeader
import com.yizhidao.app.ui.theme.PaperTextField
import com.yizhidao.app.ui.theme.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val LoginShape = RoundedCornerShape(15.dp)

private enum class LoginPage {
    Main,
    Email,
}

private enum class PendingEmailAction {
    SendEmailCode,
    EmailLogin,
}

@Composable
fun LoginScreen(
    authStore: LocalAuthStore,
    onBack: () -> Unit,
    onSuccess: () -> Unit,
) {
    var page by remember { mutableStateOf(LoginPage.Main) }
    var agreed by remember { mutableStateOf(false) }

    when (page) {
        LoginPage.Main -> MainLoginPage(
            authStore = authStore,
            agreed = agreed,
            onAgreedChange = { agreed = it },
            onBack = onBack,
            onSuccess = onSuccess,
            onEmailLogin = { page = LoginPage.Email },
        )
        LoginPage.Email -> EmailLoginPage(
            agreed = agreed,
            onAgreedChange = { agreed = it },
            authStore = authStore,
            onBack = { page = LoginPage.Main },
            onSuccess = onSuccess,
        )
    }
}

@Composable
private fun MainLoginPage(
    authStore: LocalAuthStore,
    agreed: Boolean,
    onAgreedChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onSuccess: () -> Unit,
    onEmailLogin: () -> Unit,
) {
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoggingIn by remember { mutableStateOf(false) }
    var showConsentDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun performGoogle() {
        scope.launch {
            isLoggingIn = true
            try {
                val idToken = GoogleSignInHelper.signIn(context)
                val resp = AuthApi.loginWithGoogle(idToken)
                authStore.save(
                    LocalUserSession(
                        isLoggedIn = true,
                        displayName = resp.user.nickname,
                        phone = resp.user.phone,
                        email = resp.user.email,
                        avatarSymbol = "person.crop.circle.fill",
                        accessToken = resp.accessToken,
                    ),
                )
                onSuccess()
            } catch (e: Exception) {
                errorMessage = AuthApi.describe(e)
            } finally {
                isLoggingIn = false
            }
        }
    }

    fun requireConsent() {
        if (agreed) {
            performGoogle()
            return
        }
        showConsentDialog = true
    }

    Column(Modifier.fillMaxSize()) {
        PaperBackHeader(
            title = "",
            onBack = onBack,
            leading = {
                TextButton(onClick = onBack) {
                    Text("取消", color = AppTheme.accent, fontSize = 17.sp, style = AppTheme.compactText)
                }
            },
        )
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            LoginBrandMark()

            Spacer(Modifier.height(36.dp))

            LoginFilledButton(
                onClick = { requireConsent() },
                label = "Google 登录",
                enabled = !isLoggingIn && GoogleSignInHelper.isConfigured,
                leading = {
                    Icon(
                        Icons.Default.Language,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                },
            )

            Spacer(Modifier.height(16.dp))

            LoginStatusLine(
                isBusy = isLoggingIn,
                message = errorMessage
                    ?: "Google 登录需在 build.gradle.kts 配置 GOOGLE_WEB_CLIENT_ID"
                        .takeIf { !GoogleSignInHelper.isConfigured },
                isError = errorMessage != null,
            )

            Spacer(Modifier.height(16.dp))

            ConsentRow(agreed = agreed, onAgreedChange = onAgreedChange)

            Spacer(Modifier.weight(1f))

            LoginSectionDivider("其他登录方式")

            Spacer(Modifier.height(14.dp))

            LoginGhostButton(
                onClick = onEmailLogin,
                label = "邮箱登录",
                leading = {
                    Icon(
                        Icons.Default.Email,
                        contentDescription = null,
                        tint = AppTheme.accent,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                },
            )

            if (BuildConfig.DEBUG) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "当前接口：${AuthApi.baseUrl}",
                    fontSize = 11.sp,
                    color = AppTheme.secondaryText.copy(alpha = 0.7f),
                    style = AppTheme.compactText,
                )
            }
        }
    }

    if (showConsentDialog) {
        ConsentDialog(
            onDismiss = { showConsentDialog = false },
            onAgree = {
                onAgreedChange(true)
                showConsentDialog = false
                performGoogle()
            },
        )
    }
}

@Composable
private fun EmailLoginPage(
    agreed: Boolean,
    onAgreedChange: (Boolean) -> Unit,
    authStore: LocalAuthStore,
    onBack: () -> Unit,
    onSuccess: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSendingCode by remember { mutableStateOf(false) }
    var isLoggingIn by remember { mutableStateOf(false) }
    var cooldownSec by remember { mutableIntStateOf(0) }
    var showConsentDialog by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<PendingEmailAction?>(null) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(cooldownSec) {
        if (cooldownSec <= 0) return@LaunchedEffect
        delay(1_000)
        cooldownSec -= 1
    }

    fun perform(action: PendingEmailAction) {
        when (action) {
            PendingEmailAction.SendEmailCode -> {
                val trimmed = email.trim()
                if (!isValidEmail(trimmed)) {
                    errorMessage = "请输入正确邮箱"
                    return
                }
                scope.launch {
                    isSendingCode = true
                    try {
                        val resp = AuthApi.sendEmailCode(trimmed)
                        cooldownSec = resp.cooldownSec.coerceAtLeast(0)
                        errorMessage = "验证码已发送"
                    } catch (e: Exception) {
                        errorMessage = AuthApi.describe(e)
                    } finally {
                        isSendingCode = false
                    }
                }
            }
            PendingEmailAction.EmailLogin -> {
                val trimmedEmail = email.trim()
                val trimmedCode = code.trim()
                if (!isValidEmail(trimmedEmail) || trimmedCode.isEmpty()) {
                    errorMessage = "请输入邮箱和验证码"
                    return
                }
                scope.launch {
                    isLoggingIn = true
                    try {
                        val resp = AuthApi.loginByEmail(trimmedEmail, trimmedCode)
                        authStore.save(
                            LocalUserSession(
                                isLoggedIn = true,
                                displayName = resp.user.nickname,
                                phone = resp.user.phone,
                                email = resp.user.email ?: trimmedEmail,
                                avatarSymbol = "person.crop.circle.fill",
                                accessToken = resp.accessToken,
                            ),
                        )
                        onSuccess()
                    } catch (e: Exception) {
                        errorMessage = AuthApi.describe(e)
                    } finally {
                        isLoggingIn = false
                    }
                }
            }
        }
    }

    fun requireConsent(action: PendingEmailAction) {
        if (agreed) {
            perform(action)
            return
        }
        pendingAction = action
        showConsentDialog = true
    }

    val canSendCode = !isSendingCode && cooldownSec <= 0 && isValidEmail(email.trim())

    Column(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        PaperBackHeader(
            title = "",
            onBack = onBack,
        )
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))

            Text(
                "邮箱登录",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppTheme.accent,
                style = AppTheme.compactText,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "收到验证码后填入即可登录",
                fontSize = 13.sp,
                color = AppTheme.secondaryText,
                style = AppTheme.compactText,
            )

            Spacer(Modifier.height(32.dp))

            PaperTextField(
                value = email,
                onValueChange = { email = it.trim().lowercase() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                placeholder = "邮箱",
                shape = LoginShape,
                horizontalPadding = 14.dp,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                ),
                leading = {
                    FieldLeadingIcon(Icons.Default.Email)
                },
            )

            Spacer(Modifier.height(12.dp))

            PaperTextField(
                value = code,
                onValueChange = { code = it.filter(Char::isDigit).take(6) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                placeholder = "验证码",
                shape = LoginShape,
                horizontalPadding = 14.dp,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { keyboard?.hide() },
                ),
                leading = {
                    FieldLeadingIcon(Icons.Default.Lock)
                },
                trailing = {
                    Box(
                        Modifier
                            .width(1.dp)
                            .height(22.dp)
                            .background(AppTheme.fieldStroke),
                    )
                    Text(
                        if (cooldownSec > 0) "${cooldownSec}s" else "发送验证码",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (canSendCode) AppTheme.accent else AppTheme.secondaryText,
                        style = AppTheme.compactText,
                        modifier = Modifier
                            .clickable(enabled = canSendCode) {
                                requireConsent(PendingEmailAction.SendEmailCode)
                            }
                            .padding(start = 12.dp),
                    )
                },
            )

            Spacer(Modifier.height(20.dp))

            LoginFilledButton(
                onClick = { requireConsent(PendingEmailAction.EmailLogin) },
                label = "登 录",
                enabled = !isLoggingIn && isValidEmail(email.trim()) && code.isNotBlank(),
            )

            Spacer(Modifier.height(16.dp))

            LoginStatusLine(
                isBusy = isLoggingIn,
                message = errorMessage,
                isError = errorMessage != "验证码已发送",
            )

            Spacer(Modifier.height(16.dp))

            ConsentRow(agreed = agreed, onAgreedChange = onAgreedChange)
        }
    }

    if (showConsentDialog) {
        ConsentDialog(
            onDismiss = {
                showConsentDialog = false
                pendingAction = null
            },
            onAgree = {
                onAgreedChange(true)
                val action = pendingAction
                pendingAction = null
                showConsentDialog = false
                if (action != null) perform(action)
            },
        )
    }
}

@Composable
private fun LoginBrandMark() {
    // 纯装饰用的六爻图形，自上而下阳阴阳阳阴阳。
    val strokes = listOf(true, false, true, true, false, true)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(92.dp)
                .background(Color.White.copy(alpha = 0.62f), CircleShape)
                .border(1.dp, AppTheme.accent.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                strokes.forEach { isYang ->
                    if (isYang) {
                        BrandBar(36.dp)
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            BrandBar(14.dp)
                            BrandBar(14.dp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "易玩家",
            fontSize = 27.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppTheme.accent,
            letterSpacing = 8.sp,
            style = AppTheme.compactText,
            modifier = Modifier.padding(start = 8.dp),
        )
        Spacer(Modifier.height(7.dp))
        Text(
            "起卦观辞 · 玩占明理",
            fontSize = 13.sp,
            color = AppTheme.secondaryText,
            letterSpacing = 1.5.sp,
            style = AppTheme.compactText,
            modifier = Modifier.padding(start = 1.5.dp),
        )
    }
}

@Composable
private fun BrandBar(width: Dp) {
    Box(
        Modifier
            .size(width = width, height = 3.5.dp)
            .background(AppTheme.accent.copy(alpha = 0.82f), RoundedCornerShape(2.dp)),
    )
}

@Composable
private fun FieldLeadingIcon(icon: ImageVector) {
    Icon(
        icon,
        contentDescription = null,
        tint = AppTheme.accent.copy(alpha = 0.55f),
        modifier = Modifier
            .padding(end = 10.dp)
            .size(17.dp),
    )
}

@Composable
private fun LoginFilledButton(
    onClick: () -> Unit,
    label: String,
    enabled: Boolean = true,
    fill: Color = AppTheme.accent,
    leading: @Composable (() -> Unit)? = null,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(fill.copy(alpha = if (enabled) 1f else 0.3f), LoginShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            leading?.invoke()
            Text(
                label,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                style = AppTheme.compactText,
            )
        }
    }
}

@Composable
private fun LoginGhostButton(
    onClick: () -> Unit,
    label: String,
    leading: @Composable (() -> Unit)? = null,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(Color.White.copy(alpha = 0.55f), LoginShape)
            .border(1.dp, AppTheme.accent.copy(alpha = 0.22f), LoginShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            leading?.invoke()
            Text(
                label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = AppTheme.accent,
                style = AppTheme.compactText,
            )
        }
    }
}

@Composable
private fun LoginSectionDivider(title: String) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DividerLine(Modifier.weight(1f))
        Text(
            title,
            fontSize = 12.sp,
            color = AppTheme.secondaryText,
            style = AppTheme.compactText,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        DividerLine(Modifier.weight(1f))
    }
}

@Composable
private fun DividerLine(modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(1.dp)
            .background(AppTheme.accent.copy(alpha = 0.14f)),
    )
}

/** 高度固定，避免出错或转圈时按钮上下跳动。 */
@Composable
private fun LoginStatusLine(
    isBusy: Boolean,
    message: String?,
    isError: Boolean,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isBusy -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = AppTheme.accent,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "登录中…",
                    fontSize = 12.sp,
                    color = AppTheme.secondaryText,
                    style = AppTheme.compactText,
                )
            }
            message != null -> Text(
                message,
                fontSize = 12.sp,
                color = if (isError) AppTheme.yangRed else AppTheme.secondaryText,
                textAlign = TextAlign.Center,
                style = AppTheme.compactText,
            )
        }
    }
}

@Composable
private fun ConsentRow(
    agreed: Boolean,
    onAgreedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(18.dp)
                .background(
                    if (agreed) AppTheme.accent else Color.Transparent,
                    CircleShape,
                )
                .border(
                    1.dp,
                    if (agreed) AppTheme.accent else AppTheme.secondaryText,
                    CircleShape,
                )
                .clickable { onAgreedChange(!agreed) },
            contentAlignment = Alignment.Center,
        ) {
            if (agreed) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
        Spacer(Modifier.width(7.dp))
        Text(
            "已阅读并同意《用户协议》《隐私政策》",
            fontSize = 12.sp,
            color = AppTheme.secondaryText,
            style = AppTheme.compactText,
        )
    }
}

@Composable
private fun ConsentDialog(
    onDismiss: () -> Unit,
    onAgree: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("请先同意协议") },
        text = {
            Text("登录前需同意《用户协议》和《隐私政策》。点击「同意并继续」即表示你已阅读并同意。")
        },
        confirmButton = {
            TextButton(onClick = onAgree) {
                Text("同意并继续", color = AppTheme.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = AppTheme.secondaryText)
            }
        },
        containerColor = AppTheme.parchmentTop,
    )
}

private fun isValidEmail(raw: String): Boolean {
    val trimmed = raw.trim()
    return trimmed.contains("@") && trimmed.contains(".") && trimmed.length >= 5
}
