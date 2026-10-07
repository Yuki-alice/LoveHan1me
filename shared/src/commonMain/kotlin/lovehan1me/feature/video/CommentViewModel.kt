package lovehan1me.feature.video

import lovehan1me.core.util.LogUtil
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModel
import lovehan1me.Res
import lovehan1me.cancel_thumb_down_success
import lovehan1me.cancel_thumb_up_success
import lovehan1me.data.NetworkRepo
import lovehan1me.core.domain.model.CommentPlace
import lovehan1me.core.domain.model.ReportReason
import lovehan1me.core.domain.model.VideoCommentArgs
import lovehan1me.core.domain.model.VideoComments
import lovehan1me.core.domain.state.WebsiteState
import lovehan1me.report_failed
import lovehan1me.report_success
import lovehan1me.thumb_down_success
import lovehan1me.thumb_up_success
import lovehan1me.feature.video.CommentSortType
import lovehan1me.data.network.CsrfTokenProvider.csrfToken
import lovehan1me.core.util.AppToast
import lovehan1me.core.util.decodeComposeAsset
import lovehan1me.core.util.unsafeLazy
import lovehan1me.ui.foundation.launchSafely
import org.jetbrains.compose.resources.getString
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 一条父评论下面展开出来的回覆。站点用 `loadReplies?id=` 单独给，所以按父评论 id 各自缓存，
 * 收起时留着、再点开不重取。
 */
data class ReplyThread(
    val loading: Boolean = false,
    val error: Throwable? = null,
    val items: List<VideoComments.VideoComment> = emptyList(),
)

/**
 * @project LoveHan1me
 * @author Yenaly Liew（上游原作者，见 NOTICE）
 * @time 2022/06/28 028 14:18
 */
class CommentViewModel : ViewModel() {

    data class CommentUiState(
        val firstVisibleItemIndex: Int = 0,
        val firstVisibleItemScrollOffset: Int = 0,
    )

    lateinit var code: String

    private val commentUiStateMap = mutableMapOf<String, CommentUiState>()

    var currentUserId: String? = null
    //reportMessage为点击举报按钮之后的响应及错误信息
    private val _reportMessage = MutableSharedFlow<Message>()
    val reportMessage = _reportMessage.asSharedFlow()
    // P6c：原为 (resId: Int=R.string, args)；commonMain 无 R，改为 suspend 内 getString 后直接携带文本
    data class Message(val text: String)

    private val _videoCommentStateFlow =
        MutableStateFlow<WebsiteState<VideoComments>>(WebsiteState.Loading)
    val videoCommentStateFlow = _videoCommentStateFlow.asStateFlow()

    private val _replyThreads = MutableStateFlow(emptyMap<String, ReplyThread>())
    val replyThreads = _replyThreads.asStateFlow()

    private val _videoCommentFlow = MutableStateFlow(emptyList<VideoComments.VideoComment>())
    val videoCommentFlow = _videoCommentFlow.asStateFlow()

    private val _postCommentFlow =
        MutableSharedFlow<WebsiteState<Unit>>(replay = 0)
    val postCommentFlow = _postCommentFlow.asSharedFlow()

    private val _postReplyFlow =
        MutableSharedFlow<WebsiteState<Unit>>(replay = 0)
    val postReplyFlow = _postReplyFlow.asSharedFlow()

    private val _commentLikeFlow =
        MutableSharedFlow<WebsiteState<VideoCommentArgs>>(replay = 0)
    val commentLikeFlow = _commentLikeFlow.asSharedFlow()
    val reportReason by unsafeLazy {
        // P6c：loadAssetAs → decodeComposeAsset（同步；P6a-B 把 report_reason.json 放进 files/）
        decodeComposeAsset<List<ReportReason>>("files/report_reason.json").orEmpty()
    }

    private val _currentSortType = MutableStateFlow(CommentSortType.LATEST)
    val currentSortType = _currentSortType.asStateFlow()
    fun setSortType(type: CommentSortType) {
        _currentSortType.value = type
    }

    fun getCommentUiState(code: String): CommentUiState {
        return commentUiStateMap[code] ?: CommentUiState()
    }

    fun setCommentScrollState(
        code: String,
        firstVisibleItemIndex: Int,
        firstVisibleItemScrollOffset: Int,
    ) {
        val current = commentUiStateMap[code] ?: CommentUiState()
        commentUiStateMap[code] = current.copy(
            firstVisibleItemIndex = firstVisibleItemIndex,
            firstVisibleItemScrollOffset = firstVisibleItemScrollOffset,
        )
    }

    fun clearCommentData(){
        commentsLoadedFor = null
        _videoCommentFlow.value = emptyList()
        _replyThreads.value = emptyMap()
    }

