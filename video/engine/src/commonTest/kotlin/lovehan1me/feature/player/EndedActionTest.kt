package lovehan1me.feature.player

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [resolveEndedAction] 的分支门禁。
 *
 * 钉住优先级：非 Ended 一律 Stay；循环优先于连播；
 * 连播仍要 hasNext（单片/尾集不触发）。
 */
class EndedActionTest {

    @Test
    fun `非Ended一律Stay`() {
        for (phase in PlaybackPhase.entries) {
            if (phase == PlaybackPhase.Ended) continue
            assertEquals(
                EndedAction.Stay,
                resolveEndedAction(phase, loopSingle = true, autoPlayNextEnabled = true, hasNext = true),
                "phase=$phase",
            )
        }
    }

    @Test
    fun `循环优先于连播`() {
        assertEquals(
            EndedAction.ReplayCurrent,
            resolveEndedAction(PlaybackPhase.Ended, loopSingle = true, autoPlayNextEnabled = true, hasNext = true),
        )
        // 单片开循环同样重播（连播本就只对系列有效）。
        assertEquals(
            EndedAction.ReplayCurrent,
            resolveEndedAction(PlaybackPhase.Ended, loopSingle = true, autoPlayNextEnabled = false, hasNext = false),
        )
    }

    @Test
    fun `连播要开关加后一项`() {
        assertEquals(
            EndedAction.AdvanceNext,
            resolveEndedAction(PlaybackPhase.Ended, loopSingle = false, autoPlayNextEnabled = true, hasNext = true),
        )
        assertEquals(
            EndedAction.Stay,
            resolveEndedAction(PlaybackPhase.Ended, loopSingle = false, autoPlayNextEnabled = false, hasNext = true),
        )
        assertEquals(
            EndedAction.Stay,
            resolveEndedAction(PlaybackPhase.Ended, loopSingle = false, autoPlayNextEnabled = true, hasNext = false),
        )
    }
}
