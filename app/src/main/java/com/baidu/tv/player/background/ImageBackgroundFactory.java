package com.baidu.tv.player.background;

/**
 * 背景策略工厂
 */
public class ImageBackgroundFactory {
    
    /**
     * 根据背景模式获取对应的策略
     * 
     * @param backgroundMode 背景模式 (0: 纯黑, 1: 主色调, 2: 毛玻璃)
     * @return 对应的背景策略
     */
    public static ImageBackgroundStrategy getStrategy(int backgroundMode) {
        switch (backgroundMode) {
            case 1:
                return new DominantColorBackgroundStrategy();
            case 2:
                return new BlurBackgroundStrategy();
            case 0:
            default:
                return new BlackBackgroundStrategy();
        }
    }
}