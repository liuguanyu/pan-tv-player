package com.baidu.tv.player.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.util.Log;

import androidx.palette.graphics.Palette;

/**
 * 图片背景处理工具类
 * 提供两种解决图片切换时亮暗刺眼问题的方案：
 * 1. 提取图片主色调作为背景色
 * 2. 生成图片的毛玻璃虚化背景
 */
public class ImageBackgroundUtils {
    private static final String TAG = "ImageBackgroundUtils";
    
    // 背景模式
    public enum BackgroundMode {
        BLACK(0, "纯黑色背景"),           // 传统黑色背景
        DOMINANT_COLOR(1, "主色调背景"),  // 提取图片主色调
        BLURRED(2, "毛玻璃背景");         // 图片模糊背景
        
        private final int value;
        private final String name;
        
        BackgroundMode(int value, String name) {
            this.value = value;
            this.name = name;
        }
        
        public int getValue() {
            return value;
        }
        
        public String getName() {
            return name;
        }
        
        public static BackgroundMode fromValue(int value) {
            for (BackgroundMode mode : values()) {
                if (mode.value == value) {
                    return mode;
                }
            }
            return BLACK;
        }
    }
    
    /**
     * 从图片提取主色调（带缓存）
     * 使用 Palette 库进行智能颜色提取
     * 
     * @param context 上下文
     * @param imageUrl 图片URL（用于缓存键）
     * @param drawable 源图片
     * @return 主色调颜色值，如果提取失败返回黑色
     */
    public static int extractDominantColor(Context context, String imageUrl, Drawable drawable) {
        if (drawable == null) {
            return Color.BLACK;
        }
        
        // 检查缓存
        BackgroundCache cache = BackgroundCache.getInstance();
        Integer cachedColor = cache.getColor(imageUrl);
        if (cachedColor != null) {
            return cachedColor;
        }
        
        try {
            Bitmap bitmap = drawableToBitmap(drawable);
            if (bitmap == null) {
                return Color.BLACK;
            }
            
            // 缩小图片以提高性能
            Bitmap scaledBitmap = scaleBitmapForColorExtraction(bitmap);
            
            // 使用 Palette 提取颜色
            Palette palette = Palette.from(scaledBitmap).generate();
            
            // 回收缩放后的 bitmap
            if (scaledBitmap != bitmap) {
                scaledBitmap.recycle();
            }
            
            // 优先获取柔和的暗色调（适合做背景）
            int dominantColor = palette.getDarkMutedColor(Color.BLACK);
            if (dominantColor == Color.BLACK) {
                dominantColor = palette.getMutedColor(Color.BLACK);
            }
            if (dominantColor == Color.BLACK) {
                dominantColor = palette.getDominantColor(Color.BLACK);
            }
            
            // 降低亮度，使背景更暗，不那么刺眼
            dominantColor = darkenColor(dominantColor, 0.4f);
            
            Log.d(TAG, String.format("提取主色调: #%06X", (0xFFFFFF & dominantColor)));
            
            // 缓存结果
            cache.putColor(imageUrl, dominantColor);
            
            return dominantColor;
            
        } catch (Exception e) {
            Log.e(TAG, "提取主色调失败", e);
            return Color.BLACK;
        }
    }
    
