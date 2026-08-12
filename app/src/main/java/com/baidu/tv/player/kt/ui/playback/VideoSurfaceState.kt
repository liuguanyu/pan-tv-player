package com.baidu.tv.player.kt.ui.playback

import android.view.View

/**
 * TextureView 可见性与透明度状态（Phase 13.2）。
 *
 * 视频播放期间 TextureView 必须始终保持 [View.VISIBLE]（否则 SurfaceTexture 会被释放，
 * 导致 `isAvailable` 持续为 false，视频永远无法启动）。画面透明度用于区分缓冲与渲染：
 * - [BUFFERING]：加载中，画面透明（alpha=0），只显示 loading 转圈和背景。
 * - [RENDERING]：首帧已渲染，画面不透明（alpha=1）。
 */
internal enum class VideoSurfaceState(val visibility: Int, val alpha: Float) {
    BUFFERING(View.VISIBLE, 0f),
    RENDERING(View.VISIBLE, 1f),
}
