package io.github.daisukikaffuchino.han1meviewer.ui.viewmodel

import io.github.daisukikaffuchino.utils.LogUtil
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.daisukikaffuchino.han1meviewer.worker.HanimeDownloadWorker
import io.github.daisukikaffuchino.han1meviewer.logic.platform.AndroidDownloadWorkController
import io.github.daisukikaffuchino.han1meviewer.logic.platform.DownloadWorkController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * @project Han1meViewer
 * @author Yenaly Liew
 * @time 2024/03/29 029 18:00
 */
object AppViewModel : ViewModel(), IHCsrfToken {

    private val downloadWorkController: DownloadWorkController = AndroidDownloadWorkController

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
        downloadWorkController.prune()

        viewModelScope.launch(Dispatchers.IO) {
            // HanimeDownloadManager.init()
            downloadWorkController.initialize()
        }

        viewModelScope.launch(Dispatchers.IO) {
            downloadWorkController.runningCount().collect { count ->
                LogUtil.d(HanimeDownloadWorker.TAG, "getRunningWorkInfoCount: $count")
                runningWorkInfoCountFlow.value = count
            }
        }
    }
}
