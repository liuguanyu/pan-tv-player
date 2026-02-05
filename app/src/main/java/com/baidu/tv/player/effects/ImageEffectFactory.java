package com.baidu.tv.player.effects;

import com.baidu.tv.player.model.ImageEffect;

/**
 * 图片特效工厂类
 * 使用抽象工厂模式创建各种图片特效策略对象
 */
public class ImageEffectFactory {
    
    /**
     * 根据特效类型创建对应的特效策略对象
     * @param effect 特效类型
     * @return 特效策略对象
     */
    public static ImageEffectStrategy createEffectStrategy(ImageEffect effect) {
        if (effect == null) {
            return new FadeEffectStrategy(); // 默认使用淡入淡出特效
        }
        
        switch (effect) {
            case FADE:
                return new FadeEffectStrategy();
            case EASE:
                return new EaseEffectStrategy();
            case FLOAT:
                return new FloatEffectStrategy();
            case BOUNCE:
                return new BounceEffectStrategy();
            case BLINDS:
                return new BlindsEffectStrategy();
            case ZOOM:
                return new ZoomEffectStrategy();
            case ROTATE:
                return new RotateEffectStrategy();
            case SLIDE:
                return new SlideEffectStrategy();
            case RANDOM:
                // 随机特效在调用时处理，这里返回默认的淡入淡出特效
                return new FadeEffectStrategy();
            default:
                return new FadeEffectStrategy(); // 默认使用淡入淡出特效
        }
    }
    
    /**
     * 根据特效类型创建对应的特效策略对象（处理随机特效）
     * @param effect 特效类型
     * @return 特效策略对象
     */
    public static ImageEffectStrategy createActualEffectStrategy(ImageEffect effect) {
        if (effect == null) {
            return new FadeEffectStrategy(); // 默认使用淡入淡出特效
        }
        
        // 如果是随机特效，获取实际的特效类型
        ImageEffect actualEffect = effect.getActualEffect();
        return createEffectStrategy(actualEffect);
    }
}