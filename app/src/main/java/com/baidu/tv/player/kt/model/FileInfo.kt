package com.baidu.tv.player.kt.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

/**
 * 文件信息模型（百度网盘文件/目录）。
 *
 * 通过 `@Parcelize` 生成 Parcelable，替代 Java 版手写实现。
 */
@Parcelize
data class FileInfo(
    @SerializedName("fs_id") val fsId: Long = 0,
    @SerializedName("path") val path: String? = null,
    @SerializedName("server_filename") val serverFilename: String? = null,
    @SerializedName("size") val size: Long = 0,
    @SerializedName("server_mtime") val serverMtime: Long = 0,
    @SerializedName("server_ctime") val serverCtime: Long = 0,
    @SerializedName("local_mtime") val localMtime: Long = 0,
    @SerializedName("local_ctime") val localCtime: Long = 0,
    @SerializedName("isdir") val isdir: Int = 0,
    @SerializedName("category") val category: Int = 0,
    @SerializedName("md5") val md5: String? = null,
    @SerializedName("dir_empty") val dirEmpty: Int = 0,
    @SerializedName("thumbs") val thumbs: Thumbs? = null,
    @SerializedName("dlink") val dlink: String? = null,
) : Parcelable {

    /** 是否是目录。 */
    fun isDirectory(): Boolean = isdir == 1

    /**
     * 是否是图片。优先使用百度 API 的 category 字段(3=图片)，其次检查文件扩展名。
     */
    fun isImage(): Boolean {
        if (category == 3) return true
        val ext = extension.lowercase()
        return ext in IMAGE_EXTENSIONS
    }

    /**
     * 是否是视频。优先使用百度 API 的 category 字段(1=视频)，其次检查文件扩展名。
     */
    fun isVideo(): Boolean {
        if (category == 1) return true
        val ext = extension.lowercase()
        return ext in VIDEO_EXTENSIONS
    }

    /** 获取文件扩展名（不含点，无扩展名返回空串）。 */
    val extension: String
        get() {
            val name = serverFilename ?: return ""
            val idx = name.lastIndexOf(".")
            return if (idx < 0) "" else name.substring(idx + 1)
        }

    /** 缩略图信息。 */
    @Parcelize
    data class Thumbs(
        @SerializedName("icon") val icon: String? = null,
        @SerializedName("url1") val url1: String? = null,
        @SerializedName("url2") val url2: String? = null,
        @SerializedName("url3") val url3: String? = null,
    ) : Parcelable

    companion object {
        private val IMAGE_EXTENSIONS = setOf(
            "jpg", "jpeg", "png", "avif", "webp", "heic", "heif", "bmp", "gif", "tiff", "tif",
        )

        private val VIDEO_EXTENSIONS = setOf(
            "mp4", "mov", "3gp", "mkv", "avi", "m4v", "flv", "wmv", "webm",
        )
    }
}
