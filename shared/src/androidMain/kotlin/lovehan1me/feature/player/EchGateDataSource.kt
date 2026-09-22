package lovehan1me.feature.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import lovehan1me.core.util.LogUtil
import lovehan1me.data.network.EchGate
import lovehan1me.data.network.EchGatePolicy

/**
 * Exo 侧的 ECH 网关包装（逐请求改写）。
 *
 * 为什么不能只改顶层 URL（`MediaItem.fromUri` 那一处）：HLS 的 m3u8 内
 * 分片/音轨 URL 是 CDN 域名，Exo 拉分片时直接用新 URL 建 [DataSpec]，
 * 工厂级改写够不着它们。在 `open()` 这一层按**每个请求的实际 URL**改写，
 * 每个分片各自携带自己的 `X-Ech-Target`——与网关按域名决策天然对齐。
 *
 * 网关未运行（[EchGate.port] <= 0）时零改动透传，开销为一次 [EchGatePolicy]
 * 判定（纯内存），与网关存在前一致。
 *
 * 限制：`X-Ech-Target` 只加到本次请求的头里，不污染共享工厂的默认头
 * （顶层与分片域名不同，工厂级加头会把顶层域名错贴到分片上）。
 */
@OptIn(UnstableApi::class)
internal class EchGateDataSource(
    private val delegate: HttpDataSource,
) : HttpDataSource {

    override fun open(dataSpec: DataSpec): Long {
        val rewrite = EchGatePolicy.rewrite(dataSpec.uri.toString(), EchGate.port)
            ?: return delegate.open(dataSpec)
        val headers = dataSpec.httpRequestHeaders.toMutableMap()
        headers[EchGatePolicy.TARGET_HEADER] = rewrite.targetHost
        val gated = dataSpec.buildUpon()
            .setUri(rewrite.url)
            .setHttpRequestHeaders(headers)
            .build()
        LogUtil.d(TAG, "ECH direct ${rewrite.targetHost}")
        return delegate.open(gated)
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
) : DataSource.Factory {
    override fun createDataSource(): DataSource =
        EchGateDataSource(upstream.createDataSource())
}
