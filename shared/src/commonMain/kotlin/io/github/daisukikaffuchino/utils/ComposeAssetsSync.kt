package io.github.daisukikaffuchino.utils

/**
 * 同步读取 composeResources 资产（P6c：TagLocalizer 是 `by lazy` 同步缓存，无法用 suspend 的
 * Res.readBytes，故提供 expect 同步读取）。path 以 files/ 开头（如 "files/search_options/tags.json"）。
 *  Android：APK assets 里 CMP 打包在 composeResources/<pkg>/ 下；
 *  Desktop/iOS：尽力回退（找不到返回 null → 调用方保底原样标签）。
 */
internal const val CMP_COMPOSE_RESOURCE_DIR = "composeResources/io.github.daisukikaffuchino.han1meviewer/"

internal expect fun readComposeFileSync(path: String): ByteArray?
