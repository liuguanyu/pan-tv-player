package com.baidu.tv.player.model;

/**
 * 媒体类型枚举
 */
public enum MediaType {
    IMAGE(1, "图片"),
    VIDEO(2, "视频"),
    ALL(3, "图片+视频");
    
    private final int code;
    private final String name;
    
    MediaType(int code, String name) {
        this.code = code;
        this.name = name;
    }
    
    public int getCode() {
        return code;
    }
    
    public int getValue() {
        return code;
    }
    
    public String getName() {
        return name;
    }
    
    public static MediaType fromCode(int code) {
        for (MediaType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return ALL;
    }
}