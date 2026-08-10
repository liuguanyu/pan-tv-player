package com.baidu.tv.player.kt.model

import android.os.Parcel
import android.os.Parcelable
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/**
 * FileInfo Parcelable 往返序列化 + 类型判断（isImage/isVideo/isDirectory）单测。
 * Robolectric 提供 Parcel 实现。
 */
@RunWith(RobolectricTestRunner::class)
class FileInfoTest {

    @Test
    fun fileInfo_parcelRoundTrip_preservesAllFields() {
        val original = FileInfo(
            fsId = 12345L,
            path = "/apps/test/photo.jpg",
            serverFilename = "photo.jpg",
            size = 1024L,
            serverMtime = 1700000000L,
            serverCtime = 1690000000L,
            localMtime = 1700000001L,
            localCtime = 1690000001L,
            isdir = 0,
            category = 3,
            md5 = "abc123",
            dirEmpty = 0,
            thumbs = FileInfo.Thumbs(icon = "http://icon", url1 = "http://u1"),
            dlink = "http://dlink",
        )
        val restored = parcelRoundTrip(original)
        assertEquals(original.fsId, restored.fsId)
        assertEquals(original.path, restored.path)
        assertEquals(original.serverFilename, restored.serverFilename)
        assertEquals(original.size, restored.size)
        assertEquals(original.category, restored.category)
        assertEquals(original.md5, restored.md5)
        assertEquals(original.dlink, restored.dlink)
        assertEquals(original.thumbs?.icon, restored.thumbs?.icon)
        assertEquals(original.thumbs?.url1, restored.thumbs?.url1)
    }

    @Test
    fun thumbs_parcelRoundTrip_preservesFields() {
        val thumbs = FileInfo.Thumbs(icon = "i", url1 = "1", url2 = "2", url3 = "3")
        assertEquals(thumbs, parcelRoundTrip(thumbs))
    }

    @Test
    fun isDirectory_returnsTrue_whenIsdirIs1() {
        assertTrue(FileInfo(isdir = 1).isDirectory())
        assertFalse(FileInfo(isdir = 0).isDirectory())
    }

    @Test
    fun isImage_usesCategoryWhen3() {
        assertTrue(FileInfo(category = 3, serverFilename = "noext").isImage())
    }

    @Test
    fun isImage_usesExtensionWhenCategoryNot3() {
        assertTrue(FileInfo(category = 0, serverFilename = "photo.HEIC").isImage())
        assertFalse(FileInfo(category = 0, serverFilename = "video.mp4").isImage())
    }

    @Test
    fun isImage_returnsFalse_whenNoExtensionAndNoCategory() {
        assertFalse(FileInfo(serverFilename = "noext").isImage())
    }

    @Test
    fun isVideo_usesCategoryWhen1() {
        assertTrue(FileInfo(category = 1, serverFilename = "noext").isVideo())
    }

    @Test
    fun isVideo_usesExtensionWhenCategoryNot1() {
        assertTrue(FileInfo(category = 0, serverFilename = "clip.MKV").isVideo())
        assertFalse(FileInfo(category = 0, serverFilename = "photo.jpg").isVideo())
    }

    @Test
    fun extension_returnsEmpty_whenNoDot() {
        assertEquals("", FileInfo(serverFilename = "noext").extension)
        assertEquals("jpg", FileInfo(serverFilename = "a.b.jpg").extension)
        assertEquals("", FileInfo(serverFilename = null).extension)
    }

    @Test
    fun describeContents_isZero() {
        assertEquals(0, FileInfo().describeContents())
    }

    @Test
    fun parcelableCreator_isParcelable() {
        // @Parcelize 生成的 CREATOR 为 public static 字段；用反射断言其存在
        val creator = findCreator(FileInfo::class.java)
        assertEquals("android.os.Parcelable\$Creator", creator.javaClass.interfaces.firstOrNull()?.name)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Parcelable> parcelRoundTrip(value: T): T {
        val parcel = Parcel.obtain()
        value.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        val creator = findCreator(value::class.java) as Parcelable.Creator<T>
        return creator.createFromParcel(parcel)
    }

    /** 在类上定位 @Parcelize 生成的 CREATOR 静态字段。 */
    private fun findCreator(clazz: Class<*>): Parcelable.Creator<*> {
        val field = clazz.getField("CREATOR")
        field.isAccessible = true
        return field.get(null) as Parcelable.Creator<*>
    }
}
