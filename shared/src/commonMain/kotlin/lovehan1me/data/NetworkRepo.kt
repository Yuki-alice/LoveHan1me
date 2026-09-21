package lovehan1me.data

import lovehan1me.core.util.LogUtil
import lovehan1me.core.constant.EMPTY_STRING
import lovehan1me.data.SettingsRepository
import lovehan1me.data.SettingsRepository.isAlreadyLogin
import lovehan1me.core.domain.exception.CloudflareBlockedException
import lovehan1me.core.domain.exception.HanimeNotFoundException
import lovehan1me.core.domain.exception.IPBlockedException
import lovehan1me.core.domain.exception.ParseException
import lovehan1me.core.domain.model.CommentPlace
import lovehan1me.core.domain.model.ModifiedPlaylistArgs
import lovehan1me.core.domain.model.MyListType
import lovehan1me.core.domain.model.OnlineWatchHistorySort
import lovehan1me.core.domain.model.VideoCommentArgs
import lovehan1me.core.domain.model.VideoComments
import lovehan1me.data.network.CloudflareChallenges
import lovehan1me.feature.video.PlayerTrace
import lovehan1me.data.network.HanimeNetwork
import lovehan1me.core.domain.state.PageLoadingState
import lovehan1me.core.domain.state.VideoLoadingState
import lovehan1me.core.domain.state.WebsiteState
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import lovehan1me.Res
import lovehan1me.account_or_password_wrong
import lovehan1me.cloudflare_ip_block_warning
import lovehan1me.cloudflare_network_mismatch
import lovehan1me.login_requires_cf_verification
import lovehan1me.not_logged_in_currently
import lovehan1me.parse_error_msg
import lovehan1me.ssl_handshake_error
import lovehan1me.video_might_not_exist
import org.jetbrains.compose.resources.getString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import lovehan1me.core.platform.ioDispatcher
import lovehan1me.core.platform.isSslHandshakeException
import lovehan1me.core.platform.sslHandshakeException
import lovehan1me.site.hanime1.Parser
import lovehan1me.site.hanime1.authorPage
import lovehan1me.site.hanime1.authorPlaylistsPage
import lovehan1me.site.hanime1.authorVideosPage
import lovehan1me.site.hanime1.sitePlaylistPage

/**
 * @project LoveHan1me
 * @author Yenaly Liew（上游原作者，见 NOTICE）
 * @time 2022/06/08 008 22:38
 *
 * P3：Ktor 化调用点适配（本体留在 :app）。service 返回类型由
 * Retrofit Response<ResponseBody> 改为 Ktor HttpResponse：
 *  - code()/isSuccessful/body()/errorBody() → status.value / status.isSuccess() / bodyAsText()
 *  - 判断结构保持不变（见各 flow 与 throwRequestException）
 */
object NetworkRepo {

    //<editor-fold desc="Hanime">

    fun getHomePage() = websiteIOFlow(
        request = { HanimeNetwork.hanimeService.getHomePage(SettingsRepository.homeUrl) },
        action = Parser::homePageVer2
    )

    fun getHanimeSearchResult(
        page: Int, query: String?, genre: String?,
        sort: String?, broad: Boolean, date: String?,
        duration: String?, tags: Set<String>, brands: Set<String>,
    ) = pageIOFlow(
        request = {
            HanimeNetwork.hanimeService.getHanimeSearchResult(
                page, query, genre, sort,
                if (broad) "on" else null,
                date, duration, tags, brands
            )
        },
        action = Parser::hanimeSearch
    )

    fun getHanimeVideo(videoCode: String) = videoIOFlow(
        request = { HanimeNetwork.hanimeService.getHanimeVideo(videoCode) },
        action = Parser::hanimeVideoVer2
    )

    fun getHanimePreview(date: String) = websiteIOFlow(
        request = { HanimeNetwork.hanimeService.getHanimePreview(date) },
        action = Parser::hanimePreview
    )

    //获取订阅或者可以说是关注列表及它们的更新
    fun getMySubscriptions(page: Int) = websiteIOFlow(
        request = { HanimeNetwork.hanimeService.getMySubscriptions(page) },
        action = Parser::getMySubscriptions
    )

    //<editor-fold desc="G2-1b-2 作者与系列清单">

