package com.yizhidao.app.ui.me

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yizhidao.app.BuildConfig
import com.yizhidao.app.auth.AuthApi
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

@Composable
fun LoginScreen(
    authStore: LocalAuthStore,
    onBack: () -> Unit,
    onSuccess: () -> Unit,
) {
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var agreed by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSendingCode by remember { mutableStateOf(false) }
    var isLoggingIn by remember { mutableStateOf(false) }
    var cooldownSec by remember { mutableIntStateOf(0) }
    var showWechatTip by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(cooldownSec) {
        if (cooldownSec <= 0) return@LaunchedEffect
        delay(1_000)
        cooldownSec -= 1
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
                onClick = {
                    if (!agreed) {
                        errorMessage = "请先勾选并同意用户协议与隐私政策"
                        return@PaperPrimaryButton
                    }
                    showWechatTip = true
                },
                label = "微信登录（待接入）",
                leading = {
                    Icon(
                        Icons.AutoMirrored.Filled.Chat,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                },
            )

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HorizontalDivider(Modifier.weight(1f), color = Color.Black.copy(alpha = 0.1f))
                Text(
                    "或使用手机号",
                    fontSize = 12.sp,
                    color = AppTheme.secondaryText,
                    modifier = Modifier.padding(horizontal = 10.dp),
                    style = AppTheme.compactText,
                )
                HorizontalDivider(Modifier.weight(1f), color = Color.Black.copy(alpha = 0.1f))
            }

            PaperTextField(
                value = phone,
                onValueChange = { phone = it.filter(Char::isDigit).take(11) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "手机号",
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
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
                    onClick = {
                        if (!agreed) {
                            errorMessage = "请先勾选并同意用户协议与隐私政策"
                            return@PaperOutlinedButton
                        }
                        val trimmed = phone.trim()
                        if (trimmed.length < 6) {
                            errorMessage = "请输入正确手机号"
                            return@PaperOutlinedButton
                        }
                        scope.launch {
                            isSendingCode = true
                            try {
                                val resp = AuthApi.sendSMSCode(trimmed)
                                cooldownSec = resp.cooldownSec.coerceAtLeast(0)
                                errorMessage = "验证码已发送"
                            } catch (e: Exception) {
                                errorMessage = AuthApi.describe(e)
                            } finally {
                                isSendingCode = false
                            }
                        }
                    },
                    enabled = !isSendingCode && cooldownSec <= 0 && phone.trim().length >= 6,
                    label = if (cooldownSec > 0) "${cooldownSec}s" else "发送验证码",
                )
            }

            PaperPrimaryButton(
                onClick = {
                    if (!agreed) {
                        errorMessage = "请先勾选并同意用户协议与隐私政策"
                        return@PaperPrimaryButton
                    }
                    val trimmedPhone = phone.trim()
                    val trimmedCode = code.trim()
                    if (trimmedPhone.isEmpty() || trimmedCode.isEmpty()) {
                        errorMessage = "请输入手机号和验证码"
                        return@PaperPrimaryButton
                    }
                    scope.launch {
                        isLoggingIn = true
                        try {
                            val resp = AuthApi.loginBySMS(trimmedPhone, trimmedCode)
                            authStore.save(
                                LocalUserSession(
                                    isLoggedIn = true,
                                    displayName = resp.user.nickname,
                                    phone = resp.user.phone ?: trimmedPhone,
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
                },
                enabled = !isLoggingIn && phone.trim().length >= 6 && code.isNotBlank(),
                label = if (isLoggingIn) "登录中…" else "手机号登录",
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

    if (showWechatTip) {
        AlertDialog(
            onDismissRequest = { showWechatTip = false },
            title = { Text("暂未接入") },
            text = { Text("当前为本地演示版，后续接入真实微信登录。") },
            confirmButton = {
                TextButton(onClick = { showWechatTip = false }) {
                    Text("知道了", color = AppTheme.accent)
                }
            },
            containerColor = AppTheme.parchmentTop,
        )
    }
}
