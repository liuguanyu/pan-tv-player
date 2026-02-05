package com.baidu.tv.player.effects;

import android.view.animation.DecelerateInterpolator;
import com.baidu.tv.player.ui.view.BlindsImageView;

/**
 * 缓动特效策略
 * 从右侧滑入
 */
public class EaseEffectStrategy implements ImageEffectStrategy {
    @Override
    public void applyEffect(BlindsImageView imageView) {
        imageView.setTranslationX(80f);
        imageView.animate()
                .translationX(0f)
                .setDuration(getDuration())
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }
}