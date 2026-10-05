package lovehan1me.feature.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import lovehan1me.core.util.LogUtil
import java.io.IOException

/**
 * Exo 侧的 ECH 网关包装（逐请求改写）。
 *
 * 为什么不能只改顶层 URL（`MediaItem.fromUri` 那一处）：HLS 的 m3u8 内
 * 分片/音轨 URL 是 CDN 域名，Exo 拉分片时直接用新 URL 建 [DataSpec]，
 * 工厂级改写够不着它们。在 `open()` 这一层按**每个请求的实际 URL**改写，
 * 每个分片各自携带自己的网关头——与网关按域名决策天然对齐。
 *
 * 网关未运行（改写返回 null）时零改动透传，开销为一次纯内存判定，
 * 与网关存在前一致。改写策略经 [PlayerNetworkConfig] 注入，
 * 本文件不再直读网关单例（Gate3-P1 设置项解耦）。
 *
 * ## 网关失败回退直连（阶段 4.1）
 * 逐请求级别的兜底：改写后 `open` 抛 `IOException`（网关 502 / 上游拨不通 / 连接被关）时，
 * 用**原始 URL** 直连重试一次。桌面 `DesktopMpvPlaybackEngine` 早有同语义回退，
 * Android 此前缺失 —— 同一部片"封面能刷、视频打不开"只在桌面自愈。放在 `open()` 这一层，
 * HLS 每个分片各自回退，不依赖顶层一次重载。
 *
 * ## 网关结局上报（阶段 4.2）
 * 每次走网关改写的 `open` 都回报 [PlayerNetworkConfig.onGateLoadOutcome]，成功与失败都报；
 * 让视频域拥有自己的网关健康数据（此前视频 CDN 与图床不同域时该域永不熔断）。
 *
 * 限制：网关头只加到本次请求的头里，不污染共享工厂的默认头
 * （顶层与分片域名不同，工厂级加头会把顶层域名错贴到分片上）。
 */
@OptIn(UnstableApi::class)
internal class EchGateDataSource(
    private val delegate: HttpDataSource,
    private val network: PlayerNetworkConfig,
) : HttpDataSource {

    override fun open(dataSpec: DataSpec): Long {
        val rewrite = network.rewriteForGate(dataSpec.uri.toString())
            ?: return delegate.open(dataSpec)
        val headers = dataSpec.httpRequestHeaders.toMutableMap()
        headers.putAll(rewrite.second)
        val gated = dataSpec.buildUpon()
            .setUri(rewrite.first)
            .setHttpRequestHeaders(headers)
            .build()
        LogUtil.d(TAG, "ECH direct ${rewrite.first}")
        return try {
            val length = delegate.open(gated)
            network.onGateLoadOutcome(dataSpec.uri.toString(), ok = true)
            length
        } catch (e: IOException) {
            // 网关链路失败 → 用**原始 URL** 直连重试一次，与桌面
            // `DesktopMpvPlaybackEngine.openMedia` 的 `allowGate=false` 回退同语义，
            // 别让"能浏览不能播"在 Android 上单独复现。
            //
            // 先记网关这次失败再回退：视频域据此拥有自己的网关健康数据（F9）——
            // 回退成功不代表网关是好的，该熔断的域仍要熔断。
            network.onGateLoadOutcome(dataSpec.uri.toString(), ok = false, reason = e.message)
            LogUtil.w(TAG, "网关链路打开失败，回退直连 ${dataSpec.uri} (${e.message})")
            delegate.open(dataSpec)
        }
    }

    // —— 以下全透传 ——

    override fun getUri(): android.net.Uri? = delegate.uri
    override fun getResponseCode(): Int = delegate.responseCode
    override fun getResponseHeaders(): Map<String, List<String>> = delegate.responseHeaders
    override fun setRequestProperty(name: String, value: String) = delegate.setRequestProperty(name, value)
    override fun clearRequestProperty(name: String) = delegate.clearRequestProperty(name)
    override fun clearAllRequestProperties() = delegate.clearAllRequestProperties()
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        delegate.read(buffer, offset, length)

    @Throws(java.io.IOException::class)
    override fun close() = delegate.close()

    override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) =
        delegate.addTransferListener(transferListener)

    private companion object {
        const val TAG = "EchGateDataSource"
    }
}

/**
 * 带网关改写的 Exo HTTP 数据源工厂：每个 `createDataSource()` 产出的实例
 * 都包一层 [EchGateDataSource]，顶层 manifest 与内部分片统一走网关判定。
 */
@OptIn(UnstableApi::class)
internal class EchGateDataSourceFactory(
    private val upstream: HttpDataSource.Factory,
    private val network: PlayerNetworkConfig,
) : DataSource.Factory {
    override fun createDataSource(): DataSource =
        EchGateDataSource(upstream.createDataSource(), network)
}