    /** `_videoCommentFlow` 里那批评论属于哪个 id；换 id 要清空，同 id 刷新要留着。 */
    private var commentsLoadedFor: String? = null

    /**
     * 有缓存就用缓存：同一部片子（`code` 一致）且上次成功时直接返回，
     * 播放器页（弹幕主源预热）与评论 Tab 共享一次 `loadComment`。
     * 下拉刷新与点赞等写操作走各自的原方法，不受影响。
     */
    fun ensureComments(type: String, code: String) {
        if (::code.isInitialized && this.code == code &&
            _videoCommentStateFlow.value is WebsiteState.Success
        ) {
            return
        }
        this.code = code
        getComment(type, code)
    }

    fun getComment(type: String, code: String) {
        launchSafely("CommentViewModel.getComment") {
            if (commentsLoadedFor != code) {
                commentsLoadedFor = code
                _videoCommentFlow.value = emptyList()
                // 换了一部片子，上一部展开出来的回覆线程没有意义了。
                _replyThreads.value = emptyMap()
            }
            _videoCommentStateFlow.value = WebsiteState.Loading
            NetworkRepo.getComments(type, code).collect { state ->
                // 已经切去看别的 id 了，迟到的响应就别再写进列表。
                if (commentsLoadedFor != code) return@collect
                _videoCommentStateFlow.value = state
                // Loading 与 Error 都保持原列表：刷新失败时用户至少还能看到上一次的结果，
                // 而不是被清空成"暂无评论"。
                if (state is WebsiteState.Success) {
                    _videoCommentFlow.value = state.info.videoComment
                }
            }
        }
    }

    fun updateComments(code: String, comments: List<VideoComments.VideoComment>) {
        commentsLoadedFor = code
        _videoCommentFlow.update { comments }
    }

    /**
     * 拉某条父评论下面的回覆。已经拉到过就复用缓存（收起再点开不该重取），
     * 发完回覆要看见新那条，所以留 [force]。
     */
    fun loadReplies(commentId: String, force: Boolean = false) {
        if (!force && _replyThreads.value[commentId]?.items?.isNotEmpty() == true) return
        launchSafely("CommentViewModel.loadReplies") {
            _replyThreads.update { it + (commentId to threadOf(it, commentId).copy(loading = true, error = null)) }
            NetworkRepo.getCommentReply(commentId).collect { state ->
                when (state) {
                    is WebsiteState.Success -> _replyThreads.update {
                        it + (commentId to ReplyThread(items = state.info.videoComment))
                    }

                    is WebsiteState.Error -> _replyThreads.update {
                        it + (commentId to threadOf(it, commentId).copy(loading = false, error = state.throwable))
                    }

                    WebsiteState.Loading -> Unit
                }
            }
        }
    }

    private fun threadOf(map: Map<String, ReplyThread>, commentId: String): ReplyThread =
        map[commentId] ?: ReplyThread()

    /**
     * 刚发出去的那条回覆挂在哪个父评论下：直接命中线程 key（回覆的是父评论本身），
     * 否则在各线程里找被回覆的那条子评论（回覆的是某条子评论）。
     */
    private fun threadOwning(replyTargetId: String): String? {
        val threads = _replyThreads.value
        if (threads.containsKey(replyTargetId)) return replyTargetId
        return threads.entries.firstOrNull { (_, thread) ->
            thread.items.any { it.replyTargetIdOrNull == replyTargetId }
        }?.key
    }

    fun postComment(
        currentUserId: String,
        targetUserId: String,
        type: String,
        text: String,
    ) {
        launchSafely("CommentViewModel.postComment") {
            NetworkRepo.postComment(csrfToken, currentUserId, targetUserId, type, text)
                .collect(_postCommentFlow::emit)
        }
    }

    fun postReply(
        replyCommentId: String,
        text: String,
    ) {
        launchSafely("CommentViewModel.postReply") {
            NetworkRepo.postCommentReply(csrfToken, replyCommentId, text)
                .collect { state ->
                    if (state is WebsiteState.Success) {
                        // 展开着的那层要立刻多出刚发的那条；没展开过就不用管。
                        threadOwning(replyCommentId)?.let { loadReplies(it, force = true) }
                    }
                    _postReplyFlow.emit(state)
                }
        }
    }

    fun likeComment(
        isPositive: Boolean, commentPosition: Int,
        comment: VideoComments.VideoComment, likeCommentStatus: Boolean = false,
        unlikeCommentStatus: Boolean = false,
    ) = likeCommentInternal(
        CommentPlace.COMMENT, isPositive, commentPosition,
        comment, likeCommentStatus, unlikeCommentStatus
    )

