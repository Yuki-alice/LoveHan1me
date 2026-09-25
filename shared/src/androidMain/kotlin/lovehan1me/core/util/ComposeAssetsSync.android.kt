package lovehan1me.core.util

// Android：APK assets 下 `composeResources/lovehan1me/<path>`。
// Host 单测没有 Application（appContext 未注入）时回落 JVM classpath ——
// compose resources 同样进单测运行时 classpath（见 copyNonXmlValueResources* 任务），
// 否则 commonTest 的资产护栏在 host 上恒红（2026-09-24 实测）。
internal actual fun readComposeFileSync(path: String): ByteArray? =
    readFromAssets(path) ?: readFromClasspath(path)

private fun readFromAssets(path: String): ByteArray? = runCatching {
    lovehan1me.data.database.dao.Han1meDatabaseContext.appContext.assets
        .open("$CMP_COMPOSE_RESOURCE_DIR$path")
        .use { it.readBytes() }
}.getOrNull()

private fun readFromClasspath(path: String): ByteArray? = runCatching {
    object {}.javaClass.classLoader
        ?.getResourceAsStream("$CMP_COMPOSE_RESOURCE_DIR$path")
        ?.use { it.readBytes() }
}.getOrNull()