    fun getArtistPage(userId: String) = websiteIOFlow(
        request = { HanimeNetwork.hanimeService.getArtistPage(userId) },
        action = { body -> with(Parser) { authorPage(userId, body) } }
    )

    fun getArtistUploaded(userId: String, page: Int) = pageIOFlow(
        request = { HanimeNetwork.hanimeService.getArtistUploaded(userId, page) },
        action = { body -> with(Parser) { authorVideosPage(body) } }
    )

    fun getAuthorPlaylists(userId: String, sort: String? = null) = pageIOFlow(
        request = { HanimeNetwork.hanimeService.getAuthorPlaylists(userId, sort) },
        action = { body -> with(Parser) { authorPlaylistsPage(body) } }
    )

    fun getSitePlaylist(listId: String, sort: String?) = websiteIOFlow(
        request = { HanimeNetwork.hanimeService.getSitePlaylist(listId, sort) },
        action = { body -> with(Parser) { sitePlaylistPage(listId, body) } }
    )

    //</editor-fold>
    //</editor-fold>

    //<editor-fold desc="My List">

    fun getMyListItems(userId: String, listType: Any, page: Int) = pageIOFlow(
        request = {
            when (listType) {
                is String ->
                    HanimeNetwork.myListService.getMyListItems(userId, listType, page)

                is MyListType ->
                    HanimeNetwork.myListService.getMyListItems(userId, listType.value, page)

                else ->
                    throw IllegalArgumentException("typeOrId must be String or MyListType")
            }
        },
        action = Parser::myListItems
    )

    fun getMyPlayListItems(page: Int = 1, listCode: String = "0") = pageIOFlow(
        request = {
            HanimeNetwork.myListService.getMyPlayListItems(listCode, page)
        },
        action = Parser::myPlayListItems
    )

    fun getOnlineWatchHistories(
        userId: String,
        sort: OnlineWatchHistorySort,
        page: Int,
    ) = pageIOFlow(
        request = {
            HanimeNetwork.myListService.getOnlineWatchHistories(userId, sort.value, page)
        },
        action = Parser::onlineWatchHistoryItems,
    )

    fun getUserAccountPage(userId: String) = websiteIOFlow(
        request = { HanimeNetwork.myListService.getUserAccountPage(userId) },
        action = Parser::userAccountPage,
    )

    fun updateUserAccountProfile(
        userId: String,
        csrfToken: String?,
        name: String,
        email: String,
    ) = websiteIOFlow(
        request = {
            HanimeNetwork.myListService.updateUserAccountProfile(
                userId = userId,
                csrfToken = csrfToken,
                name = name,
                email = email,
            )
        },
        permittedSuccessCode = intArrayOf(302),
    ) {
        if (it.isBlank()) {
            WebsiteState.Success(Unit)
        } else {
            when (val result = Parser.userAccountPage(it)) {
                is WebsiteState.Error -> WebsiteState.Error(result.throwable)
                else -> WebsiteState.Success(Unit)
            }
        }
    }

    fun updateUserAccountPassword(
        userId: String,
        csrfToken: String?,
        oldPassword: String,
        newPassword: String,
        newPasswordConfirm: String,
    ) = websiteIOFlow(
        request = {
            HanimeNetwork.myListService.updateUserAccountPassword(
                userId = userId,
                csrfToken = csrfToken,
                oldPassword = oldPassword,
                newPassword = newPassword,
                newPasswordConfirm = newPasswordConfirm,
            )
        },
        permittedSuccessCode = intArrayOf(302),
    ) {
        if (it.isBlank()) {
            WebsiteState.Success(Unit)
        } else {
            when (val result = Parser.userAccountPage(it)) {
                is WebsiteState.Error -> WebsiteState.Error(result.throwable)
                else -> WebsiteState.Success(Unit)
            }
        }
    }

