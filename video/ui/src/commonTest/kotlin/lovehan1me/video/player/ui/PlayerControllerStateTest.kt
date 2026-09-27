/*
 * 本文件实现移植自 Animeko (https://github.com/open-ani/animeko)，
 * 受 GNU AGPLv3 许可证约束，详见仓库根目录 LICENSE 与 NOTICE。
 */

package lovehan1me.video.player.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerControllerStateTest {
    @Test
    fun `text input preference returns to player when its owner is released`() {
        val state = PlayerFocusState()
        val owner = Any()
        assertEquals(PlayerFocusTarget.PLAYER, state.preferredTarget)

        state.preferTextInput(owner)
        assertEquals(PlayerFocusTarget.TEXT_INPUT, state.preferredTarget)

        state.releaseTextInput(Any())
        assertEquals(PlayerFocusTarget.TEXT_INPUT, state.preferredTarget)

        state.releaseTextInput(owner)
        assertEquals(PlayerFocusTarget.PLAYER, state.preferredTarget)
    }

    @Test
    fun `toggle visibility`() {
        val state = PlayerControllerState(ControllerVisibility.Visible)
        state.toggleFullVisible()
        assertEquals(ControllerVisibility.Invisible, state.visibility)
        state.toggleFullVisible()
        assertEquals(ControllerVisibility.Visible, state.visibility)
        state.toggleFullVisible(true)
        assertEquals(ControllerVisibility.Visible, state.visibility)
        state.toggleFullVisible(false)
        assertEquals(ControllerVisibility.Invisible, state.visibility)
    }

    @Test
    fun `show detached slider when init invisible`() {
        val state = PlayerControllerState(ControllerVisibility.Invisible)
        val requester = Any()
        state.setRequestProgressBar(requester)
        assertEquals(ControllerVisibility.DetachedSliderOnly, state.visibility)
        state.cancelRequestProgressBarVisible(requester)
        assertEquals(ControllerVisibility.Invisible, state.visibility)
    }

    @Test
    fun `do not show detached slider when init visible`() {
        val state = PlayerControllerState(ControllerVisibility.Visible)
        val requester = Any()
        state.setRequestProgressBar(requester)
        assertEquals(ControllerVisibility.Visible, state.visibility)
        state.cancelRequestProgressBarVisible(requester)
        assertEquals(ControllerVisibility.Visible, state.visibility)
    }

    @Test
    fun `inline slider request keeps bottom bar while hiding other controls`() {
        val state = PlayerControllerState(ControllerVisibility.Visible)
        val requester = Any()

        state.setRequestInlineProgressSlider(requester)
        assertEquals(ControllerVisibility.InlineSliderOnly, state.visibility)

        state.cancelRequestInlineProgressSlider(requester)
        assertEquals(ControllerVisibility.Visible, state.visibility)
    }

    @Test
    fun `inline slider request wins over a detached slider request`() {
        val state = PlayerControllerState(ControllerVisibility.Invisible)
        val detached = Any()
        val inline = Any()
        state.setRequestProgressBar(detached)
        state.setRequestInlineProgressSlider(inline)
        assertEquals(ControllerVisibility.InlineSliderOnly, state.visibility)

        state.cancelRequestInlineProgressSlider(inline)
        assertEquals(ControllerVisibility.DetachedSliderOnly, state.visibility)
    }

    @Test
    fun `visibility when nothing`() {
        val state = createStateRequested(false, false, false)
        assertEquals(ControllerVisibility.Invisible, state.visibility)
    }

    @Test
    fun `visibility when alwaysOn`() {
        val state = createStateRequested(true, false, false)
        assertEquals(ControllerVisibility.Visible, state.visibility)
    }

    @Test
    fun `visibility when progressBarVisible`() {
        val state = createStateRequested(false, true, false)
        assertEquals(ControllerVisibility.DetachedSliderOnly, state.visibility)
    }

    @Test
    fun `visibility when fullVisible`() {
        val state = createStateRequested(false, false, true)
        assertEquals(ControllerVisibility.Visible, state.visibility)
    }

    @Test
    fun `visibility when alwaysOn progressBarVisible`() {
        val state = createStateRequested(true, true, false)
        assertEquals(ControllerVisibility.Visible, state.visibility)
    }

    @Test
    fun `visibility when alwaysOn fullVisible`() {
        val state = createStateRequested(true, false, true)
        assertEquals(ControllerVisibility.Visible, state.visibility)
    }

    @Test
    fun `visibility when progressBarVisible fullVisible`() {
        val state = createStateRequested(false, true, true)
        assertEquals(ControllerVisibility.Visible, state.visibility)
    }

    @Test
    fun `visibility when alwaysOn progressBarVisible fullVisible`() {
        val state = createStateRequested(true, true, true)
        assertEquals(ControllerVisibility.Visible, state.visibility)
    }

    @Test
    fun `a requester cancels only its own request`() {
        val state = PlayerControllerState(ControllerVisibility.Invisible)
        val holder = Any()
        val other = Any()
        state.setRequestAlwaysOn(holder, true)
        state.setRequestAlwaysOn(other, true)

        state.setRequestAlwaysOn(holder, false)
        assertEquals(ControllerVisibility.Visible, state.visibility)

        state.setRequestAlwaysOn(other, false)
        assertEquals(ControllerVisibility.Invisible, state.visibility)
    }

    private fun createStateRequested(
        alwaysOn: Boolean,
        progressBarVisible: Boolean,
        fullVisible: Boolean,
    ): PlayerControllerState {
        val state = PlayerControllerState(ControllerVisibility.Invisible)
        val requester = Any()
        state.setRequestAlwaysOn(requester, alwaysOn)
        if (progressBarVisible) state.setRequestProgressBar(requester)
        state.toggleFullVisible(fullVisible)
        return state
    }
}
