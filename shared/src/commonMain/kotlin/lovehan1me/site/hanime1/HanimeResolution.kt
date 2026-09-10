package lovehan1me.site.hanime1

import kotlinx.serialization.Serializable

/**
 * P4：自 :app HanimeResolution.kt 下沉（包名不变）。
 * 两处 android 依赖已替换（语义等价）：
 *  - okhttp MediaType 解析 → 字符串按 '/' 切分（commonMain 不依赖 okhttp）；
 *  - HanimeLink.suffix 的 HFileManager.DEF_VIDEO_TYPE（:app，= "mp4"）→ 内联常量（同 P2b
 *    HanimeDownloadEntity 处理方式），待 HFileManager 下沉后回填。
 */
typealias ResolutionLinkMap = LinkedHashMap<String, HanimeLink>

/**
 * 如果你在其他地方看到了 Quality，那就是 Resolution，我混用了。
 *
 * @project Han1meViewer
 * @author Yenaly Liew
 * @time 2022/10/11 011 21:19
 */
class HanimeResolution {

    private val resArray = arrayOfNulls<Pair<String, HanimeLink>>(5)

    companion object {

        // 目前hanime1有的分辨率好像就這些，暫時不考慮其他分辨率

        const val RES_1080P = "1080P"
        const val RES_720P = "720P"
        const val RES_480P = "480P"
        const val RES_240P = "240P"
        const val RES_UNKNOWN = "Unknown"
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
        val mediaType = type?.let { t ->
            val slash = t.indexOf('/')
            if (slash <= 0) null else t.substring(0, slash) to t.substring(slash + 1)
        }
        val link = if (mediaType != null && mediaType.first.equals("video", ignoreCase = true)) {
            HanimeLink(resLink, mediaType.second)
        } else {
            HanimeLink(resLink, null)
        }
        when (resString) {
            RES_1080P -> resArray[0] = RES_1080P to link
            RES_720P -> resArray[1] = RES_720P to link
            RES_480P -> resArray[2] = RES_480P to link
            RES_240P -> resArray[3] = RES_240P to link
            null -> resArray[4] = RES_UNKNOWN to link
        }
    }

    fun toResolutionLinkMap(): ResolutionLinkMap {
        return resArray.filterNotNull().toMap(linkedMapOf())
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
            // TODO: HFileManager.DEF_VIDEO_TYPE 下沉后回填
            else -> "mp4"
        }
}
