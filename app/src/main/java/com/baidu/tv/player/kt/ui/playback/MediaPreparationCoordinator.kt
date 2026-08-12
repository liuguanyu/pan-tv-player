package com.baidu.tv.player.kt.ui.playback

import android.util.Log
import com.baidu.tv.player.kt.auth.BaiduAuthService
import com.baidu.tv.player.kt.model.FileInfo
import com.baidu.tv.player.kt.model.MediaType
import com.baidu.tv.player.kt.model.PlaybackHistory
import com.baidu.tv.player.kt.repository.FileRepository
import com.baidu.tv.player.kt.repository.PlayableUrlResolver
import com.baidu.tv.player.kt.repository.PlaybackHistoryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 媒体准备结果回调（Phase 9）。
 *
 * 由 [PlaybackViewModel] 实现，在 [MediaPreparationCoordinator.prepare] 成功或失败时
 * 被回调。回调只在当前 generation 仍然有效时才被调用，因此实现方可以安全地
 * 更新 UI 状态和发出播放事件，无需再检查是否陈旧。
 */
interface PrepareCallback {
    /**
     * 当前 generation 的 URL 解析成功。
     *
     * @param file  本次准备的文件。
     * @param url   解析得到的可播放 URL（已拼接 access_token）。
     */
    suspend fun onPrepareSuccess(file: FileInfo, url: String)

    /**
     * 当前 generation 的 URL 解析失败。
     *
     * @param file     本次准备的文件。
     * @param message  面向用户的错误消息。
     */
    suspend fun onPrepareFailure(file: FileInfo, message: String)
}

/**
 * 媒体准备协调器（Phase 9）。
 *
 * 将 [PlaybackViewModel] 的 `prepareAndEmitCurrent` 和 `preloadNextFile` 提取为独立组件，
 * 用**单调 generation 计数器**解决"旧请求晚到覆盖新选择"的竞态：
 *
 * 1. 每次 [prepare] 调用通过 [nextGeneration] 获取一个新的 generation。
 * 2. 只有**当前 generation**（即最大已发出且未被取消的 generation）的结果才能回调。
 * 3. 当新的 [prepare] 启动时，旧的 generation 被取消，其协程被 `cancel`，
 *    即便旧协程的 `resolve` 因网络延迟晚到完成，回调也不会触发。
 *
 * 其他语义约束（不可回归）：
 * - **不使用 `runCatching`**：所有异常用 `try/catch` 处理，[CancellationException] 重新抛出。
 * - **预加载只写缓存**：[preloadNextFile] 不修改 `currentIndex`/`currentFile`，
 *   只把解析结果写入 [dlinkCache]，失败静默。
 * - **预加载去重**：同一 fsId 不重复请求；通过 [preloadMutex] 串行化避免并发重复。
 * - **历史写库失败不中断播放**：`insertHistory` 捕获非取消异常并记录日志。
 * - HTTP 头由调用方通过 [PlayableUrlResolver] 控制（`User-Agent: pan.baidu.com`，无超时、无缓存）。
 *
 * 线程安全：所有方法应在同一线程（主线程）调用。内部 [scope] 由调用方注入
 * （通常是 ViewModel 的 `viewModelScope`），取消由调用方控制。
 */
