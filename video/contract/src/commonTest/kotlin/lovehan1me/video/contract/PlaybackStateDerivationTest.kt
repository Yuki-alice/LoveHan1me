package lovehan1me.video.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 状态派生不变式。每条对应「视频模块重构」计划的 T1–T8。
 *
 * 这些用例锁的是**曾经被破坏过**的行为，注释里写明破坏方式，避免后人"顺手优化"掉。
 */
class PlaybackStateDerivationTest {

    private val readyTruth = PlaybackTruth(
        phase = PlaybackPhase.Ready,
        isPlaying = true,
        positionMs = 10_000L,
        durationMs = 120_000L,
        bufferedPositionMs = 30_000L,
        speed = 1f,
        aspect = VideoAspectMode.Fit,
        videoWidth = 1920,
        videoHeight = 1080,
    )

    // T1：旧实现在 load() 里命令式写 phase=Error，下一次 publish() 按后端快照
    // 无条件覆盖 —— 后端还没翻到 Error 的那一帧就把 Error 抹掉了，UI 永远转圈。
    @Test
    fun `T1 开流失败写的 Error 不被下一次 publish 抹掉`() {
        val failed = PlaybackLoadStatus.Failed("boom")

        val first = derivePlaybackState(PlaybackState(), PlaybackTruth(), PlaybackRequests(), failed)
        assertEquals(PlaybackPhase.Error, first.phase)
        assertEquals("boom", first.errorMessage)

        val afterBackendStillIdle = derivePlaybackState(
            previous = first,
            truth = PlaybackTruth(phase = PlaybackPhase.Idle),
            requests = PlaybackRequests(),
            load = failed,
        )
        assertEquals(PlaybackPhase.Error, afterBackendStillIdle.phase)
        assertEquals("boom", afterBackendStillIdle.errorMessage)
    }

    // T2：切画质期间首帧标记被派生式重算抹掉 → 海报闪回。标记必须是单调锁存。
    @Test
    fun `T2 切画质期间首帧标记与画面尺寸不得被抹掉`() {
        val ready = derivePlaybackState(PlaybackState(), readyTruth, PlaybackRequests())
        assertTrue(ready.hasRenderedFirstFrame)

        val switching = derivePlaybackState(
            previous = ready,
            truth = PlaybackTruth(phase = PlaybackPhase.Preparing),
            requests = PlaybackRequests(),
            load = PlaybackLoadStatus.Opening(isQualitySwitch = true),
        )
        assertTrue(switching.hasRenderedFirstFrame, "切画质期间不得丢首帧标记（海报闪回）")
        assertTrue(switching.isSwitchingQuality)
        assertEquals(1920, switching.videoWidth, "切画质期间尺寸未知，必须保住上一档")
        assertEquals(120_000L, switching.durationMs, "切画质期间时长不得被 0 覆盖")
    }

    // T3：媒体属性尚未就绪时，后端给的 0 会把真实时长冲掉。
    @Test
    fun `T3 媒体属性未就绪时 durationMs 不被 0 覆盖`() {
        val withDuration = derivePlaybackState(PlaybackState(), readyTruth, PlaybackRequests())
        assertEquals(120_000L, withDuration.durationMs)

        val propsGone = derivePlaybackState(
            previous = withDuration,
            truth = readyTruth.copy(durationMs = 0L),
            requests = PlaybackRequests(),
        )
        assertEquals(120_000L, propsGone.durationMs)
    }

    // T4：旧实现把请求值当真值发（playbackSpeed = requestedSpeed），
    // 引擎不接受倍速时 UI 显示一个根本没生效的值。
    @Test
    fun `T4 倍速未被引擎接受时 actual 停在旧真值`() {
        val before = derivePlaybackState(PlaybackState(), readyTruth, PlaybackRequests())
        assertEquals(1f, before.actualSpeed)

        val requested2x = derivePlaybackState(
            previous = before,
            truth = readyTruth.copy(speed = 1f),
            requests = PlaybackRequests(speed = 2f),
        )
        assertEquals(2f, requested2x.requestedSpeed)
        assertEquals(1f, requested2x.actualSpeed, "引擎没回报 2x，actual 不得跟着请求值走")

        val accepted = derivePlaybackState(
            previous = requested2x,
            truth = readyTruth.copy(speed = 2f),
            requests = PlaybackRequests(speed = 2f),
        )
        assertEquals(2f, accepted.actualSpeed)
    }

