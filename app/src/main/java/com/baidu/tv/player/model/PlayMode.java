package com.baidu.tv.player.model;

/**
 * 播放模式
 */
public enum PlayMode {
    SEQUENTIAL(0, "顺序播放"),
    RANDOM(1, "随机播放"),
    SINGLE(2, "单曲循环"),
    REVERSE(3, "倒序播放");

    private final int value;
    private final String name;

    PlayMode(int value, String name) {
        this.value = value;
        this.name = name;
    }

    public int getValue() {
        return value;
    }

    public String getName() {
        return name;
    }

    public static PlayMode fromValue(int value) {
        for (PlayMode mode : values()) {
            if (mode.value == value) {
                return mode;
            }
        }
        return SEQUENTIAL;
    }
}