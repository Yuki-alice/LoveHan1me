package lovehan1me.data.network

import lovehan1me.core.util.LogUtil
import lovehan1me.core.constant.HanimeConstants.HANIME_HOSTNAME
import lovehan1me.data.SettingsRepository
import okhttp3.Dns
import okhttp3.dnsoverhttps.DnsOverHttps
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * @project LoveHan1me
 * @author Yenaly Liew（上游原作者，见 NOTICE）
 * @time 2024/03/10 010 17:01
 */
class HanimeDns : Dns {

    private data class DohRuntimeConfig(
        val url: String,
        val bootstrapIps: List<String>,
        val timeoutSeconds: Int,
    )

    @Volatile
    private var cachedDohConfig: DohRuntimeConfig? = null

    @Volatile
    private var cachedDohDns: Dns? = null

    companion object {

        /**
         * 全进程共用一份。
         *
         * DoH client 与自定义 IP 的解析结果都缓存在**实例字段**上，每处各 new 一个
         * 等于每处各建一个 DoH client、各存一份解析缓存。ServiceCreator / CDN 抓图 /
         * 下载 / 网关启动各有一处，此前是 5 份互不相干的缓存。
         */
        val SHARED: HanimeDns = HanimeDns()

        /** DoH 连续失败到此数后整档摘掉 [DOH_COOLDOWN_MS]。 */
        private const val DOH_FAILURE_THRESHOLD = 3

        /** DoH 冷却时长。 */
        private const val DOH_COOLDOWN_MS = 30_000L

        /** 并行发起的系统解析最多等这么久（DoH 也失败时，别让这次解析挂住）。 */
        private const val SYSTEM_RACE_TIMEOUT_MS = 5_000L

        /**
         * 系统与 DoH 的竞速执行者（进程级：两个 client 同时解析不该各起一套线程）。
         *
         * 只用来把"系统解析"提前发起，不是为了改成"谁先回用谁"——见 [lookup] 的说明。
         */
        private val racePool = Executors.newCachedThreadPool { runnable ->
            thread(start = false, isDaemon = true, name = "dns-race") { runnable.run() }
        }

        /**
         * DoH 的负缓存（进程级）。
         *
         * 为什么需要：DoH 端点不可达时，每一档解析都要等满超时才降级系统解析，
         * 一个首屏会碰上若干个新域名，串行等待直接把首屏拖成十几秒。
         * 连续失败 [DOH_FAILURE_THRESHOLD] 次就整档摘掉 [DOH_COOLDOWN_MS]，
         * 期间请求零延迟走系统解析（正确性不变：那条路本来就是兜底）。
         *
         * 冷却按 URL 记：用户换了 DoH 端点，新端点立刻不受旧冷却影响。
         * 计数用 `@Volatile` 而非原子类——它是"够不够糟糕"的启发式，
         * 并发下少记一次不影响结论，不值得为它加锁。
         */
        @Volatile
        private var dohFailures = 0

        @Volatile
        private var dohCoolingUrl = ""

        @Volatile
        private var dohCooldownUntilMs = 0L

        private fun dohCooling(dohUrl: String, nowMs: Long): Boolean =
            dohCoolingUrl == dohUrl && nowMs < dohCooldownUntilMs

        private fun noteDohFailure(dohUrl: String, hostname: String, cause: Throwable) {
            val failures = dohFailures + 1
            dohFailures = failures
            if (failures >= DOH_FAILURE_THRESHOLD) {
                dohCoolingUrl = dohUrl
                dohCooldownUntilMs = System.currentTimeMillis() + DOH_COOLDOWN_MS
                LogUtil.w(
                    "DNS",
                    "DoH 连续失败 $failures 次，${DOH_COOLDOWN_MS}ms 内不再排这一档：$hostname（${cause.message}）",
                )
            } else {
                LogUtil.w("DNS", "DoH 失败，降级系统解析 $hostname: ${cause.message}")
            }
        }

        private fun noteDohSuccess() {
            dohFailures = 0
        }

        private val cloudFlareIps = listOf(
            "172.64.229.154", "162.159.0.1", "108.162.192.1", "172.64.33.1", "104.19.0.1",
            "2606:4700:3035::ac43:bb8d", "2606:4700:3030::6815:746", "2606:4700:3030::6815:714"
        )

        private val getchuIps = listOf("210.155.150.166", "210.155.150.145")

        private const val GETCHU_HOSTNAME = "www.getchu.com"

        /**
         * 解析自定义 IP 列表，逗号分隔
         */
        fun parseCustomIps(raw: String): List<String> {
            return raw.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }

        /**
         * 验证自定义 IP 列表格式是否有效
         * @return 无效 IP 的错误信息列表，为空表示全部有效
         */
        fun validateCustomHosts(raw: String): List<String> {
            val errors = mutableListOf<String>()
            if (raw.isBlank()) return errors
            val ips = parseCustomIps(raw)
            if (ips.isEmpty()) {
                errors.add("No IP addresses entered")
                return errors
            }
            ips.forEach { ip ->
                if (!isValidIpAddress(ip)) {
                    errors.add("Invalid IP address: \"$ip\"")
                }
            }
            return errors
        }

        private fun isValidIpAddress(ip: String): Boolean {
            return runCatching {
                val addr = InetAddress.getByName(ip)
                addr.hostAddress == ip || addr.hostAddress == ip.removePrefix("[")
                    .removeSuffix("]")
            }.getOrDefault(false)
        }
    }