    // P4b：java.io.File 仅 JVM/Android 有，commonMain 签名改为 (bytes, name)；
    // 调用方（:app UserAccountViewModel）负责把 File 拆成字节与文件名
    fun updateUserAccountAvatar(
        userId: String,
        csrfToken: String?,
        avatarBytes: ByteArray,
        avatarName: String,
    ) = websiteIOFlow(
        request = {
            HanimeNetwork.myListService.updateUserAccountAvatar(
                userId = userId,
                csrfToken = csrfToken ?: EMPTY_STRING,
                method = "patch",
                type = "photo",
                photo = avatarBytes,
                photoName = avatarName,
            )
        },
        permittedSuccessCode = intArrayOf(302),
    ) {
        if (it.isBlank()) {
            WebsiteState.Success(Unit)
        } else {
            when (val result = Parser.userAccountPage(it)) {
                is WebsiteState.Error -> WebsiteState.Error(result.throwable)
                else -> WebsiteState.Success(Unit)
            }
        }
    }

    fun deleteOnlineWatchHistory(
        videoCode: String,
        position: Int,
        csrfToken: String?,
    ) = websiteIOFlow(
        request = {
            HanimeNetwork.myListService.deleteOnlineWatchHistory(
                videoCode = videoCode,
                csrfToken = csrfToken,
            )
        },
    ) {
        // P4b：org.json -> kotlinx（解析失败抛异常，语义同 JSONObject(it)；optBoolean 缺省 false）
        val jsonObject = Json.parseToJsonElement(it).jsonObject
        val success = jsonObject.optBooleanCompat("success", false)
        if (success) {
            WebsiteState.Success(position)
        } else {
            WebsiteState.Error(IllegalStateException("cannot delete it ?!"))
        }
    }

    fun deleteMyListItems(
        typeOrCode: Any,
        videoCode: String,
        position: Int,
        token: String?,
    ) = websiteIOFlow(
        request = {
            when (typeOrCode) {
                is String ->
                    HanimeNetwork.myListService.deleteMyListItems(
                        typeOrCode, videoCode,
                        csrfToken = token
                    )

                is MyListType ->
                    HanimeNetwork.myListService.deleteMyListItems(
                        typeOrCode.value, videoCode,
                        csrfToken = token
                    )

                else ->
                    throw IllegalArgumentException("typeOrId must be String or MyListType")
            }
        }
    ) { deleteBody ->
        // P4b：org.json -> kotlinx（get("video_id") 缺 key 抛错语义保持）
        val jsonObject = Json.parseToJsonElement(deleteBody).jsonObject
        val returnVideoCode = jsonObject.getCompatString("video_id")
        if (videoCode == returnVideoCode) {
            return@websiteIOFlow WebsiteState.Success(position)
        }

        return@websiteIOFlow WebsiteState.Error(IllegalStateException("cannot delete it ?!"))
    }

    fun getPlaylists(page: Int, userId: String ) = websiteIOFlow(
        request = { HanimeNetwork.myListService.getPlaylists(userId, page) },
        action = Parser::playlists
    )

    fun addToMyFavVideo(
        videoCode: String,
        likeStatus: Boolean, // false => "": add fav; true => "1": cancel fav;
        currentUserId: String?,
        token: String?,
    ) = websiteIOFlow(
        request = {
            HanimeNetwork.myListService.addToMyFavVideo(
                videoCode, if (likeStatus) "1" else EMPTY_STRING,
                token, currentUserId
            )
        }
    ) {
        LogUtil.d("add_to_fav_body", it)
        return@websiteIOFlow WebsiteState.Success(likeStatus)
    }

    fun rateVideo(
        videoCode: String,
        isPositive: Boolean,
        likeStatus: Boolean,
        unlikeStatus: Boolean,
        likesCount: Int,
        unlikesCount: Int,
        currentUserId: String?,
        token: String?,
    ) = websiteIOFlow(
        request = {
            HanimeNetwork.myListService.rateVideo(
                videoCode = videoCode,
                isPositive = if (isPositive) 1 else 0,
                likeStatus = if (likeStatus) "1" else EMPTY_STRING,
                unlikeStatus = if (unlikeStatus) "1" else EMPTY_STRING,
                likesCount = likesCount,
                unlikesCount = unlikesCount,
                csrfToken = token,
                userId = currentUserId,
            )
        }
    ) {
        LogUtil.d("rate_video_body", it)
        return@websiteIOFlow WebsiteState.Success(isPositive)
    }

