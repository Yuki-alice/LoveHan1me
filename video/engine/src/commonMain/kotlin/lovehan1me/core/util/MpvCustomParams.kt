package lovehan1me.core.util

/**
 * 解析「MPV 自定义参数」文本框为 mpv 属性表。
 *
 * 输入格式：`key,value;key,value;…`（分号分条目、逗号分键值）。
 *
 * ## 为什么放在 commonMain
 * 历史上 Android 侧（原生 libmpv `MPVLib.setOptionString`）与桌面侧（mediamp
 * `MPVHandle.setPropertyString`）下发的是**同一份用户输入**；Gate3-P6 砍掉 Android
 * mpv 内核后**只剩桌面消费**，但解析逻辑仍留在 commonMain：它是 mpv 语义的一部分，
 * 不该跟着"当前谁在用"来回搬（且 Android 若将来恢复 mpv 内核即自动复用）。
 * 解析一旦各写一份，就会出现"同一串参数在两端理解不同"的隐性分歧 —— 这类 bug
 * 只会在用户手里复现。
 * 故两端共用本函数。
 *
 * ## 容错取向：静默丢弃非法条目
 * 这是用户手写的文本框，常见输入是 `profile,gpu-hq; deband, no;`（尾分号、多余空格）。
 * 缺逗号 / 空键 / 空值一律丢弃而不是抛异常 —— 手滑多打一个分号不该让播放起不来。
 */
internal fun parseMpvCustomParams(raw: String): Map<String, String> = buildMap {
    raw.split(';').forEach { entry ->
        val parts = entry.trim().split(',', limit = 2)
        if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
            put(parts[0].trim(), parts[1].trim())
        }
    }
}
