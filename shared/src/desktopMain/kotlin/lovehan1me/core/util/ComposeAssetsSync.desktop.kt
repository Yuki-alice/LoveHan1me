package lovehan1me.core.util

import java.io.File

/**
 * classpath 兜底锚点：拿不到线程上下文类加载器时用本类的类加载器，避免 NPE。
 */
private object ComposeAssetsAnchor

/**
 * 桌面端同步读取 composeResources。
 *
 * 查找顺序：
 * 1. **classpath** `composeResources/lovehan1me/<path>` —— 这是唯一在打包产物（jpackage
 *    产出的 app/lib 下的 jar）里也成立的路径。CMP 把资源打进 `shared-desktop.jar` 的这个前缀下。
 * 2. `compose-resources/lovehan1me/<path>`（cwd 相对）—— CMP 某些 dev 形态的落盘约定，保底。
 *
 * ⚠️ 曾只有第 2 条：项目根下根本不存在 `compose-resources/` 目录（CMP 实际落在
 * `shared/build/processedResources/desktop/main/composeResources/...`），于是本函数恒返回 null，
 * 后果是**桌面端所有筛选选项列表全空**（类型/排序/标签/品牌/日期/时长六项一个都不显示，
 * 弹窗只剩「重置/取消」），同时 TagLocalizer 的标签中文化也静默失效。
 * 2026-09-16 实测（弹窗打开后列表为空）定位并修复。
 */
internal actual fun readComposeFileSync(path: String): ByteArray? = runCatching {
    readFromClasspath(CMP_COMPOSE_RESOURCE_DIR + path)
        ?: File("compose-resources/lovehan1me", path).takeIf(File::isFile)?.readBytes()
}.getOrNull()

private fun readFromClasspath(resourcePath: String): ByteArray? {
    val loader = Thread.currentThread().contextClassLoader
        ?: ComposeAssetsAnchor::class.java.classLoader
        ?: return null
    return loader.getResourceAsStream(resourcePath)?.use { it.readBytes() }
}