    fun createPlaylist(
        videoCode: String,
        title: String,
        description: String,
        csrfToken: String?,
    ) = websiteIOFlow(
        request = {
            HanimeNetwork.myListService.createPlaylist(
                csrfToken, videoCode, title, description
            )
        },
        permittedSuccessCode = intArrayOf(500)
    ) {
        LogUtil.d("create_playlist_body", it)
        return@websiteIOFlow WebsiteState.Success(Unit)
    }

    fun addToMyList(
        listCode: String,
        videoCode: String,
        isChecked: Boolean,
        position: Int,
        csrfToken: String?,
    ) = websiteIOFlow(
        request = {
            HanimeNetwork.myListService.addToMyList(
                csrfToken, listCode, videoCode, isChecked
            )
        }
    ) {
        LogUtil.d("add_to_playlist_body", it)
        return@websiteIOFlow WebsiteState.Success(position)
    }

    fun modifyPlaylist(
        listCode: String,
        title: String,
        description: String,
        delete: Boolean,
        csrfToken: String?,
    ) = websiteIOFlow(
        request = {
            HanimeNetwork.myListService.modifyPlaylist(
                listCode, title, description,
                if (delete) "on" else null,
                csrfToken
            )
        },
        permittedSuccessCode = intArrayOf(302)
    ) {
        LogUtil.d("modify_playlist_body", it)
        return@websiteIOFlow WebsiteState.Success(
            ModifiedPlaylistArgs(
                title = title, desc = description, isDeleted = delete,
            )
        )
    }

    //</editor-fold>

    //<editor-fold desc="Comment">

    fun getComments(type: String, code: String) = websiteIOFlow(
        request = { HanimeNetwork.commentService.getComments(type, code) },
        action = Parser::comments
    )

    fun getCommentReply(commentId: String) = websiteIOFlow(
        request = { HanimeNetwork.commentService.getCommentReply(commentId) },
        action = Parser::commentReply
    )

    fun postComment(
        csrfToken: String?,
        currentUserId: String,
        targetUserId: String,
        type: String,
        text: String,
    ) = websiteIOFlow(
        request = {
            HanimeNetwork.commentService.postComment(
                csrfToken, currentUserId,
                type, targetUserId, text
            )
        }
    ) {
        LogUtil.d("post_comment_body", it)
        return@websiteIOFlow WebsiteState.Success(Unit)
    }

    fun postCommentReply(
        csrfToken: String?,
        replyCommentId: String,
        text: String,
    ) = websiteIOFlow(
        request = {
            HanimeNetwork.commentService.postCommentReply(
                csrfToken, replyCommentId, text
            )
        }
    ) {
        LogUtil.d("post_comment_reply_body", it)
        return@websiteIOFlow WebsiteState.Success(Unit)
    }

    fun likeComment(
        csrfToken: String?,
        commentPlace: CommentPlace,
        foreignId: String?,
        isPositive: Boolean, // 你選擇的是讚還是踩，1是讚，0是踩
        likeUserId: String?,
        commentLikesCount: Int,
        commentLikesSum: Int,
        likeCommentStatus: Boolean, // 你之前有沒有點過讚，1是0否
        unlikeCommentStatus: Boolean, // 你之前有沒有點過踩，1是0否
        commentPosition: Int, comment: VideoComments.VideoComment,
    ) = websiteIOFlow(
        request = {
            HanimeNetwork.commentService.likeComment(
                csrfToken, commentPlace.value, foreignId,
                if (isPositive) 1 else 0,
                likeUserId, commentLikesCount, commentLikesSum,
                if (likeCommentStatus) 1 else 0,
                if (unlikeCommentStatus) 1 else 0
            )
        }
    ) {
        LogUtil.d("like_comment_body", it)
        return@websiteIOFlow WebsiteState.Success(
            VideoCommentArgs(
                commentPosition, isPositive, comment
            )
        )
    }

    fun reportComment(
        csrfToken: String?,
        reason: String,
        currentUserId: String?,
        redirectUrl: String,
        reportableType: String?,
        reportableId: String?
    ) = websiteIOFlow(
        request = {
            HanimeNetwork.commentService.submitReport(
                userId = currentUserId,
                csrfToken = csrfToken,
                redirectUrl = redirectUrl,
                reportableId = reportableId,
                reportableType = reportableType,
                reason = reason
            )
        },
        action = Parser::reportCommentResponse
    )