    fun likeChildComment(
        isPositive: Boolean, commentPosition: Int,
        comment: VideoComments.VideoComment, likeCommentStatus: Boolean = false,
        unlikeCommentStatus: Boolean = false,
    ) = likeCommentInternal(
        CommentPlace.CHILD_COMMENT, isPositive, commentPosition,
        comment, likeCommentStatus, unlikeCommentStatus
    )

    private fun likeCommentInternal(
        commentPlace: CommentPlace,
        isPositive: Boolean,
        commentPosition: Int,
        comment: VideoComments.VideoComment,
        likeCommentStatus: Boolean = false,
        unlikeCommentStatus: Boolean = false,
    ) {
        launchSafely("CommentViewModel.likeCommentInternal") {
            NetworkRepo.likeComment(
                csrfToken,
                commentPlace,
                comment.post.foreignId,
                isPositive,
                comment.post.likeUserId,
                comment.post.commentLikesCount ?: 0,
                comment.post.commentLikesSum ?: 0,
                likeCommentStatus,
                unlikeCommentStatus,
                commentPosition, comment
            ).collect { argState ->
                _commentLikeFlow.emit(argState)
                if (argState is WebsiteState.Success) {
                    when (commentPlace) {
                        CommentPlace.COMMENT -> _videoCommentFlow.update { prevList ->
                            prevList.map { item ->
                                if (item.reportableId == comment.reportableId){
                                    item.handleCommentLike(argState.info)
                                } else {
                                    item
                                }
                            }
//                            prevList.toMutableList().apply {
//                                this[commentPosition] =
//                                    this[commentPosition].handleCommentLike(argState.info)
//                            }
                        }

                        CommentPlace.CHILD_COMMENT -> _replyThreads.update { threads ->
                            val entry = threads.entries.firstOrNull { (_, thread) ->
                                thread.items.any { isSameComment(it, comment) }
                            }
                            if (entry == null) threads else {
                                val (parentId, thread) = entry
                                threads + (parentId to thread.copy(
                                    items = thread.items.map { item ->
                                        if (isSameComment(item, comment)) {
                                            item.handleCommentLike(argState.info)
                                        } else {
                                            item
                                        }
                                    }
                                ))
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 点赞点的是列表里那个实例，先按实例认；认不出再退到 `reportableId`。
     * 只按 `reportableId` 比会在未登录（该字段为 null）时把所有条目都当成同一条。
     */
    private fun isSameComment(a: VideoComments.VideoComment, b: VideoComments.VideoComment): Boolean =
        a === b || (a.reportableId != null && a.reportableId == b.reportableId)

    private fun VideoComments.VideoComment.handleCommentLike(
        args: VideoCommentArgs,
    ) = if (args.isPositive) {
        this.incLikesCount(cancel = post.likeCommentStatus)
    } else {
        this.decLikesCount(cancel = post.unlikeCommentStatus)
    }

    // P6c：suspend（调用方在 commentLikeFlow.collect 内，天然 suspend）；文案走 compose getString
    suspend fun handleCommentLike(args: VideoCommentArgs) {
        if (args.isPositive) {
            if (args.comment.post.likeCommentStatus) {
                AppToast.success(getString(Res.string.cancel_thumb_up_success))
            } else {
                AppToast.success(getString(Res.string.thumb_up_success))
            }
        } else {
            if (args.comment.post.unlikeCommentStatus) {
                AppToast.success(getString(Res.string.cancel_thumb_down_success))
            } else {
                AppToast.success(getString(Res.string.thumb_down_success))
            }
        }
    }

    fun reportComment(
        reason: String,
        currentUserId: String?,
        redirectUrl: String,
        reportableType: String?,
        reportableId: String?
    ){
        launchSafely("CommentViewModel.reportComment") {
            // ⚠️ 不打 csrfToken 本身（它是凭据，且这行是 INFO 级，日志门槛拦不住）；
            // 只需知道"拿到没拿到"。
            LogUtil.d("ReportComment", "提交举报（csrfToken ${if (csrfToken.isNullOrBlank()) "缺失" else "已取到"}）")
            NetworkRepo.reportComment(
                csrfToken = csrfToken,
                reason = reason,
                currentUserId = currentUserId,
                redirectUrl = redirectUrl,
                reportableType = reportableType,
                reportableId = reportableId
            ).collect { state ->
                when(state){
                    is WebsiteState.Error -> {
                        val reason = state.throwable.message ?: "unknown"
                        _reportMessage.emit(
                            Message(getString(Res.string.report_failed, reason))
                        )
                    }
                    WebsiteState.Loading -> {

                    }
                    is WebsiteState.Success<*> -> {
                        _reportMessage.emit(Message(getString(Res.string.report_success)))
                    }
                }
            }
        }
    }
}
