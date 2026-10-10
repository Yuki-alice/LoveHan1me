package lovehan1me.data.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import lovehan1me.core.platform.openBackupSink
import lovehan1me.core.platform.openBackupSource
import lovehan1me.core.platform.rebuildSystemProxy
import lovehan1me.data.SettingsRepository
import okio.buffer

/**
 * 解析 hosts 文本（B4-1 / F13）。
 *
 * 目标是让极客**整段粘贴**一份 hosts 文件（或从剪贴板/文件导入），而不是在设置页逐条手输 IP。
 * 因此这里对齐 `/etc/hosts` 的宽松写法，而不是自己发明一种格式：
 *
 * - **注释**：`#` 到行尾整段丢弃（支持整行注释与行内注释）；
 * - **空行**：跳过（含只有空白字符的行）；
 * - **一行多字段**：按空白与逗号切分，**只保留合法 IP 字面量**，主机名等非 IP 字段忽略
 *   —— 消费方（`HanimeDns` 的 `customHostsData`）只吃 IP 列表，主机名在这里没有意义；
 * - **IPv6**：`::1` / `2001:db8::1` 这类裸写法与 `[::1]` 方括号写法都接受，
 *   带 zone id（`fe80::1%eth0`）时丢弃 `%` 之后的部分；
 * - **兼容旧逗号格式**：`1.2.3.4,5.6.7.8` 与 hosts 行混写也能解析。
 *
 * 去重保序（按首次出现顺序），使导入结果稳定可复现。
 *
 * 判定本体在 commonMain（不依赖 `java.net`），三端共用同一份解析 —— 与项目"判定在
 * commonMain、平台只提供原语"的架构线一致。
 */
fun parseHostsText(text: String): List<String> {
    val seen = LinkedHashSet<String>()
    for (rawLine in text.lineSequence()) {
        val line = rawLine.substringBefore('#').trim()
        if (line.isEmpty()) continue
        for (rawToken in line.split(' ', '\t', ',')) {
            val token = rawToken.trim()
                .removeSurrounding("[", "]")
                .substringBefore('%')
            if (token.isNotEmpty() && isIpLiteral(token)) seen.add(token)
        }
    }
    return seen.toList()
}

/** 是否是合法的 IPv4 / IPv6 字面量（纯字符串判定，不触发系统解析）。 */
private fun isIpLiteral(token: String): Boolean = isIpv4(token) || isIpv6(token)

private fun isIpv4(token: String): Boolean {
    val parts = token.split('.')
    if (parts.size != 4) return false
    return parts.all { part ->
        part.isNotEmpty() && part.length <= 3 && part.all { it in '0'..'9' } &&
            part.toIntOrNull()?.let { it in 0..255 } == true
    }
}

/**
 * IPv6 判定。放宽到"能识别常见写法"即可：`192.168.0.1` 这类 IPv4 字面量不含冒号，
 * 会在这里直接落空；`::` 至多出现一次；分组非空且是 1–4 位十六进制
 * （末组允许内嵌 IPv4，如 `::ffff:192.168.0.1`）。
 */
private fun isIpv6(token: String): Boolean {
    if (!token.contains(':')) return false
    val doubleColonCount = token.windowed(2).count { it == "::" }
    if (doubleColonCount > 1) return false
    val hasDouble = doubleColonCount == 1
    val head: String
    val tail: String
    if (hasDouble) {
        val idx = token.indexOf("::")
        head = token.substring(0, idx)
        tail = token.substring(idx + 2)
    } else {
        head = token
        tail = ""
    }
    val headGroups = if (head.isEmpty()) emptyList() else head.split(':')
    val tailGroups = if (tail.isEmpty()) emptyList() else tail.split(':')
    if (!validGroups(headGroups, allowIpv4Last = false)) return false
    if (!validGroups(tailGroups, allowIpv4Last = true)) return false
    val total = headGroups.size + tailGroups.size
    return if (hasDouble) total <= 7 else total == 8
}