    /**
     * 生成图片的模糊背景（带缓存）
     * 使用 RenderScript 进行高效模糊处理
     * 
     * @param context 上下文
     * @param imageUrl 图片URL（用于缓存键）
     * @param drawable 源图片
     * @param blurRadius 模糊半径 (1-25)
     * @param downScale 缩小倍数，用于提高性能 (建议 4-8)
     * @return 模糊后的 Bitmap，如果失败返回 null
     */
    public static Bitmap createBlurredBackground(Context context, String imageUrl, Drawable drawable, 
                                                  float blurRadius, int downScale) {
        if (drawable == null || context == null) {
            return null;
        }
        
        // 检查缓存
        BackgroundCache cache = BackgroundCache.getInstance();
        Bitmap cachedBitmap = cache.getBlur(imageUrl);
        if (cachedBitmap != null && !cachedBitmap.isRecycled()) {
            return cachedBitmap;
        }
        
        try {
            Bitmap bitmap = drawableToBitmap(drawable);
            if (bitmap == null) {
                return null;
            }
            
            // 缩小图片以提高性能
            int width = bitmap.getWidth() / downScale;
            int height = bitmap.getHeight() / downScale;
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, false);
            
            // 使用 RenderScript 进行模糊
            Bitmap blurredBitmap = blurBitmap(context, scaledBitmap, blurRadius);
            
            // 回收缩放后的 bitmap
            if (scaledBitmap != blurredBitmap) {
                scaledBitmap.recycle();
            }
            
            // 降低整体亮度
            if (blurredBitmap != null) {
                blurredBitmap = adjustBrightness(blurredBitmap, 0.5f);
            }
            
            // 缓存结果
            if (blurredBitmap != null) {
                cache.putBlur(imageUrl, blurredBitmap);
            }
            
            return blurredBitmap;
            
        } catch (Exception e) {
            Log.e(TAG, "生成模糊背景失败", e);
            return null;
        }
    }
    
    /**
     * 使用 RenderScript 对 Bitmap 进行模糊处理
     */
    private static Bitmap blurBitmap(Context context, Bitmap bitmap, float blurRadius) {
        if (bitmap == null) {
            return null;
        }
        
        RenderScript rs = null;
        try {
            // 创建 RenderScript 上下文
            rs = RenderScript.create(context);
            
            // 创建输入输出 Allocation
            Allocation input = Allocation.createFromBitmap(rs, bitmap);
            Allocation output = Allocation.createTyped(rs, input.getType());
            
            // 创建模糊脚本
            ScriptIntrinsicBlur blurScript = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
            blurScript.setRadius(Math.min(25f, Math.max(1f, blurRadius))); // 限制在 1-25
            blurScript.setInput(input);
            blurScript.forEach(output);
            
            // 将结果复制回 bitmap
            output.copyTo(bitmap);
            
            // 清理资源
            input.destroy();
            output.destroy();
            blurScript.destroy();
            
            return bitmap;
            
        } catch (Exception e) {
            Log.e(TAG, "RenderScript 模糊失败", e);
            return bitmap;
        } finally {
            if (rs != null) {
                rs.destroy();
            }
        }
    }
    
    /**
     * 调整 Bitmap 的亮度
     * 
     * @param bitmap 源 bitmap
     * @param factor 亮度因子 (0-1，0 为全黑，1 为原始亮度)
     * @return 调整后的 bitmap
     */
    private static Bitmap adjustBrightness(Bitmap bitmap, float factor) {
        if (bitmap == null || factor >= 1.0f) {
            return bitmap;
        }
        
        try {
            Bitmap adjustedBitmap = Bitmap.createBitmap(
                bitmap.getWidth(), 
                bitmap.getHeight(), 
                bitmap.getConfig()
            );
            
            Canvas canvas = new Canvas(adjustedBitmap);
            Paint paint = new Paint();
            
            // 使用 ColorMatrix 调整亮度
            android.graphics.ColorMatrix cm = new android.graphics.ColorMatrix();
            cm.setScale(factor, factor, factor, 1f);
            paint.setColorFilter(new android.graphics.ColorMatrixColorFilter(cm));
            
            canvas.drawBitmap(bitmap, 0, 0, paint);
            
            // 回收原 bitmap
            if (!bitmap.isRecycled()) {
                bitmap.recycle();
            }
            
            return adjustedBitmap;
            
        } catch (Exception e) {
            Log.e(TAG, "调整亮度失败", e);
            return bitmap;
        }
    }
    
    /**
     * 降低颜色亮度
     * 
     * @param color 原始颜色
     * @param factor 降低因子 (0-1，越小越暗)
     * @return 降低亮度后的颜色
     */
    private static int darkenColor(int color, float factor) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] *= factor; // 降低亮度
        return Color.HSVToColor(Color.alpha(color), hsv);
    }
    
    /**
     * 将 Drawable 转换为 Bitmap
     */
    private static Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
            if (bitmapDrawable.getBitmap() != null) {
                return bitmapDrawable.getBitmap();
            }
        }
        
        // 创建 Bitmap 并绘制 Drawable
        Bitmap bitmap;
        if (drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
            bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        } else {
            bitmap = Bitmap.createBitmap(
                drawable.getIntrinsicWidth(),
                drawable.getIntrinsicHeight(),
                Bitmap.Config.ARGB_8888
            );
        }
        
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        
        return bitmap;
    }
    
    /**
     * 缩小 Bitmap 以提高颜色提取性能
     * 颜色提取不需要高分辨率
     */
    private static Bitmap scaleBitmapForColorExtraction(Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }
        
        final int MAX_SIZE = 100; // 最大尺寸
        
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        
        if (width <= MAX_SIZE && height <= MAX_SIZE) {
            return bitmap; // 已经足够小
        }
        
        float scale = Math.min((float) MAX_SIZE / width, (float) MAX_SIZE / height);
        int newWidth = Math.round(width * scale);
        int newHeight = Math.round(height * scale);
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, false);
    }
    
    /**
     * 计算两种背景模式的性能影响
     * 
     * @return 性能影响说明
     */
    public static String getPerformanceInfo() {
        return "性能影响分析：\n\n" +
               "1. 纯黑色背景：\n" +
               "   - CPU: 无额外开销\n" +
               "   - 内存: 无额外开销\n" +
               "   - 适用场景: 所有设备\n\n" +
               
               "2. 主色调背景：\n" +
               "   - CPU: 极低 (~5-10ms/图片)\n" +
               "   - 内存: 极低 (~0.5MB 临时占用)\n" +
               "   - 适用场景: 所有设备，推荐使用\n" +
               "   - 技术: Palette 颜色分析\n" +
               "   - 优化: 使用 LRU 缓存，重复图片无需重复计算\n\n" +
               
               "3. 毛玻璃背景：\n" +
               "   - CPU: 低-中等 (~20-50ms/图片，使用 RenderScript 硬件加速)\n" +
               "   - 内存: 中等 (~2-4MB 临时占用)\n" +
               "   - 适用场景: 中高端设备\n" +
               "   - 技术: RenderScript 模糊 + 亮度调整\n" +
               "   - 优化: 图片缩小到 1/8 后再模糊，使用 LRU 缓存\n\n" +
               
               "缓存策略：\n" +
               "   - 主色调缓存: 最多100个颜色\n" +
               "   - 模糊背景缓存: 最多20个背景（约10MB）\n" +
               "   - 自动LRU清理，避免内存溢出\n\n" +
               
               "总结: 主色调背景性能最佳，视觉效果良好；毛玻璃背景视觉效果最佳，性能开销可接受。";
    }
}