package lovehan1me.feature.danmaku

import lovehan1me.data.danmaku.DanmakuItem

// 弹幕帧驱动（Gate3-P1 解耦用）。
//
// DanmakuLayer 只需要"引擎 + 按帧推进"这两样，而 DanmakuSession 本体拖着
// 数据层（Repository/Provider/设置流）进不来契约层（:video:contract / :video:engine）。调用方传 Session 本体
// 即可（它实现本接口），绘制件签名不变语义。
interface DanmakuFrameDriver {
    val engine: DanmakuEngine

    // 与 DanmakuSession.advance 同签名：返回本次播放位置，
    // null = 尚无锚点或已关闭，调用方什么都不画。
    fun advance(
        frameNanos: Long,
        viewport: DanmakuViewport,
        measureWidth: (DanmakuItem) -> Float,
    ): Long?
}
