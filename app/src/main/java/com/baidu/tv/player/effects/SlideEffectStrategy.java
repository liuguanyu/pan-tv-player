package com.baidu.tv.player.effects;

import android.view.animation.DecelerateInterpolator;
import com.baidu.tv.player.ui.view.BlindsImageView;

/**
 * 滑入特效策略
 * 从左侧滑入并放大
 */
public class SlideEffectStrategy implements ImageEffectStrategy {
    @Override
    public void applyEffect(BlindsImageView imageView) {
        imageView.setTranslationX(-100f);
        imageView.setScaleX(0.9f);
        imageView.setScaleY(0.9f);
        imageView.animate()
                .translationX(0f)
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(getDuration())
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }
}