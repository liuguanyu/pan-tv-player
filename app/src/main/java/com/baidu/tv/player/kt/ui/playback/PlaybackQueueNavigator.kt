package com.baidu.tv.player.kt.ui.playback

import com.baidu.tv.player.kt.model.PlayMode
import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * 播放队列导航器（Phase 8）。
 *
 * 纯 Kotlin 组件，封装四种播放模式下的队列导航逻辑：
 * - [PlayMode.SEQUENTIAL]：顺序播放，到末尾后回到开头。
 * - [PlayMode.REVERSE]：倒序播放，到开头后跳到末尾。
 * - [PlayMode.SINGLE]：单曲循环，始终返回当前索引。
 * - [PlayMode.RANDOM]：随机播放，维护一个洗牌队列，队列耗尽后重新填充。
 *
 * 该组件不依赖 Android、Room 或网络，不修改传入的播放列表。
 * 随机队列的内部状态（[randomQueue]）是该组件唯一的可变状态，
 * 调用方在播放模式切换或列表重建时应调用 [clearRandomQueue]。
 *
 * @param random 随机源，可注入以便测试。
 */
@Singleton
class PlaybackQueueNavigator internal constructor(
    private val random: Random,
) {

    @Inject
    constructor() : this(Random(System.currentTimeMillis()))

    private val randomQueue = ArrayDeque<Int>()

    /**
     * 计算下一个播放索引。
     *
     * @param size        播放列表大小。
     * @param currentIndex 当前播放索引。
     * @param playMode    当前播放模式。
     * @param forward     true 表示前进（下一首），false 表示后退（上一首）。
     * @return 下一个索引，若列表为空则返回 null。
     */
    fun nextIndex(size: Int, currentIndex: Int, playMode: PlayMode, forward: Boolean): Int? {
        if (size <= 0) return null
        if (size == 1) return 0
        return when (playMode) {
            PlayMode.SINGLE -> currentIndex
            PlayMode.SEQUENTIAL ->
                if (forward) (currentIndex + 1) % size
                else (currentIndex - 1 + size) % size
            PlayMode.REVERSE ->
                if (forward) (currentIndex - 1 + size) % size
                else (currentIndex + 1) % size
            PlayMode.RANDOM -> nextRandomIndex(size, currentIndex)
        }
    }

    /**
     * 计算上一个播放索引（等价于 [nextIndex] 且 `forward = false`）。
     *
     * @param size        播放列表大小。
     * @param currentIndex 当前播放索引。
     * @param playMode    当前播放模式。
     * @return 上一个索引，若列表为空则返回 null。
     */
    fun previousIndex(size: Int, currentIndex: Int, playMode: PlayMode): Int? =
        nextIndex(size, currentIndex, playMode, forward = false)

    /**
     * 根据播放模式解析会话起始索引。
     *
     * - [PlayMode.RANDOM]：返回一个 `[0, size)` 范围内的随机索引。
     * - [PlayMode.REVERSE]：返回 `size - 1`（从最后一项开始）。
     * - 其他模式：返回 [preferredIndex] 限制在 `[0, size - 1]` 范围内的值。
     *
     * @param size           播放列表大小。
     * @param preferredIndex 调用方首选起始索引。
     * @param playMode       当前播放模式。
     * @return 起始索引。
     */
    fun initialIndex(size: Int, preferredIndex: Int, playMode: PlayMode): Int {
        if (size <= 1) return preferredIndex.coerceIn(0, maxOf(0, size - 1))
        return when (playMode) {
            PlayMode.RANDOM -> random.nextInt(size)
            PlayMode.REVERSE -> size - 1
            else -> preferredIndex.coerceIn(0, size - 1)
        }
    }

    /**
     * 清空随机队列缓存。
     *
     * 在播放模式切换或播放列表重建时调用，确保新的随机序列不沿用旧队列。
     */
    fun clearRandomQueue() {
        randomQueue.clear()
    }

    // ------------------------------------------------------------------
    // 随机索引
    // ------------------------------------------------------------------

    private fun nextRandomIndex(size: Int, currentIndex: Int): Int {
        if (randomQueue.isEmpty()) {
            val candidates = (0 until size).filter { it != currentIndex }.shuffled(random)
            randomQueue.addAll(candidates)
        }
        return randomQueue.pollFirst() ?: ((currentIndex + 1) % size)
    }
}
