package lovehan1me.core.platform

/**
 * 读取 composeResources/files/ 下的资源（P6a：assets → composeResources/files 的统一入口）。
 * path 不带 files/ 前缀（例如 "search_options/tags.json"、"shaders/Anime4K.vpy"）。
 */
internal expect suspend fun readAssetText(path: String): String?
internal expect suspend fun readAssetBytes(path: String): ByteArray?
