package com.baidu.tv.player.ui.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.animation.DecelerateInterpolator;

import androidx.appcompat.widget.AppCompatImageView;

/**
 * 支持百叶窗效果的ImageView
 */
public class BlindsImageView extends AppCompatImageView {
    private static final int BLINDS_COUNT = 8; // 百叶窗条数
    private static final int ANIMATION_DURATION = 1000; // 动画时长（毫秒）
    
    private Paint clipPaint;
    private float blindsProgress = 1.0f; // 百叶窗进度，0为完全关闭，1为完全打开
    private boolean isBlindsAnimating = false;
    private ValueAnimator blindsAnimator;

    public BlindsImageView(Context context) {
        super(context);
        init();
    }

    public BlindsImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BlindsImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        clipPaint = new Paint();
        clipPaint.setAntiAlias(true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (isBlindsAnimating && blindsProgress < 1.0f) {
            // 绘制百叶窗效果
            drawBlindsEffect(canvas);
        } else {
            // 正常绘制
            super.onDraw(canvas);
        }
    }

    /**
     * 绘制百叶窗效果
     * 奇数条（0,2,4,6...）从左到右显示
     * 偶数条（1,3,5,7...）从右到左显示
     */
    private void drawBlindsEffect(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        
        if (width == 0 || height == 0) {
            return;
        }

        Drawable drawable = getDrawable();
        if (drawable == null) {
            return;
        }

        // 计算每条百叶窗的高度
        int blindHeight = height / BLINDS_COUNT;
        
        // 保存画布状态
        canvas.save();
        
        // 计算图片在ImageView中的绘制区域（保持缩放类型一致）
        Rect drawableRect = calculateDrawableBounds(width, height, drawable);
        
        // 保存原始bounds
        Rect originalBounds = drawable.copyBounds();
        // 设置新的bounds用于绘制
        drawable.setBounds(drawableRect.left, drawableRect.top, drawableRect.right, drawableRect.bottom);
        
        // 绘制每条百叶窗
        for (int i = 0; i < BLINDS_COUNT; i++) {
            int top = i * blindHeight;
            int bottom = (i == BLINDS_COUNT - 1) ? height : (i + 1) * blindHeight;
            
            // 计算当前百叶窗条的显示宽度
            int visibleWidth = (int) (width * blindsProgress);
            
            // 裁剪区域
            canvas.save();
            
            // 奇数条从左到右，偶数条从右到左
            if (i % 2 == 0) {
                // 偶数条：从左到右
                canvas.clipRect(0, top, visibleWidth, bottom);
            } else {
                // 奇数条：从右到左
                canvas.clipRect(width - visibleWidth, top, width, bottom);
            }
            
            // 绘制图片
            drawable.draw(canvas);
            
            canvas.restore();
        }
        
        // 恢复原始bounds，避免影响super.onDraw
        drawable.setBounds(originalBounds);
        
        canvas.restore();
    }
    
    /**
     * 计算图片在ImageView中的绘制区域，保持与ImageView的scaleType一致
     */
    private Rect calculateDrawableBounds(int viewWidth, int viewHeight, Drawable drawable) {
        int drawableWidth = drawable.getIntrinsicWidth();
        int drawableHeight = drawable.getIntrinsicHeight();
        
        if (drawableWidth <= 0 || drawableHeight <= 0) {
            return new Rect(0, 0, viewWidth, viewHeight);
        }
        
        // 计算缩放比例
        float scale;
        float dx = 0, dy = 0;
        
        // 判断图片是横屏还是竖屏
        boolean isLandscape = drawableWidth >= drawableHeight;
        
        if (isLandscape) {
            // 横屏图片：尽量充满全屏 (CenterCrop 模式)
            // 取宽和高中缩放比例较大的那个，保证填满屏幕
            float scaleX = (float) viewWidth / (float) drawableWidth;
            float scaleY = (float) viewHeight / (float) drawableHeight;
            scale = Math.max(scaleX, scaleY);
        } else {
            // 竖屏图片：等比占满纵轴 (FitCenter 模式)
            // 保持原有逻辑，确保完整显示
            if (drawableWidth * viewHeight > viewWidth * drawableHeight) {
                scale = (float) viewWidth / (float) drawableWidth;
            } else {
                scale = (float) viewHeight / (float) drawableHeight;
            }
        }
        
        // 计算居中位置
        float scaledWidth = drawableWidth * scale;
        float scaledHeight = drawableHeight * scale;
        
        dx = (viewWidth - scaledWidth) * 0.5f;
        dy = (viewHeight - scaledHeight) * 0.5f;
        
        int left = Math.round(dx);
        int top = Math.round(dy);
        int right = Math.round(dx + scaledWidth);
        int bottom = Math.round(dy + scaledHeight);
        
        return new Rect(left, top, right, bottom);
    }

    /**
     * 启动百叶窗动画
     */
    public void startBlindsAnimation() {
        if (blindsAnimator != null && blindsAnimator.isRunning()) {
            blindsAnimator.cancel();
        }

        isBlindsAnimating = true;
        blindsProgress = 0.0f;

        blindsAnimator = ValueAnimator.ofFloat(0.0f, 1.0f);
        blindsAnimator.setDuration(ANIMATION_DURATION);
        blindsAnimator.setInterpolator(new DecelerateInterpolator());
        blindsAnimator.addUpdateListener(animation -> {
            blindsProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        blindsAnimator.addListener(new android.animation.Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(android.animation.Animator animation) {
            }

            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                // 动画结束后，停止百叶窗绘制，切换回正常绘制模式
                isBlindsAnimating = false;
                blindsProgress = 1.0f;
                invalidate();
            }

            @Override
            public void onAnimationCancel(android.animation.Animator animation) {
                // 动画取消时也停止百叶窗绘制
                isBlindsAnimating = false;
                blindsProgress = 1.0f;
                invalidate();
            }

            @Override
            public void onAnimationRepeat(android.animation.Animator animation) {
            }
        });
        blindsAnimator.start();
    }

    /**
     * 停止百叶窗动画
     */
    public void stopBlindsAnimation() {
        if (blindsAnimator != null && blindsAnimator.isRunning()) {
            blindsAnimator.cancel();
        }
        isBlindsAnimating = false;
        blindsProgress = 1.0f;
        invalidate();
    }

    /**
     * 重置百叶窗状态
     */
    public void resetBlinds() {
        isBlindsAnimating = false;
        blindsProgress = 1.0f;
        invalidate();
    }
}