private fun validGroups(groups: List<String>, allowIpv4Last: Boolean): Boolean =
    groups.withIndex().all { (index, group) ->
        when {
            allowIpv4Last && index == groups.lastIndex && group.contains('.') -> isIpv4(group)
            group.isEmpty() -> false
            group.length > 4 -> false
            else -> group.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
        }
    }

/**
 * 网络配置的导入 / 导出（B4-1 / F13）。
 *
 * **复用 `BackupManager` 的文件通道**：与全量备份共用 `openBackupSink` / `openBackupSource`
 * 这两个平台原语（Android=SAF、桌面=File、iOS=分享/选择器），因此三端拿到的是同一套
 * "选文件 → 读写"行为，无需各端各写一份。
 *
 * 导出的是**网络子集**（站点 / DNS / 网关 / 代理 / 镜像），不是整份应用备份 ——
 * 极客要的是"这台机器的网络配置能在另一台复现"，而不是把观看记录也搬过去。
 *
 * [importFrom] 两种输入都收：
 * - **配置 JSON**（本管理器导出的格式）→ 整份应用；
 * - **hosts 文本** → 只解析出 IP 列表写入 `customHostsData`（[parseHostsText]）。
 *
 * 判定方式是按首字符：`{` 开头走 JSON，否则按 hosts 文本。手写 JSON 或手写 hosts
 * 都不会因为"猜错格式"而毁数据——格式不对就返回 [ImportOutcome.Invalid]，不改任何设置。
 */
object NetworkConfigManager {
    /** 导出文件的建议名（桌面/Android 文件选择器的默认文件名）。 */
    const val SUGGESTED_FILE_NAME = "lovehan1me-network-config.json"

    private const val VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    /** 可复现的网络配置快照。字段与 `AppSettings` 的网络子集一一对应。 */
    @Serializable
    data class NetworkConfig(
        val version: Int = VERSION,
        val domainName: String = "",
        val selectedBaseUrl: String = "",
        val useCustomMirrorSite: Boolean = false,
        val customMirrorSite: String = "",
        val appendCustomMirrorPath: Boolean = true,
        val useBuiltInHosts: Boolean = false,
        val autoBuiltInHosts: Boolean = true,
        val customHostsData: String = "",
        val useEchGate: Boolean = true,
        val egressForceMode: String = "Auto",
        val useDoH: Boolean = false,
        val dohPreset: String = "alidns",
        val dohCustomUrl: String = "",
        val dohBootstrapIps: String = "",
        val dohTimeoutSeconds: Int = 10,
        val proxyType: Int = 1,
        val proxyIp: String = "",
        val proxyPort: Int = -1,
    )

    /** 导入结果，供 UI 给出对应提示。 */
    sealed interface ImportOutcome {
        /** 识别为配置 JSON 并整份应用。 */
        object ConfigApplied : ImportOutcome

        /** 识别为 hosts 文本并写入；[count] 为解析出的 IP 条数。 */
        data class HostsImported(val count: Int) : ImportOutcome

        /** 既不是本管理器的配置 JSON，也没有解析出任何 IP。 */
        object Invalid : ImportOutcome
    }

    /** 从当前设置取一份网络配置快照。 */
    fun snapshot(): NetworkConfig {
        val s = SettingsRepository.current
        return NetworkConfig(
            domainName = s.domainName,
            selectedBaseUrl = s.selectedBaseUrl,
            useCustomMirrorSite = s.useCustomMirrorSite,
            customMirrorSite = s.customMirrorSite,
            appendCustomMirrorPath = s.appendCustomMirrorPath,
            useBuiltInHosts = s.useBuiltInHosts,
            autoBuiltInHosts = s.autoBuiltInHosts,
            customHostsData = s.customHostsData,
            useEchGate = s.useEchGate,
            egressForceMode = s.egressForceMode,
            useDoH = s.useDoH,
            dohPreset = s.dohPreset,
            dohCustomUrl = s.dohCustomUrl,
            dohBootstrapIps = s.dohBootstrapIps,
            dohTimeoutSeconds = s.dohTimeoutSeconds,
            proxyType = s.proxyType.id,
            proxyIp = s.proxyIp,
            proxyPort = s.proxyPort,
        )
    }

