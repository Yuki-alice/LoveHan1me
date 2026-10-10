package lovehan1me.feature.player

import lovehan1me.core.util.LogUtil
import lovehan1me.core.util.MpvShaders
import lovehan1me.core.util.materializeMpvShaders
import lovehan1me.core.util.parseMpvCustomParams
import lovehan1me.video.contract.VideoEnhancementController
import lovehan1me.video.contract.VideoEnhancementLevels
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import lovehan1me.video.contract.safeCombine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.concurrent.thread
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.features.AudioLevelController
import org.openani.mediamp.features.FramePreview
import org.openani.mediamp.mpv.MPVHandle
import org.openani.mediamp.mpv.MpvMediampPlayer
import org.openani.mediamp.mpv.MpvMediampPlayerFactory
import org.openani.mediamp.source.UriMediaData

/**
 * M3：桌面 mpv 真引擎（mediamp-mpv，animeko 同款；native 库随
 * `mediamp-mpv-runtime-*` 自动加载，macOS arm64 已验证）。
 *
 * 状态机、首帧标记、缓冲真值、单写者都在 [MediampPlaybackEngineBase] —— 本类只剩
 * 桌面独有的三件事：ECH 网关改写与失败回退、mpv 属性级选项下发、mpv 属性级画面实现。
 * 此前它自带一套状态收集循环（不继承基类），首帧用 `positionMs > 0` 近似、
 * 缓冲进度直接等于当前播放位置，与 Android/iOS 语义分叉。
 *
 * - 渲染不走 surface 回调（`attach/detach` 在基类为空实现），由
 *   `PlatformVideoSurface.desktop` 的 Skia 面直接持有本引擎的 `mediampPlayer`；
 * - mpv 选项分两条路径下发，都在 `setMediaData` 之前：网络/身份（代理 + UA，见
 *   [applyNetworkOptions]）与「MPV 高级设置」页的用户选项（见 [applyMpvSettings]，
 *   每次 load 重发以覆盖上一次的状态）。
 * - **`vo` 不由本项目决定**：mediamp 持有 `vo=gpu-next`/`libmpv` 组合，故桌面端
 *   「GPU Next 渲染器」开关无法生效 → 设置页按平台能力隐藏（[applyMpvSettings] KDoc 有证据）。
 */
