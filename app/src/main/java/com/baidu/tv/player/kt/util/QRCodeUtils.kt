package com.baidu.tv.player.kt.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/**
 * 二维码工具类。
 */
object QRCodeUtils {

    /**
     * 生成二维码 Bitmap。失败返回 null。
     * @param content 内容
     * @param width 宽度
     * @param height 高度
     */
    fun createQRCodeBitmap(content: String, width: Int, height: Int): Bitmap? = try {
        val writer = QRCodeWriter()
        val hints = mapOf(EncodeHintType.MARGIN to 0) // 边距设为 0
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, width, height, hints)
        val pixels = IntArray(width * height) { idx ->
            val x = idx % width
            val y = idx / width
            if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
        }
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
