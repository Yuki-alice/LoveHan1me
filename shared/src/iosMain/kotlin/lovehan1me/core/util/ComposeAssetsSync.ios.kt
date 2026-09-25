package lovehan1me.core.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.posix.memcpy

/**
 * CMP 把 commonMain 的 composeResources 拷进 app bundle 时的目录前缀。
 *
 * 打包形态会漂移（带不带包名、在 app bundle 还是 framework bundle），所以和 desktop 版
 * 一样走「多候选逐个试」，不写死一条。首个候选是实测命中的那一条。
 */
private val COMPOSE_RESOURCE_PREFIXES = listOf(
    "compose-resources/$CMP_COMPOSE_RESOURCE_DIR",
    "compose-resources/composeResources/",
    CMP_COMPOSE_RESOURCE_DIR,
    "composeResources/",
)

/**
 * iOS 端同步读取 composeResources。
 *
 * 实测落盘位置（iPhone 17 / iOS 26.5 模拟器产物）：
 * `LoveHan1me.app/compose-resources/composeResources/lovehan1me/files/search_options/genre.json`
 *
 * ⚠️ 这里曾直接 `= null` 兜底（原注释：路径待核查，回退 null → 调用方保底原样标签）。
 * 后果是 [decodeComposeAsset] 在 iOS 上恒返回 null —— 而它早已不只是给 TagLocalizer 做
 * 「标签翻译兜底」用，`SearchViewModel` 的 genres / sortOptions / durations / timeList /
 * tags / brands 六份筛选数据源全靠它加载。于是六份全退化成空集合，
 * 表现为**发现页高级筛选「类型 / 排序方式 / 影片时长」弹窗只剩标题和「重置 / 取消」，
 * 选项一个都不显示**（2026-09-24 全功能测试 P1）。
 *
 * 桌面端 2026-09-16 踩过一模一样的坑（当时 classpath 前缀写错导致同样现象），
 * 详见 [desktop 版注释](ComposeAssetsSync.desktop.kt)。这里是同一处遗漏的 iOS 侧。
 */
internal actual fun readComposeFileSync(path: String): ByteArray? {
    val fileManager = NSFileManager.defaultManager
    for (root in bundleRoots()) {
        for (prefix in COMPOSE_RESOURCE_PREFIXES) {
            val data = fileManager.contentsAtPath("$root/$prefix$path") ?: continue
            return data.toByteArray()
        }
    }
    warnMissingComposeAsset(path)
    return null
}

/**
 * 资源可能在 app bundle 里，也可能在嵌入的 framework bundle 里，两处都试。
 */
private fun bundleRoots(): List<String> = buildList {
    add(NSBundle.mainBundle.bundlePath)
    NSBundle.mainBundle.resourcePath?.let(::add)
    NSBundle.allFrameworks.forEach { bundle ->
        val nsBundle = bundle as? NSBundle ?: return@forEach
        add(nsBundle.bundlePath)
    }
}.distinct()

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size <= 0) return ByteArray(0)
    val source = bytes ?: return ByteArray(0)
    return ByteArray(size).apply {
        usePinned { pinned ->
            memcpy(pinned.addressOf(0), source, size.toULong())
        }
    }
}

/**
 * 读不到是静默降级，历史上因此拖了很久才被发现。只报一次，避免刷屏。
 */
private object MissingAssetWarning {
    var emitted = false
}

private fun warnMissingComposeAsset(path: String) {
    if (MissingAssetWarning.emitted) return
    MissingAssetWarning.emitted = true
    println("[ComposeAssets] iOS 未读到 composeResources 资产（$path）：筛选选项与标签本地化将为空")
}
