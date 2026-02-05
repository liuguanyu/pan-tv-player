package com.baidu.tv.player.effects;

import android.view.animation.BounceInterpolator;
import com.baidu.tv.player.ui.view.BlindsImageView;

/**
 * 跳动特效策略
 * 先缩小再弹回
 */
public class BounceEffectStrategy implements ImageEffectStrategy {
    @Override
    public void applyEffect(BlindsImageView imageView) {
        imageView.setScaleX(0.8f);
        imageView.setScaleY(0.8f);
        imageView.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(getDuration())
                .setInterpolator(new BounceInterpolator())
                .start();
    }
}