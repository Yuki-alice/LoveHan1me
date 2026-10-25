package lovehan1me.data.network

/**
 * iOS 应用内代理的会话级注入字典（网络优化 1.2）。
 *
 * iOS 唯一可行的应用内代理注入点是 NSURLSession 会话配置
 * （`connectionProxyDictionary`），经 Ktor Darwin 引擎的 `configureSession`
 * 写入（见 createIosTransport）。映射约定：
 * - **Direct** → 空字典：尝试显式关闭系统代理（会话配置不设该键时会跟随系统，
 *   与"直连"语义不符；空字典能否真正关闭**未经真机验证**——若实测关不掉，
 *   降级方案是把它当 System 处理 + 文案如实，见活计划 1.2 风险注记）；
 * - **System** → null：不设该键，跟随系统；
 * - **Http** → HTTP + HTTPS 两组键；**Socks** → SOCKS 键；
 * - **例外表恒含 `127.0.0.1` / `localhost`**：ECH 网关改写后的回环请求
 *   绝不能被塞回用户代理（与 jvm 侧 `HanimeProxySelector` 的回环 bypass 同语义）。
 *
 * **为什么是字符串字面量键而不是 `kCFNetworkProxies*` C 常量**：
 * 这些常量的值就是它们自己的名字（`CFSTR("kCFNetworkProxiesHTTPEnable")`），
 * 是 iOS 上 `connectionProxyDictionary` 的通行写法（Alamofire 的代理注入同款）；
 * 而 C 符号在 iOS SDK 的头文件里未导出（K/N 绑定为 deprecated/unavailable），
 * 引了也编不过。键名是 Apple 文档化的稳定字符串，直接写比绕 C 绑定更诚实。
 *
 * 纯函数：不读设置、不碰系统 —— 四模式 + 例外表可被 iosSimulatorArm64Test
 * 确定性断言（活计划 1.2 验收第 1 条）。值类型：enable 取 1（Int → NSNumber）、
 * 端口取 Int，经 ObjC 集合桥接层按 NSNumber 消费。
 */
internal const val KEY_PROXY_EXCEPTIONS_LIST = "kCFNetworkProxiesExceptionsList"

internal fun proxyDictionaryFor(type: Int, ip: String, port: Int): Map<Any?, Any?>? {
    val dict: Map<Any?, Any?> = when (type) {
        HProxyTypes.TYPE_DIRECT -> return emptyMap()
        HProxyTypes.TYPE_SYSTEM -> return null
        HProxyTypes.TYPE_HTTP -> {
            if (ip.isBlank() || port !in 1..65535) return null
            mapOf(
                "kCFNetworkProxiesHTTPEnable" to 1,
                "kCFNetworkProxiesHTTPProxy" to ip,
                "kCFNetworkProxiesHTTPPort" to port,
                "kCFNetworkProxiesHTTPSEnable" to 1,
                "kCFNetworkProxiesHTTPSProxy" to ip,
                "kCFNetworkProxiesHTTPSPort" to port,
            )
        }
        HProxyTypes.TYPE_SOCKS -> {
            if (ip.isBlank() || port !in 1..65535) return null
            mapOf(
                "kCFNetworkProxiesSOCKSEnable" to 1,
                "kCFNetworkProxiesSOCKSProxy" to ip,
                "kCFNetworkProxiesSOCKSPort" to port,
            )
        }
        else -> return null
    }
    // 例外表恒置：网关回环（改写后的 127.0.0.1:port）与本地调试地址绕开用户代理。
    // Direct 档（空字典）本就没有代理可绕，保持纯空 —— "关闭代理"语义最直白。
    return dict + (KEY_PROXY_EXCEPTIONS_LIST to listOf("127.0.0.1", "localhost"))
}
