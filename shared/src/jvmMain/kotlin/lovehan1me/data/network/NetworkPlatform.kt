package lovehan1me.data.network

import okhttp3.Interceptor
import java.io.File

/**
 * jvmMain（android + desktop 共享）平台差异点，P3 新增：
 *  - [httpCacheDirectory]：OkHttp 磁盘缓存目录（android = cacheDir/http_cache；desktop = ~/.lovehan1me/http_cache）
 *  - [createCloudflareInterceptor]：Cloudflare 拦截器仅 Android 安装（依赖 WebView 验证），desktop 返回 null 不装
 *  - [currentHttpUserAgent]：HTTP 层统一 UA（见下）
 */
expect fun httpCacheDirectory(): File

expect fun createCloudflareInterceptor(): Interceptor?

/**
 * HTTP 层发送的 User-Agent（Android = 移动 UA，桌面 = 桌面 UA）。
 *
 * ⚠️ **它必须与 CF 验证浏览器用的是同一个字符串。**
 * `cf_clearance` 绑定 (出口 IP, UA)：浏览器解出来的 clearance 只有在服务端
 * 看到**同一个 UA** 时才有效。
 *
 * 历史 bug（2026-09-13 实测定位，本轮修复）：桌面端 HTTP 层一直发的是
 * [lovehan1me.core.constant.USER_AGENT]（Android 移动 UA），而 `CloudflareCdp`
 * 给验证浏览器塞的是 [lovehan1me.core.constant.DESKTOP_USER_AGENT]（Windows UA）
 * —— 两者不一致 ⇒ 收割回来的 clearance 对应用请求**永远无效**，
 * 表现为"验证窗口走完流程、页面依旧打不开 / 反复弹验证"，而"手动粘贴 clearance"
 * 也一样无效（同样对不上 UA）。这一条与 headless 无关，是独立的结构性错误。
 *
 * 站点对 UA 不敏感：实测移动 UA 与桌面 UA 取回的首屏 HTML **逐字节相同**
 * （219667 bytes，关键标记数 14/14、144/144 完全一致），故桌面端改发桌面 UA
 * 不存在解析风险。iOS 不在此 expect 覆盖范围内（它本来就一直用移动 UA）。
 */
expect fun currentHttpUserAgent(): String
