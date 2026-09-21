package lovehan1me.app.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lovehan1me.Res
import lovehan1me.cf_cdp_failed_title
import lovehan1me.cf_cdp_locating
import lovehan1me.cf_cdp_waiting
import lovehan1me.cf_manual_cookie_hint
import lovehan1me.cf_manual_open_browser
import lovehan1me.cf_manual_retry
import lovehan1me.cf_manual_submit
import lovehan1me.cf_manual_ua_hint
import lovehan1me.cf_use_manual
import lovehan1me.complete_cloudflare_verification
import lovehan1me.data.network.CloudflareChallenges
import org.jetbrains.compose.resources.stringResource
import java.awt.Desktop
import java.net.URI

/**
 * 阶段一⑩：桌面 CF 人机验证——独立弹窗 + CDP 驱动可见浏览器自动验证。
 *
 * 形态（用户要的"独立窗口弹窗"）：Compose Desktop 在 App 内再开 `Window`，
 * 与主窗口互不干扰；验证结束关窗，调用方回退路由重试原请求。
 *
 * 自动验证走 [CloudflareCdp]：探活本机 Chrome/Edge → **持久 profile + 可见窗口**
 * 启动 → CDP 建页导航 → 轮询 `Network.getAllCookies` 收割 **`cf_clearance`** →
 * 写回 CookieJar + DataStore。**零下载**（KCEF 的 200MB 运行时已删除）。
 *
 * ⚠️ 形态是"可见窗口"而非无头：实测无头 47 秒也拿不到 clearance（见
 * [CloudflareCdp] 类 KDoc）。因此本窗只是**说明与兜底**：真正要让用户操作的
 * 是那个浏览器窗口；本机无浏览器 / 启动失败 / 2 分钟未通过 → 切手动兜底面板。
 *
 * 手动兜底（与旧版同）：① 用系统浏览器打开验证页；② 粘贴 `cf_clearance`
 * 写回 DataStore（`HCookieJar.loadForRequest` 按域取用 clearance，
 * 后续请求自动携带）。
 */