    // T5：画面比例进了 Surface 的 key()，所以"设置成一个实际没变化的档位"
    // 也必须产出一个完全相等的状态，UI 才能判定无需重建。
    @Test
    fun `T5 重复设置同一画面比例不产生状态变化`() {
        val fit = derivePlaybackState(
            PlaybackState(),
            readyTruth.copy(aspect = VideoAspectMode.Fit),
            PlaybackRequests(aspect = VideoAspectMode.Fit),
        )
        val toCrop = derivePlaybackState(
            previous = fit,
            truth = readyTruth.copy(aspect = VideoAspectMode.Crop),
            requests = PlaybackRequests(aspect = VideoAspectMode.Crop),
        )
        assertEquals(VideoAspectMode.Crop, toCrop.actualAspect)
        assertTrue(fit != toCrop, "真的换了档位必须产出不同状态")

        val cropAgain = derivePlaybackState(
            previous = toCrop,
            truth = readyTruth.copy(aspect = VideoAspectMode.Crop),
            requests = PlaybackRequests(aspect = VideoAspectMode.Crop),
        )
        assertEquals(toCrop, cropAgain, "无变化的请求必须产出相等状态，否则 UI 会以为要重建 Surface")
    }

    // T6：开流中必须算缓冲中（旧引擎 load() 即置 isBuffering=true，UI 依赖它出转圈）；
    // 播完/出错一律不算缓冲中 —— 三端统一由本函数裁出，不再各端自己语义。
    @Test
    fun `T6 isBuffering 语义一致`() {
        val opening = derivePlaybackState(
            PlaybackState(),
            PlaybackTruth(phase = PlaybackPhase.Preparing, isBuffering = false),
            PlaybackRequests(),
            PlaybackLoadStatus.Opening(isQualitySwitch = false),
        )
        assertTrue(opening.isBuffering, "开流中必须算缓冲中")

        val buffering = derivePlaybackState(
            PlaybackState(),
            PlaybackTruth(phase = PlaybackPhase.Ready, isBuffering = true),
            PlaybackRequests(),
        )
        assertTrue(buffering.isBuffering)

        val ended = derivePlaybackState(
            PlaybackState(),
            PlaybackTruth(phase = PlaybackPhase.Ended, isBuffering = true),
            PlaybackRequests(),
        )
        assertFalse(ended.isBuffering, "播完不得算缓冲中")

        val errored = derivePlaybackState(
            PlaybackState(),
            PlaybackTruth(phase = PlaybackPhase.Error, isBuffering = true, errorMessage = "x"),
            PlaybackRequests(),
        )
        assertFalse(errored.isBuffering, "出错不得算缓冲中")
    }

    // T7：旧实现靠"只在进入 Error 那一帧写"的 write-once 护栏，是半补丁。
    // 改成纯派生后，同一错误反复 publish 必须得到同一条信息。
    @Test
    fun `T7 同一错误反复 publish 得到同一条信息`() {
        val first = derivePlaybackState(
            PlaybackState(),
            PlaybackTruth(phase = PlaybackPhase.Error, errorMessage = "net down"),
            PlaybackRequests(),
        )
        assertEquals("net down", first.errorMessage)

        val truthWentNull = derivePlaybackState(
            previous = first,
            truth = PlaybackTruth(phase = PlaybackPhase.Error, errorMessage = null),
            requests = PlaybackRequests(),
        )
        assertEquals("net down", truthWentNull.errorMessage, "后端后续不再带信息时不得清空")

        val recovered = derivePlaybackState(
            previous = truthWentNull,
            truth = PlaybackTruth(phase = PlaybackPhase.Ready),
            requests = PlaybackRequests(),
        )
        assertNull(recovered.errorMessage, "离开 Error 相必须清空错误信息")
    }

    // T8：切画质失败时"切换中"永久卡 true，UI 一直以为在切档。
    @Test
    fun `T8 切画质失败或完成时 isSwitchingQuality 必须撤销`() {
        val playing = derivePlaybackState(PlaybackState(), readyTruth, PlaybackRequests())
        val switching = derivePlaybackState(
            previous = playing,
            truth = PlaybackTruth(phase = PlaybackPhase.Preparing),
            requests = PlaybackRequests(),
            load = PlaybackLoadStatus.Opening(isQualitySwitch = true),
        )
        assertTrue(switching.isSwitchingQuality)

        val failed = derivePlaybackState(
            previous = switching,
            truth = PlaybackTruth(phase = PlaybackPhase.Preparing),
            requests = PlaybackRequests(),
            load = PlaybackLoadStatus.Failed("switch failed"),
        )
        assertFalse(failed.isSwitchingQuality, "切档失败后不得卡在 true")
        assertEquals(PlaybackPhase.Error, failed.phase)

        val backendErrored = derivePlaybackState(
            previous = switching,
            truth = PlaybackTruth(phase = PlaybackPhase.Error, errorMessage = "x"),
            requests = PlaybackRequests(),
            load = PlaybackLoadStatus.Opening(isQualitySwitch = true),
        )
        assertFalse(backendErrored.isSwitchingQuality, "后端落到 Error 后不得卡在 true")

        val finished = derivePlaybackState(
            previous = switching,
            truth = PlaybackTruth(phase = PlaybackPhase.Ready, videoWidth = 1280, videoHeight = 720),
            requests = PlaybackRequests(),
            load = PlaybackLoadStatus.Opening(isQualitySwitch = true),
        )
        assertFalse(finished.isSwitchingQuality, "后端到达 Ready 后不得卡在 true")
        assertEquals(PlaybackPhase.Ready, finished.phase)
    }
}
