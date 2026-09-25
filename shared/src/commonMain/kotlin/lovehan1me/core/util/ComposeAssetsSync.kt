package lovehan1me.core.util

import kotlinx.serialization.json.Json

/**
 * 同步读取 composeResources 资产（P6c：TagLocalizer 是 `by lazy` 同步缓存，无法用 suspend 的
 * Res.readBytes，故提供 expect 同步读取）。path 以 files/ 开头（如 "files/search_options/tags.json"）。
 *  三端都有真实实现，找不到才返回 null：
 *  - Android：APK assets 下 `composeResources/lovehan1me/<path>`；
 *  - Desktop：classpath 同前缀（打包产物里唯一成立的路径），另有 cwd 兜底；
 *  - iOS：app / framework bundle 下的 `compose-resources/composeResources/lovehan1me/<path>`。
 *
 * ⚠️ 返回 null 是**静默降级**，代价远不止「标签不翻译」：SearchViewModel 的六份筛选
 * 数据源（类型 / 排序 / 标签 / 品牌 / 发布日期 / 时长）都走这里，一旦读不到就整片变空，
 * 弹窗会退化成「只剩标题和重置/取消」。桌面端 2026-09-16、iOS 2026-09-24 各踩过一次，
 * 改这里的路径前缀后请务必真机/模拟器打开一次筛选弹窗确认有选项。
 */
internal const val CMP_COMPOSE_RESOURCE_DIR = "composeResources/lovehan1me/"

internal expect fun readComposeFileSync(path: String): ByteArray?

private val composeJson = Json { ignoreUnknownKeys = true }

/**
 * 同步读取 composeResources 文件并解码 JSON（P6c：TagLocalizer/CommentViewModel 共用；
 * loadAssetAs 的 commonMain 替代）。path 带 files/ 前缀。读取失败返回 null。
 */
internal inline fun <reified T> decodeComposeAsset(path: String): T? = runCatching {
    val bytes = readComposeFileSync(path) ?: return null
    composeJson.decodeFromString<T>(bytes.decodeToString())
}.getOrNull()

