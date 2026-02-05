package com.baidu.tv.player.background;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

/**
 * 图片背景策略接口
 */
public interface ImageBackgroundStrategy {
    /**
     * 应用背景
     *
     * @param activity     当前Activity
     * @param ivBackground 背景ImageView
     * @param imageUrl     图片URL
     * @param imageDrawable 图片Drawable对象
     */
    void applyBackground(Activity activity, ImageView ivBackground, String imageUrl, Drawable imageDrawable);
}