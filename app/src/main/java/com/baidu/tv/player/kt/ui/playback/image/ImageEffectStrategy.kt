package com.baidu.tv.player.kt.ui.playback.image

import android.view.View
import android.view.animation.BounceInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import com.baidu.tv.player.kt.model.ImageEffect

/** 图片转场特效策略（对应 tasks 6.8-6.16）。 */
sealed interface ImageEffectStrategy {
    val effect: ImageEffect
    val durationMs: Long

    fun apply(target: View)

    fun reset(target: View) {
        target.animate().cancel()
        target.alpha = 1f
        target.scaleX = 1f
        target.scaleY = 1f
        target.translationX = 0f
        target.translationY = 0f
        target.rotation = 0f
        (target as? BlindsImageView)?.resetBlinds()
    }
}

/** 淡入淡出：原 Java 由 Glide CrossFade 承担，此处补充 alpha 动画，便于 Activity 统一触发。 */
data object FadeEffectStrategy : ImageEffectStrategy {
    override val effect: ImageEffect = ImageEffect.FADE
    override val durationMs: Long = 800L

    override fun apply(target: View) {
        reset(target)
        target.alpha = 0f
        target.animate()
            .alpha(1f)
            .setDuration(durationMs)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }
}

data object EaseEffectStrategy : ImageEffectStrategy {
    override val effect: ImageEffect = ImageEffect.EASE
    override val durationMs: Long = 1_000L

    override fun apply(target: View) {
        reset(target)
        target.translationX = 80f
        target.animate()
            .translationX(0f)
            .setDuration(durationMs)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }
}

data object FloatEffectStrategy : ImageEffectStrategy {
    override val effect: ImageEffect = ImageEffect.FLOAT
    override val durationMs: Long = 5_000L

    override fun apply(target: View) {
        reset(target)
        target.scaleX = 1f
        target.scaleY = 1f
        target.animate()
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(durationMs)
            .setInterpolator(LinearInterpolator())
            .start()
    }
}

data object BounceEffectStrategy : ImageEffectStrategy {
    override val effect: ImageEffect = ImageEffect.BOUNCE
    override val durationMs: Long = 1_000L

    override fun apply(target: View) {
        reset(target)
        target.scaleX = 0.8f
        target.scaleY = 0.8f
        target.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(durationMs)
            .setInterpolator(BounceInterpolator())
            .start()
    }
}

data object BlindsEffectStrategy : ImageEffectStrategy {
    override val effect: ImageEffect = ImageEffect.BLINDS
    override val durationMs: Long = BlindsImageView.ANIMATION_DURATION_MS

    override fun apply(target: View) {
        reset(target)
        (target as? BlindsImageView)?.post { target.startBlindsAnimation() }
    }
}

data object ZoomEffectStrategy : ImageEffectStrategy {
    override val effect: ImageEffect = ImageEffect.ZOOM
    override val durationMs: Long = 1_000L

    override fun apply(target: View) {
        reset(target)
        target.scaleX = 0.7f
        target.scaleY = 0.7f
        target.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(durationMs)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }
}

data object RotateEffectStrategy : ImageEffectStrategy {
    override val effect: ImageEffect = ImageEffect.ROTATE
    override val durationMs: Long = 1_000L

    override fun apply(target: View) {
        reset(target)
        target.rotation = 180f
        target.animate()
            .rotation(0f)
            .setDuration(durationMs)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }
}

data object SlideEffectStrategy : ImageEffectStrategy {
    override val effect: ImageEffect = ImageEffect.SLIDE
    override val durationMs: Long = 1_000L

    override fun apply(target: View) {
        reset(target)
        target.translationX = -100f
        target.scaleX = 0.9f
        target.scaleY = 0.9f
        target.animate()
            .translationX(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(durationMs)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }
}

/** RANDOM 是策略选择器，不直接实现动画；调用 [ImageEffectFactory.resolve] 会返回具体策略。 */
data object RandomEffectStrategy : ImageEffectStrategy {
    override val effect: ImageEffect = ImageEffect.RANDOM
    override val durationMs: Long = 0L

    override fun apply(target: View) {
        ImageEffectFactory.resolve(ImageEffect.RANDOM).apply(target)
    }
}

object ImageEffectFactory {
    val concreteStrategies: Map<ImageEffect, ImageEffectStrategy> = mapOf(
        ImageEffect.FADE to FadeEffectStrategy,
        ImageEffect.EASE to EaseEffectStrategy,
        ImageEffect.FLOAT to FloatEffectStrategy,
        ImageEffect.BOUNCE to BounceEffectStrategy,
        ImageEffect.BLINDS to BlindsEffectStrategy,
        ImageEffect.ZOOM to ZoomEffectStrategy,
        ImageEffect.ROTATE to RotateEffectStrategy,
        ImageEffect.SLIDE to SlideEffectStrategy,
    )

    fun getStrategy(effect: ImageEffect): ImageEffectStrategy =
        if (effect == ImageEffect.RANDOM) RandomEffectStrategy else concreteStrategies.getValue(effect)

    fun resolve(effect: ImageEffect): ImageEffectStrategy =
        concreteStrategies.getValue(effect.getActualEffect())

    fun fromValue(value: Int): ImageEffectStrategy = getStrategy(ImageEffect.fromValue(value))
}
