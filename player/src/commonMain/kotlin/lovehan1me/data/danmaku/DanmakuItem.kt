package lovehan1me.data.danmaku

// 引擎层弹幕模型（Gate3-P1 从 :shared 搬入，包名不变，全仓 import 零改动）。
// 与数据源无关：弹弹play、本地自发弹幕都映射到这里。
// 匹配逻辑（DandanplayMatcher 等）仍在 :shared 的 DanmakuItem.kt 同包文件里。

data class DanmakuItem(
    // 稳定 id（弹弹 cid；本地自发用负数时间戳，保证与远端不撞）。
    val id: Long,
    // 出现时刻（毫秒，相对片头）。
    val playTimeMillis: Long,
    val text: String,
    // ARGB 整型（含 alpha）。
    val color: Int,
    val location: DanmakuLocation,
    // 是否自己发的（自发弹幕描边高亮用）。
    val isSelf: Boolean = false,
    // 哪来的：远端时间轴弹幕 vs 站内评论投影（状态条计数与将来按源过滤用）。
    val source: DanmakuSource = DanmakuSource.REMOTE,
)

enum class DanmakuSource {
    // 弹弹play：时间轴精确。
    REMOTE,

    // 站内评论投影：时间是排出来的，主源但精度低。
    COMMENT,
}

enum class DanmakuLocation {
    SCROLL,
    TOP,
    BOTTOM,
}