@Composable
fun CloudflareVerificationWindow(
    url: String,
    host: String,
    onPassed: () -> Unit,
) {
    var open by remember { mutableStateOf(true) }
    var phase by remember { mutableStateOf<CdpWindowPhase>(CdpWindowPhase.Locating) }
    var waitText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val locatingText = stringResource(Res.string.cf_cdp_locating)
    val waitingText = stringResource(Res.string.cf_cdp_waiting)

    fun runSolve() {
        scope.launch {
            phase = CdpWindowPhase.Locating
            // 探活是同步文件检查，仍放后台线程做，避免卡 UI
            val browser = withContext(Dispatchers.IO) {
                CloudflareCdp.findBrowser()
            }
            if (browser == null) {
                phase = CdpWindowPhase.Manual(NO_BROWSER)
                return@launch
            }
            phase = CdpWindowPhase.Verifying
            waitText = null
            when (val result = CloudflareCdp.solve(url, onStage = { stage ->
                waitText = when (stage) {
                    "starting" -> locatingText
                    else -> waitingText
                }
            })) {
                is CloudflareCdp.SolveResult.Solved -> {
                    // 写回成功才回调完成；写不进去还报成功，用户只会再撞一次 403
                    // UA 与 clearance 必须一起写回（cf_clearance 绑定 UA）
                    if (CloudflareCdp.persistSolvedCookies(host, result.clearance, result.browserUserAgent)) {
                        open = false
                        onPassed()
                    } else {
                        phase = CdpWindowPhase.Manual(IMPORT_FAILED)
                    }
                }

                is CloudflareCdp.SolveResult.NoBrowser ->
                    phase = CdpWindowPhase.Manual(NO_BROWSER)

                is CloudflareCdp.SolveResult.Timeout ->
                    phase = CdpWindowPhase.Manual("2 分钟内未通过验证（窗口里可能还需要人工点一下）")

                is CloudflareCdp.SolveResult.Failed ->
                    phase = CdpWindowPhase.Manual(result.reason)
            }
        }
    }

    LaunchedEffect(url) {
        runSolve()
    }

    if (open) {
        androidx.compose.ui.window.Window(
            // 用户直接关窗 = 这个域不验了：叫醒挂在 ioRequest 上的请求，让它照常报错，
            // 而不是把界面定格在转圈上直到超时。
            onCloseRequest = {
                open = false
                CloudflareChallenges.abandoned(host)
            },
            title = "Cloudflare 人机验证",
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                when (val current = phase) {
                    is CdpWindowPhase.Locating -> {
                        StatusPane(
                            title = stringResource(Res.string.complete_cloudflare_verification),
                            body = locatingText,
                            onUseManual = { phase = CdpWindowPhase.Manual("已切换为手动方式") },
                        )
                    }

                    is CdpWindowPhase.Verifying -> {
                        StatusPane(
                            title = stringResource(Res.string.complete_cloudflare_verification),
                            body = waitText ?: waitingText,
                            onUseManual = { phase = CdpWindowPhase.Manual("已切换为手动方式") },
                        )
                    }

                    is CdpWindowPhase.Manual -> {
                        ManualFallbackPane(
                            url = url,
                            host = host,
                            reason = current.reason,
                            onRetry = { runSolve() },
                            onPassed = {
                                open = false
                                onPassed()
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 无浏览器可用（探活与求解两条路径共用同一句文案）。 */
private const val NO_BROWSER = "本机未找到 Chrome / Edge 浏览器"

/** 拿到 clearance 但写回失败——不能当成成功，否则用户会再撞一次 403。 */
private const val IMPORT_FAILED = "验证已通过，但凭据写回失败，请重试或改用手动方式"

private sealed interface CdpWindowPhase {
    data object Locating : CdpWindowPhase
    data object Verifying : CdpWindowPhase
    data class Manual(val reason: String) : CdpWindowPhase
}

/** 自动验证进行中：给出状态，并允许随时改走手动兜底。 */
@Composable
private fun StatusPane(
    title: String,
    body: String,
    onUseManual: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(text = body, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onUseManual) {
            Text(stringResource(Res.string.cf_use_manual))
        }
    }
}

/**
 * 手动兜底：自动验证不可用时的自救路径。
 *
 * 写回通道只需 DataStore —— `HCookieJar.loadForRequest` 按域取 clearance
 * （精确域 → 父域回落）叠加进请求，后续请求自动携带。
 *
 * UA 与 clearance 一起要：`cf_clearance` 绑定签发它的浏览器 UA，只写 cookie
 * 会得到一把"钥匙对不上锁"的死凭据 —— 旧版正是这条路径让人以为手动兜底没用。
 */
@Composable
private fun ManualFallbackPane(
    url: String,
    host: String,
    reason: String,
    onRetry: () -> Unit,
    onPassed: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    // 预填上一次采到的真实 UA：同一个浏览器重复粘贴时不用再抄一遍，换浏览器时改这里。
    var userAgent by remember { mutableStateOf(lovehan1me.data.SettingsRepository.desktopBrowserUserAgent) }
    var browserOpened by remember { mutableStateOf(false) }
    var browserError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(Res.string.cf_cdp_failed_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(text = reason, style = MaterialTheme.typography.bodySmall)

        Button(
            onClick = {
                val ok = runCatching {
                    if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                        false
                    } else {
                        Desktop.getDesktop().browse(URI(url)); true
                    }
                }.getOrDefault(false)
                browserOpened = ok
                browserError = if (ok) null else "无法调起系统浏览器，请手动复制上方网址打开"
            },
        ) {
            Text(stringResource(Res.string.cf_manual_open_browser))
        }

        if (browserOpened) {
            Text(url, style = MaterialTheme.typography.bodySmall)
        }
        browserError?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

        Text(
            text = stringResource(Res.string.cf_manual_cookie_hint),
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            singleLine = false,
            maxLines = 8,
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp, max = 200.dp),
        )

        Text(
            text = stringResource(Res.string.cf_manual_ua_hint),
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = userAgent,
            onValueChange = { userAgent = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(2.dp))

        Button(
            // UA 与 cookie 缺一不可：只贴 cookie 就是把自己浏览器的钥匙配给一个
            // 对不上锁孔的 UA，提交后照样 403（这正是"手动导入没用"的根因）。
            enabled = input.contains('=') && userAgent.isNotBlank(),
            onClick = {
                val cookie = input.trim()
                val ua = userAgent.trim()
                scope.launch {
                    // 与 CDP 路径同序：UA 先落盘，再写 clearance（写它会叫醒等验证的请求）。
                    val settings = lovehan1me.data.SettingsRepository
                    settings.setDesktopBrowserUserAgent(ua)
                    settings.setCloudFlareCookie(host, cookie)
                    onPassed()
                }
            },
        ) {
            Text(stringResource(Res.string.cf_manual_submit))
        }

        TextButton(onClick = onRetry) {
            Text(stringResource(Res.string.cf_manual_retry))
        }
    }
}
