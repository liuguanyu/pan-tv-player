package com.baidu.tv.player.model;

import java.util.Random;

/**
 * 图片展示特效
 */
public enum ImageEffect {
    FADE(0, "淡入淡出"),
    EASE(1, "缓动"),
    FLOAT(2, "浮现"),
    BOUNCE(3, "跳动"),
    BLINDS(5, "百叶窗"),
    ZOOM(6, "放大"),
    ROTATE(7, "旋转"),
    SLIDE(8, "两侧划入"),
    RANDOM(4, "随机");

    private final int value;
    private final String name;
    private static final Random random = new Random();

    ImageEffect(int value, String name) {
        this.value = value;
        this.name = name;
    }

    public int getValue() {
        return value;
    }

    public String getName() {
        return name;
    }

    /**
     * 获取随机特效（从所有特效中随机选择）
     */
    public static ImageEffect getRandomEffect() {
        ImageEffect[] effects = {FADE, EASE, FLOAT, BOUNCE, BLINDS, ZOOM, ROTATE, SLIDE};
        return effects[random.nextInt(effects.length)];
    }

    /**
     * 如果当前特效是RANDOM，返回随机特效；否则返回当前特效
     */
    public ImageEffect getActualEffect() {
        if (this == RANDOM) {
            return getRandomEffect();
        }
        return this;
    }

    public static ImageEffect fromValue(int value) {
        for (ImageEffect effect : values()) {
            if (effect.value == value) {
                return effect;
            }
        }
        return FADE;
    }
}