    /**
     * 解析一个域名，按"哪一档先出结果就用哪一档"的次序降级。
     *
     * 为什么要链条：默认只有 `Dns.SYSTEM` 一条路，系统 DNS 被污染或 LocalDNS 抽风时
     * 请求直接失败，用户看到的只是"应用进不去"；而 DoH 与内置 IP 这两条已经写好的路，
     * 各自绑在一个手动开关上——大部分用户不会去翻设置。
     *
     * 顺序：内置 IP 自动档（Hanime 系且探测到可用节点）→ DoH（开着才排这一位）
     * → 系统 → 内置/自定义 IP 兜底（只有 Hanime 系站点有数据）。
     * [SettingsRepository.useBuiltInHosts] 是"我就是要走这些 IP"的显式指定，不参与链条。
     * 全档皆墨时把系统解析那次的异常抛出去，保持 OkHttp 原有的失败语义。
     */
    override fun lookup(hostname: String): List<InetAddress> {
        if (hostname == GETCHU_HOSTNAME) {
            return hostname.toAddresses(getchuIps)
        }

        val hanimeHost = HANIME_HOSTNAME.contains(hostname)
        if (SettingsRepository.useBuiltInHosts && hanimeHost) {
            return hostname.toAddresses(resolveStaticIps())
        }

        // 自动档：Hanime 系域名优先走"探测过能建连"的内置 IP；一个都不通就继续往下
        // （DoH → 系统 → 内置兜底）。失败会回退，所以这一档才敢默认打开。
        // 排在 DoH 之前是刻意的：实测 DoH 对这几个域名同样返回假 IP，让它抢先只是白跑一趟。
        if (hanimeHost && SettingsRepository.autoBuiltInHosts) {
            val usable = CdnIpProbe.usable(resolveStaticIps())
            if (usable.isNotEmpty()) return hostname.toAddresses(usable)
        }

        val dohUrl = DohConfig.resolveUrl()
        val now = System.currentTimeMillis()
        var systemFuture: java.util.concurrent.Future<List<InetAddress>>? = null
        if (!dohUrl.isNullOrBlank() && !dohCooling(dohUrl, now)) {
            // 系统与 DoH 并行发起。
            //
            // 刻意**不是** happy-eyeballs 那种"谁先回用谁"：系统解析快但可能被污染，
            // DoH 慢但可信，采纳顺序必须仍是 DoH 优先。并行只省掉
            // "DoH 失败后再去查系统"那一段串行等待 —— 首屏碰上若干新域名时，
            // 这段等待会按域名数叠加。
            systemFuture = runCatching {
                racePool.submit<List<InetAddress>> { Dns.SYSTEM.lookup(hostname) }
            }.getOrNull()

            val viaDoH = runCatching { lookupByDoH(dohUrl, hostname) }
                .onFailure { noteDohFailure(dohUrl, hostname, it) }
                .onSuccess { noteDohSuccess() }
                .getOrNull()?.takeIf { it.isNotEmpty() }
            if (viaDoH != null) return viaDoH
        }

        val system = systemFuture?.let { runCatching { it.get(SYSTEM_RACE_TIMEOUT_MS, TimeUnit.MILLISECONDS) } }
            ?: runCatching { Dns.SYSTEM.lookup(hostname) }
        val viaSystem = system.getOrNull()?.takeIf { it.isNotEmpty() }
        if (viaSystem != null) return viaSystem

        LogUtil.w("DNS", "系统解析不可用，降级内置 IP: $hostname ${system.exceptionOrNull()?.message}")
        if (hanimeHost) return hostname.toAddresses(resolveStaticIps())
        throw system.exceptionOrNull() ?: UnknownHostException("系统解析无结果: $hostname")
    }

