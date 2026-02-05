package com.baidu.tv.player.effects;

import com.baidu.tv.player.ui.view.BlindsImageView;

/**
 * 淡入淡出特效策略
 * 该特效完全由Glide的CrossFade处理，不需要额外动画
 */
public class FadeEffectStrategy implements ImageEffectStrategy {
    @Override
    public void applyEffect(BlindsImageView imageView) {
        // FADE效果完全由Glide的CrossFade处理，不需要额外动画
        // 重置视图到默认状态
        imageView.setScaleX(1.0f);
        imageView.setScaleY(1.0f);
        imageView.setTranslationX(0);
        imageView.setTranslationY(0);
        imageView.setRotation(0);
        imageView.setAlpha(1.0f);
    }
    
    @Override
    public long getDuration() {
        return 800; // 与Glide的CrossFade持续时间保持一致
    }
}