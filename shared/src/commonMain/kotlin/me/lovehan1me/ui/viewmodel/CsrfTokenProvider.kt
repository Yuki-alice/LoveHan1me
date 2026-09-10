package me.lovehan1me.ui.viewmodel

/**
 * csrfToken 全局唯一持有者（P4b：AppViewModel 依赖 WorkManager 留在 :app，
 * 但多数 VM 都引用其 csrfToken 全局——拆出本 holder 供 shared 内 VM 使用）。
 * :app 的 [me.lovehan1me.ui.viewmodel.AppViewModel] 委托给本对象，
 * 保证"首页/视频页刷新 csrfToken"与各 VM 读到的是同一份。
 */
object CsrfTokenProvider : IHCsrfToken {
    override var csrfToken: String? = null
}
