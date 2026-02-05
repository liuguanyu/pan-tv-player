package com.baidu.tv.player.effects;

import com.baidu.tv.player.ui.view.BlindsImageView;

/**
 * 百叶窗特效策略
 * 启动自定义百叶窗动画
 */
public class BlindsEffectStrategy implements ImageEffectStrategy {
    @Override
    public void applyEffect(BlindsImageView imageView) {
        imageView.resetBlinds();
        imageView.post(imageView::startBlindsAnimation);
    }
}