    //</editor-fold>

    //<editor-fold desc="Subscription">

    fun subscribeArtist(
        csrfToken: String?,
        userId: String,
        artistId: String,
        // 这里表示目标状态
        status: Boolean,
    ) = websiteIOFlow(
        request = {
            HanimeNetwork.subscriptionService.subscribeArtist(
                csrfToken, userId, artistId,
                if (status) "" else "1"
            )
        }
    ) {
        LogUtil.d("subscribe_artist_body", it)
        return@websiteIOFlow WebsiteState.Success(status)
    }

    //</editor-fold>

    //<editor-fold desc="Base">

    fun login(email: String, password: String) = flow {
        emit(WebsiteState.Loading)
        // 首先获取token
        val loginPage = HanimeNetwork.hanimeService.getLoginPage()
        // /login 匿名访问必为 200：CF 边缘按指纹/IP 拦截时这里直接 403/404
        //（含无标记的隐身墙），此时无 token 可取——开验证窗并明说，不再误报密码错。
        throwIfCloudflareBlocked(loginPage)
        val token = loginPage.bodyAsText().let(Parser::extractTokenFromLoginPage)
        val req = HanimeNetwork.hanimeService.login(token, email, password)
        LogUtil.d("login_verify", "POST /login status=${req.status.value}")
        // 本栈不跟随重定向（Ktor 自带 HttpRedirect 未安装）：成功登录回 302→/home，
        // 被拒回 302→/login——两者都走会话正向判定，Location 本身不作结论。
        if (req.status.isSuccess() || req.status.value == 302) {
            if (isSessionAuthenticated()) {
                // Cookie 會返回 XSRF-TOKEN 和 hanime1_session，我們只需要後者
                // 错误的，还需要 remember_web 字段！但我没找到！
                LogUtil.d("login_headers", req.headers.entries().joinToString { "${it.key}=${it.value}" })
                emit(WebsiteState.Success(req.headers.getAll("Set-Cookie").orEmpty()))
            } else {
                emit(WebsiteState.Error(IllegalStateException(getString(Res.string.account_or_password_wrong))))
            }
        } else {
            // POST 也可能撞 CF（GET 放行不代表 POST 放行）：先识别，剩下的才算凭据问题。
            throwIfCloudflareBlocked(req)
            // 雙重保險
            emit(WebsiteState.Error(IllegalStateException(getString(Res.string.account_or_password_wrong))))
        }
    }.catch { e ->
        emit(WebsiteState.Error(handleException(e)))
    }.flowOn(ioDispatcher)

    /**
     * 正向会话判定：POST 成功后确认服务器真的认了这个会话。
     *
     * 判定顺序：① 重进 /login 仍 404（旧站点语义，保留兼容）；
     * ② 取首页提用户名（现行语义，[Parser.extractLoggedInUsername]）。
     * 任一命中即已登录。校验请求自身撞 CF 时走 CF 通道（开验证窗），
     * 不吞成"密码错"。
     */
    private suspend fun isSessionAuthenticated(): Boolean {
        val loginPageAgain = HanimeNetwork.hanimeService.getLoginPage()
        LogUtil.d("login_verify", "re-GET /login status=${loginPageAgain.status.value}")
        if (loginPageAgain.status.value == 404) return true
        throwIfCloudflareBlocked(loginPageAgain)
        val home = HanimeNetwork.hanimeService.getHomePage(SettingsRepository.homeUrl)
        LogUtil.d("login_verify", "GET home status=${home.status.value}")
        if (!home.status.isSuccess()) return false
        val username = Parser.extractLoggedInUsername(home.bodyAsText())
        LogUtil.d("login_verify", "homepage username=${if (username != null) "present" else "missing"}")
        return username != null
    }

    /** 等 CF 验证通过的上限：比桌面 CDP 的求解预算（120s）再宽一点，别卡在它前面放弃。 */
    private const val CF_RETRY_WAIT_MS = 150_000L

