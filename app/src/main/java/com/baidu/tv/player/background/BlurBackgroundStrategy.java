package com.baidu.tv.player.background;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import com.baidu.tv.player.utils.ImageBackgroundUtils;

/**
 * 毛玻璃背景策略
 */
public class BlurBackgroundStrategy implements ImageBackgroundStrategy {
    private static final String TAG = "BlurBackground";
    private static final float BLUR_RADIUS = 15.0f;
    private static final int SCALE_FACTOR = 8;

    @Override
    public void applyBackground(Activity activity, ImageView ivBackground, String imageUrl, Drawable imageDrawable) {
        // 在后台线程中生成模糊背景
        new Thread(() -> {
            try {
                Bitmap blurredBitmap = ImageBackgroundUtils.createBlurredBackground(
                    activity,
                    imageUrl,
                    imageDrawable,
                    BLUR_RADIUS,
                    SCALE_FACTOR
                );

                activity.runOnUiThread(() -> {
                    if (blurredBitmap != null) {
                        ivBackground.setImageBitmap(blurredBitmap);
                        ivBackground.setBackgroundColor(Color.TRANSPARENT);
                        ivBackground.setVisibility(View.VISIBLE);
                    } else {
                        // 如果模糊失败，回退到主色调
                        Log.w(TAG, "模糊背景生成失败，回退到主色调");
                        fallbackToDominantColor(activity, ivBackground, imageUrl, imageDrawable);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "生成模糊背景失败", e);
                activity.runOnUiThread(() -> {
                    // 回退到主色调
                    fallbackToDominantColor(activity, ivBackground, imageUrl, imageDrawable);
                });
            }
        }).start();
    }

    /**
     * 回退到主色调背景
     */
    private void fallbackToDominantColor(Activity activity, ImageView ivBackground, String imageUrl, Drawable imageDrawable) {
        try {
            int dominantColor = ImageBackgroundUtils.extractDominantColor(
                activity,
                imageUrl,
                imageDrawable
            );
            ivBackground.setBackgroundColor(dominantColor);
            ivBackground.setImageBitmap(null);
            ivBackground.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            Log.e(TAG, "回退到主色调也失败", e);
            // 最终回退到黑色背景
            ivBackground.setBackgroundColor(Color.BLACK);
            ivBackground.setImageBitmap(null);
            ivBackground.setVisibility(View.VISIBLE);
        }
    }
}