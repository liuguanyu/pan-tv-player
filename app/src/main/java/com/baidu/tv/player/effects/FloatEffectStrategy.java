package com.baidu.tv.player.effects;

import android.view.animation.LinearInterpolator;
import com.baidu.tv.player.ui.view.BlindsImageView;

/**
 * 浮动特效策略
 * 缓慢放大效果
 */
public class FloatEffectStrategy implements ImageEffectStrategy {
    @Override
    public void applyEffect(BlindsImageView imageView) {
        imageView.animate()
                .scaleX(1.1f)
                .scaleY(1.1f)
                .setDuration(getDuration())
                .setInterpolator(new LinearInterpolator())
                .start();
    }
    
    @Override
    public long getDuration() {
        return 5000;
    }
}