    /**
     * 站点请求 + 状态判定 + **CF 验证通过后自动续跑一次**。
     *
     * 为什么要在流里等：验证成功之前，失败的请求早就抛完了，所以"用户验完了、应用却什么都没
     * 发生"——只能手动退回再进，观感就是弹一次窗赌一把。这里让原请求挂在通过信号上，
     * 窗口一关页面自己就出来了。只续跑一次（再失败就照常报错），不给死循环留口子。
     *
     * 非 CF 错误的语义与 [HttpResponse.throwRequestException] 完全一致。
     */
    private suspend fun ioRequest(
        request: suspend () -> HttpResponse,
        permittedSuccessCode: IntArray? = null,
    ): HttpResponse {
        val first = request()
        if (first.isSuccessfulSiteResponse(permittedSuccessCode)) return first
        try {
            first.throwRequestException()
        } catch (blocked: CloudflareBlockedException) {
            val host = CloudflareChallenges.hostOf(first.call.request.url.toString())
            if (!CloudflareChallenges.awaitPassed(host, CF_RETRY_WAIT_MS)) throw blocked
            val retried = request()
            if (!retried.isSuccessfulSiteResponse(permittedSuccessCode)) {
                retried.throwRequestException()
            }
            return retried
        }
    }

    /**
     * 状态码是否算成功：`permittedSuccessCode` 用于特殊情况，
     * 比如 [modifyPlaylist] 需要 302 成功。
     */
    private fun HttpResponse.isSuccessfulSiteResponse(permitted: IntArray?): Boolean =
        permitted?.contains(status.value) == true || status.isSuccess()

    /**
     * 用于单网页的情况
     *
     * @param permittedSuccessCode 用于处理特殊情况，比如[NetworkRepo.modifyPlaylist]需要302成功
     */
    private fun <T> websiteIOFlow(
        request: suspend () -> HttpResponse,
        permittedSuccessCode: IntArray? = null,
        // P4：action 改 suspend（Parser.homePageVer2 用 composeResources getString 需要）
        action: suspend (String) -> WebsiteState<T>,
    ) = flow {
        val requestResult = ioRequest(request, permittedSuccessCode)
        emit(action.invoke(requestResult.bodyAsText()))
    }.catch { e ->
        emit(WebsiteState.Error(handleException(e)))
    }.flowOn(ioDispatcher)

    /**
     * 用于有page分页的情况
     */
    private fun <T> pageIOFlow(
        request: suspend () -> HttpResponse,
        action: (String) -> PageLoadingState<T>,
    ) = flow {
        val requestResult = ioRequest(request)
        emit(action.invoke(requestResult.bodyAsText()))
    }.catch { e ->
        emit(PageLoadingState.Error(handleException(e)))
    }.flowOn(ioDispatcher)

    /**
     * 用于影片界面
     */
    private fun <T> videoIOFlow(
        request: suspend () -> HttpResponse,
        action: (String) -> VideoLoadingState<T>,
    ) = flow {
        PlayerTrace.mark("video-request-start")
        val requestResult = ioRequest(request)
        PlayerTrace.mark("video-request-end")
        PlayerTrace.mark("video-parse-start")
        val parsed = action.invoke(requestResult.bodyAsText())
        PlayerTrace.mark("video-parse-end")
        emit(parsed)
    }.catch { e ->
        emit(VideoLoadingState.Error(handleException(e)))
    }.flowOn(ioDispatcher)

    /**
     * 登录链路专用 CF 识别：与 [throwRequestException] 同一套标记（"you have been blocked" /
     * "Just a moment"），外加无标记 403/404 兜底；命中则抛 CF 异常（触发验证窗 +
     * UI 显示真实原因），未命中则静默返回，调用方继续原逻辑。
     *
     * 背景：桌面端没有 OkHttp 层 CF 拦截器，CF 边缘按指纹/IP 拦截时登录请求直接
     * 403/404，此前一路走到"账号或密码错误"，密码对也永远登不上。
     *
     * 状态码路由（仅登录流内成立）：成功登录的 POST 必 302→200，被拒的 POST
     * 也 302→200（跟随回登录页），所以 POST 落到 403/404 只可能是边缘干扰 →
     * 开验证窗；419（CSRF 过期）/422/5xx 走原逻辑（凭据或表单问题），不开窗。
     */
    internal suspend fun throwIfCloudflareBlocked(response: HttpResponse) {
        if (response.status.isSuccess()) return
        val body = runCatching { response.bodyAsText() }.getOrNull().orEmpty()
        fun fireVerificationWindow() {
            // 与 throwRequestException 同语义：通知 App() 压栈验证页。
            runCatching {
                CloudflareChallenges.request(response.call.request.url.toString())
            }
        }
        when {
            "you have been blocked" in body ->
                throw IPBlockedException(getString(Res.string.cloudflare_ip_block_warning))

            "Just a moment" in body -> {
                fireVerificationWindow()
                throw CloudflareBlockedException(getString(Res.string.cloudflare_network_mismatch))
            }

            response.status.value == 403 || response.status.value == 404 -> {
                fireVerificationWindow()
                throw CloudflareBlockedException(getString(Res.string.login_requires_cf_verification))
            }
        }
    }

