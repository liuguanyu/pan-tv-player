package com.baidu.tv.player.kt.ui.playback.image

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/** 图片背景模式（对应 Java 版 0/1/2）。 */
enum class ImageBackgroundMode(val value: Int, val displayName: String) {
    BLACK(0, "黑色背景"),
    DOMINANT_COLOR(1, "主色调背景"),
    BLUR(2, "模糊背景");

    companion object {
        fun fromValue(value: Int): ImageBackgroundMode =
            entries.firstOrNull { it.value == value } ?: BLACK
    }
}

/** 图片背景策略，Activity 在 Glide 回调拿到 bitmap 后调用。 */
sealed interface ImageBackgroundStrategy {
    val mode: ImageBackgroundMode

    suspend fun apply(backgroundView: ImageView, source: Bitmap?)
}

data object BlackBackgroundStrategy : ImageBackgroundStrategy {
    override val mode: ImageBackgroundMode = ImageBackgroundMode.BLACK

    override suspend fun apply(backgroundView: ImageView, source: Bitmap?) {
        backgroundView.setImageDrawable(null)
        backgroundView.setBackgroundColor(Color.BLACK)
        backgroundView.visibility = View.VISIBLE
    }
}

data object DominantColorBackgroundStrategy : ImageBackgroundStrategy {
    override val mode: ImageBackgroundMode = ImageBackgroundMode.DOMINANT_COLOR

    override suspend fun apply(backgroundView: ImageView, source: Bitmap?) {
        val color = source?.let { bitmap ->
            withContext(Dispatchers.Default) {
                runCatching {
                    Palette.from(bitmap).generate().dominantSwatch?.rgb
                        ?: Palette.from(bitmap).generate().vibrantSwatch?.rgb
                        ?: Palette.from(bitmap).generate().mutedSwatch?.rgb
                }.getOrNull()
            }
        } ?: Color.BLACK
        backgroundView.setImageDrawable(null)
        backgroundView.setBackgroundColor(darkenForTv(color))
        backgroundView.visibility = View.VISIBLE
    }
}

data object BlurBackgroundStrategy : ImageBackgroundStrategy {
    override val mode: ImageBackgroundMode = ImageBackgroundMode.BLUR

    override suspend fun apply(backgroundView: ImageView, source: Bitmap?) {
        val drawable = source?.let { bitmap ->
            withContext(Dispatchers.Default) {
                runCatching { BitmapDrawableCompat(createFastBlur(bitmap)) }.getOrNull()
            }
        }
        if (drawable != null) {
            backgroundView.scaleType = ImageView.ScaleType.CENTER_CROP
            backgroundView.setImageDrawable(drawable)
            backgroundView.visibility = View.VISIBLE
        } else {
            DominantColorBackgroundStrategy.apply(backgroundView, source)
        }
    }
}

object ImageBackgroundFactory {
    val strategies: Map<ImageBackgroundMode, ImageBackgroundStrategy> = mapOf(
        ImageBackgroundMode.BLACK to BlackBackgroundStrategy,
        ImageBackgroundMode.DOMINANT_COLOR to DominantColorBackgroundStrategy,
        ImageBackgroundMode.BLUR to BlurBackgroundStrategy,
    )

    fun getStrategy(mode: ImageBackgroundMode): ImageBackgroundStrategy = strategies.getValue(mode)

    fun fromValue(value: Int): ImageBackgroundStrategy = getStrategy(ImageBackgroundMode.fromValue(value))
}

private fun darkenForTv(color: Int): Int = ColorUtils.blendARGB(color, Color.BLACK, 0.35f)

private fun createFastBlur(source: Bitmap): Bitmap {
    val width = max(1, source.width / BLUR_SCALE)
    val height = max(1, source.height / BLUR_SCALE)
    val scaled = Bitmap.createScaledBitmap(source, width, height, true)
    val blurred = boxBlur(scaled, BLUR_RADIUS)
    return Bitmap.createScaledBitmap(blurred, source.width, source.height, true)
}

/** 简单 box blur，避免引入 RenderScript/额外依赖；用于 TV 背景氛围图，非精确滤镜。 */
private fun boxBlur(bitmap: Bitmap, radius: Int): Bitmap {
    if (radius <= 0) return bitmap.copy(Bitmap.Config.ARGB_8888, false)
    val src = bitmap.copy(Bitmap.Config.ARGB_8888, false)
    val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(src.width * src.height)
    val result = IntArray(pixels.size)
    src.getPixels(pixels, 0, src.width, 0, 0, src.width, src.height)

    val w = src.width
    val h = src.height
    for (y in 0 until h) {
        for (x in 0 until w) {
            var a = 0
            var r = 0
            var g = 0
            var b = 0
            var count = 0
            val left = max(0, x - radius)
            val right = min(w - 1, x + radius)
            val top = max(0, y - radius)
            val bottom = min(h - 1, y + radius)
            for (yy in top..bottom) {
                val row = yy * w
                for (xx in left..right) {
                    val c = pixels[row + xx]
                    a += Color.alpha(c)
                    r += Color.red(c)
                    g += Color.green(c)
                    b += Color.blue(c)
                    count++
                }
            }
            result[y * w + x] = Color.argb(a / count, r / count, g / count, b / count)
        }
    }
    out.setPixels(result, 0, w, 0, 0, w, h)
    return out
}

private class BitmapDrawableCompat(private val bitmap: Bitmap) : Drawable() {
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)

    override fun draw(canvas: android.graphics.Canvas) {
        canvas.drawBitmap(bitmap, null, bounds, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}

private const val BLUR_SCALE = 12
private const val BLUR_RADIUS = 3
