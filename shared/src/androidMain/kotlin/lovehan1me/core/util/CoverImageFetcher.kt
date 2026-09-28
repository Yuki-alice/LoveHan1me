package lovehan1me.core.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lovehan1me.data.network.createCdnFetchClient
import okhttp3.Request
import java.io.ByteArrayOutputStream

/**
 * 下载视频封面并编码为 PNG 字节（下载完成后写入下载目录，供应用内离线观看用）。
 *
 * ### 为什么重写（原 `HImageMeower`）
 *
 * 前身为了下这一张图引入了整套 **Coil 2**（`ImageLoader` + 私有 `okHttpClient`），
 * 而项目主库是 **Coil 3**（shared 的 `HanimeImageLoader` 在用）
 * ⇒ 两套图片库并存 = 双份内存缓存 + 两条网络栈。这里改为 OkHttp 直下 +
 * 原生 `BitmapFactory` / `Bitmap.compress`，**输出仍是 PNG**：与旧实现
 * `Drawable.saveTo(..., CompressFormat.PNG, 100)` 行为一致，封面文件后缀 `png` 不变。
 *
 * 顺带删掉上游遗留的两块**死代码**（已全仓 grep 确认零引用）：
 * - `placeholder()` —— 指向 picsum.photos 的在线占位图；
 * - `ImageView.loadUnhappily()` —— Android View 体系，本项目已全面 Compose。
 *
 * ⚠️ 保留 `HanimeDns`：站点域名在本机常被 DNS 污染，走系统解析拿不到图。
 *
 * ⚠️ **它必须与浏览、下载同一条出口**：封面 URL 与视频直链同在 CDN 上
 * （`vdownload.hembed.com/image/…`），同样被 SNI 阻断。此前这里只配了 DNS，
 * 代理与 ECH 网关都没接 —— 开着网关或手填代理时，下载任务的封面会走裸直连
 * 而拿不到（视频本体没事，因为下载客户端接齐了）。同 `ServiceCreator` 下载链的
 * 一句话：开着网关/代理时"能看不能下"就是哪条链没跟过来。
 *
 * G1-1A：原 `internal`（仅 :app 可见）。下沉后调用方 `HanimeDownloadWorker` 暂留 :app，
 * 故放开为 public；G1-1B worker 一起下沉后可再收回。
 */
object CoverImageFetcher {
    private const val TAG = "CoverImageFetcher"
    private const val CONNECT_TIMEOUT_SECONDS = 5L

    private val okHttpClient = createCdnFetchClient(connectTimeoutSeconds = CONNECT_TIMEOUT_SECONDS)

    /**
     * 下载并转 PNG。
     *
     * 任何一步失败都返回 null —— 封面下不到**不影响视频本体**，调用方按
     * "没有封面" 处理即可，不要抛异常打断下载任务。
     */
    suspend fun fetchAsPng(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val raw = okHttpClient.newCall(Request.Builder().url(url).build())
                .execute()
                .use { response ->
                    if (!response.isSuccessful) {
                        LogUtil.w(TAG, "cover fetch failed: HTTP ${response.code} $url")
                        return@use null
                    }
                    response.body?.bytes()
                } ?: return@runCatching null

            val bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size)
            if (bitmap == null) {
                LogUtil.w(TAG, "cover decode failed (${raw.size} bytes): $url")
                return@runCatching null
            }
            ByteArrayOutputStream().use { bos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos)
                bitmap.recycle()
                bos.toByteArray()
            }
        }.onFailure {
            LogUtil.w(TAG, "cover fetch error: $url", it)
        }.getOrNull()
    }
}
