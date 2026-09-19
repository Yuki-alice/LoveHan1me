package lovehan1me.feature.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import lovehan1me.Res
import lovehan1me.ic_panel_close
import lovehan1me.ic_panel_open
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import lovehan1me.ui.preview.HanimePreviewTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * 播放器顶栏/底栏的离屏渲染（`ImageComposeScene`，无窗口）：
 * 把宽屏 expanded 形态按真实播放器区域 1:1 摆出来，肉眼确认行结构。
 *
 * 跑法：`:shared:desktopTest --tests "lovehan1me.feature.video.PlayerBarRenderTest" --offline`
 * 产物：shared/build/playerbar-renders 目录下的 PNG（测试日志里会打印绝对路径）。
 */
class PlayerBarRenderTest {

    private fun renderScene(
        name: String,
        widthPx: Int,
        heightPx: Int,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ): File {
        val scene = ImageComposeScene(
            width = widthPx,
            height = heightPx,
            density = Density(2f),
            content = {
                HanimePreviewTheme(modifier = Modifier.fillMaxSize()) {
                    content()
                }
            },
        )
        try {
            // 多渲几帧，让 AnimatedVisibility 的 enter 动画走完，抓稳态。
            scene.render(0L)
            scene.render(500_000_000L)
            val image = scene.render(1_000_000_000L)
            val data = image.encodeToData(EncodedImageFormat.PNG)
                ?: error("PNG 编码失败：$name")
            val outDir = File("build/playerbar-renders").also { it.mkdirs() }
            val out = File(outDir, "$name.png")
            out.writeBytes(data.bytes)
            println("PLAYERBAR_RENDER_OUT: ${out.absolutePath}")
            return out
        } finally {
            scene.close()
        }
    }

    @Test
    fun `宽屏expanded_顶栏加底栏同屏`() {
        renderScene("wide-expanded", 1600, 1000) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                PlayerTopBar(
                    visible = true,
                    isFullscreen = false,
                    title = "[Lorely] 015-Yvonne + bonus animation",
                    deviceTime = "12:00",
                    frameCaptureEnabled = true,
                    onCaptureScreenshot = {},
                    onOpenGifCapture = {},
                    onBackClick = {},
                    showSidebarToggle = true,
                    sidebarVisible = false,
                    onToggleSidebar = {},
                    expanded = true,
                )
                PlayerBottomBar(
                    visible = true,
                    sliderValue = 0f,
                    bufferedProgress = 0f,
                    onSliderValueChange = {},
                    onSliderValueChangeFinished = {},
                    isPlaying = false,
                    onPlayClick = {},
                    currentTime = "00:00",
                    totalTime = "04:47",
                    playbackSpeed = 1f,
                    resolvedQualityLabel = "480P",
                    fullscreenEnabled = true,
                    onFullscreenClick = {},
                    onPlaybackSpeedSelected = {},
                    superResolutionOptions = listOf("关闭", "效率档", "质量档"),
                    selectedSuperResolutionIndex = 0,
                    onSuperResolutionSelected = {},
                    onNextClick = {},
                    isFullscreen = false,
                    durationMs = 287_000L,
                    expanded = true,
                )
            }
        }
    }

    @Test
    fun `窄屏非expanded_底栏`() {
        renderScene("narrow-collapsed", 780, 1400) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                PlayerTopBar(
                    visible = true,
                    isFullscreen = false,
                    title = "[Lorely] 015-Yvonne + bonus animation",
                    deviceTime = "12:00",
                    frameCaptureEnabled = false,
                    onCaptureScreenshot = null,
                    onOpenGifCapture = null,
                    onBackClick = {},
                    expanded = false,
                )
                PlayerBottomBar(
                    visible = true,
                    sliderValue = 0.19f,
                    bufferedProgress = 0.3f,
                    onSliderValueChange = {},
                    onSliderValueChangeFinished = {},
                    isPlaying = true,
                    onPlayClick = {},
                    currentTime = "00:55",
                    totalTime = "04:47",
                    playbackSpeed = 1f,
                    resolvedQualityLabel = "480P",
                    fullscreenEnabled = true,
                    onFullscreenClick = {},
                    onPlaybackSpeedSelected = {},
                    superResolutionOptions = listOf("关闭", "效率档", "质量档"),
                    selectedSuperResolutionIndex = 0,
                    onSuperResolutionSelected = {},
                    onNextClick = null,
                    durationMs = 287_000L,
                    expanded = false,
                )
            }
        }
    }

    @Test
    fun `功能弹窗卡片`() {
        renderScene("menu-popup", 1600, 1000) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                // 贴在给定位置上方、右对齐（锚点即 Popup 父布局，见源码注释）。
                KazumiMenuPopup(
                    above = true,
                    onDismiss = {},
                ) {
                    KazumiMenuOption("关闭", true, {})
                    KazumiMenuOption("效率档", false, {})
                    KazumiMenuOption("质量档", false, {})
                }
            }
        }
    }

    @Test
    fun `音量pill`() {
        renderScene("volume-pill", 1600, 400) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                VolumePill(
                    visible = true,
                    volume = 0.8f,
                    onVolumeChange = {},
                    onToggleMute = {},
                )
            }
        }
    }

    @Test
    fun `右侧悬浮钮加面板图标`() {
        renderScene("rhs-and-panel", 800, 800) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                PlayerGestureLockButton(
                    isLocked = false,
                    showUnlockButton = false,
                    playerUiVisible = true,
                    onLockClick = {},
                    showScreenshotButton = true,
                    onScreenshotClick = {},
                )
                // 面板折叠图标放大检视（左=收起态，右=展开态）。
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.align(Alignment.BottomStart)
                        .padding(24.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(
                        24.dp
                    ),
                ) {
                    androidx.compose.foundation.Image(
                        painter = org.jetbrains.compose.resources.painterResource(
                            lovehan1me.Res.drawable.ic_panel_close
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(96.dp),
                    )
                    androidx.compose.foundation.Image(
                        painter = org.jetbrains.compose.resources.painterResource(
                            lovehan1me.Res.drawable.ic_panel_open
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(96.dp),
                    )
                }
            }
        }
    }
}
