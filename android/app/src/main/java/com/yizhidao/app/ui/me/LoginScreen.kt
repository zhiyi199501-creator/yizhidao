package com.yizhidao.app.ui.me

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yizhidao.app.BuildConfig
import com.yizhidao.app.auth.AuthApi
import com.yizhidao.app.auth.GoogleSignInHelper
import com.yizhidao.app.auth.LocalAuthStore
import com.yizhidao.app.auth.LocalUserSession
import com.yizhidao.app.ui.theme.AppTheme
import com.yizhidao.app.ui.theme.PaperBackHeader
import com.yizhidao.app.ui.theme.PaperOutlinedButton
import com.yizhidao.app.ui.theme.PaperPrimaryButton
import com.yizhidao.app.ui.theme.PaperTextField
import com.yizhidao.app.ui.theme.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class PendingLoginAction {
    Google,
    SendEmailCode,
    EmailLogin,
}

@Composable
fun LoginScreen(
    authStore: LocalAuthStore,
    onBack: () -> Unit,
    onSuccess: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var agreed by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSendingCode by remember { mutableStateOf(false) }
    var isLoggingIn by remember { mutableStateOf(false) }
    var cooldownSec by remember { mutableIntStateOf(0) }
    var pendingAction by remember { mutableStateOf<PendingLoginAction?>(null) }
    var showConsentDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current

    LaunchedEffect(cooldownSec) {
        if (cooldownSec <= 0) return@LaunchedEffect
        delay(1_000)
        cooldownSec -= 1
    }

    fun saveSession(resp: AuthApi.LoginResponse, emailOverride: String? = null) {
        authStore.save(
            LocalUserSession(
                isLoggedIn = true,
                displayName = resp.user.nickname,
                phone = resp.user.phone,
                email = resp.user.email ?: emailOverride,
                avatarSymbol = "person.crop.circle.fill",
                accessToken = resp.accessToken,
            ),
        )
        onSuccess()
    }

    fun perform(action: PendingLoginAction) {
        when (action) {
            PendingLoginAction.Google -> {
                scope.launch {
                    isLoggingIn = true
                    try {
                        val idToken = GoogleSignInHelper.signIn(context)
                        val resp = AuthApi.loginWithGoogle(idToken)
                        saveSession(resp)
                    } catch (e: Exception) {
                        errorMessage = AuthApi.describe(e)
                    } finally {
                        isLoggingIn = false
                    }
                }
            }
            PendingLoginAction.SendEmailCode -> {
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
            PendingLoginAction.EmailLogin -> {
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
                        saveSession(resp, trimmedEmail)
                    } catch (e: Exception) {
                        errorMessage = AuthApi.describe(e)
                    } finally {
                        isLoggingIn = false
                    }
                }
            }
        }
    }

    fun requireConsent(action: PendingLoginAction) {
        if (agreed) {
            perform(action)
            return
        }
        pendingAction = action
        showConsentDialog = true
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        PaperBackHeader(
            title = "登录",
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PaperPrimaryButton(
                onClick = { requireConsent(PendingLoginAction.Google) },
                enabled = !isLoggingIn && GoogleSignInHelper.isConfigured,
                label = if (isLoggingIn) "登录中…" else "Google 登录",
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

            if (!GoogleSignInHelper.isConfigured) {
                Text(
                    "Google 登录需在 build.gradle.kts 配置 GOOGLE_WEB_CLIENT_ID",
                    fontSize = 11.sp,
                    color = AppTheme.secondaryText.copy(alpha = 0.7f),
                    style = AppTheme.compactText,
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HorizontalDivider(Modifier.weight(1f), color = Color.Black.copy(alpha = 0.1f))
                Text(
                    "或使用邮箱",
                    fontSize = 12.sp,
                    color = AppTheme.secondaryText,
                    modifier = Modifier.padding(horizontal = 10.dp),
                    style = AppTheme.compactText,
                )
                HorizontalDivider(Modifier.weight(1f), color = Color.Black.copy(alpha = 0.1f))
            }

            PaperTextField(
                value = email,
                onValueChange = { email = it.trim().lowercase() },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "邮箱",
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) },
                ),
            )

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PaperTextField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(6) },
                    modifier = Modifier.weight(1f),
                    placeholder = "验证码",
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { keyboard?.hide() },
                    ),
                )
                PaperOutlinedButton(
                    onClick = { requireConsent(PendingLoginAction.SendEmailCode) },
                    enabled = !isSendingCode && cooldownSec <= 0 && isValidEmail(email.trim()),
                    label = if (cooldownSec > 0) "${cooldownSec}s" else "发送验证码",
                )
            }

            PaperPrimaryButton(
                onClick = { requireConsent(PendingLoginAction.EmailLogin) },
                enabled = !isLoggingIn && isValidEmail(email.trim()) && code.isNotBlank(),
                label = if (isLoggingIn) "登录中…" else "邮箱登录",
            )

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Switch(
                    checked = agreed,
                    onCheckedChange = { agreed = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AppTheme.accent,
                    ),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "已阅读并同意《用户协议》《隐私政策》",
                    fontSize = 12.sp,
                    color = AppTheme.secondaryText,
                    lineHeight = 17.sp,
                    style = AppTheme.compactText,
                )
            }

            errorMessage?.let {
                Text(
                    it,
                    fontSize = 12.sp,
                    color = if (it == "验证码已发送") AppTheme.secondaryText else AppTheme.yangRed,
                    lineHeight = 17.sp,
                    style = AppTheme.compactText,
                )
            }

            if (BuildConfig.DEBUG) {
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
        AlertDialog(
            onDismissRequest = {
                showConsentDialog = false
                pendingAction = null
            },
            title = { Text("请先同意协议") },
            text = {
                Text("登录前需同意《用户协议》和《隐私政策》。点击「同意并继续」即表示你已阅读并同意。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        agreed = true
                        val action = pendingAction
                        pendingAction = null
                        showConsentDialog = false
                        if (action != null) perform(action)
                    },
                ) {
                    Text("同意并继续", color = AppTheme.accent)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showConsentDialog = false
                        pendingAction = null
                    },
                ) {
                    Text("取消", color = AppTheme.secondaryText)
                }
            },
            containerColor = AppTheme.parchmentTop,
        )
    }
}

private fun isValidEmail(raw: String): Boolean {
    val trimmed = raw.trim()
    return trimmed.contains("@") && trimmed.contains(".") && trimmed.length >= 5
}
