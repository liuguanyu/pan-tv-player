package com.baidu.tv.player.kt.ui.playback

import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * TextureView 可见性状态测试（Task 13.2）。
 *
 * 验证视频播放期间 TextureView 的 visibility / alpha 契约：
 * - 缓冲阶段：VISIBLE + alpha=0（保持 SurfaceTexture 但隐藏画面）
 * - 渲染成功：VISIBLE + alpha=1（显示画面）
 *
 * TextureView 不能设为 GONE，否则 SurfaceTexture 会被释放导致视频永远无法启动。
 */
class VideoSurfaceStateTest {

    @Test
    fun buffering_stateIsVisibleWithZeroAlpha() {
        val state = VideoSurfaceState.BUFFERING
        assertEquals(View.VISIBLE, state.visibility)
        assertEquals(0f, state.alpha)
    }

    @Test
    fun rendering_stateIsVisibleWithOneAlpha() {
        val state = VideoSurfaceState.RENDERING
        assertEquals(View.VISIBLE, state.visibility)
        assertEquals(1f, state.alpha)
    }

    @Test
    fun bothStates_keepTextureViewVisible() {
        // TextureView 必须始终 VISIBLE 才能持有 SurfaceTexture。
        for (state in VideoSurfaceState.entries) {
            assertEquals(
                "state $state must keep TextureView VISIBLE",
                View.VISIBLE,
                state.visibility,
            )
        }
    }

    @Test
    fun alphaTransitionsFromZeroToOne() {
        // 缓冲时透明，渲染后不透明。
        assertEquals(0f, VideoSurfaceState.BUFFERING.alpha)
        assertEquals(1f, VideoSurfaceState.RENDERING.alpha)
    }
}
