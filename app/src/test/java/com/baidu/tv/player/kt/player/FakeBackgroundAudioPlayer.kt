package com.baidu.tv.player.kt.player

/**
 * 测试用 [BackgroundAudioPlayer] 假身（Phase 5.1）。
 *
 * 记录所有调用序列，供测试断言 play/pause/stop/release 的调用顺序与参数。
 * 同时模拟"相同 URL 不重新 prepare"的语义：只有 URL 变化时才记录 prepare 事件。
 */
class FakeBackgroundAudioPlayer : BackgroundAudioPlayer {

    /** 单条调用记录。 */
    sealed class Call {
        data class Play(val url: String, val headers: Map<String, String>) : Call()
        data object Pause : Call()
        data object Stop : Call()
        data object Release : Call()
        /** prepare 事件——仅在 URL 变化时由 play 内部触发。 */
        data class Prepare(val url: String) : Call()
    }

    private val _calls = mutableListOf<Call>()
    val calls: List<Call> get() = _calls

    /** prepare 次数（URL 变化导致重新 prepare 的次数）。 */
    val prepareCount: Int get() = _calls.count { it is Call.Prepare }

    private var currentUrl: String? = null
    private var released = false

    override fun play(url: String, headers: Map<String, String>) {
        if (released) return
        _calls.add(Call.Play(url, headers))
        if (currentUrl != url) {
            _calls.add(Call.Prepare(url))
            currentUrl = url
        }
    }

    override fun pause() {
        if (released) return
        _calls.add(Call.Pause)
    }

    override fun stop() {
        if (released) return
        _calls.add(Call.Stop)
        currentUrl = null
    }

    override fun release() {
        if (released) return
        released = true
        _calls.add(Call.Release)
    }
}