    /** 序列化为导出文本（默认取当前设置）。 */
    fun buildConfigText(config: NetworkConfig = snapshot()): String =
        json.encodeToString(NetworkConfig.serializer(), config)

    /**
     * 解析导出文本；不是**本管理器的**配置 JSON 时返回 null。
     *
     * 判据是必须含 `version` 键：`ignoreUnknownKeys` 下任意一个 JSON 对象
     * （如别的应用的导出文件）都能被解码成"全默认值"的配置，若照单全收地应用，
     * 会把用户现有设置整体清空。要求 `version` 存在即把这种情况挡在门外。
     */
    fun parseConfigText(text: String): NetworkConfig? {
        val obj = runCatching { json.parseToJsonElement(text.trim()) as? JsonObject }.getOrNull()
            ?: return null
        if (!obj.containsKey("version")) return null
        return runCatching { json.decodeFromJsonElement(NetworkConfig.serializer(), obj) }.getOrNull()
    }

    /** 导出当前网络配置到 [uri]（走全量备份同一套文件通道）。 */
    suspend fun exportTo(uri: String) {
        val sink = openBackupSink(uri)?.buffer() ?: error("Unable to open export file")
        try {
            sink.writeUtf8(buildConfigText())
        } finally {
            sink.close()
        }
    }

    /** 从 [uri] 读取并导入；格式判定见类注释。 */
    suspend fun importFrom(uri: String): ImportOutcome {
        val source = openBackupSource(uri)?.buffer() ?: error("Unable to open import file")
        val text = try {
            source.readUtf8()
        } finally {
            source.close()
        }
        return importText(text)
    }

    /** [importFrom] 的无文件版本（测试与"粘贴文本"共用）。 */
    suspend fun importText(text: String): ImportOutcome {
        if (text.trimStart().startsWith("{")) {
            val config = parseConfigText(text) ?: return ImportOutcome.Invalid
            applyConfig(config)
            return ImportOutcome.ConfigApplied
        }
        val ips = parseHostsText(text)
        if (ips.isEmpty()) return ImportOutcome.Invalid
        SettingsRepository.update { it.copy(customHostsData = ips.joinToString(",")) }
        return ImportOutcome.HostsImported(ips.size)
    }

    /**
     * 应用配置 JSON。
     *
     * `domainName` / `selectedBaseUrl` 为空时保留当前值 —— 一个手改坏了这两个字段的配置
     * 不该把站点导航指向空串（那是"导入即砖"）。其余字段按导出值原样落地，忠实复现。
     */
    private suspend fun applyConfig(config: NetworkConfig) {
        SettingsRepository.update { current ->
            current.copy(
                domainName = config.domainName.ifBlank { current.domainName },
                selectedBaseUrl = config.selectedBaseUrl.ifBlank { current.selectedBaseUrl },
                useCustomMirrorSite = config.useCustomMirrorSite,
                customMirrorSite = config.customMirrorSite,
                appendCustomMirrorPath = config.appendCustomMirrorPath,
                useBuiltInHosts = config.useBuiltInHosts,
                autoBuiltInHosts = config.autoBuiltInHosts,
                customHostsData = config.customHostsData,
                useEchGate = config.useEchGate,
                egressForceMode = config.egressForceMode,
                useDoH = config.useDoH,
                dohPreset = config.dohPreset,
                dohCustomUrl = config.dohCustomUrl,
                dohBootstrapIps = config.dohBootstrapIps,
                dohTimeoutSeconds = config.dohTimeoutSeconds.coerceIn(1, 60),
                proxyType = lovehan1me.core.domain.model.ProxyType.fromId(config.proxyType),
                proxyIp = config.proxyIp,
                proxyPort = config.proxyPort,
            )
        }
        // 出口现实变了：复位跨平台熔断健康度与平台传输层状态（同 BackupManager 恢复后的语义）。
        rebuildSystemProxy()
    }
}