package com.baidu.tv.player.kt.ui.playback.image

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.roundToInt

/**
 * 图片百叶窗转场 View（迁移自 Java 版 BlindsImageView）。
 *
 * - 固定 8 条百叶窗。
 * - 动画进度 0f → 1f，耗时 1000ms。
 * - 偶数条从左到右展开，奇数条从右到左展开。
 * - 动画条带内仍调用 [AppCompatImageView.onDraw]，确保 scaleType / imageMatrix / padding
 *   与普通图片显示完全一致。
 * - 非动画状态回退 [AppCompatImageView.onDraw]，保持普通 ImageView 行为。
 */
class BlindsImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private var blindsProgress = 1f
    private var animator: ValueAnimator? = null
    private var isBlindsAnimating = false

    override fun onDraw(canvas: Canvas) {
        if (!isBlindsAnimating || blindsProgress >= 1f) {
            super.onDraw(canvas)
            return
        }

        val drawable = drawable ?: return super.onDraw(canvas)
        val viewWidth = width
        val viewHeight = height
        if (viewWidth <= 0 || viewHeight <= 0) return super.onDraw(canvas)

        val blindHeight = viewHeight.toFloat() / BLINDS_COUNT
        for (index in 0 until BLINDS_COUNT) {
            val top = (index * blindHeight).roundToInt()
            val bottom = ((index + 1) * blindHeight).roundToInt().coerceAtMost(viewHeight)
            val revealWidth = (viewWidth * blindsProgress).roundToInt()
            val rect = if (index % 2 == 0) {
                Rect(0, top, revealWidth, bottom)
            } else {
                Rect(viewWidth - revealWidth, top, viewWidth, bottom)
            }
            if (rect.width() <= 0 || rect.height() <= 0) continue

            canvas.save()
            canvas.clipRect(rect)
            super.onDraw(canvas)
            canvas.restore()
        }
    }

    fun startBlindsAnimation() {
        stopBlindsAnimation()
        blindsProgress = 0f
        isBlindsAnimating = true
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ANIMATION_DURATION_MS
            addUpdateListener { valueAnimator ->
                blindsProgress = valueAnimator.animatedValue as Float
                invalidate()
            }
            doOnEndCompat {
                blindsProgress = 1f
                isBlindsAnimating = false
                invalidate()
            }
            start()
        }
    }

    fun stopBlindsAnimation() {
        animator?.cancel()
        animator = null
        isBlindsAnimating = false
    }

    fun resetBlinds() {
        stopBlindsAnimation()
        blindsProgress = 1f
        invalidate()
    }

    override fun onDetachedFromWindow() {
        stopBlindsAnimation()
        super.onDetachedFromWindow()
    }

    companion object {
        const val BLINDS_COUNT = 8
        const val ANIMATION_DURATION_MS = 1_000L
    }
}

private inline fun ValueAnimator.doOnEndCompat(crossinline action: () -> Unit) {
    addListener(
        object : android.animation.Animator.AnimatorListener {
            override fun onAnimationStart(animation: android.animation.Animator) = Unit
            override fun onAnimationEnd(animation: android.animation.Animator) = action()
            override fun onAnimationCancel(animation: android.animation.Animator) = Unit
            override fun onAnimationRepeat(animation: android.animation.Animator) = Unit
        },
    )
}