@Singleton
class MediaPreparationCoordinator @Inject constructor(
    private val urlResolver: PlayableUrlResolver,
    private val historyRepository: PlaybackHistoryRepository,
    private val authService: BaiduAuthService,
    private val fileRepository: FileRepository,
) {
    /**
     * dlink 缓存：fsId → 已拼接 access_token 的可播放 URL。
     *
     * 预加载和当前准备都写入此缓存；切换到已缓存文件时直接复用，不重新请求。
     */
    internal val dlinkCache = LinkedHashMap<Long, String>()

    /**
     * 文件详情缓存：fsId → 完整 [FileInfo]（含 thumbs）。
     *
     * 仅在文件无 dlink 需要拉取详情时填充，为历史记录封面留存 thumbs。
     */
    internal val fileDetailCache = LinkedHashMap<Long, FileInfo>()

    private val generation = AtomicLong(0L)
    /** 当前生效的 generation，只有匹配此值的 prepare 回调才能生效。 */
    private var currentGeneration = 0L
    private var prepareJob: Job? = null

    private val preloadMutex = Mutex()
    private var preloadJob: Job? = null

    /**
     * 分配并返回一个新的 generation，同时取消旧 generation 的准备协程。
     *
     * 每次切换/重试当前媒体前调用。返回值应传给 [prepare]。
     */
    fun nextGeneration(): Long {
        val g = generation.incrementAndGet()
        currentGeneration = g
        cancelPrepareJob()
        return g
    }

    /** 返回当前生效的 generation。 */
    fun currentGeneration(): Long = currentGeneration

    /**
     * 准备当前媒体并回调。
     *
     * 语义：
     * 1. 若 [generation] 不等于当前已发出的 generation（被更新的请求取代），直接返回。
     * 2. 解析 URL（先查 [dlinkCache]，再查 [fileDetailCache]，最后走 [urlResolver]）。
     * 3. 成功 → 写入缓存 → 回调 [PrepareCallback.onPrepareSuccess]。
     * 4. 失败 → 回调 [PrepareCallback.onPrepareFailure]。
     * 5. 解析成功后异步写历史（失败不中断）。
     * 6. 成功后触发 [preloadNextFile] 预加载下一项。
     *
     * **不使用 `runCatching`**：[CancellationException] 重新抛出。
     *
     * @param file         要准备的文件。
     * @param gen          本次调用的 generation（来自 [nextGeneration]）。
     * @param scope        协程作用域。
     * @param uiStateRef   UI 状态流（用于读取写历史所需的来源上下文）。
     * @param callback     结果回调。
     */
    fun prepare(
        file: FileInfo,
        gen: Long,
        scope: CoroutineScope,
        uiStateRef: MutableStateFlow<PlaybackUiState>,
        callback: PrepareCallback,
    ) {
        if (gen != currentGeneration) return
        cancelPrepareJob()
        prepareJob = scope.launch {
            if (gen != currentGeneration) return@launch
            try {
                val url = resolvePlayableUrl(file)
                if (gen != currentGeneration) return@launch
                dlinkCache[file.fsId] = url
                callback.onPrepareSuccess(file, url)
                insertHistory(file, uiStateRef.value)
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                if (gen != currentGeneration) return@launch
                val msg = e.message ?: "获取播放链接失败"
                callback.onPrepareFailure(file, msg)
            }
        }
    }

    /**
     * 预加载下一项的 dlink。
     *
     * - 不修改 `currentIndex`/`currentFile`。
     * - 若 [dlinkCache] 已有该 fsId，直接返回。
     * - 通过 [preloadMutex] 串行化，避免并发重复请求。
     * - 失败静默（仅写缓存，不影响当前媒体）。
     * - **不使用 `runCatching`**：[CancellationException] 重新抛出。
     *
     * @param scope       协程作用域。
     * @param nextFile    要预加载的文件；若 null 则不操作。
     */
    fun preloadNextFile(
        scope: CoroutineScope,
        nextFile: FileInfo?,
    ) {
        if (nextFile == null) return
        preloadJob?.cancel()
        preloadJob = scope.launch {
            preloadMutex.withLock {
                if (dlinkCache.containsKey(nextFile.fsId)) return@withLock
                try {
                    val url = resolvePlayableUrl(nextFile)
                    dlinkCache[nextFile.fsId] = url
                } catch (c: CancellationException) {
                    throw c
                } catch (e: Exception) {
                    // 预加载失败静默：不影响当前播放，下次切换时会按需重新解析。
                    Log.w(TAG, "预加载失败: ${nextFile.serverFilename.orEmpty()}", e)
                }
            }
        }
    }

    /** 取消正在进行的预加载协程。 */
    fun cancelPreload() {
        preloadJob?.cancel()
        preloadJob = null
    }

    /** 等待预加载完成（测试用）。 */
    internal suspend fun awaitPreloadForTest() {
        preloadJob?.join()
    }

    /** 取消正在进行的准备协程。 */
    private fun cancelPrepareJob() {
        prepareJob?.cancel()
        prepareJob = null
    }

    /**
     * 解析可播放 URL，复用缓存。
     *
     * 1. [dlinkCache] 命中 → 直接返回。
     * 2. 文件自带 dlink → 用它。
     * 3. [fileDetailCache] 命中 → 用其 dlink。
     * 4. 拉取详情并缓存 → 用其 dlink。
     * 5. 调用 [urlResolver] 拼接 access_token。
     */
    private suspend fun resolvePlayableUrl(file: FileInfo): String {
        dlinkCache[file.fsId]?.let { return it }
        val dlink = file.dlink?.takeIf { it.isNotBlank() }
            ?: fileDetailCache[file.fsId]?.dlink?.takeIf { it.isNotBlank() }
            ?: fetchAndCacheFileDetail(file.fsId)?.dlink?.takeIf { it.isNotBlank() }
        val url = urlResolver.resolve(file.fsId, dlink, file.serverFilename)
        dlinkCache[file.fsId] = url
        return url
    }

    private suspend fun fetchAndCacheFileDetail(fsId: Long): FileInfo? {
        val token = authService.getAccessToken().orEmpty()
        check(token.isNotEmpty()) { "未获取到访问令牌，请先登录" }
        return fileRepository.fetchFileDetail(token, fsId)?.also { fileDetailCache[fsId] = it }
    }

    /**
     * 记录当前播放文件到最近播放（文件级）。
     *
     * - 失败不中断播放：捕获非 [CancellationException] 异常并记录日志。
     * - [CancellationException] 重新抛出。
     */
    private suspend fun insertHistory(file: FileInfo, state: PlaybackUiState) {
        val filePath = file.path?.takeIf { it.isNotBlank() }
            ?: file.serverFilename?.let { name ->
                val folder = state.folderPath.trimEnd('/')
                if (folder.isNotEmpty()) "$folder/$name" else "/$name"
            }
            ?: return
        val fileName = file.serverFilename ?: filePath.substringAfterLast('/')
        val mediaType = if (file.isVideo()) MediaType.VIDEO.code else MediaType.IMAGE.code
        val thumbs = file.thumbs ?: fileDetailCache[file.fsId]?.thumbs
        val cover = sequenceOf(thumbs?.icon, thumbs?.url1, thumbs?.url2, thumbs?.url3)
            .firstOrNull { url -> !url.isNullOrBlank() && !url.contains("/file/") }

        try {
            historyRepository.insert(
                PlaybackHistory(
                    folderPath = filePath,
                    folderName = fileName,
                    mediaType = mediaType,
                    fileCount = 1,
                    createTime = System.currentTimeMillis(),
                    coverImagePath = cover,
                    sourcePlaylistId = state.sourcePlaylistId,
                    sourceFolderPath = state.sourceFolderPath,
                    fsId = file.fsId,
                ),
            )
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            Log.e(TAG, "更新最近播放失败: $filePath", e)
        }
    }

    private companion object {
        const val TAG = "MediaPrepCoordinator"
    }
}
