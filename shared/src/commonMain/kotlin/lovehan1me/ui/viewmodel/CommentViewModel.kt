package lovehan1me.ui.viewmodel

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
import lovehan1me.ui.screen.video.CommentSortType
import lovehan1me.ui.viewmodel.CsrfTokenProvider.csrfToken
import lovehan1me.core.util.SonnerToast
import lovehan1me.core.util.decodeComposeAsset
import lovehan1me.core.util.unsafeLazy
import org.jetbrains.compose.resources.getString
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * @project Han1meViewer
 * @author Yenaly Liew
 * @time 2022/06/28 028 14:18
 */
class CommentViewModel : ViewModel() {

    data class CommentUiState(
        val firstVisibleItemIndex: Int = 0,
        val firstVisibleItemScrollOffset: Int = 0,
        val childCommentId: String? = null,
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

    private val _videoReplyStateFlow =
        MutableStateFlow<WebsiteState<VideoComments>>(WebsiteState.Loading)
    val videoReplyStateFlow = _videoReplyStateFlow.asStateFlow()

    private val _videoCommentFlow = MutableStateFlow(emptyList<VideoComments.VideoComment>())
    val videoCommentFlow = _videoCommentFlow.asStateFlow()

    private val _videoReplyFlow = MutableStateFlow(emptyList<VideoComments.VideoComment>())
    val videoReplyFlow = _videoReplyFlow.asStateFlow()

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

    fun setChildCommentId(code: String, childCommentId: String?) {
        val current = commentUiStateMap[code] ?: CommentUiState()
        commentUiStateMap[code] = current.copy(childCommentId = childCommentId)
    }

    fun clearCommentData(){
        _videoCommentFlow.value = emptyList()
    }
    fun getComment(type: String, code: String) {
        viewModelScope.launch {
            _videoCommentStateFlow.value = WebsiteState.Loading
            NetworkRepo.getComments(type, code).collect { state ->
                _videoCommentStateFlow.value = state
                _videoCommentFlow.update { prevList ->
                    when (state) {
                        is WebsiteState.Success -> state.info.videoComment
                        is WebsiteState.Loading -> emptyList()
                        else -> prevList
                    }
                }
            }
        }
    }

    fun updateComments(comments: List<VideoComments.VideoComment>) {
        _videoCommentFlow.update { comments }
    }

    fun getCommentReply(commentId: String) {
        viewModelScope.launch {
            // 每次获取评论回复时，都会重新加载
            _videoReplyStateFlow.value = WebsiteState.Loading
            NetworkRepo.getCommentReply(commentId).collect { state ->
                _videoReplyStateFlow.value = state
                _videoReplyFlow.update { prevList ->
                    when (state) {
                        is WebsiteState.Success -> state.info.videoComment
                        is WebsiteState.Loading -> emptyList()
                        else -> prevList
                    }
                }
            }
        }
    }

    fun postComment(
        currentUserId: String,
        targetUserId: String,
        type: String,
        text: String,
    ) {
        viewModelScope.launch {
            NetworkRepo.postComment(csrfToken, currentUserId, targetUserId, type, text)
                .collect(_postCommentFlow::emit)
        }
    }

    fun postReply(
        replyCommentId: String,
        text: String,
    ) {
        viewModelScope.launch {
            NetworkRepo.postCommentReply(csrfToken, replyCommentId, text)
                .collect(_postReplyFlow::emit)
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
        viewModelScope.launch {
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

                        CommentPlace.CHILD_COMMENT -> _videoReplyFlow.update { prevList ->
                            prevList.map { item ->
                                if (item.reportableId == comment.reportableId){
                                    item.handleCommentLike(argState.info)
                                } else {
                                    item
                                }
//                            prevList.toMutableList().apply {
//                                this[commentPosition] =
//                                    this[commentPosition].handleCommentLike(argState.info)
//                            }
                            }
                        }
                    }
                }
            }
        }
    }

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
                SonnerToast.success(getString(Res.string.cancel_thumb_up_success))
            } else {
                SonnerToast.success(getString(Res.string.thumb_up_success))
            }
        } else {
            if (args.comment.post.unlikeCommentStatus) {
                SonnerToast.success(getString(Res.string.cancel_thumb_down_success))
            } else {
                SonnerToast.success(getString(Res.string.thumb_down_success))
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
        viewModelScope.launch {
            LogUtil.i("ReportComment", "csrfToken:${csrfToken}")
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
    fun clearVideoReplyList() { _videoReplyFlow.value = emptyList() }
}