class DesktopMpvPlaybackEngine(
    private val network: PlayerNetworkConfig,
    private val mpvOptions: PlayerMpvOptionsProvider,
) : MediampPlaybackEngineBase() {

    override val mediampPlayer: MpvMediampPlayer by lazy {
        // 原生库由引擎初始化链自行加载（mediamp-mpv-runtime-*）；首参在桌面端
        // 仅作透传 token（Android 侧为 Context），传中性非空常量。
        MpvMediampPlayerFactory().create(ENGINE_TOKEN, scope.coroutineContext)
    }

    /** G2-3b：当前画面调节（mpv 的 brightness/contrast/saturation）。 */
    private var pictureAdjust: PictureAdjust = PictureAdjust.Neutral

    /**
     * 给渲染面用的挂起获取：返回预热好的 player。
     *
     * 惰性初始化（解压 + dlopen + mpv_create）在 Default 线程上执行 —— 即使
     * 启动预载还没跑完就进了视频页，EDT 也只等一次返回，不会阻塞在
     * SynchronizedLazyImpl 的锁上把整个界面冻住。
     */
    suspend fun awaitPlayer(): MpvMediampPlayer =
        withContext(Dispatchers.Default) { mediampPlayer }

    override suspend fun acquireMediampPlayer(): MediampPlayer = awaitPlayer()

    // ⚠️ init 块在文件末尾（companion object 之前）—— 不能挪回这里，原因见那里的注释。

    // ── 加载：ECH 网关改写 + 失败回退直连（桌面独有）───────────────

    /**
     * 覆盖基类：桌面要先做网关改写、下发 mpv 选项，网关链路失败还要补一次直连重试。
     *
     * @param allowGate 本次允许走 ECH 网关。回退重试时传 false 强制直连。
     */
    override suspend fun openMedia(request: PlaybackRequest) = openMedia(request, allowGate = true)

    private suspend fun openMedia(request: PlaybackRequest, allowGate: Boolean) {
        // ⚠️ 直链尾部是 `?secure=<token>,<expiry>` —— 限时访问凭据，只打 origin+path，
        // 查询串整体省略（调试时如需核对，看请求头/网关日志，别把凭据抄进终端与日志文件）。
        LogUtil.d(TAG, "load: ${request.uri.substringBefore('?')} (query 已省略, headers=${request.headers.keys})")
        // 网关改写只算一次：失败时要靠它判断"刚才是不是走的网关"。
        val gateRewrite = if (allowGate) network.rewriteForGate(request.uri) else null
        val (mediaUri, mediaHeaders) = gateRewrite
            ?.let { (url, gateHeaders) -> url to (request.headers + gateHeaders) }
            ?: (request.uri to request.headers)
        try {
            // 惰性初始化先在 Default 线程摸热：万一启动预载尚未完成，
            // 这里也只会挂起等待，不会把 EDT 阻塞在 lazy 锁上。
            val player = withContext(Dispatchers.Default) { mediampPlayer }
            // ⚠️ 必须在 setMediaData **之前**：mpv 的网络选项在"打开流"那一刻生效。
            // 不做这一步，mpv 会用**直连**去拉流（它不继承 OkHttp 的代理），
            // 在受限网络下表现为 mpv_error=-13（LOADING_FAILED）——页面能开、视频永远转圈。
            // 网络选项与「MPV 高级设置」同批下发，都在"打开流"那一步之前生效。
            mpvHandle()?.let { handle ->
                applyNetworkOptions(handle, mediaUri)
                applyMpvSettings(handle)
                // G2-3b：换流之后重下画面类偏好（新 mpv 实例/新窗口尺寸下属性仍在，
                // 但 draw 尺寸会变，Stretch 的比值必须按新渲染面重算）。
                applyVideoAspect(handle, currentAspect)
                applyPictureAdjust(handle, pictureAdjust)
                // 超分属性排在最后：`profile=gpu-hq` 会顺手改一串 scale/deband，
                // 让它盖不回已经生效的超分档。
                refreshEnhancementProperties(handle)
            }
            // 不要再补一次 play()：mpv 在 `loadfile` 之前就把 `pause=!playWhenReady`
            // 落到原生，而本协程不在 main dispatcher 上，补的那一次只会被
            // mediamp 的 checkMainThread 拒成播放错误。
            player.setMediaData(
                data = UriMediaData(mediaUri, mediaHeaders),
                playWhenReady = playWhenReadyFor(request),
                startPositionMillis = startPositionFor(request),
            )
            // 阶段 4.2：网关链路结局上报（只此一处成功点；失败走下面的 catch）。
            // 注意这是"引擎侧观察到的加载结局"：mpv 的 loadfile 之后才异步拉流，
            // 同步 setMediaData 返回不代表字节到齐 —— 但本方法与 catch 的口径一致
            //（失败回退也只兜同步抛），不假装看到了全部。
            if (gateRewrite != null) network.onGateLoadOutcome(request.uri, ok = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // R2：失败即停（学 animeko）。此前这里 1.5s 后静默重载同一 URL，
            // 纹理未释放就再开一流；手动重试（错误卡按钮调 load()）还在。
            LogUtil.e(TAG, "load failed", e)
            // ⚠️ 网关失败必须补一次直连重试：mpv 的 URL 是**静态重写**成
            // `127.0.0.1:<port>` 的，网关回 502（上游拨不通/连接被关）时
            // ffmpeg 直接拿到 502，mpv 报 mpv_error=-13 结束 —— 而 OkHttp 链路
            // 有 `EchGateInterceptor` 的"回退直连"兜底，两条链路行为不一致，
            // 症状就是"同一部片，封面能刷出来、视频打不开"。这里补上同一份兜底，
            // 只重试一次（直连不通就真的不通，别循环）。
            if (gateRewrite != null) {
                // 先记网关这次失败再回退（F9）：视频域据此能独立熔断，回退成功不代表网关是好的。
                network.onGateLoadOutcome(request.uri, ok = false, reason = e.message)
                LogUtil.w(
                    TAG,
                    "网关链路加载失败，回退直连重试一次 ${request.uri.substringBefore('?')}",
                )
                openMedia(request, allowGate = false)
                return
            }
            throw e
        }
    }

    // ── 音量 / 画面调节（mediamp 无对应 feature 的部分走 mpv 属性）──

    override fun setVolume(volume: Float) {
        if (isReleased) return
        scope.launch {
            runCatching {
                mediampPlayer.features[AudioLevelController.Key]?.setVolume(volume)
            }.onFailure {
                LogUtil.w(TAG, "setVolume unsupported: $volume")
            }
        }
    }

    // ── 视频超分（Anime4K over mpv）───────────────────────────────
    //
    // 挂载只用 `change-list glsl-shaders append`，一条命令一个路径，卸载按逆序 `remove`。
    // 两个理由：mpv 的列表属性以**逗号**分隔，把多个路径拼成 `:`/`;` 串会被当成一个
    // 不存在的路径（命令照样回 true，静默不生效）；而 `set` 会连带清掉用户在
    // 「自定义参数」里自己挂的 shader —— 我们不该替别人清空列表。
    //
    // 生效档位流暴露的是**用户选的那一档**，不是"这一帧真的挂上了什么"：分辨率门控会在
    // 片源已经够大时整档不挂，若把门控结果写进流，菜单会在用户点下去的同一帧弹回「关闭」，
    // 看起来就是按钮坏了。只有"这一档在这台机器上挂不上"（shader 落盘失败、mpv 拒绝命令）
    // 才把流收回 —— 那是真不生效，UI 该跟着变。

    private val requestedLevel = MutableStateFlow(VideoEnhancementLevels.OFF)

    /** 渲染面尺寸（px），由 [lovehan1me.feature.player.PlatformVideoSurface] 的组合期回调喂。 */
    private val viewportSize = MutableStateFlow(0 to 0)
    private val enhancementLock = Mutex()

    /** 已 append 进 mpv 的 shader，顺序即挂载顺序，卸载时逆序。 */
    private var appliedShaderPaths = listOf<String>()

    /** 已下发的生效档位：与上次相同就不重发属性，避免每次片源尺寸抖动都过一遍 mpv。 */
    private var appliedLevel = MpvShaders.OFF

    /** 进本项目之前 mpv 的属性原值，OFF 时逐项还原（首次拿到句柄时快照一次）。 */
    private var originalProperties: Map<String, String>? = null

    override val enhancement: VideoEnhancementController = object : VideoEnhancementController {
        override val levels: List<Int> = VideoEnhancementLevels.ALL
        override val level: StateFlow<Int> = requestedLevel.asStateFlow()

        override suspend fun setLevel(level: Int): Int {
            if (isReleased) return requestedLevel.value
            requestedLevel.value = level
            return applyEnhancement()
        }
    }

    /** 渲染面尺寸变化 → 门控要重算（窗口拉大、进全屏都走这里）。 */
    fun updateViewportSize(width: Int, height: Int) {
        val next = width to height
        if (next != viewportSize.value) viewportSize.value = next
    }

    /**
     * 请求档位、片源尺寸、渲染面尺寸任一变化 → 重算门控并重挂。
     *
     * 三者都在流里，所以"打开超分 → 之后才拖大窗口""窗口不变但换了清晰度"
     * 这两种时序都会自己收敛，不靠用户再点一次。
     */
    private fun startEnhancementObserver() {
        scope.launch {
            safeCombine(
                requestedLevel,
                state.map { it.videoWidth to it.videoHeight },
                viewportSize,
            ) { level, videoSize, viewport -> Triple(level, videoSize, viewport) }
                .distinctUntilChanged()
                .collect { applyEnhancement() }
        }
    }

    /** 加锁走完"算门控 → 挂 shader → 下属性"，返回**实际挂上**的档位。 */
    private suspend fun applyEnhancement(): Int = enhancementLock.withLock {
        // 从没开过超分也没挂过任何东西时别去碰 mpv：句柄是惰性的（解压 + dlopen +
        // mpv_create），观察者首帧（档位 OFF、尺寸为 0）不该为这条空操作把原生库叫醒。
        if (requestedLevel.value == MpvShaders.OFF && originalProperties == null) {
            return@withLock MpvShaders.OFF
        }
        val target = gatedLevel(requestedLevel.value)
        val applied = applyWithFallback(target)
        if (applied != target) {
            // 挂不上才收回用户那档；门控（target == OFF）不动它，否则菜单会自己弹回。
            LogUtil.w(TAG, "超分档位 $target 挂不上，收回为 $applied")
            requestedLevel.value = applied
        }
        applied
    }

    private fun gatedLevel(requested: Int): Int {
        if (requested == MpvShaders.OFF) return MpvShaders.OFF
        if (mpvHandle() == null) return requested
        val (videoWidth, videoHeight) = state.value.let { it.videoWidth to it.videoHeight }
        val (viewportWidth, viewportHeight) = viewportSize.value
        val gated = gateEnhancementByScale(requested, videoWidth, videoHeight, viewportWidth, viewportHeight)
        if (gated == MpvShaders.OFF) {
            LogUtil.d(TAG, "超分门控：渲染面 ${viewportWidth}x$viewportHeight 不比片源 ${videoWidth}x$videoHeight 大，暂不挂")
        }
        return gated
    }

    /** 依次尝试 [target] → 低档 → OFF，第一个成功的即生效。 */
    private suspend fun applyWithFallback(target: Int): Int {
        val chain = buildList {
            add(target)
            if (target == MpvShaders.QUALITY) add(MpvShaders.PERFORMANCE)
            if (target != MpvShaders.OFF) add(MpvShaders.OFF)
        }
        for (candidate in chain) {
            val applied = runCatching { applyLevel(candidate) }.getOrNull()
            if (applied != null) {
                if (candidate != target) LogUtil.w(TAG, "超分档位 $target 不可用，降级到 $candidate")
                return applied
            }
        }
        LogUtil.w(TAG, "超分无法下发（含 OFF），保持关闭")
        return MpvShaders.OFF
    }

    /**
     * @return 实际落到 mpv 的档位；句柄没起来 / 落盘失败 / mpv 拒绝命令时返回 null，
     *         调用方据此走降级链。
     */
    private suspend fun applyLevel(level: Int): Int? {
        val handle = mpvHandle() ?: return null
        val paths = materializeMpvShaders(level) ?: return null
        if (level == appliedLevel && paths == appliedShaderPaths) return level

        removeAppliedShaders(handle)
        applyEnhancementProperties(handle, level)
        paths.forEach { path ->
            if (!handle.command("change-list", "glsl-shaders", "append", path)) {
                error("mpv 拒绝挂载 shader：$path")
            }
            appliedShaderPaths += path
        }
        appliedLevel = level
        LogUtil.i(TAG, "超分档位 $level 已挂载（${paths.size} 条 shader）")
        return level
    }

    private fun removeAppliedShaders(handle: MPVHandle) {
        appliedShaderPaths.asReversed().forEach { path ->
            if (!handle.command("change-list", "glsl-shaders", "remove", path)) {
                LogUtil.w(TAG, "mpv 卸载 shader 失败：$path")
            }
        }
        appliedShaderPaths = emptyList()
    }

    /**
     * 「MPV 高级设置」每次 load 都重下发（`profile`/`deband` 会盖掉超分写的缩放属性），
     * 所以超分生效时要在它之后把属性重推一遍。shader 挂在 mpv 核心上，
     * `setMediaData` 不会清掉它，不需要重挂。
     */
    private fun refreshEnhancementProperties(handle: MPVHandle) {
        val level = appliedLevel
        if (level == MpvShaders.OFF) return
        runCatching { applyEnhancementProperties(handle, level) }
            .onFailure { LogUtil.w(TAG, "重推超分属性失败：${it.message}") }
    }

    /** 释放时把挂上去的东西摘干净。句柄已由释放链建起来时才碰它，不为清理去触发惰性初始化。 */
    override fun onRelease() {
        if (originalProperties == null) return
        val handle = mpvHandle() ?: return
        runCatching {
            removeAppliedShaders(handle)
            originalProperties?.forEach { (name, value) -> handle.setPropertyString(name, value) }
        }.onFailure { LogUtil.w(TAG, "释放时清理超分失败：${it.message}") }
        appliedLevel = MpvShaders.OFF
        requestedLevel.value = MpvShaders.OFF
    }

    /**
     * 下发/还原超分要用到的 mpv 属性。
     *
     * 超分链是"放大"，缩放滤镜与色空间换算必须一起换才看得出差别：`ewa_lanczossharp`
     * 配 sigmoid 放大、0.7 抗振铃，再叠一层轻 deband 压掉片源的色带（放大后色带更明显）。
     * Windows 走轻量档：那里的硬解路径上 deband 是实打实的额外一趟全屏 pass。
     *
     * OFF 还原的是**首次快照**，不是写死的 mpv 默认值 —— 用户在「MPV 高级设置」里
     * 自己设的 deband/profile 得原样还回去。
     */
    private fun applyEnhancementProperties(handle: MPVHandle, level: Int) {
        if (originalProperties == null) {
            originalProperties = ENHANCEMENT_PROPERTY_NAMES.associateWith {
                handle.getPropertyString(it).orEmpty()
            }
        }
        val values = if (level == MpvShaders.OFF) {
            originalProperties ?: return
        } else {
            if (isWindowsDesktop()) WINDOWS_LITE_PROFILE else FULL_PROFILE
        }
        values.forEach { (name, value) ->
            check(handle.setPropertyString(name, value)) {
                "mpv 拒绝视频增强属性 $name=$value"
            }
        }
    }

    // ── G2-3b：画面比例 / 画面调节（mpv 原生三档全支持）──────────

    override fun supportsPictureAdjust(): Boolean = true

    /**
     * 桌面覆盖基类：mpv 的 Stretch 档要按**渲染面尺寸**给 DAR，mediamp 的
     * `VideoAspectRatio` feature（keepaspect 路径）给不了这个尺寸依赖。
     */
    override suspend fun applyAspectToBackend(mode: VideoAspectMode) {
        mpvHandle()?.let { applyVideoAspect(it, mode) }
    }

    override fun setPictureAdjust(brightness: Float, contrast: Float, saturation: Float) {
        if (isReleased) return
        pictureAdjust = PictureAdjust(
            brightness = PictureAdjust.clamp(brightness),
            contrast = PictureAdjust.clamp(contrast),
            saturation = PictureAdjust.clamp(saturation),
        )
        scope.launch { runCatching { mpvHandle()?.let { applyPictureAdjust(it, pictureAdjust) } } }
    }

    /**
     * 画面比例 → mpv 属性。
     *
     * - Fit：`video-aspect-override=no`（= 关闭覆写，mpv 自己等比留边）+ `panscan=0`；
     * - Crop：`panscan=1.0`（mpv 的"裁切填满"；再叠加覆写会互相打架，故先清覆写）；
     * - Stretch：**没有直接的开关**。mpv 只有"把图像按某个 DAR 绘制"这一件事，
     *   所以这里把 DAR 覆写成**渲染面的宽高比**（`dwidth/dheight`）—— 图像被按
     *   容器形状绘制，即为拉伸。代价是容器尺寸变化时这个比值会过期，
     *   故 [startAspectWatch] 在 Stretch 档下按秒回读重算。
     *
     * ⚠️ 曾用 `-1` 表示"不覆写"，但 mpv 新版已废弃该写法，启动即刷
     * `[vd] Setting video-aspect-override to -1 is deprecated.`
     * 并提示改用 `no`（`--video-aspect-override=no --video-aspect-mode=container`）。
     * 一旦新版不再把 `-1` 当"清除"，从 Stretch 切回 Fit 就会清不掉上一次的覆写，
     * 表现为"比例菜单选了没反应"。故统一改成 `no`。
     */
    private fun applyVideoAspect(handle: MPVHandle, mode: VideoAspectMode) {
        when (mode) {
            VideoAspectMode.Fit -> {
                handle.setPropertyString("panscan", "0")
                handle.setPropertyString("video-aspect-override", ASPECT_OVERRIDE_NONE)
            }

            VideoAspectMode.Crop -> {
                handle.setPropertyString("video-aspect-override", ASPECT_OVERRIDE_NONE)
                handle.setPropertyString("panscan", "1.0")
            }

            VideoAspectMode.Stretch -> {
                handle.setPropertyString("panscan", "0")
                val width = handle.getPropertyString("dwidth")?.toDoubleOrNull() ?: 0.0
                val height = handle.getPropertyString("dheight")?.toDoubleOrNull() ?: 0.0
                if (width <= 0.0 || height <= 0.0) {
                    // 渲染面尺寸还没出来（首帧前）：先退回 Fit，等看门狗补上。
                    handle.setPropertyString("video-aspect-override", ASPECT_OVERRIDE_NONE)
                    return
                }
                handle.setPropertyString("video-aspect-override", (width / height).toString())
            }
        }
    }

    /** 亮度/对比/饱和：mpv 三个属性都是 -100~100，0 = 原始。 */
    private fun applyPictureAdjust(handle: MPVHandle, adjust: PictureAdjust) {
        handle.setPropertyString("brightness", adjust.brightness.toInt().toString())
        handle.setPropertyString("contrast", adjust.contrast.toInt().toString())
        handle.setPropertyString("saturation", adjust.saturation.toInt().toString())
    }

    /**
     * Stretch 档的看门狗：渲染面尺寸变化时重算 `video-aspect-override`。
     *
     * mpv 没有"跟随容器拉伸"的模式，只能由外部在尺寸变了之后重下一次。
     * 只在 Stretch 档下读属性（默认档下这条循环完全不碰 mpv，不会因此提前初始化原生库）。
     */
    private fun startAspectWatch() {
        scope.launch {
            while (isActive) {
                delay(1000L)
                if (isReleased || currentAspect != VideoAspectMode.Stretch) continue
                runCatching { mpvHandle()?.let { applyVideoAspect(it, VideoAspectMode.Stretch) } }
            }
        }
    }

    /**
     * M3-b：桌面端支持抓帧 —— 走 mediamp 的 `FramePreview` feature
     * （`MpvFramePreview` 编译在 mediamp-mpv-desktop 里）。
     *
     * ⚠️ **这里必须返回常量，不能去查 `mediampPlayer.features`**：
     * [mediampPlayer] 是 lazy，首次访问会触发 mpv 原生初始化（几百毫秒），
     * 而本方法是**非挂起**的、会被组合期调用（见 [awaitPlayer] 的 KDoc 警告）。
     * 真正的能力在 [grabFrameArgb] 里按 feature 查询，拿不到就返回 null ——
     * 于是"声明支持"与"实际可用"解耦，既不会卡组合，也不会假装成功。
     */
    override fun supportsFrameCapture(): Boolean = true

    /**
     * M3-b：抓取 [positionMs] 处的画面。
     *
     * 两个实现要点：
     * 1. **必须发在 main dispatcher**：mediamp 要求播放器操作走构造时传入的
     *    主线程（桌面 = Swing EDT），与 load/play/seek 同一约束。
     * 2. **尺寸由解码侧产出**：`getPreviewFrame(pos, w, h)` 直接给目标尺寸，
     *    省掉一帧 1080p（8 MB）的中间位图 —— 这是选它而不是"抓全尺寸再自己缩"的原因。
     */
    override suspend fun grabFrameArgb(
        positionMs: Long,
        targetWidth: Int,
        targetHeight: Int,
    ): IntArray? {
        if (targetWidth <= 0 || targetHeight <= 0 || positionMs < 0L) return null
        return runCatching {
            withContext(Dispatchers.Main) {
                val preview = mediampPlayer.features[FramePreview.Key] ?: return@withContext null
                preview.getPreviewFrame(positionMs, targetWidth, targetHeight)?.pixels
            }
        }.getOrNull()
    }

    /**
     * 把应用的网络配置（代理 + UA）透传给 mpv。
     *
     * ## 为什么必须做：**mpv 是独立的原生网络栈，不继承 OkHttp 的代理设置**
     * 实测（2026-09-13，同一流地址、同一台机器）：
     * - 直连 → `curl: (35) Recv failure: Connection was reset`（15 秒、0 字节）
     * - 经 `127.0.0.1:7897` → **206**（0.3 秒、200 KB）
     * 于是症状是"CF 验证过了、页面也出来了，视频就是打不开"（`mpv_error=-13`）。
     *
     * mpv 的代理选项是 `http-proxy`，透传给 ffmpeg 的 `http_proxy`；
     * 用 [MPVHandle.setPropertyString] 设置并把返回值记进日志 ——
     * 若某天 mpv 把它标成不可运行时修改，日志里会直接看到 `set=false`，不必猜。
     */
    private fun applyNetworkOptions(handle: MPVHandle, mediaUri: String) {
        // ⚠️ 判据是 **mediaUri 本身是不是网关回环地址**，不是"网关有没有在跑"：
        // 网关启用时 URL 被改写到 127.0.0.1（见 [PlayerNetworkConfig.rewriteForGate]），
        // 而 ffmpeg 的 http_proxy 没有 bypass 列表，设了它连回环请求也会被代理出去；
        // 但引擎的"网关失败 → 回退直连"分支（[openMedia]）用的是**真实源站 URL**，
        // 那时必须设代理 —— 视频 CDN 多为 CDN77 之类的非 CF 边缘，ECH 帮不上忙，
        // 不设代理的结果就是裸直连的 `Connection refused`。
        // 两个方向的判据统一由 [PlayerNetworkConfig.proxyUrlFor] 的实现方给出。
        val proxy = network.proxyUrlFor(mediaUri)
        if (proxy != null) {
            val ok = handle.setPropertyString("http-proxy", proxy)
            LogUtil.d(TAG, "mpv http-proxy=$proxy set=$ok (media=${mediaUri.substringBefore('?')})")
            if (!ok) LogUtil.w(TAG, "mpv 不接受运行时设置 http-proxy，视频可能仍走直连")
        } else {
            // 显式清掉上一次 load 留下的值：player 实例跨视频复用，若上一轮设过代理
            // 这一轮又走回环，残留的 http-proxy 会把发给网关的请求也代理出去。
            runCatching { handle.setPropertyString("http-proxy", "") }
            LogUtil.d(TAG, "mpv 不设 http-proxy（媒体走本地网关回环，或未配置代理）")
        }
        // UA 与应用 HTTP 层保持一致（站点/CDN 可能按 UA 判定）
        val userAgent = network.userAgent
        val uaOk = runCatching { handle.setPropertyString("user-agent", userAgent) }.getOrDefault(false)
        LogUtil.d(TAG, "mpv user-agent set=$uaOk")
    }

    /**
     * 把「MPV 高级设置」页的用户选项下发到 mpv。
     *
     * ## 时机：与 [applyNetworkOptions] 同批、且在 `setMediaData` 之前
     * 这些是"打开流那一刻"才读的选项（cache-secs / network-timeout / framedrop /
     * deband / interpolation …），必须在 load 之前写入。**每次 load 都重发一遍是刻意的**：
     * mediamp 的 player 跨视频复用（不会重建），只有重发才能让"改完设置 → 下一个视频
     * 即生效"，也才能把上一个视频留下的状态（如 display-resample）洗干净。
     *
     * ## 与 Android 侧 mpv 引擎（`androidMain/MpvPlaybackEngine.mpvOptions`，**已随
     * Gate3-P6 删除**）的三处必要差异
     *
     * 保留这张对照表是因为它记的是**桌面自己的取舍理由**（每条都仍然成立），
     * Android 侧那半已不存在，别按它去找代码。
     *
     * 1. **不下发 `vo`** —— `enableGpuNextRenderer` 在桌面**无法生效，故设置页隐藏该行**
     *    （`SettingsPlatformCapabilities.mpvVideoOutput = false`）。
     *    证据（反查 `mediamp-mpv-desktop-0.3.2.jar` 的 `JvmMpvMediampPlayer`）：
     *    它自己写死了 `vo=gpu-next` / `vo=libmpv` + `gpu-context` + `gpu-dumb-mode`
     *    的一整套组合，配合它自己的 render API 交付帧。改写 `vo` 会让渲染面收不到帧。
     *    换言之：桌面上"用 gpu-next"本就是底色，那个开关没有可翻转的余地。
     * 2. **`hwdec` 只有两档**：桌面没有 mediacodec / vulkan-copy，`HW`/`HW+`/`Vulkan`/
     *    `Vulkan+` 一律折叠成 mpv 的自动硬解（macOS=videotoolbox / Windows=d3d11va /
     *    Linux=vaapi）。与设置页收敛后的两档选项一致（`mpvMediacodecHwdec = false`）。
     *    注意 mediamp 自己也会设 `hwdec`（`OpenGLRenderContextLifecycle` 随渲染上下文
     *    创建设置），所以这里属于"覆盖它的默认值"。
     * 3. **不设 `vd-lavc-threads` / `cache` / `cache-pause`**：这三条在 Android 侧是解码器
     *    与缓冲调优（初始化前生效），桌面属 mediamp 的职责范围，抢过来只会和它的 render
     *    配置打架。
     *
     * ## 顺序即优先级
     * `profile` 最先（`gpu-hq` 会顺带改一串 `scale`/`deband` 缩放属性），随后逐项写入
     * 用户设置（覆盖 profile 的副作用值），最后 [parseMpvCustomParams] 收尾 —— 与 Android
     * 侧"`mpvOptions()` → `parseCustomMpvParams()`"的顺序一致，保证用户手写的
     * 「自定义参数」永远是最终裁决者。
     *
     * ## 失败不致命，逐条记录
     * mpv 有一部分选项只在初始化前可改，运行时下发会返回 false。这里不因单项失败中断播放，
     * 而是把 `set=false` 的项写进日志 —— 那就是"这一项在桌面不生效"的直接证据，不必猜。
     */
    private fun applyMpvSettings(handle: MPVHandle) {
        val o = mpvOptions()
        val options = buildMap {
            put(
                "profile",
                o.profile.takeIf { it == "gpu-hq" || it == "fast" } ?: "default",
            )
            put("hwdec", if (o.hwdec == "SW") "no" else "auto")
            // mpv 自己的日志走它的 `msg-level`，**绕不过我们的 LogUtil 门槛**，所以
            // 必须挂在同一开关上：默认只留 warn（否则 mpv 的 debug 级逐条输出会直接灌终端，
            // 例如 `[vd] Setting video-aspect-override to -1 is deprecated.`）。
            put("msg-level", "all=" + if (LogUtil.verboseEnabled) "debug" else "warn")
            put("cache-secs", o.cacheSecs.toString())
            put("framedrop", if (o.framedrop) "vo" else "no")
            put("deband", if (o.deband) "yes" else "no")
            put("network-timeout", o.networkTimeout.toString())
            // ⚠️ 字段名与文案相反（历史遗留，勿"修正"）：`tlsVerifyDisabled = true` 的 UI 文案是
            // 「忽略 HTTPS 证书验证」，对应 mpv 的 `tls-verify=no`。与 Android 侧同一约定。
            put("tls-verify", if (o.tlsVerifyDisabled) "no" else "yes")
            // 补帧：关掉时必须显式回落 `video-sync=audio`（mpv 默认值），否则上一次的
            // display-resample 会残留到下一个视频（player 跨视频复用）。
            put("interpolation", if (o.interpolation) "yes" else "no")
            if (o.interpolation) {
                put("tscale", "oversample")
                put("video-sync", "display-resample")
            } else {
                put("video-sync", "audio")
            }
            putAll(parseMpvCustomParams(o.customParams))
        }
        val rejected = mutableListOf<String>()
        options.forEach { (key, value) ->
            val ok = runCatching { handle.setPropertyString(key, value) }.getOrDefault(false)
            if (ok) {
                LogUtil.d(TAG, "mpv setting $key=$value")
            } else {
                rejected += "$key=$value"
            }
        }
        if (rejected.isNotEmpty()) {
            LogUtil.w(
                TAG,
                "mpv 拒绝运行时设置 ${rejected.size}/${options.size} 项：$rejected" +
                    "（多为只在初始化前生效的选项；自定义参数写错键名也会落到这里）",
            )
        }
    }

    /**
     * mediamp 把 mpv 句柄的 getter 标成 internal，JVM 名字被 mangled 成
     * `getHandle$mediamp_mpv`——Kotlin 源码里既不能直呼其名、也没法用反引号
     * 转义（`$` 不允许出现在标识符里），所以走反射。
     *
     * 反射失败（比如 mediamp 升级后改名）只会返回 null，随后由
     * [applySuperResolutionWithFallback] 的降级链把超分关掉，不会崩。
     */
    private fun mpvHandle(): MPVHandle? = runCatching {
        val method = mediampPlayer.javaClass.getMethod(MPV_HANDLE_GETTER)
        @Suppress("UNCHECKED_CAST")
        method.invoke(mediampPlayer) as? MPVHandle
    }.onFailure {
        LogUtil.w(TAG, "无法获取 mpv 句柄：${it.message}")
    }.getOrNull()

    /**
     * ⚠️ 必须留在**所有属性初始化之后**（Kotlin/JVM 按声明顺序执行属性初始化与 init 块）。
     *
     * 三个观察者都是 `scope.launch`，会立刻被调度到 `Dispatchers.Default`：若本块留在类顶部
     * （此前就是），协程可能在构造线程还没执行到 `requestedLevel` / `viewportSize` 的初始化
     * 语句时就读取它们 —— 字段仍是 JVM 默认值 `null`，传进 `safeCombine` 直接被非空形参
     * 检查拦下：
     * ```
     * NullPointerException: Parameter specified as non-null is null:
     *     method ...FlowCombineExtKt.safeCombine, parameter flow
     *     at DesktopMpvPlaybackEngine$startEnhancementObserver$1.invokeSuspend(...)
     * ```
     * 这是构造期竞态：窗口极小但真实（崩溃栈已捕获过一次），与"可空 Flow 未兜底"无关。
     */
    init {
        startObserving()
        // G2-3b：Stretch 档看门狗（默认 Fit 档下完全不碰 mpv，不会提前触发原生初始化）。
        startAspectWatch()
        startEnhancementObserver()
    }

    companion object {
        private const val TAG = "DesktopMpv"

        /** 传给 MpvMediampPlayerFactory 的中性 token（桌面端无 Context 概念）。 */
        private const val ENGINE_TOKEN = "LoveHan1meDesktop"

        /** mpv 新版"不覆写 DAR"的写法（旧值 `-1` 已废弃，见 [applyVideoAspect]）。 */
        private const val ASPECT_OVERRIDE_NONE = "no"

        /**
         * 超分会读写的 mpv 属性名 —— 同时也是"OFF 时要还原"的清单。
         *
         * 快照必须按这份名单去读，写入的是它的子集 + deband 系列；两边共用一份，
         * 才不会加了新属性却忘了还原，把放大滤镜永久留在 mpv 里。
         */
        private val ENHANCEMENT_PROPERTY_NAMES = listOf(
            "correct-downscaling",
            "linear-downscaling",
            "sigmoid-upscaling",
            "scale",
            "dscale",
            "cscale",
            "scale-antiring",
            "dscale-antiring",
            "deband",
            "deband-iterations",
            "deband-threshold",
            "deband-range",
            "deband-grain",
        )

        /** 超分开启时的属性：三向缩放都换 ewa_lanczossharp，配 0.7 抗振铃 + 轻 deband。 */
        private val FULL_PROFILE = mapOf(
            "correct-downscaling" to "yes",
            "linear-downscaling" to "yes",
            "sigmoid-upscaling" to "yes",
            "scale" to "ewa_lanczossharp",
            "dscale" to "ewa_lanczossharp",
            "cscale" to "ewa_lanczossharp",
            "scale-antiring" to "0.7",
            "dscale-antiring" to "0.7",
            "deband" to "yes",
            "deband-iterations" to "1",
            "deband-threshold" to "32",
            "deband-range" to "16",
            "deband-grain" to "0",
        )

        /** Windows 轻量档：只省掉 deband（那里的硬解路径上它是实打实的全屏额外一趟）。 */
        private val WINDOWS_LITE_PROFILE = FULL_PROFILE + ("deband" to "no")

        private fun isWindowsDesktop(): Boolean =
            System.getProperty("os.name").orEmpty().contains("windows", ignoreCase = true)

        /** mediamp 里 mpv 句柄 getter 的 JVM 名字（internal 成员被 mangled）。 */
        private const val MPV_HANDLE_GETTER = "getHandle\$mediamp_mpv"

        /**
         * 启动预载：把 mediamp 的 mpv 原生运行时在**后台线程**提前消化。
         *
         * 这是"点开第一个视频卡冻结 2-3 分钟"的根因修复：此前解压 + dlopen 的
         * 大成本在首次进视频页时由 EDT 承担，期间重组、数据到货的 collect、
         * 一切输入全部停摆。预载后用户点进视频页时原生库已在进程内，引擎
         * 惰性初始化只剩 mpv_create（几十毫秒，且在后台线程）。
         *
         * ## 为什么是"固定缓存目录"而不是临时目录
         * mediamp 默认每次进程运行都 `Files.createTempDirectory("mediamp-mpv")`
         * 解压 ~50MB dylib 进新目录并 `deleteOnExit` —— 解压是**每次运行**都逃
         * 不掉、跟 dlopen 叠加才有的 10~15s。
         *
         * 这里把运行时目录固定在 `~/.lovehan1me/mpv_runtime`：
         * - 首次：解压一次 + dlopen（后台线程，首页不受阻）；
         * - 以后每次启动：产物已存在 → 直接 load，**省掉解压**，只剩 dlopen。
         *   于是"构建后首次点开卡顿"不再逐次复现（除真·首次装机）。
         *
         * ## 实现
         * mediamp 的 `LibraryLoader.setRuntimeLibraryDirectory(path, extractIfNeeded)`
         * 在 Kotlin 编译层是 internal（这正是源码里用 create-temp-player 走公开 API
         * 的原因），故用反射调用它来固定目录。反射失败只记一行日志，退回
         * 原临时目录复用创建临时 player 的公开路径兜底 —— 功能不受损，只是
         * 每次运行仍要重新解压。两套路径都 safe。
         */
        fun preloadAsync() {
            thread(name = "mpv-native-preload", isDaemon = true) {
                val startedAt = System.currentTimeMillis()
                val cacheDir = mpvRuntimeDirectory()
                val hadExisting = cacheDir.resolve(
                    "libmpv.dylib",
                ).isFile // 粗判：上次是否已解压过
                runCatching {
                    try {
                        // 反射：固定运行时目录并触发"解压(首次)/直接 load(复用)+dlopen"。
                        // extractIfNeeded=true：产物缺失时才解压（见 LibraryLoader.desktop.kt）。
                        val cls = Class.forName("org.openani.mediamp.mpv.LibraryLoader")
                        val instance = cls.getField("INSTANCE").get(null)
                        val method = cls.getMethod(
                            "setRuntimeLibraryDirectory",
                            String::class.java,
                            Boolean::class.javaPrimitiveType,
                        )
                        method.invoke(instance, cacheDir.absolutePath, true)
                    } catch (reflEx: Throwable) {
                        LogUtil.w(
                            TAG,
                            "mpv 反射固定目录不可用（mediamp 升级？退回临时目录路径）: ${reflEx.message}",
                        )
                        // 兜底：公开 API 创建临时 player，仍能达成"原生库已入进程"。
                        val preloadScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
                        try {
                            MpvMediampPlayerFactory()
                                .create(ENGINE_TOKEN, preloadScope.coroutineContext)
                                .close()
                        } finally {
                            preloadScope.cancel()
                        }
                    }
                }.onSuccess {
                    LogUtil.i(
                        TAG,
                        "mpv natives preloaded in ${System.currentTimeMillis() - startedAt}ms"
                            + if (hadExisting) "（复用缓存，豁免解压）" else "（首次解压）",
                    )
                }.onFailure {
                    LogUtil.w(TAG, "mpv preload failed（视频页将退回懒加载）: ${it.message}")
                }
            }
        }

        /** mpv 原生库固定缓存目录：首次解压后跨**进程**运行复用（见 [preloadAsync]）。 */
        private fun mpvRuntimeDirectory(): java.io.File {
            val dir = java.io.File(System.getProperty("user.home"), ".lovehan1me/mpv_runtime")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }
    }
}

// `mediaUrlForGate` / `resolveMediaProxyUrl` 已搬入 `:shared` 的 `data.network`
//（`PlayerWiring.desktop.kt` + `gateRewrite`）：网关判定与代理选择是应用层策略，
// 不属于引擎内核。引擎经 [PlayerNetworkConfig] 拿结果，不再直读网关与选择器。

/**
 * 分辨率门控：两条边都放大不到 1 倍时这档没意义（Anime4K 是放大链，等比或缩着放
 * 只会白烧 GPU），整档按 [MpvShaders.OFF] 处理。
 *
 * 尺寸来源是渲染面（Compose 侧回调），不是 mpv 的属性 —— `dwidth`/`dheight` 语义是
 * 视频显示尺寸，拿它和片源尺寸比会得到恒定 1.0 附近的比值，把每一档都判死。
 * 任一尺寸还不知道时**不门控**：宁可多花算力，也不静默关掉用户显式打开的功能。
 */
internal fun gateEnhancementByScale(
    requested: Int,
    videoWidth: Int,
    videoHeight: Int,
    viewportWidth: Int,
    viewportHeight: Int,
): Int {
    if (requested == MpvShaders.OFF) return MpvShaders.OFF
    if (videoWidth <= 0 || videoHeight <= 0 || viewportWidth <= 0 || viewportHeight <= 0) return requested
    val scale = minOf(
        viewportWidth.toDouble() / videoWidth,
        viewportHeight.toDouble() / videoHeight,
    )
    return if (scale > 1.0) requested else MpvShaders.OFF
}