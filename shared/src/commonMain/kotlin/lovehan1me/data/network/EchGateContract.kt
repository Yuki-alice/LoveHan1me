package lovehan1me.data.network

/**
 * 应用进程与网关进程（`echgate`）之间的**线协议**：三端唯一持有这些口令的地方。
 *
 * 这条边界是靠字符串维系的 —— 子进程在 stdout 打一行、在 body 里写字面量、
 * 在 argv 里认参数名。此前这些口令散在 `EchGateProcess`、`EchGateInterceptor`、
 * `EchGatePlugin` 各自的函数体与私有常量里，其中错误页前缀有两份副本：
 * 网关侧改一个字节，应用侧不会有任何编译错误，只会静默地"永远不认"。
 *
 * 放在 commonMain：改动本身与平台无关，且能直接进 `commonTest` 断言。
 */
object EchGateContract {

    /**
     * 就绪行前缀。网关先经 DoH 取 ECH 公钥配置，成功后才打印这一行并开始监听；
     * 应用侧靠它把「已 spawn」与「真能收流量」分开。
     */
    const val READY_PREFIX = "LISTENING"

    /** 网关自己的上游错误页前缀（Go 侧 `onUpstreamError` 写 `echgate: ...`）。 */
    const val ERROR_PAGE_PREFIX = "echgate:"

    /** 监听地址，形如 `127.0.0.1:34567`。 */
    const val FLAG_LISTEN = "--listen"

    /** 上游 IP 种子列表（逗号分隔）：网关自己解析会撞上被污染的系统 DNS。 */
    const val FLAG_IP_LIST = "--ip-list"

    /** 只有这些域名才配用 CF IP + ECH，其余按 CNAME / 普通 TLS 走。 */
    const val FLAG_CF_HOSTS = "--cf-hosts"

    /** ECH 公钥配置的磁盘缓存目录；没有它每次都要先等一次 DoH 往返才 listen。 */
    const val FLAG_CACHE_DIR = "--cache-dir"

    /** 该行是否宣告网关已就绪。 */
    fun isReadyLine(line: String): Boolean = line.startsWith(READY_PREFIX)

    /** 该 body 是否是网关自己的上游错误页（而非上游真的返回了这个状态码）。 */
    fun isErrorPage(body: String): Boolean = body.startsWith(ERROR_PAGE_PREFIX)

    /**
     * 网关的决策/失败行 —— 这些必须可见。
     *
     * 网关的"为什么失败"全在这几行里（探测超时、拨号被拒、ECH 试不通改走 CNAME），
     * 只按 debug 级丢掉的话，客户端侧只剩一句"网关异常，回退直连"，排障没有依据。
     * 匹配的是 Go 侧 `log.Printf` 的内容，不含应用侧加的 `echgate: ` 前缀。
     */
    private val DIAGNOSTIC_MARKERS = listOf(
        "upstream error",
        "plan ",
        "CONNECT ",
        "拨号失败",
        "解析无结果",
        "ECH 试不通",
        "被阻断，改用 CNAME",
        "候选 ",
    )

    fun isDiagnosticLine(line: String): Boolean = DIAGNOSTIC_MARKERS.any { line.contains(it) }
}
