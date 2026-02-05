package com.baidu.tv.player.background;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import com.baidu.tv.player.utils.ImageBackgroundUtils;

/**
 * 主色调背景策略
 */
public class DominantColorBackgroundStrategy implements ImageBackgroundStrategy {
    private static final String TAG = "DominantColorBg";

    @Override
    public void applyBackground(Activity activity, ImageView ivBackground, String imageUrl, Drawable imageDrawable) {
        // 在后台线程中提取主色调
        new Thread(() -> {
            try {
                int dominantColor = ImageBackgroundUtils.extractDominantColor(
                    activity,
                    imageUrl,
                    imageDrawable
                );
                activity.runOnUiThread(() -> {
                    ivBackground.setBackgroundColor(dominantColor);
                    ivBackground.setImageBitmap(null);
                    ivBackground.setVisibility(View.VISIBLE);
                });
            } catch (Exception e) {
                Log.e(TAG, "提取主色调失败", e);
                activity.runOnUiThread(() -> {
                    // 失败时回退到黑色背景
                    ivBackground.setBackgroundColor(Color.BLACK);
                    ivBackground.setImageBitmap(null);
                    ivBackground.setVisibility(View.VISIBLE);
                });
            }
        }).start();
    }
}