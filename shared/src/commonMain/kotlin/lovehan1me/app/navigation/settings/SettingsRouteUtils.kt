package lovehan1me.app.navigation.settings

import lovehan1me.cache_usage_summary
import lovehan1me.core.constant.HanimeConstants.AV_URL
import lovehan1me.core.constant.HanimeConstants.HANIME_HOSTNAME
import lovehan1me.core.constant.HanimeConstants.HANIME_URL
import lovehan1me.Res
import lovehan1me.core.util.formatBytesPerSecond
import lovehan1me.core.util.formatFileSize
import org.jetbrains.compose.resources.getString

// P6d-4：自 :app SettingsRouteUtils.kt 拆分下沉——本文件只保留平台无关函数；
// Android 系统功能（Keyguard/AppOps/Intent 跳转）留在 :app SettingsPlatformUtils.kt。
// P6d-3-C2：context.getString→边界预解析。调用方（remember{} 内 plain builder）
// 无法调 @Composable/suspend，故字符串在 composable 层 stringResource 解析后以 String 传入。

fun buildDomainOptions(defaultLabel: String, alternativeLabel: String): List<Pair<String, String>> = listOf(
    "${HANIME_HOSTNAME[0]} ($defaultLabel)" to HANIME_URL[0],
    "${HANIME_HOSTNAME[1]} ($alternativeLabel)" to HANIME_URL[1],
    "${HANIME_HOSTNAME[2]} ($alternativeLabel)" to HANIME_URL[2],
    "${HANIME_HOSTNAME[3]} (av)" to AV_URL,
)

suspend fun generateClearCacheSummary(size: Long): String {
    // 原实现为 androidx parseAsHtml 后被调用点 .toString()（span 从未实际生效），
    // 最终行为即"去 HTML 标签取纯文本"；CMP 1.12 ui-text 无跨平台 fromHtml，故直接等价去标签。
    // 模板 cache_usage_summary 仅含 <b> 对
    val raw = getString(Res.string.cache_usage_summary, size.formatFileSize())
    return Regex("<[^>]+>").replace(raw, "")
}

fun Long.toDownloadSpeedPrettyString(noLimitText: String): String {
    return if (this == 0L) {
        noLimitText
    } else {
        formatBytesPerSecond()
    }
}

fun toDownloadCountLimitPrettyString(noLimitText: String, value: Int): String {
    return if (value == 0) noLimitText else value.toString()
}
