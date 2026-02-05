package com.baidu.tv.player.background;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;

/**
 * 纯黑色背景策略
 */
public class BlackBackgroundStrategy implements ImageBackgroundStrategy {
    @Override
    public void applyBackground(Activity activity, ImageView ivBackground, String imageUrl, Drawable imageDrawable) {
        ivBackground.setBackgroundColor(Color.BLACK);
        ivBackground.setImageBitmap(null);
        ivBackground.setVisibility(View.VISIBLE);
    }
}