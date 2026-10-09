package lovehan1me.site.hanime1

import kotlinx.serialization.Serializable
import lovehan1me.core.constant.DEF_VIDEO_TYPE

/**
 * 分辨率 → 播放链接映射。
 *
 * 刻意声明为只读的 [Map]（而非具体可变实现类 `LinkedHashMap`）：Compose 稳定性是
 * **声明类型的编译期属性**，具体可变集合类会让消费它的模型被判定为 unstable。构造点
 * 仍可传 `linkedMapOf(...)`（`Map` 的子类型），但对外契约只保证 `Map`。
 *
 * P4：自 :app HanimeResolution.kt 下沉（包名不变）。
 * 两处 android 依赖已替换（语义等价）：
 *  - okhttp MediaType 解析 → 字符串按 '/' 切分（commonMain 不依赖 okhttp）；
 *  - HanimeLink.suffix 的默认后缀取 lovehan1me.core.constant.DEF_VIDEO_TYPE
 *   （与 androidMain HanimeFileManager 同一源，同 P2b HanimeDownloadEntity 处理方式）。
 */
typealias ResolutionLinkMap = Map<String, HanimeLink>

/**
 * 如果你在其他地方看到了 Quality，那就是 Resolution，我混用了。
 *
 * @project LoveHan1me
 * @author Yenaly Liew（上游原作者，见 NOTICE）
 * @time 2022/10/11 011 21:19
 */
class HanimeResolution {

    private val resArray = arrayOfNulls<Pair<String, HanimeLink>>(5)
    // P0-2：未知档溢出槽。站点新增档位（2K/4K/大小写变体/m3u8）时不能静默丢弃，
    // 否则 videoUrls 为空 → 播放页转圈。已知 4 档仍占固定槽保序，余下全进 extras。
    private val extras = mutableListOf<Pair<String, HanimeLink>>()

    companion object {

        // 目前hanime1有的分辨率好像就這些，暫時不考慮其他分辨率

        const val RES_1080P = "1080P"
        const val RES_720P = "720P"
        const val RES_480P = "480P"
        const val RES_240P = "240P"
        const val RES_UNKNOWN = "Unknown"

        /**
         * P0-2：分辨率标签归一化（纯函数，可单测）。
         *
         * 开放输入（服务端随时加档）：`"720p"/" 720P "/"720"` 全收敛到 `"720P"`；
         * 空/无法识别 → null（调用方落 Unknown 槽）。保留显式 else，不删回退。
         */
        internal fun normalizeLabel(raw: String?): String? {
            val t = raw?.trim()?.uppercase().orEmpty()
            if (t.isEmpty() || t == "P") return null
            val withP = if (t.endsWith("P")) t else "${t}P"
            return when (withP) {
                RES_1080P, RES_720P, RES_480P, RES_240P -> withP
                // 数字档如 2160P/1440P/360P：承认它是档位，但不占固定槽。
                else if (withP.removeSuffix("P").all { it.isDigit() }) -> withP
                else -> null
            }
        }
    }

    /**
     * 解析分辨率，從高到低排列。
     *
     * @param resString 分辨率
     * @param resLink 分辨率對應網址
     * @param type 例如 video/mp4
     */
    fun parseResolution(resString: String?, resLink: String, type: String? = null) {
        // 原实现：okhttp MediaType.toMediaTypeOrNull()，类型为 video 才取 subtype
        val lowered = type?.trim()?.lowercase().orEmpty()
        val slash = lowered.indexOf('/')
        val mediaType = if (slash <= 0) null else lowered.substring(0, slash) to lowered.substring(slash + 1)
        // P0-2：HLS 感知。video/mp2t 本是 ts 切片；application/x-mpegurl / vnd.apple.mpegurl
        // 是顶层 m3u8，其 subtype 必须保留为 m3u8（供播放器判 HLS），不能按 video 前缀丢弃。
        val link = when {
            "m3u8" in lowered || "mpegurl" in lowered -> HanimeLink(resLink, "m3u8")
            mediaType != null && mediaType.first == "video" -> HanimeLink(resLink, mediaType.second)
            else -> HanimeLink(resLink, null)
        }
        // P0-2：when(subject) 穷举已知档 + 显式 else。未知数字档/未知字面都不丢：
        // 数字档进 extras（key 即归一化标签），非数字未知进 Unknown 槽（首个）/extras（后续）。
        when (normalizeLabel(resString)) {
            RES_1080P -> resArray[0] = RES_1080P to link
            RES_720P -> resArray[1] = RES_720P to link
            RES_480P -> resArray[2] = RES_480P to link
            RES_240P -> resArray[3] = RES_240P to link
            null -> {
                if (resArray[4] == null) resArray[4] = RES_UNKNOWN to link
                else extras += "$RES_UNKNOWN-${extras.size + 2}" to link
            }
            else -> {
                val label = normalizeLabel(resString) ?: RES_UNKNOWN
                // 同档重复（站点偶发重复 source）：后者进 extras，不覆盖主槽。
                val occupied = resArray.any { it?.first == label } || extras.any { it.first == label }
                if (!occupied) extras += label to link
                else extras += "$label-${extras.size + 2}" to link
            }
        }
    }

    fun toResolutionLinkMap(): ResolutionLinkMap {
        val ordered = resArray.filterNotNull().toMap(linkedMapOf())
        extras.forEach { (k, v) -> ordered[k] = v }
        return ordered
    }
}

@Serializable
data class HanimeLink(
    val link: String,
    val subtype: String?,
) {
    val suffix: String
        get() = when (subtype?.lowercase()) {
            "mp4" -> "mp4"
            "mpeg" -> "mpeg"
            "x-msvideo" -> "avi"
            "3gpp" -> "3gp"
            "3gpp2" -> "3g2"
            "ogg" -> "ogv"
            "mp2t" -> "ts"
            "webm" -> "webm"
            // P0-2：HLS 顶层/分片。播放器靠它判 HLS（见 MediampExoPlaybackEngine
            // interceptMediaSource 的 .m3u8 分支），落盘/下载也据此命名。
            "m3u8", "x-mpegurl", "vnd.apple.mpegurl" -> "m3u8"
            else -> DEF_VIDEO_TYPE
        }
}
