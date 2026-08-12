package com.baidu.tv.player.kt.location

import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 通过 HTTP Range 读取 QuickTime/MOV 的 Apple metadata location。
 *
 * iPhone 视频通常将 GPS 写入 `moov/meta` 的 mdta key
 * `com.apple.quicktime.location.ISO6709`，Android 9 的
 * MediaMetadataRetriever 对该结构支持不稳定。本实现只读取 atom 头和 metadata atom，
 * 通过 atom size 跳过 mdat，不下载或缓存完整视频。
 */
@Singleton
class QuickTimeLocationReader @Inject constructor(
    client: OkHttpClient,
) {
    // 元数据 Range 请求不经过全局 BODY 日志拦截器，防止 CDN 忽略 Range 时日志层读取完整视频。
    private val rangeClient = client.newBuilder().apply {
        interceptors().clear()
        networkInterceptors().clear()
    }.build()

    fun readLocationString(url: String): String? {
        val source = HttpRangeSource(rangeClient, url)
        val fileSize = source.fileSize() ?: return null
        val moov = findChild(source, 0L, fileSize, TYPE_MOOV) ?: return null

        findMeta(source, moov)?.let { meta ->
            parseMdtaLocation(source.readAtomBytes(meta, MAX_METADATA_ATOM_BYTES) ?: return@let null)
                ?.let { return it }
        }

        // 兼容旧 QuickTime ©xyz UserData atom；官方 Android key 也以此为标准来源。
        findDescendant(source, moov, TYPE_UDTA, TYPE_XYZ)?.let { xyz ->
            parseLegacyXyz(source.readAtomBytes(xyz, MAX_LEGACY_ATOM_BYTES) ?: return@let null)
                ?.let { return it }
        }
        return null
    }

    private fun findMeta(source: HttpRangeSource, moov: Atom): Atom? =
        findChild(source, moov.contentOffset, moov.endOffset, TYPE_META)
            ?: findChild(source, moov.contentOffset, moov.endOffset, TYPE_UDTA)?.let { udta ->
                findChild(source, udta.contentOffset, udta.endOffset, TYPE_META)
            }

    private fun findDescendant(source: HttpRangeSource, parent: Atom, containerType: String, targetType: String): Atom? {
        val container = findChild(source, parent.contentOffset, parent.endOffset, containerType) ?: return null
        return findChild(source, container.contentOffset, container.endOffset, targetType)
    }

    private fun findChild(source: HttpRangeSource, start: Long, end: Long, targetType: String): Atom? {
        var offset = start
        var count = 0
        while (offset + ATOM_HEADER_BYTES <= end && count++ < MAX_CHILD_ATOMS) {
            val atom = source.readAtom(offset, end) ?: return null
            if (atom.type == targetType) return atom
            if (atom.endOffset <= offset) return null
            offset = atom.endOffset
        }
        return null
    }

    internal fun parseMdtaLocation(metaAtom: ByteArray): String? {
        val metaHeader = atomHeaderSize(metaAtom, 0) ?: return null
        // ISO BMFF meta 是 FullBox（header 后有 version/flags）；Apple QuickTime 原始 MOV
        // 则可能在 header 后直接放 hdlr。根据首个合法子 atom 自动识别两种布局。
        var offset = when {
            hasValidAtomAt(metaAtom, metaHeader) -> metaHeader
            hasValidAtomAt(metaAtom, metaHeader + FULL_BOX_HEADER_BYTES) -> metaHeader + FULL_BOX_HEADER_BYTES
            else -> return null
        }
        var keys: Map<Int, String> = emptyMap()
        var ilstRange: IntRange? = null

        while (offset + ATOM_HEADER_BYTES <= metaAtom.size) {
            val size = atomSize(metaAtom, offset) ?: break
            if (size < ATOM_HEADER_BYTES || offset + size > metaAtom.size) break
            when (atomType(metaAtom, offset)) {
                TYPE_KEYS -> keys = parseKeys(metaAtom, offset, size)
                TYPE_ILST -> ilstRange = offset until offset + size
            }
            offset += size
        }

        val locationIndex = keys.entries.firstOrNull { it.value == APPLE_LOCATION_KEY }?.key ?: return null
        val range = ilstRange ?: return null
        return parseIlstValue(metaAtom, range.first, range.last + 1 - range.first, locationIndex)
    }

    private fun parseKeys(bytes: ByteArray, offset: Int, size: Int): Map<Int, String> {
        val header = atomHeaderSize(bytes, offset) ?: return emptyMap()
        var cursor = offset + header + FULL_BOX_HEADER_BYTES
        if (cursor + 4 > offset + size) return emptyMap()
        val count = readInt(bytes, cursor)
        cursor += 4
        val result = mutableMapOf<Int, String>()
        for (index in 1..count.coerceAtMost(MAX_METADATA_KEYS)) {
            if (cursor + 8 > offset + size) break
            val entrySize = readInt(bytes, cursor)
            if (entrySize < 8 || cursor + entrySize > offset + size) break
            val key = String(bytes, cursor + 8, entrySize - 8, StandardCharsets.UTF_8).trimEnd('\u0000')
            result[index] = key
            cursor += entrySize
        }
        return result
    }

    private fun parseIlstValue(bytes: ByteArray, offset: Int, size: Int, targetIndex: Int): String? {
        val header = atomHeaderSize(bytes, offset) ?: return null
        var cursor = offset + header
        val end = offset + size
        while (cursor + ATOM_HEADER_BYTES <= end) {
            val entrySize = atomSize(bytes, cursor) ?: break
            if (entrySize < ATOM_HEADER_BYTES || cursor + entrySize > end) break
            val index = readInt(bytes, cursor + 4)
            if (index == targetIndex) {
                val dataOffset = cursor + ATOM_HEADER_BYTES
                val dataSize = atomSize(bytes, dataOffset) ?: return null
                if (atomType(bytes, dataOffset) != TYPE_DATA || dataSize < 16 || dataOffset + dataSize > cursor + entrySize) return null
                return String(bytes, dataOffset + 16, dataSize - 16, StandardCharsets.UTF_8).trimEnd('\u0000')
            }
            cursor += entrySize
        }
        return null
    }

    private fun parseLegacyXyz(atom: ByteArray): String? {
        val header = atomHeaderSize(atom, 0) ?: return null
        if (atom.size <= header) return null
        val payload = atom.copyOfRange(header, atom.size)
        val text = String(payload, StandardCharsets.ISO_8859_1)
        return ISO_6709_REGEX.find(text)?.value
    }

    private class HttpRangeSource(
        private val client: OkHttpClient,
        private val url: String,
    ) {
        fun fileSize(): Long? {
            val response = requestRange(0L, ATOM_HEADER_BYTES - 1L) ?: return null
            return response.totalSize
        }

        fun readAtom(offset: Long, parentEnd: Long): Atom? {
            val bytes = requestRange(offset, offset + EXTENDED_ATOM_HEADER_BYTES - 1L)?.bytes ?: return null
            if (bytes.size < ATOM_HEADER_BYTES) return null
            val size32 = readUnsignedInt(bytes, 0)
            val type = atomType(bytes, 0)
            val headerSize: Int
            val size: Long
            when (size32) {
                0L -> {
                    headerSize = ATOM_HEADER_BYTES
                    size = parentEnd - offset
                }
                1L -> {
                    if (bytes.size < EXTENDED_ATOM_HEADER_BYTES) return null
                    headerSize = EXTENDED_ATOM_HEADER_BYTES
                    size = ByteBuffer.wrap(bytes, 8, 8).order(ByteOrder.BIG_ENDIAN).long
                }
                else -> {
                    headerSize = ATOM_HEADER_BYTES
                    size = size32
                }
            }
            if (size < headerSize || offset + size > parentEnd) return null
            return Atom(offset, size, type, headerSize)
        }

        fun readAtomBytes(atom: Atom, maxBytes: Int): ByteArray? {
            if (atom.size > maxBytes) return null
            return requestRange(atom.offset, atom.endOffset - 1)?.bytes
                ?.takeIf { it.size.toLong() == atom.size }
        }

        private fun requestRange(start: Long, endInclusive: Long): RangeResponse? {
            if (start < 0 || endInclusive < start || endInclusive - start + 1 > MAX_SINGLE_RANGE_BYTES) return null
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "pan.baidu.com")
                .header("Range", "bytes=$start-$endInclusive")
                .get()
                .build()
            return client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                // 非零偏移时服务器若忽略 Range，不能从头读取整个大文件来模拟 seek。
                if (start > 0 && response.code != 206) return@use null
                val requested = endInclusive - start + 1
                val body = response.body ?: return@use null
                val bytes = body.source().readByteArray(minOf(requested, MAX_SINGLE_RANGE_BYTES.toLong()))
                val total = response.header("Content-Range")
                    ?.substringAfterLast('/')
                    ?.toLongOrNull()
                    ?: response.header("Content-Length")?.toLongOrNull()
                RangeResponse(bytes, total)
            }
        }
    }

    private data class RangeResponse(val bytes: ByteArray, val totalSize: Long?)
    private data class Atom(val offset: Long, val size: Long, val type: String, val headerSize: Int) {
        val contentOffset: Long get() = offset + headerSize
        val endOffset: Long get() = offset + size
    }

    companion object {
        private const val APPLE_LOCATION_KEY = "com.apple.quicktime.location.ISO6709"
        private const val TYPE_MOOV = "moov"
        private const val TYPE_META = "meta"
        private const val TYPE_UDTA = "udta"
        private const val TYPE_KEYS = "keys"
        private const val TYPE_ILST = "ilst"
        private const val TYPE_DATA = "data"
        private const val TYPE_XYZ = "©xyz"
        private const val ATOM_HEADER_BYTES = 8
        private const val EXTENDED_ATOM_HEADER_BYTES = 16
        private const val FULL_BOX_HEADER_BYTES = 4
        private const val MAX_CHILD_ATOMS = 256
        private const val MAX_METADATA_KEYS = 256
        private const val MAX_METADATA_ATOM_BYTES = 2 * 1024 * 1024
        private const val MAX_LEGACY_ATOM_BYTES = 64 * 1024
        private const val MAX_SINGLE_RANGE_BYTES = MAX_METADATA_ATOM_BYTES
        private val ISO_6709_REGEX = Regex("[+-]\\d{1,3}(?:\\.\\d+)[+-]\\d{1,3}(?:\\.\\d+)(?:[+-]\\d+(?:\\.\\d+)?)?/")

        private fun hasValidAtomAt(bytes: ByteArray, offset: Int): Boolean {
            val size = atomSize(bytes, offset) ?: return false
            return size >= ATOM_HEADER_BYTES && offset + size <= bytes.size
        }

        private fun atomSize(bytes: ByteArray, offset: Int): Int? {
            if (offset < 0 || offset + 8 > bytes.size) return null
            val value = readUnsignedInt(bytes, offset)
            return when {
                value == 1L && offset + 16 <= bytes.size -> ByteBuffer.wrap(bytes, offset + 8, 8).order(ByteOrder.BIG_ENDIAN).long.toInt()
                value in 8..Int.MAX_VALUE.toLong() -> value.toInt()
                else -> null
            }
        }

        private fun atomHeaderSize(bytes: ByteArray, offset: Int): Int? {
            if (offset < 0 || offset + 8 > bytes.size) return null
            return if (readUnsignedInt(bytes, offset) == 1L) 16 else 8
        }

        private fun atomType(bytes: ByteArray, offset: Int): String =
            String(bytes, offset + 4, 4, StandardCharsets.ISO_8859_1)

        private fun readInt(bytes: ByteArray, offset: Int): Int =
            ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.BIG_ENDIAN).int

        private fun readUnsignedInt(bytes: ByteArray, offset: Int): Long =
            readInt(bytes, offset).toLong() and 0xffff_ffffL
    }
}
