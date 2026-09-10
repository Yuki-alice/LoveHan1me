package lovehan1me.ui.screen.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import lovehan1me.Res
import lovehan1me.account_or_password_wrong
import lovehan1me.email
import lovehan1me.ic_arrow_back
import lovehan1me.ic_visibility
import lovehan1me.login
import lovehan1me.login_failed
import lovehan1me.login_success
import lovehan1me.logic.NetworkRepo
import lovehan1me.logic.state.WebsiteState
import lovehan1me.password
import lovehan1me.scan_for_cookies
import lovehan1me.try_login_here
import lovehan1me.core.util.SonnerToast
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * M5：三端共享的表单登录页（对标网站 /login 的原生体验）。
 *
 * Android 端首选 WebView（拦截 Cookie，与浏览器行为一致），本页是
 * 桌面/iOS 的默认登录路径，也是 Android WebView 加载失败时的兜底语义：
 * 直接走 `NetworkRepo.login` 的 HTTP 表单登录（GET 登录页取 CSRF token →
 * POST 提交 → 校验登录态 → 取 Set-Cookie），无 WebView 依赖。
 *
 * 行为与 `:app` `LoginDialog` 保持一致：账号或密码错误以
 * [IllegalStateException] 区分提示；成功后 [login] 写入会话 Cookie。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormLoginScreen(
    onBack: () -> Unit,
    onOpenManualCookies: () -> Unit,
    onLoginSucceeded: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoggingIn by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (isLoggingIn || email.isBlank() || password.isBlank()) return
        isLoggingIn = true
        scope.launch {
            NetworkRepo.login(email.trim(), password).collect { state ->
                when (state) {
                    is WebsiteState.Error -> {
                        isLoggingIn = false
                        if (state.throwable is IllegalStateException) {
                            SonnerToast.error(getString(Res.string.account_or_password_wrong))
                        } else {
                            SonnerToast.error(getString(Res.string.login_failed))
                        }
                    }

                    is WebsiteState.Success -> {
                        login(state.info)
                        isLoggingIn = false
                        SonnerToast.success(getString(Res.string.login_success))
                        onLoginSucceeded()
                    }

                    WebsiteState.Loading -> Unit
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.login)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_arrow_back),
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(Res.string.try_login_here),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(Res.string.email)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    enabled = !isLoggingIn,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(Res.string.password)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        // 项目 drawable 无 visibility_off 变体，用 tint 透明度区分明文/密文态
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_visibility),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = if (passwordVisible) 1f else 0.4f,
                                ),
                            )
                        }
                    },
                    enabled = !isLoggingIn,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = ::submit,
                    enabled = !isLoggingIn && email.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isLoggingIn) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    Text(stringResource(Res.string.login))
                }
                androidx.compose.material3.TextButton(
                    onClick = onOpenManualCookies,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(Res.string.scan_for_cookies))
                }
            }
        }
    }
}