    internal suspend fun HttpResponse.throwRequestException(): Nothing {        // suspend 后可直接读 body（仍在 flowOn(IO) 上执行）
        val body = runCatching { bodyAsText() }.getOrNull()
        when (val code = status.value) {
            403 -> if (!body.isNullOrBlank()) {
                when {
                    "you have been blocked" in body ->
                        throw IPBlockedException(getString(Res.string.cloudflare_ip_block_warning))

                    "Just a moment" in body -> {
                        // 三端统一 CF 恢复触发（桌面 CDP 窗 / iOS WKWebView 槽位至此可达；
                        // Android 拦截器链路不受影响，见 CloudflareChallenges 文档）。
                        val url = call.request.url.toString()
                        // 先把这把死钥匙丢掉再开窗：命中挑战说明它已过期或出口 IP 变了，
                        // 留着它既会让下面的诊断日志谎报"有凭据"，也会让下一次请求继续裸奔。
                        runCatching {
                            SettingsRepository.clearCloudFlareCookie(CloudflareChallenges.hostOf(url))
                        }
                        LogUtil.d("CF", "challenge: ${cfFailureFingerprint(url, code)}")
                        runCatching {
                            CloudflareChallenges.request(url)
                        }
                        throw CloudflareBlockedException(getString(Res.string.cloudflare_network_mismatch))
                    }

                    else ->
                        throw HanimeNotFoundException(getString(Res.string.video_might_not_exist)) // 主要出現在影片界面，當你v數不大時會報403
                }
            } else throw IllegalStateException("$code ${status.description}")

            500 -> throw HanimeNotFoundException(getString(Res.string.video_might_not_exist)) // 主要出現在影片界面，當你v數很大時會報500

            404 -> if (!isAlreadyLogin) {
                throw IllegalStateException(getString(Res.string.not_logged_in_currently))
            } else {
                throw IllegalStateException("$code ${status.description}")
            }

            else -> throw IllegalStateException("$code ${status.description}")
        }
    }

    /**
     * CF 挑战诊断指纹（无敏感值）：host + 状态码 + 是否携带已持久化 clearance + 代理类型。
     * 下次"验证成功但应用仍失败"直接看这行即可定位（裸奔重试 / 换 host / 换出口 IP）。
     */
    internal fun cfFailureFingerprint(url: String, status: Int): String {
        val host = CloudflareChallenges.hostOf(url)
        return "host=$host status=$status hasClearance=${SettingsRepository.cfCookieFor(host) != null} " +
            "proxyType=${SettingsRepository.proxyType}"
    }

    internal suspend fun handleException(e: Throwable): Throwable {
        return when {
            e is CancellationException -> throw e
            e is ParseException -> {
                e.printStackTrace()
                ParseException(getString(Res.string.parse_error_msg))
            }

            e.isSslHandshakeException() -> {
                e.printStackTrace()
                // P4b：javax.net.ssl 仅 JVM 有，改走 expect（jvmMain 真实类型 / iosMain RuntimeException）
                sslHandshakeException(getString(Res.string.ssl_handshake_error))
            }

            else -> {
                e.printStackTrace()
                e
            }
        }
    }

    //</editor-fold>



    // P4b：org.json 兼容小助手（仅本文件用）
    private fun JsonObject.optBooleanCompat(key: String, default: Boolean): Boolean =
        this[key]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: default

    private fun JsonObject.getCompatString(key: String): String {
        val element = this[key] ?: error("No value for key: $key")
        return if (element is JsonPrimitive) element.content else element.toString()
    }
}