    /** 手动档/最后一档共用的 IP 表：用户填的自定义 IP 优先，没填（或填得不成列表）才用内置的。 */
    private fun resolveStaticIps(): List<String> =
        resolveCustomIps()?.takeIf { it.isNotEmpty() } ?: cloudFlareIps

    private fun String.toAddresses(ips: List<String>): List<InetAddress> = ips.map {
        InetAddress.getByAddress(this, InetAddress.getByName(it).address)
    }

    private fun lookupByDoH(dohUrl: String, hostname: String): List<InetAddress> {
        val config = DohRuntimeConfig(
            url = dohUrl,
            bootstrapIps = DohConfig.bootstrapIps(),
            timeoutSeconds = DohConfig.timeoutSeconds(),
        )
        val dns = getOrCreateDohDns(config)
        return dns.lookup(hostname).also {
            // 每次域名解析都会走到这里 —— INFO 级等于按请求刷屏；解析结果属调试细节。
            LogUtil.d("DOH", it.toString())
        }
    }

    fun lookupByDoHOnly(hostname: String): List<InetAddress> {
        val dohUrl = DohConfig.resolveUrl() ?: error("DoH is disabled")
        return lookupByDoH(dohUrl, hostname)
    }

    private fun getOrCreateDohDns(config: DohRuntimeConfig): Dns {
        val currentDns = cachedDohDns
        if (currentDns != null && cachedDohConfig == config) return currentDns

        synchronized(this) {
            val dnsAgain = cachedDohDns
            if (dnsAgain != null && cachedDohConfig == config) return dnsAgain

            val client = OkHttpClient.Builder()
                .connectTimeout(config.timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .readTimeout(config.timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .build()
            val bootstrapHosts = config.bootstrapIps.mapNotNull { ip ->
                runCatching { InetAddress.getByName(ip) }.getOrNull()
            }
            val dnsBuilder = DnsOverHttps.Builder()
                .client(client)
                .url(config.url.toHttpUrl())
                .includeIPv6(true)
                .post(false)
                .resolvePrivateAddresses(true)
                .resolvePublicAddresses(true)
            if (bootstrapHosts.isNotEmpty()) {
                dnsBuilder.bootstrapDnsHosts(bootstrapHosts)
            }
            val dns = dnsBuilder.build()

            cachedDohConfig = config
            cachedDohDns = dns
            return dns
        }
    }

    fun getCDNList(host: String): List<String> {
        if (host == GETCHU_HOSTNAME) {
            return getchuIps.distinct()
        }

        val preferred = preferredIps(host)
        if (preferred.isNotEmpty()) return preferred.distinct()

        return runCatching {
            Dns.SYSTEM.lookup(host).map { it.hostAddress }.distinct()
        }.getOrElse {
            it.printStackTrace()
            emptyList()
        }
    }

    /**
     * 这个 host **实际会走**的内置 IP（自动档只返回探测过能建连的那些）。
     *
     * 返回空表 = 这次不会走内置 IP（落到 DoH / 系统 DNS）。桌面 CF 验证浏览器靠它判断
     * 该不该用 `--host-resolver-rules` 把 host 钉住：钉到一个应用自己都不用的 IP 上，
     * 等于把验证页送到另一个出口，`cf_clearance` 照样绑不上。
     */
    fun preferredIps(host: String): List<String> {
        if (!HANIME_HOSTNAME.contains(host)) return emptyList()
        return when {
            SettingsRepository.useBuiltInHosts -> resolveStaticIps()
            SettingsRepository.autoBuiltInHosts -> CdnIpProbe.usable(resolveStaticIps())
            else -> emptyList()
        }
    }

    @Volatile
    private var cachedCustomIps: List<String>? = null

    @Volatile
    private var cachedCustomIpsRaw: String? = null

    private fun resolveCustomIps(): List<String>? {
        val raw = SettingsRepository.customHostsData
        if (raw.isBlank()) return null
        if (raw == cachedCustomIpsRaw && cachedCustomIps != null) {
            return cachedCustomIps
        }
        val result = parseCustomIps(raw)
        cachedCustomIpsRaw = raw
        cachedCustomIps = result
        return result
    }

}
