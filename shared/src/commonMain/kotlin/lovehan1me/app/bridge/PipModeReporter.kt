package lovehan1me.app.bridge

/**
 * 阶段一⑨：画中画状态回传的平台上报口。
 *
 * 背景：系统侧的 PiP 状态变化（用户点 X 关闭/切回）发生在平台回调里
 * （Android=Activity.onPictureInPictureModeChanged，
 * iOS=AVPictureInPictureControllerDelegate），共享层需要据此同步
 * `VideoViewModel.setPipMode`（否则退后台会被当普通后台暂停掉）。
 * Android 走 Activity（`onRegisterPageHost` 注册的共享 pageHost），
 * iOS 无 Activity，由 [VideoRouteHostScreen] 把共享 pageHost 的
 * `onPipModeChanged` 挂到本监听器（见其 DisposableEffect）。
 */
interface PipModeReporter {
    var pipModeListener: ((Boolean) -> Unit)?
}
