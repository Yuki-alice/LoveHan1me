package lovehan1me.app.main

import androidx.activity.ComponentActivity
import lovehan1me.app.navigation.main.HanimeScreen
import lovehan1me.app.navigation.main.TopLevelBackStack

/**
 * G1-1B：`AndroidShell` / `rememberAndroidVideoPageHost` 下沉 shared 后，
 * 不能再引用 `:app` 的 `MainActivity`（shared 不依赖 :app）。这里抽出壳层真正需要的
 * 五个能力作为边界，`MainActivity` 实现它，shared 只认接口。
 *
 * [componentActivity] 单独暴露，是因为壳层里两处需要「Activity/Context」本身
 * （`coerceToText(context)` 与 `rememberAndroidVideoPageHost(activity)`），
 * 而这两处只用到 `ComponentActivity` 的能力，不需要 `MainActivity` 的其余部分。
 */
interface MainActivityHost {
    val componentActivity: ComponentActivity
    val mainBackStack: TopLevelBackStack<HanimeScreen>
    fun openLogin()
    fun showLogoutConfirmDialog(closeCurrentPageOnConfirm: Boolean = false)
    fun showVideoDetailFragment(videoCode: String, fileUri: String? = null)
}

/**
 * PiP 通知栏「播放/暂停」广播 action。
 *
 * 原为 `MainActivity.ACTION_TOGGLE_PLAY`；AndroidVideoPageHost（发送方，shared）
 * 与 MainActivity 的 pipActionReceiver（接收方，:app）都要引用，故提到 shared 顶层，
 * 字面量保持不变以保证跨进程内广播匹配。
 */
const val ACTION_TOGGLE_PLAY: String = "lovehan1me.ui.activity.ACTION_TOGGLE_PLAY"
