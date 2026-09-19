package lovehan1me.feature.video

import lovehan1me.core.util.LogUtil
import androidx.annotation.IntDef
import lovehan1me.core.domain.model.VideoComments

/**
 * 连通预览页与预览评论页的评论预取器。
 */
class PreviewCommentPrefetcher private constructor(
    private val commentViewModel: CommentViewModel
) {

    @IntDef(flag = true, value = [Scope.PREVIEW_ACTIVITY, Scope.PREVIEW_COMMENT_ACTIVITY])
    annotation class Scope {
        companion object {
            const val PREVIEW_ACTIVITY = 1
            const val PREVIEW_COMMENT_ACTIVITY = 1 shl 1
        }
    }

    companion object {
        private const val TAG = "PreviewCommentPrefetcher"

        private var prefetcher: PreviewCommentPrefetcher? = null

        fun here(viewModel: CommentViewModel): PreviewCommentPrefetcher {
            return prefetcher ?: PreviewCommentPrefetcher(viewModel).also { prefetcher = it }
        }

        fun bye(@Scope scope: Int) {
            prefetcher?.also {
                it.activityMask = it.activityMask and scope.inv()
                if (it.activityMask == 0) {
                    prefetcher = null
                    LogUtil.i(TAG, "bye executed successfully")
                } else {
                    if (it.activityMask and Scope.PREVIEW_ACTIVITY != 0) {
                        LogUtil.i(
                            TAG, "bye executed failed: " +
                                    "prefetcher is still alive cuz of PreviewActivity"
                        )
                    }
                    if (it.activityMask and Scope.PREVIEW_COMMENT_ACTIVITY != 0) {
                        LogUtil.i(
                            TAG, "bye executed failed: " +
                                    "prefetcher is still alive cuz of PreviewCommentActivity"
                        )
                    }
                }
            }
        }

        /**
         * 无条件丢弃当前 prefetcher，不理会 [Scope] 计数。
         *
         * 与 [bye] 的区别：[bye] 是"某个界面退出了"，按位清标记、还有人用就留着；
         * 本方法是"这份 prefetcher 整个作废"，因为**它内部持有旧代的 `CommentViewModel`**，
         * 切站后那个 VM 已在旧 `ViewModelStoreOwner` 里被 `clear()`，继续复用会往一个
         * 已死的 VM 里塞新站评论。
         *
         * 调用时机：数据源热切换（见 `SiteSwitcher`）。
         */
        fun reset() {
            prefetcher = null
            LogUtil.i(TAG, "reset executed successfully")
        }
    }

    private var activityMask = 0

    val commentFlow get() = commentViewModel.videoCommentFlow

    fun tag(@Scope scope: Int) {
        activityMask = activityMask or scope
    }

    fun fetch(type: String, code: String) {
        commentViewModel.getComment(type, code)
    }

    fun update(comments: List<VideoComments.VideoComment>) {
        commentViewModel.updateComments(comments)
    }
}
