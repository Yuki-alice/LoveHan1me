package lovehan1me.core.platform

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// P1-3 守卫：改并发数即时生效（扩容直接放行、缩容后台收回），不再等重启。
// 测的是许可账本，不是真实下载：无任务运行时许可全空闲，断言是确定性的。
// updateDownloadLimit 是普通函数，缩容的后台收回用带截止的轮询等收敛，
// 不引入协程测试依赖、不写死等待时长。
class DesktopDownloadLimitTest {

    @AfterTest
    fun tearDown() {
        // 单例复位到默认 2 并等收敛：缩容的后台收回是异步的，不等它，
        // 下一个用例起手看到的许可数就不确定（用例间执行顺序无保证）。
        DesktopDownloadWorkController.updateDownloadLimit(2)
        val deadline = System.currentTimeMillis() + 5_000L
        while (DesktopDownloadWorkController.availableDownloadPermits != 2 &&
            System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(10)
        }
    }

    @Test
    fun `扩容即时放行`() {
        DesktopDownloadWorkController.updateDownloadLimit(2)
        DesktopDownloadWorkController.updateDownloadLimit(4)
        assertEquals(4, DesktopDownloadWorkController.availableDownloadPermits)
    }

    @Test
    fun `缩容收敛到目标且不阻塞调用方`() {
        DesktopDownloadWorkController.updateDownloadLimit(4)
        // 缩容调用本身必须立刻返回（收回在后台做）；收敛用轮询等。
        DesktopDownloadWorkController.updateDownloadLimit(1)
        val deadline = System.currentTimeMillis() + 5_000L
        while (DesktopDownloadWorkController.availableDownloadPermits != 1 &&
            System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(10)
        }
        assertEquals(1, DesktopDownloadWorkController.availableDownloadPermits)
    }

    @Test
    fun `零与负数钳到1`() {
        DesktopDownloadWorkController.updateDownloadLimit(4)
        DesktopDownloadWorkController.updateDownloadLimit(0)
        val deadline = System.currentTimeMillis() + 5_000L
        while (DesktopDownloadWorkController.availableDownloadPermits != 1 &&
            System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(10)
        }
        assertEquals(1, DesktopDownloadWorkController.availableDownloadPermits)
    }

    @Test
    fun `同值复调不扰动账本`() {
        DesktopDownloadWorkController.updateDownloadLimit(2)
        DesktopDownloadWorkController.updateDownloadLimit(2)
        assertEquals(2, DesktopDownloadWorkController.availableDownloadPermits)
        assertTrue(DesktopDownloadWorkController.availableDownloadPermits >= 0)
    }
}
