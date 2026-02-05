package com.baidu.tv.player.effects;

import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.BounceInterpolator;
import android.view.animation.DecelerateInterpolator;

import com.baidu.tv.player.ui.view.BlindsImageView;

/**
 * 图片特效策略接口
 * 定义所有图片特效的通用行为
 */
public interface ImageEffectStrategy {
    /**
     * 应用特效到指定的ImageView
     * @param imageView 目标ImageView
     */
    void applyEffect(BlindsImageView imageView);
    
    /**
     * 获取默认的动画插值器
     * @return 插值器
     */
    default Interpolator getDefaultInterpolator() {
        return new LinearInterpolator();
    }
    
    /**
     * 获取动画持续时间（毫秒）
     * @return 持续时间
     */
    default long getDuration() {
        return 800;
    }
}