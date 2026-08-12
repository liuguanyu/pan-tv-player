package com.baidu.tv.player.kt.player

import android.util.Log
import com.baidu.tv.player.kt.repository.BgmSelection
import com.baidu.tv.player.kt.repository.PlayableUrlResolver
import com.baidu.tv.player.kt.ui.playback.PlaybackUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val BGM_TAG = "BgmCoordinator"

/**
 * BGM 协调器状态机（Phase 6）。
 *
 * - [Disabled]：无选择或已清空。
 * - [Resolving]：正在解析 URL。
 * - [Ready]：URL 已就绪，可按决策驱动播放器。
 * - [Failed]：解析失败，可通过再次 [BackgroundMusicCoordinator.setSelection] 重试。
 */
sealed interface BgmState {
    data object Disabled : BgmState
    data class Resolving(val fsId: Long) : BgmState
    data class Ready(val fsId: Long, val url: String) : BgmState
    data class Failed(val fsId: Long, val error: String) : BgmState
}

/**
 * 拥有全部 BGM 状态并驱动 [BackgroundAudioPlayer] 的协调器（Phase 6）。
 *
 * 从 [com.baidu.tv.player.kt.ui.playback.PlaybackActivity] 提取所有 BGM 字段与
 * `syncBackgroundMusic` 逻辑，使 BGM 状态可独立测试、可注入。
 *
 * 语义约束（不可回归）：
 * - [CancellationException] 必须传播，绝不吞掉。不使用 `runCatching`。
 * - 相同 URL 再次 play 不重新 prepare（由 [BackgroundAudioPlayer] 实现保证）。
 * - `bgmSelectionKey` 仅在解析成功后才更新，失败后可重试。
 * - HTTP 头由调用方通过 [setSelection] 的 `headers` 参数传入，通常为
 *   `mapOf("User-Agent" to "pan.baidu.com")`。
 *
 * 线程安全：所有方法应在同一线程（主线程）调用。内部 [scope] 用于解析协程。
 */
@Singleton
class BackgroundMusicCoordinator @Inject constructor(
    private val resolver: PlayableUrlResolver,
    private val player: BackgroundAudioPlayer,
) {
    /** 可注入的协程作用域，[release] 时取消。测试可通过 [withScope] 替换。 */
    private var scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<BgmState>(BgmState.Disabled)
    /** 当前 BGM 状态，供测试观察。 */
    val state: StateFlow<BgmState> = _state.asStateFlow()

    private var selection: BgmSelection? = null
    private var resolvedUrl: String? = null
    private var resolveJob: Job? = null
    private var isForeground: Boolean = true
    private var lastPlaybackState: PlaybackUiState = PlaybackUiState()
    private var headers: Map<String, String> = emptyMap()

    /** 测试专用：替换协程作用域。必须在任何业务方法调用前执行。 */
    internal fun withScope(testScope: CoroutineScope): BackgroundMusicCoordinator {
        scope = testScope
        return this
    }

    /**
     * 更新 BGM 选择。
     *
     * - `selection == null` → 停止播放器，状态置为 [BgmState.Disabled]。
     * - `selection.fsId` 与当前已就绪的不同 → 取消旧解析，启动新解析。
     * - `selection.fsId` 与当前已就绪相同 → 不重新解析，直接按当前状态驱动。
     *
     * @param headers HTTP 请求头，存入协调器供后续 play 调用使用。
     */
    fun setSelection(selection: BgmSelection?, headers: Map<String, String> = this.headers) {
        this.headers = headers
        this.selection = selection
        if (selection == null) {
            resolveJob?.cancel()
            resolveJob = null
            resolvedUrl = null
            _state.value = BgmState.Disabled
            player.stop()
            return
        }
        val current = _state.value
        if (current is BgmState.Ready && current.fsId == selection.fsId) {
            // 同一选择已就绪，直接按当前播放状态驱动。
            resolvedUrl = current.url
            applyAction()
            return
        }
        if (current is BgmState.Resolving && current.fsId == selection.fsId) {
            // 正在解析同一选择，不重复启动。
            return
        }
        // 新选择或重试：取消旧解析，启动新解析。
        resolveJob?.cancel()
        resolvedUrl = null
        player.stop()
        _state.value = BgmState.Resolving(selection.fsId)
        resolveJob = scope.launch {
            try {
                val url = resolver.resolve(selection.fsId, dlink = null, serverFilename = null)
                resolvedUrl = url
                _state.value = BgmState.Ready(selection.fsId, url)
                applyAction()
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                Log.w(BGM_TAG, "背景音乐加载失败", e)
                _state.value = BgmState.Failed(selection.fsId, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** 播放状态变化时，按决策函数驱动播放器。 */
    fun onPlaybackStateChanged(state: PlaybackUiState) {
        lastPlaybackState = state
        applyAction()
    }

    /** 前后台变化时更新前台标记并重新驱动。 */
    fun onForegroundChanged(isForeground: Boolean) {
        this.isForeground = isForeground
        applyAction()
    }

    /** 直接暂停播放器（用于 playVideo 等不经过状态机的场景）。 */
    fun pause() {
        player.pause()
    }

    /**
     * 停止 BGM 播放和解析，但保留协程作用域和播放器实例。
     *
     * 用于 Activity onDestroy：因为协调器是 @Singleton，下一个 Activity 实例
     * 会复用同一实例，不能永久取消 scope 或释放 player。
     */
    fun stop() {
        resolveJob?.cancel()
        resolveJob = null
        player.stop()
        _state.value = BgmState.Disabled
        resolvedUrl = null
        selection = null
    }

    /** 释放资源：取消协程、释放播放器。幂等。仅用于进程级清理。 */
    fun release() {
        resolveJob?.cancel()
        resolveJob = null
        scope.cancel()
        player.release()
        _state.value = BgmState.Disabled
        resolvedUrl = null
        selection = null
    }

    /** 根据当前状态和决策函数驱动播放器。 */
    private fun applyAction() {
        val sel = selection ?: return
        val url = resolvedUrl
        val state = lastPlaybackState
        if (url == null) {
            // 解析中或失败：若选择存在但未就绪，用 NONE/STOP 语义。
            val action = computeBgmAction(
                state = state,
                hasBgmSelection = true,
                isBgmResolved = false,
                isForeground = isForeground,
            )
            when (action) {
                BgmTargetAction.STOP -> player.stop()
                else -> { /* NONE：不触碰播放器 */ }
            }
            return
        }
        val action = computeBgmAction(
            state = state,
            hasBgmSelection = true,
            isBgmResolved = true,
            isForeground = isForeground,
        )
        when (action) {
            BgmTargetAction.PLAY -> player.play(url, headers)
            BgmTargetAction.PAUSE -> player.pause()
            BgmTargetAction.STOP -> player.stop()
            BgmTargetAction.NONE -> { }
        }
    }
}
