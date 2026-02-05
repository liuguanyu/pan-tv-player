package com.baidu.tv.player.effects;

import android.view.animation.DecelerateInterpolator;
import com.baidu.tv.player.ui.view.BlindsImageView;

/**
 * 放大特效策略
 * 从中心放大
 */
public class ZoomEffectStrategy implements ImageEffectStrategy {
    @Override
    public void applyEffect(BlindsImageView imageView) {
        imageView.setScaleX(0.7f);
        imageView.setScaleY(0.7f);
        imageView.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(getDuration())
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }
}