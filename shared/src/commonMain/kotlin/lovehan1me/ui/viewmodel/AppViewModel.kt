package lovehan1me.ui.viewmodel

import lovehan1me.core.util.LogUtil
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import lovehan1me.logic.ioDispatcher
import lovehan1me.logic.platform.downloadWorkController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * @project Han1meViewer
 * @author Yenaly Liew
 * @time 2024/03/29 029 18:00
 */
object AppViewModel : ViewModel(), IHCsrfToken {

    // P6c：P6b-F 已把 DownloadWorkController 工厂 expect 化（android=:app provider 注册）
    private val controller = downloadWorkController()

    /**
     * csrfToken 全局唯一，只需要在首页拉起或点击视频页时更新一下就可以了。
     * P4b：委托给共享层 [CsrfTokenProvider]（本 object 因 WorkManager 依赖留 :app）。
     */
    override var csrfToken: String?
        get() = CsrfTokenProvider.csrfToken
        set(value) { CsrfTokenProvider.csrfToken = value }

    val runningWorkInfoCountFlow = MutableStateFlow(0)

    init {
        // 取消，防止每次启动都有残留的更新任务
        controller.prune()

        viewModelScope.launch(ioDispatcher) {
            // HanimeDownloadManager.init()
            controller.initialize()
        }

        viewModelScope.launch(ioDispatcher) {
            controller.runningCount().collect { count ->
                // P6c：原 HanimeDownloadWorker.TAG（:app worker）不可见，日志 tag 用字面量保持一致
                LogUtil.d("HanimeDownloadWorker", "getRunningWorkInfoCount: $count")
                runningWorkInfoCountFlow.value = count
            }
        }
    }
}
