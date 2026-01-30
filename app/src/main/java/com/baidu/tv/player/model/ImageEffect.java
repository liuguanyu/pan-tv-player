package com.baidu.tv.player.model;

/**
 * 图片展示特效
 */
public enum ImageEffect {
    FADE(0, "淡入淡出"),
    EASE(1, "缓动"),
    FLOAT(2, "浮现"),
    BOUNCE(3, "跳动");

    private final int value;
    private final String name;

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

    public static ImageEffect fromValue(int value) {
        for (ImageEffect effect : values()) {
            if (effect.value == value) {
                return effect;
            }
        }
        return FADE;
    }
}