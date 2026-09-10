package lovehan1me.utils

import kotlinx.serialization.json.Json

/**
 * 同步读取 composeResources 资产（P6c：TagLocalizer 是 `by lazy` 同步缓存，无法用 suspend 的
 * Res.readBytes，故提供 expect 同步读取）。path 以 files/ 开头（如 "files/search_options/tags.json"）。
 *  Android：APK assets 里 CMP 打包在 composeResources/<pkg>/ 下；
 *  Desktop/iOS：尽力回退（找不到返回 null → 调用方保底原样标签）。
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

